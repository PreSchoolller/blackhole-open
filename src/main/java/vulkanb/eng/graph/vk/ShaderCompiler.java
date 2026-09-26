package vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;
import org.tinylog.Logger;
import vulkanb.eng.EngCfg;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static vulkanb.utils.Constants.RESOURCES;
import static vulkanb.utils.Constants.RESOURCES_DIR;
import static vulkanb.utils.Constants.SHADER_CACHE_DIR;

/**
 * 着色器编译器 —— 将 GLSL 源码编译为 SPIR-V 字节码。
 * <p>
 * 使用 LWJGL 的 shaderc 绑定调用 glslang 编译器。
 * <p>
 * 编译策略：
 * <ul>
 *   <li>仅在 GLSL 源文件比 SPIR-V 文件更新时重新编译</li>
 *   <li>调试模式下生成调试信息并禁用优化</li>
 *   <li>目标环境为 Vulkan 1.0（SPIR-V 1.0，兼容性最好）</li>
 * </ul>
 * <p>
 * GLSL 着色器类型映射：
 * <pre>
 * VK_VERTEX   (0x01) → shaderc_glsl_vertex_shader (0)
 * VK_FRAGMENT (0x10) → shaderc_glsl_fragment_shader (1)
 * VK_GEOMETRY (0x08) → shaderc_glsl_geometry_shader (3)
 * VK_COMPUTE  (0x20) → shaderc_glsl_compute_shader (5)
 * </pre>
 */
public class ShaderCompiler {

    /** 私有构造：静态工具类 */
    private ShaderCompiler() {
    }

    /**
     * 编译 GLSL 源码为 SPIR-V 字节码。
     *
     * @param shaderCode GLSL 源码字符串
     * @param shaderType Vulkan 着色器阶段标志
     * @return 编译后的 SPIR-V 字节码
     * @throws RuntimeException 如果编译失败
     */
    public static byte[] compileShader(String shaderCode, int shaderType) {
        // 源码需编码为堆外 ByteBuffer 直传:LWJGL 绑定对 CharSequence 参数用线程本地
        // MemoryStack 编码,其默认容量仅 64KB,大着色器(如 kerr.frag ~250KB)会
        // OutOfMemoryError("Out of stack space")——改为 memUTF8 堆外分配绕开
        ByteBuffer sourceUtf8 = MemoryUtil.memUTF8(shaderCode, false);
        ByteBuffer nameUtf8 = MemoryUtil.memUTF8("shader.glsl", true);
        ByteBuffer entryUtf8 = MemoryUtil.memUTF8("main", true);
        long compiler = 0;
        long options = 0;
        byte[] compiledShader;

        int shadercType = toShadercType(shaderType);

        try {
            compiler = Shaderc.shaderc_compiler_initialize();
            options = Shaderc.shaderc_compile_options_initialize();
            // 目标环境：Vulkan 1.0
            Shaderc.shaderc_compile_options_set_target_env(options,
                    Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_0);
            // 源语言：GLSL
            Shaderc.shaderc_compile_options_set_source_language(options, Shaderc.shaderc_source_language_glsl);
            // 调试模式：生成调试信息，禁用优化
            if (EngCfg.getInstance().isDebugShaders()) {
                Shaderc.shaderc_compile_options_set_generate_debug_info(options);
                Shaderc.shaderc_compile_options_set_optimization_level(options, 0);
            }

            long result = Shaderc.shaderc_compile_into_spv(
                    compiler, sourceUtf8, shadercType, nameUtf8, entryUtf8, options);

            if (Shaderc.shaderc_result_get_compilation_status(result) != Shaderc.shaderc_compilation_status_success) {
                throw new RuntimeException("Shader compilation failed: " + Shaderc.shaderc_result_get_error_message(result));
            }

            ByteBuffer buffer = Shaderc.shaderc_result_get_bytes(result);
            compiledShader = new byte[buffer.remaining()];
            buffer.get(compiledShader);
        } finally {
            MemoryUtil.memFree(sourceUtf8);
            MemoryUtil.memFree(nameUtf8);
            MemoryUtil.memFree(entryUtf8);
            Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }

        return compiledShader;
    }
    /**
     * 将 Vulkan 着色器阶段位转换为 shaderc 着色器类型枚举。
     *
     * @param vkStage Vulkan 着色器阶段位（如 VK_SHADER_STAGE_VERTEX_BIT = 0x01）
     * @return shaderc 着色器类型（如 shaderc_glsl_vertex_shader = 0）
     */
    private static int toShadercType(int vkStage) {
        // VK stage bits: VERTEX=0x1, TESS_CTRL=0x2, TESS_EVAL=0x4, GEOMETRY=0x8, FRAGMENT=0x10, COMPUTE=0x20
        // shaderc types: vertex=0, fragment=1, tess_ctrl=2, geometry=3, tess_eval=4, compute=5
        return switch (vkStage) {
            case 0x01 -> 0; // VK_VERTEX -> shaderc_glsl_vertex_shader
            case 0x02 -> 2; // VK_TESS_CTRL -> shaderc_glsl_tesscontrol_shader
            case 0x04 -> 4; // VK_TESS_EVAL -> shaderc_glsl_tessevaluation_shader
            case 0x08 -> 3; // VK_GEOMETRY -> shaderc_glsl_geometry_shader
            case 0x10 -> 1; // VK_FRAGMENT -> shaderc_glsl_fragment_shader
            case 0x20 -> 5; // VK_COMPUTE -> shaderc_glsl_compute_shader
            default -> 0;
        };
    }

    /**
     * 条件编译：仅在 GLSL 源文件有更新时重新编译。
     * <p>
     * 比较 GLSL 源文件和对应 .spv 文件的最后修改时间。
     * 如果 .spv 不存在或源文件更新，执行编译。
     *
     * @param glsShaderFile GLSL 源文件路径（不含 .spv 后缀）
     * @param shaderType    着色器阶段标志
     */
    public static String compileShaderIfChanged(String glsShaderFile, int shaderType) {
        try {
            String shaderCode = extractShaderCode(glsShaderFile);

            String filePrefix = glsShaderFile.replaceAll("[/\\\\]", "_");                 // 如 _shaders_blackhole.frag
            String cachePrefix = filePrefix + "." + sourceHash8(shaderCode);     // 追加源码哈希
            File spvFile = new File(SHADER_CACHE_DIR + cachePrefix + ".spv");

            if (spvFile.exists()) {
                Logger.debug("Shader [{}] cache hit: [{}]", glsShaderFile, spvFile.getPath());
                return spvFile.getAbsolutePath();
            }

            Logger.debug("Compiling [{}] to [{}]", glsShaderFile, spvFile.getPath());

            if (!spvFile.getParentFile().exists()) {
                boolean mkdirs = spvFile.getParentFile().mkdirs();
                if (!mkdirs) {
                    Logger.warn("WARNING: mkdir [{}] failed", spvFile.getParentFile().getAbsolutePath());
                }
            }
            byte[] compiledShader = compileShader(shaderCode, shaderType);
            Files.write(spvFile.toPath(), compiledShader);
            cleanupStaleCache(spvFile, filePrefix);
            return spvFile.getAbsolutePath();
        } catch (IOException excp) {
            throw new RuntimeException(excp);
        }
    }

    private static String extractShaderCode(String glsShaderFile) throws IOException {
        if (!compilingInJar(glsShaderFile)) {
            return new String(Files.readAllBytes(new File(glsShaderFile).toPath()));
        }
        try (InputStream shaderCodeStream = ShaderCompiler.class.getResourceAsStream(glsShaderFile.substring(RESOURCES.length()))) {
            if (shaderCodeStream == null) {
                Logger.debug("Shader file [{}] not found", glsShaderFile);
                throw new IOException("Shader file " + glsShaderFile + " not found");
            }
            return new String(shaderCodeStream.readAllBytes());
        }
    }

    public static boolean compilingInJar(String glsShaderFile) throws IOException {
        if (glsShaderFile.startsWith(RESOURCES_DIR)) {
            glsShaderFile = glsShaderFile.substring(RESOURCES.length());
        }
        URL glsResource = ShaderCompiler.class.getResource(glsShaderFile);
        return glsResource != null && glsResource.getProtocol().equals("jar");
    }

    /** 源码内容的 SHA-256 前 8 个十六进制字符（缓存键） */
    private static String sourceHash8(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException excp) {
            throw new RuntimeException(excp);
        }
    }

    /** 尽力删除同源文件、不同哈希的过期缓存文件 */
    private static void cleanupStaleCache(File currentSpv, String filePrefix) {
        File[] stale = currentSpv.getParentFile()
                .listFiles((dir, name) -> name.startsWith(filePrefix + ".")
                        && name.endsWith(".spv") && !name.equals(currentSpv.getName()));
        if (stale == null) {
            return;
        }
        for (File file : stale) {
            try {
                Files.deleteIfExists(file.toPath());
                Logger.debug("Removed stale shader cache: [{}]", file.getPath());
            } catch (IOException excp) {
                Logger.debug("Skip stale shader cache: [{}]", file.getPath());
            }
        }
    }
}
