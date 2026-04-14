package com.java.ecommerce.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RequestRateLimiter requestRateLimiter;
    private final SecurityProperties securityProperties;

    public RateLimitFilter(RequestRateLimiter requestRateLimiter, SecurityProperties securityProperties) {
        this.requestRateLimiter = requestRateLimiter;
        this.securityProperties = securityProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        RequestRateLimiter.Decision decision = resolveDecision(request);

        if (decision != null && !decision.allowed()) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));

            response.getWriter().write("""
                    {
                        "timestamp": "%s",
                        "status": 429,
                        "error": "Too Many Requests",
                        "message": "Rate limit exceeded. Retry later.",
                        "retryAfterSeconds": %d,
                        "path": "%s"
                    }
                    """.formatted(
                    OffsetDateTime.now(),
                    decision.retryAfterSeconds(),
                    escape(request.getRequestURI())));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private RequestRateLimiter.Decision resolveDecision(HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        String method = request.getMethod();
        String clientIp = resolveClientIp(request);

        if (HttpMethod.POST.matches(method) && "/api/auth/login".equals(requestPath)) {
            return requestRateLimiter.tryConsume(
                    "login:" + clientIp,
                    securityProperties.getRateLimit().getLoginPerMinute(),
                    60L);
        }

        if (isWriteApiRequest(method, requestPath)) {
            return requestRateLimiter.tryConsume(
                    "write:" + clientIp,
                    securityProperties.getRateLimit().getWritePerMinute(),
                    60L);
        }

        return null;
    }

    private boolean isWriteApiRequest(String method, String path) {
        if (path == null || !path.startsWith("/api/")) {
            return false;
        }

        if ("/api/auth/login".equals(path)) {
            return false;
        }

        return HttpMethod.POST.matches(method)
                || HttpMethod.PUT.matches(method)
                || HttpMethod.PATCH.matches(method)
                || HttpMethod.DELETE.matches(method);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
