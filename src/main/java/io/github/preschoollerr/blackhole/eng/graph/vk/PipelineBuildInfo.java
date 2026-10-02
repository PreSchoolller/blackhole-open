package io.github.preschoollerr.blackhole.eng.graph.vk;

import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;

import static org.lwjgl.vulkan.VK13.VK_FORMAT_UNDEFINED;

/**
 * 图形管线构建参数 —— 采用 Builder 模式配置管线创建所需的所有参数。
 * <p>
 * 必需参数（构造函数）：
 * <ul>
 *   <li>着色器模块数组（顶点 + 片段）</li>
 *   <li>顶点输入状态</li>
 *   <li>颜色附件格式数组</li>
 * </ul>
 * <p>
 * 可选参数（链式调用设置）：
 * <ul>
 *   <li>深度格式（不设置则禁用深度测试）</li>
 *   <li>描述符集布局</li>
 *   <li>Push Constants 范围</li>
 *   <li>是否启用颜色混合</li>
 *   <li>是否启用深度钳制</li>
 * </ul>
 */
public class PipelineBuildInfo {

    /** 颜色附件格式数组 */
    private final int[] colorFormats;
    /** 着色器模块数组 */
    private final ShaderModule[] shaderModules;
    /** 顶点输入状态 */
    private final VkPipelineVertexInputStateCreateInfo vi;
    /** 是否启用深度钳制 */
    private boolean depthClamp;
    /** 深度附件格式（VK_FORMAT_UNDEFINED 表示不使用深度） */
    private int depthFormat;
    /** 描述符集布局数组 */
    private DescSetLayout[] descSetLayouts;
    private PushConstRange[] pushConstRanges;
    /** 是否启用颜色混合（Alpha 混合） */
    private boolean useBlend;

    /**
     * 构造管线构建参数。
     *
     * @param shaderModules 着色器模块数组（至少包含顶点和片段）
     * @param vi            顶点输入状态
     * @param colorFormats  颜色附件格式数组
     */
    public PipelineBuildInfo(ShaderModule[] shaderModules, VkPipelineVertexInputStateCreateInfo vi, int[] colorFormats) {
        this.shaderModules = shaderModules;
        this.vi = vi;
        this.colorFormats = colorFormats;
        depthFormat = VK_FORMAT_UNDEFINED;
        useBlend = false;
        depthClamp = false;
    }

    public int[] getColorFormats() {
        return colorFormats;
    }

    public int getDepthFormat() {
        return depthFormat;
    }

    public DescSetLayout[] getDescSetLayouts() {
        return descSetLayouts;
    }

    public PushConstRange[] getPushConstRanges() {
        return pushConstRanges;
    }

    public ShaderModule[] getShaderModules() {
        return shaderModules;
    }

    public VkPipelineVertexInputStateCreateInfo getVi() {
        return vi;
    }

    public boolean isDepthClamp() {
        return depthClamp;
    }

    public boolean isUseBlend() {
        return useBlend;
    }

    /** 设置深度钳制 */
    public PipelineBuildInfo setDepthClamp(boolean depthClamp) {
        this.depthClamp = depthClamp;
        return this;
    }

    /** 设置深度格式（非 UNDEFINED 则启用深度测试） */
    public PipelineBuildInfo setDepthFormat(int depthFormat) {
        this.depthFormat = depthFormat;
        return this;
    }

    /** 设置描述符集布局 */
    public PipelineBuildInfo setDescSetLayouts(DescSetLayout[] descSetLayouts) {
        this.descSetLayouts = descSetLayouts;
        return this;
    }

    /** 设置 Push Constants 范围 */
    public PipelineBuildInfo setPushConstRanges(PushConstRange[] pushConstRanges) {
        this.pushConstRanges = pushConstRanges;
        return this;
    }

    /** 设置是否启用颜色混合 */
    public PipelineBuildInfo setUseBlend(boolean useBlend) {
        this.useBlend = useBlend;
        return this;
    }
}
