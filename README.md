<p align="center">
  <img src="https://img.shields.io/badge/Version-2.3.3-blue?style=flat-square" alt="Version">
  <img src="https://img.shields.io/badge/License-Apache%202.0-green?style=flat-square" alt="License">
  <img src="https://img.shields.io/badge/JDK-21-red?style=flat-square&logo=openjdk" alt="JDK">
  <img src="https://img.shields.io/badge/Node.js-18+-green?style=flat-square&logo=nodedotjs" alt="Node">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?style=flat-square&logo=springboot" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Vue-3.x-brightgreen?style=flat-square&logo=vuedotjs" alt="Vue">
</p>

<h1 align="center">Mock Server</h1>
<p align="center">Spring Boot + Vue 3 · 全功能 API 模拟平台</p>

<p align="center">
  接口模拟 · 模板引擎 · AI 对话 · Swagger 生态 · RBAC 权限 · 调试面板 · 运维监控 · 多数据库 · 多语言
</p>

---

## ✨ 核心特性

|   | 模块 | 说明 |
|---|------|------|
| 🧩 | 接口模拟 | 固定 / 条件匹配 / 加权随机 / Java 动态代码 / 延迟 / 默认响应 / WebSocket Mock |
| 📝 | 模板引擎 | Faker.js 风格随机数据生成 `{{name()}}` `{{phone()}}`，模板面板一键预览 |
| 🔍 | 调试面板 | 实时请求日志追踪 · 延迟分布可视化 · 请求/响应详情 · 搜索与分页 |
| 🤖 | AI 智能平台 | SSE 流式对话 · 多服务商配置切换 · 12+ LLM · 一键生成响应/代码/邮件 |
| 📥 | Swagger 生态 | 2.0 / OpenAPI 3.x 导入导出，递归 `$ref` 解析，冲突检测 |
| 📦 | 项目管理 | 多项目隔离 · 成员权限 · JSON/Swagger 导入导出 |
| 🧬 | 动态代码 | Monaco Editor 编译验证 · 6 种转换器 · 热加载即时生效 |
| 🔐 | 权限管理 | RBAC 体系 · 50+ 项细粒度权限 · 权限定义管理 · 子权限自动同步 |
| 🗄️ | 多数据库 | SQLite / PostgreSQL / MySQL 一键切换，方言自动适配 |
| 📊 | 数据统计 | 请求趋势 · IP 来源 · AI 调用 · IOPS · JVM/CPU/内存/磁盘 |
| ✉️ | 邮件系统 | SMTP 配置 · 模板管理 · HTML 预览 · 占位符替换 |
| 🌍 | 国际化 | 中文 / English / 日本語 · 全站实时切换 |
| 🧪 | 自动化测试 | Python 测试框架，90+ 用例覆盖 AI / 功能 / RBAC / 安全 / Swagger |
| 🛡️ | 运维监控 | 健康检查 · Prometheus 指标 · 备份恢复 · Actuator 安全加固 |
| 🐳 | 容器化 | Docker 多阶段构建 · 非 root · 镜像加速 · 离线兼容 |

---

## 🚀 快速开始

环境要求：JDK 21+ · Maven 3.6+ · Node.js 18+ · npm 9+

```bash
# 一键启动（自动构建前后端）
git clone https://github.com/carolcoral/mock-server.git && cd mock-server
./run.sh
```

访问地址：`http://localhost:8080` | API：`/api`

<details>
<summary>📖 更多部署方式</summary>

```bash
# 数据库切换（.env 中设置 DB_TYPE）
# 支持：sqlite（默认）· mysql · postgresql

# 手动构建
./build-all-in-one.sh
java -jar backend/target/mock-server-2.3.3.jar

# 开发模式
cd backend && mvn spring-boot:run          # 终端 1
cd frontend && npm install && npm run dev   # 终端 2

# Docker
cd docker && docker-compose up -d
```

> 构建说明见 [BUILD_README.md](./BUILD_README.md)
</details>

---

## 🔐 默认账号

| 环境 | 用户名 | 密码 | 备注 |
|------|--------|------|------|
| 开发 | `admin` | `Admin@123` | 首次启动自动创建 |
| 生产 | `$ADMIN_USERNAME` | `$ADMIN_PASSWORD` | 务必修改强密码 |

```bash
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD=YourStrongP@ssw0rd123
export JWT_SECRET=your-super-secret-jwt-key-at-least-256-bits-long
```

---

## 📚 响应模式

| 模式 | 说明 | 场景 |
|------|------|------|
| 固定响应 | 预设 JSON / 文本 | 状态码 200 / 404 / 500 |
| 条件响应 | 请求参数动态匹配 | `$.userId == '123'` → 返回用户数据 |
| 随机响应 | 按权重随机返回 | 灰度发布模拟 |
| 代码处理器 | Java 动态编译，可读写 DB、调用外部 API | 数据脱敏、格式包装 |

```bash
# HTTP Mock
curl http://localhost:8080/api/mock/{projectCode}/{apiPath}

# WebSocket Mock
new WebSocket('ws://localhost:8080/api/ws/mock/{projectCode}/{path}')
```

---

## 🤖 AI 能力

|   | 能力 | 说明 |
|---|------|------|
| 💬 | 智能对话 | SSE 流式响应 · 多轮上下文记忆 · Markdown + GitHub 风格代码高亮 · 历史持久化 |
| 🔍 | 知识检索 | RAG 增强，实时检索项目文档注入上下文，未命中回退通用知识 |
| 🎨 | 内容生成 | 一键生成响应数据 · Java 代码模板 · HTML 邮件 · 接口描述 |
| 🔌 | 多模型 | 12+ LLM 预设 + 自定义兼容（OpenAI / Gemini / Claude / DeepSeek / 通义千问 等） |
| 📊 | 调用统计 | 多用户趋势折线图 · 年/月/日粒度 · 成功率追踪 |

---

## 🗄️ 多数据库

| 数据库 | 切换方式 | 适用场景 |
|--------|----------|----------|
| SQLite | `DB_TYPE=sqlite`（默认） | 单机部署、零配置开箱即用 |
| PostgreSQL | `DB_TYPE=postgresql` | 生产环境、高并发 |
| MySQL | `DB_TYPE=mysql` | 已有 MySQL 基础设施 |

只需修改 `.env` 中的 `DB_TYPE` 和对应连接信息，`DatabaseDialectProvider` 自动适配方言。

---

## 🛡️ 运维与监控

|   | 能力 | 说明 |
|---|------|------|
| 💚 | 健康检查 | 数据库连接池 / 磁盘空间 / Mock 服务状态，`/actuator/health` 详情展示 |
| 📈 | Prometheus | `mock_requests_total` / `mock_request_duration` 等指标，Grafana 直接对接 |
| 💾 | 备份恢复 | 一键导出/恢复完整 Mock 配置（JSON），支持合并与替换模式 |

---

## 📁 项目结构

```
mock-server/
├── backend/                          # Spring Boot
│   └── src/.../mockserver/
│       ├── config/                   # Security / Web / CORS / 邮件
│       ├── controller/               # REST 控制器
│       ├── entity/                   # JPA 实体
│       ├── filter/                   # JWT 认证过滤器
│       ├── plugin/                   # 动态编译器 & 响应处理器
│       ├── repository/               # Spring Data JPA
│       ├── service/                  # 业务逻辑
│       └── util/                     # 工具类
├── frontend/                         # Vue 3
│   └── src/
│       ├── locales/                  # 国际化 (zh-CN / en-US / ja-JP)
│       ├── views/                    # 页面组件
│       ├── layout/                   # 布局组件
│       ├── stores/                   # Pinia 状态管理
│       └── api/                      # Axios 封装
├── auto_test_tool/                   # 自动化测试工具
│   ├── core/                         # 测试引擎 / HTTP 客户端 / 报告生成
│   ├── tests/                        # AI / 功能 / RBAC / 安全 测试套件
│   └── config/                       # 测试配置
├── docker/                           # Docker Compose + Dockerfile
├── build.sh / run.sh                 # 构建 & 启动
├── CHANGELOG.md                      # 变更日志
└── README.md
```

---

## ⚙️ 关键配置

```yaml
# application.yml
spring:
  profiles:
    active: ${DB_TYPE:sqlite}          # 数据库：sqlite | mysql | postgresql
jwt:
  secret: ${JWT_SECRET:your-secret-key}
  expiration: 86400000                 # 24 小时
admin:
  username: ${ADMIN_USERNAME:admin}
  password: ${ADMIN_PASSWORD:}
```

```bash
# .env 环境变量
DB_TYPE=sqlite                         # 切换数据库
JWT_SECRET=your-256-bits-plus-secret-key
ADMIN_PASSWORD=YourStrongP@ssw0rd123
ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
```

---

## 🔒 安全

| 特性 | 说明 |
|------|------|
| JWT 认证 | 无状态 Token，过期时间可配 |
| 强密码策略 | 大小写 + 数字 + 特殊字符，最少 8 位 |
| 登录锁定 | 多次失败后临时锁定 |
| IP 白名单 | 限制管理接口来源 |
| CORS | 跨域白名单控制 |
| 防注入 | SQL 参数化 · XSS 过滤 · CSRF 防护 |
| RBAC | 角色-权限体系，页面/按钮级控制，动态菜单显隐 |
| Actuator 安全 | 监控端点需登录认证，杜绝信息泄露 |

---

## 📄 许可证

Apache License 2.0 · Copyright © 2024-2026 carolcoral · Author: LXW

---

## 📧 联系

GitHub [github.com/carolcoral](https://github.com/carolcoral) · Email lxw@cnkj.site

<p align="center"><sub>Made with ❤️ by <a href="https://github.com/carolcoral">carolcoral</a></sub></p>
