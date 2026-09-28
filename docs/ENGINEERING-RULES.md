# 工程铁律（记忆体 · 全局设置）

> **本文件是 NPC 与协作者的长期记忆体**：每次任务前必读，任务中持续遵守，不得绕过。
> 机器可读的阈值配置见 [`.cnb/quality-gate.yml`](../.cnb/quality-gate.yml)（单一事实来源）。
> 变更本文件或门禁阈值等同于变更**全局设置**，必须走 PR 并说明理由。

最后更新：2026-09-28

---

## 一、质量门禁（不可绕过）

### 1. 覆盖率红线

| 范围 | 分支覆盖率 | 单元（行）覆盖率 | 说明 |
| --- | --- | --- | --- |
| **后端** `backend/` | **≥ 75%** | **≥ 80%** | 分支覆盖率门禁**后端必须执行** |
| 前端 `frontend/` | ≥ 75% | ≥ 80% | 同标准执行，统计范围为可单测逻辑层 |

- **低于红线即阻断**，不允许 waive、不允许 `-DskipTests` 绕过。
- 后端由 JaCoCo `check` 在 `mvn verify` 阶段强制校验。
- 前端由 Vitest coverage `thresholds` 强制校验。
- CI 侧由 CNB 内置任务 `testing:coverage` 解析报告、上报徽章并在低于红线时阻断。

后端覆盖率统计范围（`backend/pom.xml` 的 JaCoCo `excludes`）：
`**/dto/**`、`**/entity/**`、`**/plugin/**`、`**/config/**`、`*Application`。

前端覆盖率统计范围（`frontend/vitest.config.js` 的 `include`）：
`src/{utils,stores,api,composables}/**/*.js`。
视图层 `.vue` 以交互与样式为主，交由 e2e / 人工验收，不计入单测红线。

### 2. 每次提交必须通过的检查

**代码稳定性（stability）**

- 后端单元测试全部通过：`cd backend && mvn -B verify`
- 前端单元测试全部通过：`cd frontend && npm run test:run`
- 前后端构建通过：`npm run build`、`mvn -DskipTests package`

**代码一致性（consistency）**

- 前端 ESLint 通过：`cd frontend && npm run lint`
- 覆盖率阈值达标（见上表）
- 提交信息遵循 [Conventional Commits](https://www.conventionalcommits.org/)：
  `type(scope): subject`，type ∈ `feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert`
- 保护分支必须通过状态检查才能合并

### 3. 本地自检（提 PR 前）

```bash
# 后端：单测 + 覆盖率门禁 + 构建
cd backend && mvn -B verify

# 前端：单测 + 覆盖率门禁
cd frontend && npm run test:coverage

# 前端：一致性
cd frontend && npm run lint
```

两项都通过后再推送，避免占用流水线资源。

### 4. 流水线运行环境（镜像必须自带工具链）

CI 脚本跑在容器里，**镜像是工具链的唯一来源**，不要依赖运行时现装：

| 任务 | 镜像 | 理由 |
| --- | --- | --- |
| 后端（测试 / 覆盖率 / 构建） | `maven:3.9-eclipse-temurin-21` | 自带 Maven + JDK 21，匹配 `backend/pom.xml` 的 Java 21 |
| 前端（测试 / 覆盖率 / lint / 构建） | `node:20` | 自带 Node.js / npm |

- **禁止**用 `cnbcool/default-dev-env` 跑 CI 脚本：它是云原生**开发**镜像，PATH 中**没有 `mvn`**，
  直接执行会以 `mvn: not found`（返回码 127）失败（历史上已踩坑）。
- 未显式声明 `image` 时使用缺省构建镜像，同样不保证含 Maven，因此**必须显式声明**。
- 不同镜像的任务容器之间**只共享 `CNB_BUILD_WORKSPACE`（/workspace）**，
  覆盖率报告等产物必须落在该目录内，否则后续 `testing:coverage` 任务读不到。

---

## 二、编码约定

- **不新增依赖前先评估**：优先复用既有能力（如 OIDC 接入用 JDK 内置 `HttpClient` + Jackson）。
- **新增业务逻辑必须同时提交单测**，单测与实现同一 PR，不留「后续补测」。
- **纯工具类 / 服务类优先做成可注入依赖**，避免静态单例导致无法单测。
- **提交前不删除、不放宽任何既有测试**；确需调整须在 PR 说明原因。
- **i18n 三语齐备**：新增文案需同时补 `zh-CN` / `en-US` / `ja-JP`。
- **CHANGELOG 必须同步**：功能、修复、破坏性变更都要记录。

---

## 三、门禁规则的变更流程

1. 在 Issue / PR 中写明**变更理由**与**影响面**（哪些仓库、哪些分支受影响）。
2. 同步更新本文件、[`.cnb/quality-gate.yml`](../.cnb/quality-gate.yml) 与 `CHANGELOG.md`，三者必须一致。
3. 由仓库管理员评审通过后合并。
4. 阈值变更后，存量未达标模块需在同 PR 内补齐或明确记录豁免范围。

> **记忆体如何生效**：门禁规则以本文件 + [`.cnb/quality-gate.yml`](../.cnb/quality-gate.yml)
> 作为仓库内的长期约束存在。NPC 每次执行任务时都会读取仓库内这两份文件，
> 因此规则对**每一次**提交、每一个 PR 持续生效，无需在对话中重复声明。
