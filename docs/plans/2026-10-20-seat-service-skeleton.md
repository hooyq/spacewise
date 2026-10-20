# 计划：seat-service 最小骨架

- 日期：2026-10-20
- 关联：搭建路线第 2 步；ADR-0002（Spring Boot 3.5）
- 状态：已实现，待 PR 评审

## 目标

建立 Maven 多模块结构和第一个可启动的服务 `seat-service`，暴露健康检查，并有一个能证明“服务能启动且健康”的测试。
后续 Docker healthcheck、Compose `depends_on`、CI 都依赖这个健康检查。

## 范围

做：
- 根 `pom.xml`：继承 `spring-boot-starter-parent:3.5.16`，统一 Java 21，声明子模块。
- `services/seat-service`：启动类、`application.yml`、健康检查测试。
- `AGENTS.md` “常用命令”补充构建与启动命令。

不做：
- 业务接口、数据库、Flyway、Dockerfile（第 3 步）。
- CI（第 4 步）、其他服务。

## 改动清单

| 文件 | 内容 |
|---|---|
| `pom.xml` | groupId `cn.edu.fudan.spacewise`，artifactId `spacewise-parent`，`packaging=pom`，`<modules>` 含 `services/seat-service`，`java.version=21` |
| `services/seat-service/pom.xml` | parent 为根 POM；依赖 `spring-boot-starter-web`、`spring-boot-starter-actuator`、`spring-boot-starter-test`(test)；插件 `spring-boot-maven-plugin` |
| `.../seat/SeatServiceApplication.java` | 包 `cn.edu.fudan.spacewise.seat`，`@SpringBootApplication` 启动类 |
| `src/main/resources/application.yml` | `spring.application.name=seat-service`，端口 `8081`，只暴露 `health` 端点 |
| `src/test/java/.../HealthEndpointTest.java` | `@SpringBootTest(RANDOM_PORT)`，HTTP GET `/actuator/health`，断言 200 且 `status=UP` |
| `AGENTS.md` | 常用命令：`mvn verify`、`mvn -pl services/seat-service spring-boot:run` |

端口约定（后续服务沿用）：seat 8081、room 8082、repair 8083、platform 8084、gateway 8080。

## 验收标准

1. 仓库根目录 `mvn verify` 成功，测试通过。
2. `mvn -pl services/seat-service spring-boot:run` 后，`curl localhost:8081/actuator/health` 返回 `{"status":"UP"}`。
3. **测试有效性**：删掉 actuator 依赖后 `mvn verify` 失败（测试拿到 404），恢复后通过。

## 风险

- 测试只验证“能启动 + 健康”，不涉及业务；这是有意的，业务测试随第一个需求加入。
- 端口约定若与组员本机冲突，可用环境变量 `SERVER_PORT` 覆盖。

## 实现偏差记录

- 无功能偏差。
- 验收 3 过程中的教训：第一次只用 sed 删掉 `<artifactId>` 一行，POM 变得不合法，构建失败的原因是 POM 解析错误而不是测试。
  “故意破坏”时要确认失败来自**测试断言**（本次为 `expected: 200 OK but was: 404 NOT_FOUND`），否则证明不了测试有效。
- 额外验证：启动 jar 后 `/actuator/env` 返回 404，确认只暴露了 health。
