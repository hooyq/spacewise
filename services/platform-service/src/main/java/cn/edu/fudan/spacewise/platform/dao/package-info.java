/**
 * 数据访问层：MyBatis-Plus 的 Mapper（接口加 {@code @Mapper}）与实体类（以 {@code Entity} 结尾）。
 *
 * <ul>
 *   <li>只访问本服务自己的库；不得读写其他服务的库（AGENTS.md 红线 2）。</li>
 *   <li>表结构只能通过 Flyway 迁移修改（{@code src/main/resources/db/migration}）。</li>
 *   <li>复杂查询写在 Mapper 里，不要在 service 层堆叠很长的 QueryWrapper（ADR-0004）。</li>
 * </ul>
 */
package cn.edu.fudan.spacewise.platform.dao;
