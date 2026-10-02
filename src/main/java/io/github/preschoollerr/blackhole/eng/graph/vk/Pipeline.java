package io.github.preschoollerr.blackhole.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK13.*;
import static io.github.preschoollerr.blackhole.eng.graph.vk.VkUtils.vkCheck;

/**
 * 图形管线 —— 封装 Vulkan 图形管线和管线布局。
 * <p>
 * 管线定义了 GPU 渲染的完整状态：
 * <ul>
 *   <li>着色器阶段（顶点 + 片段）</li>
 *   <li>图元装配（三角形列表）</li>
 *   <li>视口和裁剪状态（动态）</li>
 *   <li>光栅化（填充模式、无面剔除）</li>
 *   <li>多重采样（1 采样）</li>
 *   <li>深度测试（可选）</li>
 *   <li>颜色混合（可选）</li>
 *   <li>Push Constants 范围</li>
 *   <li>描述符集布局</li>
 * </ul>
 * <p>
 * 使用 Vulkan 1.3 动态渲染（{@link VkPipelineRenderingCreateInfo}），
 * 无需创建 VkRenderPass 和 VkFramebuffer。
 * <p>
 * 动态状态：视口和裁剪矩形在每帧通过命令缓冲区设置。
 */
public class Pipeline {

    /** 图形管线句柄 */
    private final long vkPipeline;
    /** 管线布局句柄（定义 Push Constants 和描述符集布局） */
    private final long vkPipelineLayout;

    /**
     * 创建图形管线。
     * <p>
     * 根据 {@link PipelineBuildInfo} 中的配置构建完整的图形管线。
     * 这是一个重量级操作，应在初始化阶段完成。
     *
     * @param vkCtx     Vulkan 上下文
     * @param buildInfo 管线构建参数
     */
    public Pipeline(VkCtx vkCtx, PipelineBuildInfo buildInfo) {
        Logger.debug("Creating pipeline");
        Device device = vkCtx.getDevice();
        try (var stack = MemoryStack.stackPush()) {
            LongBuffer lp = stack.mallocLong(1);

            ByteBuffer main = stack.UTF8("main");

            // 配置着色器阶段
            ShaderModule[] shaderModules = buildInfo.getShaderModules();
            int numModules = shaderModules.length;
            var shaderStages = VkPipelineShaderStageCreateInfo.calloc(numModules, stack);
            for (int i = 0; i < numModules; i++) {
                ShaderModule shaderModule = shaderModules[i];
                shaderStages.get(i)
                        .sType$Default()
                        .stage(shaderModule.getShaderStage())
                        .module(shaderModule.getHandle())
                        .pName(main);  // 入口函数名始终为 "main"
                if (shaderModule.getSpecInfo() != null) {
                    shaderStages.get(i).pSpecializationInfo(shaderModule.getSpecInfo());
                }
            }

            // 图元装配：三角形列表
            var vkPipelineInputAssemblyStateCreateInfo = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);

            // 视口状态：1 个视口 + 1 个裁剪矩形（动态设置）
            var vkPipelineViewportStateCreateInfo = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .viewportCount(1)
                    .scissorCount(1);

            // 光栅化状态：填充模式、无面剔除、顺时针正面
            var vkPipelineRasterizationStateCreateInfo = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .polygonMode(VK_POLYGON_MODE_FILL)
                    .cullMode(VK_CULL_MODE_NONE)      // 不剔除任何面（全屏三角形需要双面渲染）
                    .frontFace(VK_FRONT_FACE_CLOCKWISE)
                    .depthClampEnable(buildInfo.isDepthClamp())
                    .lineWidth(1.0f);

            // 多重采样：1 采样（无 MSAA）
            var vkPipelineMultisampleStateCreateInfo = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            // 深度模板状态（可选）
            VkPipelineDepthStencilStateCreateInfo ds = null;
            if (buildInfo.getDepthFormat() != VK_FORMAT_UNDEFINED) {
                ds = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .depthTestEnable(true)
                        .depthWriteEnable(true)
                        .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL)
                        .depthBoundsTestEnable(false)
                        .stencilTestEnable(false);
            }

            // 动态状态：视口和裁剪矩形
            var vkPipelineDynamicStateCreateInfo = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .pDynamicStates(stack.ints(
                            VK_DYNAMIC_STATE_VIEWPORT,
                            VK_DYNAMIC_STATE_SCISSOR
                    ));

            // Push Constants 范围
            VkPushConstantRange.Buffer vpcr = null;
            PushConstRange[] pushConstRanges = buildInfo.getPushConstRanges();
            int numPushConstants = pushConstRanges != null ? pushConstRanges.length : 0;
            if (numPushConstants > 0) {
                vpcr = VkPushConstantRange.calloc(numPushConstants, stack);
                for (int i = 0; i < numPushConstants; i++) {
                    PushConstRange pushConstRange = pushConstRanges[i];
                    vpcr.get(i)
                            .stageFlags(pushConstRange.stage())
                            .offset(pushConstRange.offset())
                            .size(pushConstRange.size());
                }
            }

            // 描述符集布局
            DescSetLayout[] descSetLayouts = buildInfo.getDescSetLayouts();
            int numLayouts = descSetLayouts != null ? descSetLayouts.length : 0;
            LongBuffer ppLayout = stack.mallocLong(numLayouts);
            for (int i = 0; i < numLayouts; i++) {
                ppLayout.put(i, descSetLayouts[i].getVkDescLayout());
            }

            // 动态渲染创建信息（Vulkan 1.3）
            int[] colorFormats = buildInfo.getColorFormats();
            int numColors = colorFormats.length;
            IntBuffer colorFormatsBuff = stack.mallocInt(numColors);
            colorFormatsBuff.put(0, colorFormats);
            var rendCreateInfo = VkPipelineRenderingCreateInfo.calloc(stack)
                    .sType$Default()
                    .colorAttachmentCount(numColors)
                    .pColorAttachmentFormats(colorFormatsBuff);
            if (ds != null) {
                rendCreateInfo.depthAttachmentFormat(buildInfo.getDepthFormat());
            }

            // 颜色混合附件状态
            VkPipelineColorBlendAttachmentState.Buffer blendAttState = VkPipelineColorBlendAttachmentState.calloc(numColors, stack);
            for (int i = 0; i < numColors; i++) {
                blendAttState.get(i)
                        .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                        .blendEnable(buildInfo.isUseBlend());
                if (buildInfo.isUseBlend()) {
                    // Alpha 混合：src * srcAlpha + dst * (1 - srcAlpha)
                    blendAttState.get(i).colorBlendOp(VK_BLEND_OP_ADD)
                            .alphaBlendOp(VK_BLEND_OP_ADD)
                            .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                            .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                            .srcAlphaBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                            .dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO);
                }
            }
            var colorBlendState = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .pAttachments(blendAttState);

            // 创建管线布局
            var pPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pSetLayouts(ppLayout)
                    .pPushConstantRanges(vpcr);

            vkCheck(vkCreatePipelineLayout(device.getVkDevice(), pPipelineLayoutCreateInfo, null, lp),
                    "Failed to create pipeline layout");
            vkPipelineLayout = lp.get(0);

            // 创建图形管线
            var pipeline = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType$Default()
                    .pStages(shaderStages)
                    .pVertexInputState(buildInfo.getVi())
                    .pInputAssemblyState(vkPipelineInputAssemblyStateCreateInfo)
                    .pViewportState(vkPipelineViewportStateCreateInfo)
                    .pRasterizationState(vkPipelineRasterizationStateCreateInfo)
                    .pColorBlendState(colorBlendState)
                    .pMultisampleState(vkPipelineMultisampleStateCreateInfo)
                    .pDynamicState(vkPipelineDynamicStateCreateInfo)
                    .layout(vkPipelineLayout)
                    .pNext(rendCreateInfo);
            if (ds != null) {
                pipeline.pDepthStencilState(ds);
            }

            vkCheck(vkCreateGraphicsPipelines(device.getVkDevice(), vkCtx.getPipelineCache().getVkPipelineCache(), pipeline, null, lp),
                    "Error creating graphics pipeline");
            vkPipeline = lp.get(0);
        }
    }

    /** 销毁管线和管线布局 */
    public void cleanup(VkCtx vkCtx) {
        Logger.debug("Destroying pipeline");
        VkDevice vkDevice = vkCtx.getDevice().getVkDevice();
        vkDestroyPipelineLayout(vkDevice, vkPipelineLayout, null);
        vkDestroyPipeline(vkDevice, vkPipeline, null);
    }

    public long getVkPipeline() {
        return vkPipeline;
    }

    public long getVkPipelineLayout() {
        return vkPipelineLayout;
    }
}
