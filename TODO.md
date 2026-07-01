# Mock Server — 功能待办文档

> 最后更新：2026-06-30

---

## ✅ 已完成功能

### 核心 Mock 引擎
- [x] **HTTP Mock** — 通配路由 `/api/mock-server/{projectCode}/**`，支持 GET/POST/PUT/DELETE/PATCH/OPTIONS
- [x] **WebSocket Mock** — `/api/ws/mock/{projectCode}/{path}`，支持会话管理
- [x] **4 种响应模式**：
  - 固定响应（Fixed Response）— 预定义 JSON/文本，自定义状态码
  - 条件响应（Conditional Response）— 基于 JSONPath 的请求参数匹配
  - 随机/加权响应（Random/Weighted Response）— 加权随机选择，适用灰度场景
  - 代码处理器（Code Handler）— Java 动态编译，支持 6 种转换器类型
- [x] **响应延迟模拟** — 每个响应可配置延迟时间（受系统最大延迟限制保护）
- [x] **响应缓存** — Caffeine 缓存代码处理器结果，可配置 TTL

### 项目管理
- [x] 多项目隔离，项目拥有唯一编码、名称、描述、启用/禁用状态
- [x] **成员管理** — 三级角色：创建者、管理员、成员
- [x] **权限分级** — 系统管理员可管理所有项目；项目成员权限受限
- [x] 完整 CRUD，服务端分页与搜索

### API（Mock API）管理
- [x] Mock API 完整 CRUD（路径、HTTP 方法、名称、描述、项目关联）
- [x] 每个 API 支持多个响应配置
- [x] 请求参数定义（PATH、QUERY、BODY、FILE 类型）
- [x] 自定义响应处理器（项目级/系统级代码模板）
- [x] 服务端分页与搜索
- [x] API 启用/禁用开关

### Swagger/OpenAPI 导入
- [x] 支持上传 JSON 文件或 URL 导入
- [x] 兼容 Swagger 2.0 和 OpenAPI 3.x
- [x] 递归 `$ref` 解析
- [x] 自动生成字段示例值（枚举、日期、邮件等）
- [x] 跳过重复的 path+method 组合
- [x] 冲突解决端点

### 代码模板
- [x] Monaco Editor 编辑 Java 代码
- [x] 保存前编译验证
- [x] 项目级和系统级模板（系统模板不可修改）
- [x] 热加载：保存后立即可用于 API 响应处理
- [x] 6 种内置转换器类型（响应包装、数据脱敏、字段转换、条件响应、日志记录、HTTP 转发）
- [x] 服务端分页与搜索

### AI 平台
- [x] AI 聊天页面 — 支持多轮对话
- [x] OpenAI 兼容 SSE 流式响应
- [x] 多 AI 提供商支持
- [x] 动态文档检索（RAG）：根据提问自动检索 README/CHANGELOG 注入上下文
- [x] API 定义 AI 智能生成

### 仪表盘
- [x] 统计概览（项目数、API 数、请求数等）
- [x] ECharts 图表可视化

### 系统管理
- [x] **用户管理** — 完整 CRUD，角色分配
- [x] **角色与权限管理** — RBAC 模型
- [x] **系统配置** — 最大响应延迟等全局参数
- [x] **操作日志** — 审计追踪

### 认证与安全
- [x] JWT 无状态认证
- [x] Spring Security 集成
- [x] 基于角色和权限的访问控制

### 国际化
- [x] 三语言支持：中文（zh-CN）、英文（en-US）、日文（ja-JP）

### 部署
- [x] Docker 多阶段构建
- [x] 一键构建脚本（`build-all-in-one.sh`）
- [x] Docker Compose 编排

---

## 📋 下阶段计划功能

### 功能增强
- [ ] **gRPC Mock 支持** — 扩展 Mock 能力到 gRPC 协议
- [ ] **GraphQL Mock 支持** — 支持 GraphQL schema 定义与 Mock
- [x] **Mock 数据模板引擎** — 支持 Faker.js 风格的随机数据生成（中文姓名、手机号、身份证等）
- [ ] **请求录制与回放** — 录制真实请求并自动生成 Mock 配置
- [x] **批量操作** — 项目API全量导出（符合swagger格式）
- [x] **Mock 数据导入导出** — 支持项目级 JSON/YAML 格式导入导出（包括接口名称、地址、请求参数、响应报文等信息），便于团队共享

### 协作与集成
- [ ] **Postman Collection 导入** — 支持 Postman 导出的 Collection 文件导入
- [ ] **Webhook 通知** — Mock 调用事件推送到飞书/钉钉/企业微信
- [ ] **CI/CD 集成插件** — 提供 Jenkins/GitHub Actions 插件，流水线中自动部署 Mock 服务

### 性能与可靠性
- [x] **支持外部数据库** — 保留 SQLite 的条件下，新增支持 MySQL/PostgreSQL ，适用于生产环境
- [ ] **请求限流** — 基于项目和 API 维度的 QPS 限流

### 体验优化
- [ ] **移动端适配** — 响应式布局优化
- [ ] **API Mock 调试面板** — 实时查看请求/响应日志、延迟分布等

### 运维与监控
- [ ] **Prometheus 指标导出** — 暴露 Mock 调用次数、延迟分布等指标
- [ ] **健康检查端点增强** — 详细的组件状态检查
- [ ] **数据备份与恢复** — 一键备份/恢复 Mock 配置

---

## 💡 建议与反馈

如有功能建议或问题反馈，欢迎提 Issue 或 PR。

