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
