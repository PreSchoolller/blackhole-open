package vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK13.vkCreateSemaphore;
import static org.lwjgl.vulkan.VK13.vkDestroySemaphore;
import static vulkanb.eng.graph.vk.VkUtils.vkCheck;

/**
 * 信号量（Semaphore）—— GPU-GPU 同步原语。
 * <p>
 * 信号量用于在不同队列或不同提交之间同步 GPU 操作。
 * 与栅栏不同，信号量只能在 GPU 端等待，CPU 无法直接查询。
 * <p>
 * 本项目使用两种信号量：
 * <ul>
 *   <li>图像获取信号量 —— 交换链图像获取完成后发出，通知渲染提交可以开始</li>
 *   <li>渲染完成信号量 —— 渲染命令执行完成后发出，通知呈现操作可以开始</li>
 * </ul>
 */
public class Semaphore {

    /** 信号量句柄 */
    private final long vkSemaphore;

    /**
     * 创建信号量。
     *
     * @param vkCtx Vulkan 上下文
     */
    public Semaphore(VkCtx vkCtx) {
        try (var stack = MemoryStack.stackPush()) {
            var semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc(stack).sType$Default();

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateSemaphore(vkCtx.getDevice().getVkDevice(), semaphoreCreateInfo, null, lp),
                    "Failed to create semaphore");
            vkSemaphore = lp.get(0);
        }
    }

    /** 销毁信号量 */
    public void cleanup(VkCtx vkCtx) {
        vkDestroySemaphore(vkCtx.getDevice().getVkDevice(), vkSemaphore, null);
    }

    public long getVkSemaphore() {
        return vkSemaphore;
    }
}
