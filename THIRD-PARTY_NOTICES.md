# Third-Party Notices / 三方出处与许可

本项目的部分代码与素材来自下列第三方项目，按其各自许可证使用与再分发。
本项目因引入 GPL-3.0 衍生内容（NPGS），**整体以 GNU GPL-3.0 发布**（见根目录 [`LICENSE`](LICENSE)）。

## NPGS — GNU GPL-3.0

- 项目：<https://github.com/baopinshui/NPGS>（作者 baopinshui）
- 使用范围：
  - `resources/shaders/kerr.frag` —— 克尔黑洞着色器，移植自其 `BlackHole_common.glsl`
    （Kerr–Schild 度规/测地线/吸积盘/多普勒红移/喷流/星空全套），并适配本项目的
    Vulkan 动态渲染架构（UBO 参数面、观者四维标架接线、prepass/composite 等）；
  - `resources/textures/skybox/` —— 来自其 `Universe0Skybox` 素材（6×1024²）；
  - `resources/textures/skybox_mountains_seas/` —— 来自其 `Antiverse0Skybox` 素材（6×2048²）；
  - 另：史瓦西管线 `blackhole.frag` 的参考实现为其知乎文章
    《从零开始搓一个黑洞——glsl编程实战》，文章着色器与 NPGS 同源（同作者历史代码）。

## vulkanbook — MIT License

- 项目：<https://github.com/lwjglgamedev/vulkanbook>（作者 Antonio Hernández Bejarano / lwjglgamedev）
- 使用范围：`vulkanb/eng` 引擎框架基础类（Device/SwapChain/Pipeline/CmdBuffer/
  DescAllocator/ShaderModule 等）与 GUI 渲染层（`eng/graph/gui/GuiRender.java`、
  `GuiUtils.java`、`gui_vtx.glsl`/`gui_frg.glsl`）移植自其书籍样例
  （appendix-01 及各章样例），并做了本项目化裁剪与修改。
- 许可证全文：

```text
MIT License

Copyright (c) 2025 Antonio Hernández Bejarano

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 运行时依赖

| 组件 | 许可证 | 来源 |
|---|---|---|
| LWJGL 3（含各平台 natives、lwjgl-stb、VMA 绑定） | BSD-3-Clause | <https://github.com/LWJGL/lwjgl3> |
| JOML（线性代数） | MIT | <https://github.com/JOML-CI/joml> |
| imgui-java（Dear ImGui 绑定） | MIT | <https://github.com/Spair/imgui-java> |
| tinylog（日志） | MIT | <https://tinylog.org/> |
| shaderc / glslang / SPIRV-Tools（经 LWJGL 绑定，运行时 GLSL→SPIR-V） | Apache-2.0（glslang 含 LLVM 例外） | <https://github.com/google/shaderc> |
| stb（stb_image，经 lwjgl-stb） | Public Domain / MIT | <https://github.com/nothings/stb> |
| Vulkan Memory Allocator（经 LWJGL 绑定） | MIT | <https://github.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator> |
| MoltenVK（仅 macOS 发行包内捆绑 `libMoltenVK.dylib`，以 loader-less 方式直连） | Apache-2.0 | <https://github.com/KhronosGroup/MoltenVK> |
