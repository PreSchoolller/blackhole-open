# 多宇宙星空(6 套 cubemap)与最大延拓(iWhitehole)接线计划书

> **状态(2026-09-26):计划书,待评审。** 由来:kerr_port_plan 头部"后续增强需另立计划
> (如多宇宙变体星空的 iInWhichUniverse 接线)";裁剪记录见 kerr.frag:137-138 与 e982848。
>
> **定位先行:** 本计划对"对齐 NPGS B站视频观感"作用有限——NPGS 演示模式 `iInWhichUniverse`
> 同样恒 0(宿主 App:2052 初始化后不再改动,无穿越切层逻辑),`iWhitehole=1` 只影响穿视界
> 内容与积分步数上限。其价值在**功能完整性**:反宇宙星空、穿视界后的白洞侧出射、最大延拓
> 物理表现。观感对齐主线在 kerr_bloom_colorblend_plan.md。

---

## 一、NPGS 机制(2026-09-26 调查,`NPGS/NPGS/Sources`)

### 1.1 六套 cubemap 与选层

- 采样器(`BlackHole_common.glsl:103-108`):`iBackground0/1/2`(正宇宙三层,binding 1/3/5)
  + `iAntiground0/1/2`(反宇宙三层,binding 2/4/6);
- 素材(`Assets/Textures/`):Universe0/1/2Skybox 各 6 面 **1024²**;Antiverse0/1/2Skybox
  各 6 面 **2048²**(已用 `file` 实测);
- 选层(`Common:272-281`):`int(iInWhichUniverse + 3 + useContground) % 3` 取 0/1/2 号层;
- 正/反宇宙组判定(`Common:267-271`):`isAntiverse = mod(round(Status), 3) == 2`
  (Status 为射线逃逸时的区标志位)**异或**负质量(`iBlackHoleMassSol < 0`)→ 走 Antiground 组;
- `useContground = -offset`,offset 由 Status 区段推导(`Common:259-266`,
  `offset = round((rStatus-1)/3)`,rStatus>3 时)——即射线终点所在延拓区决定层内偏移;
- 波长位移与亮度保持(`Common:283-293`)已在我们的 SampleBackground 移植版中,**无需动**。

### 1.2 iInWhichUniverse / iWhitehole 语义

- `iInWhichUniverse`:注释"当前最大延拓宇宙编号,分界是 II 区上边界,即应该在向内进入内视界
  切换"——相机沿最大延拓穿入内视界后应递增;**NPGS 演示恒 0 且无宿主侧动态切换**;
- `iWhitehole`:最大延拓开关,NPGS 演示**默认 1**(App:2051)。影响:
  积分最大步数 1145(`Common:4561`);阴影剔除自动禁用(`Common:4300` 条件已含
  `iWhitehole==0`);II 区穿越后从白洞侧出射的物理表现;
- 相机动态:`iUniverseSign`(正/反宇宙空间侧)在赤道穿越时翻号——我们已有
  `CheckEquatorialCrossing`/`CurrentUniverseSign` 在位。

---

## 二、现状(本项目裁剪记录)

- e982848:"天空盒裁为一套:只剩 iBackground0@b1,SampleBackground 去三层/反宇宙选层;
  描述符布局同步";
- `kerr.frag:319` 现状:`SampleBackground` 首行直接
  `textureLod(iBackground0, Dir, 0.0)`(连 textureQueryLod 都省了),波长位移/亮度保持段完整;
- **残留骨架(恢复条件良好)**:`SampleBackground(Dir, Shift, Status)` 签名未变、
  `iUniverseSign`/`CurrentUniverseSign`/`CheckEquatorialCrossing` 在位、
  Status 标志位体系仍在(kerr.frag:2145/2872 处尚有 `iInWhichUniverse` 引用残段)、
  `iWhitehole==0` 条件散布现存分支;
- Java 侧:KerrRender `putInt(0) // iWhitehole`、`putInt(0) // iInWhichUniverse` 硬编码;
  描述符 texLayout 当前 binding 0/1/7/8/9/10(NPGS 编号惯例),**b2..b6 空闲**;
- DualSkybox(mountains_seas 2048²)是我们自加的 Universe0 槽位变体机制,与之共存(§三 Phase 1)。

---

## 三、分阶段实施

### Phase 1:纹理与描述符扩容

- `kerr.frag`:恢复 `iBackground0/1/2` + `iAntiground0/1/2` 六个 samplerCube 声明
  (binding 1-6,NPGS 同槽);
- `KerrRender`:texLayout 增加 binding 2-6 描述符;6 个 `CubeTexture` 实例
  (mip 链已在 CubeTexture 内建,`textureQueryLod` 语义恢复);
- 素材拷入 `resources/textures/`:`universe1/`、`universe2/`、`antiverse0/1/2/`
  (Universe0 与 mountains_seas 已在);
- **DualSkybox 共存定义**:mountains_seas 保留为 Universe0 号层的运行时变体替换
  (现有机制不动),其余 5 套固定挂载。

**显存账(RGBA8 + 完整 mip 链 ≈ ×1.33):**

| 方案 | 内容 | 显存 |
|---|---|---|
| A(全 6 套) | Universe×3(1024²)+ Antiverse×3(2048²) | ≈ 483 MB |
| **A2(推荐)** | Universe×3 + Antiverse0 一套 | ≈ 233 MB |
| A3(最小) | Universe0 + Antiverse0 | ≈ 158 MB |

Antiverse1/2 仅在 `iInWhichUniverse≠0` 且反宇宙时才可见,NPGS 演示自身都到不了
——**A2**:正宇宙三层齐全(切层调试可用),反宇宙挂 0 号(2048²,与 Universe1/2 的
1024² 不一致是 NPGS 原样);核显吃紧再降 A3。

### Phase 2:SampleBackground 原型恢复

按 `Common:251-295` 逐行恢复选层块(useContground 推导 + isAntiverse 异或 + 六 sampler
三分支),`textureLod(..., min(1.0, textureQueryLod(...).x))` 的 lod 钳制一并恢复;
删除 kerr.frag:137-138 的裁剪注释,更新为接线说明。

### Phase 3:iWhitehole / iInWhichUniverse 接线

- `iWhitehole`:eng.properties `kerr.whitehole`(默认 **1**,对齐 NPGS 演示)+
  GUI 开关(克尔面板);注意默认改 1 后积分步数上限变化(1145)对性能的影响需实测,
  若核显吃紧则默认 0 并在计划书回写;
- `iInWhichUniverse`:第一版做**调试开关**(GUI 下拉 0/1/2)+ 面板只读显示,
  宿主不做穿越自动切层(NPGS 演示亦无);II 区上边界切层的宿主逻辑(需相机 r 与区判定)
  列为远期,不在本计划。

---

## 四、验收标准

- [ ] GUI 切 `iInWhichUniverse` 0/1/2:星空按层切换且选层公式正确(静止相机下三层画面不同);
- [ ] 反宇宙路径:构造 Status 触发 Antiground 组(或负质量),反宇宙星空出现;
- [ ] `iWhitehole=1/0` 切换:画面不崩、vkValidate 干净、GeoTest 回归不变;
- [ ] 六 sampler 全接后静止正宇宙 0 号层画面与 Phase 前逐像素一致(回归);
- [ ] 显存占用符合所选方案账面(验证层/工具确认)。

## 五、风险与注意事项

1. **显存**:全 6 套 ≈483MB 对核显不现实,按 A2 起步;纹理加载失败的兜底(缺文件回退
   Universe0)应有;
2. **Status 语义恢复错误 → 星空错层**:useContground/isAntiverse 推导务必对照 Common
   逐行,用"静止远处相机 + 强制 Status"的调试路径验证,不靠肉眼猜;
3. `iWhitehole=1` 默认值变更影响 MaxStep 与剔除路径,性能 A/B 后才定稿(见 Phase 3);
4. mountains_seas 变体与三层选层的组合矩阵(变体只作用于 0 号层)需在 DualSkybox
   注释中写死,防止后续误改;
5. jar 模式:新纹理走 classpath 读取(CubeTexture 已支持),注意打包资源清单。
