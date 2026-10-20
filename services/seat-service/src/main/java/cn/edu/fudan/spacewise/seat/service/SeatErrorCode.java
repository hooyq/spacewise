package cn.edu.fudan.spacewise.seat.service;

import cn.edu.fudan.spacewise.common.web.ErrorCode;
import org.springframework.http.HttpStatus;

/** seat-service 的业务错误码，前缀 SEAT.。错误码是接口契约，发布后不要随意改名。 */
public enum SeatErrorCode implements ErrorCode {

    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND);

    private final HttpStatus status;

    SeatErrorCode(HttpStatus status) {
        this.status = status;
    }

    @Override
    public String code() {
        return "SEAT." + name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }
}
