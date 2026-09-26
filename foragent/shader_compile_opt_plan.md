# 着色器编译启动耗时优化计划书 —— 诊断、include 评估与管线缓存落盘

> **状态(2026-09-19):诊断完成;Phase A 已实现并验收(58.5s/59.8s 实锤驱动侧管线编译);
> Phase B 已实现并经用户实测验收(清驱动缓存后仅靠 pipeline-cache.bin 仍秒开);
> 打点日志已全部移除,方案定稿。**
> **结论速览:**
> ① "kerr shader 太大导致 GLSL 编译慢"**不成立**——shaderc 编译 kerr.frag(3193 行/131KB→254KB spv)
> 实测仅 **93ms**;
> ② "改着色器后首启 10s+"的主嫌是**驱动侧管线编译**(vkCreateGraphicsPipelines):spv 内容一变,
> 驱动内部缓存必然 miss,首启要从新 SPIR-V 编 main + prepass + bloom 三条管线(核显 + vkValidate=true
> 放大),第二次启动起驱动磁盘缓存命中即恢复秒开——与"仅首次启动慢"的现象精确吻合;
> ③ **include 对启动延迟无改善**(见 §一.3),它的正确定位是 3192 行单文件的可维护性;
> ④ 真正的改善路径:Phase A 耗时打点量化 → Phase B prepass 管线懒建 + PipelineCache 落盘 →
> Phase C(可选)include 重构。10s 的最终归属以 Phase A 运行时数据为准。

---

## 一、诊断(2026-09-19)

### 1.1 shaderc 编译实测(编译器本体无罪)

方法:临时基准程序直调 `ShaderCompiler.compileShader`(与线上同参数:Vulkan 1.0 目标、
GLSL 源语言、debugShaders=false,基准程序用完即删):

| 文件 | 行数 | 源码 | 产物 spv | 编译耗时 |
|---|---|---|---|---|
| kerr.frag | 3193 | 131.2 KB | 254 KB | **93 ms** |
| blackhole.frag | 447 | 20.1 KB | 45 KB | 70 ms |

结论:GLSL→SPIR-V 这一步毫秒级,10 秒完全不在此处。spv 体量本身也正常
(无大循环病态展开迹象,254KB 属常规复杂度)。

### 1.2 启动链路与 10s 归属分析

启动(改过 kerr.frag 后首启)实际发生的事:

```
ShaderCompiler.compileShaderIfChanged × 3(kerr.vert / kerr.frag / kerr_bloom.frag)
  ├─ mtime 比对:2 个命中旧 spv,1 个重编(shaderc ~93ms,合计 <200ms)
ShaderModule × 3(vkCreateShaderModule,微秒级)
Pipeline × 3  ← 主嫌
  ├─ main        (vert+frag,   写 TAA 历史)
  ├─ prepass     (vert+frag,   半分辨率双附件)  ← kerr.prepassEnabled=false 默认关,仍无条件创建(KerrRender.java:262)
  │                                              （此为诊断时点状态;Phase B 已改懒建,prepassPipeline 仅在 prepassEnabled 时创建,见卷首结论④）
  └─ bloom       (vert+bloom,  历史→交换链)
  每条管线触发一次驱动侧编译(核显上单条可达秒级;同一 frag spv 因渲染状态不同各编一次)
```

关键佐证:

- **PipelineCache 只在单次启动内有效**:`VkCtx` 创建的 `PipelineCache` 空初始数据、
  从不调用 `vkGetPipelineCacheData` 落盘——跨启动的管线编译加速完全赌**驱动内部磁盘缓存**;
- 驱动缓存按 SPIR-V 内容寻址:改 kerr.frag → spv 变 → **必然 miss** → 首启全量编译 →
  第二次启动起命中。这正是"修改过着色器后**首次**启动要等 10 秒以上"的精确成因形态;
- 放大器:`vkValidate=true`(验证层对 254KB spv 逐管线解析校验);
- 待排除项(Phase A 打点后确认):Windows Defender 对新写 spv 文件的扫描、
  验证层自身占比等杂项。

> 注意:本诊断的 93ms 为确定证据(排除 shaderc),10s 落在驱动管线编译为
> **按排除法+现象特征推定**,最终以 Phase A 运行时打点为准。

### 1.3 include 能改善启动延迟吗 → **不能**

三点理由:

1. **glslang 吃的总代码量不变**:include 只是源码组织手段,展开后编译输入完全相同,
   而 93ms 的实测证明这一步本来就不是瓶颈;
2. **SPIR-V 产物不变**:驱动侧管线编译量一点不少,而那才是 10s 的主嫌;
3. **include 反而引入缓存失效风险**:现行 `compileShaderIfChanged` 只比较单文件
   mtime——若 kerr.frag include 了 common.glsl,只改 common.glsl 时 kerr.frag 的
   mtime/spv 均不变 → **陈旧 spv 静默生效**(画面与源码不一致的隐蔽 bug)。
   要做 include 就必须配套"依赖链失效"机制(§二 Phase C)。

include 的正确定位:**可维护性**——kerr.frag 3192 行单文件难维护、与 NPGS 上游
`BlackHole_common.glsl` 的多文件组织对照困难(尤其 prepass/composite 上游本就是独立文件)。
值不值得做取决于维护性诉求,与启动耗时无关。

---

## 二、分阶段实施

### Phase A:启动耗时打点(先行,半天)

- 两个层面加计时,统一 `Logger.info` 输出
  (**注意:tinylog 无配置文件且 eng.properties `log.level=INFO`,
  现有 `Logger.debug` 全部静默——打点必须走 INFO 才可见**):
  - `ShaderCompiler`:compileShaderIfChanged / compileShader 前后计时
    (`[startup] shaderc kerr.frag 93ms (recompile)` / `(cache hit)`);
  - `Pipeline` 构造:vkCreateGraphicsPipelines 前后计时,PipelineBuildInfo 增加可选
    name 字段(调用点传入,如 "kerr.main"/"kerr.prepass"/"kerr.bloom"),
    输出 `[startup] pipeline kerr.main 3120ms`;
  - `Render.init` / `KerrRender.init` 总时长收尾汇总一行。
- **验收**:改 kerr.frag → 首启日志把 10s 明确定位到某阶段。
  若落在管线创建 → 按 Phase B 执行;若不落 → 按实际归属修订本计划(如验证层占比高,
  则文档提示开发期 `vkValidate=false`)。

> **Phase A 实现记录(2026-09-19):** 打点四层,全部 `Logger.info` 前缀 `[startup]`
> (冒烟验证 fresh JVM 下可见;tinylog 无配置文件走默认输出):
> ① `ShaderCompiler.compileShaderIfChanged` 文件系统与 jar 两模式各输出
> `shaderc <file> recompiled in <n> ms` / `cache hit`;
> ② `Pipeline` 构造对 `vkCreateGraphicsPipelines` 单独计时,`PipelineBuildInfo` 新增可选
> `name` 字段(六个创建点已命名:kerr.main/prepass/bloom、blackhole.main/bloom、gui.main),
> 输出 `pipeline <name> created in <n> ms`——**这是定位 10s 的核心数据点**;
> ③ `Render.init` 输出 `renderer init (<KERR|SCHWARZSCHILD>) total <n> ms`;
> ④ `Engine` 构造起点→首帧完成输出 `first frame rendered, total <n> ms`(兜底:若驱动
> 延迟到首次提交才编译,耗时只会落在这里)。改动 8 文件 +55/−7,`mvn compile` 通过;
> shaderc 层冒烟通过(touch kerr.frag → `recompiled in 241 ms`(冷 JVM 首编,与热身
> 93ms 同量级)→ 二次运行 `cache hit`)。管线/首帧打点需用户真机首启读数:
> 改 kerr.frag → 启动 → 把 `[startup]` 日志各行耗时回报,10s 归属即可定案。

> **Phase A 验收结论(2026-09-19,用户真机实测,KERR 模式,改过 kerr.frag 的首启):**
>
> | 阶段 | 耗时 |
> |---|---|
> | shaderc kerr.frag + kerr_bloom.frag 重编 | 134 + 67 ms |
> | KerrRender.init CheckPoint 1–5(资源/描述符,用户自加打点) | 累计 321 ms |
> | **pipeline kerr.main(vkCreateGraphicsPipelines)** | **30 547 ms** |
> | **pipeline kerr.prepass(同上)** | **27 945 ms** |
> | pipeline kerr.bloom / gui.main(小着色器) | 0 ms |
> | renderer init 总计 / 首帧总计 | 59 129 / 59 770 ms |
>
> **定案:59.8s 启动中 58.5s 在驱动侧管线编译,全部集中在两条吃 kerr.frag(254KB
> SPIR-V)的管线上;shaderc/资源/描述符全部可忽略。** bloom/gui 管线 0ms 反证体量大的
> SPIR-V 才是驱动编译慢的决定因素。两附加观察:① main 与 prepass 是同一 module 不同
> 附件状态,驱动未共享编译结果(各 ~30s)——GPL 共享编译或可近半;② 单管线 30s 远超
> 正常量级,疑驱动对 kerr.frag 特定结构病态(如编译期有界大循环),列为后续调研。
> 附:用户在 KerrRender.init 自加 CheckPoint 1–5 打点(与 Phase A 功能重叠)——
> **已于 2026-09-19 随全部打点日志一并移除**(见 Phase B 实现记录末尾)。

### Phase B:管线创建减负(约 1 天)

- **B1 prepass 管线懒建**:prepassEnabled=false 不创建 prepassPipeline(默认路径少编
  一条大管线);GUI 首次开启时若未建则现建(描述符布局/附件格式现取)。
  注意与 resize 交互:重建的是 renderPass/帧缓冲,管线与懒建时机需核对不冲突;
- **B2 PipelineCache 落盘持久化**:
  - `PipelineCache` 构造时读 `shader-cache/pipeline-cache.bin`(存在则作
    initialData 传入,该目录 jar 模式已在用);
  - 渲染器 init 完成后(cleanup 前)`vkGetPipelineCacheData` 写回;
  - **效果边界(须写进文档)**:spv 内容变了首次启动仍要编一次(内容寻址缓存必然
    失效),此后每次启动确定性命中——把"赌驱动内部缓存"变成"确定命中",
    免受驱动更新/缓存驱逐影响;
  - 驱动更新后 stale initialData 是规范允许的(驱动自行校验丢弃),不会崩。
- **验收**:默认配置首启日志少一条 pipeline 计时;改 kerr.frag 首启仍慢(预期内)→
  第二次启动 pipeline 计时显著下降且日志可见;双模式冒烟 30s 无验证层报错;GeoTest 基线不变。

> **Phase B 实现记录(2026-09-19):**
> **B2 PipelineCache 落盘:** `PipelineCache` 重写——构造时读
> `shader-cache/pipeline-cache.bin` 作 initialData 预热(日志 `pipeline cache seeded
> (N KB)`);新增 `save(Device)`(`vkGetPipelineCacheData` 写回,日志
> `pipeline cache saved (N KB)`),在 `Render.init` 完成后与 `VkCtx.cleanup` 各调一次
> (双保险防异常退出丢失);读取缺失/损坏降级冷缓存仅告警,save 失败仅 warn 不中断
> (缓存是加速项,不是正确性依赖)。实现注意:initialData/缓存数据可达数 MB,超出
> MemoryStack 默认 64KB,须 `MemoryUtil.memAlloc` 堆外分配;LWJGL
> `vkGetPipelineCacheData` 的 size 参数是 `PointerBuffer` 而非 `long[]`。
> **B1 prepass 管线懒建:** KerrRender 存下 `vertSpvPath/fragSpvPath`,init 时仅
> `kerr.prepassEnabled=true` 直开才建;新增 `ensurePrepassPipeline(vkCtx)`(判空幂等,
> spv 路径建 module→建管线→立即清 module),`render()` 帧内首次检出开关打开时现建
> (`vkCreateGraphicsPipelines` 为宿主侧调用,不涉命令缓冲录制,可安全在帧循环触发;
> 此后复用不随开关反复销毁重建,`cleanup` 判空本就安全)。prepass 半分辨率双附件与
> 描述符集仍始终创建(既有设计,GUI 开关只控 pass 执行,与懒建互不影响)。
> `mvn compile` 通过。
> **用户验收清单(需真机):** ① 默认配置改 kerr.frag → 首启只剩 `kerr.main` 一条
> ~30s,`kerr.prepass` 行消失;② 该次启动结束即落盘缓存,立即再次启动 →
> `pipeline cache seeded` + `kerr.main created in ~0ms`(确定性命中,不再赌驱动内部
> 缓存);③ GUI 开 Prepass → 日志现建 `kerr.prepass created in ~30s`(一次性),开关
> 往复不重建;④ prepass 开启画面与改造前一致,双模式冒烟 30s 无验证层报错。
> **触发方法注记(2026-09-19,用户反馈"删 spv 和 bin 都难以触发慢路径"):** 驱动的
> 隐式磁盘管线缓存按 **SPIR-V 字节内容哈希**寻址,与 app 侧 spv/pipeline-cache.bin/
> 显存完全独立——删 spv 后 shaderc 确定性地产出逐字节相同的 SPIR-V,驱动依然命中;
> 删 bin 只清 app 层缓存。可靠触发 = 让 SPIR-V 字节变化:**实质修改 kerr.frag 数值
> 常量**(如 `1.0`→`1.0001`);注释/空行/重命名变量经预处理后 token 不变,无效。
> 手动清驱动缓存(备查,非必要):实测确认本机 Intel 路径为
> `C:\Users\<用户>\AppData\LocalLow\Intel\ShaderCache`(**LocalLow** 而非 Local,
> 与常见记载不同),其余驱动(NVIDIA `%LOCALAPPDATA%\NVIDIA\GLCache`、
> AMD `%LOCALAPPDATA%\AMD\DxCache`)随版本有异,删后自动重建。
> **Phase B 验收结论(2026-09-19,用户实测):通过。** 删除驱动缓存最新文件 + 删除
> `pipeline-cache.bin` → 启动显著变慢(驱动与 app 双缓存皆冷,复现编译成本);保留
> `pipeline-cache.bin` → 秒开(app 层落盘缓存确定性命中)。B2 的设计目标——
> "不再赌驱动内部缓存"——实测成立。
> **打点日志移除(2026-09-19,同日):** 用户确认方案后,Phase A 全部 `[startup]`
> 计时打点与 PipelineBuildInfo 的 name 字段撤除(ShaderCompiler/Pipeline/Render.init/
> Engine 首帧/CheckPoint 1–5/六处 setName,含用户本地自加的 NoiseHashLut 计时行),
> KerrRender 还原为纯 B1 懒建改动。
> 保留:`PipelineCache` 的 `seeded/saved` 两条 INFO(B2 功能反馈,非计时)。
> `mvn compile` 通过。定稿改动:KerrRender(B1 懒建)/PipelineCache + VkCtx + Render
> (B2 落盘)共 4 文件 +119/−13。

### Phase C:#include 支持(可选,可维护性,1~2 天)

> 定位重申:本阶段**不以启动提速为目标**(§一.3),与 Phase A/B 相互独立。

- **用 shaderc 原生 include 回调**(`shaderc_compile_options_set_include_callbacks`,
  LWJGL 已绑定)而非文本预展开,天然获得真实文件名+行号的错误定位;
  回调内实现:shaders/ 目录基准解析、`#pragma once`、循环包含检测(visited 集);
- **依赖失效(必须配套)**:
  - 文件系统模式:递归收集 include 依赖链,取**依赖链最大 mtime** 与 spv 比较
    (替换现行单文件 mtime 比较);
  - jar 模式:依赖内容并入现行 SHA-256 缓存键(任一依赖变了键即变)。
- **首个用户:kerr.frag 拆分**,对齐 NPGS 上游文件结构便于对照:
  `kerr_common.glsl`(度规/测地/盘/红移/tonemap)+ `kerr.frag`(main 驱动)+
  可选 `kerr_prepass.frag`/`kerr_composite.frag`(与上游 prepass 29 行/composite 175 行
  的独立文件形态一致;现状是单文件三路径 iPrepass 分支)。
  blackhole.frag 不强制拆;函数级拆分已在 Phase 1.5 完成,本阶段只动文件边界;
- **风险**:include 路径解析错误(相对 vs 基准目录)、依赖收集遗漏导致陈旧 spv、
  拆分时函数间隐式依赖(Phase 1.5 已提取的共享函数如 ComputeDiskArgument 需归属清晰);
- **验收**:改 kerr_common.glsl → 所有 includer 的 spv 失效重编(日志可见);
  改无关着色器 → 不受牵连;shaderc 0 错误;双模式冒烟 30s;GeoTest 基线不变。

### 后续方向(不在本计划内,按需另立)

- **单管线 30s 病态调研(Phase A 验收发现,优先级最高的后续项)**:一条管线驱动编译
  30s 远超正常量级,疑 Intel 核显驱动对 kerr.frag 特定结构病态(如编译期有界大循环
  展开/巨型函数调度)。可建**无头管线编译基准**(不开窗口:instance+device+动态渲染
  管线,喂同一 spv)作为 A/B 试验台,评估三条路线:① 改写嫌疑循环为运行时有界;
  ② VK_EXT_graphics_pipeline_library——main 与 prepass 共享同一 shader 编译
  (现状各编一次 ~30s,理论近半);③ 精简 SPIR-V(裁剪/重构超大函数)。
- 着色器运行时热重载(保存即重编 spv + 重建管线,免重启)——驱动编译躲不掉,
  但把"改完 → 手动重启 10s"变成"保存后数秒画面自动更新",开发体感收益大;
  工程量:文件监听/触发按钮 + 管线/描述符安全重建 + TAA 历史重置,需另立计划;
- `shaderRecompilation=true` 配置键目前无消费者(ShaderCompiler 未读),
  若做热重载可顺势赋予语义,否则删除避免误导。

---

## 三、风险与注意事项

1. **Phase A 结论可能推翻 Phase B**:若 10s 实测不在管线创建(如验证层/Defender),
   优先按数据调整,不为既定方案找证据;
2. **PipelineCache 落盘的设备绑定**:缓存数据与驱动/设备强相关,换卡/升级驱动后
   miss 属正常;写入路径沿用 shader-cache/(jar 外置运行时已验证可写);
3. **prepass 懒建的首次开启帧**:懒建发生在帧循环中(改动 GUI 开关后),需
   waitIdle 语义与现有 renderArea/描述符生命周期核对,防重蹈 DualSkybox UAF 覆辙;
4. **include 回调的 native 内存管理**:LWJGL 回调分配的 source content 需按
   shaderc 语义释放(callbacks 的 result 所有权),实现时对照 LWJGL 文档,防泄漏;
5. **spv 出库策略不变**(500ef44):include 不改变"spv 不入库、运行时再生"的既定决策;
6. **回归基线**:每个 Phase 提交前跑 GeoTest + 双模式冒烟 30s(沿用 kerr_port_plan §五.10)。

## 四、工作量预估

| Phase | 内容 | 预估 |
|---|---|---|
| A | 启动打点量化(ShaderCompiler/Pipeline/init) | 半天 |
| B1 | prepass 管线懒建 | 2~3 小时 |
| B2 | PipelineCache 落盘持久化 | 半天 |
| C | include 回调 + 依赖失效 + kerr.frag 拆分 | 1~2 天(可选,独立) |

建议提交粒度:A 一提交(附首启耗时数据入档)、B1/B2 各一提交、C 两提交
(基础设施含依赖失效先行,kerr.frag 拆分单独提交,diff 可回溯)。
