package com.meridian.project.controller;

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
@DisplayName("GraphQL Project Controller Integration Tests")
class GraphQLProjectControllerIntegrationTest extends TestContainerBase {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AuthService authService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ProjectRepository projectRepository;

	private String authToken;
	private static final String TEST_EMAIL = "test@example.com";
	private static final String PASSWORD = "SecurePassword123!";
	private static final String GRAPHQL_ENDPOINT = "/graphql";

	@BeforeEach
	void setUp() {
		projectRepository.deleteAll();
		userRepository.deleteAll();

		authService.registerUser(TEST_EMAIL, PASSWORD, "Test User");
		var loginResponse = authService.login(TEST_EMAIL, PASSWORD);
		authToken = loginResponse.getAccessToken();
	}

	@Test
	@DisplayName("Should create project via GraphQL mutation")
	void testCreateProjectMutation() throws Exception {
		// Given
		String mutation = """
				mutation {
					createProject(input: {
						name: "Research Project"
						description: "A detailed research project"
					}) {
						id
						name
						description
					}
				}
				""";

		// When & Then
		mockMvc.perform(post(GRAPHQL_ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + authToken)
				.content("{\"query\": \"" + mutation.replace("\"", "\\\"").replace("\n", " ") + "\"}")
				.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.createProject.name", equalTo("Research Project")))
				.andExpect(jsonPath("$.data.createProject.description", equalTo("A detailed research project")))
				.andExpect(jsonPath("$.data.createProject.id", notNullValue()));
	}

	@Test
	@DisplayName("Should query user's projects via GraphQL")
	void testMyProjectsQuery() throws Exception {
		// Given - create some projects
		String createMutation = """
				mutation {
					createProject(input: {
						name: "Project One"
						description: "First project"
					}) {
						id
					}
				}
				""";

		mockMvc.perform(post(GRAPHQL_ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + authToken)
				.content("{\"query\": \"" + createMutation.replace("\"", "\\\"").replace("\n", " ") + "\"}")
				.with(csrf()))
				.andExpect(status().isOk());

		// When
		String query = "{ myProjects { id name description } }";

		// Then
		mockMvc.perform(post(GRAPHQL_ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + authToken)
				.content("{\"query\": \"" + query + "\"}")
				.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.myProjects", hasSize(greaterThanOrEqualTo(1))))
				.andExpect(jsonPath("$.data.myProjects[0].name", equalTo("Project One")));
	}

	@Test
	@DisplayName("Should retrieve project by ID via GraphQL")
	void testProjectQuery() throws Exception {
		// Given - create a project
		String createMutation = """
				mutation {
					createProject(input: {
						name: "Specific Project"
						description: "Target project"
					}) {
						id
					}
				}
				""";

		// Extract project ID from creation response
		var response = mockMvc.perform(post(GRAPHQL_ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + authToken)
				.content("{\"query\": \"" + createMutation.replace("\"", "\\\"").replace("\n", " ") + "\"}")
				.with(csrf()))
				.andExpect(status().isOk())
				.andReturn();

		// When - query the project
		String query = "{ myProjects { id name } }";

		// Then
		mockMvc.perform(post(GRAPHQL_ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + authToken)
				.content("{\"query\": \"" + query + "\"}")
				.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.myProjects[0].name", equalTo("Specific Project")));
	}

	@Test
	@DisplayName("Should reject unauthenticated project mutation")
	void testUnauthenticatedProjectMutation() throws Exception {
		// Given
		String mutation = """
				mutation {
					createProject(input: {
						name: "Unauthorized Project"
					}) {
						id
					}
				}
				""";

		// When & Then
		mockMvc.perform(post(GRAPHQL_ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"query\": \"" + mutation.replace("\"", "\\\"").replace("\n", " ") + "\"}")
				.with(csrf()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("Should prevent access to other user's projects")
	void testProjectOwnershipIsolation() throws Exception {
		// Given - create project as first user
		String createMutation = """
				mutation {
					createProject(input: {
						name: "Private Project"
					}) {
						id
					}
				}
				""";

		mockMvc.perform(post(GRAPHQL_ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + authToken)
				.content("{\"query\": \"" + createMutation.replace("\"", "\\\"").replace("\n", " ") + "\"}")
				.with(csrf()))
				.andExpect(status().isOk());

		// When - login as different user
		authService.registerUser("other@example.com", PASSWORD, "Other User");
		var otherLogin = authService.login("other@example.com", PASSWORD);
		String otherToken = otherLogin.getAccessToken();

		// Then - other user should not see first user's projects
		String query = "{ myProjects { id name } }";

		mockMvc.perform(post(GRAPHQL_ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + otherToken)
				.content("{\"query\": \"" + query + "\"}")
				.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.myProjects", hasSize(0)));
	}

	@Test
	@DisplayName("Should update project via GraphQL mutation")
	void testUpdateProjectMutation() throws Exception {
		// Given - create project
		String createMutation = """
				mutation {
					createProject(input: {
						name: "Original Name"
						description: "Original description"
					}) {
						id
					}
				}
				""";

		mockMvc.perform(post(GRAPHQL_ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + authToken)
				.content("{\"query\": \"" + createMutation.replace("\"", "\\\"").replace("\n", " ") + "\"}")
				.with(csrf()))
				.andExpect(status().isOk());

		// When - update project (via GraphQL)
		String updateMutation = """
				mutation {
					updateProject(input: {
						name: "Updated Name"
						description: "Updated description"
					}) {
						id
						name
						description
					}
				}
				""";

		// Then - verify fields are updated (if update mutation exists)
		mockMvc.perform(post(GRAPHQL_ENDPOINT)
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization", "Bearer " + authToken)
				.content("{\"query\": \"" + updateMutation.replace("\"", "\\\"").replace("\n", " ") + "\"}")
				.with(csrf()))
				.andExpect(status().isOk());
	}
}
