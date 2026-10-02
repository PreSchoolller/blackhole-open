package io.github.preschoolller.blackhole.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkImageViewCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK13.*;
import static io.github.preschoolller.blackhole.eng.graph.vk.VkUtils.vkCheck;

/**
 * 图像视图 —— 定义如何访问 Vulkan 图像。
 * <p>
 * 图像视图（ImageView）描述了如何解释图像数据：
 * <ul>
 *   <li>格式（format）—— 像素数据的解释方式</li>
 *   <li>方面掩码（aspectMask）—— 颜色/深度/模板</li>
 *   <li>Mip 级别范围 —— 可以只暴露部分 mip 链</li>
 *   <li>数组层范围 —— 可以只暴露部分数组层</li>
 *   <li>视图类型 —— 1D/2D/3D/Cube</li>
 * </ul>
 * <p>
 * 图像本身是原始数据存储，图像视图是访问接口。
 * 管线和帧缓冲区使用图像视图来引用图像。
 */
public class ImageView {

    /** 方面掩码（VK_IMAGE_ASPECT_COLOR_BIT / DEPTH_BIT） */
    private final int aspectMask;
    /** 是否为深度图像 */
    private final boolean depthImage;
    /** 数组层计数 */
    private final int layerCount;
    /** Mip 级别计数 */
    private final int mipLevels;
    /** 关联的 Vulkan 图像句柄 */
    private final long vkImage;
    /** Vulkan 图像视图句柄 */
    private final long vkImageView;

    /**
     * 创建图像视图。
     *
     * @param device        逻辑设备
     * @param vkImage       Vulkan 图像句柄
     * @param imageViewData 图像视图参数
     * @param depthImage    是否为深度图像
     */
    public ImageView(Device device, long vkImage, ImageViewData imageViewData, boolean depthImage) {
        this.aspectMask = imageViewData.aspectMask;
        this.mipLevels = imageViewData.mipLevels;
        this.vkImage = vkImage;
        this.depthImage = depthImage;
        this.layerCount = imageViewData.layerCount;

        try (var stack = MemoryStack.stackPush()) {
            LongBuffer lp = stack.mallocLong(1);
            var viewCreateInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .image(vkImage)
                    .viewType(imageViewData.viewType)
                    .format(imageViewData.format)
                    .subresourceRange(it -> it
                            .aspectMask(aspectMask)
                            .baseMipLevel(0)
                            .levelCount(mipLevels)
                            .baseArrayLayer(imageViewData.baseArrayLayer)
                            .layerCount(imageViewData.layerCount));

            vkCheck(vkCreateImageView(device.getVkDevice(), viewCreateInfo, null, lp),
                    "Failed to create image view");
            vkImageView = lp.get(0);
        }
    }

    /** 销毁图像视图 */
    public void cleanup(Device device) {
        vkDestroyImageView(device.getVkDevice(), vkImageView, null);
    }

    public int getAspectMask() {
        return aspectMask;
    }

    public int getLayerCount() {
        return layerCount;
    }

    public int getMipLevels() {
        return mipLevels;
    }

    public long getVkImage() {
        return vkImage;
    }

    public long getVkImageView() {
        return vkImageView;
    }

    public boolean isDepthImage() {
        return depthImage;
    }

    /**
     * 图像视图参数 Builder。
     * <p>
     * 默认值：
     * <ul>
     *   <li>baseArrayLayer: 0</li>
     *   <li>layerCount: 1</li>
     *   <li>mipLevels: 1</li>
     *   <li>viewType: VK_IMAGE_VIEW_TYPE_2D</li>
     * </ul>
     */
    public static class ImageViewData {
        private int aspectMask;
        private int baseArrayLayer;
        private int format;
        private int layerCount;
        private int mipLevels;
        private int viewType;

        public ImageViewData() {
            this.baseArrayLayer = 0;
            this.layerCount = 1;
            this.mipLevels = 1;
            this.viewType = VK_IMAGE_VIEW_TYPE_2D;
        }

        public ImageViewData aspectMask(int aspectMask) {
            this.aspectMask = aspectMask;
            return this;
        }

        public ImageViewData baseArrayLayer(int baseArrayLayer) {
            this.baseArrayLayer = baseArrayLayer;
            return this;
        }

        public ImageViewData format(int format) {
            this.format = format;
            return this;
        }

        public ImageViewData layerCount(int layerCount) {
            this.layerCount = layerCount;
            return this;
        }

        public ImageViewData mipLevels(int mipLevels) {
            this.mipLevels = mipLevels;
            return this;
        }

        public ImageViewData viewType(int viewType) {
            this.viewType = viewType;
            return this;
        }
    }
}
