package cn.edu.fudan.spacewise.platform.service;

import cn.edu.fudan.spacewise.common.web.ErrorCode;
import org.springframework.http.HttpStatus;

/** platform-service 的业务错误码，前缀 PLATFORM.。错误码是接口契约，发布后不要随意改名。 */
public enum PlatformErrorCode implements ErrorCode {

    BUILDING_NOT_FOUND(HttpStatus.NOT_FOUND),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND),
    SEAT_NOT_FOUND(HttpStatus.NOT_FOUND),
    SPACE_CODE_DUPLICATED(HttpStatus.CONFLICT);

    private final HttpStatus status;

    PlatformErrorCode(HttpStatus status) {
        this.status = status;
    }

    @Override
    public String code() {
        return "PLATFORM." + name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }
}
