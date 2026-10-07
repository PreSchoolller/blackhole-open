import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public class SpvCheck {
    public static void main(String[] args) throws Exception {
        String code = Files.readString(Path.of(args[0]));
        int type = Integer.parseInt(args[1]);
        long c = Shaderc.shaderc_compiler_initialize();
        long o = Shaderc.shaderc_compile_options_initialize();
        Shaderc.shaderc_compile_options_set_target_env(o,
                Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_0);
        Shaderc.shaderc_compile_options_set_source_language(o, Shaderc.shaderc_source_language_glsl);
        // 源码经 MemoryUtil 堆外分配绕开 MemoryStack（默认帧 64KB，装不下 kerr.frag ~250KB，
        // 会 OutOfMemoryError("Out of stack space")；与运行时 ShaderCompiler 同款处理）
        ByteBuffer sourceUtf8 = MemoryUtil.memUTF8(code, false);
        ByteBuffer nameUtf8 = MemoryUtil.memUTF8(args[0], true);
        ByteBuffer entryUtf8 = MemoryUtil.memUTF8("main", true);
        long r = Shaderc.shaderc_compile_into_spv(c, sourceUtf8, type, nameUtf8, entryUtf8, o);
        MemoryUtil.memFree(sourceUtf8);
        if (Shaderc.shaderc_result_get_compilation_status(r) != Shaderc.shaderc_compilation_status_success) {
            System.err.println("FAILED:\n" + Shaderc.shaderc_result_get_error_message(r));
            System.exit(1);
        }
        System.out.println("OK " + args[0] + " -> " + Shaderc.shaderc_result_get_bytes(r).remaining() + " spv bytes");
    }
}
