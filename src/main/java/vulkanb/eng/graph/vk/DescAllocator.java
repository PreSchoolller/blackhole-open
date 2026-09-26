package vulkanb.eng.graph.vk;

import org.lwjgl.vulkan.VkPhysicalDeviceLimits;
import org.tinylog.Logger;
import vulkanb.eng.EngCfg;

import java.util.*;

import static org.lwjgl.vulkan.VK13.*;

/**
 * 描述符分配器 —— 管理描述符池和描述符集的分配/释放。
 * <p>
 * 描述符（Descriptor）是 Vulkan 中将 GPU 资源（缓冲区、纹理等）
 * 绑定到着色器的机制。
 * <p>
 * 本分配器的策略：
 * <ul>
 *   <li>维护一个描述符池列表，按需创建新池</li>
 *   <li>分配时找到第一个有足够空间的池</li>
 *   <li>如果所有池都满了，创建新池</li>
 *   <li>支持按 ID 释放和查询描述符集</li>
 * </ul>
 * <p>
 * 支持的描述符类型：
 * <ul>
 *   <li>UNIFORM_BUFFER —— 统一缓冲区（变换矩阵等）</li>
 *   <li>COMBINED_IMAGE_SAMPLER —— 组合图像采样器（纹理）</li>
 *   <li>STORAGE_BUFFER —— 存储缓冲区（计算着色器输入输出）</li>
 * </ul>
 */
public class DescAllocator {

    /** 描述符类型 → 最大数量限制 */
    private final Map<Integer, Integer> descLimits;
    /** 描述符池列表 */
    private final List<DescPoolInfo> descPoolList;
    /** 描述符集 ID → 描述符集信息映射 */
    private final Map<String, DescSetInfo> descSetInfoMap;

    /**
     * 创建描述符分配器：初始化描述符限制并创建第一个描述符池。
     *
     * @param physDevice 物理设备（用于查询设备限制）
     * @param device     逻辑设备
     */
    public DescAllocator(PhysDevice physDevice, Device device) {
        Logger.debug("Creating descriptor allocator");
        descPoolList = new ArrayList<>();
        descLimits = createDescLimits(physDevice);
        descPoolList.add(createDescPoolInfo(device, descLimits));
        descSetInfoMap = new HashMap<>();
    }

    /**
     * 从设备限制中计算描述符类型的最大数量。
     * 取配置值和设备限制中的较小值。
     */
    private static Map<Integer, Integer> createDescLimits(PhysDevice physDevice) {
        var engCfg = EngCfg.getInstance();
        int maxDescs = engCfg.getMaxDescs();
        VkPhysicalDeviceLimits limits = physDevice.getVkPhysicalDeviceProperties().properties().limits();
        Map<Integer, Integer> descLimits = new HashMap<>();
        descLimits.put(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, (int)Math.min(maxDescs, Integer.toUnsignedLong(limits.maxDescriptorSetUniformBuffers())));
        descLimits.put(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, (int)Math.min(maxDescs, Integer.toUnsignedLong(limits.maxDescriptorSetSamplers())));
        descLimits.put(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, (int)Math.min(maxDescs, Integer.toUnsignedLong(limits.maxDescriptorSetStorageBuffers())));
        return descLimits;
    }

    /** 创建新的描述符池 */
    private static DescPoolInfo createDescPoolInfo(Device device, Map<Integer, Integer> descLimits) {
        Map<Integer, Integer> descCount = new HashMap<>();
        List<DescPool.DescTypeCount> descTypeCounts = new ArrayList<>();
        descLimits.forEach((k, v) -> {
            descCount.put(k, v);
            descTypeCounts.add(new DescPool.DescTypeCount(k, v));
        });
        var descPool = new DescPool(device, descTypeCounts);
        return new DescPoolInfo(descCount, descPool);
    }

    /**
     * 分配单个描述符集。
     *
     * @param device         逻辑设备
     * @param id             描述符集的唯一标识符
     * @param descSetLayout  描述符集布局
     * @return 分配的描述符集
     */
    public synchronized DescSet addDescSet(Device device, String id, DescSetLayout descSetLayout) {
        return addDescSets(device, id, 1, descSetLayout)[0];
    }

    /**
     * 批量分配描述符集。
     * <p>
     * 查找第一个有足够空间的描述符池，如果找不到则创建新池。
     * 分配后更新池的剩余空间计数。
     *
     * @param device         逻辑设备
     * @param id             描述符集的唯一标识符
     * @param count          分配数量
     * @param descSetLayout  描述符集布局
     * @return 分配的描述符集数组
     */
    public synchronized DescSet[] addDescSets(Device device, String id, int count, DescSetLayout descSetLayout) {
        // 查找有足够空间的描述符池
        DescPoolInfo targetPool = null;
        int poolPos = 0;
        for (DescPoolInfo descPoolInfo : descPoolList) {
            targetPool = descPoolInfo;
            for (DescSetLayout.LayoutInfo layoutInfo : descSetLayout.getLayoutInfos()) {
                int descType = layoutInfo.descType();
                Integer available = descPoolInfo.descCount.get(descType);
                if (available == null) {
                    throw new RuntimeException("Unknown type [" + descType + "]");
                }
                Integer maxTotal = descLimits.get(descType);
                if (count > maxTotal) {
                    throw new RuntimeException("Cannot create more than [" + maxTotal + "] for descriptor type [" + descType + "]");
                }
                if (available < count) {
                    targetPool = null;
                    break;
                }
            }
            if (targetPool != null) {
                break;
            }
            poolPos++;
        }

        // 所有池都满了，创建新池
        if (targetPool == null) {
            targetPool = createDescPoolInfo(device, descLimits);
            descPoolList.add(targetPool);
            poolPos++;
        }

        // 分配描述符集
        var result = new DescSet[count];
        for (int i = 0; i < count; i++) {
            DescSet descSet = new DescSet(device, targetPool.descPool(), descSetLayout);
            result[i] = descSet;
        }
        descSetInfoMap.put(id, new DescSetInfo(result, poolPos));

        // 更新已消耗的描述符数量
        for (DescSetLayout.LayoutInfo layoutInfo : descSetLayout.getLayoutInfos()) {
            int descType = layoutInfo.descType();
            targetPool.descCount.put(descType, targetPool.descCount.get(descType) - count);
        }

        return result;
    }

    /** 释放所有描述符池 */
    public synchronized void cleanup(Device device) {
        Logger.debug("Destroying descriptor allocator");
        descSetInfoMap.clear();
        descPoolList.forEach(d -> d.descPool.cleanup(device));
    }

    /**
     * 释放描述符集：将描述符返回到所属的描述符池。
     *
     * @param device 逻辑设备
     * @param id     描述符集的唯一标识符
     */
    public synchronized void freeDescSet(Device device, String id) {
        DescSetInfo descSetInfo = descSetInfoMap.get(id);
        if (descSetInfo == null) {
            Logger.info("Could not find descriptor set with id [{}]", id);
            return;
        }
        if (descSetInfo.poolPos >= descPoolList.size()) {
            Logger.info("Could not find descriptor pool associated to set with id [{}]", id);
            return;
        }
        DescPoolInfo descPoolInfo = descPoolList.get(descSetInfo.poolPos);
        Arrays.asList(descSetInfo.descSets).forEach(d -> descPoolInfo.descPool.freeDescriptorSet(device, d.getVkDescriptorSet()));
    }

    /**
     * 按 ID 和索引获取描述符集。
     *
     * @param id  描述符集的唯一标识符
     * @param pos 数组索引
     * @return 描述符集，未找到则返回 null
     */
    public synchronized DescSet getDescSet(String id, int pos) {
        DescSet result = null;
        DescSetInfo descSetInfo = descSetInfoMap.get(id);
        if (descSetInfo != null) {
            result = descSetInfo.descSets()[pos];
        }
        return result;
    }

    /** 获取第一个描述符集（pos=0） */
    public synchronized DescSet getDescSet(String id) {
        return getDescSet(id, 0);
    }

    /** 描述符池信息记录：剩余空间 + 池实例 */
    record DescPoolInfo(Map<Integer, Integer> descCount, DescPool descPool) {
    }

    /** 描述符集信息记录：描述符集数组 + 所属池索引 */
    record DescSetInfo(DescSet[] descSets, int poolPos) {
    }
}
