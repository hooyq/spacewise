package cn.edu.fudan.spacewise.platform.api.dto;

import cn.edu.fudan.spacewise.platform.dao.SpaceStatus;

/** 为 null 的字段不修改。编号、所属教室不可修改。 */
public record UpdateSeatRequest(SpaceStatus status) {
}
