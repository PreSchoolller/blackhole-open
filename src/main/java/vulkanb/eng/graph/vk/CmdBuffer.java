package vulkanb.eng.graph.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;

import java.nio.IntBuffer;

import static org.lwjgl.vulkan.VK13.*;
import static vulkanb.eng.graph.vk.VkUtils.vkCheck;

/**
 * 命令缓冲区 —— 封装 {@link VkCommandBuffer}，用于录制和提交 GPU 命令。
 * <p>
 * 命令缓冲区有两种级别：
 * <ul>
 *   <li>主命令缓冲区（Primary）—— 可以直接提交到队列</li>
 *   <li>次级命令缓冲区（Secondary）—— 从主命令缓冲区调用（如 RenderPass 内）</li>
 * </ul>
 * <p>
 * 本项目仅使用主命令缓冲区，每帧录制一次全屏渲染命令。
 * <p>
 * 生命周期：分配 → beginRecording → 录制命令 → endRecording → submit → reset → 重复
 */
public class CmdBuffer {

    /** 是否为一次性提交（设置 VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT） */
    private final boolean oneTimeSubmit;
    /** 是否为主命令缓冲区 */
    private final boolean primary;
    /** Vulkan 命令缓冲区句柄 */
    private final VkCommandBuffer vkCommandBuffer;

    /**
     * 分配命令缓冲区。
     *
     * @param vkCtx         Vulkan 上下文
     * @param cmdPool       命令池（从此池分配）
     * @param primary       是否为主命令缓冲区
     * @param oneTimeSubmit 是否为一次性提交
     */
    public CmdBuffer(VkCtx vkCtx, CmdPool cmdPool, boolean primary, boolean oneTimeSubmit) {
        Logger.trace("Creating command buffer");
        this.primary = primary;
        this.oneTimeSubmit = oneTimeSubmit;
        VkDevice vkDevice = vkCtx.getDevice().getVkDevice();

        try (var stack = MemoryStack.stackPush()) {
            var cmdBufAllocateInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType$Default()
                    .commandPool(cmdPool.getVkCommandPool())
                    .level(primary ? VK_COMMAND_BUFFER_LEVEL_PRIMARY : VK_COMMAND_BUFFER_LEVEL_SECONDARY)
                    .commandBufferCount(1);
            PointerBuffer pb = stack.mallocPointer(1);
            vkCheck(vkAllocateCommandBuffers(vkDevice, cmdBufAllocateInfo, pb),
                    "Failed to allocate render command buffer");

            vkCommandBuffer = new VkCommandBuffer(pb.get(0), vkDevice);
        }
    }

    /**
     * 开始录制命令（主命令缓冲区，无继承信息）。
     */
    public void beginRecording() {
        beginRecording(null);
    }

    /**
     * 开始录制命令。
     * <p>
     * 对于主命令缓冲区：可选一次性提交标志。
     * 对于次级命令缓冲区：必须提供继承信息（颜色/深度格式等），
     * 以便驱动程序进行内部优化。
     *
     * @param inheritanceInfo 次级命令缓冲区的继承信息（主缓冲区传 null）
     */
    public void beginRecording(InheritanceInfo inheritanceInfo) {
        try (var stack = MemoryStack.stackPush()) {
            var cmdBufInfo = VkCommandBufferBeginInfo.calloc(stack).sType$Default();
            if (oneTimeSubmit) {
                cmdBufInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            }
            if (!primary) {
                if (inheritanceInfo == null) {
                    throw new RuntimeException("Secondary buffers must declare inheritance info");
                }
                int numColorFormats = inheritanceInfo.colorFormats.length;
                IntBuffer pColorFormats = stack.callocInt(inheritanceInfo.colorFormats.length);
                for (int i = 0; i < numColorFormats; i++) {
                    pColorFormats.put(0, inheritanceInfo.colorFormats[i]);
                }
                // 次级命令缓冲区的渲染继承信息（Vulkan 1.3 动态渲染兼容）
                var renderingInfo = VkCommandBufferInheritanceRenderingInfo.calloc(stack)
                        .sType$Default()
                        .depthAttachmentFormat(inheritanceInfo.depthFormat)
                        .pColorAttachmentFormats(pColorFormats)
                        .rasterizationSamples(inheritanceInfo.rasterizationSamples);
                var vkInheritanceInfo = VkCommandBufferInheritanceInfo.calloc(stack)
                        .sType$Default()
                        .pNext(renderingInfo);
                cmdBufInfo.pInheritanceInfo(vkInheritanceInfo);
            }
            vkCheck(vkBeginCommandBuffer(vkCommandBuffer, cmdBufInfo), "Failed to begin command buffer");
        }
    }

    /** 释放命令缓冲区 */
    public void cleanup(VkCtx vkCtx, CmdPool cmdPool) {
        Logger.trace("Destroying command buffer");
        vkFreeCommandBuffers(vkCtx.getDevice().getVkDevice(), cmdPool.getVkCommandPool(),
                vkCommandBuffer);
    }

    /** 结束录制命令 */
    public void endRecording() {
        vkCheck(vkEndCommandBuffer(vkCommandBuffer), "Failed to end command buffer");
    }

    public VkCommandBuffer getVkCommandBuffer() {
        return vkCommandBuffer;
    }

    /** 重置命令缓冲区（释放录制的命令，可重新录制） */
    public void reset() {
        vkResetCommandBuffer(vkCommandBuffer, VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT);
    }

    /**
     * 提交命令缓冲区并等待完成（阻塞）。
     * <p>
     * 用于一次性操作（如图像布局转换），内部创建临时栅栏并等待。
     *
     * @param vkCtx Vulkan 上下文
     * @param queue 目标队列
     */
    public void submitAndWait(VkCtx vkCtx, Queue queue) {
        Fence fence = new Fence(vkCtx, true);
        fence.reset(vkCtx);
        try (var stack = MemoryStack.stackPush()) {
            var cmds = VkCommandBufferSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .commandBuffer(vkCommandBuffer);
            queue.submit(cmds, null, null, fence);
        }
        fence.fenceWait(vkCtx);
        fence.cleanup(vkCtx);
    }

    /**
     * 次级命令缓冲区的继承信息。
     *
     * @param depthFormat           深度附件格式
     * @param colorFormats          颜色附件格式数组
     * @param rasterizationSamples  光栅化采样数
     */
    public record InheritanceInfo(int depthFormat, int[] colorFormats, int rasterizationSamples) {
    }
}
