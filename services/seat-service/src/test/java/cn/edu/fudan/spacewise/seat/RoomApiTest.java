package cn.edu.fudan.spacewise.seat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 分层示例的端到端测试：HTTP 请求经 api → service → dao 读到数据库中的数据。
 * HTTP 请求在另一个线程里处理，测试事务无法回滚它能看到的数据，所以这里提交数据并在测试后清理。
 */
@IntegrationTest
class RoomApiTest {

    private static final String CODE_PREFIX = "API-TEST-";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM room WHERE code LIKE ?", CODE_PREFIX + "%");
    }

    @Test
    void listsRoomsOrderedByCodeWithOnlyPublicFields() {
        jdbc.update("INSERT INTO room (code, building) VALUES (?, ?), (?, ?)",
                CODE_PREFIX + "B", "第三教学楼", CODE_PREFIX + "A", "光华楼");

        ResponseEntity<List<Map<String, Object>>> response = http.exchange(
                "/api/seat/rooms", HttpMethod.GET, null, new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> rooms = response.getBody().stream()
                .filter(room -> room.get("code").toString().startsWith(CODE_PREFIX))
                .toList();
        assertThat(rooms).containsExactly(
                Map.of("code", CODE_PREFIX + "A", "building", "光华楼"),
                Map.of("code", CODE_PREFIX + "B", "building", "第三教学楼"));
    }

    @Test
    void getsSingleRoomByCode() {
        jdbc.update("INSERT INTO room (code, building) VALUES (?, ?)", CODE_PREFIX + "C", "光华楼");

        ResponseEntity<Map> response = http.getForEntity("/api/seat/rooms/" + CODE_PREFIX + "C", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(Map.of("code", CODE_PREFIX + "C", "building", "光华楼"));
    }

    @Test
    void missingRoomIsNotFoundWithBusinessCode() {
        ResponseEntity<Map> response = http.getForEntity("/api/seat/rooms/" + CODE_PREFIX + "NONE", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .containsEntry("code", "SEAT.ROOM_NOT_FOUND")
                .containsEntry("detail", "教室 " + CODE_PREFIX + "NONE 不存在");
    }

    @Test
    void invalidRoomCodeFailsValidation() {
        ResponseEntity<Map> response = http.getForEntity("/api/seat/rooms/bad_code!", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "COMMON.VALIDATION_FAILED");
    }
}
