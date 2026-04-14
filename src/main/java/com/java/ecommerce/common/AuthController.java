package com.java.ecommerce.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService,
            LoginAttemptService loginAttemptService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String throttleKey = buildThrottleKey(request.username(), resolveClientIp(httpRequest));

        Optional<Long> retryAfter = loginAttemptService.getRetryAfterSeconds(throttleKey);
        if (retryAfter.isPresent()) {
            return tooManyAttempts(retryAfter.get(), httpRequest.getRequestURI());
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));

            Collection<String> roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(authority -> authority.startsWith("ROLE_") ? authority.substring(5) : authority)
                    .toList();

            long expiresInSeconds = jwtService.getTokenTtlSeconds();
            long expiresAtEpochMs = Instant.now().plusSeconds(expiresInSeconds).toEpochMilli();
            String accessToken = jwtService.issueToken(authentication.getName(), roles);

            loginAttemptService.recordSuccess(throttleKey);

            return ResponseEntity.ok(new LoginResponse(accessToken, "Bearer", expiresInSeconds, expiresAtEpochMs));
        } catch (AuthenticationException ex) {
            loginAttemptService.recordFailure(throttleKey);
            Optional<Long> blockedRetryAfter = loginAttemptService.getRetryAfterSeconds(throttleKey);
            if (blockedRetryAfter.isPresent()) {
                return tooManyAttempts(blockedRetryAfter.get(), httpRequest.getRequestURI());
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody(HttpStatus.UNAUTHORIZED, "Invalid username or password.",
                            httpRequest.getRequestURI()));
        }
    }

    private ResponseEntity<Map<String, Object>> tooManyAttempts(long retryAfterSeconds, String path) {
        Map<String, Object> body = errorBody(HttpStatus.TOO_MANY_REQUESTS,
                "Too many failed login attempts. Try again later.", path);
        body.put("retryAfterSeconds", retryAfterSeconds);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(retryAfterSeconds))
                .body(body);
    }

    private Map<String, Object> errorBody(HttpStatus status, String message, String path) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", path);
        return body;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardForIsBlank(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean forwardForIsBlank(String forwardedFor) {
        return forwardedFor.trim().isEmpty();
    }

    private String buildThrottleKey(String username, String clientIp) {
        return username.toLowerCase(Locale.ROOT).trim() + "|" + clientIp;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds, long expiresAtEpochMs) {
    }
}
