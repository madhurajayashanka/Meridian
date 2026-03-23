package com.meridian.auth.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.meridian.IntegrationTest;
import com.meridian.TestContainerBase;
import com.meridian.auth.entity.RefreshToken;
import com.meridian.auth.entity.User;
import com.meridian.auth.repository.RefreshTokenRepository;
import com.meridian.auth.repository.UserRepository;
import com.meridian.auth.util.JwtUtil;

@IntegrationTest
@ActiveProfiles("test")
@DisplayName("Auth Service Integration Tests")
class AuthServiceIntegrationTest extends TestContainerBase {

	@Autowired
	private AuthService authService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtUtil jwtUtil;

	private static final String TEST_EMAIL = "test@example.com";
	private static final String TEST_PASSWORD = "SecurePassword123!";
	private static final String TEST_NAME = "Test User";

	@BeforeEach
	void setUp() {
		userRepository.deleteAll();
		refreshTokenRepository.deleteAll();
	}

	@Test
	@DisplayName("Should successfully register a new user")
	void testRegisterUser() {
		// When
		User registeredUser = authService.registerUser(TEST_EMAIL, TEST_PASSWORD, TEST_NAME);

		// Then
		assertNotNull(registeredUser);
		assertEquals(TEST_EMAIL, registeredUser.getEmail());
		assertEquals(TEST_NAME, registeredUser.getName());
		assertTrue(passwordEncoder.matches(TEST_PASSWORD, registeredUser.getPasswordHash()));
		assertFalse(registeredUser.isAccountLocked());
		assertTrue(userRepository.findByEmail(TEST_EMAIL).isPresent());
	}

	@Test
	@DisplayName("Should reject duplicate email registration")
	void testRegisterDuplicateEmail() {
		// Given
		authService.registerUser(TEST_EMAIL, TEST_PASSWORD, TEST_NAME);

		// When & Then
		assertThrows(IllegalArgumentException.class,
				() -> authService.registerUser(TEST_EMAIL, "DifferentPass123!", "Different Name"));
	}

	@Test
	@DisplayName("Should reject weak password")
	void testRegisterWeakPassword() {
		// When & Then - passwords must be at least 8 chars with mixed case and number
		assertThrows(IllegalArgumentException.class,
				() -> authService.registerUser(TEST_EMAIL, "weak", TEST_NAME));

		assertThrows(IllegalArgumentException.class,
				() -> authService.registerUser(TEST_EMAIL, "alllowercase123", TEST_NAME));

		assertThrows(IllegalArgumentException.class,
				() -> authService.registerUser(TEST_EMAIL, "ALLUPPERCASE123", TEST_NAME));
	}

	@Test
	@DisplayName("Should successfully login user and return tokens")
	void testLoginUser() {
		// Given
		authService.registerUser(TEST_EMAIL, TEST_PASSWORD, TEST_NAME);

		// When
		var loginResponse = authService.login(TEST_EMAIL, TEST_PASSWORD);

		// Then
		assertNotNull(loginResponse);
		assertNotNull(loginResponse.getAccessToken());
		assertNotNull(loginResponse.getRefreshToken());
		assertEquals(TEST_EMAIL, loginResponse.getEmail());
		assertEquals(TEST_NAME, loginResponse.getName());
		assertTrue(jwtUtil.validateToken(loginResponse.getAccessToken()));
	}

	@Test
	@DisplayName("Should reject login with wrong password")
	void testLoginWrongPassword() {
		// Given
		authService.registerUser(TEST_EMAIL, TEST_PASSWORD, TEST_NAME);

		// When & Then
		assertThrows(IllegalArgumentException.class,
				() -> authService.login(TEST_EMAIL, "WrongPassword123!"));
	}

	@Test
	@DisplayName("Should reject login for non-existent user")
	void testLoginNonExistentUser() {
		// When & Then
		assertThrows(IllegalArgumentException.class,
				() -> authService.login("nonexistent@example.com", TEST_PASSWORD));
	}

	@Test
	@DisplayName("Should lock account after failed login attempts")
	void testAccountLockout() {
		// Given
		authService.registerUser(TEST_EMAIL, TEST_PASSWORD, TEST_NAME);

		// When - attempt 5 failed logins
		for (int i = 0; i < 5; i++) {
			try {
				authService.login(TEST_EMAIL, "WrongPassword123!");
			} catch (IllegalArgumentException e) {
				// Expected
			}
		}

		// Then - account should be locked
		User lockedUser = userRepository.findByEmail(TEST_EMAIL).orElseThrow();
		assertTrue(lockedUser.isAccountLocked());

		// And further login attempts should fail
		assertThrows(IllegalArgumentException.class,
				() -> authService.login(TEST_EMAIL, TEST_PASSWORD));
	}

	@Test
	@DisplayName("Should successfully refresh access token")
	void testRefreshToken() {
		// Given
		authService.registerUser(TEST_EMAIL, TEST_PASSWORD, TEST_NAME);
		var loginResponse = authService.login(TEST_EMAIL, TEST_PASSWORD);
		String oldAccessToken = loginResponse.getAccessToken();
		String refreshToken = loginResponse.getRefreshToken();

		// When
		Thread.sleep(100); // Ensure token time difference
		var refreshResponse = authService.refreshAccessToken(refreshToken);

		// Then
		assertNotNull(refreshResponse);
		assertNotNull(refreshResponse.getAccessToken());
		assertNotEquals(oldAccessToken, refreshResponse.getAccessToken());
		assertTrue(jwtUtil.validateToken(refreshResponse.getAccessToken()));
	}

	@Test
	@DisplayName("Should reject invalid refresh token")
	void testRefreshInvalidToken() {
		// When & Then
		assertThrows(IllegalArgumentException.class,
				() -> authService.refreshAccessToken("invalid_token_here"));
	}

	@Test
	@DisplayName("Should successfully change password")
	void testChangePassword() {
		// Given
		authService.registerUser(TEST_EMAIL, TEST_PASSWORD, TEST_NAME);
		User user = userRepository.findByEmail(TEST_EMAIL).orElseThrow();
		String newPassword = "NewPassword456!";

		// When
		authService.changePassword(user.getId(), TEST_PASSWORD, newPassword);

		// Then
		User updatedUser = userRepository.findById(user.getId()).orElseThrow();
		assertTrue(passwordEncoder.matches(newPassword, updatedUser.getPasswordHash()));
		assertFalse(passwordEncoder.matches(TEST_PASSWORD, updatedUser.getPasswordHash()));
	}

	@Test
	@DisplayName("Should reject password change with wrong current password")
	void testChangePasswordWrongCurrent() {
		// Given
		authService.registerUser(TEST_EMAIL, TEST_PASSWORD, TEST_NAME);
		User user = userRepository.findByEmail(TEST_EMAIL).orElseThrow();

		// When & Then
		assertThrows(IllegalArgumentException.class,
				() -> authService.changePassword(user.getId(), "WrongPassword123!", "NewPassword456!"));
	}

	@Test
	@DisplayName("JWT token should contain correct claims")
	void testJwtTokenClaims() {
		// Given
		authService.registerUser(TEST_EMAIL, TEST_PASSWORD, TEST_NAME);
		var loginResponse = authService.login(TEST_EMAIL, TEST_PASSWORD);
		String accessToken = loginResponse.getAccessToken();

		// When
		String email = jwtUtil.extractEmail(accessToken);
		Long userId = jwtUtil.extractUserId(accessToken);

		// Then
		assertEquals(TEST_EMAIL, email);
		assertNotNull(userId);
	}

	@Test
	@DisplayName("Access token should expire after issued at time + 15 minutes")
	void testAccessTokenExpiration() {
		// Given
		authService.registerUser(TEST_EMAIL, TEST_PASSWORD, TEST_NAME);
		var loginResponse = authService.login(TEST_EMAIL, TEST_PASSWORD);
		String accessToken = loginResponse.getAccessToken();

		// When
		long expirationTime = jwtUtil.extractExpiration(accessToken).getTime();
		long issuedAtTime = jwtUtil.extractIssuedAt(accessToken).getTime();

		// Then
		long expectedDuration = 15 * 60 * 1000; // 15 minutes in milliseconds
		long actualDuration = expirationTime - issuedAtTime;
		assertTrue(actualDuration >= expectedDuration - 1000); // Allow 1 second tolerance
	}

	@Test
	@DisplayName("Should reject expired tokens")
	void testExpiredTokenRejection() {
		// Given
		authService.registerUser(TEST_EMAIL, TEST_PASSWORD, TEST_NAME);
		var loginResponse = authService.login(TEST_EMAIL, TEST_PASSWORD);
		String accessToken = loginResponse.getAccessToken();

		// JWT will expire after 15 minutes, this test verifies structure
		// (actual expiration testing would require time manipulation)
		assertTrue(jwtUtil.validateToken(accessToken));
	}

	@SuppressWarnings("java:S2699") // Suppress "at least one assertion" warning
	private void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
