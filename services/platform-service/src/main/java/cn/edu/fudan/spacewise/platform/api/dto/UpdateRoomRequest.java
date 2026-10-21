package cn.edu.fudan.spacewise.platform.api.dto;

import cn.edu.fudan.spacewise.platform.dao.SpaceStatus;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 为 null 的字段不修改。编号、所属楼栋不可修改。 */
public record UpdateRoomRequest(@Size(max = 64) String name, @Positive Integer capacity, SpaceStatus status) {
}
