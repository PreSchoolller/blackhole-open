# 克尔黑洞（Kerr）移植计划书 —— 借鉴 NPGS 完整实现

> **状态(2026-10-06):裁剪功能首批恢复完成（分支 `kerr-restore-cut-features`）。**
> 背景：`c085332`（Phase 1.5 结构化重构）一次删掉 kerr.frag 2139 行，§2.5 裁剪清单里的
> 功能随之整体消失。但 `KerrParams` 字段与 `NPGS Orig` 面板控件从未删除 → 这些开关在
> KERR 模式下是**死开关**（点按无反应）。本次按"恢复被抛弃功能"的要求，从
> `D:\CodingSpace\IdeaProjects\NPGS\NPGS\Sources\Engine\Shaders\BlackHole_common.glsl`
> （权威原版，与仓库内 `resources/shaders/npgs/` 冻结副本同源）逐行移植回以下 **4 类功能**
> （`iPolarization` / `iGrid` / `iDensestar*` / 落点白点），只改 `resources/shaders/kerr.frag`
> 一个文件——`KERR_NPGS` 的冻结原版路径与 Java 侧均未触碰。
>
> - **偏振（iPolarization=1 色相显示 / 2 偏振片）**：补回 `GetWalkerPenrose`、
>   `SolvePolarization`，新增 `BuildTransversePolarizationBasis`（把两位移矢量正交化成
>   与光子横向的四维偏振矢量，抽成共享函数替代原版在两处内联的重复代码）；
>   `GetInitialMomentum` 加 `out vec2 WP_CamX/WP_CamY` 输出相机屏幕基底；`DiskColor`/`JetColor`
>   补回 Stokes Q/U 逐样本累积（`JetColor` 签名同步补 3 个参数，与 `DiskColor` 对齐）。
>   `TraceRay` 原有的输出端分支（L2941 起）本来就在，本次只补输入端。
> - **时空网格（iGrid=1 GridColor / 2 GridColorSimple）**：两函数共 510 行，自包含无外部依赖，
>   按原版行号原样搬回；`TraceRay` 主循环补 `if(iGrid==1)…else if(iGrid==2)` 分派。
>   `ShowInnerGrid` 遮罩与 `iGrid==0` 剔除分支在裁剪时已保留，无需改动。
> - **致密星表面（iDensestarsurfaceR≠0）**：补回 `DensestarColor` + 其依赖 `Fbm_Standalone`。
> - **落点白点**：补回 `DrawFallingWhiteDot` + 依赖 `GetIngoingNullParticlePos`、`GetDotDistSq`。
>   **注意**：原版此处是注释状态（默认不画），本移植版按"恢复功能"的要求**已启用**；
>   如需关闭，注释掉 `TraceRay` 里那一块调用即可。
>
> **移植适配（签名差异，共 21 处）**：原版 `ComputeGeometryScalars(7参)` → 本移植版 `(5参)`；
> `KerrSchildRadius(3参)` → `(2参)`；`transformKerrSchild_YSpin(7参)` → `(4参)`。
>
> > **[2026-10-06 更正] 上文原先记录的"待验收语义风险"是误报，现已核实无问题。**
> > 当时写的是"原版渡 `HitPointSign`/`diskSign`，本移植版该参语义为宇宙侧符号"——**这个推断错了**。
> > 回查 NPGS 原始调用点后确认：`hitSign` 在 `ImageDiskColor` 内**是局部计算的**
> > （npgs L2640 `float hitSign = (length(DiskHitPos.xz) < abs(PhysicalSpinA)) ? -StartStepSign
> > : StartStepSign;`），且 `ComputeGeometryScalars` 的**第 3 参本就是 `r_sign`**，
> > 槽位对得上。搬回后逐行核对，`ImageDiskColor` 的 `StartStepSign` 生命周期与原版完全一致
> > （L3178 初始化 → L3228 环内翻转 → L3241 取用），`DensestarColor`、`GridColor` 同构。
> > **教训**：误报来自引用了自己上一轮的小结文案（"原版传 HitPointSign"）而未回原始代码核对——
> > 转述会漂移，结论必须落到原始调用点。

>
> **验证**：shaderc 编译通过（368,368 字节 SPIR-V，离线 `ShaderCompiler` 与运行时 shader-cache
> 产物字节数一致）；`mvn clean package` BUILD SUCCESS；`java -jar` 启动无着色器报错。
> 四个功能默认均关闭（`gridMode=0`/`polarization=false`/`densestarRadiusRs=0`），
> 不影响既有默认画面。
>
> ---
>
> **状态(2026-10-06) 第二批：多天空盒 + 宇宙变体选层 + 白洞/最大延拓接线**
>
> 第一批只恢复了着色器函数体，但随即发现**接线在 Java 侧早被剪断**——只补着色器是"死开关
> 换个死法"。`KerrRender` 的 UBO 打包器里 8 个字段被写死为常量，GUI/`KerrParams` 根本到不了
> 着色器。本次一并接通：
>
> **A. KerrRender UBO 接线（12 个字段解冻）**
> | 字段 | 原值 | 现读 |
> |---|---|---|
> | `iDEBUG` | `0` | `kp.debugMode` |
> | `iWhitehole` | `0` | `kp.whitehole` |
> | `iInWhichUniverse` | `0` | `kp.universeIndex` |
> | `iGrid` | `0` | `kp.gridMode` |
> | `iEnableShadowCulling` | `0` | `kp.shadowCulling` |
> | `iPolarization` | `0` | `kp.polarization`（见下方"未暴露"） |
> | `iUseImageDisk` | `0` | `kp.useImageDisk` |
> | `iUniverseSign` | `1.0f` | `kp.universeSign` |
> | `iDensestarsurfaceR` + 4 个致密星指数 | `0/4/1/4/1` | `kp.densestar*` |
> | `iImageRotationSpeed` / `iPolarizationAngle` | `0.0f` | `kp.imageRotationSpeed` / `kp.polarizationAngle` |
>
> 另补 **宇宙符号双向交换**（照搬 NpgsRender）：`geodesic.isUniverseSignDirty()` →
> `kp.universeSign`；`isActive()` 时反向同步。缺这条则最大延拓穿越后的 `-1` 到不了着色器，
> 反宇宙选层（Antiground*）永远不触发。
>
> **B. 六套天空盒（`KerrRender.java`）+ 选层（`kerr.frag`）**
> - 描述符布局 set1 从"单层 b1"扩为 **b1..b6 = Background0/Antiground0/Background1/
>   Antiground1/Background2/Antiground2**，顺序与 `npgs` 冻结原版逐槽位对齐；
>   `KerrRender` 载入 `resources/textures/npgs/` 下六套盒（Universe 1024²×3 + Antiverse 2048²×3）。
> - `SampleBackground` 换成 NPGS 原版选层逻辑：`offset`/`useContground` 由 `Status>3`
>   （逃逸自上个宇宙的光）反推，`isAntiverse = mod(rStatus,3)==2`，再按
>   `int(iInWhichUniverse+3+useContground)%3` 在三个变体里选盒（Background 或 Antiground）。
>   **唯一差异**：固定 `textureLod(...,0.0)`，NPGS 用 `textureQueryLod` 逐面取 mip；
>   本项目盒有完整 mip 链，若要更贴原版可改回 queryLod（观感差异待实测评估）。
> - **b9 语义撞车修复**：原 b9 注释写"贴图盘占位"却绑历史视图（与 shader 的 `iHistoryTex`
>   同名槽位冲突）。现 b9 绑 NPGS 原版贴图盘 `Disk/R.jpg`（`iUseImageDisk` 门控）。
> - **显存代价**：六套盒解码 + 完整 mip 链约 **480MB 显存**（Universe 3×~33MB +
>   Antiverse 3×~133MB）。与 NPGS 模式同量级；若两种模式都初始化则接近翻倍。
>   如需省显存可改惰性加载（仅在 Whitehole/选层需要时载入）。
> - **`DualSkybox` 取舍**：项目自有的「星空/山海」双盒仍换绑在 **b1**（= 默认选层
>   `%3==0`），故该 GUI 开关在默认选层下仍生效；切到 1/2 号宇宙变体时它不参与
>   （那两层恒用 NPGS 原装盒），属已知取舍，已在 `rebindSkyboxDescSets` 注释说明。
>
> **C. 贴图盘 `ImageDiskColor`（171 行，第三批补齐）**
> 六套盒与 b9 接线到位后，`ImageDiskColor`（NPGS 2519–2689）也可整体搬回——自包含无外部依赖。
> 调用点按 NPGS 原位置放在"宇宙选层"门控内、且先于致密星/网格；`iUseImageDisk` 与
> `iImageRotationSpeed` 均已在 A 段接通，故 NPGS Orig 面板的「Use image disk (R.jpg)」
> 复选框现在真正生效。
> 顺带修一处移植期遗留：`GetKeplerianAngularVelocity` 在本移植版被简化为
> `(Radius, Rs)` 两参（自旋/电荷走全局 `PhysicalSpinA`/`PhysicalQ`），NPGS 原版是四参，
> 搬回时按本移植版签名归一。
>
> **D. 未暴露/待定**
> - `iPolarization=2`（马吕斯偏振片模式）：`KerrParams.polarization` 是 boolean，
>   GUI 只能表达"关/开"，故现映射为 1（EVPA 色相显示）。要暴露 2 需在
>   `Panels` 的 NPGS Orig 面板把复选框换成 0/1/2 三档滑条。
> - `NpgsRender` 侧天空盒绑定序经核对**正确**（数组顺序 [U0,A0,U1,A1,U2,A2] ↔ b1..b6 一致），
>   无绑定错位问题。
> - 六套盒固定 `textureLod(...,0.0)`（NPGS 用 `textureQueryLod` 逐面取 mip）；盒有完整
>   mip 链，若要更贴原版可改回 queryLod。
>
> **验证（第三批后）**：shaderc 编译通过（**387,432 字节** SPIR-V）；`mvn clean package`
> BUILD SUCCESS；以 `npgs.verbatim=false` 强制进 KERR 模式实测，运行时 shader-cache 产物
> **387,432 字节与离线编译逐字节一致**，无 Vulkan 验证层报错，六套盒 + 贴图盘 + 12 个
> 描述符绑定全部就绪。
>
> **E. 面板可达性修复（第四批，用户实测反馈"面板没改呢！调不了"）**
> 前三批把功能与接线都接通了，却漏了最后一步：**这些开关全在 `NPGS Orig` 面板里，而该面板
> 被 `Panels.buildFrame` 门控为"仅 KERR_NPGS 模式渲染"** —— KERR 模式下根本看不见，等于
> 还是调不了。
> - **做法**：把该面板的控件体提取为 `renderKerrExtraControls(kp, includeOrigOnly)`，
>   由两个面板共用同一份 `KerrParams` 字段：
>   - `NPGS Orig`（KERR_NPGS，`includeOrigOnly=true`）= 原有全部控件；
>   - **`Kerr Extras`（KERR，新增）** = 同样的网格/选层/白洞/反宇宙侧/致密星/贴图盘/偏振/调试。
>   - `includeOrigOnly` 只控制"仅原版 shader 消费"的项（偏振片角度滑条）。
> - 同时删掉替换后残留的旧内联方法体（不留死代码），并给 `iDEBUG` 加注：**模式 2 未移植**，
>   KERR 模式下选中时面板会提示改用 NPGS 模式。
> - 控件存活审计（逐个确认 kerr.frag 中有真实消费点）：`iInWhichUniverse`(10 处)、
>   `iWhitehole`(18)、`iGrid`(10)、`iDensestarsurfaceR`(7)、`iUseImageDisk`(6)、
>   `iUniverseSign`(3)、`iImageRotationSpeed`(3)、`iPolarizationAngle`(2)、
>   `iEnableShadowCulling`(1，L3481)、`iDEBUG`(1/3/4 共 12 处)。
> - **截图实测确认**：KERR 模式下 `Kerr Extras` 面板正常显示四个分区
>   （Spacetime structure / Dense star surface / Image disk / Polarization · Debug）。
>
> **F. 第五批：补齐上一批"接通了但功能不全"的三处**
> 第四批把 `iEnableShadowCulling` / `iPolarization` / `iDEBUG` 都接到 GUI 后，暴露出这三个
> 开关本身还不完整，勾了会出错误结果或没反应。本批逐个补齐：
>
> 1. **阴影剔除四函数（正确性隐患，优先）** —— `SolveCubicMaxReal` / `SolveQuarticU` /
>    `GetDropFrameAngle` / `GetShadowHalfAngleRN` 在 Phase 1.5 被留成 `return 0.0` 空壳
>    （当时 `iEnableShadowCulling` 恒 0、分支不可达，属"安全"省略）。开关一接通，这些 0
>    就变成**错误的阴影几何比较量**——阴影角恒 0，剔除逻辑会误判。已按 NPGS 原版
>    （L3597–3698）恢复实现：光子球半径 → 临界碰撞参数 b_c → 静态观者 sin/cos（含光子球
>    内外 Cos 符号判定）→ 落体观者相对论光行差。这几个函数纯自包含数学，无外部依赖。
> 2. **偏振模式 0/1/2** —— `KerrParams.polarization` 由 `boolean` 改为
>    **`int polarizationMode`（0=关 / 1=EVPA 色相显示 / 2=马吕斯偏振片）**，面板复选框换成
>    三档滑条，偏振片角度滑条仅模式 2 显示。`KerrRender` 与 `NpgsRender` 两处打包同步改为
>    读该字段——**原版 shader 同样实现了 mode 2**（npgs L4914/4921），故 KERR_NPGS 一并受益。
> 3. **调试视图 2（初始动量可视化）** —— 补回 `DebugInitialMomentum`（96 行，NPGS L834–929）
>    并在 TraceRay 中按原版位置插入 `if (iDEBUG == 2 && bShouldContinueMarchRay)` 接管分支
>    （紧跟初始动量构造、早于一切盘/网格累加，设 Status=3 直接输出）。
>    面板里的"mode 2 not ported"提示已删除，并给 1/2/3/4 补了语义标签。
>    顺带清理：`renderKerrExtraControls` 的 `includeOrigOnly` 参数在两个面板控件完全一致后
>    已无用途，删除（不留死参数）。
>
> **验证（第五批后）**：shaderc 编译通过（**407,140 字节** SPIR-V）；`mvn clean package`
> BUILD SUCCESS；KERR 模式实测运行时产物 **407,140 字节与离线编译一致**，无 Vulkan 验证层
> 报错，渲染正常。**至此 `kerr.frag` 内已无任何 `return 0.0` 桩函数**（仅剩
> `AdvanceToMarchingBoundary` 中两处合法的提前返回）。
>
> **G. 逐功能截图实测（第六批：把"编译通过"升级为"确认真的渲染出来"）**
> 教训来自第四批——"编译通过"不等于"真能用"（当时面板不可达就是编译全过、功能全废）。
> 故本轮逐个把 `KerrParams` 默认值临时打开、跑起来截取**应用窗口**（`PrintWindow`
> + `PW_RENDERFULLCONTENT`，无需最大化）逐个确认，验证后 `git checkout` 还原：
>
> | 功能 | 实测结果 |
> |---|---|
> | 时空网格 `gridMode=1` | ✅ 渲染出清晰的极坐标网格（同心环 + 辐射线） |
> | 致密星 `densestarRadiusRs=6` | ✅ 带黑体着色的湍流表面球体，明显亮于吸积盘 |
> | 偏振 `polarizationMode=1` | ✅ 彩虹色 EVPA 色相映射铺满盘面——整条偏振链路工作 |
> | 贴图盘 `useImageDisk` | ✅ 面板勾选态正确，盘面着色随贴图变化 |
> | 白洞 `whitehole=true` | ✅ 最大延拓下追迹上限提至 1145 步，无崩溃/无验证层报错 |
> | Kerr Extras 面板 | ✅ 四分区控件齐全、可达 |
>
> **顺带修正一处越权（用户未要求但确属我的问题）**：第五批把落点白点
> `DrawFallingWhiteDot` **默认设为开启**了，而 NPGS 原版该调用是注释状态（默认不画），
> 等于往默认画面里塞了个没有开关的调试元素（实测截图中黑洞中心可见小白点）。
> 现补 `iShowFallingDot` 开关（面板 `Falling dot (debug viz)` 复选框，**默认关**）：
> - 新增 UBO 字段 `int iShowFallingDot;` —— **占用 std140 结构对齐产生的尾部填充**：
>   前面字段实际 396B、缓冲区 400B，补上后内容恰为 400B，**既有字段偏移一律不变**，
>   NPGS 原版声明更短、自然忽略尾部字节，故无需改 `BH_ARGS_SIZE` 或任何其他文件。
> - 仅 `kerr.frag` 消费（NPGS 原版无此逻辑，故 `NpgsRender` 不必写该字段）。
>
> **验证（第六批后）**：shaderc 编译 **407,316 字节** SPIR-V，UBO 内容 400B 无溢出；
> `mvn clean package` BUILD SUCCESS；KERR 模式默认状态实测渲染干净（小白点已消失），
> 运行时空闲稳定。所有功能默认关闭，默认画面与恢复前一致。
>
> **H. 第七批：核对 hitSign（误报）+ 消除赤道穿越三处重复 + 暴露 iDEBUG 5**
>
> 1. **`hitSign` 语义核对 → 确认无问题**（详见上文 §2.5 后的更正块）。原先记录的"语义风险"
>    是我引用自己的转述而非原始代码造成的误报；`diskSign`/`hitSign` 一律是**函数内局部计算**，
>    与原版逐行一致。
> 2. **赤道穿越检测三处重复 → 归一为一份**。同一逻辑（Y 变号 + 柱面半径 ρ<|a| 判据）在
>    `kerr.frag` 里存在三份实现：
>    - `GetIntermediateSign`（NPGS 逐字移植，RK4 子步用，**保留不动**，属原版代码）
>    - `CheckEquatorialCrossing`（Phase 1.5 计划书 §1.5.2 明确要提取的共享函数，
>      **但提取后从未接线，调用者为 0 = 死代码**）
>    - `TraceRay` 主循环内的内联展开（原 L4415-4419）
>    现将主循环那处内联替换为 `CurrentUniverseSign = CheckEquatorialCrossing(LastX, X,
>    CurrentUniverseSign);` —— 既消除重复，也让这个当年提取出来的函数真正投入使用。
>    语义完全等价（逐行比对过判据与插值公式）。
> 3. **`iDEBUG == 5` 暴露**：致密星表面专用的"八卦限颜色 + 经纬网棋盘格"调试视图，shader
>    分支（L2956）本来就在，只是面板滑条上限卡在 4 够不到。上限改为 5 并补了标签文案。
>
> **验证（第七批后）**：shaderc 编译 **407,716 字节** SPIR-V；`mvn compile` 通过；
> `CheckEquatorialCrossing` 现有 1 处真实调用。
>
> **I. 第八批：六套天空盒惰性加载（可选，`kerr.lazySkybox`）**
> 六套盒解码 + 完整 mip 链约 480MB 显存，而默认配置（`iInWhichUniverse=0`、正宇宙）
> 只用到 b1/b2 两套。新增配置键 **`kerr.lazySkybox`（jar 内默认 `true`）**：
> - `true`：init 只载 `Background0/Antiground0`（约 160MB），其余在用户把面板
>   `Universe #` 切到 1/2 时由 `ensureSkyboxesForUniverse` 按需补载并重绑 b1..b6。
>   选层公式 `(iInWhichUniverse+3+useContground)%3` 保证变体 `u` 只用槽位 `2u`/`2u+1`，
>   故按变体成对加载；**已载入的盒不卸载**（来回切换不反复重传，最坏退化为全量加载）。
> - `false`：init 一次载齐六套，与 NPGS 原版行为一致。
> - 惰性期间未载入的槽位**始终绑着已载入的 0 号盒**（init 绑定循环里的 null 回退），
>   故描述符集任何时候都不含空句柄，不会触发验证层报错或采样未定义。
>
> **验证**：以 `log.level=DEBUG` 观察 `CubeTexture uploaded` 实际加载数——
> 默认（Universe #0）只载 `Universe0Skybox` + `Antiverse0Skybox`（U1/A1/U2/A2 未载）；
> 把 `universeIndex` 置 2 后，首次进入帧循环按需补载 `Universe2Skybox` +
> `Antiverse2Skybox`，随后每帧提前返回（`loadedUniverseIndex` 生效）；默认状态渲染正常。
>
> > **过程教训（同一天内把同一个错犯两次）**：本轮我曾两次把"查询太早 / 输出还没到"
> > 误判为"功能没生效"。第二次更严重——用了 `Select-String ... | Select-Object -First N`
> > 截取日志，该管道取够 N 行后会**提前终止并杀掉 java 进程**（`exit code: 1`），
> > 结果我既误判又亲手打断了程序。**读运行日志不要用 `-First` 截断正在写日志的进程管道**，
> > 结论必须建立在确认输出已完整到达之上。
>
> **J. 第九批（用户实机反馈的两个 bug，同一根因）**
> 用户报告：单独开白洞时中心"该有的东西"没出现（原版看得到、**两极尤其明显**）；
> 白洞 + 反宇宙时**整屏全黑**。
>
> **根因**：`KerrSchildRadius` 返回**带符号**半径（`r_sign * sqrt(r2)`），反宇宙侧
> `r_sign = iUniverseSign = -1` → `CameraStartR` / `geo.r` 都是**负值**；而白洞的两处
> `universeoffset` 自增判定写的是 `< InnerHorizonR`（正值），**负半径下恒成立**：
> ```glsl
> L4205 if(iWhitehole==1 && ... && CameraStartR < InnerHorizonR) universeoffset++;
> L4298 if(iWhitehole==1 && ... && geo.r < InnerHorizonR && lastR > InnerHorizonR) universeoffset++;
> ```
> 于是反宇宙下 `universeoffset` 被误加到 2，逃逸分支 `Status = 2.0 + 3.0*2 = 8.0`，
> 而 `main` 的背景合成门控是 `Status > 0.5 && Status < 2.5` → **背景整块被跳过 → 全黑**。
> 中心天体同理：它是反宇宙侧看到的光子环，也被同一门控吃掉。
>
> **修复**：两处改按半径**大小**比较（`abs(CameraStartR)` / `abs(geo.r)` / `abs(lastR)`）。
>
> **验证**（两模式同参数**截图比对**）：whitehole=1 + antiverse=-1 赤道视角：修复前纯黑 →
> 修复后正确渲染反宇宙山海盒，与原版 `KERR_NPGS` 一致；极点 88° 视角：中心明亮光子小环
> 正常显示，与原版一致；whitehole=1 + 正宇宙：不受影响；默认配置无回归。
>
> **顺带新增配置键**（便于切换/复现，默认值与原行为一致）：
> `kerr.whitehole` / `kerr.universeSign` / `kerr.debugMode` / `ui.hidden`
> （`ui.hidden=true` 启动即隐藏面板，F1 仍可切换——观察纯渲染画面用）。
>
> > **⚠️ 方法论教训（本轮最该记住的一条）**：调试期间我**多次用截图文件的字节大小**
> > 当作画面内容的证据（"10KB 所以是黑的"），连续推出错误结论——**纯色画面同样很小**
> > （纯绿填充只有 13KB，被我误判为黑屏）。**尺寸只能说明"是否单色"，不能说明颜色**；
> > 判断画面内容**必须看图**，不能拿文件大小当代理指标。
>
> **至此 §2.5 裁剪清单中的功能项全部恢复**（多天空盒选层、白洞/最大延拓、贴图盘
`ImageDiskColor`、致密星、网格、偏振、落点白点），仅下列"项目主动保留的差异"仍然存在：
固定 `textureLod(...,0.0)`（NPGS 用 `queryLod`）、鱼眼/落体观者的 `ObserverMode` 细节、
以及本项目自有的扩展开关。
>
> **K. 第十批（用户实机反馈：南极正宇宙白洞中心亮斑仍缺失——第九批根因只对了一半）**
> 用户截图比对：同机位（南极轴上 18 Rs、whitehole=1、正宇宙），`KERR_NPGS` 中心有亮蓝白
> 斑（上一个宇宙的天空经喉道压缩成像），KERR 中心仍纯黑。
>
> **根因**：`main` 的背景合成门控抄的是**普通版** `BlackHole.frag.glsl` L33
> （`Status > 0.5 && Status < 2.5`）；而 verbatim 模式默认走 prepass+**composite**
> 路径（`npgs.direct=false`），其 `BlackHole_composite.frag.glsl` L143 的门控是
> `(0.5 < Status < 2.5) || Status > 3.5`——**Status 4/5（出白洞、来自上个宇宙的光）也要
> 采背景**，`SampleBackground` 内按 `offset=round((rStatus-1)/3)` 取上一层盒。本移植版
> 的 main 从一开始就没有这个分支 → 正宇宙下出喉道的 ray `universeoffset=1`、
> `Status=1+3=4` → 背景被跳过 → 喉道区整片黑。第九批修的 `abs()` 半径比较只救了
> **反宇宙侧**的误计数（Status 被抬到 8 导致的全黑），正宇宙侧缺的是这整个分支，
> 当时"88° 视角已与原版一致"的验收恰好没覆盖正轴视角。
>
> **修复**（`kerr.frag` main 一处 + `KerrRender` 一处）：
> 1. 背景门控对齐 composite：`FinalColor.a < 0.99 && ((0.5 < Status < 2.5) || Status > 3.5)`；
>    并逐行移植 L146-155 的**负能量状态位**处理（小数 `.2` → 背景反相；
>    `iWhitehole==0` 时置零）。项目自有的 `iDiskScatter` 前向散射仍只作用于常规星空
>    分支（NPGS composite 的白洞分支无此项，保持一致）。
> 2. 惰性天空盒连带：白洞路径采的是「上一个宇宙」`(u+2)%3` 那对盒，`kerr.lazySkybox`
>    默认只载 U0/A0 → 亮斑会采到 fallback 盒。`ensureSkyboxesForUniverse` 增加
>    `whitehole` 参数，开启时补载该对并重绑（`loadedWhitehole` 参与去重判定）。
>
> **验证**：SpvCheck 离线编译通过（**409,340 字节** SPIR-V）；`mvn compile` 通过；
> **用户实机验收通过**（2026-10-06，同机位南极对比：中心亮斑出现、与原版一致）。
>
> > **方法论补注**：本批根因不在物理代码（TraceRay/universeoffset 全对），而在**入口
> > shader 门控**——本移植版把 main 抄自普通版 `BlackHole.frag`，而 verbatim 默认走
> > `BlackHole_composite`，两套入口的背景门控差一个 `|| Status > 3.5`。第九批在
> > TraceRay 里排查半天无果（该处本来就没错）。教训：**对"同一逻辑在 NPGS 里有两份
> > 入口实现"要保持警觉**，对拍时要先确认对的是哪一份入口。
>
> **工具注**：`SpvCheck`（测试工具）原先经 `MemoryStack` 传源码，其默认帧仅 64KB，
> kerr.frag ~250KB 直接 `OutOfMemoryError("Out of stack space")`——改为
> `MemoryUtil.memUTF8` 堆外分配（与运行时 `ShaderCompiler` L78-80 同款处理）。
>
> **状态(2026-09-19):Phase 5 完成并合并 dev(d791fec),用户运行反馈两轮已闭环
> (77f0798 修 UBO 槽位错位/标架折叠乘序/高分屏缩放;b967e39 修 G 进出测地模式视角翻到身后)。
> 另:8edb7e0(2026-09-23)修测地模式(mode=-1)星空背景旋转 90°——EscapeDir 出口多乘一次
> LocalToWorldRot,与 0 模式出口坐标系不一致。
> 4dccb6b 补齐 Kerr Disk 面板(KN 电荷 Q\*/μ/色彩后处理/热折射 GUI + KN ISCO 数值解,
> 详见 §四 Phase 2.5 追加);blackhole.frag 已按 Phase 1.5 标准重构(d419b19,513→446 行);
> Main 拆分 Panels/InputController(9e31d80)、AppLog 日志规范化(d6953ce)、窗口尺寸入配置(8bb661d)。
> **Phase 4 可选项已全部完成(2026-09-19:prepass+composite、Kerr bloom、喷流接线+GUI、
> 双天空盒、PerlinNoise 哈希 LUT;热折射 GUI 随 Phase 2.5 面板补齐提前完成)。**
> eng.properties 键:kerr.\* 31 键 + skybox.mountainsSeas(Phase 2.5 计划"eng.properties
> 默认值"落地)。后续增强需另立计划(如多宇宙变体星空的 iInWhichUniverse 接线)。
> **已立项(2026-09-26):** `kerr_bloom_colorblend_plan.md`(NPGS Bloom mip 树+ColorBlend
> 调色链移植——Bloom.comp/ColorBlend 不在 §1.1 移植清单,系当时盲区;对比 B站 观感差距的大头)
> 与 `kerr_multiverse_skybox_plan.md`(6 套 cubemap 选层恢复 + iWhitehole 接线)。
>
> **状态(2026-09-05):Phase 3 验收通过(用户运行确认:TAA 静止收敛/运动无拖影、
> Kerr 测地圆轨道带自旋多普勒、高速冲盘蓝移,均符合预期)。**
> Phase 2 / 2.5 / 3 已完成并提交(77f560d、5a834ad、14ae1e0)。
> Phase 2 排障期间修复的第二枚"盘黑屏"根因:DiskColor 温度公式错(详见卷首 ⑤ 与 Phase 2 实现记录)。
> 附:两模式盘观感差异的结论——blackhole.frag 与 NPGS 用同一温度剖面族+T⁴ 亮度律,
> 但史瓦西侧叠加了内环 ×21 增亮、三重多普勒、不透明丝缕、自定义调色板等美术放大器;
> 克尔侧(NPGS)近乎裸物理,故同峰值温度下前者亮得多。**克尔侧更接近真实薄盘物理**
> (窄热亮环+大片暗盘面),史瓦西侧是视觉夸张,二者只做定性对比。
> Phase 1.5 结构化重构已提交(kerr.frag ≈3030 行)。Phase 2 本轮改动(均在 KerrRender.java,着色器零改动):
> ① 清除上次会话残留调试代码(脏话命名的日志宏/UBO dump);
> ② `iInterRadiusRs` 从写死 2.0 改为 **ISCO(a\*) 动态计算**(BPT 顺行解析式,Q=0;
>    a=0→3Rs,a=0.998→0.632Rs(外视界+0.1 生效),对应 NPGS `calculate_KN_ISCO` 截图任务路径);
> ③ `iBlackHoleTime` 补 **c·s/Rs 单位换算**(NPGS: `GameTime*c/Rs`,Sgr A* 系数≈6.82e-3/s;
>    此前直传动画秒,盘纹转速快约 147 倍);
> ④ **盘不可见根因修复:`iDarkmut` 0→0.5** —— DiskColor 内 `SampleColor.a *= Darkmut`,
>    NPGS 交互模式默认 0=盘关(靠菜单开启),注释值 0.5 才是可见态;此前截图只见光子环+透镜星空即此故。
> ⑤ **盘全黑根因修复(第二次运行"黑的"):DiskColor 温度公式错。** Phase 1.5 去参数化重构时,
>    DiskColor 内把 NPGS 传参的 `DiskArgument`/`PeakTemperature` 本地重算成了
>    `pow(iAccretionRate*30,0.25)` 和写死 `6500.0`,而正确值是
>    `DiskArgument=kPhysicsFactor/M·(μ/η)·ṁ≈1e21`、`PeakTemperature=(DiskArgument×0.05665278)^0.25≈1e5 K`。
>    错误公式下盘亮度 ∝ pow(DiskTemperature/6500,4)≈1e-19 → 盘 alpha(Darkmut=0.5 生效)遮暗背景、
>    自身却全黑。已提取共享函数 `ComputeDiskArgument()`(kerr.frag),TraceRay 内原死代码块删除。
> 验收清单(需人工运行观察,程序全屏较卡,建议 NumPad +/- 降分辨率):
> a=0 与史瓦西盘像定性一致;倾斜视角盘两侧亮度不对称随 a 增大增强;ISCO 内缘随 a 增大内移(盘更贴黑洞)。
>
> **状态(2026-09-04):Phase 1 星空渲染已通过调试验证,UBO 写入第六枚黑屏根因已修复。**
> 已修复黑屏根因(六枚):① `iFovRadians` 双重换算;② `iInverseCamRot` 语义反转;
> ③ `renderArea` 悬垂指针(Dangling Pointer);
> ④ tonemap NaN;⑤ TAA 历史 NaN 自愈;
> ⑥ **`Matrix4f.get(ByteBuffer)` 不推进 position** —— JOML 的 `Matrix4f.get(ByteBuffer)`
> 将 mat4x4 写入 buffer 后**不移动 position**,后续 `putFloat`/`putInt` 从 offset 0 开始,
> 整个 UBO 数据全部从零覆盖 → 着色器读到全零 → 黑屏。
> 修复: `camToWorldRot.get(bh)` 后加 `bh.position(64)` 手动推进;
> 另加 `vmaFlushAllocation` 防御非 HOST_COHERENT 回退。
> 另加防御: UV y 翻转收敛、`KERR_SKIP_RAYMARCH` 调试开关已移除。
>
> **下一步:Phase 1.5 kerr.frag 结构化重构(见 §四 Phase 1.5)** —— 拆分超长函数、提取重复代码、物理删除死代码,
> 目标单函数 ≤150 行、Release 版编译期剔除调试分支、帧耗时不劣化。
>
> **状态(2026-09-01):计划书,待评审。** 目标:把 `D:\CodingSpace\IdeaProjects\NPGS` 的克尔黑洞
> (Kerr–Schild 度规,支持自旋 a、可扩展电荷 Q)移植进本项目,与现有史瓦西实现**可切换**。
>
> **三条硬约束(用户要求):**
> 1. 可与当前施瓦西切换,提供 GUI 控件;
> 2. **不改动** `resources/shaders/blackhole.frag`(已 513 行,不宜再膨胀);
> 3. **不改动** `vulkanb/eng/graph/BlackHoleRender.java`,另起 `KerrRender` 类。
> 附带推论:`blackhole.vert`/`bloomComposite.frag` 只**复用读取**不修改;
> 允许改动的 Java 侧仅限:`Render.java`(最小分发点)、`Scene.java`(模式状态)、
> `Main.java`(GUI)、`EngCfg`/`eng.properties`(默认参数)、vk 包(如需新增基础设施类)。

---

## 一、源与目标的架构对照

### 1.1 NPGS 侧资源清单(源)

| 文件 | 行数 | 职责 | 移植策略 |
|---|---|---|---|
| `NPGS/Sources/Engine/Shaders/BlackHole_common.glsl` | 5025 | **核心**:度规/测地线/盘/红移/星空/TraceRay 主循环 | 裁剪后移植为 `kerr.frag` 主体 |
| `BlackHole.frag.glsl` | 43 | 驱动 main():TraceRay → 背景 → tonemap → TAA 混合 | 移植(并入 kerr.frag 尾部) |
| `Common/CoordConverter.glsl` | 31 | 坐标系小工具 | 内联进 kerr.frag 头部 |
| `Common/NumericConstants.glsl` | 小 | 常量 | 同上 |
| `BlackHole_prepass.frag.glsl` | 28 | 低分辨率扭曲场预计算 | **第一版裁掉**(Phase 4 性能优化) |
| `BlackHole_composite.frag.glsl` | 175 | prepass 边缘感知放大合成 | **第一版裁掉** |
| `BlackHole.comp.glsl` | 488 | compute 变体 | 不移植 |
| `Application.cpp` / `GameScreen.cpp` / `DataStructures.h` | — | 宿主侧 BlackHoleArgs/GameArgs 填充参考 | 对照移植(字段语义/默认值/动态计算) |

### 1.2 NPGS 着色器输入布局(移植的参数面)

```
set 0 binding 0  uniform GameArgs {       // 帧级
    vec2 iResolution; float iFovRadians; float iTime;
    float iGameTime; float iTimeDelta; float iTimeRate; }

set 0 binding 1  uniform BlackHoleArgs {  // 场景级(巨 UBO,~40 字段)
    mat4x4 iInverseCamRot;                // 相机系旋转(世界→相机)
    vec4 iBlackHoleRelativePosRs;         // 黑洞位置(相机系,Rs 单位)
    vec4 iBlackHoleRelativeDiskNormal;    // 盘法向=自旋正方向(相机系)
    vec4 iBlackHoleRelativeDiskTangen;    // 盘切向(相机系)
    vec4 iCameraVelocity;                 // 相机坐标速度(观者模式 2/3)
    vec4 ie1_up/ie2_up/ie3_up/iU_up;      // 四维标架(仅 mode=-1)
    int  iCamDataCoordisOutgoing;         // ingoing/outgoing KS 片
    int  iDEBUG/iPrepass/iWhitehole/iInWhichUniverse/iGrid
        /iEnableHeatHaze/iEnableShadowCulling/iObserverMode
        /iPolarization/iUseImageDisk;
    float iQuality; float iUniverseSign; float iBlackHoleTime;
    float iBlackHoleMassSol; float iSpin; float iQ;   // ← 自旋/电荷
    float iMu; float iAccretionRate;
    float iDensestar*(5 个);              // 致密星 → 裁
    float iInterRadiusRs/iOuterRadiusRs/iThin/iHopper; // 盘几何
    float iBrightmut/iDarkmut/iReddening/iSaturation;  // 盘外观
    float iBlackbodyIntensityExponent/iRedShiftColorExponent
         /iRedShiftIntensityExponent;                    // 盘物理指数
    float iImageRotationSpeed;            // 贴图盘 → 裁
    float iPolarizationAngle;             // 偏振 → 裁
    float iHeatHaze/iBackgroundBrightmut;
    float iPhotonRingBoost/iPhotonRingColorTempBoost/iBoostRot;
    float iJet*(4 个);                    // 喷流(保留代码,默认关)
    float iBlendWeight; }                 // TAA 权重

set 1 binding 0  texture2D iHistoryTex;   // TAA 历史(samplerless texelFetch)
set 1 binding 1-6 samplerCube iBackground0/1/2 + iAntiground0/1/2;  // 宇宙变体星空×3(Universe0/1/2,各面 1024²) + 反宇宙对应盒×3(Antiverse0/1/2,各面 2048²)
set 1 binding 9  sampler2D iImageTexture; // 贴图盘 → 裁
```

### 1.3 与本项目现状的关键差异

| 维度 | 本项目(史瓦西) | NPGS(克尔) | 适配决策 |
|---|---|---|---|
| 传参方式 | push constants 224B(已满) | 两个 UBO(GameArgs+BlackHoleArgs) | KerrRender 走 **UBO**(VkBuffer 持久映射,参照 `prevCamUbo` 模式) |
| 度规 | 史瓦西(η+f·l⊗l, a=0) | Kerr–Schild(自旋 a、Y 为自旋轴,`transformKerrSchild_YSpin`) | 照抄;**自旋轴=Y 与本项目盘平面 XZ 天然一致** ✓ |
| 光线积分 | Euler 辛格式(知乎文章版) | RK4 + Hamiltonian 守恒修正(`StepGeodesicRK4_Optimized`、`ApplyHamiltonianCorrection`) | 照抄(慢但精确,见风险 4) |
| 射线生成 | vert 反投影(invView/invProj) | frag 内 iFovRadians+iInverseCamRot | **复用本项目 vert 思路**:kerr.vert 复制 blackhole.vert(输出 rayOrigin/rayDir),`GetInitialMomentum` 入口从"屏幕 UV+相机矩阵"小改为"直接接收 O/D" |
| 盘模型 | 体积盘+开普勒 Ω(a=0) | 体积盘+噪声+开普勒 Ω 含 a(`GetKeplerianAngularVelocity`)、喷流、致密星、贴图盘、网格 | 移植盘+红移;喷流留代码默认关;其余裁 |
| 红移 | 多普勒×引力合并 RedShift | 三色波段分别频移(`WavelengthToRgb`)+盘频移指数 | 照抄 |
| 星空 | 单层 CubeTexture | 三套宇宙变体星空×3 + 反宇宙盒×3(同族同分辨率;按 `int(iInWhichUniverse+3+..)%3` 选层,非 LOD 分层) | **裁为一层**:kerr.frag 中 `iBackground1/2` 用宏别名到 `iBackground0`;`iAntiground*`(白洞/反宇宙)stub 成 `iBackground0`(反正 iWhitehole=0 不会采样) |
| TAA | ping-pong 历史+重投影矩阵 UBO | ping-pong 历史+blendWeight 纯混合(无重投影) | 照 NPGS 模式(无 prevCam UBO,`iBlendWeight` 动态算) |
| Bloom | bloomComposite pass | `ApplyToneMapping` 内联在 frag | 第一版用 NPGS 内联 tonemap 直写交换链(无 bloom);复用 bloomComposite 列为 Phase 4 |
| 相机动力学 | 史瓦西 GeodesicIntegrator | 完整克尔观者模式(静态/落体/三维速度/四维标架) | **第一版 iObserverMode=0 静态观者**,β=0;克尔测地线相机列为 Phase 5(需移植 NPGS `Application.cpp` 的 GeodesicIntegrator 命名空间,本项目 Java 版即取其 a=Q=0 特例而来) |
| 着色器组织 | 单文件 | `#include` 组织 | 本项目 ShaderCompiler **无 include 支持** → 单文件内联(kerr.frag ≈ 4500 行,shaderc 无压力) |

---

## 二、总体设计

### 2.1 新增/改动文件总表

| 文件 | 动作 | 说明 |
|---|---|---|
| `resources/shaders/kerr.frag` | **新建** | CoordConverter/NumericConstants 内联 + 裁剪版 BlackHole_common + NPGS 式 main() |
| `resources/shaders/kerr.vert` | **新建** | 复制 blackhole.vert(反投影射线生成,输出 inRayOrigin/inRayDir) |
| `src/main/java/vulkanb/eng/graph/KerrRender.java` | **新建** | 克尔渲染器(管线/UBO/TAA ping-pong/resize/cleanup),结构对照 BlackHoleRender 但参数面全走 UBO |
| `src/main/java/vulkanb/eng/scene/Scene.java` | 修改 | +`SpacetimeMode` 枚举字段(SCHWARZSCHILD/KERR,默认前者)、+`KerrParams`(spin/quality/盘参数,GUI 写、KerrRender 读) |
| `src/main/java/vulkanb/eng/graph/Render.java` | 修改(最小) | +KerrRender 引用与 active 分发(见 2.3) |
| `src/main/java/vulkanb/Main.java` | 修改 | GUI:Spacetime 单选 + Kerr 参数滑条;G 键在 Kerr 模式下禁用测地相机(提示) |
| `src/main/resources/eng.properties` + `EngCfg` | 修改 | Kerr 默认参数(spin/quality/盘内外半径等) |
| `blackhole.frag` / `blackhole.vert` / `bloomComposite.frag` / `BlackHoleRender.java` | **零改动** | 硬约束 |

### 2.2 渲染器切换机制(最小侵入 Render.java)

- `Render` 持有 `BlackHoleRender schwarzschild`、`KerrRender kerr`、`SpacetimeMode active`;
- **lazy 初始化**:构造时只建 active 模式的 renderer(Kerr 的 TAA 历史是
  4 张 R32G32B32A32_SFLOAT 全屏图,1080p ≈ 266MB,双份常驻不值得);
- `render()` 每帧检查 `scene.getSpacetime() != active` → `device.waitIdle()` →
  `cleanup 旧 renderer` → `init 新 renderer`(交换链格式/extent 现取)→ 切换完成;
  GUI 改模式 → 下一帧生效,一次黑帧可接受(或首帧 TAA 强制重置本就有)。
- 传递路径沿用项目已有先例(`BlackHoleRender.BaseTemperature` 静态量的模式升级为
  Scene 状态):**Main(GUI)→ Scene → Render**,不新增 EngCtx→Render 依赖。

### 2.3 KerrRender 结构(对照 BlackHoleRender 的差异点)

```
KerrRender
 ├─ init(vkCtx, engCtx):编译 kerr.vert/kerr.frag
 │   ├─ desc layouts: set0 = [b0 GameArgs UBO, b1 BlackHoleArgs UBO]
 │   │                 set1 = [b0 TAA history tex, b1 skybox(+宏别名 1/2/antiground)]
 │   ├─ UBO:gameArgsUbo(64B)+blackHoleArgsUbo(~600B,std140 打包器)×MAX_IN_FLIGHT
 │   │     持久映射,每帧重写(参照 prevCamUbo 模式)
 │   ├─ 管线:colorFormats={TAA 历史 R32G32B32A32_SFLOAT},useBlend=false
 │   └─ TAA 历史 ping-pong(照抄 BlackHoleRender.createTaaResources 的插槽双缓冲)
 ├─ render(vkCtx, cmdBuffer, engCtx, frame, imageIndex):
 │   每帧打包 UBO(字段映射见 §3)→ beginRendering → bind sets → 全屏 quad → end
 │   → 历史 ping-pong barrier(照抄)
 │   → 直写交换链 pass?——第一版 frag 内已 tonemap,主 pass 直接写交换链即可,
 │      TAA 历史兼作"上一帧颜色"(iHistoryTex)。【设计点:NPGS 主 pass 写历史+读历史,
 │      结果即最终色;交换链输出=复制历史?——用第二个全屏 blit 或直接主 pass 写交换链+
 │      历史各一次(vkCmdBlitImage 或 bloom 式 pass)。实施时择简:主 pass 写 TAA 历史,
 │      再 vkCmdBlitImage 历史→交换链,2 行命令搞定】
 ├─ resize / cleanup:同 BlackHoleRender 模式
 └─ 参数源:Scene.kerrParams(spin 等) + EngCfg 默认值
```

### 2.4 GUI(Main.java)

Controls 区追加:

- **Spacetime 单选**:`ImGui.radioButton("Schwarzschild", ...)` / `radioButton("Kerr (a*)", ...)`
  → `scene.setSpacetime(...)`;切换后 Kerr 参数滑条才启用(史瓦西模式下灰显);
- Kerr 滑条:**Spin a\*** 0..0.998(0 即退化为史瓦西几何,可作交叉验证)、
  **Quality** 0.5..1.0(NPGS iQuality,子像素采样数)、盘内外半径;
- 遥测面板:Kerr 模式下显示 "a* = 0.90 | Kerr camera dynamics: N/A (static observer)"。

### 2.5 裁剪清单(kerr.frag 相对 BlackHole_common 的删减)

| 功能 | 处理 | 手段 |
|---|---|---|
| prepass/composite 双 pass | 删 | 不移植两个文件;`iPrepass=0` 恒定,相关分支随 prepass 代码一起删 |
| 致密星 DensestarColor | 删 | `iDensestar*` 字段保留占位(UBO 布局不动,填 0) |
| 贴图盘 ImageDiskColor + iImageTexture | 删 | 同上,`iUseImageDisk=0` |
| 偏振 SolvePolarization/iPolarization | 删 | `iPolarization=0` |
| 网格 GridColor/GridColorSimple | 删 | `iGrid=0` |
| 落点白点 DrawFallingWhiteDot | 删 | 不调用 |
| 白洞/最大延拓/反宇宙 | 代码保留但 `iWhitehole=0`、`iUniverseSign=+1`,iAntiground* 绑定到同一 skybox | 不删代码(分支不会走),绑定层 stub |
| 喷流 JetColor | **保留**(克尔特色),`iAccretionRate` 默认压低关闭 | 代码保留 |
| 热折射 HeatHaze | 保留代码,默认 0 | GUI 后开 |
| 三套宇宙变体星空 | 裁一层(本项目 iInWhichUniverse 恒 0 → 恒选 0 号层) | `#define iBackground1 iBackground0` 等宏别名 |
| 四维标架观者(mode=-1) | 保留代码,不启用 | `iObserverMode=0` |

> 原则:**UBO 布局字段一个不删**(std140 偏移与 NPGS 原版一致,方便对照排错),
> 只删函数体与分支;裁剪以"不调用"优先于"物理删除",防止牵连。

---

## 三、BlackHoleArgs 字段 → 本项目数据源映射

| 字段 | 来源 | 备注 |
|---|---|---|
| iResolution | swapchain extent | |
| iFovRadians | EngCfg fov | vert 反投影模式下着色器内仅日志用,可填同值 |
| iTime / iGameTime | 启动秒 / 动画时间(同现有 pushConst time) | |
| iTimeDelta / iTimeRate | 帧间隔(秒) / EngCfg timeRate | |
| iInverseCamRot | `camera.getViewMatrix()` 去平移的旋转部分 | mat4,每帧 |
| iBlackHoleRelativePosRs | `viewRot × (世界黑洞位 − 相机位)` = `viewRot × (−camPos)` | 黑洞在原点 |
| iBlackHoleRelativeDiskNormal | `viewRot × (0,1,0)` | 盘法向=Y=自旋轴 |
| iBlackHoleRelativeDiskTangen | `viewRot × (0,0,1)` | 盘切向(与法向正交即可) |
| iCameraVelocity | `(β, 0)`,第一版 β=0 | 后续接观者模式 |
| e1/e2/e3/U 标架 | 不填(零) | mode=0 不读 |
| 10 个 int 开关 | 见 §2.5(iEnableShadowCulling=1,其余 0) | |
| iQuality | KerrParams(GUI) | |
| iUniverseSign / iWhitehole / iInWhichUniverse | +1 / 0 / 0 | |
| iBlackHoleTime | 动画时间(同 iGameTime 语义) | |
| iBlackHoleMassSol | 常量(如 4.3e6,人马 A*) | 仅影响单位换算展示 |
| **iSpin** | **KerrParams(GUI 0..0.998)** | 核心参数 |
| iQ | 0(Kerr;KN 留扩展) | |
| iMu / iAccretionRate | EngCfg 默认,GUI 可后加 | |
| iInterRadiusRs / iOuterRadiusRs / iThin / iHopper | EngCfg 盘参数 / 常量 0.1 / 0.3 | |
| 各外观/指数乘数 | NPGS 默认值(查 GameScreen.cpp) | 先照抄后调 |
| iBlendWeight | 动态:相机移动→高(如 0.6),静止→低(0.05~0.1) | NPGS 策略,实施时对照 |

**std140 打包是本项目最大坑位**(见风险 2):`KerrRender` 内写一个
`packBlackHoleArgs(...)` 逐字段 put,字段顺序/对齐严格按 NPGS 声明;int 与 float 混排处
std140 各占 4B 但 vec4 成员前须 16B 对齐——NPGS 原布局已满足(全 vec4 开头),照抄顺序即可。

---

## 四、分阶段实施

### Phase 0:着色器搬运与编译打通(纯静态,无 Java)

- 建 `kerr.vert`(复制 blackhole.vert,仅改名);
- 建 `kerr.frag`:内联 CoordConverter/NumericConstants → 按裁剪清单搬运 common →
  搬 NPGS main()(TraceRay+背景+tonemap+TAA 混合)→ `GetInitialMomentum` 入口改为
  接收 vert 传来的 rayOrigin/rayDir;
- 临时用一个只调 `ShaderCompiler.compileShaderIfChanged` 的 main 或 mvn 编译期校验,
  保证 shaderc 0 error(类型/名字/删函数牵连全在此阶段暴露);
- **验收**:两文件 spv 编译通过;不接入渲染。

### Phase 1:KerrRender 骨架 + 光线弯曲(无盘)

- KerrRender.java 全结构(UBO 打包器/管线/描述符/TAA 历史占位可先只读不写混合权重=1);
- 渲染内容:克尔阴影 + 星空(频移先关或直接看效果);
- Render.java lazy 分发 + Scene.SpacetimeMode;GUI 单选(此时即可切换两模式);
- **验收**:
  1. `a=0` 时阴影角半径与史瓦西光子球 2.6Rs 视角一致(定性对比两模式截图);
  2. `a=0.9` 阴影呈 D 形不对称、一侧光子环更亮(多普勒集束);
  3. 两模式 GUI 热切换各 10 次无崩溃/无验证层报错/无显存泄漏(任务管理器观测)。

### Phase 1.5:kerr.frag 结构化重构(性能/可维护性,不改变行为)

> 当前 kerr.frag ≈ 5000 行,TraceRay 单函数 1180 行,多个 200+ 行巨型函数,维护极难且寄存器压力大。
> 本阶段**仅做代码组织与死代码清理,不改算法/数值**,通过 `mvn compile` + 双模式冒烟 30s 回归。

#### 1.5.1 超长函数拆分(目标:单函数 ≤ 150 行)

| 原函数 | 行数 | 拆分策略 | 新建子函数 |
|---|---|---|---|
| `TraceRay` | 1180 | 按阶段拆:初始化/主循环/收尾 | `TraceRay_Init`, `TraceRay_StepLoop`, `TraceRay_Finalize` |
| `DiskColor` | 343 | 按逻辑层拆:几何裁剪/步进/采样/积累 | `DiskColor_Cull`, `DiskColor_Step`, `DiskColor_Sample`, `DiskColor_Accumulate` |
| `GetHazeForce` | 187 | 分层:盘/喷流/合成 | `GetHazeForce_Disk`, `GetHazeForce_Jet`, `GetHazeForce_Combine` |
| `GetInitialMomentum` | 164 | 分支拆:观者模式/系别 | `GetInitialMomentum_Static`, `GetInitialMomentum_Geodesic`, `GetInitialMomentum_FourVec` |
| `transformKerrSchild_YSpin` | 115 | 矩阵分块写 | `TransformKS_Position`, `TransformKS_Momentum` |
| `GridColorSimple` | 303 | **删除**(iGrid=0,见 §2.5) | — |
| `GridColor` | 207 | **删除** | — |
| `DensestarColor` | 210 | **删除** | — |
| `DiskColortoRed` | 240 | **删除** | — |
| `DiskColortoBlue` | 229 | **删除** | — |
| `ImageDiskColor` | 171 | **删除** | — |
| `DrawFallingWhiteDot` | 80 | **删除** | — |

> 原则:拆分后**内联**(`inline` 或直接展开)防止调用开销;保持 `std140` UBO 与 NPGS 布局完全一致。

#### 1.5.2 重复代码提取为共享工具函数

| 重复模式 | 出现位置 | 提取为 |
|---|---|---|
| 几何标量计算 `ComputeGeometryScalars` | TraceRay、DiskColor、Grid*、GetInitialMomentum、Haze、Shadow | 保留单一实现,添加 `fade` 分支参数 |
| 几何梯度计算 `ComputeGeometryGradients` | TraceRay、ApplyHamiltonianCorrection | 保留单一实现 |
| 步长自适应公式 | TraceRay、DiskColor、Haze | `ComputeAdaptiveStepSize(pos, a, Q, OuterRadius)` |
| 赤道穿越检测 + 宇宙符号翻转 | TraceRay(2 处)、GridColorSimple、JetColor | `CheckEquatorialCrossing(inout CurrentUniverseSign, LastPos, CurrPos, a)` |
| 盘/喷流遮罩采样 | Haze、DiskColor | `SampleDiskMask(pos, InterR, OuterR, Thin, Hopper)`, `SampleJetMask(...)` |
| 偏振基底构造 | GetInitialMomentum、TraceRay(偏振分支) | `BuildPolarizationBasis(...)` — 仅 iPolarization≠0 时编译 |
| Kerr-Schild ↔ Boyer-Lindquist 坐标变换 | transformKerrSchild_YSpin、影子计算 | `KS_to_BL(vec3 pos, float a)`, `BL_to_KS(...)` |

#### 1.5.3 死代码彻底物理删除(裁剪清单 §2.5 落地)

以下函数**整体删除**(含内部注释、调试块),UBO 字段保留占位:

- `DiskColortoRed` / `DiskColortoBlue` — 蓝移/红移盘变体,未接线
- `DensestarColor` — 致密星,`iDensestarsurfaceR=0` 永不触发
- `GridColor` / `GridColorSimple` — 网格,`iGrid=0`
- `DrawFallingWhiteDot` — 落点白点,仅调试
- `ImageDiskColor` — 贴图盘,`iUseImageDisk=0`
- `SolvePolarization` / `GetWalkerPenrose` — 偏振,`iPolarization=0`
- `DebugInitialMomentum` — 仅 `iDEBUG=1` 用
- `Fbm_Standalone` / `GetIngoingNullParticlePos` / `GetDotDistSq` — 无任何调用
- `GetDropFrameAngle` / `GetShadowHalfAngleRN` / `SolveCubicMaxReal` / `SolveQuarticU` — 影子剔除分支(`iEnableShadowCulling=0`)未走,暂留桩函数 `return 0;`

#### 1.5.4 性能相关微调

- `PerlinNoise` 3D 噪声:改为 **预计算 3D 纹理 LUT**(64³ RG8,Phase 4 做),当前标记 `// TODO: replace with texture3D lookup`
- `HAZE_PROBE_STEPS` / `HAZE_STEP_SIZE` 等宏:改为 `const int/float` 便于循环展开
- `MaxStep` 公式:提取为 `ComputeMaxStep(iSpin, iQ, iWhitehole, bIsNakedSingularity)` 单一实现
- `iDEBUG` 分支:改为 `#ifdef KERR_DEBUG` 编译期剔除(Release 版 `-DKERR_DEBUG=0`)

#### 验收

1. `shaderc` 0 error/0 warning; `mvn compile` 通过
2. 双模式各跑 30 帧无验证层报错,帧耗时 **不劣化** (对比重构前基线)
3. `a=0` 阴影/光子环与重构前逐像素一致(截图 diff)
4. 文档同步:本节写入计划书;函数目录更新至 §二.1

---

### Phase 2:吸积盘 + 红移

> **实现记录(2026-09-05):** 着色器侧 DiskColor/三色波段红移/ApplyToneMapping 已在 Phase 0/1.5
> 随 common 整体移植并接线(TraceRay 主循环内 `IsAccretionDiskVisible`→`DiskColor` 调用链、
> `universeoffset=0` 时 `33%3==0` 分支恒真),UBO 37 个 float 与 kerr.frag 声明逐字段一致。
> 本阶段实际改动全在 KerrRender.java:ISCO(a\*) 动态内缘、iBlackHoleTime 单位换算、
> **iDarkmut=0.5(根因:NPGS 交互默认 0=盘透明不可见)**、清除会话残留调试代码。见卷首状态块。

- 打通 DiskColor 体积盘(噪声/开普勒 Ω 含 a/内外半径)、多普勒+引力红移三色波段、
  ApplyToneMapping;
- **验收**:a=0 时盘像与史瓦西模式定性一致;倾斜视角下盘两侧亮度不对称随 a 增大而增强;
  ISCO 内缘随 a 增大向内(视觉盘更贴黑洞)。

### Phase 1.5 扩展(2026-09-05):blackhole.frag 同标准重构

> 用户指示将 Phase 1.5 标准套用到史瓦西侧 blackhole.frag(原 Kerr 移植硬约束
> "blackhole.frag 零改动"自本次起对该重构豁免,算法/数值不动):
> ① 死代码物理删除:starfield 程序化星空(主路径早已改 uSkybox 采样)及其独占的
>    hash/noise3D,共约 66 行;
> ② 重复提取:DiskColor 内两层盘采样(Color0/Color1 逐行重复 40 行)提取为
>    DiskLayerColor(FadeOffset 0/0.5 区分相位);死变量 PosOnDisk/PosR/LastDis 清除;
> ③ 超长函数拆分:main 156 行 → AdvanceToBoundingSphere / BackgroundColor / ApplyTAA
>    三个阶段函数 + main 约 110 行;全文件最大函数 105 行(≤150 达标)。
> 513 → 446 行,shaderc 0 error。行为不变(逐公式等价搬运)。

### Phase 2.5:Kerr 参数面板(2026-09-05)

- 新建 `Scene.KerrParams`(GUI 写、KerrRender 每帧读、打入 UBO):吸积率/盘外半径/厚度/斜率、
  Brightmut/Darkmut/三指数(温度-亮度、频移-色温、频移-亮度)、背景亮度、Quality;
  默认值对应用户手调现状(brightmut=2.0、backgroundBrightmut=0.0);
- 面板 `Kerr Disk` 仅 Kerr 模式渲染(随模式显隐,受 F1 门控),拖动下一帧生效;
- 只读遥测:ISCO 内缘(`KerrParams.iscoInnerRadiusRs`,与 UBO 打包同源)、
  盘峰值温度(`peakTemperatureK`,kerr.frag ComputeDiskArgument 的 Java 镜像);
- KerrRender 的 ISCO 计算与质量常量收敛到 KerrParams 单一实现;
  `Scene.getKerrSpin/setKerrSpin` 保留为 `kerrParams.spin` 委托(旧调用点零改动);
- **Controls 滑条随模式拆分**:Temp + 测地线四件套(TimeRate/Thrust/v0/Orbit tilt)
  仅史瓦西模式渲染;Spin a* 移入 Kerr Disk 面板,并新增 `Disk TimeScale` 滑条——
  盘时间改为真实帧间隔×倍率的累计器(`KerrParams.diskTimeCsRs`,存 Scene 生命周期,
  渲染器热切换/resize 后盘纹相位连续;dt 钳制 0.1s 防失焦跳变),
  不再借用史瓦西测地线的 timeRate/engCfg 链条;
- **面板重构(2026-09-05 晚,Phase 3 修订)**:测地相机进克尔后其滑条两模式都需要,
  取消"Controls 仅史瓦西"的门控——新增**全模式可见的 Controls 面板**(时空模式单选 +
  Temp + 测地线四件套),遥测面板只留只读数据(Camera/Geodesic/Rendering/Scene);
  Kerr Disk 面板保持随模式显隐,默认位置改 (410,8) 避让遥测;
- **面板补齐(2026-09-06)**:Kerr Disk 增补 BlackHoleArgs 剩余可调项——
  电荷 Q\*(Kerr–Newman 扩展)、比荷 μ(参与盘温标)、背景频移上限 iBackShiftMax、
  红化 iReddening、饱和度 iSaturation、光子环亮度/色温双增亮、不对称增强 iBoostRot、
  热折射开关 iEnableHeatHaze + 强度 iHeatHaze(着色器 2446 行 gate 原生支持,此前 UBO 恒零);
  ISCO 升级为 **KN 数值解**:NPGS calculate_KN_ISCO/get_orbit_energy 移植
  (黄金分割搜索圆轨道能量最低点,Q=0 时回落 BPT 解析式,a=0/q=0 验证精确 3Rs,
  a=0.9 与解析值一致到 1e-3);新增裸奇点判定 isNakedSingularity(a²+Q²>1),
  面板红色警告、ISCO 给 0.5Rs 兜底(着色器 bIsNakedSingularity 分支接管);
  峰值温度镜像公式补 μ 参数。喷流/偏振/贴图盘/网格参数不进面板
  (对应着色器路径未接线或已删,见 §2.5 裁剪清单)。

### Phase 3:TAA + 测地相机 + 收尾

> **实现记录(2026-09-05,按用户修订执行——TAA 仅静止累积、测地相机从可选提前至本阶段):**
> 1. **TAA 策略**:iBlendWeight 改为 前2帧=1.0 / 镜头静止=0.06 / 镜头动=1.0(全量重置)。
>    相比此前的 0.45 部分混合,"只在镜头不动时 TAA"彻底消除运动拖影(与史瓦西侧 iCameraMoved 策略对齐)。
> 2. **测地相机支持克尔时空**(原来列在 Phase 4/5 可选):
>    - `GeodesicIntegrator` 从 a=0 扩展为自旋感知:computeMetric 换成与 kerr.frag
>      ComputeGeometryScalars 严格同式的 KS 度规(轴 Y,f=2Mr³/(r⁴+a²y²),
>      l_x=(rx−az)/(r²+a²) 等),克氏符仍为数值差分故其余代码零改动;
>      KS 半径(闭合式)取代 |x| 用于步长/视界/坠落判定;圆轨道 Ω 升级为
>      顺行 BL 式 Ω=√M/(r^1.5+a√M);新增 setSpin(a*)/getCoordinateVelocity(dx/dt)。
>    - **奇点保护修正**:a≠0 时保护半径改为外视界 r₊+2%(环奇区梯度爆炸会使轨迹
>      数值弹射到 r~10¹⁵,GeoTest 实测;视界内积分无视觉意义);
>    - Main 放开 Kerr 模式 G 键,切换时空不再强制退出测地模式,每帧同步 spin;
>    - KerrRender:测地激活时打包 iObserverMode=2 + iCameraVelocity=KS 坐标速度
>      dx/dt(ingoing 片,U^t=1 比值约定,着色器归一化重构观者四速度)——
>      相机多普勒/光行差由度规严格处理,静态时退回 mode 0。
>    - GeoTest:A/B(a=0)与基线逐位一致;C(a*=0.9 赤道圆轨道)5 圈半径漂移 1.5%
>      (能量守恒 5e-13,系 KS 系圆轨道初条件为 BL 近似,轻微椭圆化,可接受);
>      D(a*=0.9 俯冲)冻结于视界保护半径,无 NaN。
>    - 遗留:NPGS 全量四维标架输运(ObserverMode=-1)仍列 Phase 5;视线输运仍为欧氏四元数。

- TAA ping-pong + iBlendWeight 动态策略 + 抖动(RandomStep 已在 common);
- KerrParams 全部 GUI 化 + eng.properties 默认值 + 遥测面板 Kerr 读数(a*、模式);
- G 键测地相机在 Kerr 模式禁用并提示;
- **验收**:静止时噪点收敛;相机移动拖影可控;文档(本计划书)状态更新。
- **验收结论(2026-09-05,用户运行确认):通过**——静止噪点收敛、移动无拖影、
  Kerr 测地圆轨道 + 自旋多普勒不对称 + 高速冲盘蓝移均符合预期。

### Phase 5:全量四维标架输运(2026-09-05,分支 phase5-tetrad-camera)

> NPGS ObserverMode=-1 方案落地。**着色器零改动**(kerr.frag 的 mode -1 分支 Phase 0 随
> common 整体搬运时已就位:RayPosLocal 直接取 KS 坐标、RayDir=相机系视方向作标架系数、
> P=U+Σv·E 重构光子四动量),全部工作在 Java 侧:
>
> 1. **GeodesicIntegrator**:RK4 从 8 维(U)扩到 16 维(U+e1/e2/e3 三条标架腿),
>    标架腿按平行输运方程 de/dτ=−Γ(U,e) 与 U 联立推进(X 方程斜率是各阶段 U 值,
>    与 dU/dτ 分开保存——初版实现曾把两者混淆,GeoTest 回归当场拦下);
>    每子步 Gram-Schmidt 重正交清漂移(实测 5 圈误差 1.1e-15,机器精度);
>    initializeTetrad(相机轴基准,进入无跳变)/getFoldedTetrad(鼠标头转以
>    E_i=Σ_j H[ij]·e_j 折叠,与相机姿态推导同式)/tetradOrthoError(测试钩子)。
> 2. **Main**:测地模式鼠标/QE 滚转改转 headQuat(不转相机);每帧相机世界姿态
>    = 输运标架×headQuat 推导(deriveCameraOrientation),视线被参考系拖拽即源于此;
>    G 进入时 headQuat=当前姿态、标架以相机轴初始化,进出无跳变。
> 3. **KerrRender**:测地激活时打包 iObserverMode=-1 + iU_up/ie1/2/3_up +
>    iBlackHoleRelativePosRs=KS 原始坐标(mode -1 约定);静态时回 mode 0。
> 4. Camera 增 getOrientation/setOrientationRaw。
>
> 验收:GeoTest A-D 与 Phase 3 基线逐位一致,E 标架正交性 5 圈 1.1e-15。
> 视觉效果待用户运行确认:高速光行差(瞳孔效应)、视界附近星空拖拽旋转。
> **验收补充(2026-09-19):分支已合并 dev(d791fec)。用户运行反馈两轮均已修复——
> 77f0798(UBO 槽位错位/标架折叠乘序/高分屏缩放)、b967e39(G 进出测地模式视角翻到身后,
> 三处符号/基准错),反馈闭环。**

### Phase 4(可选,按需):性能与增强

- ~~**prepass+composite 低分辨率扭曲场**~~(已完成 2026-09-19,见下方实现记录);
- **多天空盒(宇宙变体选层,非已完成——机制更正)**(2026-09-19,实测核对 NPGS 资源与
  SampleBackground 源码):
  三套 Background(Universe0/1/2)是**按 `int(iInWhichUniverse+3+useContground)%3` 选择的
  "宇宙变体"星空**(与吸积盘可见性门控同款 %3 机制),**分辨率全部相同(各面 1024²)**,
  并非"三层分辨率 LOD";三套 Antiground(Antiverse0/1/2)是其反宇宙对应物(各面 2048²,
  族间差异而非层间差异)。**本项目配置下该功能为死项**:iInWhichUniverse 恒 0 且
  iWhitehole=0(status 4/5 不可达)→ 选层表达式恒等于 0 → 恒采 Universe0,恢复 b2..b6
  绑定与选层逻辑画面不变。前置依赖:先接线 iInWhichUniverse(宇宙区状态+GUI)或白洞模式;
  资源清单:NPGS Assets/Textures/{Universe,Antiverse}{0,1,2}Skybox 各六面 JPG;
  选层逻辑见 NPGS BlackHole_common.glsl SampleBackground(含 useContground 负宇宙偏移)。
  **双盒切换实现记录(2026-09-19,同日更名与扩展):** 不动 %3 选层与着色器——把 NPGS
  Antiverse0Skybox 纹理(六面 2048²,拷入 resources/textures/skybox_mountains_seas/)作为
  **第二套可切换星空("山海盒",mountainsSeas)**,与主星空盒(starfield,Universe0)
  GUI 换绑切换——本项目仅是切换星空贴图,无 NPGS 穿洞换宇宙语义(原按上游语义暂名
  antiverse,经讨论更名)。实现为共享助手 **DualSkybox**(eng/graph/vk):
  BlackHoleRender(set0.b0)与 KerrRender(set1.b1)各自在天空盒槽位换绑,**两渲染器
  均可切换**(施瓦西侧接入属用户对原移植硬约束的明确豁免);开关状态移至
  **Scene**(跨渲染器共用),配置键顶层化 `skybox.mountainsSeas`,GUI Controls 面板
  "Mountains & Seas skybox" 复选框全模式可见。山海盒惰性加载(首次开启上传,
  全 mip 链约 130MB 显存),关闭时 waitIdle→重绑回星空盒→释放;交换前 waitIdle 的原因:
  在飞帧可能引用待变更/待释放的视图。
  **切换 UAF 修复(同日,用户观察"点击后画面无变化"破案):** 原实现
  `rebind(current())` 在标志翻转前取到旧盒——开=重绑旧盒画面无变化;
  关=把山海盒绑上去后立即释放 → 描述符悬垂 → DEVICE_LOST 且报错点随机漂移
  (acquire/submit/fenceWait),伪装成核显 TDR。改为显式重绑目标盒,关闭路径先重绑
  星空再释放;顺带修复 Fence.fenceWait 吞 vkWaitForFences 返回值(DEVICE_LOST 被静默
  延迟到下次提交暴露)。
  验收:着色器零改动;自动循环无头测试(60 帧开/180 帧关,覆盖原 UAF 路径)45s 零错误;
  配置直开冒烟 20s 干净;切换观感与反复开关经用户实测确认;
  Universe1/2 与 Antiverse1/2 未引入(无选层机制时不可见,无意义)。
- ~~PerlinNoise 3D 纹理 LUT~~(已完成 2026-09-19,见下方实现记录)——**Phase 4 至此全部完成**;
- ~~复用 bloomComposite.frag 给 Kerr 加 bloom~~(已完成 2026-09-19,见下方实现记录);
- ~~喷流参数 GUI~~(接线+GUI 已完成 2026-09-19,见下方实现记录);
- 热折射开关(已随 Phase 2.5 面板补齐完成,4dccb6b);
- ~~克尔测地线相机~~(已提前至 Phase 3 完成,2026-09-05;自旋感知 KS 积分 + 观者模式 2);
- ~~全量四维标架输运 ObserverMode=-1~~(已提前至 Phase 5 完成,2026-09-05,
  分支 phase5-tetrad-camera,见 §四 Phase 5 实现记录)。

> **prepass+composite 实现记录(2026-09-19):** 与 NPGS 双 shader 文件(prepass 29 行/
> composite 175 行 + include common)不同,本项目 ShaderCompiler 无 include 支持,
> 采用 **kerr.frag 单文件三路径** 方案——同一 shader module 建两条管线,按 iPrepass 分支:
> 0=原路径逐像素完整追踪(默认);1=半分辨率 prepass(TraceResult 编码为
> 扭曲场附件 xyz=EscapeDir×FreqShift+w=Status + 体积色附件 AccumColor,双附件 R32G32B32A32,
> 体积色比 NPGS 原版 16F 加宽保 HDR);2=composite(十字邻域状态位敏感边界 + 逸出方向场
> 点积<0.99 几何边缘检测,边缘处全分辨率重算,平滑处手动双线性插值——状态位不插值取
> 权重最大邻居;坐标越界统一 clamp)。KerrRender:prepass 管线(半分辨率双附件)/
> 独立 GameArgs+BlackHoleArgs UBO(后者字节复制主拷贝仅改 iPrepass=1,offset 200)/
> NEAREST 采样器/每插槽双附件(写读均在同插槽帧内)/prepass 前后 image barrier。
> 声明双输出+单附件主管线(未消费输出被丢弃)经验证层 30s 冒烟确认合法。
> GUI:Kerr Disk 面板 "Prepass (half-res)" 复选框;配置 kerr.prepassEnabled(默认 false)。
> 验收:shaderc 0 错误;GeoTest A-E 与基线一致;prepass 关/开各 30s 冒烟无验证层报错;
> 视觉对比(边缘质量/帧耗时)待用户运行确认。
>
> **bloom 实现记录(2026-09-19):** 原计划"复用 bloomComposite.frag",实际按其机制新写
> `kerr_bloom.frag`(kerr_copy.frag 升级版,原文件删除)——kerr.frag 在 frag 内已完成
> NPGS 式 tonemap+TAA,历史是 LDR 域,与 NPGS 的 bloom 作用域一致,故不需要史瓦西侧的
> HDR 域二次 tonemap,只保留亮部提取(iBloomThreshold,默认 0.7)+ 黄金角圆盘模糊
> (24 tap 与史瓦西同参)+ 强度叠加(iBloomStrength,默认 0.5;0=逐像素等同原拷贝 pass)。
> 参数走 push constants(8B,frag 阶段,首次在克尔侧引入 push const)由 KerrParams 驱动;
> Pass B 由"拷贝管线"升级为"Bloom 合成管线"(copy 系标识全量更名 bloom 系)。
> GUI:Kerr Disk 面板 Bloom 区(Strength/Threshold 滑条);配置 kerr.bloomStrength/bloomThreshold。
> 验收:shaderc 0 错误;prepass+bloom 全开 30s 冒烟无验证层报错;观感待用户运行确认。
>
> **喷流接线+GUI 实现记录(2026-09-19):** Phase 0/1.5 移植版 JetColor 签名已与 DiskColor
> 同构(位置/动量插值 + ingoing 换系自理),不再依赖 NPGS 原版调用点的 ingoing 系光线方向
> 变量(该调用在 NPGS 上游本就是注释状态)——TraceRay 盘调用点直接恢复调用:
> `IsJetVisible(iAccretionRate, iJetBrightmut)` 门控(吸积率≥1e-2 且亮度>0)→
> JetColor(Result, X, LastX, P_cov, LastP_cov, E_conserved, isoutgoing, RayMarchPhase)。
> UBO 四个 iJet\* 字段从写死(4/1/0/3)改为 KerrParams 驱动;默认 jetBrightmut=0 保持关闭。
> GUI:Kerr Disk 面板 Jet 区(Jet brightness 0..3 / shift→bright exp 0..8 / saturation 0..1 /
> shift max 1..8);配置 kerr.jetBrightmut / jetRedShiftExp / jetSaturation / jetShiftMax。
> 注意(NPGS 原生行为):亮度末端乘 `0.5+0.5·tanh(log(ṁ)+1)`——吸积率 0.01 时系数仅 ~7e-4,
> 喷流要在高吸积率(滑条上限 0.1)+ 高亮度下才明显;若要更亮可再抬 Jet brightness 上限。
> prepass 开启时喷流进入低分体积色,composite 平滑路径插值(体积雾型,适于插值)。
> 验收:shaderc 0 错误;喷流开(jetBrightmut=1)30s 冒烟无验证层报错;观感待用户运行确认。
>
> **噪声哈希 LUT 实现记录(2026-09-19,方案与 NPGS 的 TODO 原意不同):** PerlinNoise 实为
> **值噪声**(格点 sin 哈希值 + 三次衰减三线性插值),直接把噪声值域存 LUT 有两难——
> 高倍频(GenerateAccretionDiskNoise 频率 3^i,i≤6)远超单 LUT 的奈奎斯特极限,且周期性
> 重复在盘面尺度可见。落地方案改为**哈希值查表**:64³ R8 3D 纹理(256KB,常驻缓存)按同式
> 预计算格点哈希值(Java Math.sin),着色器 8 次 texelFetch(坐标 &63 周期化,负坐标补码
> 位与自然回绕)替代 8 条 sin 超越函数链,**插值结构与值域完全不变**,任意倍频无混叠;
> 64 格点取模的重复周期(低频 0.33 时 ≈194 输入单位)远大于盘面有效范围,无可见重复。
> A/B:**BlackHoleArgs 末尾追加 iNoiseLut 字段**(std140 追加不移动既有偏移,384→400B),
> KerrParams.noiseLutEnabled → GUI Kerr Disk 面板 "Noise LUT (A/B)" 复选框实时切换,
> 配置 kerr.noiseLut(默认关);prepass UBO 字节复制自动携带,两路径口径一致。
> 基础设施:Image.ImageData 增 imageType/depth 字段(3D 纹理);新增 vk/NoiseHashLut。
> PerlinNoise1D(喷流)保持程序化。验收:shaderc 0 错误;LUT 开 30s 冒烟无验证层报错;
> GeoTest 基线不变;**用户 A/B 实测结论(2026-09-19):无可感知的视觉与帧率差异**
> (与设计预期一致——视觉同源,核显上该负载下噪声非瓶颈);功能保留,默认关,
> 供高倍频场景或未来硬件复测。

---

## 五、风险与注意事项

1. **移植体量**:kerr.frag ≈ 4500 行,中文注释密集。对策:Phase 0 一次搬运+编译驱动排错,
   不手抄公式(逐字节复制,只改裁剪点),错误集中在删除函数的引用链——用编译错误清单逐个 stub。
2. **std140 对齐**:BlackHoleArgs 打包错位=黑屏/花屏且难查。对策:打包器逐字段注释偏移;
   先跑一帧 `vkValidate=true`+RenderDoc 抓 UBO 与 NPGS C++ 侧布局逐字段 diff;
   保留"字段一个不删"原则让偏移与原版可对照。
3. **JOML `Matrix4f.get(ByteBuffer)` 不推进 position**:这是本项目已踩过的隐坑。
   JOML 的 `Matrix4f.get(ByteBuffer dest)` 将矩阵写入 dest 当前 position 处,
   但**不自动推进 position**(与 `putFloat` 行为不同)。若链式写 UBO 时依赖
   position 自动推进,后续字段全部从 offset 0 覆盖 → 黑屏且无报错。
   对策:每次 `mat.get(buf)` 后手动 `buf.position(buf.position() + 64)`。
4. **性能**:克尔 RK4+解析度规每步成本远超史瓦西 Euler 辛格式,1080p 可能显著掉帧。
   对策:iQuality 第一版就要接(GUI);步长策略 NPGS 已优化(分段球对称函数);
   prepass 是 Phase 4 大招;验收时记录两模式帧耗时对比进文档。
5. **天空盒裁剪**:SampleBackground 三套变体选层(2026-09-19 更正:宇宙变体,非分辨率分层)
   与波段频移耦合,宏别名可能改变 LOD 语义。
    对策:Phase 1 先静态星空全亮度验证方向正确,再开频移。
6. **坐标系陷阱**:NPGS 是"相机系传参"(黑洞位置/盘法向都是相机系),本项目世界系黑洞
    固定原点;映射表 §3 三行旋转乘法别漏。自旋方向(D 形开口朝向)若与预期镜像,
    检查 iBlackHoleRelativeDiskTangen 的符号。
7. **历史图直写交换链**:实施时二选一(vkCmdBlitImage 复制 vs 双附件 pass),
    注意交换链图像布局转换(本项目 acquire 后已转 COLOR_ATTACHMENT_OPTIMAL,
    blit 目标需 VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,多一个 barrier,别漏)。
8. **iTime/iGameTime 单位**:NPGS iBlackHoleTime 单位 c·s/Rs,与本项目动画秒不同,
    直接照抄 NPGS 换算(Application.cpp 参照),否则盘转速会不对。
9. **切换时序**:GUI 写 Scene 状态是即时生效语义,Render 在帧首检查——与现有 resize
    路径(waitIdle 后重建)同序,勿在渲染中途切换;TAA 首帧强制重置照抄现有
    taaForceReset 模式。
10. **回归**:GeoTest(积分器)与史瓦西渲染路径零改动,不受影响;每次提交前跑
    GeoTest + 双模式冒烟 30s。

---

## 六、工作量预估

| Phase | 内容 | 预估 |
|---|---|---|
| 0 | 着色器搬运+编译通过 | 大头(搬运+排错,半天~一天) |
| 1 | KerrRender+分发+切换 | KerrRender ≈ 400 行(对照 BlackHoleRender 裁改) |
| 1.5 | kerr.frag 结构化重构 | 纯 shader 侧,约半天(拆分/去重/删死码,不改算法) |
| 2 | 盘+红移调通 | 以调参排错为主 |
| 3 | TAA+GUI+收尾 | 小 |
| 4/5 | 性能/增强/克尔相机 | 按需另立计划 |

建议提交粒度:每 Phase 一提交,Phase 0/1/1.5 可再拆(kerr.frag 先入库一份未裁剪全量,
裁剪单独提交,重构再单独提交,diff 清晰可回溯)。

---

## 七、黑屏排障调整(2026-09-04,Phase 1 后)

> 现象:切 Kerr 模式全屏黑。frag 体量与 NPGS 复杂度不利于调试,做四项瘦身+一个短路开关,
> 以"先渲染出星空"为目标逐级恢复。

1. **天空盒裁为一套**(kerr.frag 仅剩 `iBackground0`@set1.b1;`SampleBackground` 去掉三层/反宇宙
   选层与 `textureQueryLod` LOD 模拟;KerrRender 描述符布局同步为 b0 历史/b1 天空盒/b9 贴图盘占位)。
   多天空盒支持 → Phase 4 可选项(见上;2026-09-19 核对:实为宇宙变体选层而非分辨率分层,
   本项目配置下恒选 0 号层)。
2. **删除注释死代码**:系选择竞态试验块、白洞换系注释残骸、蓝移盘/喷流注释调用块、
   落点白点注释调用、tonemap 备用 return 等(kerr.frag 5121→5002 行)。
   `DiskColortoRed/toBlue/Densestar/Grid/GridColorSimple/DrawFallingWhiteDot` 等未接线函数体暂留
   (Phase 2/4 参考),其内部注释待功能接线时一并清。
3. **射线构造剥离**:`ScreenJitter`(像素抖动)、`BuildViewDirLocal`(屏幕 UV→相机系视方向)、
   `BuildWorldToLocal`(盘法向/切向→黑洞局部标架,同时产出双向旋转)、
   `AdvanceToMarchingBoundary`(包围球跳空段,输出推进量与"未命中→直接逃逸"标志)。
   TraceRay 入口现在一眼可读:UV 翻转 → 视方向 → 局部标架 → 起点/方向 → 跳空段。
4. **`KERR_SKIP_RAYMARCH`**(已移除):原为射线步进短路开关,用于星空朝向验证。现已恢复完整测地线步进。

### 已修复的黑屏级问题(截至 2026-09-04)

见卷首状态块,共六处:

| # | 问题 | 修复 |
|---|------|------|
| 1 | `iFovRadians` 双重换算: `EngCfg.fov` 已是弧度,Java 又 `toRadians`+`tan(×0.5)`,着色器再 `tan(/2)` → 视锥≈0.005rad,全屏落阴影 | 传水平 FOV = `2*atan(tan(fovY/2)*w/h)` |
| 2 | `iInverseCamRot` 语义反转:着色器要相机→世界,Java 传了世界→相机 | `camToWorldRot.set(viewRot).invert()` |
| 3 | `renderArea` 悬垂指针: `VkRect2D.calloc(stack)` 在 `createRenderInfos` 的 try-with-resources 内,方法返回即弹栈;后续帧 `MemoryStack.stackPush()` 复用该内存 → renderArea 内容变垃圾 → scissor 裁光 → Pass A 无像素输出 | renderArea/extent 改为堆分配(`VkRect2D.calloc()`),与 `renderInfo` 同生命周期 |
| 4 | tonemap NaN: `log(1-pow(x,2.2))` 在 x≥1 时产生 NaN,NaN 混入 TAA 历史永存 | clamp(Result, 0, 0.9999) |
| 5 | TAA 历史 NaN 自愈: prev 含 NaN 时直接覆盖 | `isnan`/`isinf` 检查 → 用当前帧替换 |
| 6 | **JOML `Matrix4f.get(ByteBuffer)` 不推进 position**:JOML 的 `get(ByteBuffer)` 将 mat4x4 写入 buffer 当前 position 处,**但不推进 position**;后续 `putFloat`/`putInt` 从 offset 0 开始写,整块 UBO 数据被覆盖 → 着色器读到全零 | `camToWorldRot.get(bh)` 后加 `bh.position(64)` 手动推进 64 字节;另加 `gameArgsUbo.flush()` + `bhArgsUbo.flush()` 防御 VMA 非 HOST_COHERENT 回退 |
