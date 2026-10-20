# 计划：服务内分层约定 + 服务间调用机制（搭建路线第 6 步）

- 日期：2026-10-20
- 关联：修订后的搭建路线第 6 步；ADR-0004（MyBatis-Plus）；AGENTS.md 红线 2、5；任务书期末演示“跨模块网络调用”
- 状态：已实现，待 PR 评审（MyBatis-Plus、`@HttpExchange` 客户端由调用方自己写、框架不做重试；分层与调用合并为一个 PR）

## 目标

1. 统一每个服务内部的代码分层：`api` / `service` / `dao` / `client` / `config`，并规定各层之间的依赖规则。
2. 选定 dao 层技术（MyBatis-Plus），四个服务全部接入。
3. 统一服务间调用的写法：调用方在 `client` 层用 `@HttpExchange` 声明对方接口，用 repair → seat 的技术示例证明能跑通。

模块组以后写业务，按照这个分层和模板来写。**不包含任何业务。**

## 分层约定

```
cn.edu.fudan.spacewise.<服务>
├── api/       Controller；api/dto/ 放请求和响应对象。只依赖 service
├── service/   业务逻辑。依赖 dao、client
├── dao/       MyBatis-Plus 的 Mapper 和实体类（实体以 Entity 结尾），只访问本服务的库
├── client/    调用其他服务的 HTTP 接口声明及其响应对象
└── config/    配置类
```
- 实体类（Entity）不能出现在 api 层的返回值里，要先转换成 dto 再返回。原因是表结构变化不应直接影响接口。
- 要用其他服务的数据，只能通过 client 调用对方的 api，不能用 dao 去读对方的库（红线 2）。
- 路径约定：对外接口用 `/api/<模块>/...`（如 `/api/seat/rooms`），服务间内部接口用 `/internal/...`。
  第 7 步网关按前缀转发、不改写路径，并且不对外暴露 `/internal/**`。
- 每个层的包里放一个 `package-info.java`，写清本层的职责。AI 和人打开包就能看到规则。

## 范围

做：
- 根 `pom.xml`：在 `dependencyManagement` 中统一管理 MyBatis-Plus 版本（3.5.17）。
- 四个服务：加入 `mybatis-plus-spring-boot3-starter`；建立五个层的包，每个包里放 `package-info.java`。
- seat-service 分层示例（以已有的 `room` 表为例，只读）：
  `GET /api/seat/rooms` → `RoomService` → `RoomMapper`；`RoomEntity` → `RoomResponse`。
- seat-service：`GET /internal/ping`，返回 `{"service":"seat-service"}`。
- repair-service 调用示例：
  - `client/SeatClient`：用 `@HttpExchange` 声明 seat 的 `/internal/ping`；
  - `config/HttpClients` + `config/ServiceClientConfig`：从 `spacewise.services.<名称>` 读取地址和超时，生成客户端；
  - `GET /internal/connectivity` → `ConnectivityService` → `SeatClient`，返回 `{"seat-service":"UP"}` 或 `"DOWN"`。
- 配置：`spacewise.services.seat.base-url` 默认为 `http://localhost:8081`；Compose 中设为 `http://seat-service:8081`。
  超时统一为连接 2s、读取 5s。
- AGENTS.md：分层规则、路径约定、服务间调用约定。

不做：
- 统一响应格式、全局异常处理、分页插件、参数校验（后续框架步骤）。
- 重试、熔断、异步、outbox（属于业务取舍）。
- 服务间认证（等 D 组定下认证方案后再做）。

## 测试

| 测试 | 方式 | 验证什么 |
|---|---|---|
| seat `RoomApiTest` | `@IntegrationTest`，先插入一行，再通过 HTTP GET 查询，最后清理 | 从 api 到 dao 的分层链路，以及实体转 dto |
| seat `InternalPingTest` | `@IntegrationTest` | ping 端点 |
| repair `SeatClientTest` | 不启动 Spring；用 JDK 自带的 `HttpServer` 在随机端口启动一个假的 seat | 真实的 HTTP 请求：路径正确、正常响应能解析、5xx 抛异常、超时抛异常（读取超时设为 200ms，假 seat 延迟 1s） |
| repair `ConnectivityServiceTest` | 手写一个假的 `SeatClient`（实现接口，不用 mock 框架） | 成功返回 UP，抛异常返回 DOWN |

repair 的客户端测试**不启动 Spring 上下文**。原因是如果用 `@DynamicPropertySource` 去改 seat 的地址，Spring 会新建一个上下文，就会多启动一个 MySQL 容器。

## 验收标准

1. `mvn verify` 通过，CI 通过。
2. Compose 下：`curl localhost:8081/api/seat/rooms` 返回 JSON 数组；`curl localhost:8083/internal/connectivity` 返回 `{"seat-service":"UP"}`。
3. `docker compose stop seat-service` 后，connectivity 返回 `DOWN`（HTTP 200），repair 自己的 `/actuator/health` 仍为 UP。
4. **测试有效性**：
   - 把 `SeatClient` 的路径改成 `/internal/pingx`，`SeatClientTest` 失败；
   - 把超时配置的读取改成不生效（不设置 readTimeout），超时测试失败；
   - 让接口直接返回 `RoomEntity`、不转 dto，`RoomApiTest` 失败。`RoomResponse` 只有 `code`、`building` 两个字段，
    测试断言响应里恰好是这两个字段；实体类多出的 `id`、`createdAt` 一旦泄露到接口，测试就会失败。

## 风险

- 用假 seat 做的测试，只能证明 repair 按约定发出了请求，不能证明 seat 真的有这个接口；这一点由验收 2 的 Compose 联调覆盖。
- 分层规则目前只写在文档和 `package-info` 里，没有自动检查。后续可以加 ArchUnit 测试，把规则变成机制。

## 实现偏差记录

- 验收结果：
  - `mvn verify` 通过，共 13 个测试，本地耗时 304s。
  - Compose 下：`/api/seat/rooms` 返回 `[{"code":"H3209","building":"光华楼"}]`；`/internal/connectivity` 返回 `{"seat-service":"UP"}`。
  - 停掉 seat-service 后：connectivity 返回 `{"seat-service":"DOWN"}`，HTTP 200；repair 的 health 仍为 UP；
    日志中记录了 `Connect timed out`。seat 恢复后，connectivity 回到 UP。
  - 故意破坏：
    - 去掉 `setReadTimeout` 后，超时测试失败；
    - 接口直接返回实体后，`RoomApiTest` 失败，响应中泄露了 `id` 和 `createdAt`；
    - 路径改成 `/internal/pingx`：**第一次没有失败**，见下面第一条发现。
- 发现：
  1. **测试本身有漏洞，是故意破坏暴露出来的。** JDK `HttpServer.createContext("/internal/ping")` 按前缀匹配，
     `/internal/pingx` 也会被交给假 seat 处理，所以路径写错时测试照样通过。修复方法是让假 seat 只接受完全一致的路径，
     其他路径返回 404。修复后，同样的破坏会让测试失败。这是“用故意破坏证明测试有效”的一个典型收获。
  2. **MySQL 容器内命令行客户端的中文乱码。** 用 `docker exec ... mysql -e` 手动插入中文时，数据被存成了乱码
     （`@@character_set_client = latin1`，“光”字被存成 `C3A5E280A6...`，而不是 `E58589`）。
     原因是客户端在未设置语言环境时退回 latin1。已在 compose 中给 mysql 设置 `LANG: C.UTF-8`，客户端变为 utf8mb4。
     应用走 JDBC，不受这个问题影响。
  3. **Docker 构建时下载依赖不稳定。** 本机 Maven 配置了阿里云镜像，Docker 构建却直连 Maven Central，
     第一次构建因 TLS 握手被断开而失败，重试成功。CI 在 GitHub 上没有这个问题。
     如果频繁出现，可以给 `service.Dockerfile` 加一个可选的镜像源构建参数。
- 偏差：
  - api 层规则从“不得使用 dao”改为“不得调用 dao 的 Mapper 或 client；可以引用 service 返回的实体类型，用于转换成 dto”。
    原来的写法和 `RoomResponse.from(RoomEntity)` 矛盾。
  - 示例里实体和 dto 的转换写在 dto 的静态方法 `from` 中，没有单独建 converter 类。
