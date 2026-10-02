package io.github.preschoolller.blackhole.eng.scene;

import io.github.preschoolller.blackhole.eng.EngCfg;

/**
 * 史瓦西时空的可调参数包 —— GUI 写、{@link io.github.preschoolller.blackhole.eng.graph.BlackHoleRender} 每帧读并
 * 打入 BlackHoleArgs 参数 UBO（2026-09-27 自 push constant 迁移，见
 * foragent/schwarzschild_ubo_panel_plan.md）。字段初始值来自 {@code eng.properties} 的
 * {@code blackhole.*} 键（{@link EngCfg} 解析），与 blackhole.frag 的 BlackHoleArgs
 * 声明对应（注释里标出 UBO 字段名）。
 * <p>
 * 持有量随 Scene 常驻，BlackHoleRender 的重建（模式热切换/resize）不影响取值。
 */
public class SchwarzschildParams {

    /** temperature：基准温度（K，内盘温度；NumPad ± 快捷键与 GUI 滑条并行生效） */
    public float baseTemperature = EngCfg.getInstance().getBaseTemperature();
    /** iDiskScatter：盘前向散射强度——背光项，被盘消光的背景光散射回视线，有方向性，只泛亮剪影（0=关） */
    public float diskScatter = EngCfg.getInstance().getDiskScatter();
    /** iDiskAmbient：盘环境光强度——弥散项，全天空辐照×盘密度并入发射，无方向性，冷暗盘区均匀补底（0=关） */
    public float diskAmbient = EngCfg.getInstance().getDiskAmbient();
    /** iDiskInnerRadius：盘内半径（Rs 倍数，≈ISCO） */
    public float diskInnerRadiusRs = EngCfg.getInstance().getDiskInnerRadius();
    /** iDiskOuterRadius：盘外半径（Rs 倍数；调大显著增 raymarch 步数与 TAA 拖影面积） */
    public float diskOuterRadiusRs = EngCfg.getInstance().getDiskOuterRadius();
    /** iExposure：曝光增益——autoExposure 后的全局亮度乘子（原 shader 硬编码 2.0） */
    public float exposure = EngCfg.getInstance().getExposure();
    /** iShiftMax：盘频移钳制上限（原 shader 硬编码 2.5） */
    public float shiftMax = EngCfg.getInstance().getShiftMax();
    /** iTaaTau：TAA 静止累积时间常数 τ 基准秒，越大降噪越强/拖影越长（原硬编码 0.3） */
    public float taaTau = EngCfg.getInstance().getTaaTau();
    /** iBloomThreshold：Bloom 亮部阈值（原 bloomComposite 硬编码 1.0） */
    public float bloomThreshold = EngCfg.getInstance().getBloomThreshold();
    /** iBloomMix：Bloom 辉光混合系数（原硬编码 0.6） */
    public float bloomMix = EngCfg.getInstance().getBloomMix();
    /** iBloomMax：Bloom 色调映射输出上限（原硬编码 12.0） */
    public float bloomMax = EngCfg.getInstance().getBloomMax();
    /** iBackgroundBright：背景亮度倍率（原硬编码 0.7；散射项随 Bg 同步缩放,0=纯黑背景突出盘本体） */
    public float backgroundBright = EngCfg.getInstance().getBackgroundBright();
    /** iToneMapStrength：色调映射强度——1=全 ACES（原行为）,0=线性直出（高光硬钳,观察用） */
    public float toneMapStrength = EngCfg.getInstance().getTonemapStrength();
    /** iDiskHalfThickness：盘半厚基准（Rs 倍数,原硬编码 0.5·Rs;垂直密度/厚度/尘埃层随动。
     *  调大增加盘内 raymarch 采样步数,略降性能 */
    public float diskHalfThicknessRs = EngCfg.getInstance().getDiskHalfThickness();
    /** if_dopplerI：轨道多普勒相对论束流增亮 A/B 开关（默认开=原行为；RedShift 链仍含色移） */
    public boolean dopplerIntensityEnabled = true;
    /** if_dopplerT：轨道多普勒² 温移 A/B 开关（默认开=原行为；RedShift 链仍含色移） */
    public boolean dopplerTemperatureEnabled = true;
}
