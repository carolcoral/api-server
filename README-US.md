<p align="center">
  <img src="https://img.shields.io/badge/Version-2.4.0-blue?style=flat-square" alt="Version">
  <img src="https://img.shields.io/badge/License-Apache%202.0-green?style=flat-square" alt="License">
  <img src="https://img.shields.io/badge/JDK-21-red?style=flat-square&logo=openjdk" alt="JDK">
  <img src="https://img.shields.io/badge/Node.js-18+-green?style=flat-square&logo=nodedotjs" alt="Node">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?style=flat-square&logo=springboot" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Vue-3.x-brightgreen?style=flat-square&logo=vuedotjs" alt="Vue">
</p>

<h1 align="center">API Server</h1>
<p align="center">Full-featured API Mock Platform · Spring Boot + Vue 3</p>

<p align="center">
  HTTP / WebSocket Mock · Debug Panel · AI Generation · Swagger Import · RBAC · Template Engine · Multi-DB · Ops Monitor · i18n
</p>

---

## ✨ Core Features

| Module | Capabilities |
|------|------|
| 🧩 API Mocking | Fixed / Conditional / Weighted random / Java dynamic code / Delay / Default response / WebSocket Mock |
| 📝 Template Engine | Faker.js-style randomized data: `{{name()}}` `{{phone()}}`, helper panel + one-click preview |
| 🔍 Debug Panel | Real-time request log tracking · Latency distribution · Request/response details · Search & pagination |
| 🤖 AI Platform | SSE streaming · Multi-provider switching · 12+ LLM · One-click generate responses/code/emails |
| 📥 Swagger Import | 2.0 / OpenAPI 3.x, JSON upload or URL, recursive `$ref` resolution |
| 📦 Project Management | Multi-project isolation · Member permissions · JSON/Swagger import & export |
| 🧬 Code Templates | Monaco Editor · Compile validation · System + project-level · Hot reload · 6 transformer types |
| 🔐 Fine-grained RBAC | 50+ page & button-level permissions · Permission definition management · Custom roles · Auto-sync |
| 🗄️ Multi-Database | SQLite / PostgreSQL / MySQL one-click switch, dialect auto-adaptive |
| 📊 Statistics | Request trends · Source IP · AI call volume · IOPS · JVM/CPU/Memory/Disk |
| ✉️ Email System | SMTP config · Template management · HTML preview · Placeholder substitution |
| 🌍 i18n | Chinese / English / 日本語 · Full-site real-time switching |
| 🧪 Automated Testing | Python test framework, 90+ cases covering AI / Features / RBAC / Security / Swagger |
| 🛡️ Ops & Monitoring | Health checks · Prometheus metrics · Backup & restore · Actuator security |
| 🎨 Modern UI | Dark sidebar · Canvas dynamic lines · Role badges · Mobile responsive · Welcome page |
| 🐳 Containerized | Docker multi-stage build · Non-root · Maven mirror · Offline compatible |

---

## 🚀 Quick Start

Requirements: JDK 21+ · Maven 3.6+ · Node.js 18+ · npm 9+

```bash
# One-click start (auto builds frontend & backend)
git clone https://github.com/carolcoral/api-server.git && cd api-server
./run.sh
```

Access: `http://localhost:8080` | API: `/api`

<details>
<summary>📖 More Deployment Options</summary>

```bash
# Manual build
./build-all-in-one.sh
java -jar backend/target/api-server-2.4.0.jar

# Development mode
cd backend && mvn spring-boot:run              # Terminal 1
cd frontend && npm install && npm run dev       # Terminal 2

# Docker
cd docker && docker-compose up -d
```

> See [BUILD_README.md](./BUILD_README.md) for build instructions
</details>

---

## 🔐 Default Credentials

| Environment | Username | Password | Notes |
|------|--------|------|------|
| Development | `admin` | `Admin@123` | Auto-created on first startup |
| Production | `$ADMIN_USERNAME` | `$ADMIN_PASSWORD` | Must set a strong password |

```bash
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD=YourStrongP@ssw0rd123
export JWT_SECRET=your-super-secret-jwt-key-at-least-256-bits-long
```

---

## 📚 Response Modes

| Mode | Description | Use Case |
|------|------|------|
| Fixed Response | Predefined JSON / text | Status codes 200 / 404 / 500 |
| Conditional Response | Dynamic matching by request params | `$.userId == '123'` → return user data |
| Random Response | Weighted random selection | Canary release simulation |
| Code Handler | Java dynamic compilation, can read/write DB and call external APIs | Data masking, format wrapping |

```bash
# HTTP Mock
curl http://localhost:8080/api/mock/{projectCode}/{apiPath}

# WebSocket Mock
new WebSocket('ws://localhost:8080/api/ws/mock/{projectCode}/{path}')
```

---

## 🤖 AI Capabilities

| Capability | Description |
|------|------|
| 💬 Smart Chat | Multi-turn context memory · SSE streaming · Multi-provider switching · Markdown + syntax highlighting · Persistent history |
| 🔍 Dynamic Retrieval | Real-time document search injecting context into prompts, fallback to general knowledge |
| 🎨 Content Generation | One-click: response data · Java code · HTML email templates · API descriptions |
| 🔌 Multi-Model | 12+ providers: OpenAI · Azure · Gemini · Claude · DeepSeek · Qwen · GLM · Moonshot · Baichuan · MiniMax · MiMo · Doubao + Custom |
| 📊 Call Statistics | Multi-user AI call trend charts · Year/Month/Day granularity · Success rate monitoring |

---

## 🗄️ Multi-Database

| Database | Switch | Best For |
|----------|--------|----------|
| SQLite | `DB_TYPE=sqlite` (default) | Standalone, zero-config |
| PostgreSQL | `DB_TYPE=postgresql` | Production, high-concurrency |
| MySQL | `DB_TYPE=mysql` | Existing MySQL infrastructure |

Just set `DB_TYPE` and connection info in `.env`, the `DatabaseDialectProvider` handles the rest.

---

## 🛡️ Ops & Monitoring

| Capability | Description |
|------|------|
| 💚 Health Checks | DB connection pool / Disk space / Mock service status, `show-details: always` via `/actuator/health` |
| 📈 Prometheus | `mock_requests_total` / `mock_request_duration` metrics, ready for Grafana dashboards |
| 💾 Backup & Restore | One-click export/restore full Mock config (JSON), merge & replace modes |

---

## 📁 Project Structure

```
api-server/
├── backend/                          # Spring Boot
│   └── src/.../apiserver/
│       ├── config/                   # Security / Web / CORS / Email
│       ├── controller/               # REST Controllers
│       ├── entity/                   # JPA Entities
│       ├── filter/                   # JWT Authentication Filters
│       ├── plugin/                   # Dynamic Compiler & Response Handlers
│       ├── repository/               # Spring Data JPA
│       ├── service/                  # Business Logic
│       └── util/                     # Utilities
├── frontend/                         # Vue 3
│   └── src/
│       ├── locales/                  # i18n (zh-CN / en-US / ja-JP)
│       ├── views/                    # Page Components
│       ├── layout/                   # Layout Components
│       ├── stores/                   # Pinia State Management
│       └── api/                      # Axios Wrapper
├── auto_test_tool/                   # Automated Testing Suite
│   ├── core/                         # Test Engine / HTTP Client / Report Generator
│   ├── tests/                        # AI / Features / RBAC / Security Test Suites
│   └── config/                       # Test Configuration
├── docker/                           # Docker Compose + Dockerfile
├── build.sh / run.sh                 # Build & Start Scripts
├── CHANGELOG.md                      # Changelog
├── README.md                         # README (Chinese)
└── README-US.md                      # README (English)
```

---

## ⚙️ Key Configuration

```yaml
# application.yml
spring:
  profiles:
    active: ${DB_TYPE:sqlite}          # Database: sqlite | mysql | postgresql
jwt:
  secret: ${JWT_SECRET:your-secret-key}
  expiration: 86400000                 # 24 hours
admin:
  username: ${ADMIN_USERNAME:admin}
  password: ${ADMIN_PASSWORD:}
```

```bash
# .env environment variables
DB_TYPE=sqlite                         # Switch database type
JWT_SECRET=your-256-bits-plus-secret-key
ADMIN_PASSWORD=YourStrongP@ssw0rd123
ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
```

---

## 🔒 Security

| Feature | Description |
|------|------|
| JWT Authentication | Stateless tokens, configurable expiration |
| Strong Password Policy | Uppercase + lowercase + digits + special characters, minimum 8 characters |
| Login Lockout | Temporary lockout after multiple failures |
| IP Whitelist | Restrict management API source IPs |
| CORS | Cross-origin whitelist control |
| Injection Prevention | SQL parameterization · XSS filtering · CSRF protection |
| RBAC | Role-permission system, page/button-level control, dynamic menu visibility |
| Actuator Security | Monitoring endpoints require authentication, preventing info leaks |

---

## 📄 License

Apache License 2.0 · Copyright © 2024-2026 carolcoral · Author: LXW

---

## 📧 Contact

GitHub [github.com/carolcoral](https://github.com/carolcoral) · Email lxw@cnkj.site

<p align="center"><sub>Made with ❤️ by <a href="https://github.com/carolcoral">carolcoral</a></sub></p>
