package cn.edu.fudan.spacewise.common.web;

import org.springframework.http.HttpStatus;

/** 与具体业务无关的通用错误码，由 {@link GlobalExceptionHandler} 使用。 */
public enum CommonErrorCode implements ErrorCode {

    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    CONFLICT(HttpStatus.CONFLICT),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus status;

    CommonErrorCode(HttpStatus status) {
        this.status = status;
    }

    @Override
    public String code() {
        return "COMMON." + name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    /** Spring 标准异常只给出状态码，这里换算成通用错误码；没有对应项的按 4xx/5xx 归类。 */
    static CommonErrorCode fromStatus(int status) {
        for (CommonErrorCode code : values()) {
            if (code != VALIDATION_FAILED && code.status.value() == status) {
                return code;
            }
        }
        return status >= 500 ? INTERNAL_ERROR : BAD_REQUEST;
    }
}
