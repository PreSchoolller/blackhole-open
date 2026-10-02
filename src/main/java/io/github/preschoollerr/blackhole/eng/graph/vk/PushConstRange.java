package io.github.preschoollerr.blackhole.eng.graph.vk;

/**
 * Push Constants 范围 —— 定义着色器常量的内存布局。
 * <p>
 * Push Constants 是 Vulkan 中最快速的 CPU→GPU 数据传递方式：
 * <ul>
 *   <li>数据直接嵌入命令缓冲区，无需额外缓冲区分配</li>
 *   <li>典型限制为 128-256 字节（取决于设备）</li>
 *   <li>本项目使用 176 字节传递相机矩阵和黑洞参数</li>
 * </ul>
 *
 * @param stage  着色器阶段标志（VK_SHADER_STAGE_VERTEX_BIT | FRAGMENT_BIT 等）
 * @param offset 缓冲区中的字节偏移
 * @param size   数据大小（字节）
 */
public record PushConstRange(int stage, int offset, int size) {
}
