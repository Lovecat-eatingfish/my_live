# 旗鱼直播 × SRS 流媒体接入设计文档

> 版本：v2.0（2026-09-12）
> 参考文档：SRS 6.0 官方入门 https://ossrs.net/lts/zh-cn/docs/v6/doc/getting-started
> 前置结论：项目中**已存在一套较完整的 SRS 接入实现**（`qiyu-live-stream-interface` / `qiyu-live-stream-provider` / `web_live` 播放器 / `docker/srs.conf`），本次设计基于该实现收敛、修缺、生产化，而非推倒重来。此前"接入失败"大概率是下文 §7 列出的几个断点之一，逐项修复即可。

---

## 1. 目标与范围

| 项 | 内容 |
|---|---|
| 推流（主播侧） | ① 浏览器 WebRTC 一键开播（摄像头+麦克风）；② OBS / FFmpeg RTMP 推流（专业场景） |
| 播放（观众侧） | WebRTC 播放为主（延迟 <1s），HTTP-FLV / HLS 兜底（延迟 3~10s，兼容性最好） |
| 状态联动 | 推流开始/结束 → 回调后端 → 更新房间流状态 → IM 广播观众端自动起播/提示断流 |
| 录制回放 | SRS DVR 会话录制 FLV → 关播回调 → 上传 MinIO → 生成回放记录 |
| 部署 | Docker Compose 一键起 SRS + MinIO，与现有 mysql/redis/nacos/rocketmq 同栈 |

不涉及：转码集群、CDN 分发、连麦（PK 房间类型已有占位，二期再接）。

---

## 2. 总体架构

```
 主播端                                SRS 5 (Docker, host 网络)                      观众端
┌─────────────┐   RTMP :1935   ┌─────────────────────────────┐   WebRTC :8000(TCP)  ┌─────────────┐
│ OBS / FFmpeg │ ─────────────▶│ vhost __defaultVhost__      │ ────────────────────▶│ LivePlayer  │
└─────────────┘                │  rtc_to_rtmp / rtmp_to_rtc  │  (WHEP /rtc/v1/play) │ (web_live)  │
┌─────────────┐  WebRTC :8000  │                             │   HTTP-FLV/HLS :8080 └─────────────┘
│ 浏览器开播   │ ─────────────▶│  hls / dvr / http_hooks     │ ────────────────────▶ (兜底播放)
│ RoomPage.vue │◀─SDP信令──────│                             │
└─────────────┘  :1985 /rtc/*  └──────────┬──────────────────┘
                                          │ http_hooks 回调
                                          ▼
                              qiyu-live-stream-provider :38101 (Dubbo 30005)
                              ├─ SrsCallbackController  on_publish/on_unpublish/on_dvr
                              ├─ LivingStreamServiceImpl  流状态机 + Redis 归属校验
                              ├─ SrsApiServiceImpl        调 SRS API 查流/踢流
                              └─ LivingRecordServiceImpl  DVR → MinIO 回放
                                          │
                       ┌──────────────────┼─────────────────────┐
                       ▼                  ▼                     ▼
                MySQL(t_living_room)  Redis(streamKey映射)  IM 5563 消息
                push_url/stream_key   正反向映射24h         → 观众端自动起播
                stream_status         stream_status
```

协议选择理由：
- **推流双通道**：浏览器 WebRTC（`rtc_to_rtmp on` 桥接为 RTMP，下游 HLS/DVR 全复用）+ OBS RTMP，两条路进 SRS 后是同一条流。
- **播放双通道**：WebRTC 低延迟主通道 + HLS 兜底（iOS Safari / 网络差时自动降级），HTTP-FLV 作为中间档。
- **信令**：WebRTC SDP 交换走 SRS 内置 HTTP API（:1985 `/rtc/v1/publish|play`），不需要自建信令服务器。

---

## 3. 流地址与安全设计

### 3.1 URL 约定（app 固定为 `live`）

| 用途 | 格式 |
|---|---|
| RTMP 推流 | `rtmp://{SRS_HOST}:1935/live/{streamKey}` |
| WebRTC 开播（浏览器） | `webrtc://{SRS_HOST}:8000/live/{streamKey}`，SDP 信令 `http://{SRS_HOST}:1985/rtc/v1/publish/` |
| WebRTC 播放 | `http://{SRS_HOST}:1985/rtc/v1/play/?app=live&stream={streamKey}` |
| HTTP-FLV 播放 | `http://{SRS_HOST}:8080/live/{streamKey}.flv` |
| HLS 播放 | `http://{SRS_HOST}:8080/live/{streamKey}.m3u8` |
| 录制回放 | MinIO 预签名 URL（FLV/MP4） |

### 3.2 streamKey 设计（已有实现，保留）

```
streamKey = "live_" + md5(roomId + "_" + anchorId + "_" + srs.secret)
```

- 开播前由后端 `POST /stream/createPushUrl` 生成（**只有主播本人可取**），同时写入：
  - MySQL `t_living_room.push_url / stream_key / stream_status`
  - Redis：`streamKey→roomId` 正映射 + `roomId→streamKey` 反映射（24h 过期）
- SRS `on_publish` 回调携带 streamKey，后端反查 Redis 校验归属，**未在 Redis 登记的流直接拒绝**（防伪造推流占坑）。
- 关播：`POST /living/closeLiving` → `livingStreamRpc.stopStream` → 调 SRS API `/api/v1/clients` 踢掉推流客户端 + 重置状态 + IM 广播。
- 回调接口校验 `X-Srs-Secret` 请求头（见 §7 P0-1，当前配置未闭环）。

---

## 4. 后端设计（qiyu-live-stream-provider）

### 4.1 模块职责与现有代码地图

| 类 | 职责 | 状态 |
|---|---|---|
| `config/SrsConfig` (`@ConfigurationProperties("qiyu.srs")`) | host/端口/secret/dvr 目录/回调密钥 | 已有 |
| `controller/SrsCallbackController` | on_publish / on_unpublish / on_dvr 三回调 | 已有，需补 Secret 强校验 |
| `service/impl/LivingStreamServiceImpl` | createPushUrl / onPublish / onUnpublish / stopStream / getStreamStatus | 已有（核心） |
| `service/impl/SrsApiServiceImpl` | OkHttp 调 SRS `/api/v1/streams`、`/api/v1/clients`（踢流） | 已有 |
| `service/impl/LivingRecordServiceImpl` + `MinioStorageServiceImpl` | on_dvr → 上传 MinIO bucket `qiyu-live-records` → 写回放记录 | 已有 |
| RPC 契约 `qiyu-live-stream-interface` | `ILivingStreamRpc` / `ILivingPlayBackRpc` | 已有 |
| API 层 `qiyu-live-api/StreamController` | `/stream/createPushUrl|status|playUrl|records` | 已有 |

### 4.2 核心时序（开播）

```
主播浏览器            api(38100)         stream-provider        SRS              观众
    │ POST /stream/createPushUrl │                │                │               │
    │───────────────────────────▶│──Dubbo────────▶│ 校验主播身份     │               │
    │                            │                │ 生成streamKey   │               │
    │                            │                │ 写MySQL+Redis   │               │
    │◀──pushUrl+rtcPublishApi────│                │                │               │
    │──WebRTC publish(SDP)───────────────────────────────────────▶│               │
    │                            │                │◀─on_publish────│ 校验Secret    │
    │                            │                │ Redis归属校验   │               │
    │                            │                │ stream_status=1│               │
    │                            │                │──IM 5563──────────────────────▶│
    │                            │                │                │      LivePlayer自动起播
```

关播时序对称：`closeLiving` 主动踢流 → SRS 断开 → `on_unpublish` 回调 → 状态复位 + IM 5563（停止播放）。异常掉线（主播直接关页面）也由 `on_unpublish` 兜底，**所以关播接口不能只依赖前端调用**。

### 4.3 需要改造/补齐的点

1. **回调鉴权闭环**（P0）：`SrsCallbackController` 校验 `X-Srs-Secret`，但 `docker/srs.conf` 的 `http_hooks` 不会自动带这个头。SRS 5 的 hooks 支持在 URL 上带 query，或用 `http_hook_...` 自定义头配置；最简单做法：回调 URL 改为 `http://127.0.0.1:38101/api/stream/on_publish?secret={callbackSecret}`，后端从 query 校验。
2. **播放地址下发**：`getPlayUrl` 建议一次返回三通道（rtcSignalingUrl / flvUrl / hlsUrl），前端按优先级降级，不要让前端拼地址。
3. **状态以 SRS 为准**：`getStreamStatus` 已做 DB+SRS 双查，保留；DB 状态仅作缓存，展示前以 SRS API `publish.active` 为准。
4. **配置收敛**：`nacos-config/qiyu-live-stream-provider.yaml` 中 `dvr-*-dir` 仍指向 `D:/softwarepackage/development/srs_v5/...`（旧本机 SRS 残留），改为 `./docker-data/srs` 一致的容器内路径 `/data/dvr`；本地 `application.yml` 与 Nacos 配置二选一，避免双源漂移。

### 4.4 数据模型

`sql/streaming_module.sql`（幂等 ALTER）：
- `t_living_room` 增加：`push_url`、`stream_key`、`stream_status`、`srs_server_id`、`stream_start_time`、`record_enabled`
- 录制表：`room_id / anchor_id / record_url / duration / file_size / start_time / end_time / status(1生成中|2可用|3失败)`
- ⚠️ 该脚本建表名写的是 `qiyu_living_room_record`，而 PO 注释/其它模块用 `t_living_room_record` —— **需核实实际库表名并统一**（P0-2）。
- living-provider 与 stream-provider 各自映射同一张 `t_living_room`，列以 streaming_module.sql 补齐后的全集为准。

---

## 5. 前端设计（web_live, Vue3 + Vite）

### 5.1 页面与组件

| 位置 | 内容 | 现状 |
|---|---|---|
| `views/RoomPage.vue`（主播侧） | "摄像头开播"按钮 → WebRTC publish SDP 交换；同时展示 RTMP 地址/OBS 使用说明供复制；开播后实时预览 | 已有 |
| `views/RoomPage.vue`（观众侧） | `LivePlayer` 播放；监听 IM 5563：主播开播→自动起播，断流→提示 | 已有 |
| `components/LivePlayer.vue` | WebRTC（`/rtc/v1/play/`）主通道 + video.js HLS 兜底，带重连 | 已有 |
| `components/ReplayPlayer.vue` | 回放：FLV 用 flv.js，MP4 原生 | 已有 |

### 5.2 关键实现要点

- **WebRTC 信令代理**：开发环境 `vite.config.js` 已代理 `/rtc → http://127.0.0.1:1985`、`/api → http://localhost:38080`（重写 `/live/api`）。生产环境必须由网关/Nginx 提供同样的转发（见 §6.3），否则页面同源策略下信令不通——**这是"接入失败"的高概率嫌疑点之一**。
- 起播触发：观众端不能在进房时就拉流（流可能还没推），必须等 IM 5563 或 `stream/status` 轮询确认后再挂 `LivePlayer`。
- 主播用浏览器开播时：页面必须 **HTTPS 或 localhost**（浏览器 getUserMedia 安全策略），非本机域名调试需走 Nginx 自签证书 + `thisisunsafe`，或直接用 OBS 推流绕开。

---

## 6. Docker 部署设计

### 6.1 docker-compose 服务清单（SRS 部分，现状已基本正确，保留）

```yaml
srs:
  image: ossrs/srs:5
  container_name: qiyu-srs
  restart: unless-stopped
  network_mode: host        # WSL2 下直绑 1935/1985/8080/8000，绕开 UDP 转发丢包
  environment:
    CANDIDATE: "192.168.31.252"   # ⚠️ 换机器必改：宿主机局域网 IP（.env 管理）
  volumes:
    - ./docker-data/srs:/data
    - ./docker:/usr/local/srs/conf-qiyu:ro
  command: objs/srs -c conf-qiyu/srs.conf
minio:
  image: minio/minio
  ports: ["39000:9000", "39001:9001"]
  # bucket: qiyu-live-records
```

端口总表（对照官方 getting-started）：

| 端口 | 协议 | 用途 |
|---|---|---|
| 1935/tcp | RTMP | OBS/FFmpeg 推流 |
| 1985/tcp | HTTP API | WebRTC SDP 信令（/rtc/v1/*）+ 运维 API（查流/踢流） |
| 8080/tcp | HTTP | HTTP-FLV / HLS 播放分发 |
| 8000/tcp+udp | WebRTC | 媒体传输（配置已开 TCP 模式，绕 Docker UDP 转发问题） |
| 38101/tcp | HTTP | stream-provider http_hooks 回调（仅本机回环） |

> 官方文档还提到 1990/8088（HTTPS API/播放器）与 10080/udp（SRT），当前场景用不到，不开放。

### 6.2 srs.conf 要点（docker/srs.conf，保留现配置）

- `rtc_server.candidate $CANDIDATE` —— 必须等于**观众/主播浏览器实际可达的 SRS 主机 IP**，不是容器 IP。三环境取值：
  - 本机调试：`127.0.0.1`
  - 局域网演示：宿主机局域网 IP（如 `192.168.31.252`）
  - 云服务器：公网 EIP
- `rtc { rtc_to_rtmp on; rtmp_to_rtc on; }`：浏览器推流桥接 RTMP、RTMP 反向供 WebRTC 播放，双协议互通。
- `dvr { dvr_plan session; dvr_path /data/dvr/[stream]/[timestamp].flv; }`：会话录制，关播落盘。
- `http_hooks on_publish/on_unpublish/on_dvr → http://127.0.0.1:38101/api/stream/*`：host 网络下容器与宿主机回环互通，成立；若日后改 bridge 网络需改为 `http://host.docker.internal:38101/...`。
- HLS：`hls_fragment 2; hls_window 10;`。

### 6.3 生产化部署（二期，需要做的增量）

1. **网关补路由**：`qiyu-live-gateway` 目前只有 `/live/api/**` → qiyu-live-api，需增加：
   - `/rtc/**`、`/live/{*}.flv`、`/*.m3u8` 转发 → SRS `http://{host}:1985/8080`（或由 Nginx 承担静态流转发，网关只管业务 API）
   - 更推荐：Nginx 80/443 统一入口，`/api/` → gateway，`/rtc/` → SRS:1985，`/live/` → SRS:8080，WebRTC 播放无需 HTTPS，但**主播浏览器开播需 HTTPS**。
2. **CANDIDATE 与域名**：走 HTTPS 域名后 `CANDIDATE` 填公网 IP；`webrtc://` 地址里的 host 由后端按环境配置生成。
3. MinIO 预签名 URL 下发，避免回放地址带固定凭证。

---

## 7. 之前接入失败的可能原因排查清单（按优先级）

| # | 问题 | 现象 | 修复 |
|---|---|---|---|
| P0-1 | 回调密钥不对称：后端校验 `X-Srs-Secret`，但 srs.conf hooks 不带该头 | on_publish 回调被拒 → 推流成功但房间状态不更新、观众不自动起播 | 回调 URL 带 `?secret=`，后端从 query 校验（§4.3-1） |
| P0-2 | `streaming_module.sql` 建表名 `qiyu_living_room_record` 与 PO 不一致 | 回放记录写入失败/查不到 | 核实库表名，统一为 `t_living_room_record` |
| P0-3 | WebRTC 信令不通：生产/局域网无 `/rtc` 反向代理 | 浏览器开播卡在 SDP 交换、观众黑屏 | vite 仅开发可用；生产上 Nginx/网关补转发（§6.3-1） |
| P0-4 | CANDIDATE 配错（换网络/换机器后未改） | WebRTC 握手失败：OBS RTMP 推得动但浏览器播不出 | candidate=浏览器可达的 SRS IP；先 `docker exec` 确认 env 生效 |
| P1-5 | UDP 转发丢包（Docker Desktop/WSL2） | WebRTC 画面卡顿/黑屏但 RTMP 正常 | 已用 `network_mode: host` + RTC over TCP 解决，勿改回端口映射 |
| P1-6 | Nacos 配置残留：dvr 目录指向旧本机 SRS 路径 | 录制文件落盘位置不对/回调拿不到文件 | 改为 `/data/dvr`（§4.3-4） |
| P1-7 | 观众端未等 5563 就拉流 | 偶发黑屏，实际是流未就绪 | 前端以 IM 5563 / status 确认为起播前置（§5.2） |

快速自检命令（排障顺序）：

```bash
# 1. SRS 是否收到流（有 publish.active 才算推流成功）
curl http://127.0.0.1:1985/api/v1/streams/
# 2. 推流后浏览器能否拉到 FLV（排除 WebRTC 因素）
curl -I http://127.0.0.1:8080/live/{streamKey}.flv
# 3. 回调是否到达 stream-provider
docker logs -f qiyu-srs | grep -i hook
# 4. WebRTC 握手
curl -X POST http://127.0.0.1:1985/rtc/v1/play/ -d '{"api":"x","streamurl":"webrtc://127.0.0.1/live/test","sdp":"x"}'
```

---

## 8. 落地计划

| 阶段 | 内容 | 验收标准 |
|---|---|---|
| 一：修断点（1~2天） | P0-1/P0-2/P0-3/P0-4 + 配置收敛（§4.3） | OBS 推流 → 观众 WebRTC/HLS 均可看；关播自动复位；回调日志全通 |
| 二：体验补齐（2~3天） | getPlayUrl 三通道下发、前端降级策略、异常断流 IM 提示、回放列表页联调 | 断网/杀进程等异常场景观众端有明确提示并自动重连 |
| 三：生产化（3~5天） | Nginx 统一入口 + HTTPS、网关路由、CANDIDATE 环境化、MinIO 预签名、compose `.env` 化 | 局域网/公网双环境按同一份 compose 起服务即用 |

---

## 附：与现有 `docs/streaming-architecture.md` 的关系

旧文档记录了"SRS 4 + HLS 起步 → 演进 SRS 5 + WebRTC"的选型过程，与本设计的最终态一致。本设计以最终态为准，落地后可将旧文档标记为历史。

---

## 附2：2026-09-12 实测记录（推流链路已全链路打通）

端到端验证脚本：`scripts/stream_push_test.mjs`（登录→开播→createPushUrl→FFmpeg推流→FLV/HLS播放→closeLiving踢流清理，当前全绿）。

实测发现并修复的问题（比 §7 排查清单更靠前的根因）：

1. **宿主机残留原生 srs.exe 抢占端口（最大坑）**：`D:\softwarepackage\development\srs_v5\SRS\objs\srs.exe` 直接监听 1935/1985/8080，Windows 侧所有连接（推流、API、播放）都被它截走，docker 容器形同虚设。已 kill。**换环境/重装系统后如再现"配置改了没生效"，先查 `netstat -ano | findstr 1935` 确认监听进程身份。**
2. **Docker Desktop 的 host 网络不等价于 WSL2 mirrored 网络**：即使 `.wslconfig` 开了 `networkingMode=mirrored`，容器的 host 网络绑定在 docker-desktop 后端 VM 内，Windows 侧不可达。容器必须用**端口映射**模式（见 docker-compose.yml 已更新）。WebRTC UDP 转发质量待浏览器实测，若有问题再议 Linux 服务器部署。
3. **hooks 回调地址**：端口映射模式下容器内 `127.0.0.1` 不是宿主机，已改为 `http://host.docker.internal:38101/api/stream/*`。
4. **DVR 路径收敛（P1-6 已修复）**：删除 application.yml 中指向 `D:/softwarepackage/...` 的旧覆盖，回到代码默认值 `/data/dvr` → `./docker-data/srs/dvr`，已实测 on_dvr 回调路径映射正确。注意：旧 jar 内打包的 application.yml 仍是旧值，重新 `mvn package` 后自然修复；临时可用启动参数 `--qiyu.srs.dvr-container-prefix=/data/dvr` 覆盖。
5. **Git Bash 环境注意**：MSYS 路径转换会把 `/data/dvr` 之类参数改写成 `D:/.../Git/data/dvr`，docker 命令和 java 启动参数需加 `MSYS_NO_PATHCONV=1`。
6. ** Dubbo 注册丢失的坑**：stream-provider 的 Dubbo 服务（`ILivingStreamRpc`）可能因心跳丢失被 Nacos 摘除（Spring Cloud 注册会自行恢复，Dubbo 不会），症状为 `/stream/createPushUrl` 报 500 "No provider available"。重启 stream-provider 即恢复。
7. srs.conf 新增 `http_remux { enabled on; }`（HTTP-FLV 播放必需）；conf 文件热加载在 Windows 挂载卷上不生效，改配置必须 `docker restart qiyu-srs`。

浏览器侧待人工验证：WebRTC 播放页 `http://192.168.31.252:8080/players/rtc_player.html?autostart=true&stream={streamKey}` 与主播 WebRTC 开播（需 localhost 或 HTTPS）。
