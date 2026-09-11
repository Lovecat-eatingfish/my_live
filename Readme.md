# 旗鱼直播平台 (Qiyu Live)

旗鱼直播平台后台服务，基于 Java 17 + Spring Boot 3.0.4 构建的分布式直播系统。

## 技术栈

- **Spring Boot 3.0.4** — 基础框架
- **Dubbo 3.2** — RPC 服务治理
- **Netty** — IM 实时通讯
- **Nacos** — 服务注册与配置中心
- **ShardingJDBC** — 数据库分库分表
- **Redis** — 缓存与分布式锁
- **RocketMQ** — 异步消息队列
- **Gateway** — API 网关

## 模块结构

```text
qiyu-live-app/
├── qiyu-live-framework/       # 公共starter（datasource、redis、mq、web）
├── qiyu-live-common-interface/ # 公共接口、DTO、Topic常量
├── qiyu-live-account/         # 用户认证Token
├── qiyu-live-user/             # 用户管理
├── qiyu-live-living/           # 直播间管理
├── qiyu-live-gift/             # 礼物系统
├── qiyu-live-bank/             # 支付订单
├── qiyu-live-msg/              # 消息处理
├── qiyu-live-im/               # IM业务逻辑
├── qiyu-live-im-router/        # IM用户路由
├── qiyu-live-im-core-server/   # Netty IM服务器
├── qiyu-live-id-generate/       # 分布式ID生成
├── qiyu-live-api/              # Web API网关
├── qiyu-live-gateway/          # 鉴权网关
└── web_live/                   # 前端项目
```

## 构建

```bash
mvn clean install -DskipTests
```

## 本地开发

- Nacos 配置中心：远程服务器，namespace: `qiyu-live-test`
- 所有 Provider 服务使用 `WebApplicationType.NONE`（无嵌入式 servlet 容器）
- IM Core Server 独立启动 Netty 端口（非 HTTP）
