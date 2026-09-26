# GLSL Shader 替换计划（修订版）

> 基于知乎文章《从零开始搓一个黑洞——glsl编程实战》（作者 baopinshui，与 NPGS 项目同源——文章代码即 NPGS 历史代码），针对当前项目 `blackhole.frag` / `blackhole.vert` 制定详细替换方案。
> **本文档已对照知乎文章最新版（2025.12.9）和 GitHub 源码进行逐行校验。**
> （原文全文曾存于本目录 `zhihuzhengwen.md`，因版权考虑已于 2026-09-26 从仓库移除，原样存档于作者本地。）
>
> **状态更新（2026-08-29）：** 已对照代码实际状态（HEAD `f09ed17`）全面核对并更新各 Phase 状态。核心替换（Phase 1-3）已以**变体**形式落地；TAA/Bloom 及相关 Java 基础设施（Phase 0/4/5/6/7）**不在当前代码库中**——曾于 `560f3ea` 提交、后在 `eb50a70` 修坐标系时被整体回滚。权威状态见文末「九、实际状态核对」。
>
> **补记（2026-09-26）：** §9.5 第 5 条"全重投影 TAA 候选"已实测否决、条目关闭——方向/世界坐标两种重投影均于 2026-08-30 当日实现、当日回退（`2979bac`/`875ac03`、`b0378ad`/`d5b6065`），根因与最终裁决见 §9.5。

---

## 一、总体架构对比

> 注：下表"当前实现"列描述的是**迁移前旧实现**（RK4 + 解析盘），仅作历史对照。当前代码已是目标方案的变体，与文章版的具体差异见「九、实际状态核对」。

| 维度 | 当前实现 | 目标实现（知乎文章 v2025.12.9） |
|------|----------|-------------------------------|
| **光线传播模型** | RK4 积分 + PPN 测地线加速度 (`geodesic_accel` + `lensing_effect2`) | Euler 辛格式步进：先更新 RayDir（引力偏折），再移动 RayPos。公式：`dφ = cos³(θ) · 1.5Rs/r² · dl` |
| **吸积盘渲染** | 射线-盘面交点检测 + 沿视线直线积分 (`accretionDiskScience`) | 体积云步进（raymarching 每步调用 `diskcolor()`），黑洞坐标系下逐体素采样 |
| **噪声系统** | Value Noise (Perlin-like) + 三线性插值 `noise3D` | 8 顶点经典 Perlin Noise + `DiskRandom0`（fBM 分形噪声） |
| **多普勒+引力红移** | `D = 1/(γ(1-β·n)) · √(1-2M/r)`，亮度乘 D³ | Doppler × 引力红移合并为 RedShift；T×=RedShift；亮度×=RedShift；增加 `shiftMax` 截断 |
| **泛光 Bloom** | ❌ 未实现 | HDR bloom：降采样 + 高斯核双方向卷积（照搬 sonicether Gargantua With HDR Bloom） |
| **TAA 降噪** | ❌ 未实现 | 多帧混合，`blendWeight` 由吸积盘角速度动态计算；需要 ping-pong framebuffer |
| **步长策略** | 固定 `STEP_SIZE * (0.5+0.5·min(1,r/10))` | 连续分段球对称函数：`Dis>=2ROut→dl*=Dis; 1ROut..2ROut→线性过渡; <ROut→dl*=min(Rs,Dis)` |
| **背景星空** | `noise3D` 球面采样 + 彩色大星体 (`starfield()`) | texelFetch(iChannel1) 预渲染星空贴图，经三色波段分别蓝移后合成（`WavelengthToRgb`） |
| **吸积盘旋转** | 固定 `rotationSpeed` (未使用物理公式) | 史瓦西圆轨道角速度 `ω(r)=√(GM/(r-rs))` + 螺旋向内运动模式 |

---

## 二、文件级替换清单

### 2.1 `blackhole.frag` — 核心重写

| 模块 | 当前代码位置 | 目标行为 | 改动方式 |
|------|-------------|----------|---------|
| **常量/宏定义** | L34-L36 (`#define M`, `#define ISCO`) | PI, G0, lightspeed, sigma, ly, Msun, FOV | ⚡ 替换为文章 L1126-L1132 的完整宏集 |
| **相机坐标系变换** | ❌ 缺失（用 inverseView/inverseProj） | `GetCamera()` + `GetCameraRot()` — 球面极坐标相机，鼠标控制 θ/φ | 🔧 新增两个函数；PushConstants 增加 iMouse/iResolution |
| **坐标变换工具** | ❌ 缺失 | `uvToDir()`, `PosToNDC()`, `DirToNDC()`, `DirTouv()`, `PosTouv()` | 🔧 新增 5 个 inline 函数（文章 L103-L122） |
| **测地线步进** | L207-L218 (`geodesic_accel`) + L373-L379 (`lensing_effect2`/RK4) | Euler 辛格式：先 `RayDir = normalize(RayDir + dthe·cross(cross(RayDir,NPosToBH),RayDir)/costheta)`，再 `RayPos += RayDir*dl` | 🔄 **核心替换** — 删除所有 RK4/geodesic_accel/lensing_effect 函数 |
| **步长函数** | L403 (`dt = STEP_SIZE * (...)`) | 连续分段球对称：`Dis>=2ROut→dl*=Dis; 1ROut..2ROut→过渡公式; <ROut→min(Rs,Dis)`（文章 L462-L475，2025.4.28 最终版） | 🔄 替换步进逻辑，删除固定步长 |
| **随机抖动** | ❌ 缺失 | `RandomStep()` — 第一步加随机偏移消除条纹伪影；UV 起点也抖动（文章 L371-L389） | 🔧 新增函数 + count==0 时调用 |
| **TAA 降噪** | ❌ 缺失 | blendWeight 由 ω(r) 动态计算，混合前一帧颜色 (`texelFetch iChannel3`) | 🆕 新增；需要 Ping-Pong framebuffer；PushConstants 增加 `iFrame`/`iTimeDelta` |
| **黑洞坐标系变换** | ❌ 缺失（吸积盘在 world space 判断） | `GetBH()` + `GetBHRot()` — 将光线位置/方向换系到黑洞盘法向坐标系 | 🆕 新增两个函数，`diskcolor()` 内部调用 |
| **吸积盘体积渲染** | L262-L356 (`accretionDiskScience`) | `diskcolor()` — 完整 volumetric raymarching（含双噪声层、SpiralTheta、Doppler+RedShift、低温修正） | 🔄 **核心替换** — 删除 accretionDiskScience()，用文章 L983-L1104 的完整实现 |
| **Perlin Noise** | L51-L75 (`perlin`) + L126-L136 (`noise3D`) | 经典 8 顶点 Perlin + cubicInterpolation(3t²-2t³)（文章 L504-L529） | 🔄 替换为完整 PerlinNoise() + DiskRandom0(fBM) |
| **温度模型** | L233-L237 (`disk_temperature`) | `T = pow(diskA·Rs³/PosR³ · max(1-√(RIn/PosR)), 0.25)`，额外乘指数下降因子 `exp((PosR-RIn)/(0.6*(ROut-RIn)))` | 🔄 内联到 diskcolor() |
| **黑体颜色** | L77-L110 (`radiation_color`) | `RGB(T)` — exp 拟合函数，含低温修正：T<400→black；T>1000 时乘 (T-400)/600（文章 L932-L945） | 🔄 替换为带低温修正的 RGB() |
| **多普勒+引力红移** | L248-L259 (`doppler_factor`) | `Doppler = √((1+vre)/(1-vre))`；`RedShift = Doppler·√(1-Rs/PosR)/√(1-Rs/CamR)`；T×=max(1000., T*RedShift*Dopler²)（文章 L820-L831, L1083） | 🔄 合并到 diskcolor() 内部，增加 shiftMax 限制 |
| **Shape 轮廓** | ❌ 缺失 | `Shape(x,a,b) = k·xᵃ·(1-x)ᵇ`（Beta 分布截面，文章 L1070-L1074） | 🆕 新增函数 |
| **SoftHold / Vec2Theta** | ❌ 缺失 | `softHold()` + `Vec2Theta()` — 辅助函数（文章 L1135-L1147） | 🔧 新增 |
| **WavelengthToRgb** | ❌ 缺失 | 波长→RGB 映射（用于背景蓝移，三色波段分别处理） | 🆕 新增（文章 L1149-L1194） |
| **背景星空** | L145-L201 (`starfield`) | texelFetch(iChannel1) + WavelengthToRgb 三色波段分别蓝移后合成（文章 L866-L879） | ⚠️ `starfield()` 保留为 fallback，主路径改为贴图采样+蓝移 |
| **Bloom 泛光** | ❌ 缺失 | HDR bloom：降采样 → 高斯卷积（水平/垂直 pass）→ 叠加原图（文章 L785-L799） | 🆕 新增 post-process pass；需要 Framebuffer + ping-pong texture |
| **main() 入口** | L381-L453 | raymarching 循环 + diskcolor() 每步调用 + TAA 混合 + Bloom | 🔄 完全重写，结构对齐文章 L207-L284 |

### 2.2 `blackhole.vert` — PushConstants 扩展

| 字段 | 当前类型 | 新增/修改 |
|------|---------|----------|
| `vec2 iMouse` | ❌ 无 | 相机极角控制（θ=4PI·x/res.x, φ≈0.999PI·y/res.y） |
| `vec2 iResolution` | ❌ 无 | 屏幕分辨率（用于 uvToDir 等坐标变换） |
| `float iTimeDelta` | ❌ 无 | TAA blendWeight 计算用 |
| `int iFrame` | ❌ 无 | TAA：前 2 帧强制全权重 (blendWeight=1.0) |

其余字段（inverseView, inverseProj, cameraPos, time, blackHolePos, schwarzschildRadius, diskInner/OuterRadius, rotationSpeed, temperature, if_dopplerI/T）保持不变。

---

## 三、分阶段实施计划

### Phase 1：基础工具函数层
✅ **DONE（变体落地）** — commit `203a663` + `ce82fbb`（2026-08-09）

**实际落地情况（2026-08-29 核对）：**
- ✅ 已落地：`softHold`、`Shape`、`RGB(T)`（含低温修正）、`RandomStep`、`WavelengthToRgb`（附加了亮度归一化因子）、`Vec2Theta`（现名 `Vec2ToTheta`，用 step/mix 无分支实现）、Perlin 噪声 + fBM（`perlin` + `GenerateDiskNoise`，hash 实现，即文章 `PerlinNoise`/`DiskRandom0` 的等价变体）
- ⚠️ 变体：未采用文章的完整物理宏集（G0/lightspeed/ly/Msun/sigma/FOV），改为**全量纲归一化（Rs=1、无量纲）**；`omega(r)` 简化为开普勒式 `sqrt(1/r³)`（文章为史瓦西圆轨道公式 `sqrt(1/((2r-3Rs)·r²))`）
- ❌ 未采用：`GetBH`/`GetBHRot`（黑洞盘法向坐标系）、`GetCamera`/`GetCameraRot`/`uvToDir`/`DirTouv` 等相机系工具——项目保留 JOML 逆矩阵反投影 + 世界空间方案（顶点着色器反投影出 `inRayOrigin`/`inRayDir`），吸积盘固定在世界 XZ 平面（法向 +Y）

**目标：** 在 `blackhole.frag` 顶部建立完整的数学/物理常量与辅助函数库，不改动渲染逻辑。

#### 1.1 宏定义（文章 L1126-L1132）
```glsl
#define PI 3.141592653589
#define G0 6.673e-11
#define lightspeed 299792458.0
#define sigma 5.670373e-8
#define ly 9460730472580800.0
#define Msun 1.9891e30
#define FOV 0.5
```

#### 1.2 辅助函数（文章 L1135-L1147, L1070-L1074）
```glsl
float softHold(float x) { return 1.0 - 1.0 / (max(x, 0.0) + 1.0); }

// 两平面向量夹角 [0, 2π]，用于吸积盘角度计算
float Vec2Theta(vec2 a, vec2 b) {
    if (dot(a,b) > 0.0) return asin(0.999999 * (a.x*b.y - a.y*b.x) / length(a) / length(b));
    else if (dot(a,b) < 0.0 && (-a.x*b.y + a.y*b.x) < 0.0)
        return PI - asin(0.999999 * (a.x*b.y - a.y*b.x) / length(a) / length(b));
    else if (dot(a,b) < 0.0 && (-a.x*b.y + a.y*b.x) > 0.0)
        return -PI - asin(0.999999 * (a.x*b.y - a.y*b.x) / length(a) / length(b));
}

// Beta 分布截面轮廓（用于吸积盘厚度/密度形状）
float Shape(float x, float a, float b) {
    float k = pow(a+b, a+b) / (pow(a,a) * pow(b,b));
    return k * pow(x, a) * pow(1.0-x, b);
}

// 波长→RGB（用于背景星空蓝移，文章 L1149-L1194）
vec3 WavelengthToRgb(float wavelength) { ... } // 完整实现见原文
```

#### 1.3 黑洞坐标系变换（文章 L1200-L1241）
```glsl
// GetBH: 世界坐标 → 黑洞盘法向坐标系（平移+旋转）
vec3 GetBH(vec4 a, vec3 BHPos, vec3 DiskDir) { ... }

// GetBHRot: 仅旋转到黑洞盘坐标系（不含平移，用于方向向量）
vec3 GetBHRot(vec4 a, vec3 BHPos, vec3 DiskDir) { ... }
```

#### 1.4 相机系变换 + 坐标工具（文章 L48-L122）
```glsl
// GetCamera: 球面极坐标相机平移（用于世界位置换系）
vec4 GetCamera(vec4 a) { ... } // θ=4PI*iMouse.x/iResolution.x, φ≈0.999*PI*iMouse.y/iResolution.y

// GetCameraRot: 仅旋转（用于方向向量换系）
vec4 GetCameraRot(vec4 a) { ... }

// uv→世界空间射线方向
vec3 uvToDir(vec2 uv) { return normalize(vec3(FOV*(2.0*uv.x-1.0), FOV*(2.0*uv.y-1.0)*iResolution.y/iResolution.x, -1.0)); }

// 位置/方向到 NDC / UV 的转换
vec2 PosToNDC(vec4 pos) { ... } // -pos.x/pos.z, -pos.y/pos.z * aspect
vec2 DirToNDC(vec3 dir) { ... }
vec2 DirTouv(vec3 dir) { return vec2(0.5-0.5*dir.x/dir.z, 0.5-0.5*dir.y/dir.z*iResolution.x/iResolution.y); }
vec2 PosTouv(vec4 Pos) { return vec2(0.5-0.5*Pos.x/Pos.z, 0.5-0.5*Pos.y/Pos.z*iResolution.x/iResolution.y); }
```

#### 1.5 Perlin Noise + fBM（文章 L504-L546）
```glsl
float cubicInterpolation(float t) { return 3.0*t*t - 2.0*t*t*t; } // 3t²-2t³

float PerlinNoise(vec3 xyz) { ... } // 8顶点经典Perlin，完整实现见原文

// fBM 分形噪声（多 octave 叠加）
float DiskRandom0(vec3 EPos, int granularityStart, int granularityEnd, float Contrast) {
    float Result = 10.0;
    for (int i = granularityStart; i < granularityEnd; i++) {
        float Rate = pow(3.0, float(i));
        Result *= (1.0 + 0.1 * PerlinNoise(vec3(Rate*EPos.x, Rate*EPos.y, Rate*EPos.z)));
    }
    return log(1. + pow(0.1*Result, Contrast));
}

// 第一步随机抖动（消除条纹伪影）
float RandomStep(vec2 xy, float seed) {
    return fract(sin(dot(xy+fract(11.4514*sin(seed)), vec2(12.9898, 78.233))) * 43758.5453);
}
```

#### 1.6 角速度 + 黑体颜色（文章 L596-L597, L932-L945）
```glsl
// 史瓦西时空圆轨道角速度
float omega(float r, float Rs) {
    return sqrt(lightspeed/ly * lightspeed*Rs/ly / ((2.0*r - 3.0*Rs) * r * r));
}

// 黑体颜色映射（含低温修正 T<400→black，T<1000线性衰减）
vec3 RGB(float T) {
    if (T < 400.01) return vec3(0., 0., 0.);
    float _ = (T - 6500.0) / (6500.0 * T * 2.2);
    float R = exp(2.05539304e4 * _);
    float G = exp(2.63463675e4 * _);
    float B = exp(3.30145739e4 * _);
    float LmulRate = 1.0 / max(max(R, G), B);
    if (T < 1000.) LmulRate *= (T - 400.) / 600.; // 低温修正
    R *= LmulRate; G *= LmulRate; B *= LmulRate;
    return vec3(R, G, B);
}
```

**验证：** `mvn compile` 通过，无语法错误。

---

### Phase 2：测地线步进替换（核心物理）
**目标：** 删除 RK4 + geodesic_accel()，改用 Euler 辛格式 + 连续分段球对称步长。

#### 2.1 关键修正说明

> **⚠️ 原文 2025.12.19 更新声明：原步进顺序有误（先移动再偏折），正确做法是先更新 RayDir 再移动 RayPos，使离散步进成为辛格式。**

#### 2.2 完整步进循环（文章 L238-L284 + L462-L475）

```glsl
// ===== main() 开头初始化 =====
fragColor = vec4(0., 0., 0., 0.);
vec2 uv = fragCoord / iResolution.xy;

float Rs = pc.schwarzschildRadius * 0.5; // Schwarzschild radius (从 push const)
// RIn, ROut 从 push constant 读取（或硬编码）
float RIn = rs * 3.5;   // ISCO ~ 3.5Rs
float ROut = rs * 9.0;  // 盘外缘

vec4 BHAPos = vec4(0., 0., 0., 1.0); // 黑洞世界位置
vec3 BHRPos = GetCamera(vec4(BHAPos, 1.0)).xyz; // 相机系下的黑洞位置

// 射线初始状态（在相机系）
vec3 RayDir = uvToDir(uv + 0.5 * vec2(
    RandomStep(uv, fract(iTime*1.0+0.5)), 
    RandomStep(uv, fract(iTime*1.0))
) / iResolution.xy); // UV 起点抖动
vec3 RayPos = vec3(0.0);

vec3 PosToBH = RayPos - BHRPos;
vec3 NPosToBH = normalize(PosToBH + 0.000001); // 避免除零
float Dis = length(PosToBH);
int count = 0;

// ===== raymarching 主循环 =====
while (count < MAX_STEPS) {
    float lastR = length(PosToBH);
    
    // --- 第一步随机抖动 ---
    float dl = 1.0;
    if (count == 0) {
        dl = RandomStep(uv, fract(iTime * 1.0));
    }
    
    // --- 连续分段球对称步长（2025.4.28 最终版）---
    if (Dis >= 2.0 * ROut) {
        dl *= Dis;
    } else if (Dis >= 1.0 * ROut) {
        dl *= ((Rs) * (2.0*ROut - Dis) + Dis*(Dis - ROut)) / ROut;
    } else {
        dl *= min(Rs, Dis);
    }
    
    // --- 测地线偏折（Euler，先更新方向！）---
    float costheta = length(cross(NPosToBH, RayDir)); // 前进方向与切向夹角
    if (costheta > 0.0001) {
        float dphirate = -costheta * costheta * costheta * (1.5 * Rs / Dis);
        float dthe = dl / Dis * dphirate;
        RayDir = normalize(RayDir + (dthe + pow(dthe,3)/3.0) * 
                           cross(cross(RayDir, NPosToBH), RayDir) / costheta);
    }
    
    // --- 角向变形（空间拉伸导致的视野畸变）---
    RayDir = normalize(RayDir - NPosToBH * dot(NPosToBH, RayDir) * 
                       (-sqrt(max(1.0 - Rs/Dis, 0.00000000000000001)) + 1.0));
    
    // --- 先更新方向，再移动位置（辛格式关键）---
    RayPos += RayDir * dl;
    
    // --- 更新状态变量 ---
    PosToBH = RayPos - BHRPos;
    Dis = length(PosToBH);
    NPosToBH = normalize(PosToBH + 0.000001);
    
    // --- 吸积盘体积采样（每步都调用）---
    vec3 WorldZ = vec3(1., 0., 0.); // 参考方向
    float timerate = 1.0;            // 归一化时间速率
    float steplength = dl;           // 当前步长
    
    fragColor = diskcolor(fragColor, timerate, steplength, RayPos, vec3(0.), 
                          RayDir, vec3(0.), WorldZ, BHRPos, vec3(0.,0.,1.),
                          Rs, RIn, ROut, 1.0, 1.0, 5.0); // shiftMax=5
    
    count++;
    
    // --- 终止条件 ---
    if (Dis > 100.0 * Rs && Dis > lastR && count > 50) {
        // 远离黑洞：采样星空背景（蓝移）
        uv = DirTouv(RayDir);
        vec4 starTex = texelFetch(iChannel1, ivec2(vec2(fract(uv.x), fract(uv.y)) * iChannelResolution[1].xy), 0);
        // WavelengthToRgb 三色波段分别蓝移后合成（略，见 Phase 3）
        fragColor += starTex * (1.0 - fragColor.a);
        break;
    }
    if (Dis < 0.1 * Rs) {
        // 命中奇点 → 黑色
        break;
    }
}
```

**验证：** 编译通过；运行时能看到黑洞引力透镜效应（背景星场弯曲、光子环）。

**实际落地情况（2026-08-29 核对）— 已按变体实施（`203a663`，后经 `55a2da8`/`dcf9cd1` 调优）：**
- ✅ RK4 / `geodesic_accel` / `lensing_effect` 已全部删除；Euler 偏折（`DeltaPhi = dl/Dis · (-cos³θ·1.5/Dis)` 含 `dthe³/3` 修正项）已落地
- ⚠️ 与文章的差异：
  - `DeltaPhiRate` 额外乘了一个 `CubicInterpolate` 阻尼因子（近场偏折更平缓）
  - 步长分段以 `SmallStepBoundary = max(ROut, 12)` 为界（文章用 `ROut`），且基础步长有 `0.15 + 0.25·min(...)` 缩放
  - **步进顺序仍为"先移动位置、再更新方向"**（文章 2025.12.19 修正要求先更新 RayDir 再移动 RayPos 的辛格式；此处为待复核项）
  - 文章的"角向变形"（空间拉伸）项 `RayDir -= NPosToBH·dot(...)·(√(1-Rs/Dis)-1)` 未实现
- ➕ 项目自有改动：相机距黑洞 >200 时先做射线-包围球求交直接跳进边界（文章无此优化）；逃逸条件 `Dis > 500`、视界捕获 `Dis < 0.01`；第一步 `RandomStep` 抖动已落地（UV 起点抖动未做）

---

### Phase 3：吸积盘体积渲染 `diskcolor()`
**目标：** 删除 `accretionDiskScience()`，用完整的 `diskcolor()` 替代。这是工作量最大的模块。

#### 3.1 diskcolor() 完整实现（文章 L983-L1104）

```glsl
vec4 diskcolor(vec4 fragColor, float timerate, float steplength,
               vec3 RayPos, vec3 lastRayPos, vec3 RayDir, vec3 lastRayDir,
               vec3 WorldZ, vec3 BHPos, vec3 DiskDir,
               float Rs, float RIn, float ROut, 
               float diskA, float TPeak4, float shiftMax) {

    // === 1. 换系到黑洞坐标系 ===
    vec3 CamOnDisk = GetBH(vec4(0., 0., 0., 1.0), BHPos, DiskDir);
    vec3 References = GetBHRot(vec4(WorldZ, 1.0), BHPos, DiskDir); // 角度零点参考
    vec3 PosOnDisk = GetBH(vec4(RayPos, 1.0), BHPos, DiskDir);     // 光线黑洞系位置
    vec3 DirOnDisk = GetBHRot(vec4(RayDir, 1.0), BHPos, DiskDir);  // 方向黑洞系
    
    float PosR = length(PosOnDisk.xy);
    float PosZ = PosOnDisk.z;

    vec4 color = vec4(0.);

    if (abs(PosZ) < 0.5*Rs && PosR > RIn && PosR < ROut) {

        // === 2. 等效半径（大外径盘厚度控制）===
        float EffR = 1.0 - ((PosR - RIn) / (ROut - RIn) * 0.5);
        if ((ROut - RIn) > 9.0*Rs) {
            if (PosR < 5.0*Rs + RIn) {
                EffR = 1.0 - ((PosR - RIn) / (9.0*Rs) * 0.5);
            } else {
                EffR = 1.0 - (0.5/0.9*0.5 + 
                    ((PosR-RIn)/(ROut-RIn)-5.*Rs/(ROut-Rin)) / (1.-5.*Rs/(ROut-RIn)) * 0.5);
            }
        }

        // === 3. 盘范围严格判断（含 Shape 轮廓）===
        if ((abs(PosZ) < 0.5*Rs*Shape(EffR, 4.0, 0.9)) || 
            (PosZ < 0.5*Rs*(1.-5.*pow(2.*(1.-EffR), 2.)))) {

            // === 3a. SpiralTheta — 螺旋向内旋转（2025.4.28 最终版）===
            float omega0 = omega(PosR, Rs);
            float SpiralTheta = 12.0 * 2.0 / sqrt(3.0) * atan(sqrt(0.6666666*(PosR/Rs)-1.0));
            float RotPosR = PosR/Rs + 0.3*sqrt(3.0)/Rs*iTime; // TimeRate 隐含在 iTime

            // === 3b. 角度计算 ===
            float rthe = Vec2Theta(PosOnDisk.xy, References.xy);
            float PosTheta = fract((rthe + omega0 * iTime) / (2.*PI)) * 2.*PI;

            // === 4. 双噪声层 — 盘本体 color0 + color1（交替过渡，防过度缠绕）===
            vec4 color0 = vec4(0.), color1 = vec4(0.);
            
            { // ===== 第一层噪声 =====
                float Rho = Shape(EffR, 4.0, 0.9);
                if (abs(PosZ) < 0.5*Rs*Rho) {
                    float Thick = 0.5*Rs*Rho * (0.4 + 0.6*softHold(
                        DiskRandom0(vec3(1.5*PosTheta, PosR/Rs, 1.0), 1, 3, 80.0)));
                    float Vmix = max(0., (1.0 - abs(PosZ) / Thick));
                    Rho *= 0.7 * Vmix * Rho;
                    color0 = vec4(DiskRandom0(vec3(1.*PosR/Rs, 1.*PosZ/Rs, .5*PosTheta), 3, 6, 80.0));
                    color0.xyz *= Rho * 1.4 * (0.2 + 0.8*Vmix + (0.8-0.8*Vmix)*
                        DiskRandom0(vec3(PosR/Rs, 1.5*PosTheta, PosZ/Rs), 1, 3, 80.0));
                    color0.a *= Rho;
                }
                // 稀薄气体（distcol）
                if (abs(PosZ) < 0.5*Rs*(1.-5.*pow(2.*(1.-EffR), 2.))) {
                    float distcol = max(1.-pow(PosZ/(0.5*Rs*max(1.-5.*pow(2.*(1.-EffR),2.),0.0001)),2.),0.) *
                        DiskRandom0(vec3(1.5*fract((1.5*rthe+PosTheta)/2./PI)*2.*PI, PosR/Rs, PosZ/Rs), 0, 6, 80.0);
                    color0 += 0.02 * vec4(distcol, 0.2*distcol) * sqrt(1.0001 - DirOnDisk.z*DirOnDisk.z);
                }
                color0 *= 0.5 - 0.5*cos(2.*PI*fract(iTime*timerate)); // 过渡调制
            }

            { // ===== 第二层噪声（相位偏移 0.5，交替出现）=====
                float Rho = Shape(EffR, 4.0, 0.9);
                if (abs(PosZ) < 0.5*Rs*Rho) {
                    float Thick = 0.5*Rs*Rho * (0.4 + 0.6*softHold(
                        DiskRandom0(vec3(1.5*PosTheta, PosR/Rs, 1.0), 1, 3, 80.0)));
                    float Vmix = max(0., (1.0 - abs(PosZ) / Thick));
                    Rho *= 0.7 * Vmix * Rho;
                    color1 = vec4(DiskRandom0(vec3(1.*PosR/Rs, 1.*PosZ/Rs, .5*PosTheta), 3, 6, 80.0));
                    color1.xyz *= Rho * 1.4 * (0.2 + 0.8*Vmix + (0.8-0.8*Vmix)*
                        DiskRandom0(vec3(PosR/Rs, 1.5*PosTheta, PosZ/Rs), 1, 3, 80.0));
                    color1.a *= Rho;
                }
                if (abs(PosZ) < 0.5*Rs*(1.-5.*pow(2.*(1.-EffR), 2.))) {
                    float distcol = max(1.-pow(PosZ/(0.5*Rs*max(1.-5.*pow(2.*(1.-EffR),2.),0.0001)),2.),0.) *
                        DiskRandom0(vec3(1.5*fract((rthe+PosTheta+PI)/2./PI)*2.*PI, PosR/Rs, PosZ/Rs), 0, 6, 80.0);
                    color1 += 0.02 * vec4(distcol, 0.2*distcol) * sqrt(1.0001 - DirOnDisk.z*DirOnDisk.z);
                }
                color1 *= 0.5 - 0.5*cos(2.*PI*fract(iTime*timerate + 0.5)); // 相位偏移
            }

            color = color1 + color0;
            color *= 1.0 + 20.*exp(-10.*(PosR-RIn)/(ROut-RIn)); // 内侧密度增强

            // === 5. Doppler + RedShift 计算 ===
            vec3 v = cross(vec3(0.,0.,1.), PosOnDisk); // 线速度方向
            float vre = dot(-DirOnDisk, v) * lightspeed/ly; // 归一化速度
            float Dopler = sqrt((1.0+vre)/(1.0-vre));

            float RedShift = Dopler * sqrt(max(1.-Rs/PosR, 0.000001)) / 
                             sqrt(max(1.-Rs/max(length(CamOnDisk), 0.001), 0.000001));

            // === 6. 温度 + 黑体颜色（含低温修正、红移色温调整）===
            float T = pow(diskA * Rs*Rs*Rs / (PosR*PosR*PosR) * max(1.-sqrt(RIn/PosR), 0.000001), 0.25);

            // RedShift 对色温的影响（T>1000 才生效，避免低温区过暗）
            if (T > 1000.) T = max(1000., T * RedShift * Dopler * Dopler);
            T = min(100000.0, T);

            // === 7. 颜色合成（亮度×密度×温度×红移）===
            float BrightWithoutRedshift = 4.5*T*T*T*T / max(TPeak4, 0.001);
            
            color.xyz *= BrightWithoutRedshift * min(1., 1.8*(ROut-PosR)/(ROut-RIn)) * 
                         RGB(T / exp((PosR-RIn) / (0.6*(ROut-RIn)))); // 指数下降防颜色单调

            color.xyz *= min(shiftMax, RedShift) * min(shiftMax, Dopler); // 亮度截断
            
            // 不对称性增强
            color.xyz *= pow((1.0-(1.-min(1.,RedShift))*(PosR-RIn)/(ROut-RIn)), 9.);
            
            // 对勾函数降低中间部分亮度（防糊成一坨白）
            color.xyz *= min(1., 1.+0.5*((PosR-RIn)/RIn + RIn/(PosR-RIn)) - max(1., RedShift));

            // === 8. 步长积累 ===
            color.xyz *= steplength / Rs;
            color.a *= steplength / Rs;
        }
    }

    return fragColor + color * (1.0 - fragColor.a);
}
```

**验证：** 编译通过；运行时能看到带 Doppler 不对称性的彩色吸积盘（一侧亮蓝、一侧暗红）。
✅ **DONE（变体落地）** — commit `203a663` + `ce82fbb`，伪影修复见 `55a2da8`/`dcf9cd1`

**实际落地情况（2026-08-29 核对）— `DiskColor()` 已实现，与文章版本的差异：**
- ❌ 未用 `GetBH`/`GetBHRot` 换系：盘固定在世界 XZ 平面（`PosOnDisk.xz` 求半径、`PosY` 为高度、法向 +Y），`DirOnDisk = RayDir`
- ⚠️ 温度模型不同：`T = pc.temperature · (RIn/PosR)^0.75`（薄盘 r^(-3/4) 剖面，`pc.temperature` 由 Java 传入 15000K），非文章的 `pow(diskA·Rs³/PosR³·max(1-√(RIn/PosR)), 0.25)`
- ⚠️ 旋转时序：未用 `SpiralTheta` 螺旋模式，采用"有效时间 + 周期重随机相位"机制（`HalfPiTimeInside = π/ω(3)`，`Phase0/Phase1` 每半周期重抽样防噪声缠绕），双层噪声相位差 0.5 交替过渡的结构已还原
- ⚠️ `ShiftMax = 1.5`（文章为 5）；稀薄气体层额外乘 `min(1, Doppler²)` 亮度因子
- ✅ 其余结构与文章一致：`EffectiveRadius` 等效半径、`Shape` 轮廓、`softHold` 厚度调制、distcol 稀薄气体、Doppler + RedShift 合并、内侧增强 `1+20·exp(-10·(r-RIn)/(ROut-RIn))`、RGB 指数下降、勾函数降亮、`color *= StepLength` 步长积累、`BaseColor + Color·(1-BaseColor.a)` 前混合

---

### Phase 4：TAA + 噪声系统
⏳ **未实施**（依赖 Phase 0/6/7 的 Java 侧 ping-pong 纹理与 push constants 扩展，均已被回滚）
**目标：** 新增 TAA 多帧混合 + DiskRandom0(fBM) 噪声。已在 Phase 1/3 中实现噪声部分，此处补充 TAA。

#### 4.1 TAA 混合（文章 L401-L406）

```glsl
// 在 main() raymarching 循环结束后、输出前插入：
float blendWeight = 1.0 - pow(0.5, iTimeDelta / max(min((0.131*36.0/(timerate)*(omega(3.*Rs,Rs))/(omega(3.*0.00000465,0.00000465))/(omega(3.*Rs,Rs))), 0.3), 0.02));
blendWeight = (iFrame < 2 || iMouse.z > 0.0) ? 1.0 : blendWeight;

vec4 previousColor = texelFetch(iChannel3, ivec2(fragCoord), 0); // ping-pong buffer
fragColor = blendWeight * fragColor + (1.0 - blendWeight) * previousColor;
```

> **注意：** `blendWeight` 公式中的 `timerate` 和 `omega` 比值需要简化。实际实现中建议用一个 uniform float 传入动态 blendWeight 计算所需的归一化角速度参数，避免在 fragment shader 里做复杂计算。

#### 4.2 DiskRandom0 + RandomStep
已在 Phase 1 完成。验证要点：噪声层级从 granularityStart=1→3（大尺度）和 3→6（细节尺度）正确叠加。

---

### Phase 5：Bloom 泛光
✅ **DONE（简化版圆盘模糊，2026-08-30，dev ce998a3）** —— 渲染重构为两 pass：
主 pass 单附件输出 HDR（预色调映射）到 TAA 历史；新增 bloomComposite.frag 合成 pass
读 HDR 历史 → 亮部提取（阈值 1.0）+ 黄金角 24 tap 半径 24px 圆盘模糊 → 色调映射
（公式原样迁移）+ iFade 淡出 → 写交换链。绕开 RGBA32F 不可 blit 的 mip 方案；
调参位置：bloomComposite.frag 的 TAPS/RADIUS/THRESHOLD/强度 0.6。
升级路线：若光晕质量不足，再做 mip 链（render-to-mip 逐级降采样）。
**目标：** 实现 post-process bloom pass（降采样 + 高斯卷积）。来自 sonicether Gargantua With HDR Bloom。

#### 5.1 Bloom 实现（简化版）

```glsl
// 在 main() 末尾，TAA 之后：
float BloomStrength = 0.3; // 可调参数

vec3 bloom(vec2 uv) {
    vec3 color = texture(iChannel0, uv).rgb; // 原图
    
    for (int i = 1; i <= 4; i++) {
        float blur = float(i) * 0.5;
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                vec2 offset = vec2(float(x), float(y)) * blur / iResolution.xy;
                color += texture(iChannel0, uv + offset).rgb;
            }
        }
    }
    return color / 25.0; // 归一化
}

outColor.rgb += bloom(outColor.xy) * BloomStrength;
```

#### 5.2 基础设施需求（Java 侧）

Bloom 需要**多 pass 渲染管线**：

| Pass | 输入 | 输出 | 说明 |
|------|------|------|-----|
| P0: 黑洞渲染 | SwapChain | RT_BLOOM_INPUT (render target) | 当前 main render |
| P1: 降采样 | RT_BLOOM_INPUT | RT_BLOOM_L1 (M/2×N/2) | 提取亮度 + 降采样 |
| P2-P5: 高斯卷积 | RT_BLOOM_Ln | RT_BLOOM_L(n+1) | 水平/垂直交替 pass × 4 |
| P6: 最终叠加 | SwapChain + RT_BLOOM_L3 | SwapChain (blended) | bloom(color) * strength 叠加 |

**替代方案（简化）：** 如果多-pass 实现成本过高，可先用 iChannel0 自循环做单 pass 近似：将当前帧颜色写入 `iChannel0`，下一帧读取卷积。但这需要 shader 支持 texture() + textureFetch 混合使用。

---

### Phase 6：PushConstants 扩展
❌ **未实施** — 当前布局为 204 字节：`timeRate` + 4 个 `place_holder`（shader 与 Java 一致）。下表的目标布局尚未应用。另注：`if_dopplerI`/`if_dopplerT` 已声明但未参与任何计算（多普勒恒开）。
**目标：** Java 侧新增字段并正确传递到 GPU。

| PushConstants 新增 | Java field | 类型 | 来源 |
|--------------------|-----------|------|-----|
| `vec2 iMouse` | mouseX, mouseY | float×2 | GLFW mouse position (0~res) |
| `vec2 iResolution` | screenWidth, screenHeight | int×2 | window width/height |
| `float iTimeDelta` | deltaTime | float | glfwGetTime() - prevTime |
| `int iFrame` | frameCount | int | 递增计数器（每帧+1） |

**新增 PushConstants 内存布局：**
```
[0..63]    inverseView      mat4   (64 bytes)
[64..127]  inverseProj      mat4   (64 bytes)
[128..139] cameraPos        vec3   (12 bytes)
[140..143] time             float  (4 bytes)
[144..155] blackHolePos     vec3   (12 bytes)
[156..159] schwarzschildRadius float (4 bytes)
[160..163] diskInnerRadius  float  (4 bytes)
[164..167] diskOuterRadius  float  (4 bytes)
[168..171] rotationSpeed    float  (4 bytes)
[172..175] temperature      float  (4 bytes)
[176..179] if_dopplerI      int    (4 bytes)
[180..183] if_dopplerT      int    (4 bytes)
--- 以下为新增 ---
[184..191] iMouse           vec2   (8 bytes)
[192..199] iResolution      vec2   (8 bytes)
[200..203] iTimeDelta       float  (4 bytes)
[204..207] iFrame           int    (4 bytes)
---
PUSH_CONSTANTS_SIZE = 208 (+24 bytes from original 184)
```

**Java 侧 `BlackHoleRender.setPushConstants()` 新增：**
```java
// iMouse: 鼠标位置 [0, width] × [0, height]（文章用 iMouse.x/iResolution.x 映射到 θ）
pushConstBuff.putFloat(offset, (float) mouseX); offset += 4;
pushConstBuff.putFloat(offset, (float) mouseY); offset += 4;

// iResolution: 屏幕分辨率
pushConstBuff.putFloat(offset, (float) width); offset += 4;
pushConstBuff.putFloat(offset, (float) height); offset += 4;

// iTimeDelta: 帧间隔秒数
pushConstBuff.putFloat(offset, deltaTime); offset += 4;

// iFrame: 帧计数器（用于 TAA 前2帧强制全权重）
pushConstBuff.putInt(offset, currentFrame % 600); // 循环防溢出
offset += 4;
```

**验证：** Java 编译通过，运行时 PushConstants 数据正确传入 GPU。

---

### Phase 7：Java 侧基础设施扩展（Bloom/TAA Framebuffer）
❌ **未落地** — `BlackHoleRender` 当前为单 pass 动态渲染直写 swapchain（无 TAA 纹理、无 TAA 描述集、无多 pass）。注：本 Phase 与 Phase 0 的代码曾随 `560f3ea` 提交，后在 `eb50a70`（修黑屏/坐标系）中被整体删除。
**目标：** 为 TAA ping-pong buffer 和 Bloom render target 创建 Vulkan framebuffer/texture。

| 资源 | 用途 | 尺寸策略 |
|------|------|---------|
| `RT_TAA_A` / `RT_TAA_B` | TAA ping-pong（交替读/写） | 全屏或半屏 |
| `RT_BLOOM_INPUT` | Bloom 输入（当前帧输出） | 全屏 R32G32B32A32_SFLOAT |
| `RT_BLOOM_L1..L4` | Bloom 降采样层级 | M/2^n × N/2^n |

**实现方案：**
- 使用 Vulkan `VK_IMAGE_USAGE_STORAGE_BIT` + `VK_IMAGE_USAGE_SAMPLED_BIT` 创建浮动点纹理
- Framebuffer 绑定这些 texture view 作为 attachment
- 每个 pass 前通过 pipeline barrier 确保 layout transition 正确
- Ping-pong: A→B, B→A 交替（用 frameCount % 2 切换）

**替代简化方案：** 如果完整多-pass 实现成本过高，Phase 5/7 可暂缓实施。TAA/Bloom 是"锦上添花"，核心黑洞+吸积盘渲染不依赖它们。

---

## 四、删除 vs 保留对照表

| 模块 | 操作 | 理由 |
|------|-----|------|
| `geodesic_accel()` (L207-L218) | ❌ 删除 | 被 Euler 辛格式步进替代 |
| `lensing_effect1/2()` (L359-L379) | ❌ 删除 | RK4 + 牛顿近似，非目标方案 |
| `accretionDiskScience()` (L262-L356) | ❌ 删除 | 被 diskcolor() 体积渲染替代 |
| `starfield()` (L145-L201) | ⚠️ 保留为 fallback | 主路径改为 iChannel1 贴图+蓝移，但无贴图时仍需 procedural fallback |
| `perlin()` (Value Noise, L51-L75) | ❌ 删除 | 被完整 PerlinNoise() 替换 |
| `noise3D()` (三线性插值, L126-L136) | ❌ 删除 | 不再使用 |
| `hash()` + `lerp()` (L40-120) | ⚠️ 保留（如果 Perlin 未覆盖） | PerlinNoise() 自包含，可删；但 lerp/fade/permute/perlin 需要完整重写 |
| `radiation_color()` (L77-L110) | ❌ 删除 | 被 RGB(T) + 低温修正替代 |
| `doppler_factor()` (L248-L259) | ❌ 删除 | Doppler+RedShift 合并到 diskcolor() 内部 |
| `disk_density()` / `disk_temperature()` (L221-L237) | ❌ 删除 | 内联到 diskcolor()，不再独立函数 |
| `blackhole.vert` 主体结构 | ✅ 保留 | 全屏三角形技术不变，仅扩展 PushConstants |

> **2026-08-29 核对：** 实际情况为——`perlin`/`hash` 仍在使用（`GenerateDiskNoise` 即 hash 版 Perlin/fBM 变体，对应上表"❌ 删除 perlin"一条实际未删）；`noise3D` 仍被 `starfield()` 使用（未删）；`starfield()` 仍是背景**主路径**（无星空贴图加载器，蓝移合成已实现）；`radiation_color`/`doppler_factor`/`disk_density`/`disk_temperature`/`accretionDiskScience`/RK4 系已按计划删除。

---

## 五、变量名映射表（当前 → 目标）

| 当前变量 | 目标变量/常量 | 备注 |
|---------|-------------|------|
| `M` (pc.schwarzschildRadius*0.5) | `Rs/2` (文章 M = Rs/2, ISCO=6M=3Rs) | 保持等价 |
| `STEP_SIZE` (0.04) | 分段函数 dl()（球对称） | 不再固定步长 |
| `disk_density()` / `disk_temperature()` | diskcolor() 内部内联 | 不再独立函数 |
| `doppler_factor()` | Doppler + RedShift 合并计算 | 在 diskcolor() 中直接算 |
| `radiation_color()` | `RGB(T)` with low-T correction | 含 T<400→black, T<1000 线性衰减 |

---

## 六、风险与注意事项

### 6.1 性能风险
- **Euler vs RK4**：Euler 精度低于 RK4，但步长连续分段可补偿；实测 FPS。MAX_STEPS 建议从 256 起步，根据 GPU 调整。
- **diskcolor() 计算量**：双噪声层 + fBM (10+ octave) + Doppler/RedShift，每像素约 500+ 浮点运算。若帧率不足，可减少 DiskRandom0 的 granularityEnd（如从 6→4）。
- **Bloom pass**：嵌套循环卷积在低端 GPU 上可能成为瓶颈；可改用分离高斯 + 降采样纹理传递。

### 6.2 物理精度
- Euler 法使黑洞"略小一圈"（约 5%），但无伤大雅（原文作者原话）。
- 吸积盘转速用史瓦西圆轨道公式 `ω = √(GM/(r-rs))`，非 Kerr 度规。

### 6.3 跨平台兼容性
- `texelFetch(iChannelN, ...)` 是 Shadertoy 语法；Vulkan/GLSL 中需改为 `texture(sampler2D, uv)` 或使用 `iChannelN` uniform sampler2D。
- GLSL 中 `fract(11.4514*sin(seed))` 需确保类型一致（seed 应为 float）。

### 6.4 实施优先级建议

```
P0 (必须): Phase 1 → Phase 2 → Phase 3    [核心渲染管线]
           ↓ 端到端编译 + 运行时验证
           
P1 (推荐): Phase 4 (TAA)                    [显著改善画面质量]
           Phase 6 (PushConstants 扩展)     [TAA/Bloom 前置依赖]

P2 (可选): Phase 5 (Bloom)                  [锦上添花]
           Phase 7 (Java framebuffer infra) [Bloom 前置依赖]
           
P3 (远期): 星空贴图替换                      [需要外部纹理加载器]
```

**预估总工时：**
- P0（核心）：2.5 天
- P1（TAA+PushConsts）：1.5 天  
- P2（Bloom+infra）：2 天
- **合计：6 天**（含调试和参数调优）


---

## 八、历史会话进度记录（TAA + Bloom Java Infra）⚠️ 已过时

> **2026-08-29 备注：** 本节为 2026-08-09 会话的原始记录。其中标记 ✅ DONE 的 Java 侧工作（Phase 0 TAA/Bloom infra）曾随 `560f3ea` 提交，后在 `eb50a70` 中被整体回滚，**当前代码库中不存在**；末尾记录的编译错误也就此作废。实际状态以下文「九、实际状态核对」为准（后附的「七、端到端验证清单」之后的第二份会话记录同理，其 "P0/P6/P7 DONE"、"(208 bytes)" 等结论均已被回滚覆盖）。

### Phase 0：Java TAA/Bloom 基础设施 ✅ DONE

**目标：** 为 TAA ping-pong 和 Bloom post-process 创建 Vulkan 资源，不修改 GLSL shader。

#### 完成内容：

1. **`BlackHoleRender.java` — 新增字段和方法：**
   - `long[] taaImages[2]` — TAA ping-pong VkImage handles（VMA 分配）
   - `ImageView[] taaColorViews[2]` — 颜色附件视图（渲染目标用）
   - `ImageView[] taaSamplerViews[2]` — 只读采样器视图（TAA blend 时读取前一帧）
   - `DescSet[] taaDescSets[MAX_IN_FLIGHT]` — TAA descriptor set per frame
   - `DescSetLayout taaDescSetLayout` — sampler2D binding layout (binding=0)
   - `createVkImage()` — VMA 图像创建辅助方法（vmaCreateImage）
   - `createTaaTextures()` — 为每个 ping-pong buffer 分配 VkImage + ImageView
   - `createTaaDescLayout()` — 创建 descriptor set layout (COMBINED_IMAGE_SAMPLER)
   - `allocateTaaDescSets()` — 从 descAllocator 分配 TAA descriptor sets
   - `updateTaaDescSets()` — 每帧更新 descriptor set（绑定 ping-pong sampler view）

2. **`BlackHoleRender.java` — 修改：**
   - `init()`: 重建资源顺序 → textures → desc layout → pipeline (with desc layout) → desc sets
   - `render()`: **多 pass 渲染**（Phase 1: blackhole→ping-pong A；Phase 2: composite bloom to swapchain）
     - Pass 1: 渲染到 ping-pong texture，绑定前一帧 sampler2D
     - TAA blend + Bloom post-process 在 fragment shader 中完成
     - Pass 2: 合成结果写入 swapchain
   - `resize()`: 重建所有 TAA 资源（textures + desc sets）
   - `cleanup()`: 释放 TAA textures, descriptor sets, desc layout

3. **`DescSet.java` — 新增方法：**
   - `setImage(Device, long imageView, int binding)` — 将图像采样器绑定到描述符集
     - 使用 `VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER`
     - sampler=0（pipeline 中未配置 sampler，shader 直接读取）
     - imageLayout = `VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL`

4. **PushConstants 扩展：**（已在 `setPushConstants()` 中添加）
   - `vec2 iMouse` — 鼠标位置 [0, width] × [0, height]
   - `vec2 iResolution` — 屏幕分辨率 (width, height)
   - `float iTimeDelta` — 帧间隔秒数
   - `int iFrame` — 帧计数器（用于 TAA，前 2 帧强制全权重）
   - 总大小: 180 + 8 + 8 + 4 + 4 = **200 bytes → padded to 208**

#### 待完成：
- GLSL shader 更新（Phase 1-5）：使用 sampler2D uniforms 读取前一帧纹理、实现 TAA blend 和 Bloom composite
- `updateTaaDescSets()` 完整实现（每帧交换 ping-pong 索引，绑定正确的 sampler view）

---

### Phase 状态总览

| Phase | 内容 | 状态 |
|-------|------|------|
| P0 | Java TAA/Bloom infra | ✅ DONE |
| P1 | GLSL constants + utility functions | ✅ DONE (commit ce82fbb) |
| P2 | Euler geodesic stepping | ⏳ 待实施 |
| P3 | diskcolor volumetric rendering | ⏳ 待实施 |
| P4 | TAA blend in shader | ⏳ 待实施（需要 GLSL sampler2D） |
| P5 | Bloom post-process | ⏳ 待实施 |
| P6 | PushConstants extensions | ✅ DONE (代码已添加) |
| P7 | Java framebuffer infra | ✅ DONE |

### 本会话状态：已暂停（编译错误待修复）

**时间：** 2026-08-09，会话中途停止。

**已知编译错误（BlackHoleRender.java）：**
- `descAllocator` 字段不存在 — `allocateTaaDescSets()` / `cleanup()` 引用了不存在的变量
- `VmaAllocationCreateInfo.calloc(allocStack)` 签名不对 — 应为 `.calloc(1, stack).get(0)`
- `vmaCreateImage` 调用参数顺序错误（旧代码残留）
- `VkDescriptorSetLayoutBinding` 不能直接赋值给 `.pBindings()` — 需要 `.Buffer` 类型
- `DescSetLayout.cleanup(device)` 参数类型应为 `VkCtx` 而非 `Device`

**修复方案：**
1. 添加 `private DescAllocator descAllocator;` 字段并在构造/初始化时创建
2. 使用 Image.java 中的 VMA 正确模式（见其构造函数的 `allocCreateInfo`）
3. `createTaaDescLayout()` 中 binding 用 `.calloc(1, stack).get(0)` 
4. cleanup 中调用 descSetLayout.cleanup(vkCtx)
5. 删除旧的 createVkImage 残留代码（第204-209行）

**下一步：** 修复上述编译错误后 `mvn compile`，然后继续 GLSL shader Phase 1-3 实施。
---

## 七、端到端验证清单

> ⚠️ 本节清单本身仍可作为回归测试用，但清单之后的「Phase 状态总览（更新）」与「本会话状态：Phase 2/3 完成」是 2026-08-10 的历史记录——其中 P0/P6/P7 的 DONE 与 "(208 bytes)" 结论已被 `eb50a70` 的回滚覆盖，以「九、实际状态核对」为准。

| # | 检查项 | 预期结果 |
|---|--------|---------|
| 1 | `mvn compile` | 无编译错误 |
| 2 | Euler 步进生效 | 能看到引力透镜效应（背景星场弯曲） |
| 3 | diskcolor() 生效 | 看到带 Doppler 不对称的彩色吸积盘 |
| 4 | 步长连续 | 吸积盘无断裂条纹伪影 |
| 5 | Perlin Noise | 吸积盘表面有云状起伏细节 |
| 6 | TAA（如启用） | 噪点明显减少，无拖影 |
| 7 | Bloom（如启用） | 内侧过曝区域产生光晕效果 |
| 8 | 鼠标控制相机 | θ/φ 变化时视角平滑旋转 |
| 9 | 奇点命中 | 进入事件视界后变黑 |
| 10 | 低温修正 | 外盘边缘不会显示不自然的纯红色 |

### Phase 2：测地线步进替换 ✅ DONE (commit) + Phase 3: diskcolor() ✅ DONE

**完成内容：**

1. **Java 编译错误修复（BlackHoleRender.java）：**
   - 添加缺失的 import：`org.lwjgl.PointerBuffer`, `ImageView`, `ShaderModule`, `Pipeline`, `VkUtils`, `SwapChain`, `CmdBuffer`, `VkCtx`, `DescSet`, `PushConstRange`, `PipelineBuildInfo`
   - 添加 Vma static import: `import static org.lwjgl.util.vma.Vma.*;`
   - 添加 vkCheck static import: `import static vulkanb.eng.graph.vk.VkUtils.vkCheck;`
   - DescSetLayout 创建改用正确 API: `new DescSetLayout(vkCtx, new LayoutInfo(...))`
   - allocateTaaDescSets() / cleanup() 使用 `vkCtx.getDescAllocator()` 替代不存在的字段
   - taaDescSetLayout.cleanup() 参数改为 VkCtx
   - ImageViewData 改用 fluent builder: `.format().aspectMask().layerCount().mipLevels()`
   - VkDescriptorSetLayoutBinding.calloc(1, stack) 创建 Buffer

2. **GLSL shader Phase 2 (Euler stepping):** ✅ 已实现
   - main() 包含完整的 Euler symplectic raymarching loop
   - 连续分段球对称步长（Dis≥2ROut, 1ROut..2ROut, <ROut）
   - 先更新 RayDir（引力偏折），再移动 RayPos（辛格式关键）
   - RandomStep() UV 抖动消除条纹伪影

3. **GLSL shader Phase 3 (diskcolor):** ✅ 已实现
   - 完整 volumetric raymarching：黑洞坐标系变换 → Shape 轮廓 → 双噪声层 → Doppler/RedShift → 黑体颜色映射
   - 低温修正 T<400→black, T<1000 线性衰减
   - shiftMax=5 截断防止过曝

4. **GLSL shader fixes:**
   - `fragCoord` → computed from `uv * iResolution` (Vulkan doesn't have built-in fragCoord)
   - TAA blendWeight: removed invalid `.y` check on vec2, simplified to `iFrame < 2` only
   - Vertex shader PushConstants extended with iMouse/iResolution/iTimeDelta/iFrame

**端到端验证：**
- ✅ `mvn clean package -DskipTests` BUILD SUCCESS
- ✅ Java 40 source files compile without errors
- ✅ SPIR-V shaders will be compiled at runtime by ShaderCompiler (GLSL > SPIR-V)

---

### Phase 状态总览（更新）

| Phase | 内容 | 状态 |
|-------|------|------|
| P0 | Java TAA/Bloom infra | ✅ DONE |
| P1 | GLSL constants + utility functions | ✅ DONE (commit ce82fbb) |
| P2 | Euler geodesic stepping | ✅ DONE |
| P3 | diskcolor volumetric rendering | ✅ DONE |
| P4 | TAA blend in shader | ⚠️ Partially done (simplified, no ping-pong framebuffer yet) |
| P5 | Bloom post-process | ⏳ 待实施（需要多-pass render pipeline） |
| P6 | PushConstants extensions | ✅ DONE |
| P7 | Java framebuffer infra | ✅ DONE |

### 本会话状态：Phase 2/3 完成，编译通过

**时间：** 2026-08-10

**验证清单（已满足）：**
| # | 检查项 | 结果 |
|---|--------|------|
| 1 | `mvn compile` / `mvn package` | ✅ BUILD SUCCESS |
| 2 | Euler 步进代码结构 | ✅ 辛格式：先更新方向再移动位置 |
| 3 | diskcolor() 完整实现 | ✅ 含双噪声层、Doppler/RedShift、低温修正 |
| 4 | PushConstants 扩展 | ✅ vert + frag 同步 (208 bytes) |
| 5 | Java TAA infra | ✅ ping-pong textures, descriptor sets |

**待运行时验证：**
- [ ] 实际渲染效果（引力透镜、吸积盘、Doppler不对称）
- [ ] FPS/性能测试
- [ ] Bloom post-process（Phase 5，可选）

---

## 九、实际状态核对（2026-08-29）⭐ 权威状态

**基准：** 分支 `glsl-migration-plan`，HEAD `f09ed17`，工作区干净。本节覆盖上文所有 Phase 状态标记与历史会话记录（七、八节）。

### 9.1 关键历史脉络

| 提交 | 日期 | 内容 |
|------|------|------|
| `203a663` + `ce82fbb` | 2026-08-09 | Phase 1-3 变体落地：Euler 步进 + `DiskColor` 体积渲染 + 工具函数 |
| `560f3ea` | 2026-08-09 | TAA/Bloom Java infra + 本计划文档（该会话记录见第八节） |
| `b9c6c2b` → `8d382c7` | 2026-08-10 | 坐标系统修复链（黑屏、坐标系混用） |
| `eb50a70` | 2026-08-10 | 修黑屏时**整体回滚 TAA/Bloom Java infra**（-729 行），单 pass 方案回归 |
| `55a2da8` + `dcf9cd1` | 2026-08-10/11 | 深度调优，**品红伪影 / 画面分层 / 远距离吸积盘消失全部解决**（用户确认） |
| `f09ed17` | 2026-08-13 | jar 打包支持：运行时在 `shader-cache/` 目录缓存 .spv |

### 9.2 渲染问题修复状态

| 问题 | 状态 |
|------|------|
| 黑屏 | ✅ 已解决 |
| 品红伪影 | ✅ 已解决（用户确认，2026-08-29） |
| 画面分层 | ✅ 已解决（用户确认） |
| 远距离近屏侧吸积盘消失 | ✅ 已解决（用户确认） |
| jar 打包无法运行 | ✅ 已解决；~~遗留：jar 重新部署后 GLSL 变更不会使旧 .spv 失效（jar 模式仅检查 .spv 是否存在，不比对内容/时间戳），需手动删除 `shader-cache/`~~（已修复，见 9.5 第 3 条：a30344a 源码哈希缓存键 + 旧缓存自动清理，2026-09-26 核对 ShaderCompiler 中 SHA-256 键在位） |

### 9.3 当前 `blackhole.frag` 实况（2026-08-29 Rs 参数化重构后）

> **量纲约定（2026-08-29 重构）：** `pc.schwarzschildRadius` 为世界坐标长度的 Rs，在所有公式中显式参与（偏折 `1.5·Rs/r²`、红移 `√(1-Rs/r)`、盘厚度 `0.5·Rs·Shape` 等）；`diskInner/OuterRadius` 按"Rs 的倍数"传入，shader 内乘 Rs 换算为世界单位；噪声坐标以 Rs 归一保证盘纹理随 Rs 缩放形态不变。Java 仍传 1.0/3.0/18.0，渲染结果与重构前一致；传其他 Rs 值时黑洞整体（视界/盘厚/盘径）随之一致缩放。

- **已落地**：`softHold`/`Shape`/`RGB(T)` 低温修正/`WavelengthToRgb`/`RandomStep`/`Vec2ToTheta`/`perlin`+`GenerateDiskNoise`（fBM）/`DiskColor` 体积渲染（双噪声层、Doppler+RedShift、稀薄气体）/远相机包围球跳进/Euler 偏折（阻尼变体）/分段步长（变体）/log 色调映射
- **背景**：`starfield()` 程序化星空（多层彩色亮星 + 星云）为主路径，逃逸后做三色波段蓝移合成
- **未实施**：TAA、Bloom、`GetBH` 盘法向坐标系、SpiralTheta 螺旋、UV 起点抖动、角向变形项
- **已知与文章的物理偏差**：步进顺序（先移动后偏折 vs 辛格式）、`omega` 开普勒式、温度模型 `pc.temperature·(RIn/r)^0.75`、`ShiftMax=1.5`

### 9.4 Java 侧实况

> **2026-09-26 核对：本小节为 Rs 参数化重构当日快照，多条已被同日稍后的落地超越，勿按此为准——**
> TAA/Bloom 资源与描述集见 §9.7/§9.8（TAA 历史缓冲 RGBA32F ×4、TAA 描述符布局+采样器均已落地）；
> push constants 现为 **224 字节**（+iTimeDelta/iFrame/iCameraMoved、iFade、iCameraVel/iCameraGamma，
> 布局见 `BlackHoleRender` 类头注释）；shader-cache 失效策略已修复（见 9.5 第 3 条）。
> `if_dopplerI/T` 至今仍仅声明未消费（本次复核确认）。

- `BlackHoleRender`：单 pass 动态渲染直写 swapchain（R32G32B32A32_SFLOAT、Y 翻转视口、6 顶点全屏四边形）；无 TAA/Bloom 资源、无描述集
- Push constants 204 字节：Rs=1.0、RIn=3.0、ROut=18.0、temperature=15000K（`BaseTemperature`）、timeRate=1.0、`place_holder1..4` 与 `rotationSpeed`、`if_dopplerI/T` 未被 shader 使用
- `ShaderCompiler`：shaderc 运行时编译；普通模式按时间戳增量编译 .spv；jar 模式读 classpath 内 GLSL、写 `shader-cache/`（有上述失效策略缺陷）

### 9.5 剩余工作（按优先级，2026-08-30 刷新；2026-09-26 补记第 5 条否决结论）

1. **复核项**：步进顺序辛格式已对调（08f9f03），待用户视觉 A/B 确认
   （2026-09-26 核对：辛格式仍在 blackhole.frag:452 生效，自落地近月无回退请求）
2. ~~Phase 5 Bloom~~ ✅ 已落地（圆盘模糊简化版，ce998a3；质量不足再做 mip 链）
3. ~~ShaderCompiler jar 缓存失效策略~~ ✅ 已落地（源码哈希缓存键 + 旧缓存自动清理，a30344a）
4. ~~omega 史瓦西对齐~~ ✅ 已落地（√(Rs/2r³)，内盘动画慢约 29%，a30344a）
5. ~~专项候选：全重投影 TAA~~ ❌ **已实测否决，条目关闭（2026-09-26 补记）**。候选状态系
   悬置误差：本清单 16:51 刷新时仅记录了方向重投影的失败，18:04 全重投影也已实现并回退，
   未及回写，悬置近一个月。实际经过：方向重投影（`2979bac`）与世界坐标全重投影
   （`b0378ad`，重投影键 = raymarch 终点 RayPos）均于 2026-08-30 当日实现、当日回退
   （`875ac03`、`d5b6065`）。**根因为方案原理性缺陷，非工程欠账**：透镜化 raymarch 的
   射线终点对初始条件混沌敏感，相邻像素终点差异数 Rs 且随视角剧变，不存在稳定的
   "像素内容位置"可作重投影键；两方案同症——高速清晰，慢速/静止时"无数窗口变形游动"。
   用户裁决：运动噪声可接受，变形游动不可接受；TAA 定稿两态（静止全量累积/运动硬重置，
   "锐利稳定颗粒"），测地模式恒动裸噪声为已接受代价（结论亦记录于 blackhole.frag
   ApplyTAA 注释）。Java 侧基建保留未消费：prevCam UBO 仍逐帧写入但着色器不再读取；
   iCameraMoved 三态计算中 "2"（平滑运动，源自 6b4430c/86abf14 三态混合试验）分支已死
   ——frag 仅消费 0/非0，"2" 的实际效果 = 硬重置。唯一留口的后续方向：**亮度加权散射
   质心**作重投影键（沿光线对散射事件求亮度加权质心，相邻像素连续、不混沌），属新方案
   研究而非补短板，近期无重开计划（d5b6065）。
6. 远期：温度模型对齐文章（会改变观感，需重调）；~~天空盒 mipmap~~ ✅ 已落地（CubeTexture
   vkCmdBlitImage 逐级生成完整 mip 链，双天空盒共用，2026-09-26 核对）+ 4K 星空图（仍开放，
   现为 1024²/2048² 两套）；TAA 历史缓冲改 RGBA16F；
   Antiverse 反宇宙背景；Java 侧注释与代码质量整理（含清理 iCameraMoved 三态残留与
   prevCam UBO 写入——若确定不走质心方案可一并移除）

### 9.7 TAA 接入（2026-08-29）✅ DONE

**落地内容（对应 Phase 6 + Phase 0/7 + Phase 4）：**

- **Phase 6（PushConstants 扩展，变体）**：204 字节布局不变，`place_holder1..3` 复用为 `iTimeDelta`(f)、`iFrame`(i)、`iCameraMoved`(i)——无需扩容，shader/Java 两侧同步
- **Phase 0/7（基础设施，变体）**：`BlackHoleRender` 内新增——
  - 4 张历史图（每帧插槽 2 张 ping-pong，RGBA32F，COLOR_ATTACHMENT|SAMPLED），**插槽隔离**：插槽 s 只读自己 2 帧前写的历史（栅栏保证完成），因此**无需跨帧信号量，Render.java 零改动**
  - MRT 双附件：附件 0 = swapchain（CLEAR），附件 1 = 历史写入目标（DONT_CARE）；管线 colorFormats 为双 RGBA32F
  - TAA 描述符布局（set 1）+ 每插槽描述符集（每帧更新指向当前读取的历史图）+ NEAREST 采样器
  - 每帧收尾两条 sync2 barrier 翻转历史图布局（COLOR↔SHADER_READ）
- **Phase 4（TAA 混合）**：frag 在**色调映射前于 HDR 域混合**：`blendWeight = 1 - 0.5^(dt/τ)`，`τ = clamp(0.3/timeRate, 0.02, 0.3)`；`iFrame < 2` 或 `iCameraMoved == 1` 时跳过历史直接重置；混合结果同时写入 swapchain（经色调映射）与历史缓冲（原始 HDR）
- **相机变化检测**：Java 侧逐帧精确比较视图/投影矩阵（JOML equals），变化即重置累积——等价于文章的 `iMouse.z > 0` 拖拽重置，但全自动

**与文章版 TAA 的已知简化：**
- 无重投影/速度缓冲：相机移动时直接重置累积（静止/慢速相机为主的使用模式下无拖影，移动瞬间画面噪声变大一瞬）
- 插槽隔离使历史实际为 2 帧前结果（静止相机下视觉等价）
- τ 简化为常数 0.3s（文章为 ω(r) 动态公式），如出现盘面拖影可调小该值
- 历史缓冲 RGBA32F（1080p 约 33MB）；4K 下如显存吃紧可改 RGBA16F

**修复记录（2026-08-29，同日二次提交）：** 初版重置标记 `taaReset` 为全局单次消费，但历史是按插槽的——resize/停止移动后，另一插槽首帧会读到未写过的历史图（显存原始值 = 品红/绿色斑点）或运动期间的错位历史（双影游动 1~2s），并按 0.96/帧缓慢衰减。修复：改为**按插槽**跟踪 `taaPrevMoved[slot]`（上次运行在变化/重置 → 本帧继续跳过历史），每个插槽首跑各自强制重置一次；另在 shader 读历史处加防御性钳制。resize 拖动期间的闪烁为交换链重建风暴（每次 WM_SIZE 全量重建），属已知固有行为，如需消除可后续做尺寸稳定去抖。

### 9.8 自由视角相机模式（2026-08-29）✅ DONE

- `Camera` 新增 `CameraMode`（ORBIT / FREE_FLY）与 `F` 键切换（`Main.input` 单帧触发）
- FREE_FLY：yaw/pitch 欧拉角（无翻滚，俯仰钳制 ±89°），视图矩阵 `V = Rx(-pitch)·Ry(yaw)·T(-position)`；WASD 沿视线飞行（移动方法本就沿视图矩阵轴，两模式共用），PageUp/PageDown 改为沿相机上方向移动
- **无缝切换**：ORBIT→FREE 从"指向原点的视线方向"换算 yaw/pitch；FREE→ORBIT 从当前位置反推球坐标（距离钳制 [3,50]，越界时沿同一视线吸附，有一次轻微推拉）
- 拖拽手感与轨道模式一致（抓住世界拖：`freeYaw -= dx·sens, freePitch += dy·sens`）
- **与 TAA 的交互**：模式切换 = 视图矩阵跳变 → 既有相机变化检测自动按插槽重置累积，无需额外代码；自由飞行期间矩阵逐帧变化 → TAA 保持关闭，停稳即恢复——与轨道模式语义一致
- **扩展键位（同日追加）**：左 Shift/Ctrl = 沿相机上方向升/降（与方向键同轴向）；Q/E = 绕视线轴翻滚（仅自由视角生效）
- **修订（同日三次提交）：自由姿态由欧拉角改为四元数。** 初版 yaw/pitch/roll 欧拉角有两个问题：(1) `Rx·Ry·Rz` 合成顺序使 roll 实际绕世界 Z 轴而非视线轴；(2) 欧拉角天生需要 ±89° 俯仰钳制防万向锁，无法越过头顶。现改为 `Quaternionf freeOrientation`（相机局部系→世界系），鼠标/翻滚均为 `rotateLocalY/X/Z` 增量旋转 + `normalize()` 防漂移——roll 恒绕视线轴、无俯仰钳制（可做筋斗）、视图矩阵 `V = conjugate(orientation)·T(-position)`。ORBIT→FREE 仍由"指向原点的视线方向"经 yaw/pitch 中间量构造四元数（翻滚归零），切换无跳变。代价：纯局部轴旋转下长时间鼠标环视会自然积累任意滚转（6DOF 太空模拟器的标准行为），Q/E 可用于有意配平
- **修订（同日四次提交）：自由视角鼠标/翻滚方向改为 FPS 标准手感**（拖动方向 = 视线转向：拖右看右、拖上抬头、Q=画面逆时针）。初版为"抓住世界拖"（拖动方向 = 世界移动方向），与轨道拖拽手感保持一致但用户实测三个方向全部相反（俯视黑洞时拖左画面逆时针、拖上黑洞上跑、Q 画面顺时针）。注意：轨道模式仍为 grab 手感（黑洞恒居中，方向感被掩盖），两模式手感刻意不同；如需统一改轨道模式 `addOrbitRotation` 符号即可
- **修订（同日五次提交）：rotateLocal\* → rotate\*。** 用户复测发现俯视黑洞时水平拖动仍是"绕画面中心原地旋转"、Q/E 仍是"平移看向"——实证 JOML `Quaternionf.rotateLocalY/Z` 实际绕**父级/世界轴**（俯视时世界竖直轴恰为视线轴 → 水平拖动变成原地旋转；世界水平轴 → Q/E 变成平移）。改为 `rotateY/rotateX/rotateZ`（局部轴右乘）：鼠标水平恒为绕相机 up 轴转头、Q/E 恒绕视线轴翻滚，任意姿态下语义正确。toggleMode 姿态构造同时改为 `rotationY().mul(rotationX())` 显式控制乘法顺序
- **终版翻滚约定（用户手改，同日）**：`addRoll` 去掉内部取负，语义定为**正 = 向左滚**（镜头系，画面顺时针）；Main 中 Q → addRoll(+)、E → addRoll(−)。用户终测手感：俯视时水平拖动 = 平移扫过地面、Q/E = 绕画面中心旋转，全部符合预期

### 9.6 天空盒接入（2026-08-29）✅ DONE

**来源：** NPGS 项目（`D:\CodingSpace\IdeaProjects\NPGS`，知乎作者的 C++ 引擎版黑洞）的 `Universe0Skybox`（6 张 1024×1024 星空 JPG）。加载参数对齐 NPGS：`R8G8B8A8_UNORM`、不垂直翻转、~~暂不生成 mipmap~~（mipmap 后已补：CubeTexture 以 vkCmdBlitImage 生成完整 mip 链——2026-09-26 核对）。

**改动清单：**
- `pom.xml`：新增 `lwjgl-stb`（含各平台 natives）
- `Image.ImageData`：新增 `flags()`（cubemap 需要 `VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT`）
- `DescSet`：新增 `setImage(device, sampler, imageView, binding, type)`（Combined Image Sampler）
- `CubeTexture.java`（新增）：classpath 加载 6 面 → 暂存缓冲 → 单次提交（逐面 `vkCmdCopyBufferToImage` + 布局切换）→ CUBE 视图 + 线性采样器（设备支持时开 8x 各向异性）
- `BlackHoleRender`：init 创建贴图/描述集布局/描述符集并在管线中声明（set 0）；render 每帧 `vkCmdBindDescriptorSets`；cleanup 释放
- `blackhole.frag`：新增 `layout(set = 0, binding = 0) uniform samplerCube uSkybox`；逃逸分支改为贴图采样 + 三色波段蓝移，并移植 NPGS `SampleBackground` 的 OStrength 亮度保持；`starfield()` 保留为程序化回退参考（编译器剔除）
- 贴图资源：`resources/textures/skybox/{PosX..NegZ}.jpg`（jar 模式经 classpath 读取，同样可用）
