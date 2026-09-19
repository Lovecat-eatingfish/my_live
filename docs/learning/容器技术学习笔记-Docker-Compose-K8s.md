# 容器技术学习笔记：Docker → Compose → Kubernetes

> 面向已会 Docker 基础（镜像/容器/常用命令）的进阶笔记。所有 demo 都可以独立跑，后半部分直接拿本项目的 `docker-compose.yml` / `docker-compose-full.yml` 当真实教材。

## 目录

1. [快速回顾：Docker 的心智模型](#一快速回顾docker-的心智模型)
2. [Docker Compose：单机多容器编排](#二docker-compose单机多容器编排)
3. [用本项目真实文件学 Compose](#三用本项目真实文件学-compose)
4. [Kubernetes：是什么、为什么](#四kubernetes是什么为什么)
5. [K8s 核心概念五件套](#五k8s-核心概念五件套)
6. [上手 Demo：minikube/kind 跑一个服务](#六上手-demominikubekind-跑一个服务)
7. [把本项目从 Compose 迁到 K8s 需要什么](#七把本项目从-compose-迁到-k8s-需要什么)
8. [Compose vs K8s 对照表 + 学习路线](#八compose-vs-k8s-对照表--学习路线)

---

## 一、快速回顾：Docker 的心智模型

已经会的可以跳过，只强调三个后面要用的点：

**1. 容器 = 受限的进程，不是虚拟机。** Linux 上靠 namespace（隔离视图：pid/net/mnt...）+ cgroups（限制资源）实现，所以容器启动是毫秒级的。

**2. 镜像分层。** Dockerfile 每条指令是一层，层可缓存、可共享。本项目 `docker/app.Dockerfile` 只有两层（基础镜像 + COPY app.jar），改代码重建镜像时只有 COPY 层变化，秒级构建。

**3. 容器网络。** 同一个 docker network 里的容器**可以用服务名互相访问**（Docker 内置 DNS）：`jdbc:mysql://mysql:3306` 里的 `mysql` 是容器名/服务名，不是 IP。这是理解 Compose 和 K8s 网络的基础。

```bash
docker run -d --name web nginx              # 最基础
docker build -t myapp:v1 .                   # 构建镜像
docker logs -f xxx / docker exec -it xxx sh  # 看日志/进容器
docker network create demo-net               # 自定义网络（容器名互通的前提）
```

---

## 二、Docker Compose：单机多容器编排

### 2.1 解决什么问题

手动起 6 个中间件要敲 6 条 `docker run`，参数（端口/卷/环境变量）各不相同、记不住、换台机器就丢。**Compose 把"一组容器的完整定义"写进一个 YAML，一条命令拉起/销毁/重建全套。**

### 2.2 核心字段逐个拆（背下这张表就够 80% 场景）

```yaml
services:                    # ② 一组容器（旧版叫 services 顶层键，每个键是服务名）
  mysql:
    image: mysql:8.0         # ③ 用现成镜像；自己构建则用 build:
    # build: ./backend       #   build: context + dockerfile，compose 会自动 docker build
    container_name: qiyu-mysql   # 容器名（不指定则自动生成"项目名-服务名-N"）
    restart: unless-stopped  # ④ 重启策略：no / always / on-failure / unless-stopped
    ports:
      - "3306:3306"          # ⑤ 宿主机端口:容器端口（只有需要从宿主机访问才映射）
    environment:             # ⑥ 环境变量（注入容器内进程）
      MYSQL_ROOT_PASSWORD: "123456"
    volumes:                 # ⑦ 数据卷：容器删了数据还在
      - ./docker-data/mysql:/var/lib/mysql   # 宿主机目录:容器目录（bind mount）
      - mydata:/data                          # 命名卷（声明在顶层 volumes）
    command: --default-authentication-plugin=mysql_native_password  # ⑧ 覆盖镜像默认启动命令
    depends_on:              # ⑨ 启动顺序（只保证"先启动"，不保证"已就绪"！）
      - redis
    healthcheck:             # ⑩ 就绪探针，配了它 depends_on 才有真正意义
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      retries: 5
    networks:                # ⑪ 加入自定义网络（可省，默认都在同一网络）
      - backend

volumes:                     # ① 顶层声明命名卷
  mydata:

networks:                    # ① 顶层声明网络
  backend:
```

### 2.3 必会命令

```bash
docker compose up -d              # 后台拉起全部（-d 不能忘，否则占住终端）
docker compose up -d mysql        # 只启动某个服务
docker compose ps                 # 查看本 compose 的容器状态
docker compose logs -f mysql      # 看某个服务日志
docker compose exec mysql sh      # 进入容器
docker compose down               # 停止并删除容器+网络（数据卷还在！）
docker compose down -v            # 连命名卷一起删（重置数据）
docker compose restart mysql      # 重启某个服务（改了挂载的配置文件后常用）
docker compose up -d --build      # 有 build: 的服务重新构建再启动
```

### 2.4 三个最常踩的概念坑

**坑 1：`depends_on` ≠ 等就绪。** 它只保证容器的**启动顺序**，MySQL 进程还在初始化时你的应用可能已经连上去了然后连接失败。正确做法：给被依赖方配 `healthcheck`，依赖方写：

```yaml
depends_on:
  mysql:
    condition: service_healthy   # 等到 healthcheck 通过才启动本服务
```
本项目的 `docker-compose-full.yml` 里就是这个写法（`nacos: condition: service_healthy`）。

**坑 2：bind mount 的路径是相对 compose 文件的。** `./docker-data/mysql:/var/lib/mysql` 中的 `./` 是 YAML 文件所在目录，不是你执行命令时所在目录。

**坑 3：`ports` 映射的端口全宿主机唯一。** 两个服务都映射 `3306:3306` 会直接报端口冲突；容器之间互访**不需要**映射端口（走内部网络），映射只是给宿主机/外部用的。

---

## 三、用本项目真实文件学 Compose

### 3.1 `docker-compose.yml`（本地开发中间件）里的进阶写法

**① profiles —— 按需启用的服务分组**（SRS 容器就是这么隔离的）：

```yaml
services:
  srs:
    image: ossrs/srs:5
    profiles:
      - linux-full        # 只有显式 --profile linux-full 才会启动
```
```bash
docker compose up -d                      # SRS 不会被启动
docker compose --profile linux-full up -d srs   # 显式启动 SRS
```
本项目用它解决：Windows 本机用原生 srs.exe（Docker UDP 转发丢包），SRS 容器只在 Linux 上用。

**② 命令行覆盖镜像的配置文件**（RocketMQ broker 自定义配置）：

```yaml
rmqbroker:
  volumes:
    - ./docker/broker.conf:/home/rocketmq/rocketmq-5.3.2/conf/broker.conf
  command: sh mqbroker -n rmqnamesrv:9876 -c /home/rocketmq/.../broker.conf
```
`broker.conf` 里 `brokerIP1 = 127.0.0.1`——**客户端必须拿到宿主机可达地址**，这是容器网络最经典的坑：容器内注册了自己的内网 IP（172.17.x.x），宿主机上的应用按注册地址连过去就超时。

**③ 初始化脚本挂载**（MySQL 首次建库建表）：

```yaml
volumes:
  - ./sql:/docker-entrypoint-initdb.d
```
MySQL 官方镜像首次初始化（数据目录为空）时自动执行该目录下 `.sql/.sh`。**注意只在首次生效**——之后新增的建表脚本要手动执行。

### 3.2 `docker-compose-full.yml`（全容器化）里的进阶写法

**① 用环境变量做"配置覆盖"**——这是理解本项目容器化部署的钥匙：

```yaml
api:
  environment:
    EXTRA_ARGS: >-
      --spring.cloud.nacos.config.server-addr=nacos:8848
      --dubbo.registry.address=nacos://nacos:8848?namespace=...&&username=nacos&&password=nacos
```
`EXTRA_ARGS` 通过 Dockerfile 的 `ENTRYPOINT ... ${EXTRA_ARGS}` 拼到启动命令尾部，**Spring 启动参数优先级高于 jar 内配置**——换 Nacos 地址只改 YAML 不用重打镜像。`>-` 是 YAML 的折叠块语法（多行拼成一行，去换行）。

**② 服务发现用容器名，注册地址问题被天然化解**：所有服务在同一个 compose 网络里，Nacos 地址写 `nacos:8848`（服务名）。但注意 Dubbo/IM 这类要"把自身地址注册出去"的服务，注册出去的 IP 必须是**对方能路由到的**——本项目 im-core-server 靠 `DUBBO_IP_TO_REGISTRY` 环境变量解决，迁移到 K8s 后这个问题换成了 Headless Service/Pod IP 的新形态（见 §七）。

**③ `restart: unless-stopped` 兜底启动顺序**：compose 不表达服务间依赖时，先起的服务会因为依赖没就绪失败，`restart` 策略让它自动重试直到上游就绪——这是"没有 K8s 重试机制时的穷人版自愈"。

---

## 四、Kubernetes：是什么、为什么

### 4.1 一句话定位

> Compose 管**一台机器上**的一组容器；K8s 管**一群机器上**的大量容器——调度到哪台机器、挂了自动重启、流量自动分发、滚动升级不中断、按 CPU 自动扩缩容。

| 你在 Compose 里手动干的活 | K8s 自动干的活 |
|---|---|
| `restart: unless-stopped` 兜底 | 控制器持续对比"期望副本数 vs 实际数"，挂了自动重建（**自愈**） |
| 容器挤一台机器，内存不够 | 调度器按资源请求/节点负载分配 Pod（**调度**） |
| 改镜像要 down + up，服务中断 | 滚动更新：逐个换新 Pod，就绪后才切流量（**发布**） |
| 流量只在单机内部分 | Service 负载均衡到多个副本（**服务发现与负载均衡**） |
| 压力大只能手动加容器 | HPA 按 CPU/内存自动扩缩容（**弹性**） |
| 每台机器配一遍配置 | ConfigMap/Secret 统一下发配置（**配置管理**） |

### 4.2 架构（面试级理解就够）

```text
┌────────────────── 控制平面 Control Plane（大脑）──────────────────┐
│  kube-apiserver      唯一入口，所有操作都是"向 API Server 提交/查询对象"     │
│  etcd                存整个集群状态的分布式 KV 库                          │
│  kube-scheduler      决定新 Pod 调度到哪个节点（算资源、亲和性）               │
│  controller-manager  各种控制器：副本数、节点健康、滚动更新...                  │
└──────────────────────────────────────────────────────────────┘
┌────────────────── 工作节点 Node（干活的机器，N 台）────────────────┐
│  kubelet            节点代理：按 API Server 的指令管理本机容器              │
│  kube-proxy         维护 Service 的转发规则（iptables/IPVS）               │
│  容器运行时          containerd / docker                              │
└──────────────────────────────────────────────────────────────┘
```

**声明式 API 是 K8s 的灵魂**：你不告诉它"执行 A 再执行 B"，而是提交一份"期望状态"（我要 3 个副本、用这个镜像），控制器**持续**把现实状态向期望状态收敛。这和 Compose 的"命令式 up"有本质区别。

---

## 五、K8s 核心概念五件套

### 5.1 Pod —— 最小部署单元

Pod = 1 个或多个紧耦合容器的组合（共享网络/存储）。**实际中 99% 是 1 Pod 1 容器**。Pod 是易逝的（挂了重建，IP 会变），所以永远不直接访问 Pod IP。

### 5.2 Deployment —— 管理 Pod 的"期望状态"

你要几副本、用什么镜像、怎么更新——都写在 Deployment 里，它帮你维持。

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: qiyu-api
  labels: { app: qiyu-api }
spec:
  replicas: 2                          # 期望 2 个副本，少一个自动补
  selector:
    matchLabels: { app: qiyu-api }     # 通过 label 认领自己管的 Pod
  template:                            # Pod 模板（Pod 被删了就按这个重建）
    metadata:
      labels: { app: qiyu-api }
    spec:
      containers:
        - name: api
          image: qiyu-live/qiyu-live-api:dev
          ports:
            - containerPort: 38085
          env:
            - name: JAVA_OPTS
              value: "-Xms128m -Xmx384m"
          resources:                   # 资源申请/上限（调度依据 + 防止吃光节点）
            requests: { cpu: "250m", memory: "512Mi" }   # 250m = 0.25 核
            limits:   { cpu: "1",    memory: "1Gi" }
          readinessProbe:              # 就绪探针：没过=不接流量（对应 compose 的 healthcheck）
            httpGet: { path: /live/api/living/list, port: 38085 }
            initialDelaySeconds: 30
          livenessProbe:               # 存活探针：连续失败=重启容器
            httpGet: { path: /live/api/living/list, port: 38085 }
            initialDelaySeconds: 60
```

### 5.3 Service —— 稳定的访问入口

Pod 会死会重建 IP 会变，Service 用**虚拟 IP + label 选择器**提供稳定入口，并负载均衡到背后的 Pod。

```yaml
apiVersion: v1
kind: Service
metadata:
  name: qiyu-api-svc
spec:
  selector:
    app: qiyu-api            # 转发给所有带此 label 的 Pod
  ports:
    - port: 38085            # Service 自己的端口（集群内访问用）
      targetPort: 38085      # Pod 容器端口
  type: ClusterIP            # 见下方三种 type
```

**Service 三种 type（按暴露范围递增）：**

| type | 暴露范围 | 类比 Compose |
|---|---|---|
| `ClusterIP`（默认） | 集群内部互访 | compose 网络里用服务名互访 |
| `NodePort` | 每个节点开一个端口（30000-32767）供外部访问 | `ports: "38080:38080"` |
| `LoadBalancer` | 云厂商分配一个外部负载均衡 IP | 公网入口 |

**特殊形态 Headless Service**（`clusterIP: None`）：不做负载均衡，DNS 直接返回所有 Pod IP——给需要"客户端自己挑实例"的场景用（Dubbo 直连、IM 定点投递这类）。

### 5.4 ConfigMap / Secret —— 配置与敏感信息

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: qiyu-config
data:
  NACOS_ADDR: "nacos:8848"
---
apiVersion: v1
kind: Secret
metadata:
  name: qiyu-secret
type: Opaque
stringData:            # 明文写，K8s 存储时转 base64（不是加密，只是编码）
  DB_PASSWORD: "123456"
```
Pod 里通过 `envFrom.configMapRef` 注入环境变量，或挂成文件。**对应本项目：Nacos 里的 qiyu-live-*.yaml 就是配置中心角色，K8s 原生方案是 ConfigMap+Secret，也可以继续用 Nacos（两者不冲突）**。

### 5.5 常用命令（kubectl）

```bash
kubectl apply -f xxx.yaml      # 提交/更新期望状态（声明式的"up"）
kubectl get pods -o wide       # 看所有 Pod（-w 持续 watch）
kubectl describe pod qiyu-api-xxxx   # 看某 Pod 详情（排错第一步：Events 部分）
kubectl logs -f qiyu-api-xxxx  # 看日志
kubectl exec -it qiyu-api-xxxx -- sh   # 进容器（和 docker exec 几乎一样）
kubectl scale deployment qiyu-api --replicas=5   # 手动扩容
kubectl rollout restart deployment qiyu-api      # 滚动重启
kubectl rollout undo deployment qiyu-api         # 回滚到上一版
kubectl get svc / get deploy / get events --sort-by=.lastTimestamp
```

**其他常见对象扫盲**（知道用途即可）：`StatefulSet`（有状态服务，稳定的主机名/存储，如 MySQL）、`DaemonSet`（每节点跑一个，如日志采集）、`Job/CronJob`（一次性/定时任务，如对账 Job）、`Ingress`（七层路由，把不同域名/路径转发到不同 Service，相当于集群级网关）、`HPA`（自动扩缩容）、`PV/PVC`（持久卷，对应 compose 的 volumes）、`Namespace`（逻辑隔离，类似本项目按 dev/prod 分 namespace）。

---

## 六、上手 Demo：minikube/kind 跑一个服务

### 6.1 装环境（二选一）

```bash
# minikube（自带 UI，入门推荐）
# Windows: choco install minikube 或下载安装包；需要 Docker Desktop 或 Hyper-V 做驱动
minikube start --cpus=4 --memory=6g
minikube dashboard                      # 打开 Web 控制台

# kind（Kubernetes in Docker，更轻，CI 常用）
# choco install kind；本地已有 Docker Desktop 即可
kind create cluster --name qiyu-learn
kubectl cluster-info
```

### 6.2 Demo 1：跑一个 nginx（10 分钟理解 Pod/Deployment/Service）

```yaml
# demo-nginx.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-demo
spec:
  replicas: 2
  selector:
    matchLabels: { app: nginx-demo }
  template:
    metadata:
      labels: { app: nginx-demo }
    spec:
      containers:
        - name: nginx
          image: nginx:latest
          ports: [{ containerPort: 80 }]
---
apiVersion: v1
kind: Service
metadata:
  name: nginx-svc
spec:
  type: NodePort
  selector: { app: nginx-demo }
  ports:
    - port: 80
      targetPort: 80
      nodePort: 30080
```

```bash
kubectl apply -f demo-nginx.yaml
kubectl get pods -o wide          # 看到 2 个 Pod，分到不同"节点"
kubectl get svc nginx-svc         # 拿到 30080 端口
curl http://localhost:30080       # minikube 需先: minikube service nginx-svc
# 体会自愈：手动杀一个 Pod
kubectl delete pod <某个pod名>
kubectl get pods -w               # 立刻有新 Pod 在重建 —— 这就是控制器在工作
# 体会滚动更新
kubectl set image deployment/nginx-demo nginx=nginx:1.25
kubectl rollout status deployment/nginx-demo
```

### 6.3 Demo 2：部署一个"有依赖"的应用（模拟本项目的 api + mysql）

```yaml
# demo-app.yaml —— 练 healthcheck + service 依赖 + configmap
apiVersion: v1
kind: ConfigMap
metadata:
  name: demo-cfg
data:
  SPRING_DATASOURCE_URL: "jdbc:mysql://mysql-svc:3306/qiyu_live_user"
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mysql-demo
spec:
  replicas: 1
  selector: { matchLabels: { app: mysql-demo } }
  template:
    metadata: { labels: { app: mysql-demo } }
    spec:
      containers:
        - name: mysql
          image: mysql:8.0
          env: [{ name: MYSQL_ROOT_PASSWORD, value: "123456" }]
          readinessProbe:                      # 没这个，api 起来时 mysql 还没就绪
            exec: { command: ["mysqladmin", "ping", "-h", "localhost"] }
            initialDelaySeconds: 20
---
apiVersion: v1
kind: Service
metadata: { name: mysql-svc }
spec:
  selector: { app: mysql-demo }
  ports: [{ port: 3306, targetPort: 3306 }]
---
apiVersion: apps/v1
kind: Deployment
metadata: { name: api-demo }
spec:
  replicas: 1
  selector: { matchLabels: { app: api-demo } }
  template:
    metadata: { labels: { app: api-demo } }
    spec:
      containers:
        - name: api
          image: busybox
          command: ["sh", "-c", "echo 连接 $SPRING_DATASOURCE_URL; sleep 3600"]
          envFrom: [{ configMapRef: { name: demo-cfg } }]
```

练习点：mysql-svc 这个 DNS 名在 api 容器里 `ping` 得通（K8s 集群 DNS，等价 compose 的服务名互访）；把 mysql 的 readinessProbe 删掉重启看 api 的报错——这就是 compose `depends_on` 和 K8s `readinessProbe` 的区别。

### 6.4 Demo 3（可选）：本地镜像进集群

K8s 默认从镜像仓库拉镜像，本地 `docker build` 的镜像 Pod 会 ImagePullBackOff：

```bash
# minikube：直接用宿主机 docker daemon 构建
eval $(minikube docker-env)      # 当前终端的 docker 命令实际操作 minikube 内部
docker build -t qiyu-live/qiyu-live-api:dev -f docker/app.Dockerfile <构建上下文>
# yaml 里 imagePullPolicy: IfNotPresent 即可用本地镜像
# kind：kind load docker-image qiyu-live/qiyu-live-api:dev --name qiyu-learn
```

---

## 七、把本项目从 Compose 迁到 K8s 需要什么

理解了上面的概念，回头看 `docker-compose-full.yml` 的 23 个服务，映射关系是：

| Compose 里的东西 | K8s 对应物 | 本项目注意点 |
|---|---|---|
| 每个 `services:` 下的无状态服务 | Deployment（replicas: 1 起步） | 14 个 provider/api/gateway 都是无状态的，直接迁 |
| `EXTRA_ARGS` 环境变量 | ConfigMap + `envFrom`，或 Helm values 模板化 | 换 namespace/地址从"改 compose"变成"改 ConfigMap" |
| compose 网络 + 容器名互访 | ClusterIP Service + 集群 DNS | `nacos:8848` → `nacos-svc:8848`，几乎无痛 |
| MySQL/Redis/RocketMQ 容器 | **生产环境不这么跑**——用云厂商托管服务，或 StatefulSet + PV | 学习阶段可用 StatefulSet 玩，真实部署推荐托管 |
| SRS（WebRTC UDP 8000 + RTMP 1935） | Service type: NodePort / LoadBalancer + `externalTrafficPolicy: Local` | UDP 大流量是 K8s 网络的难点，公网集群要谨慎 |
| im-core-server 的 `DUBBO_IP_TO_REGISTRY` | Pod IP 直注册（容器网络互通）或 Headless Service | K8s 里 Pod IP 集群内全通，比 compose 跨主机更顺 |
| gateway 入口 38080 | Ingress（域名/路径路由）或 LoadBalancer Service | `/live/api/**` 就是一条 Ingress 规则 |
| `restart: unless-stopped` 兜底 | Deployment 自愈 + readiness/liveness 探针 | 不再需要"失败重试"这种穷人版自愈 |
| 15 个服务启动顺序 | **不需要**：K8s 探针 + Dubbo 懒加载重试天然解决 | 本项目启动自检（QiyuProviderStartupVerifier）在 K8s 里要放宽，否则 strict 模式会 CrashLoopBackOff |
| docker-data 数据卷 | PV/PVC（persistent volume claim） | MySQL 数据、MinIO 对象、SRS DVR 录像都要挂 PVC |

**一个真实的迁移难点预告**（面试聊到这里就够深了）：本项目 im-router 通过 Redis 里记录的 `ip:port` 定点投递消息给 im-core-server。K8s 里 Pod 重建 IP 就变，Redis 里的绑定地址会失效——方案是把 im-core 改造成 StatefulSet（稳定网络标识）或 Headless Service + 客户端发现，这正是"有状态长连接服务上 K8s"的经典课题。

---

## 八、Compose vs K8s 对照表 + 学习路线

### 8.1 概念对照速查

| 我要做什么 | Compose 写法 | K8s 写法 |
|---|---|---|
| 起一组服务 | `docker compose up -d` | `kubectl apply -f .` |
| 定义一个服务 | `services: xxx:` | Deployment + Service（两个 YAML） |
| 服务间互访 | 服务名（compose 内置 DNS） | Service 名（kube-dns） |
| 数据持久化 | `volumes:` bind mount / 命名卷 | PV/PVC |
| 配置注入 | `environment:` | ConfigMap / Secret |
| 就绪检查 | `healthcheck:` | `readinessProbe:` |
| 崩溃重启 | `restart:` 策略 | Deployment 控制器自动重建 |
| 多副本 | （不支持，只有 1 台机器） | `replicas: N` + Service 负载均衡 |
| 滚动升级/回滚 | down + up（中断） | `rollout` 原生支持，零中断 |
| 跨多台机器 | ❌ | ✅（集群的本质） |
| 自动扩缩容 | ❌ | HPA |

### 8.2 建议的学习路线（结合本项目）

1. **第一周**：跑通 §六 Demo 1/2，把 Deployment/Service/Pod/探针四个概念亲手摸一遍；`kubectl describe` 的 Events 字段学会看；
2. **第二周**：给自己的 `docker-compose-full.yml` 挑 2 个无状态服务（比如 gateway + api）手写 K8s YAML 跑进 kind/minikube，体会"Service 互访 + ConfigMap 注入"；
3. **第三周**：学 Ingress（把 gateway 的路由迁过去）+ StatefulSet（理解为什么 MySQL 不该用 Deployment 跑）；
4. **之后**：Helm（K8s 的"包管理器"，模板化 YAML——本项目 15 个服务手写 YAML 会写到怀疑人生，Helm chart 一份模板参数化出 15 份）、HPA、以及面试常问的"Pod 生命周期/滚动更新策略/Service 原理(iptables vs IPVS)"。

> **一句话总结三者关系**：Docker 解决"单个应用怎么打包运行"，Compose 解决"一台机器上怎么编排一组容器"，K8s 解决"一个集群上怎么编排生产级的容器系统"——规模逐级放大，核心思想（声明式、期望状态、自愈）在 K8s 集大成。
