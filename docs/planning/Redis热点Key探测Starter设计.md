# Redis 热点 Key 探测 Starter 设计稿（评审稿，未实现）

> 对应需求：`plan_requrie.md` todo-8 —— 设计一个 Redis 热点 key 探测的 starter，探测参数可配置，
> 探测到之后的策略可用 Bean 配置或 SPI 扩展。
> 落点：`qiyu-live-framework/qiyu-live-framework-redis-starter` 的扩展模块（建议新模块
> `qiyu-live-framework-hotkey-starter`，依赖 redis-starter 复用其连接与 KeyBuilder 体系）。

---

## 一、背景与目标

直播场景天然存在热点 key：

| 热点来源 | 典型 key | 风险 |
|---|---|---|
| 大 V 直播间 | 房间在线 Set、人气 ZSET、PK 计分（Lua） | 单分片 QPS 打满，拖慢整个 Redis 分片 |
| 爆款视频 | `video:detail:{id}` 缓存 | 缓存击穿 + 单 key 读放大 |
| 全局排行/配置 | 礼物列表、充值档位 | 所有人读同一个 key |

目标：**探测**（找出热点 key）+ **响应**（对热点做可插拔处置），对业务代码零侵入，
参数全部走 `application.yml`（可托管 Nacos 动态刷新）。

---

## 二、探测路线对比与选型

| 路线 | 原理 | 优点 | 缺点 | 结论 |
|---|---|---|---|---|
| A. 客户端采样统计 | 包装 RedisTemplate，对每次命令的 key 做本地滑动窗口计数 | 零服务端依赖、按"业务前缀"聚合、可统计 QPS（服务端 LFU 只有频率无速率） | 多实例视角各自独立；模板包装需覆盖 execute 回 | **主路线** |
| B. Redis LFU hotkeys | `maxmemory-policy` 为 LFU 时 `redis-cli --hotkeys` / `OBJECT FREQ` | 服务端权威、能发现非本应用的热点 | 只反映访问频率不反映实时 QPS；LFU 计数衰减快；需要驱逐策略配合；cluster 下要逐节点扫 | **校准辅路**（低频定时跑） |
| C. keyspace notification | 订阅 key 事件 | 实时 | 噪声大（过期/淘汰事件混入）、事件不保证送达、只能看写事件 | **不采用** |

**选型：A 为主、B 定时校准。** 项目已有 `RedisKeyBuilder` 体系，key 天然带业务前缀
（`qiyu-live-gift-provider:` 等），客户端聚合可直接按"前缀模板"归并，比裸 key 聚合更有业务含义。

---

## 三、总体架构

```text
业务代码 ──> HotKeyRedisTemplate（包装/切面，记录 key + 命令类型）
                │  采样（可配置采样率，默认全量；LongAdder 分桶计数，无锁）
                ▼
          HotKeySampler（滑动窗口：默认 10s 窗口 / 1s 桶，内存 RingBuffer）
                │  定时聚合（默认每 5s，取 TopN）
                ▼
          HotKeyAggregator ──超阈值──> HotKeyEvent（key、QPS、命令类型、前缀模板）
                │                          │
                │                    HotKeyPublisher
                │                    ├── 内置策略（按配置启用）
                │                    └── SPI / Spring Bean 扩展策略（业务自定义）
                ▼
          （校准）HotKeyCalibrator：每 5min 拉一次 LFU freq / --hotkeys 对账，
                   修正"多实例分摊后单实例看不见的全局热点"
```

### 核心组件

| 组件 | 职责 | 关键设计 |
|---|---|---|
| `HotKeyRedisTemplate` | 包装 `RedisTemplate`，命令执行前后上报 key | 用 `RedisTemplate` 的 execute 包装而非 AOP，覆盖 `opsForXxx` 与 execute 回调；**只读记录、绝不改行为** |
| `HotKeySampler` | 滑动窗口计数 | `Striped64` 风格分桶 + `ArrayRingBuffer` 桶数组；采样率 <1 时按 hash 采样降开销；内存中只保留当前窗口，O(1) 写入 |
| `HotKeyAggregator` | 周期聚合 → 判定热点 | 单线程调度器；TopN（默认 50）+ 阈值（默认单 key 1000 QPS/实例）；**热点判定带粘滞**（连续 2 个窗口命中才触发，防抖） |
| `HotKeyEventPublisher` | 事件分发 | 先内置策略、后扩展策略，任一异常不影响下一个（try-run 隔离） |
| `HotKeyCalibrator` | LFU 对账 | 低频（默认 5min）执行 `OBJECT FREQ`，结果作为事件来源之一，标注 `source=lfu` |

---

## 四、探测参数（全部可配置，支持 Nacos 刷新）

```yaml
qiyu:
  hotkey:
    enabled: true
    sample-rate: 1.0          # 采样率 0~1，1=全量
    window-seconds: 10        # 滑动窗口长度
    bucket-seconds: 1         # 窗口内桶粒度
    report-interval-seconds: 5
    threshold-qps: 1000       # 单 key 单实例判定阈值
    sticky-windows: 2         # 连续 N 个窗口超阈值才算热点（防抖）
    top-n: 50
    include-prefixes:         # 只关注这些前缀（空=全部）；建议填业务前缀降噪
      - qiyu-live-living-provider:living_room
      - qiyu-live-gift-provider:gift_config_cache
    exclude-prefixes:         # 永不告警（如心跳/锁 key）
      - *balance_lock*
    calibrator:
      enabled: true
      interval-seconds: 300
    actions:                  # 内置策略开关
      log: warn               # off|info|warn —— 默认日志告警
      notify: false           # webhook 告警（url 另配）
```

---

## 五、响应策略（可插拔）

### 注册方式（两种并存，SPI 优先级低于显式 Bean）

1. **SPI**：`META-INF/services/org.qiyu.live.framework.hotkey.HotKeyAction`
   —— 适合 framework 层内置的通用策略，业务无需写 Spring 配置
2. **Spring Bean**：实现 `HotKeyAction` 接口注册为 Bean，`@Order` 控制顺序
   —— 适合业务侧定制（如 living-provider 针对房间 key 的专项处理）

```java
public interface HotKeyAction {
    /** 是否处理该事件（按 key 前缀/命令类型过滤） */
    boolean supports(HotKeyEvent event);
    /** 处置热点（只读探测期只允许 log/notify；M2 起允许干预） */
    void onHotKey(HotKeyEvent event);
}
```

### 内置策略清单（按 M 期交付）

| 策略 | 期 | 说明 |
|---|---|---|
| 日志/指标上报 | M1 | warn 日志 + Micrometer 指标（`qiyu.hotkey.qps{key=}`），接管理端图表 |
| Webhook 告警 | M1 | 可配 URL，聚合 N 秒防重 |
| **本地兜底缓存** | M2 | 命中热点且该前缀**声明为可缓存**（`cacheable-prefixes`，要求"读多写少 + 可容忍秒级不一致"）时，用 Caffeine 加 `refreshAfterWrite(3s)` 兜底读，TTL 到期强制回源。**写命令命中的 key 强制不缓存**（一致性红线） |
| 随机退化 | M2 | 对 ZSET/Hash 类热点读，按概率降采样返回（保序近似），适合"在线人数"这类容忍误差的场景 |
| Key 分片建议 | M3 | 探测报告里对可拆分 key（计数类）给出分片建议（`key{0..N}`），由业务按需采纳 |
| 强制限流 | M3 | 对热点 key 的命令按 key 维度本地令牌桶限流，保护 Redis（最后手段，默认关） |

---

## 六、与现有框架的集成点

- 复用 `qiyu-live-framework-redis-starter` 的连接工厂与序列化器，不新建连接
- 白名单天然对齐 `RedisKeyBuilder` 的业务前缀体系
- provider 服务均为 `WebApplicationType.NONE`，starter 全部基于自动配置（`AutoConfiguration.imports`），无 HTTP 依赖
- 多实例视角问题由 Calibrator（LFU 服务端视角）补偿；单实例阈值配置时按 `全局QPS / 实例数` 折算

## 七、分期落地

| 期 | 内容 | 验收 |
|---|---|---|
| M1 | 采样 + 聚合 + 日志/Micrometer + Webhook；压测下 overhead < 3% | jmeter 打 `gift_config_cache`，日志出现热点事件 |
| M2 | SPI/Bean 策略框架 + Caffeine 兜底缓存 + 随机退化 | 热点 key QPS 打到 Redis 的流量下降 ≥ 80% |
| M3 | 管理端可视化（web_admin 新页面：热点列表/趋势/策略开关）+ 强制限流 | 运营可自助查看与处置 |

## 八、风险与红线

1. **绝不缓存"写后读"敏感 key**：余额、订单、锁、bind-ip——白名单必须业务负责人确认
2. 采样包装层的异常必须吞掉（探测失败不能影响业务读写）
3. 集群模式下 `--hotkeys` 逐节点执行，注意 scan 开销窗口
4. 兜底缓存的 key 必须带版本化 value（或 TTL ≤ 5s），避免长时间旧读
