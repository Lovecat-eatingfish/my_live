#!/usr/bin/env bash
###############################################################################
# 旗鱼直播平台 —— 一键启动/停止脚本 (Windows Git Bash / Linux 通用)
#
# 用法:
#   bash scripts/start-all.sh            # 检查并按依赖顺序启动全部服务
#   bash scripts/start-all.sh build      # 仅构建 (mvn clean install -DskipTests)
#   bash scripts/start-all.sh start      # 仅启动
#   bash scripts/start-all.sh stop       # 停止全部
#   bash scripts/start-all.sh restart   # 重启
#   bash scripts/start-all.sh status    # 查看运行状态
#   bash scripts/start-all.sh logs <模块名>  # 查看某模块日志 (如 logs qiyu-live-api)
#
# 可选环境变量:
#   JAVA_OPTS="..."        JVM 参数, 默认 -Xms128m -Xmx384m
#   NACOS_ADDR=127.0.0.1:8848   Nacos 地址(启动前做存活探测)
#   STARTUP_WAIT=10        每个启动波次之间的等待秒数
###############################################################################
set -uo pipefail   # 注意: 不用 -e, 单个服务启动失败不应中断整体

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_DIR="$PROJECT_ROOT/logs"
mkdir -p "$LOG_DIR"
cd "$PROJECT_ROOT"

# file.encoding 必须显式 UTF-8：JDK17 在中文 Windows 默认 GBK，会把 MQ 消息体里
# 的 UTF-8 字节按 GBK 解码成乱码，且 GBK 双字节会吞掉 JSON 转义符导致弹幕解析失败全丢
JAVA_OPTS="${JAVA_OPTS:--Xms128m -Xmx384m -Dfile.encoding=UTF-8}"
NACOS_ADDR="${NACOS_ADDR:-127.0.0.1:8848}"
STARTUP_WAIT="${STARTUP_WAIT:-10}"

# 按依赖分层(底层在前)。如不需要某服务, 注释掉对应行即可。
WAVE0=(qiyu-live-bank-provider qiyu-live-account-provider qiyu-live-id-generate-provider qiyu-live-im-provider)
WAVE1=(qiyu-live-user-provider qiyu-live-im-core-server qiyu-live-gateway qiyu-live-bank-api)
WAVE2=(qiyu-live-im-router-provider)
WAVE3=(qiyu-live-living-provider)
WAVE4=(qiyu-live-msg-provider qiyu-live-gift-provider)
WAVE4B=(qiyu-live-video-provider)
WAVE4C=(qiyu-live-stream-provider)
WAVE5=(qiyu-live-api qiyu-live-admin-api)
ALL_MODULES=("${WAVE0[@]}" "${WAVE1[@]}" "${WAVE2[@]}" "${WAVE3[@]}" "${WAVE4[@]}" "${WAVE4B[@]}" "${WAVE4C[@]}" "${WAVE5[@]}")

# ---------- 工具函数 ----------
# 本项目固定用本机 JDK17（写死，不读 JAVA_HOME——本机 JAVA_HOME 保持 jdk8 给公司项目用）
# 如需换路径，改这里或临时 export QIYU_JAVA_HOME=...
QIYU_JDK17="/d/enviroment/javaenviroment/jdk17"
if [[ -n "${QIYU_JAVA_HOME:-}" && -x "$QIYU_JAVA_HOME/bin/java" ]]; then
  JAVA_CMD="$QIYU_JAVA_HOME/bin/java"
elif [[ -x "$QIYU_JDK17/bin/java" ]]; then
  JAVA_CMD="$QIYU_JDK17/bin/java"
else
  echo "❌ 未找到 JDK17: $QIYU_JDK17（Spring Boot 3 必须 JDK17）"
  exit 1
fi
# mvn 构建同样用 JDK17（不污染当前 shell，仅对本脚本生效）
export JAVA_HOME="$QIYU_JDK17"
export PATH="$JAVA_HOME/bin:$PATH"
find_jar() {  # $1=模块目录 -> 输出可执行 jar 路径(可能为空)
  # target 里可能同时存在新旧两个 fatjar（finalName 变更的历史残留），按修改时间取最新
  ls -t "$1"/target/*.jar 2>/dev/null | grep -vE '(-sources\.jar|-javadoc\.jar|\.jar\.original)$' | head -1 || true
}

is_running() {  # $1=模块名 -> 0=运行中
  local pidfile="$LOG_DIR/$1.pid"
  [[ -f "$pidfile" ]] && kill -0 "$(cat "$pidfile")" 2>/dev/null
}

check_nacos() {
  if command -v curl >/dev/null 2>&1; then
    if ! curl -s -m 3 "http://$NACOS_ADDR/nacos/" >/dev/null 2>&1; then
      echo "⚠️  探测到 Nacos($NACOS_ADDR) 未就绪。请先启动: Nacos / MySQL(3306) / Redis(6379) / RocketMQ(9876)。"
      read -rp "是否仍要继续启动服务? [y/N] " ans
      [[ "${ans:-N}" =~ ^[Yy]$ ]] || exit 1
    fi
  fi
}

build_all() {
  echo "🔨 构建全部模块 (mvn clean install -DskipTests) ..."
  mvn clean install -DskipTests || { echo "❌ 构建失败"; exit 1; }
  echo "✅ 构建完成"
}

start_module() {  # $1=模块目录名
  local mod="$1" jar
  jar="$(find_jar "$mod")"
  if [[ -z "$jar" ]]; then
    echo "  ✗ $mod: 未找到 jar (target/*.jar), 请先构建: bash scripts/start-all.sh build"
    return 1
  fi
  if is_running "$mod"; then
    echo "  • $mod: 已运行(pid $(cat "$LOG_DIR/$mod.pid")), 跳过"
    return 0
  fi
  echo "  ▶ $mod ..."
  "$JAVA_CMD" $JAVA_OPTS -jar "$jar" >"$LOG_DIR/$mod.log" 2>&1 &
  local pid=$!
  disown "$pid" 2>/dev/null || true
  echo "$pid" >"$LOG_DIR/$mod.pid"
}

start_wave() {  # $1=描述, 其余=模块名
  local desc="$1"; shift
  echo "── $desc ──"
  for mod in "$@"; do start_module "$mod" || true; done
  sleep "$STARTUP_WAIT"
}

start_all() {
  check_nacos
  # 任一模块缺 jar 则先整体构建
  local need=0
  for mod in "${ALL_MODULES[@]}"; do
    [[ -z "$(find_jar "$mod")" ]] && { need=1; break; }
  done
  [[ $need -eq 1 ]] && build_all

  echo "🚀 按依赖顺序启动 ${#ALL_MODULES[@]} 个服务 (每波间隔 ${STARTUP_WAIT}s) ..."
  start_wave "Wave0 基础层(无Dubbo依赖)" "${WAVE0[@]}"
  start_wave "Wave1 依赖基础层"        "${WAVE1[@]}"
  start_wave "Wave2 IM路由"            "${WAVE2[@]}"
  start_wave "Wave3 直播间"            "${WAVE3[@]}"
  start_wave "Wave4 消息/礼物"         "${WAVE4[@]}"
  start_wave "Wave4b 视频"             "${WAVE4B[@]}"
  start_wave "Wave4c 流媒体"           "${WAVE4C[@]}"
  start_wave "Wave5 主API入口"         "${WAVE5[@]}"
  echo "✅ 全部启动指令已发出。"
  echo "   查看状态: bash scripts/start-all.sh status"
  echo "   查看日志: bash scripts/start-all.sh logs <模块名>   (如 qiyu-live-api)"
  echo "   停止:    bash scripts/start-all.sh stop"
}

stop_all() {
  echo "🛑 停止全部服务 ..."
  for mod in "${ALL_MODULES[@]}"; do
    local pidfile="$LOG_DIR/$mod.pid"
    if [[ -f "$pidfile" ]]; then
      local pid; pid="$(cat "$pidfile")"
      if kill -0 "$pid" 2>/dev/null; then
        kill "$pid" && echo "  ✓ $mod (pid $pid) 已停止"
      else
        echo "  · $mod: 进程已不在(pid $pid)"
      fi
      rm -f "$pidfile"
    fi
  done
}

status_all() {
  echo "运行状态:"
  for mod in "${ALL_MODULES[@]}"; do
    if is_running "$mod"; then
      printf "  ● %-32s 运行中 (pid %s)\n" "$mod" "$(cat "$LOG_DIR/$mod.pid")"
    else
      printf "  ○ %-32s 未运行\n" "$mod"
    fi
  done
}

tail_log() {  # $1=模块名
  local mod="${1:-}"
  [[ -z "$mod" ]] && { echo "用法: $0 logs <模块名>"; exit 1; }
  local f="$LOG_DIR/$mod.log"
  [[ -f "$f" ]] || { echo "日志不存在: $f"; exit 1; }
  echo "=== $mod 日志 (Ctrl+C 退出) ==="
  tail -n 100 -f "$f"
}

# ---------- 入口 ----------
case "${1:-start}" in
  build)   build_all ;;
  start)   start_all ;;
  stop)    stop_all ;;
  restart) stop_all; sleep 2; start_all ;;
  status)  status_all ;;
  logs)    shift; tail_log "$@" ;;
  *) echo "用法: bash $0 [build|start|stop|restart|status|logs <模块名>]"; exit 1 ;;
esac
