# 路线图实施计划（roadmap-features-2026-09-13 的执行版）

> 写作日期：2026-09-13
> 定位：`docs/roadmap-features-2026-09-13.md` 是产品路线图（不动它的内容），本文是**我的实现计划**——
> 在通读路线图 + 核对当前代码现状后，整理出的"哪些不用做、矛盾怎么裁、每批怎么做、怎么验证"。
> 后续每完成一批，回到本文对应小节打勾/补记偏差，作为回望底稿。

---

## 一、现状修正：这些已经做完，不要重复排期

路线图第一节"现状盘点"写于六功能迭代**之前**，以下内容实际已上线：

| 路线图条目 | 实际状态 |
|---|---|
| §6.3 运营后台 admin | **已完成约 80%**：`qiyu-live-admin-api`（38100 端口，独立 token 鉴权，SHA-256+盐，内存 token）+ `web_admin` 完整 SPA（登录/对账/标签/视频管理/用户/直播间强关）。剩余缺口见批次五。 |
| 对账系统（ReconPage 迁入 admin） | 已完成，且已迁入 web_admin |
| 视频观看历史/收藏/点赞/评论/分享 | iteration-2026-09-13 已完成（`t_video_watch_history`、listByAction 等） |
| 余额不足送礼引导充值 | 已完成（5557 失败码 → ElMessageBox 充值引导） |
| 用户中心基础版 | `UserCenterPage`（4 tab）+ `UserProfileDialog` 已有；ProfilePage 公开主页没有 |
| 房间类型选择/带货验货/主播刷新不断播 | 已完成（type=4 验证、30s 延迟 MQ 关播检查） |

## 二、矛盾裁决定（已定/待定）

1. **真实支付渠道**：路线图内部自相矛盾——第一节短板写"不需要对接支付渠道"，§6.2 又详设了支付宝/微信。**倾向：不做真实支付**，保持 mock 渠道；只修它顺带指出的必修 bug（`PayOrderServiceImpl.payNotify` 无幂等，见批次零）。→ *待用户最终拍板，本文按"不做"编排，若改做则插入独立批次。*
2. **敏感词服务挂哪**：用户已人工确认——**挂 msg-provider 内**，但接口按独立 RPC 形状写（`IRiskRpc` 放 common-interface），将来拆包零改动。
3. **移动端**：不做（路线图明确）。
4. **SRS**：Windows 永远本机 srs.exe；Docker SRS 仅 Linux 服务器场景（compose 已用 profile 隔离）。

## 三、总体批次路径

```text
批次零（先修，半天）    支付回调幂等 + §6.12 七项逐条核实
批次一（合规，1~2天）   敏感词过滤 + 弹幕频率风控 + 账号封禁/禁言
批次二（关系链，2~3天） 关注/粉丝 + 开播推送 + 个人主页 ProfilePage + 等级体系
批次三（发现，1~2天）   排行榜(纯Redis) + 搜索(MySQL LIKE 过渡) + 通知中心
批次四（视频跃迁，1~2天）沉浸式 Feed + FFmpeg 异步转码(§6.13)
批次五（admin 补全+点睛，1~2天）仪表盘/截帧审阅/敏感词页 + 点睛清单
──── 以下放下一轮再定 ────
连麦 / 付费直播间 / 直播玩法 / 分账 / Prometheus 监控体系
```

排序依据：先过"能不能上生产"的合规硬门槛（风控），再做留存之本（关系链），再让内容被找到，最后是体验跃迁。每个批次**完成即 E2E 验证 + 单独 commit**（铁律：测试过才叫做完）。

---

## 四、各批次实现细节

### 批次零：先修现状问题（半天）✅ 已完成（2026-09-13）

1. **支付回调幂等**（`bank-provider/PayOrderServiceImpl.payNotify`）：✅ 已修
   - 查单后 `status == PAYED` 直接返回 ok；
   - `payNotifyHandler` 改为条件更新 `SET status=2, pay_time=now WHERE order_id=? AND status IN (0,1)`，affected==0（并发双回调）不再入账/发 MQ。E2E：充值 1000 金币后重放同一回调 2 次，余额不变（scripts/paynotify_idempotent_test.mjs 全绿）。
2. **§6.12 清单逐条核实结论**：
   - [x] `uploadVideo` 的 `file.getBytes()` → **本批已修**：改 `file.getInputStream()` 流式写 MinIO，真实 17.9MB 视频上传验证通过
   - [x] 网关白名单（2 条）→ **现状够用**：admin-api 走独立 38100 端口自鉴权不经网关，bank-api mock 回调直连，暂无遗漏
   - [x] `MessageDTO.senderAvtar` 拼写 → 前后端一致能跑，按既定策略**不动**，新消息类型用新 DTO
   - [x] bank 锁 sleep 递归重试 → 锁 TTL 仅 2s、单次 sleep 0.5~1s，实际竞争窗口小，**风险可接受，留远期**（Redisson `@DistributedLock`）
   - [x] `SingleMessageHandlerImpl` SCAN 全房间用户 → **留批次三**压测项
   - [x] `t_video_info.status` 只有 0/1 → **留批次五**审核流扩值
   - 本批额外发现并修复：**start-all.sh 在 `set -u` 下引用未设置的 `QIYU_JAVA_HOME` 直接退出**（改为 `${QIYU_JAVA_HOME:-}`）

### 批次一：内容风控（敏感词 + 封禁）✅ 已完成（2026-09-13）

> 实施与本文档计划的偏差（都是简化，效果等价或更好）：
> ① 词表放 msg-provider 自己的库（qiyu-live-msg）而非 qiyu_live_common，admin-api 经 IRiskRpc 管理词库，避免跨库数据源；
> ② 词库热更新用 **Redis 版本号懒重建**（每次检测 GET 一次版本，变更才从 DB 重建自动机），未用 MQ，少一条链路；
> ③ DFA 自动机为**自写 trie**（~80 行，按场景建 5 棵树，同词多场景取最严级别），未引第三方敏感词库；
> ④ 昵称/房间名/视频标题/评论 4 个检查点全部放 **api 层**（能直接给用户返回业务错误码 10113），msg-provider 只管弹幕（内部调用不走 Dubbo）；
> ⑤ 频率风控为固定窗口 Redis INCR（5s/10 条，超限静默丢弃）。
> E2E：scripts/risk_control_test.mjs 13 项断言全过（昵称/标题/评论/房间名拦截、弹幕替换*、拦截+5566 单发、禁言、解禁恢复、频率 12→10、封号 403、解封恢复）。
> 过程中挖出并修复全项目级编码坑：JDK17 中文 Windows 默认 GBK 导致 MQ 中文消息乱码+解析失败全丢（详见 troubleshooting.md §19），start-all.sh 已加 -Dfile.encoding=UTF-8。

**敏感词（挂 msg-provider，接口按 RPC 形状写）**

- 表 `risk_sensitive_word(id, word, level(1拦截 2替换* 3记录), scene(1弹幕 2昵称 3视频标题 4评论 5房间名), status, create_time)`，建在 qiyu_live_common 库。
- 实现：引入 `houbb/sensitive-word` 单 jar（或自写 DFA），服务启动加载构建自动机；Redis key `risk_word_version` 记版本，词库变更后 bump 版本 + MQ `risk_word_refresh` → consumer 重建自动机，无需重启。
- RPC：`IRiskRpc.checkText(RiskCheckReqDTO{text, scene, userId}) → RiskCheckRespDTO{pass / replaced(替换文本) / blocked, 命中词}`。
- **4 个接入点（各一行调用成本）**：
  1. 弹幕：`SingleMessageHandlerImpl.onMsgReceive` 解析 content 后 checkText(scene=1)——pass 放行 / replace 广播替换文本 / blocked 改发 bizCode=**5566** 单发回发送者"消息含敏感内容已拦截"；
  2. 昵称/房间名：user-provider 改资料、living-provider 开播处（scene=2/5），blocked 返回业务错误；
  3. 视频标题/评论：video-provider publish 与 comment（scene=3/4）；
  4. 频率风控：同处加 Redis 滑窗计数 `risk:freq:{userId}`（5s > 10 条 → 丢弃+计数）。
- bizCode 分配：**从 5566 起新功能连续取号**（5566 敏感拦截、5567 开播推送、5568/5569 单聊上下行、5570 升级特效、5571 未读数、5572 连麦信令、5573 PK 结算）。

**账号封禁/禁言**

- 表 `t_user_ban(id, user_id, type(1禁言 2封号), reason, start_time, end_time, operator, status)`（user 库）。
- 禁言校验点：`SingleMessageHandlerImpl` 发弹幕前查 Redis `ban:mute:{userId}`（进房预热，TTL=解封时刻）；封号校验点：`AccountCheckFilter` token 通过后查 `ban:account:{userId}`，命中 403。
- RPC：`IUserRpc.banUser/unban` → 写 DB + 写 Redis。web_admin 用户页加封禁按钮。

**验证**：curl/浏览器发含敏感词弹幕 → 被拦截/替换；刷新词库版本后无需重启生效；被封号用户登录 403。

### 批次二：关系链（关注/粉丝/开播推送/主页/等级）

**数据**：`t_user_relation(id, user_id, follow_user_id, status, create_time, UNIQUE(user_id,follow_user_id))`，**跟随 user_id % 100 分表**（加进 qiyu-live-user-shardingjdbc.yaml）。计数器 Redis `relation:follow_cnt:{uid}` / `relation:fans_cnt:{uid}`，定时 Job 对账校正。

**RPC（user-provider）**：follow / unfollow / isFollow / pageFollow / pageFans / countFans / countFollow。

**开播推送（留存核心）**：
- `LivingRoomServiceImpl.openLiving` 成功后发 MQ `user_open_living_push`（Topic 定义进 `UserProviderTopicNames`）；
- user-provider 消费：分页拉粉丝 → `ImMsgBody(bizCode=5567, data={roomId, anchorName, cover})` → 走现有 `ImRouterRpc.batchSendMsg` 在线推送（不在线丢弃，不做离线推送）；同时写站内通知（批次三的 `t_user_notify`）。
- 前端：RoomPage 关注按钮 + HomePage「关注」tab + 5567 分支 → 全局 Toast「你关注的主播开播了」点击跳转。

**个人主页**：`ProfilePage`（路由 `/profile/:userId`：资料 + 关注/粉丝/获赞 + tab「TA 的视频/观看历史(仅自己)/我的收藏」）。UserCenterPage 加我的订单/通知入口。

**等级体系**：
- 表 `t_user_profile_ext(user_id PK, follow_cnt, fans_cnt, like_received_cnt, level, exp)`；
- 经验规则（先简单）：看播 +10、弹幕 +1（日上限 20）、送礼 1 金币 +1、发视频 +50。**不建经验服务**——各动作消费端发 MQ `user_exp_change`，user-provider 单点消费（Redis `user:exp:{uid}` INCRBY，跨级回写 DB + 广播 5570 升级特效到所在房间）。等级映射配置化（Nacos）。
- 前端：ChatList 弹幕昵称前渲染 `L{level}` 小色块徽章（B 站风格）。

**验证**：A 关注 B → B 开播 A 收到 Toast；主页三 tab 数据正确；送礼后等级跨级有特效。

### 批次三：发现与分发

**排行榜（纯 Redis，零新依赖，先做）**：
- gift-provider `SendGiftConsumer` 扣费成功后 `ZINCRBY rank:gift:anchor:{yyyyMMdd} {price} {anchorId}` + `rank:gift:room:{roomId}`，TTL 8 天；日榜 ZREVRANGE，周榜 7 日 key ZUNIONSTORE；
- 人气榜：`userOnlineHandler` 时 `ZINCRBY rank:heat:room 1 {roomId}`（用已有房间 Set 去重）；
- 新接口 `GET /rank/{type}/{period}`；前端 RoomPage 右侧「本场贡献榜 Top10」面板（**5556 送礼消息到达时本地增量刷新，不轮询**）+ HomePage 排行弹层。

**搜索（阶段一）**：api 层聚合三次 Dubbo 查询——`t_living_room(room_name,status=1)`、`t_video_info(title,status=1)`、`t_user(nick_name)`，MySQL LIKE 过渡；前端全局搜索框 + 结果三分栏（直播/视频/用户）。ES 记入远期。

**通知中心**：表 `t_user_notify(id, user_id, type(1系统 2互动 3私信), title, content, jump_url, is_read, create_time)`；api `GET /notify/list`、`POST /notify/read`；写入分散在各动作，统一走 user-provider `NotifyService.send`（MQ）。前端 Header 铃铛组件（下拉列表 + jump_url 跳转），私信会话挂其下（**私信 DM 本批只做表+信令预留 5568/5569，会话 UI 视进度可延到批次五**）。

**验证**：送礼后榜单实时+1；搜索三分栏返回正确；点赞/关注产生未读通知。

### 批次四：视频跃迁（沉浸式 Feed + 转码）

**沉浸式 Feed**：`VideoSquarePage` 重构为竖屏全屏 `scroll-snap-type: y mandatory`，每卡 `scroll-snap-align: start`；`IntersectionObserver`(threshold 0.6) 控制可见项 play、其余 pause 并释放（保留前后 2 个实例防内存）；下一个视频 `preload='auto'`。后端 `GET /video/feed?lastId=&size=10` 游标分页（热度分 `play*0.4+like*0.3+create_time` 排序）；`POST /video/playReport` 完播率上报写 `t_video_play_log`。右侧悬浮按钮列 + 双击点赞动画 + 评论底部抽屉（接口全复用）。

**FFmpeg 异步转码（§6.13，决策：轻量异步，不做多码率/HLS/分布式 worker）**：
- publish 成功后发 MQ `video_transcode`（Topic 进 common-interface 仿 `GiftProviderTopicNames`），消息 `{videoId, userId}`；
- `VideoTranscodeConsumer`（仿 `SendGiftConsumer` 手写 DefaultMQPushConsumer）：① Redis setIfAbsent 幂等(5min) → ② `ffprobe` 探测真实编码/时长（顺带修正前端上报不准的 duration）→ ③ H.264+AAC mp4 走 `ffmpeg -c copy -movflags +faststart`（秒级 remux，修进度条拖动）；其他（HEVC/.mov/webm）走 `libx264 veryfast crf26 scale=-2:720 + aac` 重编码 → ④ `-ss 1` 抽封面（用户传过则跳过）→ ⑤ 传 MinIO `videos/transcoded/`（**原文件保留**），回写 `t_video_info` 的 url/cover/duration/size；
- `t_video_info` 加 `transcode_status(0处理中 1完成 2失败)`，**列表/详情默认只出 =1**；发布立即返回，前端提示"处理中"；MQ 重试 2 次后置 2，**失败回退播原文件**（video_url 不动），admin 可手动重触发；
- 配置 `qiyu.ffmpeg.path`（本机 ffmpeg 8.1.2 现成）；`docker/app.Dockerfile` 补 `apt install -y ffmpeg`；
- 前端仅发布提示语调整，转码完下次拉列表自动生效。

**验证**：用用户真实 HEVC .mov（【哲风壁纸】侧脸.mp4 那类）上传 → 转码后 Windows Chrome 可播、进度条可拖；失败注入播原文件。

### 批次五：admin 补全 + 点睛清单

**admin 补全**：
- 仪表盘：今日新增用户/开播场次/GMV/充值额四张卡（4 条 COUNT）；
- 视频审核流：`t_video_info.status` 扩值 0下架/1上线/2审核中/3驳回，publish 默认写 2，admin 审核通过置 1（**存量数据跑一条 UPDATE 迁移**）；web_admin 加审核队列页（图片预览+通过/驳回）；
- 截帧审阅：stream-provider 定时任务对 stream_status=1 房间每 30s 调 SRS HTTP API 截帧传 MinIO，写 `risk_room_snapshot(room_id, img_url, audit_status)`；处置动作复用现有链路：发 5566 警告 / `LivingRoomRpc.forceClose(roomId)`（复用关播事务方法）/ 封禁主播；
- 敏感词管理页 + 礼物/充值档位配置页。

**点睛清单（每项 1~2 天内的小件，按观感收益排序）**：
进场特效（贵族/粉丝团，lottie 基建现成）→ 直播间分享卡片 → 深色模式（Element Plus 原生，半天）→ 首页骨架屏+图片懒加载 → 视频详情页"相关推荐"（同标签，一条 SQL）→ 主播开播数据看板（场观/峰值/时长/收益）。

---

## 五、既定约束（每批都要遵守）

- **JDK17 写死**：`/d/enviroment/javaenviroment/jdk17`（start-all.sh 已写死；用户 JAVA_HOME 保持 jdk8 供公司用，绝不改）。
- **端口步进 5**：新 Dubbo/HTTP 端口按 `docs/ports.md` 规则取号（下一步 Dubbo 从 30070 起，HTTP 从 38105 起若需新增），**改完必同步 ports.md**。
- **接口链完整度**：新 RPC 字段必须 clean install interface → clean package consumer，防 fatjar 内嵌旧 SNAPSHOT 静默丢字段。
- **启动方式**：一律 `scripts/start-all.sh`（波次已排好）；测试前后用 `scripts/stop-all.ps1` 清场；Nacos 同时重启 15 服务会 gRPC 超时，错峰重启。
- **提交**：每批一个 commit，**只 commit 不 push**（用户自己推）。
- **验证铁律**：每批 E2E 过了才算完（"你测试啊，你别坑我"）——能用浏览器自动化就用真实浏览器走一遍。

## 六、回望记录（随进度填写）

| 批次 | 状态 | 实际 commit | 偏差/坑 |
|---|---|---|---|
| 零 | ✅ 已完成 | 见 2026-09-13 提交 | ① start-all.sh `set -u` 未绑定变量 bug（已修）；② 全量重启时 Nacos 过载，5 个服务注册失败退出 + user-provider 成"僵尸"需手杀重启，错峰重启后恢复；③ E2E 登录撞上验证码 60s TTL 冷却（sendLoginCode 限频），等 60s 再跑即可 |
| 一 | ✅ 已完成 | 见 2026-09-13 提交 | ① E2E 弹幕全丢排查 3 小时，根因是 JDK17+中文 Windows 默认 GBK 编码坑（troubleshooting §19），顺带修复了潜伏已久的弹幕中文乱码；② 开播接口有频控，E2E 脚本需带重试；③ WS 测试连上后必须先发 1001 登录包且等 ≥3s（进房走 MQ 异步），appId 用 10001 非 URL 里的 1001 |
| 二 | 未开始 | — | — |
| 三 | 未开始 | — | — |
| 四 | 未开始 | — | — |
| 五 | 未开始 | — | — |
