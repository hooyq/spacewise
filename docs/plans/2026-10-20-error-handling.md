# 计划：统一错误响应与全局异常处理（搭建路线第 9 步）

- 日期：2026-10-20
- 关联：ADR-0006（错误响应格式与公共模块）；第 8 步（traceId）
- 状态：已实现，待 PR 评审（成功不包装 + 失败用 ProblemDetail；公共模块 `libs/web-common`；错误码为带模块前缀的字符串）

## 目标

所有服务（含网关）出错时返回同一种格式：

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "教室 H9999 不存在",
  "instance": "/api/seat/rooms/H9999",
  "code": "SEAT.ROOM_NOT_FOUND",
  "traceId": "6ad7..."
}
```

成功时直接返回数据，不包装；用 HTTP 状态码表示成功或失败。业务代码只需要 `throw new BusinessException(错误码, 说明)`。
**不包含业务**；seat 中加一个查询单个教室的示例接口。

## 范围

做：
- 新模块 `libs/web-common`（普通 jar，不是服务）：
  - `ErrorCode` 接口（`code()`、`status()`）；`CommonErrorCode`（`COMMON.*`）；`BusinessException`；
  - `ErrorResponses`：生成带 `code`、`traceId` 的 `ProblemDetail`；
  - `GlobalExceptionHandler`：继承 Spring 的 `ResponseEntityExceptionHandler`（它已经能把 404、405、400 等标准异常转成 ProblemDetail），
    统一补上 `code` 和 `traceId`；另外处理 `BusinessException`、参数校验失败（列出字段错误）以及所有未预料的异常；
  - 通过 Spring Boot 自动配置注册，服务引入依赖即可生效，无需额外配置。
- 根 pom 增加模块，并在 `dependencyManagement` 中声明 web-common；5 个模块（4 个服务和网关）都依赖它。
  web-common 同时带上 `spring-boot-starter-validation`，服务可以直接用 `@Valid`。
- gateway：`DownstreamErrorHandler` 改用 `ErrorResponses`，错误码 `GATEWAY.DOWNSTREAM_UNAVAILABLE` / `GATEWAY.DOWNSTREAM_TIMEOUT`。
- seat 示例：`GET /api/seat/rooms/{code}`，查到则 200；查不到则 404 + `SEAT.ROOM_NOT_FOUND`；
  `code` 格式不合法时返回 400 + `COMMON.VALIDATION_FAILED`。
- `deploy/service.Dockerfile`：构建时复制 `libs/`。
- `.github/CODEOWNERS`：`/libs/` 需要组内评审。
- AGENTS.md：错误处理约定。ADR-0006。

不做：
- 调用方解析下游的错误码（例如 repair 收到 seat 的 `SEAT.ROOM_NOT_FOUND` 后做相应处理），等真实业务需要时再做。
- 在进入 Spring MVC 之前就出错的请求（如 Tomcat 层拒绝的畸形请求），仍由 Spring Boot 默认的 `/error` 处理，格式不同。

## 错误类型与响应

| 情况 | 状态码 | code |
|---|---|---|
| `BusinessException` | 由错误码决定 | 业务错误码，如 `SEAT.ROOM_NOT_FOUND` |
| `@Valid` 请求体校验失败、方法参数校验失败 | 400 | `COMMON.VALIDATION_FAILED`，并附 `errors: [{field, message}]` |
| 其他 Spring MVC 标准异常（路径不存在、方法不允许、请求体格式错误等） | 对应状态码 | `COMMON.NOT_FOUND`、`COMMON.METHOD_NOT_ALLOWED`、`COMMON.BAD_REQUEST`…… |
| 未预料的异常 | 500 | `COMMON.INTERNAL_ERROR`。`detail` 固定为“服务器内部错误”，**不返回异常信息**；完整堆栈只写日志（带 traceId） |

## 测试

- web-common `GlobalExceptionHandlerTest`：测试源码里放一个最小应用和一个测试用 Controller，分别抛出各类异常。验证：
  - 状态码、`code`、`errors` 正确；
  - 500 的响应体里**不包含**异常信息（异常信息中故意放入 `SELECT * FROM secret_table`）；
  - 开启追踪时带有 `traceId`。
- seat `RoomApiTest`：查到返回 200；查不到返回 404 + `SEAT.ROOM_NOT_FOUND`；格式不合法返回 400 + `COMMON.VALIDATION_FAILED`。
- gateway `DownstreamFailureTest`：在原有状态码断言之外，再断言 `code`。

## 验收标准

1. CI 通过；本地测试 web-common、seat、gateway。
2. Compose 下：`curl localhost:8080/api/seat/rooms/H9999` 返回 404，`code` 为 `SEAT.ROOM_NOT_FOUND`，带 `traceId`；
   `curl localhost:8081/api/seat/nothing` 返回 ProblemDetail 格式（不再是 Spring Boot 默认的 `timestamp/error/path` 格式）。

## 风险

- 公共模块的改动会影响所有服务：放进 CODEOWNERS，要求组内评审；只放框架代码，不放业务代码。
- `@RestControllerAdvice` 的匹配顺序是先按 Bean 的顺序、再在同一个 advice 内按异常类型匹配。全局处理器设为最低优先级，
  否则它的 `Exception` 兜底处理会抢在网关的 `DownstreamErrorHandler` 之前生效。

## 实现偏差记录

- 验收结果：
  - 本地测试了 web-common（8 个）、seat（9 个）、gateway（7 个），全部通过；room、repair、platform 只增加了依赖，以 CI 为准。
  - 故意破坏一次：让 500 返回异常信息，`unexpectedExceptionHidesInternalDetails` 失败（响应中出现了 `secret_table`）。
  - Compose 下，经网关访问：
    - `/api/seat/rooms/H9999` → 404 `SEAT.ROOM_NOT_FOUND`
    - `/api/seat/rooms/H3209` → 200，直接返回数据
    - `/api/seat/rooms/bad_code!` → 400 `COMMON.VALIDATION_FAILED`，带 `errors`
    - 直接访问 seat 的 `/api/seat/nothing` → 404 `COMMON.NOT_FOUND`
    - `/internal/ping` → 404

    以上都带 `traceId`。
- 偏差：
  - 404 的说明文字：Spring 默认是 “No static resource xxx.”，会让人误以为是在请求静态文件，改为“请求的路径不存在”。
  - **健康检查宽限期 `start_period` 从 40s 调到 120s**（单独的 fix 提交）。原因是连续两步（第 8、9 步）都出现了同样的情况：
    整套镜像重新构建后，6 个容器同时冷启动，Java 服务实测要 93～132 秒才能就绪，超过了原来约 90 秒的容忍时间，
    导致 `docker compose up --wait` 失败。宽限期内的失败不计数，服务就绪后状态会立即变为 healthy，所以调大不会拖慢正常启动。
  - web-common 只在测试范围内依赖 actuator 和 Brave（用于验证 traceId），运行时由各服务自己提供。
