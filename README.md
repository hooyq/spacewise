# Spacewise · 智空间

校园资源服务平台：自习座位预约（M1）、整教室预约与审批（M2）、物业报修工单（M3），以及公共层 D（RBAC、空间主数据、通知、审计）。

> 复旦大学《软件过程管理与工程伦理》课程实践项目。
> **当前状态：后端框架已搭好，还没有业务功能。** 各模块组可以直接在对应服务里开始写业务，见[开发一个功能](#开发一个功能)。

## 架构

```
浏览器 ──→ gateway :8080 ──┬─ /api/seat/**     → seat-service     :8081 ─→ seat_db
           （唯一入口）    ├─ /api/room/**     → room-service     :8082 ─→ room_db
                           ├─ /api/repair/**   → repair-service   :8083 ─→ repair_db
                           └─ /api/platform/** → platform-service :8084 ─→ platform_db
                                                        │
              服务之间：HTTP 调用对方的 /internal/**（不经过网关，外部访问不到）
                                                        │
                                      MySQL 8.4（一个实例，每个服务一个库和一个只能访问自己库的账号）
```

| 组件 | 选型 | 决策记录 |
|---|---|---|
| 仓库与分支 | 单仓库，主干开发，tag 发布 | [ADR-0001](docs/adr/0001-monorepo-and-trunk-based.md) |
| 后端 | Java 21 · Spring Boot 3.5 · Maven 多模块 | [ADR-0002](docs/adr/0002-spring-boot-3-5.md) |
| 数据库 | MySQL 8.4 · Flyway 迁移 · 测试用 Testcontainers | [ADR-0003](docs/adr/0003-mysql-per-service-database.md) |
| 数据访问 | MyBatis-Plus | [ADR-0004](docs/adr/0004-mybatis-plus.md) |
| 网关 | Spring Cloud Gateway（Server WebMVC） | [ADR-0005](docs/adr/0005-spring-cloud-gateway-webmvc.md) |
| 错误响应 | ProblemDetail + 错误码 + traceId（`libs/web-common`） | [ADR-0006](docs/adr/0006-error-response-problem-detail.md) |
| 日志追踪 | Micrometer Tracing：一个 traceId 串起一次请求经过的所有服务 | [计划](docs/plans/2026-10-20-logging-tracing.md) |
| 运行 / CI | Docker Compose · GitHub Actions | — |

## 快速开始

需要：**JDK 21、Maven 3.9、Docker**（Docker Desktop 需要处于运行状态）。

```bash
# 1. 准备本地密码配置（.env 不会被提交）
cd deploy
cp compose.example.env .env        # PowerShell：Copy-Item compose.example.env .env

# 2. 构建并启动全部容器（第一次需要下载依赖，约 5～10 分钟）
docker compose up -d --build --wait

# 3. 验证
curl localhost:8080/api/repair/connectivity     # {"seat-service":"UP"}：网关 → repair → seat 整条链路是通的
curl localhost:8080/api/seat/rooms              # []：目前还没有数据
curl localhost:8080/api/seat/rooms/H9999        # 404，统一错误格式（含 code 和 traceId）
```

停止：`docker compose down`。**`docker compose down -v` 会删除数据卷、清空数据库**，只在需要重建数据库时使用。

本机的 3306 端口通常已被本地安装的 MySQL 占用，所以容器里的 MySQL 映射到宿主机的 **3307**。
可以用任意客户端连接 `localhost:3307`，账号和密码见 `deploy/.env`（例如 `seat` / `change-me-seat`）。

## 本地开发

```bash
# 测试：只测自己改动的模块，全量测试交给 CI（本机跑全量要 4～5 分钟）
# -am 表示一起构建它依赖的模块（libs/web-common），不加会报找不到 web-common
mvn verify -pl services/seat-service -am

# 只启动数据库，服务在本机用 Maven 运行（方便打断点调试）
cd deploy && docker compose up -d mysql && cd ..
mvn install -pl libs/web-common -am -DskipTests      # 先把公共模块装进本地 Maven 仓库；web-common 有改动后要重新执行
DB_PASSWORD=change-me-seat mvn -pl services/seat-service spring-boot:run
# PowerShell：$env:DB_PASSWORD="change-me-seat"; mvn -pl services/seat-service spring-boot:run
```

测试会自动启动一个真实的 MySQL 容器（Testcontainers），每次都从空库开始，执行全部迁移脚本。所以**跑测试时 Docker 必须处于运行状态**。

## 仓库结构

```
services/
  seat-service/  room-service/  repair-service/  platform-service/   四个业务服务，结构相同
  gateway/                                                           API 网关（只做路由，不连数据库）
libs/web-common/          所有服务共用的错误响应与异常处理（改动须组内评审）
deploy/
  compose.yaml            本地全栈：MySQL + 4 个服务 + 网关
  service.Dockerfile      所有服务共用的 Dockerfile
  mysql/init/             首次启动时建库、建账号的脚本
scripts/ci/               CI 检查脚本（迁移文件检查）
docs/
  requirements/  specs/  plans/  adr/      需求 → 设计 → 计划 → 决策记录
  REVIEW.md                                代码评审标准
AGENTS.md                 项目规则（AI 和人都要遵守），CLAUDE.md 只引用它
```

每个服务内部的分层都相同，规则写在各个包的 `package-info.java` 里：

```
cn.edu.fudan.spacewise.<服务>
├── api/       Controller 与 dto        → 只调用 service，不返回实体类
├── service/   业务逻辑                 → 可以调用 dao 和 client
├── dao/       MyBatis-Plus 的 Mapper 与实体类 → 只访问本服务的库
├── client/    调用其他服务（@HttpExchange）
└── config/
```

## 开发一个功能

1. **需求**：写进 `docs/requirements/`，编号如 `REQ-M1-003`，必须有验收标准。
2. **计划**：非琐碎的任务先写 `docs/plans/YYYY-MM-DD-简述.md`，组内确认后再写代码。重要的取舍写成 ADR，并列出候选方案。
3. **分支**：从 `main` 拉出 `feat/<简述>` 或 `fix/<简述>`。
4. **实现**：照着已有的示例写。
   | 要做的事 | 照着这个写 |
   |---|---|
   | 对外接口，api → service → dao | `seat-service` 的 `RoomController` → `RoomService` → `RoomMapper` |
   | 业务错误 | `RoomService.getRoom` 抛出 `BusinessException(SeatErrorCode.ROOM_NOT_FOUND, …)` |
   | 参数校验 | `RoomController.getRoom` 上的 `@Pattern` |
   | 调用其他服务 | `repair-service` 的 `SeatClient`（`client/`）与 `ServiceClientConfig`（`config/`） |
   | 改表结构 | 新增 `src/main/resources/db/migration/V<下一个整数>__<小写描述>.sql`，**不要修改已有的迁移文件** |
   | 集成测试 | 测试类加 `@IntegrationTest`，参考 `RoomApiTest` |
5. **提交**：用 Conventional Commits 格式，如 `feat(seat): ...`；数据库迁移单独一个 commit。
6. **PR**：按模板填写，包括"AI 参与情况"。CI 通过、评审通过后才能合并。

完整的规则见 [`AGENTS.md`](AGENTS.md)。用 Claude Code、Codex、Qoder 等 AI 工具时，它们会自动读取这个文件。

## CI 与分支保护

每个指向 `main` 的 PR 都必须通过以下检查才能合并：

- `build`：`mvn -B verify`，跑全部模块的测试，约 2 分钟。
- `migration-guard`：已合并的迁移文件不能修改、删除或重命名；同一服务内版本号不能重复；文件名必须符合规范。
- 分支必须和 `main` 同步；PR 中的讨论必须全部解决。管理员同样受这些规则约束。

## 排查问题

- **查看日志**：`cd deploy && docker compose logs -f seat-service`
- **追踪一次请求**：每个经过网关的响应都带有 `X-Trace-Id` 响应头，错误响应体里也有 `traceId`。用它搜索所有服务的日志：
  ```bash
  docker compose logs | grep <traceId>
  ```
  这次请求在网关、repair、seat 等服务里留下的日志会全部列出来。
- **服务之间是否连通**：`curl localhost:8083/internal/connectivity`
- **容器状态**：`docker compose ps`。服务冷启动可能要 1～2 分钟，这段时间显示 `starting` 是正常的。

## 尚未完成

| 项 | 说明 |
|---|---|
| 业务功能 | 由各模块组按需求文档开发 |
| 登录与权限（RBAC） | 属于公共层 D；确定方案后，在网关上加过滤器实现 |
| 公共层 D 的归属 | 需要组内决定，并写成决策记录（任务书要求） |
| 自动部署（CD） | 等服务器确定后再做：tag → 构建镜像 → 测试环境自动部署 → 生产环境人工审批 → 可以回滚 |
| 前端 | 框架（Vue3 还是 React）待定 |
