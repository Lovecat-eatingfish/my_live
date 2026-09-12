# Bug 分析：一条弹幕消息，其他用户会收到两条

> 排查日期：2026-09-12
> 状态：**已修复（方案 A，前端 ack）并验证通过**
> 现象：用户 A 在直播间发送一条弹幕，房间内其他用户（B）会先后收到 **两条一模一样的消息，间隔约 5 秒**。
> 影响范围：web 端（`web_live`）所有下行 IM 业务消息（5555 弹幕 / 5556 礼物 / 5560 红包雨 / 5561 领取通知 / 5562 订单状态）都会重复推送一次。App 原生客户端不受影响（已实现 ack 应答）。

## 关联需求：主播退出直播间自动关播并通知观众（2026-09-12 已实现）

在排查本 bug 时顺带发现：`LivingRoomTxServiceImpl#closeLiving` 是空壳（原实现整体被注释，直接 `return true`），导致"结束直播"按钮和"主播断开 IM 连接"都不会真正关闭直播间。

已补全实现（`qiyu-live-living-provider`）：

- `closeLiving`：校验房间存在且操作者是主播本人（观众断连不触发）→ `t_living_room.status` 置 0 → 清理 `living_room_obj` / `living_room_list` 缓存。
- 新增广播：向房间成员集合（`living_room_user_set:{appId}:{roomId}`）内所有观众推送 **bizCode=5565（LIVING_ROOM_CLOSE，新增枚举）**，data 为关闭的 roomId。
- 触发路径两条：① 主播点"结束直播"（`/living/closeLiving` API）；② 主播直接退出页面/关闭浏览器标签（IM 断连 → `im_offline_topic` → `userOfflineHandler` → `closeLiving`）。
- 前端 `RoomPage.vue`：收到 5565 且 roomId 匹配时提示"主播已离开，直播间已关闭"并跳回首页。
- 注意：`t_living_room_record` 是**直播回放记录表**（record_url/duration/file_size），原注释代码往里插"关播归档"是错的（还会因 record_url 非空约束失败），本次实现未动该表。

验证：主播 GUI 点"← 返回"离开 → 房间 31 在 DB 中 status 变 0，房间内观众探针收到 `bizCode=5565, data="31"`。

## 修复记录（2026-09-12）

- `web_live/src/utils/im/connection.js`：收到 `code=1003` 业务消息后，解析 body 中的 `appId/userId/msgId`，回发 `code=1005` ack 帧（新增 `_ackBusinessMsg`）。
- `web_live/src/views/RoomPage.vue`：msgId 去重修正为从 body（ImMsgBody JSON）内部取值（原实现取的是顶层帧字段，一直未生效），作为 ack 丢失时的兜底。
- 验证结果：探针向房间发送一条弹幕，浏览器端 8 秒后统计仅显示 **1 条**；服务端日志显示 5 秒延迟检查时 `retryTimes is -1`（ack 已删除重发记录），未发生第二次推送。

---

## 一、现象与复现

两个浏览器分别登录不同用户进入同一房间（room/29）：

- 用户 A 发送 `你好`
- 用户 B 的消息列表出现两条 `你好`，时间相差 5 秒（截图：`12:33:32` 与 `12:33:37`）

服务端日志（`D:\tmp\logs\qiyu-live-im-core-server\192.168.31.252\qiyu-live-im-core-server.log`）中同一业务消息的投递轨迹：

```
11:33:37.140  MsgAckCheckServiceImpl  msg is {...msgId=5faddaac...},sendResult is SEND_OK   ← 首次推送
11:33:42.189  ImAckConsumer           retryTimes is 1, msgId is 5faddaac...                 ← 5秒后检查：未收到ack
11:33:42.197  MsgAckCheckServiceImpl  msg is {...msgId=5faddaac...},sendResult is SEND_OK   ← 第2次推送
11:33:47.283  ImAckConsumer           retryTimes is 2, msgId is 5faddaac...                 ← 达到上限，停止
```

观众端 WebSocket 探针抓到的原始帧（同一弹幕到达两次，**msgId 不相同**）：

```
code=1003 msgId=884dab8b-cba6-4daa-98b2-77ae1016f...  body={"bizCode":5555,"data":"{\"content\":\"GUI弹幕跨用户验证...\"}"}
code=1003 msgId=9779678d-e747-46a4-b832-1db1c623c...  body={"bizCode":5555,"data":"{\"content\":\"GUI弹幕跨用户验证...\"}"}
```

## 二、根因分析

### 2.1 后端的 ack 重发机制（这是设计，不是 bug）

IM 核心服务为了保证"消息至少送达一次"，内置了一套 **ack + 延迟重发** 机制，链路如下：

```
消息到达 core server
   │
   ├─ RouterHandlerServiceImpl.sendMsgToClient()
   │     生成 msgId，推送给客户端                      ← 客户端第 1 次收到
   │     recordMsgAck(msgId, 1)   → 写入 Redis hash
   │     sendDelayMsg()           → 发 RocketMQ 延迟消息（延迟级别2 ≈ 5s）
   │
   └─ 5s 后 ImAckConsumer 消费延迟消息
         getMsgAckTimes(msgId)
         ├─ 返回 -1（客户端已ack，记录被删除）→ 不重推，结束
         ├─ retryTimes < 2 → recordMsgAck(msgId, 2)
         │                    再发一条延迟消息
         │                    sendMsgToClient() 再推一次  ← 客户端第 2 次收到
         └─ retryTimes >= 2  → doMsgAck() 清理，放弃
```

相关代码位置：

| 环节 | 类 | 位置 |
|---|---|---|
| 首推 + 记录 ack + 发延迟消息 | `RouterHandlerServiceImpl#onReceive / sendMsgToClient` | `qiyu-live-im-core-server/.../service/impl/RouterHandlerServiceImpl.java` |
| 延迟检查与重推 | `ImAckConsumer`（消费 `qiyu_live_im_ack_msg_topic`） | `qiyu-live-im-core-server/.../consumer/ImAckConsumer.java` |
| ack 确认（删除重发记录） | `AckImMsgHandler`（处理客户端上行的 **code=1005** 消息） | `qiyu-live-im-core-server/.../handler/impl/AckImMsgHandler.java` |
| ack 记录存取 | `MsgAckCheckServiceImpl`（Redis hash：`qiyu-live-im-core-server:imAckMap:{appId}:{userId}`，field=msgId） | `qiyu-live-im-core-server/.../service/impl/MsgAckCheckServiceImpl.java` |
| 消息码定义 | `ImMsgCodeEnum.IM_ACK_MSG(1005, "im服务的ack消息包")` | `qiyu-live-im-interface/.../constants/ImMsgCodeEnum.java` |

**设计前提**：客户端收到 `code=1003` 的业务消息后，必须回发一条 `code=1005` 的 ack 消息（body 携带 `appId/userId/msgId`）。服务端 `AckImMsgHandler → doMsgAck()` 会删掉 Redis 里的重发记录，这样 5 秒后的延迟消息检查到 `retryTimes = -1`，就不会再推。

### 2.2 web 前端没有实现 ack（bug 所在）

`web_live/src/utils/im/connection.js` 的 `IMConnection` 只处理了 1001 登录、1003 业务、1004 心跳，**从未向上回发 1005 ack**。于是对服务端来说，每条消息都"没有得到确认"，固定走满一次重推 → 每条消息客户端必收 2 条（首推 + 5 秒后重推）。

### 2.3 次级问题：重推时 msgId 被重新生成，前端无法去重兜底

`RouterHandlerServiceImpl#sendMsgToClient` 每次调用都会 `UUID.randomUUID()` 生成**新的** msgId 再推送：

```java
public boolean sendMsgToClient(ImMsgBody imMsgBody) {
    ...
    String msgId = UUID.randomUUID().toString();   // ← 每次重推都换新 msgId
    imMsgBody.setMsgId(msgId);
    ...
}
```

后果有两个：

1. Redis 里 ack 记录的 field 是首推的 msgId A，而重推出去的消息是 msgId B。客户端就算对 B 回 ack，也删不掉 A 的记录（当然按正常时序，客户端 ack 的是首推的 A，逻辑上成立；但这让"按消息内容幂等"变得混乱）。
2. **前端按 msgId 去重的兜底方案会失效**——首推和重推的 msgId 不一样。此前在 `RoomPage.vue` 加的 msgId 去重只能拦住"同一帧的重复投递"（如 MQ 重复消费），拦不住 ack 重推。

另外注意：前端帧结构是 `{magic, code, len, body}`，`msgId` 在 **body（ImMsgBody JSON）内部**，顶层帧上没有 msgId 字段，去重逻辑必须从解析后的 body 里取。

## 三、修复建议

### 方案 A（根治，只改前端）：收到业务消息后回发 ack

在 `connection.js` 的 `onmessage` 中，识别 `code === 1003` 的帧，解析出 body 里的 `appId/userId/msgId`，回发：

```js
// 帧格式与服务端 ImMsg 一致：{ magic: 19231, code: 1005, len, body }
this._send(1005, {
  appId: <body.appId>,
  userId: <body.userId>,
  msgId: <body.msgId>,
  data: ''
})
```

服务端 `AckImMsgHandler → doMsgAck` 收到后删除 Redis 记录，5 秒后的延迟检查发现 `retryTimes = -1`，直接跳过重推。

> 注意：ack 必须在页面关闭/断线前尽早回发；若用 `imConn.sendChat` 同一个 `_send` 通道发送即可，无需新连接。

### 方案 B（兜底加固，改后端，需重启 im-core-server）：重推时保持 msgId 稳定

`RouterHandlerServiceImpl#sendMsgToClient` 只在 `imMsgBody.getMsgId()` 为空时才生成新 msgId：

```java
if (StringUtils.isEmpty(imMsgBody.getMsgId())) {
    imMsgBody.setMsgId(UUID.randomUUID().toString());
}
```

这样重推消息与首推消息 msgId 一致，前端 msgId 去重（需改为从 body 内取 msgId）才能真正兜底——即使 ack 丢失（比如用户恰好在 5 秒窗口内关页面），重推消息也会被前端去重逻辑拦掉，不会重复展示。

**两个方案建议同时上**：A 解决重复推送的源头，B 保证极端情况下（ack 丢失）用户仍看不到重复消息。

## 四、修复后的验证方法

1. 双浏览器进入同一房间，A 发一条弹幕：
   - B 只出现一条；
   - 观察服务端日志，`ImAckConsumer` 应打印 `retryTimes is -1`（表示 ack 已删记录），且不再出现第二次 `SEND_OK` 推送日志。
2. 抓 WebSocket 帧：`code=1005` 的上行 ack 应在每条 `code=1003` 下行消息后出现。
3. 断网 10 秒再恢复（模拟 ack 丢失），确认重推消息被前端 msgId 去重拦截，界面仍只有一条。

## 五、附：本次排查的原始证据

- 服务端日志（`D:\tmp\logs\qiyu-live-im-core-server\192.168.31.252\qiyu-live-im-core-server.log` 11:33:37 ~ 11:33:57 段）：同一 msgId 的 `SEND_OK` 推送出现 2 次，中间夹着 `retryTimes is 1` / `retryTimes is 2`。
- 观众端探针日志（`gui-test-screenshots/viewer_b_received.log`）：同一弹幕两条帧，`msgId` 分别为 `884dab8b-…` 与 `9779678d-…`。
- 用户截图：room/29 内 `你好` 消息在 12:33:32 与 12:33:37 出现两次，间隔 5 秒，与延迟级别 2（5s）吻合。
