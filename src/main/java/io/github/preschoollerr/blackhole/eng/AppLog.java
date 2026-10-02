package io.github.preschoollerr.blackhole.eng;

import org.tinylog.Logger;

/**
 * 应用自定义日志的总开关与出口。
 * <p>
 * 应用自身的交互反馈消息（测地模式进出、视角捕获、参数变化等）统一走本类,
 * 而非散落的 {@code System.out}。开关有三处可调（后设置的生效）：
 * <ul>
 *   <li>配置文件:{@code log.verbose}（默认 true）</li>
 *   <li>命令行:{@code --verbose} / {@code --quiet}（覆盖配置）</li>
 *   <li>GUI:Controls 面板的 "Verbose log" 复选框（运行时切换）</li>
 * </ul>
 * 关闭时消息不输出;引擎内部日志的级别由 {@code log.level} 独立控制。
 */
public final class AppLog {

    private static volatile boolean verbose = true;

    private AppLog() {
    }

    public static void setVerbose(boolean v) {
        verbose = v;
    }

    public static boolean isVerbose() {
        return verbose;
    }

    /** 输出一条受开关控制的应用消息 */
    public static void info(String msg) {
        if (verbose) {
            Logger.info(msg);
        }
    }

    /** 输出一条受开关控制的格式化应用消息（{@code String.format} 语义） */
    public static void infof(String format, Object... args) {
        if (verbose) {
            Logger.info(String.format(format, args));
        }
    }

    /** 输出一条受开关控制的警告级应用消息（数值发散等异常恢复） */
    public static void warn(String msg) {
        if (verbose) {
            Logger.warn(msg);
        }
    }
}
