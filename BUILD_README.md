# Mock Server 一键构建和运行指南

## 概述

本项目现已支持前后端一体化打包部署，前端静态文件会被自动集成到后端 JAR 包中，只需运行一个 JAR 包即可同时提供后端 API 和前端界面服务。

## 使用方式

### 方式一：一键构建和运行（推荐）

直接运行 `run.sh` 脚本，它会自动检测是否需要构建，如果 JAR 包或前端构建产物不存在，会自动执行构建：

```bash
./run.sh
```

### 方式二：手动构建和运行

如果只需要构建而不运行，或者想分步操作：

1. **构建项目（包含前后端）**
   ```bash
   ./build-all-in-one.sh
   ```
   此脚本会：
   - 构建前端，生成静态文件到 `frontend/dist`
   - 将前端静态文件复制到后端资源目录
   - 构建后端，生成包含前端静态文件的 JAR 包

2. **运行 JAR 包**
   ```bash
   java -jar backend/target/mock-server-2.3.3.jar
   ```

### 方式三：开发模式

如果需要前后端分离开发，使用原有的 `build.sh` 脚本：

```bash
./build.sh
```

此脚本会分别构建后端和前端，但不会将前端集成到 JAR 包中。

## 文件说明

- `build-all-in-one.sh`: 一键构建脚本，将前端静态文件集成到后端 JAR 包
- `run.sh`: 一键运行脚本，自动检测构建状态并启动服务
- `build.sh`: 开发模式构建脚本，前后端分离构建

## 工作原理

1. **前端构建**：使用 Vite 构建前端，生成静态文件到 `frontend/dist` 目录
2. **静态资源复制**：Maven 在打包阶段自动将 `frontend/dist` 目录的内容复制到 `target/classes/static` 目录
3. **后端打包**：Spring Boot 打包时将 `static` 目录中的资源一起打包到 JAR 包中
4. **资源服务**：后端启动后，通过 Spring Boot 的静态资源服务机制，直接提供前端静态文件

## 访问地址

启动成功后，可通过以下地址访问：

- **前端界面**: http://localhost:8080
- **后端 API**: http://localhost:8080/api
- **API 文档**: http://localhost:8080/swagger-ui.html

## 端口配置

端口配置在项目根目录的 `.env` 文件中：

```env
SERVER_PORT=8080
FRONTEND_PORT=3000
```

**注意**：在一体化部署模式下，只需要配置 `SERVER_PORT`，`FRONTEND_PORT` 仅在开发模式下使用。

## 系统要求

- **JDK**: 21
- **Maven**: 3.6+
- **Node.js**: 18+
- **npm**: 9+

## 常见问题

### Q: 为什么前端和后端使用同一个端口？

A: 在一体化部署模式下，前端静态文件由后端 Spring Boot 服务提供，因此只需要一个端口即可访问前后端功能。

### Q: 如何停止服务？

A: 如果使用 `run.sh` 启动，会显示进程 PID，可以使用 `kill <PID>` 停止服务。
或者使用 `lsof -ti:8080 | xargs kill` 停止占用 8080 端口的进程。

### Q: 如何查看日志？

A: 日志文件位于 `backend/logs/server.log`，可以使用 `tail -f backend/logs/server.log` 实时查看。

### Q: 构建失败怎么办？

A: 请检查以下事项：
1. 确保已安装 JDK 21、Maven、Node.js 和 npm
2. 确保 `.env` 文件存在且配置正确
3. 检查网络连接，确保能够下载依赖
4. 查看构建日志中的错误信息

### Q: 如何在 Docker 中使用？

A: 可以使用 `docker-build.sh` 脚本构建 Docker 镜像，参考 `DOCKER_README.md` 文件。

## 优势

- **简化部署**：只需一个 JAR 包，无需单独部署前端
- **降低运维成本**：减少服务器资源占用，降低配置复杂度
- **便于分发**：单个文件即可分发完整的前后端功能
- **快速启动**：一键启动，无需额外配置
