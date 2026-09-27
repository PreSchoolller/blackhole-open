package vulkanb.eng.graph.gui;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiMouseButton;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;
import vulkanb.eng.AppLog;
import vulkanb.eng.EngCtx;
import vulkanb.eng.EngCfg;
import vulkanb.eng.graph.BlackHoleRender;
import vulkanb.eng.scene.Camera;
import vulkanb.eng.scene.KerrParams;
import vulkanb.eng.scene.Scene;
import vulkanb.eng.wnd.Window;

import org.joml.Vector3f;

/**
 * ImGui 面板绘制与帧构建（与 {@link GuiRender} 的 Vulkan 绘制分工：本类只生成 draw data）。
 * <p>
 * 面板分工：
 * <ul>
 *   <li><b>Black Hole Telemetry</b>（左上）—— 只读遥测：相机/测地线/渲染/场景</li>
 *   <li><b>Controls</b>（左下，全模式可见）—— 时空模式单选 + Temp + 测地相机滑条</li>
 *   <li><b>Kerr Disk</b>（右上，仅克尔模式）—— {@link KerrParams} 全部可调参数</li>
 *   <li><b>Keys</b>（右上角，默认折叠）—— 键位表。注意窗口标题不能与其他面板重名——
 *       ImGui 按标题识别窗口,同名 begin 会把内容追加进同一窗口</li>
 * </ul>
 * 状态：面板显隐（F1）、FPS 统计（1.5s 窗口均值,遥测与控制台读数共用）。
 */
public class Panels {

    /** GUI 面板显隐（F1 切换，默认显示） */
    private boolean visible = true;
    /** 本帧 GUI 是否捕获鼠标（buildFrame 更新；捕获中不响应右键切视角等相机鼠标操作） */
    private boolean mouseHover;
    /** 帧耗时累计（毫秒，用于平均 FPS：遥测面板 + 控制台周期读数共用） */
    private long frameAccumMillis;
    private int frameAccumCount;
    /** 上一次喂给 ImGui 的鼠标按钮是否仍处于按下态（进视角捕获时据此补发抬起） */
    private boolean mouseDownFed;
    /** 滑条左标签列宽（像素）：取历史最大测量宽，稳态即最长标签的像素宽 */
    private float labelCol;
    /** 标签列与滑条之间的间距（对齐 style.ItemInnerSpacing.x 默认值） */
    private static final float LABEL_GAP = 4f;

    /** F1：切换面板显隐 */
    public void toggleVisible() {
        visible = !visible;
    }

    /** 本帧 GUI 是否捕获鼠标（拖窗口/滑条悬停等） */
    public boolean isMouseHover() {
        return mouseHover;
    }

    /** 帧耗时累计（每渲染帧调用一次） */
    public void accumulateFrame(long diffTimeMillis) {
        frameAccumMillis += diffTimeMillis;
        frameAccumCount++;
    }

    /** 平均帧耗时（毫秒；窗口内无样本返回 0） */
    public double getAvgFrameMs() {
        return frameAccumCount > 0 ? (double) frameAccumMillis / frameAccumCount : 0;
    }

    /** 清零帧统计（控制台周期读数消费后重置窗口） */
    public void resetFrameStats() {
        frameAccumMillis = 0;
        frameAccumCount = 0;
    }

    /**
     * 构建一帧 ImGui UI（喂鼠标事件 → newFrame → 面板 → endFrame → render）。
     * <p>
     * 视角捕获（lookCaptured）期间光标隐藏：不喂鼠标事件（面板只读不可拖动，
     * 但照常显示——遥测在飞行中正有用处；显隐由 F1 手动控制）。
     *
     * @return ImGui 是否想捕获键盘（应用侧据此抢占相机/调参按键）
     */
    public boolean buildFrame(EngCtx engCtx, boolean lookCaptured) {
        ImGuiIO io = ImGui.getIO();
        var mi = engCtx.window().getMouseInput();
        if (lookCaptured) {
            // 捕获中（光标隐藏）不喂鼠标事件；但若进捕获时仍有按钮停在 ImGui 的按下态，
            // 补发一次抬起——否则 ImGui 的 MouseDown 永久卡住（右键单击切捕获必现）。
            if (mouseDownFed) {
                io.addMouseButtonEvent(ImGuiMouseButton.Left, false);
                io.addMouseButtonEvent(ImGuiMouseButton.Right, false);
                mouseDownFed = false;
            }
            mi.clearPendingButtons();
        } else {
            io.addMousePosEvent(mi.getCurrentPos().x, mi.getCurrentPos().y);
            for (int i = 0, n = mi.pendingButtonCount(); i < n; i++) {
                io.addMouseButtonEvent(mi.pendingButton(i), mi.pendingButtonDown(i));
            }
            io.addMouseWheelEvent(mi.consumeScrollX(), mi.consumeScrollY());
            mi.clearPendingButtons();
            mouseDownFed = mi.isLeftButtonPressed() || mi.isRightButtonPressed();
        }

        ImGui.newFrame();
        if (visible) {
            renderTelemetry(engCtx);
            renderOriginIndicator(engCtx);
            renderControlPanel(engCtx);
            renderKeys();
            if (engCtx.scene().getSpacetime() == Scene.SpacetimeMode.KERR) {
                renderKerrPanel(engCtx.scene());
            }
        }
        ImGui.endFrame();
        ImGui.render();

        mouseHover = !lookCaptured && io.getWantCaptureMouse();
        return io.getWantCaptureKeyboard();
    }

    /**
     * 只读遥测面板：相机/测地线/渲染/场景四组数据。
     * 全部 ImGui.text，无输入控件；FPS 复用 frameAccum* 周期累计器（1.5s 窗口均值）。
     * 滑条与模式切换见 Controls 面板（独立，全模式可见）。
     */
    private void renderTelemetry(EngCtx engCtx) {
        ImGui.setNextWindowPos(8, 8, ImGuiCond.FirstUseEver);
        ImGui.begin("Black Hole Telemetry");

        var scene = engCtx.scene();
        Camera camera = scene.getCamera();
        var geodesic = scene.getGeodesic();
        var engCfg = EngCfg.getInstance();
        Window window = engCtx.window();

        // [相机]
        ImGui.text("Camera");
        Vector3f pos = camera.getPosition();
        ImGui.text(String.format("  Mode: %s", camera.getMode()));
        ImGui.text(scene.isTimePaused() ? "  Time: PAUSED (P to resume)" : "  Time: running");
        ImGui.text(String.format("  Position: (%.2f, %.2f, %.2f) Rs", pos.x, pos.y, pos.z));
        double r = geodesic.isActive() ? geodesic.getRadius() : pos.length();
        ImGui.text(String.format("  Distance r: %.2f Rs", r));

        // [测地线]
        ImGui.separator();
        ImGui.text("Geodesic");
        if (geodesic.isActive()) {
            Vector3f beta = geodesic.getBeta();
            // 希腊字母 β/γ 不在 ImGui 默认字体的字形范围内（显示为 ?），用 ASCII 拼写
            ImGui.text(String.format("  beta = (%.3f, %.3f, %.3f)",
                    beta.x, beta.y, beta.z));
            ImGui.text(String.format("  |beta| = %.3f c", beta.length()));
            ImGui.text(String.format("  gamma = %.4f", geodesic.getGamma()));
            ImGui.text(String.format("  E = %.5f (conserved, drift = num.err)", geodesic.getEnergy()));
            ImGui.text(String.format("  TimeRate ×%.2f   Thrust %.2f   v0 %.2f c",
                    geodesic.getTimeScale(), geodesic.getThrust(), geodesic.getV0()));
            if (geodesic.isFalling()) {
                ImGui.text(String.format("  Falling iFade = %.2f", geodesic.getHorizonFade()));
            }
        } else {
            ImGui.textDisabled("  Inactive(G active)");
        }

        // [渲染]
        ImGui.separator();
        ImGui.text("Rendering");
        double avgFrameMs = getAvgFrameMs();
        double fps = avgFrameMs > 0 ? 1000.0 / avgFrameMs : 0;
        ImGui.text(String.format("  FPS : %.0f(FrameTime %.1f ms)", fps, avgFrameMs));
        ImGui.text(String.format("  Resolution: %d x %d", window.getWidth(), window.getHeight()));
        ImGui.text(String.format("  Window: %d x %d pts (scale %.1f)",
                window.getLogicalWidth(), window.getLogicalHeight(), window.getContentScale()));
        ImGui.text(String.format("  BaseTemperature: %.0f K(NumPad +/- Adjust)", BlackHoleRender.BaseTemperature));

        // [场景]
        ImGui.separator();
        ImGui.text("Scene");
        ImGui.text(String.format("  Rs = %.2f   Disk %.1f ~ %.1f Rs",
                engCfg.getSchwarzschildRadius(), engCfg.getDiskInnerRadius(), engCfg.getDiskOuterRadius()));
        ImGui.text(String.format("  fov = %.0f°", Math.toDegrees(engCfg.getFov())));
        ImGui.text("  F1 HidePanel");

        // 本面板只保留只读遥测；滑条与时空模式切换在 Controls 面板
        ImGui.end();
    }

    /**
     * 控制面板（全模式可见）——时空模式单选 + 相机/渲染滑条。
     * Temp 只作用于史瓦西渲染器；TimeRate/Thrust/v0/Orbit tilt 为测地线相机参数，
     * 两种时空下测地模式（G 键）均可用，故不随模式隐藏。
     */
    private void renderControlPanel(EngCtx engCtx) {
        var scene = engCtx.scene();
        var geodesic = scene.getGeodesic();
        var engCfg = EngCfg.getInstance();

        ImGui.setNextWindowPos(8, 580, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(350, 250);
        ImGui.begin("Controls");

        // 时空模式：史瓦西 / 克尔（热切换，下一帧生效；测地模式可跨切换保持）
        ImGui.text("Spacetime");
        if (ImGui.radioButton("Schwarzschild", scene.getSpacetime() == Scene.SpacetimeMode.SCHWARZSCHILD)) {
            scene.setSpacetime(Scene.SpacetimeMode.SCHWARZSCHILD);
        }
        ImGui.sameLine();
        if (ImGui.radioButton("Kerr", scene.getSpacetime() == Scene.SpacetimeMode.KERR)) {
            scene.setSpacetime(Scene.SpacetimeMode.KERR);
        }

        // 滑条（与快捷键并行生效；拖动即改，钳制范围与快捷键一致）
        ImGui.separator();
        ImGui.text("Camera / Rendering");
        float[] temp = {BlackHoleRender.BaseTemperature};
        if (sliderL("Temp (K)", temp,
                engCfg.getTemperatureMin(), engCfg.getTemperatureMax(), "%.0f")) {
            BlackHoleRender.BaseTemperature = temp[0];
        }
        float[] ts = {(float) geodesic.getTimeScale()};
        if (sliderL("TimeRate", ts, 0.1f, 50.0f, "%.2f")) {
            geodesic.setTimeScale(ts[0]);
        }
        float[] th = {(float) geodesic.getThrust()};
        if (sliderL("Thrust", th, 0.05f, 8.0f, "%.2f")) {
            geodesic.setThrust(th[0]);
        }
        float[] v0 = {(float) geodesic.getV0()};
        if (sliderL("v0 (c)", v0, 0.05f, 0.99f, "%.2f")) {
            geodesic.setV0(v0[0]);
        }
        // 圆轨道倾角：0=贴吸积盘平面，90=过极子午面；改后按 R 以新倾角初始化
        float[] tilt = {(float) geodesic.getOrbitTilt()};
        if (sliderL("Orbit tilt (R)", tilt, 0.0f, 90.0f, "%.0f deg")) {
            geodesic.setOrbitTilt(tilt[0]);
        }
        // 山海星空盒（两渲染器共用：各渲染器在自己天空盒描述符槽位换绑，着色器零改动）
        boolean mountainsSeas = scene.isMountainsSeasSkybox();
        if (ImGui.checkbox("Mountains & Seas skybox", mountainsSeas)) {
            scene.setMountainsSeasSkybox(!mountainsSeas);
        }

        // 应用自定义日志开关（初始值来自配置/命令行,此处运行时切换）
        boolean verbose = AppLog.isVerbose();
        if (ImGui.checkbox("Verbose log", verbose)) {
            AppLog.setVerbose(!verbose);
        }

        ImGui.end();
    }

    /**
     * Kerr 参数面板（Phase 2.5,面板补齐）—— 仅克尔时空模式渲染（随模式显隐，受 F1 门控）。
     * 滑条直写 {@link KerrParams}，KerrRender 每帧打包进 UBO，下一帧即生效；
     * 只读区展示 ISCO 内缘与盘峰值温度（与 kerr.frag ComputeDiskArgument 同源公式的 Java 侧镜像；
     * ISCO 含 Kerr–Newman 电荷项,a²+Q²>1 时显示裸奇点警告）。
     */
    private void renderKerrPanel(Scene scene) {
        KerrParams kp = scene.getKerrParams();
        float w = ImGui.getIO().getDisplaySizeX();
        ImGui.setNextWindowPos(w - 250, 100, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(400, 500);
        ImGui.begin("Kerr Disk");

        ImGui.text("Spacetime / Time");
        float[] spin = {kp.spin};
        if (sliderL("Spin a*", spin, 0.0f, 0.998f, "%.3f")) {
            kp.spin = spin[0];
        }
        float[] q = {kp.qStar};
        if (sliderL("Charge Q* (KN)", q, 0.0f, 0.9f, "%.3f")) {
            kp.qStar = q[0];
        }
        float[] tsc = {kp.timeScale};
        if (sliderL("Disk TimeScale", tsc, 0.0f, 50.0f, "%.2f")) {
            kp.timeScale = tsc[0];
        }

        ImGui.separator();
        ImGui.text("Physics");
        float[] v;
        v = new float[]{kp.accretionRate};
        if (sliderL("Accretion rate", v, 1e-4f, 1e-1f, "%.4f Ed")) {
            kp.accretionRate = v[0];
        }
        v = new float[]{kp.mu};
        if (sliderL("Mu (spec. charge)", v, 0.1f, 5.0f, "%.2f")) {
            kp.mu = v[0];
        }
        v = new float[]{kp.outerRadiusRs};
        if (sliderL("Outer radius", v, 5.0f, 60.0f, "%.1f Rs")) {
            kp.outerRadiusRs = v[0];
        }
        v = new float[]{kp.thinRs};
        if (sliderL("Disk half-thickness", v, 0.05f, 2.0f, "%.2f Rs")) {
            kp.thinRs = v[0];
        }
        v = new float[]{kp.hopper};
        if (sliderL("Thickness slope", v, 0.0f, 1.0f, "%.2f")) {
            kp.hopper = v[0];
        }
        // 只读：ISCO 内缘（含 KN 电荷项）与峰值温度（自旋/电荷/μ 变化即刷新）
        if (KerrParams.isNakedSingularity(kp.spin, kp.qStar)) {
            ImGui.textColored(1.0f, 0.4f, 0.2f, 1.0f, "  Naked singularity (a^2+Q^2>1)");
        } else {
            ImGui.text(String.format("  ISCO inner edge: %.3f Rs",
                    KerrParams.iscoInnerRadiusRs(kp.spin, kp.qStar)));
        }
        ImGui.text(String.format("  Peak disk temp:  %.0f K",
                KerrParams.peakTemperatureK(kp.spin, kp.mu, kp.accretionRate)));

        ImGui.separator();
        ImGui.text("Color / Post");
        v = new float[]{kp.backShiftMax};
        if (sliderL("Back shift max", v, 1.0f, 4.0f, "%.2f")) {
            kp.backShiftMax = v[0];
        }
        v = new float[]{kp.reddening};
        if (sliderL("Reddening", v, 0.0f, 1.0f, "%.2f")) {
            kp.reddening = v[0];
        }
        v = new float[]{kp.saturation};
        if (sliderL("Saturation", v, 0.0f, 1.0f, "%.2f")) {
            kp.saturation = v[0];
        }
        v = new float[]{kp.boostRot};
        if (sliderL("Asymmetry boost", v, 0.0f, 2.0f, "%.2f")) {
            kp.boostRot = v[0];
        }
        v = new float[]{kp.photonRingBoost};
        if (sliderL("Photon ring boost", v, 0.0f, 10.0f, "%.2f")) {
            kp.photonRingBoost = v[0];
        }
        v = new float[]{kp.photonRingColorTempBoost};
        if (sliderL("Photon ring blue", v, 0.0f, 10.0f, "%.2f")) {
            kp.photonRingColorTempBoost = v[0];
        }

        ImGui.separator();
        ImGui.text("Display");
        v = new float[]{kp.brightmut};
        if (sliderL("Brightness (iBrightmut)", v, 0.0f, 6.0f, "%.2f")) {
            kp.brightmut = v[0];
        }
        v = new float[]{kp.darkmut};
        if (sliderL("Opacity (iDarkmut)", v, 0.0f, 1.0f, "%.2f")) {
            kp.darkmut = v[0];
        }
        v = new float[]{kp.blackbodyIntensityExponent};
        if (sliderL("T^exp (contrast)", v, 0.25f, 8.0f, "%.2f")) {
            kp.blackbodyIntensityExponent = v[0];
        }
        v = new float[]{kp.redShiftColorExponent};
        if (sliderL("Shift->color exp", v, 0.0f, 3.0f, "%.2f")) {
            kp.redShiftColorExponent = v[0];
        }
        v = new float[]{kp.redShiftIntensityExponent};
        if (sliderL("Shift->bright exp", v, 0.0f, 8.0f, "%.2f")) {
            kp.redShiftIntensityExponent = v[0];
        }
        v = new float[]{kp.backgroundBrightmut};
        if (sliderL("Background bright", v, 0.0f, 3.0f, "%.2f")) {
            kp.backgroundBrightmut = v[0];
        }
        v = new float[]{kp.quality};
        if (sliderL("Quality (step)", v, 0.2f, 1.0f, "%.2f")) {
            kp.quality = v[0];
        }
        boolean prepass = kp.prepassEnabled;
        if (ImGui.checkbox("Prepass (half-res)", prepass)) {
            kp.prepassEnabled = !prepass;
        }
        boolean noiseLut = kp.noiseLutEnabled;
        if (ImGui.checkbox("Noise LUT (A/B)", noiseLut)) {
            kp.noiseLutEnabled = !noiseLut;
        }

        ImGui.separator();
        ImGui.text("Bloom (NPGS mip-tree, 0 = pure ColorBlend)");
        v = new float[]{kp.bloomStrength};
        if (sliderL("Strength", v, 0.0f, 2.0f, "%.2f")) {
            kp.bloomStrength = v[0];
        }

        ImGui.separator();
        ImGui.text("Jet (needs accretion rate >= 0.01)");
        v = new float[]{kp.jetBrightmut};
        if (sliderL("Jet brightness", v, 0.0f, 3.0f, "%.2f")) {
            kp.jetBrightmut = v[0];
        }
        v = new float[]{kp.jetRedShiftIntensityExponent};
        if (sliderL("Jet shift->bright exp", v, 0.0f, 8.0f, "%.2f")) {
            kp.jetRedShiftIntensityExponent = v[0];
        }
        v = new float[]{kp.jetSaturation};
        if (sliderL("Jet saturation", v, 0.0f, 1.0f, "%.2f")) {
            kp.jetSaturation = v[0];
        }
        v = new float[]{kp.jetShiftMax};
        if (sliderL("Jet shift max", v, 1.0f, 8.0f, "%.2f")) {
            kp.jetShiftMax = v[0];
        }

        ImGui.separator();
        ImGui.text("Heat haze");
        boolean haze = kp.enableHeatHaze;
        if (ImGui.checkbox("Enable heat haze", haze)) {
            kp.enableHeatHaze = !haze;
        }
        v = new float[]{kp.heatHaze};
        if (sliderL("Haze strength", v, 0.0f, 2.0f, "%.2f")) {
            kp.heatHaze = v[0];
        }

        ImGui.end();
    }

    /**
     * 键位表面板（右上角，只读，默认折叠）。
     * 注意窗口标题不能与 Controls 重名——ImGui 按标题识别窗口,
     * 同名 begin 会把两份内容追加进同一窗口。
     */
    private void renderKeys() {
        float w = ImGui.getIO().getDisplaySizeX();
        ImGui.setNextWindowPos(w - 250, 8, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowCollapsed(true, ImGuiCond.FirstUseEver);
        ImGui.begin("Keys");
        helpRow("F1", "Toggle panels");
        helpRow("F", "Orbit / Free-Fly camera");
        helpRow("G", "Geodesic mode on/off");
        helpRow("Right-click", "Look capture on/off");
        helpRow("WASD", "Move (Geodesic: W/S thrust)");
        helpRow("Up / Down", "Move (Geodesic: TimeRate)");
        helpRow("PgUp / PgDn", "Zoom (Orbit) / Rise-Sink");
        helpRow("Shift / Ctrl", "Rise / Sink (Free-Fly)");
        helpRow("Q / E", "Roll left / right");
        helpRow("[ / ]", "Thrust scale (Geodesic)");
        helpRow("R", "Circular orbit (Geodesic)");
        helpRow("1 / 2", "v0 -/+ 0.05c");
        helpRow("Numpad +/-", "Temperature -/+ (K)");
        helpRow("P", "Pause time (Geodesic)");
        ImGui.end();
    }

    /** 键位表单行：左列键位（固定列宽对齐），右列说明 */
    private void helpRow(String key, String desc) {
        ImGui.text(key);
        ImGui.sameLine(110);
        ImGui.text(desc);
    }

    /**
     * 左标签滑条：标签画在左列，滑条本体填满左列右侧的剩余宽度。
     * <p>
     * 为什么不用 ImGui 原生排版：Slider/Drag 这类"值编辑"控件默认是<b>框在左、标签在右</b>，
     * 而滑条的默认 ItemWidth = -1（铺满到右边缘），标签于是恒被挤到窗口外裁掉，越走马灯
     * 式缩短面板越明显。把标签挪进左列后，面板缩到多窄标签都完整可见（列宽自适应，见
     * {@link #labelCol}）。
     * <p>
     * 滑条自身标签传 {@code "##" + label}：渲染出来只有一个空格（不占可见标签），
     * 但保留字高使框高与原生一致（空标签会让框塌成 FramePadding*2 高）；ID 由 ## 后的
     * 整串生成，与同名遥测文本等不冲突。
     */
    private boolean sliderL(String label, float[] v, float min, float max, String fmt) {
        return sliderL(label, v, min, max, fmt, 0);
    }

    private boolean sliderL(String label, float[] v, float min, float max, String fmt, int flags) {
        labelCol = Math.max(labelCol, ImGui.calcTextSizeX(label));
        ImGui.alignTextToFramePadding();
        ImGui.textUnformatted(label);
        ImGui.sameLine(labelCol + LABEL_GAP);
        // -1 = 铺到窗口右边缘；再让出 12px 容纳不可见标签及其间距，避免内容宽度溢出
        ImGui.setNextItemWidth(-13f);
        return ImGui.sliderFloat("##" + label, v, min, max, fmt, flags);
    }

    private void renderOriginIndicator(EngCtx engCtx) {
        float W = engCtx.window().getWidth();
        float H = engCtx.window().getHeight();

        Camera camera = engCtx.scene().getCamera();
        Vector3f pos = camera.getPosition();
        Vector3f toOrigin = new Vector3f(-pos.x, -pos.y, -pos.z);
        float dist = toOrigin.length();

        ImDrawList dl = ImGui.getForegroundDrawList();
        ImVec2 center = new ImVec2(W * 0.5f, H * 0.5f);

        // --- 对准时淡出：夹角小于 HIDE 完全不画，FADE 到 HIDE 之间渐隐 ---
        final float FADE = 60.0f;   // 开始淡出
        final float HIDE = 30.0f;   // 完全隐藏

        Matrix4f view = camera.getViewMatrix();
        Vector3f o = new Vector3f(0, 0, 0);
        view.transformPosition(o);

        float cosA = -o.z / o.length();
        cosA = Math.max(-1f, Math.min(1f, cosA));
        float errDeg = (float) Math.toDegrees(Math.acos(cosA));

        if (errDeg <= HIDE) return;

        float alpha = (errDeg - HIDE) / (FADE - HIDE);
        alpha = Math.max(0f, Math.min(1f, alpha));
        alpha = alpha * alpha * (3f - 2f * alpha);
        // 暖黄色，和 3D 侧的原点标记保持一致
        final int COL = ImGui.getColorU32(1.00f, 0.82f, 0.35f, alpha);

        // 就在原点附近，方向没意义
        if (dist < 1e-3f) {
            String label = "AT ORIGIN";
            ImVec2 ts = ImGui.calcTextSize(label);
            dl.addText(new ImVec2(center.x - ts.x * 0.5f, center.y + 40f), COL, label);
            return;
        }

        Matrix4f viewProj = new Matrix4f(engCtx.scene().getProjection().getProjectionMatrix()).mul(view);

        // --- 视图空间方向：对前方/后方都稳，不会因为 w<0 翻符号 ---
        Vector4f v = new Vector4f(0f, 0f, 0f, 1f);
        view.transform(v);
        Vector2f dir = new Vector2f(v.x, -v.y);          // 屏幕 y 向下
        if (dir.lengthSquared() < 1e-6f) dir.set(0f, -1f);
        dir.normalize();

        // --- 裁剪空间判断是否落在屏幕内 ---
        Vector4f clip = new Vector4f(0f, 0f, 0f, 1f);
        viewProj.transform(clip);

        boolean onScreen = false;
        ImVec2 scr = new ImVec2();
        if (clip.w > 1e-4f) {
            float nx = clip.x / clip.w;
            float ny = clip.y / clip.w;
            // 留边距，避免贴边时圈/箭头来回闪
            if (Math.abs(nx) < 0.96f && Math.abs(ny) < 0.94f) {
                onScreen = true;
                scr.x = center.x + nx * W * 0.5f;
                scr.y = center.y - ny * H * 0.5f;
            }
        }

        String distStr = formatDistance(dist);

        if (onScreen) {
            // ---- 屏幕内：圈 + 距离 ----
            dl.addCircle(scr.x, scr.y, 10f, COL, 48, 2.0f);
            dl.addCircleFilled(scr.x, scr.y, 2.5f, COL, 16);
            ImVec2 ts = ImGui.calcTextSize(distStr);
            dl.addText(new ImVec2(scr.x - ts.x * 0.5f, scr.y + 14f), COL, distStr);
        } else {
            // ---- 屏幕外：边缘箭头 ----
            float margin = 56f;
            float rx = center.x - margin;
            float ry = center.y - margin;
            float t  = 1f / Math.max(Math.abs(dir.x) / rx, Math.abs(dir.y) / ry);
            float tipX = center.x + dir.x * t;
            float tipY = center.y + dir.y * t;

            float len = 22f, wid = 10f;
            float baseX = tipX - dir.x * len;
            float baseY = tipY - dir.y * len;
            float px = -dir.y, py = dir.x;

            dl.addTriangleFilled(
                    tipX, tipY,
                    baseX + px * wid, baseY + py * wid,
                    baseX - px * wid, baseY - py * wid,
                    COL);

            ImVec2 ts = ImGui.calcTextSize(distStr);
            dl.addText(new ImVec2(baseX - dir.x * 16f - ts.x * 0.5f,
                            baseY - dir.y * 16f - ts.y * 0.5f),
                    COL, distStr);
        }
    }

    private static String formatDistance(float d) {
        if (d >= 1000f) return String.format("%.2f kRs", d / 1000f);
        if (d >= 10f)   return String.format("%.1f Rs",  d);
        if (d >= 1f)    return String.format("%.2f Rs",  d);
        return String.format("%.3f Rs", d);
    }
}
