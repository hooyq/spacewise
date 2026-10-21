package cn.edu.fudan.spacewise.platform.api.dto;

import cn.edu.fudan.spacewise.platform.dao.BuildingEntity;
import cn.edu.fudan.spacewise.platform.dao.SpaceStatus;

/** 楼栋信息。available：楼栋启用时为 true。 */
public record BuildingResponse(long id, String code, String name, SpaceStatus status, boolean available) {

    public static BuildingResponse from(BuildingEntity building) {
        return new BuildingResponse(building.getId(), building.getCode(), building.getName(), building.getStatus(),
                building.getStatus() == SpaceStatus.ACTIVE);
    }
}
