#!/usr/bin/env bash
set -euo pipefail

APP_NAME="knowledge-planet-native"
IMAGE_NAME="knowledge-planet:native"
PROJECT_DIR="${1:-$PWD}"
NATIVE_IMAGE_XMX="${NATIVE_IMAGE_XMX:-5g}"
BUILD_MEMORY="${BUILD_MEMORY:-}"

DB_HOST="${DB_HOST:-192.168.150.101}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-knowledgeplanet}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-123456}"

MQ_HOST="${MQ_HOST:-192.168.150.101}"
MQ_PORT="${MQ_PORT:-5672}"
MQ_USER="${MQ_USER:-root}"
MQ_PASS="${MQ_PASS:-123456}"

REDIS_HOST="${REDIS_HOST:-192.168.150.101}"
REDIS_PORT="${REDIS_PORT:-6379}"

echo "[1/5] Checking docker..."
docker version >/dev/null

echo "[2/5] Building native image container..."
cd "$PROJECT_DIR"
build_cmd=(docker build -f Dockerfile.native -t "$IMAGE_NAME" --build-arg "NATIVE_IMAGE_XMX=${NATIVE_IMAGE_XMX}")
if [[ -n "$BUILD_MEMORY" ]]; then
  build_cmd+=(--memory "$BUILD_MEMORY")
fi
build_cmd+=(.)
"${build_cmd[@]}"

echo "[3/5] Recreating container..."
if docker ps -a --format '{{.Names}}' | grep -q "^${APP_NAME}$"; then
  docker rm -f "$APP_NAME" >/dev/null
fi

echo "[4/5] Starting container..."
docker run -d \
  --name "$APP_NAME" \
  --restart unless-stopped \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}" \
  -e SPRING_DATASOURCE_USERNAME="${DB_USER}" \
  -e SPRING_DATASOURCE_PASSWORD="${DB_PASS}" \
  -e SPRING_RABBITMQ_HOST="${MQ_HOST}" \
  -e SPRING_RABBITMQ_PORT="${MQ_PORT}" \
  -e SPRING_RABBITMQ_USERNAME="${MQ_USER}" \
  -e SPRING_RABBITMQ_PASSWORD="${MQ_PASS}" \
  -e SPRING_DATA_REDIS_HOST="${REDIS_HOST}" \
  -e SPRING_DATA_REDIS_PORT="${REDIS_PORT}" \
  "$IMAGE_NAME" >/dev/null

echo "[5/5] Deployment done. Recent logs:"
docker logs --tail 80 "$APP_NAME"
