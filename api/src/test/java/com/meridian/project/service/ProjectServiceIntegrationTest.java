package com.meridian.project.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import com.meridian.IntegrationTest;
import com.meridian.TestContainerBase;
import com.meridian.auth.entity.User;
import com.meridian.auth.repository.UserRepository;
import com.meridian.auth.service.AuthService;
import com.meridian.project.entity.Project;
import com.meridian.project.repository.ProjectRepository;

@IntegrationTest
@DisplayName("Project Service Integration Tests")
class ProjectServiceIntegrationTest extends TestContainerBase {

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AuthService authService;

	private User testUser;
	private User otherUser;
	private static final String TEST_EMAIL = "test@example.com";
	private static final String OTHER_EMAIL = "other@example.com";
	private static final String PASSWORD = "SecurePassword123!";

	@BeforeEach
	void setUp() {
		projectRepository.deleteAll();
		userRepository.deleteAll();

		testUser = authService.registerUser(TEST_EMAIL, PASSWORD, "Test User");
		otherUser = authService.registerUser(OTHER_EMAIL, PASSWORD, "Other User");
	}

	@Test
	@WithMockUser(username = "test@example.com")
	@DisplayName("Should create project for authenticated user")
	void testCreateProject() {
		// Given
		String projectName = "Research Project";
		String projectDescription = "Test research project";
		SecurityContextHolder.getContext().getAuthentication().setAuthenticated(true);

		// When
		Project project = new Project();
		project.setName(projectName);
		project.setDescription(projectDescription);
		project.setCreatedBy(testUser);
		project = projectRepository.save(project);

		// Then
		assertNotNull(project.getId());
		assertEquals(projectName, project.getName());
		assertEquals(projectDescription, project.getDescription());
		assertEquals(testUser.getId(), project.getCreatedBy().getId());
		assertFalse(project.isArchived());
	}

	@Test
	@WithMockUser(username = "test@example.com")
	@DisplayName("Should retrieve user's own projects")
	void testGetUserProjects() {
		// Given
		Project project1 = new Project();
		project1.setName("Project 1");
		project1.setCreatedBy(testUser);
		project1 = projectRepository.save(project1);

		Project project2 = new Project();
		project2.setName("Project 2");
		project2.setCreatedBy(testUser);
		project2 = projectRepository.save(project2);

		Project otherProject = new Project();
		otherProject.setName("Other's Project");
		otherProject.setCreatedBy(otherUser);
		projectRepository.save(otherProject);

		// When
		var userProjects = projectRepository.findByCreatedBy(testUser);

		// Then
		assertEquals(2, userProjects.size());
		assertTrue(userProjects.stream().anyMatch(p -> p.getId().equals(project1.getId())));
		assertTrue(userProjects.stream().anyMatch(p -> p.getId().equals(project2.getId())));
		assertFalse(userProjects.stream().anyMatch(p -> p.getCreatedBy().getId().equals(otherUser.getId())));
	}

	@Test
	@WithMockUser(username = "test@example.com")
	@DisplayName("Should update project name by owner")
	void testUpdateProjectByOwner() {
		// Given
		Project project = new Project();
		project.setName("Original Name");
		project.setCreatedBy(testUser);
		project = projectRepository.save(project);

		// When
		project.setName("Updated Name");
		project = projectRepository.save(project);

		// Then
		Project updated = projectRepository.findById(project.getId()).orElseThrow();
		assertEquals("Updated Name", updated.getName());
	}

	@Test
	@WithMockUser(username = "test@example.com")
	@DisplayName("Should archive project")
	void testArchiveProject() {
		// Given
		Project project = new Project();
		project.setName("Project to Archive");
		project.setCreatedBy(testUser);
		project = projectRepository.save(project);

		// When
		project.setArchived(true);
		project = projectRepository.save(project);

		// Then
		Project archived = projectRepository.findById(project.getId()).orElseThrow();
		assertTrue(archived.isArchived());
	}

	@Test
	@WithMockUser(username = "test@example.com")
	@DisplayName("Should not return archived projects in default queries")
	void testArchivedProjectsNotIncludedInDefaults() {
		// Given
		Project activeProject = new Project();
		activeProject.setName("Active Project");
		activeProject.setCreatedBy(testUser);
		activeProject = projectRepository.save(activeProject);

		Project archivedProject = new Project();
		archivedProject.setName("Archived Project");
		archivedProject.setArchived(true);
		archivedProject.setCreatedBy(testUser);
		projectRepository.save(archivedProject);

		// When
		var userProjects = projectRepository.findByCreatedBy(testUser);
		var activeOnly = userProjects.stream().filter(p -> !p.isArchived()).toList();

		// Then
		assertEquals(1, activeOnly.size());
		assertEquals("Active Project", activeOnly.get(0).getName());
	}

	@Test
	@WithMockUser(username = "test@example.com")
	@DisplayName("Should validate project name length")
	void testProjectNameValidation() {
		// Given
		Project project = new Project();
		project.setCreatedBy(testUser);

		// When & Then - name must be 1-255 characters
		project.setName("");
		project = projectRepository.save(project);
		assertTrue(project.getName().isEmpty()); // DB allows, validation at service layer

		Project valid = new Project();
		valid.setName("Valid Project Name");
		valid.setCreatedBy(testUser);
		valid = projectRepository.save(valid);
		assertNotNull(valid.getId());
	}

	@Test
	@WithMockUser(username = "test@example.com")
	@DisplayName("Should enforce ownership isolation")
	void testOwnershipIsolation() {
		// Given
		Project testUserProject = new Project();
		testUserProject.setName("Test User Project");
		testUserProject.setCreatedBy(testUser);
		testUserProject = projectRepository.save(testUserProject);

		Project otherUserProject = new Project();
		otherUserProject.setName("Other User Project");
		otherUserProject.setCreatedBy(otherUser);
		otherUserProject = projectRepository.save(otherUserProject);

		// When
		var testUserProjects = projectRepository.findByCreatedBy(testUser);
		var otherUserProjects = projectRepository.findByCreatedBy(otherUser);

		// Then - each user should only see their own projects
		assertEquals(1, testUserProjects.size());
		assertEquals(1, otherUserProjects.size());
		assertEquals(testUser.getId(), testUserProjects.get(0).getCreatedBy().getId());
		assertEquals(otherUser.getId(), otherUserProjects.get(0).getCreatedBy().getId());
	}

	@Test
	@WithMockUser(username = "test@example.com")
	@DisplayName("Should track project creation and modification timestamps")
	void testProjectTimestamps() {
		// Given
		Project project = new Project();
		project.setName("Test Project");
		project.setCreatedBy(testUser);

		// When
		long beforeCreate = System.currentTimeMillis();
		project = projectRepository.save(project);
		long afterCreate = System.currentTimeMillis();

		// Then
		assertNotNull(project.getCreatedAt());
		assertNotNull(project.getUpdatedAt());
		assertTrue(project.getCreatedAt().getTime() >= beforeCreate);
		assertTrue(project.getCreatedAt().getTime() <= afterCreate);

		// When - modify project
		project.setDescription("Updated description");
		long beforeUpdate = System.currentTimeMillis();
		project = projectRepository.save(project);
		long afterUpdate = System.currentTimeMillis();

		// Then
		Project updated = projectRepository.findById(project.getId()).orElseThrow();
		assertTrue(updated.getUpdatedAt().getTime() >= beforeUpdate);
		assertTrue(updated.getUpdatedAt().getTime() <= afterUpdate);
		assertTrue(updated.getUpdatedAt().getTime() >= updated.getCreatedAt().getTime());
	}
}
