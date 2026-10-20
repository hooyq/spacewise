package cn.edu.fudan.spacewise.repair;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 像外部调用方一样通过 HTTP 访问健康检查。
 * Docker healthcheck 和 Compose depends_on 都依赖这个端点。
 */
@IntegrationTest
class HealthEndpointTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void healthEndpointReportsUp() {
        ResponseEntity<Map> response = http.getForEntity("/actuator/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }
}
