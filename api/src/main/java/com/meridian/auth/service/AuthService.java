package com.meridian.auth.service;

import com.meridian.auth.dto.AuthResponse;
import com.meridian.auth.dto.LoginRequest;
import com.meridian.auth.dto.RefreshRequest;
import com.meridian.auth.dto.RegisterRequest;
import com.meridian.auth.entity.RefreshToken;
import com.meridian.auth.entity.User;
import com.meridian.auth.repository.RefreshTokenRepository;
import com.meridian.auth.repository.UserRepository;
import com.meridian.auth.util.JwtUtil;
import com.meridian.auth.util.PasswordEncoder;
import com.meridian.common.exception.UnauthorizedException;
import com.meridian.common.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Authentication Service for user registration, login, and token management.
 * Properties: 1, 2, 3, 4, 5, 7
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.jwt.access-token-expiry-minutes:15}")
    private int accessTokenExpiryMinutes;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Z|a-z]{2,}$"
    );

    /**
     * Register a new user.
     * Property 1: Registration round-trip — creates account and returns tokens
     * Requirement 1.1: Register with email, password, name
     * Requirement 1.2: Duplicate email returns 409 Conflict
     */
    public AuthResponse register(RegisterRequest request) {
        validateRegistrationInput(request);

        // Check if email already exists
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            log.warn("Registration attempt with existing email: {}", request.getEmail());
            throw new ValidationException("Email already registered");
        }

        // Create new user
        User user = User.builder()
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .isActive(true)
                .failedLoginAttempts(0)
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {} ({})", user.getId(), user.getEmail());

        // Generate tokens
        return generateAuthResponse(user);
    }

    /**
     * Login with email and password.
     * Property 2: Login returns valid tokens with correct expiry
     * Property 3: Invalid credentials rejected with 401
     * Requirement 1.3: Login returns 15-min access token and 7-day refresh token
     * Requirement 1.4: Invalid credentials return 401
     */
    public AuthResponse login(LoginRequest request) {
        if (request.getEmail() == null || request.getPassword() == null) {
            throw new ValidationException("Email and password required");
        }

        User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        // Check if account is locked
        if (user.isAccountLocked()) {
            log.warn("Login attempt on locked account: {}", user.getEmail());
            throw new UnauthorizedException("Account is locked. Try again later.");
        }

        // Check if deleted
        if (user.isDeleted()) {
            throw new UnauthorizedException("Account not found");
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            user.incrementFailedLoginAttempts();
            userRepository.save(user);
            log.warn("Failed login attempt for user: {} (attempt: {})", 
                    user.getEmail(), user.getFailedLoginAttempts());
            throw new UnauthorizedException("Invalid credentials");
        }

        // Reset failed login attempts and unlock
        user.resetFailedLoginAttempts();
        userRepository.save(user);

        return generateAuthResponse(user);
    }

    /**
     * Refresh access token using refresh token.
     * Property 4: Token refresh round-trip — returns new tokens, revokes old refresh token
     * Property 7: Old refresh token no longer accepted after refresh
     * Requirement 1.5: Refresh with valid token returns new tokens
     * Requirement 1.6: Invalid/expired refresh token returns 401
     */
    public AuthResponse refreshToken(RefreshRequest request) {
        if (request.getRefreshToken() == null || request.getRefreshToken().isEmpty()) {
            throw new ValidationException("Refresh token required");
        }

        // Validate JWT structure
        if (jwtUtil.validateToken(request.getRefreshToken()) == null) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        // Must be a refresh token
        if (!jwtUtil.isRefreshToken(request.getRefreshToken())) {
            throw new UnauthorizedException("Invalid token type");
        }

        UUID userId = jwtUtil.extractUserId(request.getRefreshToken());
        if (userId == null) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        // Find user
        User user = userRepository.findByIdActive(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        // Hash the refresh token for database lookup
        String tokenHash = hashToken(request.getRefreshToken());

        // Check if token exists and is valid in database
        RefreshToken dbToken = refreshTokenRepository.findValidToken(tokenHash, LocalDateTime.now())
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));

        // Revoke old refresh token (Property 4, Property 7)
        dbToken.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(dbToken);

        log.info("Token refreshed for user: {}", user.getId());
        return generateAuthResponse(user);
    }

    public AuthResponse refreshToken(String refreshToken) {
        return refreshToken(RefreshRequest.builder().refreshToken(refreshToken).build());
    }

    /**
     * Change user password (invalidates all refresh tokens).
     * Property 7: Password change invalidates refresh tokens
     * Requirement 2.2: Password change invalidates all refresh tokens
     */
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new ValidationException("New password must be at least 8 characters");
        }

        User user = userRepository.findByIdActive(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        // Verify current password
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect");
        }

        // Update password
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Revoke all refresh tokens (Property 7, Requirement 2.2)
        List<RefreshToken> tokens = refreshTokenRepository.findAllValidTokensByUser(user);
        tokens.forEach(token -> token.setRevokedAt(LocalDateTime.now()));
        refreshTokenRepository.saveAll(tokens);

        log.info("Password changed for user: {}, all refresh tokens revoked", userId);
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserById(UUID userId) {
        return userRepository.findByIdActive(userId);
    }

    public void revokeAllTokensForUser(UUID userId) {
        User user = userRepository.findByIdActive(userId)
            .orElseThrow(() -> new UnauthorizedException("User not found"));
        List<RefreshToken> tokens = refreshTokenRepository.findAllValidTokensByUser(user);
        tokens.forEach(token -> token.setRevokedAt(LocalDateTime.now()));
        refreshTokenRepository.saveAll(tokens);
    }

    public User updateProfile(UUID userId, String name, String avatarUrl) {
        User user = userRepository.findByIdActive(userId)
            .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (name != null && !name.isBlank()) {
            user.setName(name.trim());
        }
        if (avatarUrl != null) {
            user.setAvatarUrl(avatarUrl.trim().isEmpty() ? null : avatarUrl.trim());
        }

        return userRepository.save(user);
    }

    public void deleteAccount(UUID userId) {
        User user = userRepository.findByIdActive(userId)
            .orElseThrow(() -> new UnauthorizedException("User not found"));
        user.setDeletedAt(LocalDateTime.now());
        user.setIsActive(false);
        userRepository.save(user);
        revokeAllTokensForUser(userId);
    }

    // Private helper methods

    private void validateRegistrationInput(RegisterRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new ValidationException("Email is required");
        }

        if (!EMAIL_PATTERN.matcher(request.getEmail()).matches()) {
            throw new ValidationException("Invalid email format");
        }

        if (request.getPassword() == null || request.getPassword().length() < 8) {
            throw new ValidationException("Password must be at least 8 characters");
        }

        if (request.getName() == null || request.getName().isBlank()) {
            throw new ValidationException("Name is required");
        }

        if (request.getName().length() > 255) {
            throw new ValidationException("Name must be at most 255 characters");
        }
    }

    private AuthResponse generateAuthResponse(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        // Save refresh token hash to database
        RefreshToken dbToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hashToken(refreshToken))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(dbToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(accessTokenExpiryMinutes * 60) // Convert to seconds
                .tokenType("Bearer")
                .build();
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to hash token", e);
        }
    }
}
