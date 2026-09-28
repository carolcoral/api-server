#!/bin/bash

echo "=========================================="
echo "API Server 运行脚本 v2.4.1"
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
# 使用 set -a + source 的方式，避免 xargs/词分割对含特殊字符（*, &, ! 等）的密码造成破坏
if [ -f ".env" ]; then
    set -a
    # shellcheck disable=SC1091
    . "./.env"
    set +a
else
    print_error "未找到 .env 配置文件"
    exit 1
fi

SERVER_PORT=${SERVER_PORT:-8080}
FRONTEND_PORT=${FRONTEND_PORT:-3000}

# ========== 管理员账号自检与自动创建 ==========
# 说明：
#   每次启动前检查数据库中是否已存在管理员账号（默认用户名 admin）。
#   若不存在则在 .env 中写入一个随机强密码（每次生成都不同），并在脚本日志中输出一次；
#   已存在（或已生成过）时不再输出，避免密码每次启动都刷屏。
ADMIN_DIR="$(dirname "$JAR_FILE")"
if [ "$ADMIN_DIR" = "$SCRIPT_DIR" ]; then
    ADMIN_DB_FILE="$SCRIPT_DIR/data/mock-server.db"
else
    ADMIN_DB_FILE="$SCRIPT_DIR/backend/data/mock-server.db"
fi

# 解析 .env 中指定 key 的当前值（去注释、去首尾空白、去引号）
env_get() {
    local key="$1" file="$2"
    [ -f "$file" ] || return 1
    sed -n "s/^[[:space:]]*${key}[[:space:]]*=[[:space:]]*//p" "$file" | tail -n 1 | sed 's/^"//; s/"$//; s/^'"'"'//; s/'"'"'$//'
}

# 写入/更新 .env 中指定 key 的值（存在则替换，不存在则追加）
env_set() {
    local key="$1" value="$2" file="$3"
    if grep -qE "^[[:space:]]*${key}[[:space:]]*=" "$file" 2>/dev/null; then
        # 用 | 作为 sed 分隔符，并对值中的特殊字符转义（& \ |）
        local esc
        esc=$(printf '%s' "$value" | sed -e 's/[&\\|]/\\&/g')
        sed -i.bak -E "s|^[[:space:]]*${key}[[:space:]]*=.*|${key}=${esc}|" "$file" && rm -f "${file}.bak"
    else
        printf '%s=%s\n' "$key" "$value" >> "$file"
    fi
}

# 从字符集中随机取一个字符
pick_char() {
    local set="$1" len="${#1}" n
    n=$(od -An -N2 -tu2 < /dev/urandom | tr -d ' ')
    printf '%s' "${set:$((n % len)):1}"
}

# 生成强随机密码：大小写字母 + 数字 + 特殊字符，长度 20
# 逐个字符集取样，确保每类字符至少出现一次，再打乱顺序
gen_strong_password() {
    local upper='ABCDEFGHJKLMNPQRSTUVWXYZ'
    local lower='abcdefghijkmnpqrstuvwxyz'
    local digit='23456789'
    local special='@$%!*?&'
    local all="${upper}${lower}${digit}${special}"
    local pwd="" i idx

    pwd="${pwd}$(pick_char "$upper")"
    pwd="${pwd}$(pick_char "$lower")"
    pwd="${pwd}$(pick_char "$digit")"
    pwd="${pwd}$(pick_char "$special")"
    for i in $(seq 1 16); do
        pwd="${pwd}$(pick_char "$all")"
    done

    # 打乱顺序（基于 /dev/urandom，兼容无 shuf 的环境）
    pwd="$(printf '%s' "$pwd" | awk '
        BEGIN { srand(); }
        { n=split($0,c,"");
          for(i=n;i>1;i--){ j=int(rand()*i)+1; t=c[i]; c[i]=c[j]; c[j]=t; }
          for(i=1;i<=n;i++) printf "%s", c[i];
        }')"
    printf '%s' "$pwd"
}

# 判断密码是否满足后端强密码规则：≥8 位，含大小写、数字、特殊字符（@$!%*?&）
is_strong_password() {
    local p="$1"
    [ "${#p}" -ge 8 ] || return 1
    printf '%s' "$p" | grep -q '[A-Z]' || return 1
    printf '%s' "$p" | grep -q '[a-z]' || return 1
    printf '%s' "$p" | grep -q '[0-9]' || return 1
    printf '%s' "$p" | grep -q '[@$!%*?&]' || return 1
    return 0
}

# 检测数据库中是否已存在管理员账号，返回 0=存在
# 优先使用 sqlite3 CLI，其次 python3，最后退化为 .env 标记判断
admin_exists_in_db() {
    local db="$1" user="$2"
    [ -f "$db" ] || return 1

    if command -v sqlite3 >/dev/null 2>&1; then
        local cnt
        cnt=$(sqlite3 "$db" "SELECT COUNT(*) FROM t_user WHERE username='${user}';" 2>/dev/null)
        [ -n "$cnt" ] && [ "$cnt" -gt 0 ] 2>/dev/null && return 0
        return 1
    fi

    if command -v python3 >/dev/null 2>&1; then
        python3 - "$db" "$user" <<'PY' 2>/dev/null
import sqlite3, sys
db, user = sys.argv[1], sys.argv[2]
try:
    conn = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
    cur = conn.cursor()
    cur.execute("SELECT COUNT(*) FROM t_user WHERE username=?", (user,))
    sys.exit(0 if cur.fetchone()[0] > 0 else 1)
except Exception:
    sys.exit(2)
PY
        local rc=$?
        [ "$rc" -eq 0 ] && return 0
        return 1
    fi

    # 无可用 SQLite 工具，交给 .env 标记兜底
    return 2
}

ENV_FILE="$SCRIPT_DIR/.env"
ADMIN_USER="${ADMIN_USERNAME:-admin}"
# 标记：.env 中的密码是否由本脚本自动生成（用于「每次生成新密码、但只显示第一次」）
AUTO_GEN_FLAG="$(env_get ADMIN_PASSWORD_AUTO_GENERATED "$ENV_FILE" 2>/dev/null)"
CURRENT_PASSWORD="$(env_get ADMIN_PASSWORD "$ENV_FILE" 2>/dev/null)"

DB_ADMIN_STATE=1
admin_exists_in_db "$ADMIN_DB_FILE" "$ADMIN_USER" && DB_ADMIN_STATE=0

if [ "$DB_ADMIN_STATE" -eq 0 ]; then
    # 数据库中已存在管理员账号：不生成、不输出、不修改 .env
    print_info "管理员账号已存在: $ADMIN_USER（跳过密码生成）"
elif [ -n "$CURRENT_PASSWORD" ] && [ "$AUTO_GEN_FLAG" != "1" ]; then
    # 用户已在 .env 中手动配置了固定密码：尊重用户配置，不覆盖
    print_info "管理员账号待创建，使用 .env 中已配置的固定密码"
    export ADMIN_USERNAME="$ADMIN_USER"
    export ADMIN_PASSWORD="$CURRENT_PASSWORD"
else
    # 需要自动生成：管理员不存在时，每次运行都生成一个不同的强密码
    NEW_ADMIN_PASSWORD="$(gen_strong_password)"
    if ! is_strong_password "$NEW_ADMIN_PASSWORD"; then
        print_error "生成的密码未通过强度校验，请重试或手动设置 ADMIN_PASSWORD"
        exit 1
    fi

    env_set "ADMIN_USERNAME" "$ADMIN_USER" "$ENV_FILE"
    env_set "ADMIN_PASSWORD" "$NEW_ADMIN_PASSWORD" "$ENV_FILE"
    if [ -z "$(env_get ADMIN_EMAIL "$ENV_FILE" 2>/dev/null)" ]; then
        env_set "ADMIN_EMAIL" "admin@mockserver.com" "$ENV_FILE"
    fi

    # 同步到当前环境，供 Java 启动参数使用
    export ADMIN_USERNAME="$ADMIN_USER"
    export ADMIN_PASSWORD="$NEW_ADMIN_PASSWORD"

    if [ "$AUTO_GEN_FLAG" != "1" ]; then
        # 仅第一次（首次生成）输出密码
        env_set "ADMIN_PASSWORD_AUTO_GENERATED" "1" "$ENV_FILE"
        echo ""
        echo "=========================================="
        print_success "首次创建管理员账号，已生成随机强密码"
        echo "  用户名: $ADMIN_USER"
        echo "  密  码: $NEW_ADMIN_PASSWORD"
        echo "  （密码已写入 .env，仅本次显示，请妥善保存）"
        echo "=========================================="
        echo ""
    else
        # 非首次：密码已更新，但不重复显示
        print_info "管理员账号待创建，已更新随机强密码（不再重复显示，详见 .env）"
    fi
fi
# ========== 管理员账号自检结束 ==========

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
