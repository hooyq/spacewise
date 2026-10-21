package cn.edu.fudan.spacewise.platform.api;

import cn.edu.fudan.spacewise.platform.api.dto.BuildingResponse;
import cn.edu.fudan.spacewise.platform.api.dto.CreateBuildingRequest;
import cn.edu.fudan.spacewise.platform.api.dto.CreateRoomRequest;
import cn.edu.fudan.spacewise.platform.api.dto.CreateSeatsRequest;
import cn.edu.fudan.spacewise.platform.api.dto.RoomResponse;
import cn.edu.fudan.spacewise.platform.api.dto.SeatResponse;
import cn.edu.fudan.spacewise.platform.api.dto.UpdateBuildingRequest;
import cn.edu.fudan.spacewise.platform.api.dto.UpdateRoomRequest;
import cn.edu.fudan.spacewise.platform.api.dto.UpdateSeatRequest;
import cn.edu.fudan.spacewise.platform.service.SpaceAdminService;
import cn.edu.fudan.spacewise.platform.service.SpaceQueryService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 经网关对外的空间主数据接口（spec d-platform 1.5）：查询给登录用户，新建、修改给管理员。
 * 权限校验等 ADR-0008（认证）落地后再加；在那之前不要部署到外部可访问的环境。
 */
@RestController
@RequestMapping("/api/platform/spaces")
public class SpaceController {

    private final SpaceQueryService queryService;
    private final SpaceAdminService adminService;

    public SpaceController(SpaceQueryService queryService, SpaceAdminService adminService) {
        this.queryService = queryService;
        this.adminService = adminService;
    }

    @GetMapping("/buildings")
    public List<BuildingResponse> listBuildings() {
        return queryService.listBuildings().stream().map(BuildingResponse::from).toList();
    }

    @GetMapping("/rooms")
    public List<RoomResponse> listRooms(@RequestParam(required = false) Long buildingId) {
        return queryService.listRooms(buildingId).stream().map(RoomResponse::from).toList();
    }

    @GetMapping("/rooms/{id}/seats")
    public List<SeatResponse> listSeats(@PathVariable long id) {
        return queryService.listSeats(id).stream().map(SeatResponse::from).toList();
    }

    @PostMapping("/buildings")
    @ResponseStatus(HttpStatus.CREATED)
    public BuildingResponse createBuilding(@Valid @RequestBody CreateBuildingRequest request) {
        return BuildingResponse.from(adminService.createBuilding(request.code(), request.name()));
    }

    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse createRoom(@Valid @RequestBody CreateRoomRequest request) {
        return RoomResponse.from(adminService.createRoom(
                request.buildingId(), request.code(), request.name(), request.capacity()));
    }

    /** 返回本次新建的座位。 */
    @PostMapping("/rooms/{id}/seats")
    @ResponseStatus(HttpStatus.CREATED)
    public List<SeatResponse> createSeats(@PathVariable long id, @Valid @RequestBody CreateSeatsRequest request) {
        return adminService.createSeats(id, request.codes()).stream().map(SeatResponse::from).toList();
    }

    @PatchMapping("/buildings/{id}")
    public BuildingResponse updateBuilding(@PathVariable long id, @Valid @RequestBody UpdateBuildingRequest request) {
        return BuildingResponse.from(adminService.updateBuilding(id, request.name(), request.status()));
    }

    @PatchMapping("/rooms/{id}")
    public RoomResponse updateRoom(@PathVariable long id, @Valid @RequestBody UpdateRoomRequest request) {
        return RoomResponse.from(adminService.updateRoom(id, request.name(), request.capacity(), request.status()));
    }

    @PatchMapping("/seats/{id}")
    public SeatResponse updateSeat(@PathVariable long id, @Valid @RequestBody UpdateSeatRequest request) {
        return SeatResponse.from(adminService.updateSeat(id, request.status()));
    }
}
