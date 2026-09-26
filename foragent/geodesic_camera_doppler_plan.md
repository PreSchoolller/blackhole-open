# 相机测地线运动模式 + 相机速度多普勒效应 实施计划

> **补记(2026-09-26):Phase 3 剩余项归宿已代码实证,下方"未做:Phase 3 剩余(全 4-动量/标架输运/
> 参数面板/调试开关)"已过时——**
> - **3-3 视线平行输运 ✅ 完成(2026-09-05,经 kerr_port_plan Phase 5,分支 phase5-tetrad-camera
>   合并 d791fec;修复 77f0798、b967e39)。** GeodesicIntegrator RK4 8 维→16 维(U+e1/e2/e3 标架腿
>   按 de/dτ=−Γ(U,e) 与 U 联立推进,每子步 Gram-Schmidt 重正交);鼠标转头改转 headQuat,每帧
>   相机世界姿态 = 输运标架 × headQuat(InputController.deriveCameraOrientation;stepGeodesic
>   仅按相机模式触发、不分时空)——两种时空模式共用同一积分器,视线被时空拖拽均生效。
>   验收:标架正交性 5 圈 1.1e-15(机器精度),A-D 基线逐位一致。
> - **3-2 全 4-动量多普勒:克尔侧 ✅**(kerr.frag GetInitialMomentum 的 iObserverMode==-1 分支,
>   `P_up_cam = iU_up − Σ vᵢ·ie_i_up`,NPGS 能量比法;KerrRender 测地激活打包 mode=-1 +
>   四维标架 UBO);**史瓦西侧 ❌ blackhole.frag 仍为 Phase 2 近似 CameraDoppler = γ(1−β·n̂)**
>   ——此为 3-2 真实剩余,共享积分器的标架基建已就绪,收益主要在视界附近。
> - **3-4 参数面板 ✅ 完成**(经 gui_overlay_plan,2026-08-31,四滑条 + Orbit tilt)。
> - **3-5 多普勒调试开关 ❌ 仍开放**(无 if_camDoppler,GUI 亦无开关;小活)。
> 综上 Phase 3 真实剩余 = 史瓦西侧 4-动量升级(可选) + 3-5 开关(顺手)。

> **状态(2026-08-29):Phase 1 + Phase 2 已实现,待运行时验收。** 实际落地与计划的差异:
> - 积分器放 `eng.scene.GeodesicIntegrator`(计划一致),实例挂在 `Scene` 上(而非 Main),
>   渲染侧经 `EngCtx.scene().getGeodesic()` 取 β/γ——沿用单线程假设,无竞态;
> - 克氏符直接用方案 B(数值中心差分),精度满足"圆轨道 5 圈"验收,方案 A 留作优化;
> - 约束投影采用"每子步重解 U^t"实现(比计划的风险项 4 更早落地);
> - 圆轨道方向 ĥ 在两极(r̂ ∥ ŷ)退化,已加回退轴;R 键在 r 过低时返回失败提示;
> - push constants 布局按评审修正执行:总量 224B,vec3@208(16 字节对齐),γ@220;
> - 亮度乘子统一 `clamp(g_cam³, 0.1, 10)`;背景 Shift 上限改为乘后统一 min(...,8.0)。
> - 未做:Phase 3 剩余(全 4-动量/标架输运/参数面板/调试开关)。
> **Phase 3-1 视界坠落演出已完成(2026-08-30):** r<1.02Rs 触发——iFade(复用 place_holder5@204)
> 0.6s 淡出黑屏,沿当前方向弹回 5Rs 并初始化稳定圆轨道,0.6s 淡入;演出期间积分器冻结,
> 淡出仅作用于显示输出(不污染 TAA 历史)。同时引力偏折改为辛格式(先更新方向再移动位置,
> 文章 2025.12.19 修正),观感待 A/B,不满意可单独 revert。
>
> **实现后修复(2026-08-29 晚,无头测试驱动验证):**
> 1. **数组越界**:Γ 公式第三项 ∂_ρ g_νσ 的 ρ 遍历 0..3(含时间),`dG` 数组原开 [3][4][4] → AIOOBE;
>    静态度规时间导数恒 0,改为 [4][4][4] 并保持时间切片零。
> 2. **圆轨道初条件**:不能用静态观者局部速度 v=√(M/(r-Rs)) 直接当坐标空间速度(差 ~13%,
>    得到椭圆轨道);严格条件是角速度比 φ̇=Ω·ṫ,Ω=√(M/r³),即 u⃗ = Ω·U^t·(ĥ×r̂),
>    U^t 由归一化二次式与 u⃗ 互相依赖 → 8 次不动点迭代。
> 3. **KS 切片符号实证**:l_μ=(1,+x/r,…) 的切片下落可平滑穿视界(黑洞片);取负号是白洞片,
>    下落粒子会被弹射到 r~10¹⁵(实测)。保留 +x/r 并在代码注释中记录实证结论。
> 验收(无头驱动 GeoTest):r=6Rs 圆轨道 5 圈 maxDrift=0.00000、E=0.96225 恒定、
> β=0.3162c 与解析值 √(M/(r-Rs)) 一致;瞄准中心 0.5c 俯冲平滑穿视界冻结于保护半径。

> 目标:为相机新增第三种可切换的运动模式——**测地线模式**:给定初速度(可调),相机在史瓦西引力场中沿测地线自动运动;
> 同时把相机的相对速度纳入多普勒计算(红/蓝移),使观测者运动影响吸积盘与背景星空的颜色。
>
> 本计划基于当前代码实际状态(HEAD `f09ed17` 后,含 TAA ping-pong 版 BlackHoleRender)逐文件核对后编写。
> 结论:**可行性高,预计总工作量 1~2 天**。Phase 1(纯 Java,半天)、Phase 2(着色器,半天)、Phase 3(可选增强)。

---

## 一、现状分析(代码事实)

| 模块 | 现状 | 关键位置 |
|------|------|---------|
| 相机运动 | 只有位置,没有速度概念。WASD 直接 `position += 方向 × diffTimeMillis × 0.003` | `Main.java:94-112` |
| 相机模式 | `ORBIT` / `FREE_FLY` 两模式,F 键切换,切换无跳变 | `Camera.java:32,130-152` |
| 盘多普勒 | **已实现**:`RelativeVelocity = dot(-DirOnDisk, CloudVelocity)`(只含盘物质轨道速度,不含相机运动) | `blackhole.frag:263-264` |
| 引力红移 | **已实现**:`GravRed = sqrt(1-Rs/PosR)/sqrt(1-Rs/CamR)`(把相机当静态观者) | `blackhole.frag:265-267` |
| 背景蓝移 | **已实现**(只含引力项):`BackgroundBlueShift = min(1/sqrt(1-Rs/CamR...), 2.0)` | `blackhole.frag:369,458-461` |
| Push Constants | 共 204 字节,`[200..204]` 为 `place_holder4` 空闲 | `BlackHoleRender.java:59,564-566` |
| 单位约定 | 世界长度 = Rs 的倍数(Rs 恒为 1.0 推入),几何单位 G=c=1,**M = Rs/2 = 0.5** | `BlackHoleRender.java:507` |
| 着色器编译 | shaderc 运行时按时间戳自动重编译 .glsl → .spv,**改 GLSL 无需手动编译** | `ShaderCompiler.java:110-125` |
| TAA | 相机每帧变化 → `iCameraMoved=1` → 跳过历史累积。测地模式下相机恒动,TAA 将持续处于重置状态 | `BlackHoleRender.java:552-560` |

**关键结论**:相机速度多普勒 = 在现有 RedShift 管线上乘一个方向相关因子,是**加法改造**;
真正的新代码只有 Java 侧的测地线积分器。参考实现可直接对照 NPGS 项目
(`D:\CodingSpace\IdeaProjects\NPGS\NPGS\Sources\Program\Application.cpp` 的 `GeodesicIntegrator` 命名空间),
但本项目是史瓦西时空(无自旋 a=0、无电荷 Q=0),可大幅简化。

---

## 二、总体设计

### 2.1 模式设计

- 新增第三模式 `GEODESIC`,**按 G 键进入/退出**(保留 F 键原有的 ORBIT ↔ FREE_FLY 切换,互不干扰);
- 进入时:位置 = 当前相机位置;初速度方向 = 当前视线方向,大小 = 可调的 v0(以光速 c 为单位,默认 0.3);
- 模式内:位置每帧由积分器写回相机,姿态(视线)仍由鼠标自由控制(和 NPGS 一致:运动归物理,转头归玩家);
- 退出时:回到 FREE_FLY,丢弃速度,位置保持在退出点(距离超出 [3,50] 时沿用现有钳制逻辑,会有一次轻微推拉,可接受);
- **可选加分项**:模式内按 R 键 = 初始化为当前半径的圆轨道(见 §4 公式④),这是观赏吸积盘的最佳初条件。

### 2.2 时间标定

积分器推进的是固有时 τ(几何单位)。每帧推进量:

```
total_dtau = (diffTimeMillis / 1000.0) × timeScale
```

参考标定:圆轨道角速度 Ω(r) = √(M/r³)。默认相机 r = 18 Rs 时 Ω ≈ 0.00926 rad/τ,
取 `timeScale = 5.0` → 约 2.7°/s 真实时间,一圈约 2.2 分钟,观感合适。timeScale 可调(§6 按键)。

---

## 三、Phase 1 — 测地线积分器(纯 Java,先让相机"落"起来)

### 3.1 新文件 `src/main/java/vulkanb/eng/scene/GeodesicIntegrator.java`

状态向量(几何单位,长度以 Rs 为单位):

```
X = [x, y, z, t]        位置(t 为坐标时)
U = [Ux, Uy, Uz, Ut]    四速度(上行指标),约束 U·U = g_μν U^μ U^ν = -1
```

采用**笛卡尔 Kerr-Schild 坐标**(a=0 的史瓦西特例,视界处坐标天然无奇点,直接穿视界不炸):

**度规**(NPGS `ComputeMetric` 的 a=0, Q=0 特例):
```
f(r)  = Rs / r                                   (Rs = 1)
l_μ   = (1, x/r, y/r, z/r)                       (类光矢量,下行)
g_μν  = η_μν + f·l_μ·l_ν                          (η = diag(+1,+1,+1,-1))
g^μν  = η^μν - f·l^μ·l^ν                          (l^μ = η^μν l_ν)
```

**克氏符**:两种方案任选(建议先 B 后 A):
- **方案 A(推荐,精确)**:解析计算。可直接抄 NPGS `Application.cpp:123` 的 `ComputeChristoffel` 并令 a=Q=0 简化(公式量减半);
- **方案 B(兜底,简单)**:数值差分 `Γ^μ_νσ = ½ g^μρ (∂_ν g_ρσ + ∂_σ g_ρν - ∂_ρ g_νσ)`,g 用上面的解析式,中心差分步长 1e-5。先跑通再换 A。

**积分**:RK4,`dU/dτ = -Γ·(U,U)`,`dX/dτ = U`。
自适应步长(防视界附近发散,对照 NPGS `Application.cpp:2220-2227`):
```
dtau_max = 0.05 × max(r, 1)^1.5      单帧子步上限 500,超过则截断(等效时间变慢)
```

**初速度归一化**(给定空间分量 u_spatial,解出 U^t):解二次方程(NPGS `/tp` 实现,`Application.cpp:816-824`):
```
A·(U^t)² + B·U^t + C = 0
A = g_tt;  B = 2·g_ti·u^i (i 求和);  C = g_ij·u^i·u^j + 1
取正根(远离视界时 A<0,B 小,经典公式 U^t ≈ 1/√(1-v²) 退化成立)
```

**相机瞬时速度(给多普勒用,Phase 2 也要用)**——相对静态观者的 v/c:
```
U_t  = g_tμ·U^μ          (协变时间分量 = 静态观者能量)
v²   = 1 + g_tt / U_t²    (NPGS Application.cpp:3793 同款公式;r > Rs 时 0 ≤ v² < 1)
β 向量 = U_spatial / U^t 再归一化到模长 v   (近似;精确版 Phase 3)
```

**圆轨道初条件**(可选的 R 键):
```
v_circ/c = √( M / (r - Rs) )   方向 ĥ = normalize(cross(ŷ, r̂))    (公式④)
```
sanity check(已修正): ISCO 在 **r = 3Rs**(=6M) 处 v = 0.5c;r = 1.5Rs(=3M) 为**光子球**,v = c。

### 3.2 修改 `Camera.java`

- `CameraMode` 枚举加 `GEODESIC`;
- 新增 `public void setPosition(Vector3f p)`(积分器回写位置用,内部 `recalculate()`);
- 新增 `public Vector3f getViewDirection()`(进入测地模式时取初速度方向,可用 `viewMatrix.positiveZ` 取反实现);
- FREE_FLY 姿态在测地模式下保持可旋转(复用现有 `rotateFree`),无需改动。

### 3.3 修改 `Main.java`

- `input()`:
  - `G` 单帧触发(`keySinglePress`)切换测地模式:进入时用当前 position/视线/v0 调 `GeodesicIntegrator.initialize(...)`;退出时 `camera.setPosition(积分器当前位置)` 并切回 FREE_FLY;
  - **测地模式下忽略 `F`**(防止积分器驱动位置时切 ORBIT/FREE_FLY 造成状态错乱;2026-08-29 评审补充);
  - 模式内:**W/S = 沿视线方向增减初速度**不合适(运动已交给物理),改为 **W/S = 推力**(沿视线方向改变四速度空间分量后重新归一化,力臂 = thrust × dtau,NPGS 同款体验);推力大小滚轮/`[` `]` 调节,默认 0.5;
  - `↑`/`↓`(模式内不用于移动)= timeScale 增减(×1.25 步进,范围 [0.1, 50]),控制台打印;
  - v0 调节:`1`/`2` 键 ±0.05c(范围 [0.05, 0.99]),仅进入测地模式前有效,控制台打印;
  - **注意按键冲突**:现有 `KP_ADD/KP_SUBTRACT` 已被温度占用(`Main.java:162-169`),不要复用。
- `update()`:测地模式下调用 `integrator.step(diffTimeMillis, timeScale)`,然后 `camera.setPosition(integrator.getPositionRs())`。
  (位置单位:积分器内部 Rs=1,与渲染世界单位一致,直接回写。)

### 3.4 Phase 1 验收标准

- [x] G 进入测地模式后,给 0.3c 初速度,相机明显被引力弯折,无按键介入自动运动;
- [x] 近黑洞处轨道弯折明显比远处剧烈(开普勒式差异可见);
- [x] 瞄准黑洞中心给速度,能一路穿过 r < Rs 不崩(着色器已有 `max(CamR, 0.001)` 钳制,画面退化为暗/异常但不 NaN);
- [x] 圆轨道初始化(R 键)在 r = 6Rs 处能稳定绕行 ≥ 5 圈不衰减不逃逸(检验积分精度);
- [x] 模式进出无跳变,G 回 FREE_FLY 后 WASD 正常。

---

## 四、Phase 2 — 相机速度多普勒(着色器 + Push Constants)

### 4.1 物理设计

现有着色器把相机当**静态观者**。相机以速度 β(相对静态观者)运动时,接收光子还需乘**狭义相对论多普勒因子**:

```
γ = 1/√(1-β²)
g_cam = γ·(1 - β·n̂)                 (公式⑤,n̂ = 光子传播方向,即光线步进终点处 -RayDir 的单位向量)
```

- n̂ 取光子传播方向(光源→观测者):**朝光源运动时 β·n̂ < 0 → g_cam = γ(1-β·n̂) > 1 → 蓝移**;远离时红移。
  ⚠️ 必须是 γ·(1-β·n̂) 而非其倒数——初稿写成倒数会导致红蓝移整体反向,与 §4.4 验收标准矛盾(2026-08-29 评审修正)。
  与现有盘多普勒 sqrt((1+v)/(1-v)) 的关系:纯径向 β·n̂=±v 时 g_cam = sqrt((1±v)/(1∓v)),风格统一;
- 合成:`RedShift_total = Doppler × GravRed × g_cam`(盘),`Shift_total = BackgroundBlueShift × g_cam`(背景);
- **亮度**:相对论不变量 I/ν³ → 接收亮度 × g³。现有代码盘亮度乘 `Doppler·RedShift`(近似),背景乘 `Shift`;
  相机因子建议单独乘 `clamp(g_cam³, 0.1, 10)` 防爆,后续再和作者的调法统一。

### 4.2 Push Constants 扩容(204 → 220 字节)

`BlackHoleRender.java`:
- `PUSH_CONSTANTS_SIZE` 204 → **224**(GPU 保证下限 128,实测常见 256;改完后用 `maxPushConstantsSize` 断言,不通过则把温度/占位字段压缩腾位);
- ⚠️ **std430 对齐:push constant 块内 vec3 必须 16 字节对齐**,不能从 200 开始(初稿 [200..211] 非法,编译器会插 padding 导致后续全错位;2026-08-29 评审修正)。正确布局:
```
[200..203]  place_holder4        float  (保留,兼作对齐 pad)
[204..207]  place_holder5        float  (对齐 pad)
[208..219]  iCameraVel           vec3   相机速度 β 向量(单位 c,静态观者系;16 字节对齐)
[220..223]  iCameraGamma         float  γ(Java 侧预计算,省着色器一次 sqrt;与 vec3 同槽)
```
- 数据来源:`GeodesicIntegrator.getBeta(Vector3f out)` / `getGamma()`(§3.1 公式);**非测地模式推 (0,0,0, γ=1),着色器自动退化为现状**;
- 同步更新类头部的布局注释(`BlackHoleRender.java:40-59`)。

`blackhole.frag` 的 PushConstants 块同步加字段(vec3+float 对齐 16 字节槽)。

### 4.3 着色器改动点(三处,均为乘法因子)

| 位置 | 现状 | 改动 |
|------|------|------|
| 盘多普勒 `L264-267` | `Doppler`、`GravRed` | 在 `RedShift = Doppler * GravRed` 后乘 `g_cam`(用盘命中点处光子方向算 n̂);`L323` 的温度公式自动被带动;`L329` 亮度行追加 `× clamp(pow(iCameraGamma·(1-β·n̂), -3.0), 0.1, 10)` 或直接 `g_cam³` 钳制 |
| 背景蓝移 `L369` | 只含引力项 | `BackgroundBlueShift *= g_cam`(n̂ 用逃逸光线的最终方向);注意现有 `min(..., 2.0)` 上限改成对 g_cam 相乘后统一 clamp(如 8.0) |
| TAA `L552-560` | 只检测矩阵变化 | **无需改**:测地模式相机每帧动,`iCameraMoved` 自动为 1(速度变化不改 view matrix 时会漏检——积分器回写 position,viewMatrix 必变,安全) |

`g_cam` 计算建议封装成 frag 内小函数 `float CameraDoppler(vec3 photonDir)`,三处复用。

### 4.4 Phase 2 验收标准

- [x] 静止(非测地)模式画面与改动前逐像素一致(回归测试:iCameraVel=0 时 g_cam≡1);
- [x] 测地模式朝盘冲过去时,前方盘明显蓝移变亮,身后盘红移变暗(刹车效应);
- [x] 高速侧向掠过盘面,靠近侧蓝、远离侧红,左右不对称可见;
- [x] v0 调到 0.9c 时背景星空整体明显蓝移、亮度增强,无花屏/NaN;
- [x] TAA 在测地模式下不出鬼影(每帧重置生效)。

---

## 五、Phase 3 — 可选增强(按兴趣排序)

1. **视界坠落演出**:r < 1.02Rs 时淡出到黑屏 + 控制台提示"已越过事件视界",并自动弹回 r=5Rs(比现在的钳制噪声画面友好);
2. **全 4-动量多普勒**(物理更严):把相机四速度 U^μ 整个传入着色器,光子初始 4-动量按 `P = U_obs + v·(标架)` 构造(参考 NPGS `BlackHole_common.glsl:683` 的 `GetInitialMomentum`),`g = (P·U)_src/(P·U)_obs`。可顺带修正引力红移里"相机是静态观者"的近似,但改动面大,收益主要在视界附近;
3. **视线平行输运**:目前测地模式转头还是欧氏四元数,严格版应把视线四矢量沿测地线输运(NPGS 的标架方案),表现为飞近黑洞时星空的"扭曲拖拽感";
4. **参数面板**:imgui/原生覆盖层显示当前 r、v/c、τ(参考 NPGS 的 Topology Map 窗口);
5. **多普勒调试开关**:复用 `if_dopplerI/if_dopplerT` 风格,加 `if_camDoppler` int,便于 A/B 对比。

---

## 六、按键总表(Phase 1+2 落地后)

| 按键 | 现状 | 测地模式下 |
|------|------|-----------|
| F | ORBIT ↔ FREE_FLY | **无效**(测地模式由 G 管理;积分器驱动位置时切模式会状态错乱) |
| **G** | 空闲 | **进入/退出测地模式** |
| **W/S** | 前进/后退 | **沿视线方向推力**(改变速度,重新归一化) |
| A/D、↑/↓、Shift/Ctrl | 移动 | 模式内无效;↑/↓ 复用为 timeScale 增减 |
| **`[` / `]`** | 空闲 | 推力大小增减(×1.25) |
| **1 / 2** | 空闲 | 初速度 v0 增减 ±0.05c(进入前设置) |
| **R** | 空闲 | 初始化为当前半径圆轨道(可选) |
| +/- | 温度 | 不变 |
| 鼠标右键捕获 + 移动 | 视角 | 不变(自由转头,不影响测地轨迹) |

---

## 七、风险与注意事项

1. **Push Constants 上限**:220B 超过部分设备 128B 保证值的可能极低(本项目已在用 204B,说明目标设备 ≥204),改完后先用 `maxPushConstantsSize` 断言;
2. **视界内渲染**:现有 `blackhole.frag` 多处假设 `CamR > Rs`(用 `max(..., 0.000001)` 钳制),穿视界后是"错误但不崩溃"的画面,Phase 3-1 处理观感;
3. **TAA 长期重置**:测地模式恒动 → TAA 永久关闭 → 盘边缘噪点比静止时明显。这是物理正确的代价,NPGS 同样如此。缓解:盘内区域的 `blendWeight` 与相机运动解耦(改动 TAA 逻辑,风险高,默认不做);
4. **数值精度**:float 积分器在 r < 1.1 附近步长已自适应收缩,若仍有轨道能量漂移,把 RK4 升级为 RK8 或对 U 每步重新归一化(`U·U = -1` 投影);
5. **与 `glsl_replacement_plan.md` 的关系**:本计划不冲突——那份文档针对盘渲染管线替换,本计划只动相机运动与红移合成处,Phase 2 落地时注意对方若已重写 `diskcolor()`,因子乘到新的 `RedShift` 合成处即可。

---

## 八、参考实现索引(NPGS 项目,可直接对照抄公式)

| 内容 | NPGS 位置 |
|------|-----------|
| KS 度规/克氏符(Kerr-Newman,本项取 a=Q=0 特例) | `NPGS/Sources/Program/Application.cpp:83`(ComputeMetric)、`:123`(ComputeChristoffel) |
| RK4 主循环 + 自适应步长 + 子步上限 | `Application.cpp:2213-2236` |
| 四速度归一化解 U^t | `Application.cpp:816-824`(/tp 实现) |
| 静态观者速度公式 v² = 1 + g_tt/U_t² | `Application.cpp:3793` |
| 圆轨道速度 v = √(M/(r-Rs)) 的使用场景 | NPGS 无,标准教科书公式(奇点定理教材 MTW §33) |
| 相机速度多普勒与盘多普勒合成的最终形态 | `NPGS/Sources/Engine/Shaders/BlackHole_common.glsl:264` 附近(能量比法,Phase 3-2 参考) |
