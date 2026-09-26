package vulkanb.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK13.*;
import static vulkanb.eng.graph.vk.VkUtils.vkCheck;

/**
 * 描述符集 —— 绑定 GPU 资源到着色器的接口。
 * <p>
 * 描述符集包含一组描述符，每个描述符指向一个 GPU 资源：
 * <ul>
 *   <li>Uniform Buffer —— 变换矩阵、相机参数等</li>
 *   <li>Combined Image Sampler —— 纹理和采样器</li>
 *   <li>Storage Buffer —— 计算着色器的输入输出</li>
 * </ul>
 * <p>
 * 本项目中描述符集当前未实际使用（黑洞参数通过 Push Constants 传递），
 * 但保留此机制以支持未来的扩展（如多纹理、计算着色器等）。
 */
public class DescSet {

    /** Vulkan 描述符集句柄 */
    protected long vkDescriptorSet;

    /**
     * 从描述符池分配描述符集。
     *
     * @param device        逻辑设备
     * @param descPool      描述符池
     * @param descSetLayout 描述符集布局
     */
    public DescSet(Device device, DescPool descPool, DescSetLayout descSetLayout) {
        try (var stack = MemoryStack.stackPush()) {
            LongBuffer pDescriptorSetLayout = stack.mallocLong(1);
            pDescriptorSetLayout.put(0, descSetLayout.getVkDescLayout());
            var allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType$Default()
                    .descriptorPool(descPool.getVkDescPool())
                    .pSetLayouts(pDescriptorSetLayout);

            LongBuffer pDescriptorSet = stack.mallocLong(1);
            vkCheck(vkAllocateDescriptorSets(device.getVkDevice(), allocInfo, pDescriptorSet),
                    "Failed to create descriptor set");

            vkDescriptorSet = pDescriptorSet.get(0);
        }
    }

    public long getVkDescriptorSet() {
        return vkDescriptorSet;
    }

    /**
     * 将缓冲区绑定到描述符集中的指定绑定点。
     *
     * @param device  逻辑设备
     * @param buffer  Vulkan 缓冲区
     * @param range   缓冲区范围（字节）
     * @param binding 绑定点索引
     * @param type    描述符类型
     */
    public void setBuffer(Device device, VkBuffer buffer, long range, int binding, int type) {
        try (var stack = MemoryStack.stackPush()) {
            var bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                    .buffer(buffer.getBuffer())
                    .offset(0)
                    .range(range);

            var descrBuffer = VkWriteDescriptorSet.calloc(1, stack);

            descrBuffer.get(0)
                    .sType$Default()
                    .dstSet(vkDescriptorSet)
                    .dstBinding(binding)
                    .descriptorType(type)
                    .descriptorCount(1)
                    .pBufferInfo(bufferInfo);

            vkUpdateDescriptorSets(device.getVkDevice(), descrBuffer, null);
        }
    }

    /**
     * 将图像采样器绑定到描述符集中的指定绑定点（Combined Image Sampler）。
     *
     * @param device    逻辑设备
     * @param sampler   Vulkan 采样器句柄
     * @param imageView Vulkan 图像视图句柄
     * @param binding   绑定点索引
     * @param type      描述符类型（如 VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER）
     */
    public void setImage(Device device, long sampler, long imageView, int binding, int type) {
        try (var stack = MemoryStack.stackPush()) {
            var imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                    .sampler(sampler)
                    .imageView(imageView)
                    .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

            var descrImage = VkWriteDescriptorSet.calloc(1, stack);

            descrImage.get(0)
                    .sType$Default()
                    .dstSet(vkDescriptorSet)
                    .dstBinding(binding)
                    .descriptorType(type)
                    .descriptorCount(1)
                    .pImageInfo(imageInfo);

            vkUpdateDescriptorSets(device.getVkDevice(), descrImage, null);
        }
    }
}
