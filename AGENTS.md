# Spacewise · 智空间 校园资源服务平台

本文件是给 AI 编程助手（Claude Code / Codex / Qoder 等）的项目规则，人也应当遵守。
保持在一页以内：只写“AI 不知道就会犯错”的事。发现 AI 反复犯同一个错，就把它加进来。

## 项目是什么
复旦软件过程管理课程 PJ。以自习座位预约为核心，延伸到整教室预约审批和物业报修。

| 模块 | 名称 | 服务目录（规划中） |
|---|---|---|
| M1 | 自习座位预约 | `services/seat-service` |
| M2 | 整教室预约与审批 | `services/room-service` |
| M3 | 物业报修工单 | `services/repair-service` |
| D  | 公共层：RBAC、空间主数据、通知、审计 | `services/platform-service` |

跨模块写回：M3 维修中 → M1 下架该室座位；M2 审批通过 → M1 占用该室时段座位。
M1 的座位状态必须记录写入来源（M1 / M2 / M3）。

## 技术栈
Java 21 · Spring Boot · Maven · Flyway · Docker Compose · GitHub Actions。
数据库、前端框架见 `docs/adr/`（未决定前不要擅自引入）。

## 红线（不得违反）
1. **已合并的 Flyway 迁移脚本绝不修改**，只新增版本号。
2. **服务之间只通过 HTTP 接口通信**，禁止读写其他服务的 schema 或表。
3. **不提交 `.env`、密钥、证书**；配置示例放 `*.example.env`。
4. **不自行解决合并冲突**，遇到冲突停下来交给人。
5. **不引入新的基础设施组件**（注册中心、MQ、缓存等），除非已有对应 ADR。
6. **测试必须验证行为**：禁止断言常量、禁止 mock 被测逻辑本身、禁止为了通过而改测试。修 bug 先写一个会失败的测试。

## 工作流程
需求 `docs/requirements/` → 设计 `docs/specs/` → 计划 `docs/plans/YYYY-MM-DD-name.md` → 实现 → PR。
- 非琐碎任务：先出计划，人确认后再写代码。计划要具体到“没看过对话的人也能照着实现”。
- 重要取舍写 ADR（`docs/adr/NNNN-name.md`，模板见 `0000-template.md`），列出候选方案而不是只写结论。
- 一次改动只做一件事，保持 diff 可审查；不顺手重构无关代码。

## Git 约定
- 主干开发：`feat/<简述>`、`fix/<简述>` → PR → `main`。`main` 受保护，不能直接 push。
- 提交信息用 Conventional Commits：`feat(seat): ...`、`fix(repair): ...`、`docs: ...`、`ci: ...`。
  scope 用模块名：`seat` `room` `repair` `platform` `frontend` `deploy`。
- 提交说明写清做了什么、为什么，关联需求编号（如 `Refs: REQ-M1-003`）。
- 数据库迁移单独 commit，不和业务代码混在一起。
- 发布：在 `main` 上打 tag `vX.Y.Z` → 人工审批 → 部署生产。

## 常用命令
（随骨架搭建补充）
