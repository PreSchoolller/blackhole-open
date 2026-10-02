package io.github.preschoolller.blackhole.eng.graph;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import io.github.preschoolller.blackhole.eng.*;
import io.github.preschoolller.blackhole.eng.graph.gui.GuiRender;
import io.github.preschoolller.blackhole.eng.graph.vk.*;
import io.github.preschoolller.blackhole.eng.graph.vk.Queue;
import io.github.preschoolller.blackhole.eng.scene.Scene;
import io.github.preschoolller.blackhole.eng.wnd.Window;

import java.util.Arrays;

import static org.lwjgl.vulkan.VK13.*;
import static io.github.preschoolller.blackhole.utils.Constants.SHADERS_DIR;

/**
 * 顶层渲染管理器 —— 负责 Vulkan 帧同步、命令录制和呈现。
 * <p>
 * 每帧渲染流程：
 * <ol>
 *   <li>等待当前帧的栅栏（Fence）信号，确保 CPU 不会超前于 GPU</li>
 *   <li>重置命令池并开始录制命令缓冲区</li>
 *   <li>从交换链获取下一帧图像（acquire）</li>
 *   <li>将交换链图像布局从 UNDEFINED 转换为 COLOR_ATTACHMENT</li>
 *   <li>调用 {@link BlackHoleRender} 执行黑洞渲染</li>
 *   <li>结束录制并提交到图形队列</li>
 *   <li>呈现（present）到屏幕</li>
 * </ol>
 * <p>
 * 使用 {@link VkUtils#MAX_IN_FLIGHT}（=2）组命令缓冲区/栅栏/信号量实现双缓冲帧同步。
 */
public class Render {

    /** 顶点着色器源文件路径 */
    private static final String VERTEX_SHADER = SHADERS_DIR + "blackhole.vert";
    /** 片段着色器源文件路径 */
    private static final String FRAGMENT_SHADER = SHADERS_DIR + "blackhole.frag";
    /** Bloom 合成着色器源文件路径 */
    private static final String BLOOM_SHADER = SHADERS_DIR + "bloomComposite.frag";

    /** 每帧独立的命令缓冲区 */
    private final CmdBuffer[] cmdBuffers;
    /** 每帧独立的命令池 */
    private final CmdPool[] cmdPools;
    /** 每帧独立的栅栏，用于 CPU-GPU 同步 */
    private final Fence[] fences;
    /** 图形队列（提交渲染命令） */
    private final Queue.GraphicsQueue graphQueue;
    /** 呈现队列（提交图像到屏幕） */
    private final Queue.PresentQueue presentQueue;
    /** 图像获取完成信号量（每帧一个，由 acquire 发出） */
    private final Semaphore[] presCompleteSemaphs;
    /** 渲染完成信号量（每张交换链图像一个，由渲染提交发出） */
    private final Semaphore[] renderCompleteSemphs;
    /** Vulkan 核心上下文 */
    private final VkCtx vkCtx;
    /** 黑洞渲染器（史瓦西模式 lazy 持有；切克尔系模式时置空） */
    private BlackHoleRender blackHoleRender;
    /** 克尔渲染器（移植版；KERR 模式持有） */
    private KerrRender kerrRender;
    /** NPGS 原版 shader 复刻渲染器（KERR_NPGS 模式持有；GUI 单选热切换，与 Kerr 系共用 KerrParams） */
    private NpgsRender npgsRender;
    /** 当前激活的时空模式（与 Scene 状态每帧比对，变化时热切换渲染器） */
    private Scene.SpacetimeMode activeSpacetime;
    /** GUI 渲染器（ImGui 叠加层，最后绘制） */
    private final GuiRender guiRender;
    /** 窗口大小是否发生变化，触发重建 */
    private boolean resize;

    /**
     * 构造渲染器并初始化所有 Vulkan 同步对象。
     *
     * @param engCtx 引擎上下文
     */
    public Render(EngCtx engCtx) {
        // 创建 Vulkan 核心上下文（Instance → PhysDevice → Device → Surface → SwapChain ...）
        vkCtx = new VkCtx(engCtx.window());

        // 获取图形队列和呈现队列（可能在不同队列族）
        graphQueue = new Queue.GraphicsQueue(vkCtx, 0);
        presentQueue = new Queue.PresentQueue(vkCtx, 0);

        // 初始化双缓冲帧同步对象
        cmdPools = new CmdPool[VkUtils.MAX_IN_FLIGHT];
        cmdBuffers = new CmdBuffer[VkUtils.MAX_IN_FLIGHT];
        fences = new Fence[VkUtils.MAX_IN_FLIGHT];
        presCompleteSemaphs = new Semaphore[VkUtils.MAX_IN_FLIGHT];
        int numSwapChainImages = vkCtx.getSwapChain().getNumImages();
        renderCompleteSemphs = new Semaphore[numSwapChainImages];
        for (int i = 0; i < VkUtils.MAX_IN_FLIGHT; i++) {
            // 命令池支持重置（RESET_COMMAND_BUFFER），命令缓冲区为主命令缓冲区且一次性提交
            cmdPools[i] = new CmdPool(vkCtx, graphQueue.getQueueFamilyIndex(), false);
            cmdBuffers[i] = new CmdBuffer(vkCtx, cmdPools[i], true, true);
            // 初始状态为已信号（signaled），确保第一帧不会无限等待
            fences[i] = new Fence(vkCtx, true);
            presCompleteSemaphs[i] = new Semaphore(vkCtx);
        }
        // 渲染完成信号量按交换链图像数量分配（每个图像一个）
        for (int i = 0; i < numSwapChainImages; i++) {
            renderCompleteSemphs[i] = new Semaphore(vkCtx);
        }
        resize = false;

        // 按当前时空模式创建渲染器（默认史瓦西；克尔系 lazy）
        activeSpacetime = engCtx.scene().getSpacetime();
        blackHoleRender = isKerrFamily(activeSpacetime) ? null : new BlackHoleRender(vkCtx);

        // 创建 GUI 渲染器（ImGui 上下文在 init() 中创建，早于首帧 input()）
        guiRender = new GuiRender();
    }

    /** 释放所有渲染资源 */
    public void cleanup() {
        // 等待 GPU 空闲，确保所有资源不再被使用
        vkCtx.getDevice().waitIdle();

        if (blackHoleRender != null) {
            blackHoleRender.cleanup(vkCtx);
        }
        if (kerrRender != null) {
            kerrRender.cleanup(vkCtx);
        }
        if (npgsRender != null) {
            npgsRender.cleanup(vkCtx);
        }
        guiRender.cleanup(vkCtx);

        Arrays.asList(renderCompleteSemphs).forEach(i -> i.cleanup(vkCtx));
        Arrays.asList(presCompleteSemaphs).forEach(i -> i.cleanup(vkCtx));
        Arrays.asList(fences).forEach(i -> i.cleanup(vkCtx));
        for (int i = 0; i < cmdPools.length; i++) {
            cmdBuffers[i].cleanup(vkCtx, cmdPools[i]);
            cmdPools[i].cleanup(vkCtx);
        }

        vkCtx.cleanup();
    }

    /** 初始化史瓦西渲染管线（编译三个着色器并构建管线） */
    private void initSchwarzschild(VkCtx vkCtx) {
        String vertSpv = ShaderCompiler.compileShaderIfChanged(VERTEX_SHADER, VK_SHADER_STAGE_VERTEX_BIT);
        String fragSpv = ShaderCompiler.compileShaderIfChanged(FRAGMENT_SHADER, VK_SHADER_STAGE_FRAGMENT_BIT);
        String bloomSpv = ShaderCompiler.compileShaderIfChanged(BLOOM_SHADER, VK_SHADER_STAGE_FRAGMENT_BIT);
        blackHoleRender.init(vkCtx, vertSpv, fragSpv, bloomSpv);
    }

    /** 克尔系模式（移植版与 NPGS 原版复刻共用 KerrParams/测地相机/GUI 数据源） */
    private static boolean isKerrFamily(Scene.SpacetimeMode mode) {
        return mode == Scene.SpacetimeMode.KERR || mode == Scene.SpacetimeMode.KERR_NPGS;
    }

    /**
     * 初始化渲染管线（按当前时空模式分派）。
     */
    public void init(EngCtx engCtx, int width, int height) {
        activeSpacetime = engCtx.scene().getSpacetime();
        if (isKerrFamily(activeSpacetime)) {
            initKerr(engCtx);
        } else {
            initSchwarzschild(vkCtx);
        }

        // 初始化 GUI（ImGui 上下文、字体纹理、管线；格式取交换链实际格式）
        guiRender.init(vkCtx, engCtx, vkCtx.getSwapChain().getImageFormat());
        // 管线全部建完即落盘缓存:改着色器后那次昂贵的首启立即变得可复用
        vkCtx.getPipelineCache().save(vkCtx.getDevice());
    }

    /** 热切换时空模式：等待 GPU 空闲 → 释放旧渲染器 → 创建新渲染器 */
    private void switchSpacetime(EngCtx engCtx, Scene.SpacetimeMode wanted) {
        vkCtx.getDevice().waitIdle();
        if (activeSpacetime == Scene.SpacetimeMode.KERR || activeSpacetime == Scene.SpacetimeMode.KERR_NPGS) {
            cleanupKerr();
        } else {
            blackHoleRender.cleanup(vkCtx);
            blackHoleRender = null;
        }
        activeSpacetime = wanted;
        if (wanted == Scene.SpacetimeMode.KERR || wanted == Scene.SpacetimeMode.KERR_NPGS) {
            initKerr(engCtx);
        } else {
            blackHoleRender = new BlackHoleRender(vkCtx);
            initSchwarzschild(vkCtx);
        }
    }

    /** 克尔系渲染器创建：KERR=移植版 KerrRender，KERR_NPGS=NPGS 原版 shader 复刻渲染器 */
    private void initKerr(EngCtx engCtx) {
        if (activeSpacetime == Scene.SpacetimeMode.KERR_NPGS) {
            npgsRender = new NpgsRender();
            npgsRender.init(vkCtx, engCtx);
        } else {
            kerrRender = new KerrRender();
            kerrRender.init(vkCtx, engCtx);
        }
    }

    /** 克尔系渲染器释放（与 initKerr 的模式分派对应） */
    private void cleanupKerr() {
        if (npgsRender != null) {
            npgsRender.cleanup(vkCtx);
            npgsRender = null;
        } else if (kerrRender != null) {
            kerrRender.cleanup(vkCtx);
            kerrRender = null;
        }
    }

    /** 开始录制命令：重置命令池并 beginRecording */
    private void recordingStart(CmdPool cmdPool, CmdBuffer cmdBuffer) {
        cmdPool.reset(vkCtx);
        cmdBuffer.beginRecording();
    }

    /** 结束录制命令 */
    private void recordingStop(CmdBuffer cmdBuffer) {
        cmdBuffer.endRecording();
    }

    /**
     * 执行一帧渲染。
     * <p>
     * 流程：等待栅栏 → 录制命令 → 获取交换链图像 → 图像布局转换 →
     * 黑洞渲染 → 结束录制 → 提交队列 → 呈现
     *
     * @param engCtx           引擎上下文
     * @param currentRenderFrame 当前帧索引（0 或 1）
     */
    public void render(EngCtx engCtx, int currentRenderFrame) {
        SwapChain swapChain = vkCtx.getSwapChain();

        // 等待当前帧对应的 GPU 操作完成
        waitForFence(currentRenderFrame);

        var cmdPool = cmdPools[currentRenderFrame];
        var cmdBuffer = cmdBuffers[currentRenderFrame];

        recordingStart(cmdPool, cmdBuffer);

        // 从交换链获取下一帧图像
        int imageIndex;
        if (resize || (imageIndex = swapChain.acquireNextImage(vkCtx.getDevice(), presCompleteSemaphs[currentRenderFrame])) < 0) {
            // 窗口大小变化或交换链过期，触发重建
            resize(engCtx);

            // 重新获取下一帧图像（重建后需要再次 acquire）
            if ((imageIndex = swapChain.acquireNextImage(vkCtx.getDevice(), presCompleteSemaphs[currentRenderFrame])) < 0) {
                recordingStop(cmdBuffer);
                return;
            }
        }

        // 将交换链图像从 UNDEFINED 转换为 COLOR_ATTACHMENT_OPTIMAL
        // 使用 Vulkan 1.3 的同步2（VkImageMemoryBarrier2 + vkCmdPipelineBarrier2）
        try (var stack = MemoryStack.stackPush()) {
            VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();
            long swapChainImage = swapChain.getImageView(imageIndex).getVkImage();
            VkUtils.imageBarrier(stack, cmdHandle, swapChainImage,
                    VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    VK_PIPELINE_STAGE_2_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_NONE, VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_ASPECT_COLOR_BIT);
        }

        // 时空模式热切换（GUI 写 Scene 状态，帧首检查）
        var wanted = engCtx.scene().getSpacetime();
        if (wanted != activeSpacetime) {
            switchSpacetime(engCtx, wanted);
        }

        // 执行全屏渲染（使用动态渲染，无需 RenderPass/Framebuffer）
        if (activeSpacetime == Scene.SpacetimeMode.KERR_NPGS) {
            npgsRender.render(vkCtx, cmdBuffer, engCtx, currentRenderFrame, imageIndex);
        } else if (activeSpacetime == Scene.SpacetimeMode.KERR) {
            kerrRender.render(vkCtx, cmdBuffer, engCtx, currentRenderFrame, imageIndex);
        } else {
            blackHoleRender.render(vkCtx, cmdBuffer, engCtx, currentRenderFrame, imageIndex);
        }

        // GUI 叠加层（直接写交换链图像，Bloom 之后）
        guiRender.render(vkCtx, cmdBuffer, currentRenderFrame, imageIndex);

        recordingStop(cmdBuffer);

        // 提交命令缓冲区到图形队列
        submit(cmdBuffer, currentRenderFrame, imageIndex);

        // 呈现到屏幕，如果返回 true 表示交换链需要重建
        resize = swapChain.presentImage(presentQueue, renderCompleteSemphs[imageIndex], imageIndex);
    }

    /**
     * 提交命令缓冲区到图形队列。
     * <p>
     * 使用 Vulkan 1.3 的 VkSubmitInfo2 提交方式：
     * - 等待 presCompleteSemphs（图像可用信号）
     * - 发出 renderCompleteSemphs（渲染完成信号）
     * - 通过 Fence 通知 CPU 渲染完成
     *
     * @param cmdBuff     已录制完成的命令缓冲区
     * @param currentFrame 当前帧索引
     * @param imageIndex   交换链图像索引
     */
    private void submit(CmdBuffer cmdBuff, int currentFrame, int imageIndex) {
        try (var stack = MemoryStack.stackPush()) {
            var fence = fences[currentFrame];
            fence.reset(vkCtx);

            // 命令缓冲区提交信息
            var cmds = VkCommandBufferSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .commandBuffer(cmdBuff.getVkCommandBuffer());

            // 等待信号量：图像获取完成
            VkSemaphoreSubmitInfo.Buffer waitSemphs = VkSemaphoreSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .stageMask(VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .semaphore(presCompleteSemaphs[currentFrame].getVkSemaphore());

            // 发出信号量：渲染完成
            VkSemaphoreSubmitInfo.Buffer signalSemphs = VkSemaphoreSubmitInfo.calloc(1, stack)
                    .sType$Default()
                    .stageMask(VK_PIPELINE_STAGE_2_BOTTOM_OF_PIPE_BIT)
                    .semaphore(renderCompleteSemphs[imageIndex].getVkSemaphore());

            graphQueue.submit(cmds, waitSemphs, signalSemphs, fence);
        }
    }

    /** 等待指定帧的栅栏信号（阻塞直到 GPU 完成该帧的所有操作） */
    private void waitForFence(int currentFrame) {
        var fence = fences[currentFrame];
        fence.fenceWait(vkCtx);
    }

    /**
     * 处理窗口大小变化：重建 Surface、SwapChain，并更新投影矩阵和渲染器。
     *
     * @param engCtx 引擎上下文
     */
    public void resize(EngCtx engCtx) {
        Window window = engCtx.window();
        if (window.getWidth() == 0 && window.getHeight() == 0) {
            return; // 窗口最小化时跳过
        }

        vkCtx.getDevice().waitIdle();

        // 重建 Surface 和 SwapChain
        vkCtx.resize(window);

        // 重建所有信号量（旧的依赖旧的交换链）
        Arrays.asList(renderCompleteSemphs).forEach(i -> i.cleanup(vkCtx));
        Arrays.asList(presCompleteSemaphs).forEach(i -> i.cleanup(vkCtx));
        for (int i = 0; i < VkUtils.MAX_IN_FLIGHT; i++) {
            presCompleteSemaphs[i] = new Semaphore(vkCtx);
        }
        for (int i = 0; i < vkCtx.getSwapChain().getNumImages(); i++) {
            renderCompleteSemphs[i] = new Semaphore(vkCtx);
        }

        // 更新投影矩阵和渲染器的视口
        VkExtent2D extent = vkCtx.getSwapChain().getSwapChainExtent();
        engCtx.scene().getProjection().resize(extent.width(), extent.height());
        if (activeSpacetime == Scene.SpacetimeMode.KERR_NPGS) {
            npgsRender.resize(vkCtx, extent.width(), extent.height());
        } else if (activeSpacetime == Scene.SpacetimeMode.KERR) {
            kerrRender.resize(vkCtx, extent.width(), extent.height());
        } else {
            blackHoleRender.resize(vkCtx, extent.width(), extent.height());
        }
        guiRender.resize(vkCtx);
    }
}
