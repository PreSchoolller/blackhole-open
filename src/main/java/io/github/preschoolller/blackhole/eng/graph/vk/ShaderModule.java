package io.github.preschoolller.blackhole.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSpecializationInfo;
import org.tinylog.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;

import static org.lwjgl.vulkan.VK13.vkCreateShaderModule;
import static org.lwjgl.vulkan.VK13.vkDestroyShaderModule;
import static io.github.preschoolller.blackhole.eng.graph.vk.VkUtils.vkCheck;

/**
 * 着色器模块 —— 封装 SPIR-V 编译后的着色器。
 * <p>
 * 职责：
 * <ul>
 *   <li>从 SPIR-V 文件加载着色器字节码</li>
 *   <li>创建 Vulkan 着色器模块（{@link VkShaderModule}）</li>
 *   <li>管理着色器阶段（顶点/片段/几何等）和特化信息</li>
 * </ul>
 * <p>
 * 着色器模块在管线创建后可以安全销毁，因为管线已包含着色器的内部副本。
 */
public class ShaderModule {

    /** Vulkan 着色器模块句柄 */
    private final long handle;
    /** 着色器阶段标志（VK_SHADER_STAGE_VERTEX_BIT / FRAGMENT_BIT 等） */
    private final int shaderStage;
    /** 特化信息（用于着色器常量特化，可为 null） */
    private final VkSpecializationInfo specInfo;

    /**
     * 从 SPIR-V 文件创建着色器模块。
     *
     * @param vkCtx         Vulkan 上下文
     * @param shaderStage   着色器阶段标志
     * @param shaderSpvFile SPIR-V 文件路径
     * @param specInfo      特化信息（可为 null）
     */
    public ShaderModule(VkCtx vkCtx, int shaderStage, String shaderSpvFile, VkSpecializationInfo specInfo) {
        try {
            byte[] moduleContents = Files.readAllBytes(new File(shaderSpvFile).toPath());
            handle = createShaderModule(vkCtx, moduleContents);
            this.shaderStage = shaderStage;
            this.specInfo = specInfo;
        } catch (IOException excp) {
            Logger.error("Error reading shader file", excp);
            throw new RuntimeException(excp);
        }
    }

    /**
     * 从 SPIR-V 字节码创建 Vulkan 着色器模块。
     *
     * @param vkCtx Vulkan 上下文
     * @param code  SPIR-V 字节码
     * @return 着色器模块句柄
     */
    private static long createShaderModule(VkCtx vkCtx, byte[] code) {
        // SPIR-V 码堆外直传:大着色器(kerr.frag.spv ~232KB)远超线程本地 MemoryStack
        // 默认 64KB 容量,stack.malloc 会 OutOfMemoryError("Out of stack space")
        ByteBuffer pCode = MemoryUtil.memAlloc(code.length).put(0, code);
        try (var stack = MemoryStack.stackPush()) {
            var moduleCreateInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType$Default()
                    .pCode(pCode);

            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreateShaderModule(vkCtx.getDevice().getVkDevice(), moduleCreateInfo, null, lp),
                    "Failed to create shader module");

            return lp.get(0);
        } finally {
            MemoryUtil.memFree(pCode);
        }
    }

    /** 销毁着色器模块 */
    public void cleanup(VkCtx vkCtx) {
        vkDestroyShaderModule(vkCtx.getDevice().getVkDevice(), handle, null);
    }

    public long getHandle() {
        return handle;
    }

    public int getShaderStage() {
        return shaderStage;
    }

    public VkSpecializationInfo getSpecInfo() {
        return specInfo;
    }
}
