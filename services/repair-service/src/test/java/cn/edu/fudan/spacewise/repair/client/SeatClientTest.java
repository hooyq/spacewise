package cn.edu.fudan.spacewise.repair.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.edu.fudan.spacewise.repair.config.HttpClients;
import cn.edu.fudan.spacewise.repair.config.ServiceEndpoints;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 用 JDK 自带的 HttpServer 扮演 seat-service，发出真实的 HTTP 请求，验证路径、响应解析、错误和超时处理。
 * 不启动 Spring 上下文，因此不会额外启动 MySQL 容器。
 */
class SeatClientTest {

    private static final Duration READ_TIMEOUT = Duration.ofMillis(200);

    private HttpServer fakeSeat;
    private volatile int status;
    private volatile long delayMillis;

    @BeforeEach
    void startFakeSeat() throws IOException {
        status = 200;
        delayMillis = 0;
        fakeSeat = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeSeat.createContext("/internal/ping", this::handlePing);
        fakeSeat.start();
    }

    @AfterEach
    void stopFakeSeat() {
        fakeSeat.stop(0);
    }

    private void handlePing(HttpExchange exchange) throws IOException {
        // createContext 按前缀匹配（/internal/pingx 也会进来），这里要求路径完全一致，否则 404
        if (!exchange.getRequestURI().getPath().equals("/internal/ping")) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        byte[] body = "{\"service\":\"seat-service\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private SeatClient client() {
        String baseUrl = "http://127.0.0.1:" + fakeSeat.getAddress().getPort();
        return HttpClients.create(RestClient.builder(), SeatClient.class,
                new ServiceEndpoints.Endpoint(baseUrl, Duration.ofSeconds(2), READ_TIMEOUT));
    }

    @Test
    void pingParsesResponse() {
        assertThat(client().ping()).isEqualTo(new SeatClient.SeatPing("seat-service"));
    }

    @Test
    void serverErrorIsThrown() {
        status = 503;

        assertThatThrownBy(() -> client().ping()).isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void slowResponseTimesOut() {
        delayMillis = READ_TIMEOUT.toMillis() * 5;

        assertThatThrownBy(() -> client().ping()).isInstanceOf(ResourceAccessException.class);
    }
}
