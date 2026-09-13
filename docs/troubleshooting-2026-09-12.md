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
