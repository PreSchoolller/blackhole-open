package io.github.preschoolller.blackhole.eng.graph.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;
import static io.github.preschoolller.blackhole.eng.graph.vk.VkUtils.vkCheck;

/**
 * Vulkan 缓冲区 —— 封装通过 VMA 创建的 GPU 缓冲区。
 * <p>
 * 缓冲区用于存储 GPU 可访问的数据（顶点、索引、Uniform、存储等）。
 * 本项目中主要用于存储顶点数据、索引数据和 Uniform Buffer。
 * <p>
 * 支持的操作：
 * <ul>
 *   <li>映射（map）—— 将 GPU 内存映射到 CPU 地址空间</li>
 *   <li>取消映射（unMap）—— 解除内存映射</li>
 *   <li>刷新（flush）—— 将 CPU 写入的数据刷新到 GPU</li>
 *   <li>设备地址（getAddress）—— 获取 GPU 端缓冲区地址</li>
 * </ul>
 */
public class VkBuffer {

    /** VMA 内存分配句柄 */
    private final long allocation;
    /** Vulkan 缓冲区句柄 */
    private final long buffer;
    /** 指针缓冲区（用于 VMA 映射操作） */
    private final PointerBuffer pb;
    /** 请求的缓冲区大小 */
    private final long requestedSize;
    /** GPU 设备地址（仅当创建时启用了 SHADER_DEVICE_ADDRESS_BIT 时有效） */
    private Long address;
    /** 映射后的 CPU 地址（未映射时为 NULL） */
    private long mappedMemory;

    /**
     * 创建缓冲区。
     *
     * @param vkCtx       Vulkan 上下文
     * @param size        缓冲区大小（字节）
     * @param bufferUsage 缓冲区用途标志（如 VERTEX_BUFFER、UNIFORM_BUFFER 等）
     * @param vmaUsage    VMA 内存使用标志
     * @param vmaFlags    VMA 分配标志
     * @param reqFlags    要求的内存属性标志
     */
    public VkBuffer(VkCtx vkCtx, long size, int bufferUsage, int vmaUsage, int vmaFlags, int reqFlags) {
        requestedSize = size;
        mappedMemory = NULL;
        try (var stack = MemoryStack.stackPush()) {
            var bufferCreateInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(bufferUsage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            VmaAllocationCreateInfo allocInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(vmaUsage)
                    .flags(vmaFlags)
                    .requiredFlags(reqFlags);

            PointerBuffer pAllocation = stack.callocPointer(1);
            LongBuffer lp = stack.mallocLong(1);
            vkCheck(vmaCreateBuffer(vkCtx.getMemAlloc().getVmaAlloc(), bufferCreateInfo, allocInfo, lp,
                    pAllocation, null), "Failed to create buffer");
            buffer = lp.get(0);
            allocation = pAllocation.get(0);
            pb = MemoryUtil.memAllocPointer(1);
            // 如果启用了设备地址，立即获取 GPU 端地址
            if ((bufferUsage & VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT) > 0) {
                address = VkUtils.getBufferAddress(vkCtx, buffer);
            }
        }
    }

    /** 释放缓冲区和取消映射 */
    public void cleanup(VkCtx vkCtx) {
        MemoryUtil.memFree(pb);
        unMap(vkCtx);
        vmaDestroyBuffer(vkCtx.getMemAlloc().getVmaAlloc(), buffer, allocation);
    }

    /** 将 CPU 写入的数据刷新到 GPU 可见 */
    public void flush(VkCtx vkCtx) {
        vmaFlushAllocation(vkCtx.getMemAlloc().getVmaAlloc(), allocation, 0, VK_WHOLE_SIZE);
    }

    /**
     * 获取 GPU 设备地址。
     * 需要创建时启用 VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT。
     */
    public long getAddress() {
        if (address == null) {
            throw new IllegalStateException("Buffer was not created with VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT usage flag");
        }
        return address;
    }

    public long getBuffer() {
        return buffer;
    }

    public long getRequestedSize() {
        return requestedSize;
    }

    /**
     * 映射缓冲区到 CPU 地址空间。
     * 映射后可以通过返回的地址直接读写 GPU 内存。
     *
     * @return CPU 端内存地址
     */
    public long map(VkCtx vkCtx) {
        if (mappedMemory == NULL) {
            vkCheck(vmaMapMemory(vkCtx.getMemAlloc().getVmaAlloc(), allocation, pb), "Failed to map buffer");
            mappedMemory = pb.get(0);
        }
        return mappedMemory;
    }

    /** 取消缓冲区的内存映射 */
    public void unMap(VkCtx vkCtx) {
        if (mappedMemory != NULL) {
            vmaUnmapMemory(vkCtx.getMemAlloc().getVmaAlloc(), allocation);
            mappedMemory = NULL;
        }
    }
}
