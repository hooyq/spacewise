package cn.edu.fudan.spacewise.common.web;

/**
 * 业务代码表达“这个请求不能完成”的唯一方式，例如 {@code throw new BusinessException(SeatErrorCode.ROOM_NOT_FOUND, "教室 H9999 不存在")}。
 * 由 {@link GlobalExceptionHandler} 转成对应状态码的错误响应。detail 会返回给调用方，不要放内部信息或个人信息。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
