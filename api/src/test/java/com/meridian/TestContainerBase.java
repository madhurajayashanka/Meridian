package com.meridian;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;

/**
 * Base class for integration tests with Testcontainers.
 * Provides PostgreSQL and Redis containers for testing.
 */
public abstract class TestContainerBase {

	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
			.withDatabaseName("meridian_test")
			.withUsername("postgres")
			.withPassword("postgres");

	static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
			.withExposedPorts(6379);

	static {
		POSTGRES.start();
		REDIS.start();
	}

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
		
		String redisHost = REDIS.getHost();
		Integer redisPort = REDIS.getMappedPort(6379);
		registry.add("spring.redis.host", () -> redisHost);
		registry.add("spring.redis.port", () -> redisPort);
	}
}
