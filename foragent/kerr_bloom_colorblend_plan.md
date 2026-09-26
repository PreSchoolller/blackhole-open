# 克尔后处理链升级计划书 —— NPGS mipmap 树 Bloom + ColorBlend 调色链移植

> **状态(2026-09-26):Phase 1+2 完成并经用户运行验收通过(用户实测反馈"挺好！很棒"),方案定稿。**
> 落地与计划的差异:
> - 方案 A 落地:历史图扩完整 mip 链(R16G16B16A16_SFLOAT + TRANSFER_SRC/DST usage,
>   levels=floor(log2(max(w,h)))+1),Pass A 收尾后 sync2 分段布局翻转 + vkCmdBlitImage
>   逐级减半(LINEAR)再全链转 SHADER_READ;`kerr_bloom.frag` 删除,新增 `kerr_composite.frag`
>   (GetBloom 8 octave textureLod 加权 ×(iBloomStrength×0.08) + ColorBlend 调色链逐行照搬);
> - `iBloomStrength` 语义改为辉光总强度乘子(0=纯调色链=Phase 1 行为,1.0=NPGS 原始基准),
>   滑条 0..2 保留;`iBloomThreshold` 退役(EngCfg/KerrParams 字段删除,GUI 滑条移除,
>   eng.properties 键保留标注废弃);
> - `VkUtils.imageBarrier` 新增 mip 范围重载(sync2 分段布局转换);
> - Phase 3(showcase 预设)未做——等运行时验收后按视频人工迭代。
> **运行时验收(2026-09-26,用户实测通过):** 强度 0 vs 0.5 A/B 色彩风格转向 NPGS、
> 光子环/盘亮缘大半径辉光无阈值硬边,均符合预期;后续可调项:强度滑条(0..2)、
> quality/盘外半径/FOV(启动后手动调,§五)。**Phase 3(showcase 预设人工迭代)仍开放——**
> 按 B站视频逐步逼近时的参数结论请回写至 §五 表格。

---

> **原始计划(2026-09-26 评审稿)如下。** 背景:与 NPGS B站演示视频逐项对比后(宿主侧调查见 §一),
> 结论是着色器本体(`BlackHole_common` 整体搬运)能力已齐平,观感差距的大头在**后处理链**——
> NPGS 的 `Bloom.comp.glsl`/`ColorBlend.frag.glsl` 从未列入 kerr_port_plan §1.1 移植清单
> (当时的资源盘点只到 common/frag/prepass/composite,属**计划盲区**而非刻意裁剪),
> 现行 `kerr_bloom.frag` 是黑体侧 bloomComposite 同款的"阈值 + 黄金角 24-tap"简化 bloom。
>
> **参数默认值不动(用户要求):** quality/盘外半径/FOV 等对性能影响大,启动后手动可调,
> 本计划不修改任何 eng.properties 默认值;对比 NPGS 视频时的建议值见 §五。

---

## 一、NPGS 链路剖析(2026-09-26 宿主侧调查,路径 `NPGS/NPGS/Sources`)

### 1.1 每帧渲染链(App:2377-2960)

```
prepass(半分辨率,无条件录制,默认 iPrepass=0 不消费)
→ composite(全分辨率: TraceRay → ApplyToneMapping(HDR 软限幅) → TAA 混合 → 写 BlackHoleAttachment)
→ history 拷贝(App:2465-2521)
→ PreBloom compute(Bloom.comp.glsl 编译为 GENERATE_MIPMAP:一次 dispatch 生成 8-octave atlas)
→ GaussBlur compute ×2(GAUSS_BLUR:5-tap 高斯,水平 → copyback → 垂直,只处理 atlas 左半 x<0.52)
→ ColorBlend frag(最终合成 + 调色,直写上屏;App:2713-2735)
```

### 1.2 Bloom atlas 机制(Bloom.comp.glsl)

- `GENERATE_MIPMAP` 分支:对每个输出像素,把 8 个 octave 的源采样**求和写一张 atlas**——
  octave o(1..8) 用 `TexCoord += CalcOffset(o); TexCoord *= exp2(o)` 把整帧内容缩小
  2^o 倍后放进 atlas 的对应槽位(CalcOffset 给出槽位几何:左列 octave1-3 从 0.5、0.75、
  0.875 处逐级缩小,右列 octave4-8 小块);octave2+ 用 box 过采样(4/8/16 tap)抑制采样闪烁;
  octave1 直接 1:1;
- `GAUSS_BLUR` 分支:5-tap 高斯(权重 {0.19638,0.29675,0.09442,0.01038,0.00026},
  偏移 1.41~7.06 px,`Bloom.comp.glsl:104-105`)对 atlas 水平→垂直各一趟;
- **无亮部提取阈值**——"阈值"由 composite 内 `ApplyToneMapping` 的 `BloomMax` 软限幅隐式承担
  (`Common:297-311`;我们 kerr.frag:336 同款已移植,行为一致)。

### 1.3 ColorBlend 调色链(ColorBlend.frag.glsl,全硬编码无可调参数)

```
Color  = 场景色 + GetBloom(uv) * 0.08        // 8 octave 按权重 {1,1.5,1,1.5,1.8,1,1,1} 重建,
                                              // atlas 采样用 BicubicTexture(4-tap 三次卷积)
// 调色:
Color  = pow(Color, 1.5);                     // Reinhard 预压
Color  = Color / (1 + Color);                 // Reinhard
Color  = pow(Color, 1/1.5);
Color  = mix(Color, Color²(3-2Color), 1);     // smoothstep 对比度
Color  = pow(Color, vec3(1.3, 1.2, 1.0));     // 逐通道 gamma(R/G/B 各异→暖色风格)
Color  = Saturate(Color * 1.01);
Color  = pow(Color, vec3(0.7 / 2.2));         // 最终提亮 gamma
```

### 1.4 作用域与关键事实

- Bloom 与调色都作用在 **LDR 域**(composite 输出 = tonemap + TAA 之后)——与我们
  TAA 历史图的内容域**相同**,可以逐字移植;
- ColorBlend 是 NPGS 视频色彩风格的直接来源(Reinhard + smoothstep 对比 + 逐通道 gamma),
  演示模式下全链无条件运行,无开关;
- `PostProcess.frag.glsl`(纯 gamma 2.2,16 行)在 NPGS 宿主中无引用,不移植。

---

## 二、现状与差距

| 项 | NPGS | 我们(kerr) | 差距 |
|---|---|---|---|
| Bloom 源 | 无阈值,tonemap 软限幅隐式承担 | `kerr_bloom.frag` 亮部提取,阈值 0.7(LDR 域) | 辉光范围/柔和度 |
| Bloom 结构 | 8 octave(÷2..÷256)+ 双向高斯 | 单趟 24-tap 黄金角圆盘模糊(小半径) | **大半径光滑辉光完全缺失** |
| 合成 | 8 octave 加权 ×0.08 + Bicubic | 强度直接叠加 | 辉光层次 |
| 调色 | Reinhard^1.5 → smoothstep 对比 → 通道 gamma(1.3/1.2/1.0) → ×1.01 → gamma(0.7/2.2) | 无(tonemap 后直出) | **色彩风格(胶片感/暖调)缺失** |
| Pass 数 | main+prepass+composite+mip+blur×2+blend | main+copy+bloom(3 pass) | 我们更少,加两 pass 无压力 |

现行链:`KerrRender`(main → TAA 历史)→ `kerr_bloom.frag`(读历史,阈值+模糊+叠加,写交换链)。

---

## 三、方案设计

### 3.1 方案 A(推荐):真实 mip 链 + ColorBlend 移植,全 frag/vert,零 compute

本项目无 compute 管线基础设施(全动态渲染,frag/vert + blit),不照搬 NPGS 的
"atlas+compute"形态,改为**真实 mipmap 链**承载 octave:

1. **历史图扩成 mip 链**:KerrRender 的 TAA 历史 Image 增加全部 mip 级
   (usage +TRANSFER_SRC|TRANSFER_DST,levels=log2(max(w,h))+1);
2. **Pass A(mip 生成)**:main pass 每帧收尾后,`vkCmdBlitImage` 逐级
   lod N-1 → lod N(复用 `CubeTexture` 的 blit+mip 链模式,每帧 8 次小 blit;
   布局转换用项目既有 sync2 barrier 模式;双插槽历史各自成链);
3. **Pass B(合成,替换 `kerr_bloom.frag` → `kerr_composite.frag`)**:
   - `scene = texture(uHistory, uv)`;
   - `bloom = Σ_{o=1..8} textureLod(uHistoryMips, uv, o) * w[o]`,w 照抄 NPGS
     {1,1.5,1,1.5,1.8,1,1,1},总量 × `iBloomStrength`(基准 0.08);
   - ColorBlend 调色链**逐行照搬**(§1.3);
   - 写交换链。

**与 NPGS 的已知采样差异(接受):** NPGS octave = box 过采样 + atlas 高斯;真实 mip 链
blit ≈ box 双线性,大 lod 本身高模糊,叠加高斯与否的视觉差异可忽略;`BicubicTexture`
是为无 mip 的 atlas 而设,有真实 mip 链后 LINEAR `textureLod` 等效。若 A/B 后仍想要
NPGS 的柔化,可在合成着色器内对每个 lod 做 5-tap 高斯采样(40 tap,成本仍低),列为备选。

### 3.2 方案 B(不推荐):compute atlas 照搬

逐字节复刻 GENERATE_MIPMAP/GAUSS_BLUR + atlas 几何(CalcOffset 槽位/Bicubic 重建)。
需引入 compute 管线、存储图、atlas 布局管理;与项目风格相悖,收益仅"数值逐位一致"。

### 3.3 参数与 GUI

| 项 | 处置 |
|---|---|
| `iBloomStrength` | 保留,语义改为"辉光总强度乘子"(内部基准 0.08,NPGS 演示等效 1.0×基准) |
| `iBloomThreshold` | **退役**——mip 树 bloom 无阈值概念;eng.properties 键标注废弃保留兼容,GUI 滑条移除 |
| 调色链 | 全部硬编码(照抄 NPGS),**不**暴露滑条——保持与 NPGS 观感可比性;确需微调时再加 |

---

## 四、分阶段实施

### Phase 1:调色链落地(bloom 权重置 0)

历史图 mip 链 + blit 生成 + `kerr_composite.frag` 只做"场景直通 + §1.3 调色链",
GetBloom 项乘 0。

**验收:** ①静止画面与旧链 A/B——色彩风格转向 NPGS(对比度/暖调/暗角感来自通道 gamma);
②强度等效路径无回归:`kerr_bloom.frag` 退役后旧截图对照;③vkValidate 干净;④核显帧耗时
增量 < 1ms(8 次 blit + 一次 fullscreen LDR pass,相对 kerr.frag raymarch 可忽略)。

### Phase 2:GetBloom 8 octave 接入

textureLod 加权求和 × `iBloomStrength`,替换直通。

**验收:** ①光子环/盘亮缘出现**大半径光滑辉光、无阈值硬边**(与旧 24-tap 阈值 bloom 对照);
②强度=0 时与 Phase 1 逐像素一致;③`ApplyToneMapping` 软限幅未动,超亮区不过曝。

### Phase 3:showcase 预设整理(不改默认值)

NPGS 演示参数(§五)整理成 eng.properties 注释块"对齐 NPGS 视频的建议值",按 B站视频
人工迭代 Brightmut/BackgroundBrightmut 等;GUI 不新增控件。

---

## 五、对比 NPGS 视频时的建议值(启动后手动调,默认不动)

| 参数 | NPGS 演示值 | 我们当前默认 | 手动调整入口 |
|---|---|---|---|
| iQuality | 1.0(截图 10) | 0.6 | eng.properties `kerr.quality`(性能敏感,自行权衡) |
| 盘外半径 | 25 Rs | 10 Rs | eng.properties `kerr.diskOuterRadius`(性能敏感) |
| FOV | 80° | 90° | GUI/配置 fov |
| Brightmut | 1.0 | 2.0 | GUI 滑条 |
| BackgroundBrightmut | 0(演示者手动调亮) | 0.6 | GUI 滑条 |

注:NPGS B站视频是作者菜单调参后的 showcase(其默认 `BackgroundBrightmut=0` 背景全黑、
`Darkmut=0` 盘透明),观感对齐 = 本计划结构差距 + 人工参数迭代两部分。

---

## 六、风险与注意事项

1. **二次压缩**:NPGS 在 tonemap 后又叠 Reinhard + gamma(0.7/2.2),整体偏暗是其风格的一部分
   ——照搬即可,不做亮度补偿;若观感过暗先查 BackgroundBrightmut/Brightmut;
2. **mip blit 的布局正确性**:双插槽历史、每帧 COLOR↔TRANSFER_SRC↔SHADER_READ 转换,
   用 sync2 barrier(项目 TAA 收尾已有两条 barrier 的成熟模式);resize 后 mip 链重建;
3. **LDR 域 bloom 对超亮区**的信息已被 tonemap 压缩——NPGS 同域同行为,保真对齐,不升级 HDR bloom;
4. GUI `bloomThreshold` 滑条移除的清理(panels/EngCfg KerrParams);eng.properties 键保留但标注废弃;
5. `kerr_bloom.frag.spv`/旧 pass 清理后,确认 Render.java 无残留引用。

---

## 七、参考索引(NPGS file:line)

| 内容 | 位置 |
|---|---|
| 渲染链总序/无条件录制 | `Program/Application.cpp:2377-2960` |
| atlas 生成(GENERATE_MIPMAP)/CalcOffset/box 过采样 | `Engine/Shaders/Bloom.comp.glsl:59-101` |
| 5-tap 高斯(GAUSS_BLUR) | `Bloom.comp.glsl:103-126` |
| ColorBlend 全文(调色链/权重/Bicubic) | `Engine/Shaders/ColorBlend.frag.glsl`(128 行) |
| ApplyToneMapping 软限幅(已移植) | `Common:297-311` ↔ 本项目 `kerr.frag:336` |
| Blend pass 宿主接线 | `Application.cpp:1117, 2713-2735` |
