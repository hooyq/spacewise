package cn.edu.fudan.spacewise.platform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 集成测试统一使用本注解：启动完整应用（随机端口）并连接 Testcontainers 的 MySQL。
 * 配置完全相同的测试类会被 Spring 复用同一个上下文，因此整个测试运行只启动一个 MySQL 容器。
 * 不要在单个测试类上再叠加改变上下文的配置（如 @MockitoBean、@TestPropertySource），否则会额外启动上下文和容器。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}
