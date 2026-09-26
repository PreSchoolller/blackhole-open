package vulkanb.eng.graph.vk;

/**
 * 双星空盒 —— 主星空盒（/textures/skybox）与山海盒（/textures/skybox_mountains_seas，
 * 取 NPGS Antiverse0Skybox 纹理）。供 {@link vulkanb.eng.graph.BlackHoleRender} 与
 * {@link vulkanb.eng.graph.KerrRender} 在各自的天空盒描述符槽位换绑切换（着色器零改动）。
 * <p>
 * 山海盒惰性加载（首次切到时上传，全 mip 链约 130MB 显存），关闭时释放；
 * 切换涉及重绑描述符集，两渲染器的在飞帧都可能引用待变更/待释放的视图，
 * 故交换前统一 waitIdle，再经 {@link Rebind} 回调重绑，最后释放旧盒。
 */
public class DualSkybox {

    /** 换绑回调：调用方在自己的天空盒描述符集上重绑传入盒的 sampler/view */
    public interface Rebind {
        void rebind(CubeTexture box);
    }

    /** 主星空盒（NPGS Universe0Skybox） */
    private final CubeTexture starfield;
    /** 山海盒（惰性加载，null=未加载） */
    private CubeTexture mountainsSeas;
    /** 当前绑定：true=山海，false=星空 */
    private boolean mountainsSeasBound;

    public DualSkybox(VkCtx vkCtx, Queue.GraphicsQueue graphQueue) {
        starfield = new CubeTexture(vkCtx, graphQueue, "/textures/skybox", ".jpg");
    }

    /** 当前应绑定的盒（初始化描述符时取用） */
    public CubeTexture current() {
        return mountainsSeasBound ? mountainsSeas : starfield;
    }

    public boolean isMountainsSeasBound() {
        return mountainsSeasBound;
    }

    /**
     * 按期望状态切换盒：状态无变化时为无操作；有变化时 waitIdle → 惰性加载 →
     * rebind 回调重绑 → 关闭路径释放山海盒。
     * <p>
     * 注意必须重绑<b>目标盒</b>而非 current()——此刻 mountainsSeasBound 尚未翻转，
     * current() 返回的还是旧盒（曾导致：开=重绑旧盒画面无变化，关=绑上新盒后立刻
     * 释放 → 描述符悬垂 → DEVICE_LOST）。
     */
    public void bind(VkCtx vkCtx, boolean wantMountainsSeas, Rebind rebind) {
        if (wantMountainsSeas == mountainsSeasBound) {
            return;
        }
        vkCtx.getDevice().waitIdle();
        if (wantMountainsSeas && mountainsSeas == null) {
            var graphQueue = new Queue.GraphicsQueue(vkCtx, 0);
            mountainsSeas = new CubeTexture(vkCtx, graphQueue, "/textures/skybox_mountains_seas", ".jpg");
        }
        // 目标盒：开启→山海（刚确保已加载）；关闭→星空（必须在释放山海盒之前重绑回它）
        CubeTexture target = wantMountainsSeas ? mountainsSeas : starfield;
        rebind.rebind(target);
        mountainsSeasBound = wantMountainsSeas;
        if (!wantMountainsSeas && mountainsSeas != null) {
            mountainsSeas.cleanup(vkCtx);
            mountainsSeas = null;
        }
    }

    /** 释放两套盒（渲染器 cleanup 时调用；调用方须保证无在飞帧引用） */
    public void cleanup(VkCtx vkCtx) {
        if (starfield != null) {
            starfield.cleanup(vkCtx);
        }
        if (mountainsSeas != null) {
            mountainsSeas.cleanup(vkCtx);
            mountainsSeas = null;
        }
    }
}
