package cn.edu.fudan.spacewise.platform.service;

import cn.edu.fudan.spacewise.common.web.BusinessException;
import cn.edu.fudan.spacewise.platform.dao.BuildingEntity;
import cn.edu.fudan.spacewise.platform.dao.BuildingMapper;
import cn.edu.fudan.spacewise.platform.dao.RoomEntity;
import cn.edu.fudan.spacewise.platform.dao.RoomMapper;
import cn.edu.fudan.spacewise.platform.dao.SeatEntity;
import cn.edu.fudan.spacewise.platform.dao.SeatMapper;
import cn.edu.fudan.spacewise.platform.dao.SpaceStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员维护空间主数据。编号创建后不可修改；不做物理删除，只能停用（ADR-0007）。
 * 修改方法中为 null 的参数表示不修改该字段。
 */
@Service
public class SpaceAdminService {

    private static final Logger log = LoggerFactory.getLogger(SpaceAdminService.class);

    private final BuildingMapper buildingMapper;
    private final RoomMapper roomMapper;
    private final SeatMapper seatMapper;
    private final SpaceQueryService queryService;

    public SpaceAdminService(BuildingMapper buildingMapper, RoomMapper roomMapper, SeatMapper seatMapper,
            SpaceQueryService queryService) {
        this.buildingMapper = buildingMapper;
        this.roomMapper = roomMapper;
        this.seatMapper = seatMapper;
        this.queryService = queryService;
    }

    @Transactional
    public BuildingEntity createBuilding(String code, String name) {
        BuildingEntity building = new BuildingEntity();
        building.setCode(code);
        building.setName(name);
        building.setStatus(SpaceStatus.ACTIVE);
        insertUnique(() -> buildingMapper.insert(building), "楼栋编号 " + code + " 已存在");
        log.info("building created id={}", building.getId());
        return queryService.getBuilding(building.getId());
    }

    @Transactional
    public RoomView createRoom(long buildingId, String code, String name, int capacity) {
        queryService.getBuilding(buildingId);
        RoomEntity room = new RoomEntity();
        room.setBuildingId(buildingId);
        room.setCode(code);
        room.setName(name);
        room.setCapacity(capacity);
        room.setStatus(SpaceStatus.ACTIVE);
        insertUnique(() -> roomMapper.insert(room), "教室编号 " + code + " 已存在");
        log.info("room created id={} buildingId={}", room.getId(), buildingId);
        return queryService.getRoom(room.getId());
    }

    /** 任何一个编号重复时整批失败，一个座位都不新建（事务回滚）。 */
    @Transactional
    public List<SeatView> createSeats(long roomId, List<String> codes) {
        queryService.getRoom(roomId);
        List<Long> ids = new ArrayList<>();
        for (String code : codes) {
            SeatEntity seat = new SeatEntity();
            seat.setRoomId(roomId);
            seat.setCode(code);
            seat.setStatus(SpaceStatus.ACTIVE);
            insertUnique(() -> seatMapper.insert(seat), "教室 " + roomId + " 中座位编号 " + code + " 已存在");
            ids.add(seat.getId());
        }
        log.info("seats created roomId={} count={}", roomId, ids.size());
        return queryService.findSeats(ids);
    }

    @Transactional
    public BuildingEntity updateBuilding(long id, String name, SpaceStatus status) {
        queryService.getBuilding(id);
        if (name != null || status != null) {
            BuildingEntity patch = new BuildingEntity();
            patch.setId(id);
            patch.setName(name);
            patch.setStatus(status);
            buildingMapper.updateById(patch);
            log.info("building updated id={}", id);
        }
        return queryService.getBuilding(id);
    }

    @Transactional
    public RoomView updateRoom(long id, String name, Integer capacity, SpaceStatus status) {
        queryService.getRoom(id);
        if (name != null || capacity != null || status != null) {
            RoomEntity patch = new RoomEntity();
            patch.setId(id);
            patch.setName(name);
            patch.setCapacity(capacity);
            patch.setStatus(status);
            roomMapper.updateById(patch);
            log.info("room updated id={}", id);
        }
        return queryService.getRoom(id);
    }

    @Transactional
    public SeatView updateSeat(long id, SpaceStatus status) {
        queryService.getSeat(id);
        if (status != null) {
            SeatEntity patch = new SeatEntity();
            patch.setId(id);
            patch.setStatus(status);
            seatMapper.updateById(patch);
            log.info("seat updated id={}", id);
        }
        return queryService.getSeat(id);
    }

    /** 靠唯一约束判断编号重复：先查再插挡不住并发请求。 */
    private static void insertUnique(Supplier<Integer> insert, String duplicatedDetail) {
        try {
            insert.get();
        } catch (DuplicateKeyException e) {
            throw new BusinessException(PlatformErrorCode.SPACE_CODE_DUPLICATED, duplicatedDetail);
        }
    }
}
