# 需求待办清单（Backlog）

> 更新时间：2026-09-12。对照最初的 `todo.md` 整理，已完成项标注状态和对应提交，未完成项按优先级排列。
> 已完成的详细设计见 `docs/iteration-2026-09-12-design.md`。

## 一、状态总览

| # | 需求 | 状态 | 说明 |
|---|------|------|------|
| 1 | SRS 推拉流（WebRTC/FLV/HLS） | ✅ 完成 | commit `13ee4ee`，见 srs-integration-design.md |
| 2 | 直播间布局（左直播/右聊天/底栏） | ✅ 完成 | commit `2f8c35e` |
| 3 | 礼物全屏特效（lottie） | ✅ 完成 | commit `2f8c35e` |
| 4 | 红包雨（发/抢/联动） | ✅ 完成 | commit `2f8c35e` |
| 5 | 充值中心（6 档模板+模拟支付） | ✅ 完成 | commit `2f8c35e`，修复 5 个根因 |
| 6 | 金币余额展示（顶栏+自动刷新） | ✅ 完成 | commit `2f8c35e` |
| 7 | 日志彩色化 | ✅ 完成 | commit `2f8c35e` |
| 8 | 开播设置（自定义房间名+封面上传） | ✅ 完成 | commit `84b4bff` |
| 9 | 带货购物车（观众侧） | ✅ 完成 | Cart/ShopPanel 已上线 |
| 10 | 视频微服务（新 tab、上传、点播、标签） | ✅ 完成 | 见「视频模块」迭代 |
| 11 | 视频互动（点赞/收藏/分享/评论） | ✅ 完成 | 见「视频模块」迭代 |
| 12 | **对账系统（T+1 双向对账）** | ✅ 完成 | 见「对账系统」迭代 |
| 13 | **Docker 全量一键启动** | ✅ 完成 | 见「Docker 全量」迭代（Windows 仍建议 jar + 原生 srs.exe） |
| 14 | 主播发货配置界面 | ✅ 完成 | 见「中小需求批」迭代 |
| 15 | 红包扣除主播金币 | ✅ 完成 | 见「红包扣费」迭代（含延迟自动结算） |
| 16 | 直播间类型扩展+开播时选择类型 | ✅ 完成 | 见「中小需求批」迭代 |
| 17 | 用户个性化设置（改昵称/头像） | ✅ 完成 | 见「中小需求批」迭代 |
| 18 | 分账（视频收益与平台分成） | ⬜ 远期 | 依赖视频模块收益口径，明确放最后 |

## 二、下一批：视频微服务（10 + 11）

### 已拍板的设计决策
- **存储**：直接用本机已跑的 MinIO（9000），视频上传转存后前端 `<video>` 直接播 MP4，**不做转码**（转码集群是另一个量级的工程，对"能看"没必要）。
- **上传链路**：复用刚做好的封面链路模式——`/resource/upload` 扩展或平移到视频模块，走 stream-provider 的 MinIO RPC（后期视频模块自建 MinIO 客户端后迁走）。
- **模块结构**：新建 `qiyu-video-provider` + `qiyu-video-interface`，风格对齐现有模块（Dubbo RPC + Nacos + MyBatis-Plus），网关加路由。
- **前端**：首页顶部加「直播 / 视频」tab 切换；视频页 B站/抖音式卡片流（封面+标题+作者+播放量/点赞数），点进详情页播放；发布页选文件+标题+标签，带上传进度。

### 表设计（qiyu_live_video 库）
- `t_video_info`：视频 id、作者 user_id、标题、描述、object_key/video_url、cover_url、标签 id、播放量、点赞数、状态（审核中/上线/下架）
- `t_video_tag`：标签字典（游戏/音乐/生活/知识…）
- `t_video_user_action`：user_id + video_id + action(赞/藏/踩) 唯一索引，一人一条
- `t_video_comment`：评论（video_id、user_id、content、点赞数、状态）

### 接口清单
- 发布视频（上传 MP4 + 封面 + 元数据）、视频流列表（分页 + 按标签筛选）、视频详情（播放 + 播放量+1）
- 点赞/取消、收藏/取消、分享计数、评论增删查

### 验收
双 tab 互相验证：A 发布视频 → 首页视频 tab 出现卡片 → B 播放、点赞、评论 → 计数正确。

## 三、对账系统（12）✅ 已完成
- 口径：**支付回调订单（t_pay_order，status=2）** vs **金币充值流水（t_qiyu_currency_trade，type=1）**，T+1 定时任务双向核对笔数与金额。
- 差异落 `t_reconciliation_detail` 差错表（差错类型：1订单有流水无 / 2流水有订单无 / 3金额不平），差错查询接口和「对账中心」管理页已上线（首页导航「对账」进入 `/recon`）。
- 实现：bank-provider `ReconciliationServiceImpl`（(userId,金额) 多重集匹配 → 同用户剩余配对判金额不平 → 剩余单边账），`ReconciliationJob` 每天 01:30 核对前一自然日（cron 可配 `qiyu.recon.cron`），`IReconciliationRpc` 支持手动触发指定日期；payNotify 已回填 pay_time 供按日归集。
- 测试：curl + 浏览器 E2E 通过——三类差错数据全部检出并正确展示，真实数据 0 差错。测试遗留 3 条 demo 差错（recon-test-order-1/3 等），清理 SQL：删除 order_id like 'recon-test-%' 的订单、user_id in (20002,20003) 的测试流水，再触发一次对账即可。
- 学习+面试双目的：实现要能讲清楚「为什么会对不平、差错怎么处理」。

## 四、Docker 全量一键启动（13）✅ 已完成

- **镜像**：`docker/app.Dockerfile` 通用 JRE17 镜像（`EXTRA_ARGS` 启动参数覆盖配置），`scripts/build-images.sh` 一键构建 15 个服务镜像（`qiyu-live/<模块>:dev`）。
- **编排**：`docker-compose-full.yml` = 中间件（mysql/redis/nacos/rocketmq/srs/minio，带健康检查）+ 15 个 Java 服务 + 前端 nginx，共 23 个服务；服务地址全部通过环境变量/启动参数覆盖为 compose 服务名，无需改代码。
- **前端**：`web_live/Dockerfile` + `nginx.conf`（SPA 路由 + `/api`→网关、`/minio`→MinIO、`/rtc`→SRS 反代，上游走 Docker DNS）。
- **已验证**：15 个镜像构建成功；video-provider 容器烟雾测试（env 覆盖 MySQL/Redis + EXTRA_ARGS 覆盖 Nacos）启动自检 PASSED 并注册进 Nacos；前端镜像静态/SPA 路由 200。
- **关键坑**：jar 内 `bootstrap.yaml` 的 `spring.cloud.nacos.discovery.*` 优先级高于 OS 环境变量，必须用**启动参数**（EXTRA_ARGS）覆盖 nacos 地址；数据源/Redis/MQ 在 application.yml，环境变量覆盖有效。
- **Windows 注意**：WebRTC 走容器 UDP 依然丢包，直播推拉流继续用原生 srs.exe + 本机 jar（`docker-compose.yml` + `scripts/start-all.ps1`）；全量容器化适用于 Linux 服务器部署。
- 分账（18）为远期需求，依赖视频收益口径，暂不启动。

## 五、中小需求（14~17）✅ 已完成（2026-09-12 晚）

| 需求 | 实现 |
|------|------|
| 红包扣费（15） | 发送时 `consumeForRedPacket` 原子扣主播金币（余额不足 code 10111 拦截），流水类型 2=红包支出；发送后投递 1 分钟延迟 MQ 自动结算，未领完部分按类型 3=红包退还退回主播；顺带修复新用户无账户行导致入账丢失的问题（incr 改 upsert 自动建账）。E2E：`scripts/redpacket_deduct_test.mjs` 全过 |
| 直播间类型（16） | 开播弹窗增加类型选择（娱乐1/游戏2/赛事3/带货4），与首页筛选 tab 同码表；开播 type 落库，列表按类型筛选 |
| 用户设置（17） | `/user/updateProfile`（api UserController）+ `UserProfileDialog`（点顶栏头像打开）：改昵称、换头像（复用 MinIO 上传链路），user-provider 已有缓存双删+MQ 延迟二删 |
| 观众数展示 | `/living/onlineCount`（复用房间用户 Redis set）+ 房间顶栏红点 chip，10 秒轮询 |
| 主播发货配置（14） | `IAnchorShopRpc.updateShopStatus` 上架/下架（幂等）+ api `/gift/sku/list`、`/gift/shop/myList`、`/gift/shop/updateStatus` + 主播端「商品管理」弹窗（勾选即上架，实时进小黄车） |

curl 级 E2E：`scripts/medium_features_test.mjs` 9/9 全过；浏览器 E2E：改昵称生效、开播弹窗类型选择、房间顶栏观众数、商品管理上架/下架全部实测通过。

## 六、明确不做
- 对接真实支付宝/微信（需商户资质）——继续模拟回调。
- 视频转码集群（ffmpeg/HLS 转码）——直接播原始 MP4。
- 手机 App —— 仅 Web。

## 七、迭代节奏
1. ~~体验迭代（礼物/布局/充值/红包/余额/日志）~~ ✅ `2f8c35e`
2. ~~开播设置（名称+封面）~~ ✅ `84b4bff`
3. ~~视频微服务~~ ✅ 完成
4. ~~对账系统~~ ✅ `e0a7e6b`
5. ~~红包扣费~~ ✅ `9098cbc`
6. ~~中小需求批（类型/设置/观众数/商品管理）~~ ✅ `e9c384e`
7. ~~Docker 全量一键启动~~ ✅ 完成
8. 分账 —— 远期，待视频收益口径明确后再启动
