# 计划：seat-service 容器化，Compose 一条命令拉起全栈（搭建路线 3b）

- 日期：2026-10-20
- 关联：搭建路线第 3 步后半；ADR-0003
- 状态：已实现，待 PR 评审

## 目标

给 seat-service 写 Dockerfile，并加入 `deploy/compose.yaml`。在 `deploy/` 下执行 `docker compose up -d --build`，
就能从源码构建镜像，按顺序启动 MySQL → seat-service，服务通过容器网络连接数据库并完成迁移。

## 范围

做：
- `services/seat-service/Dockerfile`：多阶段构建（Maven 编译 → 只带 JRE 运行），使用非 root 用户。
- 仓库根目录 `.dockerignore`：不把 `target/`、`.git`、`.env` 等发进构建上下文。
- `deploy/compose.yaml` 增加 `seat-service`：构建配置、环境变量、`depends_on: service_healthy`、healthcheck、端口 8081。
- AGENTS.md 常用命令。

不做：
- 推送镜像到镜像仓库、打 tag（第 6 步 CD）。
- 在 Docker 构建中跑测试（测试需要 Docker，交给 CI，第 4 步）。
- Spring Boot 分层 jar 优化（之后镜像变大、构建变慢时再做）。

## 关键设计

| 问题 | 做法 | 理由 |
|---|---|---|
| 构建上下文 | 仓库根目录 | 子模块继承根 `pom.xml`，必须一起发给 Docker |
| 构建阶段镜像 | `maven:3.9.9-eclipse-temurin-21` | 与本机 Maven、JDK 版本一致 |
| 依赖下载 | BuildKit 缓存挂载 `/root/.m2` | 改代码后重新构建时不必重新下载依赖 |
| 运行阶段镜像 | `eclipse-temurin:21-jre` | 只要 JRE，不带 Maven、JDK 和源码，镜像更小，攻击面更小 |
| 运行用户 | 新建非 root 用户 `app` | 容器被攻破时权限更小 |
| 服务连数据库 | `DB_HOST=mysql`、`DB_PORT=3306` | Compose 网络内用服务名作 DNS，使用容器端口而不是宿主机的 3307 |
| 启动顺序 | `depends_on: mysql: condition: service_healthy` | MySQL 真正就绪后才启动服务，避免启动时连不上库 |
| 服务健康检查 | `curl -f localhost:8081/actuator/health` | 第 2 步打下的基础；Compose `ps` 能直接看到服务是否健康 |

## 验收标准

1. `docker compose up -d --build` 后，`mysql` 和 `seat-service` 两个容器都是 healthy。
2. 宿主机 `curl localhost:8081/actuator/health` 返回 UP；容器日志显示 Flyway 已执行迁移或已是最新版本。
3. 容器内进程不是 root（`docker compose exec seat-service id` 显示 `app`）。
4. 运行镜像里没有 Maven、没有源码；记录镜像大小。
5. 只改 Java 代码后重新构建，日志显示依赖没有重新下载（缓存生效）。
6. 停掉 mysql 后，seat-service 的状态变为 unhealthy。

## 风险

- 每次 `--build` 都会复制整个 `services/` 目录，任何服务改动都会让其他服务的 Docker 构建缓存失效。服务增多后再优化。
- 基础镜像标签（`21-jre`）会随补丁版本更新，构建结果可能不完全一致。到生产发布时再考虑锁定 digest。

## 实现偏差记录

- 6 项验收全部通过：
  - 两个容器都是 healthy，进程以 `uid=999(app)` 运行，运行镜像里只有 `app.jar`，没有 mvn 和 javac；
  - 镜像 510MB，其中基础镜像 `eclipse-temurin:21-jre` 占 461MB；
  - Maven 这一步首次构建 321.6s，只改代码后重建 22.5s（依赖走了缓存）；
  - 停掉 mysql 后约 54s，seat-service 变为 unhealthy；mysql 恢复后服务自动重连，回到 healthy。
- 偏差：去掉了 Dockerfile 首行的 `# syntax=docker/dockerfile:1`。这一行会让构建先去 Docker Hub 拉取解析器镜像，
  本机经代理访问 Docker Hub 时认证请求被重置，构建失败。当前 Docker 内置的解析器已经支持缓存挂载，不需要这一行，
  去掉后还少一次网络依赖，CI 更稳定。（也不能排除那次只是网络偶发故障。）
