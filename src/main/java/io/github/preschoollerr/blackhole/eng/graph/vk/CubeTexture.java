package io.github.preschoollerr.blackhole.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.tinylog.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;
import static io.github.preschoollerr.blackhole.eng.graph.vk.VkUtils.vkCheck;

/**
 * 立方体贴图纹理 —— 从 classpath 加载 6 面图像（JPG/PNG），上传为 Vulkan cubemap。
 * <p>
 * 用作黑洞渲染的背景星空贴图（对应 NPGS 项目的 Universe0Skybox，6 张 1024×1024 星空图）。
 * 加载约定与 NPGS 一致：R8G8B8A8_UNORM、不做垂直翻转。
 * 上传后在同一命令缓冲内用 vkCmdBlitImage 逐级生成完整 mip 链
 * （UNORM 格式支持 blit），供采样器做三线性过滤，消除旋转时的亮星闪烁。
 * <p>
 * 上传流程：STB 解码 → 暂存缓冲区（host visible）→ 单次提交命令缓冲
 * （布局 UNDEFINED→TRANSFER_DST→[blit mip 链]→SHADER_READ_ONLY_OPTIMAL）。
 */
public class CubeTexture {

    /** 立方体面文件名（Vulkan 层序：+X, -X, +Y, -Y, +Z, -Z） */
    private static final String[] FACE_FILES = {"PosX", "NegX", "PosY", "NegY", "PosZ", "NegZ"};

    private final Image image;
    private final ImageView imageView;
    private final long sampler;
    private final int width;
    private final int height;
    private final int mipLevels;

    /**
     * 加载并上传立方体贴图（阻塞直到 GPU 上传完成）。
     *
     * @param vkCtx     Vulkan 上下文
     * @param graphQueue 图形队列（用于提交上传命令）
     * @param basePath  classpath 内的目录（如 "/textures/skybox"，以 / 开头）
     * @param extension 面文件扩展名（如 ".jpg"）
     */
    public CubeTexture(VkCtx vkCtx, Queue.GraphicsQueue graphQueue, String basePath, String extension) {
        try (var stack = MemoryStack.stackPush()) {
            // 1. STB 解码 6 面（强制 RGBA8）
            ByteBuffer[] faces = new ByteBuffer[6];
            try {
                int[] dims = {0, 0};
                for (int i = 0; i < 6; i++) {
                    faces[i] = loadFace(stack, basePath + "/" + FACE_FILES[i] + extension, dims, i);
                }
                width = dims[0];
                height = dims[1];
            } catch (RuntimeException excp) {
                // 解码失败时释放已加载的面，避免泄漏
                for (ByteBuffer face : faces) {
                    if (face != null) {
                        stbi_image_free(face);
                    }
                }
                throw excp;
            }
            long faceSize = width * height * 4L;
            // 完整 mip 链级数：floor(log2(max(w,h))) + 1（1024² → 11 级）
            int maxDim = Math.max(width, height);
            mipLevels = Integer.numberOfTrailingZeros(Integer.highestOneBit(maxDim)) + 1;

            // 2. 暂存缓冲区并写入 6 面数据
            var staging = new VkBuffer(vkCtx, faceSize * 6,
                    VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_AUTO,
                    VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, 0);
            long mapped = staging.map(vkCtx);
            for (int i = 0; i < 6; i++) {
                MemoryUtil.memCopy(MemoryUtil.memAddress(faces[i]), mapped + i * faceSize, faceSize);
            }
            staging.unMap(vkCtx);
            for (ByteBuffer face : faces) {
                stbi_image_free(face);
            }

            // 3. 创建 cubemap 图像（arrayLayers=6 + CUBE_COMPATIBLE；mip 链需要 TRANSFER_SRC）
            image = new Image(vkCtx, new Image.ImageData()
                    .width(width).height(height)
                    .format(VK_FORMAT_R8G8B8A8_UNORM)
                    .arrayLayers(6)
                    .mipLevels(mipLevels)
                    .flags(VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT)
                    .usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                            | VK_IMAGE_USAGE_SAMPLED_BIT));

            // 4. 上传：布局切换 + 逐面拷贝 + blit mip 链 + 布局切换，一次提交
            var cmdPool = new CmdPool(vkCtx, graphQueue.getQueueFamilyIndex(), false);
            var cmdBuffer = new CmdBuffer(vkCtx, cmdPool, true, true);
            try {
                cmdBuffer.beginRecording();
                VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();

                // level 0：UNDEFINED → TRANSFER_DST
                var preBarrier = VkImageMemoryBarrier.calloc(1, stack)
                        .sType$Default()
                        .srcAccessMask(VK_ACCESS_NONE)
                        .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                        .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .image(image.getVkImage())
                        .subresourceRange(it -> it
                                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                .baseMipLevel(0).levelCount(1)
                                .baseArrayLayer(0).layerCount(6));
                vkCmdPipelineBarrier(cmdHandle,
                        VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                        0, null, null, preBarrier);

                var copies = VkBufferImageCopy.calloc(6, stack);
                for (int i = 0; i < 6; i++) {
                    int layer = i;
                    copies.get(i)
                            .bufferOffset(i * faceSize)
                            .bufferRowLength(0)
                            .bufferImageHeight(0)
                            .imageSubresource(it -> it
                                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                    .mipLevel(0).baseArrayLayer(layer).layerCount(1))
                            .imageOffset(it -> it.x(0).y(0).z(0))
                            .imageExtent(it -> it.width(width).height(height).depth(1));
                }
                vkCmdCopyBufferToImage(cmdHandle, staging.getBuffer(), image.getVkImage(),
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, copies);

                // mip 链：level i-1 → level i 逐级 blit 缩小（同图像不同 mip 子资源，允许）
                for (int i = 1; i < mipLevels; i++) {
                    int prevMip = i - 1;
                    int curMip = i;
                    int prevW = Math.max(width >> prevMip, 1);
                    int prevH = Math.max(height >> prevMip, 1);
                    int curW = Math.max(width >> curMip, 1);
                    int curH = Math.max(height >> curMip, 1);

                    // 两个子资源屏障：上一级 TRANSFER_DST→TRANSFER_SRC；本级 UNDEFINED→TRANSFER_DST
                    var mipBarriers = VkImageMemoryBarrier.calloc(2, stack);
                    mipBarriers.get(0)
                            .sType$Default()
                            .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                            .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                            .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                            .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                            .image(image.getVkImage())
                            .subresourceRange(it -> it
                                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                    .baseMipLevel(prevMip).levelCount(1)
                                    .baseArrayLayer(0).layerCount(6));
                    mipBarriers.get(1)
                            .sType$Default()
                            .srcAccessMask(VK_ACCESS_NONE)
                            .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                            .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                            .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                            .image(image.getVkImage())
                            .subresourceRange(it -> it
                                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                    .baseMipLevel(curMip).levelCount(1)
                                    .baseArrayLayer(0).layerCount(6));
                    vkCmdPipelineBarrier(cmdHandle,
                            VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                            0, null, null, mipBarriers);

                    var blit = VkImageBlit.calloc(1, stack);
                    blit.srcSubresource(it -> it
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .mipLevel(prevMip).baseArrayLayer(0).layerCount(6));
                    blit.srcOffsets(0, it -> it.set(0, 0, 0));
                    blit.srcOffsets(1, it -> it.set(prevW, prevH, 1));
                    blit.dstSubresource(it -> it
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .mipLevel(curMip).baseArrayLayer(0).layerCount(6));
                    blit.dstOffsets(0, it -> it.set(0, 0, 0));
                    blit.dstOffsets(1, it -> it.set(curW, curH, 1));
                    vkCmdBlitImage(cmdHandle, image.getVkImage(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                            image.getVkImage(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                            blit, VK_FILTER_LINEAR);
                }

                // 收尾：全部层级 → SHADER_READ（低级从 TRANSFER_SRC，最高级从 TRANSFER_DST）
                var tailBarriers = VkImageMemoryBarrier.calloc(2, stack);
                tailBarriers.get(0)
                        .sType$Default()
                        .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                        .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                        .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .image(image.getVkImage())
                        .subresourceRange(it -> it
                                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                .baseMipLevel(0).levelCount(mipLevels - 1)
                                .baseArrayLayer(0).layerCount(6));
                tailBarriers.get(1)
                        .sType$Default()
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                        .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                        .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                        .image(image.getVkImage())
                        .subresourceRange(it -> it
                                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                .baseMipLevel(mipLevels - 1).levelCount(1)
                                .baseArrayLayer(0).layerCount(6));
                vkCmdPipelineBarrier(cmdHandle,
                        VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                        0, null, null, tailBarriers);

                cmdBuffer.endRecording();
                cmdBuffer.submitAndWait(vkCtx, graphQueue);
            } finally {
                cmdBuffer.cleanup(vkCtx, cmdPool);
                cmdPool.cleanup(vkCtx);
            }
            staging.cleanup(vkCtx);
            Logger.debug("CubeTexture uploaded: {}x{} ({} mip levels) from {}", width, height, mipLevels, basePath);

            // 5. 立方体视图（全 mip 链）+ 三线性采样器
            imageView = new ImageView(vkCtx.getDevice(), image.getVkImage(),
                    new ImageView.ImageViewData()
                            .format(VK_FORMAT_R8G8B8A8_UNORM)
                            .viewType(VK_IMAGE_VIEW_TYPE_CUBE)
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .mipLevels(mipLevels).layerCount(6),
                    false);

            Device device = vkCtx.getDevice();
            var samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType$Default()
                    .magFilter(VK_FILTER_LINEAR)
                    .minFilter(VK_FILTER_LINEAR)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .anisotropyEnable(device.isSamplerAnisotropy())
                    .maxAnisotropy(8.0f)
                    .minLod(0.0f)
                    .maxLod(mipLevels - 1.0f);
            long[] lp = new long[1];
            vkCheck(vkCreateSampler(device.getVkDevice(), samplerInfo, null, lp),
                    "Failed to create cube texture sampler");
            sampler = lp[0];
        }
    }

    /** 从 classpath 读取图像文件并用 STB 解码为 RGBA8；dims 记录首面的尺寸并校验其余面一致 */
    private ByteBuffer loadFace(MemoryStack stack, String path, int[] dims, int faceIndex) {
        try (InputStream is = CubeTexture.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Cube texture face not found: " + path);
            }
            byte[] fileBytes = is.readAllBytes();
            ByteBuffer fileBuf = MemoryUtil.memAlloc(fileBytes.length);
            fileBuf.put(fileBytes).flip();
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);
            ByteBuffer pixels = stbi_load_from_memory(fileBuf, w, h, comp, 4);
            MemoryUtil.memFree(fileBuf);
            if (pixels == null) {
                throw new IOException("Failed to decode cube texture face [" + path + "]: " + stbi_failure_reason());
            }
            if (faceIndex == 0) {
                dims[0] = w.get(0);
                dims[1] = h.get(0);
            } else if (dims[0] != w.get(0) || dims[1] != h.get(0)) {
                throw new IOException("Cube texture face size mismatch at " + path
                        + ": expected " + dims[0] + "x" + dims[1] + ", got " + w.get(0) + "x" + h.get(0));
            }
            return pixels;
        } catch (IOException excp) {
            throw new RuntimeException(excp);
        }
    }

    /** 释放采样器、视图和图像 */
    public void cleanup(VkCtx vkCtx) {
        vkDestroySampler(vkCtx.getDevice().getVkDevice(), sampler, null);
        imageView.cleanup(vkCtx.getDevice());
        image.cleanup(vkCtx);
    }

    public ImageView getImageView() {
        return imageView;
    }

    public long getSampler() {
        return sampler;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /** mip 链级数 */
    public int getMipLevels() {
        return Integer.numberOfTrailingZeros(Integer.highestOneBit(Math.max(width, height))) + 1;
    }
}
