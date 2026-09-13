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
