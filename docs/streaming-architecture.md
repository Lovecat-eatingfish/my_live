# 视频流推送系统设计方案

> 本文档规划旗鱼直播平台的视频流推送架构，采用全自建模式：SRS 流媒体服务器 + MinIO 对象存储。本方案**暂不包含连麦功能**（WebRTC 信令通道留待后续实现）。

---

## 一、现状与目标

### 1.1 现有能力

| 模块 | 能力 | 说明 |
|------|------|------|
| `qiyu-live-living-provider` | 房间管理 | 开播/关播、房间元数据（名称/封面/人数） |
| `qiyu-live-im-core-server` | IM 通道 | Netty TCP(8085) / WebSocket(8086)，自定义二进制协议 |
| `qiyu-live-msg-provider` | 消息处理 | 弹幕、礼物、PK 等业务消息 |
| RocketMQ | 异步消息 | 用户上下线、礼物成功等事件驱动 |
| Redis | 缓存 | 房间在线用户集、房间数据缓存 |

### 1.2 缺失能力

- 无推流地址生成
- 无流媒体服务器
- 无视频流分发（HLS/WebRTC）
- 无录制回放
- 前端无视频播放器

### 1.3 本方案目标

1. 主播能够通过 OBS 等工具推流到 SRS
2. 观众能够实时观看直播（延迟可接受）
3. 支持录制回放（MinIO 存储）
4. 整体架构轻量，适合本地开发和小规模部署

---

## 二、技术选型

### 2.1 流媒体服务器

| 方案 | 选型 | 原因 |
|------|------|------|
| 流媒体服务 | **SRS 4.x** | 开源活跃、RTMP/HLS/WebRTC 一体化、HTTP API 完善、单机能抗 10W 并发 |
| 部署方式 | Docker | 快速启动，一条命令 |

### 2.2 协议选择

| 环节 | 协议 | 说明 |
|------|------|------|
| 推流（主播→服务端） | RTMP | OBS/手机 APP 广泛支持 |
| 分发（服务端→观众） | **HLS** | 普及广、兼容性最好、穿透防火墙能力强 |
| 信令通道 | 复用现有 IM 系统 | 5559 等业务消息码已有基础设施 |

**为什么不选 WebRTC**：初期不需要连麦，HLS 足够。等后续连麦需求来时再引入 WebRTC，届时再设计信令通道。

### 2.3 存储

| 存储类型 | 选型 | 说明 |
|----------|------|------|
| 视频对象存储 | **MinIO** | S3 兼容协议，本地部署，Docker 一键启动 |
| 元数据库 | 现有 MySQL | 扩展 `living_room` 表字段 |

---

## 三、部署架构

```
┌─────────────────────────────────────────────────────────────┐
│                   内网 / 本地开发环境                         │
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │    SRS       │  │    MinIO     │  │   Nacos     │      │
│  │  :1935 RTMP  │  │  :9000 S3    │  │   :8848     │      │
│  │  :1985 API   │  │              │  │              │      │
│  │  :8080 HLS   │  └──────────────┘  └──────────────┘      │
│  └──────┬───────┘                                           │
│         │ HTTP API (OkHttp 调用)                            │
│  ┌──────▼───────────────────────────────────────────────┐  │
│  │         qiyu-live-stream-provider (新增模块)           │  │
│  │         职责: 推流地址生成 / 流状态查询 / 录制触发       │  │
│  └──────┬───────────────────────────────────────────────┘  │
│         │ Dubbo RPC                                        │
│  ┌──────▼──────────┐                                      │
│  │ qiyu-live-api   │ ← 现有 web_live 前端调用              │
│  └─────────────────┘                                      │
└─────────────────────────────────────────────────────────────┘
```

### 3.1 SRS Docker 启动命令

```bash
docker run -d --rm \
  --name srs \
  -p 1935:1935 \
  -p 1985:1985 \
  -p 8080:8080 \
  -p 8000:8000 \
  -v /data/srs:/data \
  ossrs/srs:4
```

| 端口 | 用途 |
|------|------|
| 1935 | RTMP 推流 / 拉流 |
| 1985 | HTTP API（流状态查询、回调通知） |
| 8080 | HLS 分发（观众 HTTP 拉流） |
| 8000 | WebRTC（预留，后续连麦用） |

---

## 四、模块设计

### 4.1 新增模块

```
qiyu-live-stream-interface/       # 接口定义：ILivingStreamRPC, ILivingPlayBackRPC
qiyu-live-stream-provider/        # 实现：SRS 对接、流状态管理、MinIO 录制
```

### 4.2 接口设计

#### ILivingStreamRPC — 主播侧

```java
public interface ILivingStreamRPC {

    /**
     * 生成推流地址
     * 主播开播时调用，返回 rtmp://host/live/{streamKey}
     */
    LivingStreamVO createPushUrl(Long roomId, Long anchorId);

    /**
     * 查询流状态
     * @return streamStatus: 0=未开播, 1=推流中, 2=异常
     */
    StreamStatusVO getStreamStatus(Long roomId);

    /**
     * 主播关播
     */
    void stopStream(Long roomId);
}
```

#### ILivingPlayBackRPC — 观众侧

```java
public interface ILivingPlayBackRPC {

    /**
     * 获取播放地址
     * @param networkType mobile / wifi（目前统一返回 HLS 地址）
     */
    PlayBackVO getPlayUrl(Long roomId, String networkType);

    /**
     * 获取录制回放地址列表
     */
    List<RecordVO> getRecordList(Long roomId);
}
```

### 4.3 DTO / VO 定义

| 类名 | 字段 | 说明 |
|------|------|------|
| `LivingStreamVO` | pushUrl, streamKey, expireTime | 推流地址信息 |
| `StreamStatusVO` | status(0/1/2), viewerCount, bitrate | 流状态 |
| `PlayBackVO` | hlsUrl, httpFlvUrl | 播放地址 |
| `RecordVO` | recordId, recordUrl, duration, createTime | 录制回放 |

---

## 五、数据库扩展

### 5.1 living_room 表扩展

```sql
ALTER TABLE living_room
    ADD COLUMN push_url       VARCHAR(512)  COMMENT '推流地址' AFTER watch_num,
    ADD COLUMN stream_key     VARCHAR(128)  COMMENT '流 Key (roomId_hash)' AFTER push_url,
    ADD COLUMN stream_status   TINYINT  DEFAULT 0 COMMENT '0=未开播 1=推流中 2=异常' AFTER stream_key,
    ADD COLUMN srs_server_id   VARCHAR(64)   COMMENT 'SRS 实例标识' AFTER stream_status,
    ADD COLUMN start_time      DATETIME      COMMENT '本次开播时间' AFTER srs_server_id,
    ADD COLUMN record_enabled  TINYINT  DEFAULT 0 COMMENT '是否开启录制' AFTER start_time;
```

### 5.2 living_room_record 表（新增）

```sql
CREATE TABLE living_room_record (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    room_id      BIGINT      NOT NULL COMMENT '房间 ID',
    anchor_id    BIGINT      NOT NULL COMMENT '主播 ID',
    record_url   VARCHAR(512) NOT NULL COMMENT '回放地址 (MinIO URL)',
    duration     INT         NOT NULL COMMENT '时长(秒)',
    file_size    BIGINT      COMMENT '文件大小(字节)',
    start_time   DATETIME    NOT NULL COMMENT '录制开始时间',
    end_time     DATETIME    NOT NULL COMMENT '录制结束时间',
    status       TINYINT  DEFAULT 1 COMMENT '1=生成中 2=可用 3=失败',
    create_time  DATETIME  DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_room_id (room_id),
    INDEX idx_anchor_id (anchor_id)
) COMMENT '直播间录制记录';
```

---

## 六、SRS 对接设计

### 6.1 SRS HTTP API

| 接口 | 方法 | 用途 |
|------|------|------|
| `http://{srsHost}:1985/api/v1/streams` | GET | 查询所有流状态 |
| `http://{srsHost}:1985/api/v1/streams/{streamKey}` | GET | 查询指定流 |
| `http://{srsHost}:1985/api/v1/clients` | GET | 查询观众连接数 |

### 6.2 SRS 回调接口（Java 实现）

SRS 通过 HTTP 回调通知直播状态变化，Java 侧需实现以下端点：

#### 推流开始回调

```java
@PostMapping("/api/stream/on_publish")
public String onPublish(@RequestBody SrsCallbackVO vo) {
    // 1. 从 vo.stream 获取 streamKey，解析出 roomId
    // 2. 验证 token 是否合法（防伪造推流）
    // 3. 更新 living_room.stream_status = 1
    // 4. 通过 RocketMQ 广播 "STREAM_START" 事件
    // 返回 0 表示允许推流，非0拒绝
    return "0";
}
```

#### 推流结束回调

```java
@PostMapping("/api/stream/on_unpublish")
public String onUnpublish(@RequestBody SrsCallbackVO vo) {
    // 1. 更新 living_room.stream_status = 0
    // 2. 触发 MinIO 录制封存逻辑
    // 3. 通过 RocketMQ 广播 "STREAM_STOP" 事件
    return "0";
}
```

#### SRS 回调 VO

```java
@Data
public class SrsCallbackVO {
    private String action;        // on_publish / on_unpublish
    private String stream;        // streamKey
    private String client_id;     // SRS 客户端 ID
    private String ip;            // 推流客户端 IP
    private Long   timestamp;
}
```

### 6.3 流状态同步（轮询兜底）

SRS 回调依赖 SRS 能访问到 Java 服务（内网可行），若回调失败，通过定时任务轮询 SRS API 做兜底：

```java
@Scheduled(fixedDelay = 30000)
public void syncStreamStatus() {
    // 每 30 秒调用 GET /api/v1/streams 比对数据库状态
    // 修正可能的异常状态
}
```

---

## 七、录制回放设计

### 7.1 录制流程

```
主播推流 → SRS 转 HLS → 同时录制为 FLV 文件
                        ↓
              关播时触发录制封存
                        ↓
              MinIO 存储 + living_room_record 写入
                        ↓
              观众通过 getRecordList() 获取回放地址
```

### 7.2 MinIO 对接

```java
// qiyu-live-stream-provider 配置
@Bean
public MinioClient minioClient() {
    return MinioClient.builder()
        .endpoint("http://127.0.0.1:9000")
        .credentials("minioadmin", "minioadmin")
        .build();
}
```

### 7.3 录制文件命名

```
s3://qiyu-live-records/
  └── {year}/{month}/{day}/
        └── {roomId}_{anchorId}_{startTime}_{endTime}.mp4
```

---

## 八、与现有系统联动

### 8.1 现有 IM 消息码复用

| 消息码 | 用途 | 说明 |
|--------|------|------|
| 5559 | PK 用户上线 | 已有，复用 |
| 5563 | 推流状态变更 | **新增**：5560/5561 已被红包雨占用，改用 5563 |
| 5564 | 录制完成通知 | **新增**：通知观众端有回放可用 |

### 8.2 RocketMQ Topic 扩展

在 `qiyu-live-common-interface` 新增常量：

```java
public interface LivingStreamTopics {
    String STREAM_START = "living-stream-start";
    String STREAM_STOP  = "living-stream-stop";
    String RECORD_DONE   = "living-record-done";
}
```

---

## 九、前端改造

### 9.1 主播端（OBS 推流）

```
RoomPage.vue "开播" 按钮
  → 调用 createPushUrl() API
  → 获取 rtmp://127.0.0.1:1935/live/live_{roomId}_{anchorId}
  → 前端拼接显示给主播（复制到 OBS 填入）
  → 主播在 OBS 填写: 服务器=rtmp://... 串流密钥=空或随意
```

### 9.2 观众端（video.js 播放）

```html
<!-- 引入 video.js -->
<link href="/video.js/video-js.min.css" rel="stylesheet">
<script src="/video.js/video.min.js"></script>

<video id="player" class="video-js vjs-big-play-centered" controls>
</video>

<script>
  videojs('player', {
    autoplay: false,
    controls: true,
    sources: [{
      src: '{{hlsUrl}}',   // 后端返回的 http://127.0.0.1:8080/live/xxx.m3u8
      type: 'application/x-mpegURL'
    }]
  });
</script>
```

---

## 十、后续扩展预留

### 10.1 连麦（WebRTC）— 后续实现

- 信令通道：复用 IM 系统（现有 TCP/WebSocket 基础设施）
- 房间类型：普通直播间 / 连麦直播间（数据库新增 `room_type` 字段区分）
- 媒体服务：SRS 的 WebRTC 功能（端口 8000）预留

### 10.2 CDN 分发 — 后续实现

- HLS 推至 CDN 回源至 SRS
- 需要 nginx 转推或 SRS 直接配置 origin

---

## 十一、实现计划

### Phase 1（本期，实现基础推拉流）

1. SRS Docker 启动 + 验证 RTMP→HLS 通路
2. 新建 `qiyu-live-stream-interface` 模块，定义接口
3. 新建 `qiyu-live-stream-provider` 模块，实现：
   - 推流地址生成
   - SRS 回调接口
   - 流状态查询
4. 数据库扩展：`living_room` 加字段 + 新建 `living_room_record` 表
5. Nacos 配置新增 `qiyu-live-stream-provider.yaml`
6. 前端接入 video.js，实现观众端播放

### Phase 2（后续，录制回放 + 优化）

1. MinIO Docker 启动
2. 录制封存逻辑
3. 回放列表 API

### Phase 3（预留，连麦）

1. WebRTC 信令通道设计
2. SRS WebRTC 功能对接

---

## 十二、配置参考

### 12.1 SRS 配置 (`conf/srs.conf`)

```conf
listen              1935;
max_connections     1000;
pid                 ./objs/srs.pid;
srs_log_tank        console;
vhost __defaultVhost__ {
    hls {
        enabled         on;
        hls_path        /data/hls;
        hls_fragment    5;
        hls_window      30;
        hls_on_low_latency off;
    }
    recorder {
        enabled         on;
        recorder {
            enabled     on;
            append     ts_cmd();
        }
    }
}
```

### 12.2 Nacos 配置片段

```yaml
# qiyu-live-stream-provider.yaml
spring:
  application:
    name: qiyu-live-stream-provider
  data:
    redis:
      port: 8801
      host: cloud.db
      password: qiyu

qiyu:
  srs:
    host: 127.0.0.1
    rtmp-port: 1935
    api-port: 1985
    hls-port: 8080
    secret: your_secret_key_here
  minio:
    endpoint: http://127.0.0.1:9000
    access-key: minioadmin
    secret-key: minioadmin
    bucket: qiyu-live-records

dubbo:
  application:
    name: ${spring.application.name}
  registry:
    address: nacos://qiyu.nacos.com:8848?namespace=qiyu-live-test&&username=qiyu&&password=qiyu
  protocol:
    name: dubbo
    port: 9092
```
