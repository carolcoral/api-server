#!/bin/bash

echo "=========================================="
echo "API Server 环境安装脚本"
echo "作者: carolcoral"
echo "=========================================="

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_success() {
    echo -e "${GREEN}[成功]${NC} $1"
}

print_error() {
    echo -e "${RED}[错误]${NC} $1"
}

print_info() {
    echo -e "${YELLOW}[信息]${NC} $1"
}

print_step() {
    echo -e "${BLUE}[步骤]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[警告]${NC} $1"
}

# 检测操作系统
OS="$(uname -s)"
case "${OS}" in
    Linux*)     PLATFORM=Linux;;
    Darwin*)    PLATFORM=Mac;;
    CYGWIN*|MINGW32*|MSYS*|MINGW*) PLATFORM=Windows;;
    *)          PLATFORM="UNKNOWN:${OS}"
esac

print_info "检测到操作系统: ${PLATFORM}"

# 检测 Linux 发行版
detect_linux_distro() {
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        DISTRO=$ID
        DISTRO_VERSION=$VERSION_ID
        # 处理子发行版（如 ubuntu 的衍生版）
        if [ -n "$ID_LIKE" ]; then
            DISTRO_LIKE=$ID_LIKE
        fi
    elif [ -f /etc/redhat-release ]; then
        DISTRO="rhel"
    elif [ -f /etc/debian_version ]; then
        DISTRO="debian"
    else
        DISTRO="unknown"
    fi
    # 导出为全局变量，方便其他函数使用
    export DISTRO
    export DISTRO_VERSION
    export DISTRO_LIKE
    echo "$DISTRO"
}

# 确保 sudo 命令可用（若缺失则自动安装）
ensure_sudo() {
    if command -v sudo >/dev/null 2>&1; then
        print_info "sudo 命令已可用"
        return 0
    fi

    # 已经是 root 用户，直接定义 sudo 为空函数即可
    if [ "$(id -u)" -eq 0 ]; then
        print_info "当前为 root 用户，无需 sudo"
        # 定义一个空的 sudo 函数，让后续脚本中的 sudo xxx 直接执行 xxx
        sudo() { "$@"; }
        export -f sudo 2>/dev/null || true
        return 0
    fi

    print_warning "未检测到 sudo 命令，尝试安装..."

    # 先用 su 切换到 root 安装 sudo
    if [ "$PLATFORM" != "Linux" ]; then
        print_error "非 Linux 系统且缺少 sudo，请手动安装"
        return 1
    fi

    # 提前检测发行版（如果尚未检测）
    if [ -z "$DISTRO" ]; then
        detect_linux_distro > /dev/null
    fi

    print_info "检测到发行版: $DISTRO (版本: ${DISTRO_VERSION:-未知})"

    case "$DISTRO" in
        ubuntu|debian|linuxmint|pop|elementary|zorin)
            print_info "使用 apt-get 安装 sudo..."
            su -c "apt-get update -qq && apt-get install -y -qq sudo" root
            ;;
        rhel|centos|fedora|rocky|almalinux|ol|amzn)
            if command -v dnf >/dev/null 2>&1; then
                su -c "dnf install -y sudo" root
            elif command -v yum >/dev/null 2>&1; then
                su -c "yum install -y sudo" root
            fi
            ;;
        suse|opensuse*|sled|sles)
            su -c "zypper install -y sudo" root
            ;;
        alpine)
            su -c "apk add sudo" root
            ;;
        arch|manjaro|endeavouros)
            su -c "pacman -S --noconfirm sudo" root
            ;;
        *)
            # 兜底：通过 DISTRO_LIKE 判断
            case "${DISTRO_LIKE:-}" in
                *debian*|*ubuntu*)
                    su -c "apt-get update -qq && apt-get install -y -qq sudo" root
                    ;;
                *rhel*|*fedora*|*centos*)
                    if command -v dnf >/dev/null 2>&1; then
                        su -c "dnf install -y sudo" root
                    elif command -v yum >/dev/null 2>&1; then
                        su -c "yum install -y sudo" root
                    fi
                    ;;
                *suse*)
                    su -c "zypper install -y sudo" root
                    ;;
                *arch*)
                    su -c "pacman -S --noconfirm sudo" root
                    ;;
                *)
                    print_error "无法自动安装 sudo，请手动安装后重试"
                    return 1
                    ;;
            esac
            ;;
    esac

    if command -v sudo >/dev/null 2>&1; then
        print_success "sudo 安装成功"
        return 0
    else
        print_error "sudo 安装失败，请手动安装"
        return 1
    fi
}

# 检查是否有 root 权限
check_root_or_sudo() {
    if [ "$PLATFORM" = "Windows" ]; then
        return 0  # Windows 下不需要 sudo 检测
    fi
    
    if [ "$(id -u)" -eq 0 ]; then
        return 0  # 已经是 root
    fi
    
    if command -v sudo >/dev/null 2>&1; then
        if sudo -n true 2>/dev/null; then
            return 0  # 有免密 sudo 权限
        else
            print_warning "需要管理员权限来安装 JDK 21"
            print_warning "脚本将在需要时请求 sudo 密码"
            return 1
        fi
    else
        print_warning "未找到 sudo 命令，可能需要以 root 用户运行此脚本"
        return 1
    fi
}

# 检查 Java 21 是否已安装
check_java21_installed() {
    print_step "检查 Java 21 环境..."
    
    # 方法1: 检查 JAVA_HOME
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        JAVA_VERSION=$("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
        if [ "$JAVA_VERSION" = "21" ]; then
            print_success "Java 21 已安装 (JAVA_HOME): $JAVA_HOME"
            print_info "Java 版本: $("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"
            return 0
        fi
    fi
    
    # 方法2: 检查系统 PATH 中的 java
    if command -v java >/dev/null 2>&1; then
        JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
        if [ "$JAVA_VERSION" = "21" ]; then
            JAVA_PATH=$(command -v java)
            JAVA_HOME_PATH=$(dirname "$(dirname "$(readlink -f "$JAVA_PATH" 2>/dev/null || echo "$JAVA_PATH")")")
            print_success "Java 21 已安装 (PATH): $JAVA_PATH"
            print_info "Java 版本: $(java -version 2>&1 | head -n 1)"
            return 0
        fi
    fi
    
    # 方法3: 检查常见安装路径
    case "${PLATFORM}" in
        Mac)
            if command -v /usr/libexec/java_home >/dev/null 2>&1; then
                JAVA21_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null)
                if [ -n "$JAVA21_HOME" ] && [ -x "$JAVA21_HOME/bin/java" ]; then
                    print_success "Java 21 已安装: $JAVA21_HOME"
                    print_info "Java 版本: $("$JAVA21_HOME/bin/java" -version 2>&1 | head -n 1)"
                    return 0
                fi
            fi
            ;;
        Linux)
            for java_path in \
                "/usr/lib/jvm/java-21-openjdk-amd64" \
                "/usr/lib/jvm/java-21-openjdk" \
                "/usr/lib/jvm/jdk-21" \
                "/opt/jdk-21" \
                "/usr/lib/jvm/temurin-21-jdk-amd64" \
                "/usr/lib/jvm/jdk-21-oracle-x64"; do
                if [ -d "$java_path" ] && [ -x "$java_path/bin/java" ]; then
                    JAVA_VERSION=$("$java_path/bin/java" -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
                    if [ "$JAVA_VERSION" = "21" ]; then
                        print_success "Java 21 已安装: $java_path"
                        print_info "Java 版本: $("$java_path/bin/java" -version 2>&1 | head -n 1)"
                        return 0
                    fi
                fi
            done
            ;;
    esac
    
    print_warning "未检测到 Java 21"
    return 1
}

# 获取当前 JDK 21 的主目录
get_java21_home() {
    # 优先使用 JAVA_HOME
    if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        JAVA_VERSION=$("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
        if [ "$JAVA_VERSION" = "21" ]; then
            echo "$JAVA_HOME"
            return 0
        fi
    fi
    
    case "${PLATFORM}" in
        Mac)
            if command -v /usr/libexec/java_home >/dev/null 2>&1; then
                JAVA21_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null)
                if [ -n "$JAVA21_HOME" ]; then
                    echo "$JAVA21_HOME"
                    return 0
                fi
            fi
            ;;
        Linux)
            for java_path in \
                "/usr/lib/jvm/java-21-openjdk-amd64" \
                "/usr/lib/jvm/java-21-openjdk" \
                "/usr/lib/jvm/jdk-21" \
                "/opt/jdk-21" \
                "/usr/lib/jvm/temurin-21-jdk-amd64" \
                "/usr/lib/jvm/jdk-21-oracle-x64"; do
                if [ -d "$java_path" ] && [ -x "$java_path/bin/java" ]; then
                    JAVA_VERSION=$("$java_path/bin/java" -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
                    if [ "$JAVA_VERSION" = "21" ]; then
                        echo "$java_path"
                        return 0
                    fi
                fi
            done
            ;;
    esac
    
    # 通过 PATH 查找
    if command -v java >/dev/null 2>&1; then
        JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
        if [ "$JAVA_VERSION" = "21" ]; then
            JAVA_CMD=$(command -v java)
            JAVA_HOME_PATH=$(dirname "$(dirname "$(readlink -f "$JAVA_CMD" 2>/dev/null || echo "$JAVA_CMD")")")
            echo "$JAVA_HOME_PATH"
            return 0
        fi
    fi
    
    return 1
}

# 在 macOS 上安装 JDK 21
install_java21_mac() {
    print_step "在 macOS 上安装 JDK 21..."
    
    # 检查是否安装了 Homebrew
    if command -v brew >/dev/null 2>&1; then
        print_info "使用 Homebrew 安装 OpenJDK 21..."
        brew install openjdk@21
        
        if [ $? -eq 0 ]; then
            print_success "OpenJDK 21 安装完成"
            
            # 创建符号链接
            print_info "创建系统符号链接..."
            if [ -d "/usr/local/opt/openjdk@21" ]; then
                sudo ln -sfn /usr/local/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk 2>/dev/null || true
            elif [ -d "/opt/homebrew/opt/openjdk@21" ]; then
                sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk 2>/dev/null || true
            fi
            
            # 设置环境变量提示
            JAVA21_HOME=$(get_java21_home)
            if [ -n "$JAVA21_HOME" ]; then
                print_info "Java 21 安装路径: $JAVA21_HOME"
                print_info ""
                print_warning "请将以下内容添加到 ~/.zshrc 或 ~/.bash_profile："
                echo ""
                echo "  export JAVA_HOME=$JAVA21_HOME"
                echo "  export PATH=\"\$JAVA_HOME/bin:\$PATH\""
                echo ""
            fi
            return 0
        else
            print_error "Homebrew 安装 OpenJDK 21 失败"
            return 1
        fi
    else
        print_error "未找到 Homebrew，请先安装 Homebrew: https://brew.sh"
        print_info "或者手动下载 JDK 21: https://adoptium.net/download/"
        return 1
    fi
}

# 在 Linux (Debian/Ubuntu) 上安装 JDK 21
install_java21_debian() {
    print_step "在 Debian/Ubuntu 上安装 JDK 21..."
    print_info "发行版版本: ${DISTRO_VERSION:-未知}"

    # 根据版本号区分处理
    case "$DISTRO" in
        ubuntu|linuxmint|pop|elementary|zorin)
            # Ubuntu 及其衍生版
            # 获取主版本号
            UBUNTU_MAJOR=$(echo "$DISTRO_VERSION" | cut -d'.' -f1)
            print_info "Ubuntu 主版本号: $UBUNTU_MAJOR"

            if [ "$UBUNTU_MAJOR" -ge 22 ] 2>/dev/null; then
                # Ubuntu 22.04+ / 24.04+ 直接使用 apt
                print_info "Ubuntu $DISTRO_VERSION (>=22.04)，使用 apt 安装..."
                sudo apt-get update -qq
                sudo apt-get install -y -qq openjdk-21-jdk
            elif [ "$UBUNTU_MAJOR" -ge 20 ] 2>/dev/null; then
                # Ubuntu 20.04
                print_info "Ubuntu $DISTRO_VERSION (20.04)，使用 apt 安装..."
                sudo apt-get update -qq
                sudo apt-get install -y -qq openjdk-21-jdk
            elif [ "$UBUNTU_MAJOR" -ge 18 ] 2>/dev/null; then
                # Ubuntu 18.04 — 需要添加 PPA 或使用 openjdk-21 可能不在官方源
                print_warning "Ubuntu 18.04 官方源可能不包含 OpenJDK 21，尝试添加 ppa:openjdk-r/ppa..."
                sudo apt-get update -qq
                sudo apt-get install -y -qq software-properties-common 2>/dev/null || true
                sudo add-apt-repository -y ppa:openjdk-r/ppa 2>/dev/null && {
                    sudo apt-get update -qq
                    sudo apt-get install -y -qq openjdk-21-jdk
                } || {
                    print_warning "PPA 添加失败，尝试直接安装..."
                    sudo apt-get install -y -qq openjdk-21-jdk 2>/dev/null || {
                        print_warning "直接安装失败，回退到 SDKMAN..."
                        install_java21_sdkman
                        return $?
                    }
                }
            else
                # 更老版本，直接走 SDKMAN
                print_warning "Ubuntu 版本 $DISTRO_VERSION 过旧，使用 SDKMAN 安装 JDK 21..."
                install_java21_sdkman
                return $?
            fi
            ;;

        debian)
            DEBIAN_MAJOR=$(echo "$DISTRO_VERSION" | cut -d'.' -f1)
            print_info "Debian 主版本号: $DEBIAN_MAJOR"

            if [ "$DEBIAN_MAJOR" -ge 12 ] 2>/dev/null; then
                # Debian 12 (Bookworm) 及更新
                print_info "Debian $DISTRO_VERSION (>=12)，使用 apt 安装..."
                sudo apt-get update -qq
                sudo apt-get install -y -qq openjdk-21-jdk
            elif [ "$DEBIAN_MAJOR" -ge 11 ] 2>/dev/null; then
                # Debian 11 (Bullseye)
                print_info "Debian $DISTRO_VERSION (11)，使用 apt 安装..."
                sudo apt-get update -qq
                sudo apt-get install -y -qq openjdk-21-jdk
            elif [ "$DEBIAN_MAJOR" -ge 10 ] 2>/dev/null; then
                # Debian 10 (Buster) — 可能需要 backports
                print_warning "Debian 10 (Buster)，尝试从 backports 安装..."
                sudo apt-get update -qq
                sudo apt-get install -y -qq -t buster-backports openjdk-21-jdk 2>/dev/null || {
                    print_warning "backports 不可用，使用 SDKMAN 安装..."
                    install_java21_sdkman
                    return $?
                }
            else
                print_warning "Debian 版本 $DISTRO_VERSION 过旧，使用 SDKMAN 安装..."
                install_java21_sdkman
                return $?
            fi
            ;;

        *)
            # 其他 debian 系发行版，通用处理
            print_info "Debian 系发行版，使用 apt 安装..."
            sudo apt-get update -qq
            sudo apt-get install -y -qq openjdk-21-jdk
            ;;
    esac

    if [ $? -eq 0 ]; then
        print_success "OpenJDK 21 安装完成"
        
        # 设置默认 Java
        if command -v update-alternatives >/dev/null 2>&1; then
            print_info "设置 OpenJDK 21 为默认 Java..."
            sudo update-alternatives --set java /usr/lib/jvm/java-21-openjdk-amd64/bin/java 2>/dev/null || true
            sudo update-alternatives --set javac /usr/lib/jvm/java-21-openjdk-amd64/bin/javac 2>/dev/null || true
        fi
        
        JAVA21_HOME=$(get_java21_home)
        if [ -n "$JAVA21_HOME" ]; then
            print_info "Java 21 安装路径: $JAVA21_HOME"
            # 自动设置 JAVA_HOME 环境变量
            export JAVA_HOME="$JAVA21_HOME"
            export PATH="$JAVA_HOME/bin:$PATH"
            print_info "已自动设置 JAVA_HOME=$JAVA_HOME"
        fi
        return 0
    else
        print_error "OpenJDK 21 安装失败，尝试 SDKMAN..."
        install_java21_sdkman
        return $?
    fi
}

# 在 Linux (RHEL/CentOS/Fedora) 上安装 JDK 21
install_java21_rhel() {
    print_step "在 RHEL/CentOS/Fedora 上安装 JDK 21..."
    print_info "发行版版本: ${DISTRO_VERSION:-未知}"

    RHEL_MAJOR=$(echo "$DISTRO_VERSION" | cut -d'.' -f1)

    case "$DISTRO" in
        fedora)
            # Fedora 各版本都支持
            print_info "Fedora $DISTRO_VERSION，使用 dnf 安装..."
            sudo dnf install -y java-21-openjdk java-21-openjdk-devel
            ;;
        rhel|centos|rocky|almalinux|ol)
            if [ "$RHEL_MAJOR" -ge 9 ] 2>/dev/null; then
                # RHEL 9+ / Rocky 9+ / Alma 9+
                print_info "RHEL $DISTRO_VERSION (>=9)，使用 dnf 安装..."
                sudo dnf install -y java-21-openjdk java-21-openjdk-devel
            elif [ "$RHEL_MAJOR" -ge 8 ] 2>/dev/null; then
                # RHEL 8 / CentOS 8 / Rocky 8 / Alma 8
                print_info "RHEL $DISTRO_VERSION (8.x)，使用 dnf 安装..."
                sudo dnf install -y java-21-openjdk java-21-openjdk-devel
            elif [ "$RHEL_MAJOR" -ge 7 ] 2>/dev/null; then
                # CentOS 7 / RHEL 7 — java-21 可能不在默认源
                print_warning "RHEL/CentOS 7 默认源可能不包含 JDK 21，尝试安装..."
                if command -v yum >/dev/null 2>&1; then
                    sudo yum install -y java-21-openjdk java-21-openjdk-devel 2>/dev/null || {
                        print_warning "yum 安装失败，使用 SDKMAN..."
                        install_java21_sdkman
                        return $?
                    }
                elif command -v dnf >/dev/null 2>&1; then
                    sudo dnf install -y java-21-openjdk java-21-openjdk-devel 2>/dev/null || {
                        print_warning "dnf 安装失败，使用 SDKMAN..."
                        install_java21_sdkman
                        return $?
                    }
                fi
            else
                print_warning "RHEL 版本 $DISTRO_VERSION 过旧，使用 SDKMAN 安装..."
                install_java21_sdkman
                return $?
            fi
            ;;
        amzn)
            # Amazon Linux
            if [ "$RHEL_MAJOR" -ge 2023 ] 2>/dev/null || [ "$RHEL_MAJOR" -ge 2 ] 2>/dev/null; then
                # Amazon Linux 2023 / AL2
                print_info "Amazon Linux $DISTRO_VERSION，使用 dnf/yum 安装..."
                if command -v dnf >/dev/null 2>&1; then
                    sudo dnf install -y java-21-amazon-corretto-devel
                else
                    sudo yum install -y java-21-amazon-corretto-devel
                fi
            else
                print_warning "Amazon Linux 版本未知，尝试安装 Corretto 17..."
                if command -v dnf >/dev/null 2>&1; then
                    sudo dnf install -y java-21-amazon-corretto-devel 2>/dev/null || {
                        sudo dnf install -y java-21-openjdk java-21-openjdk-devel
                    }
                else
                    sudo yum install -y java-21-amazon-corretto-devel 2>/dev/null || {
                        sudo yum install -y java-21-openjdk java-21-openjdk-devel
                    }
                fi
            fi
            ;;
        *)
            # 其他 RHEL 系发行版，通用处理
            if command -v dnf >/dev/null 2>&1; then
                print_info "使用 dnf 安装 OpenJDK 21..."
                sudo dnf install -y java-21-openjdk java-21-openjdk-devel
            elif command -v yum >/dev/null 2>&1; then
                print_info "使用 yum 安装 OpenJDK 21..."
                sudo yum install -y java-21-openjdk java-21-openjdk-devel
            else
                print_error "未找到 dnf 或 yum 包管理器"
                install_java21_sdkman
                return $?
            fi
            ;;
    esac
    
    if [ $? -eq 0 ]; then
        print_success "OpenJDK 21 安装完成"
        
        # 设置默认 Java
        if command -v alternatives >/dev/null 2>&1; then
            print_info "设置 OpenJDK 21 为默认 Java..."
            JAVA21_PATH=$(find /usr/lib/jvm -name "java-21*" -type d 2>/dev/null | head -n 1)
            if [ -n "$JAVA21_PATH" ]; then
                sudo alternatives --set java "$JAVA21_PATH/bin/java" 2>/dev/null || true
                sudo alternatives --set javac "$JAVA21_PATH/bin/javac" 2>/dev/null || true
            fi
        fi
        
        JAVA21_HOME=$(get_java21_home)
        if [ -n "$JAVA21_HOME" ]; then
            print_info "Java 21 安装路径: $JAVA21_HOME"
            export JAVA_HOME="$JAVA21_HOME"
            export PATH="$JAVA_HOME/bin:$PATH"
            print_info "已自动设置 JAVA_HOME=$JAVA_HOME"
        fi
        return 0
    else
        print_error "OpenJDK 21 安装失败，尝试 SDKMAN..."
        install_java21_sdkman
        return $?
    fi
}

# 在 Linux (openSUSE) 上安装 JDK 21
install_java21_suse() {
    print_step "在 openSUSE 上安装 JDK 21..."
    
    if command -v zypper >/dev/null 2>&1; then
        print_info "使用 zypper 安装 OpenJDK 21..."
        sudo zypper install -y java-21-openjdk java-21-openjdk-devel
    else
        print_error "未找到 zypper 包管理器"
        return 1
    fi
    
    if [ $? -eq 0 ]; then
        print_success "OpenJDK 21 安装完成"
        
        JAVA21_HOME=$(get_java21_home)
        if [ -n "$JAVA21_HOME" ]; then
            print_info "Java 21 安装路径: $JAVA21_HOME"
            export JAVA_HOME="$JAVA21_HOME"
            export PATH="$JAVA_HOME/bin:$PATH"
            print_info "已自动设置 JAVA_HOME=$JAVA_HOME"
        fi
        return 0
    else
        print_error "OpenJDK 21 安装失败"
        return 1
    fi
}

# 在 Linux (Alpine) 上安装 JDK 21
install_java21_alpine() {
    print_step "在 Alpine Linux 上安装 JDK 21..."
    
    if command -v apk >/dev/null 2>&1; then
        print_info "使用 apk 安装 OpenJDK 21..."
        sudo apk add openjdk21
    else
        print_error "未找到 apk 包管理器"
        return 1
    fi
    
    if [ $? -eq 0 ]; then
        print_success "OpenJDK 21 安装完成"
        
        JAVA21_HOME=$(get_java21_home)
        if [ -n "$JAVA21_HOME" ]; then
            print_info "Java 21 安装路径: $JAVA21_HOME"
            export JAVA_HOME="$JAVA21_HOME"
            export PATH="$JAVA_HOME/bin:$PATH"
            print_info "已自动设置 JAVA_HOME=$JAVA_HOME"
        fi
        return 0
    else
        print_error "OpenJDK 21 安装失败"
        return 1
    fi
}

# 在 Linux (Arch) 上安装 JDK 21
install_java21_arch() {
    print_step "在 Arch Linux 上安装 JDK 21..."
    
    if command -v pacman >/dev/null 2>&1; then
        print_info "使用 pacman 安装 JDK 21..."
        sudo pacman -S --noconfirm jdk21-openjdk
    else
        print_error "未找到 pacman 包管理器"
        return 1
    fi
    
    if [ $? -eq 0 ]; then
        print_success "JDK 21 安装完成"
        
        JAVA21_HOME=$(get_java21_home)
        if [ -n "$JAVA21_HOME" ]; then
            print_info "Java 21 安装路径: $JAVA21_HOME"
            export JAVA_HOME="$JAVA21_HOME"
            export PATH="$JAVA_HOME/bin:$PATH"
            print_info "已自动设置 JAVA_HOME=$JAVA_HOME"
        fi
        return 0
    else
        print_error "JDK 21 安装失败"
        return 1
    fi
}

# 通用 Linux 安装（使用 SDKMAN）
install_java21_sdkman() {
    print_step "使用 SDKMAN 安装 JDK 21..."
    
    # 检查是否已安装 SDKMAN
    if [ -d "$HOME/.sdkman" ]; then
        print_info "SDKMAN 已安装"
    else
        print_info "安装 SDKMAN..."
        curl -s "https://get.sdkman.io" | bash
        if [ $? -ne 0 ]; then
            print_error "SDKMAN 安装失败"
            return 1
        fi
        # 初始化 SDKMAN
        source "$HOME/.sdkman/bin/sdkman-init.sh"
    fi
    
    # 确保 SDKMAN 已初始化
    if [ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
        source "$HOME/.sdkman/bin/sdkman-init.sh"
    fi
    
    if command -v sdk >/dev/null 2>&1; then
        print_info "使用 SDKMAN 安装 Temurin JDK 21..."
        sdk install java 21.0.5-tem
        
        if [ $? -eq 0 ]; then
            print_success "Temurin JDK 21 安装完成"
            
            JAVA21_HOME=$(get_java21_home)
            if [ -n "$JAVA21_HOME" ]; then
                print_info "Java 21 安装路径: $JAVA21_HOME"
            fi
            return 0
        fi
    fi
    
    print_error "SDKMAN 安装 JDK 21 失败"
    return 1
}

# 在 Linux 上自动选择安装方式
install_java21_linux() {
    DISTRO=$(detect_linux_distro)
    DISTRO_LIKE="${DISTRO_LIKE:-$DISTRO}"
    
    print_info "检测到 Linux 发行版: $DISTRO"
    
    # 根据发行版选择安装方式
    case "$DISTRO" in
        ubuntu|debian|linuxmint|pop|elementary|zorin)
            install_java21_debian
            return $?
            ;;
        rhel|centos|fedora|rocky|almalinux|ol|amzn)
            install_java21_rhel
            return $?
            ;;
        suse|opensuse*|sled|sles)
            install_java21_suse
            return $?
            ;;
        alpine)
            install_java21_alpine
            return $?
            ;;
        arch|manjaro|endeavouros)
            install_java21_arch
            return $?
            ;;
        *)
            # 尝试通过 DISTRO_LIKE 判断
            case "$DISTRO_LIKE" in
                *debian*|*ubuntu*)
                    install_java21_debian
                    return $?
                    ;;
                *rhel*|*fedora*|*centos*)
                    install_java21_rhel
                    return $?
                    ;;
                *suse*)
                    install_java21_suse
                    return $?
                    ;;
                *arch*)
                    install_java21_arch
                    return $?
                    ;;
            esac
            
            # 兜底方案：尝试使用 SDKMAN
            print_warning "未识别的 Linux 发行版: $DISTRO"
            print_info "尝试使用 SDKMAN 安装 JDK 21..."
            install_java21_sdkman
            return $?
            ;;
    esac
}

# 验证 Java 安装
verify_java() {
    print_step "验证 Java 安装..."
    
    JAVA21_HOME=$(get_java21_home)
    if [ -z "$JAVA21_HOME" ]; then
        print_error "无法找到 Java 21 安装路径"
        return 1
    fi
    
    if [ -x "$JAVA21_HOME/bin/java" ]; then
        JAVA_VERSION_OUTPUT=$("$JAVA21_HOME/bin/java" -version 2>&1)
        print_success "Java 安装验证通过"
        print_info "$JAVA_VERSION_OUTPUT"
        
        # 检查 javac 是否可用（确认是 JDK 而非 JRE）
        if [ -x "$JAVA21_HOME/bin/javac" ]; then
            JAVAC_VERSION=$("$JAVA21_HOME/bin/javac" -version 2>&1)
            print_info "javac: $JAVAC_VERSION"
            print_success "确认安装的是 JDK（包含编译工具）"
        else
            print_warning "未找到 javac，可能只安装了 JRE 而非 JDK"
            print_warning "项目需要 JDK 来编译代码，请确保安装了完整的 JDK"
        fi
        
        return 0
    else
        print_error "Java 安装验证失败"
        return 1
    fi
}

# ==========================================
# Maven 相关函数
# ==========================================

# 项目要求的 Maven 最低版本
REQUIRED_MAVEN_MAJOR=3
REQUIRED_MAVEN_MINOR=9

# 检查 Maven 是否已安装且版本符合要求
check_maven_installed() {
    print_step "检查 Maven 环境..."
    
    if command -v mvn >/dev/null 2>&1; then
        MAVEN_VERSION_OUTPUT=$(mvn --version 2>&1 | head -n 1)
        # 使用 sed 替代 grep -oP，兼容老系统（如 CentOS 7）
        MAVEN_VERSION=$(echo "$MAVEN_VERSION_OUTPUT" | sed -n 's/.*Apache Maven \([0-9]\+\.[0-9]\+\.[0-9]\+\).*/\1/p')
        
        if [ -n "$MAVEN_VERSION" ]; then
            MAVEN_MAJOR=$(echo "$MAVEN_VERSION" | cut -d'.' -f1)
            MAVEN_MINOR=$(echo "$MAVEN_VERSION" | cut -d'.' -f2)
            
            if [ "$MAVEN_MAJOR" -gt "$REQUIRED_MAVEN_MAJOR" ] || \
               ([ "$MAVEN_MAJOR" -eq "$REQUIRED_MAVEN_MAJOR" ] && [ "$MAVEN_MINOR" -ge "$REQUIRED_MAVEN_MINOR" ]); then
                print_success "Maven $MAVEN_VERSION 已安装 (要求 >= $REQUIRED_MAVEN_MAJOR.$REQUIRED_MAVEN_MINOR)"
                print_info "$MAVEN_VERSION_OUTPUT"
                return 0
            else
                print_warning "Maven 版本 $MAVEN_VERSION 低于要求的 $REQUIRED_MAVEN_MAJOR.$REQUIRED_MAVEN_MINOR"
                return 1
            fi
        fi
    fi
    
    print_warning "未检测到 Maven $REQUIRED_MAVEN_MAJOR.$REQUIRED_MAVEN_MINOR+"
    return 1
}

# 静默检查 Maven 版本（供安装后验证使用，不输出日志）
check_maven_version_silent() {
    if command -v mvn >/dev/null 2>&1; then
        MAVEN_VERSION=$(mvn --version 2>&1 | head -n 1 | sed -n 's/.*Apache Maven \([0-9]\+\.[0-9]\+\.[0-9]\+\).*/\1/p')
        if [ -n "$MAVEN_VERSION" ]; then
            MAVEN_MAJOR=$(echo "$MAVEN_VERSION" | cut -d'.' -f1)
            MAVEN_MINOR=$(echo "$MAVEN_VERSION" | cut -d'.' -f2)
            if [ "$MAVEN_MAJOR" -gt "$REQUIRED_MAVEN_MAJOR" ] || \
               ([ "$MAVEN_MAJOR" -eq "$REQUIRED_MAVEN_MAJOR" ] && [ "$MAVEN_MINOR" -ge "$REQUIRED_MAVEN_MINOR" ]); then
                return 0
            fi
        fi
    fi
    return 1
}

# 手动下载安装 Maven（当系统包管理器提供的版本过低时使用）
install_maven_manual() {
    print_step "手动下载 Maven 3.9.9（系统包管理器版本过低）..."
    
    MAVEN_DOWNLOAD_URL="https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz"
    MAVEN_INSTALL_DIR="/opt/maven"
    
    # 尝试下载，优先 wget，失败则回退到 curl
    local downloaded=false

    # 清除可能残留的旧文件
    rm -f /tmp/apache-maven.tar.gz

    if command -v wget >/dev/null 2>&1; then
        print_info "使用 wget 下载..."
        if sudo wget -q --show-progress "$MAVEN_DOWNLOAD_URL" -O /tmp/apache-maven.tar.gz 2>/dev/null; then
            downloaded=true
        else
            rm -f /tmp/apache-maven.tar.gz
            print_warning "wget 下载失败（尝试无 sudo）..."
            if wget -q "$MAVEN_DOWNLOAD_URL" -O /tmp/apache-maven.tar.gz 2>/dev/null; then
                downloaded=true
            else
                rm -f /tmp/apache-maven.tar.gz
                print_warning "wget 下载失败，尝试 curl..."
            fi
        fi
    fi

    if [ "$downloaded" = false ] && command -v curl >/dev/null 2>&1; then
        print_info "使用 curl 下载..."
        # -f: HTTP 错误（404等）时返回非零退出码
        # -s: 静默模式  -L: 跟随重定向  --retry: 失败重试
        if curl -fsSL --retry 3 --retry-delay 2 "$MAVEN_DOWNLOAD_URL" -o /tmp/apache-maven.tar.gz; then
            downloaded=true
        else
            rm -f /tmp/apache-maven.tar.gz
            print_warning "curl 下载也失败"
        fi
    fi

    # 验证下载的文件是否为有效的 gzip 压缩包
    if [ "$downloaded" = true ]; then
        if ! gzip -t /tmp/apache-maven.tar.gz 2>/dev/null; then
            print_error "下载的文件不是有效的 gzip 压缩包（可能下载了错误页面）"
            print_info "文件大小: $(stat -c%s /tmp/apache-maven.tar.gz 2>/dev/null || echo '未知') 字节"
            print_info "文件类型: $(file /tmp/apache-maven.tar.gz 2>/dev/null || echo '未知')"
            rm -f /tmp/apache-maven.tar.gz

            # 尝试备用镜像下载
            if command -v curl >/dev/null 2>&1; then
                print_info "尝试从 Apache 镜像列表获取可用镜像..."
                local mirror_url
                mirror_url=$(curl -fsSL "https://www.apache.org/dyn/closer.lua?action=json&filename=maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz" 2>/dev/null | \
                    grep -o '"preferred":"[^"]*"' 2>/dev/null | head -1 | sed 's/.*"preferred":"//;s/"//')
                if [ -n "$mirror_url" ] && curl -fsSL --retry 3 --retry-delay 2 "$mirror_url" -o /tmp/apache-maven.tar.gz; then
                    if gzip -t /tmp/apache-maven.tar.gz 2>/dev/null; then
                        print_success "备用镜像下载成功"
                        downloaded=true
                    else
                        rm -f /tmp/apache-maven.tar.gz
                        downloaded=false
                    fi
                else
                    downloaded=false
                fi
            else
                downloaded=false
            fi
        fi
    fi

    if [ "$downloaded" = false ]; then
        rm -f /tmp/apache-maven.tar.gz
        if ! command -v wget >/dev/null 2>&1 && ! command -v curl >/dev/null 2>&1; then
            print_error "未找到 wget 或 curl，无法下载 Maven"
        else
            print_error "Maven 下载失败（wget 和 curl 均失败或下载文件损坏）"
        fi
        print_info "请手动安装 Maven $REQUIRED_MAVEN_MAJOR.$REQUIRED_MAVEN_MINOR+: https://maven.apache.org/download.cgi"
        return 1
    fi
    
    print_info "解压并安装到 $MAVEN_INSTALL_DIR..."
    sudo mkdir -p "$MAVEN_INSTALL_DIR"
    if ! sudo tar -xzf /tmp/apache-maven.tar.gz -C "$MAVEN_INSTALL_DIR" --strip-components=1 2>/dev/null; then
        print_warning "直接解压失败，尝试兼容方式..."
        # 某些旧版 tar（如 CentOS 7 busybox tar）不支持 --strip-components
        # 先解压到临时目录再移动
        local tmp_extract="/tmp/maven-extract-$$"
        mkdir -p "$tmp_extract"
        if sudo tar -xzf /tmp/apache-maven.tar.gz -C "$tmp_extract" 2>/dev/null; then
            # 找到第一层子目录（Maven 的 apache-maven-x.x.x/）
            local inner_dir
            inner_dir=$(ls -1 "$tmp_extract" | head -1)
            if [ -n "$inner_dir" ] && [ -d "$tmp_extract/$inner_dir" ]; then
                sudo cp -a "$tmp_extract/$inner_dir/"* "$MAVEN_INSTALL_DIR/" 2>/dev/null
            else
                sudo cp -a "$tmp_extract/"* "$MAVEN_INSTALL_DIR/" 2>/dev/null
            fi
            rm -rf "$tmp_extract"
        else
            rm -rf "$tmp_extract"
            print_error "解压 Maven 失败"
            rm -f /tmp/apache-maven.tar.gz
            return 1
        fi
    fi
    rm -f /tmp/apache-maven.tar.gz
    
    # 确保 /usr/local/bin 在 PATH 中且优先级高于系统路径
    sudo ln -sf "$MAVEN_INSTALL_DIR/bin/mvn" /usr/local/bin/mvn 2>/dev/null || \
    sudo ln -sf "$MAVEN_INSTALL_DIR/bin/mvn" /usr/bin/mvn 2>/dev/null || true
    
    # 重新加载 PATH
    export PATH="/usr/local/bin:$PATH"
    hash -r 2>/dev/null || true
    
    if command -v mvn >/dev/null 2>&1; then
        MAVEN_VERSION_OUTPUT=$(mvn --version 2>&1 | head -n 1)
        if check_maven_version_silent; then
            print_success "Maven 手动安装完成"
            print_info "$MAVEN_VERSION_OUTPUT"
            return 0
        fi
    fi
    
    print_error "Maven 手动安装失败，请手动安装: https://maven.apache.org/download.cgi"
    return 1
}

# 在 macOS 上安装 Maven
install_maven_mac() {
    print_step "在 macOS 上安装 Maven..."
    
    if command -v brew >/dev/null 2>&1; then
        print_info "使用 Homebrew 安装 Maven..."
        brew install maven
        
        if [ $? -eq 0 ]; then
            print_success "Maven 安装完成"
            MAVEN_VERSION_OUTPUT=$(mvn --version 2>&1 | head -n 1)
            print_info "$MAVEN_VERSION_OUTPUT"
            return 0
        else
            print_error "Homebrew 安装 Maven 失败"
            return 1
        fi
    else
        print_error "未找到 Homebrew，请先安装 Homebrew: https://brew.sh"
        return 1
    fi
}

# 在 Debian/Ubuntu 上安装 Maven
install_maven_debian() {
    print_step "在 Debian/Ubuntu 上安装 Maven $REQUIRED_MAVEN_MAJOR.$REQUIRED_MAVEN_MINOR+..."
    
    sudo apt-get update -qq
    sudo apt-get install -y -qq maven
    
    if [ $? -ne 0 ]; then
        print_warning "apt 安装失败，尝试手动安装..."
        install_maven_manual
        return $?
    fi
    
    # 验证版本是否满足要求
    if check_maven_version_silent; then
        print_success "Maven 安装完成"
        MAVEN_VERSION_OUTPUT=$(mvn --version 2>&1 | head -n 1)
        print_info "$MAVEN_VERSION_OUTPUT"
        return 0
    fi
    
    print_warning "系统包管理器的 Maven 版本过低，卸载后手动安装..."
    sudo apt-get remove -y maven 2>/dev/null || true
    install_maven_manual
    return $?
}

# 在 RHEL/CentOS/Fedora 上安装 Maven
install_maven_rhel() {
    print_step "在 RHEL/CentOS/Fedora 上安装 Maven $REQUIRED_MAVEN_MAJOR.$REQUIRED_MAVEN_MINOR+..."
    
    if command -v dnf >/dev/null 2>&1; then
        sudo dnf install -y maven
    elif command -v yum >/dev/null 2>&1; then
        sudo yum install -y maven
    else
        print_error "未找到 dnf 或 yum 包管理器"
        return 1
    fi
    
    if [ $? -ne 0 ]; then
        print_warning "包管理器安装失败，尝试手动安装..."
        install_maven_manual
        return $?
    fi
    
    # 验证安装后的版本是否满足要求（CentOS 7 的 yum 只有 Maven 3.0.x）
    if check_maven_version_silent; then
        print_success "Maven 安装完成"
        MAVEN_VERSION_OUTPUT=$(mvn --version 2>&1 | head -n 1)
        print_info "$MAVEN_VERSION_OUTPUT"
        return 0
    fi
    
    # 系统包管理器版本过低，卸载后用 Apache 官方二进制包
    print_warning "系统包管理器的 Maven 版本过低（CentOS 7 仅提供 Maven 3.0.x）"
    print_info "卸载旧版本并使用 Apache 官方 Maven $REQUIRED_MAVEN_MAJOR.$REQUIRED_MAVEN_MINOR+..."
    if command -v dnf >/dev/null 2>&1; then
        sudo dnf remove -y maven 2>/dev/null || true
    elif command -v yum >/dev/null 2>&1; then
        sudo yum remove -y maven 2>/dev/null || true
    fi
    install_maven_manual
    return $?
}

# 在 openSUSE 上安装 Maven
install_maven_suse() {
    print_step "在 openSUSE 上安装 Maven $REQUIRED_MAVEN_MAJOR.$REQUIRED_MAVEN_MINOR+..."
    
    sudo zypper install -y maven
    
    if [ $? -ne 0 ]; then
        print_warning "zypper 安装失败，尝试手动安装..."
        install_maven_manual
        return $?
    fi
    
    if check_maven_version_silent; then
        print_success "Maven 安装完成"
        MAVEN_VERSION_OUTPUT=$(mvn --version 2>&1 | head -n 1)
        print_info "$MAVEN_VERSION_OUTPUT"
        return 0
    fi
    
    print_warning "系统包管理器的 Maven 版本过低，卸载后手动安装..."
    sudo zypper remove -y maven 2>/dev/null || true
    install_maven_manual
    return $?
}

# 在 Alpine 上安装 Maven
install_maven_alpine() {
    print_step "在 Alpine Linux 上安装 Maven..."
    
    sudo apk add maven
    
    if [ $? -eq 0 ]; then
        print_success "Maven 安装完成"
        MAVEN_VERSION_OUTPUT=$(mvn --version 2>&1 | head -n 1)
        print_info "$MAVEN_VERSION_OUTPUT"
        return 0
    else
        print_error "Maven 安装失败"
        return 1
    fi
}

# 在 Arch 上安装 Maven
install_maven_arch() {
    print_step "在 Arch Linux 上安装 Maven..."
    
    sudo pacman -S --noconfirm maven
    
    if [ $? -eq 0 ]; then
        print_success "Maven 安装完成"
        MAVEN_VERSION_OUTPUT=$(mvn --version 2>&1 | head -n 1)
        print_info "$MAVEN_VERSION_OUTPUT"
        return 0
    else
        print_error "Maven 安装失败"
        return 1
    fi
}

# 在 Linux 上自动选择 Maven 安装方式
install_maven_linux() {
    DISTRO=$(detect_linux_distro)
    DISTRO_LIKE="${DISTRO_LIKE:-$DISTRO}"
    
    print_info "检测到 Linux 发行版: $DISTRO"
    
    case "$DISTRO" in
        ubuntu|debian|linuxmint|pop|elementary|zorin)
            install_maven_debian
            return $?
            ;;
        rhel|centos|fedora|rocky|almalinux|ol|amzn)
            install_maven_rhel
            return $?
            ;;
        suse|opensuse*|sled|sles)
            install_maven_suse
            return $?
            ;;
        alpine)
            install_maven_alpine
            return $?
            ;;
        arch|manjaro|endeavouros)
            install_maven_arch
            return $?
            ;;
        *)
            case "$DISTRO_LIKE" in
                *debian*|*ubuntu*)
                    install_maven_debian
                    return $?
                    ;;
                *rhel*|*fedora*|*centos*)
                    install_maven_rhel
                    return $?
                    ;;
                *suse*)
                    install_maven_suse
                    return $?
                    ;;
                *arch*)
                    install_maven_arch
                    return $?
                    ;;
            esac
            
            print_warning "未识别的 Linux 发行版: $DISTRO"
            print_info "请手动安装 Maven $REQUIRED_MAVEN_MAJOR.$REQUIRED_MAVEN_MINOR+: https://maven.apache.org/download.cgi"
            return 1
            ;;
    esac
}

# ==========================================
# Node.js 和 npm 相关函数
# ==========================================

# 项目要求的 Node.js 最低版本
REQUIRED_NODE_MAJOR=18
# npm 最低版本（随 Node.js 18 自带 npm 9+）
REQUIRED_NPM_MAJOR=9

# 检查 Node.js 是否已安装且版本符合要求
check_nodejs_installed() {
    print_step "检查 Node.js 环境..."
    
    if command -v node >/dev/null 2>&1; then
        local node_version_raw
        node_version_raw=$(node --version 2>&1)

        # 检测 glibc 不兼容问题（CentOS 7 glibc 2.17 无法运行 Node.js 18+）
        if echo "$node_version_raw" | grep -q "GLIBC"; then
            print_error "Node.js 无法运行：系统 glibc 版本过低"
            print_info "错误详情: $node_version_raw"
            print_info ""
            print_info "原因: 当前系统 glibc 版本过低（CentOS 7 为 glibc 2.17），"
            print_info "      Node.js 18+ 预编译二进制需要 glibc 2.28+ 才能运行。"
            print_info ""
            print_info "解决方案:"
            print_info "  1. 升级系统到 CentOS 8+ / Ubuntu 20.04+ / Debian 11+"
            print_info "  2. 使用 Docker 运行: docker-compose up -d"
            print_info "  3. 从源码编译 Node.js（耗时较长，不推荐）"
            return 2  # glibc 不兼容
        fi

        NODE_VERSION_OUTPUT=$(echo "$node_version_raw" | sed 's/v//')
        NODE_MAJOR=$(echo "$NODE_VERSION_OUTPUT" | cut -d'.' -f1)
        
        if [ "$NODE_MAJOR" -ge "$REQUIRED_NODE_MAJOR" ] 2>/dev/null; then
            print_success "Node.js $NODE_VERSION_OUTPUT 已安装 (要求 >= $REQUIRED_NODE_MAJOR)"
            return 0
        else
            print_warning "Node.js 版本 v$NODE_VERSION_OUTPUT 低于要求的 v$REQUIRED_NODE_MAJOR"
            return 1
        fi
    fi
    
    print_warning "未检测到 Node.js $REQUIRED_NODE_MAJOR+"
    return 1
}

# 检查 npm 是否已安装且版本符合要求
check_npm_installed() {
    print_step "检查 npm 环境..."
    
    if command -v npm >/dev/null 2>&1; then
        NPM_VERSION_OUTPUT=$(npm --version 2>&1)
        NPM_MAJOR=$(echo "$NPM_VERSION_OUTPUT" | cut -d'.' -f1)
        
        if [ "$NPM_MAJOR" -ge "$REQUIRED_NPM_MAJOR" ] 2>/dev/null; then
            print_success "npm $NPM_VERSION_OUTPUT 已安装 (要求 >= $REQUIRED_NPM_MAJOR)"
            return 0
        else
            print_warning "npm 版本 $NPM_VERSION_OUTPUT 低于要求的 $REQUIRED_NPM_MAJOR"
            return 1
        fi
    fi
    
    print_warning "未检测到 npm $REQUIRED_NPM_MAJOR+"
    return 1
}

# 在 macOS 上安装 Node.js (使用 NodeSource 安装指定主版本)
install_nodejs_mac() {
    print_step "在 macOS 上安装 Node.js $REQUIRED_NODE_MAJOR..."
    
    if command -v brew >/dev/null 2>&1; then
        print_info "使用 Homebrew 安装 Node.js $REQUIRED_NODE_MAJOR..."
        brew install node@$REQUIRED_NODE_MAJOR
        
        if [ $? -eq 0 ]; then
            # 链接到 PATH
            print_info "链接 Node.js $REQUIRED_NODE_MAJOR 到系统 PATH..."
            brew link --force --overwrite node@$REQUIRED_NODE_MAJOR 2>/dev/null || true
            
            print_success "Node.js 安装完成"
            NODE_VERSION_OUTPUT=$(node --version 2>&1)
            print_info "Node.js: $NODE_VERSION_OUTPUT"
            NPM_VERSION_OUTPUT=$(npm --version 2>&1)
            print_info "npm: v$NPM_VERSION_OUTPUT"
            return 0
        else
            print_error "Homebrew 安装 Node.js 失败"
            return 1
        fi
    else
        print_error "未找到 Homebrew，请先安装 Homebrew: https://brew.sh"
        return 1
    fi
}

# 使用 NodeSource 安装 Node.js (通用方法，适用于大多数 Linux 发行版)
install_nodejs_nodesource() {
    print_step "使用 NodeSource 安装 Node.js $REQUIRED_NODE_MAJOR.x..."
    
    print_info "下载并执行 NodeSource 安装脚本..."
    curl -fsSL "https://deb.nodesource.com/setup_${REQUIRED_NODE_MAJOR}.x" -o /tmp/nodesource_setup.sh
    sudo bash /tmp/nodesource_setup.sh
    rm -f /tmp/nodesource_setup.sh
    
    sudo apt-get install -y nodejs
    
    if [ $? -eq 0 ]; then
        print_success "Node.js 安装完成"
        NODE_VERSION_OUTPUT=$(node --version 2>&1)
        print_info "Node.js: $NODE_VERSION_OUTPUT"
        NPM_VERSION_OUTPUT=$(npm --version 2>&1)
        print_info "npm: v$NPM_VERSION_OUTPUT"
        return 0
    else
        print_error "Node.js 安装失败"
        return 1
    fi
}

# 在 Debian/Ubuntu 上安装 Node.js
install_nodejs_debian() {
    print_step "在 Debian/Ubuntu 上安装 Node.js $REQUIRED_NODE_MAJOR..."
    print_info "发行版版本: ${DISTRO_VERSION:-未知}"
    
    case "$DISTRO" in
        ubuntu|linuxmint|pop|elementary|zorin)
            UBUNTU_MAJOR=$(echo "$DISTRO_VERSION" | cut -d'.' -f1)
            
            if [ "$UBUNTU_MAJOR" -ge 20 ] 2>/dev/null; then
                # Ubuntu 20.04+ 使用 NodeSource
                install_nodejs_nodesource
                return $?
            else
                # 老版本 Ubuntu
                print_warning "Ubuntu $DISTRO_VERSION 较旧，尝试使用 NodeSource..."
                install_nodejs_nodesource
                return $?
            fi
            ;;
        debian)
            DEBIAN_MAJOR=$(echo "$DISTRO_VERSION" | cut -d'.' -f1)
            
            if [ "$DEBIAN_MAJOR" -ge 11 ] 2>/dev/null; then
                install_nodejs_nodesource
                return $?
            else
                print_warning "Debian $DISTRO_VERSION 较旧，尝试使用 NodeSource..."
                install_nodejs_nodesource
                return $?
            fi
            ;;
        *)
            install_nodejs_nodesource
            return $?
            ;;
    esac
}

# 在 RHEL/CentOS/Fedora 上安装 Node.js
install_nodejs_rhel() {
    print_step "在 RHEL/CentOS/Fedora 上安装 Node.js $REQUIRED_NODE_MAJOR..."
    print_info "发行版版本: ${DISTRO_VERSION:-未知}"
    
    case "$DISTRO" in
        fedora)
            print_info "Fedora $DISTRO_VERSION，使用 dnf 安装..."
            sudo dnf module install -y nodejs:$REQUIRED_NODE_MAJOR 2>/dev/null || \
            sudo dnf install -y nodejs
            ;;
        rhel|centos|rocky|almalinux|ol)
            RHEL_MAJOR=$(echo "$DISTRO_VERSION" | cut -d'.' -f1)
            
            if [ "$RHEL_MAJOR" -ge 8 ] 2>/dev/null; then
                print_info "RHEL $DISTRO_VERSION (>=8)，使用 dnf module 安装..."
                sudo dnf module install -y nodejs:$REQUIRED_NODE_MAJOR 2>/dev/null || \
                sudo dnf install -y nodejs
            elif [ "$RHEL_MAJOR" -eq 7 ] 2>/dev/null; then
                # CentOS/RHEL 7 glibc 2.17 不支持 Node.js 18+ 的 RPM 包（需要 glibc 2.28+）
                # 跳过 NodeSource RPM，直接走 nvm（如有兼容构建仍可安装）
                print_warning "CentOS/RHEL 7 的 glibc 2.17 不兼容 Node.js 18 RPM 包"
                print_info "跳过 RPM 安装，交由 nvm 处理..."
                return 2  # 特殊返回码：让调用者走 nvm 路径
            else
                print_warning "版本过旧，尝试使用 NodeSource..."
                curl -fsSL "https://rpm.nodesource.com/setup_${REQUIRED_NODE_MAJOR}.x" -o /tmp/nodesource_setup.sh
                sudo bash /tmp/nodesource_setup.sh
                rm -f /tmp/nodesource_setup.sh
                if command -v yum >/dev/null 2>&1; then
                    sudo yum install -y nodejs
                elif command -v dnf >/dev/null 2>&1; then
                    sudo dnf install -y nodejs
                fi
            fi
            ;;
        amzn)
            if command -v dnf >/dev/null 2>&1; then
                sudo dnf install -y nodejs
            else
                curl -fsSL "https://rpm.nodesource.com/setup_${REQUIRED_NODE_MAJOR}.x" -o /tmp/nodesource_setup.sh
                sudo bash /tmp/nodesource_setup.sh
                rm -f /tmp/nodesource_setup.sh
                sudo yum install -y nodejs
            fi
            ;;
        *)
            if command -v dnf >/dev/null 2>&1; then
                sudo dnf module install -y nodejs:$REQUIRED_NODE_MAJOR 2>/dev/null || \
                sudo dnf install -y nodejs
            elif command -v yum >/dev/null 2>&1; then
                curl -fsSL "https://rpm.nodesource.com/setup_${REQUIRED_NODE_MAJOR}.x" -o /tmp/nodesource_setup.sh
                sudo bash /tmp/nodesource_setup.sh
                rm -f /tmp/nodesource_setup.sh
                sudo yum install -y nodejs
            fi
            ;;
    esac
    
    if [ $? -eq 0 ]; then
        print_success "Node.js 安装完成"
        NODE_VERSION_OUTPUT=$(node --version 2>&1)
        print_info "Node.js: $NODE_VERSION_OUTPUT"
        NPM_VERSION_OUTPUT=$(npm --version 2>&1)
        print_info "npm: v$NPM_VERSION_OUTPUT"
        return 0
    else
        print_error "Node.js 安装失败"
        return 1
    fi
}

# 在 openSUSE 上安装 Node.js
install_nodejs_suse() {
    print_step "在 openSUSE 上安装 Node.js $REQUIRED_NODE_MAJOR..."
    
    sudo zypper install -y nodejs$REQUIRED_NODE_MAJOR 2>/dev/null || \
    sudo zypper install -y nodejs
    
    if [ $? -eq 0 ]; then
        print_success "Node.js 安装完成"
        NODE_VERSION_OUTPUT=$(node --version 2>&1)
        print_info "Node.js: $NODE_VERSION_OUTPUT"
        NPM_VERSION_OUTPUT=$(npm --version 2>&1)
        print_info "npm: v$NPM_VERSION_OUTPUT"
        return 0
    else
        print_error "Node.js 安装失败"
        return 1
    fi
}

# 在 Alpine 上安装 Node.js
install_nodejs_alpine() {
    print_step "在 Alpine Linux 上安装 Node.js $REQUIRED_NODE_MAJOR..."
    
    sudo apk add nodejs npm
    
    if [ $? -eq 0 ]; then
        print_success "Node.js 安装完成"
        NODE_VERSION_OUTPUT=$(node --version 2>&1)
        print_info "Node.js: $NODE_VERSION_OUTPUT"
        NPM_VERSION_OUTPUT=$(npm --version 2>&1)
        print_info "npm: v$NPM_VERSION_OUTPUT"
        return 0
    else
        print_error "Node.js 安装失败"
        return 1
    fi
}

# 在 Arch 上安装 Node.js
install_nodejs_arch() {
    print_step "在 Arch Linux 上安装 Node.js..."
    
    sudo pacman -S --noconfirm nodejs npm
    
    if [ $? -eq 0 ]; then
        print_success "Node.js 安装完成"
        NODE_VERSION_OUTPUT=$(node --version 2>&1)
        print_info "Node.js: $NODE_VERSION_OUTPUT"
        NPM_VERSION_OUTPUT=$(npm --version 2>&1)
        print_info "npm: v$NPM_VERSION_OUTPUT"
        return 0
    else
        print_error "Node.js 安装失败"
        return 1
    fi
}

# 在 Linux 上自动选择 Node.js 安装方式
install_nodejs_linux() {
    DISTRO=$(detect_linux_distro)
    DISTRO_LIKE="${DISTRO_LIKE:-$DISTRO}"
    
    print_info "检测到 Linux 发行版: $DISTRO"
    
    case "$DISTRO" in
        ubuntu|debian|linuxmint|pop|elementary|zorin)
            install_nodejs_debian
            return $?
            ;;
        rhel|centos|fedora|rocky|almalinux|ol|amzn)
            install_nodejs_rhel
            return $?
            ;;
        suse|opensuse*|sled|sles)
            install_nodejs_suse
            return $?
            ;;
        alpine)
            install_nodejs_alpine
            return $?
            ;;
        arch|manjaro|endeavouros)
            install_nodejs_arch
            return $?
            ;;
        *)
            case "$DISTRO_LIKE" in
                *debian*|*ubuntu*)
                    install_nodejs_debian
                    return $?
                    ;;
                *rhel*|*fedora*|*centos*)
                    install_nodejs_rhel
                    return $?
                    ;;
                *suse*)
                    install_nodejs_suse
                    return $?
                    ;;
                *arch*)
                    install_nodejs_arch
                    return $?
                    ;;
            esac
            
            print_warning "未识别的 Linux 发行版: $DISTRO"
            print_info "请手动安装 Node.js $REQUIRED_NODE_MAJOR+: https://nodejs.org/"
            return 1
            ;;
    esac
}

# 使用 nvm 安装 Node.js (跨平台兜底方案)
install_nodejs_nvm() {
    print_step "使用 nvm 安装 Node.js $REQUIRED_NODE_MAJOR..."
    
    NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
    
    # 检查 nvm 是否已安装
    if [ ! -d "$NVM_DIR" ]; then
        print_info "安装 nvm..."
        curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
        
        if [ $? -ne 0 ]; then
            print_error "nvm 安装失败"
            return 1
        fi
    else
        print_info "nvm 已安装"
    fi
    
    # 加载 nvm
    export NVM_DIR="$HOME/.nvm"
    [ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"
    [ -s "$NVM_DIR/bash_completion" ] && \. "$NVM_DIR/bash_completion"
    
    if command -v nvm >/dev/null 2>&1; then
        print_info "使用 nvm 安装 Node.js $REQUIRED_NODE_MAJOR LTS..."
        nvm install $REQUIRED_NODE_MAJOR
        nvm use $REQUIRED_NODE_MAJOR
        nvm alias default $REQUIRED_NODE_MAJOR
        
        # 验证 Node.js 是否可正常运行（检测 glibc 不兼容）
        local node_check
        node_check=$(node --version 2>&1)
        if echo "$node_check" | grep -q "GLIBC"; then
            print_error "nvm 安装的 Node.js 无法运行：系统 glibc 版本过低"
            echo ""
            print_info "错误详情: $node_check"
            echo ""
            print_info "原因: 当前系统 glibc 版本过低（CentOS 7 为 glibc 2.17），"
            print_info "      Node.js 18+ 预编译二进制需要 glibc 2.28+ 才能运行。"
            print_info "      nvm 下载的也是同样的预编译二进制，因此同样无法运行。"
            echo ""
            print_info "解决方案:"
            print_info "  1. 升级系统到 CentOS 8+ / Ubuntu 20.04+ / Debian 11+"
            print_info "  2. 使用 Docker 运行（推荐）: docker-compose up -d"
            print_info "  3. 从源码编译 Node.js（耗时较长，不推荐）"
            return 1
        fi

        print_success "Node.js 安装完成"
        NODE_VERSION_OUTPUT=$node_check
        print_info "Node.js: $NODE_VERSION_OUTPUT"
        NPM_VERSION_OUTPUT=$(npm --version 2>&1)
        print_info "npm: v$NPM_VERSION_OUTPUT"
        return 0
    else
        print_error "nvm 加载失败"
        return 1
    fi
}

# 打印使用说明
print_usage_guide() {
    JAVA21_HOME=$(get_java21_home)
    
    echo ""
    print_info "=========================================="
    print_success "环境安装完成！"
    print_info "=========================================="
    echo ""
    
    # Java 21 信息
    if [ -n "$JAVA21_HOME" ]; then
        print_info "Java 21 安装路径: $JAVA21_HOME"
        echo ""
        
        if [ "$PLATFORM" = "Mac" ]; then
            print_warning "请将以下内容添加到 shell 配置文件："
            echo ""
            echo "  export JAVA_HOME=$JAVA21_HOME"
            echo "  export PATH=\"\$JAVA_HOME/bin:\$PATH\""
            echo ""
            print_info "如果使用 zsh，执行: echo 'export JAVA_HOME=$JAVA21_HOME' >> ~/.zshrc"
            print_info "如果使用 bash，执行: echo 'export JAVA_HOME=$JAVA21_HOME' >> ~/.bash_profile"
            echo ""
            print_info "然后执行: source ~/.zshrc  或  source ~/.bash_profile"
        else
            print_info "JAVA_HOME 已在当前会话中设置"
            print_warning "如需永久生效，请将以下内容添加到 ~/.bashrc 或 ~/.zshrc："
            echo ""
            echo "  export JAVA_HOME=$JAVA21_HOME"
            echo "  export PATH=\"\$JAVA_HOME/bin:\$PATH\""
            echo ""
        fi
    fi
    
    # Maven 信息
    if command -v mvn >/dev/null 2>&1; then
        print_info "Maven: $(mvn --version 2>&1 | head -n 1)"
    fi
    
    # Node.js 信息
    if command -v node >/dev/null 2>&1; then
        print_info "Node.js: $(node --version 2>&1)"
    fi
    if command -v npm >/dev/null 2>&1; then
        print_info "npm: v$(npm --version 2>&1)"
    fi
    
    echo ""
    print_info "下一步操作："
    print_info "  1. 执行 './build-all-in-one.sh' 构建项目"
    print_info "  2. 执行 './run.sh' 启动服务"
    echo ""
}

# ==========================================
# 主流程
# ==========================================

echo ""

# 0. 确保 sudo 可用（Linux 下缺失时自动安装）
if [ "$PLATFORM" = "Linux" ]; then
    # 提前检测发行版信息（用于 ensure_sudo 和后续安装）
    detect_linux_distro > /dev/null
    print_info "检测到 Linux 发行版: $DISTRO (版本: ${DISTRO_VERSION:-未知})"
    echo ""
    ensure_sudo || {
        print_error "sudo 安装失败，无法继续"
        exit 1
    }
    echo ""
fi

# 安装状态追踪
NEED_INSTALL=false
JAVA_READY=false
MAVEN_READY=false
NODE_READY=false
NPM_READY=false

# ==========================================
# 1. 检查 Java 21
# ==========================================
if check_java21_installed; then
    JAVA_READY=true
    JAVA21_HOME=$(get_java21_home)
    if [ -n "$JAVA21_HOME" ]; then
        export JAVA_HOME="$JAVA21_HOME"
        export PATH="$JAVA_HOME/bin:$PATH"
        print_info "JAVA_HOME 已设置为: $JAVA_HOME"
    fi
else
    NEED_INSTALL=true
fi

echo ""

# ==========================================
# 2. 检查 Maven
# ==========================================
if check_maven_installed; then
    MAVEN_READY=true
else
    NEED_INSTALL=true
fi

echo ""

# ==========================================
# 3. 检查 Node.js
# ==========================================
check_nodejs_installed
NODE_CHECK_RESULT=$?
if [ $NODE_CHECK_RESULT -eq 0 ]; then
    NODE_READY=true
elif [ $NODE_CHECK_RESULT -eq 2 ]; then
    # glibc 不兼容，已打印错误信息，跳过安装
    NODE_READY=false
    INSTALL_HAS_ERROR=true
else
    NEED_INSTALL=true
fi

echo ""

# ==========================================
# 4. 检查 npm
# ==========================================
if check_npm_installed; then
    NPM_READY=true
else
    NEED_INSTALL=true
fi

echo ""

# 如果所有工具都已就绪，直接退出
if [ "$JAVA_READY" = true ] && [ "$MAVEN_READY" = true ] && [ "$NODE_READY" = true ] && [ "$NPM_READY" = true ]; then
    print_success "所有依赖环境已就绪，无需安装"
    print_info "可直接运行 ./build-all-in-one.sh 构建项目"
    exit 0
fi

# 如果有需要安装的，汇总显示
echo ""
print_step "=========================================="
print_step "  环境检测总结"
print_step "=========================================="
[ "$JAVA_READY" = true ] && print_success "Java 21     - 已就绪" || print_warning "Java 21     - 需要安装"
[ "$MAVEN_READY" = true ] && print_success "Maven $REQUIRED_MAVEN_MAJOR.$REQUIRED_MAVEN_MINOR+  - 已就绪" || print_warning "Maven $REQUIRED_MAVEN_MAJOR.$REQUIRED_MAVEN_MINOR+  - 需要安装"
[ "$NODE_READY" = true ] && print_success "Node.js $REQUIRED_NODE_MAJOR+ - 已就绪" || print_warning "Node.js $REQUIRED_NODE_MAJOR+ - 需要安装"
[ "$NPM_READY" = true ] && print_success "npm $REQUIRED_NPM_MAJOR+    - 已就绪" || print_warning "npm $REQUIRED_NPM_MAJOR+    - 需要安装"
echo ""

# 确认安装（默认为 y 自动安装）
if [ "$NEED_INSTALL" = true ]; then
    print_info "即将安装缺失的依赖工具，部分操作需要管理员权限"
    echo ""
fi

INSTALL_HAS_ERROR=false

# ==========================================
# 5. 安装 Java 21（如需要）
# ==========================================
if [ "$JAVA_READY" != true ]; then
    echo ""
    print_info "----------------------------------------"
    print_step "开始安装 Java 21"
    print_info "----------------------------------------"
    echo ""
    
    case "${PLATFORM}" in
        Mac)
            install_java21_mac || INSTALL_HAS_ERROR=true
            ;;
        Linux)
            install_java21_linux || INSTALL_HAS_ERROR=true
            ;;
        Windows)
            print_error "Windows 系统暂不支持自动安装 Java 21"
            print_info "请手动下载安装 JDK 21: https://adoptium.net/download/"
            INSTALL_HAS_ERROR=true
            ;;
        *)
            print_error "不支持的操作系统: $PLATFORM"
            INSTALL_HAS_ERROR=true
            ;;
    esac
    
    if [ "$INSTALL_HAS_ERROR" != true ]; then
        verify_java || INSTALL_HAS_ERROR=true
        # 安装后设置环境变量
        JAVA21_HOME=$(get_java21_home)
        if [ -n "$JAVA21_HOME" ]; then
            export JAVA_HOME="$JAVA21_HOME"
            export PATH="$JAVA_HOME/bin:$PATH"
            print_info "JAVA_HOME 已设置为: $JAVA_HOME"
        fi
    fi
fi

# ==========================================
# 6. 安装 Maven（如需要）
# ==========================================
if [ "$MAVEN_READY" != true ]; then
    echo ""
    print_info "----------------------------------------"
    print_step "开始安装 Maven $REQUIRED_MAVEN_MAJOR.$REQUIRED_MAVEN_MINOR+"
    print_info "----------------------------------------"
    echo ""
    
    case "${PLATFORM}" in
        Mac)
            install_maven_mac || INSTALL_HAS_ERROR=true
            ;;
        Linux)
            install_maven_linux || INSTALL_HAS_ERROR=true
            ;;
        *)
            print_error "不支持的操作系统: $PLATFORM"
            print_info "请手动安装 Maven: https://maven.apache.org/download.cgi"
            INSTALL_HAS_ERROR=true
            ;;
    esac
fi

# ==========================================
# 7. 安装 Node.js 和 npm（如需要）
# ==========================================
if [ "$NODE_READY" != true ] || [ "$NPM_READY" != true ]; then
    echo ""
    print_info "----------------------------------------"
    print_step "开始安装 Node.js $REQUIRED_NODE_MAJOR.x (含 npm)"
    print_info "----------------------------------------"
    echo ""
    
    INSTALL_NODE_METHOD=""
    
    case "${PLATFORM}" in
        Mac)
            install_nodejs_mac
            NODE_RESULT=$?
            if [ $NODE_RESULT -ne 0 ]; then
                print_warning "Homebrew 安装失败，尝试使用 nvm..."
                install_nodejs_nvm || INSTALL_HAS_ERROR=true
            fi
            ;;
        Linux)
            install_nodejs_linux
            NODE_RESULT=$?
            if [ $NODE_RESULT -ne 0 ]; then
                print_warning "包管理器安装失败，尝试使用 nvm..."
                install_nodejs_nvm || INSTALL_HAS_ERROR=true
            fi
            ;;
        *)
            print_error "不支持的操作系统: $PLATFORM"
            print_info "尝试使用 nvm 安装..."
            install_nodejs_nvm || INSTALL_HAS_ERROR=true
            ;;
    esac
fi

echo ""

# ==========================================
# 8. 最终验证和总结
# ==========================================
echo ""
print_info "=========================================="
print_step "  安装结果验证"
print_info "=========================================="
echo ""

VERIFY_OK=true

# 验证 Java
if command -v java >/dev/null 2>&1; then
    JAVA_VER=$(java -version 2>&1 | head -n 1)
    print_success "Java:  $JAVA_VER"
else
    print_error "Java:  未安装"
    VERIFY_OK=false
fi

# 验证 Maven
if command -v mvn >/dev/null 2>&1; then
    MAVEN_VER=$(mvn --version 2>&1 | head -n 1)
    print_success "Maven: $MAVEN_VER"
else
    print_error "Maven: 未安装"
    VERIFY_OK=false
fi

# 验证 Node.js
if command -v node >/dev/null 2>&1; then
    NODE_VER=$(node --version 2>&1)
    print_success "Node:  $NODE_VER"
else
    print_error "Node:  未安装"
    VERIFY_OK=false
fi

# 验证 npm
if command -v npm >/dev/null 2>&1; then
    NPM_VER=$(npm --version 2>&1)
    print_success "npm:   v$NPM_VER"
else
    print_error "npm:   未安装"
    VERIFY_OK=false
fi

echo ""

if [ "$VERIFY_OK" = true ]; then
    print_usage_guide
    exit 0
else
    print_warning "部分工具安装失败，请检查上述错误信息"
    echo ""
    print_info "手动安装参考："
    print_info "  Java 21:  https://adoptium.net/download/"
    print_info "  Maven:    https://maven.apache.org/download.cgi"
    print_info "  Node.js:  https://nodejs.org/"
    print_info "  或使用 nvm: curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash"
    exit 1
fi
