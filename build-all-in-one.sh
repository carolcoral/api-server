#!/bin/bash

echo "=========================================="
echo "Mock Server 一键构建脚本"
echo "作者: carolcoral"
echo "=========================================="

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 打印彩色信息
print_success() {
    echo -e "${GREEN}[成功]${NC} $1"
}

print_error() {
    echo -e "${RED}[错误]${NC} $1"
}

print_info() {
    echo -e "${YELLOW}[信息]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[警告]${NC} $1"
}

# 项目版本要求
REQUIRED_NODE_MAJOR=18
REQUIRED_NPM_MAJOR=9
REQUIRED_MAVEN_MAJOR=3
REQUIRED_MAVEN_MINOR=6

# 获取脚本所在目录的绝对路径（项目根目录）
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_ROOT"

# 检测操作系统
detect_platform() {
    local os
    os="$(uname -s)"
    case "${os}" in
        Linux*)     echo "Linux";;
        Darwin*)    echo "Mac";;
        CYGWIN*|MINGW32*|MSYS*|MINGW*) echo "Windows";;
        *)          echo "Unknown";;
    esac
}
PLATFORM=$(detect_platform)

# ==========================================
# 第一步：检测基础环境
# ==========================================
print_info "=========================================="
print_info "第一步：检测基础环境..."
print_info "=========================================="

check_command() {
    local cmd="$1"
    local name="$2"
    if ! command -v "$cmd" >/dev/null 2>&1; then
        print_error "未找到 ${name}，请先安装后重试"
        exit 1
    fi
    local version_info
    version_info=$("$cmd" --version 2>&1 | head -n 1)
    print_success "${name} 已安装: ${version_info}"
}

# 多版本 JDK 环境下自动检测并设置 Java 21
check_java21() {
    print_info "检测 JDK 21 环境（支持多版本 JDK）..."

    # 方法1: 检查 JAVA_HOME 是否指向 JDK 21
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        JAVA_VER=$("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
        if [ "$JAVA_VER" = "21" ]; then
            export JAVA_HOME="$JAVA_HOME"
            export PATH="$JAVA_HOME/bin:$PATH"
            print_success "JDK 21 已就绪 (JAVA_HOME): $JAVA_HOME"
            print_info "  Java 版本: $("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"
            return 0
        fi
    fi

    # 方法2: 检查系统 PATH 中默认的 java 是否为 21
    if command -v java >/dev/null 2>&1; then
        DEFAULT_JAVA_VER=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
        if [ "$DEFAULT_JAVA_VER" = "21" ]; then
            DEFAULT_JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(command -v java)" 2>/dev/null || command -v java)")")
            export JAVA_HOME="$DEFAULT_JAVA_HOME"
            export PATH="$JAVA_HOME/bin:$PATH"
            print_success "JDK 21 已就绪 (系统默认): $JAVA_HOME"
            print_info "  Java 版本: $(java -version 2>&1 | head -n 1)"
            return 0
        fi
    fi

    # 方法3: 系统默认 java 不是 21，在多版本环境中查找 JDK 21
    print_info "系统默认 Java 不是 JDK 21，在多版本环境中查找..."

    case "${PLATFORM}" in
        Mac)
            # macOS: 使用 java_home 命令列出所有已安装的 JDK
            if command -v /usr/libexec/java_home >/dev/null 2>&1; then
                # 先尝试精确匹配 21
                JAVA21_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null)
                if [ -n "$JAVA21_HOME" ] && [ -x "$JAVA21_HOME/bin/java" ]; then
                    export JAVA_HOME="$JAVA21_HOME"
                    export PATH="$JAVA_HOME/bin:$PATH"
                    print_success "JDK 21 已就绪 (java_home): $JAVA_HOME"
                    print_info "  Java 版本: $("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"
                    return 0
                fi

                # 列出所有已安装的 JDK，查找 21
                print_info "已安装的 JDK 列表:"
                /usr/libexec/java_home -V 2>&1 | grep -E '^\s+\d' | while read -r line; do
                    print_info "  $line"
                done
            fi

            # 备用方案: 查找 Homebrew 安装的 JDK 21
            for brew_prefix in "/opt/homebrew" "/usr/local"; do
                if [ -d "$brew_prefix/Cellar/openjdk@21" ]; then
                    JAVA21_HOME=$(find "$brew_prefix/Cellar/openjdk@21" -name "libexec" -type d 2>/dev/null | head -n 1)
                    if [ -n "$JAVA21_HOME" ]; then
                        JAVA21_HOME="${JAVA21_HOME}/openjdk.jdk/Contents/Home"
                    fi
                    # 也尝试直接查找 jdk 目录
                    if [ -z "$JAVA21_HOME" ] || [ ! -x "$JAVA21_HOME/bin/java" ]; then
                        JAVA21_HOME=$(find "$brew_prefix/Cellar/openjdk@21" -maxdepth 2 -name "Home" -path "*/Contents/*" -type d 2>/dev/null | head -n 1)
                    fi
                    if [ -n "$JAVA21_HOME" ] && [ -x "$JAVA21_HOME/bin/java" ]; then
                        export JAVA_HOME="$JAVA21_HOME"
                        export PATH="$JAVA_HOME/bin:$PATH"
                        print_success "JDK 21 已就绪 (Homebrew): $JAVA_HOME"
                        print_info "  Java 版本: $("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"
                        return 0
                    fi
                fi
            done
            ;;

        Linux)
            # Linux: 扫描常见 JDK 安装路径，检测多版本
            JVM_BASE="/usr/lib/jvm"
            if [ -d "$JVM_BASE" ]; then
                print_info "扫描 $JVM_BASE 下的 JDK..."
                # 按优先级排列的路径模式
                for pattern in \
                    "java-21-openjdk-amd64" \
                    "java-21-openjdk" \
                    "java-21-openjdk-*" \
                    "jdk-21" \
                    "jdk-21*" \
                    "temurin-21-jdk-amd64" \
                    "jdk-21-oracle-x64" \
                    "java-21-*"; do
                    for java_path in "$JVM_BASE"/$pattern; do
                        if [ -d "$java_path" ] && [ -x "$java_path/bin/java" ]; then
                            JAVA_VER=$("$java_path/bin/java" -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
                            if [ "$JAVA_VER" = "21" ]; then
                                export JAVA_HOME="$java_path"
                                export PATH="$JAVA_HOME/bin:$PATH"
                                print_success "JDK 21 已就绪: $JAVA_HOME"
                                print_info "  Java 版本: $("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"
                                return 0
                            fi
                        fi
                    done
                done

                # 列出所有已安装的 JDK，帮助用户排查
                print_info "已安装的 JDK 列表:"
                for jvm_dir in "$JVM_BASE"/*/; do
                    if [ -x "$jvm_dir/bin/java" ]; then
                        jvm_ver=$("$jvm_dir/bin/java" -version 2>&1 | head -n 1 | cut -d'"' -f2)
                        print_info "  $jvm_dir ($jvm_ver)"
                    fi
                done
            fi

            # 额外扫描 /opt 下的 JDK
            for opt_dir in /opt/jdk-21 /opt/jdk-21*; do
                if [ -d "$opt_dir" ] && [ -x "$opt_dir/bin/java" ]; then
                    JAVA_VER=$("$opt_dir/bin/java" -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
                    if [ "$JAVA_VER" = "21" ]; then
                        export JAVA_HOME="$opt_dir"
                        export PATH="$JAVA_HOME/bin:$PATH"
                        print_success "JDK 21 已就绪: $JAVA_HOME"
                        print_info "  Java 版本: $("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"
                        return 0
                    fi
                fi
            done

            # sdkman 安装的 JDK
            if [ -d "$HOME/.sdkman/candidates/java" ]; then
                for sdk_java in "$HOME/.sdkman/candidates/java"/21.*/; do
                    if [ -d "$sdk_java" ] && [ -x "$sdk_java/bin/java" ]; then
                        JAVA_VER=$("$sdk_java/bin/java" -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
                        if [ "$JAVA_VER" = "21" ]; then
                            export JAVA_HOME="$sdk_java"
                            export PATH="$JAVA_HOME/bin:$PATH"
                            print_success "JDK 21 已就绪 (sdkman): $JAVA_HOME"
                            print_info "  Java 版本: $("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"
                            return 0
                        fi
                    fi
                done
            fi
            ;;

        Windows)
            for win_path in \
                "/c/Program Files/Java/jdk-21" \
                "/c/Program Files/Java/jdk-21.0" \
                "/c/Program Files (x86)/Java/jdk-21"; do
                if [ -d "$win_path" ] && [ -x "$win_path/bin/java" ]; then
                    export JAVA_HOME="$win_path"
                    export PATH="$JAVA_HOME/bin:$PATH"
                    print_success "JDK 21 已就绪: $JAVA_HOME"
                    print_info "  Java 版本: $("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"
                    return 0
                fi
            done
            ;;
    esac

    # 未找到 JDK 21
    print_error "未找到 JDK 21，请先安装 JDK 21"
    echo ""
    print_info "系统默认 Java 版本: $(java -version 2>&1 | head -n 1 2>/dev/null || echo '未安装')"
    print_info ""
    print_info "安装方式:"
    print_info "  1. 运行 ./setup-env.sh 自动安装 JDK 21"
    print_info "  2. 手动下载: https://adoptium.net/download/"
    print_info "  3. 使用 SDKMAN: curl -s \"https://get.sdkman.io\" | bash && sdk install java 21.0.5-tem"
    exit 1
}

check_java21

# ==========================================
# 多版本 Maven 自动检测与切换
# ==========================================
check_maven() {
    print_info "检测 Maven 环境（支持多版本 Maven，要求 >= ${REQUIRED_MAVEN_MAJOR}.${REQUIRED_MAVEN_MINOR}）..."

    # 查找最佳 Maven 版本的函数
    find_best_mvn() {
        local best_home=""
        local best_major=0
        local best_minor=0

        for candidate_home in "$@"; do
            if [ -d "$candidate_home" ] && [ -x "$candidate_home/bin/mvn" ]; then
                local ver_output
                ver_output=$("$candidate_home/bin/mvn" --version 2>&1 | head -n 1)
                local ver
                ver=$(echo "$ver_output" | sed -n 's/.*Apache Maven \([0-9]\+\.[0-9]\+\).*/\1/p')
                if [ -n "$ver" ]; then
                    local major minor
                    major=$(echo "$ver" | cut -d'.' -f1)
                    minor=$(echo "$ver" | cut -d'.' -f2)
                    # 必须满足最低版本要求
                    if [ "$major" -gt "$REQUIRED_MAVEN_MAJOR" ] || \
                       ([ "$major" -eq "$REQUIRED_MAVEN_MAJOR" ] && [ "$minor" -ge "$REQUIRED_MAVEN_MINOR" ]); then
                        # 选择满足要求的最高版本
                        if [ "$major" -gt "$best_major" ] || \
                           ([ "$major" -eq "$best_major" ] && [ "$minor" -gt "$best_minor" ]); then
                            best_major=$major
                            best_minor=$minor
                            best_home="$candidate_home"
                        fi
                    fi
                fi
            fi
        done
        echo "$best_home"
    }

    # 方法1: 检查当前 PATH 中的 mvn
    if command -v mvn >/dev/null 2>&1; then
        local current_ver
        current_ver=$(mvn --version 2>&1 | head -n 1 | sed -n 's/.*Apache Maven \([0-9]\+\.[0-9]\+\).*/\1/p')
        if [ -n "$current_ver" ]; then
            local cur_major cur_minor
            cur_major=$(echo "$current_ver" | cut -d'.' -f1)
            cur_minor=$(echo "$current_ver" | cut -d'.' -f2)
            if [ "$cur_major" -gt "$REQUIRED_MAVEN_MAJOR" ] || \
               ([ "$cur_major" -eq "$REQUIRED_MAVEN_MAJOR" ] && [ "$cur_minor" -ge "$REQUIRED_MAVEN_MINOR" ]); then
                print_success "Maven $current_ver 已就绪 (系统默认)"
                print_info "  $(mvn --version 2>&1 | head -n 1)"
                return 0
            else
                print_warning "系统默认 Maven $current_ver 低于要求的 ${REQUIRED_MAVEN_MAJOR}.${REQUIRED_MAVEN_MINOR}，搜索其他版本..."
            fi
        fi
    fi

    # 方法2: 扫描常见 Maven 安装路径
    local maven_candidates=""

    # Linux 常见路径
    if [ "$PLATFORM" = "Linux" ]; then
        for base in /usr/share/maven /opt/maven /usr/local/maven /usr/local/apache-maven "$HOME/maven" "$HOME/apache-maven"; do
            if [ -d "$base" ] && [ -x "$base/bin/mvn" ]; then
                maven_candidates="$maven_candidates $base"
            fi
        done
        # 通配符匹配版本号目录
        for pattern in /usr/share/maven-* /opt/maven-* /opt/apache-maven-* /usr/local/maven-* /usr/local/apache-maven-* "$HOME/maven-*" "$HOME/apache-maven-*"; do
            for candidate in $pattern; do
                if [ -d "$candidate" ] && [ -x "$candidate/bin/mvn" ]; then
                    maven_candidates="$maven_candidates $candidate"
                fi
            done
        done
    fi

    # macOS Homebrew 路径
    if [ "$PLATFORM" = "Mac" ]; then
        for prefix in /opt/homebrew /usr/local; do
            for pattern in "$prefix/Cellar/maven"/* "$prefix/opt/maven"*; do
                if [ -d "$pattern" ]; then
                    local libexec="$pattern/libexec"
                    if [ -d "$libexec" ] && [ -x "$libexec/bin/mvn" ]; then
                        maven_candidates="$maven_candidates $libexec"
                    elif [ -x "$pattern/bin/mvn" ]; then
                        maven_candidates="$maven_candidates $pattern"
                    fi
                fi
            done
        done
    fi

    # sdkman 安装的 Maven
    if [ -d "$HOME/.sdkman/candidates/maven" ]; then
        for sdk_maven in "$HOME/.sdkman/candidates/maven"/*/; do
            if [ -d "$sdk_maven" ] && [ -x "$sdk_maven/bin/mvn" ]; then
                maven_candidates="$maven_candidates $sdk_maven"
            fi
        done
    fi

    # 搜索并选择最佳版本
    local best_maven
    best_maven=$(find_best_mvn $maven_candidates)

    if [ -n "$best_maven" ]; then
        export M2_HOME="$best_maven"
        export MAVEN_HOME="$best_maven"
        export PATH="$best_maven/bin:$PATH"
        local best_ver
        best_ver=$(mvn --version 2>&1 | head -n 1 | sed -n 's/.*Apache Maven \([0-9]\+\.[0-9]\+\).*/\1/p')
        print_success "Maven $best_ver 已就绪 (多版本切换): $best_maven"
        print_info "  $(mvn --version 2>&1 | head -n 1)"
        return 0
    fi

    # 未找到合适版本
    print_error "未找到 Maven ${REQUIRED_MAVEN_MAJOR}.${REQUIRED_MAVEN_MINOR}+"
    echo ""
    if command -v mvn >/dev/null 2>&1; then
        print_info "系统当前 Maven 版本: $(mvn --version 2>&1 | head -n 1)"
    fi
    print_info "安装方式:"
    print_info "  1. 运行 ./setup-env.sh 自动安装 Maven"
    print_info "  2. 手动下载: https://maven.apache.org/download.cgi"
    print_info "  3. 使用 SDKMAN: sdk install maven"
    exit 1
}

check_maven

# ==========================================
# 多版本 Node.js 自动检测与切换（含 nvm）
# ==========================================
check_nodejs() {
    print_info "检测 Node.js 环境（支持多版本 Node.js，要求 >= ${REQUIRED_NODE_MAJOR}）..."

    # 方法1: 检查当前 PATH 中的 node 版本
    if command -v node >/dev/null 2>&1; then
        local node_version_output
        node_version_output=$(node --version 2>&1)

        # 检测 glibc 不兼容问题（CentOS 7 glibc 2.17 无法运行 Node.js 18+）
        if echo "$node_version_output" | grep -q "GLIBC"; then
            print_error "Node.js 无法运行：系统 glibc 版本过低"
            echo ""
            print_info "错误详情: $node_version_output"
            echo ""
            print_info "原因: 当前系统 glibc 版本过低（CentOS 7 为 glibc 2.17），"
            print_info "      Node.js 18+ 预编译二进制需要 glibc 2.28+ 才能运行。"
            echo ""
            print_info "解决方案:"
            print_info "  1. 升级系统到 CentOS 8+ / Ubuntu 20.04+ / Debian 11+"
            print_info "  2. 使用 Docker 运行（推荐）: docker-compose up -d"
            print_info "  3. 从源码编译 Node.js（耗时较长，不推荐）"
            exit 1
        fi

        local node_ver
        node_ver=$(echo "$node_version_output" | sed 's/v//')
        local node_major
        node_major=$(echo "$node_ver" | cut -d'.' -f1)
        if [ "$node_major" -ge "$REQUIRED_NODE_MAJOR" ] 2>/dev/null; then
            print_success "Node.js $node_ver 已就绪 (系统默认)"
            print_info "  $(node --version 2>&1)"
            return 0
        else
            print_warning "系统默认 Node.js v$node_ver 低于要求的 v${REQUIRED_NODE_MAJOR}，搜索其他版本..."
        fi
    fi

    # 方法2: 尝试使用 nvm 切换版本
    local nvm_loaded=false

    # 加载 nvm
    load_nvm() {
        if [ -n "$NVM_DIR" ] && [ -s "$NVM_DIR/nvm.sh" ]; then
            \. "$NVM_DIR/nvm.sh" 2>/dev/null
            return $?
        fi
        for nvm_init in "$HOME/.nvm/nvm.sh" "/usr/local/nvm/nvm.sh"; do
            if [ -s "$nvm_init" ]; then
                export NVM_DIR="$(dirname "$nvm_init")"
                \. "$nvm_init" 2>/dev/null
                return $?
            fi
        done
        return 1
    }

    if load_nvm; then
        nvm_loaded=true
        print_info "检测到 nvm，尝试切换到 Node.js $REQUIRED_NODE_MAJOR..."

        # 检查是否已安装目标版本
        if nvm ls "$REQUIRED_NODE_MAJOR" >/dev/null 2>&1; then
            nvm use "$REQUIRED_NODE_MAJOR" 2>/dev/null
            if [ $? -eq 0 ]; then
                local nvm_node_check
                nvm_node_check=$(node --version 2>&1)
                if echo "$nvm_node_check" | grep -q "GLIBC"; then
                    print_warning "nvm Node.js 无法运行：系统 glibc 版本过低，跳过 nvm 路径"
                    print_info "  $nvm_node_check"
                else
                    local node_ver
                    node_ver=$(echo "$nvm_node_check" | sed 's/v//')
                    print_success "Node.js $node_ver 已就绪 (nvm 切换)"
                    print_info "  $nvm_node_check"
                    return 0
                fi
            fi
        else
            print_info "nvm 中未安装 Node.js $REQUIRED_NODE_MAJOR，尝试安装..."
            nvm install "$REQUIRED_NODE_MAJOR" 2>/dev/null
            if [ $? -eq 0 ]; then
                nvm use "$REQUIRED_NODE_MAJOR" 2>/dev/null
                local nvm_node_check
                nvm_node_check=$(node --version 2>&1)
                if echo "$nvm_node_check" | grep -q "GLIBC"; then
                    print_warning "nvm 安装的 Node.js 无法运行：系统 glibc 版本过低，跳过 nvm 路径"
                    print_info "  $nvm_node_check"
                else
                    local node_ver
                    node_ver=$(echo "$nvm_node_check" | sed 's/v//')
                    print_success "Node.js $node_ver 已就绪 (nvm 安装)"
                    print_info "  $nvm_node_check"
                    return 0
                fi
            fi
        fi
    fi

    # 方法3: 扫描常见多版本 Node.js 安装路径
    local node_candidates=""

    # Linux: n 版本管理器
    if [ "$PLATFORM" = "Linux" ] || [ "$PLATFORM" = "Mac" ]; then
        if [ -d "/usr/local/n/versions/node" ]; then
            for n_node in /usr/local/n/versions/node/*/; do
                if [ -x "$n_node/bin/node" ]; then
                    node_candidates="$node_candidates $n_node"
                fi
            done
        fi
        if [ -d "$HOME/n" ]; then
            for n_node in "$HOME/n/versions/node"/*/; do
                if [ -x "$n_node/bin/node" ]; then
                    node_candidates="$node_candidates $n_node"
                fi
            done
        fi
    fi

    # macOS: Homebrew 多版本
    if [ "$PLATFORM" = "Mac" ]; then
        for prefix in /opt/homebrew /usr/local; do
            for pattern in "$prefix/Cellar/node@$REQUIRED_NODE_MAJOR"* "$prefix/Cellar/node"/$REQUIRED_NODE_MAJOR* "$prefix/opt/node@$REQUIRED_NODE_MAJOR"; do
                if [ -d "$pattern" ] && [ -x "$pattern/bin/node" ]; then
                    node_candidates="$node_candidates $pattern"
                fi
            done
        done
    fi

    # 搜索最佳版本
    local best_node=""
    local best_ver=0
    for candidate in $node_candidates; do
        if [ -x "$candidate/bin/node" ]; then
            local ver
            ver=$("$candidate/bin/node" --version 2>&1 | sed 's/v//' | cut -d'.' -f1)
            if [ "$ver" -ge "$REQUIRED_NODE_MAJOR" ] 2>/dev/null && [ "$ver" -gt "$best_ver" ] 2>/dev/null; then
                best_ver=$ver
                best_node="$candidate"
            fi
        fi
    done

    if [ -n "$best_node" ]; then
        export PATH="$best_node/bin:$PATH"
        local node_ver
        node_ver=$(node --version 2>&1 | sed 's/v//')
        print_success "Node.js $node_ver 已就绪 (多版本切换): $best_node"
        print_info "  $(node --version 2>&1)"
        return 0
    fi

    # 方法4: 自动安装（作为最后手段）
    if [ "$nvm_loaded" = true ]; then
        print_info "使用 nvm 安装 Node.js $REQUIRED_NODE_MAJOR..."
        nvm install "$REQUIRED_NODE_MAJOR" 2>/dev/null
        if [ $? -eq 0 ]; then
            nvm use "$REQUIRED_NODE_MAJOR" 2>/dev/null
            local nvm_final_check
            nvm_final_check=$(node --version 2>&1)
            if echo "$nvm_final_check" | grep -q "GLIBC"; then
                print_warning "nvm 安装的 Node.js 无法运行：系统 glibc 版本过低"
                print_info "  $nvm_final_check"
            else
                print_success "Node.js $(echo "$nvm_final_check" | sed 's/v//') 已就绪 (nvm 安装)"
                return 0
            fi
        fi
    fi

    # 未找到合适版本
    print_error "未找到 Node.js ${REQUIRED_NODE_MAJOR}+"
    echo ""
    if command -v node >/dev/null 2>&1; then
        local final_check
        final_check=$(node --version 2>&1)
        if echo "$final_check" | grep -q "GLIBC"; then
            print_info "系统当前 Node.js 状态: glibc 版本过低，无法运行"
            print_info "错误详情: $final_check"
            echo ""
            print_info "原因: Node.js 18+ 需要 glibc 2.28+，当前系统版本过低"
            print_info "解决方案: 升级系统或使用 Docker 运行"
        else
            print_info "系统当前 Node.js 版本: $final_check"
        fi
    fi
    if [ "$nvm_loaded" != true ]; then
        print_info "提示: 安装 nvm 可方便管理多版本 Node.js"
        print_info "  curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash"
    fi
    print_info "安装方式:"
    print_info "  1. 运行 ./setup-env.sh 自动安装 Node.js"
    print_info "  2. 手动下载: https://nodejs.org/"
    exit 1
}

check_nodejs

# ==========================================
# 多版本 npm 自动检测与切换
# ==========================================
check_npm() {
    print_info "检测 npm 环境（要求 >= ${REQUIRED_NPM_MAJOR}）..."

    if command -v npm >/dev/null 2>&1; then
        local npm_ver
        npm_ver=$(npm --version 2>&1)
        local npm_major
        npm_major=$(echo "$npm_ver" | cut -d'.' -f1)
        if [ "$npm_major" -ge "$REQUIRED_NPM_MAJOR" ] 2>/dev/null; then
            print_success "npm $npm_ver 已就绪"
            return 0
        else
            print_warning "npm v$npm_ver 低于要求的 v${REQUIRED_NPM_MAJOR}，尝试升级..."
            # npm 随 Node.js 安装，尝试更新 npm 自身
            print_info "执行 npm install -g npm@${REQUIRED_NPM_MAJOR}..."
            npm install -g "npm@${REQUIRED_NPM_MAJOR}" 2>/dev/null
            if [ $? -eq 0 ]; then
                npm_ver=$(npm --version 2>&1)
                print_success "npm 已升级至 $npm_ver"
                return 0
            fi
        fi
    fi

    print_error "未找到 npm ${REQUIRED_NPM_MAJOR}+"
    if command -v npm >/dev/null 2>&1; then
        print_info "系统当前 npm 版本: v$(npm --version 2>&1)"
    fi
    print_info "npm 通常随 Node.js 一起安装，请确保 Node.js ${REQUIRED_NODE_MAJOR}+ 已正确安装"
    exit 1
}

check_npm

print_success "基础环境检测通过"

# ==========================================
# 第二步：构建前端静态资源
# ==========================================
print_info ""
print_info "=========================================="
print_info "第二步：构建前端静态资源..."
print_info "=========================================="

FRONTEND_DIR="$PROJECT_ROOT/frontend"
BACKEND_DIR="$PROJECT_ROOT/backend"

if [ ! -d "$FRONTEND_DIR" ]; then
    print_error "前端目录不存在: $FRONTEND_DIR"
    exit 1
fi

cd "$FRONTEND_DIR"

print_info "安装前端依赖..."
npm install
if [ $? -ne 0 ]; then
    print_error "前端依赖安装失败 (npm install)"
    exit 1
fi
print_success "前端依赖安装完成"

print_info "构建前端生产版本..."
npm run build
if [ $? -ne 0 ]; then
    print_error "前端构建失败 (npm run build)"
    exit 1
fi
print_success "前端构建成功，输出目录: $(pwd)/dist"

# 返回项目根目录
cd "$PROJECT_ROOT"

# ==========================================
# 第三步：复制前端静态资源到后端
# ==========================================
print_info ""
print_info "=========================================="
print_info "第三步：复制前端静态资源到后端..."
print_info "=========================================="

FRONTEND_DIST="$FRONTEND_DIR/dist"
BACKEND_STATIC="$BACKEND_DIR/src/main/resources/static"

if [ ! -d "$FRONTEND_DIST" ]; then
    print_error "前端构建产物不存在: $FRONTEND_DIST"
    exit 1
fi

# 清理旧的 static 目录
if [ -d "$BACKEND_STATIC" ]; then
    print_info "清理旧的 static 目录..."
    rm -rf "$BACKEND_STATIC"
fi

# 复制 dist 并重命名为 static
print_info "复制 dist -> src/main/resources/static..."
cp -r "$FRONTEND_DIST" "$BACKEND_STATIC"

if [ $? -ne 0 ]; then
    print_error "复制前端静态资源失败"
    exit 1
fi

# 验证关键文件是否存在
if [ -f "$BACKEND_STATIC/index.html" ]; then
    print_success "前端静态资源复制完成: $BACKEND_STATIC"
    print_info "  - index.html: $( [ -f "$BACKEND_STATIC/index.html" ] && echo '存在' || echo '不存在' )"
    print_info "  - assets目录: $( [ -d "$BACKEND_STATIC/assets" ] && echo '存在' || echo '不存在' )"
else
    print_error "index.html 不存在，复制可能不完整"
    exit 1
fi

# ==========================================
# 第3.5步：下载 shields.io 版本图标到本地 badges/ 目录
# ==========================================
print_info ""
print_info "=========================================="
print_info "第3.5步：下载 shields.io 版本图标..."
print_info "=========================================="

# 项目根目录 badges 目录
BADGES_DIR="$PROJECT_ROOT/badges"
mkdir -p "$BADGES_DIR"

# 定义图标下载列表: "文件名=shields.io_URL"
BADGE_URLS=(
  "version.svg=https://img.shields.io/badge/Version-2.3.2-blue?style=flat-square"
  "license.svg=https://img.shields.io/badge/License-Apache%202.0-green?style=flat-square"
  "jdk.svg=https://img.shields.io/badge/JDK-21-red?style=flat-square&logo=openjdk"
  "node.svg=https://img.shields.io/badge/Node.js-18+-green?style=flat-square&logo=nodedotjs"
  "springboot.svg=https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?style=flat-square&logo=springboot"
  "vue.svg=https://img.shields.io/badge/Vue-3.x-brightgreen?style=flat-square&logo=vuedotjs"
)

for entry in "${BADGE_URLS[@]}"; do
  FILENAME="${entry%%=*}"
  URL="${entry#*=}"
  print_info "下载 $FILENAME ..."
  if curl -sSfL -o "$BADGES_DIR/$FILENAME" "$URL" 2>/dev/null; then
    print_success "$FILENAME 下载完成"
  else
    print_warning "$FILENAME 下载失败，跳过"
  fi
done

# 同步 badges 到后端静态资源目录
BACKEND_BADGES_DIR="$BACKEND_STATIC/badges"
mkdir -p "$BACKEND_BADGES_DIR"
if [ -d "$BADGES_DIR" ]; then
  cp "$BADGES_DIR"/*.svg "$BACKEND_BADGES_DIR/" 2>/dev/null
  print_success "badges 已同步到后端静态资源目录"
fi

# ==========================================
# 第4步：同步 README.md 和 CHANGELOG.md 到静态资源
# ==========================================
print_info ""
print_info "=========================================="
print_info "第4步：同步 README 和 CHANGELOG 文档..."
print_info "=========================================="

PROJECT_README="$PROJECT_ROOT/README.md"
PROJECT_README_US="$PROJECT_ROOT/README-US.md"
PROJECT_CHANGELOG="$PROJECT_ROOT/CHANGELOG.md"

if [ -f "$PROJECT_README" ]; then
    cp "$PROJECT_README" "$BACKEND_STATIC/README.md"
    # 替换 shields.io 图标为本地离线图标，避免内网/离线环境不显示
    sed -i 's|https://img.shields.io/badge/Version-2.3.2-blue?style=flat-square|/badges/version.svg|g' "$BACKEND_STATIC/README.md"
    sed -i 's|https://img.shields.io/badge/License-Apache%202.0-green?style=flat-square|/badges/license.svg|g' "$BACKEND_STATIC/README.md"
    sed -i 's|https://img.shields.io/badge/JDK-21-red?style=flat-square&logo=openjdk|/badges/jdk.svg|g' "$BACKEND_STATIC/README.md"
    sed -i 's|https://img.shields.io/badge/Node.js-18+-green?style=flat-square&logo=nodedotjs|/badges/node.svg|g' "$BACKEND_STATIC/README.md"
    sed -i 's|https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?style=flat-square&logo=springboot|/badges/springboot.svg|g' "$BACKEND_STATIC/README.md"
    sed -i 's|https://img.shields.io/badge/Vue-3.x-brightgreen?style=flat-square&logo=vuedotjs|/badges/vue.svg|g' "$BACKEND_STATIC/README.md"
    print_success "README.md 已同步到静态资源目录"
else
    print_warning "项目根目录未找到 README.md"
fi

if [ -f "$PROJECT_README_US" ]; then
    cp "$PROJECT_README_US" "$BACKEND_STATIC/README-US.md"
    # 替换 shields.io 图标为本地离线图标，避免内网/离线环境不显示
    sed -i 's|https://img.shields.io/badge/Version-2.3.2-blue?style=flat-square|/badges/version.svg|g' "$BACKEND_STATIC/README-US.md"
    sed -i 's|https://img.shields.io/badge/License-Apache%202.0-green?style=flat-square|/badges/license.svg|g' "$BACKEND_STATIC/README-US.md"
    sed -i 's|https://img.shields.io/badge/JDK-21-red?style=flat-square&logo=openjdk|/badges/jdk.svg|g' "$BACKEND_STATIC/README-US.md"
    sed -i 's|https://img.shields.io/badge/Node.js-18+-green?style=flat-square&logo=nodedotjs|/badges/node.svg|g' "$BACKEND_STATIC/README-US.md"
    sed -i 's|https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen?style=flat-square&logo=springboot|/badges/springboot.svg|g' "$BACKEND_STATIC/README-US.md"
    sed -i 's|https://img.shields.io/badge/Vue-3.x-brightgreen?style=flat-square&logo=vuedotjs|/badges/vue.svg|g' "$BACKEND_STATIC/README-US.md"
    print_success "README-US.md 已同步到静态资源目录"
else
    print_warning "项目根目录未找到 README-US.md"
fi

if [ -f "$PROJECT_CHANGELOG" ]; then
    cp "$PROJECT_CHANGELOG" "$BACKEND_STATIC/CHANGELOG.md"
    print_success "CHANGELOG.md 已同步到静态资源目录"
else
    print_warning "项目根目录未找到 CHANGELOG.md"
fi

# ==========================================
# 第五步：Maven 打包
# ==========================================
print_info ""
print_info "=========================================="
print_info "第五步：Maven 打包..."
print_info "=========================================="

cd "$BACKEND_DIR"

print_info "执行 mvn clean package..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    print_error "Maven 打包失败"
    exit 1
fi

print_success "Maven 打包完成"

# 返回项目根目录
cd "$PROJECT_ROOT"

# ==========================================
# 构建完成
# ==========================================
echo ""
print_info "=========================================="
print_success "构建完成！"
print_info "=========================================="

# 查找生成的 JAR 包
JAR_FILE=$(find "$BACKEND_DIR/target" -maxdepth 1 -name "*.jar" ! -name "*sources.jar" ! -name "*javadoc.jar" 2>/dev/null | head -n 1)

if [ -n "$JAR_FILE" ]; then
    JAR_NAME=$(basename "$JAR_FILE")
    print_info "JAR 包位置: $JAR_FILE"
    echo ""
    print_info "运行命令:"

    # 根据检测到的 JDK 21 路径给出准确的 java 命令
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        JAVA_RUNTIME="$JAVA_HOME/bin/java"
    else
        JAVA_RUNTIME="java"
    fi
    print_info "  $JAVA_RUNTIME -jar $JAR_FILE"
    echo ""
    print_info "或者直接运行一键启动脚本:"
    print_info "  ./run.sh"
else
    print_warning "未找到 JAR 包，请检查 backend/target/ 目录"
fi
