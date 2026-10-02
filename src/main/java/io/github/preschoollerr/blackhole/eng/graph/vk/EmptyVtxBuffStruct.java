package io.github.preschoollerr.blackhole.eng.graph.vk;

import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;

/**
 * 空顶点缓冲结构 —— 用于全屏三角形渲染。
 * <p>
 * 本项目的黑洞渲染不使用传统的顶点缓冲区。
 * 全屏三角形由顶点着色器通过 {@code gl_VertexIndex} 生成：
 * <pre>
 *   顶点 0: (-1, -1)    // 左下
 *   顶点 1: ( 3, -1)    // 右下（超出屏幕）
 *   顶点 2: (-1,  3)    // 左上（超出屏幕）
 * </pre>
 * 这个超大三角形完全覆盖屏幕，片段着色器中的每个像素
 * 都会执行光线步进计算。
 * <p>
 * 此类创建一个无顶点属性绑定的空 {@link VkPipelineVertexInputStateCreateInfo}，
 * 告诉 Vulkan 管线不需要从顶点缓冲区读取数据。
 */
public class EmptyVtxBuffStruct {

    /** 空的顶点输入状态 */
    private final VkPipelineVertexInputStateCreateInfo vi;

    /** 创建空顶点输入状态（无顶点属性、无顶点绑定） */
    public EmptyVtxBuffStruct() {
        vi = VkPipelineVertexInputStateCreateInfo.calloc();
        vi.sType$Default();
    }

    /** 释放 Vulkan 对象 */
    public void cleanup() {
        vi.free();
    }

    public VkPipelineVertexInputStateCreateInfo getVi() {
        return vi;
    }
}
