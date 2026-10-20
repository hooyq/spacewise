package cn.edu.fudan.spacewise.gateway;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 每个经过网关的请求：在响应头返回 X-Trace-Id，并记一行访问日志（方法、路径、状态码、耗时）。
 * 拿到 X-Trace-Id 后，用 {@code docker compose logs | grep <traceId>} 可以找到这次请求在所有服务里的日志。
 * 只记录路径，不记录请求体和请求头，避免把 token、个人信息写进日志。
 */
@Component
class AccessLogFilter extends OncePerRequestFilter {

    static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

    private final Tracer tracer;

    AccessLogFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        Span span = tracer.currentSpan();
        if (span != null) {
            // 必须在转发之前设置：响应一旦开始写回，就不能再加响应头
            response.setHeader(TRACE_ID_HEADER, span.context().traceId());
        }
        try {
            chain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - start) / 1_000_000;
            log.info("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(), response.getStatus(), millis);
        }
    }
}
