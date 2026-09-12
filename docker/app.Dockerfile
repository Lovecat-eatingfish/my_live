# 旗鱼直播 Java 服务通用镜像
# 构建脚本会先把对应模块的可执行 jar 拷贝到本目录（覆盖为 build-context 方式）：
#   docker build -f docker/app.Dockerfile -t qiyu-live/<服务名>:dev <服务目录>
# 也可以直接用 scripts/build-images.sh 一键构建全部镜像
FROM eclipse-temurin:17-jre

ENV TZ=Asia/Shanghai

# jar 由 docker build 时 COPY 进来（约定命名 app.jar）
COPY app.jar /app/app.jar

# 提示：容器化部署时通过环境变量覆盖中间件地址，见 docker-compose-full.yml
# EXTRA_ARGS 以 Spring 启动参数方式传入（优先级高于 jar 内 bootstrap.yaml，用于覆盖 nacos 地址）
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=70.0 ${JAVA_OPTS} -jar /app/app.jar ${EXTRA_ARGS}"]
