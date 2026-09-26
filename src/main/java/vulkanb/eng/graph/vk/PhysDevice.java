package vulkanb.eng.graph.vk;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.tinylog.Logger;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static org.lwjgl.vulkan.VK13.*;
import static vulkanb.eng.graph.vk.VkUtils.vkCheck;

/**
 * 物理设备封装 —— 封装 {@link VkPhysicalDevice}，代表实际的 GPU 硬件。
 * <p>
 * 选择逻辑（按优先级）：
 * <ol>
 *   <li>如果配置了首选设备名称且匹配，直接使用</li>
 *   <li>否则优先选择独立显卡（DISCRETE_GPU）</li>
 *   <li>过滤条件：必须支持图形队列族 + 必须支持所需扩展</li>
 * </ol>
 * <p>
 * 缓存的设备信息：
 * <ul>
 *   <li>设备属性（名称、类型等）</li>
 *   <li>设备特性（各向异性、深度钳制等）</li>
 *   <li>队列族属性（图形/计算/传输能力）</li>
 *   <li>设备扩展列表</li>
 *   <li>内存属性（堆大小、类型等）</li>
 * </ul>
 */
public class PhysDevice {

    /** 所需设备扩展：仅交换链扩展 */
    protected static final Set<String> REQUIRED_EXTENSIONS;

    static {
        REQUIRED_EXTENSIONS = new HashSet<>();
        REQUIRED_EXTENSIONS.add(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME);
    }

    /** 厂商诊断检查点扩展类型（NVIDIA/AMD 专有调试功能） */
    private final CheckPointExtension checkPointExtension;
    /** 设备支持的扩展列表 */
    private final VkExtensionProperties.Buffer vkDeviceExtensions;
    /** 设备内存属性（堆信息、内存类型等） */
    private final VkPhysicalDeviceMemoryProperties vkMemoryProperties;
    /** 物理设备句柄 */
    private final VkPhysicalDevice vkPhysicalDevice;
    /** 设备支持的特性（各向异性、几何着色器等） */
    private final VkPhysicalDeviceFeatures vkPhysicalDeviceFeatures;
    /** 设备属性2（包含设备名称、类型等） */
    private final VkPhysicalDeviceProperties2 vkPhysicalDeviceProperties;
    /** 队列族属性（每个队列族的能力和数量） */
    private final VkQueueFamilyProperties.Buffer vkQueueFamilyProps;

    /**
     * 私有构造：查询并缓存物理设备的所有信息。
     *
     * @param vkPhysicalDevice Vulkan 物理设备句柄
     */
    private PhysDevice(VkPhysicalDevice vkPhysicalDevice) {
        try (var stack = MemoryStack.stackPush()) {
            this.vkPhysicalDevice = vkPhysicalDevice;

            IntBuffer intBuffer = stack.mallocInt(1);

            // 获取设备属性（名称、设备类型、API 版本等）
            vkPhysicalDeviceProperties = VkPhysicalDeviceProperties2.calloc().sType$Default();
            vkGetPhysicalDeviceProperties2(vkPhysicalDevice, vkPhysicalDeviceProperties);

            // 获取设备支持的扩展
            vkCheck(vkEnumerateDeviceExtensionProperties(vkPhysicalDevice, (String) null, intBuffer, null),
                    "Failed to get number of device extension properties");
            vkDeviceExtensions = VkExtensionProperties.calloc(intBuffer.get(0));
            vkCheck(vkEnumerateDeviceExtensionProperties(vkPhysicalDevice, (String) null, intBuffer, vkDeviceExtensions),
                    "Failed to get extension properties");

            // 获取队列族属性（图形、计算、传输队列的能力）
            vkGetPhysicalDeviceQueueFamilyProperties(vkPhysicalDevice, intBuffer, null);
            vkQueueFamilyProps = VkQueueFamilyProperties.calloc(intBuffer.get(0));
            vkGetPhysicalDeviceQueueFamilyProperties(vkPhysicalDevice, intBuffer, vkQueueFamilyProps);

            // 获取设备支持的特性
            vkPhysicalDeviceFeatures = VkPhysicalDeviceFeatures.calloc();
            vkGetPhysicalDeviceFeatures(vkPhysicalDevice, vkPhysicalDeviceFeatures);

            // 获取内存属性（堆大小、内存类型等）
            vkMemoryProperties = VkPhysicalDeviceMemoryProperties.calloc();
            vkGetPhysicalDeviceMemoryProperties(vkPhysicalDevice, vkMemoryProperties);

            // 检测厂商诊断检查点扩展
            checkPointExtension = calcCheckPointExtension(vkDeviceExtensions);
        }
    }

    /**
     * 创建物理设备：枚举所有设备并选择最佳匹配。
     *
     * @param instance           Vulkan 实例
     * @param preferredDeviceName 首选设备名称（null 表示自动选择）
     * @return 选中的物理设备
     * @throws RuntimeException 如果没有找到合适的设备
     */
    public static PhysDevice createPhysicalDevice(Instance instance, String preferredDeviceName) {
        Logger.debug("Selecting physical devices");
        PhysDevice result = null;
        try (var stack = MemoryStack.stackPush()) {
            PointerBuffer pPhysicalDevices = getPhysicalDevices(instance, stack);
            int numDevices = pPhysicalDevices.capacity();

            var physDevices = new ArrayList<PhysDevice>();
            for (int i = 0; i < numDevices; i++) {
                var vkPhysicalDevice = new VkPhysicalDevice(pPhysicalDevices.get(i), instance.getVkInstance());
                var physDevice = new PhysDevice(vkPhysicalDevice);

                String deviceName = physDevice.getDeviceName();
                // 检查是否支持图形队列族
                if (!physDevice.hasGraphicsQueueFamily()) {
                    Logger.debug("Device [{}] does not support graphics queue family", deviceName);
                    physDevice.cleanup();
                    continue;
                }

                // 检查是否支持所有必需扩展
                if (!physDevice.supportsExtensions(REQUIRED_EXTENSIONS)) {
                    Logger.debug("Device [{}] does not support required extensions", deviceName);
                    physDevice.cleanup();
                    continue;
                }

                // 如果指定了首选设备名称且匹配，直接使用
                if (preferredDeviceName != null && preferredDeviceName.equals(deviceName)) {
                    result = physDevice;
                    break;
                }
                // 独立显卡优先
                if (physDevice.vkPhysicalDeviceProperties.properties().deviceType() == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {
                    physDevices.add(0, physDevice);
                } else {
                    physDevices.add(physDevice);
                }
            }

            // 没有首选设备或不满足要求，选第一个
            result = result == null && !physDevices.isEmpty() ? physDevices.remove(0) : result;

            // 清理未选中的设备
            physDevices.forEach(PhysDevice::cleanup);

            if (result == null) {
                throw new RuntimeException("No suitable physical devices found");
            }
            Logger.debug("Selected device: [{}]", result.getDeviceName());
        }

        return result;
    }

    /** 枚举所有可用的物理设备 */
    protected static PointerBuffer getPhysicalDevices(Instance instance, MemoryStack stack) {
        PointerBuffer pPhysicalDevices;
        IntBuffer intBuffer = stack.mallocInt(1);
        vkCheck(vkEnumeratePhysicalDevices(instance.getVkInstance(), intBuffer, null),
                "Failed to get number of physical devices");
        int numDevices = intBuffer.get(0);
        Logger.debug("Detected {} physical device(s)", numDevices);

        pPhysicalDevices = stack.mallocPointer(numDevices);
        vkCheck(vkEnumeratePhysicalDevices(instance.getVkInstance(), intBuffer, pPhysicalDevices),
                "Failed to get physical devices");
        return pPhysicalDevices;
    }

    /**
     * 检测设备是否支持厂商诊断检查点扩展。
     * NVIDIA 和 AMD 各有专有扩展用于 GPU 调试。
     */
    private CheckPointExtension calcCheckPointExtension(VkExtensionProperties.Buffer vkDeviceExtensions) {
        var result = CheckPointExtension.NONE;

        int numExtensions = vkDeviceExtensions != null ? vkDeviceExtensions.capacity() : 0;
        for (int i = 0; i < numExtensions; i++) {
            String extensionName = vkDeviceExtensions.get(i).extensionNameString();
            if (NVDeviceDiagnosticCheckpoints.VK_NV_DEVICE_DIAGNOSTIC_CHECKPOINTS_EXTENSION_NAME.equals(extensionName)) {
                result = CheckPointExtension.NVIDIA;
                break;
            } else if (AMDBufferMarker.VK_AMD_BUFFER_MARKER_EXTENSION_NAME.equals(extensionName)) {
                result = CheckPointExtension.AMD;
                break;
            }
        }
        return result;
    }

    /** 释放堆分配的 Vulkan 对象 */
    public void cleanup() {
        Logger.debug("Destroying physical device [{}]", getDeviceName());
        vkMemoryProperties.free();
        vkPhysicalDeviceFeatures.free();
        vkQueueFamilyProps.free();
        vkDeviceExtensions.free();
        vkPhysicalDeviceProperties.free();
    }

    public CheckPointExtension getCheckPointExtension() {
        return checkPointExtension;
    }

    public String getDeviceName() {
        return vkPhysicalDeviceProperties.properties().deviceNameString();
    }

    public VkPhysicalDeviceMemoryProperties getVkMemoryProperties() {
        return vkMemoryProperties;
    }

    public VkPhysicalDevice getVkPhysicalDevice() {
        return vkPhysicalDevice;
    }

    public VkPhysicalDeviceFeatures getVkPhysicalDeviceFeatures() {
        return vkPhysicalDeviceFeatures;
    }

    public VkPhysicalDeviceProperties2 getVkPhysicalDeviceProperties() {
        return vkPhysicalDeviceProperties;
    }

    public VkQueueFamilyProperties.Buffer getVkQueueFamilyProps() {
        return vkQueueFamilyProps;
    }

    /** 检查是否至少有一个队列族支持图形操作 */
    private boolean hasGraphicsQueueFamily() {
        boolean result = false;
        int numQueueFamilies = vkQueueFamilyProps != null ? vkQueueFamilyProps.capacity() : 0;
        for (int i = 0; i < numQueueFamilies; i++) {
            VkQueueFamilyProperties familyProps = vkQueueFamilyProps.get(i);
            if ((familyProps.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                result = true;
                break;
            }
        }
        return result;
    }

    /**
     * 检查设备是否支持指定的所有扩展。
     *
     * @param extensions 需要检查的扩展集合
     * @return 是否全部支持
     */
    public boolean supportsExtensions(Set<String> extensions) {
        var copyExtensions = new HashSet<>(extensions);
        int numExtensions = vkDeviceExtensions != null ? vkDeviceExtensions.capacity() : 0;
        for (int i = 0; i < numExtensions; i++) {
            String extensionName = vkDeviceExtensions.get(i).extensionNameString();
            copyExtensions.remove(extensionName);
        }

        boolean result = copyExtensions.isEmpty();
        if (!result) {
            Logger.debug("At least [{}] extension is not supported by device [{}]", copyExtensions.iterator().next(),
                    getDeviceName());
        }
        return result;
    }

    /** 厂商诊断检查点扩展枚举 */
    public enum CheckPointExtension {
        NONE, NVIDIA, AMD;
    }
}
