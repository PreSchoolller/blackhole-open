package vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.tinylog.Logger;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK13.*;
import static vulkanb.eng.graph.vk.VkUtils.vkCheck;

/**
 * 计算管线 —— 封装 Vulkan compute pipeline 和管线布局。
 * <p>
 * 为 NPGS 原版后处理链（PreBloom/GaussBlur 的 storage image 读写）而设，
 * 与图形管线 {@link Pipeline} 平行：单 compute 阶段 + 描述符集布局 + push constants，
 * 无顶点输入/光栅化/动态渲染状态。每帧经 vkCmdDispatch 分发（组数 = ceil(尺寸/16)）。
 */
public class ComputePipeline {

    /** 计算管线句柄 */
    private final long vkPipeline;
    /** 管线布局句柄 */
    private final long vkPipelineLayout;

    /**
     * 创建计算管线。
     *
     * @param vkCtx    Vulkan 上下文
     * @param module   compute 着色器模块（入口恒为 main）
     * @param layouts  描述符集布局（set0..setN 顺序）
     * @param ranges   push constant 范围（可为 null/空）
     */
    public ComputePipeline(VkCtx vkCtx, ShaderModule module, DescSetLayout[] layouts, PushConstRange[] ranges) {
        Logger.debug("Creating compute pipeline");
        Device device = vkCtx.getDevice();
        try (var stack = MemoryStack.stackPush()) {
            LongBuffer lp = stack.mallocLong(1);
            ByteBuffer main = stack.UTF8("main");

            var stageInfo = VkPipelineShaderStageCreateInfo.calloc(stack)
                    .sType$Default()
                    .stage(module.getShaderStage())
                    .module(module.getHandle())
                    .pName(main);

            // Push constants
            int numRanges = ranges != null ? ranges.length : 0;
            VkPushConstantRange.Buffer vpcr = null;
            if (numRanges > 0) {
                vpcr = VkPushConstantRange.calloc(numRanges, stack);
                for (int i = 0; i < numRanges; i++) {
                    vpcr.get(i)
                            .stageFlags(ranges[i].stage())
                            .offset(ranges[i].offset())
                            .size(ranges[i].size());
                }
            }

            // 描述符集布局
            int numLayouts = layouts != null ? layouts.length : 0;
            LongBuffer ppLayout = stack.mallocLong(numLayouts);
            for (int i = 0; i < numLayouts; i++) {
                ppLayout.put(i, layouts[i].getVkDescLayout());
            }

            var layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pSetLayouts(ppLayout)
                    .pPushConstantRanges(vpcr);
            vkCheck(vkCreatePipelineLayout(device.getVkDevice(), layoutInfo, null, lp),
                    "Failed to create compute pipeline layout");
            vkPipelineLayout = lp.get(0);

            var pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack)
                    .sType$Default()
                    .stage(stageInfo)
                    .layout(vkPipelineLayout);
            vkCheck(vkCreateComputePipelines(device.getVkDevice(), VK_NULL_HANDLE, pipelineInfo, null, lp),
                    "Failed to create compute pipeline");
            vkPipeline = lp.get(0);
        }
    }

    public long getVkPipeline() {
        return vkPipeline;
    }

    public long getVkPipelineLayout() {
        return vkPipelineLayout;
    }

    /** 释放管线与布局 */
    public void cleanup(VkCtx vkCtx) {
        vkDestroyPipeline(vkCtx.getDevice().getVkDevice(), vkPipeline, null);
        vkDestroyPipelineLayout(vkCtx.getDevice().getVkDevice(), vkPipelineLayout, null);
    }
}
