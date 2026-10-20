package cn.edu.fudan.spacewise.repair.config;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/** 根据 {@code @HttpExchange} 接口和下游地址创建服务间调用客户端。所有客户端都应通过这里创建，保证超时一致。 */
public final class HttpClients {

    private HttpClients() {
    }

    public static <T> T create(Class<T> clientType, ServiceEndpoints.Endpoint endpoint) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(endpoint.connectTimeout());
        requestFactory.setReadTimeout(endpoint.readTimeout());

        RestClient restClient = RestClient.builder()
                .baseUrl(endpoint.baseUrl())
                .requestFactory(requestFactory)
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(clientType);
    }
}
