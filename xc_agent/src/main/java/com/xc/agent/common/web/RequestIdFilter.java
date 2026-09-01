package com.xc.agent.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Slf4j
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Request-Id";
    public static final String ATTRIBUTE_NAME = RequestIdFilter.class.getName() + ".requestId";
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String candidate = request.getHeader(HEADER_NAME);
        String requestId = candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()
                ? candidate : "req_" + UUID.randomUUID().toString().replace("-", "");
        request.setAttribute(ATTRIBUTE_NAME, requestId);
        response.setHeader(HEADER_NAME, requestId);
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (request.getRequestURI().startsWith("/api/")) {
                long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
                int status = response.getStatus();
                if (status >= 500) {
                    log.error("API {} {} -> {} ({} ms), requestId={}",
                            request.getMethod(), request.getRequestURI(), status, elapsedMillis, requestId);
                } else if (status >= 400) {
                    log.warn("API {} {} -> {} ({} ms), requestId={}",
                            request.getMethod(), request.getRequestURI(), status, elapsedMillis, requestId);
                } else {
                    log.info("API {} {} -> {} ({} ms), requestId={}",
                            request.getMethod(), request.getRequestURI(), status, elapsedMillis, requestId);
                }
            }
        }
    }

    public static String get(HttpServletRequest request) {
        Object requestId = request.getAttribute(ATTRIBUTE_NAME);
        return requestId == null ? "req_unknown" : requestId.toString();
    }
}
