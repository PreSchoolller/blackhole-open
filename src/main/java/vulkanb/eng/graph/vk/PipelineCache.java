package vulkanb.eng.graph.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkPipelineCacheCreateInfo;
import org.tinylog.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK13.vkCreatePipelineCache;
import static org.lwjgl.vulkan.VK13.vkDestroyPipelineCache;
import static org.lwjgl.vulkan.VK13.vkGetPipelineCacheData;
import static vulkanb.eng.graph.vk.VkUtils.vkCheck;
import static vulkanb.utils.Constants.SHADER_CACHE;

/**
 * 管线缓存 —— 加速图形管线创建（驱动侧编译）并落盘持久化。
 * <p>
 * Vulkan 管线缓存允许驱动复用管线编译的中间结果。本类进一步把缓存数据落盘
 * （shader-cache/pipeline-cache.bin）：构造时作 initialData 预热，{@link #save(Device)}
 * 写回（Render.init 完成后与 VkCtx.cleanup 各一次）。
 * <p>
 * 效果边界：缓存按 SPIR-V 内容寻址——改着色器后 spv 变化，对应条目自然失效，
 * 改后首次编译成本无法免除；但此后每次启动<b>确定性命中</b>，不依赖驱动内部磁盘缓存
 * （驱动升级/缓存驱逐都不会殃及）。stale 数据（换卡/驱动更新）驱动会安全丢弃，
 * 不影响正确性。
 */
public class PipelineCache {

    /** 缓存落盘路径（与 jar 模式 shader 缓存共用目录，已 gitignore） */
    private static final File CACHE_FILE =
            new File(SHADER_CACHE + File.separator + "pipeline-cache.bin");

    /** 管线缓存句柄 */
    private final long vkPipelineCache;

    /**
     * 创建管线缓存：若存在落盘缓存则作为 initialData 预热。
     *
     * @param device 逻辑设备
     */
    public PipelineCache(Device device) {
        Logger.debug("Creating pipeline cache");
        byte[] initialData = loadDiskCache();
        try (var stack = MemoryStack.stackPush()) {
            var createInfo = VkPipelineCacheCreateInfo.calloc(stack).sType$Default();
            ByteBuffer data = null;
            if (initialData != null) {
                // initialData 可达数 MB，超出 MemoryStack 默认 64KB 容量，须堆外分配
                data = MemoryUtil.memAlloc(initialData.length);
                data.put(initialData).flip();
                createInfo.pInitialData(data);
            }
            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vkCreatePipelineCache(device.getVkDevice(), createInfo, null, lp),
                    "Error creating pipeline cache");
            vkPipelineCache = lp.get(0);
            if (data != null) {
                MemoryUtil.memFree(data);
                Logger.info("[startup] pipeline cache seeded ({} KB)", initialData.length / 1024);
            }
        }
    }

    /**
     * 将缓存数据写回磁盘。廉价幂等，可在管线创建完成后与退出时各调一次；
     * 失败仅告警不中断（缓存是加速项，不是正确性依赖）。
     *
     * @param device 逻辑设备
     */
    public void save(Device device) {
        try (var stack = MemoryStack.stackPush()) {
            PointerBuffer size = stack.mallocPointer(1);
            vkGetPipelineCacheData(device.getVkDevice(), vkPipelineCache, size, null);
            if (size.get(0) <= 0) {
                return;
            }
            ByteBuffer data = MemoryUtil.memAlloc((int) size.get(0));
            try {
                vkCheck(vkGetPipelineCacheData(device.getVkDevice(), vkPipelineCache, size, data),
                        "Error getting pipeline cache data");
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);
                CACHE_FILE.getParentFile().mkdirs();
                Files.write(CACHE_FILE.toPath(), bytes);
                Logger.info("[startup] pipeline cache saved ({} KB)", bytes.length / 1024);
            } finally {
                MemoryUtil.memFree(data);
            }
        } catch (IOException excp) {
            Logger.warn(excp, "Failed to save pipeline cache");
        }
    }

    /** 读取落盘缓存；缺失/损坏一律降级为冷缓存（仅告警） */
    private static byte[] loadDiskCache() {
        if (!CACHE_FILE.isFile()) {
            return null;
        }
        try {
            byte[] data = Files.readAllBytes(CACHE_FILE.toPath());
            return data.length > 0 ? data : null;
        } catch (IOException excp) {
            Logger.warn(excp, "Failed to read pipeline cache, starting cold");
            return null;
        }
    }

    /** 销毁管线缓存（不落盘，落盘由 {@link #save(Device)} 显式负责） */
    public void cleanup(Device device) {
        Logger.debug("Destroying pipeline cache");
        vkDestroyPipelineCache(device.getVkDevice(), vkPipelineCache, null);
    }

    public long getVkPipelineCache() {
        return vkPipelineCache;
    }
}
