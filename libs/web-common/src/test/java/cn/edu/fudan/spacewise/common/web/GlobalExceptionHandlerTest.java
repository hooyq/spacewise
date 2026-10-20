package cn.edu.fudan.spacewise.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** 像调用方一样通过 HTTP 访问会出错的接口，检查错误响应的状态码、code、traceId 和内容。 */
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability(metrics = false)
class GlobalExceptionHandlerTest {

    @Autowired
    private TestRestTemplate http;

    @Test
    void businessExceptionUsesItsErrorCodeAndStatus() {
        ResponseEntity<Map> response = http.getForEntity("/test/business", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .containsEntry("code", "TEST.THING_NOT_FOUND")
                .containsEntry("detail", "东西不存在")
                .containsEntry("status", 404)
                .containsEntry("instance", "/test/business");
    }

    @Test
    void everyErrorCarriesTraceId() {
        ResponseEntity<Map> response = http.getForEntity("/test/business", Map.class);

        assertThat(response.getBody().get("traceId")).asString().matches("[0-9a-f]{32}");
    }

    @Test
    void invalidRequestBodyListsFieldErrors() {
        ResponseEntity<Map> response = postJson("/test/body", "{\"name\":\"\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "COMMON.VALIDATION_FAILED");
        assertThat((List<Object>) response.getBody().get("errors"))
                .containsExactly(Map.of("field", "name", "message", "不能为空"));
    }

    @Test
    void invalidPathVariableListsParameterErrors() {
        ResponseEntity<Map> response = http.getForEntity("/test/param/abc", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "COMMON.VALIDATION_FAILED");
        assertThat((List<Object>) response.getBody().get("errors"))
                .containsExactly(Map.of("field", "code", "message", "只能是大写字母和数字"));
    }

    @Test
    void malformedJsonIsBadRequest() {
        ResponseEntity<Map> response = postJson("/test/body", "{not json");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "COMMON.BAD_REQUEST");
    }

    @Test
    void unknownPathIsNotFound() {
        ResponseEntity<Map> response = http.getForEntity("/test/nothing", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .containsEntry("code", "COMMON.NOT_FOUND")
                .containsEntry("detail", "请求的路径不存在");
    }

    @Test
    void wrongMethodIsMethodNotAllowed() {
        ResponseEntity<Map> response = http.exchange("/test/business", HttpMethod.DELETE, null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).containsEntry("code", "COMMON.METHOD_NOT_ALLOWED");
    }

    /** 未预料的异常：返回固定文案，不能把异常信息（这里故意放了 SQL 和密码）泄露给调用方。 */
    @Test
    void unexpectedExceptionHidesInternalDetails() {
        ResponseEntity<String> response = http.getForEntity("/test/boom", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody())
                .contains("\"code\":\"COMMON.INTERNAL_ERROR\"")
                .contains("服务器内部错误")
                .doesNotContain("secret_table")
                .doesNotContain("password")
                .doesNotContain("IllegalStateException");
    }

    private ResponseEntity<Map> postJson(String path, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity(path, new HttpEntity<>(json, headers), Map.class);
    }
}
