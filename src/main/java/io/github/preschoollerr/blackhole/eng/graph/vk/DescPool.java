package io.github.preschoollerr.blackhole.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.tinylog.Logger;

import java.nio.LongBuffer;
import java.util.List;

import static org.lwjgl.vulkan.VK13.*;
import static io.github.preschoollerr.blackhole.eng.graph.vk.VkUtils.vkCheck;

/**
 * 描述符池 —— 预分配的描述符存储空间。
 * <p>
 * Vulkan 要求通过描述符池分配描述符集。
 * 每个池有固定的最大描述符数量，用完后需要创建新池。
 * <p>
 * 本池使用 {@code VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT} 标志，
 * 允许单独释放描述符集（而非销毁整个池）。
 */
public class DescPool {

    /** 描述符池句柄 */
    private final long vkDescPool;
    /** 每种描述符类型的数量配置 */
    private List<DescTypeCount> descTypeCounts;

    /**
     * 创建描述符池。
     *
     * @param device         逻辑设备
     * @param descTypeCounts 每种描述符类型的数量
     */
    public DescPool(Device device, List<DescTypeCount> descTypeCounts) {
        Logger.debug("Creating descriptor pool");
        this.descTypeCounts = descTypeCounts;
        try (var stack = MemoryStack.stackPush()) {
            int maxSets = 0;
            int numTypes = descTypeCounts.size();
            var typeCounts = VkDescriptorPoolSize.calloc(numTypes, stack);
            for (int i = 0; i < numTypes; i++) {
                maxSets += descTypeCounts.get(i).count();
                typeCounts.get(i)
                        .type(descTypeCounts.get(i).descType())
                        .descriptorCount(descTypeCounts.get(i).count());
            }

            var descriptorPoolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)  // 允许单独释放
                    .pPoolSizes(typeCounts)
                    .maxSets(maxSets);

            LongBuffer pDescriptorPool = stack.mallocLong(1);
            vkCheck(vkCreateDescriptorPool(device.getVkDevice(), descriptorPoolInfo, null, pDescriptorPool),
                    "Failed to create descriptor pool");
            vkDescPool = pDescriptorPool.get(0);
        }
    }

    /** 销毁描述符池 */
    public void cleanup(Device device) {
        Logger.debug("Destroying descriptor pool");
        vkDestroyDescriptorPool(device.getVkDevice(), vkDescPool, null);
    }

    /**
     * 释放单个描述符集返回到池中。
     *
     * @param device          逻辑设备
     * @param vkDescriptorSet 要释放的描述符集句柄
     */
    public void freeDescriptorSet(Device device, long vkDescriptorSet) {
        try (var stack = MemoryStack.stackPush()) {
            LongBuffer longBuffer = stack.mallocLong(1);
            longBuffer.put(0, vkDescriptorSet);

            vkCheck(vkFreeDescriptorSets(device.getVkDevice(), vkDescPool, longBuffer),
                    "Failed to free descriptor set");
        }
    }

    public List<DescTypeCount> getDescTypeCounts() {
        return descTypeCounts;
    }

    public long getVkDescPool() {
        return vkDescPool;
    }

    /**
     * 描述符类型和数量记录。
     *
     * @param descType Vulkan 描述符类型（如 VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER）
     * @param count    该类型的描述符数量
     */
    public record DescTypeCount(int descType, int count) {
    }
}
