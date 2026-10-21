-- 楼栋（空间主数据，ADR-0007）。只停用不物理删除，其他服务保存了这里的 ID。
CREATE TABLE building (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    code       VARCHAR(32)  NOT NULL COMMENT '楼栋编号，如 H3',
    name       VARCHAR(64)  NOT NULL COMMENT '名称，如 第三教学楼',
    status     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT uk_building_code UNIQUE (code)
) COMMENT = '楼栋';
