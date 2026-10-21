-- 教室（空间主数据，ADR-0007）。编号全局唯一；只停用不物理删除。
CREATE TABLE room (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    building_id BIGINT       NOT NULL COMMENT '所属楼栋',
    code        VARCHAR(32)  NOT NULL COMMENT '教室编号，如 H3209',
    name        VARCHAR(64)  NULL     COMMENT '可选显示名',
    capacity    INT          NOT NULL COMMENT '容纳人数',
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_room_code UNIQUE (code),
    CONSTRAINT fk_room_building FOREIGN KEY (building_id) REFERENCES building (id) ON DELETE RESTRICT
) COMMENT = '教室';
