import org.lwjgl.util.shaderc.Shaderc;
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
        long r = Shaderc.shaderc_compile_into_spv(c, code, type, args[0], "main", o);
        if (Shaderc.shaderc_result_get_compilation_status(r) != Shaderc.shaderc_compilation_status_success) {
            System.err.println("FAILED:\n" + Shaderc.shaderc_result_get_error_message(r));
            System.exit(1);
        }
        System.out.println("OK " + args[0] + " -> " + Shaderc.shaderc_result_get_bytes(r).remaining() + " spv bytes");
    }
}
