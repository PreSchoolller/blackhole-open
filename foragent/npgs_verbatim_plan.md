# NPGS 原版 shader 复刻实验（npgs-verbatim 分支）

> 状态：Phase 1（复刻渲染）+ Phase 2（热切换 + 原版参数面板补齐）+ Phase 3
>（原版后处理链 + 穿越自动翻转）均已落地。
> 目标：验证本框架**不改 NPGS 的 shader** 能否渲染出原版效果。NPGS 源位于同级目录 `../NPGS`（GPL-3.0）。

## Phase 3：原版后处理链 + 穿越自动翻转

- **NPGS 原版后处理链**（`npgs.postChain`，默认 true；false = 本框架 kerr_composite 供 A/B）：
  - 拓扑照搬 Application.cpp：PreBloom（compute，`GENERATE_MIPMAP`，历史→8-octave 图集）
    → GaussBlur H（compute，`GAUSS_BLUR` + ibHorizontal=true）→ **copyImage 回拷**
    （水平结果回图集作垂直趟源）→ GaussBlur V → Blend（图形，ColorBlend.frag 图集重建
    辉光 ×0.08 + pow1.5→Reinhard→gamma 调色链 → 交换链）。
  - `Bloom.comp.glsl` 一份源码两个宏变体 → **ShaderCompiler 新增宏定义支持**
    （缓存键 = 文件 + 宏哈希 + 源哈希，过期清理按宏分组互不误伤）。
  - 框架新增：`vk/ComputePipeline`（本项目首个 compute 管线）、DescSet 存储图绑定
    （GENERAL 布局）与采样器数组绑定（iBloomTexs[2]）、DescAllocator 补
    STORAGE_IMAGE 池配额。
  - **潜伏 bug 修复**：`ShaderCompiler.toShadercType` 的 compute 映射原写 5
    （实为 tess_evaluation；shaderc 实测 compute=2）——本项目首个 compute shader
    才触发，javap 常量池定案，现直接引用 Shaderc 常量。
  - preBloom/gaussBlur 附件按帧插槽隔离（NPGS 单附件跨帧存在竞争隐患，本框架惯例加固）。
- **穿越自动翻转**：NPGS `GetIntermediateSign` 逐字移植进 `GeodesicIntegrator`——
  每积分子步检查 Y（自旋轴）变号，插值赤道面穿越点、柱面半径 ρ < |a|（环内穿过 =
  虫洞喉道）即翻转宇宙符号镜像；NpgsRender 每帧与 KerrParams.universeSign 双向同步
  （穿越翻转 → GUI/UBO，GUI 手动改 → 下帧积分）。initialize/deactivate/坠落演出
  重置时符号归 +1（GUI 复选框同步）。跨帧粒度 = 积分子步（与 NPGS StepRK4 同位）。
- **坠落演出热切换**（Controls 面板 "Horizon fall show"，默认开）：关 = 不触发
  r<1.02Rs 传送演出且放开积分器视界护栏（`GeodesicIntegrator.setHorizonGuard`），
  测地相机可穿过视界/虫洞喉道观察翻转。注：视界内数值为实验路径——NPGS 的
  CheckAndSwitchCoords 坐标切换未移植，极靠近环奇点时积分可能发散（dtauMax ∝ r^1.5
  自动缩步长缓解）；穿越玩法建议配 NPGS Orig 面板 Whitehole=1（最大延拓）食用。
- **KS 补丁切换机制补齐（穿越稳定性修复）**：首版穿越实测"甩飞/黑屏/白屏"，根因是
  积分器缺 NPGS 的换系三件套，且 UBO `iCamDataCoordisOutgoing` 恒写 0——换补丁后
  shader 用错误度规解释相机数据。已补齐（全部 NPGS 逐字移植）：
  1. `computeMetric` 支持带符号 r（signR=-1 反宇宙）与 ingoing/outgoing 方向
     （dir 反转类空 null 向量；a=0 自然退化 Schwarzschild，合并旧分支）；
  2. `TransformKS`（含 KN 对数/反正切修正与坐标旋转；本积分器 Q=0）+ `ChangeIndex`；
  3. `CheckAndSwitchCoords`：rk4Step 每子步开始，全状态试变换到另一补丁，四速度
     协变分量之和发散 2× 判定换系；
  4. RK4 逐子步宇宙符号演化（NPGS sign2/3/4 同款：GetIntermediateSign 喂进各阶段
     EvaluateDerivatives）；
  5. `iCamDataCoordisOutgoing` ← `geodesic.isOutgoingPatch()`（KerrRender/NpgsRender
     两处接线；NPGS Application.cpp:2246 同款）。
  已知残留：NPGS 的 Christoffel 为解析式（"极其稳定"），本框架仍是中心差分——
  环奇点邻域精度劣于原版，如仍见数值抖动需补解析式移植。
- **解析 Christoffel 移植 + NaN 防护（穿越数值完善）**：实测三类异常——极向穿越
  y≈0 回弹、原点邻域坐标暴涨至 2000+ Rs、赤道撞环奇点 NaN 全黑巨卡（NaN 相机
  数据使 shader 每条光线跑满 MaxStep，单帧数秒）。根因：中心差分克氏符在环奇点
  邻域采样跨奇点结构。修复：
  1. `computeChristoffel` 替换为 NPGS 解析式逐字移植（Y=y/r 恒等式消 0/0、
     ∂l_y 的 KS 恒等式化简、解析 ∂f），无数值差分；
  2. `step()` 子步后有限性检查，发散即 `recoverFromBlowup()`：重置 5Rs 圆轨道
     （正宇宙 ingoing 片 + 世界轴重建标架 + GUI 符号复位），NaN 不出积分器。
  极向 y≈0 的"回弹"若在解析式下仍偶发，则属最大延拓几何的真实动力学（喉道处
  补丁切换与符号演化的联合效应），待实测观察。
- **fix：G 关闭后宇宙符号残留**——deactivate() 的 +1 复位被 dirty 消费路径的
  `isActive()` 门控拦住（测地已关无人消费），穿越后的 -1 残留在 KerrParams 里，
  原版 shader 在视界外按负 r 补丁追迹 → 整屏被"反宇宙着色"（青色山海放射条纹
  ——那正是 Antiground0 山海盒经波长重映射+pow(Shift,4) 的样子，与移植版换绑
  纹理观感差异巨大是**原版正确行为**：反宇宙 = 同一 raytracer + 另一侧几何 +
  按光线史重调色，非贴图替换）。修复：dirty 消费移出 isActive 门控。
- **语义澄清（Anti-universe 复选框）**：`iUniverseSign` 是光线追踪的度规补丁种子
  （负 r 侧 KS 系），不是背景盒选择器——静态观者在视界外手动切它会得到"负 r 补丁
  解释相机"的扭曲画面（NPGS 同行为，该状态本应只随穿越演化）；反宇宙星空
  （Antiground 盒）由逃逸光线的 status 位驱动，需 Whitehole=1（最大延拓）才有
  光线逃到另一侧。
- **fix：缩放窗口黑屏/闪烁（历史图未清零 → 回收显存 NaN 渗入 TAA）**——程序化
  SetWindowPos 缩放风暴实机取证（截图 + 历史图 GPU 读回 + iDEBUG=3 热图 + UV 梯度
  shader 探针）逐步二分定位：Pass A 在任意尺寸下输出完好（历史图逐位正确），坏态
  在 resize 后第一帧（iBlendWeight=1.0 纯新鲜帧）即出现星星状/黑色垃圾斑点——
  根因是 resize 重建的历史图内容未定义，VMA 回收显存残留任意位型，其中 NaN/Inf
  半浮点位型经 BlackHole.frag 的 `iBlendWeight*Final + (1-iBlendWeight)*Prev` 在
  权重=1 时仍算 `0.0*Prev=NaN`，稀疏导出后经 TAA 反馈永不衰减、经 postChain 辉光
  模糊空间扩散 → 整屏黑（kerr_composite 路径则呈内容带错位状坏相）。启动期 VMA
  给的是 OS 清零新页故无此问题；复刻（KerrRender）的 TAA 实现不同故幸免；NPGS
  原版程序缩放也会变黑，但其根因不同（`_WindowSize` 启动时冻结、最终 copyImage
  以旧尺寸矩形拷进新尺寸交换链，App 层 bug，不逐字保真）。修复：
  1. `createHistoryResources` 建图后 `vkCmdClearColorImage` 清零再转目标布局
     （0*任意=0，NaN 永不进入反馈）；验证：两轮风暴 13+ 次混合缩放画面完好。
  2. 顺手修 resize 逐次泄漏的历史采样器（cleanupHistoryResources 统一销毁）与
     `createRenderInfos` 未释放的 prepass 渲染信息结构。
- **穿越可达性修复（视界内步长细分）+ 预测穿越读数**——离线穿越模拟器
  （`vulkanb.TraversalSim`，离线驱动真实积分器跑脚本化俯冲，java -cp 直跑）扫出
  三种失败模式，共用一个数值根因：
  1. **势垒弹回**：反宇宙侧（sign=-1）g_tt=-1+f（f<0）是排斥势，斜率
     ~2Ma/(a²-ρ²)^{3/2}（贴环处爆炸）——E≈1.4 的滑入（v0 上限 0.99 也只到这）
     穿盘后 Δy~0.01-0.03 即被推回、二次穿盘翻回 +1。物理（模型内自洽），非 bug。
  2. **贴环弹射**：中心瞄准的俯冲被拖曳漏斗化到 ρ_cross≈0.44（|a|=0.45，只差
     0.007），近环数值梯度 (a²-ρ²)^{-3/2} 使 RK4 粗步长注入能量——弹射到
     10⁷-10⁸ Rs 的虚空（γ=1.3e5，纯数值泵能）或直接 NaN。
  3. **发散恢复踢出**：NaN 触发 recoverFromBlowup 重置 5Rs + 符号复位 +1——
     任何一次 hiccup = 整次穿越作废。
  根因：`dtauMax = 0.05·max(r,1)^1.5` 在视界内被钳在 0.05，比环奇邻域的动力
  时标粗 50-200 倍。**修复：去掉 max 钳制，dtauMax = 0.05·r^1.5**（r≥1 区域
  公式不变——坠落演出/轨道/视界外行为零影响；视界内 MAX_SUBSTEPS=500 封顶
  自动表现为喉道附近时间放缓，对操控反而友好）。修复后：全程朝心推力的俯冲
  穿盘（ρ_cross=0.426）后稳定驻留反宇宙（sign=-1，弹射向外、γ 回落、无发散）；
  低能量俯冲变为干净弹回（不再 NaN）。
- **穿越配方**（模拟器扫出的可行路线）：① Controls 关 Horizon fall show（放开
  视界护栏）+ 勾 Whitehole=1（反宇宙星空才可见）；② G 进测地模式后**全程按住
  W 朝心推进**（蓄能冲过反侧势垒，纯滑入必被弹回）；③ 看遥测 pred rho 修瞄准，
  穿越落点套住环内（pred rho < |a|，环缘内侧 0.2-0.4 最稳）；④ 穿盘翻转后继续
  推进直到脱离喉道区。返回主宇宙：调头再穿一次盘即可（符号再次翻转）。

## Phase 2：热切换 + 原版 UI 参数补齐

- **第三时空模式**：`Scene.SpacetimeMode.KERR_NPGS`——Controls 面板第三单选
  "NPGS verbatim"，与 Schwarzschild/Kerr 完全同权热切换（`Render.switchSpacetime`
  克尔系三分派；`npgs.verbatim=true` 的语义变为"启动即进入原版模式"）。
  克尔系两模式共用 KerrParams：盘时间相位跨切换连续，测地相机（G 键）跨切换保持。
- **NPGS Orig 面板**（仅原版模式显示）：原版有、移植时被裁剪的功能参数——
  iGrid（关/GridColor/Simple）、iInWhichUniverse（宇宙变体选层 0..2）、iWhitehole
  （最大延拓）、iEnableShadowCulling、iDEBUG（0..4，3=步数热图）、iPolarization+角度、
  iDensestarsurfaceR + 4 个表面指数（致密星表面，原版独有）、iUseImageDisk +
  iImageRotationSpeed（贴图盘）。默认值 = NPGS Application.cpp 初始化值（全关）。
- **Kerr Disk 面板**跨两克尔系模式显示；移植版专有三条（Noise LUT/Disk scatter/
  Disk ambient，原版 UBO 声明不含）仅 Kerr 模式显示。
- **贴图盘真纹理**：新增 `vk/Texture2D`（单层版 CubeTexture 流程，含 mip 链），
  NPGS `Assets/Textures/Disk/R.jpg` 拷至 `resources/textures/npgs/Disk/`，绑 set1.b9。
- **鉴别实验记录**：同一 UBO 值 `iDensestarsurfaceR=6.0`——原版画出中子星表面
  （DensestarColor 椭球求交+黑体着色），移植版无变化（该代码移植时被裁剪）；
  叠加像素差量化（同机位移植版 vs 原版：平均 2.59/255，显著差异像素 2.2%，
  集中于盘内区/光子环——即移植版追加扩展所在处）。iGrid 拉满亦是原版独有实时证据。
- 已知未接线：iUniverseSign 已入面板（Anti-universe side 复选框，±1 二元）但
  **测地穿越不自动翻转**——它是每条光线宇宙符号的种子（NPGS 引擎侧随穿越状态维护，
  本引擎未接线状态跟踪），穿越观察需手动切换；贴图盘仅 R.jpg（NPGS 的 Disk 集合
  含 anw/dxnw 轮换，未搬）。

## Phase 1：复刻渲染（结论：能跑）

**能跑**。NPGS 的接口设计（uniform 布局、绑定槽位、pass 拓扑）与本项目移植版高度同源，
兼容缺口只有三个，全部可以在框架侧补齐而 NPGS 文件逐字节不动：

| 缺口 | 原因 | 框架侧解法 |
|---|---|---|
| `#include` 不被支持 | NPGS 用 `#include` 组织代码（common 5025 行），本项目 kerr.frag 是内联后的产物；shaderc 默认不解析 include | `ShaderCompiler` 新增 `resolveIncludes`：相对包含者目录递归内联 + **include-once 去重**（BlackHole.frag 会经两条路径重复引入 CoordConverter.glsl，不去重必炸重定义）+ 缓存键改为"解析后全文哈希"（改被包含文件也触发重编译） |
| 天空盒槽位 6 个 vs 1 个 | NPGS set1.b1..b6 = Universe/Antiverse × 3 套（`SampleBackground` 按 `iInWhichUniverse+3+useContground %3` 选层；RGB 通道在单盒内做波长重映射，三套盒是宇宙变体而非色带）；本项目裁剪为一套 | NpgsRender 把 texLayout 扩到 b0..b9，NPGS 原装 6 套盒拷贝至 `resources/textures/npgs/`（共 19MB）全量绑定。其 C++ 加载参数为 `bFlipVertically=false`，与本项目 CubeTexture 的"不翻转"约定一致，纹理侧零适配 |
| 顶点着色器要顶点缓冲 | NPGS ScreenQuad.vert 读 Position/TexCoord 属性；本框架全屏 pass 一律 gl_VertexIndex 生成、无顶点缓冲 | 沿用 kerr.vert：location 0 输出 vec2 恰好满足 BlackHole.frag 的 `TexCoordFromVert` 输入契约（prepass/composite 两个 frag 无顶点输入，多余输出无害）。ScreenQuad.vert 是 NPGS 四文件中唯一未上机的一个 |

UBO 无需任何适配：本项目 BlackHoleArgs 前 384 字节与 NPGS 声明逐字段一致（移植时刻意
保持了字段序，末尾追加的 iNoiseLut/iDiskScatter/iDiskAmbient 3 float 原版声明不含、自然忽略）。

## NPGS 管线实测拓扑（Application.cpp）

```
Prepass（半分辨率双附件：Distortion R32F ×4 + Volumetric 16F ×4）
  → Composite（全分辨率：边缘感知升采样 + tonemap + TAA → BlackHoleAttachment R16F×4）
  → ImageCopy → HistoryAttachment（下一帧 TAA 历史）
  → PreBloom / GaussBlur / Blend（后处理链）
```

- composite 分支：`iPrepass==0` 时全分辨率重迹（NPGS 主循环当前实值 Prepass=0，prepass 等于白跑），
  非 0 走边缘路径；本复刻 iPrepass 用 NPGS 原值 1（prepassEnabled=true 时），0 时跳过 Pass P。
- composite 视口翻转（height 负高度）——对对称全屏四边形是覆盖中性操作（gl_FragCoord 枚举
  与视口方向无关），本框架同为翻转视口，互不影响。
- 本复刻的后处理直接沿用 kerr_composite.frag（8-octave 辉光 + ColorBlend 调色链逐行照搬版），
  NPGS 自家的 PreBloom/GaussBlur/Blend 未搬运（如需 100% 后处理复刻可作 Phase 2）。

## 用法

```properties
npgs.verbatim=true   # 启动即进入 NPGS verbatim 模式（GUI Controls 单选可随时热切换）
npgs.direct=false    # true = BlackHole.frag 单 pass 全分辨率直出（最原汁原味，最重）
```

GUI：Kerr Disk 面板（两克尔系模式共用）调共享物理参数；NPGS Orig 面板（仅原版模式）
调原版专有参数（网格/致密星/白洞/选层/贴图盘/偏振/调试）；测地相机（G 键）、P 键暂停、
F 键视角照常。山海盒开关不作用于原版模式（b1..b6 被原装 6 套盒占用）。

## 已知取向风险（一行开关）

画面若整体上下镜像：在 `CubeTexture.loadFace` 的 stb 解码处对 NPGS 盒开
`stbi_set_flip_vertically_on_load(true)`（加载翻转）即可——本框架 kerr.frag 的
`FragUv.y = 1.0 - FragUv.y`（kerr.frag:2438，移植时加的"全流程唯一一次 y 翻转"）
在原版 shader 中不存在，理论上加载翻转与否决定镜像取向；以实机目视为准。

## 文件清单

- `resources/shaders/npgs/`：BlackHole.frag.glsl / BlackHole_prepass.frag.glsl /
  BlackHole_composite.frag.glsl / BlackHole_common.glsl / Common/{CoordConverter,NumericConstants}.glsl
  ——**逐字节未动**（含 NPGS 的中文注释与 `#pragma shader_stage`）。
- `resources/textures/npgs/{Universe,Antiverse}{0,1,2}Skybox/`：6 套原装盒（Pos/Neg XYZ.jpg）。
- `NpgsRender.java`：克隆 KerrRender，diff = 着色器路径/描述符布局/天空盒加载/iPrepass 值，
  UBO 打包与历史 ping-pong/mip 链/合成 pass 原样保留。
- `Render.java`：`npgs.verbatim` 分派（init/switchSpacetime/render/resize/cleanup 五处）。
- `ShaderCompiler.java`：`#include` 解析（见上表）。
- `EngCfg`：`npgs.verbatim` / `npgs.direct` 两键。
