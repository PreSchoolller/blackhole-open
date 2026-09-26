package vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.nio.ByteBuffer;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.KHRSynchronization2.VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR;
import static org.lwjgl.vulkan.VK13.*;
import static vulkanb.eng.graph.vk.VkUtils.vkCheck;

/**
 * 噪声哈希 LUT —— 64³ R8_UNORM 3D 纹理，将 kerr.frag {@code PerlinNoise} 的 sin 哈希查表化。
 * <p>
 * PerlinNoise 是值噪声：8 个格点角的哈希值做三次衰减三线性插值。本 LUT 把哈希函数
 * {@code fract(sin(dot(cell, C)) * K)} 在 64³ 格点上按同式预计算（Java Math.sin 求值），
 * 着色器用 8 次 {@code texelFetch}（坐标 &63 周期化，负坐标由补码位与自然回绕）替代
 * 8 条 sin 超越函数链，插值结构与值域（[-1,1] 线性映射 [0,1]）保持不变。
 * 256KB 常驻纹理缓存；格点 64 取模引入的重复周期远大于盘面低频噪声的有效范围。
 * 由 BlackHoleArgs 末尾追加的 {@code iNoiseLut} 字段做运行时开关，与程序化路径实时 A/B。
 */
public class NoiseHashLut {

    public static final int SIZE = 64;
    /** 与 kerr.frag 哈希常量严格一致 */
    private static final float HASH_CX = 12.9898f;
    private static final float HASH_CY = 78.233f;
    private static final float HASH_CZ = 213.765f;
    private static final float HASH_K = 43758.5453f;

    private final Image image;
    private final ImageView imageView;
    private final long sampler;

    public NoiseHashLut(VkCtx vkCtx, Queue.GraphicsQueue graphQueue) {
        // 1. CPU 求值哈希表（与 GLSL 同式；值域 [-1,1] 线性映射到 [0,255]）
        byte[] lut = new byte[SIZE * SIZE * SIZE];
        int idx = 0;
        for (int z = 0; z < SIZE; z++) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    float dot = x * HASH_CX + y * HASH_CY + z * HASH_CZ;
                    float h = 2.0f * fract((float) Math.sin(dot) * HASH_K) - 1.0f;
                    lut[idx++] = (byte) Math.round((h + 1.0f) * 127.5f);
                }
            }
        }

        try (var stack = MemoryStack.stackPush()) {
            // 2. 暂存缓冲
            int lutBytes = SIZE * SIZE * SIZE;
            var staging = new VkBuffer(vkCtx, lutBytes, VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                    VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, 0);
            long mapped = staging.map(vkCtx);
            ByteBuffer buf = MemoryUtil.memByteBuffer(mapped, lutBytes);
            buf.put(lut).flip();
            staging.unMap(vkCtx);

            // 3. 3D 图像
            image = new Image(vkCtx, new Image.ImageData()
                    .width(SIZE).height(SIZE).depth(SIZE)
                    .imageType(VK_IMAGE_TYPE_3D)
                    .format(VK_FORMAT_R8_UNORM)
                    .usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT));

            // 4. 上传：UNDEFINED → TRANSFER_DST → 拷贝 → SHADER_READ_ONLY，一次提交
            var cmdPool = new CmdPool(vkCtx, graphQueue.getQueueFamilyIndex(), false);
            var cmdBuffer = new CmdBuffer(vkCtx, cmdPool, true, true);
            try {
                cmdBuffer.beginRecording();
                VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();
                VkUtils.imageBarrier(stack, cmdHandle, image.getVkImage(),
                        VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        VK_PIPELINE_STAGE_2_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR,
                        VK_ACCESS_2_NONE, VK_ACCESS_2_TRANSFER_WRITE_BIT, VK_IMAGE_ASPECT_COLOR_BIT);

                var copy = VkBufferImageCopy.calloc(1, stack);
                copy.get(0)
                        .bufferOffset(0)
                        .bufferRowLength(0)
                        .bufferImageHeight(0)
                        .imageSubresource(it -> it
                                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                .mipLevel(0).baseArrayLayer(0).layerCount(1))
                        .imageOffset(it -> it.x(0).y(0).z(0))
                        .imageExtent(it -> it.width(SIZE).height(SIZE).depth(SIZE));
                vkCmdCopyBufferToImage(cmdHandle, staging.getBuffer(), image.getVkImage(),
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, copy);

                VkUtils.imageBarrier(stack, cmdHandle, image.getVkImage(),
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR, VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                        VK_ACCESS_2_TRANSFER_WRITE_BIT, VK_ACCESS_2_SHADER_READ_BIT,
                        VK_IMAGE_ASPECT_COLOR_BIT);
                cmdBuffer.endRecording();
                cmdBuffer.submitAndWait(vkCtx, graphQueue);
            } finally {
                cmdBuffer.cleanup(vkCtx, cmdPool);
                cmdPool.cleanup(vkCtx);
            }
            staging.cleanup(vkCtx);

            // 5. 3D 视图 + 采样器（texelFetch 不经过采样滤波，NEAREST/REPEAT 仅为语义合法）
            imageView = new ImageView(vkCtx.getDevice(), image.getVkImage(),
                    new ImageView.ImageViewData()
                            .format(VK_FORMAT_R8_UNORM)
                            .viewType(VK_IMAGE_VIEW_TYPE_3D)
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT),
                    false);
            var samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType$Default()
                    .magFilter(VK_FILTER_NEAREST)
                    .minFilter(VK_FILTER_NEAREST)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .maxLod(0.0f);
            long[] lp = new long[1];
            vkCheck(vkCreateSampler(vkCtx.getDevice().getVkDevice(), samplerInfo, null, lp),
                    "Failed to create noise hash LUT sampler");
            sampler = lp[0];
        }
    }

    private static float fract(float f) {
        return f - (float) Math.floor(f);
    }

    public long getSampler() {
        return sampler;
    }

    public ImageView getImageView() {
        return imageView;
    }

    public void cleanup(VkCtx vkCtx) {
        imageView.cleanup(vkCtx.getDevice());
        image.cleanup(vkCtx);
        vkDestroySampler(vkCtx.getDevice().getVkDevice(), sampler, null);
    }
}
