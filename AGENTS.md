# Spacewise · 智空间 校园资源服务平台

本文件是给 AI 编程助手（Claude Code / Codex / Qoder 等）的项目规则，人也应当遵守。
保持在一页以内：只写“AI 不知道就会犯错”的事。发现 AI 反复犯同一个错，就把它加进来。

## 项目是什么
复旦软件过程管理课程 PJ。以自习座位预约为核心，延伸到整教室预约审批和物业报修。

| 模块 | 名称 | 服务目录 | 端口 | 数据库 / 账号 |
|---|---|---|---|---|
| M1 | 自习座位预约 | `services/seat-service` | 8081 | `seat_db` / `seat` |
| M2 | 整教室预约与审批 | `services/room-service` | 8082 | `room_db` / `room` |
| M3 | 物业报修工单 | `services/repair-service` | 8083 | `repair_db` / `repair` |
| D  | 公共层：RBAC、空间主数据、通知、审计 | `services/platform-service` | 8084 | `platform_db` / `platform` |

网关 `services/gateway`（8080）是浏览器的唯一入口：`/api/<模块>/**` 原样转发给对应服务，其余路径（含 `/internal/**`）一律 404。
每个服务只能连自己的库，账号权限由数据库强制。

跨模块写回：M3 维修中 → M1 下架该室座位；M2 审批通过 → M1 占用该室时段座位。
M1 的座位状态必须记录写入来源（M1 / M2 / M3）。

## 技术栈
Java 21 · Spring Boot · Maven · Flyway · Docker Compose · GitHub Actions。
数据库、前端框架见 `docs/adr/`（未决定前不要擅自引入）。

## 红线（不得违反）
1. **已合并的 Flyway 迁移脚本绝不修改**，只新增版本号。
2. **服务之间只通过 HTTP 接口通信**，禁止读写其他服务的 schema 或表。
3. **不提交 `.env`、密钥、证书**；配置示例放 `*.example.env`。
4. **不自行解决合并冲突**，遇到冲突停下来交给人。
5. **不引入新的基础设施组件**（注册中心、MQ、缓存等），除非已有对应 ADR。
6. **测试必须验证行为**：禁止断言常量、禁止 mock 被测逻辑本身、禁止为了通过而改测试。修 bug 先写一个会失败的测试。

## 代码分层（每个服务相同，详见各包的 `package-info.java`）
`api`（Controller、`api/dto`）→ `service` → `dao`（MyBatis-Plus Mapper、`*Entity`）/ `client`（调其他服务）；`config` 放配置。
- api 只调用 service；**不返回实体类**，一律转换成 dto。
- 要用其他服务的数据，只能通过 `client`（`@HttpExchange` 接口）调用对方的 api，调用方自己声明用到的接口，不引用对方代码。
  客户端用 `config/HttpClients` 创建（传入 Spring 注入的 `RestClient.Builder`，否则 traceId 传不到下游），
  地址和超时配置在 `spacewise.services.<名称>`；框架不自动重试。示例：repair-service 的 `SeatClient`。
- 路径：对外 `/api/<模块>/...`（**不带这个前缀，网关转发不到**），服务间内部 `/internal/...`。
- 下游服务不可用不能让本服务的 `/actuator/health` 变 DOWN（避免故障沿调用链扩散）。

## 错误响应（ADR-0006，由 `libs/web-common` 自动生效）
- 成功：直接返回数据（dto），**不要包 `Result`**。失败：统一为 ProblemDetail + `code` + `traceId`，用 HTTP 状态码表示成败。
- 业务错误：`throw new BusinessException(XxxErrorCode.YYY, "给调用方看的说明")`；每个服务一个错误码枚举，`code` 带模块前缀（`SEAT.ROOM_NOT_FOUND`）。
  说明里不要放内部信息或个人信息；错误码发布后不改名。
- 参数校验用 `@Valid` / `@Pattern` 等注解，失败自动返回 400 `COMMON.VALIDATION_FAILED` 和字段列表。
- 不要在 Controller 里 try-catch 后自己拼错误 JSON；也不要把异常信息返回给调用方（500 只返回固定文案）。
- `libs/web-common` 是公共代码，改动须组内评审。

## 日志
- 用 SLF4J（`LoggerFactory.getLogger`），不用 `System.out` / `printStackTrace`。每行日志自动带 `[traceId-spanId]`。
- 排查一次请求：取网关响应头 `X-Trace-Id`，执行 `cd deploy && docker compose logs | grep <traceId>`。
- **不得记录密码、token、学号、手机号等个人信息**；记 ID，不记内容。
- 要在测试里验证 trace 传递，测试类加 `@AutoConfigureObservability`（Spring Boot 测试默认关闭追踪）。

## 工作流程
需求 `docs/requirements/` → 设计 `docs/specs/` → 计划 `docs/plans/YYYY-MM-DD-name.md` → 实现 → PR。
- 非琐碎任务：先出计划，人确认后再写代码。计划要具体到“没看过对话的人也能照着实现”。
- 重要取舍写 ADR（`docs/adr/NNNN-name.md`，模板见 `0000-template.md`），列出候选方案而不是只写结论。
- 需求、设计按模块一个文件，新功能在原文件里追加或修改；计划、ADR 每次新建。
- **回填（在同一个 PR 里做，不等合并后）**：需求状态改为“已完成”，填“实现”（PR 链接）和“验证”（测试类.方法 / 手动验收）；
  spec 改成实际实现的样子；计划末尾写偏差记录。开工时把需求状态改为“开发中”。
- 一次改动只做一件事，保持 diff 可审查；不顺手重构无关代码。

## Git 约定
- 分支（ADR-0001）：从 `dev` 拉出 `feat/<简述>`、`fix/<简述>` → PR → `dev`；发布时 `dev` → PR → `main`。
  **PR 目标默认是 `dev`**；`main` 只接受来自 `dev` 的 PR。两个分支都受保护，不能直接 push。
- 提交信息用 Conventional Commits：`feat(seat): ...`、`fix(repair): ...`、`docs: ...`、`ci: ...`。
  scope 用模块名：`seat` `room` `repair` `platform` `frontend` `deploy`。
- 提交说明写清做了什么、为什么，关联需求编号（如 `Refs: REQ-M1-003`）。
- 数据库迁移单独 commit，不和业务代码混在一起。
- 发布：在 `main` 上打 tag `vX.Y.Z` → 人工审批 → 部署生产。

## 常用命令
- 本地**只测改动的模块**：`mvn verify -pl services/<服务> -am`（`-am` 一起构建依赖的 `libs/web-common`，不加会找不到它）（需要 Docker，测试用 Testcontainers 启动真实 MySQL）。
  全量 `mvn verify` 本机要 4～5 分钟，交给 CI 跑，不要每次改动都本地跑全量
- 本地全栈：`cd deploy && cp compose.example.env .env && docker compose up -d --build`（MySQL 映射到宿主机 3307，服务端口见上表）
  - 只要数据库、服务在本机用 mvn 跑：`docker compose up -d mysql`
  - 所有服务共用 `deploy/service.Dockerfile`，构建参数 `SERVICE` 指定服务目录名
  - `docker compose down -v` 会删除数据卷、清空所有数据；修改 `deploy/mysql/init/` 后必须这样重建才生效
- 本地启动单个服务：先 `mvn install -pl libs/web-common -am -DskipTests`（web-common 改动后要重新执行），
  再设置 `DB_PASSWORD`（与 `deploy/.env` 一致）后 `mvn -pl services/seat-service spring-boot:run`，
  健康检查 `curl localhost:8081/actuator/health`
- 迁移脚本放 `services/<服务>/src/main/resources/db/migration/V<整数>__<小写下划线描述>.sql`；每个服务只连自己的库（`seat_db` 等）
  - 本地自检：`bash scripts/ci/check-migrations.sh`（CI 的 migration-guard 跑的就是它）
- 集成测试类统一加 `@IntegrationTest`，不要自己组合 `@SpringBootTest` 等注解，否则会多启动一个 MySQL 容器
- 版本号只在根 `pom.xml` 维护；子模块依赖不写版本（由 Spring Boot 父 POM 管理）

## CI
- PR（目标 `dev` 或 `main`）必须通过 `build`（`mvn -B verify`）和 `migration-guard`，且与目标分支同步，才能合并。
  只改 `docs/` 或 `*.md` 的 PR，`build` 跳过编译和测试直接通过（`scripts/ci/docs-only.sh`）。
- 新增 GitHub Action：官方 action（`actions/*`）用主版本标签；**第三方 action 必须锁定 commit SHA**。
