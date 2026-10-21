package cn.edu.fudan.spacewise.platform.api;

import cn.edu.fudan.spacewise.platform.api.dto.RoomResponse;
import cn.edu.fudan.spacewise.platform.api.dto.SeatResponse;
import cn.edu.fudan.spacewise.platform.service.SpaceQueryService;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供 M1/M2/M3 查询空间主数据（spec d-platform 1.4）。网关不转发 /internal/**，只在服务之间调用。
 * 这些接口是跨模块契约，修改须经调用方模块组评审。
 */
@RestController
@RequestMapping("/internal/spaces")
public class InternalSpaceController {

    private final SpaceQueryService queryService;

    public InternalSpaceController(SpaceQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/rooms/{id}")
    public RoomResponse getRoom(@PathVariable long id) {
        return RoomResponse.from(queryService.getRoom(id));
    }

    /** 批量查询：只返回存在的教室，按 ID 升序。 */
    @GetMapping("/rooms")
    public List<RoomResponse> findRooms(
            @RequestParam @NotEmpty @Size(max = 100, message = "一次最多查询 100 个") List<Long> ids) {
        return queryService.findRooms(ids).stream().map(RoomResponse::from).toList();
    }

    @GetMapping("/rooms/{id}/seats")
    public List<SeatResponse> listSeats(@PathVariable long id) {
        return queryService.listSeats(id).stream().map(SeatResponse::from).toList();
    }

    @GetMapping("/seats/{id}")
    public SeatResponse getSeat(@PathVariable long id) {
        return SeatResponse.from(queryService.getSeat(id));
    }

    /** 批量查询：只返回存在的座位，按 ID 升序。 */
    @GetMapping("/seats")
    public List<SeatResponse> findSeats(
            @RequestParam @NotEmpty @Size(max = 100, message = "一次最多查询 100 个") List<Long> ids) {
        return queryService.findSeats(ids).stream().map(SeatResponse::from).toList();
    }
}
