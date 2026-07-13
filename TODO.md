# Mock Server — 功能待办文档

> 最后更新：2026-07-13

---

## ✅ 已完成功能

### 核心 Mock 引擎
- [x] **HTTP Mock** — 通配路由 `/api/mock-server/{projectCode}/**`，支持 GET/POST/PUT/DELETE/PATCH/OPTIONS
- [x] **WebSocket Mock** — `/api/ws/mock/{projectCode}/{path}`，支持会话管理
- [x] **5 种响应模式**：固定 / 条件匹配 / 加权随机 / Java 动态代码处理器 / 默认响应
- [x] **响应延迟模拟** — 每个响应可配置毫秒级延迟（受系统最大延迟限制保护）
- [x] **响应缓存** — Caffeine 缓存代码处理器结果，可配置 TTL
- [x] **Mock 模板引擎** — Faker.js 风格随机数据生成（中文姓名、手机号、身份证等）

### 项目管理
- [x] 多项目隔离，项目拥有唯一编码、名称、描述、启用/禁用状态
- [x] **成员管理** — 三级角色：创建者、管理员、成员
- [x] **权限分级** — 系统管理员可管理所有项目，项目成员权限受限
- [x] 完整 CRUD + 服务端分页与搜索
- [x] **数据导入导出** — 项目级 JSON 格式导出/导入，支持增量/覆盖模式
- [x] **Swagger 导出** — Swagger 2.0 / OpenAPI 3.0 格式
- [x] **批量操作** — 批量删除代码模板

### API（Mock API）管理
- [x] Mock API 完整 CRUD（路径、HTTP 方法、名称、描述、项目关联）
- [x] 每个 API 支持多个响应配置
- [x] 请求参数定义（PATH / QUERY / BODY / HEADER / FILE 类型）
- [x] 自定义响应处理器（项目级/系统级代码模板 · Java 动态编译 · 6 种转换器）
- [x] 服务端分页与搜索
- [x] API 启用/禁用开关

### Swagger/OpenAPI 导入
- [x] 上传 JSON 文件或 URL 导入
- [x] 兼容 Swagger 2.0 和 OpenAPI 3.x
- [x] 递归 `$ref` 解析 · 自动生成字段示例值
- [x] 跳过重复 path+method · 冲突检测与覆盖确认

### Mock 调试面板
- [x] **请求日志追踪** — 实时记录每次 Mock 请求完整信息（路径/方法/耗时/状态码/响应大小）
- [x] **延迟分布统计** — 可视化展示请求延迟分布
- [x] **请求日志持久化** — `t_request_log` 表异步写入，支持分页、搜索、清空

### 代码模板
- [x] Monaco Editor 编辑 · 编译验证 · 热加载
- [x] 项目级 + 系统级模板（系统模板不可修改）
- [x] 6 种内置转换器（响应包装 / 数据脱敏 / 字段转换 / 条件响应 / 日志记录 / HTTP 转发）
- [x] AI 流式生成自定义代码模板
- [x] 服务端分页与搜索

### AI 智能平台
- [x] **AI 对话** — 多轮上下文记忆 · SSE 流式响应 · Markdown + GitHub 风格代码高亮 · 对话历史持久化
- [x] **动态文档检索 (RAG)** — 根据提问实时检索 README/CHANGELOG 注入上下文
- [x] **AI 内容生成** — 一键生成响应数据 / Java 代码模板 / HTML 邮件模板 / 接口描述
- [x] **12+ LLM 服务商** — OpenAI · Azure · Gemini · Claude · DeepSeek · 通义千问 · 智谱GLM · Moonshot · 百川 · MiniMax · 小米MiMo · 火山引擎豆包 + 自定义兼容
- [x] **多配置切换** — 同时配置多个 AI 服务商，对话页一键切换，支持默认配置与模型选择器
- [x] **AI 调用统计** — 多用户年/月/日粒度趋势折线图 + 成功率追踪

### 数据统计
- [x] ECharts 可视化 · 请求频率（年/月/日/时四档）· 来源 IP TOP15 · IOPS 实时监控
- [x] 项目与接口新增趋势 · AI 调用量统计

### 权限管理
- [x] **RBAC 模型** — 50+ 项细粒度权限，页面+按钮级控制，子权限自动同步
- [x] **权限定义管理** — 权限 CRUD（Permissions.vue 双标签页），支持创建/编辑/删除权限定义
- [x] **子权限自动同步** — `DatabaseMigration` 启动时将子权限自动赋予已拥有父权限的角色
- [x] **AI 设置子权限** — 6 项按钮级权限（创建/编辑/删除/启用禁用/设置默认/测试连通性），前后端统一控制
- [x] **系统设置子权限** — 8 项按钮级权限，菜单项按权限动态显隐
- [x] **运维监控权限** — 3 项权限（页面访问/备份导出/数据恢复）

### 系统管理
- [x] **用户管理** — 完整 CRUD，角色分配
- [x] **角色管理** — 自定义角色 CRUD，权限分配
- [x] **系统配置** — 全局参数（响应延迟上限、JWT 过期、IP 白名单、登录锁定等）
- [x] **系统公告** — Markdown 编辑，优先级分级
- [x] **邮件系统** — SMTP 配置 · 模板管理（注册验证/重置密码）· HTML 预览 · 占位符替换

### 认证与安全
- [x] JWT 无状态认证 · Spring Security 集成
- [x] 强密码策略（大小写+数字+特殊字符 8位+）· 登录失败锁定
- [x] IP 白名单 · CORS 白名单 · SQL 参数化防注入
- [x] **Actuator 安全加固** — `/actuator/**` 需登录认证，杜绝信息泄露

### 多数据源
- [x] **SQLite / MySQL / PostgreSQL** — 方言抽象层自动适配，`.env` 一键切换
- [x] DatabaseMigration 启动时自动执行，全方言兼容

### 运维与监控
- [x] **运维监控页面** — OpsMonitor.vue，健康检查仪表盘 + 数据概览 + Prometheus 指标浏览器
- [x] **Prometheus 指标** — 暴露 `mock_requests_total` / `mock_request_duration` 等指标，`application.yml` 启用
- [x] **健康检查增强** — 数据库/磁盘组件状态、`show-details: always`
- [x] **数据备份与恢复** — 一键备份/恢复完整 Mock 配置（JSON 格式），支持合并/替换模式
- [x] **系统信息** — 数据库类型、磁盘空间（GB）、JVM 详情

### 自动化测试
- [x] **Python 全自动测试框架**（`auto_test_tool/`）
- [x] 9 大领域 90+ 用例：模板引擎 · 导入导出 · 响应管理 · AI 对话 · 页面功能 · RBAC · 安全扫描 · 权限校验 · Swagger 导入
- [x] HTML/Markdown 双格式报告 · 并行测试 · 指数退避重试

### 国际化
- [x] 中 / 英 / 日三语言全站实时切换
- [x] 运维监控页面三语言适配
- [x] 权限定义管理页面三语言适配

### 部署
- [x] Docker 多阶段构建 · 非 root 只读容器 · Docker Compose
- [x] `build-all-in-one.sh` 一键构建 · `run.sh` 一键启动
- [x] 离线环境徽章本地化，内网正常显示

### UI/UX
- [x] **角色铭牌** — 右上角用户名区域流光科幻特效，优先展示 `roleName`
- [x] **外部资源离线化** — 用户头像本地 SVG，登录背景内置，无外部依赖
- [x] **移动端适配** — 侧边栏、页面布局全面响应式优化

## 📋 下阶段计划功能
### 功能增强
- [ ] **gRPC Mock 支持** — 扩展 Mock 能力到 gRPC 协议
- [ ] **GraphQL Mock 支持** — 支持 GraphQL schema 定义与 Mock
- [ ] **请求录制与回放** — 录制真实请求并自动生成 Mock 配置
### 协作与集成
- [ ] **Postman Collection 导入** — 支持 Postman 导出的 Collection 文件
- [ ] **CI/CD 集成插件** — Jenkins/GitHub Actions 插件，流水线中自动部署 Mock 服务
### 性能与可靠性
- [ ] **请求限流** — 基于项目和 API 维度的 QPS 限流


---

## 💡 建议与反馈

如有功能建议或问题反馈，欢迎提 Issue 或 PR。
