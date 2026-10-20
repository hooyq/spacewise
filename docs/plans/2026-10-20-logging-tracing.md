# 计划：日志与请求追踪（搭建路线第 8 步）

- 日期：2026-10-20
- 关联：修订后的搭建路线（原第 8 步 CD 因服务器未定而推后，本步改为日志与追踪）
- 状态：已实现，待 PR 评审

## 目标

出问题时能用一个 traceId 把一次请求在网关和各服务里的日志全部找出来；网关对每个请求记一行访问日志；
Docker 日志不会无限增长，避免撑满服务器磁盘。**不包含业务，不引入新的基础设施组件。**

## 范围

做：
- 5 个模块加入 `micrometer-tracing-bridge-brave`（版本由 Spring Boot 管理）。
  它只负责生成并传递 traceId，并把 traceId 写进日志；不需要部署 Zipkin 等追踪服务器。
  Spring Boot 默认的日志格式在启用追踪后会自动带上 `[traceId-spanId]`。
- 修正 `HttpClients`：改用 Spring 自动配置的 `RestClient.Builder`。原来自己 new 的 RestClient 不会把 traceId 传给下游服务。
- gateway：`AccessLogFilter`
  - 每个请求记一行日志：方法、路径、状态码、耗时；
  - 在响应头里加上 `X-Trace-Id`，前端或用户报错时附上它，就能查到对应的日志。
- repair：新增对外接口 `GET /api/repair/connectivity`，与 `/internal/connectivity` 做同一件事。
  这样就有一条“网关 → repair → seat”的完整链路，可以用来演示和验证追踪。seat 的 ping 和 repair 的连通性检查各记一行 info 日志。
- compose：所有容器统一做日志轮转（`json-file`，单个文件 10MB，最多保留 3 个）。
- AGENTS.md：日志约定（用 SLF4J，禁止 `System.out`；**不记录密码、token、学号、手机号等个人信息**；如何按 traceId 查日志）。

不做：
- 集中日志平台（ELK、Loki）、追踪服务器（Zipkin）：属于新的基础设施，需要先写 ADR。
- JSON 格式的结构化日志（只有接入日志平台后才有用）。
- 业务上的操作审计（属于公共层 D）。

## 测试

| 测试 | 验证什么 |
|---|---|
| gateway `RoutingTest`（扩展，加 `@AutoConfigureObservability`） | 转发给下游的请求带有 `traceparent` 头；响应带有 `X-Trace-Id`，且与传给下游的 traceId 一致 |
| repair `SeatClientTest`（调整） | `HttpClients` 的新签名下，原有的路径、错误、超时用例都仍然通过 |

repair → seat 的 traceId 传递，如果放在 Spring 测试里验证，需要改 seat 的地址，这会让 Spring 新建上下文、多启动一个 MySQL 容器。
因此这一点由 Compose 验收覆盖（验收 2）。

## 验收标准

1. CI 通过。本地只测 gateway 和 repair 两个模块。
2. Compose 下执行 `curl -i localhost:8080/api/repair/connectivity`：返回 `{"seat-service":"UP"}`，响应头里有 `X-Trace-Id`；
   用这个 traceId 去 `docker compose logs | grep`，能同时找到 **gateway、repair-service、seat-service** 三个服务的日志。
3. `docker inspect` 显示每个容器的日志配置为 `max-size=10m`、`max-file=3`。

## 风险

- 追踪默认按 10% 采样。采样率只影响“是否上报追踪数据”，我们没有上报目标，所以 traceId 照样会写进日志、照样会传递。
  如果实测不是这样，就把采样率设为 1.0。
- 网关自己的转发是否会传递 `traceparent`，取决于 Spring Cloud Gateway 内部是否使用了带观测的 HTTP 客户端，以测试结果为准。

## 实现偏差记录

- 验收结果：
  - gateway 共 7 个测试通过，repair 共 6 个测试通过（本地只测了这两个模块，其余以 CI 为准）。
  - Compose 下：`/api/repair/connectivity` 返回 `{"seat-service":"UP"}`，响应头带 `X-Trace-Id`。
    用它 grep 日志，**gateway、repair-service、seat-service 三个服务都找到了**。连续 5 次请求，每次都能串起来。
  - 6 个容器的日志配置都是 `json-file`，`max-size=10m`、`max-file=3`。
- **差点犯的错（值得记录）**：第一次运行时，网关转发的请求没有带 `traceparent`。调试时看到 `inject={}`、`sampled=false`，
  据此接连得出两个结论：“Gateway MVC 不会传 trace，需要自己写一个过滤器补上”“未采样的请求不传 trace，采样率必须设为 1.0”，
  并据此写了 `TracePropagationFilter`，也改了采样率。
  后来发现**根本原因是 Spring Boot 在测试中默认关闭追踪**（传递器变成了空实现），两个结论都是测试环境造成的假象。
  给测试加上 `@AutoConfigureObservability` 后，去掉过滤器、恢复默认采样率，测试照样通过，所以两项改动都已撤销。
  教训：测试环境和真实运行环境不一致时，从测试里得出的“框架不支持”的结论要先怀疑测试环境。
- 偏差：
  - 计划中担心的“网关可能不传 trace”实际不存在，没有新增任何网关转发相关的代码。
  - 验收中出现过一次：6 个容器同时启动，又赶上整套镜像重新构建完，机器负载高，seat 启动用了 93 秒，
    超过了健康检查的容忍时间（宽限期 40s，加 5 次重试，约 90s），被短暂判为 unhealthy，之后自行恢复。
    正常情况下启动只要十几秒。后续做 CD、上服务器时，如果服务器性能较弱，需要调大 `start_period`。
