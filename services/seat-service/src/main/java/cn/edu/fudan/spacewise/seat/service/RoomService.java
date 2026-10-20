package cn.edu.fudan.spacewise.seat.service;

import cn.edu.fudan.spacewise.common.web.BusinessException;
import cn.edu.fudan.spacewise.seat.dao.RoomEntity;
import cn.edu.fudan.spacewise.seat.dao.RoomMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.springframework.stereotype.Service;

/** 分层示例：service 通过 dao 读取本服务的数据。 */
@Service
public class RoomService {

    private final RoomMapper roomMapper;

    public RoomService(RoomMapper roomMapper) {
        this.roomMapper = roomMapper;
    }

    public List<RoomEntity> listRooms() {
        return roomMapper.selectList(new LambdaQueryWrapper<RoomEntity>().orderByAsc(RoomEntity::getCode));
    }

    /** 错误处理示例：查不到时抛 BusinessException，由 web-common 转成 404 + SEAT.ROOM_NOT_FOUND。 */
    public RoomEntity getRoom(String code) {
        RoomEntity room = roomMapper.selectOne(new LambdaQueryWrapper<RoomEntity>().eq(RoomEntity::getCode, code));
        if (room == null) {
            throw new BusinessException(SeatErrorCode.ROOM_NOT_FOUND, "教室 " + code + " 不存在");
        }
        return room;
    }
}
