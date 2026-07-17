#!/bin/bash
# ========================================
# API Server Docker 构建 & 推送脚本
# ========================================
set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'
print_ok()  { echo -e "${GREEN}[OK]${NC} $1"; }
print_err() { echo -e "${RED}[ERR]${NC} $1"; }
print_info(){ echo -e "${YELLOW}[>>]${NC} $1"; }

IMAGE="docker.cnb.cool/xindu.site/api-server"
TAG="${1:-latest}"
FULL_IMAGE="${IMAGE}:${TAG}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# 检查 Docker
if ! command -v docker &>/dev/null; then
    print_err "Docker 未安装"
    exit 1
fi

print_info "镜像: ${FULL_IMAGE}"
print_info "构建上下文: ${SCRIPT_DIR}"

# 创建 .env（如果不存在）
if [ ! -f docker/.env ]; then
    cp docker/.env.example docker/.env
    print_ok ".env 已创建"
fi

# 创建数据目录
mkdir -p docker/data docker/logs

# 构建
print_info "开始构建 Docker 镜像..."
docker build \
    -t "${FULL_IMAGE}" \
    -t "${IMAGE}:latest" \
    -f docker/Dockerfile \
    .

print_ok "构建完成: ${FULL_IMAGE}"

# 推送
print_info "推送镜像到 CNB 仓库..."
docker push "${FULL_IMAGE}"
docker push "${IMAGE}:latest"

print_ok "推送完成"

echo ""
echo "========================================"
echo "  ${FULL_IMAGE}"
echo "========================================"
echo "  本地运行:"
echo "    cd docker && docker compose up -d"
echo ""
echo "  查看日志:"
echo "    cd docker && docker compose logs -f"
echo ""
echo "  停止服务:"
echo "    cd docker && docker compose down"
echo "========================================"
