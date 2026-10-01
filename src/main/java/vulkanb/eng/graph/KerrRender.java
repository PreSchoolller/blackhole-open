package vulkanb.eng.graph;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import vulkanb.eng.EngCfg;
import vulkanb.eng.EngCtx;
import vulkanb.eng.graph.vk.*;
import vulkanb.eng.scene.Camera;
import vulkanb.eng.scene.KerrParams;
import vulkanb.eng.scene.Scene;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;
import static vulkanb.eng.graph.vk.VkUtils.vkCheck;
import static vulkanb.utils.Constants.SHADERS_DIR;

/**
 * 克尔黑洞渲染器 —— NPGS BlackHole 管线的移植（Kerr–Schild 度规，自旋 a 可调）。
 * <p>
 * 与 {@link BlackHoleRender}（史瓦西）完全平行、互不依赖：
 * <ul>
 *   <li>参数面走 UBO（NPGS 的 GameArgs + BlackHoleArgs 两个 uniform block，
 *       std140 顺序打包，字段顺序与 kerr.frag 声明严格一致），不用 push constants</li>
 *   <li>射线在 frag 内经 gl_FragCoord + iInverseCamRot/iFovRadians 生成（NPGS 原生方式），
 *       顶点为无数据全屏四边形</li>
 *   <li>Pass A（kerr 管线）：tonemap + TAA 混合在 frag 内完成，写 R16G16B16A16 历史
 *       （按帧插槽 ping-pong，iHistoryTex 供下一帧混合；含完整 mip 链）</li>
 *   <li>Pass B（合成管线）：本帧历史 blit 逐级生成 mip 链后，采样 8 octave 辉光
 *       （NPGS 权重）+ ColorBlend 调色链（逐行照搬 NPGS ColorBlend.frag.glsl），写交换链
 *       （kerr_composite.frag；强度 0 时输出 = 纯调色链）</li>
 *   <li>星空：单层 CubeTexture 绑定到 iBackground0(b1)（NPGS 六套盒 = Universe0/1/2 宇宙
 *       变体星空 1024² + Antiverse0/1/2 反宇宙 2048²，其 %3 选层依赖 iInWhichUniverse/白洞
 *       模式，本项目未接线故恒选 0 号层；本项目将 Universe0 用作主星空盒、Antiverse0 纹理
 *       用作山海盒，GUI 在同一 b1 槽位换绑切换，着色器零改动）；
 *       iImageTexture(b9) 绑定历史视图占位
 *       （iUseImageDisk=0 恒不采样）</li>
 * </ul>
 * 相机动力学：静态观者（iObserverMode=0）；测地模式（G 键）激活时切换为观者模式 -1 ——
 * 外传 GeodesicIntegrator 平行输运的四维标架（iU_up + 折叠鼠标头转的 ie1/2/3_up），
 * 着色器以 P = U − Σ v·E 直接构造光子四动量（负号=光子朝向观者飞来，与静态观者分支同式），
 * 相机的多普勒/光行差/参考系拖拽全部由度规处理。
 * Phase 2：吸积盘（DiskColor）+ 多普勒/引力红移已接线；盘内缘按 ISCO(a*) 动态计算，
 * 盘时间走 NPGS 的 c·s/Rs 单位换算。
 */
public class KerrRender {

    private static final String VERTEX_SHADER = SHADERS_DIR + "kerr.vert";
    private static final String FRAGMENT_SHADER = SHADERS_DIR + "kerr.frag";
    private static final String COMPOSITE_SHADER = SHADERS_DIR + "kerr_composite.frag";
    /** TAA 历史格式：NPGS 在 frag 内 tonemap，输出已是 [0,1]，半精度浮点足够 */
    private static final int HISTORY_FORMAT = VK_FORMAT_R16G16B16A16_SFLOAT;
    /** prepass 双附件格式：均为 32F——扭曲场按 NPGS 用 32F 保向量/频移精度；
     *  体积色 NPGS 原版 16F，这里取 32F（HDR 线性域亮度不受半精度上限钳制，半分辨率下代价可忽略） */
    private static final int PREPASS_DISTORTION_FORMAT = VK_FORMAT_R32G32B32A32_SFLOAT;
    private static final int PREPASS_VOLUMETRIC_FORMAT = VK_FORMAT_R32G32B32A32_SFLOAT;
    /** iPrepass 在 BlackHoleArgs UBO 内的字节偏移：mat4(64) + 8×vec4(128) + 前两个 int(8) */
    private static final int BH_ARGS_IPREPASS_OFFSET = 200;
    /** GameArgs UBO 字节数（std140：vec2 + 4×float，struct 尺寸对齐到 16 → 32） */
    private static final int GAME_ARGS_SIZE = 32;
    /** BlackHoleArgs UBO 字节数（mat4 + 8×vec4 + 11×int + 37×float = 384，末尾追加 iNoiseLut
     *  / iDiskScatter / iDiskAmbient 3 float = 387，std140 结构尺寸对齐 16 → 400；既有字段偏移不受追加影响） */
    private static final int BH_ARGS_SIZE = 400;
    /** 黑洞质量（太阳质量倍数，人马 A*；GUI 面板与 UBO 打包共用 KerrParams.BH_MASS_SOL） */
    private static final float BH_MASS_SOL = KerrParams.BH_MASS_SOL;
    /** 史瓦西半径（米）= 2GM/c²，用于把动画秒换算成 NPGS 盘时间的 c·s/Rs 单位 */
    private static final float BH_RS_METERS =
            2.0f * 6.67430e-11f * 1.98892e30f * BH_MASS_SOL / (2.99792458e8f * 2.99792458e8f);
    /** 盘时间换算系数：1 动画秒对应的 c·s/Rs 值（Sgr A* ≈ 6.82e-3） */
    private static final float BH_TIME_SCALE = 2.99792458e8f / BH_RS_METERS;

    /** 克尔主管线 */
    private Pipeline pipeline;
    /** prepass 管线（半分辨率双颜色附件：扭曲场+体积色；与主管线共用 kerr.frag module；懒建见 {@link #ensurePrepassPipeline}） */
    private Pipeline prepassPipeline;
    /** 合成管线（本帧历史 mip 链 → 8-octave 辉光 + ColorBlend 调色 → 交换链；强度 0 = 纯调色链） */
    private Pipeline compositePipeline;
    /** prepass 管线懒建所需的 spv 路径（init 时存下；GUI 首次开启时现建管线用） */
    private String vertSpvPath, fragSpvPath;
    /** set 0：GameArgs(b0) + BlackHoleArgs(b1) */
    private DescSetLayout argsLayout;
    /** set 1：历史(b0) + 天空盒×6(b1..b6) + prepass 双纹理(b7..b8) + 贴图占位(b9) */
    private DescSetLayout texLayout;
    /** 合成管线 set 0：本帧历史 mip 链(b0) */
    private DescSetLayout bloomTexLayout;
    /** 每帧插槽的 set0（双 UBO 绑定一次，内容每帧重写） */
    private DescSet[] argsDescSets;
    /** prepass pass 的 set0（GameArgsPrepass 分辨率减半 + BlackHoleArgsPrepass 仅 iPrepass=1） */
    private DescSet[] prepassArgsDescSets;
    /** 每帧插槽的 set1（b0 历史视图每帧更新） */
    private DescSet[] texDescSets;
    /** 每帧插槽的合成 set0（指向本帧刚写完的历史 mip 链） */
    private DescSet[] bloomDescSets;
    /** GameArgs / BlackHoleArgs 持久映射缓冲（每插槽一份） */
    private final VkBuffer[] gameArgsUbo = new VkBuffer[VkUtils.MAX_IN_FLIGHT];
    private final VkBuffer[] bhArgsUbo = new VkBuffer[VkUtils.MAX_IN_FLIGHT];
    private final long[] gameMapped = new long[VkUtils.MAX_IN_FLIGHT];
    private final long[] bhMapped = new long[VkUtils.MAX_IN_FLIGHT];
    /** prepass 专用 UBO（每插槽一份；BlackHoleArgs 用字节复制改写 iPrepass，保证与主拷贝一致） */
    private final VkBuffer[] gameArgsPrepassUbo = new VkBuffer[VkUtils.MAX_IN_FLIGHT];
    private final VkBuffer[] bhArgsPrepassUbo = new VkBuffer[VkUtils.MAX_IN_FLIGHT];
    private final long[] gamePreMapped = new long[VkUtils.MAX_IN_FLIGHT];
    private final long[] bhPreMapped = new long[VkUtils.MAX_IN_FLIGHT];
    /** prepass 半分辨率双附件 [帧插槽]（写读均在本插槽帧内完成，复用安全性同历史） */
    private Image[] prepassDistortionImages;
    private ImageView[] prepassDistortionViews;
    private Image[] prepassVolumetricImages;
    private ImageView[] prepassVolumetricViews;
    /** prepass 点采样器（texelFetch 不滤波，NEAREST 保证任意格式合法，NPGS 亦要求 Nearest） */
    private long prepassSampler;
    /** 双星空盒（主星空 + 山海；GUI 在同一 set1.b1 槽位换绑切换，着色器零改动） */
    private DualSkybox dualSkybox;
    /** 噪声哈希 LUT（64³ R8 3D；PerlinNoise 查表路径的哈希表，iNoiseLut 运行时 A/B 开关） */
    private NoiseHashLut noiseHashLut;
    /** TAA 历史 [帧插槽][ping-pong]（含完整 mip 链，供合成 pass 取辉光 octave） */
    private Image[][] histImages;
    private ImageView[][] histViews;
    /** 历史 mip 级数（floor(log2(max(w,h)))+1；合成 pass 采样 lod 1..8） */
    private int histMipLevels;
    /** 历史读取采样器（Bloom pass 双线性；kerr frag 用 texelFetch 不受滤波影响） */
    private long histSampler;
    /** 每插槽历史读写下标（读上次写入的） */
    private int[] histReadIdx = {0, 0};
    /** 每插槽已渲染帧数：前 2 帧强制 iBlendWeight=1（历史初值未定义可能为 NaN，
     *  混合权重 <1 会让 NaN 永久自我延续 → 永远黑屏） */
    private final int[] histFrameCount = {0, 0};
    /** 主 pass 动态渲染信息（写历史） */
    private VkRenderingInfo renderInfo;
    private VkRenderingAttachmentInfo.Buffer attInfoHist;
    /** prepass pass 动态渲染信息（写半分辨率双附件；同 renderArea 教训——堆分配） */
    private VkRenderingInfo prepassRenderInfo;
    private VkRenderingAttachmentInfo.Buffer attInfoPrepass;
    private VkRect2D prepassRenderArea;
    private VkExtent2D prepassRenderExtent;
    /** 拷贝 pass 动态渲染信息（写交换链，view 每帧设置） */
    private VkRenderingInfo bloomRenderInfo;
    private VkRenderingAttachmentInfo.Buffer attInfoBloom;
    /** Bloom push constants（8B 直接内存：强度 + 阈值，frag 阶段） */
    private ByteBuffer pushConstBuff;
    /** renderArea/extent 必须与 renderInfo 同生命周期堆分配：
     *  若借道 MemoryStack，方法返回即弹栈，VkRenderingInfo 里的指针悬垂，
     *  renderArea 内容随后续栈复用变垃圾 → scissor 裁光 → pass 静默画不出任何像素 */
    private VkRect2D renderArea, bloomRenderArea;
    private VkExtent2D renderExtent, bloomRenderExtent;
    /** 启动时间（动画时间基准） */
    private long startTime;
    /** 上次 UBO 打包时刻（盘时间 dt 的基准；init 时与 startTime 同步） */
    private long lastUboNanos;
    /** 每插槽上一帧视图矩阵（TAA 混合权重启发式：相机动了就加重当前帧） */
    private final Matrix4f[] prevViewPerSlot = {new Matrix4f(), new Matrix4f()};
    /** 实际历史图像尺寸（与 createHistoryResources 传入的 width/height 一致） */
    private int histWidth, histHeight;
    /** 临时矩阵/向量（单线程渲染循环内复用，避免分配） */
    private final Matrix4f rotView = new Matrix4f();
    private final Matrix4f camToWorldRot = new Matrix4f();
    private final Vector3f tmpVec = new Vector3f();
    private final Vector3f tmpDir = new Vector3f();
    /** Phase 5:测地观者四维标架打包暂存（U + 折叠头转的 e1/e2/e3） */
    private final double[][] foldedTetrad = new double[3][4];
    private final double[] fourVel = new double[4];

    /** 构造轻量（对象创建），重资源在 {@link #init} 中分配 */
    public KerrRender() {
        startTime = System.nanoTime();
    }

    /**
     * 初始化：编译着色器、上传星空、创建历史缓冲/UBO/描述符/管线。
     *
     * @param vkCtx  Vulkan 上下文
     * @param engCtx 引擎上下文（相机/场景参数）
     */
    public void init(VkCtx vkCtx, EngCtx engCtx) {
        startTime = System.nanoTime();
        lastUboNanos = startTime;
        String vertSpv = ShaderCompiler.compileShaderIfChanged(VERTEX_SHADER, VK_SHADER_STAGE_VERTEX_BIT);
        String fragSpv = ShaderCompiler.compileShaderIfChanged(FRAGMENT_SHADER, VK_SHADER_STAGE_FRAGMENT_BIT);
        String compositeSpv = ShaderCompiler.compileShaderIfChanged(COMPOSITE_SHADER, VK_SHADER_STAGE_FRAGMENT_BIT);
        var graphQueue = new Queue.GraphicsQueue(vkCtx, 0);

        // 星空立方体贴图（一次性命令上传；双盒助手含山海盒惰性加载）
        dualSkybox = new DualSkybox(vkCtx, graphQueue);
        // 噪声哈希 LUT（64³ R8，256KB；PerlinNoise 查表路径的哈希表，iNoiseLut 运行时开关）
        noiseHashLut = new NoiseHashLut(vkCtx, graphQueue);

        // 描述符布局：set0 双 UBO；set1 历史(b0)+单层天空盒(b1)+prepass 双纹理(b7/b8)+贴图盘占位(b9)；
        // Bloom set0 单采样器
        // （NPGS 的三套变体星空+反宇宙盒裁为一套资源——宇宙变体按 iInWhichUniverse%3 选层，
        //  本项目配置恒选 0 号层；多天空盒恢复的前置依赖见 kerr_port_plan.md Phase 4 更正）
        argsLayout = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo[]{
                new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 0, 1, VK_SHADER_STAGE_FRAGMENT_BIT),
                new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1, 1, VK_SHADER_STAGE_FRAGMENT_BIT)});
        texLayout = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo[]{
                new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 0, 1, VK_SHADER_STAGE_FRAGMENT_BIT),
                new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1, 1, VK_SHADER_STAGE_FRAGMENT_BIT),
                new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 7, 1, VK_SHADER_STAGE_FRAGMENT_BIT),
                new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 8, 1, VK_SHADER_STAGE_FRAGMENT_BIT),
                new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 9, 1, VK_SHADER_STAGE_FRAGMENT_BIT),
                new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 10, 1, VK_SHADER_STAGE_FRAGMENT_BIT)});
        bloomTexLayout = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(
                VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 0, 1, VK_SHADER_STAGE_FRAGMENT_BIT));

        // TAA 历史（ping-pong，初始布局 j=0 SHADER_READ / j=1 COLOR）
        SwapChain swapChain = vkCtx.getSwapChain();
        VkExtent2D extent = swapChain.getSwapChainExtent();
        createHistoryResources(vkCtx, graphQueue, extent.width(), extent.height());
        // prepass 半分辨率双附件（始终创建：GUI 开关只控制是否执行该 pass，避免运行时重建资源）
        createPrepassResources(vkCtx, graphQueue, extent.width(), extent.height());

        // UBO（持久映射，内容每帧重写）
        Device device = vkCtx.getDevice();
        for (int i = 0; i < VkUtils.MAX_IN_FLIGHT; i++) {
            gameArgsUbo[i] = new VkBuffer(vkCtx, GAME_ARGS_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, 0);
            gameMapped[i] = gameArgsUbo[i].map(vkCtx);
            bhArgsUbo[i] = new VkBuffer(vkCtx, BH_ARGS_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, 0);
            bhMapped[i] = bhArgsUbo[i].map(vkCtx);
            gameArgsPrepassUbo[i] = new VkBuffer(vkCtx, GAME_ARGS_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, 0);
            gamePreMapped[i] = gameArgsPrepassUbo[i].map(vkCtx);
            bhArgsPrepassUbo[i] = new VkBuffer(vkCtx, BH_ARGS_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                    VMA_MEMORY_USAGE_AUTO, VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT, 0);
            bhPreMapped[i] = bhArgsPrepassUbo[i].map(vkCtx);
        }

        // 描述符集分配与静态绑定
        argsDescSets = new DescSet[VkUtils.MAX_IN_FLIGHT];
        prepassArgsDescSets = new DescSet[VkUtils.MAX_IN_FLIGHT];
        texDescSets = new DescSet[VkUtils.MAX_IN_FLIGHT];
        bloomDescSets = new DescSet[VkUtils.MAX_IN_FLIGHT];
        for (int i = 0; i < VkUtils.MAX_IN_FLIGHT; i++) {
            argsDescSets[i] = vkCtx.getDescAllocator().addDescSet(device, "kerr-args-" + i, argsLayout);
            argsDescSets[i].setBuffer(device, gameArgsUbo[i], GAME_ARGS_SIZE, 0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
            argsDescSets[i].setBuffer(device, bhArgsUbo[i], BH_ARGS_SIZE, 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);

            prepassArgsDescSets[i] = vkCtx.getDescAllocator().addDescSet(device, "kerr-args-prepass-" + i, argsLayout);
            prepassArgsDescSets[i].setBuffer(device, gameArgsPrepassUbo[i], GAME_ARGS_SIZE, 0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
            prepassArgsDescSets[i].setBuffer(device, bhArgsPrepassUbo[i], BH_ARGS_SIZE, 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);

            texDescSets[i] = vkCtx.getDescAllocator().addDescSet(device, "kerr-tex-" + i, texLayout);
            texDescSets[i].setImage(device, dualSkybox.current().getSampler(),
                    dualSkybox.current().getImageView().getVkImageView(),
                    1, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            texDescSets[i].setImage(device, noiseHashLut.getSampler(),
                    noiseHashLut.getImageView().getVkImageView(),
                    10, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            // b9 贴图盘占位：绑历史视图（iUseImageDisk=0 恒不采样，仅需合法句柄防 use-after-free）
            texDescSets[i].setImage(device, histSampler, histViews[i][0].getVkImageView(), 9,
                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);

            bloomDescSets[i] = vkCtx.getDescAllocator().addDescSet(device, "kerr-bloom-" + i, bloomTexLayout);
        }

        // 管线：主（写历史）+ prepass（半分辨率双附件,懒建）+ 合成（历史 mip 链→辉光+调色→交换链，
        //  格式取交换链实际格式）
        vertSpvPath = vertSpv;
        fragSpvPath = fragSpv;
        var vertModule = new ShaderModule(vkCtx, VK_SHADER_STAGE_VERTEX_BIT, vertSpv, null);
        var fragModule = new ShaderModule(vkCtx, VK_SHADER_STAGE_FRAGMENT_BIT, fragSpv, null);
        var compositeModule = new ShaderModule(vkCtx, VK_SHADER_STAGE_FRAGMENT_BIT, compositeSpv, null);
        var vtxBuffStruct = new EmptyVtxBuffStruct();
        pipeline = new Pipeline(vkCtx, new PipelineBuildInfo(new ShaderModule[]{vertModule, fragModule},
                vtxBuffStruct.getVi(), new int[]{HISTORY_FORMAT})
                .setDescSetLayouts(new DescSetLayout[]{argsLayout, texLayout}));
        // prepass 管线与主管线同吃 kerr.frag,驱动编译成本同量级(实测 ~28s,见
        // shader_compile_opt_plan.md)——默认关时不建,把"改着色器后首启"的等待砍半;
        // 配置直开或 GUI 首次开启时经 ensurePrepassPipeline 现建
        if (engCtx.scene().getKerrParams().prepassEnabled) {
            ensurePrepassPipeline(vkCtx);
        }
        compositePipeline = new Pipeline(vkCtx, new PipelineBuildInfo(new ShaderModule[]{vertModule, compositeModule},
                vtxBuffStruct.getVi(), new int[]{swapChain.getImageFormat()})
                .setDescSetLayouts(new DescSetLayout[]{bloomTexLayout})
                .setPushConstRanges(new PushConstRange[]{
                        new PushConstRange(VK_SHADER_STAGE_FRAGMENT_BIT, 0, 4)}));
        pushConstBuff = MemoryUtil.memAlloc(4);
        vtxBuffStruct.cleanup();
        vertModule.cleanup(vkCtx);
        fragModule.cleanup(vkCtx);
        compositeModule.cleanup(vkCtx);

        createRenderInfos(extent.width(), extent.height());
    }

    /**
     * prepass 管线懒建：默认关时跳过启动期驱动编译；GUI 首次开启时现建（此后复用，
     * 不随开关反复销毁重建）。spv 路径取 init 存下的 {@code vertSpvPath/fragSpvPath}。
     */
    private void ensurePrepassPipeline(VkCtx vkCtx) {
        if (prepassPipeline != null) {
            return;
        }
        var vertModule = new ShaderModule(vkCtx, VK_SHADER_STAGE_VERTEX_BIT, vertSpvPath, null);
        var fragModule = new ShaderModule(vkCtx, VK_SHADER_STAGE_FRAGMENT_BIT, fragSpvPath, null);
        var vtxBuffStruct = new EmptyVtxBuffStruct();
        prepassPipeline = new Pipeline(vkCtx, new PipelineBuildInfo(new ShaderModule[]{vertModule, fragModule},
                vtxBuffStruct.getVi(), new int[]{PREPASS_DISTORTION_FORMAT, PREPASS_VOLUMETRIC_FORMAT})
                .setDescSetLayouts(new DescSetLayout[]{argsLayout, texLayout}));
        vtxBuffStruct.cleanup();
        vertModule.cleanup(vkCtx);
        fragModule.cleanup(vkCtx);
    }

    /** 创建 TAA 历史缓冲 + 线性采样器，初始布局按 histReadIdx={0,0}（j=0 读 / j=1 写）。
     *  历史图含完整 mip 链（合成 pass 按 lod 1..8 取辉光 octave），usage 含 TRANSFER_SRC/DST。 */
    private void createHistoryResources(VkCtx vkCtx, Queue graphQueue, int width, int height) {
        histWidth = width;
        histHeight = height;
        histMipLevels = Integer.numberOfTrailingZeros(Integer.highestOneBit(Math.max(width, height))) + 1;
        histImages = new Image[VkUtils.MAX_IN_FLIGHT][2];
        histViews = new ImageView[VkUtils.MAX_IN_FLIGHT][2];
        try (var stack = MemoryStack.stackPush()) {
            for (int slot = 0; slot < VkUtils.MAX_IN_FLIGHT; slot++) {
                for (int j = 0; j < 2; j++) {
                    histImages[slot][j] = new Image(vkCtx, new Image.ImageData()
                            .width(width).height(height)
                            .format(HISTORY_FORMAT)
                            .usage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT
                                    | VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT)
                            .mipLevels(histMipLevels));
                    histViews[slot][j] = new ImageView(vkCtx.getDevice(), histImages[slot][j].getVkImage(),
                            new ImageView.ImageViewData()
                                    .format(HISTORY_FORMAT)
                                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                    .mipLevels(histMipLevels),
                            false);
                }
            }
            var cmdPool = new CmdPool(vkCtx, graphQueue.getQueueFamilyIndex(), false);
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
                        VkUtils.imageBarrier(stack, cmdHandle, histImages[slot][j].getVkImage(),
                                VK_IMAGE_LAYOUT_UNDEFINED, newLayout,
                                VK_PIPELINE_STAGE_2_TOP_OF_PIPE_BIT, dstStage,
                                VK_ACCESS_2_NONE, dstAccess, VK_IMAGE_ASPECT_COLOR_BIT);
                    }
                }
                cmdBuffer.endRecording();
                cmdBuffer.submitAndWait(vkCtx, graphQueue);
            } finally {
                cmdBuffer.cleanup(vkCtx, cmdPool);
                cmdPool.cleanup(vkCtx);
            }
        }

        Device device = vkCtx.getDevice();
        try (var stack = MemoryStack.stackPush()) {
            var samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType$Default()
                    .magFilter(VK_FILTER_LINEAR)
                    .minFilter(VK_FILTER_LINEAR)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .maxLod(VK_LOD_CLAMP_NONE);
            long[] lp = new long[1];
            vkCheck(vkCreateSampler(device.getVkDevice(), samplerInfo, null, lp),
                    "Failed to create Kerr history sampler");
            histSampler = lp[0];
        }
    }

    /**
     * 创建/重建 prepass 半分辨率双附件（扭曲场 R32G32B32A32 + 体积色 R32G32B32A32）。
     * 始终创建（GUI 开关只控制是否执行该 pass，避免运行时重建资源）。
     */
    private void createPrepassResources(VkCtx vkCtx, Queue graphQueue, int width, int height) {
        prepassDistortionImages = new Image[VkUtils.MAX_IN_FLIGHT];
        prepassDistortionViews = new ImageView[VkUtils.MAX_IN_FLIGHT];
        prepassVolumetricImages = new Image[VkUtils.MAX_IN_FLIGHT];
        prepassVolumetricViews = new ImageView[VkUtils.MAX_IN_FLIGHT];
        int halfW = Math.max(1, width / 2);
        int halfH = Math.max(1, height / 2);
        for (int slot = 0; slot < VkUtils.MAX_IN_FLIGHT; slot++) {
            prepassDistortionImages[slot] = new Image(vkCtx, new Image.ImageData()
                    .width(halfW).height(halfH)
                    .format(PREPASS_DISTORTION_FORMAT)
                    .usage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT));
            prepassDistortionViews[slot] = new ImageView(vkCtx.getDevice(), prepassDistortionImages[slot].getVkImage(),
                    new ImageView.ImageViewData()
                            .format(PREPASS_DISTORTION_FORMAT)
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT),
                    false);
            prepassVolumetricImages[slot] = new Image(vkCtx, new Image.ImageData()
                    .width(halfW).height(halfH)
                    .format(PREPASS_VOLUMETRIC_FORMAT)
                    .usage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_SAMPLED_BIT));
            prepassVolumetricViews[slot] = new ImageView(vkCtx.getDevice(), prepassVolumetricImages[slot].getVkImage(),
                    new ImageView.ImageViewData()
                            .format(PREPASS_VOLUMETRIC_FORMAT)
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT),
                    false);
        }
        // 初始布局 COLOR（首个用途是 prepass 写入；每帧 prepass 前另有 UNDEFINED→COLOR barrier，内容不需保留）
        try (var stack = MemoryStack.stackPush()) {
            var cmdPool = new CmdPool(vkCtx, graphQueue.getQueueFamilyIndex(), false);
            var cmdBuffer = new CmdBuffer(vkCtx, cmdPool, true, true);
            try {
                cmdBuffer.beginRecording();
                VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();
                for (int slot = 0; slot < VkUtils.MAX_IN_FLIGHT; slot++) {
                    VkUtils.imageBarrier(stack, cmdHandle, prepassDistortionImages[slot].getVkImage(),
                            VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                            VK_PIPELINE_STAGE_2_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                            VK_ACCESS_2_NONE, VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT, VK_IMAGE_ASPECT_COLOR_BIT);
                    VkUtils.imageBarrier(stack, cmdHandle, prepassVolumetricImages[slot].getVkImage(),
                            VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                            VK_PIPELINE_STAGE_2_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                            VK_ACCESS_2_NONE, VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT, VK_IMAGE_ASPECT_COLOR_BIT);
                }
                cmdBuffer.endRecording();
                cmdBuffer.submitAndWait(vkCtx, graphQueue);
            } finally {
                cmdBuffer.cleanup(vkCtx, cmdPool);
                cmdPool.cleanup(vkCtx);
            }
        }

        // NEAREST 点采样器（composite 全程 texelFetch，不滤波；NEAREST 对任何格式都合法）
        Device device = vkCtx.getDevice();
        try (var stack = MemoryStack.stackPush()) {
            var samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType$Default()
                    .magFilter(VK_FILTER_NEAREST)
                    .minFilter(VK_FILTER_NEAREST)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .maxLod(0.0f);
            long[] lp = new long[1];
            vkCheck(vkCreateSampler(device.getVkDevice(), samplerInfo, null, lp),
                    "Failed to create Kerr prepass sampler");
            prepassSampler = lp[0];
        }
    }

    /** 释放 prepass 双附件（采样器由 cleanup 统一销毁） */
    private void cleanupPrepassResources(VkCtx vkCtx) {
        if (prepassDistortionViews != null) {
            for (int slot = 0; slot < prepassDistortionViews.length; slot++) {
                if (prepassDistortionViews[slot] != null) {
                    prepassDistortionViews[slot].cleanup(vkCtx.getDevice());
                }
                if (prepassDistortionImages != null && prepassDistortionImages[slot] != null) {
                    prepassDistortionImages[slot].cleanup(vkCtx);
                }
                if (prepassVolumetricViews != null && prepassVolumetricViews[slot] != null) {
                    prepassVolumetricViews[slot].cleanup(vkCtx.getDevice());
                }
                if (prepassVolumetricImages != null && prepassVolumetricImages[slot] != null) {
                    prepassVolumetricImages[slot].cleanup(vkCtx);
                }
            }
            prepassDistortionViews = null;
            prepassDistortionImages = null;
            prepassVolumetricViews = null;
            prepassVolumetricImages = null;
        }
    }

    /** 创建/重建两个 pass 的动态渲染信息（全部堆分配，与 renderInfo 同生命周期） */
    private void createRenderInfos(int width, int height) {
        if (renderInfo != null) {
            renderInfo.free();
        }
        if (attInfoHist != null) {
            attInfoHist.free();
        }
        if (bloomRenderInfo != null) {
            bloomRenderInfo.free();
        }
        if (attInfoBloom != null) {
            attInfoBloom.free();
        }
        if (renderArea != null) {
            renderArea.free();
            renderExtent.free();
            bloomRenderArea.free();
            bloomRenderExtent.free();
            prepassRenderArea.free();
            prepassRenderExtent.free();
        }
        attInfoHist = VkRenderingAttachmentInfo.calloc(1);
        attInfoBloom = VkRenderingAttachmentInfo.calloc(1);
        attInfoPrepass = VkRenderingAttachmentInfo.calloc(2);
        renderExtent = VkExtent2D.calloc().width(width).height(height);
        renderArea = VkRect2D.calloc().extent(renderExtent);
        bloomRenderExtent = VkExtent2D.calloc().width(width).height(height);
        bloomRenderArea = VkRect2D.calloc().extent(bloomRenderExtent);
        int halfW = Math.max(1, width / 2);
        int halfH = Math.max(1, height / 2);
        prepassRenderExtent = VkExtent2D.calloc().width(halfW).height(halfH);
        prepassRenderArea = VkRect2D.calloc().extent(prepassRenderExtent);
        renderInfo = VkRenderingInfo.calloc().sType$Default().renderArea(renderArea).layerCount(1);
        bloomRenderInfo = VkRenderingInfo.calloc().sType$Default().renderArea(bloomRenderArea).layerCount(1);
        prepassRenderInfo = VkRenderingInfo.calloc().sType$Default().renderArea(prepassRenderArea).layerCount(1);
    }

    /** 释放所有 GPU 资源 */
    public void cleanup(VkCtx vkCtx) {
        Device device = vkCtx.getDevice();
        for (int i = 0; i < VkUtils.MAX_IN_FLIGHT; i++) {
            if (argsDescSets != null) {
                vkCtx.getDescAllocator().freeDescSet(device, "kerr-args-" + i);
                vkCtx.getDescAllocator().freeDescSet(device, "kerr-args-prepass-" + i);
                vkCtx.getDescAllocator().freeDescSet(device, "kerr-tex-" + i);
                vkCtx.getDescAllocator().freeDescSet(device, "kerr-bloom-" + i);
            }
            if (gameArgsUbo[i] != null) {
                gameArgsUbo[i].cleanup(vkCtx);   // 内部含 unMap
                gameArgsUbo[i] = null;
            }
            if (bhArgsUbo[i] != null) {
                bhArgsUbo[i].cleanup(vkCtx);
                bhArgsUbo[i] = null;
            }
            if (gameArgsPrepassUbo[i] != null) {
                gameArgsPrepassUbo[i].cleanup(vkCtx);
                gameArgsPrepassUbo[i] = null;
            }
            if (bhArgsPrepassUbo[i] != null) {
                bhArgsPrepassUbo[i].cleanup(vkCtx);
                bhArgsPrepassUbo[i] = null;
            }
        }
        argsDescSets = null;
        prepassArgsDescSets = null;
        texDescSets = null;
        bloomDescSets = null;
        if (argsLayout != null) {
            argsLayout.cleanup(vkCtx);
        }
        if (texLayout != null) {
            texLayout.cleanup(vkCtx);
        }
        if (bloomTexLayout != null) {
            bloomTexLayout.cleanup(vkCtx);
        }
        if (dualSkybox != null) {
            dualSkybox.cleanup(vkCtx);
        }
        if (noiseHashLut != null) {
            noiseHashLut.cleanup(vkCtx);
            noiseHashLut = null;
        }
        cleanupHistoryResources(vkCtx);
        cleanupPrepassResources(vkCtx);
        if (histSampler != 0) {
            vkDestroySampler(device.getVkDevice(), histSampler, null);
            histSampler = 0;
        }
        if (pushConstBuff != null) {
            MemoryUtil.memFree(pushConstBuff);
            pushConstBuff = null;
        }
        if (prepassSampler != 0) {
            vkDestroySampler(device.getVkDevice(), prepassSampler, null);
            prepassSampler = 0;
        }
        if (pipeline != null) {
            pipeline.cleanup(vkCtx);
        }
        if (prepassPipeline != null) {
            prepassPipeline.cleanup(vkCtx);
        }
        if (compositePipeline != null) {
            compositePipeline.cleanup(vkCtx);
        }
        if (renderInfo != null) {
            renderInfo.free();
        }
        if (attInfoHist != null) {
            attInfoHist.free();
        }
        if (bloomRenderInfo != null) {
            bloomRenderInfo.free();
        }
        if (attInfoBloom != null) {
            attInfoBloom.free();
        }
        if (prepassRenderInfo != null) {
            prepassRenderInfo.free();
        }
        if (attInfoPrepass != null) {
            attInfoPrepass.free();
        }
        if (renderArea != null) {
            renderArea.free();
            renderExtent.free();
            bloomRenderArea.free();
            bloomRenderExtent.free();
            prepassRenderArea.free();
            prepassRenderExtent.free();
        }
    }

    /** 释放历史图与视图 */
    private void cleanupHistoryResources(VkCtx vkCtx) {
        if (histViews != null) {
            for (int slot = 0; slot < histViews.length; slot++) {
                for (int j = 0; j < 2; j++) {
                    if (histViews[slot][j] != null) {
                        histViews[slot][j].cleanup(vkCtx.getDevice());
                    }
                    if (histImages != null && histImages[slot][j] != null) {
                        histImages[slot][j].cleanup(vkCtx);
                    }
                }
            }
            histViews = null;
            histImages = null;
        }
    }

    /**
     * 执行一帧克尔渲染：Pass A 写 TAA 历史 → 布局翻转 → Pass B Bloom 合成到交换链。
     */
    public void render(VkCtx vkCtx, CmdBuffer cmdBuffer, EngCtx engCtx, int currentFrame, int imageIndex) {
        try (var stack = MemoryStack.stackPush()) {
            VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();
            SwapChain swapChain = vkCtx.getSwapChain();
            VkExtent2D extent = swapChain.getSwapChainExtent();
            int width = extent.width();
            int height = extent.height();
            int slot = currentFrame;
            int readIdx = histReadIdx[slot];
            int writeIdx = 1 - readIdx;
            Device device = vkCtx.getDevice();
            var kp = engCtx.scene().getKerrParams();
            // prepass 管线懒建：开关首次打开时现建（vkCreateGraphicsPipelines 是宿主侧
            // 调用，不涉命令缓冲录制，可安全在帧循环内触发）
            if (kp.prepassEnabled) {
                ensurePrepassPipeline(vkCtx);
            }

            // GUI 山海盒开关 → b1 绑定交换（惰性加载/卸载，waitIdle 后重绑两插槽描述符）
            boolean wantMountainsSeas = engCtx.scene().isMountainsSeasSkybox();
            if (wantMountainsSeas != dualSkybox.isMountainsSeasBound()) {
                dualSkybox.bind(vkCtx, wantMountainsSeas, box -> rebindSkyboxDescSets(device, box));
            }

            writeUbos(engCtx, slot, width, height);
            gameArgsUbo[slot].flush(vkCtx);
            bhArgsUbo[slot].flush(vkCtx);
            if (kp.prepassEnabled) {
                gameArgsPrepassUbo[slot].flush(vkCtx);
                bhArgsPrepassUbo[slot].flush(vkCtx);
            }

            // 历史视图指向：set1.b0 = 上次写入（供混合），Bloom set0 = 本次写入（合成到屏幕）；
            // b7/b8 = 本插槽 prepass 双附件（每帧重绑，与 b0 同一惯用法）
            texDescSets[slot].setImage(device, histSampler, histViews[slot][readIdx].getVkImageView(),
                    0, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            texDescSets[slot].setImage(device, prepassSampler, prepassDistortionViews[slot].getVkImageView(),
                    7, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            texDescSets[slot].setImage(device, prepassSampler, prepassVolumetricViews[slot].getVkImageView(),
                    8, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            bloomDescSets[slot].setImage(device, histSampler, histViews[slot][writeIdx].getVkImageView(),
                    0, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);

            // ===== Pass P：半分辨率扭曲场 + 体积色（prepass 开启时） =====
            if (kp.prepassEnabled) {
                int pw = Math.max(1, width / 2);
                int ph = Math.max(1, height / 2);
                VkUtils.imageBarrier(stack, cmdHandle, prepassDistortionImages[slot].getVkImage(),
                        VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                        VK_PIPELINE_STAGE_2_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                        VK_ACCESS_2_NONE, VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT, VK_IMAGE_ASPECT_COLOR_BIT);
                VkUtils.imageBarrier(stack, cmdHandle, prepassVolumetricImages[slot].getVkImage(),
                        VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                        VK_PIPELINE_STAGE_2_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                        VK_ACCESS_2_NONE, VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT, VK_IMAGE_ASPECT_COLOR_BIT);

                attInfoPrepass.get(0).sType$Default()
                        .imageView(prepassDistortionViews[slot].getVkImageView())
                        .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                        .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                        .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
                attInfoPrepass.get(1).sType$Default()
                        .imageView(prepassVolumetricViews[slot].getVkImageView())
                        .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                        .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                        .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
                prepassRenderInfo.pColorAttachments(attInfoPrepass);
                prepassRenderInfo.renderArea().extent().width(pw).height(ph);

                vkCmdBeginRendering(cmdHandle, prepassRenderInfo);
                vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, prepassPipeline.getVkPipeline());
                // 与主 pass 同为翻转视口：prepass 图行序与历史一致，composite texelFetch 直接对齐
                var prepassViewport = VkViewport.calloc(1, stack)
                        .x(0).y(ph).height(-ph).width(pw)
                        .minDepth(0.0f).maxDepth(1.0f);
                vkCmdSetViewport(cmdHandle, 0, prepassViewport);
                var prepassScissor = VkRect2D.calloc(1, stack)
                        .extent(it -> it.width(pw).height(ph))
                        .offset(it -> it.x(0).y(0));
                vkCmdSetScissor(cmdHandle, 0, prepassScissor);
                vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, prepassPipeline.getVkPipelineLayout(), 0,
                        stack.longs(prepassArgsDescSets[slot].getVkDescriptorSet(), texDescSets[slot].getVkDescriptorSet()),
                        null);
                vkCmdDraw(cmdHandle, 6, 1, 0, VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
                vkCmdEndRendering(cmdHandle);

                // prepass 双附件写完 → 本帧 composite 读（同帧内 barrier）
                VkUtils.imageBarrier(stack, cmdHandle, prepassDistortionImages[slot].getVkImage(),
                        VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                        VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT, VK_ACCESS_2_SHADER_READ_BIT,
                        VK_IMAGE_ASPECT_COLOR_BIT);
                VkUtils.imageBarrier(stack, cmdHandle, prepassVolumetricImages[slot].getVkImage(),
                        VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                        VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT, VK_ACCESS_2_SHADER_READ_BIT,
                        VK_IMAGE_ASPECT_COLOR_BIT);
            }

            // ===== Pass A：克尔渲染 → 历史 =====
            attInfoHist.get(0).sType$Default()
                    .imageView(histViews[slot][writeIdx].getVkImageView())
                    .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            renderInfo.pColorAttachments(attInfoHist);
            renderInfo.renderArea().extent().width(width).height(height);

            vkCmdBeginRendering(cmdHandle, renderInfo);
            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getVkPipeline());

            // 翻转视口（kerr.frag 已做 Uv.y 翻转适配，与项目其余 pass 一致）
            var viewport = VkViewport.calloc(1, stack)
                    .x(0).y(height).height(-height).width(width)
                    .minDepth(0.0f).maxDepth(1.0f);
            vkCmdSetViewport(cmdHandle, 0, viewport);
            var scissor = VkRect2D.calloc(1, stack)
                    .extent(it -> it.width(width).height(height))
                    .offset(it -> it.x(0).y(0));
            vkCmdSetScissor(cmdHandle, 0, scissor);

            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getVkPipelineLayout(), 0,
                    stack.longs(argsDescSets[slot].getVkDescriptorSet(), texDescSets[slot].getVkDescriptorSet()),
                    null);
            vkCmdDraw(cmdHandle, 6, 1, 0, VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
            vkCmdEndRendering(cmdHandle);

            // 布局翻转 + mip 链生成：写入槽基底层 COLOR→TRANSFER_SRC，高层批量 UNDEFINED→TRANSFER_DST
            // （内容丢弃重建），vkCmdBlitImage 逐级减半，尾部全链 → SHADER_READ（合成 pass 取辉光
            // octave，TAA 混合 texelFetch 读基底层）；读取槽 → COLOR（下次写入）
            long histImage = histImages[slot][writeIdx].getVkImage();
            if (histMipLevels > 1) {
                VkUtils.imageBarrier(stack, cmdHandle, histImage,
                        VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                        VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT, VK_ACCESS_TRANSFER_READ_BIT,
                        VK_IMAGE_ASPECT_COLOR_BIT, 0, 1);
                VkUtils.imageBarrier(stack, cmdHandle, histImage,
                        VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        VK_PIPELINE_STAGE_2_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK_ACCESS_2_NONE, VK_ACCESS_TRANSFER_WRITE_BIT,
                        VK_IMAGE_ASPECT_COLOR_BIT, 1, histMipLevels - 1);
                int levelW = histWidth;
                int levelH = histHeight;
                for (int lvl = 1; lvl < histMipLevels; lvl++) {
                    if (lvl > 1) {
                        // 上级刚被上一轮 blit 写入：TRANSFER_DST→TRANSFER_SRC 供本轮作 blit 源
                        VkUtils.imageBarrier(stack, cmdHandle, histImage,
                                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                                VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                                VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_TRANSFER_READ_BIT,
                                VK_IMAGE_ASPECT_COLOR_BIT, lvl - 1, 1);
                    }
                    int downW = Math.max(1, levelW >> 1);
                    int downH = Math.max(1, levelH >> 1);
                    int srcLvl = lvl - 1;
                    int dstLvl = lvl;
                    var blitRegion = VkImageBlit.calloc(1, stack)
                            .srcSubresource(it -> it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                    .mipLevel(srcLvl).baseArrayLayer(0).layerCount(1))
                            .dstSubresource(it -> it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                    .mipLevel(dstLvl).baseArrayLayer(0).layerCount(1));
                    blitRegion.srcOffsets(0).set(0, 0, 0);
                    blitRegion.srcOffsets(1).set(levelW, levelH, 1);
                    blitRegion.dstOffsets(0).set(0, 0, 0);
                    blitRegion.dstOffsets(1).set(downW, downH, 1);
                    vkCmdBlitImage(cmdHandle, histImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                            histImage, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                            blitRegion, VK_FILTER_LINEAR);
                    levelW = downW;
                    levelH = downH;
                }
                // 尾部：低级 TRANSFER_SRC→SHADER_READ（合成/采样），最高级 TRANSFER_DST→SHADER_READ
                VkUtils.imageBarrier(stack, cmdHandle, histImage,
                        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                        VK_ACCESS_TRANSFER_READ_BIT, VK_ACCESS_2_SHADER_READ_BIT,
                        VK_IMAGE_ASPECT_COLOR_BIT, 0, histMipLevels - 1);
                VkUtils.imageBarrier(stack, cmdHandle, histImage,
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                        VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_2_SHADER_READ_BIT,
                        VK_IMAGE_ASPECT_COLOR_BIT, histMipLevels - 1, 1);
            } else {
                VkUtils.imageBarrier(stack, cmdHandle, histImage,
                        VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                        VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT, VK_ACCESS_2_SHADER_READ_BIT,
                        VK_IMAGE_ASPECT_COLOR_BIT);
            }
            VkUtils.imageBarrier(stack, cmdHandle, histImages[slot][readIdx].getVkImage(),
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                    VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                    VK_ACCESS_2_SHADER_READ_BIT, VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                    VK_IMAGE_ASPECT_COLOR_BIT);

            // ===== Pass B：合成（8-octave 辉光 + NPGS ColorBlend 调色链）→ 交换链 =====
            attInfoBloom.get(0).sType$Default()
                    .imageView(swapChain.getImageView(imageIndex).getVkImageView())
                    .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            bloomRenderInfo.pColorAttachments(attInfoBloom);
            bloomRenderInfo.renderArea().extent().width(width).height(height);

            vkCmdBeginRendering(cmdHandle, bloomRenderInfo);
            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, compositePipeline.getVkPipeline());

            // 不翻转视口：kerr.vert 的 uv=(ndc+1)/2 在非翻转视口下与历史图行序对齐
            var bloomViewport = VkViewport.calloc(1, stack)
                    .x(0).y(0).height(height).width(width)
                    .minDepth(0.0f).maxDepth(1.0f);
            vkCmdSetViewport(cmdHandle, 0, bloomViewport);
            vkCmdSetScissor(cmdHandle, 0, scissor);

            vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, compositePipeline.getVkPipelineLayout(), 0,
                    stack.longs(bloomDescSets[slot].getVkDescriptorSet()), null);
            // 合成参数：辉光总强度（Kerr Disk 面板可调；0 = 纯调色链，1.0 = NPGS 原始基准）
            pushConstBuff.putFloat(0, kp.bloomStrength);
            vkCmdPushConstants(cmdHandle, compositePipeline.getVkPipelineLayout(),
                    VK_SHADER_STAGE_FRAGMENT_BIT, 0, pushConstBuff);
            vkCmdDraw(cmdHandle, 6, 1, 0, VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
            vkCmdEndRendering(cmdHandle);

            histReadIdx[slot] = writeIdx;
            histFrameCount[slot]++;
        }
    }

    /** 窗口尺寸变化：重建历史缓冲与渲染信息，重绑 b9 占位 */
    public void resize(VkCtx vkCtx, int width, int height) {
        createRenderInfos(width, height);
        cleanupHistoryResources(vkCtx);
        var graphQueue = new Queue.GraphicsQueue(vkCtx, 0);
        createHistoryResources(vkCtx, graphQueue, width, height);
        // prepass 双附件随半分辨率重建；b7/b8 在下一帧 render() 重绑（与 b0 同惯例）
        cleanupPrepassResources(vkCtx);
        createPrepassResources(vkCtx, graphQueue, width, height);
        histReadIdx = new int[]{0, 0};
        Arrays.fill(histFrameCount, 0);
        Device device = vkCtx.getDevice();
        for (int i = 0; i < VkUtils.MAX_IN_FLIGHT; i++) {
            texDescSets[i].setImage(device, histSampler, histViews[i][0].getVkImageView(), 9,
                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        }
    }

    /**
     * 打包两个 UBO。std140 布局自然对齐无填充，字段顺序与 kerr.frag 声明严格一致。
     */
    private void writeUbos(EngCtx engCtx, int slot, int width, int height) {
        Scene scene = engCtx.scene();
        Camera camera = scene.getCamera();
        var engCfg = EngCfg.getInstance();
        float animTime = (float) ((System.nanoTime() - startTime) / 1_000_000_000.0);

        // ---- GameArgs：vec2 分辨率 + fov(弧度) + 时间组 ----
        // EngCfg.fov 已是弧度且为垂直 FOV（与 joml perspective(fovY) 同源）；
        // kerr.frag 的 FragUvToDir 约定 x 乘半角正切、y 乘(半角正切×宽高比)，
        // 即等效"水平 FOV"约定 —— 换算成水平弧度传入，保证与史瓦西管线视锥逐像素一致
        float halfTanY = (float) Math.tan(engCfg.getFov() * 0.5);
        float fovHorizontalRad = (float) (2.0 * Math.atan(halfTanY * width / height));
        ByteBuffer game = MemoryUtil.memByteBuffer(gameMapped[slot], GAME_ARGS_SIZE);
        game.putFloat(width).putFloat(height);
        game.putFloat(fovHorizontalRad);                            // iFovRadians（水平，勿再 tan）
        game.putFloat(animTime);                                  // iTime（抖动种子）
        game.putFloat(animTime);                                  // iGameTime
        game.putFloat(1.0f / 60.0f);                              // iTimeDelta（估算，仅装饰用）
        game.putFloat(engCfg.getTimeRate());                      // iTimeRate

        // ---- BlackHoleArgs ----
        Matrix4f view = camera.getViewMatrix();
        rotView.set(view).setTranslation(0, 0, 0);                // 视图旋转：世界→相机
        camToWorldRot.set(rotView).invert();                      // 其逆：相机→世界

        var geodesic = scene.getGeodesic();
        boolean geodesicActive = geodesic.isActive();

        ByteBuffer bh = MemoryUtil.memByteBuffer(bhMapped[slot], BH_ARGS_SIZE);
        camToWorldRot.get(bh);                                    // iInverseCamRot=相机→世界（着色器以此旋转相机系方向到世界）
        bh.position(64);

        if (geodesicActive) {
            // 观者模式 -1 约定：位置直接给 KS 世界坐标（着色器跳过相机系变换,见 kerr.frag TraceRay）
            Vector3f p = geodesic.getPosition(tmpVec);
            bh.putFloat(p.x).putFloat(p.y).putFloat(p.z).putFloat(0);
        } else {
            view.transformPosition(0, 0, 0, tmpVec);              // = -R·camPos（黑洞在原点）
            bh.putFloat(tmpVec.x).putFloat(tmpVec.y).putFloat(tmpVec.z).putFloat(0);  // 相对位置
        }
        putDir(bh, rotView, 0, 1, 0);                             // 盘法向（自旋轴 +Y，相机系）
        putDir(bh, rotView, 0, 0, 1);                             // 盘切向
        if (geodesicActive) {
            // 观者模式 -1:iCameraVelocity 不读,置零;iU_up=四速度,ie1/2/3_up=折叠头转的输运标架
            // (着色器 P = U − Σ v·E 重构光子四动量(v=相机系视方向,-Z 前),
            //  视线的多普勒/光行差/参考系拖拽全部由度规处理)
            bh.putFloat(0).putFloat(0).putFloat(0).putFloat(0);
            geodesic.getFoldedTetrad(foldedTetrad);
            for (int a = 0; a < 3; a++) {
                for (int mu = 0; mu < 4; mu++) {
                    bh.putFloat((float) foldedTetrad[a][mu]);
                }
            }
            geodesic.getFourVelocity(fourVel);
            for (int mu = 0; mu < 4; mu++) {
                bh.putFloat((float) fourVel[mu]);
            }
        } else {
            for (int i = 0; i < 5; i++) {                         // 相机速度 + 四维标架（静态观者全零；5 个 vec4 槽一个不能少，
                bh.putFloat(0).putFloat(0).putFloat(0).putFloat(0);   // 少一个后面 37 个 float 全部错位 16B）
            }
        }
        // 11 个 int 开关（顺序同声明；可调项读 Scene.kerrParams —— GUI 面板数据源）
        var kp = scene.getKerrParams();
        float spin = kp.spin;
        bh.putInt(geodesic.isOutgoingPatch() ? 1 : 0)  // iCamDataCoordisOutgoing（CheckAndSwitchCoords 换系后同步）
          .putInt(0)      // iDEBUG
          .putInt(kp.prepassEnabled ? 2 : 0)  // iPrepass（2=composite 边缘感知合成；0=原路径）
          .putInt(0)      // iWhitehole
          .putInt(0)      // iInWhichUniverse
          .putInt(0)      // iGrid
          .putInt(kp.enableHeatHaze ? 1 : 0)  // iEnableHeatHaze
          .putInt(0)      // iEnableShadowCulling（NPGS 默认 0）
          .putInt(geodesicActive ? -1 : 0)      // iObserverMode（-1=外传四维标架；0=静态观者）
          .putInt(0)      // iPolarization
          .putInt(0);     // iUseImageDisk
        // 37 个 float（顺序同声明）

        // 盘时间推进（c·s/Rs）：真实帧间隔 × GUI 倍率；dt 钳制防失焦恢复后跳变，
        // 累计器存 KerrParams 使模式热切换/resize 重建渲染器后相位连续；
        // 时间暂停（P 键）时冻结——iTime（墙钟）继续流动，TAA 抖动不受影响
        long now = System.nanoTime();
        float dt = Math.min(0.1f, Math.max(0.0f, (now - lastUboNanos) / 1_000_000_000.0f));
        lastUboNanos = now;
        if (!scene.isTimePaused()) {
            kp.diskTimeCsRs += dt * kp.timeScale * BH_TIME_SCALE;
        }

        bh.putFloat(kp.quality)                  // iQuality
          .putFloat(1.0f)                        // iUniverseSign
          .putFloat(kp.diskTimeCsRs)             // iBlackHoleTime（c·s/Rs 单位，NPGS: GameTime*c/Rs）
          .putFloat(BH_MASS_SOL)                 // iBlackHoleMassSol
          .putFloat(spin)                        // iSpin ← GUI
          .putFloat(kp.qStar)                    // iQ（Kerr–Newman 电荷）
          .putFloat(kp.mu)                       // iMu（吸积物质比荷,影响盘温标）
          .putFloat(kp.accretionRate)            // iAccretionRate
          .putFloat(kp.backShiftMax)             // iBackShiftMax
          .putFloat(0.0f)                        // iDensestarsurfaceR
          .putFloat(4.0f)                        // iDensestarBlackbodyIntensityExponent
          .putFloat(1.0f)                        // iDensestarRedShiftColorExponent
          .putFloat(4.0f)                        // iDensestarRedShiftIntensityExponent
          .putFloat(1.0f)                        // iDensestarBrightmut
          .putFloat(KerrParams.iscoInnerRadiusRs(spin, kp.qStar))  // iInterRadiusRs（ISCO(a*,Q*)，随自旋/电荷变化）
          .putFloat(kp.outerRadiusRs)            // iOuterRadiusRs
          .putFloat(kp.thinRs)                   // iThinRs
          .putFloat(kp.hopper)                   // iHopper
          .putFloat(kp.brightmut)                // iBrightmut
          .putFloat(kp.darkmut)                  // iDarkmut（0=盘完全透明不可见）
          .putFloat(kp.reddening)                // iReddening
          .putFloat(kp.saturation)               // iSaturation
          .putFloat(kp.blackbodyIntensityExponent)  // iBlackbodyIntensityExponent
          .putFloat(kp.redShiftColorExponent)    // iRedShiftColorExponent
          .putFloat(kp.redShiftIntensityExponent)  // iRedShiftIntensityExponent
          .putFloat(0.0f)                        // iImageRotationSpeed
          .putFloat(0.0f)                        // iPolarizationAngle
          .putFloat(kp.heatHaze)                 // iHeatHaze
          .putFloat(kp.backgroundBrightmut)      // iBackgroundBrightmut
          .putFloat(kp.photonRingBoost)          // iPhotonRingBoost
          .putFloat(kp.photonRingColorTempBoost) // iPhotonRingColorTempBoost
          .putFloat(kp.boostRot)                 // iBoostRot
          .putFloat(kp.jetRedShiftIntensityExponent)  // iJetRedShiftIntensityExponent
          .putFloat(kp.jetBrightmut)             // iJetBrightmut（0=喷流关闭）
          .putFloat(kp.jetSaturation)            // iJetSaturation
          .putFloat(kp.jetShiftMax)              // iJetShiftMax
          .putFloat(histFrameCount[slot] < 2 ? 1.0f
                  : prevViewPerSlot[slot].equals(view) ? 0.06f : 1.0f)  // iBlendWeight（前 2 帧全量覆盖；仅镜头静止时累积，动了即全量重置防拖影）
          .putFloat(kp.noiseLutEnabled ? 1.0f : 0.0f)            // iNoiseLut（扩展字段@384：噪声哈希查表 A/B 开关）
          .putFloat(kp.diskScatter)                              // iDiskScatter（扩展字段@388：盘前向散射强度/背光项，0=关）
          .putFloat(kp.diskAmbient);                             // iDiskAmbient（扩展字段@392：盘环境光强度/弥散项，0=关）
        prevViewPerSlot[slot].set(view);

        // ---- prepass UBO：GameArgs 分辨率减半 + iPrepass=1；BlackHoleArgs 字节复制主拷贝后仅改 iPrepass，
        //      保证与主 UBO 除该开关外逐字节一致（NPGS 同款 GameArgsPrepass 布局） ----
        if (kp.prepassEnabled) {
            int pw = Math.max(1, width / 2);
            int ph = Math.max(1, height / 2);
            ByteBuffer gamePre = MemoryUtil.memByteBuffer(gamePreMapped[slot], GAME_ARGS_SIZE);
            gamePre.putFloat(pw).putFloat(ph);
            gamePre.putFloat(fovHorizontalRad)          // iFovRadians（水平，勿再 tan）
                    .putFloat(animTime)                 // iTime
                    .putFloat(animTime)                 // iGameTime
                    .putFloat(1.0f / 60.0f)             // iTimeDelta
                    .putFloat(engCfg.getTimeRate());    // iTimeRate
            MemoryUtil.memCopy(bhMapped[slot], bhPreMapped[slot], BH_ARGS_SIZE);
            ByteBuffer bhPre = MemoryUtil.memByteBuffer(bhPreMapped[slot], BH_ARGS_SIZE);
            bhPre.putInt(BH_ARGS_IPREPASS_OFFSET, 1);
        }
    }

    /** [临时调试] 重绑两个插槽 set1.b1 到指定盒 */
    private void rebindSkyboxDescSets(Device device, CubeTexture box) {
        for (int i = 0; i < VkUtils.MAX_IN_FLIGHT; i++) {
            texDescSets[i].setImage(device, box.getSampler(), box.getImageView().getVkImageView(),
                    1, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        }
    }

    /** 方向向量经相机系旋转后写入 UBO（vec4，w=0） */
    private void putDir(ByteBuffer bh, Matrix4f rot, float x, float y, float z) {
        Vector3f v = rot.transformDirection(tmpDir.set(x, y, z));
        bh.putFloat(v.x).putFloat(v.y).putFloat(v.z).putFloat(0);
    }
}
