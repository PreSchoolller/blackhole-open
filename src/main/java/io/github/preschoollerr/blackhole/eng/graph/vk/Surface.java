package io.github.preschoollerr.blackhole.eng.graph.vk;

import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.tinylog.Logger;
import io.github.preschoollerr.blackhole.eng.wnd.Window;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK13.VK_FORMAT_B8G8R8A8_UNORM;
import static io.github.preschoollerr.blackhole.eng.graph.vk.VkUtils.vkCheck;

/**
 * Vulkan 窗口表面 —— 连接 Vulkan 渲染与 GLFW 窗口的桥接层。
 * <p>
 * 职责：
 * <ul>
 *   <li>通过 GLFW 创建窗口表面（{@code glfwCreateWindowSurface}）</li>
 *   <li>查询表面能力（最小/最大图像数量、当前变换等）</li>
 *   <li>选择最佳表面格式：优先 {@code B8G8R8A8_UNORM + SRGB_NONLINEAR}，否则使用第一个可用格式</li>
 * </ul>
 * <p>
 * Surface 在窗口大小变化时需要重建。
 */
public class Surface {

    /** 表面能力（图像数量范围、当前变换等） */
    private final VkSurfaceCapabilitiesKHR surfaceCaps;
    /** 表面格式（图像格式 + 色彩空间） */
    private final SurfaceFormat surfaceFormat;
    /** Vulkan 表面句柄 */
    private final long vkSurface;

    /**
     * 创建窗口表面并查询其属性。
     *
     * @param instance Vulkan 实例
     * @param physDevice 物理设备
     * @param window   GLFW 窗口
     */
    public Surface(Instance instance, PhysDevice physDevice, Window window) {
        Logger.debug("Creating Vulkan surface");
        try (var stack = MemoryStack.stackPush()) {
            LongBuffer pSurface = stack.mallocLong(1);
            // 通过 GLFW 创建 Vulkan 表面
            GLFWVulkan.glfwCreateWindowSurface(instance.getVkInstance(), window.getHandle(),
                    null, pSurface);
            vkSurface = pSurface.get(0);

            // 查询表面能力
            surfaceCaps = VkSurfaceCapabilitiesKHR.calloc();
            vkCheck(KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physDevice.getVkPhysicalDevice(),
                    vkSurface, surfaceCaps), "Failed to get surface capabilities");

            // 选择最佳表面格式
            surfaceFormat = calcSurfaceFormat(physDevice, vkSurface);
        }
    }

    /**
     * 选择最佳表面格式。
     * 优先选择 B8G8R8A8_UNORM + SRGB_NONLINEAR（标准 sRGB 色彩空间），
     * 否则使用第一个可用格式。
     */
    private static SurfaceFormat calcSurfaceFormat(PhysDevice physDevice, long vkSurface) {
        int imageFormat;
        int colorSpace;
        try (var stack = MemoryStack.stackPush()) {
            IntBuffer ip = stack.mallocInt(1);
            vkCheck(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physDevice.getVkPhysicalDevice(),
                    vkSurface, ip, null), "Failed to get the number surface formats");
            int numFormats = ip.get(0);
            if (numFormats <= 0) {
                throw new RuntimeException("No surface formats retrieved");
            }

            var surfaceFormats = VkSurfaceFormatKHR.calloc(numFormats, stack);
            vkCheck(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physDevice.getVkPhysicalDevice(),
                    vkSurface, ip, surfaceFormats), "Failed to get surface formats");

            // 默认使用第一个格式
            imageFormat = VK_FORMAT_B8G8R8A8_UNORM;
            colorSpace = surfaceFormats.get(0).colorSpace();
            // 查找最优格式
            for (int i = 0; i < numFormats; i++) {
                VkSurfaceFormatKHR surfaceFormatKHR = surfaceFormats.get(i);
                if (surfaceFormatKHR.format() == VK_FORMAT_B8G8R8A8_UNORM &&
                        surfaceFormatKHR.colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                    imageFormat = surfaceFormatKHR.format();
                    colorSpace = surfaceFormatKHR.colorSpace();
                    break;
                }
            }
        }
        return new SurfaceFormat(imageFormat, colorSpace);
    }

    /** 销毁表面 */
    public void cleanup(Instance instance) {
        Logger.debug("Destroying Vulkan surface");
        surfaceCaps.free();
        KHRSurface.vkDestroySurfaceKHR(instance.getVkInstance(), vkSurface, null);
    }

    public VkSurfaceCapabilitiesKHR getSurfaceCaps() {
        return surfaceCaps;
    }

    public SurfaceFormat getSurfaceFormat() {
        return surfaceFormat;
    }

    public long getVkSurface() {
        return vkSurface;
    }

    /**
     * 表面格式记录：图像格式 + 色彩空间。
     *
     * @param imageFormat Vulkan 图像格式（如 VK_FORMAT_B8G8R8A8_UNORM）
     * @param colorSpace  色彩空间（如 VK_COLOR_SPACE_SRGB_NONLINEAR_KHR）
     */
    public record SurfaceFormat(int imageFormat, int colorSpace) {
    }
}
