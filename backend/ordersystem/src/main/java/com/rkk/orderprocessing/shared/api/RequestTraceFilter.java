package com.rkk.orderprocessing.shared.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns one opaque correlation value to every request before controller or exception handling.
 * The value is stored as a request attribute for error serialization and in MDC for structured
 * application logs; caller-supplied values are deliberately not trusted.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    static final String TRACE_ID_ATTRIBUTE =
            RequestTraceFilter.class.getName() + ".traceId";
    private static final String MDC_KEY = "traceId";

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestTraceFilter.class);

    /** Establishes request-scoped trace context and always restores the worker thread's MDC. */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = getOrCreateTraceId(request);
        String previousMdcValue = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMillis = (System.nanoTime() - startedAt) / 1_000_000;
            LOGGER.info(
                    "HTTP request completed traceId={} method={} path={} status={} durationMs={}",
                    traceId,
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMillis);
            if (previousMdcValue == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previousMdcValue);
            }
        }
    }

    /** Returns the existing server trace ID or installs a new opaque request value. */
    static String getOrCreateTraceId(HttpServletRequest request) {
        Object existing = request.getAttribute(TRACE_ID_ATTRIBUTE);
        if (existing instanceof String value && !value.isBlank()) {
            return value;
        }
        String generated = UUID.randomUUID().toString();
        request.setAttribute(TRACE_ID_ATTRIBUTE, generated);
        return generated;
    }
}
