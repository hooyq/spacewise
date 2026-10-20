/**
 * 对外提供的 HTTP 接口层：Controller 放在本包，请求/响应对象放在 {@code api.dto}。
 *
 * <ul>
 *   <li>只调用 {@code service} 层；不得直接调用 {@code dao} 的 Mapper 或 {@code client}（可以引用 service 返回的实体类型，用于转换成 dto）。</li>
 *   <li>返回值必须是 dto，不得返回 {@code dao} 中的实体类（表结构变化不应直接改变接口）。</li>
 *   <li>路径：对外接口 {@code /api/<模块>/...}，服务间内部接口 {@code /internal/...}。</li>
 * </ul>
 */
package cn.edu.fudan.spacewise.repair.api;
