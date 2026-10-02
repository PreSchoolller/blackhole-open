package io.github.preschoollerr.blackhole.eng.graph.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;
import static io.github.preschoollerr.blackhole.eng.graph.vk.VkUtils.vkCheck;

/**
 * Vulkan 图像 —— 封装通过 VMA 创建的 GPU 图像。
 * <p>
 * 图像用于存储纹理、渲染目标、深度缓冲等 GPU 数据。
 * 本项目中主要用于创建渲染目标（如帧缓冲颜色附件）。
 * <p>
 * 使用 {@link ImageData} Builder 模式配置图像参数：
 * <pre>
 *   var imageData = new Image.ImageData()
 *       .width(1920).height(1080)
 *       .format(VK_FORMAT_R8G8B8A8_SRGB)
 *       .usage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
 *   var image = new Image(vkCtx, imageData);
 * </pre>
 */
public class Image {

    /** VMA 内存分配句柄 */
    private final long allocation;
    /** 图像格式 */
    private final int format;
    /** 图像高度 */
    private final int height;
    /** Mip 级别数 */
    private final int mipLevels;
    /** Vulkan 图像句柄 */
    private final long vkImage;
    /** 图像宽度 */
    private final int width;

    /**
     * 通过 VMA 创建图像。
     *
     * @param vkCtx     Vulkan 上下文
     * @param imageData 图像参数
     */
    public Image(VkCtx vkCtx, ImageData imageData) {
        try (var stack = MemoryStack.stackPush()) {
            this.format = imageData.format;
            this.mipLevels = imageData.mipLevels;
            this.width = imageData.width;
            this.height = imageData.height;

            // 图像创建信息
            VkImageCreateInfo imageCreateInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .imageType(imageData.imageType)
                    .format(format)
                    .extent(it -> it
                            .width(imageData.width)
                            .height(imageData.height)
                            .depth(imageData.depth)
                    )
                    .mipLevels(mipLevels)
                    .arrayLayers(imageData.arrayLayers)
                    .samples(imageData.sampleCount)
                    .flags(imageData.flags)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)   // 最优平铺（GPU 优化布局）
                    .usage(imageData.usage);

            // VMA 分配信息
            var allocCreateInfo = VmaAllocationCreateInfo.calloc(1, stack)
                    .get(0)
                    .usage(VMA_MEMORY_USAGE_AUTO)   // VMA 自动选择最佳内存类型
                    .flags(imageData.memUsage)
                    .priority(1.0f);

            PointerBuffer pAllocation = stack.callocPointer(1);
            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vmaCreateImage(vkCtx.getMemAlloc().getVmaAlloc(), imageCreateInfo, allocCreateInfo, lp, pAllocation, null),
                    "Failed to create image");
            vkImage = lp.get(0);
            allocation = pAllocation.get(0);
        }
    }

    /** 通过 VMA 销毁图像和释放内存 */
    public void cleanup(VkCtx vkCtx) {
        vmaDestroyImage(vkCtx.getMemAlloc().getVmaAlloc(), vkImage, allocation);
    }

    public int getFormat() {
        return format;
    }

    public int getHeight() {
        return height;
    }

    public int getMipLevels() {
        return mipLevels;
    }

    public long getVkImage() {
        return vkImage;
    }

    public int getWidth() {
        return width;
    }

    /**
     * 图像参数 Builder —— 使用流式 API 配置图像创建参数。
     * <p>
     * 默认值：
     * <ul>
     *   <li>format: R8G8B8A8_SRGB</li>
     *   <li>mipLevels: 1</li>
     *   <li>sampleCount: 1</li>
     *   <li>arrayLayers: 1</li>
     *   <li>memUsage: VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT</li>
     * </ul>
     */
    public static class ImageData {
        private int arrayLayers;
        private int flags;
        private int format;
        private int height;
        private int imageType;
        private int depth;
        private int memUsage;
        private int mipLevels;
        private int sampleCount;
        private int usage;
        private int width;

        public ImageData() {
            format = VK_FORMAT_R8G8B8A8_SRGB;
            mipLevels = 1;
            sampleCount = 1;
            arrayLayers = 1;
            flags = 0;
            imageType = VK_IMAGE_TYPE_2D;
            depth = 1;
            memUsage = VMA_ALLOCATION_CREATE_DEDICATED_MEMORY_BIT;
        }

        /** 图像类型（默认 VK_IMAGE_TYPE_2D；3D 纹理传 VK_IMAGE_TYPE_3D） */
        public ImageData imageType(int imageType) {
            this.imageType = imageType;
            return this;
        }

        /** 第三维尺寸（2D 图像恒为 1；3D 图像为深度） */
        public ImageData depth(int depth) {
            this.depth = depth;
            return this;
        }

        public ImageData arrayLayers(int arrayLayers) {
            this.arrayLayers = arrayLayers;
            return this;
        }

        /** 图像创建标志（如立方体贴图需要 VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT） */
        public ImageData flags(int flags) {
            this.flags = flags;
            return this;
        }

        public ImageData format(int format) {
            this.format = format;
            return this;
        }

        public ImageData height(int height) {
            this.height = height;
            return this;
        }

        public ImageData memUsage(int memUsage) {
            this.memUsage = memUsage;
            return this;
        }

        public ImageData mipLevels(int mipLevels) {
            this.mipLevels = mipLevels;
            return this;
        }

        public ImageData sampleCount(int sampleCount) {
            this.sampleCount = sampleCount;
            return this;
        }

        public ImageData usage(int usage) {
            this.usage = usage;
            return this;
        }

        public ImageData width(int width) {
            this.width = width;
            return this;
        }
    }
}
