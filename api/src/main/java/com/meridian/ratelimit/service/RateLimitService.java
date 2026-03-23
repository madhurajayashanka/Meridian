package com.meridian.ratelimit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Rate Limit Service - Implements Redis-backed sliding window rate limiting
 *
 * Uses Redis Sorted Sets for O(log n) operations:
 * - Member: request timestamp
 * - Score: timestamp (for sorting by time)
 * - Window: fixed time period (e.g., 60 seconds, 3600 seconds)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    /**
     * General rate limit: 60 requests per minute per user
     */
    public static final int GENERAL_LIMIT = 60;
    public static final int GENERAL_WINDOW_SECONDS = 60;

    /**
     * Research job submission limit: 5 jobs per hour per user
     */
    public static final int JOB_SUBMISSION_LIMIT = 5;
    public static final int JOB_SUBMISSION_WINDOW_SECONDS = 3600;

    /**
     * Check if request exceeds rate limit
     * Returns true if allowed, false if rate limited
     *
     * Uses sliding window algorithm on Redis Sorted Set:
     * 1. Remove entries older than window
     * 2. Count remaining entries
     * 3. If count < limit, add new entry and return true
     * 4. Otherwise return false
     */
    public boolean isAllowed(String key, int limit, int windowSeconds) {
        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - (long) windowSeconds * 1000;

        try {
            return redisTemplate.execute((RedisOperations<String, String> operations) -> {
                // Remove old entries outside the window
                operations.opsForZSet().removeRangeByScore(key, 0, windowStart);

                // Count current requests in window
                Long count = operations.opsForZSet().zCard(key);
                if (count == null) count = 0L;

                // If under limit, add new request
                if (count < limit) {
                    operations.opsForZSet().add(key, String.valueOf(currentTime), currentTime);
                    // Set key expiry to window size for cleanup
                    operations.expire(key, windowSeconds, TimeUnit.SECONDS);
                    return true;
                }

                return false;
            });
        } catch (Exception e) {
            log.error("Rate limit check failed for key: {}", key, e);
            // Fail open - allow request if Redis is down
            return true;
        }
    }

    /**
     * Check general API rate limit (60 req/min per user)
     */
    public boolean isGeneralAllowed(String userId) {
        String key = "ratelimit:user:" + userId + ":general";
        return isAllowed(key, GENERAL_LIMIT, GENERAL_WINDOW_SECONDS);
    }

    /**
     * Check job submission rate limit (5 jobs/hour per user)
     */
    public boolean isJobSubmissionAllowed(String userId) {
        String key = "ratelimit:user:" + userId + ":job_submission";
        return isAllowed(key, JOB_SUBMISSION_LIMIT, JOB_SUBMISSION_WINDOW_SECONDS);
    }

    /**
     * Get remaining requests for a user (for headers)
     */
    public long getRemainingRequests(String userId) {
        String key = "ratelimit:user:" + userId + ":general";
        Long count = redisTemplate.opsForZSet().zCard(key);
        return Math.max(0, GENERAL_LIMIT - (count != null ? count : 0));
    }

    /**
     * Get time until next request is allowed (in seconds)
     */
    public long getRetryAfter(String userId) {
        String key = "ratelimit:user:" + userId + ":general";
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }

    /**
     * Get time until next job submission is allowed (in seconds)
     */
    public long getJobRetryAfter(String userId) {
        String key = "ratelimit:user:" + userId + ":job_submission";
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }
}
