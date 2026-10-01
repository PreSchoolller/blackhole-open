# 史瓦西侧参数管线迁移 UBO + Black Hole 调参面板 实施计划

> **状态(2026-09-27 晚):Phase 1/2/3 全部实现(分支 schwarzschild-ubo,1f7e719/bbc618d/
> 7625ae9),Phase 1/2 经用户运行验收通过;Phase 3 冒烟通过(vkValidate=true,30s 零验证层
> 报错)待用户画面验收。Phase 3 落地与计划的差异:**
> - **UBO 布局:** 未按 §3-1"追加尾部"直删 rotationSpeed——改为槽位复用(iExposure 占
>   原 rotationSpeed@168 槽),避免 vec3 16B 对齐位移链;iShiftMax 等 5 字段尾部追加,
>   252B 数据对齐 16 → BH_ARGS_SIZE 256。
> - **if_dopplerI/T 接线语义:** 两字段原从未消费。I=切断轨道多普勒束流增亮项
>   mix(1, min(ShiftMax,Doppler), float(I));T=切断温移²项 mix(1, Doppler², float(T))。
>   RedShift=Doppler·GravRed·CamG 链未拆,关断后不对称减弱但不完全消失(注释已标)。
> - **GLOBAL_EXPOSURE:** 确认为死定义(从未引用),删除;曝光走 iExposure
>   (autoExposure 后乘子,原字面量 2.0)。
> - **Phase 3(可选)已完成:** §5 表全部行落地;新配置键 blackhole.exposure/shiftMax/
>   taaTau/bloomThreshold/bloomMix/bloomMax,默认即原硬编码值(行为零变化)。
> - **事故与守卫(6fec1d4):** Phase 3 首版 bloomComposite.frag 块漏改(rotationSpeed
>   未换 iExposure 且尾部重复追加),bloom 侧 iBloom 三字段后移 4B——iBloomMax 读到
>   未写过的零字节 → tonemap min(x,0) → 切史瓦西整屏全黑(克尔走 kerr_composite
>   不受影响;冒烟停留默认克尔模式未拦住)。修复 + 新增 src/test/java/UboBlockCheck.java
>   守卫三 shader 块成员清单一致性(27 members × 3)。
> - **计划外追加(8a213f2/57251b0):** 背景亮度/色调映射强度/盘半厚三参数随用户点名
>   陆续加入,布局增至 30 成员/272B;明细与待选池见 §9。
>
> **Phase 1/2 补记(2026-09-27):** Phase 1/2 已实现(1f7e719/bbc618d),冒烟通过
> (vkValidate=true,35s/30s 零验证层报错,管线创建通过——布局与 shader 块不匹配会即时抛错),
> 画面级验收经用户实测通过。落地点与计划的差异:
> - **Phase 1:** 参数 UBO 绑定采用 set 2(主管线)/set 1(bloom 管线)、同一 DescSet 实例两处
>   复用,块实例名保留 `pc` → 着色器正文零改动;vert 块删除了原 `place_holder4` 占位字段
>   (统一为含 iRenderTime 的完整 22 字段声明,三 shader 逐字段一致);BH_ARGS_SIZE=240
>   (232 数据 + std140 尾部填充 8B)。
> - **Phase 2:** SchwarzschildParams 五字段(baseTemperature/diskScatter/diskAmbient/
>   diskInnerRadiusRs/diskOuterRadiusRs),Black Hole 面板五滑条与 Kerr Disk 同位互斥;
>   Controls 的 Temp 与散射/环境光滑条撤出;NumPad ± 经 handleParamKeys 增传 EngCtx 改接
>   参数包;盘内/外半径自本提交起由参数包供数(原每帧读 EngCfg)。
>   （Phase 3 当时未做,完成记录见顶部状态。）
>
> **原始计划(2026-09-27 评审稿)如下。**

> **状态(2026-09-27):评审稿,未开工。**
> 背景:盘前向散射/环境光两块落地并合入 dev/main(308ae14、117599f,feat-disk-scatter 分支;
> feat-disk-ambient 独立分支被叠加版覆盖后已删)后,史瓦西侧 push constant 用量 224→232 字节,
> 距桌面 GPU 普遍上限 256 仅剩 24 字节(6 个 float),无法承载一个 Kerr Disk 规模的调参面板。
> 经评估决定:参数管线整体迁移 UBO(照抄 KerrRender 的 BlackHoleArgs 模式),push constant 退役,
> 顺带补齐史瓦西侧的专属调参面板。本计划书记录容量实测、依赖面盘点、迁移方案与分阶段步骤。

> **目标:** Phase 1 纯迁移(零行为变化,画面与 dev 一致);Phase 2 落地
> `SchwarzschildParams` + 仅史瓦西模式显示的 Black Hole 面板(先搬存量滑条);
> Phase 3(可选)把 shader 硬编码常数参数化进面板。全程 vkValidate=true 冒烟无报错。

---

## 1. 背景与容量实测

| 项 | 值 | 备注 |
|---|---|---|
| `maxPushConstantsSize` 规范保证最低 | **128 B** | 基本只出现在移动/嵌入式实现 |
| 桌面驱动(NV/AMD/Intel)实际值 | **256 B** | 几乎一律 |
| 本机实测(2026-09-27) | **256 B**,Vulkan 1.4 | headless `vkCreateInstance` + `vkEnumeratePhysicalDevices` 查询:Intel Arc 130T(16GB),设备枚举 2 条同型号 |
| 史瓦西侧当前用量 | **232 B** | 224(叠加散射前)+iDiskScatter@224+iDiskAmbient@228 |
| 剩余 | **24 B = 6 float** | 撑不起面板 |
| UBO `maxUniformBufferRange` 规范最低 | **16 KB** | 迁移后参数空间不再是约束 |

克尔侧先例(KerrRender,本计划的抄写蓝本):`BlackHoleArgs` UBO 400 B(mat4+8×vec4+11×int
+37×float=384,末尾追加 3 个本项目扩展 float=387,对齐 16→400),每帧插槽一份、持久映射、
每帧整块重写。数百字节 UBO 的逐帧更新对该逐像素 raymarch 场景性能无感。

## 2. 现状盘点:push constant 的三个消费方

| Shader | 消费字段 | 声明块大小 |
|---|---|---|
| `blackhole.frag` | 全部 22 字段 | 232 B |
| `blackhole.vert` | 仅 `inverseView`/`inverseProj`/`cameraPos`(反投影射线) | 224 B 块(未访问尾部字段) |
| `bloomComposite.frag` | **仅 `iFade`**(@204,用 `place_holder4` 保偏移) | 224 B 自声明块 |

Java 侧:主管线与 bloom 管线共用同一 `pushConstBuff`(232 B)分别下发;
字段来源 = EngCfg 每帧读(`schwarzschildRadius`/`diskInner/OuterRadius`/`timeRate`)
+ 三个静态字段(`BaseTemperature`/`DiskScatter`/`DiskAmbient`,GUI/快捷键运行时覆盖)
+ 帧状态(时间/TAA/测地相机 β、γ、iFade)。

**关键有利事实:** 现有字段序恰好 std140 干净——三个 vec3 后都紧跟一个 float
(`cameraPos+time`、`blackHolePos+schwarzschildRadius`、`iCameraVel+iCameraGamma`,
注释中"兼作对齐"即为此设计),int/float 链自然 4 字节对齐,mat4 天然 16 对齐。
**整块声明原样搬进 std140 UBO 无需重排、无填充坑。**

## 3. 方案设计

### 3-1 UBO 布局(GLSL 块名 `BlackHoleArgs`,与克尔侧同名——独立 shader 互不冲突,grep 一门式学习)

- 绑定点:**set 2, binding 0**(不动现有 set 0=天空盒、set 1=TAA 历史+prevCam UBO);
  bloom 管线布局里同一 DescSet 绑为 **set 1**(bloom 现仅 set 0 一份,顺延即可——
  同一 DescSet 实例可绑到不同管线布局的不同 set 号)。
- stage flags:**VERTEX | FRAGMENT**(vert 要矩阵,bloom frag 只要 iFade)。
- Phase 1 布局 = 现 push constant 22 字段原样(232 B,对齐 16 → 240 B)。
- Phase 3 新增字段一律按"float×4 一组 / vec4"补位(克尔侧惯例),禁止裸 vec3 收尾。

### 3-2 管线与 Java 改动(BlackHoleRender)

- `PUSH_CONSTANTS_SIZE`/`pushConstBuff`/`setPushConstants`/两处 `vkCmdPushConstants` 全部退役;
  `PipelineBuildInfo.setPushConstRanges` 调用删除(**三个 shader 必须同一提交内同步换块**,
  否则 shader 声明 push_constant 而管线布局无 range → 验证层报错)。
- 新增每帧插槽 params UBO ×2(`MAX_IN_FLIGHT`,VMA HOST_ACCESS_SEQUENTIAL_WRITE,持久映射),
  `packParams(slot)` 照 KerrRender.packUbo 逐字段写入。
- 新增 DescSetLayout(set 2,UNIFORM_BUFFER×1,VERTEX|FRAGMENT)+ 每插槽一份 DescSet;
  主管线 descSetLayouts 追加第 3 项,bloom 追加第 2 项。
- `render()` 中两处绑定描述符集的地方各加 params set(main: firstSet=2;bloom: firstSet=1)。
- 三个静态字段(`BaseTemperature`/`DiskScatter`/`DiskAmbient`)迁入 SchwarzschildParams(Phase 2;
  Phase 1 先原样保留,`packParams` 读静态/EngCfg,行为零变化)。NumPad ± 温度快捷键改写
  params 字段(InputController 现引用 `BlackHoleRender.BaseTemperature` 的落点同步)。

### 3-3 SchwarzschildParams(eng/scene,照 KerrParams)

- 字段从 `eng.properties` 初始化(EngCfg 已有全部 getter,无新键);
  GUI 直写、渲染器每帧读;Scene 常驻持有(模式热切换/resize 不丢值),与 KerrParams 同生命周期。
- `diskTimeCsRs` 类似的"渲染器无状态"原则:史瓦西侧的 simTime 累计器留在 BlackHoleRender
  (现状即如此),params 只存用户可调量。

### 3-4 Black Hole 面板(Panels.renderSchwarzschildPanel)

- 仅 `scene.getSpacetime() == SCHWARZSCHILD` 显示(同 Kerr Disk 面板的模式门控),F1 门控复用。
- **标签全英文**(默认 ImGui 字体无 CJK 字形,gui_overlay_plan Phase 4-2 教训)。
- Controls 面板的 Temp / Disk scatter (backlight) / Disk ambient (all-sky) 三滑条**挪入**新面板
  (Controls 只留跨模式项:时空切换、测地相机滑条、山海盒、Verbose log);
  sliderL 左标签列宽自适应已有,无额外工作。

## 4. 实施步骤

- **Phase 0 准备**:自 dev 切分支(建议 `schwarzschild-ubo`);SpvCheck/KerrCheck 校验脚本备好。
- **Phase 1 纯迁移**(一个提交):三个 shader 块替换(vert/bloom 的块同步替换并删占位字段)
  + BlackHoleRender UBO 化 + set 2 布局与绑定。静态字段、EngCfg 读取路径原样。
  验收:画面与 dev 逐项一致(同配置对比盘/阴影/背景/散射项),TAA 行为不变。
- **Phase 2 面板**(一个提交):SchwarzschildParams + Black Hole 面板,搬存量滑条
  (Temp / Disk scatter / Disk ambient)+ 新增盘半径两滑条(见 §5);
  NumPad ± 与快捷键路径改指 params。
- **Phase 3(可选)硬编码参数化**(一个提交):§5 表中"shader 硬编码"行的字段追加进 UBO
  (float×4 补位)并上滑条;rotationSpeed 字段(从未消费)趁机删除。

## 5. 面板参数清单

| 滑条(英文标签) | 配置键 / 来源 | 范围 | 默认 | Phase | 说明 |
|---|---|---|---|---|---|
| Temp (K) | `blackhole.temperature` | cfg(min..max) | 90000 | 2 | NumPad ± 并行 |
| Disk scatter (backlight) | `blackhole.diskScatter` | 0..1 | 0.25 | 2 | 背光项,只泛亮剪影 |
| Disk ambient (all-sky) | `blackhole.diskAmbient` | 0..4 | 1.0 | 2 | 弥散项,均匀补底 |
| Disk inner radius | `blackhole.diskInnerRadius` | 2..6 | 3.0 | 2 | Rs 倍数;改 ISCO 附近观感 |
| Disk outer radius | `blackhole.diskOuterRadius` | 6..40 | 18.0 | 2 | **警告:调大显著增步数与 TAA 拖影面积**(properties 已注) |
| Exposure | shader 硬编码 `GLOBAL_EXPOSURE` 2.0 / autoExposure ×2.0 | 0.5..4 | 2.0 | 3 | 合成亮度 |
| Shift max | shader 硬编码 `ShiftMax` 2.5 | 1.5..4 | 2.5 | 3 | 盘频移钳制 |
| Bloom threshold / mix / max | 硬编码 1.0 / 0.6 / 12.0 | 0..2 / 0..1.5 / 4..24 | 同左 | 3 | bloomComposite 参数化 |
| TAA tau | shader 硬编码 0.3 | 0.05..0.6 | 0.3 | 3 | 静止累积降噪/拖影权衡 |
| Doppler brightness / temp | push 硬编码 `if_dopplerI/T`=1 | checkbox | 开 | 3 | A/B 实验 |

## 6. 风险与注意

- **std140 对齐**:vec3 必须跟 float 占尾(现状满足);新增字段禁裸 vec3 收尾、
  禁 vec3+vec3 交错;改完用验证层跑一遍(VkValidationFeatures 调试时报
  "std140 布局不匹配"类 VUID)。
- **三 shader 同步**:push_constant 块在三处(frag/vert/bloom frag),管线布局去 range
  必须与块删除同提交,否则驱动/验证层拒绝。
- **bloom 偏移敏感性**:bloomComposite 现靠 `place_holder4` 对齐到 iFade@204——换 UBO 后
  按字段名直接读,占位字段一并删除,不再有偏移对齐问题。
- **TAA**:历史缓冲/插槽 ping-pong/重置逻辑与参数传递方式无关,不动;
  params UBO 是每帧整块重写,无需 prepass 字节复制那套(史瓦西无 prepass)。
- **着色器缓存**:SHADER_CACHE_DIR 按源码哈希键控,改块自动失效重编译,无需清缓存。
- **描述符预算**:DescAllocator `maxDescs=100`,新增每插槽 1 set + 1 布局,余量充足。

## 7. 验收标准

1. `mvn compile` 通过;SpvCheck(blackhole.frag/vert)+ KerrCheck 模式三 shader 全绿。
2. Phase 1 后同配置画面与 dev 一致(散射/环境光默认值下肉眼无差,TAA 静止累积收敛正常)。
3. Phase 2 后:Black Hole 面板仅史瓦西模式显示,滑条拖动下一帧生效;
   NumPad ± 温度、G/F 测地切换等快捷键路径不受迁移影响。
4. 克尔模式热切换往返无异常;vkValidate=true 连续 30s 冒烟零报错。
5. README 源码结构/面板描述同步(如 GUI 面板一节)。

## 8. 参考

- `KerrRender.packUbo`(UBO 打包范本)/ `KerrParams`(参数类范本)/ `Panels.renderKerrPanel`(面板范本)
- `foragent/gui_overlay_plan.md`(面板基建、英文标签教训)/ `foragent/kerr_bloom_colorblend_plan.md`
- push constant 容量实测脚本要点:`vkCreateInstance`→`vkGetPhysicalDeviceProperties`
  `.limits().maxPushConstantsSize()`(headless 可跑,无需 surface)

## 9. 计划外追加调参记录（2026-09-27，计划收口后用户逐次点名）

> 三轮追加全部沿用既有路径：UBO 尾部追加 → 三 shader 块同步（UboBlockCheck 守卫）→
> EngCfg 键 → SchwarzschildParams → packParams → Black Hole 面板滑条。
> 布局现状：**30 成员 / 264B 数据 / BH_ARGS_SIZE 272**（权威偏移表见 BlackHoleRender 类注释）。

| 滑条（英文标签） | UBO 字段@偏移 | 配置键（默认） | 滑条范围 | 接线语义 | 提交 |
|---|---|---|---|---|---|
| Background bright | iBackgroundBright@252 | blackhole.backgroundBright（0.7） | 0..2 | BackgroundColor 原 0.7 倍率参数化；前向散射项经 Bg 同步缩放，0=纯黑背景突出盘本体（与 kerr.backgroundBrightness 对位） | 8a213f2 |
| Tonemap (ACES mix) | iToneMapStrength@256 | blackhole.tonemapStrength（1.0） | 0..1 | mix(线性, ACES, k)：1=原行为，0=线性直出（高光硬钳）；只作用主 pass，bloom 端 NPGS 对数曲线不归它管 | 8a213f2 |
| Disk half-thickness | iDiskHalfThickness@260 | blackhole.diskHalfThickness（0.5） | 0.1..2.0 Rs | 垂直方向 7 处 0.5·Rs 硬编码（主体/尘埃层边界、扰动厚度、竖直混合分母、外层剔除盒）统一替换，剖面形状与噪声不变、整体垂直尺度随动 | 57251b0 |

**待选池（同日审计结论，未实施，用户点名即加）：**

- 第一梯队（视觉收益大/接线便宜）：
  - 内缘增亮 Inner ring boost —— `blackhole.frag:275` 的 `20.0`，热内环突出程度，0=无增亮
  - 径向冷却长度 Outer cooling —— `blackhole.frag:288` 的 `0.6`，外盘变冷变暗速率，决定外盘可见范围
  - 盘动画时间倍率 Disk TimeRate —— `blackhole.timeRate`，克尔面板有对称项而史瓦西面板缺失；**零 UBO 改动**（字段已在 UBO@184，仅改数据源+滑条）
  - 噪声对比 Noise contrast —— `blackhole.frag:193/196/198/204` 的 `80.0` ×4，盘面丝缕感强弱
- 第二梯队（有用但小众）：红移暗化指数 9.0（`frag:294`，与多普勒开关同链）/ 尘埃辉光 0.02（`frag:205`）/ Bloom 半径 24.0（`bloomComposite:60`，tap 数固定只改散布）/ 外缘亮度截断 1.8（`frag:287`，与径向冷却重叠二选一）
- 不建议滑条化：温度剖面指数 0.75/0.25（Shakura-Sunyaev 物理值）、背景引力蓝移上限 2.0、相机 γ 钳制 0.1..10、MAX_STEPS 与步长因子（性能旋钮，要动进配置文件不进面板）
