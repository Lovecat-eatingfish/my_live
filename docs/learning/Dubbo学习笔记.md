# Dubbo 学习笔记：从会用到了解原理

> 配合本项目学习——本项目是 Dubbo 3.2 的深度使用者：interface/provider/api 三层拆分、自定义 Cluster SPI 定点投递、启动自检强制引用校验，都是真实落点。文中凡标注"本项目"的地方都可以在代码里找到对应实现。

## 目录

1. [Dubbo 是什么：RPC 框架的定位](#一dubbo-是什么rpc-框架的定位)
2. [核心架构：一次 RPC 调用的完整旅程](#二核心架构一次-rpc-调用的完整旅程)
3. [本项目的用法：三层拆分与注解](#三本项目的用法三层拆分与注解)
4. [核心配置详解](#四核心配置详解)
5. [SPI：Dubbo 最精华的扩展机制](#五spidubbo-最精华的扩展机制)
6. [集群容错与负载均衡](#六集群容错与负载均衡)
7. [RpcContext 与隐式传参（本项目定点投递的基石）](#七rpccontext-与隐式传参本项目定点投递的基石)
8. [上手 Demo：最小可跑的 provider + consumer](#八上手-demo最小可跑的-provider--consumer)
9. [常见坑与面试题](#九常见坑与面试题)
10. [进阶路线](#十进阶路线)

---

## 一、Dubbo 是什么：RPC 框架的定位

**一句话**：Dubbo 解决的是"服务 A 的进程想调用服务 B 的进程里的一个 Java 方法，就像调本地方法一样"。

```java
// 看起来是本地调用：
ILivingRoomRpc rpc = ...;
rpc.queryRoomInfo(roomId);
// 实际上这个方法在另一个 JVM 里执行——参数被序列化→网络传输→对端反序列化→执行→结果传回来
```

**和 HTTP 接口（Feign/RestTemplate）对比**：

| 维度 | Dubbo（RPC） | REST + Feign |
|---|---|---|
| 通信方式 | 长连接多路复用（默认 dubbo 协议，单连接异步 IO） | 短连接 HTTP/1.1（HTTP/2 需 Triple） |
| 序列化 | Hessian2/Protobuf（紧凑二进制） | JSON（可读性好但体积大） |
| 接口契约 | **Java interface 强类型**，编译期检查 | 靠约定/文档，弱类型 |
| 服务治理 | 内置：注册发现、负载均衡、集群容错、路由 | 依赖 Spring Cloud 全家桶拼装 |
| 跨语言 | 弱（Java 为主，Triple+Protobuf 改善中） | 强 |
| 适用 | **内部服务间高频调用**（本项目 15 个服务互调） | 对外 API、异构系统 |

本项目选 Dubbo 的理由：内部服务多、调用频繁、全是 Java、需要 SPI 定制路由（§五）——典型的 Dubbo 场景。

---

## 二、核心架构：一次 RPC 调用的完整旅程

```text
┌─────────────┐    ①启动时注册(export)     ┌──────────────┐
│  Provider    │ ────────────────────────▶ │    Nacos      │
│ (qiyu-live-  │                           │  (注册中心)    │
│  living-     │ ◀──────────────────────── │  服务:IP:端口  │
│  provider)   │    ②订阅 + 接收推送        └──────┬───────┘
└──────▲───────┘                                  │
       │ ④dubbo协议长连接                          │③启动时订阅
       │   (序列化请求/反序列化响应)                  ▼
       └────────────────────────────────── ┌──────────────┐
                                           │   Consumer   │
                                           │ (qiyu-live-  │
                                           │  api)        │
                                           └──────────────┘
```

1. **Provider 启动**：把"接口全限定名 + 自己的 IP:端口"注册到 Nacos；
2. **Consumer 启动**：从 Nacos 订阅这个接口的提供者列表，并**监听变更**（provider 下线自动摘除）；
3. **调用时**：Consumer 根据负载均衡策略从列表选一个地址，通过长连接把序列化后的请求发过去；
4. **Provider 执行**本地实现方法，结果原路返回。

关键理解：**注册中心挂了不影响已建立的调用**——Consumer 本地缓存了提供者列表，Nacos 只在"列表变化"时才有用。这是 Dubbo 高可用的设计基石。

---

## 三、本项目的用法：三层拆分与注解

### 3.1 三层模块（本项目最值得学的设计纪律）

```text
qiyu-live-living-interface   ← 纯 POJO：ILivingRoomRpc 接口 + DTO + 枚举，无任何依赖
qiyu-live-living-provider    ← @DubboService 实现，WebApplicationType.NONE（无HTTP容器）
qiyu-live-api                ← @DubboReference 注入，是唯一有 HTTP 端口的模块
```

**为什么 interface 必须独立成模块**：Consumer 只需要"接口签名 + DTO"，如果它被迫依赖 provider 模块，就会拖进 MyBatis、数据库驱动等一堆实现依赖。轻量 interface 包让"面向接口编程"落到依赖关系上。

### 3.2 两个注解（本项目真实代码）

```java
// Provider 端（living-provider）：
@DubboService
public class LivingRoomRpcImpl implements ILivingRoomRpc { ... }

// Consumer 端（api 模块）：
@DubboReference(check = false)     // 启动时不校验提供者是否存在
private ILivingRoomRpc livingRoomRpc;
```

`check = false` 的含义：**启动时**不做提供者存在性校验（懒校验），第一次真正调用时才报 `no provider`。为什么本项目全用 `check=false`？因为 15 个服务的启动顺序很难严格保证，启动期互相等待会形成死锁式依赖。

**但注意本项目的特殊设计**：`QiyuProviderStartupVerifier`（bootstrap-starter）在启动后**手动把每个 @DubboReference 强制初始化一遍**——相当于把 check=false 的宽松换回 check=true 的严格，且给了漂亮的失败清单和 `exit(1)`。这就是"既要启动灵活、又要 fail-fast"的折中（细节见 `ops/启动手册.md` §一）。

### 3.3 端口规划

每个 provider 一个独立 dubbo 协议端口（`dubbo.protocol.port`），本项目规则：30010 起步长 5（bank 30010 / account 30015 / idgen 30020 / ... / stream 30065），见 `ops/端口总表.md`。**Dubbo 端口不需要暴露给宿主机/公网**——只有服务间内网互访。

---

## 四、核心配置详解

本项目的配置在各服务 application.yml + Nacos 的 qiyu-live-*.yaml：

```yaml
spring:
  application:
    name: qiyu-live-living-provider   # ① 应用名 = 注册到 Nacos 的服务名

dubbo:
  application:
    name: qiyu-live-living-provider
    qos-enable: false                 # ② QoS 运维端口(22222)，默认开，集群里会端口冲突
  registry:
    address: nacos://127.0.0.1:8848?namespace=qiyu-live-test   # ③ 注册中心
    #  原始线上版: nacos://qiyu.nacos.com:8848?namespace=qiyu-live-test
  protocol:
    name: dubbo                       # ④ 协议：dubbo(默认,单长连接) / triple(Dubbo3 HTTP/2)
    port: 30045                       #    每个 provider 独立端口
  provider:
    timeout: 3000                     # ⑤ 调用超时ms（消费端可覆盖）
    retries: 2                        # ⑥ 失败重试次数（默认2，只对幂等接口安全！）
```

**重点讲 ⑤⑥**：`retries=2` 意味着一次调用最多发出 3 次——如果接口不幂等（比如"扣款""发消息"），超时重试会造成重复扣款。治理手段：
- 写操作接口上显式 `@DubboReference(retries = 0)`；
- 或消费端做幂等（本项目送礼用 Redis SETNX uuid 幂等，就是给 MQ + RPC 双重重复投递上的保险）。

**多版本/多分组**（灰度发布用）：`@DubboService(version="1.0", group="dev")` ↔ `@DubboReference(version="1.0", group="dev")`，同一接口新老实现并存。

---

## 五、SPI：Dubbo 最精华的扩展机制

### 5.1 什么是 Dubbo SPI

JDK 原生 SPI 一次加载所有实现；Dubbo SPI **按名字按需加载 + 自适应扩展 + IOC/AOP**，几乎框架里每个环节（协议、注册中心、负载均衡、集群容错、路由、过滤器）都是 SPI 扩展点。扩展声明放在：

```text
src/main/resources/META-INF/dubbo/internal/org.apache.dubbo.rpc.cluster.Cluster
文件内容（key=扩展名, value=实现类全限定名）：
imRouter=org.qiyu.live.im.router.provider.cluster.ImRouterCluster
```

### 5.2 本项目的自定义 Cluster：IM 定点投递（最有含金量的实战）

**问题**：用户 A 的长连接连在 Netty 机器 1 上，要给他推消息就必须**精确调用机器 1**，而 Dubbo 默认负载均衡会随机挑一台——普通用法做不到。

**解法**（im-router-provider）：

```java
// ① 自定义 Cluster 扩展（SPI 入口）
public class ImRouterCluster implements Cluster {
    public static final String NAME = "imRouter";
    @Override
    public <T> Invoker<T> join(Directory<T> directory) {
        return new ImRouterClusterInvoker<>(directory);
    }
}

// ② 自定义 Invoker：调用时从 RpcContext 取目标 ip，精确匹配 invoker 地址
public class ImRouterClusterInvoker<T> extends AbstractClusterInvoker<T> {
    @Override
    protected Result doInvoke(Invocation invocation, List<Invoker<T>> invokers, LoadBalance lb) {
        String targetIp = (String) RpcContext.getContext().get("ip");  // 见 §七
        // 在注册的 invoker 列表里找 ip 匹配的那台，只调它
        Invoker<T> matched = invokers.stream()
                .filter(inv -> inv.getUrl().getIp().equals(targetIp))
                .findFirst().orElseThrow(...);
        return matched.invoke(invocation);
    }
}

// ③ 使用：在 @DubboReference 上指定 cluster
@DubboReference(cluster = "imRouter", check = false)
private IRouterHandlerRpc routerHandlerRpc;

// ④ 调用前设定目标地址（Redis 里查到的"用户A连在哪台机器"）
RpcContext.getContext().set("ip", bindAddress);   // 如 "127.0.0.1:30035"
routerHandlerRpc.onReceive(msg);
```

> **学习点**：这是 Dubbo 面试的天花板级素材——从"业务需求（定点投递）→ 现有能力不够 → 找到 Cluster 扩展点 → 读 AbstractClusterInvoker 源码 → 写 SPI 文件"完整走一遍，能讲 10 分钟。对比一下：Feign/RestTemplate 要做同样的事得自己手写整个寻址层。

### 5.3 内置扩展点扫盲

| 扩展点 | 作用 | 内置实现举例 |
|---|---|---|
| Protocol | 通信协议 | dubbo / triple / injvm（本地 JVM 内直调） |
| Registry | 注册中心对接 | nacos / zookeeper / redis |
| LoadBalance | 负载均衡 | random(默认) / roundrobin / leastactive / consistenthash |
| Cluster | 集群容错 | failover / failfast / failsafe + **本项目自定义 imRouter** |
| Filter | 调用拦截链（AOP） | timeout / token / context / 自定义日志鉴权过滤器 |
| Serialization | 序列化 | hessian2(默认) / protobuf / kryo |

---

## 六、集群容错与负载均衡

### 6.1 集群容错策略（一个接口有多个提供者时，失败了怎么办）

| 策略 | 行为 | 适用 |
|---|---|---|
| `failover`（默认） | 失败**换一台**重试（配合 retries） | **幂等读操作** |
| `failfast` | 失败立即抛异常 | **非幂等写操作**（扣款） |
| `failsafe` | 失败忽略，仅记日志 | 写审计日志这类"失败了无所谓" |
| `failback` | 失败定时重发 | 消息通知类 |
| `broadcast` | 逐台调用，任一失败算失败 | 通知所有节点刷新缓存 |

```java
@DubboReference(cluster = "failfast", retries = 0)   // 写操作的标准姿势
```

### 6.2 负载均衡策略

`random`（默认，按权重随机）/ `roundrobin`（轮询）/ `leastactive`（挑并发最少的，慢机器自动少接活）/ `consistenthash`（一致性哈希，相同参数落同一台——有状态路由的轻量替代方案，也是 im-router 定点投递之外另一种思路）。

---

## 七、RpcContext 与隐式传参（本项目定点投递的基石）

`RpcContext` 是 Dubbo 的**调用上下文**，本质是一个 ThreadLocal：

```java
// 消费端：调用前放置（随请求头传给对端）
RpcContext.getContext().set("ip", "127.0.0.1:30035");
RpcContext.getContext().setAttachment("trace-id", UUID.randomUUID());
rpc.xxx();

// 提供端：读取消费端传来的隐式参数
String traceId = RpcContext.getServerAttachment().getAttachment("trace-id");
String ip = RpcContext.getContext().get("ip");   // 自定义 Cluster 里就是这么取的
```

**典型用途**：全链路 traceId 透传、灰度标记、本项目的"定点 ip"。**坑**：ThreadLocal 有作用域和内存泄漏问题——用完 `remove()`，异步线程里拿不到（需要手动传递或用 Dubbo 的上下文传递 API）。

**同步转异步**也经 RpcContext：

```java
rpc.queryBigData(param);                       // 发起调用即返回
Future<Result> future = RpcContext.getContext().getFuture();
Result r = future.get(5, TimeUnit.SECONDS);    // 需要时再取——两个调用可并行发出
```

---

## 八、上手 Demo：最小可跑的 provider + consumer

### 8.1 准备：一个 Nacos（复用本项目的中间件即可）

```bash
docker compose up -d nacos       # 本项目 compose 里现成的，127.0.0.1:8848 (nacos/nacos)
```

### 8.2 建一个 Maven 三模块工程（体会"interface 独立"的必要性）

```text
dubbo-demo/
├── pom.xml                       (parent: dubbo 3.2.0 + spring-boot 3.x)
├── demo-interface/               (纯接口模块)
├── demo-provider/
└── demo-consumer/
```

```xml
<!-- 三个模块共同的 dubbo 依赖 -->
<dependency>
    <groupId>org.apache.dubbo</groupId>
    <artifactId>dubbo-spring-boot-starter</artifactId>
    <version>3.2.0</version>
</dependency>
<dependency>
    <groupId>com.alibaba.nacos</groupId>
    <artifactId>nacos-client</artifactId>
    <version>2.2.1</version>
</dependency>
```

```java
// demo-interface
public interface HelloRpc {
    String sayHello(String name);
}

// demo-provider —— 实现类
@DubboService(version = "1.0")
public class HelloRpcImpl implements HelloRpc {
    public String sayHello(String name) { return "hello, " + name + " (from dubbo)"; }
}
```

```yaml
# demo-provider 的 application.yml
dubbo:
  application: { name: demo-provider, qos-enable: false }
  registry: { address: nacos://127.0.0.1:8848 }
  protocol: { name: dubbo, port: 20880 }
spring: { application: { name: demo-provider } }
```

```java
// demo-consumer —— 调用方（随便一个 CommandLineRunner）
@DubboReference(version = "1.0", check = false, timeout = 3000, retries = 0)
private HelloRpc helloRpc;

@Override
public void run(String... args) {
    System.out.println(helloRpc.sayHello("qiyu"));   // 打印: hello, qiyu (from dubbo)
}
```

```yaml
# demo-consumer 的 application.yml（注意 consumer 无需配 protocol.port）
dubbo:
  application: { name: demo-consumer, qos-enable: false }
  registry: { address: nacos://127.0.0.1:8848 }
```

### 8.3 验证与玩法

```bash
# 先起 provider 再起 consumer，Nacos 控制台「服务列表」应出现 demo-provider
# 玩法1：再起一个 provider 实例（改 port=20881），观察消费端负载均衡到两台
# 玩法2：kill 掉一台 provider，调用自动切到另一台 —— 体会 failover + 注册中心摘除
# 玩法3：把 retries 改成 2 并在实现里随机抛异常，观察重试次数变化
# 玩法4：给 provider 加第二版实现 @DubboService(version="2.0")，
#        consumer 指定 version 切换 —— 体会多版本灰度
```

---

## 九、常见坑与面试题

### 9.1 本项目真实踩过的坑

| 坑 | 现象 | 根因与解法 |
|---|---|---|
| `check=false` 太宽松 | 服务"带病启动"，调用时才炸 no provider | 启动自检（QiyuProviderStartupVerifier）手动强检 + exit(1) |
| im-core 注册地址错 | 弹幕收不到 | 容器/多网卡下 `System.exit` 前必须设 `DUBBO_IP_TO_REGISTRY`（注册出去的 IP 必须是消费方可达的） |
| QoS 端口冲突 | 第二个服务启动失败 | 本地起多个 dubbo 应用时 `qos-enable: false`（本项目全局关了） |
| 超时重试导致重复写 | 扣款/发消息执行了多次 | 写操作 `retries=0` 或消费端幂等 |

### 9.2 高频面试题速答

- **Dubbo 和 Spring Cloud 怎么选？** 内部高频 Java 服务互调选 Dubbo（性能、强类型、SPI 可定制）；对外/异构/团队熟 HTTP 选 Spring Cloud。Dubbo3 的 Triple 协议兼容 HTTP/2 + gRPC 后界限在模糊。
- **注册中心挂了还能调用吗？** 能。消费者本地缓存提供者列表，只是感知不到上下线变更。
- **Dubbo3 应用级服务发现是什么？** 2.x 是"接口级"注册（每个接口一条注册记录，接口多了注册中心数据爆炸）；3.x 改为"应用级"（一个应用一条记录 + 接口到应用的映射），数据量降一个数量级。本项目 3.2 两者兼容。
- **默认序列化是什么，有什么风险？** hessian2。反序列化漏洞是 RPC 框架共同风险，生产建议白名单类 + 升级 dubbo 修复版本；跨语言场景换 Protobuf。
- **怎么实现灰度发布？** version/group 隔离 + tag 路由（`RpcContext.getContext().setAttachment("dubbo.tag","gray")`）+ 管理台动态规则。
- **链路上想传用户身份怎么办？** RpcContext attachment（§七），或自定义 Filter 统一注入/提取——本项目网关把 userId 塞 HTTP header 传给 api 层，是同一思想在 HTTP 层的版本。

---

## 十、进阶路线

1. **读源码的顺序**（先框架主干再扩展点）：`DubboBootstrap`（启动装配）→ `RegistryProtocol.export`（服务暴露）→ `FailoverClusterInvoker`（容错）→ 自己写一个 Filter 和 LoadBalance 试试；
2. **Triple 协议**：Dubbo3 主推，基于 HTTP/2 兼容 gRPC，跨语言 + 网关友好——新项目可以考虑；
3. **Dubbo Admin / 服务治理**：动态配置（超时/权重热调整）、路由规则、服务测试——生产运维必备；
4. **与 Sentinel 结合**：接口级限流熔断（本项目 api 层用了自研 @RequestLimit，Dubbo Filter 接 Sentinel 是进阶玩法）；
5. **结合本项目练手**：给 gift-provider 写一个自定义 Filter 记录每次 RPC 耗时并打 WARN（慢调用），或给 IM 定点投递加 consistenthash 备选方案做对比实验——这两个都比"看文档"收获大得多。

> **一句话总结**：Dubbo = 强类型接口契约 + 注册中心寻址 + 长连接二进制传输 + SPI 全可定制。本项目的价值在于它不止"会用"——三层拆分、启动自检、自定义 Cluster SPI 定点投递，每个都是可以从"怎么用"讲到"为什么"的真实素材。
