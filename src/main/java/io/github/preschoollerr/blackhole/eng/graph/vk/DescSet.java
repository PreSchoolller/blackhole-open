package io.github.preschoollerr.blackhole.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK13.*;
import static io.github.preschoollerr.blackhole.eng.graph.vk.VkUtils.vkCheck;

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
        setImage(device, sampler, imageView, binding, type, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
    }

    /**
     * 将图像绑定到指定绑定点（可指定图像布局）。
     * <p>
     * 存储图像（VK_DESCRIPTOR_TYPE_STORAGE_IMAGE，compute imageStore 写入）必须传
     * VK_IMAGE_LAYOUT_GENERAL；采样读取用默认 SHADER_READ_ONLY_OPTIMAL。
     *
     * @param imageLayout 描述符声明的图像布局（须与 barrier 后实际布局一致）
     */
    public void setImage(Device device, long sampler, long imageView, int binding, int type, int imageLayout) {
        try (var stack = MemoryStack.stackPush()) {
            var imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                    .sampler(sampler)
                    .imageView(imageView)
                    .imageLayout(imageLayout);

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

    /**
     * 将图像数组绑定到指定绑定点的 dstArrayElement 0..n-1（如 NPGS ColorBlend 的
     * {@code iBloomTexs[2]}：[0]=黑洞原图，[1]=模糊图集）。所有元素按
     * SHADER_READ_ONLY_OPTIMAL 布局写入。
     */
    public void setImageArray(Device device, long[] samplers, long[] imageViews, int binding, int type) {
        try (var stack = MemoryStack.stackPush()) {
            int n = samplers.length;
            var imageInfo = VkDescriptorImageInfo.calloc(n, stack);
            for (int i = 0; i < n; i++) {
                imageInfo.get(i)
                        .sampler(samplers[i])
                        .imageView(imageViews[i])
                        .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            }

            var descrImage = VkWriteDescriptorSet.calloc(1, stack);
            descrImage.get(0)
                    .sType$Default()
                    .dstSet(vkDescriptorSet)
                    .dstBinding(binding)
                    .descriptorType(type)
                    .descriptorCount(n)
                    .pImageInfo(imageInfo);

            vkUpdateDescriptorSets(device.getVkDevice(), descrImage, null);
        }
    }
}
