package cn.edu.fudan.spacewise.repair.client;

import org.springframework.web.service.annotation.GetExchange;

/**
 * repair-service 需要调用的 seat-service 接口。只声明本服务用到的接口；实例由 config.ServiceClientConfig 创建。
 * 对方返回 4xx/5xx 时抛出 RestClientResponseException，连接失败或超时抛出 ResourceAccessException。
 */
public interface SeatClient {

    @GetExchange("/internal/ping")
    SeatPing ping();

    /** seat-service /internal/ping 的响应，只声明本服务关心的字段。 */
    record SeatPing(String service) {
    }
}
