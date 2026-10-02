package io.github.preschoolller.blackhole;

import org.tinylog.Logger;
import org.tinylog.configuration.Configuration;
import io.github.preschoolller.blackhole.eng.AppLog;
import io.github.preschoolller.blackhole.eng.EngCtx;
import io.github.preschoolller.blackhole.eng.EngCfg;
import io.github.preschoolller.blackhole.eng.Engine;
import io.github.preschoolller.blackhole.eng.IGameLogic;
import io.github.preschoolller.blackhole.eng.InputController;
import io.github.preschoolller.blackhole.eng.graph.gui.Panels;

/**
 * 应用程序入口类 —— 黑洞模拟器。
 * <p>
 * 实现 {@link IGameLogic} 接口,职责已全部委托:
 * <ul>
 *   <li>{@link InputController} —— 每渲染帧的按键/鼠标/测地积分推进</li>
 *   <li>{@link Panels} —— ImGui 面板构建（遥测/Controls/Kerr Disk/Keys）</li>
 *   <li>{@link io.github.preschoolller.blackhole.eng.graph.Render} —— 渲染分发(史瓦西/克尔渲染器)</li>
 * </ul>
 */
public class Main implements IGameLogic {

    /** ImGui 面板（显隐/FPS 统计/帧构建） */
    private final Panels panels = new Panels();
    /** 输入控制器（按键/鼠标/测地相机） */
    private final InputController inputController = new InputController(panels);

    /**
     * 程序入口。
     * 命令行参数:{@code --verbose} / {@code --quiet} 强制开/关应用自定义日志
     * （优先级高于配置文件的 log.verbose;GUI 复选框可随时再切换）。
     * 创建 {@link Engine} 实例并进入渲染主循环。
     */
    public static void main(String[] args) {
        // 日志级别必须先于任何日志调用设置(tinylog 首次调用即初始化 provider)
        var engCfg = EngCfg.getInstance();
        Configuration.set("level", engCfg.getLogLevel());

        Boolean cliVerbose = null;
        for (String arg : args) {
            if ("--verbose".equals(arg)) {
                cliVerbose = true;
            } else if ("--quiet".equals(arg)) {
                cliVerbose = false;
            } else {
                Logger.warn("Unknown command-line argument [{}] ignored", arg);
            }
        }
        AppLog.setVerbose(cliVerbose != null ? cliVerbose : engCfg.isLogVerbose());

        Logger.info("Starting Black Hole Simulation");
        var engine = new Engine("Black Hole Simulation", new Main());
        Logger.info("Started application");
        engine.run();
    }

    /** 清理资源，当前无需额外操作 */
    @Override
    public void cleanup() {
    }

    /**
     * 初始化游戏逻辑。
     * 根据窗口尺寸设置投影矩阵的宽高比。
     */
    @Override
    public void init(EngCtx engCtx) {
        engCtx.scene().getProjection().resize(engCtx.window().getWidth(), engCtx.window().getHeight());
    }

    /** 每渲染帧输入处理（委托 {@link InputController#handleInput}） */
    @Override
    public void input(EngCtx engCtx, long diffTimeMillis) {
        inputController.handleInput(engCtx, diffTimeMillis);
    }

    /** 每帧更新逻辑（UPS 节拍）：测地模式周期性控制台读数 */
    @Override
    public void update(EngCtx engCtx, long diffTimeMillis) {
        inputController.periodicReadout(engCtx);
    }
}
