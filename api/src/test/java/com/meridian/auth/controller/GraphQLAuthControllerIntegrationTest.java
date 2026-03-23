package com.meridian.auth.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.meridian.IntegrationTest;
import com.meridian.TestContainerBase;
import com.meridian.auth.repository.UserRepository;
import com.meridian.auth.service.AuthService;
import com.meridian.project.repository.ProjectRepository;

@IntegrationTest
@DisplayName("GraphQL Auth Controller Integration Tests")
class GraphQLAuthControllerIntegrationTest extends TestContainerBase {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AuthService authService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ProjectRepository projectRepository;

	private static final String TEST_EMAIL = "test@example.com";
	private static final String PASSWORD = "SecurePassword123!";
	private static final String GRAPHQL_ENDPOINT = "/graphql";

	@BeforeEach
	void setUp() {
		projectRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	@DisplayName("Should register user via GraphQL mutation")
	void testRegisterUserMutation() throws Exception {
		// Given
		String mutation = """
				mutation {
					register(input: {
						email: "%s"
						password: "%s"
						name: "Test User"
					}) {
						id
						email
						name
					}
				}
				""".formatted(TEST_EMAIL, PASSWORD);

		// When & Then
		mockMvc.perform(post(GRAPHQL_ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"query\": \"" + mutation.replace("\"", "\\\"").replace("\n", " ") + "\"}")
				.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.register.email", equalTo(TEST_EMAIL)))
				.andExpect(jsonPath("$.data.register.name", equalTo("Test User")))
				.andExpect(jsonPath("$.data.register.id", notNullValue()));
	}

	@Test
	@DisplayName("Should reject duplicate email registration")
	void testDuplicateEmailRegistration() throws Exception {
		// Given - first registration
		authService.registerUser(TEST_EMAIL, PASSWORD, "Test User");

		// When - try to register same email
		String mutation = """
				mutation {
					register(input: {
						email: "%s"
						password: "DifferentPass123!"
						name: "Different User"
					}) {
						id
					}
				}
				""".formatted(TEST_EMAIL);

		// Then
		mockMvc.perform(post(GRAPHQL_ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"query\": \"" + mutation.replace("\"", "\\\"").replace("\n", " ") + "\"}")
				.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.errors", notNullValue()));
	}

	@Test
	@DisplayName("Should login user and return tokens via GraphQL")
	void testLoginMutation() throws Exception {
		// Given
		authService.registerUser(TEST_EMAIL, PASSWORD, "Test User");

		String mutation = """
				mutation {
					login(input: {
						email: "%s"
						password: "%s"
					}) {
						accessToken
						refreshToken
						email
						name
					}
				}
				""".formatted(TEST_EMAIL, PASSWORD);

		// When & Then
		mockMvc.perform(post(GRAPHQL_ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"query\": \"" + mutation.replace("\"", "\\\"").replace("\n", " ") + "\"}")
				.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.login.email", equalTo(TEST_EMAIL)))
				.andExpect(jsonPath("$.data.login.name", equalTo("Test User")))
				.andExpect(jsonPath("$.data.login.accessToken", notNullValue()))
				.andExpect(jsonPath("$.data.login.refreshToken", notNullValue()));
	}

	@Test
	@DisplayName("Should reject invalid credentials")
	void testLoginWithInvalidCredentials() throws Exception {
		// Given
		authService.registerUser(TEST_EMAIL, PASSWORD, "Test User");

		String mutation = """
				mutation {
					login(input: {
						email: "%s"
						password: "WrongPassword123!"
					}) {
						accessToken
					}
				}
				""".formatted(TEST_EMAIL);

		// When & Then
		mockMvc.perform(post(GRAPHQL_ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"query\": \"" + mutation.replace("\"", "\\\"").replace("\n", " ") + "\"}")
				.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.errors", notNullValue()));
	}

	@Test
	@DisplayName("Should refresh access token via GraphQL")
	void testRefreshTokenMutation() throws Exception {
		// Given
		var loginResponse = authService.login(TEST_EMAIL, PASSWORD);

		authService.registerUser(TEST_EMAIL, PASSWORD, "Test User");
		var response = authService.login(TEST_EMAIL, PASSWORD);
		String refreshToken = response.getRefreshToken();

		String mutation = """
				mutation {
					refreshToken(refreshToken: "%s") {
						accessToken
						refreshToken
					}
				}
				""".formatted(refreshToken);

		// When & Then
		mockMvc.perform(post(GRAPHQL_ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"query\": \"" + mutation.replace("\"", "\\\"").replace("\n", " ") + "\"}")
				.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.refreshToken.accessToken", notNullValue()))
				.andExpect(jsonPath("$.data.refreshToken.refreshToken", notNullValue()));
	}

	@Test
	@DisplayName("Should query current user via GraphQL")
	void testMeQuery() throws Exception {
		// Given
		authService.registerUser(TEST_EMAIL, PASSWORD, "Test User");
		var loginResponse = authService.login(TEST_EMAIL, PASSWORD);

		String query = "{ me { id email name } }";

		// When & Then
		mockMvc.perform(post(GRAPHQL_ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + loginResponse.getAccessToken())
				.content("{\"query\": \"" + query + "\"}")
				.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.me.email", equalTo(TEST_EMAIL)))
				.andExpect(jsonPath("$.data.me.name", equalTo("Test User")));
	}

	@Test
	@DisplayName("Should reject unauthenticated requests to protected queries")
	void testUnauthenticatedMeQuery() throws Exception {
		// Given
		String query = "{ me { id email } }";

		// When & Then
		mockMvc.perform(post(GRAPHQL_ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"query\": \"" + query + "\"}")
				.with(csrf()))
				.andExpect(status().isUnauthorized());
	}
}
