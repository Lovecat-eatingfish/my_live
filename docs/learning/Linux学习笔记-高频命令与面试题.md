# Linux 学习笔记：高频命令与面试题

> 面向后端秋招的 Linux 实战笔记。所有"项目场景"都来自本项目的真实操作（start-all 脚本、日志排查、Docker 运维），学完即可在项目里用起来。
> 本机是 Windows + Git Bash，下述命令 90% 在 Git Bash 里就能练；涉及 `top/free/systemctl` 的部分建议开一台 Linux 虚拟机/云服务器练。

## 目录

1. [目录结构与文件操作](#一目录结构与文件操作)
2. [查看文件与日志（后端最高频）](#二查看文件与日志后端最高频)
3. [文本三剑客：grep / sed / awk](#三文本三剑客grep--sed--awk)
4. [进程管理（Java 后端必会）](#四进程管理java-后端必会)
5. [网络排查](#五网络排查)
6. [权限与用户](#六权限与用户)
7. [磁盘 / 内存 / 系统资源](#七磁盘--内存--系统资源)
8. [打包压缩与文件传输](#八打包压缩与文件传输)
9. [Shell 基础：管道、重定向、变量、定时任务](#九shell-基础管道重定向变量定时任务)
10. [项目实战场景串讲](#十项目实战场景串讲)
11. [高频面试题](#十一高频面试题)

---

## 一、目录结构与文件操作

### 1.1 必须记住的目录

```text
/etc        配置文件（如 /etc/profile 全局环境变量）
/home、/root  用户家目录
/var/log    系统与应用日志（生产查日志第一站）
/usr/local  自装软件
/proc       内核/进程的虚拟文件（如 /proc/<pid>/status 看进程信息）
/tmp        临时文件（重启可能清空）
```

### 1.2 文件操作高频命令

```bash
cd /path && pwd                  # 切换目录 / 显示当前路径
ls -la                           # -l 详细 -a 含隐藏；ll 通常是 ls -l 的别名
cp -r src dst                    # 复制（目录要 -r）
mv old new                       # 移动/重命名（同一目录下就是改名）
mkdir -p a/b/c                   # 递归建目录
rm -rf dir/                      # ⚠递归强删（rf 没有回收站，先 ls 确认再删）
ln -s /real/path /link/path      # 软链接（改版本部署常用：link 永远指向新版本目录）
stat file                        # 看文件元信息（大小/时间/权限）
touch a.txt                      # 新建空文件 / 更新时间戳
which java && java -version      # 找可执行文件位置（脚本里定位 JDK 用）
```

### 1.3 find 查找（面试高频）

```bash
find /var/log -name "*.log"                  # 按名字
find . -mtime -1 -name "*.jar"               # 1天内修改过的 jar（核对"旧jar陷阱"就靠这个）
find . -size +100M                           # 大于100M的文件（磁盘满了先找大文件）
find . -type d -name "target"                # 只找目录
find . -name "*.log" -exec rm {} \;          # 找到即删（-exec 接命令，{} 是占位符）
```

> **项目场景**：`部署文档.md` 里"判断 target 里的 jar 是不是旧的"= `ls -l xxx/target/*.jar` 对比 `git log -1 --format=%ci -- <模块>`，本质上就是 find/mtime 的时间比较思路。

---

## 二、查看文件与日志（后端最高频）

```bash
cat file                          # 全量输出（小文件）
less file                         # 分页看大文件（空格翻页 /关键词 搜索 q 退出，G 到末尾）
head -n 100 file                  # 前100行
tail -n 100 file                  # 后100行
tail -f app.log                   # ★ 实时滚动看日志（Ctrl+C 退出）
tail -f app.log | grep --line-buffered ERROR   # ★ 实时过滤错误（注意加 --line-buffered 否则缓冲不输出）
tail -f app.log | grep -A 3 -B 3 Exception     # 命中行前后各3行（看异常栈）
grep -c ERROR app.log             # 统计错误出现次数
grep -n "Started" app.log         # -n 带行号
zcat app.log.gz | grep ERROR      # ★ 不解压直接查 gz 压缩日志（生产日志都会滚动压缩）
```

**必会技巧——日志时间窗定位**：线上问题通常是"某时间点出的事"：

```bash
grep "2026-09-14 10:3" app.log | less     # 锁定10:30~10:39的日志
sed -n '/10:30:00/,/10:31:00/p' app.log   # 按模式取时间区间（三剑客sed的范围语法）
```

> **项目场景**：`scripts/start-all.ps1` 启动 15 个服务，每个服务日志在 `logs/<模块>.log`，`logs <module>` 子命令内部就是 `Get-Content -Tail 100 -Wait`——Linux 等价物就是 `tail -n 100 -f logs/qiyu-live-api.log`。

---

## 三、文本三剑客：grep / sed / awk

面试最爱，按"查找→替换→统计"三个角色记。

### 3.1 grep —— 找（上面已讲，补几个参数）

```bash
grep -i error f.txt        # -i 忽略大小写
grep -r "DubboReference" src/ --include="*.java"   # -r 递归目录 + 限定文件类型
grep -v "^#" conf          # -v 反向匹配（排除注释行，看配置文件必用）
grep -E "a|b" f.txt        # -E 扩展正则（| 或）
ps aux | grep java | grep -v grep    # ★ 经典组合：找java进程但排除grep自己
```

### 3.2 sed —— 改（流编辑器）

```bash
sed 's/old/new/' f.txt              # 每行替换第一处（输出到屏幕，不改原文件）
sed -i 's/old/new/g' f.txt          # -i 直接改文件，g 全局替换（我们修文档链接就这么干的）
sed -n '100,200p' f.txt             # 只打印100~200行（-n 抑制默认输出）
sed '/^$/d' f.txt                   # 删除空行
```

### 3.3 awk —— 按列统计（日志分析神器）

awk 自动按空白切列：`$1` 第一列，`$NF` 最后一列，`-F` 指定分隔符。

```bash
# 取每行第7列（nginx日志里的URL）
awk '{print $7}' access.log | sort | uniq -c | sort -rn | head -10

# 统计每个接口的平均耗时（假设日志格式 "uri cost"）
awk '{sum[$1]+=$2; cnt[$1]++} END {for (u in sum) print u, sum[u]/cnt[u]}' cost.log

# 汇总某列数值
awk -F, '{s+=$3} END {print s}' data.csv      # -F, 按逗号切列（CSV求和）
```

**必背组合管道**（面试手写频率最高）：

```bash
sort | uniq -c | sort -rn | head    # 出现次数TopN（先排序才能uniq、-c计数、-rn逆序数值）
```

---

## 四、进程管理（Java 后端必会）

### 4.1 查进程

```bash
ps aux | grep java          # 快照式：所有进程里筛java（aux: 所有用户+详细+无终端）
ps -ef | grep qiyu          # -e 全部 -f 全格式（显示PPID父子关系）
top                         # ★ 实时资源面板（P按CPU排序 M按内存排序 q退出）
jps -l                      # ★ JDK自带！只列Java进程+主类，本项目stop脚本就靠它
jps -l | grep qiyu-live     # 本项目 start-all.ps1 的 Stop-All 内核就是这个思路
```

### 4.2 杀进程

```bash
kill <pid>              # 发SIGTERM(15)，优雅退出（Spring会走shutdown hook）
kill -9 <pid>           # 发SIGKILL，强杀（数据可能丢/资源不释放，最后手段）
kill -l                 # 看所有信号
pkill -f qiyu-live      # 按命令行模式匹配杀（小心误杀）
```

> **项目场景**：`start-all.ps1` 的 `Stop-All` 用 `jps -l` 匹配 `qiyu-live` 拿 PID 再逐个 `Stop-Process`（等价 `kill`），还处理了"pid 文件过期但进程还在"的情况——这就是脚本化进程管理的完整范式。`Linux 版 start-all.sh` 里就是 `jps + kill`。

### 4.3 后台运行（脚本/部署必会）

```bash
java -jar app.jar &          # & 放后台，但关终端会收到SIGHUP可能被杀
nohup java -jar app.jar > out.log 2>&1 &   # ★ 标准姿势：不挂断+日志重定向
                             # 2>&1: 把2(stderr)重定向到1(stdout)所指的地方
jobs / fg / bg               # 查看后台任务 / 调回前台 / 继续运行
disown -h %1                 # 已启动的放后台任务免受SIGHUP
```

### 4.4 排查进程吃资源（面试热点，先记命令链）

```bash
# ① 找到最吃CPU的Java进程
top -p <pid>                 # 或 top 里按 P
# ② 进程内哪个线程吃CPU（Java专属三板斧）
top -H -p <pid>              # -H 线程视图，记下最吃CPU的线程tid（十进制）
printf '%x\n' <tid>          # 转十六进制（jstack里线程nid是16进制）
jstack <pid> | grep -A 20 'nid=0x<16进制tid>'   # 定位到具体线程栈 → 看代码
# ③ 内存/对象
jmap -heap <pid>             # 堆概况
jmap -histo:live <pid> | head -20   # 对象数量TopN（查内存泄漏第一步）
```

---

## 五、网络排查

```bash
netstat -ano | findstr 30035     # Windows写法（本项目启动手册里的查端口占用）
ss -tlnp | grep 30035            # ★ Linux写法（比netstat快，t=tcp l=监听 n=数字 p=进程）
lsof -i:3306                     # 谁占着3306端口
curl http://127.0.0.1:38080/live/api/living/list   # ★ 测HTTP接口（-X POST -d '{}' -H 'Content-Type: application/json'）
curl -I http://x.com             # 只看响应头（测通不通最快）
ping 10.0.0.5                    # 网络通不通（ICMP）
telnet 127.0.0.1 9876            # 端口通不通（telnet通了立刻断开是正常的）
nc -zv 127.0.0.1 6379            # nc测端口（telnet的现代替代）
traceroute baidu.com             # 路由路径（哪一跳开始丢包）
tcpdump -i any port 3306 -w dump.pcap   # ★ 抓包（分析连接/重传问题，Wireshark打开）
```

**排查思路口诀**：`ping` 通不通（网络层）→ `telnet/nc` 端口通不通（传输层）→ `curl` 应用通不通（应用层）→ `tcpdump` 抓包看细节。

> **项目场景**：`start-all.ps1` 的 `Test-Port` 函数就是用 TCP connect 测端口判断中间件就绪——Linux 等价物就是 `nc -z 127.0.0.1 3306`。"netstat 查端口被占用再 taskkill"更是启动手册排错表里的高频操作。

---

## 六、权限与用户

```bash
ls -l                           # -rwxr-xr-- 拆解: 类型+属主(u)权限-属组(g)权限-其他(o)权限
chmod +x start-all.sh           # 加可执行权限（拿到脚本跑不了先想到这个）
chmod 755 f / chmod 600 key     # 数字法: r=4 w=2 x=1 → 7=rwx 6=rw- 5=r-x
chown appuser:appgroup file     # 改属主属组（服务用低权用户跑是安全基线）
sudo <cmd>                      # 以root执行（/etc/sudoers 控制谁能用）
whoami / su - user              # 当前用户 / 切换用户（- 连环境一起切）
useradd -m appuser && passwd appuser   # 建服务专用用户
```

**chmod 777 为什么是反模式**：所有人可写=任何人可篡改，生产上用 chown + 最小权限，而不是图省事 777。

---

## 七、磁盘 / 内存 / 系统资源

```bash
df -h                           # ★ 磁盘各分区使用率（h 人类可读单位）——日志打爆磁盘就是它报警
du -sh * | sort -rh | head      # ★ 当前目录下哪个子目录最占空间（磁盘满第二板斧）
du -sh logs/                    # 单目录总大小
free -h                         # 内存：注意 available 才是真正可用的（不是 free 列！）
uptime                          # 负载均值 load average：1/5/15分钟（经验: 超过CPU核数即饱和）
nproc                           # CPU核数（JVM线程池/堆参数估算用）
vmstat 1                        # 每1秒刷新：看CPU/内存/IO综合趋势
iostat -x 1                     # 磁盘IO详情（%util 接近100%说明磁盘瓶颈）
```

> **项目场景**：部署文档里"14 个 JVM 吃内存"——机器上先 `free -h` 看 available，再决定 `JAVA_OPTS=-Xms64m -Xmx256m` 压多少；`nproc` 决定 RocketMQ 消费线程数这类参数怎么给。

---

## 八、打包压缩与文件传输

```bash
tar -czvf app.tar.gz dir/       # c创建 z用gzip v显示过程 f文件名（背口诀: 创建压gz）
tar -xzvf app.tar.gz            # x解压（口诀: 解压同gz）
tar -tzf app.tar.gz             # 只看不解
zip -r app.zip dir/ && unzip app.zip
scp app.jar user@10.0.0.5:/opt/app/    # 本地→远程拷贝
scp user@10.0.0.5:/var/log/app.log ./  # 远程→本地
rsync -avz --delete dir/ user@host:/backup/   # 增量同步（比scp适合大目录反复同步）
```

---

## 九、Shell 基础：管道、重定向、变量、定时任务

```bash
# 管道 | ：前一个命令的输出作为后一个的输入（Linux 哲学的核心）
# 重定向： > 覆盖写  >> 追加写  2> 错误  &> 全部  < 输入
java -jar app.jar >> app.log 2>&1 &

# 变量与常用语法（读懂 start-all.sh 的最低要求）
APP=qiyu-live-api
echo "$APP"                     # 双引号会解析变量，单引号原样输出
if [ -f "$APP.jar" ]; then echo exists; fi     # -f 存在且是文件 -d 目录 -z 空串
for m in a b c; do echo $m; done               # for循环
$(命令) 或 `命令`               # 命令替换：拿命令输出当值用
PID=$(jps -l | grep $APP | awk '{print $1}')   # ★ 真实例子：拿进程PID

# crontab 定时任务（五段: 分 时 日 月 周）
crontab -e                      # 编辑当前用户的
0 30 1 * * /opt/app/recon.sh    # 每天01:30（本项目对账Job的cron语义在Linux上的形态）
crontab -l                      # 查看

# systemd 服务管理（现代Linux部署的标准姿势）
systemctl start|stop|status|enable mysql
journalctl -u mysql -f          # 看某服务日志（相当于 tail -f）
```

---

## 十、项目实战场景串讲（学完即用）

**场景 1：qiyu-live-api 起不来，怎么查？**
```bash
jps -l | grep qiyu-live                  # ① 确认进程在不在
tail -n 200 logs/qiyu-live-api.log       # ② 看日志尾部找异常
grep -A 20 "Exception" logs/qiyu-live-api.err.log   # ③ 错误日志里捞异常栈
nc -zv 127.0.0.1 8848                    # ④ 怀疑依赖：Nacos/MySQL/Redis端口逐个测
netstat -tlnp | grep 38085               # ⑤ 怀疑端口被占（Address already in use）
```

**场景 2：服务器磁盘满了。**
```bash
df -h                                    # ① 哪个分区满了
du -sh /* 2>/dev/null | sort -rh | head  # ② 逐层往下找大目录
find /var/log -size +500M -name "*.log"  # ③ 通常是大日志
find /var/log -name "*.log" -mtime +7 -delete    # ④ 删7天前的旧日志（或配logrotate）
```

**场景 3：接口突然变慢。**
```bash
top                                      # ① CPU/内存哪台机器哪个进程异常
top -H -p <pid> → printf '%x' <tid> → jstack <pid> | grep -A 20 nid=0x...   # ② CPU热点定位到代码行
jmap -histo:live <pid> | head            # ③ 怀疑内存：对象TopN
curl -w "总耗时%{time_total}s\n" -o /dev/null http://...:38080/live/api/...   # ④ 确认应用层耗时
ss -s                                    # ⑤ 看连接数（怀疑连接池打满/未释放）
```

**场景 4：统计今天 RocketMQ 消费日志里哪种消息最多。**
```bash
grep "$(date +%Y-%m-%d)" consume.log | awk '{print $5}' | sort | uniq -c | sort -rn | head
```

---

## 十一、高频面试题

**1. `kill` 和 `kill -9` 的区别？**
kill 默认发 SIGTERM(15)，进程可以执行清理逻辑（Spring 的 shutdown hook、Netty 的优雅关闭——本项目 im-core-server 就注册了 ShutdownHook）；kill -9 发 SIGKILL 内核直接杀死，进程没有任何机会清理，可能丢数据/留下锁。**生产先 kill（15），等不到再 -9**。

**2. 怎么找到并杀掉占用某端口的进程？**
`ss -tlnp | grep <port>`（或 `lsof -i:<port>`）拿到 PID → `kill <pid>`。Windows 对应 `netstat -ano | findstr` + `taskkill //PID x //F`（本项目启动手册的排错表就是这条）。

**3. 查看日志最后 100 行并实时监听新增的 ERROR？**
`tail -n 100 -f app.log | grep --line-buffered ERROR`（--line-buffered 让 grep 按行实时刷出，否则有缓冲延迟）。

**4. 统计文件中出现次数最多的前 10 个单词？**
`cat f.txt | tr ' ' '\n' | grep -v '^$' | sort | uniq -c | sort -rn | head -10`（tr 拆词成行 → 排序 → 计数 → 数值逆序 → TopN。管道每一步拆开讲清楚比背下来重要）。

**5. `>` 和 `>>` 区别？`2>&1` 是什么意思？**
`>` 覆盖写，`>>` 追加写；`2>&1` 把标准错误(2)重定向到标准输出(1)当前指向的位置——`> app.log 2>&1` 表示两者都进 app.log。**注意顺序**：`2>&1 > app.log` 是错的（2 先指到了屏幕）。

**6. 软链接和硬链接的区别？**
软链接是独立文件存路径（可跨分区、可指目录、源删了变悬空）；硬链接是同一 inode 的另一个名字（不可跨分区、不可指目录、删源数据还在）。部署场景用软链接做版本切换。

**7. `free` 里 free 列很小是不是内存不够了？**
不是。Linux 把空闲内存拿去做页缓存（buff/cache），**看 available 列**——它才是应用真正能要到的内存。被缓存的内存在应用需要时自动回收。

**8. CPU 100% 怎么排查？（Java 场景完整答案）**
`top` 找进程 → `top -H -p <pid>` 找线程 → `printf '%x'` 转16进制 → `jstack <pid>` 里按 nid 定位线程栈 → 看是业务代码/死循环/GC（GC 线程热点则转 jstat/jmap 查内存）。

**9. TCP 连接很多 TIME_WAIT 正常吗？**
短连接（每次请求新建连接）高并发下的正常现象，TIME_WAIT 是主动关闭方等待 2MSL 的状态，默认占约 60s。大量 TIME_WAIT 说明没复用连接——应用侧改长连接/连接池（这也是为什么 Dubbo 用长连接多路复用）。真正危险的是大量 **CLOSE_WAIT**（对端关了你没关，代码泄漏）。

**10. 一台服务器很卡，你的排查顺序？**
分层：`uptime` 看负载 → `top` 分 CPU/内存 → `iostat -x`/`vmstat` 看磁盘IO（%util 高=磁盘瓶颈）→ `free -h` available（swap in/out 频繁=内存不足）→ `ss -s`/`dmesg | tail` 看连接数与 OOM 记录 → 应用内部 jstack/jmap。**先系统层定位瓶颈类型，再进应用层**，不要上来就重启。

---

## 附：7 天练习计划（结合本项目）

| 天 | 内容 | 练法 |
|---|---|---|
| 1 | 文件操作 + 查看日志 | Git Bash 里把本项目 docs/、logs/ 当素材，练 §一§二全部命令 |
| 2 | grep/管道组合 | 统计各模块日志里 Exception 出现次数 TopN |
| 3 | 进程管理 | 用 jps/kill 模拟 start-all.ps1 的 stop 逻辑（Windows 用 taskkill 对照） |
| 4 | 网络排查 | nc/curl 测本项目全部中间件端口，画出"哪个端口属于哪个服务" |
| 5 | 权限 + 打包 | 写一个 tar 备份 logs/ 的脚本，chmod +x 跑起来 |
| 6 | awk 实战 | 对 logs/ 任一日志做按列统计（每模块启动耗时） |
| 7 | 虚拟机/云服务器 | 把本项目的中间件 compose + 一个 provider 部署到 Linux 上跑通 |
