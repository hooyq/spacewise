package cn.edu.fudan.spacewise.platform.service;

import cn.edu.fudan.spacewise.common.web.BusinessException;
import cn.edu.fudan.spacewise.platform.dao.BuildingEntity;
import cn.edu.fudan.spacewise.platform.dao.BuildingMapper;
import cn.edu.fudan.spacewise.platform.dao.RoomEntity;
import cn.edu.fudan.spacewise.platform.dao.RoomMapper;
import cn.edu.fudan.spacewise.platform.dao.SeatEntity;
import cn.edu.fudan.spacewise.platform.dao.SeatMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 空间主数据的只读查询，供对外接口和其他服务的内部接口共用。
 * 上级（楼栋、教室）用一次批量查询取出，不逐条查询。
 */
@Service
public class SpaceQueryService {

    private final BuildingMapper buildingMapper;
    private final RoomMapper roomMapper;
    private final SeatMapper seatMapper;

    public SpaceQueryService(BuildingMapper buildingMapper, RoomMapper roomMapper, SeatMapper seatMapper) {
        this.buildingMapper = buildingMapper;
        this.roomMapper = roomMapper;
        this.seatMapper = seatMapper;
    }

    public List<BuildingEntity> listBuildings() {
        return buildingMapper.selectList(new LambdaQueryWrapper<BuildingEntity>().orderByAsc(BuildingEntity::getCode));
    }

    public BuildingEntity getBuilding(long id) {
        BuildingEntity building = buildingMapper.selectById(id);
        if (building == null) {
            throw new BusinessException(PlatformErrorCode.BUILDING_NOT_FOUND, "楼栋 " + id + " 不存在");
        }
        return building;
    }

    /** buildingId 为 null 时返回全部教室。 */
    public List<RoomView> listRooms(Long buildingId) {
        List<RoomEntity> rooms = roomMapper.selectList(new LambdaQueryWrapper<RoomEntity>()
                .eq(buildingId != null, RoomEntity::getBuildingId, buildingId)
                .orderByAsc(RoomEntity::getCode));
        return toRoomViews(rooms);
    }

    public RoomView getRoom(long id) {
        RoomEntity room = roomMapper.selectById(id);
        if (room == null) {
            throw new BusinessException(PlatformErrorCode.ROOM_NOT_FOUND, "教室 " + id + " 不存在");
        }
        return toRoomViews(List.of(room)).getFirst();
    }

    /** 只返回存在的教室，按 ID 升序；不存在的 ID 不报错，由调用方自行比对。 */
    public List<RoomView> findRooms(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<RoomEntity> rooms = roomMapper.selectByIds(Set.copyOf(ids)).stream()
                .sorted(Comparator.comparing(RoomEntity::getId))
                .toList();
        return toRoomViews(rooms);
    }

    /** 某教室的全部座位，按编号升序；教室不存在时报错。 */
    public List<SeatView> listSeats(long roomId) {
        RoomView room = getRoom(roomId);
        return seatMapper.selectList(new LambdaQueryWrapper<SeatEntity>()
                        .eq(SeatEntity::getRoomId, roomId)
                        .orderByAsc(SeatEntity::getCode))
                .stream()
                .map(seat -> new SeatView(seat, room))
                .toList();
    }

    public SeatView getSeat(long id) {
        SeatEntity seat = seatMapper.selectById(id);
        if (seat == null) {
            throw new BusinessException(PlatformErrorCode.SEAT_NOT_FOUND, "座位 " + id + " 不存在");
        }
        return toSeatViews(List.of(seat)).getFirst();
    }

    /** 只返回存在的座位，按 ID 升序；规则同 {@link #findRooms}。 */
    public List<SeatView> findSeats(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<SeatEntity> seats = seatMapper.selectByIds(Set.copyOf(ids)).stream()
                .sorted(Comparator.comparing(SeatEntity::getId))
                .toList();
        return toSeatViews(seats);
    }

    private List<RoomView> toRoomViews(List<RoomEntity> rooms) {
        if (rooms.isEmpty()) {
            return List.of();
        }
        Map<Long, BuildingEntity> buildings = byId(
                buildingMapper.selectByIds(rooms.stream().map(RoomEntity::getBuildingId).collect(Collectors.toSet())),
                BuildingEntity::getId);
        return rooms.stream().map(room -> new RoomView(room, buildings.get(room.getBuildingId()))).toList();
    }

    private List<SeatView> toSeatViews(List<SeatEntity> seats) {
        if (seats.isEmpty()) {
            return List.of();
        }
        Map<Long, RoomView> rooms = byId(
                findRooms(seats.stream().map(SeatEntity::getRoomId).collect(Collectors.toSet())),
                view -> view.room().getId());
        return seats.stream().map(seat -> new SeatView(seat, rooms.get(seat.getRoomId()))).toList();
    }

    private static <T> Map<Long, T> byId(Collection<T> items, Function<T, Long> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
