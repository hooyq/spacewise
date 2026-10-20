package cn.edu.fudan.spacewise.common.web;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 所有服务共用的异常处理：把异常统一转成带 code 和 traceId 的 ProblemDetail。
 *
 * <ul>
 *   <li>父类 ResponseEntityExceptionHandler 已能处理 Spring MVC 的标准异常（404、405、请求体格式错误等），这里只补上 code。</li>
 *   <li>未预料的异常一律返回 500 和固定文案，不把异常信息返回给调用方（可能包含 SQL、表名、内部地址），完整堆栈只写日志。</li>
 *   <li>优先级最低：服务自己的 @RestControllerAdvice（如网关的 DownstreamErrorHandler）先匹配，否则会被这里的兜底处理抢走。</li>
 * </ul>
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ErrorResponses errorResponses;

    public GlobalExceptionHandler(ErrorResponses errorResponses) {
        this.errorResponses = errorResponses;
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ProblemDetail> handleBusiness(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.status()).body(errorResponses.of(errorCode, e.getMessage()));
    }

    /** service 层用 @Validated 做方法参数校验时抛出。 */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException e) {
        List<Map<String, String>> errors = e.getConstraintViolations().stream()
                .map(v -> fieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return ResponseEntity.badRequest().body(validationFailed(errors));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
        log.error("未处理的异常", e);
        return ResponseEntity.internalServerError()
                .body(errorResponses.of(CommonErrorCode.INTERNAL_ERROR, "服务器内部错误"));
    }

    /** 请求体（@Valid @RequestBody）校验失败。 */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> fieldError(f.getField(), f.getDefaultMessage()))
                .toList();
        return handleExceptionInternal(e, validationFailed(errors), headers, status, request);
    }

    /** 路径变量、请求参数上的校验注解（如 @Pattern）不满足。 */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> fieldError(result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return handleExceptionInternal(e, validationFailed(errors), headers, status, request);
    }

    /** 路径不存在。Spring 默认文案是 “No static resource xxx.”，会让调用方误以为在请求静态文件。 */
    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = e.getBody();
        problem.setDetail("请求的路径不存在");
        return handleExceptionInternal(e, problem, headers, status, request);
    }

    /** 父类处理的所有标准异常最终都经过这里：统一补上 code 和 traceId。 */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (body == null && e instanceof ErrorResponse errorResponse) {
            body = errorResponse.getBody();
        }
        if (body instanceof ProblemDetail problem && !hasCode(problem)) {
            errorResponses.decorate(problem, CommonErrorCode.fromStatus(status.value()));
        }
        return super.handleExceptionInternal(e, body, headers, status, request);
    }

    private static boolean hasCode(ProblemDetail problem) {
        return problem.getProperties() != null && problem.getProperties().containsKey("code");
    }

    private ProblemDetail validationFailed(List<Map<String, String>> errors) {
        ProblemDetail problem = errorResponses.of(CommonErrorCode.VALIDATION_FAILED, "请求参数校验失败");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", String.valueOf(field), "message", String.valueOf(message));
    }
}
