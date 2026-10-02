package io.github.preschoolller.blackhole.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;
import io.github.preschoolller.blackhole.eng.wnd.Window;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;

import static org.lwjgl.vulkan.VK13.*;
import static io.github.preschoolller.blackhole.eng.graph.vk.VkUtils.vkCheck;

/**
 * 交换链 —— 管理双缓冲/三缓冲的屏幕呈现。
 * <p>
 * 职责：
 * <ul>
 *   <li>创建交换链（指定图像数量、格式、呈现模式等）</li>
 *   <li>为每张交换链图像创建 {@link ImageView}</li>
 *   <li>获取下一帧图像（{@link #acquireNextImage}）</li>
 *   <li>呈现图像到屏幕（{@link #presentImage}）</li>
 * </ul>
 * <p>
 * 呈现模式：
 * <ul>
 *   <li>VSync 开启：{@code VK_PRESENT_MODE_FIFO_KHR}（垂直同步，帧率锁定到显示器刷新率）</li>
 *   <li>VSync 关闭：{@code VK_PRESENT_MODE_IMMEDIATE_KHR}（不等待，可能撕裂）</li>
 * </ul>
 */
public class SwapChain {

    /** 交换链图像的视图数组 */
    private final ImageView[] imageViews;
    /** 交换链图像数量 */
    private final int numImages;
    /** 交换链图像尺寸 */
    private final VkExtent2D swapChainExtent;
    /** 交换链图像格式（供 GUI 等后续 pass 的管线声明使用，须与附件实际格式一致） */
    private final int imageFormat;
    /** 交换链句柄 */
    private final long vkSwapChain;

    /**
     * 创建交换链。
     *
     * @param window          GLFW 窗口
     * @param device          逻辑设备
     * @param surface         窗口表面
     * @param requestedImages 期望的图像数量（如 3 表示三重缓冲）
     * @param vsync           是否启用垂直同步
     */
    public SwapChain(Window window, Device device, Surface surface, int requestedImages, boolean vsync) {
        Logger.debug("Creating Vulkan SwapChain");
        try (var stack = MemoryStack.stackPush()) {
            VkSurfaceCapabilitiesKHR surfaceCaps = surface.getSurfaceCaps();

            // 计算实际图像数量（在 min 和 max 之间取 requestedImages）
            int reqImages = calcNumImages(surfaceCaps, requestedImages);
            // 计算交换链尺寸
            swapChainExtent = calcSwapChainExtent(window, surfaceCaps);

            Surface.SurfaceFormat surfaceFormat = surface.getSurfaceFormat();
            imageFormat = surfaceFormat.imageFormat();
            var vkSwapchainCreateInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                    .sType$Default()
                    .surface(surface.getVkSurface())
                    .minImageCount(reqImages)
                    .imageFormat(surfaceFormat.imageFormat())
                    .imageColorSpace(surfaceFormat.colorSpace())
                    .imageExtent(swapChainExtent)
                    .imageArrayLayers(1)                     // 非立体渲染
                    .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)  // 用作颜色附件
                    .preTransform(surfaceCaps.currentTransform())      // 不做额外变换
                    .compositeAlpha(KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)  // 不透明混合
                    .clipped(true);  // 允许驱动丢弃被其他窗口遮挡的像素

            // 设置呈现模式
            if (vsync) {
                vkSwapchainCreateInfo.presentMode(KHRSurface.VK_PRESENT_MODE_FIFO_KHR);
            } else {
                vkSwapchainCreateInfo.presentMode(KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR);
            }

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(KHRSwapchain.vkCreateSwapchainKHR(device.getVkDevice(), vkSwapchainCreateInfo, null, lp),
                    "Failed to create swap chain");
            vkSwapChain = lp.get(0);

            // 为每张交换链图像创建 ImageView
            imageViews = createImageViews(stack, device, vkSwapChain, surfaceFormat.imageFormat());
            numImages = imageViews.length;
        }
    }

    /**
     * 计算交换链图像数量：确保在 [minImageCount, maxImageCount] 范围内。
     * maxImageCount 为 0 表示无上限。
     */
    private static int calcNumImages(VkSurfaceCapabilitiesKHR surfCapabilities, int requestedImages) {
        int maxImages = surfCapabilities.maxImageCount();
        int minImages = surfCapabilities.minImageCount();
        int result = minImages;
        if (maxImages != 0) {
            result = Math.min(requestedImages, maxImages);
        }
        result = Math.max(result, minImages);
        Logger.debug("Requested [{}] images, got [{}] images. Surface capabilities, maxImages: [{}], minImages [{}]",
                requestedImages, result, maxImages, minImages);

        return result;
    }

    /**
     * 计算交换链尺寸。
     * 如果表面当前尺寸为 0xFFFFFFFF（未定义），则使用窗口尺寸并钳制到允许范围。
     * 否则使用表面的当前尺寸。
     */
    private static VkExtent2D calcSwapChainExtent(Window window, VkSurfaceCapabilitiesKHR surfCapabilities) {
        var result = VkExtent2D.calloc();
        if (surfCapabilities.currentExtent().width() == 0xFFFFFFFF) {
            // 表面尺寸未定义，使用窗口尺寸并钳制到允许范围
            int width = Math.min(window.getWidth(), surfCapabilities.maxImageExtent().width());
            width = Math.max(width, surfCapabilities.minImageExtent().width());

            int height = Math.min(window.getHeight(), surfCapabilities.maxImageExtent().height());
            height = Math.max(height, surfCapabilities.minImageExtent().height());

            result.width(width);
            result.height(height);
        } else {
            // 表面尺寸已定义，直接使用
            result.set(surfCapabilities.currentExtent());
        }
        return result;
    }

    /**
     * 为交换链的每张图像创建 ImageView。
     */
    private static ImageView[] createImageViews(MemoryStack stack, Device device, long swapChain, int format) {
        IntBuffer ip = stack.mallocInt(1);
        vkCheck(KHRSwapchain.vkGetSwapchainImagesKHR(device.getVkDevice(), swapChain, ip, null),
                "Failed to get number of surface images");
        int numImages = ip.get(0);

        LongBuffer swapChainImages = stack.mallocLong(numImages);
        vkCheck(KHRSwapchain.vkGetSwapchainImagesKHR(device.getVkDevice(), swapChain, ip, swapChainImages),
                "Failed to get surface images");

        var result = new ImageView[numImages];
        var imageViewData = new ImageView.ImageViewData().format(format).aspectMask(VK_IMAGE_ASPECT_COLOR_BIT);
        for (int i = 0; i < numImages; i++) {
            result[i] = new ImageView(device, swapChainImages.get(i), imageViewData, false);
        }

        return result;
    }

    /**
     * 获取交换链中的下一帧图像。
     *
     * @param device       逻辑设备
     * @param imageAqSem   图像获取信号量（获取完成后发出信号）
     * @return 图像索引，如果交换链过期则返回 -1
     */
    public int acquireNextImage(Device device, Semaphore imageAqSem) {
        int imageIndex;
        try (var stack = MemoryStack.stackPush()) {
            IntBuffer ip = stack.mallocInt(1);
            int err = KHRSwapchain.vkAcquireNextImageKHR(device.getVkDevice(), vkSwapChain, ~0L,
                    imageAqSem.getVkSemaphore(), MemoryUtil.NULL, ip);
            if (err == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
                return -1;  // 交换链过期，需要重建
            } else if (err == KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                // 次优但可继续使用
            } else if (err != VK_SUCCESS) {
                throw new RuntimeException("Failed to acquire image: " + err);
            }
            imageIndex = ip.get(0);
        }

        return imageIndex;
    }

    /** 销毁交换链和所有图像视图 */
    public void cleanup(Device device) {
        Logger.debug("Destroying Vulkan SwapChain");
        swapChainExtent.free();
        Arrays.asList(imageViews).forEach(i -> i.cleanup(device));
        KHRSwapchain.vkDestroySwapchainKHR(device.getVkDevice(), vkSwapChain, null);
    }

    public ImageView getImageView(int pos) {
        return imageViews[pos];
    }

    public int getNumImages() {
        return numImages;
    }

    /** 交换链图像格式（VK_FORMAT_* 常量） */
    public int getImageFormat() {
        return imageFormat;
    }

    public VkExtent2D getSwapChainExtent() {
        return swapChainExtent;
    }

    /**
     * 将渲染完成的图像呈现到屏幕。
     *
     * @param queue            呈现队列
     * @param renderCompleteSem 渲染完成信号量（等待渲染完成后才呈现）
     * @param imageIndex       要呈现的图像索引
     * @return true 表示交换链需要重建
     */
    public boolean presentImage(Queue queue, Semaphore renderCompleteSem, int imageIndex) {
        boolean resize = false;
        try (var stack = MemoryStack.stackPush()) {
            VkPresentInfoKHR present = VkPresentInfoKHR.calloc(stack)
                    .sType$Default()
                    .pWaitSemaphores(stack.longs(renderCompleteSem.getVkSemaphore()))
                    .swapchainCount(1)
                    .pSwapchains(stack.longs(vkSwapChain))
                    .pImageIndices(stack.ints(imageIndex));

            int err = KHRSwapchain.vkQueuePresentKHR(queue.getVkQueue(), present);
            if (err == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR) {
                resize = true;
            } else if (err == KHRSwapchain.VK_SUBOPTIMAL_KHR) {
                // 次优但可继续使用
            } else if (err != VK_SUCCESS) {
                throw new RuntimeException("Failed to present KHR: " + err);
            }
        }
        return resize;
    }
}
