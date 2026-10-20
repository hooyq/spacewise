package cn.edu.fudan.spacewise.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 下游服务不可用时，网关要快速失败并用状态码说明原因：连不上 502，超时 504。
 * 读取超时在测试中调小到 500ms，以证明超时配置确实作用在网关的 HTTP 客户端上。
 * 测试端用 JDK HttpClient 发请求：spring.http.client.* 也会作用于 TestRestTemplate，会让测试客户端自己先超时。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DownstreamFailureTest {

    private static final Duration READ_TIMEOUT = Duration.ofMillis(500);
    private static final HttpServer slowSeat = startSlowSeat();
    private static final int closedPort = findClosedPort();

    private static HttpServer startSlowSeat() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                try {
                    Thread.sleep(READ_TIMEOUT.toMillis() * 6);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 拿到一个空闲端口后立即关闭，之后连接它会被拒绝，模拟“服务没在运行”。 */
    private static int findClosedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void pointRoutesToBrokenDownstreams(DynamicPropertyRegistry registry) {
        registry.add("SEAT_SERVICE_URL", () -> "http://127.0.0.1:" + slowSeat.getAddress().getPort());
        registry.add("ROOM_SERVICE_URL", () -> "http://127.0.0.1:" + closedPort);
        registry.add("spring.http.client.read-timeout", READ_TIMEOUT::toString);
    }

    @AfterAll
    static void stopSlowSeat() {
        slowSeat.stop(0);
    }

    @LocalServerPort
    private int port;

    private int getStatus(String path) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    void slowDownstreamTimesOutWith504() throws Exception {
        long start = System.nanoTime();
        int status = getStatus("/api/seat/rooms");
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(status).isEqualTo(504);
        assertThat(elapsed).isLessThan(READ_TIMEOUT.multipliedBy(4));
    }

    @Test
    void unreachableDownstreamIs502() throws Exception {
        assertThat(getStatus("/api/room/anything")).isEqualTo(502);
    }
}
