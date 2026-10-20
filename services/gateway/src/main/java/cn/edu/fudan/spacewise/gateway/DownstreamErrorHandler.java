package cn.edu.fudan.spacewise.gateway;

import cn.edu.fudan.spacewise.common.web.ErrorCode;
import cn.edu.fudan.spacewise.common.web.ErrorResponses;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

/**
 * 转发失败时返回能说明原因的状态码，而不是笼统的 500（500 看起来像网关自己出错）：
 * 下游超时 504 Gateway Timeout；下游连不上 502 Bad Gateway。格式与各服务一致（web-common）。
 * 优先级高于 web-common 的 GlobalExceptionHandler，否则会被它的兜底处理抢走。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class DownstreamErrorHandler {

    enum GatewayErrorCode implements ErrorCode {
        DOWNSTREAM_UNAVAILABLE(HttpStatus.BAD_GATEWAY),
        DOWNSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT);

        private final HttpStatus status;

        GatewayErrorCode(HttpStatus status) {
            this.status = status;
        }

        @Override
        public String code() {
            return "GATEWAY." + name();
        }

        @Override
        public HttpStatus status() {
            return status;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(DownstreamErrorHandler.class);

    private final ErrorResponses errorResponses;

    DownstreamErrorHandler(ErrorResponses errorResponses) {
        this.errorResponses = errorResponses;
    }

    @ExceptionHandler(ResourceAccessException.class)
    ResponseEntity<ProblemDetail> downstreamUnavailable(ResourceAccessException e) {
        GatewayErrorCode errorCode = isTimeout(e) ? GatewayErrorCode.DOWNSTREAM_TIMEOUT : GatewayErrorCode.DOWNSTREAM_UNAVAILABLE;
        log.warn("转发失败，返回 {}: {}", errorCode.status().value(), e.getMessage());
        return ResponseEntity.status(errorCode.status()).body(errorResponses.of(errorCode, "下游服务暂时不可用"));
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
