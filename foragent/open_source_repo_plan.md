# 开源仓库与同步约定（blackhole-open）

> **状态(2026-09-26):已上线。** 公开仓库已建立并完成首次推送（初始提交 + 用户后续 CI 提交）。
> 本文档记录两仓库的角色分工、同步工作流与硬性约束——**约束条款为长期有效约定，后续会话
> （含 agent 会话）执行同步前必读**。
> **修订(2026-09-26):** agent 同步改走**新分支**（不碰 main，合并主线由用户执行）；CI 开放
> agent 按需修改；分支规则同样约束用户在开源仓内使用 agent 的开发场景。

---

## 一、仓库拓扑

| 仓库 | 远端 / 位置 | 角色 | 历史 |
|---|---|---|---|
| **blackhole**（本仓库） | 私有 GitHub 仓（远端 URL 略）+ 本机 `D:\CodingSpace\IdeaProjects\blackhole` | **开发主战场**：全部功能开发、计划书、完整历史档案（含已移除的 zhihu 全文等历史） | 全量 |
| **blackhole-open** | `git@github.com:PreSchoolller/blackhole-open.git`（分支 `main`）+ 本机 `D:\CodingSpace\IdeaProjects\blackhole-open` | **公开镜像**：干净单根历史起步，面向公众 | 从 2026-09-26 状态重新开始，不追溯 |

- 许可：GPL-3.0（因 kerr.frag 与天空盒素材移植自 NPGS）；出处与义务见公开仓
  `LICENSE` / `THIRD-PARTY_NOTICES.md` / README「许可证与致谢」「碎碎念」两节；

## 二、同步工作流（约定）

1. **触发**：本仓库有**大更新**（新特性/修复批次/文档批次）且**经用户同意**后，方可执行同步；
   平时不要主动同步；
2. **步骤**（在用户同意后由 agent 执行，或用户自行执行）：
   ```bash
   # ① 导出覆盖（只增改、不删除开源仓已有文件）
   git -C D:/CodingSpace/IdeaProjects/blackhole archive HEAD | tar -x -C D:/CodingSpace/IdeaProjects/blackhole-open
   # ② agent 只落新分支：每次同步新建独立分支（命名 sync/<日期>-<主题>），永不直接碰 main
   cd D:/CodingSpace/IdeaProjects/blackhole-open
   git switch -c sync/<日期>-<主题>
   git add -A && git commit
   # ③ 推送同步分支、合并回 main、推送 main：**全部由用户本人执行**，agent 不代推不代合并
   ```
3. **同步后核对**：`git status` 干净；`.github/workflows/release.yml` 仍在位；
   `git log --format="%ae"` 全部为 noreply 身份。

## 三、硬性约束（红线）

1. **agent 在 blackhole-open 的一切写操作只落新分支**——同步也好、在开源仓内的开发也好，
   永不直接提交/推送到 `main`；推送分支、合并主线、上线均由用户本人执行。本条同样约束
   用户在 blackhole-open 内使用 agent 的场景（该仓由用户 + agent 共同开发）；
2. **`.github/` CI 配置只存在于 blackhole-open**（含后续迭代），**严禁同步进
   本仓库**，也严禁在同步中删除或覆盖它——archive 覆盖式导出天然不触碰未包含的文件，
   因此**永不**对 blackhole-open 工作树执行 `git clean` / `checkout .` / `reset --hard`；
3. **agent 不代推不代合并**：见第二节步骤 ③ 与红线 1；
4. **公开仓历史只追加**：已推送的提交永不 amend / rebase / filter；
5. 本仓库后续开发中，不得再引入：脏话命名、个人绝对路径新增引用
   （存量计划书里的 `D:\...` 引用已用户确认保留）；`if_dopplerI/T` 之类未消费字段
   不受此限（代码内容随同步走）。

## 四、开源仓 CI（用户与 agent 共同维护，agent 可按需修改）

- `.github/workflows/release.yml`：`v*` tag 推送触发 + 手动 workflow_dispatch；
  三平台 matrix（windows-x64 / linux-x64 / macos-arm64，temurin 17 + maven 缓存）；
  jdeps+jlink 裁剪运行时 + jpackage app-image，各平台 zip 汇总发 Release，
  另附 Linux 构建的跨平台 fat jar（BlackHole-universal.jar）；资产名拼入 ref 名
  （tag 斜杠转横杠）。提交链：d24e718 → 6cc3fda → 5023cf3。
- jar 模式运行依赖源码哈希缓存键的 spv 失效策略（a30344a）与 `src/test` 的
  SpvCheck/SpvWrite 工具，属 CI 链路一部分，同步时随代码自然走。
- 用户有需求时 agent 可直接修改 CI（属共同维护范畴），改动经第二节分支流程进入公开仓；
  agent 亦可主动提出 CI 改进建议，采纳与否由用户决定。

## 五、角色备忘

- 本仓库的 foragent/ 计划书随同步进入公开仓（README 已注明"非运行所需"）；
- 后续新计划书的"同步到开源仓"默认跟随第二节流程，无需再次征求删除意见——
  需要征询的是**代码大更新**的同步时机。
