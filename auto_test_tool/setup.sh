#!/usr/bin/env bash
# ============================================
# API Server 自动化测试工具 - 环境安装脚本
# ============================================
# 自动检测并安装: Python3, pip, 虚拟环境, 项目依赖
# 支持: Linux (Ubuntu/Debian/CentOS/RHEL/Fedora), macOS, CNB 云开发环境
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WORKSPACE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
VENV_DIR="${SCRIPT_DIR}/.venv"
REQUIREMENTS_FILE="${SCRIPT_DIR}/requirements.txt"
CONFIG_FILE="${SCRIPT_DIR}/config/auto_test.config"
MIN_PYTHON_VERSION="3.9"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info()  { echo -e "${BLUE}[INFO]${NC}  $*"; }
log_ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }
log_step()  { echo -e "\n${CYAN}>>>${NC} $*"; }

# ---------- 检测操作系统 ----------
detect_os() {
    case "$(uname -s)" in
        Linux*)     OS="linux"
                    if [ -f /etc/debian_version ]; then
                        OS_FAMILY="debian"
                    elif [ -f /etc/redhat-release ]; then
                        OS_FAMILY="redhat"
                    else
                        OS_FAMILY="unknown"
                    fi
                    ;;
        Darwin*)    OS="macos"
                    OS_FAMILY="macos"
                    ;;
        *)          OS="unknown"
                    OS_FAMILY="unknown"
                    ;;
    esac
    log_info "操作系统: ${OS} (${OS_FAMILY})"
}

# ---------- 检测 CNB 云开发环境 ----------
detect_cnb_env() {
    CNB_ENV=false
    CNB_PUBLIC_URL=""

    # CNB 云开发环境通常有以下特征
    # 检查常见环境变量
    if [ -n "${CNB_PUBLIC_URL}" ]; then
        CNB_ENV=true
        CNB_PUBLIC_URL="${CNB_PUBLIC_URL}"
    elif [ -n "${PUBLIC_URL}" ]; then
        CNB_ENV=true
        CNB_PUBLIC_URL="${PUBLIC_URL}"
    elif [ -n "${CODING_PUBLIC_URL}" ]; then
        CNB_ENV=true
        CNB_PUBLIC_URL="${CODING_PUBLIC_URL}"
    elif [ -n "${APP_URL}" ]; then
        CNB_ENV=true
        CNB_PUBLIC_URL="${APP_URL}"
    fi

    # 尝试从 workspace 相关环境变量推导
    if [ "$CNB_ENV" = false ] && [ -n "${WORKSPACE_URL}" ]; then
        CNB_ENV=true
        CNB_PUBLIC_URL="${WORKSPACE_URL}"
    fi

    if [ "$CNB_ENV" = true ]; then
        # 去掉尾部斜杠
        CNB_PUBLIC_URL="${CNB_PUBLIC_URL%/}"
        log_info "检测到 CNB 云开发环境"
        log_info "公网访问地址: ${CNB_PUBLIC_URL}"
    else
        log_info "本地开发环境"
    fi

    export CNB_ENV
    export CNB_PUBLIC_URL
}

# ---------- 检测是否需要 sudo ----------
SUDO=""
if [ "$(id -u)" -ne 0 ]; then
    if command -v sudo &>/dev/null; then
        SUDO="sudo"
    else
        log_warn "非 root 用户且 sudo 不可用，尝试无特权安装"
    fi
fi

# ---------- 检测 Python3 ----------
check_python() {
    PYTHON_CMD=""
    for cmd in python3.12 python3.11 python3.10 python3.9 python3; do
        if command -v "$cmd" &>/dev/null; then
            version=$("$cmd" -c 'import sys; print(".".join(map(str, sys.version_info[:2])))' 2>/dev/null || echo "0")
            major=$(echo "$version" | cut -d. -f1)
            minor=$(echo "$version" | cut -d. -f2)
            if [ "$major" -ge 3 ] && [ "$minor" -ge 9 ] 2>/dev/null; then
                PYTHON_CMD="$cmd"
                PYTHON_VERSION="$version"
                break
            fi
        fi
    done

    if [ -z "$PYTHON_CMD" ]; then
        log_warn "未找到 Python ${MIN_PYTHON_VERSION}+"
        return 1
    fi

    log_ok "Python 版本: ${PYTHON_VERSION} ($($PYTHON_CMD --version 2>&1))"
    log_info "Python 路径: $(which "$PYTHON_CMD")"
    return 0
}

# ---------- 安装 Python ----------
install_python() {
    log_step "正在安装 Python ${MIN_PYTHON_VERSION}+ ..."

    case "$OS_FAMILY" in
        debian)
            log_info "使用 apt 安装 Python..."
            $SUDO apt-get update -qq 2>&1 | tail -1
            $SUDO apt-get install -y -qq python3 python3-pip python3-venv python3-dev 2>&1 | tail -3
            ;;
        redhat)
            log_info "使用 yum/dnf 安装 Python..."
            if command -v dnf &>/dev/null; then
                $SUDO dnf install -y python3 python3-pip python3-devel 2>&1 | tail -3
            else
                $SUDO yum install -y python3 python3-pip python3-devel 2>&1 | tail -3
            fi
            ;;
        macos)
            if command -v brew &>/dev/null; then
                log_info "使用 Homebrew 安装 Python..."
                brew install python@3.12 2>&1 | tail -3
            else
                log_error "请先安装 Homebrew: https://brew.sh"
                exit 1
            fi
            ;;
        *)
            log_error "无法自动安装 Python，请手动安装 Python ${MIN_PYTHON_VERSION}+"
            log_error "下载地址: https://www.python.org/downloads/"
            exit 1
            ;;
    esac

    if ! check_python; then
        log_error "Python 安装后仍无法检测到，请手动检查"
        exit 1
    fi
}

# ---------- 检测 pip ----------
check_pip() {
    if ! "$PYTHON_CMD" -m pip --version &>/dev/null; then
        log_warn "pip 不可用"
        return 1
    fi
    log_ok "pip 版本: $("$PYTHON_CMD" -m pip --version 2>&1)"
    return 0
}

# ---------- 安装 pip ----------
install_pip() {
    log_step "正在安装 pip..."
    case "$OS_FAMILY" in
        debian)
            $SUDO apt-get install -y -qq python3-pip 2>&1 | tail -3
            ;;
        redhat)
            if command -v dnf &>/dev/null; then
                $SUDO dnf install -y python3-pip 2>&1 | tail -3
            else
                $SUDO yum install -y python3-pip 2>&1 | tail -3
            fi
            ;;
        *)
            curl -sS https://bootstrap.pypa.io/get-pip.py -o /tmp/get-pip.py
            "$PYTHON_CMD" /tmp/get-pip.py --user --quiet
            rm -f /tmp/get-pip.py
            ;;
    esac
}

# ---------- 创建虚拟环境 ----------
setup_venv() {
    log_step "创建 Python 虚拟环境..."

    if [ -d "$VENV_DIR" ]; then
        log_info "虚拟环境已存在: ${VENV_DIR}"
        if [ -f "${VENV_DIR}/bin/python" ]; then
            venv_version=$("${VENV_DIR}/bin/python" -c 'import sys; print(".".join(map(str, sys.version_info[:2])))' 2>/dev/null || echo "0")
            if [ "$venv_version" != "$PYTHON_VERSION" ]; then
                log_warn "虚拟环境 Python 版本 (${venv_version}) 与当前 (${PYTHON_VERSION}) 不一致，重建中..."
                rm -rf "$VENV_DIR"
            fi
        fi
    fi

    if [ ! -d "$VENV_DIR" ]; then
        "$PYTHON_CMD" -m venv "$VENV_DIR"
        log_ok "虚拟环境已创建: ${VENV_DIR}"
    else
        log_ok "使用现有虚拟环境: ${VENV_DIR}"
    fi

    VENV_PYTHON="${VENV_DIR}/bin/python"
    VENV_PIP="${VENV_DIR}/bin/pip"
    log_info "虚拟环境 Python: ${VENV_PYTHON}"
}

# ---------- 升级 pip ----------
upgrade_pip() {
    log_info "升级 pip 到最新版本..."
    "$VENV_PIP" install --upgrade pip -q 2>&1 | tail -1
    log_ok "pip 已升级"
}

# ---------- 安装项目依赖 ----------
install_dependencies() {
    log_step "安装项目依赖..."

    if [ ! -f "$REQUIREMENTS_FILE" ]; then
        log_error "未找到 requirements.txt: ${REQUIREMENTS_FILE}"
        exit 1
    fi

    log_info "依赖包:"
    while IFS= read -r line; do
        [ -n "$line" ] && [ "${line:0:1}" != "#" ] && echo "         - $line"
    done < "$REQUIREMENTS_FILE"

    echo ""
    "$VENV_PIP" install -r "$REQUIREMENTS_FILE" -q 2>&1

    if [ $? -eq 0 ]; then
        log_ok "所有依赖安装成功"
    else
        log_error "依赖安装失败，尝试逐个安装..."
        while IFS= read -r line; do
            [ -z "$line" ] && continue
            [ "${line:0:1}" = "#" ] && continue
            pkg=$(echo "$line" | cut -d'>' -f1 | cut -d'=' -f1 | cut -d'<' -f1 | xargs)
            log_info "安装: ${pkg}"
            "$VENV_PIP" install "$line" -q 2>&1 || log_warn "  ${pkg} 安装失败，跳过"
        done < "$REQUIREMENTS_FILE"
    fi
}

# ---------- 配置测试地址 ----------
configure_test_url() {
    log_step "配置测试目标地址..."

    local target_url=""

    # 优先级: 命令行参数 > CNB 公网地址 > 配置文件已有 > localhost
    if [ -n "$1" ]; then
        target_url="$1"
        log_info "使用命令行指定的地址: ${target_url}"
    elif [ "$CNB_ENV" = true ] && [ -n "$CNB_PUBLIC_URL" ]; then
        target_url="$CNB_PUBLIC_URL"
        log_info "使用 CNB 公网地址: ${target_url}"
    elif [ -n "${TEST_BASE_URL}" ]; then
        target_url="${TEST_BASE_URL}"
        log_info "使用环境变量 TEST_BASE_URL: ${target_url}"
    else
        # 检查 localhost:8080 是否可达
        if curl -s -o /dev/null -w "%{http_code}" --max-time 3 "http://localhost:8080" 2>/dev/null | grep -q "200\|302\|401\|403"; then
            target_url="http://localhost:8080"
            log_info "检测到本地服务: ${target_url}"
        else
            target_url="http://localhost:8080"
            log_warn "未检测到运行中的服务，使用默认地址: ${target_url}"
        fi
    fi

    # 更新配置文件中的 base_url
    if [ -f "$CONFIG_FILE" ]; then
        # 使用 sed 更新 base_url
        if [ "$(uname -s)" = "Darwin" ]; then
            sed -i '' "s|^base_url = .*|base_url = ${target_url}|" "$CONFIG_FILE"
        else
            sed -i "s|^base_url = .*|base_url = ${target_url}|" "$CONFIG_FILE"
        fi
        log_ok "配置文件已更新: base_url = ${target_url}"
    else
        log_warn "配置文件不存在: ${CONFIG_FILE}"
    fi

    export TEST_BASE_URL="$target_url"
}

# ---------- 部署并启动 API Server（CNB 环境） ----------
deploy_api_server() {
    log_step "部署 API Server 服务..."

    cd "$WORKSPACE_DIR"

    # Step 1: 安装系统依赖（如果 setup-env.sh 存在）
    if [ -f "$WORKSPACE_DIR/setup-env.sh" ]; then
        log_info "执行 setup-env.sh 安装系统依赖 (Java 21 / Maven / Node.js)..."
        bash "$WORKSPACE_DIR/setup-env.sh"
    else
        log_warn "未找到 setup-env.sh，跳过系统依赖安装"
    fi

    # Step 2: 构建项目
    if [ -f "$WORKSPACE_DIR/build-all-in-one.sh" ]; then
        log_info "执行 build-all-in-one.sh 构建项目..."
        bash "$WORKSPACE_DIR/build-all-in-one.sh"
    else
        log_warn "未找到 build-all-in-one.sh，跳过构建"
    fi

    # Step 3: 启动服务
    if [ -f "$WORKSPACE_DIR/run.sh" ]; then
        log_info "执行 run.sh 启动服务..."
        bash "$WORKSPACE_DIR/run.sh"
    else
        log_warn "未找到 run.sh，跳过启动"
    fi

    # 等待服务就绪
    log_info "等待服务就绪..."
    local max_wait=60
    local waited=0
    while [ $waited -lt $max_wait ]; do
        if curl -s -o /dev/null -w "%{http_code}" --max-time 3 "http://localhost:8080" 2>/dev/null | grep -q "200\|302\|401\|403"; then
            log_ok "服务已就绪 (耗时 ${waited}s)"
            break
        fi
        sleep 2
        waited=$((waited + 2))
    done

    if [ $waited -ge $max_wait ]; then
        log_warn "服务启动超时，请手动检查"
    fi

    cd "$SCRIPT_DIR"
}

# ---------- 一键部署并测试（CNB 环境） ----------
deploy_and_test() {
    log_step "CNB 云开发环境：一键部署并测试"

    # 部署服务
    deploy_api_server

    # 配置测试地址
    configure_test_url "$@"

    # 运行测试
    log_step "开始运行自动化测试..."
    cd "$SCRIPT_DIR"
    exec "$VENV_PYTHON" main.py "$@"
}

# ---------- 验证安装 ----------
verify_installation() {
    log_step "验证安装..."

    errors=0

    if "$VENV_PYTHON" --version &>/dev/null; then
        log_ok "Python: $("$VENV_PYTHON" --version 2>&1)"
    else
        log_error "Python 不可用"
        errors=$((errors + 1))
    fi

    if "$VENV_PIP" --version &>/dev/null; then
        log_ok "pip: $("$VENV_PIP" --version 2>&1)"
    else
        log_error "pip 不可用"
        errors=$((errors + 1))
    fi

    for pkg in requests jinja2 colorama; do
        if "$VENV_PYTHON" -c "import ${pkg//-/_}" 2>/dev/null; then
            version=$("$VENV_PYTHON" -c "import ${pkg//-/_}; print(getattr(${pkg//-/_}, '__version__', 'unknown'))" 2>/dev/null)
            log_ok "${pkg}: ${version}"
        else
            log_error "${pkg} 未安装"
            errors=$((errors + 1))
        fi
    done

    log_info "检查项目模块..."
    cd "$SCRIPT_DIR"
    if "$VENV_PYTHON" -c "
import sys
sys.path.insert(0, '.')
from core.config_loader import ConfigLoader
from core.http_client import HttpClient
from core.test_runner import TestRunner
from models.ai_model_manager import AIModelManager
print('All modules imported successfully')
" 2>&1; then
        log_ok "所有项目模块导入成功"
    else
        log_error "项目模块导入失败"
        errors=$((errors + 1))
    fi

    if [ $errors -eq 0 ]; then
        log_ok "环境验证全部通过!"
        return 0
    else
        log_error "环境验证发现 ${errors} 个问题"
        return 1
    fi
}

# ---------- 生成辅助脚本 ----------
generate_scripts() {
    # 激活脚本
    cat > "${SCRIPT_DIR}/activate.sh" << 'ACTIVATE_EOF'
#!/usr/bin/env bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
VENV_DIR="${SCRIPT_DIR}/.venv"
if [ ! -f "${VENV_DIR}/bin/activate" ]; then
    echo "错误: 未找到虚拟环境，请先运行 ./setup.sh"
    exit 1
fi
source "${VENV_DIR}/bin/activate"
echo "虚拟环境已激活"
echo "运行测试: python main.py --help"
ACTIVATE_EOF
    chmod +x "${SCRIPT_DIR}/activate.sh"

    # 运行脚本
    cat > "${SCRIPT_DIR}/run_test.sh" << 'RUN_EOF'
#!/usr/bin/env bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
VENV_PYTHON="${SCRIPT_DIR}/.venv/bin/python"
if [ ! -f "$VENV_PYTHON" ]; then
    echo "错误: 未找到虚拟环境，请先运行 ./setup.sh"
    exit 1
fi
cd "$SCRIPT_DIR"
exec "$VENV_PYTHON" main.py "$@"
RUN_EOF
    chmod +x "${SCRIPT_DIR}/run_test.sh"

    # CNB 一键部署+测试脚本
    cat > "${SCRIPT_DIR}/cnb_test.sh" << 'CNB_EOF'
#!/usr/bin/env bash
# CNB 云开发环境：一键部署服务并运行自动化测试
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"
exec bash setup.sh --deploy "$@"
CNB_EOF
    chmod +x "${SCRIPT_DIR}/cnb_test.sh"

    log_ok "辅助脚本已生成: activate.sh, run_test.sh, cnb_test.sh"
}

# ---------- 打印使用说明 ----------
print_usage() {
    echo ""
    echo "============================================"
    echo -e "  ${GREEN}环境安装完成!${NC}"
    echo "============================================"
    echo ""
    echo "使用方式:"
    echo ""
    echo "  --- 本地环境 ---"
    echo "  ./run_test.sh --list          # 列出测试模块"
    echo "  ./run_test.sh                 # 运行全部测试"
    echo "  ./run_test.sh --test rbac     # 运行指定测试"
    echo ""
    echo "  --- CNB 云开发环境 ---"
    echo "  ./cnb_test.sh                 # 一键部署+测试"
    echo "  ./setup.sh --deploy           # 部署服务并测试"
    echo "  ./setup.sh --url <URL>        # 指定测试地址"
    echo ""
    echo "  --- 手动使用 ---"
    echo "  source activate.sh            # 激活虚拟环境"
    echo "  python main.py --help         # 查看帮助"
    echo ""
    echo "配置文件: config/auto_test.config"
    echo "测试报告: reports/"
    echo ""
}

# ---------- 主流程 ----------
main() {
    local MODE="setup"
    local TARGET_URL=""

    # 解析参数
    while [ $# -gt 0 ]; do
        case "$1" in
            --deploy)
                MODE="deploy"
                shift
                ;;
            --url)
                TARGET_URL="$2"
                shift 2
                ;;
            --help|-h)
                echo "用法: ./setup.sh [选项]"
                echo ""
                echo "选项:"
                echo "  (无参数)      安装测试工具环境"
                echo "  --deploy      部署 API Server 并运行测试 (CNB 环境)"
                echo "  --url <URL>   指定测试目标地址"
                echo "  --help        显示帮助"
                exit 0
                ;;
            *)
                shift
                ;;
        esac
    done

    echo ""
    echo "============================================"
    echo "  API Server 自动化测试工具 - 环境安装"
    echo "============================================"

    detect_os
    detect_cnb_env

    if [ "$MODE" = "deploy" ]; then
        # CNB 部署模式：部署服务 + 配置地址 + 运行测试
        if ! check_python; then
            install_python
            check_python || exit 1
        fi
        if ! check_pip; then
            install_pip
            check_pip || exit 1
        fi
        setup_venv
        upgrade_pip
        install_dependencies
        verify_installation
        generate_scripts
        mkdir -p "${SCRIPT_DIR}/reports"

        # 部署并测试
        deploy_and_test "$@"
        exit 0
    fi

    # 标准安装模式
    # 1. 检测/安装 Python
    if ! check_python; then
        install_python
        check_python || exit 1
    fi

    # 2. 检测/安装 pip
    if ! check_pip; then
        install_pip
        check_pip || exit 1
    fi

    # 3. 创建虚拟环境
    setup_venv

    # 4. 升级 pip
    upgrade_pip

    # 5. 安装依赖
    install_dependencies

    # 6. 验证安装
    verify_installation

    # 7. 配置测试地址
    configure_test_url "$TARGET_URL"

    # 8. 生成辅助脚本
    generate_scripts

    # 9. 创建 reports 目录
    mkdir -p "${SCRIPT_DIR}/reports"

    # 10. 打印使用说明
    print_usage
}

main "$@"
