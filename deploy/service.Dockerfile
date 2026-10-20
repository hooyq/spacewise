# 所有后端服务共用的 Dockerfile，用构建参数 SERVICE 指定构建哪个服务（目录名，如 seat-service）。
# 构建上下文必须是仓库根目录（子模块要继承根 pom.xml），见 deploy/compose.yaml。
# 某个服务确实需要特殊处理时，再在该服务目录下单独写 Dockerfile。

# ---- 阶段 1：用 Maven 编译打包 ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
ARG SERVICE
RUN test -n "$SERVICE" || (echo "缺少构建参数 SERVICE" && exit 1)
WORKDIR /src
COPY pom.xml .
COPY libs libs
COPY services services
# 缓存挂载：依赖下载到构建机的缓存里，下次构建复用，不进入镜像。
# 测试需要 Docker（Testcontainers），在 CI 中运行，这里跳过。
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl services/${SERVICE} -am package -DskipTests

# ---- 阶段 2：只带 JRE 和 jar 运行 ----
FROM eclipse-temurin:21-jre
ARG SERVICE
RUN useradd --system --no-create-home app
WORKDIR /app
COPY --from=build /src/services/${SERVICE}/target/${SERVICE}-*.jar app.jar
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
