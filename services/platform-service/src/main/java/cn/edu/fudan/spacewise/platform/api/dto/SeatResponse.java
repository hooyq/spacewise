package cn.edu.fudan.spacewise.platform.api.dto;

import cn.edu.fudan.spacewise.platform.dao.SeatEntity;
import cn.edu.fudan.spacewise.platform.dao.SpaceStatus;
import cn.edu.fudan.spacewise.platform.service.SeatView;

/** 座位信息，对外接口和内部接口共用；字段只加不删。available：座位、教室、楼栋都启用时为 true。 */
public record SeatResponse(long id, long roomId, String roomCode, String code, SpaceStatus status, boolean available) {

    public static SeatResponse from(SeatView view) {
        SeatEntity seat = view.seat();
        return new SeatResponse(seat.getId(), seat.getRoomId(), view.room().room().getCode(), seat.getCode(),
                seat.getStatus(), view.available());
    }
}
