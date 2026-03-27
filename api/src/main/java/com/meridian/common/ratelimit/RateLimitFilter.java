package com.meridian.common.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
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
        HttpServletRequest requestToUse = request;
        if (request.getRequestURI().contains("/graphql")) {
            requestToUse = new CachedBodyHttpServletRequest(request);
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Skip rate limiting for unauthenticated requests (they can hit auth endpoints)
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(requestToUse, response);
            return;
        }

        String userId = auth.getName();
        String path = request.getRequestURI();
        String method = request.getMethod();

        // Check if this is a job creation request
        if (isJobCreationRequest(requestToUse, path)) {
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

        filterChain.doFilter(requestToUse, response);
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
            return request.getReader().lines().reduce("", (a, b) -> a + b);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Don't rate limit health checks or swagger endpoints
        if (path.contains("/actuator/health") ||
            path.contains("/swagger") ||
            path.contains("/v3/api-docs")) {
            return true;
        }

        // Don't rate limit auth endpoints on GraphQL (register, login, refreshToken)
        if (path.contains("/graphql")) {
            String body = getRequestBody(request);
            if (body != null && (
                body.contains("register") ||
                body.contains("login") ||
                body.contains("refreshToken")
            )) {
                return true;
            }
        }

        return false;
    }

    private String getRequestBody(HttpServletRequest request) {
        try {
            if (request instanceof CachedBodyHttpServletRequest) {
                return request.getReader().lines().reduce("", (a, b) -> a + b);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
