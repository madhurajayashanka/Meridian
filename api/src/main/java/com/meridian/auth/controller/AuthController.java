package com.meridian.auth.controller;

import com.meridian.auth.dto.AuthResponse;
import com.meridian.auth.dto.LoginRequest;
import com.meridian.auth.dto.RefreshRequest;
import com.meridian.auth.dto.RegisterRequest;
import com.meridian.auth.service.AuthService;
import com.meridian.common.exception.UnauthorizedException;
import com.meridian.common.exception.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for authentication endpoints.
 * POST /api/auth/register - Register new user
 * POST /api/auth/login - Login with email/password
 * POST /api/auth/refresh - Refresh access token
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Register a new user.
     * Requirement 1.1: Register with email, password, name
     * Requirement 1.2: Duplicate email returns 409 Conflict
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            AuthResponse response = authService.register(request);
            log.info("User registered successfully");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (ValidationException e) {
            if (e.getMessage().contains("Email already registered")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(new ErrorResponse(409, e.getMessage()));
            }
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(400, e.getMessage()));
        } catch (Exception e) {
            log.error("Registration error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "Registration failed"));
        }
    }

    /**
     * Login with email and password.
     * Requirement 1.3: Login with valid credentials returns tokens
     * Requirement 1.4: Invalid credentials return 401
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            log.info("User logged in successfully");
            return ResponseEntity.ok(response);
        } catch (ValidationException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(400, e.getMessage()));
        } catch (UnauthorizedException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, e.getMessage()));
        } catch (Exception e) {
            log.error("Login error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "Login failed"));
        }
    }

    /**
     * Refresh access token using refresh token.
     * Requirement 1.5: Refresh with valid token returns new tokens
     * Requirement 1.6: Invalid/expired refresh token returns 401
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
        try {
            AuthResponse response = authService.refreshToken(request);
            log.info("Token refreshed successfully");
            return ResponseEntity.ok(response);
        } catch (ValidationException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(400, e.getMessage()));
        } catch (UnauthorizedException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(401, e.getMessage()));
        } catch (Exception e) {
            log.error("Token refresh error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(500, "Token refresh failed"));
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ErrorResponse {
        private int status;
        private String message;
    }
}
