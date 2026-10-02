package io.github.preschoollerr.blackhole.eng.graph.vk;

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;
import org.tinylog.Logger;
import io.github.preschoollerr.blackhole.eng.EngCfg;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.preschoollerr.blackhole.utils.Constants.RESOURCES;
import static io.github.preschoollerr.blackhole.utils.Constants.RESOURCES_DIR;
import static io.github.preschoollerr.blackhole.utils.Constants.SHADER_CACHE_DIR;

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

    /** #include 指令（仅支持引号相对路径形式，如 #include "Common/X.glsl"） */
    private static final Pattern INCLUDE_PATTERN = Pattern.compile("^\\s*#include\\s+\"([^\"]+)\".*$", Pattern.MULTILINE);

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
        return compileShader(shaderCode, shaderType, new String[0]);
    }

    /**
     * 编译 GLSL 源码为 SPIR-V 字节码（带宏定义变体）。
     *
     * @param shaderCode GLSL 源码字符串
     * @param shaderType Vulkan 着色器阶段标志
     * @param defines    宏定义列表（"NAME" 或 "NAME=VALUE"）
     * @return 编译后的 SPIR-V 字节码
     * @throws RuntimeException 如果编译失败
     */
    public static byte[] compileShader(String shaderCode, int shaderType, String... defines) {
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
            // 宏定义变体（如 GENERATE_MIPMAP / GAUSS_BLUR）
            for (String define : defines) {
                int eq = define.indexOf('=');
                if (eq < 0) {
                    Shaderc.shaderc_compile_options_add_macro_definition(options, define, "1");
                } else {
                    Shaderc.shaderc_compile_options_add_macro_definition(options,
                            define.substring(0, eq), define.substring(eq + 1));
                }
            }
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
     * <p>
     * shaderc 枚举实测值（LWJGL 3.4.3，javap 常量池）：vertex=0, fragment=1,
     * compute=2, geometry=3, tess_control=4, tess_evaluation=5。
     * （此前的注释表把 compute 写成 5=tess_evaluation——本项目首个 compute shader
     * 才触发：local_size_x 被按 tess 阶段语义拒绝。现直接引用 Shaderc 常量。）
     *
     * @param vkStage Vulkan 着色器阶段位（如 VK_SHADER_STAGE_VERTEX_BIT = 0x01）
     * @return shaderc 着色器类型（如 shaderc_glsl_vertex_shader = 0）
     */
    private static int toShadercType(int vkStage) {
        // VK stage bits: VERTEX=0x1, TESS_CTRL=0x2, TESS_EVAL=0x4, GEOMETRY=0x8, FRAGMENT=0x10, COMPUTE=0x20
        return switch (vkStage) {
            case 0x01 -> Shaderc.shaderc_glsl_vertex_shader;
            case 0x02 -> Shaderc.shaderc_glsl_tess_control_shader;
            case 0x04 -> Shaderc.shaderc_glsl_tess_evaluation_shader;
            case 0x08 -> Shaderc.shaderc_glsl_geometry_shader;
            case 0x10 -> Shaderc.shaderc_glsl_fragment_shader;
            case 0x20 -> Shaderc.shaderc_glsl_compute_shader;
            default -> Shaderc.shaderc_glsl_vertex_shader;
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
        return compileShaderIfChanged(glsShaderFile, shaderType, new String[0]);
    }

    /**
     * 条件编译（带宏定义变体）：同一源文件可按不同 -D 宏编译出多个 spv
     * （NPGS 的 Bloom.comp.glsl 以 GENERATE_MIPMAP / GAUSS_BLUR 两配置复用）。
     * 缓存键 = 文件名 + 宏哈希 + 源码哈希；过期清理只清同宏变体。
     *
     * @param defines    宏定义列表（如 "GENERATE_MIPMAP"；形如 "NAME=VALUE" 带值）
     */
    public static String compileShaderIfChanged(String glsShaderFile, int shaderType, String... defines) {
        try {
            // 先解析 #include 再参与缓存键——被包含文件改动同样触发重编译
            String shaderCode = resolveIncludes(extractShaderCode(glsShaderFile), glsShaderFile, new HashSet<>());

            String filePrefix = glsShaderFile.replaceAll("[/\\\\]", "_");                 // 如 _shaders_blackhole.frag
            String definesJoined = String.join("|", defines);
            // 宏哈希：无宏为空串（缓存命名与既有无宏条目完全一致）；同文件不同宏的变体
            // 聚在不同哈希前缀下，过期清理互不误伤
            String definesHash = defines.length == 0 ? "" : "." + sourceHash8(definesJoined);
            String cachePrefix = filePrefix + definesHash + "." + sourceHash8(shaderCode + "#" + definesJoined);
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
            byte[] compiledShader = compileShader(shaderCode, shaderType, defines);
            Files.write(spvFile.toPath(), compiledShader);
            cleanupStaleCache(spvFile, filePrefix + definesHash + ".");
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

    /**
     * 递归解析 GLSL 源码中的 {@code #include "相对路径"} 指令（shaderc 不处理 include）。
     * <p>
     * 路径相对包含者所在目录解析，文件系统与 jar 内资源两种形态通用
     * （路径形态随 {@link #extractShaderCode} 的约定）；同一文件只内联一次
     * （include-once 去重，NPGS 的 BlackHole.frag 会经两条路径重复引入
     * CoordConverter.glsl，不去重将重定义报错）。
     *
     * @param shaderCode  当前文件的源码
     * @param sourceFile  当前文件路径（决定相对基准与去重键）
     * @param included    已内联文件集合（跨递归共享；调用方传入空集合并含根文件）
     */
    private static String resolveIncludes(String shaderCode, String sourceFile, Set<String> included) throws IOException {
        String baseDir = directoryOf(sourceFile);
        Matcher matcher = INCLUDE_PATTERN.matcher(shaderCode);
        StringBuilder out = new StringBuilder(shaderCode.length());
        int last = 0;
        while (matcher.find()) {
            out.append(shaderCode, last, matcher.start());
            String includePath = baseDir.isEmpty() ? matcher.group(1) : baseDir + "/" + matcher.group(1);
            if (included.add(normalizeKey(includePath))) {
                out.append(resolveIncludes(extractShaderCode(includePath), includePath, included));
            }
            last = matcher.end();
        }
        out.append(shaderCode.substring(last));
        return out.toString();
    }

    /** 路径的目录部分（无目录返回空串；统一 '/' 分隔，兼容反斜杠输入） */
    private static String directoryOf(String path) {
        String normalized = path.replace('\\', '/');
        int idx = normalized.lastIndexOf('/');
        return idx < 0 ? "" : normalized.substring(0, idx);
    }

    /** include-once 去重键：统一分隔符与大小写（Windows 文件系统大小写不敏感） */
    private static String normalizeKey(String path) {
        return path.replace('\\', '/').toLowerCase();
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
