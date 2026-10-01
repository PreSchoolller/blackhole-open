# Black Hole Simulation

基于 **Vulkan + LWJGL3** 的黑洞实时可视化模拟器。双时空渲染器可热切换：

- **Schwarzschild**：引力偏折 raymarch（辛格式）+ 体积吸积盘（双相位噪声动画）+ bloom/TAA，测地线相机模式（G 键）带引力红移/相机多普勒与视界坠落演出。
- **Kerr**：NPGS 项目的 Kerr–Schild 度规移植——自旋 a\* 可调、RK4 测地线光线追踪、体积吸积盘（ISCO 内缘随自旋内移）、三色波段多普勒/引力红移；测地相机支持自旋感知积分与观测者四维标架平行输运（视线随参考系拖拽）；星空/山海双天空盒可在 Controls 面板切换（prepass 半分辨率加速与 bloom 可选）。

## 构建与运行

```bash
mvn package                 # 产出 fat jar:target/blackhole-lwjgl-1.0-SNAPSHOT.jar(shade 替换主产物,含全平台 natives)
mvn package -Dplatform=windows   # 瘦身 jar:只带单个平台 natives(可选 windows/linux/macos,CI 打平台包用)
java -jar target/blackhole-lwjgl-1.0-SNAPSHOT.jar           # 正常运行
java -jar target/blackhole-lwjgl-1.0-SNAPSHOT.jar --quiet   # 关闭应用自定义日志
java -jar target/blackhole-lwjgl-1.0-SNAPSHOT.jar --verbose # 强制开启(优先级高于配置)
```

开发期直接运行（着色器按时间戳自动重编译，改 GLSL 免构建）：

```bash
mvn compile exec:java -Dexec.mainClass=vulkanb.Main
```

依赖：支持 Vulkan 1.3 的 GPU/驱动。开发分支 `dev`，stable 合入 `main`；进行中的实验性功能在独立分支（如 `phase5-tetrad-camera`）。

## 配置

配置文件为 `eng.properties`（Java properties 格式），**双层加载**：

1. **jar 内默认**——随包分发的 `eng.properties`；
2. **外部覆盖**——放在 **jar 包同目录**（优先）或**工作目录**下的 `eng.properties`，其中出现的键覆盖 jar 内值，**未写的键沿用 jar 内**。

因此发布后调参只需在 jar 旁边放一个只写想改项的配置文件，例如只改窗口大小：

```properties
window.width=1920
window.height=1080
```

> 渲染分辨率 = 窗口帧缓冲尺寸，拖拽窗口即改变。高分屏不做 DPI 放大（像素量代价过大）。

主要配置项（完整见 `src/main/resources/eng.properties` 注释）：

| 键 | 说明（默认值） |
|---|---|
| `window.width` / `window.height` | 初始窗口尺寸（1280×720） |
| `log.level` | 引擎日志级别：TRACE/DEBUG/INFO/WARN/ERROR（INFO） |
| `log.verbose` | 应用自定义日志开关（true；GUI 复选框与 `--verbose`/`--quiet` 可覆盖） |
| `physDeviceName` | 指定 GPU，空缺自动选择 |
| `vkValidate` | Vulkan 验证层（false，调试用 true） |
| `vsync` / `requestedImages` | 垂直同步 / 交换链图像数（true / 3） |
| `fov` / `zNear` / `zFar` | 相机（60° / 0.1 / 1000） |
| `skybox.mountainsSeas` | 山海星空盒开关（false；两模式共用，惰性加载约 130MB 显存，GUI Controls 可切换） |
| `blackhole.temperature` | 基准温度 K（15000，运行时 NumPad ±） |
| `blackhole.diskInnerRadius` / `diskOuterRadius` | 盘半径 Rs 倍数（3 / 18） |
| `kerr.spin` 等 `kerr.*` | 克尔模式初始参数（自旋/吸积率/盘几何/亮度/电荷 Q\*/prepass/噪声 LUT/bloom/喷流等 31 键，完整见 `eng.properties` 注释；GUI 滑条运行时覆盖） |
| `input.mouseSensitivity` 等 | 鼠标/滚转/移动/缩放手感 |

## 操作

| 键 | 功能 |
|---|---|
| **G** | 测地线相机模式开关（引力驱动相机运动；两种时空均可用） |
| **F** | 轨道相机 / 自由视角切换（测地模式下无效） |
| 鼠标右键 | 视角捕获开关；捕获中移动鼠标控制视线 |
| WASD / 方向键 | 移动（测地模式 W/S=沿视线推力，↑/↓=时间流速） |
| Shift / Ctrl | 自由视角升 / 降 |
| Q / E | 绕视线轴翻滚 |
| R | 测地模式：初始化为当前半径圆轨道 |
| 1 / 2 | 测地初速 v0 ∓ 0.05c（进入测地模式前设置） |
| `[` / `]` | 推力大小增减 |
| PageUp / PageDown | 轨道缩放 / 自由视角升降 |
| NumPad +/− | 基准温度 ±（K） |
| **P** | 时间暂停 / 恢复（冻结盘动画与测地相机推进，定格观察某一时刻；渲染时间与 TAA 照常累积出清晰静帧，鼠标视角仍可转动） |
| F1 | 显隐全部 GUI 面板 |

GUI 面板：**Black Hole Telemetry**（只读遥测）/ **Controls**（时空模式切换 + 相机滑条，全模式可见）/ **Kerr Disk**（仅克尔模式：吸积率、盘几何、亮度/不透明度/频移指数等，拖动即生效）/ **Keys**（键位表）。

## 源码结构

```
src/main/java/vulkanb/
├── Main.java                  # 入口(接口委托,保持精简)
├── eng/
│   ├── EngCfg.java            # 双层配置加载(jar 内默认 + 外部覆盖)
│   ├── InputController.java   # 按键/鼠标/测地相机推进(渲染帧节拍)
│   ├── scene/                 # Camera / GeodesicIntegrator(测地积分+标架输运) / KerrParams
│   └── graph/                 # Render(分发) / BlackHoleRender(史瓦西) / KerrRender(克尔)
│       ├── gui/               # GuiRender(Vulkan 绘制) / Panels(ImGui 面板构建)
│       └── vk/                # Vulkan 封装 + ShaderCompiler(运行时 shaderc)
└── resources/
    ├── shaders/               # blackhole.frag(史瓦西) / kerr.frag(克尔) 等
    └── eng.properties         # 默认配置(随 jar 分发)

foragent/                      # 开发计划书与排障记录(kerr 移植/测地相机/相机多普勒)
```

着色器在运行期由 shaderc 编译（`ShaderCompiler.compileShaderIfChanged`，比对时间戳），`*.spv` 为构建产物、不入库。

## 许可证与致谢

本项目整体以 **GNU GPL-3.0** 发布（见 [`LICENSE`](LICENSE)，Copyright © 2026 gwangxwan）——
因克尔渲染管线与部分素材移植自 NPGS（GPL-3.0），衍生作品随之以同许可证开放。

| 来源 | 许可证 | 本项目中的使用 |
|---|---|---|
| [NPGS](https://github.com/baopinshui/NPGS)（baopinshui） | GPL-3.0 | `kerr.frag` 克尔黑洞着色器（移植自 BlackHole_common.glsl）；`skybox/`、`skybox_mountains_seas/` 天空盒素材 |
| 知乎文章《从零开始搓一个黑洞——glsl编程实战》（同作者，代码与 NPGS 同源） | GPL-3.0（同源） | 史瓦西管线 `blackhole.frag` 的参考实现 |
| [vulkanbook](https://github.com/lwjglgamedev/vulkanbook)（Antonio Hernández Bejarano） | MIT | `eng/` 引擎框架基础类、GUI 渲染层（GuiRender/GuiUtils 与 gui 着色器） |

三方许可全文与运行时依赖（LWJGL/JOML/imgui-java/shaderc 等）清单见
[THIRD-PARTY_NOTICES.md](THIRD-PARTY_NOTICES.md)。
`foragent/` 为开发过程文档，非运行所需。

## 碎碎念

Vibe coding 兴起，恰逢各家模型在自家 Agent 平台上限免，遂尝试让 AI 做一个黑洞模拟（哈哈哈，很经典吧）。当时 AI 用 JavaFX 以粒子模拟吸积盘（开发历史太多信息不想删，所以那个仓库没公开），后又改为 LWJGL+OpenGL，但一直在一些问题上徘徊无法解决，遂引入 [vulkanbook](https://github.com/lwjglgamedev/vulkanbook) 的示例框架，让 AI 在这个基础上开发。

大数据或许是知道了我的意图，也或许是那时候大家都在让 AI 干这件事儿——B 站给我推了 UP 主 Baopinsui（[space.bilibili.com/95332087](https://space.bilibili.com/95332087)）的视频，太惊艳了。遂循着其动态找到了他的知乎文章《[从零开始搓一个黑洞——glsl编程实战](https://zhuanlan.zhihu.com/p/20536269771)》，当时已 0 点，顶着困意看完——详细的开发流程和公式推导让我在想：能不能把它搬到我的 vibe coding 项目上呢？于是就有了本项目的大更新。

期间先后用过 MimoCode（时值 mimo v2.5 免费）、本地 Qwen（图一乐）、讯飞星辰（时值 Qwen 系列免费）、opencode（免费模型）、AMD cloud（免费模型）、最近的 ZCode（周末 glm-5.3-flash）、以及全程在线的 DeepSeek（Chat 对话询问式"古法"）。做到现在，跟 NPGS 项目差距还是好大，作者牛逼哦！

因为开发过程多有不规范、不合适、不隐私的历史，所以决定新开仓库将代码公开——前文提及的旧仓库内容在本项目里是看不到的，见谅。
（完整的私有仓提交史已整理为 [foragent/HISTORY.md](foragent/HISTORY.md)，算是一份变相的开发年轮。）
