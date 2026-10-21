package cn.edu.fudan.spacewise.platform.service;

import cn.edu.fudan.spacewise.platform.dao.SeatEntity;
import cn.edu.fudan.spacewise.platform.dao.SpaceStatus;

/** 座位连同所属教室（及楼栋），供 api 组装教室编号和 available。 */
public record SeatView(SeatEntity seat, RoomView room) {

    /** 座位、教室、楼栋都启用时才可用。 */
    public boolean available() {
        return seat.getStatus() == SpaceStatus.ACTIVE && room.available();
    }
}
