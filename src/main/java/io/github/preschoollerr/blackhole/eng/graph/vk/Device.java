package io.github.preschoollerr.blackhole.eng.graph.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;
import io.github.preschoollerr.blackhole.eng.EngCfg;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.vulkan.KHRPortabilitySubset.VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK13.*;
import static io.github.preschoollerr.blackhole.eng.graph.vk.VkUtils.vkCheck;

/**
 * 逻辑设备 —— 封装 {@link VkDevice}，代表与 GPU 的逻辑连接。
 * <p>
 * 创建时启用以下特性：
 * <ul>
 *   <li>{@code bufferDeviceAddress}    —— GPU 缓冲区设备地址（VMA 需要）</li>
 *   <li>{@code scalarBlockLayout}      —— 标量块布局（着色器对齐更灵活）</li>
 *   <li>{@code dynamicRendering}       —— 动态渲染（Vulkan 1.3，替代 RenderPass）</li>
 *   <li>{@code synchronization2}       —— 同步2（Vulkan 1.3，更精细的屏障控制）</li>
 *   <li>{@code samplerAnisotropy}      —— 各向异性过滤（纹理采样质量）</li>
 *   <li>{@code geometryShader}         —— 几何着色器</li>
 *   <li>{@code depthClamp}             —— 深度钳制（可选，防止远平面裁剪）</li>
 *   <li>{@code multiDrawIndirect}      —— 多重间接绘制</li>
 *   <li>{@code shaderInt64}            —— 着色器 64 位整数支持</li>
 *   <li>{@code drawIndirectFirstInstance} —— 间接绘制首实例</li>
 * </ul>
 * <p>
 * 启用的设备扩展：
 * <ul>
 *   <li>{@code VK_KHR_swapchain}       —— 交换链支持（必需）</li>
 *   <li>{@code VK_KHR_portability_subset} —— macOS MoltenVK 兼容（可选）</li>
 * </ul>
 */
public class Device {

    /** 是否支持深度钳制特性 */
    private final boolean depthClamp;
    /** 是否支持各向异性过滤 */
    private final boolean samplerAnisotropy;
    /** 逻辑设备句柄 */
    private final VkDevice vkDevice;

    /**
     * 创建逻辑设备：配置队列族、设备特性和扩展。
     *
     * @param physDevice 物理设备
     */
    public Device(PhysDevice physDevice) {
        Logger.debug("Creating device");

        try (var stack = MemoryStack.stackPush()) {
            PointerBuffer reqExtensions = createReqExtensions(physDevice, stack);

            // 启用所有队列族：为每个队列族的每个队列设置优先级
            var queuePropsBuff = physDevice.getVkQueueFamilyProps();
            int numQueuesFamilies = queuePropsBuff.capacity();
            var queueCreationInfoBuf = VkDeviceQueueCreateInfo.calloc(numQueuesFamilies, stack);
            for (int i = 0; i < numQueuesFamilies; i++) {
                FloatBuffer priorities = stack.callocFloat(queuePropsBuff.get(i).queueCount());
                queueCreationInfoBuf.get(i)
                        .sType$Default()
                        .queueFamilyIndex(i)
                        .pQueuePriorities(priorities);
            }

            // Vulkan 1.2 特性：缓冲区设备地址 + 标量块布局
            var features12 = VkPhysicalDeviceVulkan12Features.calloc(stack)
                    .sType$Default()
                    .bufferDeviceAddress(true)
                    .scalarBlockLayout(true);

            // Vulkan 1.3 特性：动态渲染 + 同步2
            var features13 = VkPhysicalDeviceVulkan13Features.calloc(stack)
                    .sType$Default()
                    .dynamicRendering(true)
                    .synchronization2(true);

            // 基础设备特性
            var features2 = VkPhysicalDeviceFeatures2.calloc(stack).sType$Default();
            var features = features2.features();

            // 根据物理设备支持情况启用各向异性过滤
            VkPhysicalDeviceFeatures supportedFeatures = physDevice.getVkPhysicalDeviceFeatures();
            samplerAnisotropy = supportedFeatures.samplerAnisotropy();
            if (samplerAnisotropy) {
                features.samplerAnisotropy(true);
            }
            // 以下特性按物理设备实际支持启用：MoltenVK 基于 Metal，不支持几何
            // 着色器，无条件开启会导致 vkCreateDevice 报 VK_ERROR_FEATURE_NOT_PRESENT
            features.geometryShader(supportedFeatures.geometryShader());
            // 深度钳制（可选）
            depthClamp = supportedFeatures.depthClamp();
            features.depthClamp(depthClamp);
            features.multiDrawIndirect(supportedFeatures.multiDrawIndirect());
            features.shaderInt64(supportedFeatures.shaderInt64());
            features.drawIndirectFirstInstance(supportedFeatures.drawIndirectFirstInstance());

            // 构建特性链：features2 → features12 → features13
            features2.pNext(features12.address());
            features12.pNext(features13.address());

            var deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pNext(features2.address())
                    .ppEnabledExtensionNames(reqExtensions)
                    .pQueueCreateInfos(queueCreationInfoBuf);

            PointerBuffer pp = stack.mallocPointer(1);
            vkCheck(vkCreateDevice(physDevice.getVkPhysicalDevice(), deviceCreateInfo, null, pp),
                    "Failed to create device");
            vkDevice = new VkDevice(pp.get(0), physDevice.getVkPhysicalDevice(), deviceCreateInfo);
        }
    }

    /**
     * 构建设备扩展列表：必需扩展 + macOS Portability 扩展。
     */
    private static PointerBuffer createReqExtensions(PhysDevice physDevice, MemoryStack stack) {
        Set<String> deviceExtensions = getDeviceExtensions(physDevice);
        boolean usePortability = deviceExtensions.contains(VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME) && VkUtils.getOS() == VkUtils.OSType.MACOS;

        var extsList = new ArrayList<ByteBuffer>();
        for (String extension : PhysDevice.REQUIRED_EXTENSIONS) {
            extsList.add(stack.ASCII(extension));
        }
        if (usePortability) {
            extsList.add(stack.ASCII(VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME));
        }
        PointerBuffer requiredExtensions = stack.mallocPointer(extsList.size());
        extsList.forEach(requiredExtensions::put);
        requiredExtensions.flip();

        return requiredExtensions;
    }

    /** 枚举物理设备支持的所有扩展 */
    private static Set<String> getDeviceExtensions(PhysDevice physDevice) {
        Set<String> deviceExtensions = new HashSet<>();
        try (var stack = MemoryStack.stackPush()) {
            IntBuffer numExtensionsBuf = stack.callocInt(1);
            vkEnumerateDeviceExtensionProperties(physDevice.getVkPhysicalDevice(), (String) null, numExtensionsBuf, null);
            int numExtensions = numExtensionsBuf.get(0);
            Logger.trace("Device supports [{}] extensions", numExtensions);

            try (var propsBuff = VkExtensionProperties.calloc(numExtensions)) {
                vkEnumerateDeviceExtensionProperties(physDevice.getVkPhysicalDevice(), (String) null, numExtensionsBuf, propsBuff);
                for (int i = 0; i < numExtensions; i++) {
                    VkExtensionProperties props = propsBuff.get(i);
                    String extensionName = props.extensionNameString();
                    deviceExtensions.add(extensionName);
                    Logger.trace("Supported device extension [{}]", extensionName);
                }

            }
        }
        return deviceExtensions;
    }

    /** 销毁逻辑设备 */
    public void cleanup() {
        Logger.debug("Destroying Vulkan device");
        vkDestroyDevice(vkDevice, null);
    }

    public boolean getDepthClamp() {
        return depthClamp;
    }

    public VkDevice getVkDevice() {
        return vkDevice;
    }

    public boolean isSamplerAnisotropy() {
        return samplerAnisotropy;
    }

    /** 等待设备空闲（阻塞直到所有 GPU 操作完成） */
    public void waitIdle() {
        vkDeviceWaitIdle(vkDevice);
    }
}
