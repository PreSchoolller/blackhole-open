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

    /** 时空模式：史瓦西（现有管线）/ 克尔（NPGS 移植管线），GUI 可热切换 */
    public enum SpacetimeMode { SCHWARZSCHILD, KERR }

    /** 相机实例 */
    private final Camera camera;
    /** 投影矩阵实例 */
    private final Projection projection;
    /** 史瓦西时空测地线积分器（测地线相机模式的位置驱动 + 相机速度多普勒数据源） */
    private final GeodesicIntegrator geodesic = new GeodesicIntegrator();
    /** 当前时空模式（默认克尔；Render 每帧检查变化并切换渲染器） */
    private SpacetimeMode spacetime = SpacetimeMode.KERR;
    /** 克尔可调参数包（GUI 写、KerrRender 读；Phase 2.5 面板数据源） */
    private final KerrParams kerrParams = new KerrParams();
    /** 山海星空盒开关（两渲染器共用；DualSkybox 在各自天空盒描述符槽位换绑，惰性加载） */
    private boolean mountainsSeasSkybox = EngCfg.getInstance().isMountainsSeasSkybox();
    /** 时间暂停（P 键切换）：冻结盘动画与测地相机推进；渲染时间/TAA/鼠标视角不受影响，
     *  暂停后 TAA 继续累积出清晰静帧，便于观察运动中某一时刻的状态 */
    private boolean timePaused;

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
}
