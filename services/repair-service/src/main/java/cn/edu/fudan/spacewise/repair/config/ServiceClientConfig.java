package cn.edu.fudan.spacewise.repair.config;

import cn.edu.fudan.spacewise.repair.client.SeatClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** 本服务调用的下游客户端。新增下游时：在 client 包声明接口，在这里加一个 Bean，在 application.yml 加地址。 */
@Configuration
@EnableConfigurationProperties(ServiceEndpoints.class)
public class ServiceClientConfig {

    @Bean
    SeatClient seatClient(RestClient.Builder builder, ServiceEndpoints endpoints) {
        return HttpClients.create(builder, SeatClient.class, endpoints.get("seat"));
    }
}
