package io.github.preschoolller.blackhole.eng.graph.vk;

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
import static io.github.preschoolller.blackhole.eng.graph.vk.VkUtils.vkCheck;

/**
 * 二维纹理 —— 从 classpath 加载单张图像（JPG/PNG），上传为 Vulkan 2D 纹理并生成 mip 链。
 * <p>
 * 为 NPGS 原版贴图盘（iImageTexture，set1.b9）而设，加载约定与 NPGS 一致：
 * R8G8B8A8_UNORM、不做垂直翻转。上传流程与 {@link CubeTexture} 相同（单层版）：
 * STB 解码 → 暂存缓冲 → 一次提交（UNDEFINED→TRANSFER_DST→blit mip 链→SHADER_READ）。
 */
public class Texture2D {

    private final Image image;
    private final ImageView imageView;
    private final long sampler;
    private final int width;
    private final int height;
    private final int mipLevels;

    /**
     * 加载并上传 2D 纹理（阻塞直到 GPU 上传完成）。
     *
     * @param vkCtx      Vulkan 上下文
     * @param graphQueue 图形队列（提交上传命令）
     * @param path       classpath 内的图像路径（如 "/textures/npgs/Disk/R.jpg"，以 / 开头）
     */
    public Texture2D(VkCtx vkCtx, Queue.GraphicsQueue graphQueue, String path) {
        try (var stack = MemoryStack.stackPush()) {
            // 1. STB 解码（强制 RGBA8）
            ByteBuffer pixels;
            int[] w = {0}, h = {0}, comp = {0};
            try (InputStream is = Texture2D.class.getResourceAsStream(path)) {
                if (is == null) {
                    throw new IOException("Texture2D not found: " + path);
                }
                byte[] fileBytes = is.readAllBytes();
                ByteBuffer fileBuf = MemoryUtil.memAlloc(fileBytes.length);
                fileBuf.put(fileBytes).flip();
                pixels = stbi_load_from_memory(fileBuf, w, h, comp, 4);
                MemoryUtil.memFree(fileBuf);
            } catch (IOException excp) {
                throw new RuntimeException(excp);
            }
            if (pixels == null) {
                throw new RuntimeException("Failed to decode texture [" + path + "]: " + stbi_failure_reason());
            }
            width = w[0];
            height = h[0];
            long imageSize = (long) width * height * 4;
            mipLevels = Integer.numberOfTrailingZeros(Integer.highestOneBit(Math.max(width, height))) + 1;

            // 2. 暂存缓冲区写入
            var staging = new VkBuffer(vkCtx, imageSize,
                    VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_AUTO,
                    VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, 0);
            long mapped = staging.map(vkCtx);
            MemoryUtil.memCopy(MemoryUtil.memAddress(pixels), mapped, imageSize);
            staging.unMap(vkCtx);
            stbi_image_free(pixels);

            // 3. 2D 图像（mip 链需要 TRANSFER_SRC）
            image = new Image(vkCtx, new Image.ImageData()
                    .width(width).height(height)
                    .format(VK_FORMAT_R8G8B8A8_UNORM)
                    .mipLevels(mipLevels)
                    .usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                            | VK_IMAGE_USAGE_SAMPLED_BIT));

            // 4. 上传 + blit mip 链 + 收尾，一次提交
            var cmdPool = new CmdPool(vkCtx, graphQueue.getQueueFamilyIndex(), false);
            var cmdBuffer = new CmdBuffer(vkCtx, cmdPool, true, true);
            try {
                cmdBuffer.beginRecording();
                VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();

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
                                .baseArrayLayer(0).layerCount(1));
                vkCmdPipelineBarrier(cmdHandle,
                        VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                        0, null, null, preBarrier);

                var copy = VkBufferImageCopy.calloc(1, stack);
                copy.get(0)
                        .bufferOffset(0)
                        .bufferRowLength(0)
                        .bufferImageHeight(0)
                        .imageSubresource(it -> it
                                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                .mipLevel(0).baseArrayLayer(0).layerCount(1))
                        .imageOffset(it -> it.x(0).y(0).z(0))
                        .imageExtent(it -> it.width(width).height(height).depth(1));
                vkCmdCopyBufferToImage(cmdHandle, staging.getBuffer(), image.getVkImage(),
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, copy);

                for (int i = 1; i < mipLevels; i++) {
                    int curMip = i;
                    int prevMip = i - 1;
                    int prevW = Math.max(width >> prevMip, 1);
                    int prevH = Math.max(height >> prevMip, 1);
                    int curW = Math.max(width >> curMip, 1);
                    int curH = Math.max(height >> curMip, 1);

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
                                    .baseArrayLayer(0).layerCount(1));
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
                                    .baseArrayLayer(0).layerCount(1));
                    vkCmdPipelineBarrier(cmdHandle,
                            VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                            0, null, null, mipBarriers);

                    var blit = VkImageBlit.calloc(1, stack);
                    blit.srcSubresource(it -> it
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .mipLevel(prevMip).baseArrayLayer(0).layerCount(1));
                    blit.srcOffsets(0, it -> it.set(0, 0, 0));
                    blit.srcOffsets(1, it -> it.set(prevW, prevH, 1));
                    blit.dstSubresource(it -> it
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .mipLevel(curMip).baseArrayLayer(0).layerCount(1));
                    blit.dstOffsets(0, it -> it.set(0, 0, 0));
                    blit.dstOffsets(1, it -> it.set(curW, curH, 1));
                    vkCmdBlitImage(cmdHandle, image.getVkImage(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                            image.getVkImage(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                            blit, VK_FILTER_LINEAR);
                }

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
                                .baseArrayLayer(0).layerCount(1));
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
                                .baseArrayLayer(0).layerCount(1));
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
            Logger.debug("Texture2D uploaded: {}x{} ({} mip levels) from {}", width, height, mipLevels, path);

            // 5. 2D 视图 + 三线性采样器
            imageView = new ImageView(vkCtx.getDevice(), image.getVkImage(),
                    new ImageView.ImageViewData()
                            .format(VK_FORMAT_R8G8B8A8_UNORM)
                            .viewType(VK_IMAGE_VIEW_TYPE_2D)
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .mipLevels(mipLevels),
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
                    .maxLod(mipLevels - 1.0f);
            long[] lp = new long[1];
            vkCheck(vkCreateSampler(device.getVkDevice(), samplerInfo, null, lp),
                    "Failed to create 2D texture sampler");
            sampler = lp[0];
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
}
