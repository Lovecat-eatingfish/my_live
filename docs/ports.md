# 旗鱼直播平台 —— 端口规划

> 所有服务均跑在本地。Dubbo 端口统一使用 **30001–30011**（30000 段，互不冲突）。
> 启动服务前请先确保「基础设施端口」对应的中间件已启动。

---

## 一、基础设施端口（中间件，需先启动）

| 中间件 | 端口 | 配置位置 / 说明 |
|--------|------|-----------------|
| Nacos | 8848 (HTTP) | `bootstrap.yaml` `spring.cloud.nacos.discovery.server-addr` |
| Nacos (gRPC, Nacos2.x) | 9848 | Nacos 2.x 客户端额外需要，一般随 Nacos 自动开放 |
| MySQL | 3306 | 各 `application.yml` `spring.datasource.url`，root/123456 |
| Redis | 6379 | 各 `application.yml` `spring.data.redis` |
| RocketMQ NameServer | 9876 | 各 `application.yml` `qiyu.rmq.*.nameSrv` |
| RocketMQ Broker | 10911 | MQ 默认，随 RocketMQ 启动 |

> Nacos 命名空间：`ef63e53e-94c8-4b1c-865e-6824177b2893`（需在 Nacos 控制台预先创建，**无需导入配置**，本地不走配置中心）。

---

## 二、Dubbo 协议端口（30000 段）

按启动波次顺序分配，消费端（api / bank-api / gateway）不开放 Dubbo 端口。

| 端口 | 模块 | 暴露的主要 RPC | 启动波次 |
|------|------|---------------|----------|
| 30001 | qiyu-live-bank-provider | IQiyuCurrencyAccountRpc, IPayProductRpc, IPayOrderRpc | Wave0 |
| 30002 | qiyu-live-account-provider | IAccountTokenRPC | Wave0 |
| 30003 | qiyu-live-id-generate-provider | IdGenerateRpc | Wave0 |
| 30004 | qiyu-live-im-provider | ImTokenRpc, ImOnlineRpc | Wave0 |
| 30005 | qiyu-live-stream-provider | ILivingStreamRpc, ILivingPlayBackRpc | Wave0 |
| 30006 | qiyu-live-user-provider | IUserRpc, IUserTagRpc, IUserPhoneRPC | Wave1 |
| 30007 | qiyu-live-im-core-server | IRouterHandlerRpc | Wave1 |
| 30008 | qiyu-live-im-router-provider | ImRouterRpc | Wave2 |
| 30009 | qiyu-live-living-provider | ILivingRoomRpc | Wave3 |
| 30010 | qiyu-live-msg-provider | ISmsRpc | Wave4 |
| 30011 | qiyu-live-gift-provider | IGiftConfigRpc, IRedPacketRpc, ISkuOrderRpc, ISkuRpc, ... | Wave4 |

> Dubbo 消费端（`@DubboReference`）从 Nacos 注册中心动态发现 provider，不占固定端口。

---

## 三、HTTP / Web 入口端口

| 端口 | 模块 | context-path | 说明 |
|------|------|--------------|------|
| 38080 | qiyu-live-gateway | — | API 网关，前端统一入口 |
| 38100 | qiyu-live-api | `/live/api` | 主业务 API（网关路由 `lb://qiyu-live-api`） |
| 38201 | qiyu-live-bank-api | `/live/bank` | 支付回调入口 |

---

## 四、IM 长连接端口（Netty，im-core-server）

| 端口 | 协议 | 配置 |
|------|------|------|
| 38085 | TCP | `qiyu.im.tcp.port` |
| 38086 | WebSocket | `qiyu.im.ws.port` |

> im-core-server 为 `WebApplicationType.NONE`，不启动 Tomcat，仅起 Netty。

---

## 五、流媒体相关端口（stream-provider 连接的外部服务，可选）

> 仅直播/录像功能用到，本地不跑直播可忽略。

| 端口 | 服务 | 配置 |
|------|------|------|
| 31935 | SRS RTMP | `qiyu.srs.rtmp-port` |
| 31985 | SRS HTTP API | `qiyu.srs.api-port` |
| 30080 | SRS HLS | `qiyu.srs.hls-port` |
| 39000 | MinIO | `qiyu.minio.endpoint` |

---

## 六、Dubbo 依赖分层与启动顺序

```
Wave0 基础层(无Dubbo依赖): bank-provider(30001) account-provider(30002) id-generate-provider(30003) im-provider(30004) stream-provider(30005)
Wave1:                    user-provider(30006) im-core-server(30007) gateway(38080) bank-api(38201)
Wave2:                    im-router-provider(30008)
Wave3:                    living-provider(30009)
Wave4:                    msg-provider(30010) gift-provider(30011)
Wave5 入口:               api(38100)
```

一键启动（自动按上述顺序），详见 [startup-guide.md](startup-guide.md)：
```bat
:: Windows
scripts\start-all.bat            :: 构建+启动
scripts\start-all.bat status      :: 查看状态
scripts\start-all.bat stop        :: 停止
```
```bash
# Git Bash / Linux
bash scripts/start-all.sh          # 构建+启动
bash scripts/start-all.sh status   # 查看状态
bash scripts/start-all.sh stop     # 停止
```

---

## 七、端口占用速查（本机需空闲的端口）

```
基础设施: 8848  9848  3306  6379  9876  10911
Dubbo:    30001 ~ 30011
HTTP:     38080  38100  38201
IM Netty: 38085  38086
流媒体:   31935  31985  30080  39000  (可选)
```
