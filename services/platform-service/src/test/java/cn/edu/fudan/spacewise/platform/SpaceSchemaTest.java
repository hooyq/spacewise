package cn.edu.fudan.spacewise.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 验证迁移后的空间表约束。每个测试在事务中执行并回滚，互不影响。 */
@IntegrationTest
@Transactional
class SpaceSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void duplicateBuildingCodeIsRejected() {
        jdbc.update("INSERT INTO building (code, name) VALUES ('H3', '第三教学楼')");

        assertThatThrownBy(() -> jdbc.update("INSERT INTO building (code, name) VALUES ('H3', '另一栋')"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void duplicateRoomCodeIsRejectedEvenInAnotherBuilding() {
        long h3 = insertBuilding("H3");
        long h4 = insertBuilding("H4");
        insertRoom(h3, "H3209");

        assertThatThrownBy(() -> insertRoom(h4, "H3209")).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void seatCodeIsUniqueWithinRoomOnly() {
        long building = insertBuilding("H3");
        long room1 = insertRoom(building, "H3101");
        long room2 = insertRoom(building, "H3102");
        insertSeat(room1, "A-1");

        insertSeat(room2, "A-1");
        assertThatThrownBy(() -> insertSeat(room1, "A-1")).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void roomMustReferenceExistingBuilding() {
        assertThatThrownBy(() -> insertRoom(999_999, "H9999")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void newSpaceIsActiveByDefault() {
        long building = insertBuilding("H3");

        String status = jdbc.queryForObject("SELECT status FROM building WHERE id = ?", String.class, building);

        assertThat(status).isEqualTo("ACTIVE");
    }

    private long insertBuilding(String code) {
        jdbc.update("INSERT INTO building (code, name) VALUES (?, ?)", code, code + " 楼");
        return jdbc.queryForObject("SELECT id FROM building WHERE code = ?", Long.class, code);
    }

    private long insertRoom(long buildingId, String code) {
        jdbc.update("INSERT INTO room (building_id, code, capacity) VALUES (?, ?, 30)", buildingId, code);
        return jdbc.queryForObject("SELECT id FROM room WHERE code = ?", Long.class, code);
    }

    private void insertSeat(long roomId, String code) {
        jdbc.update("INSERT INTO seat (room_id, code) VALUES (?, ?)", roomId, code);
    }
}
