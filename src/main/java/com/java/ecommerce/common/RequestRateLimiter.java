package com.java.ecommerce.common;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RequestRateLimiter {

    private static final long DEFAULT_WINDOW_SECONDS = 60L;

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public Decision tryConsume(String key, int limitPerWindow, long windowSeconds) {
        long now = Instant.now().getEpochSecond();
        WindowCounter counter = counters.computeIfAbsent(key, ignored -> new WindowCounter(now));

        synchronized (counter) {
            long elapsed = now - counter.windowStartEpochSecond;
            if (elapsed >= windowSeconds) {
                counter.windowStartEpochSecond = now;
                counter.count = 0;
            }

            if (counter.count < limitPerWindow) {
                counter.count++;
                return new Decision(true, 0L);
            }

            long retryAfter = Math.max(1L, windowSeconds - (now - counter.windowStartEpochSecond));
            return new Decision(false, retryAfter);
        }
    }

    @Scheduled(fixedRate = 120_000)
    void evictStaleCounters() {
        long now = Instant.now().getEpochSecond();
        counters.entrySet().removeIf(entry -> {
            WindowCounter counter = entry.getValue();
            synchronized (counter) {
                return (now - counter.windowStartEpochSecond) >= DEFAULT_WINDOW_SECONDS * 2;
            }
        });
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    private static final class WindowCounter {
        private long windowStartEpochSecond;
        private int count;

        private WindowCounter(long windowStartEpochSecond) {
            this.windowStartEpochSecond = windowStartEpochSecond;
            this.count = 0;
        }
    }
}

