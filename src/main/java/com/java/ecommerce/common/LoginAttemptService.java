package com.java.ecommerce.common;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private final SecurityProperties securityProperties;
    private final ConcurrentHashMap<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public Optional<Long> getRetryAfterSeconds(String key) {
        long now = Instant.now().getEpochSecond();
        AttemptState state = attempts.get(key);
        if (state == null) {
            return Optional.empty();
        }

        synchronized (state) {
            if (state.blockedUntilEpochSecond <= now) {
                if (state.failures == 0) {
                    attempts.remove(key, state);
                }
                return Optional.empty();
            }

            long retryAfter = Math.max(1L, state.blockedUntilEpochSecond - now);
            return Optional.of(retryAfter);
        }
    }

    public void recordFailure(String key) {
        long now = Instant.now().getEpochSecond();
        AttemptState state = attempts.computeIfAbsent(key, ignored -> new AttemptState(now));

        synchronized (state) {
            if (state.blockedUntilEpochSecond > now) {
                return;
            }

            long failureWindow = securityProperties.getLoginThrottle().getFailureWindowSeconds();
            if ((now - state.firstFailureEpochSecond) >= failureWindow) {
                state.failures = 0;
                state.firstFailureEpochSecond = now;
            }

            state.failures++;

            if (state.failures >= securityProperties.getLoginThrottle().getMaxFailures()) {
                state.failures = 0;
                state.firstFailureEpochSecond = now;
                state.blockedUntilEpochSecond = now + securityProperties.getLoginThrottle().getBlockDurationSeconds();
            }
        }
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    @Scheduled(fixedRate = 300_000)
    void evictStaleEntries() {
        long now = Instant.now().getEpochSecond();
        long failureWindow = securityProperties.getLoginThrottle().getFailureWindowSeconds();

        attempts.entrySet().removeIf(entry -> {
            AttemptState state = entry.getValue();
            synchronized (state) {
                boolean blockExpired = state.blockedUntilEpochSecond <= now;
                boolean windowExpired = (now - state.firstFailureEpochSecond) >= failureWindow;
                return blockExpired && (state.failures == 0 || windowExpired);
            }
        });
    }

    private static final class AttemptState {
        private int failures;
        private long firstFailureEpochSecond;
        private long blockedUntilEpochSecond;

        private AttemptState(long now) {
            this.failures = 0;
            this.firstFailureEpochSecond = now;
            this.blockedUntilEpochSecond = 0L;
        }
    }
}

