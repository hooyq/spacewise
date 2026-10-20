package cn.edu.fudan.spacewise.seat.api;

import cn.edu.fudan.spacewise.seat.api.dto.RoomResponse;
import cn.edu.fudan.spacewise.seat.service.RoomService;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    /** 参数校验示例：code 不合法时由 web-common 返回 400 + COMMON.VALIDATION_FAILED，并列出字段错误。 */
    @GetMapping("/{code}")
    public RoomResponse getRoom(
            @PathVariable @Pattern(regexp = "[A-Za-z0-9-]{1,32}", message = "只能包含字母、数字和连字符，最长 32 位") String code) {
        return RoomResponse.from(roomService.getRoom(code));
    }
}
