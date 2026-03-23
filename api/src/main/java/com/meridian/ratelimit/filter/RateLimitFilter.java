package com.meridian.ratelimit.filter;

import com.meridian.ratelimit.service.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * Rate Limit Filter - Enforces rate limiting on HTTP requests
 *
 * Applies rate limits based on:
 * - User ID (extracted from JWT)
 * - Request type (general vs job submission)
 *
 * Returns 429 (Too Many Requests) if limit exceeded
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip rate limiting for auth endpoints
        if (path.startsWith("/api/auth/") || path.equals("/health") || path.equals("/graphql")
            || path.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract user ID from principal (set by JwtAuthenticationFilter)
        String userId = extractUserId(request);

        if (userId != null) {
            // Check if this is a job submission request
            if (isJobSubmissionRequest(path, request.getMethod())) {
                if (!rateLimitService.isJobSubmissionAllowed(userId)) {
                    long retryAfter = rateLimitService.getJobRetryAfter(userId);
                    sendRateLimitResponse(response, retryAfter, "Job submission limit exceeded (5 per hour)");
                    return;
                }
            } else {
                // Apply general rate limit
                if (!rateLimitService.isGeneralAllowed(userId)) {
                    long retryAfter = rateLimitService.getRetryAfter(userId);
                    sendRateLimitResponse(response, retryAfter, "Rate limit exceeded (60 per minute)");
                    return;
                }
            }

            // Add rate limit headers to response
            long remaining = rateLimitService.getRemainingRequests(userId);
            response.setHeader("X-RateLimit-Limit", String.valueOf(RateLimitService.GENERAL_LIMIT));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract user ID from JWT principal
     */
    private String extractUserId(HttpServletRequest request) {
        try {
            Object principal = request.getUserPrincipal();
            if (principal != null) {
                // Principal name is the user UUID
                String userId = principal.getName();
                // Validate it's a valid UUID
                UUID.fromString(userId);
                return userId;
            }
        } catch (Exception e) {
            // Invalid or missing user
        }
        return null;
    }

    /**
     * Check if this is a job submission request
     */
    private boolean isJobSubmissionRequest(String path, String method) {
        // GraphQL mutation for submitResearchJob
        return path.contains("/graphql") && "POST".equals(method);
        // For REST API version: return path.contains("/jobs") && "POST".equals(method);
    }

    /**
     * Send 429 Too Many Requests response
     */
    private void sendRateLimitResponse(
        HttpServletResponse response,
        long retryAfter,
        String message
    ) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(String.format(
            "{\"error\": \"Rate limit exceeded\", \"message\": \"%s\", \"retryAfter\": %d}",
            message, retryAfter
        ));
        response.flushBuffer();
    }

    /**
     * Should this filter process this request?
     * Skip non-HTTP request types
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        return false; // Apply to all requests (filtering done inside)
    }
}
