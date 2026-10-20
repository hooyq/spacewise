package cn.edu.fudan.spacewise.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 用 JDK HttpServer 扮演 seat-service，所有路径都返回 200 并记录收到的请求，
 * 以此验证网关“转发了什么、没转发什么”。网关不连数据库，不需要 MySQL 容器。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoutingTest {

    private static final List<String> receivedBySeat = new CopyOnWriteArrayList<>();
    private static final HttpServer fakeSeat = startFakeSeat();

    private static HttpServer startFakeSeat() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                receivedBySeat.add(exchange.getRequestURI().toString());
                byte[] body = "{\"from\":\"fake-seat\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void pointSeatRouteToFake(DynamicPropertyRegistry registry) {
        registry.add("SEAT_SERVICE_URL", () -> "http://127.0.0.1:" + fakeSeat.getAddress().getPort());
    }

    @AfterAll
    static void stopFakeSeat() {
        fakeSeat.stop(0);
    }

    @Autowired
    private TestRestTemplate http;

    @BeforeEach
    void clearReceived() {
        receivedBySeat.clear();
    }

    @Test
    void forwardsModulePrefixWithPathAndQueryUnchanged() {
        ResponseEntity<Map> response = http.getForEntity("/api/seat/rooms?x=1", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(Map.of("from", "fake-seat"));
        assertThat(receivedBySeat).containsExactly("/api/seat/rooms?x=1");
    }

    @Test
    void internalPathsAreNotReachable() {
        ResponseEntity<String> response = http.getForEntity("/internal/ping", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(receivedBySeat).isEmpty();
    }

    @Test
    void unknownModuleIsNotFound() {
        ResponseEntity<String> response = http.getForEntity("/api/unknown/x", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(receivedBySeat).isEmpty();
    }

    @Test
    void gatewayHealthIsUp() {
        ResponseEntity<Map> response = http.getForEntity("/actuator/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }
}
