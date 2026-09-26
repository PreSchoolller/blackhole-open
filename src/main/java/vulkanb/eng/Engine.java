package vulkanb.eng;

import vulkanb.Main;
import vulkanb.eng.graph.Render;
import vulkanb.eng.scene.Scene;
import vulkanb.eng.wnd.Window;

/**
 * 引擎核心类 —— 管理窗口、场景和渲染的生命周期。
 * <p>
 * 主循环采用固定时间步更新 + 可变帧率渲染的模式：
 * <ol>
 *   <li>轮询 GLFW 事件并处理输入</li>
 *   <li>按配置的 UPS（Updates Per Second）频率执行逻辑更新</li>
 *   <li>每帧执行 Vulkan 渲染</li>
 *   <li>通过双缓冲索引（currentRenderFrame）实现帧间同步</li>
 * </ol>
 */
public class Engine {

    /** 引擎上下文：持有窗口和场景 */
    private final EngCtx engCtx;
    /** 游戏逻辑回调（由 Main 实现） */
    private final IGameLogic gameLogic;
    /** Vulkan 渲染器 */
    private final Render render;
    /** 当前渲染帧索引（0 或 1，用于双缓冲帧同步） */
    private int currentRenderFrame;
    /** 当前更新帧索引（0 或 1） */
    private int currentUpdateFrame;

    /**
     * 构造引擎并初始化所有子系统。
     *
     * @param windowTitle 窗口标题
     * @param appLogic    游戏逻辑实现（本项目为 {@link Main}）
     */
    public Engine(String windowTitle, IGameLogic appLogic) {
        this.gameLogic = appLogic;
        var window = new Window(windowTitle);
        engCtx = new EngCtx(window, new Scene(window));
        render = new Render(engCtx);
        // 先初始化游戏逻辑（设置投影矩阵等），再初始化渲染管线
        gameLogic.init(engCtx);
        render.init(engCtx, window.getWidth(), window.getHeight());
        currentUpdateFrame = 1;
        currentRenderFrame = 0;
    }

    /** 依次清理游戏逻辑、渲染管线和引擎上下文 */
    private void cleanup() {
        gameLogic.cleanup();
        render.cleanup();
        engCtx.cleanup();
    }

    /**
     * 主循环。
     * <p>
     * 采用半固定时间步模式：
     * - 输入处理始终在每帧执行
     * - 逻辑更新按 UPS 频率执行（默认 60 次/秒）
     * - 渲染每帧执行
     * <p>
     * 循环结束（窗口关闭）后执行资源清理。
     */
    public void run() {
        var engCfg = EngCfg.getInstance();
        long lastFrameTime = System.currentTimeMillis();
        // 每次更新的时间间隔（毫秒）
        float timeU = 1000.0f / engCfg.getUps();
        double deltaUpdate = 0;

        long updateTime = lastFrameTime;
        Window window = engCtx.window();
        while (!window.shouldClose()) {
            long now = System.currentTimeMillis();
            // 累积时间差，用于判断是否需要执行一次逻辑更新
            deltaUpdate += (now - lastFrameTime) / timeU;

            // 1. 轮询 GLFW 事件（键盘/鼠标回调在此触发）
            window.pollEvents();
            // 2. 处理输入（相机移动、旋转等），传入帧间时间差
            gameLogic.input(engCtx, now - lastFrameTime);
            // 3. 重置单帧输入状态
            window.resetInput();

            // 4. 按 UPS 频率执行逻辑更新
            if (deltaUpdate >= 1) {
                long diffTimeMillis = now - updateTime;
                gameLogic.update(engCtx, diffTimeMillis);
                updateTime = now;
                deltaUpdate--;
            }

            // 5. 执行 Vulkan 渲染（在渲染前检查是否需要重建）
            if (window.needsResize()) {
                window.resetResizeFlag();
                render.resize(engCtx);
                gameLogic.init(engCtx); // 重新初始化游戏逻辑以更新投影矩阵的宽高比
            }
            render.render(engCtx, currentRenderFrame);

            // 6. 交替双缓冲索引（0 ↔ 1），实现 CPU-GPU 并行
            currentUpdateFrame = (currentUpdateFrame + 1) % 2;
            currentRenderFrame = (currentRenderFrame + 1) % 2;

            lastFrameTime = now;
        }

        cleanup();
    }
}
