package cn.edu.fudan.spacewise.platform.service;

import cn.edu.fudan.spacewise.platform.dao.BuildingEntity;
import cn.edu.fudan.spacewise.platform.dao.RoomEntity;
import cn.edu.fudan.spacewise.platform.dao.SpaceStatus;

/** 教室连同所属楼栋，供 api 组装楼栋名称和 available。 */
public record RoomView(RoomEntity room, BuildingEntity building) {

    /** 本身和楼栋都启用时才可用；调用方判断能否使用只看这一个值。 */
    public boolean available() {
        return room.getStatus() == SpaceStatus.ACTIVE && building.getStatus() == SpaceStatus.ACTIVE;
    }
}
