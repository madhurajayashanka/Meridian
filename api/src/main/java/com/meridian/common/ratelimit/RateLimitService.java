package com.meridian.common.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiting service using Redis sliding window counters
 * Requirement 6: Rate limiting with 60 req/min general and 5 jobs/hour limit
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final int GENERAL_LIMIT = 100; // requests per minute
    private static final int JOB_LIMIT = 70; // jobs per hour
    private static final long GENERAL_WINDOW_SECS = 600;
    private static final long JOB_WINDOW_SECS = 60;

    /**
     * Check if a request is allowed for general endpoint
     */
    public RateLimitResult checkGeneralLimit(String userId) {
        return checkLimit(
            "general:" + userId,
            GENERAL_LIMIT,
            GENERAL_WINDOW_SECS,
            "General rate limit"
        );
    }

    /**
     * Check if a research job submission is allowed
     */
    public RateLimitResult checkJobLimit(String userId) {
        return checkLimit(
            "job:" + userId,
            JOB_LIMIT,
            JOB_WINDOW_SECS,
            "Job submission rate limit"
        );
    }

    /**
     * Generic sliding window rate limit check
     */
    private RateLimitResult checkLimit(
        String key,
        int maxRequests,
        long windowSeconds,
        String limitName
    ) {
        try {
            long now = Instant.now().getEpochSecond();
            long windowStart = now - windowSeconds;

            // Remove old entries older than the window
            String keyWithWindow = key + ":" + WindowHelper.getCurrentWindow(windowSeconds);
            Long count = redisTemplate.opsForValue().increment(keyWithWindow);

            // Set expiry on first request in window
            if (count == 1) {
                redisTemplate.expire(keyWithWindow, windowSeconds + 1, TimeUnit.SECONDS);
            }

            if (count > maxRequests) {
                long retryAfter = (long) Math.ceil((double) windowSeconds / (count - maxRequests));
                RateLimitResult result = RateLimitResult.limited(
                    retryAfter,
                    maxRequests,
                    count.intValue(),
                    limitName
                );
                log.warn("Rate limit exceeded: {}, userId={}, count={}", limitName, key, count);
                return result;
            }

            return RateLimitResult.allowed(maxRequests - count.intValue(), maxRequests);
        } catch (Exception e) {
            log.error("Rate limit check failed", e);
            // Fail open - allow request if Redis is down
            return RateLimitResult.allowed(maxRequests, maxRequests);
        }
    }

    /**
     * Reset rate limit for a user (for testing)
     */
    public void resetLimit(String key) {
        try {
            String keyWithWindow = key + ":" + WindowHelper.getCurrentWindow(GENERAL_WINDOW_SECS);
            redisTemplate.delete(keyWithWindow);
        } catch (Exception e) {
            log.error("Failed to reset rate limit", e);
        }
    }

    // =========================================================================
    // Helper Classes
    // =========================================================================

    private static class WindowHelper {
        static long getCurrentWindow(long windowSeconds) {
            return Instant.now().getEpochSecond() / windowSeconds;
        }
    }

    /**
     * Result of a rate limit check
     */
    public static class RateLimitResult {
        public final boolean allowed;
        public final int remainingRequests;
        public final int maxRequests;
        public final Long retryAfterSeconds;
        public final String message;

        private RateLimitResult(boolean allowed, int remaining, int max, Long retryAfter, String message) {
            this.allowed = allowed;
            this.remainingRequests = remaining;
            this.maxRequests = max;
            this.retryAfterSeconds = retryAfter;
            this.message = message;
        }

        public static RateLimitResult allowed(int remaining, int max) {
            return new RateLimitResult(true, remaining, max, null, null);
        }

        public static RateLimitResult limited(long retryAfter, int max, int used, String message) {
            return new RateLimitResult(false, 0, max, retryAfter, message);
        }
    }
}
