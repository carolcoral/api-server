#!/bin/bash

echo "=========================================="
echo "API Server 运行脚本 v2.3.3"
echo "=========================================="

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_success() { echo -e "${GREEN}[✓]${NC} $1"; }
print_error()   { echo -e "${RED}[✗]${NC} $1"; }
print_info()    { echo -e "${YELLOW}[→]${NC} $1"; }

# 获取脚本所在目录
if [ -L "$0" ]; then
    SCRIPT_PATH="$(readlink -f "$0" 2>/dev/null || readlink "$0" 2>/dev/null || echo "$0")"
else
    SCRIPT_PATH="$0"
fi
SCRIPT_DIR="$(cd "$(dirname "$SCRIPT_PATH")" 2>/dev/null && pwd)"
cd "$SCRIPT_DIR" 2>/dev/null || { print_error "无法进入脚本目录: $SCRIPT_DIR"; exit 1; }

# 查找 jar 包
find_jar() {
    local jar=""
    jar=$(find "$SCRIPT_DIR" -maxdepth 1 -name "api-server-*.jar" -type f 2>/dev/null | head -n 1)
    [ -n "$jar" ] && [ -f "$jar" ] && { echo "$jar"; return 0; }
    jar=$(find "$SCRIPT_DIR/backend/target" -maxdepth 1 -name "api-server-*.jar" -type f 2>/dev/null | head -n 1)
    [ -n "$jar" ] && [ -f "$jar" ] && { echo "$jar"; return 0; }
    return 1
}

JAR_FILE=$(find_jar)

if [ -z "$JAR_FILE" ]; then
    print_info "未找到 jar 包，开始构建..."
    bash ./build-all-in-one.sh || { print_error "构建失败"; exit 1; }
    JAR_FILE=$(find_jar)
else
    print_info "jar 包已存在，跳过构建"
fi

# 加载 .env
if [ -f ".env" ]; then
    export $(cat .env | grep -v '^#' | grep -v '^$' | xargs)
else
    print_error "未找到 .env 配置文件"
    exit 1
fi

SERVER_PORT=${SERVER_PORT:-8080}
FRONTEND_PORT=${FRONTEND_PORT:-3000}

# 检测操作系统
OS="$(uname -s)"
case "${OS}" in
    Linux*)     PLATFORM=Linux;;
    Darwin*)    PLATFORM=Mac;;
    CYGWIN*|MINGW32*|MSYS*|MINGW*) PLATFORM=Windows;;
    *)          PLATFORM="UNKNOWN:${OS}"
esac

# 自动检测 Java 21
INTERNAL_JAVA_HOME=""
auto_set_java_home() {
    # 优先检查已设置的 INTERNAL_JAVA_HOME
    if [ -n "$INTERNAL_JAVA_HOME" ] && [ -x "$INTERNAL_JAVA_HOME/bin/java" ]; then
        JAVA_VERSION=$($INTERNAL_JAVA_HOME/bin/java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
        [ "$JAVA_VERSION" = "21" ] && return 0
    fi

    # 检查全局 JAVA_HOME
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        JAVA_VERSION=$($JAVA_HOME/bin/java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
        if [ "$JAVA_VERSION" = "21" ]; then
            INTERNAL_JAVA_HOME="$JAVA_HOME"
            return 0
        fi
    fi

    case "${PLATFORM}" in
        Mac)
            if command -v /usr/libexec/java_home >/dev/null 2>&1; then
                INTERNAL_JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null)
                [ -n "$INTERNAL_JAVA_HOME" ] && [ -x "$INTERNAL_JAVA_HOME/bin/java" ] && return 0
            fi
            for prefix in /usr/local /opt/homebrew; do
                if [ -d "$prefix/Cellar/openjdk@21" ]; then
                    INTERNAL_JAVA_HOME=$(find "$prefix/Cellar/openjdk@21" -name "libexec" -type d 2>/dev/null | head -n 1)
                    [ -n "$INTERNAL_JAVA_HOME" ] && INTERNAL_JAVA_HOME="${INTERNAL_JAVA_HOME}/openjdk.jdk/Contents/Home"
                    [ -n "$INTERNAL_JAVA_HOME" ] && [ -x "$INTERNAL_JAVA_HOME/bin/java" ] && return 0
                fi
            done
            ;;
        Linux)
            for java_path in \
                "/usr/lib/jvm/java-21-openjdk-amd64" \
                "/usr/lib/jvm/java-21-openjdk" \
                "/usr/lib/jvm/jdk-21" \
                "/opt/jdk-21" \
                "/usr/lib/jvm/temurin-21-jdk-amd64" \
                "/usr/lib/jvm/jdk-21-oracle-x64"; do
                if [ -d "$java_path" ]; then
                    INTERNAL_JAVA_HOME="$java_path"
                    break
                fi
            done
            ;;
        Windows)
            for java_path in "/c/Program Files/Java/jdk-21" "/c/Program Files/Java/jdk-21.0"; do
                if [ -d "$java_path" ]; then
                    INTERNAL_JAVA_HOME="$java_path"
                    break
                fi
            done
            ;;
    esac

    if [ -n "$INTERNAL_JAVA_HOME" ] && [ -x "$INTERNAL_JAVA_HOME/bin/java" ]; then
        JAVA_VERSION=$($INTERNAL_JAVA_HOME/bin/java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
        if [ "$JAVA_VERSION" = "21" ]; then
            return 0
        fi
    fi

    print_error "未找到 Java 21，请确保已安装 JDK 21"
    return 1
}

auto_set_java_home || exit 1
print_success "Java 21: $INTERNAL_JAVA_HOME"

# 确认 jar 包
JAR_FILE=$(find_jar)
if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
    print_error "jar 包不存在，请先构建: ./build-all-in-one.sh"
    exit 1
fi

# 工作目录
JAR_DIR=$(dirname "$JAR_FILE")
if [ "$JAR_DIR" = "$SCRIPT_DIR" ]; then
    DATA_DIR="$SCRIPT_DIR/data"
    LOG_DIR="$SCRIPT_DIR/logs"
else
    DATA_DIR="$SCRIPT_DIR/backend/data"
    LOG_DIR="$SCRIPT_DIR/backend/logs"
fi
mkdir -p "$DATA_DIR" "$LOG_DIR"

PID_FILE="$SCRIPT_DIR/.pid"

# 停止已有进程
if [ -f "$PID_FILE" ]; then
    PID_FROM_FILE=$(cat "$PID_FILE" 2>/dev/null)
    if [ ! -z "$PID_FROM_FILE" ] && kill -0 "$PID_FROM_FILE" 2>/dev/null; then
        print_info "停止已有进程 (PID: $PID_FROM_FILE)..."
        kill "$PID_FROM_FILE" 2>/dev/null
        sleep 2
        kill -0 "$PID_FROM_FILE" 2>/dev/null && { kill -9 "$PID_FROM_FILE" 2>/dev/null; sleep 1; }
    else
        rm -f "$PID_FILE"
    fi
fi

# 兜底：通过端口清理（兼容多种 ss/netstat/lsof 输出格式）
OLD_PID=""
if command -v lsof >/dev/null 2>&1; then
    OLD_PID=$(lsof -ti:$SERVER_PORT -sTCP:LISTEN 2>/dev/null | head -1)
fi
if [ -z "$OLD_PID" ] && command -v ss >/dev/null 2>&1; then
    # ss 输出格式：users:(("java",pid=12345,fd=42))  或  pid=12345
    OLD_PID=$(ss -tlnp 2>/dev/null | grep -E "[:,]${SERVER_PORT}\b" | sed -n 's/.*pid=\([0-9]\+\).*/\1/p' | head -1)
fi
if [ -z "$OLD_PID" ] && command -v netstat >/dev/null 2>&1; then
    OLD_PID=$(netstat -tlnp 2>/dev/null | grep -E "[:,]${SERVER_PORT}\b" | awk '{print $NF}' | cut -d'/' -f1 | head -1)
fi
if [ -n "$OLD_PID" ] && [ "$OLD_PID" != "$PID_FROM_FILE" ]; then
    kill "$OLD_PID" 2>/dev/null
    sleep 2
    kill -0 "$OLD_PID" 2>/dev/null && kill -9 "$OLD_PID" 2>/dev/null
fi

# 构造数据库和日志路径参数
DB_TYPE=${DB_TYPE:-sqlite}
if [ "$JAR_DIR" = "$SCRIPT_DIR" ]; then
    JAVA_DB_URL="jdbc:sqlite:./data/api-server.db"
    JAVA_LOG_PATH="./logs/api-server.log"
else
    JAVA_DB_URL="jdbc:sqlite:./backend/data/api-server.db"
    JAVA_LOG_PATH="./backend/logs/api-server.log"
fi

# 启动参数：仅 SQLite 模式需要覆盖 DB_URL 路径，MySQL/PostgreSQL 由 profile YAML 自动构建
JAVA_OPTS="-DLOG_FILE_PATH=$JAVA_LOG_PATH"
# 传递关键配置为系统属性（StartupConfig 通过 System.getProperty() 读取）
JAVA_OPTS="$JAVA_OPTS -DADMIN_USERNAME=$ADMIN_USERNAME -DADMIN_PASSWORD=$ADMIN_PASSWORD -DADMIN_EMAIL=$ADMIN_EMAIL"
JAVA_OPTS="$JAVA_OPTS -DJWT_SECRET=$JWT_SECRET -DJWT_EXPIRATION=$JWT_EXPIRATION"
JAVA_OPTS="$JAVA_OPTS -DSWAGGER_USERNAME=$SWAGGER_USERNAME -DSWAGGER_PASSWORD=$SWAGGER_PASSWORD"
if [ "$DB_TYPE" = "sqlite" ]; then
    JAVA_OPTS="$JAVA_OPTS -DDB_URL=$JAVA_DB_URL"
fi

# 启动服务
print_info "启动后端服务 (端口: $SERVER_PORT, 数据库: $DB_TYPE)..."
nohup "$INTERNAL_JAVA_HOME/bin/java" \
    $JAVA_OPTS \
    -jar "$JAR_FILE" \
    > "$LOG_DIR/server.log" 2>&1 &

NEW_PID=$!
echo "$NEW_PID" > "$PID_FILE"
print_success "进程已启动 (PID: $NEW_PID)"

# 等待启动
print_info "等待服务就绪（最长等待约 120 秒）..."
sleep 8

# ========== 端口监听检测工具函数 ==========
port_listening() {
    local port="$1"
    # 方式a: /dev/tcp (bash 内置，最可靠)
    (echo >/dev/tcp/localhost/${port}) 2>/dev/null && return 0
    # 方式b: ss
    if command -v ss >/dev/null 2>&1; then
        ss -tlnp 2>/dev/null | grep -qE "[:,]${port}\b" && return 0
    fi
    # 方式c: netstat
    if command -v netstat >/dev/null 2>&1; then
        netstat -tlnp 2>/dev/null | grep -qE "[:,]${port}\b" && return 0
    fi
    # 方式d: lsof
    if command -v lsof >/dev/null 2>&1; then
        lsof -i:${port} -sTCP:LISTEN >/dev/null 2>&1 && return 0
    fi
    return 1
}

# 检测服务状态
HEALTH_URL="http://localhost:${SERVER_PORT}/actuator/health"
FALLBACK_URL="http://localhost:${SERVER_PORT}/api/v3/api-docs"
MAX_RETRIES=36
RETRY_COUNT=0
STARTED=false
PID_ALIVE=false

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    RETRY_COUNT=$((RETRY_COUNT + 1))

    # 先确认进程是否还活着（存活是前提）
    if [ -f "$PID_FILE" ]; then
        PID_CHECK=$(cat "$PID_FILE" 2>/dev/null)
        if [ -n "$PID_CHECK" ] && kill -0 "$PID_CHECK" 2>/dev/null; then
            PID_ALIVE=true
        else
            print_error "进程已退出，启动失败"
            exit 1
        fi
    fi

    # 方案1: 进程存活 + 端口已监听 → 服务已就绪（最可靠的判断）
    if [ "$PID_ALIVE" = true ] && port_listening "$SERVER_PORT"; then
        STARTED=true; break
    fi

    # 方案2: curl HTTP 可达（可选，仅当 curl 可用时辅助验证）
    if command -v curl >/dev/null 2>&1; then
        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 --noproxy '*' "$HEALTH_URL" 2>/dev/null)
        if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "302" ]; then
            STARTED=true; break
        fi
        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 --noproxy '*' "$FALLBACK_URL" 2>/dev/null)
        if [ "$HTTP_CODE" != "000" ] && [ "$HTTP_CODE" != "000" ]; then
            STARTED=true; break
        fi
    fi

    sleep 2
done

if [ "$STARTED" = true ]; then
    print_success "服务启动成功"
else
    print_error "服务启动超时，请查看日志: $LOG_DIR/server.log"
    print_info "提示：如果日志显示应用已启动，可能是端口监听检测失败，请检查防火墙/SELinux 是否拦截本地连接。"
    exit 1
fi

echo ""
echo "=========================================="
print_success "API Server 已启动！"
echo "=========================================="
echo "  访问地址:  http://localhost:${SERVER_PORT}"
echo "  API 文档:  http://localhost:${SERVER_PORT}/swagger-ui.html"
echo "  运行日志:  $LOG_DIR/server.log"
echo "  停止服务:  kill \$(cat $PID_FILE)"
echo "=========================================="
