# 构建上下文必须是仓库根目录（子模块要继承根 pom.xml），见 deploy/compose.yaml

# ---- 阶段 1：用 Maven 编译打包 ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY services services
# 缓存挂载：依赖下载到构建机的缓存里，下次构建复用，不进入镜像。
# 测试需要 Docker（Testcontainers），在 CI 中运行，这里跳过。
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl services/seat-service -am package -DskipTests

# ---- 阶段 2：只带 JRE 和 jar 运行 ----
FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home app
WORKDIR /app
COPY --from=build /src/services/seat-service/target/seat-service-*.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
