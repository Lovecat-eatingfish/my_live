# 旗鱼直播平台 —— 全项目端口总表

> 更新日期：2026-09-13（第二轮编排：全部自研服务端口按**步长 5** 重新编号，有规律可循、预留扩展空隙）
> 配套文档：启动顺序详见 [startup-order.md](startup-order.md)，启动操作详见 [startup-guide.md](startup-guide.md)。

## 〇、一图速查

```
前端 dev        3000(web_live)  3005(web_admin)   (Vite; web_live 代理 /api→38080 /rtc→1985 /minio→9000)
前端 prod        80   (nginx 容器, docker-compose-full.yml)
网关            38080 (仅路由 /live/api/** → 38085)
HTTP 服务    38085(api) 38090(SRS回调) 38095(支付回调) 38100(管理后台)
IM Netty     38110(TCP)  38115(WebSocket)
Dubbo        30010 起、步长 5：bank→30010 account→30015 idgen→30020 im→30025 user→30030
             im-core→30035 im-router→30040 living→30045 msg→30050 gift→30055 video→30060 stream→30065
中间件       8848/9848(Nacos) 3306(MySQL) 6379(Redis) 9876/10911/10909(RocketMQ)
             9000/39000(MinIO) 1935/1985/8080/8000(SRS)   ← 标准端口不动
```

**编号规则**：自研端口全部按**启动波次顺序、步长 5** 分配（30010、30015、30020…）。
步长 5 的好处：端口段有记忆规律（第 N 个启动的服务 = 30010 + 5N）；相邻服务之间留有空隙，
新服务可插队编号（如 30012）而不打乱整体；避免与常见软件默认端口贴脸冲突。

> 固定端口被占用时 Spring Boot / Dubbo **不会自动换端口**（Tomcat bind 失败 → 启动退出；
> Dubbo 同理，报 `Address already in use`）。想让服务自动挑空闲端口可用 `server.port=0`
> / `dubbo.protocol.port=-1`，但注册进 Nacos 的端口会随机化，不利于排查与文档，
> 因此本项目选择固定端口 + 拉开间距的方案。

---

## 一、基础设施端口（中间件，docker-compose.yml，需先启动）

| 中间件 | 宿主机端口 | 容器端口 | 说明 / 配置位置 |
|--------|-----------|---------|-----------------|
| Nacos 控制台/HTTP | 8848 | 8848 | 各模块 `bootstrap.yaml` `spring.cloud.nacos.discovery.server-addr` |
| Nacos gRPC（2.x 客户端） | 9848 | 9848 | Nacos 2.x 客户端必需，随 8848 自动开放 |
| MySQL | 3306 | 3306 | 各模块 `spring.datasource.url`，首次启动自动执行 `./sql` 建库 |
| Redis | 6379 | 6379 | 各模块 `spring.data.redis` |
| RocketMQ NameServer | 9876 | 9876 | 各模块 `qiyu.rmq.*.nameSrv` |
| RocketMQ Broker | 10911 / 10909 | 10911 / 10909 | 10911 主端口，10909 VIP 通道 |
| MinIO API | 9000（本机直连）/ 39000（容器映射） | 9000 | 应用配置 `qiyu.minio.endpoint=http://127.0.0.1:9000`；39000 供 Linux 容器部署 |
| MinIO 控制台 | 39001 | 9001 | 浏览器访问 MinIO 管理界面 |
| SRS RTMP 推流 | 1935 | 1935 | OBS/推流地址 |
| SRS HTTP API / WebRTC 信令 | 1985 | 1985 | 前端 `/rtc` 代理到此 |
| SRS HTTP-FLV / HLS | 8080 | 8080 | 拉流播放地址 |
| SRS WebRTC 媒体 | 8000 (UDP+TCP) | 8000 | **Windows 本机必须用原生 srs.exe**（`docker/srs-native.conf`） |

> Nacos 命名空间：`ef63e53e-94c8-4b1c-865e-6824177b2893`。

---

## 二、Dubbo 协议端口（30010 起，步长 5，按启动波次编号）

消费端（api / bank-api / admin-api / gateway）不监听 Dubbo 端口，从 Nacos 动态发现 provider。

| 新端口 | 模块 | 暴露的主要 RPC | 启动波次 |
|--------|------|---------------|----------|
| **30010** | qiyu-live-bank-provider | IQiyuCurrencyAccountRpc, IPayProductRpc, IPayOrderRpc, IReconciliationRpc | Wave0（旧 31001） |
| **30015** | qiyu-live-account-provider | IAccountTokenRPC | Wave0（旧 30002） |
| **30020** | qiyu-live-id-generate-provider | IdGenerateRpc | Wave0（旧 30003） |
| **30025** | qiyu-live-im-provider | ImTokenRpc, ImOnlineRpc | Wave0（旧 30004） |
| **30030** | qiyu-live-user-provider | IUserRpc, IUserTagRpc, IUserPhoneRPC | Wave1（旧 30006） |
| **30035** | qiyu-live-im-core-server | IRouterHandlerRpc | Wave1（旧 30007；启动需注入 `DUBBO_IP_TO_REGISTRY=127.0.0.1 DUBBO_PORT_TO_REGISTRY=30035`） |
| **30040** | qiyu-live-im-router-provider | ImRouterRpc | Wave2（旧 30008） |
| **30045** | qiyu-live-living-provider | ILivingRoomRpc | Wave3（旧 30009） |
| **30050** | qiyu-live-msg-provider | ISmsRpc | Wave4（旧 30010） |
| **30055** | qiyu-live-gift-provider | IGiftConfigRpc, IRedPacketRpc, ISkuOrderRpc, ISkuRpc, IAnchorShopRpc, ICartRpc | Wave4（旧 30011） |
| **30060** | qiyu-live-video-provider | IVideoRpc | Wave4b（旧 30021） |
| **30065** | qiyu-live-stream-provider | ILivingStreamRpc, ILivingPlayBackRpc, IResourceRpc | Wave4c（旧 30005；引用 living+im-router，必须在它们之后） |

---

## 三、HTTP / Web 入口端口（38080 锚点，步长 5）

| 新端口 | 模块 | context-path | 说明 |
|--------|------|--------------|------|
| 38080 | qiyu-live-gateway | — | 鉴权网关，**仅路由 `/live/api/**` → 38085**；bank-api/admin-api 为直连 |
| **38085** | qiyu-live-api | `/live/api` | 主业务 API（前端 `/api` 代理经 38080 转到这里）（旧 38100） |
| **38090** | qiyu-live-stream-provider | —（Controller 前缀 `/api/stream`） | SRS 回调入口：`on_publish`/`on_unpublish`/`on_dvr`（旧 38101，SRS conf 的 http_hooks 已同步） |
| **38095** | qiyu-live-bank-api | `/live/bank` | 支付回调入口（模拟回调 URL：`http://localhost:38095/live/bank/payNotify/wxNotify`，api 侧默认值已同步）（旧 38201） |
| **38100** | qiyu-live-admin-api | `/live/admin` | 管理后台 API（运营台）（旧 38300；web_admin 代理已同步） |

---

## 四、IM 长连接端口（Netty，im-core-server）

| 新端口 | 协议 | 配置项 |
|--------|------|--------|
| **38110** | TCP | `qiyu.im.tcp-port`（旧 38085） |
| **38115** | WebSocket | `qiyu.im.ws-port`（旧 38086） |

> 前端连接地址由 `/live/api/im/getImConfig` 下发（读 api 侧同名配置，与 im-core-server 保持一致），前端不写死端口。
> 服务间消息推送路径：msg/gift/living/stream → im-router(30040) → 按 Redis 绑定 ip 找到 im-core-server(30035) → 写回 Channel。

---

## 五、前端端口

| 端口 | 场景 | 说明 |
|------|------|------|
| 3000 | web_live 开发（`npm run dev`） | Vite；代理：`/api`→38080、`/rtc`→1985、`/minio`→9000 |
| **3005** | web_admin 开发（`npm run dev`） | 运营台；代理 `/adminApi`→38100（旧 3001） |
| 80 | 生产（docker-compose-full.yml） | nginx 镜像 |

---

## 六、启动波次总览（scripts/start-all.sh 已同步）

```
Wave0  基础层:            bank(30010) account(30015) idgen(30020) im(30025)
Wave1:                    user(30030) im-core(30035,需注入注册IP) gateway(38080) bank-api(38095)
Wave2  IM路由:            im-router(30040)
Wave3  直播间:            living(30045)
Wave4  消息/礼物:         msg(30050) gift(30055)
Wave4b 视频:              video(30060)
Wave4c 流媒体:            stream(30065 + HTTP 38090)
Wave5  入口:              api(38085) admin-api(38100)
```

一键启动（自动按波次；脚本已改为优先用 `JAVA_HOME` 的 JDK，避免 PATH 里 JDK8 启动失败）：

```powershell
scripts\start-all.bat            # 中间件+全部服务（缺 jar 自动构建）
scripts\start-all.bat status     # 查看状态
scripts\start-all.bat stop       # 停止
```

```bash
bash scripts/start-all.sh
```

> admin-api 已加入 Wave5。若之前用 IDEA 启动过服务，先全部停掉再跑脚本，
> 否则 IDEA 的旧进程会占住 Dubbo 端口导致新进程绑定失败。

---

## 七、端口占用速查（本机需空闲的端口）

```
前端:     3000(dev web_live)  3005(dev web_admin)  80(prod, 可选)
HTTP:     38080 38085 38090 38095 38100
Dubbo:    30010 30015 30020 30025 30030 30035 30040 30045 30050 30055 30060 30065
IM Netty: 38110 38115
中间件:   8848 9848 | 3306 | 6379 | 9876 10911 10909 | 9000 39000 39001 | 1935 1985 8080 8000
```

查占用：`netstat -ano | findstr <端口>`（Windows）/ `lsof -i:<端口>`（Linux/macOS）。
