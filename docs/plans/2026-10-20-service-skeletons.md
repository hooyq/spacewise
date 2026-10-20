# 计划：补齐 room / repair / platform 服务骨架（搭建路线第 5 步）

- 日期：2026-10-20
- 关联：修订后的搭建路线第 5 步；ADR-0001、0002、0003
- 状态：已确认（测试辅助类复制、Dockerfile 共用），已实现，待 PR 评审

## 目标

照 seat-service 的结构建好其余三个服务。完成后，每个模块组拿到的是一个已经接好
数据库、健康检查、容器、CI 的空服务，可以直接开始写业务代码。**不包含任何业务。**

## 范围

做：
- `services/room-service`（M2，8082，`room_db`）、`services/repair-service`（M3，8083，`repair_db`）、
  `services/platform-service`（D，8084，`platform_db`）。每个服务包含：
  - `pom.xml`：与 seat-service 相同的依赖；
  - 启动类，包名为 `cn.edu.fudan.spacewise.<room|repair|platform>`；
  - `application.yml`：库名、端口不同，其余与 seat 相同；
  - 测试：`IntegrationTest`、`TestcontainersConfiguration`、`HealthEndpointTest`。
- 根 `pom.xml` 的 `<modules>` 加入三个服务。
- Dockerfile 改为全仓库共用一份（见取舍 2），seat-service 也改用它。
- `deploy/compose.yaml` 加入三个服务。
- AGENTS.md：服务目录表去掉“规划中”字样，补充“新增服务的步骤”。

不做：
- 任何业务表、业务接口，不新增迁移文件；迁移目录由各模块组写第一个迁移时自己创建。
- 服务间调用（第 6 步）、网关（第 7 步）。

## 需要确认的取舍

1. **测试辅助类（`IntegrationTest` 等）每个服务复制一份，还是抽成一个公共测试模块？**
   推荐**复制**。每份只有十几行；微服务之间共享代码会带来耦合，改公共模块会同时影响四个组。
   代价是四份副本可能各自演变，可以接受。
2. **Dockerfile 每个服务一份，还是全仓库共用一份？**
   推荐**共用一份** `deploy/service.Dockerfile`，用构建参数 `SERVICE` 指定构建哪个服务。
   四份 Dockerfile 只有服务名不同，复制容易漏改（例如以后改基础镜像时，四份必须同步改）。
   某个服务确实需要特殊处理时，再给它单独写一份。

## 验收标准

1. `mvn verify` 通过：4 个服务各自的健康检查测试都通过（每个服务各启动一个 MySQL 测试容器）。
2. `docker compose up -d --build` 后，5 个容器（mysql 加 4 个服务）都是 healthy；`curl localhost:808{1..4}/actuator/health` 都返回 UP。
3. 每个服务只能连自己的库：日志中各服务的 Flyway 作用于各自的库（`seat_db`、`room_db` 等）。
4. CI 在本 PR 上通过，并记录 `build` 耗时的变化。
5. **测试有效性**：把 room-service 的数据库名改错（例如改成 `seat_db`），确认 room-service 的测试失败，或在 Compose 中启动失败。

## 风险

- CI 时间随服务数增加（4 个 MySQL 容器依次启动）。超过 5 分钟时再考虑按路径只构建受影响的服务。
- platform-service 的归属（D 由谁负责）还没定，骨架先建出来，归属在 ADR 中决定。

## 实现偏差记录

- 验收结果：
  - `mvn verify` 通过，本地耗时 243s（之前 84s），每个服务约 40～90s，主要花在启动 MySQL 容器上；
  - 5 个容器都是 healthy，8081～8084 的 health 全部为 UP，日志中三个新服务分别连接 `room_db`、`repair_db`、`platform_db`；
  - 故意把 room-service 的库名改成 `seat_db` 后，容器启动失败（`Exited (1)`），日志为
    `Access denied for user 'room'@'%' to database 'seat_db'`；恢复后重新变为 healthy。
- 发现：这种“连错库”的错误，**单元测试发现不了**。测试中 `@ServiceConnection` 会用测试容器的地址覆盖 `application.yml`
  里的地址，配置文件里的库名写错，测试照样通过。能拦住它的是运行时的数据库账号权限，也就是 ADR-0003 的方案 D。
  这说明 Compose 启动本身也是一种必要的验证，第 8 步 CD 部署测试环境后应做一次健康检查冒烟测试。
- 偏差：
  - Compose 中用 `x-backend` 锚点合并公共配置。由于 `<<:` 是浅合并，`build`、`environment` 仍写在每个服务里。
  - 计划中的“新增服务步骤”没有写进 AGENTS.md：服务数量已固定为 4 个，改为在服务表中列出端口、库和账号。
  - 顺带修正：AGENTS.md 中“端口约定、版本号”两行在上一步被误插到了“CI”一节，已挪回“常用命令”。
  - Maven 输出中的中文模块名在 Windows 终端里显示为乱码，只影响显示，不影响构建；CI 是 UTF-8 环境，显示正常。
