package org.cmb.presentation.filter;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceContextFilter extends OncePerRequestFilter {

    private static final Pattern TRACE_ID = Pattern.compile(
            "(?i)([0-9a-f]{32}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"
                    + "[0-9a-f]{4}-[0-9a-f]{12})");
    private static final Pattern PROJECT_PATH = Pattern.compile(
            "/api/v1/projects/(project-[A-Za-z0-9-]+)(?:/|$)");
    private static final Logger LOGGER = LoggerFactory.getLogger(TraceContextFilter.class);
    private final Tracer tracer = GlobalOpenTelemetry.getTracer("org.cmb");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = traceId(request);
        String projectId = projectId(request);
        Span span = tracer.spanBuilder(request.getMethod() + " " + request.getRequestURI()).startSpan();

        try (Scope ignored = span.makeCurrent()) {
            MDC.put("trace_id", traceId);
            MDC.put("project_id", projectId);
            response.setHeader("X-Trace-Id", traceId);
            filterChain.doFilter(request, response);
            LOGGER.info(
                    "request_completed method={} path={} status={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus());
        } finally {
            MDC.remove("trace_id");
            MDC.remove("project_id");
            span.end();
        }
    }

    private String traceId(HttpServletRequest request) {
        String value = request.getHeader("X-Trace-Id");
        return value != null && TRACE_ID.matcher(value).matches()
                ? value : UUID.randomUUID().toString();
    }

    private String projectId(HttpServletRequest request) {
        Matcher matcher = PROJECT_PATH.matcher(request.getRequestURI());
        return matcher.find() ? matcher.group(1) : "";
    }
}
