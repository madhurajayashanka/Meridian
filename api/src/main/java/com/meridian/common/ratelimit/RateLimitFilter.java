package com.meridian.common.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Rate limit filter for REST and GraphQL endpoints
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Skip rate limiting for unauthenticated requests (they can hit auth endpoints)
        if (auth == null || !auth.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = auth.getName();
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Check if this is a job creation request
        if (isJobCreationRequest(request, path)) {
            RateLimitService.RateLimitResult result = rateLimitService.checkJobLimit(userId);
            if (!result.allowed) {
                response.setStatus(429); // Too Many Requests
                response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds));
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"" + result.message + "\",\"retryAfter\":" + result.retryAfterSeconds + "}");
                return;
            }
        } else {
            // General rate limit for other authenticated endpoints
            RateLimitService.RateLimitResult result = rateLimitService.checkGeneralLimit(userId);
            if (!result.allowed) {
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds));
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Rate limit exceeded\",\"retryAfter\":" + result.retryAfterSeconds + "}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isJobCreationRequest(HttpServletRequest request, String path) {
        // GraphQL endpoint
        if (!path.contains("/graphql")) {
            return false;
        }

        // Check if it's a createResearchJob mutation
        String body = getRequestBody(request);
        return body != null && body.contains("createResearchJob");
    }

    private String getRequestBody(HttpServletRequest request) {
        try {
            return request.getReader().readLine();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Don't rate limit health checks or swagger endpoints
        return path.contains("/actuator/health") ||
            path.contains("/swagger") ||
            path.contains("/v3/api-docs");
    }
}
