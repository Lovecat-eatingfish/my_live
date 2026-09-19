# 旗鱼直播平台 (Qiyu Live)

旗鱼直播平台，基于 Java 17 + Spring Boot 3.0.4 构建的分布式直播系统：**直播 + IM 实时通讯 + 短视频 + 礼物打赏 + 支付充值** 五条业务线，15 个后端服务 + 2 个前端。

## 技术栈

| 层 | 技术 |
|---|---|
| 基础框架 | Java 17 · Spring Boot 3.0.4 · Spring Cloud Gateway |
| 服务治理 | Dubbo 3.2（RPC）· Nacos 2.2（注册+配置中心） |
| 实时通讯 | Netty（自定义二进制协议，TCP/WebSocket 长连接集群） |
| 流媒体 | SRS 5（RTMP 推流 / WebRTC + HLS 拉流）· FFmpeg（转码/截帧） |
| 数据 | MySQL 8.0 + ShardingSphere（读写分离 + 100 分表）· Redis · MinIO |
| 异步 | RocketMQ 5.3（削峰/延迟消息/事件广播） |
| 前端 | Vue 3 + Vite（web_live C 端 / web_admin 管理端，Element Plus） |

## 模块结构

```text
qiyu-live-app/
├── qiyu-live-framework/           # 公共 starter：bootstrap(启动自检)/datasource(ShardingSphere+Nacos SPI)/redis/mq/web
├── qiyu-live-common-interface/    # 公共 DTO、MQ Topic、跨服务常量
│
│   —— 业务域（统一 interface / provider / api 三层拆分）——
├── qiyu-live-user-*               # 用户管理、经验等级、关注、通知中心
├── qiyu-live-account-*            # 登录 Token
├── qiyu-live-living-*             # 直播间、PK、连麦、分区、口令抽奖
├── qiyu-live-gift-*               # 礼物、红包雨、分账
├── qiyu-live-bank-*               # 支付订单、余额账户、对账（bank-api 承载支付回调）
├── qiyu-live-msg-*                # 短信、内容风控（DFA 敏感词）、弹幕消费
├── qiyu-live-stream-*             # SRS 对接：推拉流、hook 回调、录制回放、截帧巡查
├── qiyu-live-video-*              # 短视频、FFmpeg 异步转码
├── qiyu-live-im-*                 # IM 业务逻辑 / 连接路由 / Netty 长连接集群
├── qiyu-live-id-generate-*        # 分布式发号器（DB 号段模式）
│
│   —— 入口层 ——
├── qiyu-live-api                  # C 端聚合 API（13 个 Controller）
├── qiyu-live-admin-api            # 管理后台 API（11 个 Controller）
├── qiyu-live-gateway              # 鉴权网关（token 校验/封禁/身份透传）
├── qiyu-live-bank-api             # 支付回调独立端点
│
├── web_live/                      # 前端 C 端（直播间/短视频 Feed/搜索/钱包/私信）
├── web_admin/                     # 前端管理端（审核/风控/运营配置/仪表盘）
├── scripts/                       # 启停脚本(start-all.*) + 20+ E2E 冒烟测试(*_test.mjs)
├── docker/ + docker-compose*.yml  # 中间件与服务编排（本机开发/全容器化两套）
└── docs/                          # 项目文档（见下）
```

## 文档导航

| 分类 | 内容 |
|---|---|
| [docs/architecture/项目技术详解.md](docs/architecture/项目技术详解.md) | **全功能技术详解（30 章）**：每个功能域的链路、实现、缓存 Key、设计取舍 |
| [docs/architecture/架构流程图.md](docs/architecture/架构流程图.md) | 全功能一图流 + 12 张流程图（[浏览器渲染版](docs/architecture/架构流程图.html)） |
| [docs/ops/部署文档.md](docs/ops/部署文档.md) | 三种部署形态：本机开发 / 一键脚本 / 全容器化 |
| [docs/ops/启动手册.md](docs/ops/启动手册.md) | 启动依赖原理、波次顺序、验证与排错 |
| [docs/ops/端口总表.md](docs/ops/端口总表.md) · [docs/ops/踩坑记录.md](docs/ops/踩坑记录.md) | 端口总表 · 28 条踩坑记录 |
| [docs/learning/](docs/learning/) | 技术学习笔记：Docker/Compose/K8s · Dubbo · Linux |
| [docs/planning/](docs/planning/) | 需求总账 · 产品路线图 · P2/P3 设计评审稿 |

## 构建

```bash
mvn clean install -DskipTests          # 全量（-T 1C 并行加速）
mvn -pl <模块> -am clean package -DskipTests   # 单模块增量
```

## 快速开始（本机开发）

```powershell
docker compose up -d                    # ① 中间件（MySQL/Redis/Nacos/RocketMQ/MinIO）
scripts\start-all.bat                   # ② 中间件健康检查 + 15 个服务按波次启动
srs.exe -c docker\srs-native.conf       # ③ SRS 原生启动（Windows 下容器 UDP 转发丢包，见部署文档）
cd web_live && npm run dev              # ④ 前端 :3000（管理端 web_admin :3005）
node scripts/e2e/e2e_test.mjs               # ⑤ E2E 冒烟验证
```

详见 [docs/ops/部署文档.md](docs/ops/部署文档.md) 与 [docs/ops/启动手册.md](docs/ops/启动手册.md)。

## 本地开发要点

- Nacos 命名空间 `qiyu-live-test`，全部业务配置托管在 Nacos（本地副本 `nacos-config/`），启动时 `optional:nacos:qiyu-live-*.yaml` 导入
- Provider 类服务 `WebApplicationType.NONE`（无 HTTP 容器），且带**启动自检**：上游依赖未就绪会打印清单并退出——必须按波次启动
- IM Core Server 独立 Netty 端口（非 HTTP），启动前需注入 `DUBBO_IP_TO_REGISTRY`/`DUBBO_PORT_TO_REGISTRY`
- Dubbo 端口规则：30010 起步长 5，见 [docs/ops/端口总表.md](docs/ops/端口总表.md)
