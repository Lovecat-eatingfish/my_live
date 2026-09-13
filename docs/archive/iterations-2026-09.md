# 迭代归档（2026-09）

> 本文件归档 2026 年 9 月两轮迭代的设计文档，需求均已实施完成（对应提交见文内）。
> 当前进行中的需求见 [requirements-backlog.md](../requirements-backlog.md)，下一版规划见 [roadmap-features-2026-09-13.md](../roadmap-features-2026-09-13.md)。

---

# 迭代设计文档 —— 体验完善版（2026-09-12）

> 本期目标：不新增大模块，把「观感与手感」做完整——让直播间的送礼、红包、充值、余额这些核心体验贴合抖音/B站的真实使用习惯，前后端一起落地。
>
> 视频微服务、对账系统、Docker 全量镜像 → 下一期（本期明确不做）。
>
> 本期完成后本地 commit，**由用户自行 push 到 GitHub**。

---

## 0. 本期范围总览

| # | 需求 | 类型 | 主要改动点 |
|---|------|------|-----------|
| 1 | 礼物全屏特效 | 前端为主 + 数据 | GiftPanel、新增 GiftAnimation 组件、礼物配置数据 |
| 2 | 直播间布局优化 | 前端 | RoomPage 三区布局重排 |
| 3 | 日志彩色化 | 后端配置 | 14 个 logback-spring.xml 统一 |
| 4 | 充值中心（简化版） | 前后端 | WalletPage 档位 UI、下单、模拟支付回调 |
| 5 | 红包雨完善联调 | 前后端 | RoomPage 集成 RedPacketRain、主播发红包入口 |
| 6 | 金币余额展示 | 前端为主 | HomePage 顶栏 + RoomPage 顶栏余额 |

---

## 1. 礼物全屏特效

### 现状
- `t_gift_config` 表**已有 `svga_url` 字段**（原设计预留，一直是空的），还有 `cover_img_url`。
- 前端 `public/gift/` 下已有 7 个静态 SVG：rose / heart / car / rocket / crown / lamp / pumpkin。
- GiftPanel 只有点选+发送，送出后聊天区出现一条文字消息，**没有任何动画**。

### 方案
- **动画技术选型：lottie-web**。理由：免费的 lottie JSON 动画资源（LottieFiles 等）远比 SVGA 资源丰富、好找；体积小、纯前端播放。表字段沿用 `svga_url` 存动画资源地址（本期内放 `/gift/anim/xxx.json` 本地路径），不动表结构。
- **降级兜底**：`svga_url` 为空或资源加载失败时，退回「SVG + CSS keyframes」方案（跑车横屏飞过、火箭升空等手写轨迹动画），保证任何礼物都有动效、不白屏。
- **分档播放策略**（贴合抖音观感）：
  - 小礼物（价格 < 100 金币，rose/heart 等）：聊天区连击气泡 + 屏幕左侧漂浮迷你动画，不遮挡直播画面；
  - 大礼物（≥ 100 金币，car/rocket/crown 等）：**全屏动画**（居中播放 1.5~3s，带「XX 送出了 跑车」横幅），期间可被更新的礼物打断替换。
- **触发链路**：送礼成功后走现有 IM 5556（送礼成功）广播 → 房间内所有观众端收到消息里带 giftId → 前端根据 giftId 查礼物配置里的 `svga_url` 播放。多人同时送礼排队依次播（简单队列）。
- **数据准备**：`t_gift_config` 现有记录的 `cover_img_url`/`svga_url` 统一刷一遍，指向 `public/gift/` 下 SVG 与 `public/gift/anim/` 下动画；缺的礼物补插几条（价格分档：1/10/99/520/1314 金币档），动画 JSON 去网上找免费资源下载入库，找不到的用 CSS 动画兜底。

### 改动点
- 前端：新增 `components/GiftAnimation.vue`（lottie 播放 + 队列 + 全屏/迷你两种模式）；GiftPanel 发送后不需要额外调用，交给 IM 广播驱动；RoomPage 挂载该组件。
- 依赖：`lottie-web`（pnpm 添加）。
- 后端：无代码改动（5556 消息体需确认带 giftId/giftName，缺则补）。
- 数据：`sql/gift_anim_update.sql` 刷配置。

### 验收
观众端和主播端同时能看到：A 送跑车 → 双方屏幕全屏跑车动画 + 横幅；送玫瑰 → 左下角漂浮小动画。

---

## 2. 直播间布局优化（抖音式三区）

### 现状
RoomPage 目前三块内容都有（视频区 / 聊天 / GiftPanel），但排布是流式的，视频区被推流面板挤压，聊天区和礼物区位置随内容浮动，整体是「demo 感」。

### 方案（目标布局）
```
┌────────────────────────────────────────────────┐
│ 顶栏：← 返回 | 主播头像 昵称 关注 | 👰 余额 | 更多 │
├───────────────────────────────┬────────────────┤
│                               │  在线观众 xxx   │
│        直播画面 (16:9)         │  ┌──────────┐  │
│   （主播时：推流面板/OBS指引）   │  │ 聊天滚动  │  │
│                               │  │ (含系统   │  │
│                               │  │  礼物消息)│  │
│                               │  └──────────┘  │
│                               │  输入框 [发送]  │
├───────────────────────────────┴────────────────┤
│ 底栏：💬聊天(聚焦右栏) | 🎁礼物 | 🧧红包(主播) | 🛒带货 │
└────────────────────────────────────────────────┘
```
- 左侧视频区 `flex:1` 固定 16:9 居中，黑底；右侧聊天栏固定 320px，半透明深色浮层（抖音风格）。
- 底栏只放功能按钮，点「礼物」在底栏上方弹出 GiftPanel（现在常驻改为抽屉式，不挡画面）。
- 顶栏加观众数、余额（见 §6）。
- 观众端与主播端同一布局，仅视频区内容不同（观众=拉流，主播=推流面板/预览）。

### 改动点
仅 `RoomPage.vue`（+ 少量子组件样式），无后端改动。

### 验收
浏览器 1280×800 下三区不重叠不溢出；礼物面板弹出时不挤压视频区。

---

## 3. 日志彩色化

### 现状
14 个模块各有一份 logback-spring.xml，CONSOLE 的 pattern 是 `[%d -%5p] %-40.40logger{39} :%msg%n`，无颜色，控制台全白。

### 方案
- 各模块 logback-spring.xml 的 **CONSOLE appender** 换用带 `%highlight` 的 pattern（error 红 / warn 黄 / info 绿 / debug 蓝，thread 与 logger 暗青色），文件 appender 保持无色纯文本（写文件不能带 ANSI 码）。
- 统一 pattern（控制台）：
  `%highlight(%d{HH:mm:ss.SSS} %5p) [%15.15t] %cyan(%-40.40logger{39}) : %msg%n`
- 逐份修改而非抽公共文件：logback include 跨模块引入容易踩类加载顺序，且只有一处 pattern 改动，复制维护成本可接受。
- 控制台需支持 ANSI：IDEA / Windows Terminal / Git Bash 均支持；若在老 cmd 里跑出现乱码，脚本启动统一加 `JANSI` 可后补（本期不做）。

### 验收
重启任一服务，控制台 WARN 黄色、ERROR 红色；日志文件打开无乱码。

---

## 4. 充值中心（简化版）

### 现状
- 表链路完整：`t_pay_product`（产品表，价格单位分）→ `t_pay_order`（订单）→ `t_qiyu_currency_account`（余额）→ `t_qiyu_currency_trade`（流水）。
- bank-provider 已有 `products()` RPC；bank-api 已有 `PayNotifyController /payNotify/wxNotify`，回调链 `payNotify → 入账` 已通（有 t_pay_order 历史 AUTO_INCREMENT=242）。
- WalletPage 目前只有余额展示，没有商品档位和支付操作。

### 方案（刻意做简单，不对接真实支付）
- **商品数据**：按用户口径插 6 档充值模板（`t_pay_product`，`extra` 字段存 `{"coins":N}`）：
  `6元=600金币、10元=1000金币(推荐)、30元=3080、98元=10800、198元=22800、328元=38800`（对齐行业惯例的首充/大额加赠感）。
- **后端新增**（bank-api 层）：
  1. `POST /bank/payOrder/createPayOrder`（productId）→ 生成订单号、落 `t_pay_order`（status=0 待支付），返回 orderNo；
  2. `POST /payNotify/mockNotify`（orderNo）→ **模拟第三方支付成功回调**：校验订单存在且待支付 → 复用现有 `payNotify` 入账链路（账户加币 + 流水 + 订单置为已支付）→ MQ/IM 通知（可选）。
     本期不做支付渠道跳转，前端点「模拟支付成功」即触发该接口，等同于回调打进来。
- **前端 WalletPage 重排**（支付宝/抖音充值页风格）：
  顶部余额大字卡片 → 6 档格子（金币数、价格、推荐角标）→ 选中档位 →「确认充值」→ 直接弹「模拟支付成功」→ 余额刷新。
- **对账**不在本期（下一期做 T+1 双向对账），但本期入账必须写流水，保证下一期有账可对。

### 验收
选 10 元档 → 模拟支付 → 余额 +1000、`t_pay_order` 该单 status=2、`t_qiyu_currency_trade` 新增一条充值流水。

---

## 5. 红包雨完善联调

### 现状
- 后端接口齐全：`GiftController` 里 create/prepare/send/receive/query 五个红包接口，IM code 5560（发送）/5561（领取成功），红包池 Redis 逻辑有专门测试脚本（redpacket_pool_test.mjs 通过）。
- 前端 `RedPacketRain.vue`（金币下落动画）已写好，**但没有挂进 RoomPage，主播也没有发红包的入口**——链路断在集成层。

### 方案
- **主播端**：底栏「🧧红包」按钮 → 弹配置小窗（总金额、红包个数）→ create+prepare+send → IM 5560 广播全房间。
- **观众端**：收到 5560 → 启动 RedPacketRain（下落数量=红包个数，时长可配 5s）→ 点catch任意红包 → 调 `receiveRedPacket` → 5561 广播领取结果 → 飘字「抢到 X 金币」+ 余额刷新。
- **收尾**：活动结束（红包抢完/超时）停止雨幕；主播放Configs查询看领取情况。
- 后端预计只需补消息体字段/小的空值修复，以联调结果为准。

### 验收
两个浏览器：主播发 100 金币 10 个红包 → 观众端出现红包雨 → 点击抢到 → 双方余额变化正确、红包不可重复抢。

---

## 6. 金币余额展示

### 现状
bank-provider 有账户查询能力（`t_qiyu_currency_account`），前端各页面都不显示余额；送礼、抢红包、充值后余额变化无感知。

### 方案
- 后端确认 `BankController` 已有余额查询接口（缺则补一条 `GET /bank/account/balance`，走现有 account RPC，Redis 缓存 30s）。
- 前端：
  - `HomePage` 顶栏右上角常驻 `💰 12,345`（登录后拉取）；
  - `RoomPage` 顶栏同样展示；
  - 全局用 Pinia store 存余额，**送礼成功(5556)/抢到红包(5561)/充值成功**三个事件触发刷新，保证数字始终是新的。

### 验收
送礼后顶栏余额立刻减少；充值/抢红包后立刻增加。

---

## 7. 实施顺序与提交

1. 日志彩色化（改配置、重启即验，先做掉给后面调试用）
2. 金币余额展示（小，且后面 4/5 都要依赖余额刷新）
3. 充值中心（依赖 2 的 store）
4. 礼物特效 + 直播间布局（同一批改 RoomPage，避免反复重排）
5. 红包雨联调（依赖布局底栏入口）
6. 全量回归：开播→送礼特效→发红包→观众抢→充值→关播 一条龙走一遍
7. commit（用户自行 push GitHub）

## 8. 明确不做（下一期）
视频微服务（新 tab、MinIO 点播、点赞收藏）、T+1 对账系统、Docker 全量镜像一键启动、分账、直播间类型扩展。

---

# 迭代设计文档（2026-09-13）：后台管理系统 + 功能补全

> 覆盖 6 项新需求，按「体验 bug → 功能联动 → 功能补全 → 后台系统 → 视觉设计」顺序实施。
> 每项完成即提交，全部过浏览器 E2E 再算完成。

## 需求总览与实施顺序

| 顺序 | 需求 | 优先级 | 规模 | 说明 |
|------|------|--------|------|------|
| 1 | ⑥ 主播刷新不关播（WebRTC 体验） | P0 | 中 | 线上体验 bug，最影响真实使用 |
| 2 | ⑤ 带货类型联动商品配置 | P1 | 小 | 后端校验 + 前端引导 |
| 3 | ③ 视频个人中心（历史/收藏/点赞） | P1 | 中 | 补全视频闭环 |
| 4 | ①+② 后台管理系统（对账/标签/运营） | P1 | 大 | 独立系统，本次最大块 |
| 5 | ④ 前端视觉重设计（贯穿） | P1 | 中 | 按上表伴随进行，最后统一验收 |

---

## 一、⑥ 主播刷新浏览器不再关播（P0）

### 现状问题
`living-provider` 的 `userOfflineHandler`：主播 IM 一断线就调 `closeLiving`。刷新浏览器 = IM 断线 = 直播结束。而此时 SRS 推流（尤其 OBS 推流）其实还在，观众端看起来就是"直播莫名没了"。

### 设计：关播判定从「单条件」改为「宽限期 + 双条件」

```
主播IM断线 ──→ 不再直接关播
             └→ 投递 30s 延迟MQ（关播检查，复用红包结算的延迟消息模式）
                        └→ 消费者检查三个条件：
                           1. 房间仍是开播状态
                           2. 主播未回到房间（房间用户Redis set里没有主播）
                           3. SRS 推流已断（查 redis stream_status / SRS API）
                           → 三条全中才关播；任一不满足则放行（直播继续）
```

- 主播 IM 重连（刷新后 IM 自动重连回房间）→ 房间 set 里又有主播了 → 检查自然放行
- **OBS 推流的主播**：刷新浏览器不影响推流 → 条件 3 不满足 → 直播继续，体验无缝
- **摄像头推流的主播**：刷新后推流断了 → 30 秒宽限内刷新回来并重新推流 → 不关播；超时未回 → 正常关播

### 前端恢复体验
- 新增接口 `POST /living/myLivingRoom`：查询「我进行中的直播」，返回 roomId 或空
- 首页 onMounted：若我正在直播 → 顶栏出现「回到我的直播间」按钮（不强制跳转）
- 主播端房间页刷新后（浏览器记住的 roomId）：房间还在开播 → 直接回到房间页，摄像头推流区显示「继续推流」（一键重推）；OBS 场景显示推流地址（地址不变，无需操作）

### 改动点
| 层 | 文件 | 改动 |
|----|------|------|
| living-provider | LivingRoomServiceImpl.userOfflineHandler | 主播断线改为发延迟MQ，不直接关播 |
| living-provider | 新增 OffLineCheckConsumer（或复用 gift topic 模式） | 30s 后三条件校验决定关播 |
| api | LivingRoomController/Service | 新增 myLivingRoom 接口 |
| web_live | HomePage / RoomPage | 「回到我的直播间」入口 + 恢复推流 UI |

### 验收
1. OBS 推流中刷新主播浏览器 → 直播不中断，观众无感知
2. 摄像头推流中刷新 → 30 秒内回来点"继续推流"→ 直播继续
3. 摄像头推流中刷新后 30 秒不回来 → 自动关播（老行为保留兜底）

---

## 二、⑤ 带货类型联动商品配置（P1）

### 设计
1. **后端强校验**：`startingLiving(type=4)` 时 Dubbo 调 `anchorShopRpc.listByAnchorId`，上架商品数为 0 → 报错（新 ApiErrorEnum：`请先在商品管理上架至少一件商品`）
2. **开播弹窗引导**：选择「带货」类型时，前端先查 `/gift/shop/myList`；为空则弹确认框「带货直播需要先配置商品」→「去配置」直接打开商品管理弹窗（ShopManageDialog 复用，配置完回到开播弹窗）
3. **商品管理入口前移**：主页顶栏对主播增加「商品」按钮（不用先进直播间才能配置）

### 验收
- 无商品选带货开播 → 被拦截并引导配置；配置 1 件商品后开播成功；观众端小黄车有货

---

## 三、③ 视频个人中心：观看历史 + 收藏/点赞/我的视频（P1）

### 数据模型
```sql
CREATE TABLE t_video_watch_history (
  id bigint unsigned AUTO_INCREMENT PRIMARY KEY,
  user_id bigint unsigned NOT NULL,
  video_id bigint unsigned NOT NULL,
  watch_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_video (user_id, video_id)   -- 重复观看只刷时间
);
```
- 视频详情页播放 ≥3 秒时上报 `POST /video/history`（upsert），避免误点污染历史

### 接口（video-provider + api 透传）
| 接口 | 说明 |
|------|------|
| `/video/history` | 我的观看历史（按 watch_time 倒序分页） |
| `/video/myList` | 我发布的视频 |
| `/video/favoriteList` | 我收藏的（join t_video_user_action is_favorite=1） |
| `/video/likeList` | 我点赞的 |

> 点赞/收藏/评论本体上个迭代已完成，本项补「历史」和「聚合查看」。

### 前端：个人中心页 `/user/center`
- 顶栏头像改为下拉菜单：个人中心 / 个人设置 / 退出
- 页面 4 个 tab：我的视频 / 我的收藏 / 我点赞的 / 观看历史；卡片复用视频广场样式
- 头像下拉替代现有"点头像开设置弹窗"（避免入口歧义）

### 验收
- A 看视频 ≥3 秒 → 历史出现且按最近排序；重复观看不重复记录；收藏/点赞列表与实际操作一致

---

## 四、①+② 后台管理系统（独立系统，公司内部用）

### 定位
对账、标签、用户、内容等**运营能力**从 C 端剥离，独立一套「旗鱼运营台」：独立后端服务 + 独立前端工程 + 独立登录，与 C 端用户体系完全隔离。

### 后端：新模块 `qiyu-live-admin-api`
| 项 | 设计 |
|----|------|
| 形态 | Spring Boot Web（端口 38300），Dubbo consumer 调现有 provider，不重复建库 |
| 登录 | 独立账号表 `t_admin_user`（admin 库或 qiyu_live_common），账号密码 + BCrypt；Session/Token 简化为固定 token 头校验（内部系统） |
| 模块 | ① 对账中心 ② 标签管理 ③ 用户管理 ④ 内容管理 ⑤ 直播管理 |

各模块接口（全部经 Dubbo 复用现有能力）：
| 模块 | 接口 | 数据源 |
|------|------|--------|
| 对账中心 | 差错分页/按日筛选、手动触发对账、标记已处理（update status） | t_reconciliation_detail（新增 markProcessed RPC） |
| 标签管理 | 视频标签 CRUD | t_video_tag（新增 tag CRUD RPC） |
| 用户管理 | 分页搜索、查看余额、封禁/解封 | t_user + bank RPC |
| 内容管理 | 视频列表/下架/上架 | t_video_info（新增 admin RPC） |
| 直播管理 | 当前直播列表、强制关播 | t_living_room + 现有 RPC |

### 前端：新工程 `web_admin`（端口 3001）
- Vue3 + vite + element-plus，vite 代理 `/adminApi` → 38300
- **视觉方向（frontend-design）**：与 C 端暗色娱乐风刻意区分——浅色高效运营风：
  - 底色暖白 `#FAFAF7`，主色深青 `#0F6B5C`，告警红 `#C0392B`，文字墨黑 `#1A1D1F`
  - 数字等宽字体（差错金额、余额）+ 表格斑马纹，突出"查账"场景
  - 签名元素：对账中心首页以「账实差值」大数字卡开场（当日应收 vs 实收 vs 差额），差错表按类型左侧色条区分
- 登录页 + 左侧导航布局

### 验收
- admin 登录 → 对账中心看到已有差错 → 触发对账 → 标记处理；标签增删改 → C 端发布页同步可见；视频下架 → C 端不可见；强制关播生效

---

## 五、④ 前端视觉重设计（frontend-design，贯穿）

### 方法
按 frontend-design skill 两遍走：先出 token 系统（色板/字体/布局/签名元素）再动代码；完成后截图自评。

### C 端 web_live 方向（直播/视频社区）
- 现状是"默认暗色 + 紫蓝渐变"，缺乏记忆点。重设计方向：
  - **主题隐喻「深海旗鱼」**：底色不是纯黑而是深海蓝黑（`#0B1220` 系），主强调色旗鱼银蓝 `#5EA8FF` → 高能状态的流光渐变仅用于"开播/送礼"两个动作
  - 字体：标题用更有性格的中文黑体（HarmonyOS Sans / MiSans），数字（余额/人气）用等宽 `JetBrains Mono`
  - 签名元素：直播间在线人数 chip 的「鱼群游动」微动效；首页直播卡片 hover 时封面轻微"游动"位移
- 覆盖页面：首页/房间页/视频三页/钱包/对账中心/个人中心，统一 token（CSS 变量抽取）

### 管理端 web_admin 方向见第四节

### 验收
每个页面截图对比改版前后；移动端宽度可看；键盘焦点与 reduced-motion 保留。

---

## 六、不做 / 边界
- 管理端不做细粒度 RBAC 权限体系（内部系统，单一管理员角色即可，后续要再加）
- 观看历史不做"断点续播"（只做记录与列表）
- 主播刷新恢复不涉及 SRS 侧会话迁移（OBS 流本来就不断）

## 七、提交切分
1. `feat: 主播刷新不关播（宽限期+推流状态判定）+ 回到直播间`
2. `feat: 带货类型开播校验商品配置 + 开播弹窗引导`
3. `feat: 视频观看历史 + 个人中心四tab`
4. `feat: 后台管理系统（admin-api + web_admin：对账/标签/用户/内容/直播）`
5. `feat: 前端视觉重设计（C端深海旗鱼主题 + 管理端运营台）`

---

## 八、实施结果（2026-09-13 全部完成）

| 需求 | 提交 | 验证 |
|------|------|------|
| ⑥ 主播刷新不关播 | `da9b9b7` | 三场景 E2E 全过（`scripts/refresh_no_close_test.mjs`）：无推流超时关播 / 30s内重连继续 / OBS推流存活继续；新增「回到我的直播间」入口 |
| ⑤ 带货联动 | 见 git log | 无商品开播被拦（code 10112 引导文案）；上架后开播成功；开播弹窗选带货自动校验并引导 |
| ③ 视频个人中心 | `f345904` | 播放≥3秒自动记录历史；四tab数据正确；头像改下拉菜单 |
| ①② 后台管理系统 | `f725c17` | admin-api(38300)+web_admin(3001) 独立登录；对账中心「账实差值」大数字卡+标记处理闭环；标签CRUD同步C端；视频下架C端立即不可见；强制关播可用。管理员 admin/admin123 |
| ④ 视觉重设计 | 见 git log | 「深海旗鱼」主题 token 落地（theme.css），全部页面换深海色阶+银蓝主色，房间顶栏鱼群游动签名动效，数字等宽，开播按钮高能渐变 |

### 部署备忘
- 新服务：qiyu-live-admin-api（端口 38300，jar 启动；web_admin 开发 `cd web_admin && npm run dev` 端口 3001）
- 管理员账号：admin / admin123（t_admin_user 表，qiyu_live_common 库）
- 注意：admin-api 依赖各 interface 的最新 SNAPSHOT，改过接口后需先 `mvn install` 对应 interface 再 package admin-api（曾因旧 jar 打包导致 status 字段丢失）
