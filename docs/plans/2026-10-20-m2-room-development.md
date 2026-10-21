# M2 整教室预约与审批：双人开发计划

- 日期：2026-10-20
- 更新：2026-10-21，按最新 AGENTS.md 同步 dev/main 分支流程。
- 状态：草稿，供组内及 M1/D 组评审；尚未开始业务实现
- 依据：AGENTS.md、docs/REVIEW.md、现有 room-service 骨架及 repair-service HTTP 客户端示例。
- 需求来源限制：仓库尚无任务书正文和 M2 需求基线。下文需求编号、接口、状态和排期均为建议，不能当作已经确认的课程要求。

## 1. 目标与范围

交付可演示的闭环：查看教室 → 提交申请 → 查看本人申请 → 管理员审批 → M1 占用该教室对应时段的座位 → 取消后释放本次占用。

已确定的边界：M2 在 services/room-service，端口 8082，只使用 room_db；公共层 D 管理 RBAC、空间主数据、通知与审计；M2 通过 HTTP 请求 M1，占用必须标记来源 M2。浏览器只走网关 /api/room/**。

建议首版采用单级审批、单教室、单次连续时段。批量/周期预约、多级审批、附件、候补、复杂统计暂不纳入首版；若任务书要求，重新排期。

前端框架尚无 ADR，不擅自选型。先按接口完成后端演示；需要完整页面时，在团队确认前端方案后接入申请页、我的申请页、审批页。

## 2. 开工前必须确认的业务契约

| 决策 | 建议默认值 | 需要确认的人 |
|---|---|---|
| 谁能申请、谁能审批、审批范围 | 登录用户申请，具有指定空间审批权限的管理员审批；禁止自审 | D 组及任务书负责人 |
| 时段粒度、开放时间、提前量、最长时长 | 由任务书确定，不套用 M1 README 中的需求示例 | M2 两人 |
| 待审批是否占用资源 | 不占用；允许多个待审批申请，批准时竞争资源 | M1/M2 |
| 已有 M1 座位预约时如何处理 | 拒绝整教室占用，不自动取消学生预约 | M1/M2 |
| M3 维修与 M2 占用如何冲突 | 不覆盖维修状态；释放 M2 占用也不能恢复 M3 下架资源 | M1/M3/M2 |
| 取消条件 | 本人可撤销待审批申请；已通过申请仅开始前可取消 | M2 两人 |
| 身份及服务间调用可信来源 | 按 D 组认证契约；不得信任请求体里的 applicantId 或客户端自报角色 | D 组 |
| 通知、审计接口及失败语义 | 审批事实与投递状态分开；失败保留待补发记录 | D/M2 |

业务编码前形成 docs/requirements/m2-room.md、docs/specs/m2-room.md；跨模块需求放 docs/requirements/x-integration.md。契约由双方评审，不能只由调用方假定。

## 3. 建议需求与验收基线

以下均为草稿编号，正式落需求时检查是否冲突。

| 编号 | 功能 | 最低验收标准 |
|---|---|---|
| REQ-M2-001 | 查询可申请教室 | 从 D 获取空间信息；不存在/不可申请教室被拒绝；查询可用性不等于保证预约成功 |
| REQ-M2-002 | 提交申请 | 保存本人、教室、起止时间、用途、人数；结束不晚于开始、过去时间、超容量等非法申请被拒绝；同一幂等键重试不产生重复申请 |
| REQ-M2-003 | 查询申请 | 本人列表分页；详情校验所有权或审批权限；不能查看他人的用途等信息 |
| REQ-M2-004 | 审批 | 有权限者通过/驳回；驳回理由必填；并发审批只生效一次；批准成功前 M1 已确认占用 |
| REQ-M2-005 | 取消 | 待审批直接撤销；已批准的申请释放 M1 成功后才进入取消完成；不释放其他申请或 M3 的占用 |
| REQ-M2-006 | 冲突与故障恢复 | 同教室重叠时段最多一个成功占用；M1 超时显示处理中；重启后可恢复；重复调用效果一致 |
| REQ-M2-007 | 通知与审计 | 记录操作者、动作、时间、关联申请及结果；申请人收到最终结果；通知失败不回滚已经完成的占用 |

## 4. 领域模型及状态机

建议申请状态：

- PENDING：等待审批。
- APPROVING：已受理批准操作，等待 M1 确认占用。
- APPROVED：M1 占用成功且 M2 已保存结果。
- REJECTED：审批人驳回，终态。
- CANCELLING：已批准申请正在释放 M1 占用。
- CANCELLED：撤销/取消完成，终态。

允许转换：PENDING → REJECTED / CANCELLED / APPROVING；APPROVING → APPROVED；M1 明确拒绝且确认无占用时 APPROVING → PENDING（附失败原因，允许重新处理）；APPROVED → CANCELLING → CANCELLED。

超时、连接中断、5xx 不能直接视为“占用失败”。保持处理中，按操作标识查询/重试，防止 M1 已占用但 M2 误认为失败。APPROVING 阶段先拒绝取消和其他审批操作；恢复到稳定状态再操作。

暂不为“已结束”增加定时状态转换，可由 APPROVED 与结束时间计算展示。所有业务时间采用 Asia/Shanghai；API 明确携带时区偏移，数据库统一存储约定写入 spec。

建议表（仅在 room_db）：

1. room_booking：id、room_id、applicant_id、start_at、end_at、purpose、attendee_count、status、version、创建/更新时间、提交幂等键与请求摘要。唯一约束 (applicant_id, idempotency_key)。
2. room_booking_action：申请 ID、动作、操作者 ID、前后状态、理由、发生时间、操作 ID；审批记录追加保存。
3. room_booking_operation：持久化跨服务占用/释放操作，含唯一 operation_id、booking_id、动作、generation、状态、尝试次数、下次重试时间、脱敏错误码。用于故障恢复，不能只用内存重试。
4. 如 D 的投递协议需要，增加 room_booking_delivery 保存通知/审计投递及幂等标识；与业务提交在同一本地事务中落库。

索引至少覆盖 (applicant_id, created_at)、(status, created_at)、(room_id, start_at, end_at)、恢复操作的 (status, next_retry_at)。用途内容不得写日志。D 的空间数据只保留引用和必要历史快照，不复制一套主数据管理。

## 5. 并发与跨模块一致性设计

跨服务没有共同事务，不能靠给审批方法加 @Transactional 实现原子性。建议采用本地持久化操作 + HTTP 幂等占用/释放 + 定时恢复；不用 MQ、Redis 或新的基础设施。该取舍须新增 ADR，比较直接同步调用、持久化恢复以及引入消息设施的复杂度。

批准流程：

1. 本地短事务：校验权限，条件更新 PENDING → APPROVING，新增占用操作及动作记录；提交后再发 HTTP。
2. M1 在自己的事务里原子地检查座位预约、其他整教室占用与维修限制，并写入本次整室占用。所有 M1 座位预约写入也必须遵循同一互斥协议，否则存在检查后插入的竞态。
3. M1 明确成功：本地事务更新 APPROVED，记录结果并登记通知/审计待投递任务。
4. M1 明确业务冲突：确认本操作无占用后返回 PENDING 并记录冲突，前端提示调整时段。
5. 结果未知：保留 APPROVING，使用相同 operation_id 查询或重试；后台恢复可在进程重启后继续。超过阈值标记需要人工处理，但不伪装成失败或成功。

M1 契约要求：原子整室占用，不允许只占到部分座位；关联 source=M2、bookingId、operationId；相同操作重放返回同一结果；同一幂等键不同请求内容返回冲突。可查询操作结果。明确拒绝后的新批准尝试生成新的操作 ID，同一次未知结果重试沿用旧 ID。

释放按准确的占用标识执行，重复释放幂等；不能按 roomId 清空所有占用。释放成功后 M2 才写 CANCELLED。占用与释放使用不同操作 ID，M1 应拒绝旧占用请求在释放后再次生效。

同教室时间区间采用半开区间 [start, end)：09:00–10:00 与 10:00–11:00 可相邻；冲突条件 existing.start < new.end 且 existing.end > new.start。普通唯一索引不能防止任意时间区间重叠。M1 必须选定统一房间锁/时段锁协议，并与本组契约测试；仅在 M2 加锁不能防止 M1 学生预约竞态。

## 6. 建议接口（尚不存在）

| 方法与路径 | 用途 |
|---|---|
| GET /api/room/rooms | 教室列表及筛选，由 M2 client 请求 D |
| GET /api/room/rooms/{roomId}/availability | 指定范围的可用性参考，最终以批准时为准 |
| POST /api/room/bookings | 创建申请，携带 Idempotency-Key |
| GET /api/room/bookings/mine | 本人申请分页 |
| GET /api/room/bookings/{id} | 详情与当前处理状态 |
| GET /api/room/approvals | 有审批权限用户的待办分页 |
| POST /api/room/bookings/{id}/approve | 受理批准；未完成返回 202，已完成返回 DTO |
| POST /api/room/bookings/{id}/reject | 驳回并记录理由 |
| POST /api/room/bookings/{id}/cancel | 撤销或发起取消 |

M1 内部接口由双方共同命名，需提供占用、释放、结果查询三种能力，路径放 /internal/**。D 需提供空间查询、可信身份/权限判定、通知与审计契约。

统一成功返回 DTO，不套 Result；错误使用 ProblemDetail + code + traceId。建议 ROOM.BOOKING_NOT_FOUND、ROOM.INVALID_STATE、ROOM.TIME_CONFLICT、ROOM.FORBIDDEN 等稳定错误码；参数格式错误沿用 COMMON.VALIDATION_FAILED。HTTP 400/401/403/404/409/503 的语义写入 spec；资源是否存在的泄露策略与 D 对齐。

## 7. 两人分工

推荐按业务纵向拆分，两人都写 Controller、Service、Mapper 和测试，不按“一个人所有前端、一个人所有后端”拆。

| 工作 | 同学 A：申请侧负责人 | 同学 B：审批侧负责人 |
|---|---|---|
| 共同设计 | 需求、DTO、表结构、状态机、M1/D 契约 | 同左，互审后冻结首版 |
| 后端主责 | 教室查询、申请提交、本人列表/详情、撤销待审批申请、D 空间客户端 | 审批待办、批准/驳回、已批准申请取消、M1 客户端、持久化操作恢复 |
| 通知/审计 | 实现 D 投递适配与查询展示 | 在状态转换事务中登记动作及待投递事件 |
| 前端（选型后） | 教室选择/申请表单、本人申请与详情 | 审批列表/详情、处理中/失败/重试展示 |
| 测试 | 校验、提交幂等、分页、本人权限 | 状态机、竞态、占用释放幂等、超时与恢复 |
| 交付 | 使用说明和申请侧演示 | 联调记录与故障恢复演示 |

B 的一致性工作较重，A 完成申请侧后协助恢复场景测试。共享 BookingEntity、状态枚举、DTO、迁移由第一份基础 PR 固定；后续修改先同步。取消操作通过统一服务入口按状态分派，两人不得分别编写冲突的状态更新逻辑。

## 8. 文件级改动计划

以 services/room-service/src/main/java/cn/edu/fudan/spacewise/room/ 为基准，新增：

- api/RoomController.java、BookingController.java、ApprovalController.java。
- api/dto/ 下创建请求、分页响应、申请详情、审批/取消响应 DTO。
- service/BookingService.java、ApprovalService.java、BookingCancellationService.java、BookingOperationService.java、BookingRecoveryJob.java、RoomCatalogService.java、RoomErrorCode.java；状态转换由统一方法约束。
- dao/BookingEntity.java、BookingMapper.java、BookingActionEntity.java、BookingActionMapper.java、BookingOperationEntity.java、BookingOperationMapper.java；必要时增加 delivery 对应类。
- client/SeatClient.java、PlatformClient.java 及调用方私有 DTO。
- config/HttpClients.java、ServiceEndpoints.java、ServiceClientConfig.java，参考 repair-service，通过注入的 RestClient.Builder 保持 trace 传播。
- src/main/resources/application.yml：下游地址、超时、恢复周期配置；不放密码。
- src/main/resources/db/migration/V1__create_room_booking.sql 起新增迁移；开始前检查远端是否已有版本，已合并脚本不可修改。
- src/test/java/.../room/：BookingApiTest、BookingPermissionTest、ApprovalFlowTest、BookingConcurrencyTest、BookingRecoveryTest、BookingSchemaTest；client/ 下 SeatClientTest、PlatformClientTest。
- docs/requirements/m2-room.md、x-integration.md；docs/specs/m2-room.md；docs/adr/ 下取下一个空闲编号记录一致性决策。
- deploy/compose.yaml：仅按需要补 M2 下游环境变量；由相关负责人评审。

分页实现先检查公共约定；若需要插件/依赖，单独确认配置，不能假设已有。身份适配按 D 契约实现；测试替身仅用于测试，不得留下生产可伪造身份的请求头捷径。

## 9. 实施阶段与 PR 切分

以下按两人兼职约 8–10 个有效工作日估算，并非承诺日期；取决于课程任务书及 M1/D 的接口就绪时间。

| 阶段 | 工作量建议 | 交付与退出条件 |
|---|---|---|
| P0 契约与设计 | 1–2 天，两人 | 正式需求、状态机、接口样例、M1/D 错误语义及 ADR 获评审；课程要求核对完成 |
| P1 数据基础 | 1 天，共同 | 首个迁移、实体、DTO、错误码、测试数据及空库迁移验证；数据库脚本单独 commit |
| P2 双线开发 | 2–3 天，各自并行 | A 完成申请侧；B 完成审批与 HTTP 协议；可用测试替身验证，但不宣称实际联调通过 |
| P3 完整联动 | 2 天，两人 | 真实 M1/D 联调；占用、取消、权限及崩溃恢复验证通过 |
| P4 页面与交付 | 2 天，两人 | 按已确认技术栈接页面；端到端演示、文档、课程验收证据；未选前端则重新估算 |

建议 PR：①需求/设计/ADR；②表结构与共享模型；③申请查询提交；④审批与 M1 占用；⑤取消及恢复；⑥通知审计联调；⑦页面（选型后）。每个 PR 可审查、有行为测试，后续功能依赖已合并的基础 PR。从最新 dev 创建 feat/m2-xxx 分支，功能 PR 目标为 dev；提交用 feat(room)/test(room)/docs 并关联需求；两人互审，dev/main 均不直接 push。发布时由 dev 向 main 提交 PR。

## 10. 测试与验收

- 时间边界：结束等于开始、过去时段、相邻时段、完全包含/部分重叠、跨天、Asia/Shanghai；跨天是否允许按最终规则断言。
- 权限：匿名不可操作；A 不能查询/取消 B 的申请；普通用户不能审批；管理员空间权限及自审限制生效。
- 幂等：重复提交仅一条；同键不同内容拒绝；批准、释放重试不产生额外占用。
- 并发：同申请同时批准/驳回只一方成功；同室重叠申请竞争最多一方占用成功；M1 单座预约与 M2 整室占用同时发生时不双占。
- 故障：M1 未收到请求、已占用但响应丢失、占用成功后 M2 落库失败、取消释放响应丢失、恢复任务执行时服务重启，均能最终恢复一致；下游不可用不让 M2 health 变 DOWN。
- 来源隔离：释放申请 A 不影响申请 B、M1 或 M3；旧请求重放不能复活已释放的占用。
- API：返回 DTO、正确状态码、稳定错误码，异常不泄露内部/个人信息；跨服务 traceId 可关联。
- 集成测试统一使用现有 @IntegrationTest 与真实 MySQL Testcontainers；客户端测试用本地 HTTP 假服务模拟响应与超时，不 mock 被测业务逻辑。
- 本地验证：mvn verify -pl services/room-service -am；迁移检查：bash scripts/ci/check-migrations.sh；全量构建交 CI。
- 联调演示：用户提交 → 管理员批准 → M1 同时段座位不可预约 → 用户取消 → 仅本次占用释放；另演示一次冲突及一次超时恢复。

本轮只生成计划，未运行构建或业务测试，也未开始实现。

## 11. 风险与应对

1. 正式任务书缺失：所有新增业务假设保留草稿，P0 核对后才能作为实现依据。
2. M1/D 未完成：先做契约测试与可替换客户端，但真实联调列为交付门槛。
3. 分布式一致性低估：优先实现最小故障恢复，不先扩展审批层级或展示功能；M1 若无法提供原子幂等占用，需要调整跨组排期。
4. 两人同时改公共文件：基础模型先合并，迁移版本提前分配；按仓库规则遇合并冲突交给人处理。
5. 前端方案未定：由团队补 ADR；完整 UI 工期随后重估。

## 12. 实现偏差记录

待实现后追加实际差异、原因与验证证据。
