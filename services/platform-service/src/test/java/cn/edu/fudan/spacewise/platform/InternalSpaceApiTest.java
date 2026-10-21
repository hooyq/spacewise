package cn.edu.fudan.spacewise.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
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
 * 其他服务查询空间主数据的内部接口（REQ-D-002、REQ-D-003）。测试数据直接写库，不依赖管理员接口；
 * 测试后清空三张表（platform 目前没有其他数据）。
 */
@IntegrationTest
class InternalSpaceApiTest {

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
    void roomIncludesBuildingCodeAndName() {
        long roomId = insertRoom(insertBuilding("H3", "第三教学楼"), "H3209");

        ResponseEntity<Map> response = http.getForEntity("/internal/spaces/rooms/" + roomId, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("code", "H3209")
                .containsEntry("buildingCode", "H3")
                .containsEntry("buildingName", "第三教学楼")
                .containsEntry("available", true);
    }

    @Test
    void missingRoomIsNotFound() {
        ResponseEntity<Map> response = http.getForEntity("/internal/spaces/rooms/999999", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("code", "PLATFORM.ROOM_NOT_FOUND");
    }

    @Test
    void batchReturnsOnlyExistingRoomsInIdOrder() {
        long building = insertBuilding("H3", "第三教学楼");
        long first = insertRoom(building, "H3101");
        long second = insertRoom(building, "H3102");

        List<Map<String, Object>> rooms = getList("/internal/spaces/rooms?ids=" + second + ",999999," + first + "," + second);

        assertThat(rooms).extracting(room -> ((Number) room.get("id")).longValue()).containsExactly(first, second);
    }

    @Test
    void batchOverLimitIsRejected() {
        String ids = LongStream.rangeClosed(1, 101).mapToObj(Long::toString).collect(Collectors.joining(","));

        ResponseEntity<Map> response = http.getForEntity("/internal/spaces/seats?ids=" + ids, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "COMMON.VALIDATION_FAILED");
    }

    @Test
    void batchWithoutIdsIsRejected() {
        ResponseEntity<Map> response = http.getForEntity("/internal/spaces/rooms", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void disabledBuildingMakesSeatUnavailableUntilReenabled() {
        long building = insertBuilding("H3", "第三教学楼");
        long seat = insertSeat(insertRoom(building, "H3209"), "A-1");

        jdbc.update("UPDATE building SET status = 'DISABLED' WHERE id = ?", building);
        Map<String, Object> disabled = http.getForEntity("/internal/spaces/seats/" + seat, Map.class).getBody();

        assertThat(disabled).containsEntry("status", "ACTIVE").containsEntry("available", false);

        jdbc.update("UPDATE building SET status = 'ACTIVE' WHERE id = ?", building);
        Map<String, Object> reenabled = http.getForEntity("/internal/spaces/seats/" + seat, Map.class).getBody();

        assertThat(reenabled).containsEntry("available", true);
    }

    @Test
    void roomSeatsAreOrderedByCode() {
        long room = insertRoom(insertBuilding("H3", "第三教学楼"), "H3209");
        insertSeat(room, "B-1");
        insertSeat(room, "A-2");
        insertSeat(room, "A-1");

        List<Map<String, Object>> seats = getList("/internal/spaces/rooms/" + room + "/seats");

        assertThat(seats).extracting(seat -> seat.get("code")).containsExactly("A-1", "A-2", "B-1");
        assertThat(seats).allSatisfy(seat -> assertThat(seat).containsEntry("roomCode", "H3209"));
    }

    @Test
    void seatsOfMissingRoomIsNotFound() {
        ResponseEntity<Map> response = http.getForEntity("/internal/spaces/rooms/999999/seats", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("code", "PLATFORM.ROOM_NOT_FOUND");
    }

    private List<Map<String, Object>> getList(String path) {
        ResponseEntity<List<Map<String, Object>>> response =
                http.exchange(path, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private long insertBuilding(String code, String name) {
        jdbc.update("INSERT INTO building (code, name) VALUES (?, ?)", code, name);
        return jdbc.queryForObject("SELECT id FROM building WHERE code = ?", Long.class, code);
    }

    private long insertRoom(long buildingId, String code) {
        jdbc.update("INSERT INTO room (building_id, code, capacity) VALUES (?, ?, 30)", buildingId, code);
        return jdbc.queryForObject("SELECT id FROM room WHERE code = ?", Long.class, code);
    }

    private long insertSeat(long roomId, String code) {
        jdbc.update("INSERT INTO seat (room_id, code) VALUES (?, ?)", roomId, code);
        return jdbc.queryForObject("SELECT id FROM seat WHERE room_id = ? AND code = ?", Long.class, roomId, code);
    }
}
