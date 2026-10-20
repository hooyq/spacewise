package cn.edu.fudan.spacewise.seat.api;

import cn.edu.fudan.spacewise.seat.api.dto.RoomResponse;
import cn.edu.fudan.spacewise.seat.service.RoomService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 分层示例：api → service → dao，实体在这里转换成 dto 后返回。 */
@RestController
@RequestMapping("/api/seat/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public List<RoomResponse> listRooms() {
        return roomService.listRooms().stream().map(RoomResponse::from).toList();
    }
}
