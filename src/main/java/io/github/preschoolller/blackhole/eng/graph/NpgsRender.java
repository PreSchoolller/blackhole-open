package io.github.preschoolller.blackhole.eng.graph;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import io.github.preschoolller.blackhole.eng.AppLog;
import io.github.preschoolller.blackhole.eng.EngCfg;
import io.github.preschoolller.blackhole.eng.EngCtx;
import io.github.preschoolller.blackhole.eng.graph.vk.*;
import io.github.preschoolller.blackhole.eng.graph.vk.Queue;
import io.github.preschoolller.blackhole.eng.scene.Camera;
import io.github.preschoolller.blackhole.eng.scene.KerrParams;
import io.github.preschoolller.blackhole.eng.scene.Scene;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK13.*;
import static io.github.preschoolller.blackhole.eng.graph.vk.VkUtils.vkCheck;
import static io.github.preschoolller.blackhole.eng.graph.vk.VkUtils.MAX_IN_FLIGHT;
import static io.github.preschoolller.blackhole.eng.graph.vk.ShaderCompiler.compileShaderIfChanged;
import static io.github.preschoolller.blackhole.utils.Constants.SHADERS_DIR;

/**
 * NPGS 原版 shader 复刻渲染器（npgs-verbatim 分支实验）——
 * 用 {@code resources/shaders/npgs/} 下逐字节未动的 NPGS 原始 GLSL 渲染克尔时空，
 * 回答"本框架不改人家 shader 能不能跑"的问题；结构克隆自 {@link KerrRender}。
 * <p>
 * 与 KerrRender 的差异（全部在框架侧，NPGS 文件零改动）：
 * <ul>
 *   <li>片元走 NPGS 原生三件套：BlackHole_prepass（半分辨率双附件）→
 *       BlackHole_composite（边缘感知升采样 + tonemap + TAA）→ 本框架 kerr_composite
 *       （8-octave 辉光 + NPGS ColorBlend 调色链逐行照搬版）到交换链；
 *       npgs.direct=true 时改用 BlackHole.frag 单 pass 全分辨率直出（NPGS 的原路径），
 *       跳过 prepass/composite</li>
 *   <li>顶点没有用 NPGS 的 ScreenQuad.vert：它需要顶点缓冲（Position+TexCoord），
 *       而本框架全屏 pass 一律无顶点缓冲（gl_VertexIndex 生成）。沿用 kerr.vert ——
 *       location 0 输出 vec2 恰好满足 BlackHole.frag 的 TexCoordFromVert 输入契约
 *       （其余两个 frag 无顶点输入，多余输出无害）。NPGS 四个 GLSL 文件保持原样</li>
 *   <li>{@link ShaderCompiler} 为本实验新增 #include 解析（NPGS 用 include 组织代码，
 *       BlackHole.frag 还经两条路径重复引入 CoordConverter.glsl，include-once 去重必需）</li>
 *   <li>天空盒：NPGS 原装 6 套盒（Universe/Antiverse × 0/1/2）拷贝至
 *       resources/textures/npgs/ 并绑 set1.b1..b6（KerrRender 裁剪为一套：其 %3 选层
 *       依赖 iInWhichUniverse/白洞模式，本项目恒选 0 号层但槽位全部备齐以贴原版布局）；
 *       加载约定与 NPGS 一致（stb 不翻转、R8G8B8A8_UNORM，见 CubeTexture 类注释），
 *       本框架 kerr.frag 的 Uv.y 翻转适配在原版 shader 中不存在，两侧组合恰好对齐</li>
 *   <li>UBO 打包与 KerrRender 完全一致：BlackHoleArgs 前 384 字节与 NPGS 声明逐字段
 *       对齐（std140 前缀兼容），末尾 3 个本项目扩展字段 NPGS 声明不含、自然忽略；
 *       iPrepass 语义回到 NPGS 原值（1=边缘感知合成，0=全分辨率重迹）</li>
 *   <li>无 DualSkybox/NoiseHashLut：原版 shader 不含本项目扩展（噪声全程程序化 sin 哈希），
 *       山海盒 GUI 开关在复刻模式下不生效</li>
 * </ul>
 * KerrParams/GUI 面板复用：时空模式仍是 Scene.KERR（Render 按配置选择渲染器实现），
 * Kerr Disk 面板与测地相机（G 键，观者模式 -1 四维标架）在复刻模式下照常工作。
 */
public class NpgsRender {

    /** 顶点着色器沿用本框架 kerr.vert（全屏四边形 + location 0 vec2 输出，见类注释） */
    private static final String VERTEX_SHADER = SHADERS_DIR + "kerr.vert";
    /** NPGS 原版三件套 + 直绘路径（逐字节未动的拷贝，include 依赖同目录 Common/） */
    private static final String NPGS_FRAGS_DIR = SHADERS_DIR + "npgs/";
    private static final String DIRECT_FRAG = NPGS_FRAGS_DIR + "BlackHole.frag.glsl";
    private static final String PREPASS_FRAG = NPGS_FRAGS_DIR + "BlackHole_prepass.frag.glsl";
    private static final String COMPOSITE_FRAG = NPGS_FRAGS_DIR + "BlackHole_composite.frag.glsl";
    /** 合成到交换链沿用本框架 kerr_composite.frag（辉光 + ColorBlend 调色链；npgs.postChain=false 时） */
    private static final String BLOOM_COMPOSITE_SHADER = SHADERS_DIR + "kerr_composite.frag";
    /** NPGS 原版后处理链（npgs.postChain=true 时）：Bloom.comp 同源双变体（图集打包/可分离模糊）
     *  + ColorBlend.frag（8-octave 辉光重建 + 调色链）——逐字节未动的拷贝 */
    private static final String BLOOM_COMPUTE_SHADER = NPGS_FRAGS_DIR + "Bloom.comp.glsl";
    private static final String COLOR_BLEND_SHADER = NPGS_FRAGS_DIR + "ColorBlend.frag.glsl";
    /** NPGS 原装天空盒目录（6 套盒拷贝自 NPGS Assets/Textures） */
    private static final String NPGS_SKYBOX_DIR = "/textures/npgs/";
    /** set1.b1..b6 对应的盒目录，顺序 = NPGS 绑定序（Background0/Antiground0/1/2） */
    private static final String[] SKYBOX_DIRS = {
            "Universe0Skybox", "Antiverse0Skybox",
            "Universe1Skybox", "Antiverse1Skybox",
            "Universe2Skybox", "Antiverse2Skybox"};
    /** TAA 历史/合成输出格式：与 NPGS 一致（R16G16B16A16Sfloat，frag 内 tonemap 后 [0,1]） */
    private static final int HISTORY_FORMAT = VK_FORMAT_R16G16B16A16_SFLOAT;
    /** prepass 扭曲场：NPGS 用 32F 保向量/频移精度 */
    private static final int PREPASS_DISTORTION_FORMAT = VK_FORMAT_R32G32B32A32_SFLOAT;
    /** prepass 体积色：NPGS 原版 16F（HDR 线性域） */
    private static final int PREPASS_VOLUMETRIC_FORMAT = VK_FORMAT_R16G16B16A16_SFLOAT;
    /** iPrepass 在 BlackHoleArgs UBO 内的字节偏移：mat4(64) + 8×vec4(128) + 前两个 int(8) */
    private static final int BH_ARGS_IPREPASS_OFFSET = 200;
    /** GameArgs UBO 字节数（std140：vec2 + 4×float → 32） */
    private static final int GAME_ARGS_SIZE = 32;
    /** BlackHoleArgs UBO 字节数：NPGS 声明到 384（mat4+8×vec4+11×int+37×float），
     *  缓冲沿用 KerrRender 的 400（末尾 3 个本项目扩展字段 NPGS 声明不含、自然忽略） */
    private static final int BH_ARGS_SIZE = 400;
    /** 黑洞质量（太阳质量倍数）与盘时间换算，与 KerrRender 共用 KerrParams 基准 */
    private static final float BH_MASS_SOL = KerrParams.BH_MASS_SOL;
    private static final float BH_RS_METERS =
            2.0f * 6.67430e-11f * 1.98892e30f * BH_MASS_SOL / (2.99792458e8f * 2.99792458e8f);
    private static final float BH_TIME_SCALE = 2.99792458e8f / BH_RS_METERS;

    /** 直绘模式（npgs.direct）：BlackHole.frag 单 pass，无 prepass/composite */
    private final boolean directMode;
    /** 原版后处理链模式（npgs.postChain，默认 true）：PreBloom/GaussBlur compute 链 +
     *  ColorBlend 合成；false 时沿用本框架 kerr_composite（mip 链辉光） */
    private final boolean postChainMode;
    /** 主管线（写历史）：直绘 = BlackHole.frag；复合 = BlackHole_composite.frag */
    private Pipeline pipeline;
    /** prepass 管线（半分辨率双附件；直绘模式不创建） */
    private Pipeline prepassPipeline;
    /** 合成管线（本帧历史 mip 链 → 辉光+调色 → 交换链；仅 postChainMode=false） */
    private Pipeline compositePipeline;
    /** NPGS 原版后处理链管线（仅 postChainMode=true）：
     *  PreBloom（compute GENERATE_MIPMAP，历史→8-octave 图集）与 GaussBlur（compute
     *  GAUSS_BLUR，push constant 选水平/垂直）为同一 Bloom.comp.glsl 的两个宏变体；
     *  Blend（图形，ColorBlend.frag + kerr.vert，从图集重建辉光 + 调色 → 交换链） */
    private ComputePipeline preBloomPipeline;
    private ComputePipeline gaussBlurPipeline;
    private Pipeline blendPipeline;
    /** prepass 管线懒建所需的 spv 路径（直绘模式为 null） */
    private String vertSpvPath, prepassFragSpvPath;
    /** set 0：GameArgs(b0) + BlackHoleArgs(b1) */
    private DescSetLayout argsLayout;
    /** set 1：历史(b0) + 天空盒×6(b1..b6) + prepass 双纹理(b7..b8) + 贴图占位(b9)；无 b10（原版无噪声 LUT） */
    private DescSetLayout texLayout;
    /** 合成管线 set 0：本帧历史 mip 链(b0) */
    private DescSetLayout bloomTexLayout;
    private DescSet[] argsDescSets;
    private DescSet[] prepassArgsDescSets;
    private DescSet[] texDescSets;
    private DescSet[] bloomDescSets;
    /** GameArgs / BlackHoleArgs 持久映射缓冲（每插槽一份；prepass 份经字节复制仅改 iPrepass/iResolution） */
    private final VkBuffer[] gameArgsUbo = new VkBuffer[MAX_IN_FLIGHT];
    private final VkBuffer[] bhArgsUbo = new VkBuffer[MAX_IN_FLIGHT];
    private final long[] gameMapped = new long[MAX_IN_FLIGHT];
    private final long[] bhMapped = new long[MAX_IN_FLIGHT];
    private final VkBuffer[] gameArgsPrepassUbo = new VkBuffer[MAX_IN_FLIGHT];
    private final VkBuffer[] bhArgsPrepassUbo = new VkBuffer[MAX_IN_FLIGHT];
    private final long[] gamePreMapped = new long[MAX_IN_FLIGHT];
    private final long[] bhPreMapped = new long[MAX_IN_FLIGHT];
    /** prepass 半分辨率双附件 [帧插槽] */
    private Image[] prepassDistortionImages;
    private ImageView[] prepassDistortionViews;
    private Image[] prepassVolumetricImages;
    private ImageView[] prepassVolumetricViews;
    private long prepassSampler;
    /** NPGS 原装 6 套天空盒（set1.b1..b6 静态绑定） */
    private final CubeTexture[] skyboxes = new CubeTexture[SKYBOX_DIRS.length];
    /** NPGS 原版贴图盘（set1.b9；iUseImageDisk 开关控制采样） */
    private Texture2D diskTexture;
    // ---- NPGS 原版后处理链资源（仅 postChainMode；布局/描述符/附件均按帧插槽隔离） ----
    /** compute 侧 set0：GameArgs(b0，COMPUTE 阶段——与图形侧 argsLayout 阶段位不同须分建) */
    private DescSetLayout computeArgsLayout;
    /** compute 侧 set1：源纹理(b0 sampled) + 目标存储图(b1 storage image, GENERAL 布局) */
    private DescSetLayout postTexLayout;
    /** Blend 侧 set1：iBloomTexs[2]（b0 数组 count=2：[0]历史原图 [1]模糊图集） */
    private DescSetLayout blendTexLayout;
    private DescSet[] computeArgsDescSets;
    private DescSet[] preBloomDescSets;
    private DescSet[] gaussBlurDescSets;
    private DescSet[] blendDescSets;
    /** PreBloom 输出（8-octave 图集，全分辨率 R16F×4，SAMPLED|STORAGE）[帧插槽] */
    private Image[] preBloomImages;
    private ImageView[] preBloomViews;
    /** GaussBlur 输出（水平/垂直两趟均写此图，中途回拷 PreBloom 作垂直趟源） */
    private Image[] gaussBlurImages;
    private ImageView[] gaussBlurViews;
    /** TAA 历史 [帧插槽][ping-pong]（含完整 mip 链，供合成 pass 取辉光 octave） */
    private Image[][] histImages;
    private ImageView[][] histViews;
    private int histMipLevels;
    private long histSampler;
    private int[] histReadIdx = {0, 0};
    /** 每插槽已渲染帧数：前 2 帧强制 iBlendWeight=1（防未定义历史 NaN 自延续） */
    private final int[] histFrameCount = {0, 0};
    private VkRenderingInfo renderInfo;
    private VkRenderingAttachmentInfo.Buffer attInfoHist;
    private VkRenderingInfo prepassRenderInfo;
    private VkRenderingAttachmentInfo.Buffer attInfoPrepass;
    private VkRect2D prepassRenderArea;
    private VkExtent2D prepassRenderExtent;
    private VkRenderingInfo bloomRenderInfo;
    private VkRenderingAttachmentInfo.Buffer attInfoBloom;
    /** Bloom push constants（4B：辉光强度） */
    private ByteBuffer pushConstBuff;
    /** renderArea/extent 必须与 renderInfo 同生命周期堆分配（借道 MemoryStack 会悬垂，
     *  renderArea 内容变垃圾 → scissor 裁光 → pass 静默画不出像素；教训同 KerrRender） */
    private VkRect2D renderArea, bloomRenderArea;
    private VkExtent2D renderExtent, bloomRenderExtent;
    private long startTime;
    private long lastUboNanos;
    private final Matrix4f[] prevViewPerSlot = {new Matrix4f(), new Matrix4f()};
    private int histWidth, histHeight;
    private final Matrix4f rotView = new Matrix4f();
    private final Matrix4f camToWorldRot = new Matrix4f();
    private final Vector3f tmpVec = new Vector3f();
    private final Vector3f tmpDir = new Vector3f();
    /** 测地观者四维标架打包暂存（U + 折叠头转的 e1/e2/e3） */
    private final double[][] foldedTetrad = new double[3][4];
    private final double[] fourVel = new double[4];

    public NpgsRender() {
        directMode = EngCfg.getInstance().isNpgsDirect();
        postChainMode = EngCfg.getInstance().isNpgsPostChain();
        startTime = System.nanoTime();
    }

    /**
     * 初始化：编译 NPGS 原版着色器（经 #include 解析）、上传 6 套原装天空盒、
     * 创建历史缓冲/UBO/描述符/管线。
     *
     * @param vkCtx  Vulkan 上下文
     * @param engCtx 引擎上下文（相机/场景参数）
     */
    public void init(VkCtx vkCtx, EngCtx engCtx) {
        startTime = System.nanoTime();
        lastUboNanos = startTime;
        String vertSpv = compileShaderIfChanged(VERTEX_SHADER, VK_SHADER_STAGE_VERTEX_BIT);
        // 后处理链二选一：NPGS 原版 compute 链（Bloom.comp 双宏变体 + ColorBlend）或本框架 kerr_composite
        String preBloomSpv = null, gaussBlurSpv = null, colorBlendSpv = null, bloomCompositeSpv = null;
        if (postChainMode) {
            preBloomSpv = compileShaderIfChanged(BLOOM_COMPUTE_SHADER, VK_SHADER_STAGE_COMPUTE_BIT, "GENERATE_MIPMAP");
            gaussBlurSpv = compileShaderIfChanged(BLOOM_COMPUTE_SHADER, VK_SHADER_STAGE_COMPUTE_BIT, "GAUSS_BLUR");
            colorBlendSpv = compileShaderIfChanged(COLOR_BLEND_SHADER, VK_SHADER_STAGE_FRAGMENT_BIT);
        } else {
            bloomCompositeSpv = compileShaderIfChanged(BLOOM_COMPOSITE_SHADER, VK_SHADER_STAGE_FRAGMENT_BIT);
        }
        // 直绘模式只编译 BlackHole.frag（省两次 5k 行 GLSL 的驱动编译）；复合模式编译 prepass+composite
        String mainFragSpv = compileShaderIfChanged(directMode ? DIRECT_FRAG : COMPOSITE_FRAG,
                VK_SHADER_STAGE_FRAGMENT_BIT);
        if (!directMode) {
            prepassFragSpvPath = compileShaderIfChanged(PREPASS_FRAG, VK_SHADER_STAGE_FRAGMENT_BIT);
        }
        var graphQueue = new Queue.GraphicsQueue(vkCtx, 0);

        // NPGS 原装 6 套天空盒（加载约定与 NPGS 一致：不翻转、R8G8B8A8_UNORM）
        for (int i = 0; i < SKYBOX_DIRS.length; i++) {
            skyboxes[i] = new CubeTexture(vkCtx, graphQueue, NPGS_SKYBOX_DIR + SKYBOX_DIRS[i], ".jpg");
        }
        // NPGS 原版贴图盘（其 Assets/Textures/Disk 集合中的写实盘面图，iImageTexture/b9）
        diskTexture = new Texture2D(vkCtx, graphQueue, NPGS_SKYBOX_DIR + "Disk/R.jpg");

        // 描述符布局：set0 双 UBO；set1 历史(b0)+6 套盒(b1..b6)+prepass 双纹理(b7/b8)+贴图占位(b9)
        argsLayout = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo[]{
                new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 0, 1, VK_SHADER_STAGE_FRAGMENT_BIT),
                new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1, 1, VK_SHADER_STAGE_FRAGMENT_BIT)});
        DescSetLayout.LayoutInfo[] texBindings = new DescSetLayout.LayoutInfo[10];
        for (int b = 0; b < texBindings.length; b++) {
            texBindings[b] = new DescSetLayout.LayoutInfo(
                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, b, 1, VK_SHADER_STAGE_FRAGMENT_BIT);
        }
        texLayout = new DescSetLayout(vkCtx, texBindings);
        bloomTexLayout = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(
                VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 0, 1, VK_SHADER_STAGE_FRAGMENT_BIT));
        // 后处理链布局（仅 postChainMode）：compute 侧 GameArgs 阶段位必须含 COMPUTE
        if (postChainMode) {
            computeArgsLayout = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(
                    VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 0, 1, VK_SHADER_STAGE_COMPUTE_BIT));
            postTexLayout = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo[]{
                    new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 0, 1, VK_SHADER_STAGE_COMPUTE_BIT),
                    new DescSetLayout.LayoutInfo(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, 1, 1, VK_SHADER_STAGE_COMPUTE_BIT)});
            blendTexLayout = new DescSetLayout(vkCtx, new DescSetLayout.LayoutInfo(
                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 0, 2, VK_SHADER_STAGE_FRAGMENT_BIT));
        }

        // TAA 历史（ping-pong）+ prepass 半分辨率双附件（直绘模式 prepass 附件不建）
        SwapChain swapChain = vkCtx.getSwapChain();
        VkExtent2D extent = swapChain.getSwapChainExtent();
        createHistoryResources(vkCtx, graphQueue, extent.width(), extent.height());
        if (!directMode) {
            createPrepassResources(vkCtx, graphQueue, extent.width(), extent.height());
        }
        if (postChainMode) {
            createPostResources(vkCtx, graphQueue, extent.width(), extent.height());
        }

        // UBO（持久映射，内容每帧重写）
        Device device = vkCtx.getDevice();
        for (int i = 0; i < MAX_IN_FLIGHT; i++) {
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

        // 描述符集分配与静态绑定（名字用 npgs- 前缀，与 KerrRender 的 kerr- 系隔离）
        argsDescSets = new DescSet[MAX_IN_FLIGHT];
        prepassArgsDescSets = new DescSet[MAX_IN_FLIGHT];
        texDescSets = new DescSet[MAX_IN_FLIGHT];
        bloomDescSets = new DescSet[MAX_IN_FLIGHT];
        if (postChainMode) {
            computeArgsDescSets = new DescSet[MAX_IN_FLIGHT];
            preBloomDescSets = new DescSet[MAX_IN_FLIGHT];
            gaussBlurDescSets = new DescSet[MAX_IN_FLIGHT];
            blendDescSets = new DescSet[MAX_IN_FLIGHT];
        }
        for (int i = 0; i < MAX_IN_FLIGHT; i++) {
            argsDescSets[i] = vkCtx.getDescAllocator().addDescSet(device, "npgs-args-" + i, argsLayout);
            argsDescSets[i].setBuffer(device, gameArgsUbo[i], GAME_ARGS_SIZE, 0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
            argsDescSets[i].setBuffer(device, bhArgsUbo[i], BH_ARGS_SIZE, 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);

            if (!directMode) {
                prepassArgsDescSets[i] = vkCtx.getDescAllocator().addDescSet(device, "npgs-args-prepass-" + i, argsLayout);
                prepassArgsDescSets[i].setBuffer(device, gameArgsPrepassUbo[i], GAME_ARGS_SIZE, 0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
                prepassArgsDescSets[i].setBuffer(device, bhArgsPrepassUbo[i], BH_ARGS_SIZE, 1, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
            }

            texDescSets[i] = vkCtx.getDescAllocator().addDescSet(device, "npgs-tex-" + i, texLayout);
            for (int b = 0; b < skyboxes.length; b++) {
                texDescSets[i].setImage(device, skyboxes[b].getSampler(),
                        skyboxes[b].getImageView().getVkImageView(),
                        1 + b, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            }
            // b7/b8 prepass 双纹理先绑历史视图防悬空，每帧 render() 重绑
            // （直绘模式恒绑历史视图——BlackHole.frag/直绘路径不采样 prepass 纹理）
            for (int b = 7; b <= 8; b++) {
                texDescSets[i].setImage(device, histSampler, histViews[i][0].getVkImageView(), b,
                        VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            }
            // b9 = NPGS 原版贴图盘（iUseImageDisk 开关在 shader 内门控采样）
            texDescSets[i].setImage(device, diskTexture.getSampler(),
                    diskTexture.getImageView().getVkImageView(), 9,
                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);

            bloomDescSets[i] = vkCtx.getDescAllocator().addDescSet(device, "npgs-bloom-" + i, bloomTexLayout);

            if (postChainMode) {
                computeArgsDescSets[i] = vkCtx.getDescAllocator().addDescSet(device, "npgs-cargs-" + i, computeArgsLayout);
                computeArgsDescSets[i].setBuffer(device, gameArgsUbo[i], GAME_ARGS_SIZE, 0, VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
                preBloomDescSets[i] = vkCtx.getDescAllocator().addDescSet(device, "npgs-prebloom-" + i, postTexLayout);
                gaussBlurDescSets[i] = vkCtx.getDescAllocator().addDescSet(device, "npgs-gaussblur-" + i, postTexLayout);
                blendDescSets[i] = vkCtx.getDescAllocator().addDescSet(device, "npgs-blend-" + i, blendTexLayout);
            }
        }
        if (postChainMode) {
            bindPostStaticDescriptors(vkCtx.getDevice());
        }

        // 管线：主（写历史）+ prepass（复合模式懒建）+ 合成（历史 mip 链→交换链）
        vertSpvPath = vertSpv;
        var vertModule = new ShaderModule(vkCtx, VK_SHADER_STAGE_VERTEX_BIT, vertSpv, null);
        var mainFragModule = new ShaderModule(vkCtx, VK_SHADER_STAGE_FRAGMENT_BIT, mainFragSpv, null);
        var vtxBuffStruct = new EmptyVtxBuffStruct();
        pipeline = new Pipeline(vkCtx, new PipelineBuildInfo(new ShaderModule[]{vertModule, mainFragModule},
                vtxBuffStruct.getVi(), new int[]{HISTORY_FORMAT})
                .setDescSetLayouts(new DescSetLayout[]{argsLayout, texLayout}));
        if (!directMode && engCtx.scene().getKerrParams().prepassEnabled) {
            ensurePrepassPipeline(vkCtx);
        }
        if (postChainMode) {
            // NPGS 原版后处理链：PreBloom/GaussBlur（Bloom.comp 双宏变体 compute）+ Blend（ColorBlend 图形）
            var preBloomModule = new ShaderModule(vkCtx, VK_SHADER_STAGE_COMPUTE_BIT, preBloomSpv, null);
            preBloomPipeline = new ComputePipeline(vkCtx, preBloomModule,
                    new DescSetLayout[]{computeArgsLayout, postTexLayout},
                    new PushConstRange[]{new PushConstRange(VK_SHADER_STAGE_COMPUTE_BIT, 0, 4)});
            preBloomModule.cleanup(vkCtx);
            var gaussBlurModule = new ShaderModule(vkCtx, VK_SHADER_STAGE_COMPUTE_BIT, gaussBlurSpv, null);
            gaussBlurPipeline = new ComputePipeline(vkCtx, gaussBlurModule,
                    new DescSetLayout[]{computeArgsLayout, postTexLayout},
                    new PushConstRange[]{new PushConstRange(VK_SHADER_STAGE_COMPUTE_BIT, 0, 4)});
            gaussBlurModule.cleanup(vkCtx);
            var colorBlendModule = new ShaderModule(vkCtx, VK_SHADER_STAGE_FRAGMENT_BIT, colorBlendSpv, null);
            blendPipeline = new Pipeline(vkCtx, new PipelineBuildInfo(new ShaderModule[]{vertModule, colorBlendModule},
                    vtxBuffStruct.getVi(), new int[]{swapChain.getImageFormat()})
                    .setDescSetLayouts(new DescSetLayout[]{argsLayout, blendTexLayout}));
            colorBlendModule.cleanup(vkCtx);
        } else {
            var bloomCompositeModule = new ShaderModule(vkCtx, VK_SHADER_STAGE_FRAGMENT_BIT, bloomCompositeSpv, null);
            compositePipeline = new Pipeline(vkCtx, new PipelineBuildInfo(new ShaderModule[]{vertModule, bloomCompositeModule},
                    vtxBuffStruct.getVi(), new int[]{swapChain.getImageFormat()})
                    .setDescSetLayouts(new DescSetLayout[]{bloomTexLayout})
                    .setPushConstRanges(new PushConstRange[]{
                            new PushConstRange(VK_SHADER_STAGE_FRAGMENT_BIT, 0, 4)}));
            bloomCompositeModule.cleanup(vkCtx);
        }
        pushConstBuff = MemoryUtil.memAlloc(4);
        vtxBuffStruct.cleanup();
        vertModule.cleanup(vkCtx);
        mainFragModule.cleanup(vkCtx);

        createRenderInfos(extent.width(), extent.height());
    }

    /** 后处理链静态描述符绑定（b1 存储图 / GaussBlur 源目标对；历史相关项每帧动态重绑） */
    private void bindPostStaticDescriptors(Device device) {
        for (int i = 0; i < MAX_IN_FLIGHT; i++) {
            // PreBloom：b0=源纹理（每帧重绑历史）此处先绑历史占位；b1=图集存储图（GENERAL）
            preBloomDescSets[i].setImage(device, histSampler, histViews[i][0].getVkImageView(), 0,
                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            preBloomDescSets[i].setImage(device, 0, preBloomViews[i].getVkImageView(), 1,
                    VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, VK_IMAGE_LAYOUT_GENERAL);
            // GaussBlur：b0=图集采样（PreBloom 输出，含回拷中转），b1=模糊输出存储图（GENERAL）
            gaussBlurDescSets[i].setImage(device, histSampler, preBloomViews[i].getVkImageView(), 0,
                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            gaussBlurDescSets[i].setImage(device, 0, gaussBlurViews[i].getVkImageView(), 1,
                    VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, VK_IMAGE_LAYOUT_GENERAL);
        }
    }

    /**
     * prepass 管线懒建：默认关时跳过启动期驱动编译；GUI 首次开启时现建（此后复用）。
     * 仅复合模式可调（直绘模式 {@code prepassFragSpvPath} 为 null）。
     */
    private void ensurePrepassPipeline(VkCtx vkCtx) {
        if (prepassPipeline != null || directMode) {
            return;
        }
        var vertModule = new ShaderModule(vkCtx, VK_SHADER_STAGE_VERTEX_BIT, vertSpvPath, null);
        var fragModule = new ShaderModule(vkCtx, VK_SHADER_STAGE_FRAGMENT_BIT, prepassFragSpvPath, null);
        var vtxBuffStruct = new EmptyVtxBuffStruct();
        prepassPipeline = new Pipeline(vkCtx, new PipelineBuildInfo(new ShaderModule[]{vertModule, fragModule},
                vtxBuffStruct.getVi(), new int[]{PREPASS_DISTORTION_FORMAT, PREPASS_VOLUMETRIC_FORMAT})
                .setDescSetLayouts(new DescSetLayout[]{argsLayout, texLayout}));
        vtxBuffStruct.cleanup();
        vertModule.cleanup(vkCtx);
        fragModule.cleanup(vkCtx);
    }

    /** 创建 TAA 历史缓冲 + 线性采样器（postChain 模式单 mip——mip 1+ 层永不写入，
     *  ColorBlend 的隐式 LOD 采样会采到未定义内容 = resize 后黑块/闪烁/全黑） */
    private void createHistoryResources(VkCtx vkCtx, Queue graphQueue, int width, int height) {
        histWidth = width;
        histHeight = height;
        // postChain 模式无 mip 链生成（辉光走 NPGS 图集），历史只留基底层级——与 NPGS
        // 自己的单 mip FColorAttachment 一致；kerr_composite 路径（postChain=false）仍需完整 mip 链
        histMipLevels = postChainMode ? 1
                : Integer.numberOfTrailingZeros(Integer.highestOneBit(Math.max(width, height))) + 1;
        histImages = new Image[MAX_IN_FLIGHT][2];
        histViews = new ImageView[MAX_IN_FLIGHT][2];
        try (var stack = MemoryStack.stackPush()) {
            for (int slot = 0; slot < MAX_IN_FLIGHT; slot++) {
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
                for (int slot = 0; slot < MAX_IN_FLIGHT; slot++) {
                    for (int j = 0; j < 2; j++) {
                        // 清零新历史图：VMA 回收显存的内容未定义，其中 NaN/Inf 位型经
                        // BlackHole.frag 首两帧的 0.0*PrevColor=NaN 渗入输出（resize 后黑斑/全黑根因），
                        // 启动期 OS 清零页无此问题。清零后 0*任意=0， NaN 永不进入 TAA 反馈。
                        var clearRange = VkImageSubresourceRange.calloc(stack)
                                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                .baseMipLevel(0)
                                .levelCount(histMipLevels)
                                .baseArrayLayer(0)
                                .layerCount(1);
                        var clearColor = VkClearColorValue.calloc(stack);
                        clearColor.float32(stack.floats(0f, 0f, 0f, 0f));
                        VkUtils.imageBarrier(stack, cmdHandle, histImages[slot][j].getVkImage(),
                                VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                                VK_PIPELINE_STAGE_2_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                                VK_ACCESS_2_NONE, VK_ACCESS_TRANSFER_WRITE_BIT, VK_IMAGE_ASPECT_COLOR_BIT);
                        vkCmdClearColorImage(cmdHandle, histImages[slot][j].getVkImage(),
                                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, clearColor, clearRange);
                        boolean asRead = j == 0;
                        int newLayout = asRead ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                                : VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
                        long dstStage = asRead ? VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT
                                : VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT;
                        long dstAccess = asRead ? VK_ACCESS_2_SHADER_READ_BIT
                                : VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT;
                        VkUtils.imageBarrier(stack, cmdHandle, histImages[slot][j].getVkImage(),
                                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, newLayout,
                                VK_PIPELINE_STAGE_TRANSFER_BIT, dstStage,
                                VK_ACCESS_TRANSFER_WRITE_BIT, dstAccess, VK_IMAGE_ASPECT_COLOR_BIT);
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
                    .maxLod(postChainMode ? 0.0f : VK_LOD_CLAMP_NONE);
            long[] lp = new long[1];
            vkCheck(vkCreateSampler(device.getVkDevice(), samplerInfo, null, lp),
                    "Failed to create NPGS history sampler");
            histSampler = lp[0];
        }
    }

    /** 创建/重建 prepass 半分辨率双附件（扭曲场 32F + 体积色 16F，NPGS 原版格式组合） */
    private void createPrepassResources(VkCtx vkCtx, Queue graphQueue, int width, int height) {
        prepassDistortionImages = new Image[MAX_IN_FLIGHT];
        prepassDistortionViews = new ImageView[MAX_IN_FLIGHT];
        prepassVolumetricImages = new Image[MAX_IN_FLIGHT];
        prepassVolumetricViews = new ImageView[MAX_IN_FLIGHT];
        int halfW = Math.max(1, width / 2);
        int halfH = Math.max(1, height / 2);
        for (int slot = 0; slot < MAX_IN_FLIGHT; slot++) {
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
        // 初始布局 COLOR（首个用途是 prepass 写入；每帧 prepass 前另有 UNDEFINED→COLOR barrier）
        try (var stack = MemoryStack.stackPush()) {
            var cmdPool = new CmdPool(vkCtx, graphQueue.getQueueFamilyIndex(), false);
            var cmdBuffer = new CmdBuffer(vkCtx, cmdPool, true, true);
            try {
                cmdBuffer.beginRecording();
                VkCommandBuffer cmdHandle = cmdBuffer.getVkCommandBuffer();
                for (int slot = 0; slot < MAX_IN_FLIGHT; slot++) {
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

        // NEAREST 点采样器（NPGS composite 全程 texelFetch，注释明确要求 Nearest）
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
                    "Failed to create NPGS prepass sampler");
            prepassSampler = lp[0];
        }
    }

    /**
     * 创建/重建后处理链双附件（PreBloom 图集 + GaussBlur 输出，全分辨率 R16F×4，
     * SAMPLED|STORAGE；仅 postChainMode）。初始布局 UNDEFINED——每帧链首有
     * UNDEFINED→GENERAL barrier（内容无需跨帧保留），布局流转见 render()。
     */
    private void createPostResources(VkCtx vkCtx, Queue graphQueue, int width, int height) {
        preBloomImages = new Image[MAX_IN_FLIGHT];
        preBloomViews = new ImageView[MAX_IN_FLIGHT];
        gaussBlurImages = new Image[MAX_IN_FLIGHT];
        gaussBlurViews = new ImageView[MAX_IN_FLIGHT];
        for (int slot = 0; slot < MAX_IN_FLIGHT; slot++) {
            preBloomImages[slot] = new Image(vkCtx, new Image.ImageData()
                    .width(width).height(height)
                    .format(HISTORY_FORMAT)
                    .usage(VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_STORAGE_BIT
                            | VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT));
            preBloomViews[slot] = new ImageView(vkCtx.getDevice(), preBloomImages[slot].getVkImage(),
                    new ImageView.ImageViewData()
                            .format(HISTORY_FORMAT)
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT),
                    false);
            gaussBlurImages[slot] = new Image(vkCtx, new Image.ImageData()
                    .width(width).height(height)
                    .format(HISTORY_FORMAT)
                    .usage(VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_STORAGE_BIT
                            | VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT));
            gaussBlurViews[slot] = new ImageView(vkCtx.getDevice(), gaussBlurImages[slot].getVkImage(),
                    new ImageView.ImageViewData()
                            .format(HISTORY_FORMAT)
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT),
                    false);
        }
        // resize 重建后重绑静态存储图绑定（init 阶段描述符集未分配，由 init 尾部统一绑定）
        if (preBloomDescSets != null) {
            bindPostStaticDescriptors(vkCtx.getDevice());
        }
    }

    /** 释放后处理链双附件 */
    private void cleanupPostResources(VkCtx vkCtx) {
        if (preBloomViews != null) {
            for (int slot = 0; slot < preBloomViews.length; slot++) {
                if (preBloomViews[slot] != null) {
                    preBloomViews[slot].cleanup(vkCtx.getDevice());
                }
                if (preBloomImages != null && preBloomImages[slot] != null) {
                    preBloomImages[slot].cleanup(vkCtx);
                }
                if (gaussBlurViews != null && gaussBlurViews[slot] != null) {
                    gaussBlurViews[slot].cleanup(vkCtx.getDevice());
                }
                if (gaussBlurImages != null && gaussBlurImages[slot] != null) {
                    gaussBlurImages[slot].cleanup(vkCtx);
                }
            }
            preBloomViews = null;
            preBloomImages = null;
            gaussBlurViews = null;
            gaussBlurImages = null;
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

    /** 创建/重建三个 pass 的动态渲染信息（全部堆分配，与 renderInfo 同生命周期） */
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
        if (attInfoPrepass != null) {
            attInfoPrepass.free();
        }
        if (prepassRenderInfo != null) {
            prepassRenderInfo.free();
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
        for (int i = 0; i < MAX_IN_FLIGHT; i++) {
            if (argsDescSets != null) {
                vkCtx.getDescAllocator().freeDescSet(device, "npgs-args-" + i);
                if (!directMode) {
                    vkCtx.getDescAllocator().freeDescSet(device, "npgs-args-prepass-" + i);
                }
                vkCtx.getDescAllocator().freeDescSet(device, "npgs-tex-" + i);
                vkCtx.getDescAllocator().freeDescSet(device, "npgs-bloom-" + i);
                if (postChainMode) {
                    vkCtx.getDescAllocator().freeDescSet(device, "npgs-cargs-" + i);
                    vkCtx.getDescAllocator().freeDescSet(device, "npgs-prebloom-" + i);
                    vkCtx.getDescAllocator().freeDescSet(device, "npgs-gaussblur-" + i);
                    vkCtx.getDescAllocator().freeDescSet(device, "npgs-blend-" + i);
                }
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
        if (computeArgsLayout != null) {
            computeArgsLayout.cleanup(vkCtx);
            computeArgsLayout = null;
        }
        if (postTexLayout != null) {
            postTexLayout.cleanup(vkCtx);
            postTexLayout = null;
        }
        if (blendTexLayout != null) {
            blendTexLayout.cleanup(vkCtx);
            blendTexLayout = null;
        }
        computeArgsDescSets = null;
        preBloomDescSets = null;
        gaussBlurDescSets = null;
        blendDescSets = null;
        for (int i = 0; i < skyboxes.length; i++) {
            if (skyboxes[i] != null) {
                skyboxes[i].cleanup(vkCtx);
                skyboxes[i] = null;
            }
        }
        if (diskTexture != null) {
            diskTexture.cleanup(vkCtx);
            diskTexture = null;
        }
        cleanupHistoryResources(vkCtx);
        cleanupPrepassResources(vkCtx);
        cleanupPostResources(vkCtx);
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
        if (preBloomPipeline != null) {
            preBloomPipeline.cleanup(vkCtx);
            preBloomPipeline = null;
        }
        if (gaussBlurPipeline != null) {
            gaussBlurPipeline.cleanup(vkCtx);
            gaussBlurPipeline = null;
        }
        if (blendPipeline != null) {
            blendPipeline.cleanup(vkCtx);
            blendPipeline = null;
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

    /** 释放历史图与视图（含历史采样器——resize 每次重建都会新建采样器，不释放则逐次泄漏） */
    private void cleanupHistoryResources(VkCtx vkCtx) {
        if (histSampler != 0) {
            vkDestroySampler(vkCtx.getDevice().getVkDevice(), histSampler, null);
            histSampler = 0;
        }
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
     * 执行一帧 NPGS 原版渲染：[Pass P prepass] → Pass A 写 TAA 历史 → 布局翻转/mip 链
     * → Pass B 合成到交换链（直绘模式无 Pass P，Pass A 换 BlackHole.frag 管线）。
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
            var geodesic = engCtx.scene().getGeodesic();
            // 宇宙符号双向交换：积分子步穿越翻转 / deactivate 复位 → GUI/UBO；GUI 手动改值 → 下帧积分。
            // dirty 消费不受 isActive 门控——否则 G 关闭时的 +1 复位永远到不了 KerrParams，
            // 原版 shader 会带着穿越残留的 -1 在视界外追迹（整屏反宇宙着色）
            if (geodesic.isUniverseSignDirty()) {
                kp.universeSign = (float) geodesic.getUniverseSign();
            }
            if (geodesic.isActive()) {
                geodesic.syncUniverseSign(kp.universeSign);
            }
            boolean runPrepass = !directMode && kp.prepassEnabled;
            // prepass 管线懒建：开关首次打开时现建（宿主侧调用，可安全在帧循环内触发）
            if (runPrepass) {
                ensurePrepassPipeline(vkCtx);
            }

            writeUbos(engCtx, slot, width, height);
            gameArgsUbo[slot].flush(vkCtx);
            bhArgsUbo[slot].flush(vkCtx);
            if (runPrepass) {
                gameArgsPrepassUbo[slot].flush(vkCtx);
                bhArgsPrepassUbo[slot].flush(vkCtx);
            }

            // 历史视图指向：set1.b0 = 上次写入（供混合），Bloom set0 = 本次写入（合成到屏幕）；
            // b7/b8 = 本插槽 prepass 双附件（直绘模式绑历史视图占位即可）
            texDescSets[slot].setImage(device, histSampler, histViews[slot][readIdx].getVkImageView(),
                    0, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            if (runPrepass) {
                texDescSets[slot].setImage(device, prepassSampler, prepassDistortionViews[slot].getVkImageView(),
                        7, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
                texDescSets[slot].setImage(device, prepassSampler, prepassVolumetricViews[slot].getVkImageView(),
                        8, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            }
            bloomDescSets[slot].setImage(device, histSampler, histViews[slot][writeIdx].getVkImageView(),
                    0, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);

            // ===== Pass P：半分辨率扭曲场 + 体积色（复合模式且 prepass 开启时） =====
            if (runPrepass) {
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

            // ===== Pass A：NPGS 原版渲染 → 历史 =====
            attInfoHist.get(0).sType$Default()
                    .imageView(histViews[slot][writeIdx].getVkImageView())
                    .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            renderInfo.pColorAttachments(attInfoHist);
            renderInfo.renderArea().extent().width(width).height(height);

            vkCmdBeginRendering(cmdHandle, renderInfo);
            vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.getVkPipeline());

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

            // 布局翻转 + 合成（二选一）：postChain = NPGS 原版 compute 链；否则本框架 mip 链 + kerr_composite
            long histImage = histImages[slot][writeIdx].getVkImage();
            if (postChainMode) {
                // 历史写完即可采样（下帧 TAA texelFetch + 本帧 PreBloom/Blend 采样）；无 mip 链——
                // 原版链以 Bloom 图集重建辉光，不需要 mip
                VkUtils.imageBarrier(stack, cmdHandle, histImage,
                        VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                        VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT | VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                        VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT, VK_ACCESS_2_SHADER_READ_BIT,
                        VK_IMAGE_ASPECT_COLOR_BIT);
                VkUtils.imageBarrier(stack, cmdHandle, histImages[slot][readIdx].getVkImage(),
                        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                        VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_2_COLOR_ATTACHMENT_OUTPUT_BIT,
                        VK_ACCESS_2_SHADER_READ_BIT, VK_ACCESS_2_COLOR_ATTACHMENT_WRITE_BIT,
                        VK_IMAGE_ASPECT_COLOR_BIT);
                renderPostChain(vkCtx, stack, cmdHandle, slot, writeIdx, width, height, imageIndex, swapChain);
            } else {
                // 布局翻转 + mip 链生成（同 KerrRender：写入槽基底 COLOR→TRANSFER_SRC，
                // 高层批量 UNDEFINED→TRANSFER_DST，blit 逐级减半，尾部全链 → SHADER_READ）
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

                var bloomViewport = VkViewport.calloc(1, stack)
                        .x(0).y(0).height(height).width(width)
                        .minDepth(0.0f).maxDepth(1.0f);
                vkCmdSetViewport(cmdHandle, 0, bloomViewport);
                vkCmdSetScissor(cmdHandle, 0, scissor);

                vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, compositePipeline.getVkPipelineLayout(), 0,
                        stack.longs(bloomDescSets[slot].getVkDescriptorSet()), null);
                pushConstBuff.putFloat(0, kp.bloomStrength);
                vkCmdPushConstants(cmdHandle, compositePipeline.getVkPipelineLayout(),
                        VK_SHADER_STAGE_FRAGMENT_BIT, 0, pushConstBuff);
                vkCmdDraw(cmdHandle, 6, 1, 0, VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
                vkCmdEndRendering(cmdHandle);
            }

            histReadIdx[slot] = writeIdx;
            histFrameCount[slot]++;
        }
    }

    /**
     * NPGS 原版后处理链（postChainMode）：PreBloom（历史→8-octave 图集）→ GaussBlur H
     * （图集→模糊输出）→ copyImage 回拷（模糊结果回图集作垂直趟源，NPGS 同款）→
     * GaussBlur V → Blend（图集重建辉光 + 调色 → 交换链）。
     * compute dispatch 组数 = ceil(尺寸/16)（Bloom.comp local_size 16×16）；
     * push constant = ibHorizontal 布尔（PreBloom 的 GENERATE_MIPMAP 路径不读，占位推 0）。
     */
    private void renderPostChain(VkCtx vkCtx, MemoryStack stack, VkCommandBuffer cmdHandle, int slot, int writeIdx,
                                 int width, int height, int imageIndex, SwapChain swapChain) {
        Device device = vkCtx.getDevice();
        int dispatchX = (width + 15) / 16;
        int dispatchY = (height + 15) / 16;
        long histView = histViews[slot][writeIdx].getVkImageView();

        // 每帧动态重绑：PreBloom 源 = 本帧历史；Blend 数组 = [本帧历史, 模糊图集]
        preBloomDescSets[slot].setImage(device, histSampler, histView, 0,
                VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        blendDescSets[slot].setImageArray(device, new long[]{histSampler, histSampler},
                new long[]{histView, gaussBlurViews[slot].getVkImageView()}, 0,
                VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);

        // ---- PreBloom：历史 → 8-octave 图集（GENERAL 存储） ----
        VkUtils.imageBarrier(stack, cmdHandle, preBloomImages[slot].getVkImage(),
                VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_GENERAL,
                VK_PIPELINE_STAGE_2_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                VK_ACCESS_2_NONE, VK_ACCESS_2_SHADER_WRITE_BIT, VK_IMAGE_ASPECT_COLOR_BIT);
        vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_COMPUTE, preBloomPipeline.getVkPipeline());
        vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_COMPUTE, preBloomPipeline.getVkPipelineLayout(), 0,
                stack.longs(computeArgsDescSets[slot].getVkDescriptorSet(), preBloomDescSets[slot].getVkDescriptorSet()),
                null);
        pushConstBuff.putInt(0, 0);
        vkCmdPushConstants(cmdHandle, preBloomPipeline.getVkPipelineLayout(),
                VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstBuff);
        vkCmdDispatch(cmdHandle, dispatchX, dispatchY, 1);
        VkUtils.imageBarrier(stack, cmdHandle, preBloomImages[slot].getVkImage(),
                VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                VK_ACCESS_2_SHADER_WRITE_BIT, VK_ACCESS_2_SHADER_READ_BIT, VK_IMAGE_ASPECT_COLOR_BIT);

        // ---- GaussBlur 水平趟：图集 → 模糊输出 ----
        VkUtils.imageBarrier(stack, cmdHandle, gaussBlurImages[slot].getVkImage(),
                VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_GENERAL,
                VK_PIPELINE_STAGE_2_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                VK_ACCESS_2_NONE, VK_ACCESS_2_SHADER_WRITE_BIT, VK_IMAGE_ASPECT_COLOR_BIT);
        vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_COMPUTE, gaussBlurPipeline.getVkPipeline());
        vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_COMPUTE, gaussBlurPipeline.getVkPipelineLayout(), 0,
                stack.longs(computeArgsDescSets[slot].getVkDescriptorSet(), gaussBlurDescSets[slot].getVkDescriptorSet()),
                null);
        pushConstBuff.putInt(0, 1);
        vkCmdPushConstants(cmdHandle, gaussBlurPipeline.getVkPipelineLayout(),
                VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstBuff);
        vkCmdDispatch(cmdHandle, dispatchX, dispatchY, 1);

        // ---- 回拷（NPGS 同款）：水平结果 GaussBlur → PreBloom（垂直趟的源） ----
        VkUtils.imageBarrier(stack, cmdHandle, gaussBlurImages[slot].getVkImage(),
                VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_ACCESS_2_SHADER_WRITE_BIT, VK_ACCESS_TRANSFER_READ_BIT, VK_IMAGE_ASPECT_COLOR_BIT);
        VkUtils.imageBarrier(stack, cmdHandle, preBloomImages[slot].getVkImage(),
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_ACCESS_2_SHADER_READ_BIT, VK_ACCESS_TRANSFER_WRITE_BIT, VK_IMAGE_ASPECT_COLOR_BIT);
        var copyRegion = VkImageCopy.calloc(1, stack);
        copyRegion.srcSubresource(it -> it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .mipLevel(0).baseArrayLayer(0).layerCount(1));
        copyRegion.dstSubresource(it -> it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .mipLevel(0).baseArrayLayer(0).layerCount(1));
        copyRegion.srcOffset(it -> it.set(0, 0, 0));
        copyRegion.dstOffset(it -> it.set(0, 0, 0));
        copyRegion.extent(it -> it.width(width).height(height).depth(1));
        vkCmdCopyImage(cmdHandle, gaussBlurImages[slot].getVkImage(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                preBloomImages[slot].getVkImage(), VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, copyRegion);
        VkUtils.imageBarrier(stack, cmdHandle, preBloomImages[slot].getVkImage(),
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_2_SHADER_READ_BIT, VK_IMAGE_ASPECT_COLOR_BIT);
        VkUtils.imageBarrier(stack, cmdHandle, gaussBlurImages[slot].getVkImage(),
                VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, VK_IMAGE_LAYOUT_GENERAL,
                VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT,
                VK_ACCESS_TRANSFER_READ_BIT, VK_ACCESS_2_SHADER_WRITE_BIT, VK_IMAGE_ASPECT_COLOR_BIT);

        // ---- GaussBlur 垂直趟（管线/描述符已绑定，仅翻 push constant） ----
        pushConstBuff.putInt(0, 0);
        vkCmdPushConstants(cmdHandle, gaussBlurPipeline.getVkPipelineLayout(),
                VK_SHADER_STAGE_COMPUTE_BIT, 0, pushConstBuff);
        vkCmdDispatch(cmdHandle, dispatchX, dispatchY, 1);
        VkUtils.imageBarrier(stack, cmdHandle, gaussBlurImages[slot].getVkImage(),
                VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_2_FRAGMENT_SHADER_BIT,
                VK_ACCESS_2_SHADER_WRITE_BIT, VK_ACCESS_2_SHADER_READ_BIT, VK_IMAGE_ASPECT_COLOR_BIT);

        // ---- Blend（图形）：图集重建辉光 + ColorBlend 调色链 → 交换链 ----
        attInfoBloom.get(0).sType$Default()
                .imageView(swapChain.getImageView(imageIndex).getVkImageView())
                .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .loadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
        bloomRenderInfo.pColorAttachments(attInfoBloom);
        bloomRenderInfo.renderArea().extent().width(width).height(height);

        vkCmdBeginRendering(cmdHandle, bloomRenderInfo);
        vkCmdBindPipeline(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, blendPipeline.getVkPipeline());
        var bloomViewport = VkViewport.calloc(1, stack)
                .x(0).y(0).height(height).width(width)
                .minDepth(0.0f).maxDepth(1.0f);
        vkCmdSetViewport(cmdHandle, 0, bloomViewport);
        var blendScissor = VkRect2D.calloc(1, stack)
                .extent(it -> it.width(width).height(height))
                .offset(it -> it.x(0).y(0));
        vkCmdSetScissor(cmdHandle, 0, blendScissor);
        vkCmdBindDescriptorSets(cmdHandle, VK_PIPELINE_BIND_POINT_GRAPHICS, blendPipeline.getVkPipelineLayout(), 0,
                stack.longs(argsDescSets[slot].getVkDescriptorSet(), blendDescSets[slot].getVkDescriptorSet()), null);
        vkCmdDraw(cmdHandle, 6, 1, 0, VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
        vkCmdEndRendering(cmdHandle);
    }

    /** 窗口尺寸变化：重建历史缓冲与渲染信息，重绑 b9 占位（直绘模式无 prepass 附件） */
    public void resize(VkCtx vkCtx, int width, int height) {
        AppLog.infof("NPGS resize -> %dx%d (postChain=%b direct=%b)", width, height, postChainMode, directMode);
        createRenderInfos(width, height);
        cleanupHistoryResources(vkCtx);
        var graphQueue = new Queue.GraphicsQueue(vkCtx, 0);
        createHistoryResources(vkCtx, graphQueue, width, height);
        if (!directMode) {
            cleanupPrepassResources(vkCtx);
            createPrepassResources(vkCtx, graphQueue, width, height);
        }
        if (postChainMode) {
            cleanupPostResources(vkCtx);
            createPostResources(vkCtx, graphQueue, width, height);
        }
        histReadIdx = new int[]{0, 0};
        Arrays.fill(histFrameCount, 0);
        Device device = vkCtx.getDevice();
        for (int i = 0; i < MAX_IN_FLIGHT; i++) {
            for (int b = 7; b <= 8; b++) {
                texDescSets[i].setImage(device, histSampler, histViews[i][0].getVkImageView(), b,
                        VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            }
            texDescSets[i].setImage(device, diskTexture.getSampler(),
                    diskTexture.getImageView().getVkImageView(), 9,
                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
        }
    }

    /**
     * 打包两个 UBO。std140 布局自然对齐无填充，字段顺序与 NPGS BlackHoleArgs 声明严格一致；
     * 末尾 3 个本项目扩展字段（iNoiseLut/iDiskScatter/iDiskAmbient）NPGS 声明不含、自然忽略，
     * 保留写入以维持与 KerrRender 的缓冲布局一致。仅 iPrepass 语义回到 NPGS 原值。
     */
    private void writeUbos(EngCtx engCtx, int slot, int width, int height) {
        Scene scene = engCtx.scene();
        Camera camera = scene.getCamera();
        var engCfg = EngCfg.getInstance();
        float animTime = (float) ((System.nanoTime() - startTime) / 1_000_000_000.0);

        // ---- GameArgs：vec2 分辨率 + fov(弧度) + 时间组（水平 FOV 约定同 KerrRender） ----
        float halfTanY = (float) Math.tan(engCfg.getFov() * 0.5);
        float fovHorizontalRad = (float) (2.0 * Math.atan(halfTanY * width / height));
        ByteBuffer game = MemoryUtil.memByteBuffer(gameMapped[slot], GAME_ARGS_SIZE);
        game.putFloat(width).putFloat(height);
        game.putFloat(fovHorizontalRad);                          // iFovRadians（水平，勿再 tan）
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
        camToWorldRot.get(bh);                                    // iInverseCamRot=相机→世界
        bh.position(64);

        if (geodesicActive) {
            // 观者模式 -1 约定：位置直接给 KS 世界坐标（NPGS 原生约定，着色器跳过相机系变换）
            Vector3f p = geodesic.getPosition(tmpVec);
            bh.putFloat(p.x).putFloat(p.y).putFloat(p.z).putFloat(0);
        } else {
            view.transformPosition(0, 0, 0, tmpVec);              // = -R·camPos（黑洞在原点）
            bh.putFloat(tmpVec.x).putFloat(tmpVec.y).putFloat(tmpVec.z).putFloat(0);
        }
        putDir(bh, rotView, 0, 1, 0);                             // 盘法向（自旋轴 +Y，相机系）
        putDir(bh, rotView, 0, 0, 1);                             // 盘切向
        if (geodesicActive) {
            // 观者模式 -1：iCameraVelocity 不读取置零；iU_up=四速度，ie1/2/3_up=折叠头转的输运标架
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
            for (int i = 0; i < 5; i++) {                         // 相机速度 + 四维标架（静态观者全零，
                bh.putFloat(0).putFloat(0).putFloat(0).putFloat(0);   // 5 个 vec4 槽一个不能少）
            }
        }
        // 11 个 int 开关（顺序同 NPGS 声明；原版专有项读 KerrParams 的 NPGS 段——GUI 面板数据源）
        var kp = scene.getKerrParams();
        float spin = kp.spin;
        bh.putInt(geodesic.isOutgoingPatch() ? 1 : 0)  // iCamDataCoordisOutgoing（CheckAndSwitchCoords 换系后同步）
          .putInt(kp.debugMode)  // iDEBUG（0=关，1..4=调试视图）
          .putInt(!directMode && kp.prepassEnabled ? 1 : 0)  // iPrepass（NPGS 原值：1=边缘感知合成，0=全分辨率重迹）
          .putInt(kp.whitehole ? 1 : 0)      // iWhitehole（最大延拓）
          .putInt(kp.universeIndex)          // iInWhichUniverse（宇宙变体选层 0..2）
          .putInt(kp.gridMode)               // iGrid（0=关，1/2=两种时空网格）
          .putInt(kp.enableHeatHaze ? 1 : 0)  // iEnableHeatHaze
          .putInt(kp.shadowCulling ? 1 : 0)  // iEnableShadowCulling
          .putInt(geodesicActive ? -1 : 0)      // iObserverMode（-1=外传四维标架；0=静态观者）
          .putInt(kp.polarization ? 1 : 0)   // iPolarization（偏振输出）
          .putInt(kp.useImageDisk ? 1 : 0);  // iUseImageDisk（贴图盘）
        // 37 个 float（顺序同 NPGS 声明）

        // 盘时间推进（c·s/Rs）：与 KerrRender 同一套累计器（KerrParams 持有，模式/重建后相位连续）
        long now = System.nanoTime();
        float dt = Math.min(0.1f, Math.max(0.0f, (now - lastUboNanos) / 1_000_000_000.0f));
        lastUboNanos = now;
        if (!scene.isTimePaused()) {
            kp.diskTimeCsRs += dt * kp.timeScale * BH_TIME_SCALE;
        }

        bh.putFloat(kp.quality)                  // iQuality
          .putFloat(kp.universeSign)             // iUniverseSign（相机所在空间侧 +1/-1，光线宇宙符号种子）
          .putFloat(kp.diskTimeCsRs)             // iBlackHoleTime（c·s/Rs 单位）
          .putFloat(BH_MASS_SOL)                 // iBlackHoleMassSol
          .putFloat(spin)                        // iSpin
          .putFloat(kp.qStar)                    // iQ
          .putFloat(kp.mu)                       // iMu
          .putFloat(kp.accretionRate)            // iAccretionRate
          .putFloat(kp.backShiftMax)             // iBackShiftMax
          .putFloat(kp.densestarRadiusRs)        // iDensestarsurfaceR（0=关；原版独有致密星表面）
          .putFloat(kp.densestarBlackbodyExp)    // iDensestarBlackbodyIntensityExponent
          .putFloat(kp.densestarShiftColorExp)   // iDensestarRedShiftColorExponent
          .putFloat(kp.densestarShiftBrightExp)  // iDensestarRedShiftIntensityExponent
          .putFloat(kp.densestarBrightmut)       // iDensestarBrightmut
          .putFloat(KerrParams.iscoInnerRadiusRs(spin, kp.qStar))  // iInterRadiusRs（ISCO(a*,Q*)）
          .putFloat(kp.outerRadiusRs)            // iOuterRadiusRs
          .putFloat(kp.thinRs)                   // iThinRs
          .putFloat(kp.hopper)                   // iHopper
          .putFloat(kp.brightmut)                // iBrightmut
          .putFloat(kp.darkmut)                  // iDarkmut
          .putFloat(kp.reddening)                // iReddening
          .putFloat(kp.saturation)               // iSaturation
          .putFloat(kp.blackbodyIntensityExponent)  // iBlackbodyIntensityExponent
          .putFloat(kp.redShiftColorExponent)    // iRedShiftColorExponent
          .putFloat(kp.redShiftIntensityExponent)  // iRedShiftIntensityExponent
          .putFloat(kp.imageRotationSpeed)       // iImageRotationSpeed（贴图盘自转角速度）
          .putFloat(kp.polarizationAngle)        // iPolarizationAngle（偏振片角度）
          .putFloat(kp.heatHaze)                 // iHeatHaze
          .putFloat(kp.backgroundBrightmut)      // iBackgroundBrightmut
          .putFloat(kp.photonRingBoost)          // iPhotonRingBoost
          .putFloat(kp.photonRingColorTempBoost) // iPhotonRingColorTempBoost
          .putFloat(kp.boostRot)                 // iBoostRot
          .putFloat(kp.jetRedShiftIntensityExponent)  // iJetRedShiftIntensityExponent
          .putFloat(kp.jetBrightmut)             // iJetBrightmut
          .putFloat(kp.jetSaturation)            // iJetSaturation
          .putFloat(kp.jetShiftMax)              // iJetShiftMax
          .putFloat(histFrameCount[slot] < 2 ? 1.0f
                  : prevViewPerSlot[slot].equals(view) ? 0.06f : 1.0f)  // iBlendWeight（前 2 帧全量覆盖）
          .putFloat(kp.noiseLutEnabled ? 1.0f : 0.0f)            // ↓ NPGS 声明不含，自然忽略
          .putFloat(kp.diskScatter)
          .putFloat(kp.diskAmbient);
        prevViewPerSlot[slot].set(view);

        // ---- prepass UBO：GameArgs 分辨率减半 + iPrepass=1；BlackHoleArgs 字节复制主拷贝后仅改 iPrepass ----
        if (!directMode && kp.prepassEnabled) {
            int pw = Math.max(1, width / 2);
            int ph = Math.max(1, height / 2);
            ByteBuffer gamePre = MemoryUtil.memByteBuffer(gamePreMapped[slot], GAME_ARGS_SIZE);
            gamePre.putFloat(pw).putFloat(ph);
            gamePre.putFloat(fovHorizontalRad)
                    .putFloat(animTime)
                    .putFloat(animTime)
                    .putFloat(1.0f / 60.0f)
                    .putFloat(engCfg.getTimeRate());
            MemoryUtil.memCopy(bhMapped[slot], bhPreMapped[slot], BH_ARGS_SIZE);
            ByteBuffer bhPre = MemoryUtil.memByteBuffer(bhPreMapped[slot], BH_ARGS_SIZE);
            bhPre.putInt(BH_ARGS_IPREPASS_OFFSET, 1);
        }
    }

    /** 方向向量经相机系旋转后写入 UBO（vec4，w=0） */
    private void putDir(ByteBuffer bh, Matrix4f rot, float x, float y, float z) {
        Vector3f v = rot.transformDirection(tmpDir.set(x, y, z));
        bh.putFloat(v.x).putFloat(v.y).putFloat(v.z).putFloat(0);
    }
}
