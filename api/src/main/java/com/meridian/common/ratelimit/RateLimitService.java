package com.meridian.common.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiting using Redis fixed-window counters.
 * Limits are driven by application.yml / env vars — no hardcoded values.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.rate-limit.general-requests-per-minute:60}")
    private int generalLimit;

    @Value("${app.rate-limit.job-submissions-per-hour:5}")
    private int jobLimit;

    private static final long GENERAL_WINDOW_SECS = 60;
    private static final long JOB_WINDOW_SECS     = 3600;

    public RateLimitResult checkGeneralLimit(String userId) {
        return checkLimit("rl:general:" + userId, generalLimit, GENERAL_WINDOW_SECS);
    }

    public RateLimitResult checkJobLimit(String userId) {
        return checkLimit("rl:job:" + userId, jobLimit, JOB_WINDOW_SECS);
    }

    private RateLimitResult checkLimit(String key, int max, long windowSecs) {
        try {
            long window = Instant.now().getEpochSecond() / windowSecs;
            String windowKey = key + ":" + window;
            Long count = redisTemplate.opsForValue().increment(windowKey);
            if (count == 1) {
                redisTemplate.expire(windowKey, windowSecs + 1, TimeUnit.SECONDS);
            }
            if (count > max) {
                long retryAfter = windowSecs - (Instant.now().getEpochSecond() % windowSecs);
                log.warn("Rate limit exceeded key={} count={}/{}", key, count, max);
                return RateLimitResult.limited(retryAfter, max);
            }
            return RateLimitResult.allowed(max - count.intValue(), max);
        } catch (Exception e) {
            log.error("Rate limit check failed for {}: {}", key, e.getMessage());
            return RateLimitResult.allowed(max, max); // fail open
        }
    }

    public static class RateLimitResult {
        public final boolean allowed;
        public final int remaining;
        public final int max;
        public final Long retryAfterSeconds;

        private RateLimitResult(boolean allowed, int remaining, int max, Long retryAfter) {
            this.allowed = allowed;
            this.remaining = remaining;
            this.max = max;
            this.retryAfterSeconds = retryAfter;
        }

        public static RateLimitResult allowed(int remaining, int max) {
            return new RateLimitResult(true, remaining, max, null);
        }

        public static RateLimitResult limited(long retryAfter, int max) {
            return new RateLimitResult(false, 0, max, retryAfter);
        }
    }
}
