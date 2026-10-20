# ADR-0005：API 网关采用 Spring Cloud Gateway（Server WebMVC）

- 状态：提议中（待组内确认）
- 日期：2026-10-20
- 决策人：（待填）
- 相关：ADR-0001、ADR-0002（Spring Boot 3.5）；AGENTS.md 红线 5；搭建路线第 7 步

## 背景

浏览器需要一个统一入口访问 4 个后端服务；服务间内部接口（`/internal/**`）和运维端点（`/actuator/**`）不能对外暴露；
以后登录与权限校验最好只在一处做。引入网关属于新增基础设施组件，按红线 5 需要 ADR。

## 候选方案

### 方案 A：Spring Cloud Gateway
- 优点：与各服务同为 Java/Spring 技术栈；路由写在 yml 里；以后的认证过滤器用 Java 写在网关一处，组员能读懂、能写测试。
- 缺点：引入 Spring Cloud，需要管理一个与 Spring Boot 配套的 BOM 版本；多一个要部署的进程。

### 方案 B：只用 Nginx
- 优点：轻量；前端静态文件本来也需要它托管。
- 缺点：认证要么在每个服务各做一遍，要么用 `auth_request` 调用 platform-service，组员不熟，也难以测试。

### A 的两个版本

- **A1：Server WebMVC**：基于 Spring MVC（Servlet），过滤器是普通同步代码，与各服务的编程模型相同。
- **A2：Server WebFlux**：基于响应式编程（`Mono`/`Flux`），资料较多，高并发下性能更好；组员需要额外学习一套编程模型，课程项目的流量用不上它的优势。

<!-- 候选方案由 AI 提出，人确认选择 A1。 -->

## 决定

选择 **A1：Spring Cloud Gateway Server WebMVC**。根 `pom.xml` 引入 Spring Cloud BOM `2025.0.3`（对应 Spring Boot 3.5）。
第 9 步的 Nginx 只负责托管前端静态文件，并把 `/api` 转发给网关。

## 依据

关键因素是**认证集中、而且用组员熟悉的写法实现**。WebMVC 版不要求组员学习响应式编程。

## 代价与后续

- **版本配套**：升级 Spring Boot 时必须同步升级 Spring Cloud BOM（2025.0.x 对应 Boot 3.5，2025.1.x 对应 Boot 4.0）。
- 路由按前缀匹配，不改写路径：服务的对外接口必须以 `/api/<模块>` 开头，否则经网关访问不到。
- 转发超时：连接 2s、读取 10s；下游超时返回 504，连不上返回 502（`DownstreamErrorHandler`）。
- 浏览器请求会多经过一跳；以后如果出现性能问题，再评估改用 WebFlux 版本或其他方案。

## 如何达成一致

（待组内讨论后填写）
