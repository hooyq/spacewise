/**
 * 调用其他服务的 HTTP 接口声明（{@code @HttpExchange}）及其响应对象。
 *
 * <ul>
 *   <li>调用方只声明自己用到的接口和字段，不引用对方服务的代码。</li>
 *   <li>客户端实例在 {@code config} 中创建，地址和超时来自 {@code spacewise.services.<名称>} 配置。</li>
 *   <li>出错或超时会抛出异常；是否重试、如何补偿由业务决定，框架不自动重试。</li>
 * </ul>
 */
package cn.edu.fudan.spacewise.repair.client;
