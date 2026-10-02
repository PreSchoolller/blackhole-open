package io.github.preschoollerr.blackhole.eng.scene;

import io.github.preschoollerr.blackhole.eng.EngCfg;

/**
 * 克尔时空的可调参数包（Phase 2.5）—— GUI 写、{@link io.github.preschoollerr.blackhole.eng.graph.KerrRender} 每帧读并
 * 打入 BlackHoleArgs UBO。字段初始值来自 {@code eng.properties} 的 {@code kerr.*} 键
 * （{@link io.github.preschoollerr.blackhole.eng.EngCfg} 解析，缺省值取 NPGS Application.cpp 交互默认/注释值），
 * 与 kerr.frag 的 BlackHoleArgs 声明一一对应（注释里标出 UBO 字段名）。
 * <p>
 * 持有量随 Scene 常驻，KerrRender 的 lazy 重建（模式热切换/resize）不影响取值。
 */
public class KerrParams {

    /** 黑洞质量（太阳质量倍数，人马 A*；与 NPGS 默认一致，参与盘温标与时间换算） */
    public static final float BH_MASS_SOL = 1.49e7f;

    /** 无量纲自旋 a*（0=史瓦西极限，上限 0.998=极端克尔） */
    public float spin = EngCfg.getInstance().getKerrSpin();
    /** iQuality：采样步长质量（越低步长越大越快越糙） */
    public float quality = EngCfg.getInstance().getKerrQuality();
    /** prepass 开关：半分辨率扭曲场 + composite 边缘感知合成（NPGS Prepass，大分辨率性能优化） */
    public boolean prepassEnabled = EngCfg.getInstance().isKerrPrepassEnabled();
    /** 噪声哈希查表开关（PerlinNoise 的 sin 哈希 ↔ 64³ LUT texelFetch，GUI 实时 A/B） */
    public boolean noiseLutEnabled = EngCfg.getInstance().isKerrNoiseLut();
    /** iAccretionRate：吸积率（爱丁顿倍数）——驱动盘温标与喷流开关 */
    public float accretionRate = EngCfg.getInstance().getKerrAccretionRate();
    /** iOuterRadiusRs：盘外半径（Rs；内半径恒为 ISCO(a*)，不开放） */
    public float outerRadiusRs = EngCfg.getInstance().getKerrDiskOuterRadius();
    /** iThinRs：盘半厚（Rs） */
    public float thinRs = EngCfg.getInstance().getKerrDiskHalfThickness();
    /** iHopper：盘厚度随半径增长的斜率 */
    public float hopper = EngCfg.getInstance().getKerrThicknessSlope();
    /** iBrightmut：盘亮度乘数（显示增益，不动物理） */
    public float brightmut = EngCfg.getInstance().getKerrBrightness();
    /** iDarkmut：盘不透明度乘数（0=盘完全透明不可见） */
    public float darkmut = EngCfg.getInstance().getKerrOpacity();
    /** iBlackbodyIntensityExponent：温度-亮度指数（T/Tpeak)^n，越大内环越突出 */
    public float blackbodyIntensityExponent = EngCfg.getInstance().getKerrTemperatureExponent();
    /** iRedShiftColorExponent：频移-色温指数 */
    public float redShiftColorExponent = EngCfg.getInstance().getKerrShiftColorExponent();
    /** iRedShiftIntensityExponent：频移-亮度指数 */
    public float redShiftIntensityExponent = EngCfg.getInstance().getKerrShiftBrightnessExponent();
    /** iBackgroundBrightmut：背景星空亮度乘数（0=纯黑背景，突出盘本体） */
    public float backgroundBrightmut = EngCfg.getInstance().getKerrBackgroundBrightness();

    /** iDiskScatter：盘前向散射强度——背光项，被盘消光的背景光散射回视线，有方向性，只泛亮剪影（0=关） */
    public float diskScatter = EngCfg.getInstance().getKerrDiskScatter();
    /** iDiskAmbient：盘环境光强度——弥散项，全天空辐照×盘密度并入发射，无方向性，冷暗盘区均匀补底（0=关） */
    public float diskAmbient = EngCfg.getInstance().getKerrDiskAmbient();

    // ---- 相对论扩展与显示增强（Phase 2.5 面板补齐） ----
    /** iQ：无量纲电荷 Q*（Kerr–Newman 扩展；影响视界半径/ISCO/度规,a²+Q²>1 为裸奇点） */
    public float qStar = EngCfg.getInstance().getKerrCharge();
    /** iMu：吸积物质比荷——参与 DiskArgument,直接影响盘温标 */
    public float mu = EngCfg.getInstance().getKerrMu();
    /** iBackShiftMax：背景频移钳制上限 */
    public float backShiftMax = EngCfg.getInstance().getKerrBackShiftMax();
    /** iReddening：盘红化系数（RGB 分层消光） */
    public float reddening = EngCfg.getInstance().getKerrReddening();
    /** iSaturation：盘饱和度指数 */
    public float saturation = EngCfg.getInstance().getKerrSaturation();
    /** iPhotonRingBoost：光子环亮度增亮 */
    public float photonRingBoost = EngCfg.getInstance().getKerrPhotonRingBoost();
    /** iPhotonRingColorTempBoost：光子环色温增蓝 */
    public float photonRingColorTempBoost = EngCfg.getInstance().getKerrPhotonRingBlue();
    /** iBoostRot：增强自旋非 0 时的盘不对称程度（显示端放大） */
    public float boostRot = EngCfg.getInstance().getKerrAsymmetryBoost();
    /** iEnableHeatHaze：热折射开关（着色器已完整接线） */
    public boolean enableHeatHaze = EngCfg.getInstance().isKerrHeatHazeEnabled();
    /** iHeatHaze：热气流扰动强度 */
    public float heatHaze = EngCfg.getInstance().getKerrHeatHaze();

    // ---- Bloom（Pass B 合成端；作用于 tonemap 后的 LDR 历史域，与 NPGS bloom 同域） ----
    /** 辉光总强度（0=无辉光，合成退化为纯 NPGS ColorBlend 调色链；1.0=NPGS 原始基准 ×0.08） */
    public float bloomStrength = EngCfg.getInstance().getKerrBloomStrength();

    // ---- 喷流（JetColor；门控 IsJetVisible：吸积率 ≥1e-2 且 jetBrightmut > 0） ----
    /** iJetBrightmut：喷流亮度乘数（默认 0=喷流关闭） */
    public float jetBrightmut = EngCfg.getInstance().getKerrJetBrightmut();
    /** iJetRedShiftIntensityExponent：喷流频移-亮度指数 */
    public float jetRedShiftIntensityExponent = EngCfg.getInstance().getKerrJetRedShiftExp();
    /** iJetSaturation：喷流饱和度指数 */
    public float jetSaturation = EngCfg.getInstance().getKerrJetSaturation();
    /** iJetShiftMax：喷流频移钳制上限 */
    public float jetShiftMax = EngCfg.getInstance().getKerrJetShiftMax();

    // ---- NPGS 原版专有（仅 KERR_NPGS 模式消费；移植版无对应代码，KerrRender 忽略并写 0） ----
    /** iGrid：时空网格 0=关 / 1=GridColor / 2=GridColorSimple（原版独有绘制） */
    public int gridMode = 0;
    /** iInWhichUniverse：宇宙变体选层 0..2（SampleBackground 按 %3 选 6 套盒中的一套） */
    public int universeIndex = 0;
    /** iUniverseSign：相机所在空间侧（+1 正宇宙 / -1 反宇宙）——每条光线宇宙符号的种子
     *  （随行进中的赤道穿越翻转逻辑演化）。注意：测地相机穿越虫洞不会自动翻转此值
     *  （引擎未接线状态跟踪），穿越观察时需在此手动切换 */
    public float universeSign = 1.0f;
    /** iWhitehole：最大延拓（1=启用白洞/多宇宙解；配合测地相机穿越内视界观察 II 区） */
    public boolean whitehole = false;
    /** iEnableShadowCulling：阴影剔除优化（原版默认 0） */
    public boolean shadowCulling = false;
    /** iDEBUG：调试视图 0=关 / 1 / 2 / 3=步数热图 / 4 */
    public int debugMode = 0;
    /** iPolarization：偏振输出开关（配合 iPolarizationAngle 偏振片角度） */
    public boolean polarization = false;
    /** iPolarizationAngle：偏振片角度 */
    public float polarizationAngle = 0.0f;
    /** iDensestarsurfaceR：致密星表面半径（Rs 倍数；0=关，>视界半径即渲染中子星表面——原版独有） */
    public float densestarRadiusRs = 0.0f;
    /** iDensestarBlackbodyIntensityExponent：表面温度-黑体颜色指数 */
    public float densestarBlackbodyExp = 4.0f;
    /** iDensestarRedShiftColorExponent：表面频移-颜色指数 */
    public float densestarShiftColorExp = 1.0f;
    /** iDensestarRedShiftIntensityExponent：表面频移-亮度指数 */
    public float densestarShiftBrightExp = 4.0f;
    /** iDensestarBrightmut：表面亮度乘数 */
    public float densestarBrightmut = 1.0f;
    /** iUseImageDisk：贴图盘开关（赤道面贴 R.jpg 贴图；仅 KERR_NPGS 模式绑定真纹理） */
    public boolean useImageDisk = false;
    /** iImageRotationSpeed：贴图盘整体自转角速度（NPGS 初始值 ≈0.0078） */
    public float imageRotationSpeed = 0.0076561966f * (3.06f / 3.0f);

    /** 盘时间倍率（克尔侧的 TimeRate 镜像；史瓦西侧同名滑条驱动的是测地线相机，互不相干） */
    public float timeScale = EngCfg.getInstance().getKerrTimeScale();
    /**
     * 盘时间累计（单位 c·s/Rs，即 UBO iBlackHoleTime）。由 KerrRender 每帧按
     * realDt × timeScale 推进；存放在本类（Scene 生命周期）而非 KerrRender，
     * 模式热切换/窗口 resize 重建渲染器时盘纹相位保持连续。
     */
    public float diskTimeCsRs = 0.0f;

    /**
     * 是否裸奇点（a²+Q²>1,无视界）。着色器有对应的裸奇点分支。
     */
    public static boolean isNakedSingularity(float aStar, float qStar) {
        return aStar * aStar + qStar * qStar > 1.0f;
    }

    /**
     * 外视界半径（单位 Rs）= ½(1+√(1−a²−Q²))；裸奇点时返回 NaN 的安全值 0.5。
     */
    public static float outerHorizonRs(float aStar, float qStar) {
        float disc = Math.max(0.0f, 1.0f - aStar * aStar - qStar * qStar);
        return 0.5f * (1.0f + (float) Math.sqrt(disc));
    }

    /**
     * 吸积盘内半径（单位 Rs）：ISCO 与外视界+0.1 取大者（NPGS 截图任务路径同式）。
     * <p>
     * Q=0 时用 Bardeen–Press–Teukolsky 顺行解析式（a=0 → 3Rs，a→1 → 0.5Rs）；
     * Q≠0 时按 NPGS calculate_KN_ISCO 移植的黄金分割搜索数值求解
     * Kerr–Newman 圆轨道能量最低点（单位换算：能量公式 x 以 M 计,×M=0.5 得 Rs）。
     * GUI 只读展示 + KerrRender UBO 打包共用。
     */
    public static float iscoInnerRadiusRs(float aStar, float qStar) {
        float a = Math.min(0.998f, Math.max(0.0f, aStar));
        float q = Math.min(0.9f, Math.max(0.0f, qStar));
        if (isNakedSingularity(a, q)) {
            return 0.5f;   // 裸奇点无 ISCO/视界,给小值兜底（着色器走 bIsNakedSingularity 分支）
        }
        float iscoRs;
        if (q < 1e-6f) {
            float a2 = a * a;
            float cbrt = (float) Math.cbrt(1.0f - a2);
            float z1 = 1.0f + cbrt * ((float) Math.cbrt(1.0f + a) + (float) Math.cbrt(1.0f - a));
            float z2 = (float) Math.sqrt(3.0f * a2 + z1 * z1);
            iscoRs = 0.5f * (3.0f + z2 - (float) Math.sqrt((3.0f - z1) * (3.0f + z1 + 2.0f * z2)));
        } else {
            iscoRs = (float) (knIscoInUnitsOfM(a, q * q) * 0.5);
        }
        return Math.max(iscoRs, outerHorizonRs(a, q) + 0.1f);
    }

    /**
     * Kerr–Newman ISCO（单位 M）：黄金分割搜索圆轨道能量 E(x) 的最低点
     * （NPGS calculate_KN_ISCO 移植;搜索区间右端 15 覆盖逆行极端 ISCO 的 9M 理论上限）。
     */
    private static double knIscoInUnitsOfM(double a, double q2) {
        double left = Math.max(1.0, q2) + 1e-5;
        double right = 15.0;
        final double invphi = (Math.sqrt(5.0) - 1.0) / 2.0;
        final double invphi2 = (3.0 - Math.sqrt(5.0)) / 2.0;
        double c = left + invphi2 * (right - left);
        double d = left + invphi * (right - left);
        double fc = knOrbitEnergy(c, a, q2);
        double fd = knOrbitEnergy(d, a, q2);
        while ((right - left) > 1e-11) {
            if (fc < fd) {
                right = d;
                d = c;
                fd = fc;
                c = left + invphi2 * (right - left);
                fc = knOrbitEnergy(c, a, q2);
            } else {
                left = c;
                c = d;
                fc = fd;
                d = left + invphi * (right - left);
                fd = knOrbitEnergy(d, a, q2);
            }
        }
        return 0.5 * (left + right);
    }

    /** Kerr–Newman 圆轨道单位质量能量 E(x)（NPGS get_orbit_energy 移植;x 单位 M） */
    private static double knOrbitEnergy(double x, double a, double q2) {
        if (x < q2) {
            return 1e100;
        }
        double sq = Math.sqrt(Math.max(0.0, x - q2));
        double f = x * x - 3.0 * x + 2.0 * q2 + 2.0 * a * sq;
        if (f <= 1e-15) {
            return 1e100;   // 光子球以内：轨道不存在
        }
        double num = x * x - 2.0 * x + q2 + a * sq;
        return num / (x * Math.sqrt(f));
    }

    /**
     * 盘峰值温度估计（开尔文）——与 kerr.frag ComputeDiskArgument 同源公式：
     * DiskArgument = kPhysicsFactor/M·(μ/η)·ṁ，PeakT = (DiskArgument×0.05665278)^0.25。
     * 仅供 GUI 遥测展示，着色器仍以 UBO 原始字段自行计算。
     */
    public static float peakTemperatureK(float spin, float mu, float accretionRate) {
        float a = Math.min(0.998f, Math.max(0.0f, spin));
        float a2 = a * a;
        float cbrt = (float) Math.cbrt(1.0f - a2);
        float z1 = 1.0f + cbrt * ((float) Math.cbrt(1.0f + a) + (float) Math.cbrt(1.0f - a));
        float z2 = (float) Math.sqrt(3.0f * a2 + z1 * z1);
        float rootTerm = (float) Math.sqrt(Math.max(0.0f, (3.0f - z1) * (3.0f + z1 + 2.0f * z2)));
        float rmsM = 3.0f + z2 - rootTerm;
        float accretionEffective = (float) Math.sqrt(Math.max(0.001f, 1.0f - (2.0f / 3.0f) / rmsM));
        float diskArgument = 1.52491e30f / BH_MASS_SOL * (mu / accretionEffective) * accretionRate;
        return (float) Math.pow(diskArgument * 0.05665278, 0.25);
    }
}
