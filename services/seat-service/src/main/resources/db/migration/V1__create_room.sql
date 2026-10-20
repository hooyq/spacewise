-- 自习室（空间主数据）。字段按最小需要建立，后续用新的迁移版本扩展，不修改本文件。
CREATE TABLE room (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    code       VARCHAR(32)  NOT NULL COMMENT '教室编号，如 H3209',
    building   VARCHAR(64)  NOT NULL COMMENT '所在楼栋',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_room_code UNIQUE (code)
) COMMENT = '自习室';
