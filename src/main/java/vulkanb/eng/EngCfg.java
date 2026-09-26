package vulkanb.eng;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 引擎配置 —— 单例模式，双层加载：
 * <ol>
 *   <li><b>jar 内默认</b>：classpath 根下的 {@code eng.properties}（打包后随 jar 分发）；</li>
 *   <li><b>外部覆盖</b>：jar 包同目录（优先）或工作目录下的 {@code eng.properties}，
 *       其中出现的键覆盖 jar 内值,未写的键沿用 jar 内——发布后无需重新打包即可调参。</li>
 * </ol>
 * 两处文件都不存在时使用代码内默认值。
 * <p>
 * 可配置项：
 * <ul>
 *   <li>{@code window.width/height} —— 初始窗口尺寸（默认 1280×720）</li>
 *   <li>{@code ups}            —— 逻辑更新频率（默认 60）</li>
 *   <li>{@code vkValidate}     —— 是否启用 Vulkan 验证层（默认 false）</li>
 *   <li>{@code physDeviceName} —— 指定物理设备名称（null 则自动选择）</li>
 *   <li>{@code requestedImages}—— 交换链期望图像数（默认 3，即三重缓冲）</li>
 *   <li>{@code vsync}          —— 是否启用垂直同步（默认 true）</li>
 *   <li>{@code shaderRecompilation} —— 是否自动重新编译着色器</li>
 *   <li>{@code debugShaders}   —— 是否生成着色器调试信息（默认 false）</li>
 *   <li>{@code fov}            —— 视场角（度，默认 60）</li>
 *   <li>{@code zNear}          —— 近裁剪面（默认 0.1）</li>
 *   <li>{@code zFar}           —— 远裁剪面（默认 1000）</li>
 *   <li>{@code maxDescs}       —— 描述符池最大数量（默认 100）</li>
 *   <li>黑洞场景参数（{@code blackhole.*}）—— push constants 物理量与 TAA 阈值</li>
 *   <li>天空盒（{@code skybox.*}）—— 山海星空盒开关（两渲染器共用）</li>
 *   <li>克尔时空参数（{@code kerr.*}）—— KerrParams 初始值（GUI 可运行时改）</li>
 *   <li>输入手感参数（{@code input.*}）—— 鼠标灵敏度、翻滚/移动/缩放速度</li>
 * </ul>
 */
public class EngCfg {
    private static final int DEFAULT_UPS = 60;
    private static final String FILENAME = "eng.properties";
    /** 初始窗口默认尺寸（window.* 未配置时） */
    private static final int DEFAULT_WINDOW_WIDTH = 1280;
    private static final int DEFAULT_WINDOW_HEIGHT = 720;
    private static EngCfg instance;

    /** 初始窗口宽度（窗口单位） */
    private int windowWidth;
    /** 初始窗口高度（窗口单位） */
    private int windowHeight;
    /** 引擎日志级别（tinylog:TRACE/DEBUG/INFO/WARN/ERROR,默认 INFO 压掉内部调试刷屏） */
    private String logLevel;
    /** 应用自定义日志开关（AppLog;GUI/命令行可运行时覆盖） */
    private boolean logVerbose;
    /** 每秒逻辑更新次数 */
    private int ups;
    /** 是否启用 Vulkan 验证层 */
    private boolean vkValidate;
    /** 首选物理设备名称 */
    private String physDeviceName;
    /** 交换链期望图像数量 */
    private int requestedImages;
    /** 是否启用垂直同步 */
    private boolean vSync;
    /** 是否在着色器源文件变更时自动重新编译 */
    private boolean shaderRecompilation;
    /** 是否生成着色器调试信息 */
    private boolean debugShaders;
    /** 视场角（弧度） */
    private float fov;
    /** 近裁剪面距离 */
    private float zNear;
    /** 远裁剪面距离 */
    private float zFar;
    /** 描述符分配器最大描述符数量 */
    private int maxDescs;

    // ---- 黑洞场景参数（blackhole.*） ----
    /** 史瓦西半径（世界单位；量纲基准，一般保持 1.0） */
    private float schwarzschildRadius;
    /** 吸积盘内半径（Rs 的倍数，≈ISCO） */
    private float diskInnerRadius;
    /** 吸积盘外半径（Rs 的倍数） */
    private float diskOuterRadius;
    /** 基础温度（K，运行时可用小键盘 +/- 调节） */
    private float baseTemperature;
    /** 温度调节步长（K） */
    private float temperatureStep;
    /** 温度上限（K） */
    private float temperatureMax;
    /** 温度下限（K） */
    private float temperatureMin;
    /** 盘动画时间速率 */
    private float timeRate;
    /** TAA 硬重置的帧间平移阈值（Rs/帧） */
    private float taaMotionResetThreshold;

    // ---- 克尔时空参数（kerr.*；KerrParams 构造时取用，GUI 仍可运行时改） ----
    /** 无量纲自旋 a*（0=史瓦西极限，上限 0.998=极端克尔） */
    private float kerrSpin;
    /** iQuality：采样步长质量（越低步长越大越快越糙） */
    private float kerrQuality;
    /** prepass 开关：半分辨率扭曲场 + composite 边缘感知合成 */
    private boolean kerrPrepassEnabled;
    /** 噪声哈希查表开关（PerlinNoise sin 哈希 ↔ LUT texelFetch 实时 A/B） */
    private boolean kerrNoiseLut;
    /** 山海星空盒开关（Antiverse0Skybox 纹理，两渲染器共用 DualSkybox 换绑，惰性加载） */
    private boolean mountainsSeasSkybox;
    /** iAccretionRate：吸积率（爱丁顿倍数）——驱动盘温标 */
    private float kerrAccretionRate;
    /** iOuterRadiusRs：盘外半径（Rs；内半径恒为 ISCO(a*)） */
    private float kerrDiskOuterRadius;
    /** iThinRs：盘半厚（Rs） */
    private float kerrDiskHalfThickness;
    /** iHopper：盘厚度随半径增长的斜率 */
    private float kerrThicknessSlope;
    /** iBrightmut：盘亮度乘数（显示增益） */
    private float kerrBrightness;
    /** iDarkmut：盘不透明度乘数（0=盘完全不可见） */
    private float kerrOpacity;
    /** iBlackbodyIntensityExponent：温度-亮度指数（越大内环越突出） */
    private float kerrTemperatureExponent;
    /** iRedShiftColorExponent：频移-色温指数 */
    private float kerrShiftColorExponent;
    /** iRedShiftIntensityExponent：频移-亮度指数 */
    private float kerrShiftBrightnessExponent;
    /** iBackgroundBrightmut：背景星空亮度乘数 */
    private float kerrBackgroundBrightness;
    /** iQ：无量纲电荷 Q*（Kerr–Newman 扩展） */
    private float kerrCharge;
    /** iMu：吸积物质比荷（参与盘温标） */
    private float kerrMu;
    /** iBackShiftMax：背景频移钳制上限 */
    private float kerrBackShiftMax;
    /** iReddening：盘红化系数（RGB 分层消光） */
    private float kerrReddening;
    /** iSaturation：盘饱和度指数 */
    private float kerrSaturation;
    /** iPhotonRingBoost：光子环亮度增亮 */
    private float kerrPhotonRingBoost;
    /** iPhotonRingColorTempBoost：光子环色温增蓝 */
    private float kerrPhotonRingBlue;
    /** iBoostRot：盘不对称增强（显示端放大） */
    private float kerrAsymmetryBoost;
    /** iEnableHeatHaze：热折射开关 */
    private boolean kerrHeatHazeEnabled;
    /** iHeatHaze：热气流扰动强度 */
    private float kerrHeatHaze;
    /** 盘时间倍率（克尔侧 Disk TimeScale） */
    private float kerrTimeScale;
    /** bloom 辉光总强度（0=无辉光，合成退化为纯 NPGS ColorBlend 调色链；1.0=NPGS 原始基准） */
    private float kerrBloomStrength;
    /** iJetBrightmut：喷流亮度乘数（0=喷流关闭） */
    private float kerrJetBrightmut;
    /** iJetRedShiftIntensityExponent：喷流频移-亮度指数 */
    private float kerrJetRedShiftExp;
    /** iJetSaturation：喷流饱和度指数 */
    private float kerrJetSaturation;
    /** iJetShiftMax：喷流频移钳制上限 */
    private float kerrJetShiftMax;

    // ---- 输入手感参数（input.*） ----
    /** 鼠标灵敏度（弧度/像素） */
    private float mouseSensitivity;
    /** 自由视角翻滚速度（弧度/毫秒） */
    private float rollSpeed;
    /** WASD 移动速度系数（单位/毫秒） */
    private float moveSpeed;
    /** 轨道缩放速度（Rs） */
    private float zoomSpeed;

    /**
     * 私有构造函数：加载配置并解析各配置项。
     * 先读 jar 内默认,再以外部文件覆盖（键级合并）;两处都缺失时用代码内默认值。
     */
    private EngCfg() {
        var props = new Properties();

        // ---- 第 1 层:jar 内默认配置(classpath 根) ----
        try (InputStream stream = EngCfg.class.getResourceAsStream("/" + FILENAME)) {
            if (stream != null) {
                props.load(stream);
            } else {
                System.out.printf("In-jar %s not found on classpath, using built-in defaults", FILENAME);
            }
        } catch (IOException excp) {
            System.out.printf("Could not read in-jar %s properties file: %s", FILENAME, excp);
        }

        // ---- 第 2 层:外部覆盖文件(jar 包同目录优先,其次工作目录) ----
        Path external = locateExternalConfig();
        if (external != null) {
            try (InputStream in = Files.newInputStream(external)) {
                var overrides = new Properties();
                overrides.load(in);
                int count = 0;
                for (String name : overrides.stringPropertyNames()) {
                    props.put(name, overrides.getProperty(name));
                    count++;
                }
                System.out.printf("External config %s loaded: %s key(s) override in-jar values", external, count);
            } catch (IOException excp) {
                System.out.printf("Could not read external config %s: %s", external, excp);
            }
        }

        // ---- 解析(外部覆盖已合并,默认值兜底缺键) ----
        windowWidth = Integer.parseInt(props.getOrDefault("window.width", DEFAULT_WINDOW_WIDTH).toString());
        windowHeight = Integer.parseInt(props.getOrDefault("window.height", DEFAULT_WINDOW_HEIGHT).toString());
        logLevel = props.getProperty("log.level", "INFO").trim();
        logVerbose = Boolean.parseBoolean(props.getOrDefault("log.verbose", true).toString());
        ups = Integer.parseInt(props.getOrDefault("ups", DEFAULT_UPS).toString());
        vkValidate = Boolean.parseBoolean(props.getOrDefault("vkValidate", false).toString());
        physDeviceName = props.getProperty("physDeviceName");
        requestedImages = Integer.parseInt(props.getOrDefault("requestedImages", 3).toString());
        vSync = Boolean.parseBoolean(props.getOrDefault("vsync", true).toString());
        shaderRecompilation = Boolean.parseBoolean(props.getOrDefault("shaderRecompilation", false).toString());
        debugShaders = Boolean.parseBoolean(props.getOrDefault("debugShaders", false).toString());
        fov = (float) Math.toRadians(Float.parseFloat(props.getOrDefault("fov", 90.0f).toString()));
        zNear = Float.parseFloat(props.getOrDefault("zNear", 0.1f).toString());
        zFar = Float.parseFloat(props.getOrDefault("zFar", 1000.f).toString());
        maxDescs = Integer.parseInt(props.getOrDefault("maxDescs", 100).toString());
        // ---- 黑洞场景参数 ----
        schwarzschildRadius = Float.parseFloat(props.getOrDefault("blackhole.schwarzschildRadius", 1.0f).toString());
        diskInnerRadius = Float.parseFloat(props.getOrDefault("blackhole.diskInnerRadius", 3.0f).toString());
        diskOuterRadius = Float.parseFloat(props.getOrDefault("blackhole.diskOuterRadius", 18.0f).toString());
        baseTemperature = Float.parseFloat(props.getOrDefault("blackhole.temperature", 90000.0f).toString());
        temperatureStep = Float.parseFloat(props.getOrDefault("blackhole.temperatureStep", 500.0f).toString());
        temperatureMax = Float.parseFloat(props.getOrDefault("blackhole.temperatureMax", 50000.0f).toString());
        temperatureMin = Float.parseFloat(props.getOrDefault("blackhole.temperatureMin", 0.0f).toString());
        timeRate = Float.parseFloat(props.getOrDefault("blackhole.timeRate", 1.0f).toString());
        taaMotionResetThreshold = Float.parseFloat(props.getOrDefault("blackhole.taaMotionResetThreshold", 0.15f).toString());
        // ---- 克尔时空参数（kerr.*） ----
        kerrSpin = Float.parseFloat(props.getOrDefault("kerr.spin", 0.9f).toString());
        kerrQuality = Float.parseFloat(props.getOrDefault("kerr.quality", 0.6f).toString());
        kerrPrepassEnabled = Boolean.parseBoolean(props.getOrDefault("kerr.prepassEnabled", false).toString());
        kerrNoiseLut = Boolean.parseBoolean(props.getOrDefault("kerr.noiseLut", false).toString());
        mountainsSeasSkybox = Boolean.parseBoolean(props.getOrDefault("skybox.mountainsSeas", false).toString());
        kerrAccretionRate = Float.parseFloat(props.getOrDefault("kerr.accretionRate", 0.01f).toString());
        kerrDiskOuterRadius = Float.parseFloat(props.getOrDefault("kerr.diskOuterRadius", 10.0f).toString());
        kerrDiskHalfThickness = Float.parseFloat(props.getOrDefault("kerr.diskHalfThickness", 0.75f).toString());
        kerrThicknessSlope = Float.parseFloat(props.getOrDefault("kerr.thicknessSlope", 0.4f).toString());
        kerrBrightness = Float.parseFloat(props.getOrDefault("kerr.brightness", 2.0f).toString());
        kerrOpacity = Float.parseFloat(props.getOrDefault("kerr.opacity", 0.5f).toString());
        kerrTemperatureExponent = Float.parseFloat(props.getOrDefault("kerr.temperatureExponent", 4.0f).toString());
        kerrShiftColorExponent = Float.parseFloat(props.getOrDefault("kerr.shiftColorExponent", 1.0f).toString());
        kerrShiftBrightnessExponent = Float.parseFloat(props.getOrDefault("kerr.shiftBrightnessExponent", 4.0f).toString());
        kerrBackgroundBrightness = Float.parseFloat(props.getOrDefault("kerr.backgroundBrightness", 0.6f).toString());
        kerrCharge = Float.parseFloat(props.getOrDefault("kerr.charge", 0.0f).toString());
        kerrMu = Float.parseFloat(props.getOrDefault("kerr.mu", 1.0f).toString());
        kerrBackShiftMax = Float.parseFloat(props.getOrDefault("kerr.backShiftMax", 1.5f).toString());
        kerrReddening = Float.parseFloat(props.getOrDefault("kerr.reddening", 0.0f).toString());
        kerrSaturation = Float.parseFloat(props.getOrDefault("kerr.saturation", 0.0f).toString());
        kerrPhotonRingBoost = Float.parseFloat(props.getOrDefault("kerr.photonRingBoost", 0.0f).toString());
        kerrPhotonRingBlue = Float.parseFloat(props.getOrDefault("kerr.photonRingBlue", 0.0f).toString());
        kerrAsymmetryBoost = Float.parseFloat(props.getOrDefault("kerr.asymmetryBoost", 0.0f).toString());
        kerrHeatHazeEnabled = Boolean.parseBoolean(props.getOrDefault("kerr.heatHazeEnabled", false).toString());
        kerrHeatHaze = Float.parseFloat(props.getOrDefault("kerr.heatHaze", 0.0f).toString());
        kerrTimeScale = Float.parseFloat(props.getOrDefault("kerr.timeScale", 1.0f).toString());
        kerrBloomStrength = Float.parseFloat(props.getOrDefault("kerr.bloomStrength", 0.5f).toString());
        kerrJetBrightmut = Float.parseFloat(props.getOrDefault("kerr.jetBrightmut", 0.0f).toString());
        kerrJetRedShiftExp = Float.parseFloat(props.getOrDefault("kerr.jetRedShiftExp", 4.0f).toString());
        kerrJetSaturation = Float.parseFloat(props.getOrDefault("kerr.jetSaturation", 0.0f).toString());
        kerrJetShiftMax = Float.parseFloat(props.getOrDefault("kerr.jetShiftMax", 3.0f).toString());
        // ---- 输入手感参数 ----
        mouseSensitivity = Float.parseFloat(props.getOrDefault("input.mouseSensitivity", 0.003f).toString());
        rollSpeed = Float.parseFloat(props.getOrDefault("input.rollSpeed", 0.002f).toString());
        moveSpeed = Float.parseFloat(props.getOrDefault("input.moveSpeed", 0.003f).toString());
        zoomSpeed = Float.parseFloat(props.getOrDefault("input.zoomSpeed", 0.5f).toString());
    }

    /**
     * 定位外部覆盖配置文件:jar/classes 所在目录优先,其次工作目录;都不存在返回 null。
     */
    private static Path locateExternalConfig() {
        try {
            Path codeDir = Paths.get(EngCfg.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParent();
            if (codeDir != null) {
                Path besideJar = codeDir.resolve(FILENAME);
                if (Files.isRegularFile(besideJar)) {
                    return besideJar;
                }
            }
        } catch (Exception excp) {
            System.out.printf("Could not resolve code location for external config: %s", excp);
        }
        Path cwd = Paths.get(FILENAME);
        return Files.isRegularFile(cwd) ? cwd : null;
    }

    /** 线程安全的单例获取 */
    public static synchronized EngCfg getInstance() {
        if (instance == null) {
            instance = new EngCfg();
        }
        return instance;
    }

    // ---- 窗口 ----

    public int getWindowWidth() {
        return windowWidth;
    }

    public int getWindowHeight() {
        return windowHeight;
    }

    /** 引擎日志级别（tinylog level 字符串,如 INFO/DEBUG/TRACE） */
    public String getLogLevel() {
        return logLevel;
    }

    /** 应用自定义日志默认开关（GUI/命令行可运行时覆盖） */
    public boolean isLogVerbose() {
        return logVerbose;
    }

    public float getFov() {
        return fov;
    }

    public int getMaxDescs() {
        return maxDescs;
    }

    public String getPhysDeviceName() {
        return physDeviceName;
    }

    public int getRequestedImages() {
        return requestedImages;
    }

    public int getUps() {
        return ups;
    }

    public boolean getVSync() {
        return vSync;
    }

    public float getZFar() {
        return zFar;
    }

    public float getZNear() {
        return zNear;
    }

    public boolean isDebugShaders() {
        return debugShaders;
    }

    public boolean isShaderRecompilation() {
        return shaderRecompilation;
    }

    public boolean isVkValidate() {
        return vkValidate;
    }

    // ---- 黑洞场景参数 ----

    public float getSchwarzschildRadius() {
        return schwarzschildRadius;
    }

    public float getDiskInnerRadius() {
        return diskInnerRadius;
    }

    public float getDiskOuterRadius() {
        return diskOuterRadius;
    }

    public float getBaseTemperature() {
        return baseTemperature;
    }

    public float getTemperatureStep() {
        return temperatureStep;
    }

    public float getTemperatureMax() {
        return temperatureMax;
    }

    public float getTemperatureMin() {
        return temperatureMin;
    }

    public float getTimeRate() {
        return timeRate;
    }

    public float getTaaMotionResetThreshold() {
        return taaMotionResetThreshold;
    }

    // ---- 克尔时空参数 ----

    public float getKerrSpin() {
        return kerrSpin;
    }

    public float getKerrQuality() {
        return kerrQuality;
    }

    public boolean isKerrPrepassEnabled() {
        return kerrPrepassEnabled;
    }

    public boolean isKerrNoiseLut() {
        return kerrNoiseLut;
    }

    public boolean isMountainsSeasSkybox() {
        return mountainsSeasSkybox;
    }

    public float getKerrAccretionRate() {
        return kerrAccretionRate;
    }

    public float getKerrDiskOuterRadius() {
        return kerrDiskOuterRadius;
    }

    public float getKerrDiskHalfThickness() {
        return kerrDiskHalfThickness;
    }

    public float getKerrThicknessSlope() {
        return kerrThicknessSlope;
    }

    public float getKerrBrightness() {
        return kerrBrightness;
    }

    public float getKerrOpacity() {
        return kerrOpacity;
    }

    public float getKerrTemperatureExponent() {
        return kerrTemperatureExponent;
    }

    public float getKerrShiftColorExponent() {
        return kerrShiftColorExponent;
    }

    public float getKerrShiftBrightnessExponent() {
        return kerrShiftBrightnessExponent;
    }

    public float getKerrBackgroundBrightness() {
        return kerrBackgroundBrightness;
    }

    public float getKerrCharge() {
        return kerrCharge;
    }

    public float getKerrMu() {
        return kerrMu;
    }

    public float getKerrBackShiftMax() {
        return kerrBackShiftMax;
    }

    public float getKerrReddening() {
        return kerrReddening;
    }

    public float getKerrSaturation() {
        return kerrSaturation;
    }

    public float getKerrPhotonRingBoost() {
        return kerrPhotonRingBoost;
    }

    public float getKerrPhotonRingBlue() {
        return kerrPhotonRingBlue;
    }

    public float getKerrAsymmetryBoost() {
        return kerrAsymmetryBoost;
    }

    public boolean isKerrHeatHazeEnabled() {
        return kerrHeatHazeEnabled;
    }

    public float getKerrHeatHaze() {
        return kerrHeatHaze;
    }

    public float getKerrTimeScale() {
        return kerrTimeScale;
    }

    public float getKerrBloomStrength() {
        return kerrBloomStrength;
    }

    public float getKerrJetBrightmut() {
        return kerrJetBrightmut;
    }

    public float getKerrJetRedShiftExp() {
        return kerrJetRedShiftExp;
    }

    public float getKerrJetSaturation() {
        return kerrJetSaturation;
    }

    public float getKerrJetShiftMax() {
        return kerrJetShiftMax;
    }

    // ---- 输入手感参数 ----

    public float getMouseSensitivity() {
        return mouseSensitivity;
    }

    public float getRollSpeed() {
        return rollSpeed;
    }

    public float getMoveSpeed() {
        return moveSpeed;
    }

    public float getZoomSpeed() {
        return zoomSpeed;
    }
}
