package com.meridian.auth.controller;

import com.meridian.auth.dto.AuthResponse;
import com.meridian.auth.dto.LoginRequest;
import com.meridian.auth.dto.RegisterRequest;
import com.meridian.auth.entity.User;
import com.meridian.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import java.util.UUID;

/**
 * GraphQL Controller for authentication operations
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthGraphQLController {

    private final AuthService authService;

    // =========================================================================
    // Queries
    // =========================================================================

    @QueryMapping
    public User me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        UUID userId = UUID.fromString(auth.getName());
        return authService.getUserById(userId).orElse(null);
    }

    @QueryMapping
    public User user(@Argument String id) {
        try {
            UUID userId = UUID.fromString(id);
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
                return null;
            }
            // Users can only query themselves
            UUID currentUserId = UUID.fromString(auth.getName());
            if (!currentUserId.equals(userId)) {
                return null;
            }
            return authService.getUserById(userId).orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // =========================================================================
    // Mutations
    // =========================================================================

    @MutationMapping
    public AuthResponse register(
        @Argument String email,
        @Argument String password,
        @Argument String name
    ) {
        RegisterRequest request = RegisterRequest.builder()
            .email(email)
            .password(password)
            .name(name)
            .build();
        return authService.register(request);
    }

    @MutationMapping
    public AuthResponse login(
        @Argument String email,
        @Argument String password
    ) {
        LoginRequest request = LoginRequest.builder()
            .email(email)
            .password(password)
            .build();
        return authService.login(request);
    }

    @MutationMapping
    public AuthResponse refreshToken(@Argument String refreshToken) {
        return authService.refreshToken(refreshToken);
    }

    @MutationMapping
    public Boolean logout() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return false;
        }
        UUID userId = UUID.fromString(auth.getName());
        authService.revokeAllTokensForUser(userId);
        return true;
    }

    @MutationMapping
    public Boolean changePassword(
        @Argument String currentPassword,
        @Argument String newPassword
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return false;
        }
        UUID userId = UUID.fromString(auth.getName());
        authService.changePassword(userId, currentPassword, newPassword);
        return true;
    }

    @MutationMapping
    public User updateProfile(
        @Argument String name,
        @Argument String avatarUrl
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        UUID userId = UUID.fromString(auth.getName());
        return authService.updateProfile(userId, name, avatarUrl);
    }

    @MutationMapping
    public Boolean deleteAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return false;
        }
        UUID userId = UUID.fromString(auth.getName());
        authService.deleteAccount(userId);
        return true;
    }
}
