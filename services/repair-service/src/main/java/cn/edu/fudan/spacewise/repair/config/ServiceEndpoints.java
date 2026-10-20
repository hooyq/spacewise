package cn.edu.fudan.spacewise.repair.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 下游服务的地址和超时，对应配置 spacewise.services.&lt;名称&gt;.*（见 application.yml）。
 */
@ConfigurationProperties("spacewise")
public record ServiceEndpoints(Map<String, Endpoint> services) {

    public record Endpoint(String baseUrl, Duration connectTimeout, Duration readTimeout) {
    }

    /** 缺少配置时启动即失败，而不是等到第一次调用才发现。 */
    public Endpoint get(String name) {
        Endpoint endpoint = services == null ? null : services.get(name);
        if (endpoint == null || endpoint.baseUrl() == null
                || endpoint.connectTimeout() == null || endpoint.readTimeout() == null) {
            throw new IllegalStateException(
                    "缺少配置 spacewise.services." + name + ".base-url / connect-timeout / read-timeout");
        }
        return endpoint;
    }
}
