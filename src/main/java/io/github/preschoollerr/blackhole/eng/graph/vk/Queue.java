package io.github.preschoollerr.blackhole.eng.graph.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;

import java.nio.IntBuffer;

import static org.lwjgl.vulkan.VK13.*;
import static io.github.preschoollerr.blackhole.eng.graph.vk.VkUtils.vkCheck;

/**
 * Vulkan 队列封装 —— 表示一个命令执行队列。
 * <p>
 * Vulkan 使用队列族（Queue Family）组织不同类型的命令执行能力：
 * <ul>
 *   <li>图形队列 —— 支持图形渲染命令</li>
 *   <li>计算队列 —— 支持计算着色器命令</li>
 *   <li>传输队列 —— 支持数据传输命令</li>
 *   <li>呈现队列 —— 支持图像呈现到屏幕</li>
 * </ul>
 * <p>
 * 本项目使用三个队列子类：
 * <ul>
 *   <li>{@link GraphicsQueue} —— 提交渲染命令</li>
 *   <li>{@link PresentQueue}  —— 提交图像到屏幕</li>
 *   <li>{@link ComputeQueue}  —— 计算队列（预留，当前未使用）</li>
 * </ul>
 */
public class Queue {

    /** 队列族索引 */
    private final int queueFamilyIndex;
    /** Vulkan 队列句柄 */
    private final VkQueue vkQueue;

    /**
     * 构造队列：从逻辑设备获取指定队列族和队列索引的队列。
     *
     * @param vkCtx           Vulkan 上下文
     * @param queueFamilyIndex 队列族索引
     * @param queueIndex      队列在族内的索引
     */
    public Queue(VkCtx vkCtx, int queueFamilyIndex, int queueIndex) {
        Logger.debug("Creating queue");

        this.queueFamilyIndex = queueFamilyIndex;
        try (var stack = MemoryStack.stackPush()) {
            PointerBuffer pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(vkCtx.getDevice().getVkDevice(), queueFamilyIndex, queueIndex, pQueue);
            long queue = pQueue.get(0);
            vkQueue = new VkQueue(queue, vkCtx.getDevice().getVkDevice());
        }
    }

    public int getQueueFamilyIndex() {
        return queueFamilyIndex;
    }

    public VkQueue getVkQueue() {
        return vkQueue;
    }

    /**
     * 提交命令到队列（使用 Vulkan 1.3 的 vkQueueSubmit2）。
     *
     * @param commandBuffers   命令缓冲区提交信息
     * @param waitSemaphores   等待信号量（可为 null）
     * @param signalSemaphores 发出信号量
     * @param fence            栅栏（可为 null，用于 CPU 端同步）
     */
    public void submit(VkCommandBufferSubmitInfo.Buffer commandBuffers, VkSemaphoreSubmitInfo.Buffer waitSemaphores,
                       VkSemaphoreSubmitInfo.Buffer signalSemaphores, Fence fence) {
        try (var stack = MemoryStack.stackPush()) {
            var submitInfo = VkSubmitInfo2.calloc(1, stack)
                    .sType$Default()
                    .pCommandBufferInfos(commandBuffers)
                    .pSignalSemaphoreInfos(signalSemaphores);
            if (waitSemaphores != null) {
                submitInfo.pWaitSemaphoreInfos(waitSemaphores);
            }
            long fenceHandle = fence != null ? fence.getVkFence() : VK_NULL_HANDLE;

            vkCheck(vkQueueSubmit2(vkQueue, submitInfo, fenceHandle), "Failed to submit command to queue");
        }
    }

    /** 等待队列空闲（阻塞直到所有待处理命令执行完成） */
    public void waitIdle() {
        vkQueueWaitIdle(vkQueue);
    }

    /**
     * 计算队列 —— 用于通用计算操作（当前项目中预留，未实际使用）。
     */
    public static class ComputeQueue extends Queue {

        public ComputeQueue(VkCtx vkCtx, int queueIndex) {
            super(vkCtx, getComputeQueueFamilyIndex(vkCtx), queueIndex);
        }

        /** 查找第一个支持计算操作的队列族索引 */
        private static int getComputeQueueFamilyIndex(VkCtx vkCtx) {
            int index = -1;
            var queuePropsBuff = vkCtx.getPhysDevice().getVkQueueFamilyProps();
            int numQueuesFamilies = queuePropsBuff.capacity();
            for (int i = 0; i < numQueuesFamilies; i++) {
                VkQueueFamilyProperties props = queuePropsBuff.get(i);
                boolean computeQueue = (props.queueFlags() & VK_QUEUE_COMPUTE_BIT) != 0;
                if (computeQueue) {
                    index = i;
                    break;
                }
            }

            if (index < 0) {
                throw new RuntimeException("Failed to get compute Queue family index");
            }
            return index;
        }
    }

    /**
     * 图形队列 —— 用于提交渲染命令。
     */
    public static class GraphicsQueue extends Queue {

        public GraphicsQueue(VkCtx vkCtx, int queueIndex) {
            super(vkCtx, getGraphicsQueueFamilyIndex(vkCtx), queueIndex);
        }

        /** 查找第一个支持图形操作的队列族索引 */
        private static int getGraphicsQueueFamilyIndex(VkCtx vkCtx) {
            int index = -1;
            var queuePropsBuff = vkCtx.getPhysDevice().getVkQueueFamilyProps();
            int numQueuesFamilies = queuePropsBuff.capacity();
            for (int i = 0; i < numQueuesFamilies; i++) {
                VkQueueFamilyProperties props = queuePropsBuff.get(i);
                boolean graphicsQueue = (props.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0;
                if (graphicsQueue) {
                    index = i;
                    break;
                }
            }

            if (index < 0) {
                throw new RuntimeException("Failed to get graphics Queue family index");
            }
            return index;
        }
    }

    /**
     * 呈现队列 —— 用于将渲染结果呈现到屏幕。
     */
    public static class PresentQueue extends Queue {

        public PresentQueue(VkCtx vkCtx, int queueIndex) {
            super(vkCtx, getPresentQueueFamilyIndex(vkCtx), queueIndex);
        }

        /**
         * 查找第一个支持呈现操作的队列族索引。
         * 呈现能力通过 vkGetPhysicalDeviceSurfaceSupportKHR 查询。
         */
        private static int getPresentQueueFamilyIndex(VkCtx vkCtx) {
            int index = -1;
            try (var stack = MemoryStack.stackPush()) {
                var queuePropsBuff = vkCtx.getPhysDevice().getVkQueueFamilyProps();
                int numQueuesFamilies = queuePropsBuff.capacity();
                IntBuffer intBuff = stack.mallocInt(1);
                for (int i = 0; i < numQueuesFamilies; i++) {
                    KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(vkCtx.getPhysDevice().getVkPhysicalDevice(),
                            i, vkCtx.getSurface().getVkSurface(), intBuff);
                    boolean supportsPresentation = intBuff.get(0) == VK_TRUE;
                    if (supportsPresentation) {
                        index = i;
                        break;
                    }
                }
            }

            if (index < 0) {
                throw new RuntimeException("Failed to get Presentation Queue family index");
            }
            return index;
        }
    }
}
