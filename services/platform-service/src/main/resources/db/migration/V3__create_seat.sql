-- 座位（空间主数据，ADR-0007）。编号在教室内唯一；座位能否预约等业务状态由 M1 记录，不在这里。
CREATE TABLE seat (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    room_id    BIGINT       NOT NULL COMMENT '所属教室',
    code       VARCHAR(16)  NOT NULL COMMENT '室内编号，如 A-12',
    status     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_seat_room_code UNIQUE (room_id, code),
    CONSTRAINT fk_seat_room FOREIGN KEY (room_id) REFERENCES room (id) ON DELETE RESTRICT
) COMMENT = '座位';
