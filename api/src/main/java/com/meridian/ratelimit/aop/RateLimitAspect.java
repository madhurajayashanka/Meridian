package com.meridian.ratelimit.aop;

import com.meridian.ratelimit.service.RateLimitService;
import com.meridian.common.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * GraphQL Rate Limiting Aspect
 * Enforces rate limits on specific GraphQL mutations
 *
 * Usage: @RateLimit(limit & window as annotation parameters)
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimitService rateLimitService;

    /**
     * Apply rate limiting to GraphQL mutations
     * Intercepting submitResearchJob specifically
     */
    @Around("execution(* com.meridian.graphql.resolver.MutationResolver.submitResearchJob(..)) " +
            "|| execution(* com.meridian.graphql.resolver.MutationResolver.uploadDocument(..))")
    public Object enforceJobSubmissionRateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();

        // Extract user ID from security context
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new InvalidRequestException("User not authenticated");
        }

        String userId = auth.getName();

        // Check rate limits based on mutation type
        if ("submitResearchJob".equals(methodName)) {
            // 5 jobs per hour
            if (!rateLimitService.isJobSubmissionAllowed(userId)) {
                long retryAfter = rateLimitService.getJobRetryAfter(userId);
                throw new InvalidRequestException(
                    String.format("Job submission limit exceeded. Retry after %d seconds", retryAfter)
                );
            }
        } else if ("uploadDocument".equals(methodName)) {
            // 10 uploads per hour (2x job limit)
            if (!isDocumentUploadAllowed(userId)) {
                long retryAfter = getDocumentUploadRetryAfter(userId);
                throw new InvalidRequestException(
                    String.format("Document upload limit exceeded. Retry after %d seconds", retryAfter)
                );
            }
        }

        return joinPoint.proceed();
    }

    /**
     * Check document upload rate limit (10 per hour)
     */
    private boolean isDocumentUploadAllowed(String userId) {
        String key = "ratelimit:user:" + userId + ":document_upload";
        return rateLimitService.isAllowed(key, 10, 3600);
    }

    /**
     * Get document upload retry time
     */
    private long getDocumentUploadRetryAfter(String userId) {
        String key = "ratelimit:user:" + userId + ":document_upload";
        return getRetryAfter(key);
    }

    /**
     * Get retry-after header value in seconds
     */
    private long getRetryAfter(String key) {
        // This would be retrieved from Redis TTL
        return 60; // Placeholder - implement with Redis TTL lookup
    }
}
