package cn.edu.fudan.spacewise.platform.api.dto;

import cn.edu.fudan.spacewise.platform.dao.SpaceStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 为 null 的字段不修改。编号不可修改。 */
public record UpdateBuildingRequest(
        @Size(max = 64) @Pattern(regexp = ".*\\S.*", message = "不能为空白") String name,
        SpaceStatus status) {
}
