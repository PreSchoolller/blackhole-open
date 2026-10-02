package io.github.preschoollerr.blackhole.eng.graph.vk;

import io.github.preschoollerr.blackhole.eng.EngCfg;
import io.github.preschoollerr.blackhole.eng.wnd.Window;

/**
 * Vulkan 核心上下文 —— 持有 Vulkan 初始化阶段创建的所有核心对象。
 * <p>
 * 初始化顺序（严格按 Vulkan 规范）：
 * <ol>
 *   <li>{@link Instance}     —— Vulkan 实例（应用与 Vulkan 驱动的连接）</li>
 *   <li>{@link PhysDevice}   —— 物理设备（GPU 硬件）</li>
 *   <li>{@link Device}       —— 逻辑设备（GPU 的逻辑接口）</li>
 *   <li>{@link Surface}      —— 窗口表面（Vulkan 与窗口系统的桥接）</li>
 *   <li>{@link SwapChain}    —— 交换链（双缓冲/三缓冲呈现）</li>
 *   <li>{@link PipelineCache}—— 管线缓存（加速后续管线创建）</li>
 *   <li>{@link DescAllocator}—— 描述符分配器（管理 uniform buffer 绑定）</li>
 *   <li>{@link MemAlloc}     —— VMA 内存分配器（高效管理 GPU 内存）</li>
 * </ol>
 * <p>
 * 清理顺序与初始化相反。
 */
public class VkCtx {

    /** 描述符集分配器 */
    private final DescAllocator descAllocator;
    /** 逻辑设备 */
    private final Device device;
    /** Vulkan 实例 */
    private final Instance instance;
    /** VMA 内存分配器 */
    private final MemAlloc memAlloc;
    /** 物理设备 */
    private final PhysDevice physDevice;
    /** 管线缓存 */
    private final PipelineCache pipelineCache;
    /** 窗口表面（窗口大小变化时需要重建） */
    private Surface surface;
    /** 交换链（窗口大小变化时需要重建） */
    private SwapChain swapChain;

    /**
     * 构造 Vulkan 上下文：按顺序初始化所有核心对象。
     *
     * @param window GLFW 窗口
     */
    public VkCtx(Window window) {
        var engCfg = EngCfg.getInstance();
        instance = new Instance(engCfg.isVkValidate());
        physDevice = PhysDevice.createPhysicalDevice(instance, engCfg.getPhysDeviceName());
        device = new Device(physDevice);
        surface = new Surface(instance, physDevice, window);
        swapChain = new SwapChain(window, device, surface, engCfg.getRequestedImages(), engCfg.getVSync());
        pipelineCache = new PipelineCache(device);
        descAllocator = new DescAllocator(physDevice, device);
        memAlloc = new MemAlloc(instance, physDevice, device);
    }

    /** 按初始化的反序清理所有资源 */
    public void cleanup() {
        memAlloc.cleanUp();
        descAllocator.cleanup(device);
        // 退出前落盘管线缓存(与 Render.init 完成后的 save 双保险,防异常退出丢失)
        pipelineCache.save(device);
        pipelineCache.cleanup(device);
        swapChain.cleanup(device);
        surface.cleanup(instance);
        device.cleanup();
        physDevice.cleanup();
        instance.cleanup();
    }

    public DescAllocator getDescAllocator() {
        return descAllocator;
    }

    public Device getDevice() {
        return device;
    }

    public MemAlloc getMemAlloc() {
        return memAlloc;
    }

    public PhysDevice getPhysDevice() {
        return physDevice;
    }

    public PipelineCache getPipelineCache() {
        return pipelineCache;
    }

    public Surface getSurface() {
        return surface;
    }

    public SwapChain getSwapChain() {
        return swapChain;
    }

    /**
     * 窗口大小变化时重建 Surface 和 SwapChain。
     * <p>
     * Surface 依赖窗口句柄，SwapChain 依赖 Surface，
     * 因此两者都需要重建。
     *
     * @param window 窗口实例
     */
    public void resize(Window window) {
        swapChain.cleanup(device);
        surface.cleanup(instance);
        var engCfg = EngCfg.getInstance();
        surface = new Surface(instance, physDevice, window);
        swapChain = new SwapChain(window, device, surface, engCfg.getRequestedImages(), engCfg.getVSync());
    }
}
