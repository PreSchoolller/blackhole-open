package vulkanb.eng.graph.gui;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImDrawData;
import imgui.ImVec4;
import imgui.type.ImInt;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import vulkanb.eng.EngCtx;
import vulkanb.eng.graph.vk.*;
import vulkanb.eng.wnd.Window;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.Objects;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;
import static vulkanb.eng.graph.vk.VkUtils.vkCheck;
import static vulkanb.utils.Constants.SHADERS_DIR;

/**
 * GUI 渲染器 —— 用自写 Vulkan 管线渲染 Dear ImGui draw data（移植自 vulkanbook appendix-01）。
 * <p>
 * imgui-java 仅作 UI 状态库 + draw data 生成器（不用其 glfw/vulkan 后端）：
 * <ul>
 *   <li>字体图集经 getTexDataAsRGBA32 取像素，staging + 一次性命令上传为 SRGB 纹理，
 *       描述符集句柄作为 texID 塞回 ImGui（ImDrawCmd.textureId 即 descSet 句柄）</li>
 *   <li>专用图形管线：Alpha 混合、push constant 仅 8 字节 scale=(2/w, -2/h)，
 *       顶点 pos(vec2)+uv(vec2)+color(R8G8B8A8) 共 20 字节，索引 UINT16</li>
 *   <li>动态渲染（VK1.3）直接叠加写交换链：loadOp=LOAD，位于 Bloom 合成 pass 之后；
 *       附件 imageView 随 imageIndex 每帧设置（本项目与 appendix-01 固定附件的差异点）</li>
 *   <li>每帧插槽（MAX_IN_FLIGHT）一组顶点/索引 HOST_VISIBLE 缓冲，按需增长、只增不缩</li>
 * </ul>
 * UI 帧构建（newFrame/面板/endFrame/render）由应用侧 Main.handleGui 按渲染帧驱动。
 */
public class GuiRender {

    /** 顶点着色器源文件路径 */
    private static final String VERTEX_SHADER = SHADERS_DIR + "gui_vtx.glsl";
    /** 片段着色器源文件路径 */
    private static final String FRAGMENT_SHADER = SHADERS_DIR + "gui_frg.glsl";
    /** 字体描述符集 ID */
    private static final String FONTS_DESC_ID = "gui-fonts";
    /** GUI 顶点字节数：pos(vec2) + uv(vec2) + color(R8G8B8A8) */
    private static final int VERTEX_SIZE = VkUtils.FLOAT_SIZE * 5;
    /** 字体纹理格式（sRGB，保证文字灰度直读） */
    private static final int FONT_FORMAT = VK_FORMAT_R8G8B8A8_SRGB;

    /** 每帧插槽的索引缓冲（按需增长） */
    private final VkBuffer[] buffsIdx = new VkBuffer[VkUtils.MAX_IN_FLIGHT];
    /** 每帧插槽的顶点缓冲（按需增长） */
    private final VkBuffer[] buffsVtx = new VkBuffer[VkUtils.MAX_IN_FLIGHT];
    /** 图形管线 */
    private Pipeline pipeline;
    /** 字体纹理描述符集布局（set 0，binding 0 = combined image sampler） */
    private DescSetLayout fontsDescLayout;
    /** 字体纹理描述符集 */
    private DescSet fontsDescSet;
    /** 字体纹理图像 */
    private Image fontsImage;
    /** 字体纹理视图 */
    private ImageView fontsView;
    /** 字体纹理采样器 */
    private long fontsSampler;
    /** 动态渲染信息（写交换链，imageView 每帧设置） */
    private VkRenderingInfo renderInfo;
    /** 颜色附件描述（loadOp=LOAD 叠加） */
    private VkRenderingAttachmentInfo.Buffer attInfoColor;

    /**
     * 初始化：创建 ImGui 上下文、上传字体纹理、创建管线并接线键盘回调。
     *
     * @param vkCtx       Vulkan 上下文
     * @param engCtx      引擎上下文（取窗口键盘输入注册回调）
     * @param colorFormat 交换链实际颜色格式（管线声明必须与附件一致，勿用硬编码常量）
     */
    public void init(VkCtx vkCtx, EngCtx engCtx, int colorFormat) {
        // 1. ImGui 上下文与显示尺寸
        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null);
        // ImGui 布局用窗口逻辑坐标（与 GLFW 光标同一坐标系，macOS 上点击才不会错位），
        // 像素密度交给 FramebufferScale（Retina=2），投影时再乘回去
        Window window = engCtx.window();
        float scale = window.getContentScale();
        io.setDisplaySize(window.getLogicalWidth(), window.getLogicalHeight());
        io.setDisplayFramebufferScale(scale, scale);

        // 2. 字体图集像素 → Vulkan 纹理（一次性命令提交）
        ImInt texWidth = new ImInt();
        ImInt texHeight = new ImInt();
        ByteBuffer pixels = io.getFonts().getTexDataAsRGBA32(texWidth, texHeight);
        var graphQueue = new Queue.GraphicsQueue(vkCtx, 0);
        uploadFontTexture(vkCtx, graphQueue, pixels, texWidth.get(), texHeight.get());

        // 3. 采样器 + 描述符集（绑定字体纹理），句柄作为 texID 塞回 ImGui
        fontsSampler = createSampler(vkCtx);
        fontsDescLayout = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(
                VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 0, 1, VK_SHADER_STAGE_FRAGMENT_BIT));
        fontsDescSet = vkCtx.getDescAllocator().addDescSet(vkCtx.getDevice(), FONTS_DESC_ID, fontsDescLayout);
        fontsDescSet.setImage(vkCtx.getDevice(), fontsSampler, fontsView.getVkImageView(),
                0, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        io.getFonts().setTexID(fontsDescSet.getVkDescriptorSet());

        // 4. 图形管线（Alpha 混合 + push constant scale）
        String vertSpv = ShaderCompiler.compileShaderIfChanged(VERTEX_SHADER, VK_SHADER_STAGE_VERTEX_BIT);
        String fragSpv = ShaderCompiler.compileShaderIfChanged(FRAGMENT_SHADER, VK_SHADER_STAGE_FRAGMENT_BIT);
        var vertModule = new ShaderModule(vkCtx, VK_SHADER_STAGE_VERTEX_BIT, vertSpv, null);
        var fragModule = new ShaderModule(vkCtx, VK_SHADER_STAGE_FRAGMENT_BIT, fragSpv, null);
        var vi = createVertexInput();
        var buildInfo = new PipelineBuildInfo(new ShaderModule[]{vertModule, fragModule}, vi,
                new int[]{colorFormat})
                .setPushConstRanges(new PushConstRange[]{
                        new PushConstRange(VK_SHADER_STAGE_VERTEX_BIT, 0, VkUtils.VEC2_SIZE)})
                .setDescSetLayouts(new DescSetLayout[]{fontsDescLayout})
                .setUseBlend(true);
        pipeline = new Pipeline(vkCtx, buildInfo);
        vi.pVertexBindingDescriptions().free();
        vi.pVertexAttributeDescriptions().free();
        vi.free();
        vertModule.cleanup(vkCtx);
        fragModule.cleanup(vkCtx);

        // 5. 动态渲染信息（renderArea 尺寸每帧从交换链刷新）
        attInfoColor = VkRenderingAttachmentInfo.calloc(1);
        renderInfo = VkRenderingInfo.calloc()
                .sType$Default()
                .layerCount(1)
                .pColorAttachments(attInfoColor);
        try (var stack = MemoryStack.stackPush()) {
            renderInfo.renderArea(VkRect2D.calloc(stack).extent(VkExtent2D.calloc(stack)));
        }

        // 6. 键盘回调转发（字符 + 按键）
        var ki = engCtx.window().getKeyboardInput();
        ki.setCharCallBack(new GuiUtils.CharCallBack());
        ki.addKeyCallBack(new GuiUtils.KeyCallback());
    }

    /** 字体纹理采样器（单张无 mip，线性 + CLAMP_TO_EDGE） */
    private static long createSampler(VkCtx vkCtx) {
        long sampler;
        try (var stack = MemoryStack.stackPush()) {
            var samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType$Default()
                    .magFilter(VK_FILTER_LINEAR)
                    .minFilter(VK_FILTER_LINEAR)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .maxLod(0.0f);
            long[] lp = new long[1];
            vkCheck(vkCreateSampler(vkCtx.getDevice().getVkDevice(), samplerInfo, null, lp),
                    "Failed to create GUI font sampler");
            sampler = lp[0];
        }
        return sampler;
    }

    /** GUI 顶点输入布局：binding 0 stride 20B，location 0/1/2 = pos/uv/color */
    private static VkPipelineVertexInputStateCreateInfo createVertexInput() {
        var attrs = VkVertexInputAttributeDescription.calloc(3);
        attrs.get(0).binding(0).location(0).format(VK_FORMAT_R32G32_SFLOAT).offset(0);
        attrs.get(1).binding(0).location(1).format(VK_FORMAT_R32G32_SFLOAT).offset(VkUtils.VEC2_SIZE);
        attrs.get(2).binding(0).location(2).format(VK_FORMAT_R8G8B8A8_UNORM).offset(VkUtils.VEC4_SIZE);
        var bindings = VkVertexInputBindingDescription.calloc(1);
        bindings.get(0).binding(0).stride(VERTEX_SIZE).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
        return VkPipelineVertexInputStateCreateInfo.calloc()
                .sType$Default()
                .pVertexBindingDescriptions(bindings)
                .pVertexAttributeDescriptions(attrs);
    }

    /**
     * 上传字体图集：staging 缓冲 → 图像（TRANSFER_DST|SAMPLED）→ SHADER_READ，
     * 一次性命令缓冲提交等待（照 CubeTexture 的上传流程，单张 2D 简化版）。
     */
    private void uploadFontTexture(VkCtx vkCtx, Queue graphQueue, ByteBuffer pixels, int width, int height) {
        long size = (long) width * height * 4L;
        var staging = new VkBuffer(vkCtx, size,
                VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VMA_MEMORY_USAGE_AUTO,
                VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, 0);
        long mapped = staging.map(vkCtx);
        MemoryUtil.memCopy(MemoryUtil.memAddress(pixels), mapped, size);
        staging.unMap(vkCtx);

        fontsImage = new Image(vkCtx, new Image.ImageData()
                .width(width).height(height)
                .format(FONT_FORMAT)
                .usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT));
        fontsView = new ImageView(vkCtx.getDevice(), fontsImage.getVkImage(),
                new ImageView.ImageViewData()
                        .format(FONT_FORMAT)
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT),
                false);

        var cmdPool = new CmdPool(vkCtx, graphQueue.getQueueFamilyIndex(), false);
        var cmdBuffer = new CmdBuffer(vkCtx, cmdPool, true, true);
        try (var stack = MemoryStack.stackPush()) {
            cmdBuffer.beginRecording();
            VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();

            // UNDEFINED → TRANSFER_DST
            var preBarrier = VkImageMemoryBarrier.calloc(1, stack)
                    .sType$Default()
                    .srcAccessMask(VK_ACCESS_NONE)
                    .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(fontsImage.getVkImage())
                    .subresourceRange(it -> it
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .baseMipLevel(0).levelCount(1)
                            .baseArrayLayer(0).layerCount(1));
            vkCmdPipelineBarrier(cmdHandle,
                    VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, null, preBarrier);

            var copy = VkBufferImageCopy.calloc(1, stack)
                    .bufferOffset(0)
                    .bufferRowLength(0)
                    .bufferImageHeight(0)
                    .imageSubresource(it -> it
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .mipLevel(0).baseArrayLayer(0).layerCount(1))
                    .imageOffset(it -> it.x(0).y(0).z(0))
                    .imageExtent(it -> it.width(width).height(height).depth(1));
            vkCmdCopyBufferToImage(cmdHandle, staging.getBuffer(), fontsImage.getVkImage(),
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, copy);

            // TRANSFER_DST → SHADER_READ
            var tailBarrier = VkImageMemoryBarrier.calloc(1, stack)
                    .sType$Default()
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(fontsImage.getVkImage())
                    .subresourceRange(it -> it
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .baseMipLevel(0).levelCount(1)
                            .baseArrayLayer(0).layerCount(1));
            vkCmdPipelineBarrier(cmdHandle,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                    0, null, null, tailBarrier);

            cmdBuffer.endRecording();
            cmdBuffer.submitAndWait(vkCtx, graphQueue);
        } finally {
            cmdBuffer.cleanup(vkCtx, cmdPool);
            cmdPool.cleanup(vkCtx);
        }
        staging.cleanup(vkCtx);
    }

    /** 释放所有 GUI 资源并销毁 ImGui 上下文（须晚于一切 ImGui 访问） */
    public void cleanup(VkCtx vkCtx) {
        if (fontsSampler != 0) {
            vkDestroySampler(vkCtx.getDevice().getVkDevice(), fontsSampler, null);
            fontsSampler = 0;
        }
        if (fontsView != null) {
            fontsView.cleanup(vkCtx.getDevice());
        }
        if (fontsImage != null) {
            fontsImage.cleanup(vkCtx);
        }
        vkCtx.getDescAllocator().freeDescSet(vkCtx.getDevice(), FONTS_DESC_ID);
        if (fontsDescLayout != null) {
            fontsDescLayout.cleanup(vkCtx);
        }
        if (pipeline != null) {
            pipeline.cleanup(vkCtx);
        }
        Arrays.stream(buffsVtx).filter(Objects::nonNull).forEach(b -> b.cleanup(vkCtx));
        Arrays.stream(buffsIdx).filter(Objects::nonNull).forEach(b -> b.cleanup(vkCtx));
        if (renderInfo != null) {
            renderInfo.free();
        }
        if (attInfoColor != null) {
            attInfoColor.free();
        }
        ImGui.destroyContext();
    }

    /**
     * 执行一帧 GUI 绘制：叠加写交换链图像（Bloom 合成之后调用）。
     *
     * @param vkCtx         Vulkan 上下文
     * @param cmdBuffer     已 begin 的命令缓冲区
     * @param currentFrame  当前帧插槽索引（0 或 1）
     * @param imageIndex    交换链图像索引
     */
    public void render(VkCtx vkCtx, CmdBuffer cmdBuffer, int currentFrame, int imageIndex) {
        updateBuffers(vkCtx, currentFrame);
        if (buffsVtx[currentFrame] == null) {
            return;
        }

        try (var stack = MemoryStack.stackPush()) {
            SwapChain swapChain = vkCtx.getSwapChain();
            VkExtent2D extent = swapChain.getSwapChainExtent();
            int width = extent.width();
            int height = extent.height();
            VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();

            // 附件 = 本帧交换链图像（随 imageIndex 变化，每帧设置）；叠加不清屏
            attInfoColor.get(0).sType$Default()
                    .imageView(swapChain.getImageView(imageIndex).getVkImageView())
                    .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            renderInfo.renderArea().extent().width(width).height(height);

            vkCmdBeginRendering(cmdHandle, renderInfo);

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getVkPipeline());

            // Y 翻转视口（与 BlackHoleRender 一致）
            var viewport = VkViewport.calloc(1, stack)
                    .x(0)
                    .y(height)
                    .height(-height)
                    .width(width)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            vkCmdSetViewport(cmdHandle, 0, viewport);

            LongBuffer vtxBuffer = stack.mallocLong(1);
            vtxBuffer.put(0, buffsVtx[currentFrame].getBuffer());
            LongBuffer offsets = stack.mallocLong(1);
            offsets.put(0, 0L);
            vkCmdBindVertexBuffers(cmdHandle, 0, vtxBuffer, offsets);
            vkCmdBindIndexBuffer(cmdHandle, buffsIdx[currentFrame].getBuffer(), 0, VK_INDEX_TYPE_UINT16);

            // push constant：NDC 缩放（ImGui 逻辑坐标 × 帧缓冲缩放 → 像素 → [-1,1]）
            ImGuiIO io = ImGui.getIO();
            FloatBuffer pushConstantBuffer = stack.mallocFloat(2);
            pushConstantBuffer.put(0, 2.0f / (io.getDisplaySizeX() * io.getDisplayFramebufferScaleX()));
            pushConstantBuffer.put(1, -2.0f / (io.getDisplaySizeY() * io.getDisplayFramebufferScaleY()));
            vkCmdPushConstants(cmdHandle, pipeline.getVkPipelineLayout(),
                    VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstantBuffer);

            // 遍历 draw data：逐 cmd 绑字体 descSet（textureId 即句柄）、按 clipRect 设 scissor、绘制
            LongBuffer descriptorSets = stack.mallocLong(1);
            ImVec4 clipRect = new ImVec4();
            VkRect2D.Buffer rect = VkRect2D.calloc(1, stack);
            ImDrawData imDrawData = ImGui.getDrawData();
            int numCmdLists = imDrawData.getCmdListsCount();
            int offsetIdx = 0;
            int offsetVtx = 0;
            for (int i = 0; i < numCmdLists; i++) {
                int cmdBufferSize = imDrawData.getCmdListCmdBufferSize(i);
                for (int j = 0; j < cmdBufferSize; j++) {
                    long texId = imDrawData.getCmdListCmdBufferTextureId(i, j);
                    descriptorSets.put(0, texId);
                    vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                            pipeline.getVkPipelineLayout(), 0, descriptorSets, null);

                    imDrawData.getCmdListCmdBufferClipRect(clipRect, i, j);
                    rect.offset(it -> it.x((int) Math.max(clipRect.x, 0)).y((int) Math.max(clipRect.y, 1)));
                    rect.extent(it -> it.width((int) (clipRect.z - clipRect.x)).height((int) (clipRect.w - clipRect.y)));
                    vkCmdSetScissor(cmdHandle, 0, rect);
                    int numElements = imDrawData.getCmdListCmdBufferElemCount(i, j);
                    vkCmdDrawIndexed(cmdHandle, numElements, 1,
                            offsetIdx + imDrawData.getCmdListCmdBufferIdxOffset(i, j),
                            offsetVtx + imDrawData.getCmdListCmdBufferVtxOffset(i, j), 0);
                }
                offsetIdx += imDrawData.getCmdListIdxBufferSize(i);
                offsetVtx += imDrawData.getCmdListVtxBufferSize(i);
            }

            vkCmdEndRendering(cmdHandle);
        }
    }

    /** 窗口尺寸变化：同步 ImGui 显示尺寸（逻辑坐标 + 帧缓冲缩放，同 init） */
    public void resize(VkCtx vkCtx, EngCtx engCtx) {
        Window window = engCtx.window();
        float scale = window.getContentScale();
        ImGui.getIO().setDisplaySize(window.getLogicalWidth(), window.getLogicalHeight());
        ImGui.getIO().setDisplayFramebufferScale(scale, scale);
    }

    /** 把 ImGui draw data 拼入本帧插槽的顶点/索引缓冲（HOST_VISIBLE，按需增长只增不缩） */
    private void updateBuffers(VkCtx vkCtx, int idx) {
        ImDrawData imDrawData = ImGui.getDrawData();

        if (imDrawData.ptr == 0) {
            return;
        }
        int vertexBufferSize = imDrawData.getTotalVtxCount() * VERTEX_SIZE;
        int indexBufferSize = imDrawData.getTotalIdxCount() * Short.BYTES;

        if (vertexBufferSize == 0 || indexBufferSize == 0) {
            return;
        }
        var vtxBuffer = buffsVtx[idx];
        if (vtxBuffer == null || vertexBufferSize > vtxBuffer.getRequestedSize()) {
            if (vtxBuffer != null) {
                vtxBuffer.cleanup(vkCtx);
            }
            vtxBuffer = new VkBuffer(vkCtx, vertexBufferSize, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
                    VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
            buffsVtx[idx] = vtxBuffer;
        }

        var indicesBuffer = buffsIdx[idx];
        if (indicesBuffer == null || indexBufferSize > indicesBuffer.getRequestedSize()) {
            if (indicesBuffer != null) {
                indicesBuffer.cleanup(vkCtx);
            }
            indicesBuffer = new VkBuffer(vkCtx, indexBufferSize, VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
                    VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT,
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
            buffsIdx[idx] = indicesBuffer;
        }

        ByteBuffer dstVertexBuffer = MemoryUtil.memByteBuffer(vtxBuffer.map(vkCtx), vertexBufferSize);
        ByteBuffer dstIdxBuffer = MemoryUtil.memByteBuffer(indicesBuffer.map(vkCtx), indexBufferSize);

        int numCmdLists = imDrawData.getCmdListsCount();
        for (int i = 0; i < numCmdLists; i++) {
            ByteBuffer imguiVertexBuffer = imDrawData.getCmdListVtxBufferData(i);
            dstVertexBuffer.put(imguiVertexBuffer);

            // Always get the indices buffer after finishing with the vertices buffer
            ByteBuffer imguiIndicesBuffer = imDrawData.getCmdListIdxBufferData(i);
            dstIdxBuffer.put(imguiIndicesBuffer);
        }

        vtxBuffer.flush(vkCtx);
        indicesBuffer.flush(vkCtx);

        vtxBuffer.unMap(vkCtx);
        indicesBuffer.unMap(vkCtx);
    }
}
