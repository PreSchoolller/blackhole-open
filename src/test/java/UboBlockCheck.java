import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BlackHoleArgs UBO 块一致性守卫 —— 三个 shader（blackhole.frag / blackhole.vert /
 * bloomComposite.frag）的块声明必须逐字段一致，否则字段错位读垃圾
 * （Phase 3 曾因 bloom 块漏改 rotationSpeed→iExposure 而整屏全黑，2026-09-27）。
 * <p>
 * 用法：java UboBlockCheck [frag] [vert] [bloom]（缺省为 resources/shaders 下三个文件）。
 * 提取每个文件中 {@code uniform BlackHoleArgs { ... } pc;} 的成员声明行，
 * 归一化（去注释/空白）后两两比对，不一致则打印首个差异并退出码 1。
 */
public class UboBlockCheck {

    private static final Pattern BLOCK = Pattern.compile(
            "uniform\\s+BlackHoleArgs\\s*\\{(.*?)\\}\\s*pc\\s*;", Pattern.DOTALL);
    private static final Pattern MEMBER = Pattern.compile("^\\s*(mat\\d|vec\\d|ivec\\d|float|int|uint|bool|double)\\s+([A-Za-z_][A-Za-z_0-9]*)\\s*(?:\\[\\d*\\])?\\s*;", Pattern.MULTILINE);

    public static void main(String[] args) throws Exception {
        String[] paths = args.length == 3 ? args : new String[]{
                "resources/shaders/blackhole.frag",
                "resources/shaders/blackhole.vert",
                "resources/shaders/bloomComposite.frag"};
        List<List<String>> all = new ArrayList<>();
        for (String p : paths) {
            String src = Files.readString(Path.of(p));
            Matcher m = BLOCK.matcher(src);
            if (!m.find()) {
                System.err.println("FAILED: " + p + " 中未找到 BlackHoleArgs 块");
                System.exit(1);
            }
            List<String> members = new ArrayList<>();
            Matcher mm = MEMBER.matcher(m.group(1));
            while (mm.find()) {
                members.add(mm.group(1) + " " + mm.group(2));
            }
            if (members.isEmpty()) {
                System.err.println("FAILED: " + p + " 块内未解析到成员（正则与声明风格不符？）");
                System.exit(1);
            }
            all.add(members);
        }
        boolean ok = true;
        for (int i = 1; i < all.size(); i++) {
            List<String> a = all.get(0);
            List<String> b = all.get(i);
            int n = Math.max(a.size(), b.size());
            for (int j = 0; j < n; j++) {
                String va = j < a.size() ? a.get(j) : "<缺>";
                String vb = j < b.size() ? b.get(j) : "<缺>";
                if (!va.equals(vb)) {
                    System.err.println("FAILED: " + paths[0] + " 与 " + paths[i]
                            + " 第 " + j + " 个成员不一致: [" + va + "] vs [" + vb + "]");
                    ok = false;
                }
            }
        }
        if (!ok) {
            System.exit(1);
        }
        System.out.println("OK " + all.get(0).size() + " members x " + all.size()
                + " shaders (" + String.join(", ", paths) + ")");
    }
}
