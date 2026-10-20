# 计划：MySQL 容器化 + Flyway 第一张表（搭建路线 3a）

- 日期：2026-10-20
- 关联：搭建路线第 3 步前半；ADR-0003（MySQL 8.4、每服务一库一账号、Testcontainers）
- 状态：已实现，待 PR 评审

## 目标

用 Docker Compose 运行一个 MySQL 实例，每个服务一个库、一个只能访问自己库的账号；
seat-service 连接自己的库，启动时由 Flyway 执行迁移建出第一张表 `room`；测试使用 Testcontainers 的真实 MySQL。

## 范围

做：
- `deploy/compose.yaml`：只含 `mysql` 服务（镜像、命名数据卷、端口映射、healthcheck）。
- `deploy/mysql/init/01-databases.sh`：首次初始化时创建 4 个库（seat/room/repair/platform）和 4 个专属账号。
  一次建齐 4 个，是因为 init 脚本只在数据卷为空时执行，后补需要重建卷。
- `deploy/compose.example.env`：环境变量示例，复制为 `deploy/.env` 使用（`.env` 不提交）。
- seat-service：加入 JDBC、MySQL 驱动、Flyway、Testcontainers；`V1__create_room.sql`；迁移行为测试。
- ADR-0003；AGENTS.md 常用命令。

不做：
- seat-service 的 Dockerfile 和容器化运行（3b）。
- ORM 选型（JPA / MyBatis 等），第一个业务需求时另写 ADR；本步只用 `spring-boot-starter-jdbc` 提供数据源。
- 业务接口。

## 改动清单

| 文件 | 内容 |
|---|---|
| `deploy/compose.yaml` | `mysql:8.4`；卷 `mysql-data:/var/lib/mysql`；初始化脚本目录只读挂载；端口 `${MYSQL_HOST_PORT:-3307}:3306`（本机 3306 常被本地 MySQL 占用）；healthcheck 用 `mysqladmin ping -h 127.0.0.1`（走 TCP，避免初始化阶段的临时实例被误判为就绪） |
| `deploy/mysql/init/01-databases.sh` | 从环境变量读密码，建 `seat_db` 等 4 库（utf8mb4）及同名账号，每个账号只授予自己库的全部权限 |
| `deploy/compose.example.env` | `MYSQL_ROOT_PASSWORD`、`SEAT_DB_PASSWORD` 等示例值 |
| `services/seat-service/pom.xml` | `spring-boot-starter-jdbc`、`mysql-connector-j`(runtime)、`flyway-core`、`flyway-mysql`；测试：`spring-boot-testcontainers`、`testcontainers:mysql`、`testcontainers:junit-jupiter` |
| `application.yml` | 数据源 `jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3307}/seat_db`，账号 `${DB_USERNAME:seat}`，密码 `${DB_PASSWORD}`（**无默认值**，不把密码写进仓库） |
| `db/migration/V1__create_room.sql` | `room(id, code 唯一, building, created_at)`，**单独 commit** |
| `TestcontainersConfiguration.java` | `@ServiceConnection` 的 `MySQLContainer("mysql:8.4")`，库名 `seat_db` |
| `HealthEndpointTest.java` | 引入上面的配置（健康检查现在包含数据库） |
| `RoomSchemaTest.java` | 迁移后能插入并读回 room；重复 `code` 被唯一约束拒绝 |

## 验收标准

1. `mvn verify` 通过（Testcontainers 自动启动 MySQL，从空库重放迁移）。
2. `docker compose up -d` 后 mysql 变为 healthy；用 `seat` 账号**无法**访问 `room_db`（权限错误）。
3. 本地启动 seat-service 后：`seat_db` 中存在 `room` 表和 `flyway_schema_history` 记录 V1；`/actuator/health` 为 UP。
4. 停掉 mysql 后 `/actuator/health` 变为 DOWN（503）。
5. 持久化演示：插入一行 → `docker compose down` → `up` 数据仍在；`down -v` 后数据消失。
6. **测试有效性**：删掉 V1 中的唯一约束后 `RoomSchemaTest` 失败；恢复后通过。

## 风险

- 跑测试需要 Docker；组员本机和 CI 都必须有 Docker（GitHub Actions 自带）。
- 空间主数据（room）暂放 M1，若组内决定 D 归属不同，需要迁移数据归属（ADR 待定）。
- 修改 init 脚本对已有数据卷无效，必须 `down -v` 重建；已在 compose.yaml 注释中说明。

## 实现偏差记录

- 无功能偏差，6 项验收全部通过：
  - 越权访问报 `ERROR 1044 Access denied for user 'seat'@'%' to database 'room_db'`；
  - 停掉 MySQL 后 health 返回 `{"status":"DOWN"}`、HTTP 503；
  - 去掉唯一约束后 `duplicateRoomCodeIsRejected` 断言失败（`Expecting code to raise a throwable`）。
- 待改进：两个测试类的 Spring 上下文配置不同（一个随机端口、一个 mock 环境），所以启动了两个 MySQL 容器，
  本机每个约 70～100 秒。测试变多后应让测试类共享同一个容器（例如静态单例容器），留待 CI 那一步一起处理。
