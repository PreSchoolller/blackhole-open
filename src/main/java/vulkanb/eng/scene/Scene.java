package vulkanb.eng.scene;

import vulkanb.eng.EngCfg;
import vulkanb.eng.wnd.Window;

/**
 * 场景容器 —— 持有相机和投影矩阵。
 * <p>
 * 在本黑洞模拟项目中，场景非常简单：
 * <ul>
 *   <li>一个轨道相机（始终看向原点）</li>
 *   <li>一个透视投影矩阵</li>
 *   <li>黑洞本身不作为场景对象存在，而是由片段着色器通过数学公式计算</li>
 * </ul>
 * <p>
 * 相机和投影矩阵的数据通过 Push Constants 传递给 GPU，
 * 在着色器中用于将屏幕像素反投影为世界空间射线。
 */
public class Scene {

    /**
     * 时空模式：史瓦西（现有管线）/ 克尔（NPGS 移植管线）/ 克尔·NPGS 原版（npgs-verbatim
     * 实验：逐字节未动的 NPGS 原始 GLSL + 原装 6 套天空盒/贴图盘），GUI 可热切换。
     */
    public enum SpacetimeMode { SCHWARZSCHILD, KERR, KERR_NPGS }

    /** 相机实例 */
    private final Camera camera;
    /** 投影矩阵实例 */
    private final Projection projection;
    /** 史瓦西时空测地线积分器（测地线相机模式的位置驱动 + 相机速度多普勒数据源） */
    private final GeodesicIntegrator geodesic = new GeodesicIntegrator();
    /** 当前时空模式（默认克尔；npgs.verbatim=true 时以 NPGS 原版模式启动；Render 每帧检查变化并切换渲染器） */
    private SpacetimeMode spacetime =
            EngCfg.getInstance().isNpgsVerbatim() ? SpacetimeMode.KERR_NPGS : SpacetimeMode.KERR;
    /** 克尔可调参数包（GUI 写、KerrRender 读；Phase 2.5 面板数据源） */
    private final KerrParams kerrParams = new KerrParams();
    /** 史瓦西可调参数包（GUI 写、BlackHoleRender 读；Black Hole 面板数据源） */
    private final SchwarzschildParams schwarzschildParams = new SchwarzschildParams();
    /** 山海星空盒开关（两渲染器共用；DualSkybox 在各自天空盒描述符槽位换绑，惰性加载） */
    private boolean mountainsSeasSkybox = EngCfg.getInstance().isMountainsSeasSkybox();
    /** 时间暂停（P 键切换）：冻结盘动画与测地相机推进；渲染时间/TAA/鼠标视角不受影响，
     *  暂停后 TAA 继续累积出清晰静帧，便于观察运动中某一时刻的状态 */
    private boolean timePaused;
    /** 视界坠落演出开关（Controls 面板热切换）：true = r<1.02Rs 触发淡出→传送→淡入；
     *  false = 不触发演出且放开积分器视界护栏，测地相机可穿过视界/虫洞喉道
     *  （观察 NPGS 最大延拓 traverse 与宇宙符号自动翻转） */
    private boolean horizonFallEnabled = true;

    /**
     * 构造场景：从引擎配置读取 FOV/近远平面，创建相机和投影。
     *
     * @param window 窗口（用于获取初始分辨率计算宽高比）
     */
    public Scene(Window window) {
        var engCfg = EngCfg.getInstance();
        projection = new Projection(engCfg.getFov(), engCfg.getZNear(), engCfg.getZFar(), window.getWidth(),
                window.getHeight());
        camera = new Camera();
    }

    public Camera getCamera() {
        return camera;
    }

    public Projection getProjection() {
        return projection;
    }

    public GeodesicIntegrator getGeodesic() {
        return geodesic;
    }

    public SpacetimeMode getSpacetime() {
        return spacetime;
    }

    public void setSpacetime(SpacetimeMode spacetime) {
        this.spacetime = spacetime;
    }

    public float getKerrSpin() {
        return kerrParams.spin;
    }

    public void setKerrSpin(float kerrSpin) {
        kerrParams.spin = Math.min(0.998f, Math.max(0.0f, kerrSpin));
    }

    public KerrParams getKerrParams() {
        return kerrParams;
    }

    public SchwarzschildParams getSchwarzschildParams() {
        return schwarzschildParams;
    }

    public boolean isMountainsSeasSkybox() {
        return mountainsSeasSkybox;
    }

    public void setMountainsSeasSkybox(boolean mountainsSeasSkybox) {
        this.mountainsSeasSkybox = mountainsSeasSkybox;
    }

    public boolean isTimePaused() {
        return timePaused;
    }

    public void setTimePaused(boolean timePaused) {
        this.timePaused = timePaused;
    }

    /** 视界坠落演出开关（false = 可穿越视界/虫洞，观察宇宙符号自动翻转） */
    public boolean isHorizonFallEnabled() {
        return horizonFallEnabled;
    }

    public void setHorizonFallEnabled(boolean horizonFallEnabled) {
        this.horizonFallEnabled = horizonFallEnabled;
    }
}
