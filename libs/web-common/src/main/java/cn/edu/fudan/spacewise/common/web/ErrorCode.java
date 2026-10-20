package cn.edu.fudan.spacewise.common.web;

import org.springframework.http.HttpStatus;

/**
 * 错误码。每个服务用一个枚举实现本接口，code 带模块前缀，如 {@code SEAT.ROOM_NOT_FOUND}，避免各服务重名。
 * 错误码是接口契约的一部分：前端和调用方会按它判断错误类型，发布后不要随意改名。
 */
public interface ErrorCode {

    /** 形如 {@code 模块.错误名}，全大写下划线。 */
    String code();

    /** 对应的 HTTP 状态码。 */
    HttpStatus status();
}
