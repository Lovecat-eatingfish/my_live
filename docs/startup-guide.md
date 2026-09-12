# 旗鱼直播平台 —— 服务启动指南

> 配套端口表见 [ports.md](ports.md)。本文件讲**启动顺序、一键脚本、验证方法、常见排错**。

---

## 一、前置条件（先启动中间件）

启动任何业务服务前，确保以下已就绪：

| 中间件 | 地址 | 说明 |
|--------|------|------|
| Nacos | `127.0.0.1:8848` | 命名空间需先建好：`ef63e53e-94c8-4b1c-865e-6824177b2893`（本地**不走配置中心**，命名空间里无需导入任何配置） |
| MySQL | `127.0.0.1:3306` | `root/123456`，需建好 `qiyu_live_*` 各库（gift/bank/living/msg/user/common） |
| Redis | `127.0.0.1:6379` | 无密码 |
| RocketMQ | NameServer `127.0.0.1:9876` + Broker `10911` | 默认配置 |

> Nacos 命名空间创建方式：控制台 → 命名空间 → 新建命名空间，**命名空间 ID** 填 `ef63e53e-94c8-4b1c-865e-6824177b2893`（手填，不要用自动生成的）。

---

## 二、启动顺序（按 Dubbo 依赖分层）

Dubbo 服务有上下游依赖，**底层先起**。各层及其 Dubbo 端口（30000 段）如下：

| 顺序 | 层 | 模块 (Dubbo端口) | 依赖的上游 | 暴露的主要 RPC |
|------|----|------------------|-----------|----------------|
| 1 | Wave0 基础 | qiyu-live-bank-provider (30001) | — | IQiyuCurrencyAccountRpc, IPayProductRpc, IPayOrderRpc |
| 2 | | qiyu-live-account-provider (30002) | — | IAccountTokenRPC |
| 3 | | qiyu-live-id-generate-provider (30003) | — | IdGenerateRpc |
| 4 | | qiyu-live-im-provider (30004) | — | ImTokenRpc, ImOnlineRpc |
| 5 | | qiyu-live-stream-provider (30005) | — | ILivingStreamRpc, ILivingPlayBackRpc |
| 6 | Wave1 | qiyu-live-user-provider (30006) | →id-generate | IUserRpc, IUserTagRpc, IUserPhoneRPC |
| 7 | | qiyu-live-im-core-server (30007) | →im-provider | IRouterHandlerRpc |
| 8 | | qiyu-live-gateway (HTTP 38080) | →account-provider | 网关入口（不开放 Dubbo 端口） |
| 9 | | qiyu-live-bank-api (HTTP 38201) | →bank-provider | 支付回调入口 |
| 10 | Wave2 | qiyu-live-im-router-provider (30008) | →im-core-server | ImRouterRpc |
| 11 | Wave3 | qiyu-live-living-provider (30009) | →im-router-provider | ILivingRoomRpc |
| 12 | Wave4 | qiyu-live-msg-provider (30010) | →im-router, →living | ISmsRpc |
| 13 | | qiyu-live-gift-provider (30011) | →im-router, →bank, →living | IGiftConfigRpc, IRedPacketRpc, ISkuOrderRpc, ISkuRpc … |
| 14 | Wave5 入口 | qiyu-live-api (HTTP 38100) | 几乎全部 | 前端主 API |

> **说明**：Dubbo + Nacos 下 `@DubboReference` 默认懒加载，理论上乱序也能启动（消费端会重试重连）。分层顺序只为减少启动期 "no provider" 报错噪音。

---

## 三、一键启动

### Windows（推荐）

```bat
:: 1. 启动中间件（docker compose + 等待就绪 + 自动创建 Nacos 命名空间）
scripts\start-all.bat infra

:: 2. 一键构建（若缺 jar）并按 Wave 顺序启动全部 14 个服务
scripts\start-all.bat

:: 3. 启动前端 dev server（可选）-> http://localhost:3000
scripts\start-all.bat web
```

等价 PowerShell：
```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1 infra
powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1 start
powershell -ExecutionPolicy Bypass -File scripts\start-all.ps1 web
```

> **JDK 17 要求**：系统默认 `java` 可能是 1.8，脚本默认使用 `D:\enviroment\javaenviroment\jdk17`（构建和运行都会切换到它）。路径不同时设置环境变量 `$env:QIYU_JAVA_HOME = "你的JDK17路径"`。

### 通用子命令（bat / ps1 均支持）
| 命令 | 作用 |
|------|------|
| `start-all.bat infra` | `docker compose up -d` 起全部中间件，探测端口就绪，自动建 Nacos 命名空间 |
| `start-all.bat build` | 仅构建 `mvn clean install -DskipTests` |
| `start-all.bat` / `... start` | 起 infra(若需) + 构建(若缺 jar) + 按 Wave 顺序启动全部服务 |
| `start-all.bat web` | 启动 web_live 前端 vite dev server（http://localhost:3000） |
| `start-all.bat status` | 查看中间件端口 + 各服务运行状态 |
| `start-all.bat logs qiyu-live-api` | 实时查看某模块日志（Ctrl+C 退出） |
| `start-all.bat stop` | 停止全部（含 web） |
| `start-all.bat restart` | 重启 |

脚本逻辑：
- 启动服务前自动起中间件并探测 MySQL/Redis/Nacos/RocketMQ 端口就绪；
- 自动通过 Nacos API 创建命名空间 `qiyu-live-test`（ID `ef63e53e-...`，无需手动建）；
- 任一模块缺 jar 自动先 `mvn clean install -DskipTests`；
- 按 Wave0→Wave5 顺序启动，每波间隔 10s（`STARTUP_WAIT` 可调）；
- 每个服务独立后台进程，日志在 `logs/<模块名>.log`（错误流 `logs/<模块名>.err.log`），pid 在 `logs/<模块名>.pid`；
- `qiyu-live-im-core-server` 启动时自动注入 `DUBBO_IP_TO_REGISTRY=127.0.0.1`、`DUBBO_PORT_TO_REGISTRY=30007`（Netty 服务器在 Redis 中注册身份用，缺失会启动失败）；
- 关掉窗口服务不会退出（`Start-Process` 脱离父进程）。

### 可选环境变量
```bat
set JAVA_OPTS=-Xms64m -Xmx256m   :: 14 个 JVM 较吃内存，机器吃力可调小
set STARTUP_WAIT=15
scripts\start-all.bat start
```
不需要的模块：编辑 `scripts/start-all.ps1`（或 `.sh`），把对应 `WAVE*` 数组里的模块名注释掉即可。

---

## 四、验证启动成功

1. **进程状态**：`scripts\start-all.bat status`，14 个都显示 `running`。
2. **Nacos 服务列表**：打开 `http://127.0.0.1:8848/nacos`（nacos/nacos），「服务管理 → 服务列表」，命名空间选 `ef63e53e-...`，应能看到 14 个服务名（qiyu-live-*）均有 1 个实例。
3. **HTTP 入口**：
   - 网关：`http://127.0.0.1:38080/live/api/living/list` （应返回 JSON）
   - API：`http://127.0.0.1:38100/live/api/living/list`
4. **日志**：`scripts\start-all.bat logs qiyu-live-api`，看到 `Started ApiWebApplication` 且无异常即成功。其余模块同理。

---

## 五、常见问题排查

| 现象 | 原因 / 解决 |
|------|------------|
| 启动卡住 / Dubbo 报 `no provider` | 上游 provider 未起全。按 Wave 顺序确认；或忽略，Dubbo 会自动重连重试 |
| `Address already in use: 3000x` | 端口被旧进程占用。`start-all.bat stop` 后再启动；或 `netstat -ano\|findstr 3000x` 查 pid 杀掉 |
| Nacos 里看不到某服务 | bootstrap.yaml 的 namespace 与建的不一致；或 Nacos 未启动 |
| 报 `Could not autowire ... @DubboReference` | 对应 provider 模块没起来，或其 jar 未构建（先 `build`） |
| MySQL 报 `Access denied` / 库不存在 | 确认 root/123456，并建好 `qiyu_live_gift/bank/living/msg/user/common` 库 |
| RocketMQ 消费者启动失败 | NameServer 未起，或 Broker 没注册；确认 `127.0.0.1:9876` 可达 |
| Redis 连接失败 | 确认 6379 已起且无密码 |
| 内存不足 / 启动很慢 | 14 个 JVM 吃内存，用 `JAVA_OPTS=-Xms64m -Xmx256m` 调小，或关掉不用的模块 |

---

## 六、手动单个启动（调试用）

```bat
:: 先构建一次（生成各模块 jar）
mvn clean install -DskipTests

:: 单独启动某模块（PowerShell，注意用 JDK17；im-core-server 还需注入注册地址环境变量）
& "D:\enviroment\javaenviroment\jdk17\bin\java.exe" -jar qiyu-live-api\target\qiyu-live-api-docker.jar

:: im-core-server 必须先设置（否则启动直接抛异常）：
$env:DUBBO_IP_TO_REGISTRY = "127.0.0.1"; $env:DUBBO_PORT_TO_REGISTRY = "30007"
```
> 部分模块 finalName 是 `xxx-docker.jar`（api/gateway/account/id-generate/gift），其余是 `xxx-1.0-SNAPSHOT.jar`。`logs/` 下脚本会自动识别。


# 识别内容

## 前置（先起）：Nacos (8848) → MySQL (3306) → Redis (6379) → RocketMQ (9876)

## 业务服务（按此顺序）：

表格

| # | 模块 | Dubbo 端口 |
| --- | --- | --- |
| 1 | qiyu-live-bank-provider | 30001 |
| 2 | qiyu-live-account-provider | 30002 |
| 3 | qiyu-live-id-generate-provider | 30003 |
| 4 | qiyu-live-im-provider | 30004 |
| 5 | qiyu-live-stream-provider | 30005 |
| 6 | qiyu-live-user-provider | 30006 |
| 7 | qiyu-live-im-core-server | 30007 |
| 8 | qiyu-live-gateway | (HTTP 38080) |
| 9 | qiyu-live-bank-api | (HTTP 38201) |
| 10 | qiyu-live-im-router-provider | 30008 |
| 11 | qiyu-live-living-provider | 30009 |
| 12 | qiyu-live-msg-provider | 30010 |
| 13 | qiyu-live-gift-provider | 30011 |
| 14 | qiyu-live-api | (HTTP 38100) |

>
> 底部备注：
> **1–5 是基础层（无依赖，先起）；6–9 依赖基础层；10–13 逐层依赖；14（api）最后起。乱序也能起，Dubbo 会自动重连。**
