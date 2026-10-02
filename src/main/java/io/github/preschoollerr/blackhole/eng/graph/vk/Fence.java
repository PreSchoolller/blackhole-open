package io.github.preschoollerr.blackhole.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkFenceCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK13.*;
import static io.github.preschoollerr.blackhole.eng.graph.vk.VkUtils.vkCheck;

/**
 * 栅栏（Fence）—— CPU-GPU 同步原语。
 * <p>
 * 栅栏用于 CPU 端等待 GPU 完成特定操作。典型用法：
 * <ol>
 *   <li>提交命令时指定栅栏</li>
 *   <li>CPU 调用 {@link #fenceWait} 阻塞等待</li>
 *   <li>重置栅栏以便下一帧使用</li>
 * </ol>
 * <p>
 * 本项目每帧使用一个栅栏：
 * - 初始状态为 signaled（已信号），确保第一帧不会无限等待
 * - 每帧提交前重置，提交后在下一帧开始时等待
 */
public class Fence {

    /** 栅栏句柄 */
    private final long vkFence;

    /**
     * 创建栅栏。
     *
     * @param vkCtx   Vulkan 上下文
     * @param signaled 初始状态是否为已信号
     */
    public Fence(VkCtx vkCtx, boolean signaled) {
        try (var stack = MemoryStack.stackPush()) {
            var fenceCreateInfo = VkFenceCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(signaled ? VK_FENCE_CREATE_SIGNALED_BIT : 0);

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateFence(vkCtx.getDevice().getVkDevice(), fenceCreateInfo, null, lp), "Failed to create fence");
            vkFence = lp.get(0);
        }
    }

    /** 销毁栅栏 */
    public void cleanup(VkCtx vkCtx) {
        vkDestroyFence(vkCtx.getDevice().getVkDevice(), vkFence, null);
    }

    /**
     * 等待栅栏信号（阻塞直到 GPU 完成或超时）。
     * 超时设置为 Long.MAX_VALUE（无限等待）。
     * <p>
     * 返回值必须检查：vkWaitForFences 返回 DEVICE_LOST 时若吞掉，
     * 错误会延后到下一次队列提交才暴露，真实根因难以定位。
     */
    public void fenceWait(VkCtx vkCtx) {
        vkCheck(vkWaitForFences(vkCtx.getDevice().getVkDevice(), vkFence, true, Long.MAX_VALUE),
                "Failed to wait for fence");
    }

    public long getVkFence() {
        return vkFence;
    }

    /** 重置栅栏（清除信号状态，准备下一次等待） */
    public void reset(VkCtx vkCtx) {
        vkResetFences(vkCtx.getDevice().getVkDevice(), vkFence);
    }
}
