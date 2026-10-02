package io.github.preschoolller.blackhole.eng.graph.vk;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.tinylog.Logger;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK13.vkCreateDescriptorSetLayout;
import static org.lwjgl.vulkan.VK13.vkDestroyDescriptorSetLayout;
import static io.github.preschoolller.blackhole.eng.graph.vk.VkUtils.vkCheck;

/**
 * 描述符集布局 —— 定义描述符集中绑定的结构。
 * <p>
 * 布局描述了：
 * <ul>
 *   <li>每个绑定点的描述符类型（Uniform Buffer / Sampler 等）</li>
 *   <li>每个绑定点的描述符数量</li>
 *   <li>每个绑定点的着色器阶段可见性</li>
 * </ul>
 * <p>
 * 布局在管线创建时使用，确保着色器和 CPU 端的资源绑定一致。
 */
public class DescSetLayout {

    /** 布局信息数组（每个绑定点一个） */
    private final LayoutInfo[] layoutInfos;
    /** Vulkan 描述符集布局句柄 */
    protected long vkDescLayout;

    /**
     * 创建单绑定的描述符集布局。
     *
     * @param vkCtx      Vulkan 上下文
     * @param layoutInfo 布局信息
     */
    public DescSetLayout(VkCtx vkCtx, LayoutInfo layoutInfo) {
        this(vkCtx, new LayoutInfo[]{layoutInfo});
    }

    /**
     * 创建多绑定的描述符集布局。
     *
     * @param vkCtx       Vulkan 上下文
     * @param layoutInfos 布局信息数组
     */
    public DescSetLayout(VkCtx vkCtx, LayoutInfo[] layoutInfos) {
        this.layoutInfos = layoutInfos;
        try (var stack = MemoryStack.stackPush()) {
            int count = layoutInfos.length;
            var layoutBindings = VkDescriptorSetLayoutBinding.calloc(count, stack);
            for (int i = 0; i < count; i++) {
                LayoutInfo layoutInfo = layoutInfos[i];
                layoutBindings.get(i)
                        .binding(layoutInfo.binding())
                        .descriptorType(layoutInfo.descType())
                        .descriptorCount(layoutInfo.descCount())
                        .stageFlags(layoutInfo.stage());
            }

            var vkLayoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default().pBindings(layoutBindings);

            LongBuffer pSetLayout = stack.mallocLong(1);
            vkCheck(vkCreateDescriptorSetLayout(vkCtx.getDevice().getVkDevice(), vkLayoutInfo, null, pSetLayout),
                    "Failed to create descriptor set layout");
            vkDescLayout = pSetLayout.get(0);
        }
    }

    /** 销毁描述符集布局 */
    public void cleanup(VkCtx vkCtx) {
        Logger.debug("Destroying descriptor set layout");
        vkDestroyDescriptorSetLayout(vkCtx.getDevice().getVkDevice(), vkDescLayout, null);
    }

    /** 获取第一个布局信息 */
    public LayoutInfo getLayoutInfo() {
        return getLayoutInfos()[0];
    }

    public LayoutInfo[] getLayoutInfos() {
        return layoutInfos;
    }

    public long getVkDescLayout() {
        return vkDescLayout;
    }

    /**
     * 布局信息记录：描述单个绑定点的配置。
     *
     * @param descType  描述符类型（如 VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER）
     * @param binding   绑定点索引
     * @param descCount 该绑定点的描述符数量
     * @param stage     着色器阶段可见性标志
     */
    public record LayoutInfo(int descType, int binding, int descCount, int stage) {
    }
}
