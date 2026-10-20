package cn.edu.fudan.spacewise.seat.api.dto;

import cn.edu.fudan.spacewise.seat.dao.RoomEntity;

/** 接口返回的自习室信息。只暴露调用方需要的字段，数据库主键、创建时间等不对外。 */
public record RoomResponse(String code, String building) {

    public static RoomResponse from(RoomEntity room) {
        return new RoomResponse(room.getCode(), room.getBuilding());
    }
}
