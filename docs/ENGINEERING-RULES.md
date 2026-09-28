# 工程铁律（记忆体 · 全局设置）

> **本文件是 NPC 与协作者的长期记忆体**：每次任务前必读，任务中持续遵守，不得绕过。
> 机器可读的阈值配置见 [`.cnb/quality-gate.yml`](../.cnb/quality-gate.yml)（单一事实来源）。
> 变更本文件或门禁阈值等同于变更**全局设置**，必须走 PR 并说明理由。

最后更新：2026-09-28

---

## 一、质量门禁（不可绕过）

### 0. 门禁状态：staged（分批推进期）

门禁分两个状态，**当前为 `staged`**，与 [`.cnb/quality-gate.yml`](../.cnb/quality-gate.yml) 的 `mode` 字段一一对应：

| 状态 | 含义 | 是否存在阻断 |
| --- | --- | --- |
| `staged`（当前） | 脚手架 + 单测骨架就位，存量覆盖率尚未达标，先只上报数据 | 前端强制；后端只设「不倒退」底线 |
| `enforced` | 全部红线正式生效 | 全部阻断，未达标不允许合并 |

为什么先用 `staged`：后端存量代码约 1.6 万行、约 800 个业务分支，覆盖率约 3.9% 分支 / 6.3% 行，
若立刻按 75% / 80% 卡死，**所有 PR（包括引入门禁本身的 PR）都会被阻断**，门禁无法合入。

`staged` 期间的**真实约束**（不是空转）：

- **前端**：`vitest.config.js` 的 `thresholds` 已按 75% / 80% 强制，未达标即失败（前端逻辑层已达标）
- **后端（整体）**：JaCoCo `check` 仍开启，`BUNDLE` 级阈值设为一组「不倒退底线」，
  覆盖率跌破底线即构建失败（改坏了已有覆盖会立刻暴露），底线值见 `backend/pom.xml` 的 `coverage.*.minimum`
- **后端（已达标模块）**：对已补齐单测、达到红线的类，按 **`CLASS` 级规则以 75% / 80% 红线强制**
  （当前名单见 `backend/pom.xml` 的 `jacoco check` → `element=CLASS` 的 `includes`）。
  即 **逐步补测、逐步上锁**：每补齐一个模块，就把它加进名单，红线随即对该模块生效
- **CI**：`testing:coverage` 在两个阶段都上报全量 + 增量覆盖率数据与徽章，曲线可见、趋势可查

### 1. 覆盖率红线（最终目标）

| 范围 | 分支覆盖率 | 单元（行）覆盖率 | 说明 | 当前状态 |
| --- | --- | --- | --- | --- |
| **后端** `backend/` | **≥ 75%** | **≥ 80%** | 分支覆盖率门禁**后端必须执行** | ⏳ staged（整体）；已达标模块按红线强制 |
| ↳ `service.OidcService` | ≥ 75% | ≥ 80% | 首个达标模块（94.9% 行 / 78.8% 分支） | ✅ 已按红线强制 |
| 前端 `frontend/` | ≥ 75% | ≥ 80% | 同标准执行，统计范围为可单测逻辑层 | ✅ 已强制生效 |

- **达到 `enforced` 后低于红线即阻断**，不允许 waive、不允许 `-DskipTests` 绕过。
- 后端由 JaCoCo `check` 在 `mvn verify` 阶段强制校验。
- 前端由 Vitest coverage `thresholds` 强制校验。
- CI 侧由 CNB 内置任务 `testing:coverage` 解析报告、上报徽章；`enforced` 时设 `lines` / `diffLines` 阈值阻断。

**逐模块上锁（progressive enforcement）**

后端存量代码量大，改用「**补一个模块、锁一个模块**」的推进方式，避免长期停留在只看不卡：

1. 选一个模块（优先级：service → controller → filter → handler），补齐单测
2. 确认该模块 `mvn verify` 后行 ≥ 80% / 分支 ≥ 75%
3. 把该模块（或其类）加入 `backend/pom.xml` → `jacoco check` 的 `CLASS` 级 `includes` 名单
4. 模块一旦入列，**后续任何改动跌破红线都会立刻构建失败**
5. 全部模块入列后，按下一节流程把整体切到 `enforced`，移除分模块名单（整体红线即覆盖全部）

已入列模块：

| 模块 | 行覆盖 | 分支覆盖 | 入列版本 |
| --- | --- | --- | --- |
| `service.OidcService`（含内部类） | 94.9% | 78.8% | Unreleased |

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

### 3. 如何从 staged 切到 enforced

1. 按模块补齐后端单测（priority：service → controller → filter → handler），
   使后端整体达到 **分支 ≥ 75% / 行 ≥ 80%**。
2. 改 `.cnb/quality-gate.yml`：`mode: enforced`，`coverage.branch.enforced` /
   `coverage.line.enforced` 置 `true`，`coverage.scope.backend.enforced` 置 `true`。
3. 改 `.cnb.yml`：两处 `backend-coverage` / `frontend-coverage` 任务加回
   `lines: 80` 与 `diffLines: 80`（PR 侧）——即取消本文件的 staged 注释段。
4. 改 `backend/pom.xml`：`coverage.branch.minimum` → `${coverage.branch.target}`、
   `coverage.line.minimum` → `${coverage.line.target}`（即 0.75 / 0.80）。
5. 同步本文件、`README.md`、`CHANGELOG.md`，走 PR 评审。

> **判断依据只有一个**：`mvn -B verify` 打印的 jacoco check 结果。
> 未达到 75% / 80% 前不要改这些值，否则流水线会红。

### 4. 本地自检（提 PR 前）

```bash
# 后端：单测 + 覆盖率门禁 + 构建
cd backend && mvn -B verify

# 前端：单测 + 覆盖率门禁
cd frontend && npm run test:coverage

# 前端：一致性
cd frontend && npm run lint
```

两项都通过后再推送，避免占用流水线资源。

### 5. 流水线运行环境（镜像必须自带工具链）

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

> **记忆体如何生效（务必看准）**：记忆体就是**本文件**（`docs/ENGINEERING-RULES.md`）这类仓库内
> 可读文档。NPC 每次执行任务时会读取仓库内的文档，因此规则对**每一次**提交持续生效。
>
> [`.cnb/quality-gate.yml`](../.cnb/quality-gate.yml) 是**机器可读的阈值单一来源**，但它
> **不会被自动注入 NPC 上下文**：CNB 的 `include` / `imports` 只支持 YAML / JSON / 证书 /
> `key=value` 文本，把整篇配置塞进提示词并不是平台能力。
> 所以「阈值读哪个文件」这件事必须写进本文件——NPC 读了本文件，就知道要去读哪份配置以及当前是哪种状态。
