# 开发历史（提交信息整理）

> 整理自私有开发仓的全部提交信息（截至 2026-09-26，c11a03f），按分支/时代分节，此后新增内容随同步提交信息记录。
> 各早期分支间存在共享提交，按分支原始状态分节呈现；少量早期提交信息中的粗话已做淡化处理，语义未变。
> 项目缘起见 README「碎碎念」一节；各阶段的详细技术记录见本目录各计划书。

## 序章：JavaFX 粒子时代（2026-07-04）

_AI 生成的第一版：JavaFX 粒子模拟吸积盘，也是「碎碎念」里说的那个没公开历史的起点。_

- **2026-07-04** `c2889f6` feat: JavaFX black hole simulation with 3D rotation
  - Particle system with gravitational physics
  - Accretion disk with Doppler effect coloring
  - Event horizon and photon ring visualization
  - Gravitational lensing rays
  - Mouse drag for 3D view rotation
  - Interactive controls (mass, time scale, pause)

- **2026-07-04** `3f92297` fix: remove gravitational lensing rays for cleaner look
- **2026-07-04** `357e716` fix: remove photon ring for cleaner visualization
- **2026-07-04** `d77c7e7` fix: remove accretion disk for minimal visualization
- **2026-07-04** `350fc6d` feat: scroll for zoom, left/right keys for mass adjustment
- **2026-07-04** `8ec23c6` fix: proper zoom by moving camera position
- **2026-07-04** `a9f8cf1` feat: change black hole color to bright cyan for visibility
- **2026-07-04** `721dc49` fix: keep black hole fixed at screen center
- **2026-07-04** `2513a81` fix: restore perspective projection while keeping black hole centered
- **2026-07-04** `f541fce` fix: use orthographic projection for proper zoom scaling
- **2026-07-04** `f5e4ae5` feat: add gravitational lensing effect and increase star count to 600
- **2026-07-04** `d1e21b8` feat: apply gravitational lensing to all particles and accretion disk
- **2026-07-04** `3022c82` ignore vscode folder
- **2026-07-04** `34e387f` feat: 3D starfield that moves with camera view
- **2026-07-04** `25fd638` feat: realistic 3D gravitational lensing with Einstein ring effect
- **2026-07-04** `ce07a98` fix: lensing deflection direction now properly follows camera rotation
- **2026-07-04** `6a5a8a9` fix: radial lensing deflection for consistent effect at all angles
- **2026-07-04** `f3d529e` feat: working gravitational lensing with radial deflection
  - Particles pushed outward from black hole center
  - Background stars deflected
  - Einstein ring glow around event horizon
  - Lensing works at all viewing angles

- **2026-07-04** `f336f0d` chore: clean up screenshot files from repo
- **2026-07-04** `0e309d8` chore: remove screenshot files from tracking
- **2026-07-04** `32a0d94` fix: remove broken lensing artifacts, clean visualization
- **2026-07-04** `a3b6979` fix: remove Einstein ring artifact that caused wavy lines
- **2026-07-04** `52a6cd7` feat: re-add clean gravitational lensing with radial deflection
- **2026-07-04** `4e779fc` feat: stronger lensing, edge-on default view, black hole back to black
- **2026-07-04** `3d5d308` default time scale change, reference blackhole.html,
- **2026-07-04** `2a07402` feat: larger schwarzschild radius (60) and tighter disk for better visibility
- **2026-07-04** `c66fcdc` default size
- **2026-07-04** `2bbbebc` feat: full-screen post-processing gravitational lensing via PixelReader/PixelWriter
  - Render scene to buffer first
  - Apply radial UV distortion per-pixel like the HTML version
  - Much stronger and more visible lensing effect

- **2026-07-04** `dfc59aa` fix: revert to fast per-particle lensing, remove laggy post-processing
- **2026-07-04** `5e39490` feat: thinner disk (z * 0.5) and stronger lensing (deflection * 12)
- **2026-07-04** `3f8fcff` fix: reduce free particle Z range from 0.6 to 0.05 for thinner disk
- **2026-07-04** `32b424a` feat: brighter particles (alpha 0.8) and stars farther away (1200-2400)
- **2026-07-04** `734fdf0` feat: brighter particles, wider disk (2x), XYZ axes with tick marks every 100
- **2026-07-04** `6178af4` fix: lensing only affects objects behind the black hole (depth check)
- **2026-07-04** `8a485f6` fix: use camera-space depth for lensing front/back check instead of Euclidean distance
- **2026-07-04** `30b4922` fix: restore projectScale method
- **2026-07-04** `ce2338c` fix: draw event horizon before particles so front particles aren't hidden
- **2026-07-04** `de5a582` depth reverse bug fix
- **2026-07-04** `637df45` record vibe history

## 早期过渡（封存分支，2026-07-04 ~ 07-22）

_从 JavaFX 转向 LWJGL 的摸索期存档，分支名即用户批注：不再触碰。_

- **2026-07-04** `c2889f6` feat: JavaFX black hole simulation with 3D rotation
  - Particle system with gravitational physics
  - Accretion disk with Doppler effect coloring
  - Event horizon and photon ring visualization
  - Gravitational lensing rays
  - Mouse drag for 3D view rotation
  - Interactive controls (mass, time scale, pause)

- **2026-07-04** `3f92297` fix: remove gravitational lensing rays for cleaner look
- **2026-07-04** `357e716` fix: remove photon ring for cleaner visualization
- **2026-07-04** `d77c7e7` fix: remove accretion disk for minimal visualization
- **2026-07-04** `350fc6d` feat: scroll for zoom, left/right keys for mass adjustment
- **2026-07-04** `8ec23c6` fix: proper zoom by moving camera position
- **2026-07-04** `a9f8cf1` feat: change black hole color to bright cyan for visibility
- **2026-07-04** `721dc49` fix: keep black hole fixed at screen center
- **2026-07-04** `2513a81` fix: restore perspective projection while keeping black hole centered
- **2026-07-04** `f541fce` fix: use orthographic projection for proper zoom scaling
- **2026-07-04** `f5e4ae5` feat: add gravitational lensing effect and increase star count to 600
- **2026-07-04** `d1e21b8` feat: apply gravitational lensing to all particles and accretion disk
- **2026-07-04** `3022c82` ignore vscode folder
- **2026-07-04** `34e387f` feat: 3D starfield that moves with camera view
- **2026-07-04** `25fd638` feat: realistic 3D gravitational lensing with Einstein ring effect
- **2026-07-04** `ce07a98` fix: lensing deflection direction now properly follows camera rotation
- **2026-07-04** `6a5a8a9` fix: radial lensing deflection for consistent effect at all angles
- **2026-07-04** `f3d529e` feat: working gravitational lensing with radial deflection
  - Particles pushed outward from black hole center
  - Background stars deflected
  - Einstein ring glow around event horizon
  - Lensing works at all viewing angles

- **2026-07-04** `f336f0d` chore: clean up screenshot files from repo
- **2026-07-04** `0e309d8` chore: remove screenshot files from tracking
- **2026-07-04** `32a0d94` fix: remove broken lensing artifacts, clean visualization
- **2026-07-04** `a3b6979` fix: remove Einstein ring artifact that caused wavy lines
- **2026-07-04** `52a6cd7` feat: re-add clean gravitational lensing with radial deflection
- **2026-07-04** `4e779fc` feat: stronger lensing, edge-on default view, black hole back to black
- **2026-07-04** `3d5d308` default time scale change, reference blackhole.html,
- **2026-07-04** `2a07402` feat: larger schwarzschild radius (60) and tighter disk for better visibility
- **2026-07-04** `c66fcdc` default size
- **2026-07-04** `2bbbebc` feat: full-screen post-processing gravitational lensing via PixelReader/PixelWriter
  - Render scene to buffer first
  - Apply radial UV distortion per-pixel like the HTML version
  - Much stronger and more visible lensing effect

- **2026-07-04** `dfc59aa` fix: revert to fast per-particle lensing, remove laggy post-processing
- **2026-07-04** `5e39490` feat: thinner disk (z * 0.5) and stronger lensing (deflection * 12)
- **2026-07-04** `3f8fcff` fix: reduce free particle Z range from 0.6 to 0.05 for thinner disk
- **2026-07-04** `32b424a` feat: brighter particles (alpha 0.8) and stars farther away (1200-2400)
- **2026-07-04** `734fdf0` feat: brighter particles, wider disk (2x), XYZ axes with tick marks every 100
- **2026-07-04** `6178af4` fix: lensing only affects objects behind the black hole (depth check)
- **2026-07-04** `8a485f6` fix: use camera-space depth for lensing front/back check instead of Euclidean distance
- **2026-07-04** `30b4922` fix: restore projectScale method
- **2026-07-04** `ce2338c` fix: draw event horizon before particles so front particles aren't hidden
- **2026-07-04** `de5a582` depth reverse bug fix
- **2026-07-04** `637df45` record vibe history
- **2026-07-05** `7b96e9c` using lwjgl windows is up, nothing but white screen
- **2026-07-05** `9c3d8fa` mimo cannot handle this project, waste of time
- **2026-07-05** `ec078c6` copy from vulkanbook example appendix-01, hoping mimo can modify it to blackhole, hopefully
- **2026-07-05** `c321283` let mimo try again, save it first
- **2026-07-05** `07efe1e` runnable, woh!
- **2026-07-06** `70d077a` runnable, woh! save first
- **2026-07-06** `55c82f9` comment details
- **2026-07-07** `337fea2` ***?!
- **2026-07-07** `3bae940` ***?! again, problem solved! by local ai? qwen 3.5-9b q4_K_M
- **2026-07-07** `46d2a60` fix: 修复黑洞画面偏移、窗口变形问题
  - 顶点着色器：改用6个顶点的四边形覆盖全屏，解决UV插值不均导致只有部分区域有内容的问题
  - Render.java：添加public resize()方法，支持主动触发渲染管线重建
  - Window.java：添加resizeNeeded标志，在framebuffer size回调中设置，确保窗口大小变化时能及时检测
  - Engine.java：在主循环中检查needsResize()，调用render.resize()和gameLogic.init()更新投影矩阵宽高比

- **2026-07-07** `c50dd06` feat: 调亮吸积盘和星空背景亮度
  - ACCRETION_BRIGHTNESS: 5.0 → 8.0
  - ACCRETION_COLOR_INNER/OFFER: 提高颜色值
  - starfield: 星星亮度和星云亮度提升
  - accretionDiskColor: 最低亮度保底从 0.15 提升到 0.25
  - 新增 GLOBAL_EXPOSURE = 1.2 全局曝光控制

- **2026-07-08** `42b3374` fix: 吸积盘多普勒效应改为随视角实时变化
  - 原代码用 hitPos.x 计算多普勒，导致亮斑固定在黑洞右侧
  - 改为基于切线方向与相机方向的点积，实现正确的相对运动亮度
  - 盘面旋转朝向相机的部分更亮（蓝移），远离的部分更暗（红移）

- **2026-07-08** `093c77e` feat: 星空背景添加特殊彩色大星体（红巨星、蓝超巨星、紫色星）
  - 红色大星体：深红色，尺寸较大，亮度高
  - 蓝色大星体：蓝白色，尺寸中等偏大，最亮
  - 紫色大星体：品红色，尺寸较小，中等亮度
  - 使用网格哈希定位，确保每帧位置固定不闪烁

- **2026-07-08** `fc60047` fix: 吸积盘内边缘改为硬边界，消除灰蒙蒙效果
  - 内边缘（ISCO）：从渐变淡出改为硬边界，符合真实物理
  - 外边缘：保留渐变淡出，模拟物质逐渐稀薄
  - 修复后吸积盘内侧不再发灰，与黑洞阴影的过渡更清晰

- **2026-07-08** `ae1237f` why on earth did qwen3.6-35b-a3b q4_k_m repeat endleseely and stupid? hand fix a bug:   rotate view not started from wasd shifted position
- **2026-07-15** `ccf7910` fix: 修复吸积盘5个关键bug
  1. blackbodyColor() 归一化错误：低温段 r=1.0、高温段 b=1.0 已在[0,1]范围，但结尾统一/255导致颜色变黑。修复为所有分支输出[0,255]再一次性归一化
  2. 移除多余gamma校正：blackbodyColor()返回sRGB颜色，不应再做pow(c,vec3(2.2))压制亮度
  3. 吸积盘检测范围过宽吞噬星空：原r>=1.0就标记hitDisk但体积渲染只在r>=2.5返回非零颜色。修复为严格匹配diskInnerRadius~diskOuterRadius+1.0范围，并改用当前步进位置做体积渲染
  4. 引力透镜系数太弱：系数0.5->2.0(4倍增强)，移除多余*STEP_SIZE，分母改为+0.5增加稳定性
  5. accretionDiskColor()多普勒逻辑丢弃湍流：先算颜色再黑体覆盖。修复为先温度->blackbodyColor()->湍流调制->亮度衰减正确顺序
  
  新增Perlin 3D噪声替代Value Noise，吸积盘改用体积渲染(ray-marching+Beer-Lambert吸收)
  新增Push Constants: temperature(5000K), if_dopplerI, if_dopplerT

- **2026-07-21** `6faca50` 真特么艰难，结果只是换了个颜色的吸积盘，本地ai不太靠谱啊，还得是自己来。
- **2026-07-21** `71ccfdb` 随不明，但觉厉，有点儿闪
- **2026-07-21** `ada2824` d老师指导科学化公式，仍有待改进
- **2026-07-21** `c52f307` d老师指导修改，big step， ohhhh！
- **2026-07-21** `730d588` 清理旧函数
- **2026-07-22** `ff1cb8c` feat：+ - 控制基础温度功能

## LWJGL 摸索期（2026-07-08 ~ 07-22）

_与本地小模型搏斗的现场（qwen/mimo），问题徘徊期，最终引入 vulkanbook 示例框架。_

- **2026-07-22** `35002d6` 空分支初始提交测试
- **2026-07-05** `31607e7` mimo cannot handle this project, waste of time
- **2026-07-05** `08f036b` copy from vulkanbook example appendix-01, hoping mimo can modify it to blackhole, hopefully
- **2026-07-05** `386be1a` let mimo try again, save it first
- **2026-07-05** `f7c7bb8` runnable, woh!
- **2026-07-06** `a0617ec` runnable, woh! save first
- **2026-07-06** `cdec281` comment details
- **2026-07-07** `4eb3a5a` ***?!
- **2026-07-07** `bbe9adb` ***?! again, problem solved! by local ai? qwen 3.5-9b q4_K_M
- **2026-07-07** `a478282` fix: 修复黑洞画面偏移、窗口变形问题
  - 顶点着色器：改用6个顶点的四边形覆盖全屏，解决UV插值不均导致只有部分区域有内容的问题
  - Render.java：添加public resize()方法，支持主动触发渲染管线重建
  - Window.java：添加resizeNeeded标志，在framebuffer size回调中设置，确保窗口大小变化时能及时检测
  - Engine.java：在主循环中检查needsResize()，调用render.resize()和gameLogic.init()更新投影矩阵宽高比

- **2026-07-07** `474c56d` feat: 调亮吸积盘和星空背景亮度
  - ACCRETION_BRIGHTNESS: 5.0 → 8.0
  - ACCRETION_COLOR_INNER/OFFER: 提高颜色值
  - starfield: 星星亮度和星云亮度提升
  - accretionDiskColor: 最低亮度保底从 0.15 提升到 0.25
  - 新增 GLOBAL_EXPOSURE = 1.2 全局曝光控制

- **2026-07-08** `49bbb76` fix: 吸积盘多普勒效应改为随视角实时变化
  - 原代码用 hitPos.x 计算多普勒，导致亮斑固定在黑洞右侧
  - 改为基于切线方向与相机方向的点积，实现正确的相对运动亮度
  - 盘面旋转朝向相机的部分更亮（蓝移），远离的部分更暗（红移）

- **2026-07-08** `4e45baa` feat: 星空背景添加特殊彩色大星体（红巨星、蓝超巨星、紫色星）
  - 红色大星体：深红色，尺寸较大，亮度高
  - 蓝色大星体：蓝白色，尺寸中等偏大，最亮
  - 紫色大星体：品红色，尺寸较小，中等亮度
  - 使用网格哈希定位，确保每帧位置固定不闪烁

- **2026-07-08** `d1924b1` fix: 吸积盘内边缘改为硬边界，消除灰蒙蒙效果
  - 内边缘（ISCO）：从渐变淡出改为硬边界，符合真实物理
  - 外边缘：保留渐变淡出，模拟物质逐渐稀薄
  - 修复后吸积盘内侧不再发灰，与黑洞阴影的过渡更清晰

- **2026-07-08** `fdd894d` why on earth did qwen3.6-35b-a3b q4_k_m repeat endleseely and stupid? hand fix a bug:   rotate view not started from wasd shifted position

## 物理化尝试（2026-07-22）

_尝试加入更多物理的短命分支，思路并入主线。_

- **2026-07-22** `25615a5` 空白分支初始化
- **2026-07-15** `00b7933` fix: 修复吸积盘5个关键bug
  1. blackbodyColor() 归一化错误：低温段 r=1.0、高温段 b=1.0 已在[0,1]范围，但结尾统一/255导致颜色变黑。修复为所有分支输出[0,255]再一次性归一化
  2. 移除多余gamma校正：blackbodyColor()返回sRGB颜色，不应再做pow(c,vec3(2.2))压制亮度
  3. 吸积盘检测范围过宽吞噬星空：原r>=1.0就标记hitDisk但体积渲染只在r>=2.5返回非零颜色。修复为严格匹配diskInnerRadius~diskOuterRadius+1.0范围，并改用当前步进位置做体积渲染
  4. 引力透镜系数太弱：系数0.5->2.0(4倍增强)，移除多余*STEP_SIZE，分母改为+0.5增加稳定性
  5. accretionDiskColor()多普勒逻辑丢弃湍流：先算颜色再黑体覆盖。修复为先温度->blackbodyColor()->湍流调制->亮度衰减正确顺序
  
  新增Perlin 3D噪声替代Value Noise，吸积盘改用体积渲染(ray-marching+Beer-Lambert吸收)
  新增Push Constants: temperature(5000K), if_dopplerI, if_dopplerT

- **2026-07-21** `526f65b` 真特么艰难，结果只是换了个颜色的吸积盘，本地ai不太靠谱啊，还得是自己来。
- **2026-07-21** `94498f5` 随不明，但觉厉，有点儿闪
- **2026-07-21** `8e5f218` d老师指导科学化公式，仍有待改进
- **2026-07-21** `9d0e8b9` d老师指导修改，big step， ohhhh！
- **2026-07-21** `0088632` 清理旧函数
- **2026-07-22** `cfdd4bd` feat：+ - 控制基础温度功能
- **2026-07-22** `0358116` 真是懵懵比比的 1. 透镜重构 2. 物理公式重构

## 主线：vulkanbook 框架时代（2026-07-22 ~ 09-26）

_现开发线：Vulkan 1.3 动态渲染、体积盘、TAA/Bloom、测地相机、克尔移植、标架输运、ColorBlend 后处理……即公开仓的内容。_

- **2026-07-22** `25615a5` 空白分支初始化
- **2026-07-15** `00b7933` fix: 修复吸积盘5个关键bug
  1. blackbodyColor() 归一化错误：低温段 r=1.0、高温段 b=1.0 已在[0,1]范围，但结尾统一/255导致颜色变黑。修复为所有分支输出[0,255]再一次性归一化
  2. 移除多余gamma校正：blackbodyColor()返回sRGB颜色，不应再做pow(c,vec3(2.2))压制亮度
  3. 吸积盘检测范围过宽吞噬星空：原r>=1.0就标记hitDisk但体积渲染只在r>=2.5返回非零颜色。修复为严格匹配diskInnerRadius~diskOuterRadius+1.0范围，并改用当前步进位置做体积渲染
  4. 引力透镜系数太弱：系数0.5->2.0(4倍增强)，移除多余*STEP_SIZE，分母改为+0.5增加稳定性
  5. accretionDiskColor()多普勒逻辑丢弃湍流：先算颜色再黑体覆盖。修复为先温度->blackbodyColor()->湍流调制->亮度衰减正确顺序
  
  新增Perlin 3D噪声替代Value Noise，吸积盘改用体积渲染(ray-marching+Beer-Lambert吸收)
  新增Push Constants: temperature(5000K), if_dopplerI, if_dopplerT

- **2026-07-21** `526f65b` 真特么艰难，结果只是换了个颜色的吸积盘，本地ai不太靠谱啊，还得是自己来。
- **2026-07-21** `94498f5` 随不明，但觉厉，有点儿闪
- **2026-07-21** `8e5f218` d老师指导科学化公式，仍有待改进
- **2026-07-21** `9d0e8b9` d老师指导修改，big step， ohhhh！
- **2026-07-21** `0088632` 清理旧函数
- **2026-07-22** `cfdd4bd` feat：+ - 控制基础温度功能
- **2026-07-22** `0358116` 真是懵懵比比的 1. 透镜重构 2. 物理公式重构
- **2026-08-09** `31fb37c` refactor: rename donotcommitthisdir → foragent
- **2026-08-09** `203a663` refactor: rewrite black hole shader — Euler symplectic stepping + volumetric accretion disk
  Phase 1-6 implementation per glsl_replacement_plan.md:
  
  Shader (blackhole.frag):
  - Replaced RK4 geodesic integration with Euler symplectic stepping
  - Added piecewise symmetric step size function (3 regions)
  - Removed old perlin noise, starfield, accretionDiskScience functions
  - Added classic 8-vertex PerlinNoise + DiskRandom0 fBM fractal noise
  - Implemented GetBH/GetBHRot camera coordinate transforms
  - Implemented uvToDir, DirTouv, PosTouv UV conversion utilities
  - Added RGB(T) blackbody color with low-temp correction (T<400→black)
  - Added omega(r,Rs) Schwarzschild circular orbit angular velocity
  - Implemented complete diskcolor() volumetric raymarching:
    * Dual noise layers with alternating phase modulation
    * SpiralTheta inward rotation + Kepler angular velocity
    * Doppler × RedShift merged with shiftMax=5 clamp
    * Temperature model with exponential fall-off
    * Asymmetry enhancement (9th power) + hook function
  - Added TAA blendWeight computation (ping-pong deferred, no framebuffer infra)
  
  Java (BlackHoleRender.java):
  - Extended PushConstants from 184 to 208 bytes
  - Added iMouse(vec2), iResolution(vec2), iTimeDelta(float), iFrame(int)
  - Added prevFrameTime/currentFrameCount tracking fields

- **2026-08-09** `ce82fbb` fix: resolve 5 GLSL shader compilation errors
  - lerp() → mix() (6 calls in PerlinNoise, compiler lacks lerp)
  - vec4(float) insufficient args → explicit 4-component vec4 (lines 344,365)
  - vec4(a,b) only 2 args → full vec4 with sqrt factor distributed (lines 353,373)
  - GetCamera(vec4(BHAPos,1.0)) nested vec4 invalid → GetCamera(BHAPos)
  - pc.iMouse.z swizzle out of range → pc.iMouse.y (iMouse is vec2)

- **2026-08-09** `560f3ea` TAA/Bloom Java infra + plan update (session paused, compile errors pending)
  Changes:
  - BlackHoleRender.java: Added TAA ping-pong texture infrastructure
    (taaImages, taaColorViews, taaSamplerViews, taaDescSets, taaDescSetLayout)
    - createVkImage(), createTaaTextures(), createTaaDescLayout()
    - allocateTaaDescSets(), updateTaaDescSets()
    - Multi-pass render(): Pass1 blackhole→ping-pong, Pass2 composite bloom to swapchain
    - PushConstants expanded: iMouse(8), iResolution(8), iTimeDelta(4), iFrame(4) = +200 bytes (padded to 208)
    - init()/resize()/cleanup() updated for TAA resources
  
  - DescSet.java: Added setImage(device, imageView, binding) for sampler2D binding
  
  - glsl_replacement_plan.md: Updated with session progress and known compile errors
  
  Known issues requiring fix before next run:
  1. descAllocator field missing in BlackHoleRender (allocateTaaDescSets/cleanup)
  2. VmaAllocationCreateInfo.calloc() signature incorrect
  3. Old vmaCreateImage call parameters stale (lines 204-209)
  4. VkDescriptorSetLayoutBinding not wrapped as Buffer for pBindings()
  5. DescSetLayout.cleanup() expects Device but gets wrong type

- **2026-08-10** `151b3f4` fix: resolve BlackHoleRender compilation errors + GLSL shader fixes
  - Add missing imports (PointerBuffer, ImageView, ShaderModule, Pipeline, VkUtils, SwapChain, CmdBuffer, VkCtx, DescSet, PushConstRange, PipelineBuildInfo)
  - Fix DescSetLayout creation via correct API constructor
  - Use vkCtx.getDescAllocator() instead of missing field in allocateTaaDescSets/cleanup
  - Fix ImageViewData to use fluent builder pattern
  - Add Vma static import and vkCheck static import
  - Add VkDescriptorImageInfo import to DescSet.java
  
  GLSL fixes:
  - blackhole.frag: replace fragCoord with uv*iResolution (Vulkan has no built-in)
  - blackhole.frag: simplify TAA blendWeight mouse check (vec2 has no .z)
  - blackhole.vert: extend PushConstants with iMouse/iResolution/iTimeDelta/iFrame
  
  BlackHoleRender.java: fix all 15+ compilation errors from TAA/Bloom infra work

- **2026-08-10** `dc127b8` fix: use world-space rays from vertex shader instead of hardcoded origin
  Root cause of black screen (GPU 100%): fragment shader main() was ignoring
  inRayOrigin/inRayDir passed by vertex shader and hardcoding RayPos=(0,0,0)
  plus recomputing camera-relative RayDir. Rays started from world origin
  instead of camera position → missed everything → Dis>100*Rs break → black.
  
  Fix:
  - RayPos = inRayOrigin (camera world pos from vertex shader)
  - RayDir = normalize(inRayDir + jitter) (world-space direction)
  - BHRPos = pc.blackHolePos - pc.cameraPos (world-space offset)

- **2026-08-10** `91a442f` fix: resolve vec3+vec2 GLSL type error in main()
  Root cause: RayDir initialization used 'inRayDir (vec3) + 0.5*vec2(...)' which is
  a GLSL type mismatch — you cannot add a vec3 and vec2 directly. shaderc rejects this
  at runtime with a compilation error, causing the black screen (shader never compiles).
  
  Fix: jitter UV → convert to 3D direction via uvToDir() → lerp between original ray
  and perturbed direction (both vec3):
      vec3 RayDirPerturbed = uvToDir(uv + jitter / resolution);
      vec3 RayDir = normalize(mix(inRayDir, RayDirPerturbed, 0.05));
  
  Also fixed variable declaration order: BHRPos/RayPos declared before PosToBH/Dis.

- **2026-08-10** `2aba013` fix: correct BHRPos to use world position instead of relative position
  The black hole position was incorrectly calculated as a relative position
  (pc.blackHolePos - pc.cameraPos), but diskcolor() expects the absolute
  world space position. This caused PosToBH calculations to be completely wrong,
  resulting in no pixels hitting the accretion disk detection region.

- **2026-08-10** `3ec514b` fix: correct BHRPos to use world position instead of relative position
  The black hole position was incorrectly calculated as a relative position
  (pc.blackHolePos - pc.cameraPos), but diskcolor() expects the absolute
  world space position. This caused PosToBH calculations to be completely wrong,
  resulting in no pixels hitting the accretion disk detection region.

- **2026-08-10** `b9c6c2b` fix: correct accretion disk coordinate system
  1. Fix BHRPos to use world position instead of relative position
  2. Change DiskDir from vec3(0,0,1) to vec3(0,1,0) so the disk is in XZ plane (matching 0358116 behavior)
  3. Fix GetBH() to handle DiskDir along Y axis without zero vector cross product

- **2026-08-10** `02c7a5a` fix: use only inRayDir (world space) instead of mixing with camera-space uvToDir
  The original code mixed inRayDir (world space direction from vertex shader)
  with RayDirPerturbed (camera space direction from uvToDir), which caused
  incorrect ray directions and prevented rays from entering the accretion disk.

- **2026-08-10** `3a9c5c5` fix: make window display content by fixing multiple coordinate system bugs
  1. Fix BHRPos to use world position instead of relative position (pc.blackHolePos)
  2. Change DiskDir from vec3(0,0,1) to vec3(0,1,0) so disk is in XZ plane
  3. Fix GetBH() to handle DiskDir along Y axis without zero vector cross product
  4. Use only inRayDir (world space) instead of mixing with camera-space uvToDir
  
  These fixes ensure rays correctly enter the accretion disk detection region.

- **2026-08-10** `85adfde` fix: restore complete shader with correct coordinate system fixes
  1. BHRPos uses world position (pc.blackHolePos) instead of relative
  2. DiskDir = vec3(0,1,0) for XZ plane disk
  3. GetBH() handles Y-axis DiskDir correctly
  4. RayDir uses only inRayDir (world space), no camera-space mixing

- **2026-08-10** `de90671`  Diagnosis & Fix Summary
   ### Root Cause
  
   The Euler stepping step size formula in blackhole.frag produces enormous steps when rays are far from the black hole, causing them to overshoot the entire accretion disk without any volumetric color accumulation.
  
   Problematic code (lines 456-462):
  
   Working version (0358116) uses dt = STEP_SIZE * (0.5 + 0.5*min(1,r/10)) with STEP_SIZE=0.04, giving dt ∈ [0.02, 0.04] — small enough for ~250 proper steps through the scene.
  
   Current HEAD: With camera at r≈18 and ROut=18:
   - Far zone (Dis≥36): dl = Dis → step size of 36 units!
   - Transition zone (18≤Dis<36): dl ≈ 4–27 units
  
   A single step jumps from the camera position right past the accretion disk (r ∈ [3.5, 18]), so diskcolor() never accumulates color → black screen.
  
   ### Fix Applied
  
   Capped dl in far and transition zones relative to scene scale (ROut):
  
   Effect:
  
   ┌───────────────────┬────────────────────────┬─────────────────────────┬───────────────────────────────────────────────────┐
   │ Zone              │ Before (Dis=36)        │ After (Dis=36)          │ Effect                                            │
   ├───────────────────┼────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────┤
   │ Far               │ dl = 36 → jump to r≈54 │ dl = 5.4 → step to r≈23 │ Rays now traverse disk region over multiple steps │
   ├───────────────────┼────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────┤
   │ Transition (r=27) │ dl ≈ 6.3               │ dl ≈ 4.05               │ More sampling passes through disk                 │
   ├───────────────────┼────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────┤
   │ Near (r<18)       │ dl ≤ Rs = 1            │ Unchanged               │ Fine-grained sampling preserved                   │
   └───────────────────┴────────────────────────┴─────────────────────────┴───────────────────────────────────────────────────┘
  
   Verification: mvn compile — BUILD SUCCESS, no errors.

- **2026-08-10** `8d382c7`  Diagnosis & Fix Summary
   ### Root Cause
  
   The Euler stepping step size formula in blackhole.frag produces enormous steps when rays are far from the black hole, causing them to overshoot the entire accretion disk without any volumetric color accumulation.
  
   Problematic code (lines 456-462):
  
   ```glsl
  if (Dis >= 2.0 * ROut) {
      dl *= Dis;              // ← At r=36, dl = 36! One step jumps from r=18 to r=54
  } else if (Dis >= 1.0 * ROut) {
      dl *= ((Rs) * (2*ROut - Dis) + Dis*(Dis - ROut)) / ROut;  // ← At r=27, dl ≈ 6.3
   ```
  
   Working version (0358116) uses dt = STEP_SIZE * (0.5 + 0.5*min(1,r/10)) with STEP_SIZE=0.04, giving dt ∈ [0.02, 0.04] — small enough for ~250 proper steps through the scene.
  
   Current HEAD: With camera at r≈18 and ROut=18:
   - Far zone (Dis≥36): dl = Dis → step size of 36 units!
   - Transition zone (18≤Dis<36): dl ≈ 4–27 units
  
   A single step jumps from the camera position right past the accretion disk (r ∈ [3.5, 18]), so diskcolor() never accumulates color → black screen.
  
   ### Fix Applied
  
   Capped dl in far and transition zones relative to scene scale (ROut):
  
   ```glsl
  if (Dis >= 2.0 * ROut) {
      dl = min(Dis * 0.15, ROut);      // Far zone: max step = ROut instead of Dis
  } else if (Dis >= 1.0 * ROut) {
      float rawDl = ((Rs)*(2*ROut-Dis)+Dis*(Dis-ROut))/ROut;
      dl = min(rawDl, ROut);           // Transition: cap at ROut
  } else {
      dl *= min(Rs, Dis);              // Near zone: unchanged (already bounded by Rs)
   ```
  
   Effect:
  
   ┌───────────────────┬────────────────────────┬─────────────────────────┬───────────────────────────────────────────────────┐
   │ Zone              │ Before (Dis=36)        │ After (Dis=36)          │ Effect                                            │
   ├───────────────────┼────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────┤
   │ Far               │ dl = 36 → jump to r≈54 │ dl = 5.4 → step to r≈23 │ Rays now traverse disk region over multiple steps │
   ├───────────────────┼────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────┤
   │ Transition (r=27) │ dl ≈ 6.3               │ dl ≈ 4.05               │ More sampling passes through disk                 │
   ├───────────────────┼────────────────────────┼─────────────────────────┼───────────────────────────────────────────────────┤
   │ Near (r<18)       │ dl ≤ Rs = 1            │ Unchanged               │ Fine-grained sampling preserved                   │
   └───────────────────┴────────────────────────┴─────────────────────────┴───────────────────────────────────────────────────┘
  
   Verification: mvn compile — BUILD SUCCESS, no errors.

- **2026-08-10** `eb50a70` 终于不黑屏了，但是还是有些问题。画面分层严重，品红一直出现。远距离，近屏幕侧吸积盘消失。随着距离拉远，品红面积一会儿大一会儿小，总体呈增大趋势。pushconstants数值或许也需要调整。
- **2026-08-10** `55a2da8` deepseek优化了一下，效果怎么说呢，一言难尽
- **2026-08-11** `dcf9cd1` deepseek又优化了一下，太美妙了，不容易啊。还有小瑕疵，先不管了。
- **2026-08-12** `75b86f1` 无关小调整
- **2026-08-12** `1054c56` 无关小调整
- **2026-08-13** `f09ed17` fix: 现在打包的jar也可以运行了，会在jar文件所在路径生成shader-cache目录，内存 .spv文件。目前还有缺陷，如代码复用，代码质量，jar重新部署需要手动删除原有shader-cache等
- **2026-08-29** `101ba92` docs: 计划文档对齐代码实际状态（2026-08-29 核对）
  - Phase 1-3 标注为变体落地，记录与文章版的具体差异
  - Phase 0/4/5/6/7 更正为未实施（TAA/Bloom infra 曾在 560f3ea 提交、eb50a70 回滚）
  - 品红伪影/画面分层/吸积盘消失标记为已解决
  - 新增「九、实际状态核对」作为权威状态章节

- **2026-08-29** `2938f58` refactor: frag 显式使用 pc.schwarzschildRadius，公式不再隐含 Rs=1
  - schwarzschildRadius 语义改为世界坐标长度，直接参与所有公式：
    偏折 1.5·Rs/r²、红移 √(1-Rs/r)、盘厚度 0.5·Rs·Shape、omega=√(Rs/r³) 等
  - 盘半径按"Rs 的倍数"传入，shader 内乘 Rs 换算（与注释约定一致）
  - 盘噪声坐标以 Rs 归一，盘纹理随 Rs 缩放形态不变
  - Java 传值不变（1.0/3.0/18.0），Rs=1 时渲染结果与重构前一致
  - 同步 vert/Java 注释的量纲约定，重新生成 .spv

- **2026-08-29** `7de2c70` feat: 接入 NPGS Universe0Skybox 作为黑洞背景星空 cubemap
  - pom 新增 lwjgl-stb；Image 支持 CUBE_COMPATIBLE 标志；DescSet 支持 setImage
  - 新增 CubeTexture：classpath 加载 6 面 jpg → 暂存上传 → CUBE 视图 + 采样器
  - BlackHoleRender：声明 set 0 描述符布局、分配/绑定描述符集、init/cleanup 接入
  - frag：samplerCube 替换程序化 starfield() 主路径，移植 NPGS 的 OStrength 亮度保持；
    starfield() 保留为回退参考
  - 加载参数对齐 NPGS：R8G8B8A8_UNORM、不翻转、暂无 mipmap
  - 计划文档新增 9.6 天空盒接入记录

- **2026-08-29** `199c47b` feat: 接入 TAA 时域抗锯齿（Phase 6 + 0/7 + 4 变体落地）
  - 帧插槽 ping-pong：每插槽 2 张历史图（共 4 张 RGBA32F），插槽隔离免跨帧信号量，
    Render.java 零改动
  - MRT 双附件输出：附件 0=swapchain（CLEAR），附件 1=历史写入（DONT_CARE），
    每帧收尾 sync2 barrier 翻转历史布局
  - PushConstants 204B 不变，place_holder1..3 复用为 iTimeDelta/iFrame/iCameraMoved
  - frag 在 HDR 域（色调映射前）混合：blendWeight = 1-0.5^(dt/τ)，τ=clamp(0.3/timeRate)；
    iFrame<2 或相机/投影矩阵变化时重置累积
  - resize 重建历史缓冲并重置累积；计划文档新增 9.7 记录

- **2026-08-29** `e8fb64d` fix: TAA 重置标记改为按插槽跟踪，消除停稳震荡/resize 花屏
  根因：taaReset 为全局单次消费，但历史按插槽隔离——resize 或停止移动后，
  另一插槽首帧读到未写过的历史图（显存原始值=品红/绿斑点）或运动期错位
  历史（双影游动），且以 96% 权重回写缓慢衰减。
  
  - 新增 taaPrevMoved[slot]：上次运行相机在变化/重置 → 本帧继续跳过历史读取，
    每个插槽首跑各自强制重置一次
  - resize 时重置 taaPrevMoved；shader 读历史加防御性钳制
  - resize 拖动期间的闪烁为交换链重建风暴（固有行为），记录于计划文档

- **2026-08-29** `7966528` feat: 新增自由视角相机模式（F 键与轨道模式互切）
  - Camera 新增 CameraMode 枚举（ORBIT/FREE_FLY）与 toggleMode()
  - 自由视角：yaw/pitch 欧拉角（无翻滚，±89° 钳制），V=Rx(-pitch)·Ry(yaw)·T(-pos)，
    WASD 沿视线飞行，PageUp/Down 沿相机上方向移动
  - 切换无缝：ORBIT→FREE 由指向原点的视线换算欧拉角；FREE→ORBIT 由当前位置
    反推球坐标（距离钳制 [3,50]），切换瞬间位置与视角均无跳变
  - 拖拽手感与轨道一致（抓住世界拖）；模式切换触发 TAA 按插槽自动重置
  - 计划文档新增 9.8 记录

- **2026-08-29** `1244f7e` feat: 自由视角新增升降与翻滚（Shift/Ctrl、Q/E，仅自由视角）
  - 左Shift/左Ctrl：沿相机上方向升/降（与方向键同轴向）
  - Q/E：绕视线轴翻滚，视图矩阵增加 Rz(roll)，翻滚角包装 ±180° 防精度漂移
  - ORBIT→FREE 切换时翻滚角归零，保证切换瞬间视角无跳变
  - 计划文档 9.8 补充键位记录

- **2026-08-29** `70ade89` fix: 自由视角姿态改为四元数——修复翻滚轴错误并解除俯仰钳制
  - 初版 Rx·Ry·Rz 欧拉合成中 Rz 实际绕世界 Z 轴而非视线轴（用户实测发现）
  - 欧拉角天生需要 ±89° 俯仰钳制防万向锁，无法越过头顶
  - 改为 Quaternionf 姿态：rotateLocalY/X/Z 增量旋转 + normalize，
    roll 恒绕视线轴、无俯仰钳制，V = conjugate(orientation)·T(-position)
  - ORBIT→FREE 由视线方向构造四元数（翻滚归零），切换仍无跳变
  - 轨道模式与移动方法不变，Camera 单类双模式保留

- **2026-08-29** `b55fbb5` fix: 自由视角鼠标/翻滚方向改为 FPS 标准手感（拖向=看向）
  - 用户实测三方向全反：拖左画面逆时针、拖上黑洞上跑、Q 画面顺时针，
    均为初版'抓住世界拖'手感的固有方向，与用户期望的 FPS 标准相反
  - rotateFree 符号翻转：拖右看右、拖上抬头
  - Q/E 交换：Q=画面逆时针（向左滚）、E=顺时针
  - 轨道模式保持原 grab 手感不变（黑洞恒居中无方向感），计划文档记录差异

- **2026-08-29** `baff958` fix: 自由视角旋转改用 rotate*（局部轴），修复 rotateLocal* 绕世界轴问题
  - 用户复测：俯视黑洞时水平拖动仍原地旋转、Q/E 仍平移——实证 JOML
    Quaternionf.rotateLocal* 实际绕父级/世界轴（俯视时世界竖直轴=视线轴，
    水平拖动即原地旋转；世界水平轴使 Q/E 表现为平移）
  - rotateFree/addRoll 改用 rotateY/rotateX/rotateZ（局部轴右乘）：
    鼠标水平恒绕相机 up 轴转头，Q/E 恒绕视线轴翻滚
  - toggleMode 构造改 rotationY().mul(rotationX()) 显式乘法顺序

- **2026-08-29** `8bbd792` docs: 同步自由视角翻滚注释与计划文档至用户手改后的语义（正=向左滚）
- **2026-08-29** `8c87eb3` feat: 右键单击切换视角捕获（隐藏光标 + 鼠标移动控制视角）
  - MouseInput 新增右键单击边缘触发（isRightButtonSinglePress，resetInput 逐帧清除）
  - Window 新增 grabCursor/releaseCursor（GLFW_CURSOR_DISABLED/NORMAL）
  - Main 维护 lookCaptured 状态：捕获中鼠标移动控制视角（自由=自由看向、轨道=轨道旋转），
    不再需要按住右键；再次右键恢复光标并停止视角控制

- **2026-08-29** `9d307e1` docs: 新增测地线相机模式 + 相机速度多普勒实施计划
  评审修正三处硬伤:
  - 圆轨道 sanity check:ISCO 在 3Rs(非 1.5Rs),1.5Rs 为光子球 v=c
  - g_cam 公式应为 γ·(1-β·n̂) 而非其倒数,否则红蓝移反向
  - push constants vec3 需 16 字节对齐,总量改为 224B(vec3@208)
  另补充:测地模式下 F 键无效

- **2026-08-29** `e0bddab` feat: 测地线相机模式 + 相机速度多普勒（Phase 1+2）
  Phase 1(纯 Java):
  - 新增 GeodesicIntegrator:Kerr-Schild 史瓦西度规(解析度规+逆)、
    数值克氏符、RK4 自适应步长(dtau_max=0.05·max(r,1)^1.5,子步上限 500)、
    每步重解 U^t 投影 U·U=-1 约束、奇点保护(r<0.1 冻结)
  - Camera 第三模式 GEODESIC:位置由积分器驱动、姿态仍自由;
    新增 setPosition/getViewDirection/setModeRaw;测地下 F 屏蔽
  - Main:G 进出模式(进入=当前位置+视线方向+v0)、W/S=推力、↑/↓=时间流速、
    []=推力大小、1/2=初速度、R=圆轨道(v=√(M/(r-Rs)),两极退化回退)
  
  Phase 2(着色器):
  - push constants 204→224B:vec3 iCameraVel@208(16 字节对齐)+iCameraGamma@220
  - frag 新增 CameraDoppler: g=γ(1-β·n̂),n̂=光子传播方向(-RayDir)
    盘 RedShift 乘 g_cam(温度/亮度链路自动联动),亮度额外 clamp(g³,0.1,10)
    背景 Shift=引力项×g_cam 统一 clamp 8.0,亮度乘 g³
  - 非测地模式 β=0/γ=1 → g_cam≡1,画面与改动前逐像素一致(回归安全)
  
  验证:mvn BUILD SUCCESS,frag/vert shaderc 编译通过,.spv 已重新生成

- **2026-08-29** `f3ad399` fix: 测地线积分器三处问题（无头测试驱动定位验证）
  1. 数组越界:Γ 公式第三项 ∂_ρ g_νσ 的 ρ 遍历含时间维,dG 原开 [3][4][4]
     越界(AIOOBE Index 3 length 3);静态度规 ∂_t g=0,改 [4][4][4] 时间切片保持零
  2. 圆轨道初条件:静态观者局部速度不能直接当坐标空间速度(差 13% → 椭圆轨道,
     5 圈内 r 漂移达 1.66);改为角速度比条件 φ̇=Ω·ṫ 的自洽不动点迭代 u=Ω·U^t·(ĥ×r̂)
  3. KS 切片符号实证:l_i 取负为白洞出射片,下落粒子被弹射到 r~10^15;
     保留 +x/r 黑洞片(下落平滑穿视界)
  
  无头验证(GeoTest):r=6Rs 圆轨道 5 圈 maxDrift=0.00000,E=0.96225 恒定,
  β=0.3162c 与解析值一致;0.5c 瞄心俯冲平滑穿视界冻结于保护半径

- **2026-08-30** `996feb5` fix/tune: 测地模式测试反馈三连修
  - MAX_STEPS 1024→4096:大外径时 ROut 内小步长耗尽步数导致星空分支永不执行
    (bWaitCalBack 永不触发),提步数上限修复
  - ShiftMax 1.5→2.5:原钳制把趋近侧饱和在 1.5,相机多普勒乘上去被完全钳死;
    放宽后前后不对称可见(NPGS 用 5)
  - 测地模式每 1.5s 控制台打印 r/v/γ,G 退出时打印落点 r——被甩远可实时观测

- **2026-08-30** `5b85a68` feat: 视界坠落演出（测地计划 Phase 3-1）
  - r < 1.02Rs 触发：积分器冻结，iFade 0.6s 淡出到全黑（复用 place_holder5@204）
  - 黑屏时刻沿当前方向弹回 5Rs 并初始化为稳定圆轨道，再 0.6s 淡入
  - 淡出仅作用于显示输出（outColor），不污染 TAA 历史
  - 控制台提示'已越过事件视界'；G 中途退出自动复位演出状态

- **2026-08-30** `08f9f03` feat: 引力偏折改为辛格式（先更新方向再移动位置）
  文章 2025.12.19 修正：原'先移动后偏折'使离散步进非辛格式，黑洞略小/轨道有系统性偏差。
  对调 RayPos/RayDir 更新顺序（DeltaPhi 仍用当前位置计算），供 A/B 对比透镜精度与光子环形态，
  若观感变差可单独 revert 此提交。

- **2026-08-30** `3725246` docs: 测地计划状态更新——视界坠落演出完成、辛格式待 A/B
- **2026-08-30** `609e92b` fix: 第一步抖动种子自适应——TAA 关闭时固定种子，消除测地模式阴影边缘逐帧抖动
  根因：测地模式相机恒动 → iCameraMoved=1 → TAA 永久关闭；而第一步随机抖动种子
  fract(pc.time) 每帧变化，光子环/阴影边缘每帧重抽随机步长 → 视觉上'黑洞发抖'。
  静止时 TAA 开启会时域平均掉该噪声，故仅运动中暴露。
  修法：TAA 激活(iCameraMoved==0)用时间种子（供时域平均），关闭时用固定种子
  （噪声成稳定颗粒，无逐帧闪烁）。

- **2026-08-30** `6b4430c` feat: TAA 三态混合——平滑运动中保留部分时域滤波，消除测地模式盘面闪烁
  根因再定位：固定抖动种子无效，说明主因是测地模式相机恒动 → TAA 永久关闭 →
  盘体高频体积噪声逐帧裸渲（黑洞周围内盘噪声密度最高，故'黑洞很抖'）。
  
  - iCameraMoved 改三态：0=静止全量累积 / 2=平滑运动 0.35 部分混合（压噪+轻运动模糊）
    / 1=硬重置（init/resize 后插槽首跑、帧间运动超阈值：视线 >2°/帧或平移 >0.4Rs/帧）
  - 测地圆轨道约 0.3°/帧 → 恒走 2 态，噪点被时域平滑，残影在该角速度下不可见
  - 抖动种子改为仅硬重置帧固定（2 态下时间种子可被部分混合平均）
  - 传送/快速拖拽超阈值自动硬重置，无残影

- **2026-08-30** `dc146ec` fix: 恢复吸积盘外半径 180Rs（5b85a68 误将用户手动调参回退为 18）
  用户二分定位：抖动感知从 5b85a68 开始——该提交在 git add -A 时把工作区里
  用户手动设置的 diskOuterRadius=180 意外回退为 18。紧凑亮盘集中在黑洞周围，
  TAA 0.35 部分混合压不住内盘高密度噪声闪烁；大盘(180)将噪声摊薄故观感干净。
  恢复 180 并加注释说明；后续提交不再使用 add -A 全量扫入，避免覆盖手动调参。

- **2026-08-30** `0c4e237` tune: 近距抖动缓解——靠近黑洞自适应加强 TAA 平滑 + 帧耗时诊断读数
  近距离抖动机理（远距不抖的对照实验结论）：阴影/光子环附近角放大率 5-10 倍，
  相机每帧 0.03Rs 的平滑移动在透镜图样上是数像素位移，叠加盘噪声近距离'沸腾'；
  积分解本身无缺陷（圆轨道零漂移、能量守恒）。
  
  - 平滑运动混合比自适应：r≥30Rs 时 0.35，r≤6Rs 渐增至 0.20（加强近距时域平滑）
  - 控制台读数增加平均帧耗时/FPS：区分 GPU 掉帧（帧耗时大→优化 raymarch）
    与采样噪声（帧耗时正常→需重投影 TAA，下一专项）

- **2026-08-30** `4d6fa42` fix: 帧耗时读数单位错误（毫秒误按纳秒换算，报出 0.0ms/1800万FPS）
- **2026-08-30** `9831b81` tune: 吸积盘外半径恢复 18Rs（大外径 raymarch 步数暴涨导致卡顿+大面积拖影）
- **2026-08-30** `86abf14` fix: 部分混合仅限测地模式——修复 6b4430c 起自由/轨道模式运动拖影晃动
  6b4430c 把一切平滑运动都改为 0.35 部分混合，但 TAA 历史滞后 2 帧，
  高倍放大区（近黑洞）亚度级的鼠标晃动也会造成数像素的重影错位，
  表现为'晃动鼠标就抖'。回归为：轨道/自由视角运动 = 硬重置（噪声但锐利，
  即 6b4430c 之前用户已接受的行为）；仅测地模式保留部分混合（运动归物理、
  平滑可预测，压噪收益 > 残影代价）。

- **2026-08-30** `a3ffbf3` fix: 测地积分器从 30Hz update() 移至每渲染帧 input()——消除近距相机位置台阶跳变
  根因（用户实测缩小到'测地模式首次可用起近距离必抖'）：相机位置仅在 update()
  （ups=30）回写，渲染帧率 80-145 → 每 2-5 帧共享同一位置后一次跳 2-5 帧距离。
  远距离台阶为亚像素不可见；近距离角放大率 ~10 倍，每次台阶 = 十几像素猛蹿 = 抖。
  与 TAA 状态/盘外径/抖动种子均无关（此前各轮修复未中的原因）。
  
  - step/fall/触发/位置回写全部移入 input()（按渲染帧、真实帧间隔推进）
  - update() 仅保留周期读数
  - 附带修复：视界触发从 30Hz 提高到逐帧，r<1.02 触发更及时

- **2026-08-30** `f33d27b` fix: 光标捕获左/上边界（负虚拟坐标被 -1 哨兵误判）+ 收紧部分混合运动阈值
  - MouseInput：'坐标非负'哨兵在光标捕获模式下误判——虚拟坐标向左/上越过窗口
    原点变负即被当作'无上一帧数据'清零增量，表现为左/上有边界、右/下没有；
    改用显式 hasPreviousPos 标志
  - 运动阈值收紧：视线 2°/帧→0.9°/帧、平移 0.4→0.15Rs/帧——黑洞附近角放大率
    ~10 倍，中等摆动的画面位移在部分混合下拖影明显，超阈值即硬重置保锐利

- **2026-08-30** `ce998a3` feat: Bloom 泛光（迁移计划 Phase 5，圆盘模糊简化版）
  渲染结构重构：
  - 主 pass 不再直写交换链：单附件输出 HDR（TAA 混合后、预色调映射）到 TAA 历史
  - 新增 Pass 2（Bloom 合成）：读本帧 HDR 历史 → 亮部提取（阈值 1.0）+
    黄金角 24 tap 半径 24px 圆盘模糊（免 mip/blit——RGBA32F 不支持 blit）→
    色调映射公式原样迁移 + iFade 淡出 → 写交换链
  - 新增 bloomComposite.frag + bloom 管线（复用 blackhole.vert）+
    bloomSceneLayout/每插槽描述符集/线性采样器
  - Render 编译第三个着色器；.spv 已重新生成
  
  调参位置（bloomComposite.frag）：TAPS=24、RADIUS=24、THRESHOLD=1.0、强度 0.6

- **2026-08-30** `6aef313` docs: 迁移计划 Phase 5 状态更新（Bloom 简化版落地）
- **2026-08-30** `2979bac` feat: 方向重投影 TAA——运动中拖影的根治方案（旋转分量）
  - TAA 描述符布局 set1 增加 binding 1 UBO（64B prevProj·prevView，每插槽一份，
    持久映射，帧间经 getToAddress 更新）
  - frag 运动态改为方向重投影：当前像素视线方向经上一帧 viewProj 映回上一帧
    屏幕位置做双线性采样——旋转分量被精确补偿，鼠标转头不再拖影
  - 重投影后混合权重回到 τ 全量水平（与静止态一致），降噪能力大幅恢复
  - 平移视差仍会残留（需逐像素深度输出升级为全重投影，留作后续）
  - 运动阈值简化为仅平移检测（>0.15Rs/帧 硬重置；旋转不再触发重置）
  - TAA 采样器 NEAREST→LINEAR（texelFetch 不受影响，重投影需双线性）
  - 运动检测矩阵改为按插槽存储（与各自历史帧配对）

- **2026-08-30** `875ac03` fix: 撤销方向重投影——运动回归硬重置（锐利颗粒）
  用户实测：方向重投影在本场景不成立——相机全程贴着有限几何（盘）飞行，
  方向隐含的'内容在无穷远'假设不成立，视差使历史采样错位：
  慢速/静止时画面'无数窗口变形游动'，噪声可接受但变形不可接受。
  TAA 回归两态：静止全量累积 / 运动硬重置。
  UBO 基建保留（着色器未消费），为将来基于逐像素世界坐标的全重投影预留。

- **2026-08-30** `a30344a` feat: jar 缓存按源码哈希失效 + omega 对齐史瓦西值
  - ShaderCompiler jar 模式：缓存文件名携带源码 SHA-256 前 8 位，源码一变缓存键
    即变、旧缓存自动清理——修复'jar 重新部署需手动删除 shader-cache'的老问题
  - omega 对齐史瓦西圆轨道坐标角速度 √(M/r³)=√(Rs/2r³)（原实现快 √2），
    内盘动画随之慢约 29%

- **2026-08-30** `2b25ba8` docs: 9.5 剩余工作清单刷新（Bloom/jar 缓存/omega 已落地）
- **2026-08-30** `b0378ad` feat: 全重投影 TAA——世界坐标补偿平移视差（运动中完整降噪）
  方向重投影的升级：当前像素的 raymarch 终点世界坐标（RayPos）经上一帧
  proj·view 全变换（含平移，w=1）映回上一帧屏幕位置采样历史——
  平移视差与旋转一并补偿，测地运动中混合权重回到 τ 全量（与静止一致）。
  
  - 包围球未命中分支补 RayPos 远点（直达背景的像素也要有合法重投影坐标）
  - 硬重置条件收敛为：init/resize 插槽首跑、帧间平移 >0.15Rs（瞬移）
  - 旋转不再触发重置（重投影精确补偿）；重投影坐标出界/在相机后方则放弃该样本
  - 已知边界：无逐像素深度校验，遮挡边界（洞缘二次像出没）可能有少量残影，v1 接受
  - 附：dependency-reduced-pom.xml 为 shade 插件自动再生成（与 pom.xml 现状一致）

- **2026-08-30** `d5b6065` fix: 撤销世界坐标全重投影——运动回归硬重置（锐利稳定颗粒）
  用户实测与方向重投影同症：高速清晰、慢速/停止时'无数窗口变形游动'。
  根因为方案原理性缺陷：透镜化 raymarch 的射线终点（RayPos）对初始条件
  混沌敏感，不存在稳定的'像素内容位置'可作重投影键——相邻像素终点差
  异数 Rs 且随视角剧变，逐帧混入不相干历史内容。
  
  - frag 移除重投影分支与 PrevCamera UBO 声明，回归两态：静止 τ 全量/运动硬重置
  - Java 侧 UBO/每插槽 prev 矩阵基建保留（当前未消费），为将来
    '亮度加权散射质心'类方案预留；近期无重开计划

- **2026-08-30** `04bcdc8` chore: 卫生清理——死常量/拼写/过时注释/积分器回归测试常驻
  - Render 移除死常量 PUSH_CONSTANTS_SIZE=176（无人引用且值早已失效）
  - Main TEMPERTURE→TEMPERATURE 拼写修正
  - BlackHoleRender 类注释对齐现状（Bloom 两 pass、push 224B）
  - GeoTest 无头回归测试移入 src/test/java 常驻（圆轨道零漂移+视界穿越两条用例，
    运行方式见文件头注释）

- **2026-08-30** `4242c3c` feat+chore: 天空盒 mipmap 链 + 卫生清理
  - CubeTexture：上传后 vkCmdBlitImage 逐级生成完整 mip 链（1024²→11 级，
    UNORM 可 blit），视图覆盖全链，采样器 mipmap LINEAR + 各向异性——
    转动视角时亮星不再闪烁
  - Render 移除死常量 PUSH_CONSTANTS_SIZE=176；Main 拼写 TEMPERTURE→TEMPERATURE；
    BlackHoleRender 类注释对齐 Bloom 两 pass 现状与 224B 布局
  - GeoTest 无头回归测试已常驻 src/test（上一提交）

- **2026-08-30** `c7c07cf` feat: 温度模型对齐文章（实验分支）
  T = Tpeak·(RIn/r)^0.75·max(1-√(RIn/r),0)^0.25——内盘边缘温度趋零，
  物质 plunge 区不再辐射，内缘变暗。观感变化大，需重调亮度相关参数；
  不合意可直接弃分支。

- **2026-08-30** `82e4c06` chore: gitignore 追加 shader-cache/（运行时着色器缓存目录）
- **2026-08-30** `99dd754` feat: 参数提取至 eng.properties
  - EngCfg 新增 blackhole.*（Rs/盘内外半径/温度及步长上限/timeRate/TAA 平移阈值）
    与 input.*（鼠标灵敏度/翻滚/移动/缩放速度）配置，默认值与原硬编码一致
  - BlackHoleRender.setPushConstants 全部改读配置；BaseTemperature 初值取自配置
    （运行时小键盘 +/- 调节行为不变，边界/步长同步走配置）；rotationSpeed 为
    着色器未消费的死参数，保持原值并加注释

- **2026-08-30** `0c6feae` docs: imgui参考vulkanbook移植计划
- **2026-08-30** `9117944` chore: 测试常驻，spv文件入库
- **2026-08-31** `e3c2146` feat: Phase 1 已完成并验证通过：编译无错，程序实际运行 30 秒无验证层报错、无崩溃，ImGui demo 窗口的完整渲染链路（上下文 → 字体纹理 → 专用管线 → 叠加绘制）已就绪。
        改动一览：
  
        计划文档：
        foragent/gui_overlay_plan.md
         —— 含参考实现摘要、现状盘点（含发现的 Bloom 管线格式声明与交换链实际格式不符的现存隐患）、数据源映射表、四个 Phase 的实施细节与验收标准；状态区已更新本次落地差异。
        新建
        GuiRender.java
        ：核心移植。imgui-java 只当 UI 库，Vulkan 渲染自写——字体图集经 staging 一次性上传为 SRGB 纹理、描述符集句柄作 texID 塞回 ImGui；Alpha 混合管线 + 8 字节 push constant（NDC scale）；动态渲染 loadOp=LOAD 叠加写交换链，附件 view 随 imageIndex 每帧设置（与 appendix-01 固定附件的关键差异）；每帧插槽顶点/索引缓冲按需增长。cleanup 末尾销毁 ImGui 上下文。
        新建
        GuiUtils.java
        ：GLFW 键盘/字符回调 → ImGuiIO 转发（含完整键码映射），原样移植改包名。
        新建 gui_vtx.glsl / gui_frg.glsl：从 appendix-01 复制。
        修改
        SwapChain.java
        ：新增 getImageFormat()，GUI 管线用交换链真实格式（B8G8R8A8_UNORM），避免复制 BlackHoleRender 那个与实际不符的 SRGB float 常量。
        修改
        Render.java
        ：init/render/resize/cleanup 四点接线，GUI 在 Bloom 合成之后绘制。
        修改
        Main.java
        ：handleGui() 按渲染帧驱动 ImGui（喂鼠标事件 → newFrame → showDemoWindow → render），input() 开头用 getWantCaptureKeyboard() 做键盘抢占。
        修改 pom.xml：加 imgui-java 1.92.0（binding + windows/linux natives，与 vulkanbook 版本一致）。
        运行后即可看到 demo 窗口叠加在黑洞画面上，可拖动/缩放，resize 正常。下一步是 Phase 2（把 demo 窗口换成位置/速度/r/β/γ/E 等只读数据面板，数据源映射表已备好在计划 §2.4），随时可以继续。

- **2026-08-31** `8943328` chore: ignore *.log files
- **2026-08-31** `57cfa7d` feat: GUI Phase 2+3 —— 只读遥测面板（相机/测地线/渲染/场景）+ F1 显隐 + 输入互斥（视角捕获期间隐藏面板，GUI 悬停挡右键误触）
- **2026-08-31** `9b24049` feat: GUI Phase 4 —— 面板 Controls 滑条（温度/时间流速/推力/v0，积分器加绝对值 setter）+ Bloom 管线格式改用交换链真实格式（修复 VUID 06580 隐患）
- **2026-08-31** `27e36f4` feat: 面板在视角捕获期间照常显示（显隐统一由 F1 控制）+ 右上角操作说明面板（键位两列对齐，随 F1 显隐）
- **2026-08-31** `d08d613` fix: 面板 β/γ 希腊字母改 ASCII 拼写（默认字体无希腊字形，显示为问号）
- **2026-08-31** `1850720` feat: 圆轨道倾角可调 —— 轨道面法线参数化 n̂=e1·cos(tilt)+(e1×r̂)·sin(tilt)（0°贴吸积盘平面/90°原子午面），GUI 滑条 0..90°，R 键初始化时生效；tilt 0/45/90 校验均严格圆+能量守恒
- **2026-08-31** `10a4f6d` feat: Q/E 放在了模式外，测试没啥问题，recalculate会指定轨道模式上方向，所以不会影响。
- **2026-09-02** `84b4a4c` feat: Kerr Phase 0 —— NPGS BlackHole_common(5025行)全量内联为 kerr.frag（含 Uv.y 翻译适配）+ 极简 kerr.vert + kerr_copy.frag；修复 ShaderCompiler 大着色器 MemoryStack 64KB 溢出（源码改堆外 ByteBuffer 直传）；附移植计划书 kerr_port_plan.md
- **2026-09-02** `41ff678` feat: Kerr Phase 1 —— KerrRender(UBO参数面/TAA ping-pong/拷贝pass) + Scene.SpacetimeMode 热切换(Render lazy 分发,waitIdle 后重建) + GUI 单选与 Spin a* 滑条 + G 键克尔守卫；修复 ShaderModule 大 spv 的 MemoryStack 溢出；双路径冒烟 45s 无验证层报错
- **2026-09-02** `852dbf5` fix: Kerr 黑屏 —— TAA 历史初值未定义(NaN)在混合权重<1 时永久自我延续；每插槽前 2 帧强制 iBlendWeight=1 全量覆盖历史，resize 后复位；spv入库
- **2026-09-02** `99f2127` fix: Kerr 黑屏 —— iFovRadians 应传裸弧度(TraceRay 内再做 tan),之前传 tan(fov/2) 导致射线方向严重失真
- **2026-09-04** `e982848` 四项调整全部完成，且找到了黑屏的两枚根因： 根因修复（这才是黑的真正原因） 1. iFovRadians 被双重换算：EngCfg.fov 加载时已是弧度，KerrRender 又 toRadians+tan(×0.5)，着色器再 tan(/2) → 视锥半角≈0.005rad，全屏都落进黑洞阴影角区、全按被吸收输出黑。现按水平 FOV 约定换算传入（与史瓦西管线视锥逐像素一致）。 2. iInverseCamRot 着色器语义是相机→世界，Java 传的是视图旋转（世界→相机），差一个转置。现传 viewRot⁻¹。 3. 顺带：tonemap 输入钳制 + TAA 读历史 NaN 自愈（NaN 曾以 0 权重在历史里永存）、UV y 翻转收敛为 TraceRay 内唯一一次。 四项调整 1. 天空盒裁为一套：只剩 iBackground0@b1，SampleBackground 去三层/反宇宙选层；描述符布局同步；多天空盒移入计划书 Phase 4 可选。 2. 删注释死代码：系竞态试验块、白洞换系残骸、蓝移盘/喷流/白点注释调用等，kerr.frag 5121→5002 行。 3. 射线构造剥离为函数：ScreenJitter / BuildViewDirLocal / BuildWorldToLocal / AdvanceToMarchingBoundary，TraceRay 入口一眼可读。 4. kerr.frag 顶部 KERR_SKIP_RAYMARCH=true：跳过步进只出星空（此模式无阴影属预期）。 已验证：shaderc 0 错误、mvn compile 通过、本机跑 15s 无异常。请运行确认星空朝向正确后，把开关改回 false 逐段恢复（验收顺序已写进计划书 §七）。
- **2026-09-04** `45087f7` Merge remote-tracking branch 'origin/dev' into dev
  # Conflicts:
  #	src/main/java/vulkanb/eng/graph/KerrRender.java

- **2026-09-04** `617f028` 貌似能看见星空了，不容易，可喜可贺
- **2026-09-04** `8610614` fix: Kerr renderArea 悬垂指针导致黑屏 —— VkRect2D 改堆分配
  根因：createRenderInfos() 中 renderArea/extent 借 MemoryStack 分配，
  方法返回即弹栈，VkRenderingInfo 持有的指针悬垂。后续帧 stackPush()
  复用该内存 → scissor 区域变垃圾 → Pass A 所有片段被裁光 → 静默黑屏。
  
  修复：renderArea/extent 改为 VkRect2D.calloc() 堆分配，与 renderInfo
  同生命周期，cleanup() 中释放。同时移除 KERR_SKIP_RAYMARCH 调试开关
  和 KERR_DEBUG_READBACK 回读代码，恢复完整测地线步进。

- **2026-09-04** `91b40fa` fix: KerrRender ubos绑定错误，Matrix4f的get方法不会移动ByteBuffer的指针位置。SampleBackGround函数验证正确。
- **2026-09-04** `66760c9` tag: it's working, wooh!
- **2026-09-04** `0568e26` docs: 同步计划书 —— 补充第六枚黑屏根因(JOML Matrix4f.get 不推进 position)
- **2026-09-04** `c085332` refactor(kerr.frag): Phase 1.5 结构化重构 —— 死代码删除+共享工具函数提取
  - 物理删除 17 个死代码函数: DiskColortoRed/Blue, DensestarColor,
    GridColor/Simple, DrawFallingWhiteDot, ImageDiskColor, Fbm_Standalone,
    GetIngoingNullParticlePos, GetDotDistSq, SolvePolarization,
    GetWalkerPenrose, DebugInitialMomentum, GetDropFrameAngle,
    GetShadowHalfAngleRN, SolveCubicMaxReal, SolveQuarticU
  - 添加阴影剔除桩函数(return 0),删除极化/调试/贴图盘/致密星/网格调用块
  - 新增 §3.5 共享工具函数: ComputeAdaptiveStepSize, CheckEquatorialCrossing,
    SampleDiskMask, SampleJetMask, ComputeMaxStep
  - HAZE 宏提升为 const 变量(§3.5)
  - kerr.frag 4982→3077 行(-38%),mvn compile 通过

- **2026-09-05** `50d4f85` refactor(kerr.frag): 清理函数冗余参数 —— UBO 直传/派生值改为函数内局部变量
  移除约 60 个冗余参数，涉及 12 个函数：
  
  - PhysicalSpinA/PhysicalQ（始终为 iSpin*CONST_M/iQ*CONST_M）：
    从 ComputeGeometryScalars/Gradients、GetDerivativesAnalytic、
    ApplyHamiltonianCorrection、StepGeodesicRK4_Optimized、
    GetInitialMomentum、transformKerrSchild_YSpin、
    CheckEquatorialCrossing、GetKeplerianAngularVelocity、
    KerrSchildRadius、GetIntermediateSign、DiskColor、JetColor、
    GetHazeForce 共 13 个函数签名中移除，改为函数体内
    float PhysicalSpinA = iSpin * CONST_M; 局部变量
  
  - transformKerrSchild_YSpin 的 M/a/Q 参数移除（M=CONST_M 常量，
    a/Q 同上派生），函数内直接定义
  
  - GetInitialMomentum 的 iObserverMode 参数移除（同名遮蔽全局
    uniform，调用方始终传全局值），universesign 改为
    float universesign = iUniverseSign; 局部变量
  
  - DiskColor 16 个 UBO 直传参数移除（iInterRadiusRs/iOuterRadiusRs/
    iThinRs/iHopper/iBrightmut/iDarkmut/iReddening/iSaturation/
    iBlackbodyIntensityExponent/iRedShiftColorExponent/
    iRedShiftIntensityExponent 等），签名从 25 参数缩至 12
  
  - GetHazeForce 7 个参数移除，签名从 9 缩至 2（仅保留 pos_Rg, time）
  
  - JetColor 同步清理（当前未调用，预留 Phase 4）
  
  - TraceRay Resolution 参数移除，函数内直接用 iResolution 全局
  
  mvn compile 通过

- **2026-09-05** `7fc88ec` spv in
- **2026-09-05** `ca8474f` refactor(kerr.frag): TraceRay 阴影剔除段提取为 ComputeShadowCulling + 删除热折射 iDEBUG 死代码块
  - 新增共享函数 ComputeShadowCulling：将 TraceRay 内 300 行阴影剔除逻辑
    (KN/RN 阴影判定、D 形修正、延迟剔除)整体提取，签名约 7 输入 + 4 inout，
    内部只依赖全局 uniform + 参数 + res/Result 两个 inout，耦合最弱，收益最大。
  - TraceRay 收尾只留一个调用点，命中无盘/无喷流时返回 true 提前 return res。
  - 物理删除热折射 iDEBUG==1 死代码块(~110 行,仅 iDEBUG==1 触发,永不启用)。
  - TraceRay 主体从约 1000 行降至约 590 行(-41%)。
  - 主循环段保持原位：单遍紧耦合状态机(25+ 跨状态变量),机械拆函数会
    引入 25 参数巨型签名加重寄存器压力,违背 Phase 1.5 帧耗时验收标准。
  - shaderc 0 错误,spv 重编入库;mvn compile 通过。

- **2026-09-05** `77f560d` feat(kerr): Phase 2 吸积盘+红移调通 & Phase 2.5 Kerr 参数面板
  Phase 2（盘+红移排障与修复）:
  - KerrRender: iDarkmut 0→0.5 —— 盘不可见根因（NPGS 交互默认 0=盘透明）
  - kerr.frag: DiskColor 温度公式修复 —— Phase 1.5 去参数化时引入的错误本地重算
    （pow(ṁ×30,0.25)/写死 6500K）替换为 NPGS 同源 ComputeDiskArgument（≈1e21/≈1e5K），
    TraceRay 内对应死代码块删除
  - KerrRender: iInterRadiusRs 改为 ISCO(a*) 动态计算（BPT 顺行解析式）；
    iBlackHoleTime 补 c·s/Rs 单位换算（修复盘纹转速 ×147）；清除会话残留调试代码
  - eng.properties: temperatureMax 50000→100000（与克尔峰值温度对齐观察用）
  
  Phase 2.5（GUI）:
  - 新建 Scene.KerrParams（GUI 写、KerrRender 每帧读入 UBO）：吸积率/盘几何/
    亮度/透明度/三指数/背景亮度/Quality，附 ISCO 与盘峰值温度静态助手（单一实现）
  - Main: 新增 Kerr Disk 面板（仅克尔模式渲染，F1 门控，拖动下一帧生效）；
    Controls 滑条随模式拆分——Temp+测地线四件套仅史瓦西渲染，Spin a* 移入 Kerr
    面板，新增 Disk TimeScale（真实帧间隔累计器，模式热切换/resize 后盘纹相位连续）
  
  docs: 计划书同步 Phase 2 验收记录、两条盘黑屏根因、两模式盘观感差异结论

- **2026-09-05** `5a834ad` chore: 着色器 spv 出库——运行时 ShaderCompiler 自动再生的构建产物不入库
  git 对二进制无 patch 语义,每次改 shader 都整文件入库且无法合并;
  checkout 会把 frag/spv mtime 打平,存在改了源码不触发重编译的隐患。
  .gitignore 追加 resources/shaders/*.spv,git rm --cached 仅移出索引(本地保留)。

- **2026-09-05** `14ae1e0` feat(kerr): Phase 3 —— TAA 仅静止累积 + 测地相机进克尔时空 + GUI 面板重构
  TAA(按用户修订,对齐史瓦西侧策略):
  - iBlendWeight: 前2帧=1.0 / 镜头静止=0.06 / 镜头动=1.0(全量重置),
    取消 0.45 部分混合,运动彻底无拖影
  
  测地相机(从 Phase 4/5 可选提前):
  - GeodesicIntegrator 扩展自旋感知:computeMetric 换成与 kerr.frag 同式 Kerr KS 度规
    (轴 Y,f=2Mr³/(r⁴+a²y²),l=(rx∓az)/(r²+a²) 等),克氏符仍数值差分,其余零改动;
    KS 参数半径(闭合式)取代 |x| 用于步长/视界/坠落判定;圆轨道 Ω 升级顺行 BL 式
  - 修复:a≠0 奇点保护改用外视界 r₊+2% —— 环奇区梯度爆炸会数值弹射到 r~1e15(GeoTest 实测)
  - Main: Kerr 模式放开 G 键,跨时空切换不再强制退出测地模式,每帧同步 spin
  - KerrRender: 测地激活时打包 iObserverMode=2 + iCameraVelocity=KS 坐标速度 dx/dt
    (ingoing 片,U^t=1 比值约定),相机多普勒/光行差由度规严格处理,静止退回 mode 0
  - GeoTest: A/B(a=0)与基线逐位一致;C(a*=0.9 赤道圆轨道)能量守恒 5e-13,
    半径漂移 1.5%(KS 系初条件 BL 近似,轻微椭圆化);D(a*=0.9 俯冲)冻结于视界保护
  
  GUI 重构(按用户要求):
  - 新增全模式可见 Controls 面板:时空模式单选 + Temp + 测地线四件套
    (测地相机两模式均可用,取消上一提交的仅史瓦西门控)
  - 遥测面板瘦身为纯只读;操作说明面板改名 Keys
    (ImGui 按标题识别窗口,与 Controls 同名会把内容追加进同一窗口——已注释记录)
  - Kerr Disk 保持随模式显隐,默认位置改 (410,8) 避让遥测
  
  docs: 计划书同步 Phase 3 实现记录与 Phase 4 测地相机项完结

- **2026-09-05** `3cd5e48` docs: Phase 3 验收通过(用户运行确认),同步计划书状态
- **2026-09-05** `9d0847a` feat(kerr): Phase 5 观者四维标架平行输运(ObserverMode=-1)——分支实验
  着色器零改动(kerr.frag mode -1 分支 Phase 0 已随 common 搬运就位),全 Java 侧:
  
  - GeodesicIntegrator: RK4 8维→16维(U+e1/e2/e3 标架腿联立),标架按
    平行输运方程 de/dτ=−Γ(U,e) 推进;X 方程斜率为各阶段 U 值,与 dU/dτ
    分开保存(初版混淆二者,GeoTest 回归当场拦下);每子步 Gram-Schmidt
    重正交;initializeTetrad(相机轴基准)/getFoldedTetrad(头转折叠
    E_i=Σ_j H[ij]·e_j)/tetradOrthoError 测试钩子
  - Main: 测地模式鼠标/QE 滚转改转 headQuat;每帧相机世界姿态=输运标架×
    headQuat(deriveCameraOrientation)——视线随参考系拖拽,Phase 5 视觉核心;
    G 进入时 headQuat=当前姿态、标架以相机轴初始化,进出无跳变
  - KerrRender: 测地激活打包 iObserverMode=-1 + iU_up/ie1/2/3_up +
    iBlackHoleRelativePosRs=KS 原始坐标(mode -1 约定);静态回 mode 0
  - Camera: 增 getOrientation/setOrientationRaw
  
  GeoTest: A-D 与 Phase 3 基线逐位一致(零回归);
  E(a*=0.9 圆轨道标架输运 5 圈)正交性误差 1.1e-15(机器精度)

- **2026-09-05** `77f0798` fix(kerr.phase5): 三处验收反馈修复 —— UBO 槽位错位/标架折叠乘序/高分屏缩放
  1. 色块(轨道/自由视角)根因:KerrRender 非测地分支 5 个 vec4 槽只写了 4 个,
     后续 11 int + 37 float 全部错位 16B → 参数乱(测地路径恰好 5 个故正常)。
     循环 4→5,补注释防再犯
  2. 测地鼠标反向根因:标架折叠与相机姿态推导用了 M=T·Hᵀ,头四元数实际绕
     世界/输运轴转 → 改为 M=T·H(E_i=Σ_j t_j·H[j][i],getFoldedTetrad 与
     deriveCameraOrientation 同步换下标)——头四元数变成绕头部局部轴右乘,
     与 rotateFree 的 FPS 手感同构,无需翻符号
  3. 高分屏:Window 加 GLFW_SCALE_TO_MONITOR(帧缓冲随系统 DPI 缩放);
     初始窗口改为主显示器 60%(原写死 800×600 物理尺寸过小);
     ImGui 鼠标事件乘内容缩放比(GLFW 光标是窗口单位,DisplaySize 是帧缓冲像素)
  
  GeoTest A-E 全绿:基线零回归,标架正交性 1.1e-15

- **2026-09-05** `d419b19` refactor(blackhole.frag): 按 Phase 1.5 标准重构 —— 死代码删除/重复提取/超长函数拆分
  行为不变(逐公式等价搬运),513 → 446 行,shaderc 0 error:
  - 死代码物理删除:starfield 程序化星空(主路径已改 uSkybox 采样)及其独占的
    hash/noise3D,约 66 行
  - 重复提取:DiskColor 两层盘采样(Color0/Color1 逐行重复)提取为 DiskLayerColor,
    以 FadeOffset 0/0.5 区分两层时间相位;死变量 PosOnDisk/PosR/LastDis 清除
  - main 156 行拆为 AdvanceToBoundingSphere(包围球跳空)/BackgroundColor(背景
    三色波段蓝移采样)/ApplyTAA(时域累积)三个阶段函数 + main 约 110 行;
    全文件最大函数 105 行,达标 ≤150
  
  注:Kerr 移植的"blackhole.frag 零改动"硬约束经用户指示对本次重构豁免,
  计划书已留痕。spv 不入库(运行时 ShaderCompiler 自动再生)。

- **2026-09-05** `d791fec` Merge branch 'phase5-tetrad-camera' into dev
- **2026-09-06** `fb8055e` fix(wnd): 移除 GLFW_SCALE_TO_MONITOR——渲染分辨率不应随 DPI 隐性暴涨
  该开关使帧缓冲=窗口×DPI 缩放,对光线步进渲染器像素量暴涨(150% 屏 ×2.25);
  不开时高分屏由 DWM 拉伸显示(略糊),要清晰拖大窗口即可,分辨率自决。
  contentScale 改为帧缓冲/窗口单位实测比值(glfwGetWindowSize),
  不再用 DPI 标称值——鼠标坐标换算在两种模式下都真实。
  
  另勘误:此前多次提示的'NumPad +/- 降渲染分辨率'并不存在
  (NumPad 只调 BaseTemperature),渲染分辨率始终=窗口尺寸,误导性文案已不在。

- **2026-09-06** `8bb661d` feat(cfg): 窗口尺寸入配置 + jar 外置配置文件覆盖 + README;含高分屏回退
  高分屏回退(用户手动,本提交一并入库):移除 SCALE_TO_MONITOR 相关全部痕迹
  (内容缩放比/鼠标换算/60% 初始尺寸),窗口尺寸不再受 DPI 隐性放大。
  
  配置双层加载(EngCfg):
  - 第 1 层:jar 内 classpath 根的 eng.properties(随包分发)
  - 第 2 层:外部覆盖文件——jar/classes 同目录优先,其次工作目录;
    键级合并,外部未写的键沿用 jar 内,两处都缺失用代码内默认值
  - 新增 window.width/height(初始窗口尺寸,默认 1280×720),Window 改读配置
  - 无头验证:外部文件只写 window.width=1234 → 宽度覆盖/其余沿用 ✓
  
  README.md:项目简介/构建运行/双层配置说明/键位表/源码结构/分支指引

- **2026-09-06** `9e31d80` refactor(main): 主类拆分 —— GUI 面板提取 Panels,输入提取 InputController
  Main.java 613→63 行,只保留 IGameLogic 委托与入口:
  - vulkanb.eng.graph.gui.Panels: 四个 ImGui 面板构建(Telemetry/Controls/Kerr
    Disk/Keys)+ 显隐状态(F1)+ FPS 统计(遥测与控制台读数共用);Keys 面板改默认折叠
    (setNextWindowCollapsed FirstUseEver)
  - vulkanb.eng.InputController: 按键/鼠标/视角捕获/测地积分推进/头转与相机姿态
    推导,按职责拆为 handleGeodesicToggle/handleFreeMove/stepGeodesic/
    handleMouseLook/handleRoll/handleModeKeys/handleParamKeys 等私有方法
  - 行为不变:处理顺序(F1→GUI帧→键盘抢占→各键组)与原 input() 逐段等价
  
  键盘抢占与帧统计等跨类状态经 Panels 显式方法读写,无共享可变静态。

- **2026-09-06** `d6953ce` feat(log): 应用日志规范化 —— AppLog 总开关(配置/命令行/GUI 三处可调);含 beta.z 遥测修复
  - 新增 vulkanb.eng.AppLog:应用自定义消息统一出口(原 14 处散落的
    System.out.println/printf 全部收敛),volatile 开关,info/infof 两个出口
  - 三处可调(后设生效):eng.properties log.verbose(默认 true)、
    命令行 --verbose/--quiet(覆盖配置,Main.main 解析)、
    Controls 面板 "Verbose log" 复选框(运行时切换)
  - 引擎内部日志与业务日志分离:eng.properties 新增 log.level(tinylog level,
    默认 INFO,压掉启动期 TRACE/DEBUG 刷屏),Main.main 在任何日志调用前
    经 Configuration.set 应用
  - 顺手修复:遥测面板 beta 向量缺 z 分量(用户发现并修复,一并入库)
  
  eng.properties/README 同步文档。

- **2026-09-06** `8e6ed34` feat: 改下kerr参数面板初始位置
- **2026-09-06** `4dccb6b` feat(kerr.gui): Kerr Disk 面板补齐 —— KN 电荷/μ/色彩后处理/热折射 + KN ISCO 数值解
  KerrParams 新增 9 个 UBO 可调字段:qStar(iQ)、mu(iMu)、backShiftMax、
  reddening、saturation、photonRingBoost、photonRingColorTempBoost、
  boostRot、enableHeatHaze+heatHaze(着色器原生 gate,此前 UBO 恒零)。
  
  ISCO 升级 Kerr–Newman 数值解:NPGS calculate_KN_ISCO/get_orbit_energy 移植
  (黄金分割搜索,Q=0 回落 BPT 解析式);新增 isNakedSingularity 判定
  (a²+Q²>1,面板红色警告+ISCO 0.5Rs 兜底,着色器裸奇点分支接管);
  峰值温度镜像公式补 μ 参数。
  
  Panels.renderKerrPanel 新增 Color/Post 与 Heat haze 两组滑条;
  KerrRender UBO 打包改读全部新字段(字段顺序与 kerr.frag 声明严格一致)。
  
  数值验证:a=0/q=0 → 3.0000Rs(解析精确);a=0.9 → 1.1604Rs(=BPT 解析);
  q=0 连续性 ✓;μ=5 → 温标 ×5^0.25 ✓;裸奇点判定 ✓。

- **2026-09-08** `b967e39` fix(kerr.phase5): G 进出测地模式视角翻到身后 —— 三处符号/基准错
  - kerr.frag mode=-1 初动量漏负号:主循环按 -dLambda 反向积分,静态观者分支为
    P = U − Σk·e,而测地分支写成 P = U + Σv·E → 光子动量与视方向同向,
    反向积分采到身后半球。改为 P = U − Σv·E(宿主标架 = 相机 right/up/back)。
  - G 进入时 headQuat 预置为相机姿态,与"标架腿=相机轴"叠加后被
    deriveCameraOrientation 再乘一次得 R²(yaw≈180° 时翻回正后方)。改为单位四元数。
  - 进入时相机轴改取自视图矩阵:ORBIT 模式下 freeOrientation 是从不更新的陈值,
    标架/速度基准皆错;同时把取到的轴回写 freeOrientation,进测地不跳变。
  - 进出当帧重判 geodesicOn,不再用陈值多跑一次已停用的 stepGeodesic、
    或把本帧鼠标转给已无用的 headQuat。

- **2026-09-09** `500ef44` fix: 调整 .gitattributes 统一换行符
- **2026-09-19** `6015f92` feat(kerr.phase4): prepass/composite + bloom + 喷流接线 —— kerr.* 参数入 eng.properties
  - 配置面:eng.properties 新增 kerr.* 30 键(自旋/吸积率/盘几何/亮度/电荷/prepass/bloom/喷流),
    EngCfg 逐字段解析 + KerrParams 初值接线,jar 外置覆盖链路对克尔参数生效(Phase 2.5 计划欠账)
  - prepass+composite(默认关):kerr.frag 单文件三路径(无 include 方案),iPrepass=0 原路径逐像素
    /1 半分辨率双附件(扭曲场 xyz=Dir*Shift+w=Status + 体积色,均 R32G32B32A32)/2 边缘感知合成
    (状态位敏感边界 + 逸出方向场点积<0.99 几何检测,边缘全分辨率重算,平滑处手动双线性——
    状态位取权重最大邻居,坐标越界 clamp);KerrRender 双附件管线 + 独立 GameArgs/BlackHoleArgs
    UBO(后者字节复制仅改 iPrepass@200)+ NEAREST 采样器 + 同帧写读 barrier;面板 Prepass 复选框
  - bloom:kerr_copy.frag 升级 kerr_bloom.frag(亮部提取 + 24-tap 黄金角圆盘模糊,机制与史瓦西侧
    bloomComposite 一致;kerr 历史已是 tonemap 后 LDR 域与 NPGS bloom 同域,免二次 tonemap);
    push constants 8B 传强度/阈值(默认 0.5/0.7,强度 0 逐像素等同纯拷贝);copy 系标识全量更名 bloom 系
  - 喷流接线:Phase 0/1.5 移植版 JetColor 签名已与 DiskColor 同构(位置/动量插值+换系自理),
    不再依赖 NPGS 原版调用点的 ingoing 系变量(上游本就是注释态),TraceRay 盘调用点恢复调用,
    IsJetVisible 门控(ṁ≥1e-2 且亮度>0);UBO iJet* 四字段写死改 KerrParams 驱动,默认关闭;
    面板 Jet 区四滑条(亮度/频移指数/饱和度/蓝移上限)
  - 验证:shaderc 0 错误;GeoTest A-E 与文档基线逐位一致;四轮 30s 验证层冒烟
    (默认/prepass/prepass+bloom/喷流开)零报错;观感经用户运行确认

- **2026-09-19** `a571c2c` docs: 同步计划书 —— Phase 4 三项完成入档 + Phase 5 验收补充 + 卷首状态块更新
  - 卷首状态块(2026-09-19):Phase 5 合并 d791fec + 两轮验收反馈闭环(77f0798/b967e39)、
    4dccb6b 面板补齐、blackhole.frag 重构 d419b19、Main 拆分/AppLog/窗口配置等补记
  - Phase 5 节补验收补充记录(反馈闭环)
  - Phase 4 清单:prepass+composite / Kerr bloom / 喷流接线+GUI 三项划掉,各附实现记录
    (单文件三路径方案、bloom LDR 域与 push const、喷流 IsJetVisible 门控与调参提醒);
    剩余仅多天空盒 + PerlinNoise 3D LUT

- **2026-09-19** `0111b27` docs(kerr): 更正多天空盒机制描述 —— 宇宙变体选层,非三层分辨率 LOD
  经实测核对 NPGS 资源与 SampleBackground 源码:
  - Universe0/1/2 各面均 1024×1024(分辨率相同),按 int(iInWhichUniverse+3+useContground)%3
    选择"宇宙变体"星空(与吸积盘可见性门控同款 %3 机制),并非三层分辨率分层;
  - Antiverse0/1/2 各面 2048×2048,是 Background 族的反宇宙对应物(族间差异,非层间差异);
  - 本项目配置下(iInWhichUniverse 恒 0、iWhitehole=0)选层表达式恒为 0 → 恒采 Universe0,
    恢复 b2..b6 绑定画面不变,该功能为死项;前置依赖为接线 iInWhichUniverse 或白洞模式。
  计划书 §1.2/§1.3/§2.5/§五-5/§七-1/Phase 4 条目及 kerr.frag、KerrRender 注释同步更正;
  Phase 4 条目保持未完成状态,仅更正机制描述与前置依赖

- **2026-09-19** `491c6f8` feat(skybox): 星空/山海双天空盒接入两渲染器 —— DualSkybox 换绑 + 切换 UAF 修复
  - DualSkybox 助手(eng/graph/vk):主星空(/textures/skybox)+ 山海盒
    (/textures/skybox_mountains_seas,取 NPGS Antiverse0Skybox 纹理,六面 2048²);
    山海盒惰性加载(首开上传,全 mip 链约 130MB 显存),关闭时重绑回星空后释放;
    BlackHoleRender(set0.b0)与 KerrRender(set1.b1)各自在天空盒槽位换绑,着色器零改动
  - 施瓦西侧接入属用户对原移植硬约束的明确豁免;开关状态移至 Scene(跨渲染器共用),
    配置键顶层化 skybox.mountainsSeas,Controls 面板 Mountains & Seas skybox 复选框全模式可见
  - 修复 DualSkybox.bind 重绑时序:rebind(current()) 在标志翻转前取到旧盒——
    开=重绑旧盒画面无变化,关=绑上新盒后立即释放 → 描述符悬垂 → DEVICE_LOST
    (报错点随机漂移,伪装成核显 TDR);改为显式重绑目标盒,关闭路径先重绑星空再释放
  - 修复 Fence.fenceWait 吞 vkWaitForFences 返回值:DEVICE_LOST 被静默延迟到
    下一次提交才暴露,根因难查;现 vkCheck 在真实出错点抛出
  - 验证:自动循环无头测试(60 帧开/180 帧关,覆盖原 UAF 路径)45s 零错误;
    配置直开冒烟 20s 干净;切换观感与反复开关经用户实测确认

- **2026-09-19** `c43d585` perf(kerr.noise): PerlinNoise 哈希查表 A/B —— Phase 4 可选项全部完成
  - PerlinNoise 为值噪声(格点 sin 哈希 + 三次衰减三线性插值),直接存噪声值域的 LUT
    有两难:高倍频(3^i,i≤6)超单 LUT 奈奎斯特极限、周期重复在盘面可见;落地方案改为
    哈希值查表——64³ R8 3D 纹理(256KB)按同式预计算格点哈希值(Java Math.sin),
    8 次 texelFetch(坐标 &63 周期化,负坐标补码位与自然回绕)替代 8 条 sin 超越函数链,
    插值结构与值域不变,任意倍频无混叠;低频重复周期 ≈194 输入单位,远大于盘面范围
  - A/B:BlackHoleArgs 末尾追加 iNoiseLut 字段(std140 追加不移动既有偏移,384→400B),
    KerrParams.noiseLutEnabled → Kerr Disk 面板 Noise LUT (A/B) 复选框实时切换,
    配置 kerr.noiseLut 默认关;prepass 字节复制 UBO 自动携带,两路径口径一致
  - 基础设施:Image.ImageData 增 imageType/depth(3D 纹理);新增 vk/NoiseHashLut;
    PerlinNoise1D(喷流)保持程序化
  - 用户 A/B 实测:无可感知的视觉与帧率差异(视觉同源符合预期;噪声非该负载瓶颈);
    功能保留默认关,供高倍频场景或未来硬件复测
  - 验证:shaderc 0 错误;LUT 开 30s 冒烟无验证层报错;GeoTest 基线一致

- **2026-09-19** `e16dce9` perf(graph): prepass 管线懒建 + PipelineCache 落盘 —— 着色器改动后首启减负
  诊断:改着色器后首启 59.8s 中 58.5s 在驱动侧管线编译(kerr.main/prepass
  各 ~30s,同一 kerr.frag spv 两管线各编一次),shaderc 仅 ~200ms;spv 删除
  不触发慢路径(驱动按 SPIR-V 字节内容寻址缓存),完整数据与触发方法见新增
  计划书 foragent/shader_compile_opt_plan.md。
  
  - B1:prepass 管线默认关不再创建,GUI 首次开启时帧内现建一次后复用
    (prepass 双附件与描述符集仍始终创建,与既有设计一致)
  - B2:PipelineCache 落盘持久化(shader-cache/pipeline-cache.bin)——启动
    initialData 预热,Render.init 完成后与退出各写回一次;缓存按内容寻址,
    改着色器后首启仍需编译一次,此后确定性命中不赌驱动内部缓存
    (实测:清驱动 LocalLow/Intel/ShaderCache 后仅靠该文件秒开)

- **2026-09-20** `3cfd22f` fix: TinyLog设置日志等级前不可调用，改用System.out.print
- **2026-09-20** `e395a5b` feat: 统一spv文件路径，统一shader过期校验方法，新建常量类
- **2026-09-21** `806ed40` fix(gui): 滑条 Ctrl+Click 不弹输入框 —— 修饰键须按 ImGuiMod_* 喂
  Dear ImGui 1.92 起 io.KeyCtrl/KeyShift/KeyAlt/KeySuper 只由 ImGuiMod_Ctrl 等
  "修饰键键位"的 AddKeyEvent 驱动，不再从 ImGuiKey_LeftCtrl/RightCtrl 合并；
  imgui-java 1.92 的 ImGuiIO 也只剩 addKeyEvent(int, boolean) 一个重载（没有带
  mods 的重载可以代理这件事）。原 KeyCallback 只喂 LeftCtrl/RightCtrl，于是
  io.KeyCtrl 恒为 false，SliderScalar 的 Ctrl+Click 判定永不成立——按键事件确实
  进了 ImGui（打点日志可见），但 ImGui 眼里的 Ctrl 从来没按下过。
  
  - GuiUtils.KeyCallback：把 GLFW mods 位掩码显式喂成 ImGuiMod_Ctrl/Shift/Alt/Super；
    LeftCtrl 等普通键位照旧喂（供 IsKeyPressed(ImGuiKey_LeftCtrl) 类查询）；补上
    GLFW_REPEAT；ImGuiKey.None 直接跳过，避免把无映射键喂进 native 触发断言。
  - GuiUtils.KeyCallback：去掉 wantCaptureKeyboard 拦截。控件焦点正是这次点击/按键
    才建立的，空闲态把修饰键拦在门外属于先有鸡还是先有蛋；与相机按键的互斥本就由
    InputController 在 buildFrame 返回 wantCaptureKeyboard 时直接 return 完成。
  - MouseInput/Panels：鼠标按钮改由 GLFW 回调排队的边沿事件喂（press/release 各一条，
    buildFrame 消费后清空），不再轮询 isLeftButtonPressed。轮询的隐患：落在同一帧内的
    press+release 会被处理成最终"未按下"，整次点击丢失——人手点击时长约 50~100ms
    通常跨多帧故无感，但低帧率（测地近距 ~10FPS，一帧 100ms）下一次点击正好被吞。
  - Panels.buildFrame：进入视角捕获那一帧补发左/右键抬起。ImGui 纯事件驱动、从不主动查
    系统按键状态，而捕获期间不再喂鼠标事件 → 切捕获前那帧喂进去的按下态等不到配对的抬起，
    io.MouseDown 从此恒为 true（IsMouseDown/IsMouseDragging 恒真，拖放及"需无键按下"
    的判定全乱）。右键单击切视角捕获必现。
  - 移除 KeyCallback 与 buildFrame 里的调试打点。
  - 附带修正：遥测面板 fov 显示补 Math.toDegrees（此前直接打印弧度）。

- **2026-09-21** `00a783b` feat: 修改默认施瓦西模式温度为90000，确保两模式初始观感差别不是太大
- **2026-09-21** `1f4c543` feat: 修改默认FOV为90°
- **2026-09-21** `897a816` feat(gui): 滑条标签移到左列并自适应列宽 —— 窄面板不再裁标签
  ImGui 对 Slider/Drag 这类"值编辑"控件的默认排版是框在左、标签在右，而滑条默认
  ItemWidth = -1（直接铺到窗口右边缘），标签因此永远落在窗口右缘之外被裁掉——面板
  （Kerr Disk 定位在 w-250）越窄越明显，拉宽面板又只会把滑条拉得更长、标签照旧显示不全。
  
  - Panels.sliderL()：新增左标签滑条包装，alignTextToFramePadding + textUnformatted(label)
    + sameLine(列宽) + setNextItemWidth(-13)，标签占左列、滑条填满左列右侧剩余宽度；
    面板内 30 处 ImGui.sliderFloat 全部改走该包装。压的是滑条而非标签，标签恒完整。
  - 列宽 labelCol 由 calcTextSizeX 取历史最大值自适应（稳态即最长标签像素宽），无需魔数；
    该列宽为 Controls / Kerr Disk 两个面板共用，故左列在两个面板间天然对齐。
  - 滑条自身标签传 " ##" + label：可见文本只剩一个空格，既不重复画标签，又保留字高
    ——真·空标签会让 SliderScalar 的框高塌成 FramePadding.y*2；ID 取 ## 之后整串，
    同窗口内各滑条仍唯一（值本身存在 Scene 里，ID 变化不涉及状态迁移）。
  - Controls / Kerr Disk 各补 setNextWindowSize 给定初始尺寸（Controls 350x250、
    Kerr Disk 400x500，Kerr 面板项多，超出部分走面板内滚动条）。注意未传 ImGuiCond，
    等价 ImGuiCond.Always：尺寸每帧强制，面板不再支持手动拖边框缩放。

- **2026-09-22** `8a76bd8` fix: 补全 GLFW 滚轮回调，恢复 ImGui 面板滚轮滚动
  ImGui 面板此前只能通过拖动滚动条滚动，鼠标滚轮无效。排查发现
  MouseInput 只安装了光标位置、进入/离开、鼠标按键回调，唯独缺少
  glfwSetScrollCallback，导致 GLFW 的滚轮事件从未进入 ImGui 输入系统，
  io.getMouseWheel() 恒为 0。
  
  改动：
  - MouseInput 新增 pendingScrollX/Y 累积字段，并安装 glfwSetScrollCallback；
    采用累积而非直接赋值，避免两次 newFrame 之间的多次滚轮事件被覆盖丢失。
  - 新增 consumeScrollX/Y()，读取后清零，供每帧一次性消费。
  - Panels.buildFrame 在喂完鼠标位置后调用 io.addMouseWheelEvent()，
    将滚轮事件转发给 ImGui。
  
  效果：面板滚轮滚动与拖动滚动条行为一致，无需再手动 setScrollY。

- **2026-09-22** `cfc65d2` fix: 在ubuntu下 mod 值与 windows下表现不同，是glfw已知差异：https://github.com/glfw/glfw/issues/1630
- **2026-09-23** `8edb7e0` fix(kerr): 修复测地模式背景旋转 90° 的坐标系错误
  问题：
  进入测地模式（iObserverMode=-1）后，星空背景整体左移约 90°，
  黑洞偏离屏幕中心。0 模式正常。
  
  原因：
  -1 模式下 RayPosLocal 与 P_up 已在 KS 世界系，但 EscapeDir 出口
  仍统一乘 LocalToWorldRot（一个由盘法向/切向推出的固定绕 Y 旋转），
  多转了一次 90°。0 模式下 X 与 P_up 在 BH 局部系，出口乘该旋转
  是正确的；两种模式出口坐标系不一致导致切换时背景跳变。
  
  改动：
  1. shader (kerr.frag)：bWaitCalBack 分支中三处 EscapeDir 按模式区分。
     - iObserverMode == -1：方向已在世界系，直接输出
     - 其余模式：保留 LocalToWorldRot * dir
     覆盖 res.EscapeDir / DistanceToBlackHole > Boundary / isInvalid
     三条路径。
  
  2. InputController：handleGeodesicToggle 进入测地模式时，标架三轴
     由 view.positiveX/Y/Z（= 世界轴在相机中的表示，camToWorld 的行）
     改为 camToWorld 的列（= 相机轴在世界中的表示）。initializeTetrad
     形参语义即为相机世界轴，原写法语义不符，会造成观察者初始姿态偏差。
  
  验证：
  进入/退出测地模式背景不再旋转，黑洞回到屏幕中心；0 模式行为不变。

- **2026-09-23** `1325461` feat: 额...调整了点儿...观感？
- **2026-09-23** `8861835` feat: 调整测地模式时间尺度默认值，调整时间调节按键为单次按下；增加屏幕ui renderOriginIndicator 用于指示原点位置。
- **2026-09-26** `259aff0` docs: 9.5 补记全重投影 TAA 否决结论 —— 方向/世界坐标两案 8.30 当日实现当日回退，混沌敏感根因与用户裁决回写
- **2026-09-26** `088eaa3` docs: 测地计划书卷首补记 Phase 3 各项实际归宿 —— 3-3 经克尔 Phase 5 完成/3-2 仅史瓦西侧剩余/3-4 经 GUI 计划完成/3-5 开放
- **2026-09-26** `9da6481` docs: 四份计划书 2026-09-26 核对补记 —— glsl 计划 §9.2/9.4/远期项过时点、GUI 落点迁移、kerr 修复链补 8edb7e0、编译优化诊断注懒建
- **2026-09-26** `a796819` docs(kerr): 立项两份计划书 —— NPGS Bloom mip 树+ColorBlend 调色链移植(观感差距大头,§1.1 盲区)与多宇宙星空 6 套 cubemap+iWhitehole 接线;附 B站 观感对比调查结论与参数建议(默认值不动)
- **2026-09-26** `a0817eb` feat(kerr): NPGS mip 树 Bloom + ColorBlend 调色链移植 —— 历史图完整 mip 链(blit 逐级生成)+ kerr_composite 8-octave 辉光与 ColorBlend 调色逐行照搬;kerr_bloom.frag 退役,bloomThreshold 废弃(bloom 计划 Phase 1+2,待运行时验收)
- **2026-09-26** `efa1718` docs(kerr): bloom 计划验收闭环 —— Phase 1+2 用户实测通过定稿,Phase 3 人工调参开放并约定结论回写 §五
- **2026-09-26** `5af592a` feat: P 键时间暂停/恢复 —— 冻结盘动画与测地相机推进用于定格观察;渲染时间(iRenderTime@place_holder4)与模拟时间解耦,TAA 暂停期继续累积出清晰静帧;遥测面板显示暂停态
- **2026-09-26** `3b00109` chore: 开源准备 —— GPL-3.0 LICENSE + THIRD-PARTY_NOTICES(vulkanbook MIT/NPGS GPL-3.0/运行时依赖) + README 许可证与致谢节;移除知乎文章全文(版权,已存档至仓库外),计划书引用改为标题指称
- **2026-09-26** `f2a16d7` docs: kerr 计划书措辞中性化(公开前清理)
- **2026-09-26** `c11a03f` docs(readme): 加「碎碎念」—— 项目缘起与 vibe coding 开发史
