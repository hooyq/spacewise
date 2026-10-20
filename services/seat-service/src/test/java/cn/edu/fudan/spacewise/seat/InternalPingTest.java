package cn.edu.fudan.spacewise.seat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** 其他服务（如 repair-service 的 SeatClient）依赖这个接口的路径和响应格式。 */
@IntegrationTest
class InternalPingTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void pingReturnsServiceName() {
        ResponseEntity<Map> response = http.getForEntity("/internal/ping", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(Map.of("service", "seat-service"));
    }
}
