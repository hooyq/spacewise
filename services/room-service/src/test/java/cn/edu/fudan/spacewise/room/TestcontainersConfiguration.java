package cn.edu.fudan.spacewise.room;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;

/**
 * 测试用的真实 MySQL：每次测试运行启动一个全新的空库，Flyway 从 V1 开始重放全部迁移，用完即销毁。
 * {@code @ServiceConnection} 让 Spring 自动把数据源指向这个容器，覆盖 application.yml 中的配置。
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>("mysql:8.4").withDatabaseName("room_db");
    }
}
