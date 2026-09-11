# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

旗鱼直播平台 (Qiyu Live Streaming Platform) — a Java 17/Spring Boot 3.0.4 backend for a live streaming platform with IM, gifts, payments, and e-commerce.

## Build Commands

```bash
# Full build (skip tests)
mvn clean install -DskipTests

# Build single module with dependencies
mvn clean package -pl <module-name> -am -DskipTests

# Run tests
mvn test

# Run single test class
mvn test -Dtest=ClassName

# Run single test method
mvn test -Dtest=ClassName#methodName
```

## Architecture

### Module Pattern — Interface/Provider Split

Every business domain follows a consistent 3-layer pattern:

| Module Type | Purpose | Key Annotations |
|---|---|---|
| `*-interface` | Defines RPC contracts: interfaces, DTOs, enums, constants | None (pure POJOs) |
| `*-provider` | Implements business logic, exposes Dubbo services | `@DubboService`, runs as `WebApplicationType.NONE` |
| `*-api` | Web controller layer, calls providers via Dubbo | `@DubboReference` to inject remote services |

**Example flow** — Account domain:
1. `qiyu-live-account-interface` defines `IAccountTokenRPC`
2. `qiyu-live-account-provider` implements it with `@DubboService` on `AccountTokenRPCImpl`
3. `qiyu-live-api` injects it with `@DubboReference` in service implementations

### Module List

**Deployable services (14):**
- `qiyu-live-account-provider` — User authentication tokens
- `qiyu-live-user-provider` — User management
- `qiyu-live-api` — Main web API gateway (calls all providers)
- `qiyu-live-gateway` — API Gateway with filters (鉴权, 白名单)
- `qiyu-live-im-core-server` — Netty-based IM server (non-HTTP, runs Netty port)
- `qiyu-live-im-provider` — IM business logic
- `qiyu-live-im-router-provider` — IM user routing/connection management
- `qiyu-live-msg-provider` — Message handling
- `qiyu-live-living-provider` — Live streaming room management
- `qiyu-live-gift-provider` — Gift system
- `qiyu-live-bank-provider` — Payment/order processing
- `qiyu-live-bank-api` — Payment callback endpoints
- `qiyu-live-id-generate-provider` — Distributed ID generation

**Shared libraries:**
- `qiyu-live-framework/*` — Spring Boot starters: `datasource-starter`, `redis-starter`, `mq-starter`, `web-starter`
- `qiyu-live-common-interface` — Common utilities, DTOs, topic constants

### Infrastructure

- **Nacos** (`nacos-config/`) — All provider configs checked in; services import `- optional:nacos:qiyu-live-*.yaml` at bootstrap
- **ShardingJDBC** — Database sharding configured via Nacos SPI extension
- **Redis** — Caching, distributed locks
- **RocketMQ** — Async messaging; topic constants in `qiyu-live-common-interface`
- **Netty** — IM core server handles WebSocket/TCP connections on a dedicated port

### Package Naming

All code lives under `org.qiyu.live.<domain>`:
- `<domain>.interfaces` — RPC interfaces
- `<domain>.provider.rpc` — `@DubboService` implementations
- `<domain>.provider.service` — Internal business logic interfaces/impls
- `<domain>.provider.dao` — MyBatis mappers and PO entities
- `<domain>.api.controller` — Web controllers (in `*-api` modules only)

### Response Wrappers

`org.qiyu.live.common.interfaces.vo.WebResponseVO` — standard API response wrapper.

## Local Development Notes

- All provider services use `WebApplicationType.NONE` (no embedded servlet container)
- IM core server starts a Netty server on a separate port (not HTTP)
- Nacos connection: `qiyu.nacos.com:8848`, namespace `qiyu-live-test`; credentials via environment variables `NACOS_USER`/`NACOS_PWD`
- Gateway uses Spring Cloud Gateway with `spring-cloud-starter-gateway`
