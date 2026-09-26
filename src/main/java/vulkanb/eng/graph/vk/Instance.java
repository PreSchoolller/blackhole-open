package vulkanb.eng.graph.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.VK13.*;
import static vulkanb.eng.graph.vk.VkUtils.vkCheck;

/**
 * Vulkan 实例 —— 应用程序与 Vulkan 驱动之间的连接点。
 * <p>
 * 职责：
 * <ul>
 *   <li>创建 VkInstance（指定 API 版本 1.3）</li>
 *   <li>启用验证层（可选，用于开发调试）</li>
 *   <li>启用调试回调（接收验证层的错误/警告消息）</li>
 *   <li>处理 macOS Portability 扩展（ MoltenVK 兼容）</li>
 * </ul>
 * <p>
 * 验证层在以下情况自动禁用：
 * <ul>
 *   <li>配置文件中 {@code vkValidate=false}</li>
 *   <li>系统不支持 Khronos 验证层</li>
 * </ul>
 */
public class Instance {

    /** 调试消息严重级别掩码：仅报告错误和警告 */
    public static final int MESSAGE_SEVERITY_BITMASK = VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT;
    /** 调试消息类型掩码：通用、验证和性能消息 */
    public static final int MESSAGE_TYPE_BITMASK = VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
            VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT;
    private static final String DBG_CALL_BACK_PREF = "VkDebugUtilsCallback, {}";
    /** macOS Portability 枚举扩展名 */
    private static final String PORTABILITY_EXTENSION = "VK_KHR_portability_enumeration";
    /** Khronos 标准验证层名称 */
    private static final String VALIDATION_LAYER = "VK_LAYER_KHRONOS_validation";

    /** Vulkan 实例句柄 */
    private final VkInstance vkInstance;
    /** 调试回调创建信息（堆分配，生命周期与 Instance 相同） */
    private VkDebugUtilsMessengerCreateInfoEXT debugUtils;
    /** 调试消息回调句柄 */
    private long vkDebugHandle;

    /**
     * 创建 Vulkan 实例。
     *
     * @param validate 是否启用验证层
     */
    public Instance(boolean validate) {
        Logger.debug("Creating Vulkan instance");
        try (var stack = MemoryStack.stackPush()) {
            // 创建应用信息：指定 API 版本为 Vulkan 1.3
            ByteBuffer appShortName = stack.UTF8("VulkanBook");
            var appInfo = VkApplicationInfo.calloc(stack)
                    .sType$Default()
                    .pApplicationName(appShortName)
                    .applicationVersion(1)
                    .pEngineName(appShortName)
                    .engineVersion(0)
                    .apiVersion(VK_API_VERSION_1_3);

            // 查询并配置验证层
            List<String> validationLayers = getSupportedValidationLayers();
            int numValidationLayers = validationLayers.size();
            boolean supportsValidation = validate;
            if (validate && numValidationLayers == 0) {
                supportsValidation = false;
                Logger.warn("Request validation but no supported validation layers found. Falling back to no validation");
            }
            Logger.debug("Validation: {}", supportsValidation);

            // 将验证层名称转为指针缓冲区
            PointerBuffer requiredLayers = null;
            if (supportsValidation) {
                requiredLayers = stack.mallocPointer(numValidationLayers);
                for (int i = 0; i < numValidationLayers; i++) {
                    Logger.debug("Using validation layer [{}]", validationLayers.get(i));
                    requiredLayers.put(i, stack.ASCII(validationLayers.get(i)));
                }
            }

            // 检查是否需要 macOS Portability 扩展
            Set<String> instanceExtensions = getInstanceExtensions();
            boolean usePortability = instanceExtensions.contains(PORTABILITY_EXTENSION) &&
                    VkUtils.getOS() == VkUtils.OSType.MACOS;

            // 获取 GLFW 所需的平台表面扩展
            PointerBuffer glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
            if (glfwExtensions == null) {
                throw new RuntimeException("Failed to find the GLFW platform surface extensions");
            }
            var additionalExtensions = new ArrayList<ByteBuffer>();
            // 如果启用验证，添加调试工具扩展
            if (supportsValidation) {
                additionalExtensions.add(stack.UTF8(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME));
            }
            // 如果是 macOS 且支持 Portability，添加枚举扩展
            if (usePortability) {
                additionalExtensions.add(stack.UTF8(PORTABILITY_EXTENSION));
            }
            int numAdditionalExtensions = additionalExtensions.size();

            // 合并所有需要的扩展：GLFW 扩展 + 额外扩展
            PointerBuffer requiredExtensions = stack.mallocPointer(glfwExtensions.remaining() + numAdditionalExtensions);
            requiredExtensions.put(glfwExtensions);
            for (int i = 0; i < numAdditionalExtensions; i++) {
                requiredExtensions.put(additionalExtensions.get(i));
            }
            requiredExtensions.flip();

            // 创建调试回调
            long extension = MemoryUtil.NULL;
            if (supportsValidation) {
                debugUtils = createDebugCallBack();
                extension = debugUtils.address();
            }

            // 创建实例
            var instanceInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pNext(extension)
                    .pApplicationInfo(appInfo)
                    .ppEnabledLayerNames(null)
                    .ppEnabledExtensionNames(requiredExtensions);
            if (usePortability) {
                // VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR：启用 Portability 设备枚举
                instanceInfo.flags(0x00000001);
            }

            PointerBuffer pInstance = stack.mallocPointer(1);
            vkCheck(vkCreateInstance(instanceInfo, null, pInstance), "Error creating instance");
            vkInstance = new VkInstance(pInstance.get(0), instanceInfo);

            // 创建调试消息回调
            vkDebugHandle = VK_NULL_HANDLE;
            if (supportsValidation) {
                LongBuffer longBuff = stack.mallocLong(1);
                vkCheck(vkCreateDebugUtilsMessengerEXT(vkInstance, debugUtils, null, longBuff), "Error creating debug utils");
                vkDebugHandle = longBuff.get(0);
            }
        }
    }

    /**
     * 创建调试回调：将 Vulkan 验证层消息路由到日志系统。
     *
     * @return 调试回调创建信息
     */
    private static VkDebugUtilsMessengerCreateInfoEXT createDebugCallBack() {
        return VkDebugUtilsMessengerCreateInfoEXT
                .calloc()
                .sType$Default()
                .messageSeverity(MESSAGE_SEVERITY_BITMASK)
                .messageType(MESSAGE_TYPE_BITMASK)
                .pfnUserCallback((messageSeverity, messageTypes, pCallbackData, pUserData) -> {
                    VkDebugUtilsMessengerCallbackDataEXT callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
                    if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT) != 0) {
                        Logger.info(DBG_CALL_BACK_PREF, callbackData.pMessageString());
                    } else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0) {
                        Logger.warn(DBG_CALL_BACK_PREF, callbackData.pMessageString());
                    } else if ((messageSeverity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) {
                        Logger.error(DBG_CALL_BACK_PREF, callbackData.pMessageString());
                    } else {
                        Logger.debug(DBG_CALL_BACK_PREF, callbackData.pMessageString());
                    }
                    return VK_FALSE;
                });
    }

    /**
     * 枚举所有可用的实例扩展。
     *
     * @return 扩展名称集合
     */
    private static Set<String> getInstanceExtensions() {
        Set<String> instanceExtensions = new HashSet<>();
        try (var stack = MemoryStack.stackPush()) {
            IntBuffer numExtensionsBuf = stack.callocInt(1);
            vkEnumerateInstanceExtensionProperties((String) null, numExtensionsBuf, null);
            int numExtensions = numExtensionsBuf.get(0);
            Logger.trace("Instance supports [{}] extensions", numExtensions);

            var instanceExtensionsProps = VkExtensionProperties.calloc(numExtensions, stack);
            vkEnumerateInstanceExtensionProperties((String) null, numExtensionsBuf, instanceExtensionsProps);
            for (int i = 0; i < numExtensions; i++) {
                VkExtensionProperties props = instanceExtensionsProps.get(i);
                String extensionName = props.extensionNameString();
                instanceExtensions.add(extensionName);
                Logger.trace("Supported instance extension [{}]", extensionName);
            }
        }
        return instanceExtensions;
    }

    /**
     * 枚举所有支持的验证层，筛选出 Khronos 标准验证层。
     *
     * @return 可用的验证层名称列表
     */
    private static List<String> getSupportedValidationLayers() {
        try (var stack = MemoryStack.stackPush()) {
            IntBuffer numLayersArr = stack.callocInt(1);
            vkEnumerateInstanceLayerProperties(numLayersArr, null);
            int numLayers = numLayersArr.get(0);
            Logger.debug("Instance supports [{}] layers", numLayers);

            var propsBuf = VkLayerProperties.calloc(numLayers, stack);
            vkEnumerateInstanceLayerProperties(numLayersArr, propsBuf);
            List<String> supportedLayers = new ArrayList<>();
            for (int i = 0; i < numLayers; i++) {
                VkLayerProperties props = propsBuf.get(i);
                String layerName = props.layerNameString();
                supportedLayers.add(layerName);
                Logger.trace("Supported layer [{}]", layerName);
            }

            // 仅使用 Khronos 标准验证层
            List<String> layersToUse = new ArrayList<>();
            if (supportedLayers.contains(VALIDATION_LAYER)) {
                layersToUse.add(VALIDATION_LAYER);
            }

            return layersToUse;
        }
    }

    /** 销毁调试回调和 Vulkan 实例 */
    public void cleanup() {
        Logger.debug("Destroying Vulkan instance");
        if (vkDebugHandle != VK_NULL_HANDLE) {
            vkDestroyDebugUtilsMessengerEXT(vkInstance, vkDebugHandle, null);
        }
        vkDestroyInstance(vkInstance, null);
        if (debugUtils != null) {
            debugUtils.pfnUserCallback().free();
            debugUtils.free();
        }
    }

    public VkInstance getVkInstance() {
        return vkInstance;
    }
}
