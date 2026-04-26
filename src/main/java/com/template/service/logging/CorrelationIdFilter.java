package com.template.service.logging;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_MDC_KEY = "correlation_id";
    public static final String HTTP_METHOD_MDC_KEY = "http_method";
    public static final String REQUEST_PATH_MDC_KEY = "request_path";

    private final String correlationIdHeader;

    public CorrelationIdFilter(
            @Value("${app.logging.correlation-id-header:X-Correlation-Id}") String correlationIdHeader) {
        this.correlationIdHeader = correlationIdHeader;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = Optional.ofNullable(request.getHeader(this.correlationIdHeader))
                .filter(value -> !value.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());

        response.setHeader(this.correlationIdHeader, correlationId);
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        MDC.put(HTTP_METHOD_MDC_KEY, request.getMethod());
        MDC.put(REQUEST_PATH_MDC_KEY, request.getRequestURI());

        try {
            filterChain.doFilter(request, response);
        }
        finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
            MDC.remove(HTTP_METHOD_MDC_KEY);
            MDC.remove(REQUEST_PATH_MDC_KEY);
        }
    }
}
