package cn.edu.fudan.spacewise.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 管理员维护空间主数据（REQ-D-001）。HTTP 请求在另一个线程里处理，测试事务无法回滚，
 * 所以提交数据并在测试后清空三张表（platform 目前没有其他数据）。
 */
@IntegrationTest
class SpaceAdminApiTest {

    private static final String BASE = "/api/platform/spaces";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM seat");
        jdbc.update("DELETE FROM room");
        jdbc.update("DELETE FROM building");
    }

    @Test
    void createsActiveBuildingAndRejectsDuplicateCode() {
        ResponseEntity<Map> created = post("/buildings", Map.of("code", "H3", "name", "第三教学楼"));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody())
                .containsEntry("code", "H3")
                .containsEntry("name", "第三教学楼")
                .containsEntry("status", "ACTIVE")
                .containsEntry("available", true);

        ResponseEntity<Map> duplicated = post("/buildings", Map.of("code", "H3", "name", "另一栋"));

        assertThat(duplicated.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicated.getBody()).containsEntry("code", "PLATFORM.SPACE_CODE_DUPLICATED");
    }

    @Test
    void createsRoomUnderExistingBuildingWithBuildingInfo() {
        long buildingId = createBuilding("H3");

        ResponseEntity<Map> created = post("/rooms", Map.of("buildingId", buildingId, "code", "H3209", "capacity", 60));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody())
                .containsEntry("buildingCode", "H3")
                .containsEntry("code", "H3209")
                .containsEntry("capacity", 60)
                .containsEntry("available", true);
    }

    @Test
    void roomInMissingBuildingIsNotFound() {
        ResponseEntity<Map> response = post("/rooms", Map.of("buildingId", 999_999, "code", "H3209", "capacity", 60));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("code", "PLATFORM.BUILDING_NOT_FOUND");
    }

    @Test
    void nonPositiveCapacityFailsValidation() {
        long buildingId = createBuilding("H3");

        ResponseEntity<Map> response = post("/rooms", Map.of("buildingId", buildingId, "code", "H3209", "capacity", 0));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "COMMON.VALIDATION_FAILED");
    }

    @Test
    void createsSeatsInBatch() {
        long roomId = createRoom(createBuilding("H3"), "H3209");

        ResponseEntity<List<Map<String, Object>>> response = http.exchange(BASE + "/rooms/" + roomId + "/seats",
                HttpMethod.POST, new HttpEntity<>(Map.of("codes", List.of("A-1", "A-2"))),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).extracting(seat -> seat.get("code")).containsExactly("A-1", "A-2");
        assertThat(response.getBody()).allSatisfy(seat -> assertThat(seat)
                .containsEntry("roomCode", "H3209")
                .containsEntry("available", true));
    }

    @Test
    void duplicateSeatCodeInBatchCreatesNoSeat() {
        long roomId = createRoom(createBuilding("H3"), "H3209");

        ResponseEntity<Map> response = post("/rooms/" + roomId + "/seats", Map.of("codes", List.of("A-1", "A-2", "A-1")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "PLATFORM.SPACE_CODE_DUPLICATED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM seat WHERE room_id = ?", Integer.class, roomId)).isZero();
    }

    @Test
    void patchChangesOnlyGivenFields() {
        long roomId = createRoom(createBuilding("H3"), "H3209");

        ResponseEntity<Map> response = patch("/rooms/" + roomId, Map.of("name", "多媒体教室"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("name", "多媒体教室")
                .containsEntry("capacity", 60)
                .containsEntry("status", "ACTIVE");
    }

    @Test
    void disablingRoomMakesItUnavailable() {
        long roomId = createRoom(createBuilding("H3"), "H3209");

        ResponseEntity<Map> response = patch("/rooms/" + roomId, Map.of("status", "DISABLED"));

        assertThat(response.getBody())
                .containsEntry("status", "DISABLED")
                .containsEntry("available", false);
    }

    @Test
    void patchMissingRoomIsNotFound() {
        ResponseEntity<Map> response = patch("/rooms/999999", Map.of("name", "x"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("code", "PLATFORM.ROOM_NOT_FOUND");
    }

    @Test
    void unknownStatusValueIsBadRequest() {
        long buildingId = createBuilding("H3");

        ResponseEntity<Map> response = patch("/buildings/" + buildingId, Map.of("status", "DELETED"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private long createBuilding(String code) {
        return id(post("/buildings", Map.of("code", code, "name", code + " 楼")));
    }

    private long createRoom(long buildingId, String code) {
        return id(post("/rooms", Map.of("buildingId", buildingId, "code", code, "capacity", 60)));
    }

    private static long id(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private ResponseEntity<Map> post(String path, Object body) {
        return http.postForEntity(BASE + path, body, Map.class);
    }

    private ResponseEntity<Map> patch(String path, Object body) {
        return http.exchange(BASE + path, HttpMethod.PATCH, new HttpEntity<>(body), Map.class);
    }
}
