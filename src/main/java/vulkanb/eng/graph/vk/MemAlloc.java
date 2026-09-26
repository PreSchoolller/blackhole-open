package vulkanb.eng.graph.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.*;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.VK_API_VERSION_1_3;
import static vulkanb.eng.graph.vk.VkUtils.vkCheck;

/**
 * VMA 内存分配器封装 —— 使用 Vulkan Memory Allocator 库管理 GPU 内存。
 * <p>
 * VMA（Vulkan Memory Allocator）是 AMD 开源的 Vulkan 内存管理库，
 * 提供以下优势：
 * <ul>
 *   <li>自动选择最佳内存类型</li>
 *   <li>内存池化，减少分配开销</li>
 *   <li>支持缓冲区设备地址</li>
 *   <li>内存碎片管理</li>
 * </ul>
 * <p>
 * 本项目通过 VMA 创建缓冲区和图像的内存分配。
 */
public class MemAlloc {

    /** VMA 分配器句柄 */
    private final long vmaAlloc;

    /**
     * 创建 VMA 分配器。
     *
     * @param instance  Vulkan 实例
     * @param physDevice 物理设备
     * @param device    逻辑设备
     */
    public MemAlloc(Instance instance, PhysDevice physDevice, Device device) {
        try (var stack = MemoryStack.stackPush()) {
            PointerBuffer pAllocator = stack.mallocPointer(1);

            // 配置 VMA Vulkan 函数绑定
            var vmaVulkanFunctions = VmaVulkanFunctions.calloc(stack)
                    .set(instance.getVkInstance(), device.getVkDevice());

            // 创建 VMA 分配器：启用缓冲区设备地址，API 版本 1.3
            var createInfo = VmaAllocatorCreateInfo.calloc(stack)
                    .flags(VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT)
                    .instance(instance.getVkInstance())
                    .vulkanApiVersion(VK_API_VERSION_1_3)
                    .device(device.getVkDevice())
                    .physicalDevice(physDevice.getVkPhysicalDevice())
                    .pVulkanFunctions(vmaVulkanFunctions);
            vkCheck(vmaCreateAllocator(createInfo, pAllocator),
                    "Failed to create VMA allocator");

            vmaAlloc = pAllocator.get(0);
        }
    }

    /** 销毁 VMA 分配器 */
    public void cleanUp() {
        vmaDestroyAllocator(vmaAlloc);
    }

    public long getVmaAlloc() {
        return vmaAlloc;
    }
}
