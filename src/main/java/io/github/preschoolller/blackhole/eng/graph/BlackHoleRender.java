package io.github.preschoolller.blackhole.eng.graph;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import io.github.preschoolller.blackhole.eng.EngCfg;
import io.github.preschoolller.blackhole.eng.EngCtx;
import io.github.preschoolller.blackhole.eng.graph.vk.*;
import io.github.preschoolller.blackhole.eng.scene.Camera;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.lwjgl.vulkan.VK13.*;
import static org.lwjgl.util.vma.Vma.*;
import static io.github.preschoolller.blackhole.eng.graph.vk.VkUtils.vkCheck;

/**
 * 黑洞渲染器 —— 实现全屏光线追踪渲染 + TAA 时域抗锯齿。
 * <p>
 * 核心思路：
 * <ol>
 *   <li>使用全屏四边形（无顶点缓冲区）覆盖整个屏幕</li>
 *   <li>顶点着色器通过逆投影/逆视图矩阵将屏幕坐标反投影为世界空间射线</li>
 *   <li>片段着色器对每条射线执行光线步进（ray marching），模拟引力透镜效应</li>
 *   <li>通过参数 UBO（BlackHoleArgs，std140，每帧插槽一份、整块重写）传递相机矩阵、
 *       黑洞参数等数据到 GPU（2026-09-27 自 push constant 迁移,容量不再受 256B 限制,
 *       见 foragent/schwarzschild_ubo_panel_plan.md）</li>
 *   <li>TAA：HDR 输出到历史缓冲，片段着色器在 HDR 域与上一帧累积结果混合
 *       （静止全量累积；运动硬重置）；相机/投影变化时自动重置累积</li>
 *   <li>Bloom：合成 pass 读取本帧 HDR 历史，亮部提取 + 圆盘模糊 + 色调映射后写交换链</li>
 * </ol>
 * <p>
 * TAA 历史缓冲采用"按帧插槽 ping-pong"：每个帧插槽（MAX_IN_FLIGHT=2）拥有
 * 2 张历史图，插槽 s 读 hist[s][r]、写 hist[s][w]，下次运行（2 帧后，栅栏已保证
 * GPU 完成）翻转读写。插槽之间互不读写，因此无需跨帧信号量，同步完全自包含。
 * <p>
 * BlackHoleArgs 参数 UBO 内存布局（std140，共 272 字节 = 264 数据对齐 16；绑定为
 * 主管线 set 2 / bloom 管线 set 1，顶点+片段两阶段可见；三个 shader 的块声明逐字段一致）：
 * <pre>
 * [  0.. 63]  inverseView    mat4   (64 bytes)
 * [ 64..127]  inverseProj    mat4   (64 bytes)
 * [128..139]  cameraPos      vec3   (12 bytes)
 * [140..143]  time           float  (4 bytes)
 * [144..155]  blackHolePos   vec3   (12 bytes)
 * [156..159]  schwarzschildRadius float (4 bytes)，世界坐标长度单位
 * [160..163]  diskInnerRadius      float (4 bytes)，Rs 的倍数
 * [164..167]  diskOuterRadius      float (4 bytes)，Rs 的倍数
 * [168..171]  iExposure            float (4 bytes)，曝光增益（原 rotationSpeed 死字段槽位复用）
 * [172..175]  temperature          float (4 bytes)，基础温度(K)
 * [176..179]  if_dopplerI          int   (4 bytes)，多普勒亮度 A/B 开关
 * [180..183]  if_dopplerT          int   (4 bytes)，多普勒温度 A/B 开关
 * [184..187]  timeRate             float (4 bytes)，动画时间速率
 * [188..191]  iTimeDelta           float (4 bytes)，帧间隔（秒），TAA 用
 * [192..195]  iFrame               int   (4 bytes)，全局帧计数，TAA 用
 * [196..199]  iCameraMoved         int   (4 bytes)，TAA 三态：0=静止全量累积 2=平滑运动部分混合 1=硬重置
 * [200..203]  iRenderTime        float (4 bytes)，渲染时间（墙钟，暂停时仍流动；TAA 抖动种子）
 * [204..207]  iFade                float (4 bytes)，视界坠落淡出系数 0..1（兼作对齐）
 * [208..219]  iCameraVel           vec3  (12 bytes)，相机速度 β（单位 c，静态观者系；测地模式）
 * [220..223]  iCameraGamma         float (4 bytes)，相机洛伦兹因子 γ
 * [224..227]  iDiskScatter         float (4 bytes)，盘前向散射强度（0=关）
 * [228..231]  iDiskAmbient         float (4 bytes)，盘环境光强度（0=关）
 * [232..235]  iShiftMax            float (4 bytes)，盘频移钳制上限
 * [236..239]  iTaaTau              float (4 bytes)，TAA 静止累积 τ 基准秒
 * [240..243]  iBloomThreshold      float (4 bytes)，Bloom 亮部阈值
 * [244..247]  iBloomMix            float (4 bytes)，Bloom 辉光混合系数
 * [248..251]  iBloomMax            float (4 bytes)，Bloom 色调映射输出上限
 * [252..255]  iBackgroundBright    float (4 bytes)，背景亮度倍率（默认 0.7）
 * [256..259]  iToneMapStrength     float (4 bytes)，色调映射强度（默认 1.0）
 * [260..263]  iDiskHalfThickness   float (4 bytes)，盘半厚基准（Rs 倍数，默认 0.5）
 * [264..271]  std140 尾部对齐填充（8 bytes）
 * </pre>
 */
public class BlackHoleRender {

    /** TAA 历史缓冲格式（HDR 浮点，保证混合精度；交换链格式经 SwapChain.getImageFormat() 获取） */
    private static final int TAA_HISTORY_FORMAT = VK_FORMAT_R32G32B32A32_SFLOAT;
    /** 参数 UBO 字节数（BlackHoleArgs std140：264B 数据对齐 16 → 272；必须与三 shader 声明一致） */
    private static final int BH_ARGS_SIZE = 272;

    /** 参数 UBO（BlackHoleArgs，每帧插槽一份，持久映射，packParams 每帧整块重写） */
    private final VkBuffer[] argsUbo = new VkBuffer[2];
    private final long[] argsMapped = new long[2];
    /** 参数 UBO 描述符集布局（binding 0 = BlackHoleArgs，顶点+片段两阶段；主管线 set 2 / bloom set 1） */
    private DescSetLayout argsDescLayout;
    /** 参数 UBO 描述符集（每帧插槽一份，指向本插槽 UBO） */
    private DescSet[] argsDescSets;
    /** 图形管线（包含管线布局和管线对象） */
    private Pipeline pipeline;
    /** 双星空盒（主星空 + 山海，GUI 在 set0.b0 换绑切换，惰性加载） */
    private DualSkybox dualSkybox;
    /** 天空盒描述符集布局（set 0，binding 0 = combined image sampler） */
    private DescSetLayout skyboxDescLayout;
    /** 天空盒描述符集（静态资源，单份即可） */
    private DescSet skyboxDescSet;

    // ---- TAA 时域抗锯齿资源 ----
    /** 历史缓冲 [帧插槽][ping-pong] */
    private Image[][] taaHistImages;
    /** 历史缓冲视图 [帧插槽][ping-pong] */
    private ImageView[][] taaHistViews;
    /** TAA 历史描述符集布局（set 1，binding 0 = 上一帧颜色） */
    private DescSetLayout taaDescLayout;
    /** TAA 描述符集（每帧插槽一份，引用的历史视图每帧更新） */
    private DescSet[] taaDescSets;
    /** 读取历史用的 2D 采样器（texelFetch 不经过滤波，滤波方式随意） */
    private long taaSampler;

    // ---- Bloom 合成 pass 资源 ----
    /** Bloom 合成管线（读 HDR 历史 → 亮部提取 + 圆盘模糊 + 色调映射 → 交换链） */
    private Pipeline bloomPipeline;
    /** Bloom 场景描述符集布局（set 0，binding 0 = HDR 历史纹理） */
    private DescSetLayout bloomSceneLayout;
    /** Bloom 描述符集（每帧插槽一份，指向本帧刚写完的历史图） */
    private DescSet[] bloomDescSets;
    /** Bloom 线性采样器（圆盘模糊的线性采样） */
    private long bloomSampler;
    /** Bloom 合成 pass 的动态渲染信息（写交换链） */
    private VkRenderingInfo bloomRenderInfo;
    /** Bloom 合成 pass 颜色附件（交换链） */
    private VkRenderingAttachmentInfo.Buffer attInfoBloom;
    /** 每插槽本次要读取的历史下标（等于上次运行写入的下标） */
    private int[] taaReadIdx = {0, 0};
    /** 全局帧计数（传入 shader 的 iFrame，前 2 帧强制重置） */
    private int taaFrameCount;
    /** 每插槽强制硬重置标记（init/resize 后置位，由各插槽首次运行分别消费） */
    private final boolean[] taaForceReset = {true, true};
    /** 每插槽上一帧视图/投影矩阵（运动检测 + 与该插槽历史帧配对的重投影） */
    private final Matrix4f[] prevViewPerSlot = {new Matrix4f(), new Matrix4f()};
    private final Matrix4f[] prevProjPerSlot = {new Matrix4f(), new Matrix4f()};
    /** 重投影矩阵暂存（prevProj·prevView，世界方向 → 上一帧裁剪空间） */
    private final Matrix4f prevViewProj = new Matrix4f();
    /** 上一帧相机矩阵 UBO（每插槽一份，持久映射，供着色器方向重投影） */
    private final VkBuffer[] prevCamUbo = new VkBuffer[2];
    private final long[] prevCamMapped = new long[2];
    /** 上一帧时间戳（纳秒），用于计算 iTimeDelta */
    private long lastFrameNanos;

    /** 动态渲染信息（Vulkan 1.3 动态渲染替代传统 RenderPass） */
    private VkRenderingInfo renderInfo;
    /** 颜色附件描述（2 个：swapchain + TAA 历史写入目标） */
    private VkRenderingAttachmentInfo.Buffer attInfoColor;
    /** 应用启动时间（纳秒），用于计算动画时间 */
    private long startTime;
    /** 模拟时间累计器（秒）：驱动盘动画；时间暂停（P 键）时停止推进 */
    private float simTime;
    /** 上一帧墙钟时间（秒，自启动起），供 simTime 增量计算 */
    private float prevWallTime;

    /**
     * 构造黑洞渲染器。
     * 记录启动时间（参数 UBO 等资源在 init() 创建）。
     *
     * @param vkCtx Vulkan 上下文
     */
    public BlackHoleRender(VkCtx vkCtx) {
        startTime = System.nanoTime();
        lastFrameNanos = 0;
        taaFrameCount = 0;
    }

    /**
     * 初始化渲染管线：加载着色器、配置管线状态、创建图形管线。
     *
     * @param vkCtx           Vulkan 上下文
     * @param vertShaderPath  顶点着色器 SPIR-V 文件路径
     * @param fragShaderPath  片段着色器 SPIR-V 文件路径
     */
    public void init(VkCtx vkCtx, String vertShaderPath, String fragShaderPath, String bloomShaderPath) {
        // 背景星空 cubemap（从 classpath 加载并上传，一次性提交；双盒助手含山海盒惰性加载）
        var graphQueue = new Queue.GraphicsQueue(vkCtx, 0);
        dualSkybox = new DualSkybox(vkCtx, graphQueue);

        // 天空盒描述符集布局：set 0，binding 0 = combined image sampler（片段着色器读取）
        skyboxDescLayout = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(
                VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 0, 1, VK_SHADER_STAGE_FRAGMENT_BIT));
        // TAA 历史描述符集布局：set 1，binding 0 = 上一帧累积颜色；binding 1 = 上一帧相机矩阵（重投影）
        taaDescLayout = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo[]{
                new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 0, 1, VK_SHADER_STAGE_FRAGMENT_BIT),
                new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1, 1, VK_SHADER_STAGE_FRAGMENT_BIT)});
        // Bloom 场景描述符集布局：set 0，binding 0 = 本帧 HDR 历史
        bloomSceneLayout = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(
                VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 0, 1, VK_SHADER_STAGE_FRAGMENT_BIT));
        // 参数 UBO 描述符集布局：binding 0 = BlackHoleArgs；顶点（反投影矩阵）与片段（场景参数）
        // 两阶段共用。主管线绑 set 2、bloom 管线绑 set 1（同一 DescSet 实例，两处布局各占一号）
        argsDescLayout = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(
                VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 0, 1,
                VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT));

        // 创建 TAA 历史缓冲（按交换链尺寸）
        SwapChain swapChain = vkCtx.getSwapChain();
        VkExtent2D extent = swapChain.getSwapChainExtent();
        createTaaResources(vkCtx, graphQueue, extent.width(), extent.height());

        // 上一帧相机矩阵 UBO（每插槽一份，持久映射；内容每帧更新，供方向重投影）
        for (int i = 0; i < VkUtils.MAX_IN_FLIGHT; i++) {
            prevCamUbo[i] = new VkBuffer(vkCtx, 64,
                    VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VMA_MEMORY_USAGE_AUTO,
                    VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, 0);
            prevCamMapped[i] = prevCamUbo[i].map(vkCtx);
        }

        // 参数 UBO（每插槽一份，持久映射；packParams 每帧整块重写，主机写 GPU 读按帧栅栏隔离）
        for (int i = 0; i < VkUtils.MAX_IN_FLIGHT; i++) {
            argsUbo[i] = new VkBuffer(vkCtx, BH_ARGS_SIZE,
                    VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VMA_MEMORY_USAGE_AUTO,
                    VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, 0);
            argsMapped[i] = argsUbo[i].map(vkCtx);
        }

        // 分配 TAA / Bloom / 参数描述符集（每帧插槽一份；历史视图每帧渲染前更新）
        taaDescSets = new DescSet[VkUtils.MAX_IN_FLIGHT];
        bloomDescSets = new DescSet[VkUtils.MAX_IN_FLIGHT];
        argsDescSets = new DescSet[VkUtils.MAX_IN_FLIGHT];
        for (int i = 0; i < VkUtils.MAX_IN_FLIGHT; i++) {
            taaDescSets[i] = vkCtx.getDescAllocator().addDescSet(vkCtx.getDevice(), "blackhole-taa-" + i, taaDescLayout);
            taaDescSets[i].setBuffer(vkCtx.getDevice(), prevCamUbo[i], 64, 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
            bloomDescSets[i] = vkCtx.getDescAllocator().addDescSet(vkCtx.getDevice(), "blackhole-bloom-" + i, bloomSceneLayout);
            argsDescSets[i] = vkCtx.getDescAllocator().addDescSet(vkCtx.getDevice(), "blackhole-args-" + i, argsDescLayout);
            argsDescSets[i].setBuffer(vkCtx.getDevice(), argsUbo[i], BH_ARGS_SIZE, 0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
        }

        // 创建着色器模块（从 SPIR-V 文件加载）
        ShaderModule vertModule = new ShaderModule(vkCtx, VK_SHADER_STAGE_VERTEX_BIT, vertShaderPath, null);
        ShaderModule fragModule = new ShaderModule(vkCtx, VK_SHADER_STAGE_FRAGMENT_BIT, fragShaderPath, null);
        ShaderModule bloomModule = new ShaderModule(vkCtx, VK_SHADER_STAGE_FRAGMENT_BIT, bloomShaderPath, null);

        // 空顶点缓冲结构：全屏四边形不使用顶点数据，由 gl_VertexIndex 生成
        var vtxBuffStruct = new EmptyVtxBuffStruct();

        // 主管线：黑洞场景（HDR 输出到 TAA 历史，单附件）
        var buildInfo = new PipelineBuildInfo(new ShaderModule[]{vertModule, fragModule}, vtxBuffStruct.getVi(),
                new int[]{TAA_HISTORY_FORMAT})
                // 声明描述符集布局：set 0 = 天空盒，set 1 = TAA 历史，set 2 = 参数 UBO
                .setDescSetLayouts(new DescSetLayout[]{skyboxDescLayout, taaDescLayout, argsDescLayout});
        pipeline = new Pipeline(vkCtx, buildInfo);

        // Bloom 合成管线：读 HDR 历史 → 交换链（颜色格式必须用交换链真实格式，
        // 管线声明与动态渲染附件不一致属未定义行为，验证层 VUID 06580 会报）
        var bloomBuildInfo = new PipelineBuildInfo(new ShaderModule[]{vertModule, bloomModule}, vtxBuffStruct.getVi(),
                new int[]{vkCtx.getSwapChain().getImageFormat()})
                // 声明描述符集布局：set 0 = 本帧 HDR 历史，set 1 = 参数 UBO（frag 读 iFade）
                .setDescSetLayouts(new DescSetLayout[]{bloomSceneLayout, argsDescLayout});
        bloomPipeline = new Pipeline(vkCtx, bloomBuildInfo);

        vtxBuffStruct.cleanup();
        vertModule.cleanup(vkCtx);
        fragModule.cleanup(vkCtx);
        bloomModule.cleanup(vkCtx);

        // Bloom 线性采样器（圆盘模糊采样用）
        Device device = vkCtx.getDevice();
        try (var stack = MemoryStack.stackPush()) {
            var samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType$Default()
                    .magFilter(VK_FILTER_LINEAR)
                    .minFilter(VK_FILTER_LINEAR)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .maxLod(0.0f);
            long[] lp = new long[1];
            vkCheck(vkCreateSampler(device.getVkDevice(), samplerInfo, null, lp),
                    "Failed to create bloom sampler");
            bloomSampler = lp[0];
        }

        // 分配并更新天空盒描述符集（静态槽位；盒内容可由 GUI 经 DualSkybox 换绑）
        skyboxDescSet = vkCtx.getDescAllocator().addDescSet(vkCtx.getDevice(), "blackhole-skybox", skyboxDescLayout);
        skyboxDescSet.setImage(vkCtx.getDevice(), dualSkybox.current().getSampler(),
                dualSkybox.current().getImageView().getVkImageView(),
                0, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);

        // 根据交换链尺寸创建动态渲染信息
        createRenderInfo(extent.width(), extent.height());
    }

    /**
     * 创建 TAA 历史缓冲：每个帧插槽 2 张 ping-pong 图 + 视图 + 采样器，
     * 并通过一次性命令缓冲把初始布局设为：j=0 → SHADER_READ（读），j=1 → COLOR（写）。
     */
    private void createTaaResources(VkCtx vkCtx, Queue.GraphicsQueue queue, int width, int height) {
        taaHistImages = new Image[VkUtils.MAX_IN_FLIGHT][2];
        taaHistViews = new ImageView[VkUtils.MAX_IN_FLIGHT][2];
        try (var stack = MemoryStack.stackPush()) {
            for (int slot = 0; slot < VkUtils.MAX_IN_FLIGHT; slot++) {
                for (int j = 0; j < 2; j++) {
                    taaHistImages[slot][j] = new Image(vkCtx, new Image.ImageData()
                            .width(width).height(height)
                            .format(TAA_HISTORY_FORMAT)
                            .usage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT));
                    taaHistViews[slot][j] = new ImageView(vkCtx.getDevice(), taaHistImages[slot][j].getVkImage(),
                            new ImageView.ImageViewData()
                                    .format(TAA_HISTORY_FORMAT)
                                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT),
                            false);
                }
            }

            // 初始布局转换（对应 taaReadIdx={0,0}：j=0 作为读取源，j=1 作为写入目标）
            var cmdPool = new CmdPool(vkCtx, queue.getQueueFamilyIndex(), false);
            var cmdBuffer = new CmdBuffer(vkCtx, cmdPool, true, true);
            try {
                cmdBuffer.beginRecording();
                VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();
                for (int slot = 0; slot < VkUtils.MAX_IN_FLIGHT; slot++) {
                    for (int j = 0; j < 2; j++) {
                        boolean asRead = j == 0;
                        int newLayout = asRead ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                                : VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
                        long dstStage = asRead ? VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT
                                : VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT;
                        long dstAccess = asRead ? VK_ACCESS_2_SHADER_READ_BIT
                                : VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT;
                        VkUtils.imageBarrier(stack, cmdHandle, taaHistImages[slot][j].getVkImage(),
                                VK_IMAGE_LAYOUT_UNDEFINED, newLayout,
                                VK_PIPELINE_STAGE_2_TOP_OF_PIPE_BIT, dstStage,
                                VK_ACCESS_2_NONE, dstAccess, VK_IMAGE_ASPECT_COLOR_BIT);
                    }
                }
                cmdBuffer.endRecording();
                cmdBuffer.submitAndWait(vkCtx, queue);
            } finally {
                cmdBuffer.cleanup(vkCtx, cmdPool);
                cmdPool.cleanup(vkCtx);
            }
        }

        // 历史读取采样器：texelFetch 不受滤波影响；重投影采样需要线性过滤
        Device device = vkCtx.getDevice();
        try (var stack = MemoryStack.stackPush()) {
            var samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType$Default()
                    .magFilter(VK_FILTER_LINEAR)
                    .minFilter(VK_FILTER_LINEAR)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .maxLod(0.0f);
            long[] lp = new long[1];
            vkCheck(vkCreateSampler(device.getVkDevice(), samplerInfo, null, lp),
                    "Failed to create TAA sampler");
            taaSampler = lp[0];
        }
    }

    /** 释放 TAA 历史缓冲与采样器（描述符集和布局由调用方管理） */
    private void cleanupTaaResources(VkCtx vkCtx) {
        if (taaHistViews != null) {
            for (int slot = 0; slot < taaHistViews.length; slot++) {
                for (int j = 0; j < 2; j++) {
                    if (taaHistViews[slot][j] != null) {
                        taaHistViews[slot][j].cleanup(vkCtx.getDevice());
                    }
                    if (taaHistImages != null && taaHistImages[slot][j] != null) {
                        taaHistImages[slot][j].cleanup(vkCtx);
                    }
                }
            }
            taaHistViews = null;
            taaHistImages = null;
        }
        if (taaSampler != 0) {
            vkDestroySampler(vkCtx.getDevice().getVkDevice(), taaSampler, null);
            taaSampler = 0;
        }
    }

    /**
     * 创建/重建动态渲染信息（VkRenderingInfo）。
     * 主 pass 写 TAA 历史（HDR），Bloom 合成 pass 写交换链。
     *
     * @param width  渲染宽度
     * @param height 渲染高度
     */
    private void createRenderInfo(int width, int height) {
        if (renderInfo != null) {
            renderInfo.free();
        }
        if (attInfoColor != null) {
            attInfoColor.free();
        }
        if (bloomRenderInfo != null) {
            bloomRenderInfo.free();
        }
        if (attInfoBloom != null) {
            attInfoBloom.free();
        }

        // 主 pass 附件：TAA 历史写入目标（HDR，全屏覆盖写入，具体视图每帧设置）
        attInfoColor = VkRenderingAttachmentInfo.calloc(1);
        // Bloom 合成 pass 附件：交换链（具体视图每帧设置）
        attInfoBloom = VkRenderingAttachmentInfo.calloc(1);

        try (var stack = MemoryStack.stackPush()) {
            VkExtent2D extent = VkExtent2D.calloc(stack).width(width).height(height);
            var renderArea = VkRect2D.calloc(stack).extent(extent);

            renderInfo = VkRenderingInfo.calloc()
                    .sType$Default()
                    .renderArea(renderArea)
                    .layerCount(1);
            bloomRenderInfo = VkRenderingInfo.calloc()
                    .sType$Default()
                    .renderArea(VkRect2D.calloc(stack).extent(VkExtent2D.calloc(stack).width(width).height(height)))
                    .layerCount(1);
        }
    }

    /** 释放所有 GPU 资源 */
    public void cleanup(VkCtx vkCtx) {
        if (skyboxDescSet != null) {
            vkCtx.getDescAllocator().freeDescSet(vkCtx.getDevice(), "blackhole-skybox");
        }
        if (taaDescSets != null) {
            for (int i = 0; i < taaDescSets.length; i++) {
                vkCtx.getDescAllocator().freeDescSet(vkCtx.getDevice(), "blackhole-taa-" + i);
            }
        }
        if (bloomDescSets != null) {
            for (int i = 0; i < bloomDescSets.length; i++) {
                vkCtx.getDescAllocator().freeDescSet(vkCtx.getDevice(), "blackhole-bloom-" + i);
            }
        }
        if (argsDescSets != null) {
            for (int i = 0; i < argsDescSets.length; i++) {
                vkCtx.getDescAllocator().freeDescSet(vkCtx.getDevice(), "blackhole-args-" + i);
            }
        }
        if (skyboxDescLayout != null) {
            skyboxDescLayout.cleanup(vkCtx);
        }
        if (taaDescLayout != null) {
            taaDescLayout.cleanup(vkCtx);
        }
        if (bloomSceneLayout != null) {
            bloomSceneLayout.cleanup(vkCtx);
        }
        if (argsDescLayout != null) {
            argsDescLayout.cleanup(vkCtx);
        }
        if (dualSkybox != null) {
            dualSkybox.cleanup(vkCtx);
        }
        cleanupTaaResources(vkCtx);
        for (int i = 0; i < 2; i++) {
            if (prevCamUbo[i] != null) {
                prevCamUbo[i].cleanup(vkCtx);   // 内部含 unMap
                prevCamUbo[i] = null;
            }
            if (argsUbo[i] != null) {
                argsUbo[i].cleanup(vkCtx);      // 内部含 unMap
                argsUbo[i] = null;
            }
        }
        if (bloomSampler != 0) {
            vkDestroySampler(vkCtx.getDevice().getVkDevice(), bloomSampler, null);
            bloomSampler = 0;
        }
        if (pipeline != null) {
            pipeline.cleanup(vkCtx);
        }
        if (bloomPipeline != null) {
            bloomPipeline.cleanup(vkCtx);
        }
        if (renderInfo != null) {
            renderInfo.free();
        }
        if (attInfoColor != null) {
            attInfoColor.free();
        }
        if (bloomRenderInfo != null) {
            bloomRenderInfo.free();
        }
        if (attInfoBloom != null) {
            attInfoBloom.free();
        }
    }

    /**
     * 执行一帧黑洞渲染。
     * <p>
     * Pass 1（主）：动态渲染写入 TAA 历史缓冲（HDR，预色调映射），
     * 片段着色器读取本插槽上一份历史做时域混合。
     * Pass 2（Bloom 合成）：读取刚写完的 HDR 历史 → 亮部提取 + 圆盘模糊 +
     * 色调映射 + 淡出 → 写入交换链。
     *
     * @param vkCtx           Vulkan 上下文
     * @param cmdBuffer       已 begin 的命令缓冲区
     * @param engCtx          引擎上下文
     * @param currentFrame    当前帧插槽索引（0 或 1）
     * @param imageIndex      交换链图像索引
     */
    public void render(VkCtx vkCtx, CmdBuffer cmdBuffer, EngCtx engCtx, int currentFrame, int imageIndex) {
        try (var stack = MemoryStack.stackPush()) {
            VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();

            SwapChain swapChain = vkCtx.getSwapChain();
            VkExtent2D extent = swapChain.getSwapChainExtent();

            // TAA 插槽状态：读 hist[slot][readIdx]（本插槽上次写入的），写 hist[slot][writeIdx]
            int slot = currentFrame;
            int readIdx = taaReadIdx[slot];
            int writeIdx = 1 - readIdx;

            // GUI 山海盒开关 → set0.b0 换绑（waitIdle 后重绑单份天空盒描述符集）
            boolean wantMountainsSeas = engCtx.scene().isMountainsSeasSkybox();
            if (wantMountainsSeas != dualSkybox.isMountainsSeasBound()) {
                dualSkybox.bind(vkCtx, wantMountainsSeas, box ->
                        skyboxDescSet.setImage(vkCtx.getDevice(), box.getSampler(),
                                box.getImageView().getVkImageView(), 0, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER));
            }

            // 更新本插槽描述符集指向要读的历史图（上次使用在 2 帧前，栅栏已完成，可安全更新）
            taaDescSets[slot].setImage(vkCtx.getDevice(), taaSampler,
                    taaHistViews[slot][readIdx].getVkImageView(), 0, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            // Bloom 合成将采样本帧刚写完的历史图
            bloomDescSets[slot].setImage(vkCtx.getDevice(), bloomSampler,
                    taaHistViews[slot][writeIdx].getVkImageView(), 0, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);

            // ===== Pass 1：黑洞场景 → TAA 历史（HDR，单附件，全屏覆盖写入） =====
            attInfoColor.get(0).sType$Default()
                    .imageView(taaHistViews[slot][writeIdx].getVkImageView())
                    .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE);

            renderInfo.pColorAttachments(attInfoColor);
            renderInfo.renderArea().extent().width(extent.width());
            renderInfo.renderArea().extent().height(extent.height());

            vkCmdBeginRendering(cmdHandle, renderInfo);

            // 绑定图形管线
            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getVkPipeline());

            // 绑定描述符集：set 0 = 天空盒，set 1 = TAA 历史，set 2 = 参数 UBO
            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                    pipeline.getVkPipelineLayout(), 0,
                    stack.longs(skyboxDescSet.getVkDescriptorSet(), taaDescSets[slot].getVkDescriptorSet(),
                            argsDescSets[slot].getVkDescriptorSet()),
                    null);

            int width = extent.width();
            int height = extent.height();
            // 设置视口：Y 轴翻转（Vulkan 坐标系 Y 轴向下）
            var viewport = VkViewport.calloc(1, stack)
                    .x(0)
                    .y(height)       // 从底部开始
                    .height(-height)  // Y 轴向上（翻转）
                    .width(width)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            vkCmdSetViewport(cmdHandle, 0, viewport);

            // 设置裁剪矩形：覆盖整个视口
            var scissor = VkRect2D.calloc(1, stack)
                    .extent(it -> it.width(width).height(height))
                    .offset(it -> it.x(0).y(0));
            vkCmdSetScissor(cmdHandle, 0, scissor);

            // 每帧整块重写本插槽参数 UBO（相机/时间/TAA/黑洞参数，std140 布局与三 shader 一致）
            packParams(engCtx, slot);

            // 绘制全屏四边形（6 个顶点，两个三角形）
            vkCmdDraw(cmdHandle, 6, 1, 0, VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);

            vkCmdEndRendering(cmdHandle);

            // TAA 历史布局翻转：
            // 本次写入的历史 → SHADER_READ（本帧 Bloom 合成采样 + 本插槽下次运行读取）
            VkUtils.imageBarrier(stack, cmdHandle, taaHistImages[slot][writeIdx].getVkImage(),
                    VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                    VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT, VK_ACCESS_2_SHADER_READ_BIT,
                    VK_IMAGE_ASPECT_COLOR_BIT);
            // 本次读取的历史 → COLOR（本插槽下次运行写入）
            VkUtils.imageBarrier(stack, cmdHandle, taaHistImages[slot][readIdx].getVkImage(),
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_SHADER_READ_BIT, VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_ASPECT_COLOR_BIT);

            // ===== Pass 2：Bloom 合成 → 交换链 =====
            var clearValue = VkClearValue.calloc(stack);
            clearValue.color().float32(0, 0.0f);
            clearValue.color().float32(1, 0.0f);
            clearValue.color().float32(2, 0.0f);
            clearValue.color().float32(3, 1.0f);

            attInfoBloom.get(0).sType$Default()
                    .imageView(swapChain.getImageView(imageIndex).getVkImageView())
                    .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .clearValue(clearValue);

            bloomRenderInfo.pColorAttachments(attInfoBloom);
            bloomRenderInfo.renderArea().extent().width(width);
            bloomRenderInfo.renderArea().extent().height(height);

            vkCmdBeginRendering(cmdHandle, bloomRenderInfo);

            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, bloomPipeline.getVkPipeline());

            // 绑定描述符集：set 0 = 本帧 HDR 历史，set 1 = 参数 UBO（frag 消费 iFade）
            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS,
                    bloomPipeline.getVkPipelineLayout(), 0,
                    stack.longs(bloomDescSets[slot].getVkDescriptorSet(), argsDescSets[slot].getVkDescriptorSet()),
                    null);

            vkCmdDraw(cmdHandle, 6, 1, 0, VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);

            vkCmdEndRendering(cmdHandle);

            // 翻转本插槽读写下标（下次运行生效）
            taaReadIdx[slot] = writeIdx;
            taaFrameCount++;
        }
    }

    /**
     * 打包参数 UBO 数据（BlackHoleArgs，std140）。
     * <p>
     * 将相机逆矩阵、投影逆矩阵、相机位置、时间、黑洞参数、TAA 参数逐字段写入
     * 本插槽持久映射的 UBO 内存（字节偏移与类注释布局表一致），着色器经
     * 描述符集读取（主管线 set 2 / bloom 管线 set 1）。
     *
     * @param engCtx 引擎上下文
     * @param slot   当前帧插槽索引（0/1，用于按插槽跟踪历史可信度）
     */
    private void packParams(EngCtx engCtx, int slot) {
        ByteBuffer args = MemoryUtil.memByteBuffer(argsMapped[slot], BH_ARGS_SIZE);
        Camera camera = engCtx.scene().getCamera();
        var projection = engCtx.scene().getProjection();
        // 用户可调量（GUI 滑条/快捷键写, eng.properties 初始化）统一取自参数包
        var params = engCtx.scene().getSchwarzschildParams();

        // 墙钟时间（秒，自启动起）：驱动 TAA 抖动种子（iRenderTime），暂停时仍流动
        float wallTime = (float) ((System.nanoTime() - startTime) / 1_000_000_000.0);
        // 模拟时间累计器：驱动盘动画；时间暂停（P 键）时冻结
        if (prevWallTime == 0.0f) {
            prevWallTime = wallTime;
        }
        float dtWall = wallTime - prevWallTime;
        prevWallTime = wallTime;
        if (!engCtx.scene().isTimePaused()) {
            simTime += dtWall;
        }
        float time = simTime;

        // 计算视图矩阵的逆矩阵（用于将屏幕空间射线反投影到世界空间）
        Matrix4f invView = new Matrix4f();
        camera.getViewMatrix().invert(invView);

        // 计算投影矩阵的逆矩阵（用于将裁剪空间坐标转换到视图空间）
        Matrix4f invProj = new Matrix4f();
        projection.getProjectionMatrix().invert(invProj);

        int offset = 0;

        // inverseView 矩阵（64 字节 = 4×4 float）
        invView.get(offset, args);
        offset += 64;

        // inverseProj 矩阵（64 字节）
        invProj.get(offset, args);
        offset += 64;

        // 相机世界坐标位置（12 字节 = vec3）
        camera.getPosition().get(offset, args);
        offset += 12;

        // 动画时间（4 字节 = float）
        args.putFloat(offset, time);
        offset += 4;

        // 黑洞世界坐标位置（12 字节 = vec3），固定在原点 (0,0,0)
        args.putFloat(offset, 0.0f);
        args.putFloat(offset + 4, 0.0f);
        args.putFloat(offset + 8, 0.0f);
        offset += 12;

        // 史瓦西半径（4 字节 = float），世界坐标长度单位（eng.properties: blackhole.schwarzschildRadius）；
        // frag 中所有公式显式使用该值，盘半径以 Rs 倍数传入并在此约定下换算
        args.putFloat(offset, EngCfg.getInstance().getSchwarzschildRadius());
        offset += 4;

        // 吸积盘内半径（4 字节 = float），Rs 的倍数（3.0 = 3·Rs ≈ ISCO），frag 内乘 Rs 换算
        args.putFloat(offset, params.diskInnerRadiusRs);
        offset += 4;

        // 吸积盘外半径（4 字节 = float），Rs 的倍数，frag 内乘 Rs 换算
        // （18：小盘流畅；调大会显著增加 raymarch 步数与 TAA 拖影面积）
        args.putFloat(offset, params.diskOuterRadiusRs);
        offset += 4;

        // iExposure：曝光增益（4 字节 = float；原 rotationSpeed 死字段槽位复用，Phase 3）
        args.putFloat(offset, params.exposure);
        offset += 4;

        // 基础温度 (K)，默认 15000K
        args.putFloat(offset, params.baseTemperature);
        offset += 4;

        // if_dopplerI：多普勒亮度 A/B 开关 (int)，1=开启（原行为）
        args.putInt(offset, params.dopplerIntensityEnabled ? 1 : 0);
        offset += 4;

        // if_dopplerT：多普勒温度 A/B 开关 (int)，1=开启（原行为）
        args.putInt(offset, params.dopplerTemperatureEnabled ? 1 : 0);
        offset += 4;

        // timeRate：时间速率（用于动画速度）
        args.putFloat(offset, EngCfg.getInstance().getTimeRate());
        offset += 4;

        // iTimeDelta：帧间隔（秒），TAA blendWeight 用
        long now = System.nanoTime();
        float timeDelta = lastFrameNanos == 0 ? 0.0f : (float) ((now - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = now;
        timeDelta = Math.max(0.0f, Math.min(timeDelta, 0.1f));
        args.putFloat(offset, timeDelta);
        offset += 4;

        // iFrame：全局帧计数（shader 中前 2 帧强制重置）
        args.putInt(offset, taaFrameCount);
        offset += 4;

        // iCameraMoved 三态：
        //  1 = 硬重置：init/resize 后插槽首跑（历史是垃圾）；帧间平移 >0.15Rs（瞬移，视差不可重投影）
        //  2 = 世界坐标全重投影 + τ 全量混合：测地模式的连续运动（平移视差与旋转一并补偿）
        //  0 = 静止：同位全量时域累积
        boolean changed = !prevViewPerSlot[slot].equals(camera.getViewMatrix())
                || !prevProjPerSlot[slot].equals(projection.getProjectionMatrix());
        boolean hardMoved = taaForceReset[slot]
                || camera.getViewMatrix().getTranslation(new Vector3f())
                        .distance(prevViewPerSlot[slot].getTranslation(new Vector3f())) > EngCfg.getInstance().getTaaMotionResetThreshold();
        // 重投影矩阵：上一帧（与该插槽历史帧配对）的 proj·view，写入持久映射的 UBO 供着色器使用
        prevViewProj.set(prevProjPerSlot[slot]).mul(prevViewPerSlot[slot]);
        prevViewProj.getToAddress(prevCamMapped[slot]);
        int cameraMoved;
        if (hardMoved) {
            cameraMoved = 1;
        } else if (!changed) {
            cameraMoved = 0;
        } else {
            cameraMoved = camera.getMode() == Camera.CameraMode.GEODESIC ? 2 : 1;
        }
        taaForceReset[slot] = false;
        prevViewPerSlot[slot].set(camera.getViewMatrix());
        prevProjPerSlot[slot].set(projection.getProjectionMatrix());
        args.putInt(offset, cameraMoved);
        offset += 4;

        // iRenderTime：渲染时间（墙钟，时间暂停时仍流动）——TAA 抖动种子用，与模拟时间 time 解耦
        args.putFloat(offset, wallTime);
        offset += 4;

        // iFade：视界坠落淡出系数（非演出时为 0，画面正常）
        args.putFloat(offset, engCtx.scene().getGeodesic().getHorizonFade());
        offset += 4;

        // iCameraVel：相机速度 β 向量（单位 c，静态观者系；非测地模式为 0 → 多普勒因子恒 1）
        Vector3f cameraBeta = engCtx.scene().getGeodesic().getBeta();
        args.putFloat(offset, cameraBeta.x);
        offset += 4;
        args.putFloat(offset, cameraBeta.y);
        offset += 4;
        args.putFloat(offset, cameraBeta.z);
        offset += 4;

        // iCameraGamma：相机洛伦兹因子 γ
        args.putFloat(offset, engCtx.scene().getGeodesic().getGamma());
        offset += 4;

        // iDiskScatter：盘前向散射强度（背光项，被盘消光的背景光散射回视线的比例，0=关）
        args.putFloat(offset, params.diskScatter);
        offset += 4;

        // iDiskAmbient：盘环境光强度（弥散项，全天空辐照×盘密度并入发射，0=关）
        args.putFloat(offset, params.diskAmbient);
        offset += 4;

        // iShiftMax：盘频移钳制上限（原 shader 硬编码 2.5）
        args.putFloat(offset, params.shiftMax);
        offset += 4;

        // iTaaTau：TAA 静止累积 τ 基准秒（原 shader 硬编码 0.3）
        args.putFloat(offset, params.taaTau);
        offset += 4;

        // iBloomThreshold / iBloomMix / iBloomMax：Bloom 三参数（原 bloomComposite 硬编码）
        args.putFloat(offset, params.bloomThreshold);
        offset += 4;
        args.putFloat(offset, params.bloomMix);
        offset += 4;
        args.putFloat(offset, params.bloomMax);
        offset += 4;

        // iBackgroundBright：背景亮度倍率（原硬编码 0.7,散射项随 Bg 同步缩放）
        args.putFloat(offset, params.backgroundBright);
        offset += 4;

        // iToneMapStrength：色调映射强度（1=全 ACES 原行为,0=线性直出）
        args.putFloat(offset, params.toneMapStrength);
        offset += 4;

        // iDiskHalfThickness：盘半厚基准（Rs 倍数,原硬编码 0.5·Rs;垂直密度/厚度/尘埃层随动）
        args.putFloat(offset, params.diskHalfThicknessRs);
        offset += 4;

        // （参数经描述符集读取,无需命令提交;主机写入即时可见,VMA 分配为 HOST_COHERENT）
    }

    /**
     * 处理窗口大小变化：重建动态渲染信息和 TAA 历史缓冲（重置累积）。
     *
     * @param vkCtx  Vulkan 上下文
     * @param width  新宽度
     * @param height 新高度
     */
    public void resize(VkCtx vkCtx, int width, int height) {
        createRenderInfo(width, height);
        cleanupTaaResources(vkCtx);
        var graphQueue = new Queue.GraphicsQueue(vkCtx, 0);
        createTaaResources(vkCtx, graphQueue, width, height);
        taaReadIdx = new int[]{0, 0};
        Arrays.fill(taaForceReset, true);
    }
}
