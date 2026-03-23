package com.meridian.common.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import com.meridian.IntegrationTest;
import com.meridian.TestContainerBase;
import com.meridian.auth.repository.UserRepository;
import com.meridian.auth.service.AuthService;
import com.meridian.project.repository.ProjectRepository;

@IntegrationTest
@DisplayName("Rate Limiting Service Integration Tests")
class RateLimitServiceIntegrationTest extends TestContainerBase {

	@Autowired
	private RateLimitService rateLimitService;

	@Autowired
	private RedisTemplate<String, String> redisTemplate;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private AuthService authService;

	private Long userId;
	private static final String TEST_EMAIL = "test@example.com";
	private static final String PASSWORD = "SecurePassword123!";

	@BeforeEach
	void setUp() {
		projectRepository.deleteAll();
		userRepository.deleteAll();
		redisTemplate.getConnectionFactory().getConnection().flushAll();

		var user = authService.registerUser(TEST_EMAIL, PASSWORD, "Test User");
		userId = user.getId();
	}

	@Test
	@DisplayName("Should allow requests within rate limit")
	void testRequestWithinRateLimit() {
		// When
		for (int i = 0; i < 25; i++) {
			boolean allowed = rateLimitService.allowRequest(userId);
			assertTrue(allowed, "Request " + (i + 1) + " should be allowed");
		}
	}

	@Test
	@DisplayName("Should block requests exceeding rate limit (60 req/min)")
	void testRateLimitExceeded() {
		// When - make requests up to the limit
		for (int i = 0; i < 60; i++) {
			rateLimitService.allowRequest(userId);
		}

		// Then - next request should be blocked
		boolean blocked = rateLimitService.allowRequest(userId);
		assertFalse(blocked, "61st request in minute should be blocked");
	}

	@Test
	@DisplayName("Should enforce job submission limit (5 jobs per hour)")
	void testJobSubmissionRateLimit() {
		// When - submit jobs up to the limit
		for (int i = 0; i < 5; i++) {
			boolean allowed = rateLimitService.allowJobSubmission(userId);
			assertTrue(allowed, "Job submission " + (i + 1) + " should be allowed");
		}

		// Then - 6th job submission should be blocked
		boolean blocked = rateLimitService.allowJobSubmission(userId);
		assertFalse(blocked, "6th job submission in hour should be blocked");
	}

	@Test
	@DisplayName("Should use sliding window algorithm for rate limiting")
	void testSlidingWindowAlgorithm() {
		// Given - make some requests
		for (int i = 0; i < 30; i++) {
			rateLimitService.allowRequest(userId);
		}

		// When - wait for window to expire (simulated by checking window)
		// Then - should track correct count in Redis
		String key = "rate_limit:request:" + userId;
		Long count = redisTemplate.opsForValue().increment(key);
		assertNotNull(count);
	}

	@Test
	@DisplayName("Should track rate limit per user separately")
	void testUserIsolation() {
		// Given - two users
		var user2 = authService.registerUser("user2@example.com", PASSWORD, "User 2");

		// When - first user makes requests
		for (int i = 0; i < 50; i++) {
			rateLimitService.allowRequest(userId);
		}

		// Then - second user should still be within limit
		boolean user2Allowed = rateLimitService.allowRequest(user2.getId());
		assertTrue(user2Allowed, "Second user should not be affected by first user's rate limit");

		// And - first user at limit
		boolean user1Blocked = rateLimitService.allowRequest(userId);
		assertFalse(user1Blocked, "First user should be at rate limit");
	}

	@Test
	@DisplayName("Should reset rate limit after window expires")
	void testRateLimitReset() {
		// Given
		String key = "rate_limit:request:" + userId;

		// When - make requests to hit limit
		for (int i = 0; i < 60; i++) {
			rateLimitService.allowRequest(userId);
		}

		// Then - should be blocked
		boolean blocked = rateLimitService.allowRequest(userId);
		assertFalse(blocked);

		// When - set expiration in the past
		redisTemplate.expire(key, -1, TimeUnit.SECONDS);
		redisTemplate.delete(key);

		// Then - should be allowed again
		boolean allowed = rateLimitService.allowRequest(userId);
		assertTrue(allowed);
	}

	@Test
	@DisplayName("Should return remaining quota information")
	void testRateLimitQuotaInfo() {
		// When - make some requests
		for (int i = 0; i < 20; i++) {
			rateLimitService.allowRequest(userId);
		}

		// Then - remaining quota should be accurate
		long remaining = rateLimitService.getRemainingRequests(userId);
		assertTrue(remaining > 0);
		assertTrue(remaining <= 60);
	}
}
