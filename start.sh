#!/bin/bash

set -e

PROJECT_DIR=$(cd "$(dirname "$0")" && pwd)

echo "=== 船舶码头船员休息室配套电器房间占用关联登记系统 ==="
echo ""

if [ "$1" = "dev" ]; then
    echo "[INFO] 启动开发环境..."
    docker compose -f docker-compose.dev.yml up -d --build
else
    echo "[INFO] 启动生产环境..."
    docker compose up -d --build
fi

echo ""
echo "[INFO] 服务启动中，请稍候..."
sleep 5

echo ""
echo "[INFO] 服务状态:"
docker compose ps

echo ""
echo "[INFO] 访问地址:"
echo "  前端: http://localhost:8122"
echo "  后端API: http://localhost:8132/api"
echo "  MySQL: localhost:3348"
echo "  Redis: localhost:6420"