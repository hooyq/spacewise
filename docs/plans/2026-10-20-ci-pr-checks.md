# 计划：CI 质量门禁（搭建路线第 4 步）

- 日期：2026-10-20
- 关联：搭建路线第 4 步；AGENTS.md 红线 1、6；任务书“测试不过不能合并”
- 状态：已实现，待 PR 评审

## 目标

PR 和 main 的每次提交都自动构建测试；修改已合并的迁移、迁移版本号重复、迁移文件命名不合规时，CI 失败；
分支保护要求这些检查通过、分支与 main 同步后才能合并。同时让集成测试共用一个 MySQL 容器，缩短测试时间。

## 范围

做：
- `.github/workflows/ci.yml`：
  - job `build`：PR 和 push main 时运行 `mvn -B verify`；测试失败时上传测试报告。
  - job `migration-guard`：仅 PR 时运行，执行迁移检查脚本。
- `scripts/ci/check-migrations.sh`：
  - a）相对目标分支，迁移目录只允许新增文件（修改、删除、重命名都失败）；
  - b）同一服务内版本号不得重复；
  - c）文件名必须符合 `V<整数>__<小写下划线描述>.sql`。Flyway 会**静默忽略**命名不合规的文件，迁移不会执行，也不会报错。
- 测试：新增 `@IntegrationTest` 组合注解，现有两个测试类改用它，共用同一个 Spring 上下文和同一个 MySQL 容器。
- 分支保护：`build`、`migration-guard` 设为 required，开启“合并前必须与 main 同步”（strict）。
- AGENTS.md：迁移命名规则、集成测试写法、action 版本锁定规则。

不做：
- CI 中构建 Docker 镜像（第 6 步）。
- 按路径只构建受影响的服务（出现第二个服务时再做）。

## 设计要点

| 问题 | 做法 |
|---|---|
| action 版本 | 官方 action 用主版本标签（checkout@v7、setup-java@v6、upload-artifact@v7）；第三方 action 一律锁 commit SHA |
| 依赖缓存 | `setup-java` 的 `cache: maven` |
| 权限 | workflow 级别设为 `contents: read`（最小权限） |
| 重复运行 | `concurrency` 按分支分组，同一分支有新提交时取消旧的运行 |
| 迁移检查的比较基准 | `git diff origin/<目标分支>...HEAD`（三个点：只比较 PR 自身的改动）；checkout 时使用 `fetch-depth: 0` |
| 重命名 | `--no-renames`，把重命名拆成“删除 + 新增”，按删除拦截 |

## 验收标准

1. 本 PR 上两个检查都显示 ✅。
2. 本地 `mvn verify` 只启动 1 个 MySQL 容器，耗时明显下降（记录前后对比）。
3. 迁移检查脚本的故意破坏（在本地临时提交上验证，验证后丢弃）：
   - 修改 `V1__create_room.sql` → 失败；
   - 新增 `V1__dup.sql` → 失败（重复版本号）；
   - 新增 `v2_bad_name.sql` → 失败（命名不合规）；
   - 新增合规的 `V2__xxx.sql` → 通过。
4. 在 CI 上验证门禁：推一个让测试失败的提交，确认 `build` 变红、合并按钮被阻止，再撤销这个提交。
5. 分支保护设置完成后，用 `gh api` 读回配置确认。

## 风险

- 迁移检查依赖“分支与 main 同步”才能发现并行 PR 的版本号冲突，strict 模式要求合并前更新分支，会多一些操作。
- Testcontainers 在 GitHub runner 上首次拉取镜像约需几十秒。

## 实现偏差记录

- 5 项验收全部通过：
  - CI 首次运行：`build` 49s（Linux runner 上 Testcontainers 比本机快），`migration-guard` 4s；
  - 本地 `mvn verify`：MySQL 容器从 2 个降到 1 个，耗时从约 176s 降到 84s；
  - 迁移检查脚本的 6 个场景（无改动、改 V1、删 V1、重复版本号、命名不合规、合规新增）结果全部符合预期；
  - 门禁验证：推送“只暴露 info 端点”的破坏性提交后，`build` 失败（`expected: 200 OK but was: 404`），
    测试报告作为 artifact 上传，PR 状态为 `BLOCKED`；revert 后两项检查通过，状态变为 `CLEAN`。
    破坏提交和 revert 都保留在历史中，作为验证记录。
- 补充：required checks 绑定了 GitHub Actions 的 `app_id: 15368`，防止其他应用发出同名状态冒充检查通过。
- 过程问题：验证门禁时，AI 尝试直接执行 `gh pr merge` 来证明合并会被拒绝，被 Claude Code 的权限机制拦下。
  这个做法不对：如果门禁没生效，坏代码就真的进了 main，而且合并应当由人操作。`mergeStateStatus=BLOCKED` 已足以证明，
  以后验证门禁不要用真实合并。
