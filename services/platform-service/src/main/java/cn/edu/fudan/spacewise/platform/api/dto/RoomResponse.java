package cn.edu.fudan.spacewise.platform.api.dto;

import cn.edu.fudan.spacewise.platform.dao.BuildingEntity;
import cn.edu.fudan.spacewise.platform.dao.RoomEntity;
import cn.edu.fudan.spacewise.platform.dao.SpaceStatus;
import cn.edu.fudan.spacewise.platform.service.RoomView;

/** 教室信息，对外接口和内部接口共用；字段只加不删（其他服务依赖它）。available：教室和楼栋都启用时为 true。 */
public record RoomResponse(long id, long buildingId, String buildingCode, String buildingName,
        String code, String name, int capacity, SpaceStatus status, boolean available) {

    public static RoomResponse from(RoomView view) {
        RoomEntity room = view.room();
        BuildingEntity building = view.building();
        return new RoomResponse(room.getId(), building.getId(), building.getCode(), building.getName(),
                room.getCode(), room.getName(), room.getCapacity(), room.getStatus(), view.available());
    }
}
