# 计划：platform-service 空间主数据（楼栋、教室、座位）

- 日期：2026-10-21
- 关联：ADR-0007；`docs/specs/d-platform.md` 第 1 节；需求 REQ-D-001 ～ REQ-D-003（本次新增）
- 状态：待确认

## 目标

M1/M2/M3 开工时能拿到同一份“在哪”的数据：

- 管理员通过 `/api/platform/spaces/...` 维护楼栋、教室、座位；
- 其他服务通过 `/internal/spaces/...` 按 ID 查询（单个、批量、某教室全部座位），返回里带 `available`。

## 范围

做：
- `docs/requirements/d-platform.md`：REQ-D-001 维护空间、REQ-D-002 查询空间、REQ-D-003 停用规则，验收标准与下面的测试一一对应。
- platform-service：3 个迁移、dao / service / api 三层、错误码、测试。

不做（写明原因，后续另起）：
- 权限校验：等 ADR-0008。在那之前管理员接口任何人可调，**只在开发环境使用**。
- 删除 seat-service 的示例 `room` 表：改的是另一个服务，单独一个 PR（spec 1.8）。
- M1/M2/M3 调用 D 的 `client`：调用方自己声明（AGENTS.md），由各模块组写。
- 分页、按名称搜索、演示用初始数据：校园规模小，列表先不分页；演示数据等前端/联调需要时再定放哪里。
- 修改教室所属楼栋、修改编号：编号是其他人对照用的标识，先不允许改。

## 改动清单

基准包 `services/platform-service/src/main/java/cn/edu/fudan/spacewise/platform/`。

### 迁移（单独一个 commit）

`src/main/resources/db/migration/`，一个脚本只建一张表（ADR-0003），字段按 spec 1.3：

- `V1__create_building.sql`：`code` 唯一。
- `V2__create_room.sql`：`code` 全局唯一；`building_id` 外键 → `building(id)`。
- `V3__create_seat.sql`：`(room_id, code)` 唯一；`room_id` 外键 → `room(id)`。

公共列：`status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'`；`created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)`；
`updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)`。外键 `ON DELETE RESTRICT`（本来就不做物理删除，防误删）。

### dao

- `BuildingEntity` / `BuildingMapper`、`RoomEntity` / `RoomMapper`、`SeatEntity` / `SeatMapper`，写法同 seat-service 的 `RoomEntity`。
- `status` 字段类型为 `service` 包的枚举 `SpaceStatus { ACTIVE, DISABLED }`（MyBatis 默认按名称存取，列里就是 `ACTIVE`）。
  枚举放 service 而不是 dao，因为 api 的请求 DTO 也要用它，api 不能依赖 dao。
- 实体不映射 `created_at` / `updated_at`（由数据库维护，目前没有接口返回它们）。

### service

- `SpaceStatus`、`PlatformErrorCode`（spec 1.6 的 4 个，前缀 `PLATFORM.`）。
- `SpaceQueryService`：只读。
  - `getRoom(id)` / `getSeat(id)`：不存在抛 `BusinessException`（404）。
  - `findRooms(ids)` / `findSeats(ids)`：只返回存在的，按 `id` 升序，重复 ID 去重。
  - `listBuildings()`、`listRooms(buildingId 可空)`、`listSeats(roomId)`：按 `code` 升序；`listSeats` 的教室不存在 → 404。
  - 返回 service 层的只读视图 `RoomView(RoomEntity room, BuildingEntity building)`、`SeatView(seat, room, building)`，
    让 api 能组装 `buildingName`、`available`。上级用一次 `selectByIds` 批量取，不逐条查（避免 N+1）。
- `SpaceAdminService`：写操作，方法加 `@Transactional`。
  - `createBuilding`、`createRoom`（`buildingId` 不存在 → `BUILDING_NOT_FOUND`）、`createSeats(roomId, codes)`（教室不存在 → `ROOM_NOT_FOUND`）。
  - 编号重复：捕获 `DuplicateKeyException` 转成 `SPACE_CODE_DUPLICATED`（409）。不先查再插，因为并发时查询挡不住，唯一约束才可靠。
    批量建座位中任何一个重复，整批回滚。
  - `updateBuilding` / `updateRoom` / `updateSeat`：请求里为 `null` 的字段不修改；不存在 → 对应 404。
  - 每次写操作记一行 INFO 日志，只记 ID 和动作（如 `room created id=12`）。

`available` 的计算放在一处（`SpaceQueryService` 内的静态方法）：楼栋 = 自己 ACTIVE；教室 = 自己且楼栋 ACTIVE；座位 = 自己、教室、楼栋都 ACTIVE。

### api

- `api/dto/`：
  - 响应：`BuildingResponse(id, code, name, status, available)`；
    `RoomResponse(id, buildingId, buildingCode, buildingName, code, name, capacity, status, available)`；
    `SeatResponse(id, roomId, roomCode, code, status, available)`。内部、对外接口共用。
  - 请求：`CreateBuildingRequest(code, name)`、`CreateRoomRequest(buildingId, code, name, capacity)`、`CreateSeatsRequest(codes)`、
    `UpdateBuildingRequest(name, status)`、`UpdateRoomRequest(name, capacity, status)`、`UpdateSeatRequest(status)`。
  - 校验：编号 `@Pattern("[A-Za-z0-9-]{1,32}")`（座位 1～16 位）；名称 `@Size(max = 64)`，楼栋名称必填；`capacity` `@Positive`，新建时必填；
    `codes` `@NotEmpty @Size(max = 200)`。
- `InternalSpaceController`（`/internal/spaces`）：spec 1.4 的 5 个 GET。批量参数 `?ids=1,2,3` 绑定为 `List<Long>`，
  加 `@NotEmpty @Size(max = 100)`；缺少参数由 web-common 返回 400。
- `SpaceController`（`/api/platform/spaces`）：spec 1.5 的 7 个接口。新建返回 201 + 新对象；批量建座位返回该教室新建的座位列表；PATCH 返回修改后的对象。

网关已转发 `/api/platform/**` 且拦截 `/internal/**`，不用改。

### 文档

- `docs/specs/d-platform.md`：补上本计划定下的细节（批量去重与排序、PATCH 的 `null` 语义、批量建座位上限 200、编号不可修改），状态不变。
- 本计划末尾记录偏差。

## 测试（`@IntegrationTest`，真实 MySQL）

API 测试在 `@AfterEach` 里按 `seat → room → building` 顺序清空三张表（platform 目前没有其他数据）。

- `SpaceSchemaTest`（`@Transactional` 回滚）：
  楼栋、教室编号重复被拒；同一教室座位编号重复被拒，不同教室可以相同；教室引用不存在的楼栋被外键拒绝。
- `SpaceAdminApiTest`：
  1. 新建楼栋 → 201，返回 `status=ACTIVE`、`available=true`；同编号再建 → 409 `PLATFORM.SPACE_CODE_DUPLICATED`；
  2. 新建教室时楼栋不存在 → 404 `PLATFORM.BUILDING_NOT_FOUND`；`capacity=0` → 400 `COMMON.VALIDATION_FAILED`；
  3. 批量建座位成功；请求内编号重复 → 409，且该教室**一个座位都没有建**（回滚）；
  4. PATCH 只改传了的字段（只改 `name` 时 `capacity` 不变）；PATCH 不存在的 ID → 404；`status` 传非法值 → 400。
- `InternalSpaceApiTest`：
  1. 单个教室带楼栋编号和名称；不存在 → 404 `PLATFORM.ROOM_NOT_FOUND`；
  2. 批量查询只返回存在的 ID，按 ID 升序；101 个 ID → 400；不带 `ids` → 400；
  3. 停用楼栋后，其下座位 `status` 仍为 `ACTIVE`，但 `available=false`；重新启用后恢复 `true`；
  4. 教室座位列表按编号升序；教室不存在 → 404。

## 提交

1. `docs(platform): 新增空间主数据实现计划与 REQ-D-001~003`（本计划 + 需求文件）——**你确认计划后再继续**
2. `feat(platform): 新增楼栋、教室、座位表迁移`（只含 3 个 SQL）
3. `feat(platform): 空间主数据的管理与内部查询接口`（代码 + 测试 + spec 补充），`Refs: REQ-D-001, REQ-D-002, REQ-D-003`

一个 PR 到 `dev`。

## 验收标准

1. `mvn verify -pl services/platform-service -am` 通过；`bash scripts/ci/check-migrations.sh origin/dev` 通过；CI 通过。
2. Compose 全栈下经网关：
   - `POST localhost:8080/api/platform/spaces/buildings` → 建楼栋、教室、座位，`GET .../rooms/{id}/seats` 能看到；
   - `curl localhost:8080/internal/spaces/rooms/1` → 404（网关不转发）；
   - `curl localhost:8084/internal/spaces/rooms?ids=1,999` → 只返回 1。

## 风险

- **管理员接口暂无权限**：ADR-0008 落地前不要部署到能被外部访问的环境；PR 描述里写明。
- **接口一经 M1/M2/M3 使用就是契约**：响应字段只加不删；本 PR 合并后通知各组。
- M2 计划提到“不可申请的教室”：本次不加“教室用途”字段（spec 待定问题 2），由 M1/M2 组提需求后新增迁移添加。
- 座位编号按字符串排序，`A-12` 会排在 `A-2` 前；需要自然排序时再改。

## 实现偏差记录

（实现后填写）
