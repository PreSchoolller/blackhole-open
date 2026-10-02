package io.github.preschoolller.blackhole.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.util.Locale;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;

/**
 * Vulkan 工具类 —— 静态方法集合，提供常用的 Vulkan 操作辅助。
 * <p>
 * 功能：
 * <ul>
 *   <li>Vulkan 错误码检查与转换</li>
 *   <li>图像内存屏障（Vulkan 1.3 同步2）</li>
 *   <li>内存类型查找</li>
 *   <li>缓冲区设备地址查询</li>
 *   <li>操作系统类型检测</li>
 * </ul>
 */
public class VkUtils {

    /** float 类型字节数 */
    public static final int FLOAT_SIZE = 4;
    /** int 类型字节数 */
    public static final int INT_SIZE = 4;
    /** 4×4 矩阵字节数 */
    public static final int MAT4X4_SIZE = 16 * FLOAT_SIZE;
    /** 最大同时飞行帧数（双缓冲） */
    public static final int MAX_IN_FLIGHT = 2;
    /** 指针大小（64 位系统） */
    public static final int PTR_SIZE = 8;
    /** vec2 字节数 */
    public static final int VEC2_SIZE = 2 * FLOAT_SIZE;
    /** vec3 字节数 */
    public static final int VEC3_SIZE = 3 * FLOAT_SIZE;
    /** vec4 字节数 */
    public static final int VEC4_SIZE = 4 * FLOAT_SIZE;

    private VkUtils() {
    }

    /**
     * 获取缓冲区的 GPU 设备地址。
     * 需要缓冲区创建时启用 VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT。
     *
     * @param vkCtx  Vulkan 上下文
     * @param buffer Vulkan 缓冲区句柄
     * @return GPU 设备地址
     */
    public static long getBufferAddress(VkCtx vkCtx, long buffer) {
        long address;
        try (var stack = MemoryStack.stackPush()) {
            address = vkGetBufferDeviceAddress(vkCtx.getDevice().getVkDevice(), VkBufferDeviceAddressInfo
                    .calloc(stack)
                    .sType$Default()
                    .buffer(buffer));
        }
        return address;
    }

    /**
     * 检测当前操作系统类型。
     *
     * @return 操作系统枚举值
     */
    public static OSType getOS() {
        OSType result;
        String os = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
        if ((os.contains("mac")) || (os.contains("darwin"))) {
            result = OSType.MACOS;
        } else if (os.contains("win")) {
            result = OSType.WINDOWS;
        } else if (os.contains("nux")) {
            result = OSType.LINUX;
        } else {
            result = OSType.OTHER;
        }

        return result;
    }

    /**
     * 插入图像内存屏障（使用 Vulkan 1.3 同步2）。
     * <p>
     * 用于在管线阶段之间同步图像访问，例如将图像布局从
     * UNDEFINED 转换为 COLOR_ATTACHMENT_OPTIMAL。
     *
     * @param stack       内存栈
     * @param cmdHandle   命令缓冲区
     * @param image       图像句柄
     * @param oldLayout   旧图像布局
     * @param newLayout   新图像布局
     * @param srcStage    源管线阶段
     * @param dstStage    目标管线阶段
     * @param srcAccess   源访问掩码
     * @param dstAccess   目标访问掩码
     * @param aspectMask  图像方面掩码（COLOR/DEPTH）
     */
    public static void imageBarrier(MemoryStack stack, VkCommandBuffer cmdHandle, long image, int oldLayout, int newLayout,
                                    long srcStage, long dstStage, long srcAccess, long dstAccess, int aspectMask) {
        imageBarrier(stack, cmdHandle, image, oldLayout, newLayout, srcStage, dstStage, srcAccess, dstAccess,
                aspectMask, 0, VK_REMAINING_MIP_LEVELS);
    }

    /**
     * 插入图像内存屏障（sync2，指定 mip 范围）—— 用于 mip 链的分段布局转换
     * （如基底层转 TRANSFER_SRC、高层转 TRANSFER_DST 后逐级 blit）。
     */
    public static void imageBarrier(MemoryStack stack, VkCommandBuffer cmdHandle, long image, int oldLayout, int newLayout,
                                    long srcStage, long dstStage, long srcAccess, long dstAccess, int aspectMask,
                                    int baseMipLevel, int levelCount) {
        var imageBarrier = VkImageMemoryBarrier2.calloc(1, stack)
                .sType$Default()
                .oldLayout(oldLayout)
                .newLayout(newLayout)
                .srcStageMask(srcStage)
                .dstStageMask(dstStage)
                .srcAccessMask(srcAccess)
                .dstAccessMask(dstAccess)
                .subresourceRange(it -> it
                        .aspectMask(aspectMask)
                        .baseMipLevel(baseMipLevel)
                        .levelCount(levelCount)
                        .baseArrayLayer(0)
                        .layerCount(VK_REMAINING_ARRAY_LAYERS))
                .image(image);

        VkDependencyInfo depInfo = VkDependencyInfo.calloc(stack)
                .sType$Default()
                .pImageMemoryBarriers(imageBarrier);

        vkCmdPipelineBarrier2(cmdHandle, depInfo);
    }

    /**
     * 根据内存类型位和属性要求查找合适的内存类型索引。
     * <p>
     * Vulkan 内存按类型组织，每种类型有特定属性（如设备本地、主机可见等）。
     * 此方法遍历所有内存类型，找到第一个同时满足类型位和属性要求的索引。
     *
     * @param vkCtx    Vulkan 上下文
     * @param typeBits 内存类型位掩码（由缓冲区/图像创建信息返回）
     * @param reqsMask 需要的内存属性标志
     * @return 内存类型索引
     * @throws RuntimeException 如果找不到匹配的内存类型
     */
    public static int memoryTypeFromProperties(VkCtx vkCtx, int typeBits, int reqsMask) {
        int result = -1;
        VkMemoryType.Buffer memoryTypes = vkCtx.getPhysDevice().getVkMemoryProperties().memoryTypes();
        for (int i = 0; i < VK_MAX_MEMORY_TYPES; i++) {
            if ((typeBits & 1) == 1 && (memoryTypes.get(i).propertyFlags() & reqsMask) == reqsMask) {
                result = i;
                break;
            }
            typeBits >>= 1;
        }
        if (result < 0) {
            throw new RuntimeException("Failed to find memoryType");
        }
        return result;
    }

    /**
     * 检查 Vulkan API 调用返回值。
     * 如果不是 VK_SUCCESS，将错误码转换为可读名称并抛出异常。
     *
     * @param err    Vulkan API 返回值
     * @param errMsg 错误描述信息
     * @throws RuntimeException 如果 err != VK_SUCCESS
     */
    public static void vkCheck(int err, String errMsg) {
        if (err != VK_SUCCESS) {
            String errCode = switch (err) {
                case VK_NOT_READY -> "VK_NOT_READY";
                case VK_TIMEOUT -> "VK_TIMEOUT";
                case VK_EVENT_SET -> "VK_EVENT_SET";
                case VK_EVENT_RESET -> "VK_EVENT_RESET";
                case VK_INCOMPLETE -> "VK_INCOMPLETE";
                case VK_ERROR_OUT_OF_HOST_MEMORY -> "VK_ERROR_OUT_OF_HOST_MEMORY";
                case VK_ERROR_OUT_OF_DEVICE_MEMORY -> "VK_ERROR_OUT_OF_DEVICE_MEMORY";
                case VK_ERROR_INITIALIZATION_FAILED -> "VK_ERROR_INITIALIZATION_FAILED";
                case VK_ERROR_DEVICE_LOST -> "VK_ERROR_DEVICE_LOST";
                case VK_ERROR_MEMORY_MAP_FAILED -> "VK_ERROR_MEMORY_MAP_FAILED";
                case VK_ERROR_LAYER_NOT_PRESENT -> "VK_ERROR_LAYER_NOT_PRESENT";
                case VK_ERROR_EXTENSION_NOT_PRESENT -> "VK_ERROR_EXTENSION_NOT_PRESENT";
                case VK_ERROR_FEATURE_NOT_PRESENT -> "VK_ERROR_FEATURE_NOT_PRESENT";
                case VK_ERROR_INCOMPATIBLE_DRIVER -> "VK_ERROR_INCOMPATIBLE_DRIVER";
                case VK_ERROR_TOO_MANY_OBJECTS -> "VK_ERROR_TOO_MANY_OBJECTS";
                case VK_ERROR_FORMAT_NOT_SUPPORTED -> "VK_ERROR_FORMAT_NOT_SUPPORTED";
                case VK_ERROR_FRAGMENTED_POOL -> "VK_ERROR_FRAGMENTED_POOL";
                case VK_ERROR_UNKNOWN -> "VK_ERROR_UNKNOWN";
                default -> "Not mapped";
            };
            throw new RuntimeException(errMsg + ": " + errCode + " [" + err + "]");
        }
    }

    /** 操作系统类型枚举 */
    public enum OSType {WINDOWS, MACOS, LINUX, OTHER}
}
