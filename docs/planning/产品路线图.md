# 旗鱼直播 · 产品功能完善路线图（对标 B 站 / 抖音）

> 生成日期：2026-09-13
> 基于对当前代码库的完整浏览（15 个 Dubbo 服务 + web_live 前端 + docs 全部文档），
> 盘点现状、对标成熟产品，梳理出「让它更接近生产、更接近哔哩哔哩 / 抖音」的功能路线图。

---

## 一、现状盘点（已有什么）

### 已实现 ✅

| 域 | 已有功能 |
|---|---|
| 账号 | 手机验证码登录注册、Token 鉴权网关、修改昵称/头像 |
| 直播 | 开播/关播、房间类型（娱乐/游戏/赛事/带货）、封面、在线观众数、PK、主播配置 |
| 流媒体 | SRS WebRTC 推流 + FLV/HLS 拉流、播放记录、开播/关播回调 |
| IM | Netty 自研 IM（WebSocket+TCP）、弹幕、心跳、ACK 去重、延迟关播检查（进行中） |
| 礼物 | 礼物配置、送礼（lottie 特效）、余额扣费、余额不足引导充值 |
| 红包 | 红包雨创建/抢/延迟结算退还 |
| 带货 | 小黄车（商品/SKU/上架管理）、购物车 7 个接口、结算 |
| 支付 | 充值模板、模拟支付回调、对账中心（T+1 双向核对） |
| 视频 | 上传/发布/列表/点赞/收藏/评论/分享、视频广场 |
| 部署 | Docker Compose 全量 23 服务、Nacos 配置中心、文档齐全 |

### 明显的短板 ❌（对标 B 站/抖音逐条对照）

1. **支付是纯模拟的**——下单后直接回调本地 mock URL，没有真实支付渠道。（不需要对接支付渠道）
2. **没有审核/合规体系**——视频表里有"审核中"状态字段，但没有任何审核功能；弹幕/直播内容无风控。
3. **没有运营后台**——ReconPage（对账）是唯一的"管理页面"，没有内容管理、用户管理、礼物/商品配置后台。
4. **没有推荐/发现体系**——首页是纯列表，没有个性化推荐、分区、排行榜、搜索。
5. **没有关系链**——关注/粉丝/黑名单/私信完全缺失，这是 B 站和抖音的核心底座。
6. **没有用户个人主页**——无主页、无动态、无成长等级体系。
7. **移动端缺失**——只有 Web SPA，而抖音/快手本质是移动优先产品。（移动端不需要做）
8. **可观测性与生产化基建薄弱**——无监控告警、无链路追踪、无压测数据。

---

## 二、功能路线图（按优先级分层）

> 每一项标注：**建议实现的模块位置** 和 **对标产品的参照**，尽量复用现有架构（新增 interface/provider 或在现有 provider 内扩展）。

---

### P0 · 补齐"直播平台"的底盘（生产化必需）

#### 1. 内容安全与合规（没有它不能叫"生产"）

- **敏感词过滤服务**：新建 `qiyu-live-risk-provider`（或挂在 msg-provider 内），基于 DFA/AC 自动机 + 词库热更新（Redis 发布词库变更）。接入点：
  - 弹幕发送链路（im-core-server 收到消息后先过词库再广播）
  - 昵称/头像/直播间标题/封面上传
  - 视频标题、评论
  - 需求确定（人工审核过了： 用msg-provider 这个方法）
- **直播内容审核**：
  - SRS 截帧回调（现有 SrsCallbackController 扩展）：每 N 秒截一帧推给审核服务（先用开源模型/接口占位，如黄反检测）
  - 违规处置动作：警告弹幕 → 静音 → 掐流（调 stream-provider 的关播接口）→ 封禁
- **账号封禁体系**：user-provider 增加 `user_ban` 表（禁言 N 分钟 / 封号 N 天 / 永封），IM 网关和登录网关统一校验。
- 对标：抖音直播的"秒级掐播"、B 站的弹幕风控分级。

#### 2. 真实支付渠道（可灰度）

- bank-provider 的 PayNotifyController 已具备回调骨架，只需补：
  - 支付宝「电脑网站支付/手机网站支付」SDK 接入（下单 → 跳转 → 异步回调验签 → 幂等入账）
  - 微信 Native 扫码支付
  - 保留现有模拟支付作为 `mock` 渠道开关（Nacos 配置切换），方便本地开发和演示
- 补充：退款流程、支付对账文件下载（现有对账 Job 直接对齐真实账单格式）。

#### 3. 运营后台管理系统（admin）

- 新建独立前端工程 `web_admin`（Vue3 + Element Plus，可复用 web_live 的 axios 封装），后端在 qiyu-live-api 下加 `/admin/*` 路由（独立鉴权 + RBAC）：
  - **用户管理**：查询/封禁/解封、注册趋势
  - **直播管理**：房间列表、强制关播、违规记录
  - **内容审核台**：视频审核队列（通过/驳回，状态字段已预留）、截帧审核
  - **配置管理**：礼物配置、充值档位、红包规则（现在只能改库）
  - **订单/对账**：现有 ReconPage 迁入
- RBAC：`admin_role` / `admin_permission` 两张表 + 网关层权限校验即可，不必引复杂框架。

#### 4. 直播核心体验补强

- **连麦**（对标 B 站连麦、抖音连麦 PK）：SRS 支持 RTC 多人合流；living-provider 扩展 `link_mic` 状态机（邀请/接受/断开），前端双播放器布局。这是直播产品从"看"到"互动"的分水岭。
- **直播间消息等级完善**：进场欢迎（"欢迎 xxx 进入直播间"）、关注主播公告、房间管理员（禁言观众）、直播间公告/主播介绍。
- **录制与回放**：SRS 已支持录制，补 stream-provider 的回放转码 + 房间"精彩时刻/回放"列表，回放可挂到主播主页（对标 B 站直播回放）。
- **主播开播工具**：OBS 推流文档（已有 WebRTC 推流，补 RTMP 地址展示）、开播数据看板（场观、峰值、时长、收益）。

---

### P1 · 对标 B 站/抖音的产品形态（体验跃迁）

#### 5. 关系链：关注 / 粉丝 / 私信（最高价值的功能缺口）

- user-provider 新增 `user_relation` 分表（follower/followee），Dubbo 接口：
  - 关注/取关、关注列表、粉丝列表、共同关注、相互关注标记
- 落地场景：
  - 直播间"关注"按钮 + 关注推送（开播时给粉丝发 MQ → 站内/短信通知，"你关注的主播开播了"——抖音的留存核心）
  - 首页"关注"tab（只显示关注主播的直播间）
  - 主播粉丝团/灯牌（B 站舰长体系的简化版：粉丝等级 = 观看时长+送礼贡献积分）
- **私信（DM）**：复用现有 IM 通道（im-core-server 已是双协议长连接），增加会话表 + 单聊消息类型，天生的架构优势，成本低收益高。

#### 6. 用户中心 / 个人主页

- 前端新增 `ProfilePage`：
  - 基础资料、关注/粉丝数、获赞数
  - 我的视频 / 我发布的作品（video-provider 已有接口）
  - 观看历史、我的收藏、我的点赞（iteration-2026-09-13 已规划，落地即可）
  - 我的钱包入口（已有 WalletPage）、我的订单（小黄车购买记录）
- 用户成长体系：`user_level`（经验值 = 观看+互动+送礼），弹幕昵称旁显示等级牌（B 站标志性的 UI 元素，前端成本低、辨识度高）。

#### 7. 发现与分发（让内容"被找到"）

- **搜索**：Elasticsearch 集群（或先用 MySQL 全文索引过渡）建 `search-provider`，索引直播间标题、视频标题/标签、用户昵称；前端全局搜索框 + 结果三分栏（直播/视频/用户）。
- **排行榜**：Redis ZSet 实现小时榜/日榜/周榜——礼物榜（打赏主播榜）、人气榜（房间热度）、新人榜；直播间右侧挂榜单面板（斗鱼/B 站标配）。
- **分区/频道**：房间类型字段已有（娱乐/游戏/赛事/带货），把它升级为运营可配置的"分区体系"（多级分区 + 分区封面），首页按分区 tab 浏览。
- **推荐系统（远期，可分阶段）**：
  - 阶段一：规则推荐（热度分 = 在线人数 + 近期送礼 + 互动率的加权，Redis 缓存榜单）
  - 阶段二：协同过滤（用户行为日志入 MQ → 离线训练 → 召回排序，挂 ES/向量库）

#### 8. 视频模块向抖音形态靠拢

- **沉浸式 Feed 流**：VideoSquarePage 改为竖屏全屏上下滑动（`IntersectionObserver` + 预加载下一个视频），自动播放/静音切换——这是"抖音感"的最直接来源。
- **视频互动强化**：弹幕式评论（评论飘过画面）、@提及、话题标签聚合页（tag 字段已有，缺聚合页）、合拍/Duet（远期）。
- **创作者中心**：数据看板（播放/点赞/完播率）、视频管理（删除/改标签）、收益（如果有激励计划）。
- **视频审核流**：发布后进入"审核中"（字段已有）→ 机器先审 → 后台人工复审 → 通过后进推荐池。

---

### P2 · 商业化深化与生态

#### 9. 直播商业化矩阵

- **付费直播间**（对标 B 站付费直播）：房间门票/按分钟计费，进场前鉴权（living-provider 房间属性 + IM 网关白名单校验）。
- **直播间玩法扩展**：
  - 抽奖（口令抽奖、整点抽奖）
  - 投票/答题互动（主播发起，观众弹幕或面板参与）
  - 连麦 PK 竞技化（已有 PK 基础，补 PK 值来源：送礼折算 + 观众点赞，倒计时结算胜负 + 惩罚动画）
- **分成与结算**（todo 中标注"远期"的分账）：主播收益账户（虚拟收入 → 兑换）、平台抽成比例配置、提现申请 + 后台审批。bank-provider 已有账户/对账底子，扩展即可。

#### 10. 电商带货闭环

- 现状是"主播上架 → 观众买"，补齐：
  - **订单履约**：模拟物流（发货/收货/退货退款状态机）
  - **商品审核**：后台审核主播上架的商品（防违规商品）
  - **带货分成**：订单关联主播 → 分账入主播收益账户（和 9.3 共用结算底座）
  - 评价体系：SKU 订单评价 + 商品评分

#### 11. 会员/成长商业化

- 大航海/舰队体系（B 站）：月度订阅 → 专属勋章、弹幕颜色、进场特效、优先发言
- 直播间贵族体系（斗鱼/虎牙）：连续充值身份、专属座驾进场特效（lottie 已有播放基建，新增素材即可）

---

### P3 · 工程化与生产就绪（不直接可见但决定生死）

#### 12. 可观测性体系

- **监控**：Prometheus + Grafana，每个 provider 暴露 Micrometer 指标（JVM、Dubbo RPC QPS/RT、MQ 堆积、IM 在线连接数——im-core-server 的连接数是最核心的业务指标）
- **告警**：Alertmanager → 钉钉/飞书机器人（服务宕机、MQ 堆积、支付回调失败率、对账差错新增）
- **链路追踪**：SkyWalking 或 OpenTelemetry，覆盖 HTTP 网关 → Dubbo → MQ → DB 全链路（现有彩色日志保留，作开发期排查）
- **日志聚合**：Loki 或 ELK，按 traceId 串联

#### 13. 性能与容量

- **弹幕压力治理**（对标 B 站直播间百万人在线的弹幕优化）：
  - 弹幕合并/降级策略：房间人数 > 阈值时，按比例抽样广播 + 弹幕聚合渲染（前端合帧）
  - 房间消息分级：礼物/进场/系统消息与普通弹幕分优先级通道
- **压测**：JMeter/Gatling 压弹幕链路（im-core-server 目标连接数、广播 RT），拿到自己的容量基线写进文档
- **热点房间架构**：单房间在线人数的广播扇出瓶颈评估（im-router 现有按连接路由，超热房间考虑专用 channel + 分层广播）

#### 14. 安全加固

- 接口层：网关限流（Sentinel，Spring Cloud Alibaba 自带生态）、防刷（验证码发送频率、登录失败锁定）
- 资源安全：MinIO 上传的类型/大小校验、图片鉴黄、防盗链（播放 URL 签名 + 时效）
- 资金安全：余额操作的分布式锁审计（已有 Redis 锁）、关键操作二次确认、对账告警闭环

#### 15. 多端覆盖（远期愿景）

- **移动端**：优先 H5 响应式改造（现有 Vue3 页面适配移动布局，成本低），再用 uni-app/Taro 复用接口出小程序（微信小程序是国内直播分发的关键入口）
- **客户端开播**：Electron 桌面开播端（推流 + 弹幕管理 + 商品管理一体的主播工作台）

---

## 三、建议的实施顺序（结合现状的现实路径）

```text
第一批（让项目"能上生产"）：
  敏感词/风控 → 运营后台 admin → 真实支付(灰度) → 账号封禁
  理由：没有合规和后台，任何真实运营都不可能发生。

第二批（让产品"像个平台"）：
  关注/粉丝 → 个人主页 + 用户等级 → 开播推送 → 搜索 → 排行榜 → 私信
  理由：关系链是留存之本，且能最大程度复用现有 IM/MQ/Redis 基建。

第三批（让产品"像 B 站/抖音"）：
  沉浸式视频 Feed → 连麦 → 付费直播 → 直播玩法(抽奖/投票/PK 竞技化) → 推荐系统
  理由：体验跃迁项，依赖第二批的关系链数据。

并行推进（工程线）：
  Prometheus 监控 + 告警 → 弹幕压测 → 限流/防刷 → 日志链路
```

## 四、低成本高感知的"点睛"清单（每项 1~2 天工作量）

这些小功能单独看不大，但对"产品完成度"的观感提升最明显：

- [ ] 弹幕等级牌 + 粉丝灯牌（前端徽章渲染，数据从等级体系来）
- [ ] 进场特效：贵族/粉丝团用户进场全屏动效（lottie 基建现成）
- [ ] 直播间"本场贡献榜" Top10 面板（Redis ZSet，直播间右侧）
- [ ] 主播开播推送：站内通知小红点 + 关注页 tab
- [ ] 视频完播率上报 + 详情页"相关推荐"（同标签视频，一条 SQL 就能先做）
- [ ] 直播间分享卡片（分享链接带房间参数 + 落地页 OG 标签）
- [ ] 系统通知中心（消息 Bell 角标：关注/点赞/评论/回复/系统）
- [ ] 深色模式（Element Plus 原生支持，纯前端半天）
- [ ] 首页骨架屏 + 图片懒加载（观感顺滑度）

---

## 五、总结

当前项目的技术底盘（微服务拆分、IM 长连接、SRS 流媒体、MQ 异步化、分库分表、Docker 化）已经是**准生产架构**，短板集中在**产品层**：合规风控、运营后台、关系链、分发推荐这四块。

对标结论：
- **像 B 站**：补"关注 + 弹幕等级 + 分区 + 视频 PGC 感 + 审核流"
- **像抖音**：补"沉浸式 Feed + 推荐分发 + 强私信/通知 + 移动优先"
- **像能运营的直播平台（斗鱼/虎牙）**：补"风控掐播 + 运营后台 + 礼物榜/贵族体系 + 主播收益分账"

建议按本文第三节的顺序推进：先用 P0 把"能不能上生产"的硬门槛过掉，再用 P1 关系链和分发把产品做"活"。

---

# 六、技术实现方案（详细设计 · 供审核）

> 以下方案均基于当前代码库的真实链路编写（2026-09-13 审阅版），关键落点引用了实际类名/Topic/表名。
> 现有链路速查（后续方案反复引用）：
>
> - **弹幕链路**：前端 `IMConnection.sendChat`（code=1003, bizCode=5555）→ Netty `WsSharkHandler` 鉴权 → `BizImMsgHandler` 发 MQ `qiyu_live_im_biz_msg_topic` → msg-provider `ImMsgConsumer`/`SingleMessageHandlerImpl` 查房间用户 → im-router `ImRouterServiceImpl.batchSendMsg` 按绑定 ip 分组 → im-core-server 写回 Channel
> - **IM 频道 code**（`ImMsgCodeEnum`）：1001 登录 / 1002 登出 / 1003 业务 / 1004 心跳 / 1005 ACK；**业务 bizCode**（`ImMsgBizCodeEnum`）：5555 聊天、5556/5557 送礼成功/失败、5558/5559 PK、5560/5561 红包、5562 订单、5563 推流状态、5564 回放、5565 关播 → **新功能从 5566 起分配**
> - **开播/关播**：SRS `on_publish`/`on_unpublish` 回调（`SrsCallbackController`，header `X-Srs-Secret` 鉴权）→ 改 `t_living_room.stream_status` + 广播 5563；关播延迟检查走 30s 延迟 MQ `living_room_close_check`（`LivingRoomCloseCheckConsumer` 三条件判定）
> - **余额**：Redis key `buildUserBalance` 缓存 + `setIfAbsent` 锁 + 异步事务写 `t_qiyu_currency_account`/`t_qiyu_currency_trade`
> - **网关**：`AccountCheckFilter`（token 从 cookie `qytk` 或 header）→ 下游 header `qiyu_gh_user_id` → `QiyuRequestContext.getUserId()`；白名单在 nacos `qiyu.gateway.notCheckUrlList`
> - **表分片**：user 域三表按 `user_id % 100` 分 100 张；新表设计需考虑分片键

## 6.1 内容风控与合规（P0-1）

### 6.1.1 敏感词过滤服务

**模块**：新建 `qiyu-live-risk-interface` + `qiyu-live-risk-provider`（Dubbo 端口沿用 30012），或第一阶段先以包形式挂在 msg-provider 内（更快，后续再拆）。推荐**先挂 msg-provider，接口按独立 RPC 的形状写**（`IRiskRpc` 放 common-interface），后续拆包零改动。

**词库与算法**：
- 表 `risk_sensitive_word`（`id, word, level(1拦截 2替换* 3记录), scene(1弹幕 2昵称 3视频标题 4评论 5房间名), status, create_time`），运营后台维护。
- 服务启动时加载词库构建 **DFA 自动机**（首尾字符 + Map 子节点，或直接引 `houbb/sensitive-word` 单 jar 无额外依赖）；`risk_word_version` Redis key 记录版本，后台改词库后 bump 版本 + 发 MQ `risk_word_refresh`，provider 收到后重建自动机（无需重启）。
- RPC 接口：
  ```java
  RiskCheckRespDTO checkText(RiskCheckReqDTO{ text, scene, userId });
  // 返回: pass / replaced(替换后文本) / blocked + 命中词列表(记录审计)
  ```

**接入点（4 处，均为一行调用成本）**：
1. **弹幕**：`msg-provider/SingleMessageHandlerImpl.onMsgReceive` 解析出 content 后先 `checkText(scene=1)`——pass 放行、replace 广播替换文本、blocked 改发 bizCode=5566（新）单发给发送者"消息包含敏感内容已被拦截"。
2. **昵称/房间名**：user-provider 修改资料处、living-provider 开播处 `checkText(scene=2/5)`，blocked 直接返回业务错误。
3. **视频标题/评论**：video-provider publish 与 comment 接口 `checkText(scene=3/4)`。
4. **弹幕频率风控**：`SingleMessageHandlerImpl` 前加 Redis 滑动窗口计数（`risk:freq:{userId}` 5s 内 >10 条 → 丢弃 + 计数），防刷屏。

### 6.1.2 直播截帧审核

- SRS 支持 `on_hls` / http 静态截图，最简方案：**stream-provider 加定时任务**，对 `stream_status=1` 的房间每 30s 调 SRS HTTP API 截帧（或 ffmpeg 拉流截帧）上传 MinIO，写表 `risk_room_snapshot(room_id, img_url, create_time, audit_status)`。
- 接入审核：第一阶段截图仅落库 + admin 后台人工审阅（6.3 的审核台看图处置）；第二阶段接审核 API（如内容安全云服务）自动打标。
- **处置动作**（复用现有能力，不需要新链路）：后台对房间执行 → ① 发 5566 警告给主播 ② `LivingRoomRpc` 新增 `forceClose(roomId)`（内部直接调 `livingRoomTxService.closeLiving` + `stopStream`，与关播延迟检查复用同一事务方法）③ user-provider 封禁主播账号。

### 6.1.3 账号封禁 / 禁言

- 表（user 库）：
  ```sql
  t_user_ban(id, user_id, type(1禁言 2封号), reason, start_time, end_time, operator, status)
  ```
- **禁言**校验点：`SingleMessageHandlerImpl` 发弹幕前查 Redis `ban:mute:{userId}`（登录/进房时预热，TTL=解封时间）；**封号**校验点：`AccountCheckFilter` token 校验通过后加一步 Redis `ban:account:{userId}` 判断，命中直接 403。
- 处置接口 `IUserRpc.banUser/unban` 写 DB + 写 Redis + 发 MQ 删本地缓存。所有新增 URL 中只有登录和支付回调在白名单，**admin 接口全部走网关鉴权**（见 6.3）。

## 6.2 真实支付渠道（P0-2）

**前置修复（现状缺陷，必须先做）**：`PayOrderServiceImpl.payNotify` 目前**没有幂等判断**——重复回调会重复入账。修复：方法开头查单后 `if (order.getStatus() == PAYED) return ok;`，并在 `UPDATE t_pay_order SET status=2 WHERE order_id=? AND status=1` 的 affected rows 上做条件更新兜底（防并发双回调）。

**接入方案（以支付宝「电脑网站支付」为例，微信 Native 同构）**：
1. bank-provider 引 `alipay-sdk-java`（官方单 jar），配置 `qiyu.alipay.{app-id, private-key, alipay-public-key, gateway-url, notify-url}` 放 Nacos。
2. `BankServiceImpl.payProduct` 改造：按 `pay_channel` 分支——
   - `mock`（保留，Nacos 开关 `qiyu.pay.mock-enabled`，默认本地开发 true）：走现有 RestTemplate 自回调逻辑不动；
   - `alipay`：调 `AlipayTradePagePayRequest` 生成支付表单 HTML 返回给前端，前端 `window.open` 跳转；
   - `wxpay`：Native 下单返回二维码 url，前端弹窗展示 + 轮询订单状态（新增 GET `/bank/order/status?orderId=`）。
3. 回调：`PayNotifyController` 新增 `/alipayNotify` 端点 → **验签**（`AlipaySignature.rsaCheckV1`）→ 金额比对（回调金额 == `t_pay_product.price`）→ 转 DTO 走现有 `IPayOrderRpc.payNotify`（幂等修复后天然安全）。支付宝回调需返回 `success` 字符串、微信需返回 XML/JSON——注意 bank-api 的 context-path 与对外可达性（生产需公网域名 + HTTPS）。
4. 对账：`ReconciliationJob` 增加"下载支付宝/微信对账单文件 → 与 `t_pay_order` 双向核对"，核对框架（差错表、trigger 接口）已有，只换数据源。

**表改动**：`t_pay_order` 加 `notify_raw`（回调原文 JSON，留证）、`trade_no`（渠道流水号，唯一索引）。

## 6.3 运营后台 admin（P0-3）

**后端**：
- qiyu-live-api 下新增 `controller/admin/*` + `/admin/**` 路由；**鉴权不走现有用户 token**，独立一套：表 `admin_account(id, username, password(bcrypt), role)`、`admin_login_log`；登录发 admin token（Redis key `admin:token:{token}` 存 adminId+role，TTL 2h）。
- 网关放行 `/live/api/admin/login`（加入 `notCheckUrlList`），其余 admin URL 在 `AccountCheckFilter` 加分支：路径以 `/admin` 开头时改查 `admin:token:*`，校验通过后下发 header `qiyu_gh_admin_id`；qiyu-live-api 加 `AdminInfoInterceptor`（仿 `QiyuUserInfoInterceptor`）+ `@RequireRole` 注解做 RBAC（角色枚举就三档：超管/运营/审核员，硬编码即可，不建权限表）。
- admin 服务内聚合 Dubbo 调用各 provider，需要给 provider 新增的方法：
  - user：`pageUser`、`banUser/unban`、`resetPassword`
  - living：`pageRoom(status)`、`forceClose`
  - video：`pageVideoByStatus`、`auditVideo(videoId, pass, reason)`（**注意：`t_video_info.status` 现只有 0 下架/1 上架语义，需扩值**：`0下架 1上线 2审核中 3审核驳回`，publish 时默认写 2，审核通过才置 1——存量数据跑一条 `UPDATE` 迁移）
  - gift/bank：礼物/充值档位 CRUD、订单查询、差错处理确认

**前端**：新建 `web_admin/`（Vite + Vue3 + Element Plus，复制 web_live 的 request.js 改造），路由：登录 / 仪表盘（今日数据卡：新增用户、开播场次、GMV、充值额——四条 COUNT SQL 即可）/ 用户 / 直播间 / 视频审核（队列 + 图片预览 + 通过驳回）/ 截帧审阅 / 礼物配置 / 商品配置 / 订单对账（迁移 ReconPage）/ 敏感词管理。约 10 个列表页，用统一的 ProTable 封装（搜索表单 + 表格 + 分页一个配置对象搞定）。

## 6.4 关注 / 粉丝 / 私信（P1-5）

**表**（user 库，跟随 user_id 分片）：
```sql
t_user_relation(id, user_id, follow_user_id, status(1关注 0取关), create_time,
                UNIQUE KEY uk(user_id, follow_user_id))
-- 新增分表配置进 qiyu-live-user-shardingjdbc.yaml（user_id % 100）
```
粉丝数/关注数：Redis `relation:follow_cnt:{uid}` / `relation:fans_cnt:{uid}` 计数器，DB 定时对账校正（复用对账 Job 思路）。

**RPC（user-provider）**：`follow/unfollow/isFollow/pageFollow/pageFans/countFans/countFollow`。

**开播推送（抖音留存核心）**：
- 主播开播时（`LivingRoomServiceImpl.openLiving` 成功后）发 MQ `user_open_living_push`（新 Topic，定义进 `UserProviderTopicNames`）。
- user-provider 消费：查粉丝列表（分页拉取，10 万粉级可接受）→ 组装 `ImMsgBody(bizCode=5567, data={roomId, anchorName, cover})` → 走**现有 `ImRouterRpc.batchSendMsg` 在线推送**（不在线的自然丢弃，不做离线推送，第一阶段够用）；同时写站内通知表（见 6.7 通知中心）。

**前端**：
- RoomPage 关注按钮（调 follow/unfollow，主播放器下方）+ HomePage 增加「关注」tab（`pageFollow` 关联查 `living/list` 过滤开播中）。
- 新 bizCode 5567 在 `handleIMMessage` 加分支 → 全局 Toast「你关注的主播开播了」+ 点击跳转。

**私信（DM）——复用现有 IM 是本方案最大的架构红利**：
- 新 bizCode **5568**（单聊上行）/ **5569**（单聊下行）。上行时 `BizImMsgHandler` 无需改动（它只是透传 MQ）；在 msg-provider `SingleMessageHandlerImpl` 按 bizCode 路由新增 `DmMessageHandler`：
  1. 风控 `checkText(scene=1)`；2. 写表 `t_user_dm_message(id, from_uid, to_uid, content, status, create_time)`（按 from_uid 分片）；3. 写会话表 `t_user_dm_conversation(owner_uid, peer_uid, last_msg, unread_cnt, update_time)`（双方各一行）；4. 若对方**当前连接绑定的房间与发送者相同或查 Redis `qiyu:live:im:bind:ip:{appId}:{uid}` 存在**（即在线），走 `routerRpc.sendMsg` 单发 5569。
- 前端：全局右上角消息铃铛（通知中心，见 6.7）内含私信会话列表 + 会话窗口（复用 ChatList/ChatInput 组件）。

## 6.5 用户中心 / 等级体系（P1-6）

**表**：
```sql
t_user_profile_ext(user_id PK, follow_cnt, fans_cnt, like_received_cnt, level, exp)
t_user_watch_history(user_id, video_id, watch_time, create_time)   -- iteration-0913 已规划
t_user_notify(id, user_id, type(1系统 2互动 3私信), title, content, jump_url, is_read, create_time)
```

**经验/等级规则（先简单后迭代）**：观看 1 场直播 +10、发弹幕 +1（日上限 20）、送礼 1 金币 +1、发布视频 +50。落地方式：**不新建经验服务**，在对应动作的消费端（`SendGiftConsumer`、`SingleMessageHandlerImpl`、视频 publish）发 MQ `user_exp_change`，user-provider 单点消费累加（Redis `user:exp:{uid}` INCRBY，跨级时回写 DB + 广播 bizCode=5570 等级提升特效到所在房间）。等级映射表配置化（L1~L6，每级所需经验存 Nacos）。

**前端**：`ProfilePage`（路由 `/profile/:userId`，资料 + 关注/粉丝数 + 三 tab：TA 的视频/观看历史(仅自己)/我的收藏）+ `UserCenterPage` 扩展（/user/center 已存在，加"我的订单/我的通知"入口）。弹幕昵称前渲染等级徽章（ChatList 里 `L{level}` 小色块，B 站风格）。

## 6.6 发现与分发（P1-7）

**排行榜（先做，纯 Redis 零新依赖）**：
- gift-provider `SendGiftConsumer` 扣费成功后顺手 `ZINCRBY rank:gift:anchor:{yyyyMMdd} {price} {anchorId}`（主播榜）/ `rank:gift:room:{roomId}`（房间内贡献榜）；TTL 8 天。日榜 = ZREVRANGE，周榜用 7 个日 key 合并（ZUNIONSTORE）。
- 人气榜：`LivingRoomServiceImpl.userOnlineHandler` 时 `ZINCRBY rank:heat:room {1} {roomId}`（去重用已有的房间 Set 判断）。
- 新增 api 接口 `GET /rank/{type}/{period}`；前端 RoomPage 右侧「本场贡献榜 Top10」面板（bizCode=5556 送礼消息到达时前端本地增量刷新，不用轮询）+ HomePage 排行弹层。

**搜索（分两阶段）**：
- 阶段一（1 天）：MySQL `LIKE '%kw%'` 过渡——`t_living_room`(room_name, status=1)、`t_video_info`(title, status=1)、`t_user`(nick_name)。新增 search-provider 或直接 api 层聚合三次 Dubbo 查询。
- 阶段二：Elasticsearch，canal/Logstash 或双写（发布/开播时同步索引），`search-provider` 出统一 `/search?kw=&scene=` 接口。前端全局搜索框 + 结果三分栏。

**分区/频道**：`t_living_room.type`（1 普通 2 PK）扩为 `category_id` 外键 → 新表 `t_living_category(id, name, icon, sort, status)` 运营后台配置；HomePage 的分区 tab 动态拉取。存量 type 数据迁移脚本映射（娱乐/游戏/赛事/带货是 openLiving 的下拉选项，映射成 4 个分类 + PK）。

## 6.7 通知中心（P1 点睛项）

- 后端只有一张表（6.5 的 `t_user_notify`）+ 一个 api：`GET /notify/list`、`POST /notify/read`。写入口分散在各动作（关注、点赞、评论、开播推送、系统公告），统一封装 user-provider 的 `NotifyService.send(userId, type, ...)` 内部调 RPC 或发 MQ。
- 前端：全局 Header 铃铛组件（轮询未读数 60s 一次，或登录后由 IM 通道推 bizCode=5571 未读数变更）→ 下拉通知列表 → 点击 `jump_url` 跳转。私信会话也挂在这下面。

## 6.8 沉浸式视频 Feed（P1-8，纯前端 + 少量后端）

- **布局**：`VideoSquarePage` 重构为竖屏全屏容器（`height: 100vh; overflow-y: scroll; scroll-snap-type: y mandatory`），每个视频卡片 `scroll-snap-align: start` + `IntersectionObserver`（threshold 0.6）判断当前可见项 → 可见项 `video.play()`、其余 `pause()` 并释放（最多保留前后 2 个实例，防内存）。
- **预加载**：可见项变更时对下一个视频 `video.preload='auto'` + 封面提前请求。
- **后端配合**：`GET /video/feed?lastId=&size=10`（游标分页，按热度分 `play_count*0.4+like_count*0.3+create_time` 排序，SQL 一条可做）；**完播率上报** `POST /video/playReport {videoId, duration, watched}` 写 `t_video_play_log`，为后续推荐积累数据。
- **互动**：右侧悬浮按钮列（点赞/评论/收藏/分享，数据接口全部已有）+ 双击点赞动画；评论改为底部抽屉（现有评论接口复用）。

## 6.9 连麦 & PK 竞技化（P1/P2）

- **连麦**：SRS 原生支持 WebRTC 多路推流。方案：被连麦观众调用 stream-provider 新接口 `createGuestPushUrl(roomId, guestUserId)` 生成第二路 streamKey（格式 `live_{md5(roomId_guestId_secret)}`），前端第二路 `RTCPeerConnection` 推流（**复用 RoomPage 现有开播推流代码**，抽成 composable `useWebRTCPublish`）；观众端用 SRS 的合流能力（RTC 混流 `mix_correct` 配置）或前端双播放器并排。living-provider 加 `t_living_linkmic(room_id, guest_user_id, status, start_time)` 状态机 + bizCode 5572 连麦邀请/接受/挂断信令（走现有 MQ 广播链路）。
- **PK 竞技化**：现有 PK 已有进度条（5558 + Redis Lua）。补：PK 值来源登记（送礼折算已在、加观众点赞接口）、倒计时结算（复用 **RocketMQ 延迟消息**模式，与红包结算/关播检查同构）、胜负结果 bizCode=5573 广播 + 前端惩罚动画（lottie）。

## 6.10 付费直播间（P2）

- `t_living_room` 扩列 `pay_type(0免费 1门票) / ticket_price`；开播时主播设置。
- 鉴权点：观众进房接口 `joinRoom` 校验 Redis `room:ticket:{roomId}:{userId}`（购票标记），未购票返回特定错误码 → 前端弹购票窗 → 走 bank 域 `payProduct`（product type=ROOM_TICKET）→ `payNotify` 入账后写购票标记 + 单发 IM 允许进场。
- 弹幕广播放行：`SingleMessageHandlerImpl` 的房间用户列表来自 Redis Set（进房时已校验），无需二次鉴权；**但播放地址要防蹭**：`playUrl` 接口同样校验购票标记。

## 6.11 可观测性（P3，工程线）

- **Metrics**：所有 provider/api 的 `pom.xml` 加 `spring-boot-actuator` + `micrometer-registry-prometheus`（framework web-starter 统一加最省事），暴露 `/actuator/prometheus`；关键业务指标手动埋点：`im_online_connections`（im-core-server channel 计数）、`mq_lag`（RocketMQ exporter）、`dubbo_server_seconds`（Dubbo 自带 filter）。
- **Grafana**：docker-compose-full.yml 加 prometheus + grafana 两个服务 + 预置 dashboard JSON（JVM、Dubbo、MQ、业务四块面板）。
- **告警**：Alertmanager → 飞书/钉钉 webhook，规则：服务 down、MQ 堆积 > 1w、支付回调失败、对账差错新增、CPU/内存。
- **链路追踪**：SkyWalking agent（javaagent 挂 docker 镜像 ENTRYPOINT，无代码侵入），覆盖 HTTP→Dubbo→MQ。
- **弹幕压测**：写 `scripts/im_benchmark.mjs`（Node WebSocket 并发客户端，参考现有 scripts/refresh_no_close_test.mjs 的写法），目标：拿到单机 im-core-server 在 1w/5w 连接下的广播 RT 基线，写入 docs。

## 6.12 顺手要修的现状问题（审阅重点）

审阅本方案时请一并确认这几个代码现状问题，都已在上文方案中纳入：

1. `PayOrderServiceImpl.payNotify` **无幂等判断**，重复回调重复入账（6.2 前置修复）。
2. `t_video_info.status` 只有 0/1，**无审核语义**（6.3 扩值迁移）。
3. 网关白名单只有 2 个 URL，admin/支付回调需新增放行条目并区分鉴权（6.3）。
4. `MessageDTO.senderAvtar` 拼写错误（前后端一致所以能跑），建议新消息类型一律用新 DTO，存量不动。
5. Redis 分布式锁均为手写 `setIfAbsent`，bank 扣款锁失败 sleep 递归重试有雪崩风险，建议 framework redis-starter 加通用 `@DistributedLock` 注解（Redisson 单依赖）。
6. `SingleMessageHandlerImpl` 每条弹幕 SCAN 全房间用户 Set，热房万人时是瓶颈（6.11 压测项，预案：房间成员改本地内存布隆 + 心跳存活表）。
7. `VideoApiServiceImpl.uploadVideo` 用 `file.getBytes()` 将最多 300MB 文件整个读入堆内存，应改 `file.getInputStream()` 流式写入（6.13 顺带修复）。

## 6.13 视频异步轻转码（FFmpeg 处理链）

> 背景：当前上传是原始字节流直写 MinIO（`VideoApiServiceImpl.uploadVideo`），无任何处理。
> 造成 4 个真实问题：① 手机 `.mov`（HEVC 编码）在 Windows Chrome/Edge 黑屏（无解码器）；
> ② mp4 moov atom 在文件尾导致无法拖动进度条、须下完才能播；③ 原始 4K 码率 20~50Mbps 带宽浪费；
> ④ 封面靠手传，不传则 feed 空白。
> **决策：做轻量异步转码（约 1~2 天，复用现有 MQ 消费者模式），不做多码率梯队/HLS 切片/分布式 worker（记入 P3 远期）。**

### 方案

- **触发点**：`VideoApiServiceImpl.publish` 成功后发 MQ `video_transcode`（新 Topic，定义进 common-interface 仿 `GiftProviderTopicNames`），消息体 `{videoId, userId}`。
- **消费者**：video-provider 新增 `VideoTranscodeConsumer`（仿 `SendGiftConsumer` 手写 DefaultMQPushConsumer 模式），流程：
  1. **幂等**：Redis `setIfAbsent(video:transcode:{videoId}, 5min)`（与送礼消费同款）。
  2. **探测**：`ffprobe -v quiet -print_format json -show_format -show_streams <file>` 拿真实编码/分辨率/时长——顺带修正前端上报不准的 `duration`。
  3. **转码决策**（按探测结果二选一）：
     - 已是 H.264+AAC 的 mp4 → **秒级 remux**：`ffmpeg -i in.mp4 -c copy -movflags +faststart out.mp4`（只把 moov atom 重排到文件头，不重编码，几秒完成，修复拖动/秒开）
     - 其他（HEVC/webm/mov 等）→ **重编码**：`ffmpeg -i in -c:v libx264 -preset veryfast -crf 26 -vf scale=-2:720 -c:a aac -movflags +faststart out.mp4`（修兼容 + 统一 720p 控带宽）
  4. **抽封面**：`ffmpeg -ss 1 -i out.mp4 -vframes 1 -q:v 3 cover.jpg`（用户已传封面则跳过）。
  5. **回写**：产物传 MinIO `videos/transcoded/...`（**原文件保留**，便于未来重转/多码率），更新 `t_video_info` 的 `video_url/cover_url/duration/size`。
- **状态字段**：`t_video_info` 加 `transcode_status`(0处理中 1完成 2失败)，**列表/详情默认只出 transcode_status=1**；发布接口立即返回不等转码，前端提示"处理中，稍后出现在广场"。
- **失败兜底**：MQ 重试 2 次后置 transcode_status=2（admin 后台可见、可手动重触发）；**失败回退播原文件**（video_url 不动），视频不丢。
- **配置**：`qiyu.ffmpeg.path` 指定 ffmpeg/ffprobe 可执行路径——本机已装 ffmpeg 8.1.2 直接可用；`docker/app.Dockerfile` 加 `apt install -y ffmpeg` 保证容器内可用。临时文件用系统临时目录，转完即删。
- **顺带修复**：`uploadVideo` 的 `file.getBytes()` 改 `file.getInputStream()` 流式写入（消除 300MB 堆内存峰值，见 6.12-7）。
- **前端**：仅发布成功提示语调整；封面/播放地址转码完成后下次拉列表自动生效，无需推送。

### 明确不做的（防止范围蔓延）

- 多码率梯队（360p~1080p 自适应）与 HLS 切片——需播放器改造（hls.js），本项目规模下收益不划算，记入 P3。
- 转码进度百分比推送——三态状态字段够用。
- 分布式转码 worker 池、GPU 加速——单机串行消费即可（veryfast preset 720p 约为实时 5~10 倍速，10 分钟视频约 1~2 分钟转完）。
