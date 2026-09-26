# 叠加式 GUI（Dear ImGui）显示模拟数据 实施计划

> **状态(2026-08-31):Phase 0 ~ Phase 4 已实现,运行时验收通过(用户实测 + 30s 冒烟无验证层报错)。** 落地与计划的差异:
> - `GuiRender` 改为无参构造 + `init(vkCtx, engCtx, colorFormat)` 两段式（Render 构造期
>   创建对象、init() 建 ImGui 上下文/字体/管线），与 BlackHoleRender 的两段式对齐;
> - 顶点输入结构未单独建 GuiVtxBuffStruct 类，内联为 GuiRender.createVertexInput();
> - clipRect 接收对象用 `ImVec4`（imgui-java API），texID 直接来自
>   `getCmdListCmdBufferTextureId`（字体 descSet 句柄）;
> - Phase 2 面板落地为 `Main.renderTelemetry()`（相机/测地线/渲染/场景四组，全只读,
>   FPS 复用 frameAccum* 累计器）;F1 显隐在 `input()` 最前处理（优先于键盘抢占）,
>   隐藏时仍跑 ImGui 空帧维持状态机;
> - Phase 3 落地:`handleGui()` 中视角捕获（lookCaptured）期间不喂鼠标事件且隐藏面板;
>   `guiMouseHover`（= wantCaptureMouse）为 true 时挡住右键切捕获（防拖窗口误触）;
>   键盘抢占维持 handleGui 返回值早退不变;
> - Phase 4-1 落地:面板尾部 Controls 区四个滑条（温度/时间流速/推力/v0）,
>   GeodesicIntegrator 新增绝对值 setter（钳制范围同快捷键路径）,与快捷键并行生效;
> - Phase 4-2 跳过:面板标签改用英文（默认字体无 CJK 字形,中文显示为问号）,无需中文字体;
> - Phase 4-3 落地:Bloom 合成管线颜色格式改用 `SwapChain.getImageFormat()`（修复与
>   B8G8R8A8_UNORM 附件不匹配的 VUID 06580 隐患;画面行为不变——附件实际格式本就是 UNORM）,
>   COLOR_FORMAT 常量删除;
> - Phase 4-4（GuiTexture 回显）未做,无需求。
> **追加(2026-08-31 晚):**
> - 面板在视角捕获期间照常显示（显隐统一归 F1;捕获时仍不喂鼠标事件,面板只读）;
>   新增右上角操作说明面板 renderHelp()(键位两列对齐,随 F1 显隐);
> - β/γ 希腊字母改 ASCII 拼写（默认字体无希腊字形）;
> - **圆轨道倾角可调**:initializeCircularOrbit 的轨道面法线参数化为
>   n̂ = e1·cos(tilt) + (e1×r̂)·sin(tilt)（e1=ŷ⊥,tilt=0 贴吸积盘平面,90=原子午面[默认]）,
>   GUI 滑条 Orbit tilt（0..90°,R 键初始化时生效）;校验:tilt 0/45/90 @r=6Rs
>   5 圈 maxDrift=0.00000、E=0.96225 恒定,GeoTest 默认路径回归不变。
>
> **补记(2026-09-26):** 正文所述落点已随主类拆分(9e31d80,2026-09-06)迁移——面板绘制在
> `eng/graph/gui/Panels.java`(renderTelemetry/renderControlPanel/renderKerrPanel/renderKeys/
> renderOriginIndicator 五个面板),F1 显隐与 GUI 帧构建/键盘抢占移入 `eng/InputController.java`
> (委托 Panels);正文中"Main.renderTelemetry()/Main.input()"等描述按当时落点阅读。
> 面板后续扩展:克尔面板出自 kerr 计划 Phase 2.5(4dccb6b),原点指示器出自 8861835。
> Phase 4-3 的 COLOR_FORMAT 删除复核在位(BlackHoleRender 无此常量),Phase 4-2/4-4 仍为跳过态。

> **原始计划（2026-08-30 评审稿）如下。**
> 参照 `D:\CodingSpace\IdeaProjects\vulkanbook\booksamples\appendix-01`（GUI 渲染引擎侧）与
> `chapter-12`（应用侧驱动 + 输入抢占）的模式，为 blackhole 增加叠加在黑洞画面上的
> 只读数据面板：相机位置/速度、测地线状态（r、β、γ、E）、渲染性能等。
> 第一版只做**只读显示**，交互控件（滑条调参）留作 Phase 4。

> **目标:** 在 Vulkan 1.3 动态渲染管线下，以第三个渲染 pass（Bloom 合成之后）叠加
> Dear ImGui 界面，实时显示模拟数据；与现有视角捕获（右键 grabCursor）互斥规则明确；
> 不引入 render pass/framebuffer（沿用本项目全动态渲染风格）。

---

## 1. 参考实现摘要（appendix-01 / chapter-12）

appendix-01 的 GUI 架构核心决策：**imgui-java 只当"UI 状态库 + draw data 生成器"，
不用它自带的 lwjgl-glfw/vulkan 后端，Vulkan 渲染完全自写**（一条专用管线 + 动态渲染）。

| 职责 | 参考文件 | 说明 |
|---|---|---|
| Vulkan 渲染 | `appendix-01/.../eng/graph/gui/GuiRender.java` | 管线、字体纹理、每帧顶点/索引缓冲、draw data 遍历绘制 |
| 顶点结构 | `appendix-01/.../eng/graph/gui/GuiVtxBuffStruct.java` | pos(vec2)+uv(vec2)+color(R8G8B8A8) 共 20B |
| 着色器 | `appendix-01/resources/shaders/gui_vtx.glsl` / `gui_frg.glsl` | push constant 传 scale；片元采样字体纹理 |
| 键盘转发 | `appendix-01/.../eng/graph/gui/GuiUtils.java` | GLFW key/char 回调 → ImGuiIO，含 GLFWKey→ImGuiKey 大映射 |
| 应用侧驱动 | `chapter-12/.../Main.java` `handleGui()` | 每帧喂鼠标事件 → newFrame → UI → endFrame → render |
| 引擎接线 | `appendix-01/.../eng/graph/Render.java` | GUI 在所有渲染之后作为最后一个 pass |

关键机制（照抄即可的部分）：

1. **字体纹理即 texID**：`imGuiIO.getFonts().getTexDataAsRGBA32(w,h)` → 上传为
   `VK_FORMAT_R8G8B8A8_SRGB` 纹理 → `fonts.setTexID(descSetHandle)`（把**描述符集句柄**
   当 texID 塞回 ImGui）；绘制时 ImDrawCmd 的 `textureId` 就是 descSet 句柄，
   `vkCmdBindDescriptorSets` 直接绑定它。
2. **管线**：`setUseBlend(true)`（标准 alpha 混合）、push constant 仅 8 字节
   （`scale = (2/w, -2/h)`，顶点着色器 `pos*scale + (-1,1)` 完成 NDC 变换）、
   索引类型 `VK_INDEX_TYPE_UINT16`。
3. **Y 翻转视口**：`viewport.y(height); viewport.height(-height)`——与本项目
   `BlackHoleRender.render()` 中已有的翻转视口写法完全一致
   （`vulkanb/eng/graph/BlackHoleRender.java:499`）。
4. **叠加加载**：GUI pass 的颜色附件 `loadOp = VK_ATTACHMENT_LOAD_OP_LOAD`
   （不清屏，叠在 Bloom 输出上）；同一命令缓冲内上一 pass（Bloom 写交换链）→
   本 pass 按提交顺序隐式同步，无需额外 barrier。
5. **每帧缓冲**：`MAX_IN_FLIGHT` 组顶点/索引 `VkBuffer`（HOST_VISIBLE +
   `VMA_ALLOCATION_CREATE_HOST_ACCESS_SEQUENTIAL_WRITE_BIT`），只增不缩
   （`vertexBufferSize > buffer.getRequestedSize()` 时销毁重建）。
6. **draw data 遍历**：逐 ImDrawList 把 vtx/idx 数据 `ByteBuffer.put` 拼进 staging；
   逐 ImDrawCmd 绑纹理 descSet、由 clipRect 设 scissor、`vkCmdDrawIndexed`
   （用 offsetIdx/offsetVtx 累计跨 list 偏移）。
7. **输入抢占**（chapter-12 模式）：`handleGui()` 返回 `imGuiIO.getWantCaptureKeyboard()`，
   为 true 时应用跳过相机输入；鼠标侧用 `getWantCaptureMouse()` 判断。

---

## 2. blackhole 现状盘点

### 2.1 已具备（可直接复用，无需改动）

| 能力 | 位置 | 与 appendix-01 的兼容性 |
|---|---|---|
| 管线 Alpha 混合开关 | `vulkanb/eng/graph/vk/PipelineBuildInfo.java:117` `setUseBlend` | 同名同语义 |
| 键盘回调注册 | `vulkanb/eng/wnd/KeyboardInput.java:46,103` `addKeyCallBack` / `setCharCallBack` | **签名完全一致**，GuiUtils 可原样移植 |
| 鼠标状态查询 | `vulkanb/eng/wnd/MouseInput.java` `getCurrentPos()` / `isLeftButtonPressed()` / `isRightButtonPressed()` | chapter-12 喂事件模式直接可用 |
| 描述符分配 | `vulkanb/eng/graph/vk/DescAllocator.java:90` `addDescSet(device,id,layout)` / `freeDescSet(device,id)` | 单数签名（appendix-01 是 `addDescSets` 复数），注意 |
| 描述符更新 | `vulkanb/eng/graph/vk/DescSet.java:99` `setImage(device, sampler, view, binding, type)` | 参数顺序不同：blackhole 是 `(device, sampler, view, binding, type)` |
| HOST_VISIBLE 缓冲 | `vulkanb/eng/graph/vk/VkBuffer.java` 构造 + `map/flush/unMap` | 同款 |
| 一次性命令上传 | `vulkanb/eng/graph/vk/CmdBuffer.java:138` `submitAndWait`；`CubeTexture` 的 staging + 一次性 cmd 模式（`CubeTexture.java:79-237`） | 字体纹理上传照此模式 |
| 着色器编译 | `vulkanb/eng/graph/vk/ShaderCompiler.java` `compileShaderIfChanged(glsl, stage)` 返回 spv 路径 | 返回值是 String（appendix-01 为 void） |
| 动态渲染/翻转视口/push constant | `vulkanb/eng/graph/BlackHoleRender.java`（全屏 pass 的完整范本） | GuiRender 结构可对照仿写 |
| VkUtils 常量 | `FLOAT_SIZE` / `MAX_IN_FLIGHT=2` / `VEC2_SIZE` | **缺 `SHORT_LENGTH`** → 用 `Short.BYTES`（=2） |

### 2.2 缺失 / 差异（本计划要补的）

1. **pom 无 imgui-java 依赖**（项目是独立 pom，properties 仅 `lwjgl.version=3.3.4`）。
2. **无 `Texture` / `TextureSampler` / `ImageSrc` 类**（appendix-01 有，blackhole 精简掉了）。
   GUI 字体纹理是本项目唯一一张 2D 纹理，不值得移植整个 TextureCache 体系——
   在 GuiRender 内部用 `Image`/`ImageView` + staging `VkBuffer` + 一次性 cmd 直接实现。
3. **SwapChain 不暴露图像格式**：`Surface.calcSurfaceFormat()`（`Surface.java:69`）实际选择
   `VK_FORMAT_B8G8R8A8_UNORM + SRGB_NONLINEAR`（兜底也是 B8G8R8A8_UNORM）。
   GUI 管线的 `colorFormats` 必须与交换链实际格式一致 → 需给 `SwapChain` 加
   `getImageFormat()` getter（构造时已持有 `surfaceFormat.imageFormat()`，一行改动）。
4. **现存格式隐患（顺带发现，非本计划必改）**：`BlackHoleRender.COLOR_FORMAT`
   （`BlackHoleRender.java:65`）声明为 `R32G32B32A32_SFLOAT`，但 Bloom 管线实际写入的
   交换链图像是 `B8G8R8A8_UNORM`——管线声明与动态渲染附件格式不匹配（VUID
   06580 类），目前驱动宽松未炸。GUI 不复制该常量，一律用交换链真实格式；
   Bloom 管线格式的修正列为 Phase 4 可选加固。
5. **GUI 附件每帧变**：appendix-01 的 `renderInfo` 构造时固化 `dstAttachment`（post pass
   的固定附件）；blackhole 的 GUI 直接写交换链图像，`imageIndex` 每帧不同 →
   必须按本项目 `attInfoBloom` 的模式**每帧 `.imageView(swapChain.getImageView(imageIndex))`**
   （`BlackHoleRender.java:543`），不能照抄 appendix-01 的固化写法。
6. **视角捕获与 GUI 互斥**：现有 `lookCaptured`（右键 grabCursor 隐藏光标，`Main.java:164`）
   下鼠标是虚拟坐标、无可见光标，无法与 GUI 交互 → 需定互斥规则（见 Phase 3）。

### 2.3 帧循环时序（GUI 插入点）

当前 `Engine.run()`（`vulkanb/eng/Engine.java:76`）单线程顺序：

```
pollEvents → gameLogic.input() → window.resetInput() → [UPS] gameLogic.update()
  → [resize] render.resize() → render.render()
```

- **UI 构建**放 `Main.input()` 开头（chapter-12 的 `handleGui` 模式）：喂鼠标/键盘事件 →
  `ImGui.newFrame()` → 画面板 → `ImGui.endFrame()` → `ImGui.render()`。
  只构建 draw data，不碰 Vulkan。
- **UI 绘制**放 `Render.render()` 内 `blackHoleRender.render(...)` 之后、`recordingStop`
  之前：`guiRender.render(vkCtx, cmdBuffer, currentRenderFrame, imageIndex)`。
- **resize**：`Render.resize()` 末尾调 `guiRender.resize(vkCtx)`（更新 DisplaySize +
  重建 renderInfo 尺寸）。
- ImGui context 创建于 `GuiRender` 构造（`Render` 构造期，早于 `gameLogic.init` 与
  首帧 `input()`），时序安全。

### 2.4 数据源映射（Phase 2 面板内容，全部已有 getter，无需改物理/渲染代码）

| 显示项 | 来源 | 备注 |
|---|---|---|
| 相机模式 | `camera.getMode()`（ORBIT/FREE_FLY/GEODESIC） | |
| 相机位置 (x,y,z) | `camera.getPosition()`（Rs 单位） | |
| 到黑洞距离 r | `geodesic.getRadius()`（等价 `|position|`） | 测地模式外用 `position.length()` |
| 速度 β 向量 / \|β\| | `geodesic.getBeta()` / `.length()`（单位 c） | 未激活时 β=0 |
| 洛伦兹因子 γ | `geodesic.getGamma()` | |
| 静态系能量 E | `geodesic.getEnergy()` | 测地模式下应恒定（守恒校验） |
| 时间流速 / 推力 / v0 | `geodesic.getTimeScale()` / `getThrust()` / `getV0()` | |
| 坠落演出状态 | `geodesic.isFalling()` / `getHorizonFade()` | |
| FPS / 平均帧耗时 | `Main.frameAccumMillis/frameAccumCount`（`Main.java:33`）已累计 | 建议挪成静态或经 EngCtx 可达 |
| 分辨率 | `engCtx.window().getWidth/getHeight` | |
| 基础温度 | `BlackHoleRender.BaseTemperature`（public static） | |
| Rs / 盘内/外半径 / fov | `EngCfg.getInstance().getSchwarzschildRadius()` 等 | |

---

## 3. 分阶段实施

### Phase 0：依赖与基础设施

**改动文件：**

| 文件 | 动作 |
|---|---|
| `pom.xml` | 加 imgui-java 依赖（见下） |
| `src/main/java/vulkanb/eng/graph/vk/SwapChain.java` | 加 `getImageFormat()` getter（返回构造时保存的 `surfaceFormat.imageFormat()`） |
| `src/main/java/vulkanb/eng/graph/gui/GuiUtils.java` | 新建：从 appendix-01 原样移植（`getImKey` 映射 + `CharCallBack` + `KeyCallback`，包名改为 `vulkanb.eng.graph.gui`） |
| `resources/shaders/gui_vtx.glsl`、`resources/shaders/gui_frg.glsl` | 新建：从 appendix-01 原样复制（内容见 §1，共约 40 行） |

**pom 依赖**（版本与 vulkanbook 父 pom 对齐，binding 不依赖 LWJGL，与 3.3.4 无耦合；
imgui-java 自带 JNI native，classifier 与项目现有 natives 段平级）：

```xml
<properties>
    ...
    <imgui-java.version>1.92.0</imgui-java.version>
</properties>

<dependency>
    <groupId>io.github.spair</groupId>
    <artifactId>imgui-java-binding</artifactId>
    <version>${imgui-java.version}</version>
</dependency>
<!-- 与现有 natives-windows / natives-linux 段并排 -->
<dependency>
    <groupId>io.github.spair</groupId>
    <artifactId>imgui-java-natives-windows</artifactId>
    <version>${imgui-java.version}</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.github.spair</groupId>
    <artifactId>imgui-java-natives-linux</artifactId>
    <version>${imgui-java.version}</version>
    <scope>runtime</scope>
</dependency>
```

**验收：** `mvn -q compile` 通过；`SwapChain.getImageFormat()` 返回 B8G8R8A8_UNORM。

### Phase 1：GuiRender 最小可用（demo 窗口验证链路）

**新建 `src/main/java/vulkanb/eng/graph/gui/GuiRender.java`**（对照 appendix-01
`GuiRender.java` 仿写，按 blackhole 风格裁剪）：

- 构造（`Render` 构造期调用）：只做轻量字段初始化（缓冲数组、`guiTexturesMap`）。
- `init(VkCtx vkCtx, Queue.GraphicsQueue queue, int colorFormat)`：
  1. `ImGui.createContext()`；`io.setIniFilename(null)`（不落 imgui.ini）；
     `io.setDisplaySize(swapChainExtent)`；`setDisplayFramebufferScale(1,1)`；
     `io.setConfigWindowsMoveFromTitleBarOnly(true)`（可选，防误拖）。
  2. 字体纹理：`io.getFonts().getTexDataAsRGBA32(w,h)` → staging `VkBuffer`
     （SEQUENTIAL_WRITE）→ `Image`（usage=`COLOR_ATTACHMENT` 不需要，用
     `SAMPLED_BIT | TRANSFER_DST_BIT`，format=`VK_FORMAT_R8G8B8A8_SRGB`）→
     `vkCmdCopyBufferToImage` + 布局转 SHADER_READ（一次 cmd，`submitAndWait`，
     整体照 `CubeTexture` 的 staging→copy→transition→fence 流程，是单张 2D 图简化版）。
  3. 采样器：LINEAR/REPEAT（appendix-01 用 REPEAT+1mip，此处一张图无 mip）。
  4. 管线：`gui_vtx/gui_frg` spv（`ShaderCompiler.compileShaderIfChanged`）→
     `PipelineBuildInfo(modules, guiVtxStruct.getVi(), new int[]{colorFormat})`
     `.setPushConstRanges(new PushConstRange(VK_SHADER_STAGE_VERTEX_BIT, 0, VEC2_SIZE))`
     `.setDescSetLayouts(new DescSetLayout[]{fontsDescLayout})`
     `.setUseBlend(true)`；`guiVtxStruct` 内联为本类私有实现（20B 顶点：
     R32G32_SFLOAT pos @0、R32G32_SFLOAT uv @8、R8G8B8A8_UNORM color @16）。
  5. `fontsDescSet = vkCtx.getDescAllocator().addDescSet(device, "gui-fonts", fontsDescLayout)`；
     `fontsDescSet.setImage(device, sampler, fontView, 0, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)`；
     `io.getFonts().setTexID(fontsDescSet.getVkDescriptorSet())`。
  6. 键盘接线：`engCtx.window().getKeyboardInput().setCharCallBack(new GuiUtils.CharCallBack())`
     `.addKeyCallBack(new GuiUtils.KeyCallback())`。
  7. 动态渲染信息：`VkRenderingInfo`/`VkRenderingAttachmentInfo`（loadOp=**LOAD**，
     storeOp=STORE，imageView 每帧设置——见 §2.2 第 5 点差异）。
- `render(VkCtx vkCtx, CmdBuffer cmdBuffer, int currentFrame, int imageIndex)`：
  appendix-01 `render()` 主体照搬（updateBuffers → beginRendering → bindPipeline →
  翻转视口 → bind vtx/idx → pushConstants(scale=(2/w,-2/h)) → 逐 cmdList/cmdBuffer
  绑 descSet+scissor+drawIndexed → endRendering），仅两处本地化：
  - 附件：`attInfoColor.get(0).imageView(swapChain.getImageView(imageIndex).getVkImageView())`
    每帧设置，renderArea 尺寸每帧从 `swapChain.getSwapChainExtent()` 取；
  - `VkUtils.SHORT_LENGTH` → `Short.BYTES`。
- `resize(VkCtx vkCtx)`：`io.setDisplaySize(新 extent)`；重建 renderInfo/attInfo。
- `cleanup(VkCtx vkCtx)`：释放采样器/字体 Image+View/管线/布局/缓冲/渲染信息，
  `vkCtx.getDescAllocator().freeDescSet(device, "gui-fonts")`，最后
  `ImGui.destroyContext()`。

**修改 `vulkanb/eng/graph/Render.java`：**

- 字段 `private final GuiRender guiRender;`
- 构造末尾：`guiRender = new GuiRender(engCtx);`
- `init()`：三个黑洞着色器编译后追加 gui 着色器编译，并
  `guiRender.init(vkCtx, graphQueue, vkCtx.getSwapChain().getImageFormat());`
- `render()`：`blackHoleRender.render(...)` 之后插
  `guiRender.render(vkCtx, cmdBuffer, currentRenderFrame, imageIndex);`
- `resize()`：末尾 `guiRender.resize(vkCtx);`
- `cleanup()`：`blackHoleRender.cleanup` 之后 `guiRender.cleanup(vkCtx);`

**修改 `vulkanb/Main.java`**（临时，Phase 2 替换）：`input()` 开头加 `handleGui()`：

```java
private boolean handleGui(EngCtx engCtx) {
    ImGuiIO io = ImGui.getIO();
    MouseInput mi = engCtx.window().getMouseInput();
    Vector2f pos = mi.getCurrentPos();
    io.addMousePosEvent(pos.x, pos.y);
    io.addMouseButtonEvent(0, mi.isLeftButtonPressed());
    io.addMouseButtonEvent(1, mi.isRightButtonPressed());
    ImGui.newFrame();
    ImGui.showDemoWindow();          // Phase 1 仅验证链路
    ImGui.endFrame();
    ImGui.render();
    return io.getWantCaptureKeyboard();
}
```

`input()` 内：`if (handleGui(engCtx)) return;`（键盘抢占先行；鼠标抢占 Phase 3 精化）。

**验收：**
1. 启动即见 demo 窗口叠加在黑洞画面上，文字清晰、无颜色偏差（字体 sRGB 直读）。
2. 拖动/缩放 demo 窗口正常，`imgui.ini` 不生成。
3. resize 窗口后 GUI 比例正常、无拉伸。
4. 关闭窗口退出无崩溃/无验证层报错（vkValidate=true 下）。

### Phase 2：只读数据面板（替换 demo 窗口）

**修改 `vulkanb/Main.java`**：`handleGui()` 中 `showDemoWindow()` 替换为自绘面板
（建议一个窗口分四组，`ImGui.setNextWindowPos(8, 8, ImGuiCond.FirstUseEver)`）：

```
● Black Hole Telemetry（左上，半透明）
  [相机]  模式 | 位置 (x, y, z) Rs | r
  [测地线]（isActive() 时显示，否则灰字"—"）
          β = (bx, by, bz), |β| = 0.32 c | γ = 1.054
          E = 0.9623（恒定性观察）| 时间流速 ×5.0 | 推力 0.5 | v0 0.30 c
          坠落演出：iFade=0.00（isFalling 时显示）
  [渲染]  FPS（平均）| 帧耗时 ms | 分辨率 | BaseTemperature K
  [场景]  Rs | 盘内/外半径 | fov
```

- 全部用 `ImGui.text()/textFormatted()`（imgui-java 为 `ImGui.text(String.format(...))`），
  无任何输入控件。
- FPS 读数：把 `Main.frameAccumMillis/frameAccumCount` 的周期清零逻辑保留在 `update()`
  的控制台读数处，GUI 侧实时算 `1000.0 * frameAccumCount / frameAccumMillis`，不清零
  （两处共享同一累计器即可，不引入新状态）。
- **F1 切换 GUI 显隐**（`ki.keySinglePress(GLFW_KEY_F1)`，默认显示）；隐藏时
  `handleGui` 仍需 `newFrame/endFrame/render` 空帧（保持 ImGui 内部状态机），
  `GuiRender.render` 里 `imDrawData.ptr == 0` 或顶点数为 0 时自然跳过
  （appendix-01 `updateBuffers` 已处理 0 数据早退）。
- 保留现有控制台周期读数（GUI 与控制台并行一段时间，稳定后再议去留）。

**验收：**
1. 轨道/自由/测地三模式下读数正确：测地模式 r 连续减小、β/γ 实时变化、
   圆轨道（R 键）时 E 读数基本恒定（与 GeoTest 精度一致数量级）。
2. 小键盘 +/- 调温度时面板 BaseTemperature 同步。
3. F1 显隐切换，隐藏后帧耗时不回升（空 draw data 路径开销≈0）。

### Phase 3：输入抢占与视角捕获互斥

**修改 `vulkanb/Main.java` `input()` 的输入分发**（替换 Phase 1 的粗粒度 `return`）：

规则（互斥优先级：**视角捕获 > GUI 交互 > 相机控制**）：

1. `lookCaptured == true`（grabCursor，光标隐藏）：不喂 ImGui 鼠标事件
   （`handleGui` 里跳过 addMousePosEvent/addMouseButtonEvent，并可在 UI 上自动隐藏面板
   ——渲染照常，纯 `ImGui.text` 无交互不受影响；推荐直接隐藏，画面更干净）。
   退出捕获（再按右键）后恢复。
2. `lookCaptured == false` 且 `io.getWantCaptureMouse()`：跳过鼠标→相机的分发
   （右键切捕获、轨道旋转、自由视角旋转都不响应——注意右键单击此时是"拖 GUI 窗口"
   语境，误触进捕获会很难受，必须挡住）。
3. `io.getWantCaptureKeyboard()`：跳过 WASD/G/F/1/2/+/-/Q/E 等全部相机与调参键
   （ImGui 文本输入无控件时一般不抢占键盘，此条主要是防未来加输入框）。

**验收：**
1. 光标悬停 GUI 窗口时：左键拖窗口正常、相机不旋转、右键不触发捕获切换。
2. 光标离开 GUI：相机操作一切如旧；右键进入捕获后 GUI 自动隐藏、鼠标转视角正常。
3. 捕获状态下按 G/W/S 等功能键行为与现状一致（测地线操作不受 GUI 影响）。

### Phase 4（可选扩展，独立小步提交）

1. **交互控件**：温度滑条（`engCfg.getTemperatureMin/Max` 为界，步进 temperatureStep，
   替代/并存小键盘 +/-）、时间流速/推力/v0 滑条（对应 `scaleTime/scaleThrust/adjustV0`
   的连续版，需给 GeodesicIntegrator 加直接 setter——`scaleTime` 是乘法步进，
   GUI 滑条适合绝对值 `setTimeScale(double)`，新增 setter 钳制范围同现有）。
2. **中文字形**：`io.getFonts().addFontFromFileTTF("resources/fonts/xxx.ttf", 16,
   null, io.getFonts().getGlyphRangesChineseFull())` + 重建字体纹理（imgui-java
   `addFontFromFileTTF` 需在 createContext 后、首次 getTexDataAsRGBA32 前配置；
   面板标签当前用英文即无需此步）。
3. **Bloom 管线格式加固**：`BlackHoleRender` 两处 `new int[]{COLOR_FORMAT}` /
   `new int[]{TAA_HISTORY_FORMAT}` 的管线声明改用 `swapChain.getImageFormat()` /
   TAA 真实格式（修复 §2.2 第 4 点的 mismatch，属行为修正，改后需 A/B 回归画面）。
4. **GuiTexture 回显**（如需在面板内显示纹理，例如吸积盘温度分布小图）：
   appendix-01 的 `loadTextures` + `guiTexturesMap` + `ImGui.image(descSetHandle, size)`
   机制已随 GuiRender 移植，按需扩展。

---

## 4. 风险与注意事项

1. **交换链格式必须实测**：GUI 管线 `colorFormats` 一律来自
   `vkCtx.getSwapChain().getImageFormat()`，**不要**抄 `BlackHoleRender.COLOR_FORMAT`
   常量（它是 SRGB float 声明，与实际 B8G8R8A8_UNORM 附件不符，属现存隐患）。
2. **附件 view 每帧变**：`imageIndex` 驱动的 `attInfoColor.imageView(...)` 每帧设置，
   `renderInfo.renderArea` 尺寸每帧从 swapchain extent 刷新（resize 后 extent 已变）。
3. **resize 时序**：`Render.resize()` 中 `vkCtx.resize()` 重建 SwapChain 在前，
   `guiRender.resize()` 在后（依赖新 extent）；ImGui DisplaySize 同步更新，否则
   鼠标坐标与 UI 命中错位。
4. **色彩**：GUI 在 Bloom tonemap 之后叠加、直写最终颜色，ImGui 输出即所见，
   不参与 HDR；字体纹理用 SRGB 格式保证文字灰度正确（appendix-01 同款）。
5. **性能**：每帧 map/unMap 两个 KB 级缓冲 + 拼接拷贝（appendix-01 模式），近距
   高负载场景（帧耗时敏感）验收时对比开关 GUI 的平均帧耗时差，应 <0.5 ms。
   `updateBuffers` 的只增不缩策略在窗口拉大后保持大缓冲，属预期。
6. **线程模型**：本项目单线程渲染（无 appendix-01 的 ExecutorService/Phaser），
   ImGui 无跨线程访问，无需加锁。
7. **ImGui.destroyContext 时机**：`Render.cleanup()` 中 guiRender.cleanup 里最后调用，
   且必须晚于任何 `ImGui.getIO()` 访问（Main.update 的控制台读数不碰 ImGui，安全）。
8. **imgui.ini**：`setIniFilename(null)` 已关；否则项目根会多出运行时文件。
9. **hs_err_pid\*.log 与本计划无关**（JVM 崩溃遗留，勿纳入本次提交）。

---

## 5. 涉及文件总表

| 文件 | 动作 | Phase |
|---|---|---|
| `pom.xml` | 修改：+imgui-java 依赖 | 0 |
| `src/main/java/vulkanb/eng/graph/vk/SwapChain.java` | 修改：+`getImageFormat()` | 0 |
| `src/main/java/vulkanb/eng/graph/gui/GuiUtils.java` | 新建（移植） | 0 |
| `resources/shaders/gui_vtx.glsl` / `gui_frg.glsl` | 新建（复制） | 0 |
| `src/main/java/vulkanb/eng/graph/gui/GuiRender.java` | 新建（仿写+本地化） | 1 |
| `src/main/java/vulkanb/eng/graph/Render.java` | 修改：接线 init/render/resize/cleanup | 1 |
| `src/main/java/vulkanb/Main.java` | 修改：handleGui 驱动；Phase 2 面板；Phase 3 抢占 | 1-3 |
| `src/main/java/vulkanb/eng/scene/GeodesicIntegrator.java` | （仅 Phase 4 可能加绝对值 setter） | 4 |
| `src/main/java/vulkanb/eng/graph/BlackHoleRender.java` | （仅 Phase 4 格式加固） | 4 |

预计规模：Phase 0 ≈ 5 文件小改；Phase 1 新代码约 350-400 行（GuiRender 主体）；
Phase 2/3 仅改 Main（约 +120 行）。
