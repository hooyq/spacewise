# ADR-0004：数据访问层采用 MyBatis-Plus

- 状态：提议中（待组内确认）
- 日期：2026-10-20
- 决策人：（待填）
- 相关：ADR-0003（MySQL、Flyway）；搭建路线第 6 步

## 背景

各服务的 dao 层需要统一的数据访问技术，否则四个模块组会各用各的，评审和互相支援都困难。
约束：组员对 Spring 的熟悉程度有限；表结构由 Flyway 的手写 SQL 迁移管理（ADR-0003），
不允许 ORM 自动建表或修改表结构；学期内完成。

## 候选方案

### 方案 A：MyBatis-Plus
- 优点：国内课程和项目中最常见，组员和网上资料都熟悉；单表的增删改查由 `BaseMapper` 提供，
  复杂查询手写 SQL，执行的 SQL 一目了然；不管理表结构，和 Flyway 手写迁移的分工清晰。
- 缺点：不是 Spring 官方项目，需要单独管理版本；国际资料相对较少；
  `QueryWrapper` 链式写法用多了，查询逻辑会散落在 service 层。

### 方案 B：Spring Data JPA（Hibernate）
- 优点：Spring 官方方案，版本由 Spring Boot 统一管理；简单查询只需声明方法名。
- 缺点：SQL 由框架生成，懒加载、N+1 查询等问题对初学者不透明；
  `ddl-auto` 会和 Flyway 争夺表结构管理权，必须显式关闭。

### 方案 C：只用 Spring 的 JdbcTemplate
- 优点：没有额外依赖，行为最透明。
- 缺点：每个查询的 SQL 和结果映射都要手写，重复代码多。

<!-- 候选方案由 AI 提出；人接受了 AI 的推荐（A），尚未征求组员意见。 -->

## 决定

选择**方案 A：MyBatis-Plus**（`mybatis-plus-spring-boot3-starter` 3.5.17），版本在根 `pom.xml` 的
`dependencyManagement` 中统一管理。表结构**只能**由 Flyway 迁移修改。

## 依据

关键因素是**组员熟悉**和 **SQL 透明**：与选择 MySQL 的理由一致；手写 SQL 和手写迁移放在一起，
排查问题时看到的就是实际执行的语句。

## 代价与后续

- 约定：复杂查询写在 Mapper 中（注解或 XML），不要在 service 层堆叠很长的 `QueryWrapper`。
- 分页插件、逻辑删除、自动填充等功能，等需要时再统一配置，不要各组各自配置。
- 如果组内多数人更熟悉 JPA，可以在第一次 Review 前推翻本决定；目前只有一个示例 Mapper，改动成本很低。

## 如何达成一致

（待组内讨论后填写）
