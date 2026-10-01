package vulkanb.eng;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import vulkanb.eng.graph.gui.Panels;
import vulkanb.eng.scene.Camera;
import vulkanb.eng.scene.GeodesicIntegrator;
import vulkanb.eng.scene.Scene;
import vulkanb.eng.wnd.KeyboardInput;
import vulkanb.eng.wnd.MouseInput;
import vulkanb.eng.wnd.Window;

import static org.lwjgl.glfw.GLFW.*;

/**
 * 输入控制器 —— 每渲染帧的按键/鼠标处理（从 Main 委托而来）。
 * <p>
 * 职责：
 * <ul>
 *   <li>F1 面板显隐 + GUI 帧构建（委托 {@link Panels}）,ImGui 抓键盘时抢占相机输入</li>
 *   <li>G 测地线相机进出（含 Phase 5 标架初始化）/ F 相机模式切换</li>
 *   <li>WASD/方向键移动、右键视角捕获与鼠标转视线、Q/E 翻滚、PageUp/PageDown 缩放升降</li>
 *   <li>测地模式专属：↑/↓ 时间流速、[/] 推力大小、R 圆轨道、W/S 推力、1/2 初速</li>
 *   <li>NumPad± 基准温度</li>
 * </ul>
 * 测地模式下积分器按渲染帧推进（不能放 update():UPS 节拍会让相机位置以 30Hz
 * 台阶式跳变,近距高倍放大下表现为黑洞抖动）。
 */
public class InputController {

    /** ImGui 面板（显隐切换 + 帧构建 + 鼠标悬停/键盘捕获状态） */
    private final Panels panels;
    /** 视角捕获状态：true = 光标隐藏，鼠标移动控制视角（右键单击切换） */
    private boolean lookCaptured = false;
    /** 测地模式控制台读数的上次打印时间 */
    private long geodesicReadoutNanos;
    /** 非测地模式（自由飞行/轨道）的穿越检测：上一帧相机位置（Rs） */
    private final Vector3f freePrevPos = new Vector3f();
    private boolean freePrevYValid;

    // ---- Phase 5:测地模式头部旋转与姿态推导暂存 ----
    /** 头部旋转（测地模式下鼠标/QE 滚转只转它;相机世界姿态由输运标架×headQuat 每帧推导） */
    private final Quaternionf headQuat = new Quaternionf();
    private final Matrix3f headMat = new Matrix3f();
    private final Matrix3f camAxes = new Matrix3f();
    private final Quaternionf derivedQuat = new Quaternionf();
    private final Vector3f[] triad = {new Vector3f(), new Vector3f(), new Vector3f()};
    private final Vector3f axisTmp = new Vector3f();

    public InputController(Panels panels) {
        this.panels = panels;
    }

    /**
     * 处理每帧输入（渲染帧节拍）。
     * 处理顺序：F1 面板显隐 → GUI 帧构建与键盘抢占 → 移动/测地推进 → 鼠标 → 模式键 → 参数键。
     *
     * @param engCtx          引擎上下文，包含窗口、场景等
     * @param diffTimeMillis  距上一帧的时间差（毫秒），用于帧率无关的移动
     */
    public void handleInput(EngCtx engCtx, long diffTimeMillis) {
        // F1：GUI 面板显隐（任何状态可用，优先于键盘抢占）
        if (engCtx.window().getKeyboardInput().keySinglePress(GLFW_KEY_F1)) {
            panels.toggleVisible();
        }

        // GUI 帧构建 + 键盘抢占：ImGui 捕获键盘时跳过相机/调参输入
        if (panels.buildFrame(engCtx, lookCaptured)) {
            return;
        }
        panels.accumulateFrame(diffTimeMillis);

        Window window = engCtx.window();
        Camera camera = engCtx.scene().getCamera();
        GeodesicIntegrator geodesic = engCtx.scene().getGeodesic();
        boolean geodesicOn = camera.getMode() == Camera.CameraMode.GEODESIC;
        var engCfg = EngCfg.getInstance();
        float cfgMouseSensitivity = engCfg.getMouseSensitivity();
        float cfgRollSpeed = engCfg.getRollSpeed();
        float cfgZoomSpeed = engCfg.getZoomSpeed();
        KeyboardInput ki = window.getKeyboardInput();

        // 移动步长：与帧时间成正比（轨道/自由视角通用）
        float move = diffTimeMillis * engCfg.getMoveSpeed();

        boolean toggled = handleGeodesicToggle(ki, camera, geodesic, geodesicOn);
        handleCameraModeSwitch(ki, camera, geodesicOn);
        if (toggled) {
            // 本帧刚进出：重判模式，避免用陈值多跑一次 stepGeodesic / 把鼠标转给 headQuat
            geodesicOn = camera.getMode() == Camera.CameraMode.GEODESIC;
        }

        // P：时间暂停/恢复（单帧触发）。冻结盘动画与测地相机推进，定格观察某一时刻；
        // 渲染时间与鼠标视角不受影响，TAA 在静止后继续累积出清晰静帧
        if (ki.keySinglePress(GLFW_KEY_P)) {
            Scene scene = engCtx.scene();
            scene.setTimePaused(!scene.isTimePaused());
            AppLog.info(scene.isTimePaused() ? "时间已暂停（P 恢复）" : "时间已恢复");
        }

        if (geodesicOn) {
            stepGeodesic(engCtx, camera, geodesic, ki, diffTimeMillis);
        } else {
            handleFreeMove(ki, camera, move);
        }

        handleMouseLook(engCtx, camera, geodesicOn, cfgMouseSensitivity);
        handleRoll(engCtx, camera, geodesic, geodesicOn, diffTimeMillis, cfgRollSpeed);
        handleModeKeys(ki, camera, geodesic, geodesicOn, move, cfgZoomSpeed);
        handleParamKeys(engCtx, ki, geodesic, engCfg);
        if (!geodesicOn) {
            handleFreeTraversal(engCtx, camera);
        } else {
            freePrevYValid = false; // 测地帧位置由积分器驱动（可能传送），作废穿越检测基准
        }
    }

    /**
     * 自由飞行/轨道模式的穿越检测：相机 y（自旋轴）帧间变号时插值赤道面穿越点，
     * 柱面半径 ρ<|a|（环内穿喉道，与测地 GetIntermediateSign 同规则）即翻转
     * iUniverseSign——shader 随即切到反宇宙侧渲染。符号经 KerrParams 与 GUI 复选框
     * 天然同步；测地模式不用此路径（积分器子步级检测更精细）。
     */
    private void handleFreeTraversal(EngCtx engCtx, Camera camera) {
        Scene scene = engCtx.scene();
        var spacetime = scene.getSpacetime();
        if (spacetime != Scene.SpacetimeMode.KERR && spacetime != Scene.SpacetimeMode.KERR_NPGS) {
            freePrevYValid = false;
            return;
        }
        Vector3f p = camera.getPosition();
        if (freePrevYValid && freePrevPos.y * p.y < 0) {
            double t = freePrevPos.y / (freePrevPos.y - p.y);
            double crossX = freePrevPos.x + t * (p.x - freePrevPos.x);
            double crossZ = freePrevPos.z + t * (p.z - freePrevPos.z);
            double rho = Math.sqrt(crossX * crossX + crossZ * crossZ);
            double ringA = Math.abs(scene.getKerrParams().spin) * 0.5;
            var kp = scene.getKerrParams();
            if (rho < ringA) {
                kp.universeSign = -kp.universeSign;
                AppLog.infof("自由飞行穿越喉道（ρ=%.3f < |a|=%.3f）→ 宇宙符号 %.0f",
                        rho, ringA, kp.universeSign);
            }
        }
        freePrevPos.set(p);
        freePrevYValid = true;
    }

    /** 测地模式周期性控制台读数（update() 的 UPS 节拍调用） */
    public void periodicReadout(EngCtx engCtx) {
        var geodesic = engCtx.scene().getGeodesic();
        if (!geodesic.isActive()) {
            return;
        }
        // 被甩远时能实时看到 r 暴涨（超过 ~500Rs 后渲染退化为噪点）；
        // 平均帧耗时用于区分"近距抖动"是 GPU 掉帧（帧耗时大）还是采样噪声（帧耗时正常）
        long now = System.nanoTime();
        if (now - geodesicReadoutNanos > 1_500_000_000L) {
            geodesicReadoutNanos = now;
            double avgFrameMs = panels.getAvgFrameMs();
            AppLog.infof("测地线: r = %.1f Rs, v = %.2f c, γ = %.2f, 平均帧耗时 = %.1f ms (≈%.0f FPS)%n",
                    geodesic.getRadius(), geodesic.getBeta().length(), geodesic.getGamma(),
                    avgFrameMs, avgFrameMs > 0 ? 1000.0 / avgFrameMs : 0);
            panels.resetFrameStats();
        }
    }

    /** G：进入/退出测地线模式（单帧触发）。触发时返回 true,调用方需重新判定模式 */
    private boolean handleGeodesicToggle(KeyboardInput ki, Camera camera,
                                         GeodesicIntegrator geodesic, boolean geodesicOn) {
        if (!ki.keySinglePress(GLFW_KEY_G)) {
            return false;
        }
        if (!geodesicOn) {
            // Phase 5:相机轴一律取自视图矩阵(ORBIT 下 freeOrientation 是陈值,不可用);
            // 标架腿 = 相机轴、headQuat = I → 相机姿态 = 标架×I = 相机轴,进入瞬间无跳变。
            // 注意 headQuat 不能预置为相机姿态:标架已含该旋转,再乘一次得 R²(视角翻倍)。
            Matrix4f view = camera.getViewMatrix();
            Matrix4f camToWorld = new Matrix4f(view).setTranslation(0, 0, 0).invert();
            Vector3f camXw = new Vector3f(camToWorld.m00(), camToWorld.m01(), camToWorld.m02());
            Vector3f camYw = new Vector3f(camToWorld.m10(), camToWorld.m11(), camToWorld.m12());
            Vector3f camZw = new Vector3f(camToWorld.m20(), camToWorld.m21(), camToWorld.m22());
            headQuat.identity();
            triad[0].set(camXw);
            triad[1].set(camYw);
            triad[2].set(camZw);
            camAxes.setColumn(0, triad[0]).setColumn(1, triad[1]).setColumn(2, triad[2]);
            camera.setOrientationRaw(derivedQuat.setFromNormalized(camAxes));
            geodesic.initialize(camera.getPosition(), camera.getViewDirection(), geodesic.getV0());
            geodesic.initializeTetrad(triad[0], triad[1], triad[2]);
            camera.setModeRaw(Camera.CameraMode.GEODESIC);
            AppLog.infof("测地线模式：开启（v0=%.2fc，W/S=推力，[/]=推力大小，↑/↓=时间流速，R=圆轨道，G=退出）%n",
                    geodesic.getV0());
        } else {
            // 先 deactivate（可能从奇异区弹出复位位置），再把（复位后的）位置交给自由视角相机
            geodesic.deactivate();
            camera.setPosition(geodesic.getPosition(new Vector3f()));
            camera.setModeRaw(Camera.CameraMode.FREE_FLY);
            AppLog.infof("测地线模式：关闭（当前位置 r = %.1f Rs）%n", geodesic.getRadius());
        }
        return true;
    }

    /** F：切换 轨道/自由视角 模式（测地模式下无效，测地由 G 管理） */
    private void handleCameraModeSwitch(KeyboardInput ki, Camera camera, boolean geodesicOn) {
        if (ki.keySinglePress(GLFW_KEY_F) && !geodesicOn) {
            Camera.CameraMode mode = camera.toggleMode();
            AppLog.info("相机模式切换为："
                    + (mode == Camera.CameraMode.FREE_FLY ? "自由视角（WASD 沿视线飞行）" : "轨道模式（始终看向黑洞）"));
        }
    }

    /** 测地模式：推进积分器 + 回写相机位置/姿态 + W/S 沿视线推力 */
    private void stepGeodesic(EngCtx engCtx, Camera camera, GeodesicIntegrator geodesic,
                              KeyboardInput ki, long diffTimeMillis) {
        // 每帧同步 GUI 自旋：积分器度规随 a* 变化（两种时空模式共用同一积分器）
        geodesic.setSpin(engCtx.scene().getKerrParams().spin);
        geodesic.setHeadRotation(headQuat);
        // 视界护栏随坠落演出开关联动（演出关 = 允许积分穿过视界/虫洞喉道）
        geodesic.setHorizonGuard(engCtx.scene().isHorizonFallEnabled());
        // 时间暂停（P 键）：跳过积分推进与推力——相机位置定格，鼠标转头(deriveCameraOrientation)
        // 仍生效，便于在冻结的画面里环顾
        if (!engCtx.scene().isTimePaused()) {
            if (geodesic.isFalling()) {
                // 坠落演出：积分器冻结，画面由 iFade 接管（淡出→传送→淡入）
                geodesic.updateFall(diffTimeMillis);
            } else {
                geodesic.step(diffTimeMillis);
                if (engCtx.scene().isHorizonFallEnabled() && geodesic.getRadius() < 1.02) {
                    geodesic.beginHorizonFall();
                    AppLog.info("已越过事件视界，正在拉回安全轨道…");
                }
            }
        }
        camera.setPosition(geodesic.getPosition(new Vector3f()));
        // Phase 5:相机世界姿态 = 输运标架 × 头部旋转(视线被时空拖拽的来源)
        deriveCameraOrientation(engCtx.scene());

        // W/S = 沿视线方向的推力（时间暂停时不施加）
        if (!engCtx.scene().isTimePaused()) {
            if (ki.keyPressed(GLFW_KEY_W)) {
                geodesic.applyThrust(camera.getViewDirection(), diffTimeMillis);
            }
            if (ki.keyPressed(GLFW_KEY_S)) {
                geodesic.applyThrust(camera.getViewDirection().negate(), diffTimeMillis);
            }
        }
    }

    /** 非测地模式（轨道/自由视角通用）：WASD + 上下方向键移动 */
    private void handleFreeMove(KeyboardInput ki, Camera camera, float move) {
        if (ki.keyPressed(GLFW_KEY_W)) {
            camera.moveForward(move);
        }
        if (ki.keyPressed(GLFW_KEY_S)) {
            camera.moveBackwards(move);
        }
        if (ki.keyPressed(GLFW_KEY_A)) {
            camera.moveLeft(move);
        }
        if (ki.keyPressed(GLFW_KEY_D)) {
            camera.moveRight(move);
        }
        if (ki.keyPressed(GLFW_KEY_UP)) {
            camera.moveUp(move);
        }
        if (ki.keyPressed(GLFW_KEY_DOWN)) {
            camera.moveDown(move);
        }
    }

    /**
     * 鼠标：右键单击切换视角捕获（GUI 悬停时抢占右键防误触）；
     * 捕获中移动控制视线——轨道 = 转轨道角,自由视角 = 转相机,
     * 测地线 = 转 headQuat（头部旋转在输运标架内生效,相机世界姿态每帧由标架+headQuat 推导）
     */
    private void handleMouseLook(EngCtx engCtx, Camera camera, boolean geodesicOn, float cfgMouseSensitivity) {
        MouseInput mi = engCtx.window().getMouseInput();
        if (!panels.isMouseHover() && mi.isRightButtonSinglePress()) {
            lookCaptured = !lookCaptured;
            if (lookCaptured) {
                engCtx.window().grabCursor();
            } else {
                engCtx.window().releaseCursor();
            }
            AppLog.info(lookCaptured ? "视角捕获：开（移动鼠标控制视角）" : "视角捕获：关");
        }

        if (!lookCaptured) {
            return;
        }
        Vector2f deltaPos = mi.getDeltaPos();
        if (camera.getMode() == Camera.CameraMode.ORBIT) {
            camera.addOrbitRotation(-deltaPos.x * cfgMouseSensitivity, deltaPos.y * cfgMouseSensitivity);
        } else if (geodesicOn) {
            // 与 rotateFree(-dx, dy) 完全同式:rotateY(yaw).rotateX(-pitch)
            headQuat.rotateY(-deltaPos.x * cfgMouseSensitivity)
                    .rotateX(-deltaPos.y * cfgMouseSensitivity).normalize();
        } else {
            camera.rotateFree(-deltaPos.x * cfgMouseSensitivity, deltaPos.y * cfgMouseSensitivity);
        }
    }

    /** Q/E：绕视线轴翻滚（Q = 向左滚/画面顺时针，E = 向右滚；任何时刻生效）。
     *  测地模式下滚的是 headQuat（与鼠标同理由,addRoll 同式 rotateZ） */
    private void handleRoll(EngCtx engCtx, Camera camera, GeodesicIntegrator geodesic,
                            boolean geodesicOn, long diffTimeMillis, float cfgRollSpeed) {
        KeyboardInput ki = engCtx.window().getKeyboardInput();
        if (ki.keyPressed(GLFW_KEY_Q)) {
            if (geodesicOn) {
                headQuat.rotateZ(diffTimeMillis * cfgRollSpeed).normalize();
            } else {
                camera.addRoll(diffTimeMillis * cfgRollSpeed);
            }
        }
        if (ki.keyPressed(GLFW_KEY_E)) {
            if (geodesicOn) {
                headQuat.rotateZ(-diffTimeMillis * cfgRollSpeed).normalize();
            } else {
                camera.addRoll(-diffTimeMillis * cfgRollSpeed);
            }
        }
    }

    /** 模式相关按键：测地（↑/↓ 时间流速、[/] 推力、R 圆轨道）/ 自由视角（PgUp/PgDn、Shift/Ctrl 升降）/ 轨道（PgUp/PgDn 缩放） */
    private void handleModeKeys(KeyboardInput ki, Camera camera, GeodesicIntegrator geodesic,
                                boolean geodesicOn, float move, float cfgZoomSpeed) {
        if (geodesicOn) {
            // 测地模式：↑/↓ = 时间流速增减（×1.25）
            if (ki.keySinglePress(GLFW_KEY_UP)) {
                geodesic.scaleTime(1.25);
            }
            if (ki.keySinglePress(GLFW_KEY_DOWN)) {
                geodesic.scaleTime(1.0 / 1.25);
            }
            if (ki.keySinglePress(GLFW_KEY_LEFT_BRACKET)) {
                geodesic.scaleThrust(1.0 / 1.25);
                AppLog.infof("推力大小：%.2f%n", geodesic.getThrust());
            }
            if (ki.keySinglePress(GLFW_KEY_RIGHT_BRACKET)) {
                geodesic.scaleThrust(1.25);
                AppLog.infof("推力大小：%.2f%n", geodesic.getThrust());
            }
            if (ki.keySinglePress(GLFW_KEY_R)) {
                String msg = geodesic.initializeCircularOrbit();
                AppLog.info(msg != null ? "测地线初始化：" + msg : "当前半径无法稳定圆轨道（过低，接近光子球）");
            }
        } else if (camera.getMode() == Camera.CameraMode.FREE_FLY) {
            // 升降（沿相机上方向，与方向键一致）
            if (ki.keyPressed(GLFW_KEY_PAGE_UP) || ki.keyPressed(GLFW_KEY_LEFT_SHIFT)) {
                camera.moveUp(move);
            }
            if (ki.keyPressed(GLFW_KEY_PAGE_DOWN) || ki.keyPressed(GLFW_KEY_LEFT_CONTROL)) {
                camera.moveDown(move);
            }
        } else {
            if (ki.keyPressed(GLFW_KEY_PAGE_UP)) {
                camera.zoom(-cfgZoomSpeed);
            }
            if (ki.keyPressed(GLFW_KEY_PAGE_DOWN)) {
                camera.zoom(cfgZoomSpeed);
            }
        }
    }

    /** 全局参数键：1/2 测地初速 v0 ±0.05c（进入测地模式前设置）、NumPad± 基准温度 */
    private void handleParamKeys(EngCtx engCtx, KeyboardInput ki, GeodesicIntegrator geodesic, EngCfg engCfg) {
        if (ki.keySinglePress(GLFW_KEY_1)) {
            geodesic.adjustV0(-0.05);
            AppLog.infof("测地线初速度：%.2f c%n", geodesic.getV0());
        }
        if (ki.keySinglePress(GLFW_KEY_2)) {
            geodesic.adjustV0(0.05);
            AppLog.infof("测地线初速度：%.2f c%n", geodesic.getV0());
        }

        // NumPad ±：基准温度（与 Black Hole 面板滑条并行写同一字段）
        if (ki.keySinglePress(GLFW_KEY_KP_ADD)) {
            var sp = engCtx.scene().getSchwarzschildParams();
            sp.baseTemperature = Math.min(engCfg.getTemperatureMax(),
                    sp.baseTemperature + engCfg.getTemperatureStep());
            AppLog.info("当前温度为： " + sp.baseTemperature);
        }
        if (ki.keySinglePress(GLFW_KEY_KP_SUBTRACT)) {
            var sp = engCtx.scene().getSchwarzschildParams();
            sp.baseTemperature = Math.max(engCfg.getTemperatureMin(),
                    sp.baseTemperature - engCfg.getTemperatureStep());
            AppLog.info("当前温度为： " + sp.baseTemperature);
        }
    }

    /**
     * Phase 5 核心:由输运标架 + 头部旋转推导相机世界姿态。
     * 相机第 i 条世界轴 = Σ_j H[i][j]·t_j(H=头部旋转阵,t_j=输运标架腿);
     * 与 GeodesicIntegrator.getFoldedTetrad 的折叠式严格一致,保证
     * 着色器观者模式 -1 的光子四动量与屏幕射线方向同源。
     * H=I 时相机轴=标架腿本身——视线随测地线被时空拖拽,这就是"扭曲感"的来源。
     */
    private void deriveCameraOrientation(Scene scene) {
        var g = scene.getGeodesic();
        for (int j = 0; j < 3; j++) {
            g.getTetradSpatial(j, triad[j]);
        }
        headMat.rotation(headQuat);
        for (int i = 0; i < 3; i++) {
            axisTmp.set(0.0f, 0.0f, 0.0f);
            for (int j = 0; j < 3; j++) {
                // M = T·H(M_col_i = Σ_j t_j·H[j][i])——与 getFoldedTetrad 同约定;
                // joml Matrix3f.get(column,row) → 元素 H[j][i] 写作 get(i, j)
                axisTmp.add(new Vector3f(triad[j]).mul(headMat.get(i, j)));
            }
            camAxes.setColumn(i, axisTmp);
        }
        scene.getCamera().setOrientationRaw(derivedQuat.setFromNormalized(camAxes));
    }
}
