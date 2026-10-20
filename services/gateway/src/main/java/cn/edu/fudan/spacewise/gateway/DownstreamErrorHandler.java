package cn.edu.fudan.spacewise.gateway;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

/**
 * 转发失败时返回能说明原因的状态码，而不是笼统的 500（500 看起来像网关自己出错）：
 * 下游超时 504 Gateway Timeout；下游连不上 502 Bad Gateway。
 */
@RestControllerAdvice
class DownstreamErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(DownstreamErrorHandler.class);

    @ExceptionHandler(ResourceAccessException.class)
    ProblemDetail downstreamUnavailable(ResourceAccessException e) {
        HttpStatus status = isTimeout(e) ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY;
        log.warn("转发失败，返回 {}: {}", status.value(), e.getMessage());
        return ProblemDetail.forStatusAndDetail(status, "下游服务暂时不可用");
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
