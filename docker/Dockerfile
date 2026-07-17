# ========================================
# API Server - Debian 构建版
# ========================================

FROM debian:12-slim

LABEL maintainer="carolcoral"
LABEL description="API Server v2.1.2"

# ---- 基础依赖 + Eclipse Temurin JDK 21 + Maven + Node.js ----
# 一次性装完所有构建依赖，setup-env.sh 检测到全部就绪后直接跳过
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl ca-certificates gnupg sudo bash procps tzdata wget gosu \
    && mkdir -p /etc/apt/keyrings \
    && wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public \
    | gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg \
    && echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb bookworm main" \
    > /etc/apt/sources.list.d/adoptium.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends temurin-21-jdk maven nodejs npm \
    && ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# ---- 确保 Java 21 优先 ----
ENV JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
ENV PATH=$JAVA_HOME/bin:$PATH

# ---- 复制源码 ----
COPY . /app/build
WORKDIR /app/build

# ---- 配置 Maven 阿里云镜像加速 ----
RUN mkdir -p /root/.m2 \
    && printf '<?xml version="1.0" encoding="UTF-8"?>\n\
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"\n\
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"\n\
  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">\n\
  <mirrors>\n\
    <mirror>\n\
      <id>aliyunmaven</id>\n\
      <mirrorOf>central</mirrorOf>\n\
      <name>阿里云公共仓库</name>\n\
      <url>https://maven.aliyun.com/repository/public</url>\n\
    </mirror>\n\
  </mirrors>\n\
</settings>\n' > /root/.m2/settings.xml

# ---- 构建 ----
# JDK 21、Maven、Node.js、npm 已全部预装，跳过 setup-env.sh 直接构建
RUN bash build-all-in-one.sh

# ---- 保留产物，删除源码 ----
RUN mkdir -p /app/data /app/logs /app/frontend/dist \
    && cp backend/target/api-server-*.jar /app/api-server.jar \
    && cp -r frontend/dist/* /app/frontend/dist/ \
    && rm -rf /app/build \
    && rm -rf /root/.m2 /root/.npm /root/.cache /root/.sdkman

# ---- 非 root 用户 ----
RUN groupadd -r appgroup && useradd -r -g appgroup -d /app -s /bin/sh appuser

# ---- 启动脚本 ----
# 以 root 启动时先修复 volume 目录权限，再用 gosu 切换到 appuser 运行 Java
# gosu 比 su 更适合容器环境，避免 seccomp 阻止 setgroups/setuid 系统调用
RUN printf '#!/bin/sh\n\
# 确保数据目录存在且权限正确（支持以 root 启动时自动修复）\n\
mkdir -p /app/data /app/logs\n\
if [ "$(id -u)" = "0" ]; then\n\
    chown -R appuser:appgroup /app/data /app/logs 2>/dev/null || true\n\
    exec gosu appuser java ${JAVA_OPTS} -jar /app/api-server.jar --server.port=8080\n\
else\n\
    exec java ${JAVA_OPTS} -jar /app/api-server.jar --server.port=8080\n\
fi\n' > /app/start.sh \
    && chmod +x /app/start.sh \
    && chown -R appuser:appgroup /app

USER appuser
WORKDIR /app
EXPOSE 8080

ENV JAVA_OPTS="-Xmx512m -Xms256m"
VOLUME ["/app/data", "/app/logs"]

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -sf http://localhost:8080/api/actuator/health || exit 1

ENTRYPOINT ["/app/start.sh"]
