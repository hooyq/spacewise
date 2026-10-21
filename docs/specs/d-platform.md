# 公共层 D（platform-service）设计

- 状态：草稿（待组内评审）
- 日期：2026-10-21
- 相关：ADR-0007（空间主数据归 D）；需求 `docs/requirements/d-platform.md`（待写）

本文只定**各模块开工必需的最小集合**。字段、接口以后可以加；已发布的接口改名或删除须经调用方模块组评审（跨模块接口是契约）。

| 部分 | 本轮 | 说明 |
|---|---|---|
| 空间主数据（楼栋、教室、座位） | **定义** | 见第 1 节 |
| 用户与权限（RBAC） | 占位 | 取决于认证 ADR（ADR-0008，待写），见第 2 节 |
| 通知、审计 | 占位 | 后置，见第 3 节 |

---

## 1. 空间主数据

### 1.1 领域模型

```
楼栋 Building 1 ── n 教室 Room 1 ── n 座位 Seat
```

- **主数据只描述“是什么、在哪”**：这个座位存在、编号是什么、属于哪间教室。
- **业务状态不在这里**：座位能否预约、被下架、被占用（及写入来源 M1/M2/M3）由 M1 记录；整间教室的预约、审批由 M2 记录；工单由 M3 记录。

### 1.2 状态

每个楼栋、教室、座位只有一个 `status`：

```
ACTIVE（启用） ⇄ DISABLED（停用）
```

- 停用表示“不再对外提供”（拆除、改作他用），**不是**临时维修（维修中是 M3 → M1 的业务状态）。
- **不做物理删除**：其他服务里存着这些 ID，删掉会让引用悬空。停用后仍可按 ID 查到，以便展示历史预约、工单。
- 停用上级不自动修改下级的 `status`；查询接口返回时由调用方同时看上级状态（见 1.4 的 `available` 字段）。

### 1.3 数据表（`platform_db`）

最小字段，后续用新的迁移版本扩展。

**building**

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT 自增 | 主键 |
| code | VARCHAR(32) | 楼栋编号，如 `H3`，唯一 |
| name | VARCHAR(64) | 名称，如“第三教学楼” |
| status | VARCHAR(16) | `ACTIVE` / `DISABLED` |
| created_at / updated_at | DATETIME(3) | |

**room**

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT 自增 | 主键 |
| building_id | BIGINT | 所属楼栋，外键（同库内可以用外键） |
| code | VARCHAR(32) | 教室编号，如 `H3209`，全局唯一 |
| name | VARCHAR(64) | 可选显示名 |
| capacity | INT | 容纳人数（M2 整间预约要用） |
| status | VARCHAR(16) | `ACTIVE` / `DISABLED` |
| created_at / updated_at | DATETIME(3) | |

**seat**

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT 自增 | 主键 |
| room_id | BIGINT | 所属教室，外键 |
| code | VARCHAR(16) | 室内编号，如 `A-12`；`(room_id, code)` 唯一 |
| status | VARCHAR(16) | `ACTIVE` / `DISABLED` |
| created_at / updated_at | DATETIME(3) | |

> 座位属性（靠窗、有插座等）、教室用途（是否开放自习、是否可整间预约）等**暂不建**，由 M1/M2 组在需求里提出后再加列。

### 1.4 内部接口（服务间，`/internal/spaces/...`，网关不转发）

供 M1/M2/M3 校验 ID、展示名称。返回的每个对象都带：

- `available`：本身和所有上级都是 `ACTIVE` 时为 `true`。调用方判断“能不能用”只看这一个字段。

| 方法与路径 | 说明 |
|---|---|
| `GET /internal/spaces/rooms/{id}` | 单个教室（含所属楼栋 `buildingId`、`buildingName`）；不存在 → 404 `PLATFORM.ROOM_NOT_FOUND` |
| `GET /internal/spaces/rooms?ids=1,2,3` | 批量查教室；**只返回存在的**，不存在的 ID 不报错（调用方自行比对）；一次最多 100 个；结果按 ID 升序，重复 ID 只返回一次 |
| `GET /internal/spaces/seats/{id}` | 单个座位（含 `roomId`）；不存在 → 404 `PLATFORM.SEAT_NOT_FOUND` |
| `GET /internal/spaces/seats?ids=...` | 批量查座位，规则同教室 |
| `GET /internal/spaces/rooms/{id}/seats` | 某教室的全部座位（M3 维修中 → M1 下架“该室座位”要用） |

### 1.5 对外接口（经网关，`/api/platform/spaces/...`）

| 方法与路径 | 谁能调 | 说明 |
|---|---|---|
| `GET /api/platform/spaces/buildings` | 登录用户 | 楼栋列表 |
| `GET /api/platform/spaces/rooms?buildingId=` | 登录用户 | 教室列表，可按楼栋过滤 |
| `GET /api/platform/spaces/rooms/{id}/seats` | 登录用户 | 某教室的座位列表 |
| `POST /api/platform/spaces/buildings` | 管理员 | 新建楼栋 |
| `POST /api/platform/spaces/rooms` | 管理员 | 新建教室 |
| `POST /api/platform/spaces/rooms/{id}/seats` | 管理员 | 批量新建座位（一次传多个编号） |
| `PATCH /api/platform/spaces/{buildings｜rooms｜seats}/{id}` | 管理员 | 修改名称、容量、`status`（停用 / 启用） |

补充约定：

- 新建成功返回 201 和新对象；批量新建座位一次最多 200 个，任何一个编号重复则整批失败，一个都不新建。
- PATCH 中没有传（或为 `null`）的字段保持不变；编号、所属楼栋 / 教室创建后不可修改。
- 列表按编号升序（字符串排序，`A-12` 排在 `A-2` 前）。

“登录用户 / 管理员”的校验方式取决于 ADR-0008；在它落地前，这些接口先不做权限校验。

### 1.6 错误码（`PlatformErrorCode`，发布后不改名）

| code | HTTP | 场景 |
|---|---|---|
| `PLATFORM.BUILDING_NOT_FOUND` | 404 | 楼栋不存在 |
| `PLATFORM.ROOM_NOT_FOUND` | 404 | 教室不存在 |
| `PLATFORM.SEAT_NOT_FOUND` | 404 | 座位不存在 |
| `PLATFORM.SPACE_CODE_DUPLICATED` | 409 | 编号重复 |

### 1.7 与其他模块的依赖

| 调用方 | 用到的接口 | 用途 |
|---|---|---|
| M1 | 查座位（单个 / 批量）、教室下的座位 | 预约前校验座位、展示；收到写回时找出该室座位 |
| M2 | 查教室（单个 / 批量） | 预约前校验教室、容量 |
| M3 | 查教室、查座位 | 报修位置校验与展示 |

- D **不调用** M1/M2/M3，也不主动通知它们（没有 MQ，红线 5）。停用教室后已有预约怎么处理，属于 M1/M2 的业务规则。
- 调用方按 AGENTS.md 用 `client` + `HttpClients` 调用；D 不可用时返回明确错误，不影响自己的健康检查。

### 1.8 遗留

seat-service 的 `room` 表（V1）只是分层示例，与本设计冲突。实现空间接口时：新增 seat-service 的 V2 迁移删除该表及示例代码（V1 不修改）。具体写进那一步的计划。

---

## 2. 用户与权限（占位）

待 ADR-0008（认证与用户上下文）决定后补充。目前的倾向：

- 网关验证登录凭证，向下游注入 `X-User-Id`、`X-Roles`；`libs/web-common` 提供解析，业务代码直接拿到“当前用户”。
- 用户、角色由 D 维护；其他服务只存 `user_id`。
- 角色清单（学生、管理员、维修人员、审批人……）由各模块需求汇总后确定。

## 3. 通知、审计（占位）

后置。届时只约定 D 提供 `/internal/notifications`、`/internal/audit-logs` 一类的写入接口，由各模块在关键动作后调用。

---

## 待组内确认

1. 教室编号是否全局唯一（本文假设是，如 `H3209`）？
2. 是否需要“教室用途”字段区分自习室与可整间预约的教室？由 M1/M2 组决定。
3. 批量接口上限 100 是否够用？
