# M3 物业报修工单设计

- 状态：提议中，待 M1、D 与项目负责人评审
- 日期：2026-10-21
- 需求：`docs/requirements/m3-repair.md`
- 相关决策：ADR-0003、ADR-0006、ADR-0007、ADR-0009；认证方案预留 ADR-0008

## 1. 边界与依赖

M3 位于 `services/repair-service`，端口 8083，只读写 `repair_db`。浏览器只经网关访问 `/api/repair/**`；服务间调用使用 `/internal/**`。

- D 是楼栋、教室和座位的唯一主数据源。前端直接调用 `/api/platform/spaces/rooms` 选择教室，M3 创建工单时仍调用 `/internal/spaces/rooms/{id}` 校验，避免信任客户端缓存。
- M1 保存座位是否可预约及限制来源。M3 不枚举或修改座位记录，只按教室建立来源为 M3、关联工单的限制。
- 身份、角色和空间管辖范围由 D/公共认证层提供；通知、审计由 D 接收。D 尚未提供的能力通过 M3 端口和待投递表隔离，不在 Controller 中伪造。

## 2. 角色与权限

| 动作 | 报修人 | 维修人员 | 物业管理员 |
|---|---|---|---|
| 上报、查看本人、取消未开工工单 | 是 | 作为登录用户时是 | 作为登录用户时是 |
| 查看被指派工单、开始维修、提交完工 | 否 | 仅被指派工单 | 否 |
| 查询管辖范围、派单、开工前改派 | 否 | 否 | 是 |
| 验收 | 否 | 否 | 有空间权限且不是该工单维修人员 |
| 评价 | 仅本人已完成工单 | 否 | 若本人是报修人则是 |

授权只使用可信 `CurrentUser`（`userId`、角色、空间范围）。请求 DTO 不接收用于授权的 `reporterId`、`operatorId` 或角色。认证能力未落地时，生产环境应拒绝需要身份的请求；测试可注入仅在测试配置中生效的用户提供器。

## 3. 状态机

```text
SUBMITTED -> ASSIGNED -> STARTING -> IN_PROGRESS -> PENDING_ACCEPTANCE
                |                         ^                 |
                |                         | 验收不通过       | 验收通过
                v                         +-----------------+
             CANCELLED                                      v
SUBMITTED -> CANCELLED                                  RESTORING -> COMPLETED
```

- `SUBMITTED`：等待物业管理员派单。
- `ASSIGNED`：已指派，可由管理员改派；尚未产生维修限制。
- `STARTING`：下架意图已持久化，等待 M1 给出确定结果。
- `IN_PROGRESS`：M1 已确认本工单限制生效。
- `PENDING_ACCEPTANCE`：维修人员已提交完工，限制继续有效。
- `RESTORING`：验收通过，等待 M1 确认精确解除本工单限制。
- `COMPLETED`、`CANCELLED`：终态。

所有转换通过统一的 `RepairTransitionService` 执行，使用 `version` 乐观锁或带当前状态条件的更新。Controller 和定时任务不得直接改状态。

明确的 M1 业务拒绝且能证明未产生限制时，`STARTING` 可回到 `ASSIGNED` 并记录原因；超时、断连和 5xx 属于结果未知，保持 `STARTING`。`RESTORING` 只有解除成功一条出路，失败持续恢复并升级人工处理。

## 4. 领域数据

### 4.1 `repair_work_order`

核心字段：`id`、`room_id`、`reporter_id`、`category`、`description`、`status`、`assignee_id`、`version`、`stage_cycle_id`、`stage_started_at`、`due_at`、`repair_due_at`、`idempotency_key`、`request_hash`、`created_at`、`updated_at`。

- 唯一键：`(reporter_id, idempotency_key)`。
- 不复制 D 的教室名称；历史页面查询不到 D 时仍显示 `room_id`，不删除工单。
- 描述属于受限业务内容，不写运行日志。

### 4.2 `repair_work_order_action`

追加式记录：工单、动作类型、操作者、前后状态、理由/完工说明/验收结论、`stage_cycle_id`、发生时间。已产生记录不覆盖，用于时间线和审计证据。

### 4.3 `repair_operation`

保存跨 M1 操作：`operation_id`、`work_order_id`、`type(ACTIVATE/RELEASE)`、`restriction_id`、`status(PENDING/SUCCEEDED/REJECTED/MANUAL_REQUIRED)`、请求摘要、尝试次数、`next_attempt_at`、脱敏错误码和时间。`operation_id` 唯一，每张工单每种操作最多一条有效记录。

### 4.4 `repair_escalation`

保存 `work_order_id`、`stage_cycle_id`、`level`、接收角色/用户、发生时间。唯一键 `(work_order_id, stage_cycle_id, level)` 防止重复升级。

### 4.5 `repair_evaluation`

保存工单、评价人、1 至 5 分、可选评价内容和时间；`work_order_id` 唯一。

### 4.6 `repair_delivery`

保存通知/审计待投递事件：`event_id`、类型、业务键、最小必要载荷、状态、尝试次数和下次重试时间。业务状态和待投递事件在同一本地事务登记。正文等敏感内容不复制到不需要它的通知载荷。

迁移从 `V1__create_repair_work_order.sql` 起按版本新增；迁移一经合并不得修改。索引覆盖报修人列表、指派人待办、空间状态查询、`due_at` 扫描和恢复任务扫描。

## 5. 对外 API

成功直接返回 DTO；失败遵循 ADR-0006 的 `ProblemDetail + code + traceId`。

| 方法与路径 | 权限与行为 |
|---|---|
| `POST /api/repair/work-orders` | 登录用户；请求头 `Idempotency-Key`，请求含 `roomId/category/description` |
| `GET /api/repair/work-orders/mine` | 当前报修人的分页列表 |
| `GET /api/repair/work-orders` | 维修人员看本人待办；管理员按空间范围查询 |
| `GET /api/repair/work-orders/{id}` | 按角色与关系读取详情和时间线 |
| `POST /api/repair/work-orders/{id}/assign` | 管理员派单/开工前改派；含 `assigneeId/repairDueAt/reason` |
| `POST /api/repair/work-orders/{id}/start` | 被指派人员；跨服务处理中返回 202 |
| `POST /api/repair/work-orders/{id}/finish` | 被指派人员；含完工说明 |
| `POST /api/repair/work-orders/{id}/acceptance` | 管理员；含 `accepted/reason`，恢复处理中返回 202 |
| `POST /api/repair/work-orders/{id}/evaluation` | 报修人；含 `score/comment` |
| `POST /api/repair/work-orders/{id}/cancel` | 报修人或管理员；仅未开工状态 |

不再增加 `/api/repair/rooms`：空间选择复用 D 的公开接口，M3 只在写入时通过 D 内部接口二次校验。

稳定错误码至少包括：`REPAIR.WORK_ORDER_NOT_FOUND`、`REPAIR.INVALID_STATE`、`REPAIR.FORBIDDEN`、`REPAIR.ROOM_UNAVAILABLE`、`REPAIR.INVALID_ASSIGNEE`、`REPAIR.IDEMPOTENCY_CONFLICT`、`REPAIR.DOWNSTREAM_UNAVAILABLE`。

## 6. D 内部契约

已可使用：

- `GET /internal/spaces/rooms/{id}`：返回 `id/buildingId/buildingCode/buildingName/code/name/capacity/status/available`。创建工单要求 `available=true`。

待 D/认证 ADR 落地：

- 可信当前用户、角色和空间管辖范围；
- 查询可被指派维修人员；
- 幂等通知写入；
- 幂等操作审计写入。

M3 为这些能力声明调用方自己的 `PlatformClient`/端口 DTO，不引用 platform-service 类。空间查询失败使创建请求失败；通知、审计投递失败只保留 `repair_delivery` 重试，不回滚工单状态。

## 7. M1 维修限制契约

由 M1 提供以下 `/internal/**` 接口，具体 DTO 变更须由 M1/M3 共同评审：

### 7.1 创建或确认限制

`POST /internal/seat-restrictions`

请求：`operationId`、`source=M3`、`sourceRef=workOrderId`、`roomId`。响应：`operationId`、`restrictionId`、`status=SUCCEEDED|REJECTED`、`affectedReservationCount`。

- `operationId` 是幂等键；同键同内容返回原结果，同键不同内容返回 409。
- `(source, sourceRef)` 唯一且生命周期单调：同一工单只对应一条限制。
- M1 在一个本地事务内建立限制、阻止新预约，并处理已有预约。

### 7.2 精确解除限制

`POST /internal/seat-restrictions/{restrictionId}/release`

请求：`operationId`、`source=M3`、`sourceRef=workOrderId`。响应包含操作状态。只能解除三者匹配的限制；重复解除返回原成功结果。不得按 `roomId` 清空该室所有限制。

### 7.3 查询操作结果

`GET /internal/seat-restriction-operations/{operationId}`

返回操作类型、结果、`restrictionId` 和脱敏错误码；未知操作返回 404。用于响应丢失后的确认。已经解除的限制收到旧 ACTIVATE 重放时仍返回历史结果，不得重新激活。

## 8. 开工与恢复流程

开始维修：

1. 本地事务校验 `ASSIGNED` 和指派人，转为 `STARTING`，写动作与 ACTIVATE 操作；
2. 事务提交后调用 M1；
3. 成功则保存 `restrictionId` 并转为 `IN_PROGRESS`；确定拒绝且无副作用则回到 `ASSIGNED`；结果未知则返回 202；
4. 恢复任务按同一 `operationId` 先查结果，再安全重试。

验收通过：

1. 本地事务校验验收权限，转为 `RESTORING`，写验收动作与 RELEASE 操作；
2. 调用 M1 精确解除；成功后转为 `COMPLETED`；结果未知返回 202；
3. 其他 M3 工单、M2 占用和 M1 预约状态均由 M1 独立计算，M3 不声明“整个教室完全可约”。

恢复任务使用数据库原子领取或租约，允许多实例扫描但不能并发推进同一操作。采用指数退避并设置上限；超过配置的技术恢复窗口后标记 `MANUAL_REQUIRED`、产生二级运维升级，但不伪造终态。

## 9. 已有预约规则

ACTIVATE 生效后立即阻止新预约和签到使用。与维修影响范围重叠的已有预约由 M1 保留原始记录并转为“因维修受影响/取消”的明确状态；不物理删除、不计学生违约。M1 返回受影响数量，并登记面向预约人的通知事件；通知失败不撤销限制。若当下已有用户使用，物业管理员需先完成现场疏散，系统记录不能代替现场安全处置。

## 10. 超时升级

| 阶段 | 首版期限 | 一级接收人 | 二级接收人 |
|---|---:|---|---|
| `SUBMITTED` 待派单 | 2 小时 | 空间物业管理员 | 物业主管/系统管理员 |
| `ASSIGNED` 待开工 | 4 小时 | 指派维修人员、物业管理员 | 物业主管/系统管理员 |
| `IN_PROGRESS` 维修 | 派单时指定，默认 24 小时 | 指派维修人员、物业管理员 | 物业主管/系统管理员 |
| `PENDING_ACCEPTANCE` 待验收 | 4 小时 | 空间物业管理员 | 物业主管/系统管理员 |

按自然时间计算。一级在 `due_at` 触发，二级在一级后 4 小时触发；所有数值通过 `spacewise.repair.sla.*` 配置。`STARTING/RESTORING` 属于技术恢复，使用独立重试和人工处理阈值，不混入业务 SLA。

阶段变化生成新的 `stage_cycle_id` 和期限。扫描以当前状态、周期和到期时间为条件插入唯一升级记录，使用可注入 `Clock` 测试，不用长时间 `sleep`。

## 11. 安全、隐私和可观测性

- 网关不转发 `/internal/**`；后续服务身份认证落地后，M1/D 内部接口还须校验服务身份，不能把网络位置当成永久授权。
- 运行日志只记录工单 ID、操作 ID、状态和脱敏错误码，不记录描述、完工说明、评价、联系方式或令牌。
- 下游不可用不参与 M3 健康检查；跨服务请求沿用 `HttpClients` 传播 traceId。
- 评价不直接作为维修人员惩罚依据；争议处理依靠动作记录和人工复核。

## 12. 验证重点

- 状态与权限：越权访问、维修人员自验、非法状态跳转、并发派单/取消/开始；
- 数据：空库迁移、唯一约束、乐观锁、分页和动作时间线；
- 一致性：请求未送达、成功响应丢失、服务重启、重复请求、同室多工单、与 M2 限制叠加；
- 预约公平：维修取消不产生学生违约，记录和通知可追溯；
- 超时：到期边界、重复扫描、多实例、返修新周期和 D 不可用后的补投递。

本设计通过 `mvn verify -pl services/repair-service -am` 和真实 MySQL Testcontainers 验证；客户端错误使用本地 HTTP 假服务模拟，不 mock 被测业务状态机本身。
