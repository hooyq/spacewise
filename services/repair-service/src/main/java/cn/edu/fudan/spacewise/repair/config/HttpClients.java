package cn.edu.fudan.spacewise.repair.config;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/** 根据 {@code @HttpExchange} 接口和下游地址创建服务间调用客户端。所有客户端都应通过这里创建，保证超时一致。 */
public final class HttpClients {

    private HttpClients() {
    }

    /**
     * @param builder 应传入 Spring 自动配置的 {@code RestClient.Builder}：它带有追踪配置，会把 traceId 传给下游。
     *                自己 {@code RestClient.builder()} 新建的不会传，下游日志就和本次请求串不起来。
     */
    public static <T> T create(RestClient.Builder builder, Class<T> clientType, ServiceEndpoints.Endpoint endpoint) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(endpoint.connectTimeout());
        requestFactory.setReadTimeout(endpoint.readTimeout());

        // clone：Builder 是共享的 Bean，直接修改会影响其他客户端
        RestClient restClient = builder.clone()
                .baseUrl(endpoint.baseUrl())
                .requestFactory(requestFactory)
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(clientType);
    }
}
