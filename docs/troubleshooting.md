# 故障排查记录（持续追加）

> 由 `troubleshooting-2026-09-12.md` 与 `im-duplicate-message-analysis.md` 合并而来，新坑直接往后追加。

# 故障排查记录（2026-09-12）

> 这一天围绕"SRS 直播推流接入"排查了 5 个问题，每个都列了**现象 → 根因 → 修复 → 以后怎么避坑**。
> 相关改动已落到代码/配置里，本文只讲原因，方便以后回忆。

---

## 1. IDEA 编译报错：`程序包 org.qiyu.live.account.interfaces 不存在`

- **现象**：`AccountTokenRPCImpl.java:5` 编译失败，提示接口包不存在。
- **排查**：
  - 本地 Maven 仓库 `D:\repo\org\idea\qiyu-live-account-interface\1.0-SNAPSHOT\` 里有 jar；
  - 解包确认 jar 内**确实有** `org/qiyu/live/account/interfaces/IAccountTokenRPC.class`；
  - 源码、仓库、版本号（1.0-SNAPSHOT）三方一致。
- **根因**：**IDEA 的 Maven 工程没有重新导入**。`qiyu-live-account-provider/pom.xml` 在 git 里是修改状态（版本号从 1.0.1 等有过调整），pom 变更后 IDEA 的模块依赖关系还停留在旧快照上，编译时没把 `qiyu-live-account-interface` 模块挂到 account-provider 的类路径里，于是报"包不存在"。**代码和仓库本身都没问题。**
- **修复**：IDEA 右侧 Maven 面板 → **Reload All Maven Projects**（刷新图标），然后 Build → Rebuild Project。已全量执行过 `mvn clean install -DskipTests`，本地仓库构件全部是新的。
- **避坑**：任何 pom.xml 改动（尤其版本号、新增依赖）之后，先刷新 Maven 再编译；遇到"包不存在"先 `jar tf` 解包验证仓库里的 jar，再怀疑代码。

---

## 2. SRS "配置改了没反应/播放 404"：宿主机残留 srs.exe 抢占端口 ⭐ 最大坑

- **现象**：反复修改 `docker/srs.conf`、重启 `qiyu-srs` 容器，HTTP-FLV 依然 404；推流回调却又能到达后端；SRS 日志里看不到自己推的流。
- **根因**：宿主机上残留着一个**原生 Windows 版 SRS**（`D:\softwarepackage\development\srs_v5\SRS\objs\srs.exe`，旧实验留下的）直接监听 1935/1985/8080。Windows 侧所有连接（ffmpeg 推流、curl、浏览器）都被它截走——它用的是旧配置（hooks 指向 38101 所以回调通、没开 http_remux 所以 FLV 404、DVR 落在 `D:/softwarepackage/...`）。**docker 容器自始至终没参与服务。**
- **佐证技巧**：`curl http://127.0.0.1:1985/api/v1/versions` 看版本号——原生是 5.0.185，容器镜像是 5.0.213，版本号一换就暴露了。
- **修复**：`taskkill /F /PID <srs.exe的pid>`，只保留 docker 的 `qiyu-srs`。
- **避坑**：凡是"改了配置没生效"，第一步先 `netstat -ano | findstr :1935` 看监听进程是谁（`Get-Process -Id <pid>` 看 Path），确认流量真的到容器。

## 3. Docker 容器 host 网络从 Windows 不可达（mirrored 模式的坑）

- **现象**：杀掉残留 srs.exe 后，Windows 连 `127.0.0.1:1985` 直接 connection refused——容器明明监听着。
- **根因**：`.wslconfig` 虽然开了 `networkingMode=mirrored`，但 **Docker Desktop 的容器跑在它自己的后端 VM（docker-desktop 发行版）里，host 网络绑定不参与 Windows 的镜像网络**。之前 compose 里"host 网络 + mirrored 所以原生可达"的注释是错误假设。
- **修复**：SRS 容器改回**端口映射模式**（`-p 1935/1985/8080/8000tcp/8000udp`），docker-compose.yml 已同步更新。TCP 映射没问题；WebRTC 的 UDP 映射质量待浏览器实测。
- **连带修复**：端口映射后容器内 `127.0.0.1` 不再是宿主机，`srs.conf` 的 http_hooks 地址必须用 `http://host.docker.internal:38101/...`（已改）。
- **避坑**：Docker Desktop 上不要用 `network_mode: host` 访问宿主机服务，一律端口映射 + `host.docker.internal`。

## 4. stream-provider Dubbo 注册丢失：`createPushUrl` 报 500 "No provider available"

- **现象**：`/stream/createPushUrl` 500，api 日志抛 `No provider available from registry ... ILivingStreamRpc`；但 38101 端口的 HTTP 回调一切正常。
- **根因**：stream-provider 的 **Dubbo 接口级注册被 Nacos 摘除了**（服务名还在、实例列表为空）。该进程中途经历过网络波动/休眠，Spring Cloud 的注册（心跳独立）自己恢复了，Dubbo 的 nacos 客户端没有重新注册。多见于电脑睡眠后。
- **修复**：重启 stream-provider 即恢复。
- **避坑**：怀疑注册问题时用 Nacos API 精确查实例列表（服务列表里"服务名在"不等于"实例在"）：
  `GET /nacos/v1/ns/instance/list?serviceName=providers:org.qiyu.live.stream.interfaces.rpc.ILivingStreamRpc::&namespaceId=<ns>`
  另外 PATH 里默认 java 是 **JDK 8**，手动起服务必须用 `D:\enviroment\javaenviroment\jdk17\bin\java.exe`。

## 5. Git Bash (MSYS) 路径转换改写命令参数

- **现象**：给 java 传 `--qiyu.srs.dvr-container-prefix=/data/dvr`，进程里实际变成 `D:/softwarepackage/Git/data/dvr`；docker 挂载路径也出现过类似改写。
- **根因**：Git Bash 的 MSYS 路径自动转换把以 `/` 开头的参数当作 POSIX 路径翻译成 Windows 路径。
- **修复**：命令前加 `MSYS_NO_PATHCONV=1`。
- **避坑**：在 Git Bash 里给 java -jar 传路径参数、docker 传容器内绝对路径时，一律加这个前缀。

## 6. 顺带修掉的小问题

- `srs.conf` vhost 缺 `http_remux { enabled on; }`，HTTP-FLV 播放 404（SRS 不会自动把 RTMP 重封装成 FLV）。
- `stream-provider/application.yml` 里 `dvr-container-prefix/dvr-local-dir` 指向旧本机 SRS 目录，已删掉回到代码默认值（`/data/dvr` → `./docker-data/srs/dvr`）。
- Windows 挂载卷下 SRS 的 conf 热加载（auto reload）不生效，改配置必须 `docker restart qiyu-srs`。
- Docker Desktop 整个停过一次（所有中间件随之下线），重启 Docker Desktop 后配了 `restart: unless-stopped` 的容器会自动回来，但要等引擎就绪。

## 7. 浏览器开播失败："WebRTC 连接超时"（UDP 转发丢包）⭐ 最终结论

- **现象**：SRS 容器（端口映射模式）下，浏览器 WebRTC 开播信令正常（offer/answer 交换成功、candidate 下发 `192.168.31.252:8000`），但 ICE 之后 DTLS 握手包一个都到不了，20 秒里只收到 3 个 STUN 包（正常应为每秒几十个），40 秒后 SRS 销毁会话。
- **根因**：**Docker Desktop 的 UDP 端口转发大面积丢包**——STUN 偶尔能穿透，DTLS/大包全丢。这正是当初 compose 用 host 网络想绕开的老问题；而 host 网络方案又撞上问题 3（Windows 侧不可达）。即使 Docker Desktop 开了 `EnableHostNetworking=True`（实测无效，绑定仍在 docker-desktop VM 内）。
- **修复（本机开发环境）**：放弃容器方案，改用**原生 srs.exe + `docker/srs-native.conf`**（真实 UDP 套接字），启动命令见该 conf 头部。RTMP/FLV/HLS/WebRTC 全链路验证通过。
- **注意**：
  - docker 的 `qiyu-srs` 容器已停止（端口冲突）；compose 保留给 Linux 服务器部署。
  - 原生模式下 on_dvr 回调文件路径是 `./objs/dvr/...`（相对 srs.exe 的 cwd），stream-provider 的 `/data/dvr` 前缀映射不上 → **录制回放在原生模式下暂不可用**。若需要，在 IDEA 运行配置里加 VM 参数/程序参数 `--qiyu.srs.dvr-container-prefix=D:/softwarepackage/development/srs_v5/SRS/objs/dvr`。
  - 生产/Linux 环境：容器端口映射模式 RTMP/FLV/HLS 正常，WebRTC 用 UDP 映射需实测（Linux 原生 UDP 转发质量远好于 Docker Desktop）。

## 8. 最终端到端联调发现并修复的 4 个代码 bug（2026-09-12 下午）

浏览器全链路实测（主播 WebRTC 开播 → 观众观看 → 停播）暴露的代码问题，均已修复：

1. **观众端黑屏（LivePlayer.vue）**：`ontrack` 只设 `srcObject` 从不调 `play()`，`<video>` 无 `muted/autoplay` 属性。未静音的自动播放被浏览器策略拦截 → 流已到达但画面黑屏。修复：video 加 `autoplay muted playsinline`，`play()` 成功后尝试解除静音。
2. **关播踢流永远踢不掉（SrsApiServiceImpl，三连坑）**：
   - SRS `/api/v1/clients` 返回的 `stream` 字段是内部流ID（vid-xxx），流名在 **`name`** 字段，拿 `stream` 匹配 streamKey 永远失败；
   - 数组键是 **`clients`** 不是 `data`，取错键静默返回 0；
   - 踢流端点是 `DELETE /api/v1/clients/{id}`，代码多写了 `/delete` 后缀。
   - 教训：后端静默 return 不打日志，害排查多绕一小时。已全部修复并补日志（`kicked=N`）。
3. **开播瞬间的状态竞态误判（LivingStreamServiceImpl）**：on_publish 落库后 134ms，主播页面的状态轮询就来查 SRS——此时 WebRTC 握手/桥接未完成，SRS 无流 → 误判断流 → DB 状态被打成"异常(2)"卡死，观众端永远显示"主播暂未推流"。修复：`getStreamStatus` 增加 **30 秒开播宽限期**，宽限期内不做断流修正。
4. **关播接口限流**：`closeLiving` 有"关播请求过于频繁"限流，连续快速关播会被拒（前端只弹 toast），自动化测试时注意间隔 >1s。

验证脚本：`scripts/stream_push_test.mjs`（推流链路）、`scripts/stream_kick_test.mjs`（关播踢流：推流保持中 closeLiving → 流必须销毁且 ffmpeg 被踢断）。

### 本机开发环境最终形态

- SRS：**原生 srs.exe + `docker/srs-native.conf`**（`set CANDIDATE=192.168.31.252` 后启动），docker 的 `qiyu-srs` 容器停止（留 Linux 部署用）
- 后端：IDEA 或 `scripts/start-all.ps1` 启动（注意 stream-provider 需重新编译才有踢流修复）
- 前端：`web_live` 目录 `pnpm dev`（3000 端口）

---

## 当前可用状态

- 验证脚本 `scripts/stream_push_test.mjs` 全链路绿灯：登录 → 开播 → 签发推流地址 → FFmpeg 推流 → SRS 收流 → on_publish 回调 → FLV/HLS 播放 → 关播踢流清理 → DVR 落盘。
- 完整设计与部署文档：`docs/srs-integration-design.md`（含"附2 实测记录"）。

## 9. 充值中心打不通的 5 个根因（2026-09-12 体验迭代）

现象：充值页下单成功但金币永远不到账。逐层排查发现是**五个独立问题叠加**，任何一个修掉都不够：

1. **`t_pay_topic` 表为空**：`payNotify` 先按 bizCode(10001) 查回调主题配置，查不到直接返回 false。修复：插入 `qiyu-pay-notify-topic` 配置（见 `sql/recharge_seed.sql`）。
2. **`t_pay_product.extra` 全为空**：入账代码 `JSON.parseObject(extra).getInteger("coin")` 对空串解析返回 null → NPE。修复：补齐 `{"coin":N}`。
3. **商品 `type=1` 不被认领**：入账只认 `PayProductTypeEnum.QIYU_COIN(0)`，库里商品却是 type=1，就算回调通了也不加币。修复：统一改 type=0。
4. **模拟回调地址端口错误**：api 层写死 `localhost:8201`，而 bank-api 实际跑在 38201，回调永远打不通。修复：改为可配置 `qiyu.pay.mock-notify-url`，默认 38201。
5. **RestTemplate 双重编码**：用 `{param}` URI 模板传 JSON 时，值里的花括号先干扰模板解析；改成手动 URLEncoder 后，RestTemplate 又对已编码的 `%` 二次编码，bank-api 收到 `%7B%22...` 原始转义串反序列化失败。修复：用 `URI.create(url)` 对象传参绕过自动编码。

附加修正：充值入账的流水类型由「送礼物(0)」改为「直播间充值(1)」（`incrForRecharge`），否则后续 T+1 对账无法按类型区分资金流向。

经验：**这类"配置数据 + 转换链路"的复合故障，必须先查数据再查代码**——四个数据/配置问题都藏在 DB 里，代码逻辑本身（除端口和编码外）是对的。

## 10. 双浏览器标签页测试的 localStorage 串号

同一浏览器多个标签页共享 localStorage：观众 tab 登录会覆盖主播 tab 的 token。主播 tab 后续任何 `fetchUserInfo`（路由跳转/刷新）都会静默变成观众身份，表现为"按钮消失""操作无反应"。测试多角色时要么用两个浏览器（普通+无痕），要么每步操作前重新核对页面身份。

## 11. 容器化覆盖 Nacos 地址：环境变量不生效，必须用启动参数

jar 内 `bootstrap.yaml` 里的 `spring.cloud.nacos.discovery.server-addr` 等配置，会在启动时以 bootstrap 属性源插入环境**头部**，优先级高于 OS 环境变量——所以 `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR=...` 传进容器后仍连 127.0.0.1。application.yml 里的数据源/Redis/RocketMQ 键没有这个问题，环境变量覆盖有效。

解法：`docker/app.Dockerfile` 的 ENTRYPOINT 支持 `EXTRA_ARGS`，以 Spring 启动参数方式（优先级最高）覆盖 nacos config/discovery 与 dubbo.registry.address，见 `docker-compose-full.yml`。

另一个坑：容器里没有 Nacos 鉴权用户时，config 客户端会报 `403 user not found!`（dataId 不存在导致），与宿主机启动日志一致，属良性告警，不影响启动。

## 12. 视频上传"假死"：响应 200 已到，前端却永远卡在上传中（两个叠加 bug）

用户实际遇到的现象：上传进度走完、网络面板里也能看到 200 响应（code:200 + videoUrl），但弹窗一直显示"上传中"，分类、取消、发布全部点不动。排查发现是**两个独立 bug 叠加**，且测试合成文件时完全暴露不出来：

### 12.1 fetch 的 res.json() 挂起（诱因）

上传用的 `fetch` 在 `res.json()` 这一步依赖**响应体流读取完成**。用户浏览器装的视频下载类扩展会劫持媒体响应流，导致流永远读不完 → `await` 挂起 → UI 冻结。干净浏览器复现不出来。

修复：上传统一改用 **XMLHttpRequest**（`load` 事件在传输结束时必触发，不依赖 body 流读取），并加 15 分钟超时兜底、真实进度条、可取消（`api/video.js`、`api/resource.js`、`VideoPublishDialog.vue`）。

经验：**涉及用户环境的 web 功能，别把"我这里正常"当验证通过**；上传这类大文件链路用 XHR 比 fetch 可靠，且必须给超时和取消兜底。

### 12.2 formatDuration 未定义 → 渲染崩溃冻结（真正的元凶）

修完 12.1 用户仍卡住。用真实视频文件（17.9MB，有时长）复现：上传完成后弹窗整体冻结。遍历 Vue 组件树时触发重渲染，暴露报错 `_ctx.formatDuration is not a function`——模板里"已上传（xxMB · 时长）"调用了 `formatDuration`，但脚本里**从没定义过这个函数**。

为什么之前测试全过：合成测试文件是无效视频，`duration=0`，模板三元表达式短路，根本不会调用 `formatDuration`。真实视频有时长 → 走到该分支 → 渲染函数崩溃 → Vue 渲染循环反复失败 → **整个弹窗 DOM 不再更新**，表现为"所有按钮都点不动"（其实点击有响应，只是界面冻结）。

修复：补上函数定义（`78539d8`）。

经验：**测试数据要贴近真实形态**（真实格式、有时长、中文文件名）；Vue 里"点击无反应"未必是事件没绑定，先开 console 看有没有渲染报错——渲染函数一崩，整个组件树都会冻结。

### 排查过程附注（为什么绕了远路）
- 先怀疑 vite 代理、`Expect: 100-continue`、并发封面上传——逐一用对照实验排除（curl 直连网关 vs 走代理、抑制 Expect 头、页面内裸 XHR 直传都成功），最后靠 Performance API 看到**请求层全部 200 完成**才把方向锁定到"promise 链断了"，再由组件树遍历暴露渲染异常定位根因。
- 复现手段：无法直接上传本地文件给浏览器，就在页面里把文件 base64 分块传入重建 `File` 对象（或起本地 CORS 静态服务让页面 fetch）。

## 13. Dubbo 接口 jar 版本不一致：新加字段静默丢失

admin-api 打包时，本地仓库里的 `qiyu-live-video-interface` 是旧 SNAPSHOT（`VideoDTO` 还没有 `status` 字段），打出来的 fatjar 里嵌的就是旧 jar。运行时 provider 端返回了 status，consumer 端按旧类反序列化**静默丢字段**（不报错），管理端视频列表全部显示"已下架"。

排查线索：直接解包 fatjar 看嵌套 jar 里的 class（`unzip -p app.jar BOOT-INF/lib/xxx-interface.jar | javap -p`），对比字段。

规则：**改过任何 interface 模块后，必须先 `mvn install` 该 interface，再 package 依赖它的模块**；用 `mvn clean package` 而不是增量 package，避免旧 jar 残留。

## 14. Spring 接口签名与实现不一致的连锁编译坑

给 `closeLivingCheck` 换参数类型（LivingRoomReqDTO → ImOfflineDTO）时，接口、实现、消费者三处只改了一半，加上脚本批量替换时误写了 `closeLiving(livingRoomReqDTO := null)` 这类非法语法、`getCode()` 返回 int 不能 `.equals` 比较，连续报了四种编译错。逐一修正：接口/实现签名统一为 ImOfflineDTO，内部再手工构造 LivingRoomReqDTO 传给事务方法；int 比较用 `!=`。

经验：**改接口签名时一次性 grep 所有调用点**（接口/实现/消费者/测试），批量脚本替换后必须人工 review diff，脚本会"补丁摞补丁"。

## 15. 运行中的 jar 会锁文件导致 repackage 失败

`mvn package` 报 `Unable to rename xxx.jar to xxx.jar.original`——目标 jar 还被运行中的 Java 进程占用。用 `jps -l` 找到对应进程 kill 后重新打包。另外 IDEA 里启动的服务与自己 jar 启动的服务会抢同一 Dubbo 端口（`Address already in use: bind`），重启服务前先 `jps` 核对谁在跑。

## 16. 定时/异步链路测试的三个实用技巧

- **测试脚本别依赖非原生依赖**：Node 脚本里想直连 MySQL 发现没有 mysql2，改走 HTTP API 查询 + `mysql` CLI 改数据，避免为测试装依赖。
- **开播限流是 10 秒 1 次**：E2E 里连续开播会被 `RequestLimit` 拦截（"开播请求过于频繁"），断言前注意这不是功能 bug。
- **自动化点击时机**：页面异步加载的按钮（如收藏）在 DOM 就绪前点击会"点了没反应"，先等状态渲染完成再操作，否则会误判成应用 bug（本次收藏按钮验证两次误报，实际功能是好的）。


## 17. 端口重编排时的连环坑（JDK8 启动 / target 双 jar / find_jar 选旧包）

全项目端口按步长 5 重编排时踩了一串坑，复盘如下：

1. **启动脚本用裸 `java`**：PATH 里是 JDK8，`UnsupportedClassVersionError` 全体秒退。修复：`start-all.sh` 改为优先取 `${JAVA_HOME}/bin/java`（本项目固定用 JDK17：`D:\enviroment\javaenviroment\jdk17`）。
2. **target 下新旧两个 fatjar 并存**：各模块历史 finalName 不一致（`-1.0-SNAPSHOT` / `-docker` / 裸名），重新 package 产出新名 jar 后旧 jar 仍残留；`start-all.sh` 的 `find_jar` 用 `head -1`（字母序）**选中了旧 jar**——于是服务"启动成功"（自检 PASSED）却跑的旧端口旧代码，Nacos 里注册的还是 31001/30006。修复：`find_jar` 改 `ls -t`（修改时间最新优先）+ 重建用 `clean package`。
3. **15 个服务同时重启打爆 Nacos gRPC**：并发注册导致部分服务 `TimeoutException ... Request/request` 直接退出。处理：分批补拉起即可，非配置问题。
4. **首次 Dubbo 调用超时**：服务刚起来后第一次 RPC 要建连，默认 1s timeout 会失败，重试即通，不是故障。

经验：**换端口/改配置后的验证必须落到"Nacos 实际注册的 ip:port"**（控制台或 ns API 查），进程活着 + 自检 PASSED 不代表跑的是新配置；启动脚本选 jar 的逻辑要按修改时间而非字母序。

## 18. IDEA 与脚本混跑的端口冲突

IDEA 里启动过的服务如果没停，会和脚本启动的同名服务抢 Dubbo 端口（`Address already in use: bind`，新进程直接退出）。表现是"明明启动了却连不上/端口不是配置里的值"。启动前统一 `jps -l` 检查并清理，或固定只用一种方式启动。

---

# 附录：IM 弹幕重复消息专项分析（2026-09-12）

> 状态：**已修复（方案 A，前端 ack）并验证通过**
> 现象：用户 A 在直播间发送一条弹幕，房间内其他用户（B）会先后收到 **两条一模一样的消息，间隔约 5 秒**。
> 影响范围：web 端（`web_live`）所有下行 IM 业务消息（5555 弹幕 / 5556 礼物 / 5560 红包雨 / 5561 领取通知 / 5562 订单状态）都会重复推送一次。App 原生客户端不受影响（已实现 ack 应答）。

## 关联需求：主播退出直播间自动关播并通知观众（2026-09-12 已实现）

在排查本 bug 时顺带发现：`LivingRoomTxServiceImpl#closeLiving` 是空壳（原实现整体被注释，直接 `return true`），导致"结束直播"按钮和"主播断开 IM 连接"都不会真正关闭直播间。

已补全实现（`qiyu-live-living-provider`）：

- `closeLiving`：校验房间存在且操作者是主播本人（观众断连不触发）→ `t_living_room.status` 置 0 → 清理 `living_room_obj` / `living_room_list` 缓存。
- 新增广播：向房间成员集合（`living_room_user_set:{appId}:{roomId}`）内所有观众推送 **bizCode=5565（LIVING_ROOM_CLOSE，新增枚举）**，data 为关闭的 roomId。
- 触发路径两条：① 主播点"结束直播"（`/living/closeLiving` API）；② 主播直接退出页面/关闭浏览器标签（IM 断连 → `im_offline_topic` → `userOfflineHandler` → `closeLiving`）。
- 前端 `RoomPage.vue`：收到 5565 且 roomId 匹配时提示"主播已离开，直播间已关闭"并跳回首页。
- 注意：`t_living_room_record` 是**直播回放记录表**（record_url/duration/file_size），原注释代码往里插"关播归档"是错的（还会因 record_url 非空约束失败），本次实现未动该表。

验证：主播 GUI 点"← 返回"离开 → 房间 31 在 DB 中 status 变 0，房间内观众探针收到 `bizCode=5565, data="31"`。

## 修复记录（2026-09-12）

- `web_live/src/utils/im/connection.js`：收到 `code=1003` 业务消息后，解析 body 中的 `appId/userId/msgId`，回发 `code=1005` ack 帧（新增 `_ackBusinessMsg`）。
- `web_live/src/views/RoomPage.vue`：msgId 去重修正为从 body（ImMsgBody JSON）内部取值（原实现取的是顶层帧字段，一直未生效），作为 ack 丢失时的兜底。
- 验证结果：探针向房间发送一条弹幕，浏览器端 8 秒后统计仅显示 **1 条**；服务端日志显示 5 秒延迟检查时 `retryTimes is -1`（ack 已删除重发记录），未发生第二次推送。

---

## 一、现象与复现

两个浏览器分别登录不同用户进入同一房间（room/29）：

- 用户 A 发送 `你好`
- 用户 B 的消息列表出现两条 `你好`，时间相差 5 秒（截图：`12:33:32` 与 `12:33:37`）

服务端日志（`D:\tmp\logs\qiyu-live-im-core-server\192.168.31.252\qiyu-live-im-core-server.log`）中同一业务消息的投递轨迹：

```
11:33:37.140  MsgAckCheckServiceImpl  msg is {...msgId=5faddaac...},sendResult is SEND_OK   ← 首次推送
11:33:42.189  ImAckConsumer           retryTimes is 1, msgId is 5faddaac...                 ← 5秒后检查：未收到ack
11:33:42.197  MsgAckCheckServiceImpl  msg is {...msgId=5faddaac...},sendResult is SEND_OK   ← 第2次推送
11:33:47.283  ImAckConsumer           retryTimes is 2, msgId is 5faddaac...                 ← 达到上限，停止
```

观众端 WebSocket 探针抓到的原始帧（同一弹幕到达两次，**msgId 不相同**）：

```
code=1003 msgId=884dab8b-cba6-4daa-98b2-77ae1016f...  body={"bizCode":5555,"data":"{\"content\":\"GUI弹幕跨用户验证...\"}"}
code=1003 msgId=9779678d-e747-46a4-b832-1db1c623c...  body={"bizCode":5555,"data":"{\"content\":\"GUI弹幕跨用户验证...\"}"}
```

## 二、根因分析

### 2.1 后端的 ack 重发机制（这是设计，不是 bug）

IM 核心服务为了保证"消息至少送达一次"，内置了一套 **ack + 延迟重发** 机制，链路如下：

```
消息到达 core server
   │
   ├─ RouterHandlerServiceImpl.sendMsgToClient()
   │     生成 msgId，推送给客户端                      ← 客户端第 1 次收到
   │     recordMsgAck(msgId, 1)   → 写入 Redis hash
   │     sendDelayMsg()           → 发 RocketMQ 延迟消息（延迟级别2 ≈ 5s）
   │
   └─ 5s 后 ImAckConsumer 消费延迟消息
         getMsgAckTimes(msgId)
         ├─ 返回 -1（客户端已ack，记录被删除）→ 不重推，结束
         ├─ retryTimes < 2 → recordMsgAck(msgId, 2)
         │                    再发一条延迟消息
         │                    sendMsgToClient() 再推一次  ← 客户端第 2 次收到
         └─ retryTimes >= 2  → doMsgAck() 清理，放弃
```

相关代码位置：

| 环节 | 类 | 位置 |
|---|---|---|
| 首推 + 记录 ack + 发延迟消息 | `RouterHandlerServiceImpl#onReceive / sendMsgToClient` | `qiyu-live-im-core-server/.../service/impl/RouterHandlerServiceImpl.java` |
| 延迟检查与重推 | `ImAckConsumer`（消费 `qiyu_live_im_ack_msg_topic`） | `qiyu-live-im-core-server/.../consumer/ImAckConsumer.java` |
| ack 确认（删除重发记录） | `AckImMsgHandler`（处理客户端上行的 **code=1005** 消息） | `qiyu-live-im-core-server/.../handler/impl/AckImMsgHandler.java` |
| ack 记录存取 | `MsgAckCheckServiceImpl`（Redis hash：`qiyu-live-im-core-server:imAckMap:{appId}:{userId}`，field=msgId） | `qiyu-live-im-core-server/.../service/impl/MsgAckCheckServiceImpl.java` |
| 消息码定义 | `ImMsgCodeEnum.IM_ACK_MSG(1005, "im服务的ack消息包")` | `qiyu-live-im-interface/.../constants/ImMsgCodeEnum.java` |

**设计前提**：客户端收到 `code=1003` 的业务消息后，必须回发一条 `code=1005` 的 ack 消息（body 携带 `appId/userId/msgId`）。服务端 `AckImMsgHandler → doMsgAck()` 会删掉 Redis 里的重发记录，这样 5 秒后的延迟消息检查到 `retryTimes = -1`，就不会再推。

### 2.2 web 前端没有实现 ack（bug 所在）

`web_live/src/utils/im/connection.js` 的 `IMConnection` 只处理了 1001 登录、1003 业务、1004 心跳，**从未向上回发 1005 ack**。于是对服务端来说，每条消息都"没有得到确认"，固定走满一次重推 → 每条消息客户端必收 2 条（首推 + 5 秒后重推）。

### 2.3 次级问题：重推时 msgId 被重新生成，前端无法去重兜底

`RouterHandlerServiceImpl#sendMsgToClient` 每次调用都会 `UUID.randomUUID()` 生成**新的** msgId 再推送：

```java
public boolean sendMsgToClient(ImMsgBody imMsgBody) {
    ...
    String msgId = UUID.randomUUID().toString();   // ← 每次重推都换新 msgId
    imMsgBody.setMsgId(msgId);
    ...
}
```

后果有两个：

1. Redis 里 ack 记录的 field 是首推的 msgId A，而重推出去的消息是 msgId B。客户端就算对 B 回 ack，也删不掉 A 的记录（当然按正常时序，客户端 ack 的是首推的 A，逻辑上成立；但这让"按消息内容幂等"变得混乱）。
2. **前端按 msgId 去重的兜底方案会失效**——首推和重推的 msgId 不一样。此前在 `RoomPage.vue` 加的 msgId 去重只能拦住"同一帧的重复投递"（如 MQ 重复消费），拦不住 ack 重推。

另外注意：前端帧结构是 `{magic, code, len, body}`，`msgId` 在 **body（ImMsgBody JSON）内部**，顶层帧上没有 msgId 字段，去重逻辑必须从解析后的 body 里取。

## 三、修复建议

### 方案 A（根治，只改前端）：收到业务消息后回发 ack

在 `connection.js` 的 `onmessage` 中，识别 `code === 1003` 的帧，解析出 body 里的 `appId/userId/msgId`，回发：

```js
// 帧格式与服务端 ImMsg 一致：{ magic: 19231, code: 1005, len, body }
this._send(1005, {
  appId: <body.appId>,
  userId: <body.userId>,
  msgId: <body.msgId>,
  data: ''
})
```

服务端 `AckImMsgHandler → doMsgAck` 收到后删除 Redis 记录，5 秒后的延迟检查发现 `retryTimes = -1`，直接跳过重推。

> 注意：ack 必须在页面关闭/断线前尽早回发；若用 `imConn.sendChat` 同一个 `_send` 通道发送即可，无需新连接。

### 方案 B（兜底加固，改后端，需重启 im-core-server）：重推时保持 msgId 稳定

`RouterHandlerServiceImpl#sendMsgToClient` 只在 `imMsgBody.getMsgId()` 为空时才生成新 msgId：

```java
if (StringUtils.isEmpty(imMsgBody.getMsgId())) {
    imMsgBody.setMsgId(UUID.randomUUID().toString());
}
```

这样重推消息与首推消息 msgId 一致，前端 msgId 去重（需改为从 body 内取 msgId）才能真正兜底——即使 ack 丢失（比如用户恰好在 5 秒窗口内关页面），重推消息也会被前端去重逻辑拦掉，不会重复展示。

**两个方案建议同时上**：A 解决重复推送的源头，B 保证极端情况下（ack 丢失）用户仍看不到重复消息。

## 四、修复后的验证方法

1. 双浏览器进入同一房间，A 发一条弹幕：
   - B 只出现一条；
   - 观察服务端日志，`ImAckConsumer` 应打印 `retryTimes is -1`（表示 ack 已删记录），且不再出现第二次 `SEND_OK` 推送日志。
2. 抓 WebSocket 帧：`code=1005` 的上行 ack 应在每条 `code=1003` 下行消息后出现。
3. 断网 10 秒再恢复（模拟 ack 丢失），确认重推消息被前端 msgId 去重拦截，界面仍只有一条。

## 五、附：本次排查的原始证据

- 服务端日志（`D:\tmp\logs\qiyu-live-im-core-server\192.168.31.252\qiyu-live-im-core-server.log` 11:33:37 ~ 11:33:57 段）：同一 msgId 的 `SEND_OK` 推送出现 2 次，中间夹着 `retryTimes is 1` / `retryTimes is 2`。
- 观众端探针日志（`gui-test-screenshots/viewer_b_received.log`）：同一弹幕两条帧，`msgId` 分别为 `884dab8b-…` 与 `9779678d-…`。
- 用户截图：room/29 内 `你好` 消息在 12:33:32 与 12:33:37 出现两次，间隔 5 秒，与延迟级别 2（5s）吻合。

---

## 19. JDK17 中文 Windows 默认 GBK：MQ 消息体乱码 + 弹幕整批解析失败（2026-09-13）

**现象**：风控 E2E 发弹幕，接收端 0 条收到；服务端日志显示 `consumeMessage exception: JSONException: illegal identifier : \`，消息在重试队列无限打转（`retryTimes 1/2/...` 反复出现）。更隐蔽的是：部分中文消息能"成功"投递但内容是乱码（`调试弹幕` 变 `璋冭瘯寮瑰箷`），容易被误判为控制台显示问题放过。

**根因**（两层叠加）：

1. fastjson 的 `JSON.toJSONBytes()` 固定写 **UTF-8** 字节进 MQ，但消费端 `ImMsgConsumer` 用 `new String(msg.getBody())` 解码——**JDK17 在中文 Windows 上默认 `file.encoding=GBK`**（JEP 400 是 JDK18 才改默认 UTF-8），于是按 GBK 去解 UTF-8 字节 → 内容乱码。
2. 更致命：GBK 是双字节编码且**第二字节合法范围包含 0x5C（反斜杠）**。当多字节汉字紧跟 JSON 转义 `\"` 时（弹幕 content 恰好总在 `\",\"roomId` 前面），GBK 配对会"吞掉"转义符 `\` → fastjson 抛 `illegal identifier` → 解析失败 → 消息进重试队列，永远消费不掉。这就是"弹幕整批丢失"的直接原因。纯 ASCII 内容不受影响，所以此前部分测试能过、部分不能过，呈**随机假象**。

**修复**：`scripts/start-all.sh` 的 JAVA_OPTS 默认值加 `-Dfile.encoding=UTF-8`，全链路字符集统一。任何"Java 服务间传中文"的链路都受此影响（MQ、HTTP body 手工解析等），脚本启动已全局覆盖；用 IDEA 单跑服务时也需在 VM options 里加该参数。

**排查手段**：消费端无任何业务日志时，先看 `C:/Users/<用户>/logs/rocketmqlogs/rocketmq_client.log`（RocketMQ 客户端自己的日志），`consumeMessage exception` 会打出完整消息体和解析异常；broker 侧用 `mqadmin consumerConnection -g <组名> -n <namesrv>` 确认消费者是否还注册着（组名格式见各 provider 的 `rocketMQConsumerProperties` 配置，不是想当然的 spring.application.name）。

**教训**：编码问题的症状（乱码/偶发解析失败）与业务 bug 极易混淆，"部分成功部分失败"首先怀疑字符集，其次才是业务逻辑；跨服务传中文必须显式统一 file.encoding，不能依赖平台默认值。

---

## 20. IDE 污染的 class 被打进 maven jar（批次二踩坑）

**现象**：api 服务运行时抛 `java.lang.Error: Unresolved compilation problems: QiyuRequestContext cannot be resolved`（framework-web-starter 里），但 maven 构建显示 BUILD SUCCESS。

**根因**：两层。
1. **Eclipse/IDE 带错误编译**：IDE 在 classpath 不完整时也会产出 class 文件，错误以 `throw new Error("Unresolved compilation problems...")` 形式嵌在字节码里（ECJ 特性，javac 不允许）；
2. **maven 增量编译不覆盖**：maven-compiler-plugin 见源码不比 target/classes 里的 class 新就跳过重编译，坏 class 原样打进 jar → BUILD SUCCESS 但运行必炸。本项目 IDE 与命令行 maven 共用同一 target 目录，用户白天开过 IDE 就会中招。

**修复**：`mvn clean install`（必须 clean）受影响模块；排查手段：对可疑 class 执行 `unzip -p xx.jar 路径/Class.class | od -c | grep -i "U n r e s"`（od 输出字节间是多空格，grep 二进制不可靠，用 od）。

**教训**：IDE 与 maven 混用同一 target 目录是隐患源；遇到"构建成功但运行时 Unresolved compilation problems"，第一反应查 target/classes 里是否有 ECJ 产物，clean 重编即可。另注意：`mvn ... | grep ERROR; echo $?` 的 `$?` 是管道最后一个命令的退出码，判断 maven 成败要看真实输出。

## 21. SRS hooks 未生效 + @EnableScheduling 只 import 未标注 + lavfi 推流忘加 -re（批次五三连）

**现象**：截帧巡查 E2E 推流后 stream_status 永远为 0、快照表无记录；且推流常"莫名被断"（-10054）。

**根因（三个叠加，逐层排查）**：
1. **srs.exe 启动早于 conf 修改**：SRS 只在启动时读配置，9/12 启动的进程加载不到 9/13 才加入的 http_hooks 配置 → on_publish/on_unpublish 回调从未触发 → stream_status 永远为 0。**改 conf 后必须重启 SRS**。
2. **@EnableScheduling 只 import 未标注**：启动类 import 了注解但类上没写，@Scheduled 任务从未调度且无任何报错。注解类必须真正标注才生效。
3. **lavfi 推流忘加 -re**：`ffmpeg -f lavfi -i testsrc=duration=180` 以编码器全速推流（实测 52 倍速），3 秒推完 180 秒内容即正常退出 → 观察者看到的是"推流莫名秒断"。**模拟真实直播必须加 -re**。

**排查手段**：SRS HTTP API `curl http://127.0.0.1:1985/api/v1/streams/` 看流是否注册；SRS 日志（log_tank=console 时重定向到文件）grep on_publish；wmic process get CreationDate 对比 conf 修改时间。

**教训**：环境级故障（配置没生效、注解没标注）的表现是"功能静默失效"，比代码错误更难查——先确认链路上每个组件的配置加载时间线，再怀疑代码。
