# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

旗鱼直播平台 (Qiyu Live Streaming Platform) — a Java 17/Spring Boot 3.0.4 distributed live-streaming platform with IM, gifts, payments, video clips, and e-commerce. 15 deployable backend services + 2 Vue 3 frontends.

## Build Commands

```bash
# Full build (skip tests, -T 1C enables parallel compilation)
mvn clean install -DskipTests -T 1C

# Build single module with dependencies (-am is mandatory: brings upstream interface/starter modules)
mvn -pl <module-name> -am clean package -DskipTests

# Run tests
mvn test

# E2E smoke tests (Node native scripts, no framework; exit code 0 = pass)
node scripts/e2e/e2e_test.mjs            # main flow
node scripts/e2e/lottery_test.mjs        # per-feature scripts: pay_ticket / dm / pk_share / ...
```

**Stale-jar trap**: scripts only auto-build when jar is missing. After changing code, always re-package; compare `ls -l <module>/target/*.jar` mtime vs last source commit.

**scripts/ layout (re-org 2026-09-19)**: `e2e/` = E2E smoke tests (`*_test.mjs`), `ops/` = start/stop/reload implementations (ps1/sh), root-level `*.bat` entries + `*.ps1`/`start-all.sh` shims delegate into `ops/` so legacy `scripts\start-all.ps1` style references keep working.

## Architecture

### Module Pattern — Interface/Provider Split

Every business domain follows a consistent 3-layer pattern:

| Module Type | Purpose | Key Annotations |
|---|---|---|
| `*-interface` | RPC contracts: interfaces, DTOs, enums, constants (pure POJO, no deps) | None |
| `*-provider` | Business logic, exposes Dubbo services | `@DubboService`, runs as `WebApplicationType.NONE` |
| `*-api` | Web controller layer, calls providers via Dubbo | `@DubboReference(check=false)` |

Providers carry a **startup verifier** (`qiyu-live-framework-bootstrap-starter` → `QiyuProviderStartupVerifier`): it force-checks every `@DubboReference` at boot and `exit(1)` with a failure list if upstream providers are down — hence **wave-ordered startup** is mandatory (see docs/ops/启动手册.md).

### Deployable services (16)

- `qiyu-live-gateway` — Spring Cloud Gateway: token auth (cookie `qytk`), ban check, userId header passthrough
- `qiyu-live-api` — main C-end API (13 controllers, HTTP 38085)
- `qiyu-live-admin-api` — admin API (11 controllers)
- `qiyu-live-bank-api` — payment callback endpoint (isolated deployment)
- Providers: account / user / living (rooms, PK, link-mic, lottery) / gift / bank (orders, balance, reconciliation) / msg (SMS, DFA-based content risk, danmu consumer) / stream (SRS hooks, playback, snapshot patrol) / video (FFmpeg async transcode) / im / im-router / im-core-server (Netty TCP+WS) / id-generate (DB segment mode)

Notable cross-cutting designs: custom Dubbo Cluster SPI (`ImRouterCluster`) for pin-point IM message delivery via Redis bind-ip; `PkConstants`/`TicketConstants` fixed Redis key prefixes to avoid the RedisKeyBuilder cross-service prefix trap (see docs/ops/踩坑记录.md §22).

### Shared libraries

- `qiyu-live-framework/*` — starters: `bootstrap` (startup verify), `datasource` (ShardingSphere config loaded from Nacos via custom `ShardingSphereDriverURLProvider` SPI), `redis`, `mq`, `web` (userId ThreadLocal interceptor, `@RequestLimit`, global exception handler)
- `qiyu-live-common-interface` — DTOs, `WebResponseVO`, MQ topic constants, cross-service Redis key constants. Cross-domain RPC contracts (e.g. `IDmRpc`, `IRiskRpc`) and shared DTOs intentionally live here when 2+ modules reference them (decided 2026-09-19: do NOT migrate them to per-domain `*-interface` modules)

### Infrastructure

- **Nacos** (`nacos-config/` local copies) — all provider configs; services import `optional:nacos:qiyu-live-*.yaml`; namespace `qiyu-live-test` (id `ef63e53e-94c8-4b1c-865e-6824177b2893`)
- **ShardingJDBC** — user DB: read-write splitting + `t_user/t_user_phone/t_user_tag` sharded 100 tables by `user_id % 100`
- **Redis** — cache, distributed locks, ZSET rankings, Lua (PK bar), heartbeat routing (bind-ip TTL)
- **RocketMQ** — async: gift, exp, transcode, danmu; delayed messages (PK settle 10min, lottery)
- **Netty** — custom binary protocol (magic+code+len+body), TCP 8085 / WS 8086; im-core-server needs `DUBBO_IP_TO_REGISTRY`/`DUBBO_PORT_TO_REGISTRY` env vars
- **SRS 5** — RTMP push / WebRTC+HLS play; HTTP hooks (on_publish/on_unpublish/on_dvr) → stream-provider :38090 with `X-Srs-Secret`; Windows local uses native srs.exe (`docker/srs-native.conf`), container profile `linux-full`
- Ports: Dubbo 30010+5k (see docs/ops/端口总表.md); HTTP: gateway 38080, api 38085, bank-api 38095, im-core 38110/38115, stream 38101 (SRS callbacks)

### Package Naming

All code under `org.qiyu.live.<domain>`:
- `<domain>.interfaces` — RPC interfaces
- `<domain>.provider.rpc` — `@DubboService` implementations
- `<domain>.provider.service` — internal business services
- `<domain>.provider.dao` — MyBatis mappers and POs
- `<domain>.api.controller` — web controllers (in `*-api` modules only)

### Response Wrappers

`org.qiyu.live.common.interfaces.vo.WebResponseVO` — standard API response wrapper.

## Local Development Notes

- Start: `docker compose up -d` (middleware) then `scripts/start-all.bat` (wave-ordered) — full manual in docs/ops/部署文档.md / 启动手册.md
- All provider services use `WebApplicationType.NONE` (no embedded servlet container)
- Frontends: `web_live` (dev :3000), `web_admin` (dev :3005)

## Documentation Map (docs/, filenames are Chinese)

- `architecture/` — 项目技术详解.md (30-chapter feature deep-dive), 架构流程图.md + .html (mermaid diagrams), 流媒体架构.md, 数据库设计.md
- `ops/` — 部署文档.md (3 deployment modes), 启动手册.md (startup waves + troubleshooting), 端口总表.md, 踩坑记录.md (28 pitfall records)
- `planning/` — 需求总账.md, 产品路线图.md, 路线图执行计划.md, 复查清单.md, P2P3功能设计.md
- `learning/` — 容器技术学习笔记 (Docker/Compose/K8s), Dubbo学习笔记, Linux学习笔记
