package cn.edu.fudan.spacewise.common.web;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ProblemDetail;

/**
 * 生成统一格式的错误响应：标准 ProblemDetail（RFC 9457）加上 {@code code} 和 {@code traceId} 两个字段。
 * 一般不需要直接使用——抛 {@link BusinessException} 即可；只有自己写异常处理器时（如网关）才用它。
 */
public class ErrorResponses {

    private final ObjectProvider<Tracer> tracer;

    public ErrorResponses(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer;
    }

    public ProblemDetail of(ErrorCode errorCode, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
        return decorate(problem, errorCode);
    }

    /** 给已有的 ProblemDetail（如 Spring 为标准异常生成的）补上 code 和 traceId。 */
    public ProblemDetail decorate(ProblemDetail problem, ErrorCode errorCode) {
        problem.setProperty("code", errorCode.code());
        String traceId = currentTraceId();
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        return problem;
    }

    public String currentTraceId() {
        Tracer current = tracer.getIfAvailable();
        Span span = current == null ? null : current.currentSpan();
        return span == null ? null : span.context().traceId();
    }
}
