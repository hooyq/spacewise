package cn.edu.fudan.spacewise.seat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 验证迁移后的 room 表结构行为。每个测试在事务中执行并回滚，互不影响。
 */
@IntegrationTest
@Transactional
class RoomSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void insertedRoomCanBeReadBack() {
        jdbc.update("INSERT INTO room (code, building) VALUES (?, ?)", "H3209", "光华楼");

        String building = jdbc.queryForObject("SELECT building FROM room WHERE code = ?", String.class, "H3209");

        assertThat(building).isEqualTo("光华楼");
    }

    @Test
    void duplicateRoomCodeIsRejected() {
        jdbc.update("INSERT INTO room (code, building) VALUES (?, ?)", "H3209", "光华楼");

        assertThatThrownBy(() -> jdbc.update("INSERT INTO room (code, building) VALUES (?, ?)", "H3209", "第三教学楼"))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
