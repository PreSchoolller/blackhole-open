package io.github.preschoollerr.blackhole.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.tinylog.Logger;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK13.*;
import static io.github.preschoollerr.blackhole.eng.graph.vk.VkUtils.vkCheck;

/**
 * 命令池 —— 管理命令缓冲区的分配和重置。
 * <p>
 * 命令池绑定到特定的队列族，所有从该池分配的命令缓冲区
 * 只能提交到同一队列族的队列。
 * <p>
 * 本项目每帧使用一个命令池，每帧开始时重置（{@link #reset}），
 * 确保命令缓冲区可以被重新录制。
 */
public class CmdPool {
    /** 命令池句柄 */
    private final long vkCommandPool;

    /**
     * 创建命令池。
     *
     * @param vkCtx            Vulkan 上下文
     * @param queueFamilyIndex 绑定的队列族索引
     * @param supportReset     是否支持独立重置命令缓冲区
     */
    public CmdPool(VkCtx vkCtx, int queueFamilyIndex, boolean supportReset) {
        Logger.debug("Creating Vulkan command pool");

        try (var stack = MemoryStack.stackPush()) {
            var cmdPoolInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .queueFamilyIndex(queueFamilyIndex);
            if (supportReset) {
                cmdPoolInfo.flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
            }

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateCommandPool(vkCtx.getDevice().getVkDevice(), cmdPoolInfo, null, lp),
                    "Failed to create command pool");

            vkCommandPool = lp.get(0);
        }
    }

    /** 销毁命令池 */
    public void cleanup(VkCtx vkCtx) {
        Logger.debug("Destroying Vulkan command pool");
        vkDestroyCommandPool(vkCtx.getDevice().getVkDevice(), vkCommandPool, null);
    }

    public long getVkCommandPool() {
        return vkCommandPool;
    }

    /** 重置命令池：释放所有已分配命令缓冲区的内存（可重新录制） */
    public void reset(VkCtx vkCtx) {
        vkResetCommandPool(vkCtx.getDevice().getVkDevice(), vkCommandPool, 0);
    }
}
