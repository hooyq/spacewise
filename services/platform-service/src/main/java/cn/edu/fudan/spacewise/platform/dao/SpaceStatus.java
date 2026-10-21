package cn.edu.fudan.spacewise.platform.dao;

/** 楼栋、教室、座位共用的状态，按名称存进 status 列。只停用不删除（ADR-0007）。 */
public enum SpaceStatus {
    ACTIVE,
    DISABLED
}
