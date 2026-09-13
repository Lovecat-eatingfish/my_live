#!/usr/bin/env bash
# 一键构建全部 Java 服务 Docker 镜像
# 前置：先 ./mvnw 或 mvn package 完成构建（各模块 target 下有可执行 jar）
# 用法：bash scripts/build-images.sh [tag]  (默认 dev)
# 注意 Windows 上 Git Bash 需要能访问 docker 命令（Docker Desktop 运行中）
set -e
TAG="${1:-dev}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$ROOT/docker/build"
mkdir -p "$TMP"

# 模块名 -> jar 文件名（target 下；所有模块 finalName 统一为 ${artifactId}，jar 名即 模块名.jar）
MODULES=(
  "qiyu-live-account-provider:qiyu-live-account-provider.jar"
  "qiyu-live-bank-provider:qiyu-live-bank-provider.jar"
  "qiyu-live-gift-provider:qiyu-live-gift-provider.jar"
  "qiyu-live-id-generate-provider:qiyu-live-id-generate-provider.jar"
  "qiyu-live-im-provider:qiyu-live-im-provider.jar"
  "qiyu-live-im-router-provider:qiyu-live-im-router-provider.jar"
  "qiyu-live-im-core-server:qiyu-live-im-core-server.jar"
  "qiyu-live-living-provider:qiyu-live-living-provider.jar"
  "qiyu-live-msg-provider:qiyu-live-msg-provider.jar"
  "qiyu-live-stream-provider:qiyu-live-stream-provider.jar"
  "qiyu-live-user-provider:qiyu-live-user-provider.jar"
  "qiyu-live-video-provider:qiyu-live-video-provider.jar"
  "qiyu-live-gateway:qiyu-live-gateway.jar"
  "qiyu-live-api:qiyu-live-api.jar"
  "qiyu-live-bank-api:qiyu-live-bank-api.jar"
)

echo "==> 构建镜像 tag=qiyu-live/*:$TAG"
for entry in "${MODULES[@]}"; do
  mod="${entry%%:*}"
  jar="${entry##*:}"
  jarPath="$ROOT/$mod/target/$jar"
  if [[ ! -f "$jarPath" ]]; then
    echo "  ✗ $mod 缺少 $jarPath，请先 mvn package -DskipTests"
    exit 1
  fi
  cp "$jarPath" "$TMP/app.jar"
  echo "  ▶ $mod"
  docker build -q -f "$ROOT/docker/app.Dockerfile" -t "qiyu-live/$mod:$TAG" "$TMP" >/dev/null
  echo "    ✓ qiyu-live/$mod:$TAG"
done
rm -rf "$TMP"
echo "==> 全部镜像构建完成"
