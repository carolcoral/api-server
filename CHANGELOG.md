# 版本变更说明

## v2.4.0 (2026-07-21)

> AI 服务管理 · 用户自助订阅 · 代理请求录制回放 · 权限扫描增强 · 项目品牌升级 · 性能优化。

### 🤖 AI 服务管理平台

- **服务商管理**：独立页面管理 AI 服务商，支持创建/编辑/删除（级联删除关联模型和订阅），API 类型与认证方式配置
- **模型管理**：按服务商查看模型列表，支持手动添加、批量远程导入（调用 `/v1/models` 端点自动拉取），价格/Token 上限/流式/自动模式等参数配置，模型健康检查（延迟探测 + 在线状态）
- **订阅管理**：用户-模型订阅关系，支持优先级/权重/回落启用/MaxTokens 等策略配置
- **额度管理**：按用户/模型维度设置请求配额与周期限制
- **API Key 管理**：用户级 API Key 生成与管理，支持绑定服务商
- **调用日志**：全量 AI 请求日志追踪，记录 Token 用量/延迟/费用/回落链/错误信息，支持分页浏览
- **统计看板**：模型总数/订阅数/API Key 数/今日调用 四项指标，页面横幅展示总调用次数
- 新增权限：`ai-service:view`（页面访问）、`ai-subscription:view`（订阅页面）、6 项 AI 服务管理子权限

### 🧩 AI 用户自助订阅

- **订阅管理页面**：普通用户可自行订阅已启用的模型，配置优先级与权重，支持回落订阅
- **个人 API Key**：用户可创建/查看/删除个人 API Key
- **用量统计**：查看个人 AI 调用历史与 Token 消耗
- **自动模式订阅**：支持后端自动选择最优模型，订阅互斥逻辑防止冲突
- 新增权限：`ai-subscription:subscribe`、`ai-subscription:key-manage` 用户自助权限

### 📡 AI 代理统一接入

- **OpenAI 兼容代理**：`/api/ai/v1/chat/completions` 端点完全兼容 OpenAI Chat API 格式
- **智能模型选择**：`AiModelSelector` 根据用户订阅优先级/权重/健康状态自动选择最佳模型，支持故障回落链
- **SSE 流式实时推送**：优化流式响应传输，减少缓冲延迟，支持中断与错误恢复
- **LAZY 加载安全**：`JacksonConfig` 禁用空 Bean 序列化失败，避免 Hibernate 代理序列化异常；`AiProxyExceptionHandler` 全局异常处理

### 🔄 请求录制回放

- **透明代理录制**：`HttpProxyController` 捕获 HTTP 请求/响应并持久化为 `ProxyRecord`
- **录制回放页面**：`RecordReplay.vue` 支持查看/搜索/回放已录制的请求，对比响应差异
- **代理记录管理**：完整的录制数据 CRUD，支持按路径/状态码/时间范围筛选

### 🔐 权限系统增强

- **权限扫描器**：`PermissionScanner` 启动时自动扫描所有 `@PreAuthorize` 注解，按分组级联展示可用权限，创建权限时联动过滤
- **权限类型自动识别**：`:view` 后缀 → 页面权限，其他 → 按钮权限，用户无需手动选择
- **分组级联禁用**：分组下所有权限均已添加时该分组自动隐藏
- **菜单权限修复**：AI 订阅菜单从硬编码改为 `v-if` 权限判断，无权限用户不可见
- 新增 `record-replay:view`、`record-replay:replay` 录制回放权限

### 🏗️ 项目品牌升级

- **项目重命名**：`MockServer` → `API Server`，包名 `com.carolcoral.mockserver` → `com.carolcoral.apiserver`
- **数据库重命名**：默认数据库文件 `mock-server.db` → `api-server.db`
- **启动类重命名**：`MockServerApplication` → `ApiServerApplication`，OpenAPI 文档标题同步更新

### ⚡ 性能优化

- **SQLite WAL 模式**：启用 WAL（Write-Ahead Logging）模式，提升并发读写性能，减少锁竞争
- **自动测试工具升级**：`auto_test_tool/setup.sh` 优化环境配置流程，减少构建时间

### 🐛 修复

- **删除服务商级联清理**：修复删除服务商后模型和订阅成为孤儿数据的问题，`deleteProvider` 增加级联删除
- **侧边栏权限显示**：AI 订阅菜单添加 `v-if="hasPermission('ai-subscription:view')"` 修复无权限用户可见菜单但点击无反应
- **统计卡片准确性**：清理孤儿模型数据（`provider_id` 指向不存在服务商），统计数字与实际列表一致
- 模板引擎增加容错处理，避免异常渲染导致的服务中断

### 📝 升级说明

> ⚠️ **v2.3.3 → v2.4.0 数据库变更**：`DatabaseMigration` 启动时自动执行。本次新增 7 张表及 18 条权限记录。若自动迁移失败，请手动执行：

```sql
-- ============================================
-- 从 v2.3.3 升级到 v2.4.0
-- ============================================

-- 1. 创建 AI 服务商表
CREATE TABLE IF NOT EXISTS t_ai_provider (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100) NOT NULL UNIQUE,
    base_url VARCHAR(500) NOT NULL,
    api_key VARCHAR(500) NOT NULL,
    api_type VARCHAR(50),
    auth_type VARCHAR(20),
    description VARCHAR(500),
    status BOOLEAN NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL
);

-- 2. 创建 AI 模型表
CREATE TABLE IF NOT EXISTS t_ai_model (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    provider_id BIGINT NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(100),
    input_price DOUBLE,
    output_price DOUBLE,
    max_tokens INTEGER,
    supports_stream BOOLEAN NOT NULL DEFAULT 1,
    health_status VARCHAR(20) DEFAULT 'online',
    last_health_check DATETIME,
    cooldown_until DATETIME,
    consecutive_failures INTEGER NOT NULL DEFAULT 0,
    avg_latency_ms BIGINT,
    auto_mode BOOLEAN NOT NULL DEFAULT 0,
    status BOOLEAN NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    FOREIGN KEY (provider_id) REFERENCES t_ai_provider(id)
);

-- 3. 创建 AI 订阅表
CREATE TABLE IF NOT EXISTS t_ai_subscription (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id BIGINT NOT NULL,
    model_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    priority INTEGER NOT NULL DEFAULT 0,
    weight INTEGER NOT NULL DEFAULT 1,
    max_tokens_per_request INTEGER,
    fallback_enabled BOOLEAN NOT NULL DEFAULT 0,
    tags VARCHAR(200),
    status BOOLEAN NOT NULL DEFAULT 1,
    expire_time DATETIME,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ai_sub_user ON t_ai_subscription(user_id);
CREATE INDEX IF NOT EXISTS idx_ai_sub_model ON t_ai_subscription(model_id);

-- 4. 创建 AI 额度表
CREATE TABLE IF NOT EXISTS t_ai_quota (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id BIGINT NOT NULL,
    model_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    period_limit BIGINT DEFAULT 0,
    period_type VARCHAR(10) DEFAULT 'daily',
    used_count BIGINT NOT NULL DEFAULT 0,
    reset_time DATETIME,
    status BOOLEAN NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL
);

-- 5. 创建 AI API Key 表
CREATE TABLE IF NOT EXISTS t_ai_api_key (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id BIGINT NOT NULL,
    provider_id BIGINT,
    key_name VARCHAR(100),
    api_key VARCHAR(500) NOT NULL UNIQUE,
    status BOOLEAN NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL
);

-- 6. 创建 AI 用量日志表
CREATE TABLE IF NOT EXISTS t_ai_usage_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id BIGINT NOT NULL,
    provider_id BIGINT,
    model_id BIGINT,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_tokens INTEGER,
    latency_ms BIGINT,
    cost FLOAT,
    status_code INTEGER,
    fallback_from BIGINT,
    error_msg VARCHAR(500),
    request_body TEXT,
    response_body TEXT,
    create_time DATETIME NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ai_usage_user ON t_ai_usage_log(user_id);
CREATE INDEX IF NOT EXISTS idx_ai_usage_time ON t_ai_usage_log(create_time);

-- 7. 创建代理录制记录表
CREATE TABLE IF NOT EXISTS t_proxy_record (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    method VARCHAR(10),
    path VARCHAR(500),
    request_headers TEXT,
    request_body TEXT,
    response_status INTEGER,
    response_headers TEXT,
    response_body TEXT,
    duration_ms BIGINT,
    create_time DATETIME NOT NULL
);

-- 8. 新增权限记录（18 条）
INSERT OR IGNORE INTO t_permission (name, code, group_name, type, sort_order, create_time, update_time) VALUES
('AI服务管理-页面访问', 'ai-service:view', '系统管理', 'PAGE', 54, datetime('now'), datetime('now')),
('AI服务管理-创建服务商', 'ai-service:create', '系统管理', 'BUTTON', 55, datetime('now'), datetime('now')),
('AI服务管理-编辑服务商', 'ai-service:edit', '系统管理', 'BUTTON', 56, datetime('now'), datetime('now')),
('AI服务管理-删除服务商', 'ai-service:delete', '系统管理', 'BUTTON', 57, datetime('now'), datetime('now')),
('AI服务管理-管理模型', 'ai-service:models', '系统管理', 'BUTTON', 58, datetime('now'), datetime('now')),
('AI服务管理-管理订阅', 'ai-service:subscriptions', '系统管理', 'BUTTON', 59, datetime('now'), datetime('now')),
('AI服务管理-管理额度', 'ai-service:quotas', '系统管理', 'BUTTON', 60, datetime('now'), datetime('now')),
('AI订阅-页面访问', 'ai-subscription:view', 'AI用户自助', 'PAGE', 118, datetime('now'), datetime('now')),
('AI订阅-订阅管理', 'ai-subscription:subscribe', 'AI用户自助', 'BUTTON', 119, datetime('now'), datetime('now')),
('AI订阅-密钥管理', 'ai-subscription:key-manage', 'AI用户自助', 'BUTTON', 120, datetime('now'), datetime('now')),
('录制回放-页面访问', 'record-replay:view', '业务管理', 'PAGE', 52, datetime('now'), datetime('now')),
('录制回放-执行回放', 'record-replay:replay', '业务管理', 'BUTTON', 53, datetime('now'), datetime('now'));

-- 9. 清理孤儿模型数据（若存在）
DELETE FROM t_ai_model WHERE provider_id NOT IN (SELECT id FROM t_ai_provider);
```

> 💡 MySQL 用户：`INSERT OR IGNORE` → `INSERT IGNORE`，`datetime('now')` → `NOW()`
> PostgreSQL 用户：`INSERT OR IGNORE` → `INSERT ... ON CONFLICT DO NOTHING`，`datetime('now')` → `NOW()`

> Mock 调试面板 · 多 AI 配置切换 · 权限体系全覆盖 · 运维监控 · 权限定义管理 · Actuator 安全加固 · 移动端适配。

### 🔍 Mock 调试面板
- **请求日志追踪**：实时记录每次 Mock 请求的完整信息（路径/方法/耗时/状态码/响应大小），支持分页、搜索与清空
- **延迟分布统计**：可视化展示请求延迟分布，快速定位性能瓶颈
- **后端请求日志**：新增 `t_request_log` 表持久化所有 Mock 调用记录，`RequestLogService` 异步写入不影响请求响应
- 新增 `debug-panel:view` 权限控制

### 🤖 AI 配置多服务商切换
- **多配置管理**：AI 设置页全面重构，支持同时配置多个 LLM 服务商，每个独立启用/禁用
- **默认配置**：新增 `is_default` 字段，Chat 页面自动选择首个启用的默认配置，支持一键设为默认
- **模型选择器**：对话页面新增配置/模型下拉切换，无需返回设置页即可在已启用服务商间切换
- **自动修复**：启动时自动确保至少存在一个默认配置，避免空配置导致对话不可用

### 🔐 权限体系全覆盖
- **AI 设置子权限**：新增 `ai-settings:create/edit/delete/toggle/set-default/test` 6 项按钮级权限，前后端统一控制按钮显隐
- **系统设置子权限**：新增 `settings:basic/security/jwt/mock/announcement/system/footer/registration` 8 项按钮级权限，菜单项按权限动态显隐
- **运维监控权限**：新增 `ops:view/backup/restore` 3 项权限，备份恢复操作可独立分配
- **跨项目查看**：新增 `project:view_all` `api:view_all` `code-template:view_all`，管理员可见所有项目数据
- **权限自动同步**：`DatabaseMigration` 启动时将新增子权限自动赋予已拥有父权限的角色，无需手动配置
- **权限分组修正**：用户管理权限从"系统管理"移至"权限管理"分组，侧边栏菜单结构更清晰

### 🛠️ 权限定义管理
- **权限 CRUD**：新增权限定义管理页面（Permissions.vue 重构为双标签页），支持创建/编辑/删除权限定义
- **后端增强**：`PermissionService` 新增 `createPermission` / `updatePermission` / `deletePermission`，删除时自动清理角色关联
- **Repository 增强**：`RolePermissionRepository` 新增 `deleteByPermissionId` / `existsByPermissionId` 方法

### 📊 运维与监控
- **运维监控页面**：新增 OpsMonitor.vue，涵盖健康检查仪表盘、数据概览、Prometheus 指标浏览器（表格/文本双视图）
- **系统备份恢复**：支持一键导出完整 Mock 配置（项目/接口/响应/模板），JSON 格式，支持合并/替换两种恢复模式
- **Prometheus 指标**：暴露 `mock_requests_total` / `mock_request_duration` 等指标端点，`application.yml` 启用 Prometheus 导出
- **健康检查**：数据库连接池纳入健康检查，磁盘健康指示器基于实际使用率动态计算，`show-details: always`

### 🔒 安全加固
- **Actuator 端点认证**：`/actuator/**` 从公开访问改为需登录认证，移除 JWT 过滤器和 SecurityConfig 中的公开白名单
- **前端 Axios 适配**：Actuator 响应（非统一格式）直接透传原始数据，避免拦截器误解析
- **SPA 路由扩展**：新增 `/ops-monitor` SPA 路由转发，支持直接访问运维页面

### 🎨 UI / UX
- **角色铭牌增强**：右上角用户区域新增角色铭牌，流光科幻特效渐变动画，优先展示登录响应中的 `roleName`
- **外部资源离线化**：用户头像改为本地 SVG 生成，登录页背景内置，移除对 Cravatar / Bing 的外部依赖
- **移动端适配**：侧边栏、页面布局全面响应式优化，小屏幕下自动折叠菜单
- **运维监控增强**：数据库卡片 UP 下方显示当前数据库类型，磁盘空间统一以 GB 为单位展示

### 🐛 修复
- `run.sh` 增强端口清理机制，修复端口变更后 PID 文件导致的启动检测失败
- `AiConfigController` 修复无默认配置时对话页面不可用的问题，子权限注解精确匹配（create/toggle/delete/set-default/test 各归其位）
- `DashboardLayout` 菜单按权限分组正确显隐，修复权限修正导致的侧边栏空白
- 首页路由兜底逻辑扩展，优先匹配 ops-monitor / settings 权限再跳转

### 📝 升级说明

> ⚠️ **v2.3.2 → v2.3.3 数据库变更**：`DatabaseMigration` 启动时自动执行，需新增 `t_request_log` 表（Hibernate `ddl-auto: update` 自动创建）及 21 条权限记录。若自动迁移失败，请手动执行：

```sql
-- ============================================
-- 从 v2.3.2 升级到 v2.3.3
-- ============================================

-- 1. 创建请求日志表（SQLite；MySQL/PostgreSQL 由 Hibernate 自动创建，可跳过）
CREATE TABLE IF NOT EXISTS t_request_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    project_code VARCHAR(100),
    api_path VARCHAR(500),
    method VARCHAR(10),
    status_code INTEGER,
    response_time_ms INTEGER,
    response_size INTEGER,
    client_ip VARCHAR(50),
    username VARCHAR(100),
    request_time DATETIME NOT NULL,
    success BOOLEAN DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_request_log_time ON t_request_log(request_time);
CREATE INDEX IF NOT EXISTS idx_request_log_project ON t_request_log(project_code);

-- 2. 新增权限记录（21 条，SQLite 语法，MySQL/PGSQL 需调整 NOW() 和 INSERT 语法）
INSERT OR IGNORE INTO t_permission (name, code, group_name, type, sort_order, create_time, update_time) VALUES
('项目管理-查看全部', 'project:view_all', '业务管理', 'BUTTON', 14, datetime('now'), datetime('now')),
('接口管理-查看全部', 'api:view_all', '业务管理', 'BUTTON', 24, datetime('now'), datetime('now')),
('代码模板-查看全部', 'code-template:view_all', '业务管理', 'BUTTON', 34, datetime('now'), datetime('now')),
('调试面板-页面访问', 'debug-panel:view', '数据统计', 'PAGE', 51, datetime('now'), datetime('now')),
('AI设置-创建', 'ai-settings:create', '系统管理', 'BUTTON', 91, datetime('now'), datetime('now')),
('AI设置-编辑', 'ai-settings:edit', '系统管理', 'BUTTON', 92, datetime('now'), datetime('now')),
('AI设置-删除', 'ai-settings:delete', '系统管理', 'BUTTON', 93, datetime('now'), datetime('now')),
('AI设置-启用禁用', 'ai-settings:toggle', '系统管理', 'BUTTON', 94, datetime('now'), datetime('now')),
('AI设置-设置默认', 'ai-settings:set-default', '系统管理', 'BUTTON', 95, datetime('now'), datetime('now')),
('AI设置-测试连通性', 'ai-settings:test', '系统管理', 'BUTTON', 96, datetime('now'), datetime('now')),
('系统设置-基础设置', 'settings:basic', '系统管理', 'BUTTON', 110, datetime('now'), datetime('now')),
('系统设置-安全配置', 'settings:security', '系统管理', 'BUTTON', 111, datetime('now'), datetime('now')),
('系统设置-JWT配置', 'settings:jwt', '系统管理', 'BUTTON', 112, datetime('now'), datetime('now')),
('系统设置-Mock配置', 'settings:mock', '系统管理', 'BUTTON', 113, datetime('now'), datetime('now')),
('系统设置-公告管理', 'settings:announcement', '系统管理', 'BUTTON', 114, datetime('now'), datetime('now')),
('系统设置-系统信息', 'settings:system', '系统管理', 'PAGE', 115, datetime('now'), datetime('now')),
('系统设置-页脚设置', 'settings:footer', '系统管理', 'BUTTON', 116, datetime('now'), datetime('now')),
('系统设置-注册设置', 'settings:registration', '系统管理', 'BUTTON', 117, datetime('now'), datetime('now')),
('运维与监控-页面访问', 'ops:view', '系统管理', 'PAGE', 101, datetime('now'), datetime('now')),
('运维与监控-备份导出', 'ops:backup', '系统管理', 'BUTTON', 102, datetime('now'), datetime('now')),
('运维与监控-数据恢复', 'ops:restore', '系统管理', 'BUTTON', 103, datetime('now'), datetime('now'));

-- 3. 将用户管理权限分组从"系统管理"修正为"权限管理"
UPDATE t_permission SET group_name = '权限管理' WHERE code IN ('user:view', 'user:create', 'user:edit', 'user:delete') AND group_name = '系统管理';

-- 4. 子权限自动同步（DatabaseMigration 已自动执行，可跳过）
-- 系统设置子权限同步到拥有 settings:view 的角色
-- AI 设置子权限同步到拥有 ai-settings:view 的角色
-- 运维监控权限同步到拥有 settings:view 的角色
```

> 💡 MySQL 用户：`INSERT OR IGNORE` → `INSERT IGNORE`，`datetime('now')` → `NOW()`
> PostgreSQL 用户：`INSERT OR IGNORE` → `INSERT ... ON CONFLICT DO NOTHING`，`datetime('now')` → `NOW()`

---


## v2.3.2 (2026-07-05)

> Mock 模板引擎、项目导入导出、访问页重构与国际化生产兼容、性能优化。

### 🎨 Mock 模板引擎
- **随机数据生成**：响应体中支持 `{{name()}}` `{{email()}}` `{{phone()}}` `{{idCard()}}` `{{uuid()}}` 等模板函数，每次请求生成随机中文数据
- **模板助手面板**：响应编辑框下方展示可用函数标签，点击自动插入光标位置
- **模板预览**：一键预览模板渲染结果，支持 JSON 格式化显示
- **模板函数说明弹窗**：分类展示所有可用函数及说明
- 新增 `api:template_engine` 权限控制，可按角色分配

### 📦 项目数据导入导出
- **项目导出**：导出完整项目数据（含接口名称/路径/方法/响应报文/请求参数等）为 JSON 文件
- **项目导入**：上传 JSON 文件还原项目，支持增量导入（追加）和覆盖导入（替换）两种模式
- **冲突检测**：项目编码重复时提示选择导入模式
- **Swagger 导出**：支持 Swagger 2.0 / OpenAPI 3.0 两种格式导出项目接口
- 新增 `project:import_swagger` `project:export_swagger` `project:export_data` `project:import_data` 权限

### 🐛 响应管理修复
- **默认响应唯一性**：同接口同状态码下仅允许一个默认响应，设置新默认时自动取消旧默认
- **随机返回修复**：开启随机返回后权重逻辑优先于默认响应，候选池扩展为所有启用响应，确保多响应场景下随机生效
- **空项目搜索修复**：用户无任何可访问项目时 API 搜索直接返回空结果，避免 NullPointerException

### 🌐 访问页重构

- **i18n 生产兼容**：欢迎页所有国际化 key 添加 `ms_` 前缀，特性卡片从 `computed(() => [t()])` 改为静态数组 + `$t()` 模板调用，解决 Terser 生产构建下 `t()` 返回空字符串问题
- **模板语法转义**：`{{name()}}` 等模板引擎占位符改用命名插值 `{open}name(){close}`，避免 Vue I18n 解析为嵌套占位符报错
- **性能优化**：移除 Canvas 粒子动效；背景光球从 4 个减至 2 个，模糊半径 100px→60px；导航栏 `backdrop-filter` 降至 8px；标题渐变动画周期延长；`transition:all` 改为精确属性
- **技术栈区域移除**：访问页不再展示技术栈标签

### 🔧 引导页修复

- Step 6 模板中 `t()` 统一改为 `$t()`，确保生产构建正常渲染
- `step6UrlDesc` 中 `{项目编码}` `{接口路径}` 等文本花括号改用命名插值转义

### ⚡ 部署优化
- **Docker 构建加速**：基础镜像预装 Maven + Node.js + npm，跳过 `setup-env.sh` 环境安装
- **Maven 阿里云镜像**：容器内自动配置阿里云 Maven 中央仓库，加速依赖下载
- **JAR 启动参数**：`run.sh` 透传 admin/JWT/Swagger 系统属性，`StartupConfig` 统一读取
- **离线环境图标**：`build-all-in-one.sh` 构建时下载 Shields.io 徽章为本地 SVG，内网环境正常显示

### 📝 升级说明

> ⚠️ **v2.3.1 → v2.3.2 数据库变更**：`DatabaseMigration` 启动时自动执行，仅新增 5 条权限记录，无表结构变更。若自动迁移失败，请手动执行：

```sql
-- ============================================
-- 从 v2.3.1 升级到 v2.3.2
-- ============================================
-- 新增权限（仅 SQLite，PostgreSQL/MySQL 请在 t_permission 表中对应插入）

INSERT OR IGNORE INTO t_permission (name, code, group_name, type, sort_order, create_time, update_time) VALUES
('项目管理-导入Swagger', 'project:import_swagger', '业务管理', 'BUTTON', 15, datetime('now'), datetime('now')),
('项目管理-导出Swagger', 'project:export_swagger', '业务管理', 'BUTTON', 16, datetime('now'), datetime('now')),
('项目管理-导出项目数据', 'project:export_data', '业务管理', 'BUTTON', 17, datetime('now'), datetime('now')),
('项目管理-导入项目数据', 'project:import_data', '业务管理', 'BUTTON', 18, datetime('now'), datetime('now')),
('接口管理-模板引擎', 'api:template_engine', '业务管理', 'BUTTON', 25, datetime('now'), datetime('now'));
```

---

## v2.3.1 (2026-06-30)

> 多数据源支持、自动化测试框架、AI 对话检索增强与 UI 优化。

### 🗄️ 多数据源支持
- **数据库抽象层**：新增 `DatabaseDialectProvider`，统一 SQLite / PostgreSQL / MySQL 方言差异（ID 自增 / 布尔值 / 日期函数 / UPSERT 语法）
- **动态切换**：`.env` 设置 `DB_TYPE` 即可切换数据库，无须修改代码，Spring Profile 自动装配
- **DatabaseMigration 全面重构**：所有建表/插入语句改为方言感知，自动适配三种数据库
- **新增 MySQL / PostgreSQL 连接配置**：`datasources/docker-compose.yml` 提供 Docker 环境
- 统计 SQL 全面改写，移除硬编码 `strftime()`，改用 `DatabaseDialectProvider.formatEpochMillisToDate()`
- JPA `ddl-auto: update` 与 Hibernate 方言协同，MySQL/PGSQL 由 Hibernate 处理 DDL，SQLite 由迁移脚本处理

### 🧪 自动化测试框架
- **全新 `auto_test_tool/` 模块**：Python 驱动的全自动测试工具，覆盖 6 大领域、66 个测试用例
- **测试领域**：AI 对话流式/多模型/智能建议/代码模板/邮件生成 · 页面功能 37 项 CRUD/搜索/分页 · RBAC 角色权限体系 · 安全漏洞扫描 · 页面访问权限 · Swagger 导入冲突检测
- **核心组件**：`TestRunner` 编排引擎 · `ReportGenerator` HTML/Markdown 双格式报告 · `AuthManager` 多用户认证 · `ConfigLoader` YAML 配置驱动 · `AIModelManager` 多模型轮换
- **CI 友好**：`setup.sh` 自动环境配置 · `run_test.sh` 一键执行 · `activate.sh` 虚拟环境激活
- 支持并行测试、指数退避重试、上下文管理器自动清理

### 🤖 AI 对话增强
- **动态文档检索**：根据用户提问实时检索 README / CHANGELOG 相关段落注入系统提示词，命中文档则优先引用，未命中则使用通用知识回答，不再直接阻断
- **代码高亮主题**：从 `atom-one-dark` 升级为 `github-dark-dimmed`，与 GitHub 深色模式配色一致
- 修复代码块语法高亮丢失问题：重构 `wrapCodeBlocks` 逻辑，保留 `hljs` 类名与 token 标签
- 修复 `color: inherit` 导致普通文本在深色背景上不可见
- 对话建议问题预缓存加速冷启动

### 🐛 修复
- **项目删除**：删除项目前清理 `t_project_member` 孤儿记录，修复 SQLite rowid 复用导致的 `UNIQUE constraint failed` 错误
- **Swagger 导入**：修复项目内接口重复性判断异常
- 测试框架解包异常（`ValueError: too many values to unpack`）
- 新增 `/forgot-password` SPA 路由与静态资源转发

### 📝 升级说明

> ⚠️ **v2.3.0 → v2.3.1 数据库变更**：本版本**无新增表结构**，主要重构为多数据源兼容。`DatabaseMigration` 启动时自动执行，所有现有迁移兼容 SQLite / PostgreSQL / MySQL。

若需要在已有 SQLite 基础上切换为 PostgreSQL 或 MySQL：

```sql
-- ============================================
-- 从 SQLite 迁移到 PostgreSQL / MySQL
-- ============================================

-- 1. 导出 SQLite 数据
-- sqlite3 data/api-server.db .dump > backup.sql

-- 2. 创建目标数据库
-- PostgreSQL: CREATE DATABASE api_server;
-- MySQL:      CREATE DATABASE api_server CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 3. 修改 .env 配置
-- DB_TYPE=postgresql  或  DB_TYPE=mysql
-- 并配置对应的 HOST / PORT / DATABASE / USERNAME / PASSWORD

-- 4. 启动应用，DatabaseMigration 自动创建表结构
-- 5. 使用 ETL 工具或手动导入历史数据

-- 注意：SQLite 的 datetime('now') 需替换为目标数据库对应函数
-- PostgreSQL: NOW()   MySQL: NOW()
```

---

## v2.3.0 (2026-06-25)

> 细粒度权限、AI 对话平台、多模型支持、统计增强与安全加固。

### 🔐 细粒度权限控制 (RBAC)
- **角色管理**：新增角色 CRUD 页面，支持自定义角色（名称/编码/描述），管理员与普通用户默认角色
- **权限管理**：页面级 & 按钮级权限定义，涵盖项目管理、接口管理、代码模板、AI 对话、数据统计、邮件模板、用户管理、系统设置等模块
- **角色权限分配**：每个角色可独立分配 30+ 项细粒度权限，管理员默认拥有全部权限
- **用户角色绑定**：用户支持绑定自定义角色（`roleId`），登录时返回权限列表，前端菜单按权限动态显隐
- 后端所有控制器 `@PreAuthorize` 从单一 `hasRole('ADMIN')` 升级为 `hasRole('ADMIN') or hasAuthority('xxx:action')`
- 新增 `CustomUserDetailsService` 加载用户+权限信息

### 🤖 AI 对话与智能平台
- **AI 对话页面**：全新聊天界面，SSE 流式响应（逐 Token 实时渲染），Markdown 渲染 + 代码语法高亮，多轮对话上下文记忆，对话历史 localStorage 持久化
- **智能建议**：对话框空态展示 4 个点击式引导建议，基于 README + CHANGELOG AI 动态生成
- **多模型支持**：内置 12 家 LLM 服务商预设（OpenAI / Azure / Gemini / Claude / DeepSeek / 通义千问 / 智谱GLM / Moonshot / 百川 / MiniMax / 小米MiMo / 火山引擎豆包）+ 自定义 OpenAI 兼容接入
- **AI 设置页**：渐变 Banner · 服务商下拉选择（预设/自定义标签）· API 配置表单（地址/密钥/模型/超时/MaxTokens/Temperature 滑块）· 连通性测试 + 延迟展示 · 启用/禁用开关
- **AI 辅助生成**：一键生成响应数据、Java 代码模板（6 种转换器）、HTML 邮件模板、接口描述文档
- **AI 调用统计**：多用户按年/月/日粒度展示 AI 调用趋势（多条折线 + 汇总线），成功率追踪
- 对话历史支持一键复制助手回复、一键清空

### 📊 统计功能增强
- **请求频率**：新增年/月粒度，支持 yearly / monthly / daily / hourly 四档切换，折线图 + 面积渐变
- **来源 IP**：从横向柱状图升级为多折线图，按年/月/日展示各 IP 调用趋势 + 汇总虚线
- **新增趋势**：修复 epoch 毫秒时间戳 SQL 解析错误（`DATE()` → `strftime('%Y-%m-%d', col/1000, 'unixepoch')`），支持年/月/日粒度
- 统计权限从 `ADMIN` 降级为 `statistics:view`，可分配给自定义角色

### 🔒 安全加固
- **Swagger 全面禁用**：前端入口移除，后端 `springdoc` 关闭，`JwtAuthenticationFilter` 移除 Swagger 自动登录逻辑
- `SecurityConfig` 安全规则从硬编码 `hasRole('ADMIN')` 迁移为 `.authenticated()` + 细粒度 `@PreAuthorize` 控制
- 用户删除操作在 `UserService` 中增加 `user:delete` 权限校验

### 🎨 UI / UX
- **使用说明引导对话框**：首页新增交互式使用引导（取代原 Swagger 入口按钮）
- 角色管理 / 权限管理全新页面，管理员侧边栏可见
- AI 设置页允许所有认证用户读取已启用的服务商列表（AI Chat 选择模型用）
- **用户管理角色搜索**：从枚举 `USER/ADMIN` 改为 `roleId` 动态下拉，展示全部自定义角色
- 版本号图标同步更新至 v2.3.0

### 🐛 修复
- 新增趋势统计因 `create_time` 存储为 epoch 毫秒导致始终为 0（已修复 SQL）
- 项目创建者成员记录缺失导致部分权限校验异常（启动时自动补全迁移）
- 已废弃的 `CREATOR` 角色统一迁移为 `ADMIN`

### 📝 升级说明

> ⚠️ **v2.2.0 → v2.3.0 数据库变更**：`DatabaseMigration` 启动时自动执行。若自动迁移失败，请手动执行以下 SQL：

```sql
-- ============================================
-- 从 v2.2.0 升级到 v2.3.0
-- ============================================

-- 1. 创建角色表
CREATE TABLE IF NOT EXISTS t_role (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(50) NOT NULL UNIQUE,
    code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200),
    is_default BOOLEAN NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL
);

-- 2. 创建权限表
CREATE TABLE IF NOT EXISTS t_permission (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(100) NOT NULL UNIQUE,
    group_name VARCHAR(50) NOT NULL,
    type VARCHAR(20) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL
);

-- 3. 创建角色-权限关联表
CREATE TABLE IF NOT EXISTS t_role_permission (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL
);

-- 4. 用户表新增角色ID字段
ALTER TABLE t_user ADD COLUMN role_id BIGINT;

-- 5. 创建 AI 调用日志表
CREATE TABLE IF NOT EXISTS t_ai_call_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id BIGINT NOT NULL,
    username VARCHAR(100),
    api_type VARCHAR(50) NOT NULL,
    call_time DATETIME NOT NULL,
    success BOOLEAN,
    error_message VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS idx_ai_call_time ON t_ai_call_log(call_time);
CREATE INDEX IF NOT EXISTS idx_ai_call_username ON t_ai_call_log(username);

-- 6. 插入默认角色
INSERT OR IGNORE INTO t_role (id, name, code, description, is_default, create_time, update_time)
VALUES (1, '管理员', 'ROLE_ADMIN', '系统管理员，拥有所有权限', 0, datetime('now'), datetime('now'));
INSERT OR IGNORE INTO t_role (id, name, code, description, is_default, create_time, update_time)
VALUES (2, '普通用户', 'ROLE_USER', '默认注册用户角色', 1, datetime('now'), datetime('now'));

-- 7. 插入默认权限定义（仪表盘/项目管理/接口管理/代码模板/AI对话/数据统计/权限管理/邮件模板/用户管理/AI设置/系统设置）
INSERT OR IGNORE INTO t_permission (name, code, group_name, type, sort_order, create_time, update_time) VALUES
('仪表盘-页面访问', 'dashboard:view', '仪表盘', 'PAGE', 1, datetime('now'), datetime('now')),
('项目管理-页面访问', 'project:view', '业务管理', 'PAGE', 10, datetime('now'), datetime('now')),
('项目管理-创建', 'project:create', '业务管理', 'BUTTON', 11, datetime('now'), datetime('now')),
('项目管理-编辑', 'project:edit', '业务管理', 'BUTTON', 12, datetime('now'), datetime('now')),
('项目管理-删除', 'project:delete', '业务管理', 'BUTTON', 13, datetime('now'), datetime('now')),
('接口管理-页面访问', 'api:view', '业务管理', 'PAGE', 20, datetime('now'), datetime('now')),
('接口管理-创建', 'api:create', '业务管理', 'BUTTON', 21, datetime('now'), datetime('now')),
('接口管理-编辑', 'api:edit', '业务管理', 'BUTTON', 22, datetime('now'), datetime('now')),
('接口管理-删除', 'api:delete', '业务管理', 'BUTTON', 23, datetime('now'), datetime('now')),
('代码模板-页面访问', 'code-template:view', '业务管理', 'PAGE', 30, datetime('now'), datetime('now')),
('代码模板-创建', 'code-template:create', '业务管理', 'BUTTON', 31, datetime('now'), datetime('now')),
('代码模板-编辑', 'code-template:edit', '业务管理', 'BUTTON', 32, datetime('now'), datetime('now')),
('代码模板-删除', 'code-template:delete', '业务管理', 'BUTTON', 33, datetime('now'), datetime('now')),
('AI对话-页面访问', 'ai-chat:view', 'AI对话', 'PAGE', 40, datetime('now'), datetime('now')),
('数据统计-页面访问', 'statistics:view', '数据统计', 'PAGE', 50, datetime('now'), datetime('now')),
('权限管理-页面访问', 'permission:view', '权限管理', 'PAGE', 60, datetime('now'), datetime('now')),
('角色管理-页面访问', 'role:view', '权限管理', 'PAGE', 61, datetime('now'), datetime('now')),
('角色管理-创建', 'role:create', '权限管理', 'BUTTON', 62, datetime('now'), datetime('now')),
('角色管理-编辑', 'role:edit', '权限管理', 'BUTTON', 63, datetime('now'), datetime('now')),
('角色管理-删除', 'role:delete', '权限管理', 'BUTTON', 64, datetime('now'), datetime('now')),
('权限分配-编辑', 'permission:assign', '权限管理', 'BUTTON', 65, datetime('now'), datetime('now')),
('邮件模板-页面访问', 'email-template:view', '系统管理', 'PAGE', 70, datetime('now'), datetime('now')),
('邮件模板-创建', 'email-template:create', '系统管理', 'BUTTON', 71, datetime('now'), datetime('now')),
('邮件模板-编辑', 'email-template:edit', '系统管理', 'BUTTON', 72, datetime('now'), datetime('now')),
('邮件模板-删除', 'email-template:delete', '系统管理', 'BUTTON', 73, datetime('now'), datetime('now')),
('用户管理-页面访问', 'user:view', '系统管理', 'PAGE', 80, datetime('now'), datetime('now')),
('用户管理-创建', 'user:create', '系统管理', 'BUTTON', 81, datetime('now'), datetime('now')),
('用户管理-编辑', 'user:edit', '系统管理', 'BUTTON', 82, datetime('now'), datetime('now')),
('用户管理-删除', 'user:delete', '系统管理', 'BUTTON', 83, datetime('now'), datetime('now')),
('AI设置-页面访问', 'ai-settings:view', '系统管理', 'PAGE', 90, datetime('now'), datetime('now')),
('系统设置-页面访问', 'settings:view', '系统管理', 'PAGE', 100, datetime('now'), datetime('now'));

-- 8. 管理员角色分配全部权限
INSERT OR IGNORE INTO t_role_permission (role_id, permission_id)
SELECT 1, id FROM t_permission;

-- 9. 迁移现有用户的 role_id
UPDATE t_user SET role_id = 1 WHERE role = 'ADMIN' AND role_id IS NULL;
UPDATE t_user SET role_id = 2 WHERE role = 'USER' AND role_id IS NULL;

-- 10. 补全项目创建者成员记录（若缺失）
INSERT OR IGNORE INTO t_project_member (project_id, user_id, role, create_time, update_time)
SELECT p.id, p.create_user_id, 1, datetime('now'), datetime('now')
FROM t_project p
WHERE p.create_user_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM t_project_member pm
      WHERE pm.project_id = p.id AND pm.user_id = p.create_user_id
  );
```

---

## v2.2.0 (2026-06-24)

> AI 智能生成、服务端分页、Swagger 导入与页面美化。

### 🚀 AI 智能生成
- **AI 代码模板生成**：根据接口信息 + 转换器类型一键生成 `CustomResponseTransformer` Java 代码，支持 6 种转换器（响应包装 / 数据脱敏 / 字段转换 / 条件响应 / 日志记录 / HTTP 请求转发）
- 生成时将系统默认模板作为 prompt 参考，自动补充缺失 import、修正 `getParams()` 类型转换
- **AI 邮件模板生成**：AI 自动生成邮件 HTML 内容和主题，支持预览按钮右侧并排
- **AI 设置页面**：重命名为「服务商设置」，页面全面美化（渐变头部横幅、卡片式布局、自定义 SVG 图标）
- **AI 超时可配**：AI 设置页新增超时时间字段（30-600 秒），全局实时生效
- 连通性验证结果改为弹窗展示，延迟毫秒数 + 模型名称

### 🚀 Swagger 导入
- **项目管理页新增「导入 Swagger」**：支持上传 JSON 文件或输入 Swagger 文档 URL
- 自动解析 Swagger 2.0 / OpenAPI 3.x，生成接口列表（名称、路径、请求方式、响应体示例）
- 递归解析 `$ref` 引用，智能生成字段示例值（枚举 / 日期 / 邮箱等格式）
- 自动跳过已存在的 path+method 重复接口，导入完成后跳转接口管理页

### 🚀 服务端分页
- **全模块支持真正服务端分页**：项目管理、接口管理、代码模板、用户管理、邮件模板
- 后端 `JpaSpecificationExecutor` + `PageRequest` 动态查询，前端页码/每页条数联动
- 邮件模板页新增搜索栏（名称 / 类型 / 启用状态）+ 分页组件
- 邮件模板启用状态文字改为「启用/禁用」

### 🎨 UI 美化
- 服务商设置页面全新设计：紫色渐变头部、状态卡片、分区卡片、SVG 图标装饰
- 菜单图标优化，空状态插画替换

### 🐛 修复
- 注册邮箱验证码发送时校验用户名并传递用于占位符替换
- HttpClient 转发模板编译错误（类型不匹配、安全规则误拦截）
- 条件响应处理器模板 `getParam()` 返回类型错误
- `DynamicCompiler` 安全规则优化（允许 `System.currentTimeMillis()`、`java.net.http.*`）
- AI 代码生成 504 超时（动态读取 localStorage 超时配置）
- AI 生成代码缺失 import 和类型转换错误（自动修正）
- 邮件模板页面启用状态文字统一

### 📝 升级说明

> ⚠️ **v2.1.2 → v2.2.0 数据库变更**：`DatabaseMigration` 启动时自动执行。若自动迁移失败，请手动执行以下 SQL：

```sql
-- ============================================
-- 从 v2.1.0 / v2.1.1 / v2.1.2 升级到 v2.2.0
-- ============================================

-- 1. 创建 AI 服务商配置表（新增）
CREATE TABLE IF NOT EXISTS t_ai_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    provider VARCHAR(50) NOT NULL UNIQUE,
    provider_name VARCHAR(100) NOT NULL,
    api_url VARCHAR(500) NOT NULL,
    api_key VARCHAR(500) NOT NULL,
    default_model VARCHAR(100),
    max_tokens INTEGER DEFAULT 4096,
    temperature REAL DEFAULT 0.7,
    timeout INTEGER DEFAULT 120,
    enabled BOOLEAN NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL
);

-- 2. 创建系统配置表（新增）
CREATE TABLE IF NOT EXISTS t_system_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value VARCHAR(500),
    description VARCHAR(500),
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL
);
INSERT OR IGNORE INTO t_system_config (config_key, config_value, description, create_time, update_time)
VALUES ('defaultLanguage', 'zh-CN', '系统默认语言', datetime('now'), datetime('now'));

-- 3. 创建请求参数定义表（新增）
CREATE TABLE IF NOT EXISTS t_response_request_param (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    param_name VARCHAR(100) NOT NULL,
    param_type VARCHAR(20) NOT NULL DEFAULT 'QUERY',
    param_value VARCHAR(500),
    required BOOLEAN NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    response_id BIGINT NOT NULL,
    FOREIGN KEY (response_id) REFERENCES t_mock_response(id)
);

-- 4. t_mock_response 新增列（active / is_default / response_delay）
-- SQLite 不支持 ADD COLUMN IF NOT EXISTS，使用 try/catch 或先检查
ALTER TABLE t_mock_response ADD COLUMN active BOOLEAN DEFAULT 0;
ALTER TABLE t_mock_response ADD COLUMN is_default BOOLEAN DEFAULT 0;
ALTER TABLE t_mock_response ADD COLUMN response_delay INTEGER DEFAULT 0;

-- 5. t_mock_api 新增列（custom_response_handler / custom_response_source）
ALTER TABLE t_mock_api ADD COLUMN custom_response_handler VARCHAR(500);
ALTER TABLE t_mock_api ADD COLUMN custom_response_source TEXT;

-- 6. t_user 新增列（language）
ALTER TABLE t_user ADD COLUMN language VARCHAR(10) DEFAULT 'zh-CN';

-- 7. t_custom_code_template 新增 is_system 列 + project_id 改为可空
ALTER TABLE t_custom_code_template ADD COLUMN is_system BOOLEAN DEFAULT 0;

-- 将 project_id 改为可空（SQLite 需重建表）
CREATE TABLE IF NOT EXISTS t_custom_code_template_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    source_code TEXT NOT NULL,
    language VARCHAR(50) NOT NULL DEFAULT 'JAVA',
    enabled BOOLEAN NOT NULL DEFAULT 1,
    is_system BOOLEAN DEFAULT 0,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    create_user_id BIGINT NOT NULL,
    project_id BIGINT
);
INSERT INTO t_custom_code_template_new SELECT
    id, name, description, source_code, language, enabled,
    COALESCE(is_system, 0), create_time, update_time, create_user_id, project_id
FROM t_custom_code_template;
DROP TABLE t_custom_code_template;
ALTER TABLE t_custom_code_template_new RENAME TO t_custom_code_template;
```

---

## v2.1.2 (2026-06-23)

> Swagger 权限管控、代码模板增强与系统优化。

### 🔒 安全
- **Swagger 权限管控**：仅系统管理员可访问 Swagger 接口文档，入口按钮仅管理员可见
- Swagger 自动登录使用真实管理员身份签发 token，避免数据库查询失败导致的 403

### 🚀 新增
- **系统代码模板**：新增 `is_system` 字段，支持全局默认模板（不可修改/删除），`project_id` 改为可空

### 🎨 优化
- Swagger 静态资源公开访问，确保页面正常加载
- 首页 Swagger 入口增加管理员权限校验（前端 + 后端双重验证）

### 📝 升级说明

> ⚠️ **v2.1.1 → v2.1.2 数据库变更**：`DatabaseMigration` 启动时自动执行。若自动迁移失败，请手动执行以下 SQL：

```sql
-- 1. 新增系统默认模板标识
ALTER TABLE t_custom_code_template ADD COLUMN is_system BOOLEAN DEFAULT 0;

-- 2. 将 project_id 改为可空（系统模板不属于任何项目）
-- SQLite 不支持直接 ALTER COLUMN，需重建表：
CREATE TABLE t_custom_code_template_new (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100) NOT NULL, description VARCHAR(500),
    source_code TEXT NOT NULL, language VARCHAR(50) NOT NULL DEFAULT 'JAVA',
    enabled BOOLEAN NOT NULL DEFAULT 1, is_system BOOLEAN DEFAULT 0,
    create_time DATETIME NOT NULL, update_time DATETIME NOT NULL,
    create_user_id BIGINT NOT NULL, project_id BIGINT
);
INSERT INTO t_custom_code_template_new SELECT
    id, name, description, source_code, language, enabled,
    COALESCE(is_system, 0), create_time, update_time, create_user_id, project_id
FROM t_custom_code_template;
DROP TABLE t_custom_code_template;
ALTER TABLE t_custom_code_template_new RENAME TO t_custom_code_template;
```

---

## v2.1.1 (2026-06-17)

> 邮件系统、菜单重构与搜索逻辑修复。

### 🚀 新增
- **邮件模板管理**：新增邮件模板 CRUD 页面，支持模板名称、类型（通用 / 验证码 / 告警）、启用状态和 HTML 内容编辑
- **邮件 HTML 预览**：模板编辑时支持独立弹窗预览渲染后的 HTML 效果，使用 iframe 沙箱渲染
- **邮件模板选择**：系统设置 → 邮箱验证区域可选择验证码邮件模板，与邮箱配置联动保存
- **测试邮件增强**：SMTP 认证失败返回精确错误提示（535 认证失败 / 连接超时 / 收件人无效等），引导用户排查
- **模板占位符**：支持 `{{username}}` `{{email}}` `{{code}}` `{{time}}` 等动态变量替换

### 🎨 UI / UX
- **侧边栏分组折叠菜单**：8 项平铺菜单重构为「业务管理」「系统管理」两个 `el-sub-menu` 折叠分组
- 非管理员仅见「首页 + 业务管理」，界面清爽简洁
- 折叠态子菜单图标居中对齐，icon-arrow 自动隐藏
- 菜单项 hover 左侧光柱 + 渐变高亮动效

### 🐛 修复
- **测试邮件 403**：SecurityConfig 中 OPTIONS 预检规则排在 `/api/email-config/**` 之后导致 CORS 预检被拦截，移至首位修复
- **重复 `@PreAuthorize`**：EmailConfigController 方法级注解与 URL 级 hasRole 冲突，已移除冗余注解
- **代码模板搜索**：修复前端 `filteredList` computed 仅过滤当前页数据的问题，改为服务端动态 Specification 查询（支持按名称 / 项目 / 状态过滤）
- **邮件模板下拉无数据**：Settings 页面首次加载模板列表仅在开关 toggle 时触发，修复为页面加载时自动获取
- **i18n `{{}}` 嵌套解析异常**：邮件模板 placeholder 包含 `{{username}}` 等占位符时 Vue i18n 抛出 `Not allowed nest placeholder` 错误，已用 HTML 实体替代

### ⚡ 优化
- JWT 过滤器认证成功日志升级为 INFO 级别，记录 `authorities` 集合便于调试
- 自定义 AccessDeniedHandler 返回 JSON 格式 403 响应，含 URI / 用户 / 角色信息
- 测试邮件 catch 处移除重复 `ElMessage`，避免双重错误提示

---

## v2.1.0 (2026-06-15)

> 代码模板、着陆页与侧边栏交互升级。

### 🚀 新增
- **自定义代码模板**：项目级 Java 代码模板管理，支持 Monaco 编辑器、编译验证，保存即生效，接口自定义响应处理器可直接引用
- **首页着陆页**：全新品牌访问页，Hero 区域 + 四大特性卡片 + 登录入口按钮，多语言适配
- **侧边栏折叠**：右下角切换按钮，支持展开（220px）⇄ 收缩（64px 仅图标），Canvas 动态线条自动适配

### 🐛 修复
- **自定义接口缓存 0 秒失效**：TTL 设为 0 时立即清除全部已缓存响应，更新接口无条件驱逐缓存
- **个人信息页校验误触**：修改语言保存时密码表单不再被联动校验
- **代码模板保存校验**：创建/编辑模板保存前自动编译验证，不通过则拦截

### ⚡ 优化
- 代码模板页面完整三语言支持（中 / 英 / 日）
- 代码模板 Status 列宽度适配多语言文本

---

## v2.0.5 (2026-06-12)

> 统计页面、安全修复与体验优化。

### 🚀 新增
- **统计页面**：ECharts 可视化，请求频率（天 / 小时）、来源 IP TOP15、新增趋势、IOPS 实时监控，仅管理员可访问
- **路径复制按钮**：接口路径 hover 时显示复制图标

### 🐛 修复
- 统计页面刷新 401 / 403（JWT 白名单与 WebConfig 路由遗漏）
- SQLite `strftime()` 替代 `DATE_FORMAT()`
- `run.sh` PID 文件机制修复端口变更不生效
- 项目创建者无权限查看接口（成员权限校验）
- DynamicCompiler 兼容 JDK 21+，动态扫描 Maven 依赖修复第三方库导入
- Chrome 140 `execCommand('copy')` 降级兼容
- Profile 页密码表单宽度适配多语言
- 邮箱 SHA256 → Cravatar 头像，失败回退默认图

### ⚡ 优化
- 首页统计数 > 9999 显示 `9999+`，hover 精确值
- 统计菜单排序调整

---

## v2.0.4 (2026-06-12)

> UI/UX 深度美化。

### ✨ 新增
- 页脚自定义链接支持 SVG 图标（实时预览）
- 页脚模块独立开关，全部关闭时自动隐藏

### 🎨 UI
- 登录页标题渐变流动 + 六边形 SVG 图标
- 侧边栏深色渐变 + Canvas 动态游走线条 + 顶部光晕
- 侧边栏菜单 hover 左侧光柱 + 渐变高亮 + 图标缩放，选中态发光边框
- Logo 彩色渐变文字流动动画

### 🐛 修复
- 页脚设置保存后不自动刷新
- `setup-env.sh` 默认安装，取消交互确认

---

## v2.0.3 (2026-06-11)

> 安全漏洞修复，`npm audit` 清零。

### 🔒 安全
- axios `1.13.6` → `1.15.1`（3 CVE）、postcss `8.5.6` → `8.5.10`（XSS）
- vite `5.x` → `6.x` 全系升级、Spring Boot `3.2.0` → `3.2.12`
- Docker 基础镜像升级，非 root 运行，容器只读文件系统，禁止提权

---

## v2.0.2 (2026-06-11)

> 新功能与核心修复。

### 🚀 新增
- 登录页 Bing 每日图片背景（2s 超时回退默认图）
- Docker 多阶段构建 + docker-compose + 一键构建推送脚本

### 🐛 修复
- Mock 随机返回条件匹配优先级导致的随机失效
- CORS / 静态资源 401（生产环境 Bing 跨域、静态文件认证拦截）
- Favicon 错误引用

---

## v2.0.1 (2026-06-11)

> UI/UX 全面优化。

### ✨ 新增
- 航空主题亮色 UI 重设计
- 操作下拉菜单（界面更整洁）
- 全站删除统一红色标识 + 危险级确认

### 🐛 修复
- 系统信息 16 个字段 i18n 异常，三语言补齐
- `version` / `systemVersion` 键名冲突

---

## v2.0.0 (2026-06-10)

> 基于 v1.0.2 的重大版本升级。

### 🚀 新增
- **多语言国际化**：中 / 英 / 日三语言，全站实时切换
- **自定义响应处理器**：Java 动态编译，保存即生效，内置脱敏插件
- **系统监控**：CPU / 内存 / 磁盘实时 + JVM 堆详情
- **系统公告**：Markdown 编辑，优先级分级
- **日期格式配置**：多格式可选，全局统一

### ✨ 增强
- 登录失败锁定、IP 白名单、JWT 过期可配
- 管理员完整编辑用户，权限分级
- 请求参数管理（PATH / QUERY / BODY / FILE）
- Axios 超时可配，配置持久化

### 🐛 修复
- 语言切换不全站生效、i18n key 异常、日期格式不持久化
- 日语环境按钮错位、创建时间换行、401 刷新拦截
- 管理员编辑用户权限、修改密码不退出、个人信息刷新

---

## v1.0.2

> 上一稳定版本。

- Vue 3 + Spring Boot 前后端分离
- HTTP / WebSocket Mock，多状态码、条件 / 随机响应
- 多项目隔离，JWT 认证，用户权限控制
- SQLite + Caffeine 缓存，Docker 部署

---

## v1.0.1

> 功能完善与 Bug 修复。

---

## v1.0.0

> 首个正式发布版本。
