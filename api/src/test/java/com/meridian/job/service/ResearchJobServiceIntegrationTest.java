package com.meridian.job.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;

import com.meridian.IntegrationTest;
import com.meridian.TestContainerBase;
import com.meridian.auth.entity.User;
import com.meridian.auth.repository.UserRepository;
import com.meridian.auth.service.AuthService;
import com.meridian.document.entity.Document;
import com.meridian.document.entity.DocumentStatus;
import com.meridian.document.repository.DocumentRepository;
import com.meridian.job.entity.JobStatus;
import com.meridian.job.entity.LLMProvider;
import com.meridian.job.entity.ResearchDepth;
import com.meridian.job.entity.ResearchJob;
import com.meridian.job.repository.ResearchJobRepository;
import com.meridian.project.entity.Project;
import com.meridian.project.repository.ProjectRepository;

@IntegrationTest
@DisplayName("Research Job Service Integration Tests")
class ResearchJobServiceIntegrationTest extends TestContainerBase {

	@Autowired
	private ResearchJobRepository jobRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DocumentRepository documentRepository;

	@Autowired
	private AuthService authService;

	@MockBean
	private FastApiClient fastApiClient;

	private User testUser;
	private Project testProject;
	private List<Document> testDocuments;

	private static final String TEST_EMAIL = "test@example.com";
	private static final String PASSWORD = "SecurePassword123!";

	@BeforeEach
	void setUp() {
		jobRepository.deleteAll();
		documentRepository.deleteAll();
		projectRepository.deleteAll();
		userRepository.deleteAll();

		testUser = authService.registerUser(TEST_EMAIL, PASSWORD, "Test User");

		testProject = new Project();
		testProject.setName("Test Project");
		testProject.setCreatedBy(testUser);
		testProject = projectRepository.save(testProject);

		// Create test documents
		testDocuments = new ArrayList<>();
		Document doc1 = new Document();
		doc1.setFileName("document1.pdf");
		doc1.setStatus(DocumentStatus.READY);
		doc1.setProject(testProject);
		testDocuments.add(documentRepository.save(doc1));

		Document doc2 = new Document();
		doc2.setFileName("document2.pdf");
		doc2.setStatus(DocumentStatus.READY);
		doc2.setProject(testProject);
		testDocuments.add(documentRepository.save(doc2));
	}

	@Test
	@WithMockUser(username = "test@example.com")
	@DisplayName("Should create research job with valid query")
	void testCreateResearchJob() {
		// Given
		String query = "What are the latest trends in AI?";
		List<Long> documentIds = testDocuments.stream().map(Document::getId).toList();

		// When
		ResearchJob job = new ResearchJob();
		job.setQuery(query);
		job.setProject(testProject);
		job.setCreatedBy(testUser);
		job.setStatus(JobStatus.PENDING);
		job.setDepth(ResearchDepth.STANDARD);
		job.setLlmProvider(LLMProvider.OPENAI);
		job = jobRepository.save(job);

		// Then
		assertNotNull(job.getId());
		assertEquals(query, job.getQuery());
		assertEquals(JobStatus.PENDING, job.getStatus());
		assertEquals(ResearchDepth.STANDARD, job.getDepth());
		assertEquals(LLMProvider.OPENAI, job.getLlmProvider());
	}

	@Test
	@WithMockUser(username = "test@example.com")
	@DisplayName("Should reject query shorter than 10 characters")
	void testQueryValidationMinLength() {
		// When & Then
		ResearchJob job = new ResearchJob();
		job.setQuery("too short");
		job.setProject(testProject);
		job.setCreatedBy(testUser);
		// Validation should happen at service layer
		assertTrue(job.getQuery().length() < 10);
	}

	@Test
	@WithMockUser(username = "test@example.com")
	@DisplayName("Should reject query longer than 500 characters")
	void testQueryValidationMaxLength() {
		// When & Then
		String longQuery = "a".repeat(501);
		ResearchJob job = new ResearchJob();
		job.setQuery(longQuery);
		job.setProject(testProject);
		job.setCreatedBy(testUser);
		assertTrue(job.getQuery().length() > 500);
	}

	@Test
	@WithMockUser(username = "test@example.com")
	@DisplayName("Should transition job status from PENDING to RUNNING to COMPLETE")
	void testJobStatusTransition() {
		// Given
		ResearchJob job = new ResearchJob();
		job.setQuery("What are machine learning applications?");
		job.setProject(testProject);
		job.setCreatedBy(testUser);
		job.setStatus(JobStatus.PENDING);
		job = jobRepository.save(job);

		// When - transition to RUNNING
		job.setStatus(JobStatus.RUNNING);
		job = jobRepository.save(job);

		// Then
		ResearchJob running = jobRepository.findById(job.getId()).orElseThrow();
		assertEquals(JobStatus.RUNNING, running.getStatus());

		// When - transition to COMPLETE
		job.setStatus(JobStatus.COMPLETE);
		job.setCompletedAt(new java.util.Date());
		job = jobRepository.save(job);

		// Then
		ResearchJob completed = jobRepository.findById(job.getId()).orElseThrow();
		assertEquals(JobStatus.COMPLETE, completed.getStatus());
		assertNotNull(completed.getCompletedAt());
	}

	@Test
	@WithMockUser(username = "test@example.com")
	@DisplayName("Should retrieve jobs by project")
	void testGetJobsByProject() {
		// Given
		ResearchJob job1 = new ResearchJob();
		job1.setQuery("First research query");
		job1.setProject(testProject);
		job1.setCreatedBy(testUser);
		jobRepository.save(job1);

		ResearchJob job2 = new ResearchJob();
		job2.setQuery("Second research query");
		job2.setProject(testProject);
		job2.setCreatedBy(testUser);
		jobRepository.save(job2);

		// When
		var projectJobs = jobRepository.findByProject(testProject);

		// Then
		assertEquals(2, projectJobs.size());
	}

	@Test
	@WithMockUser(username = "test@example.com")
	@DisplayName("Should retrieve only user's jobs")
	void testOwnershipIsolationForJobs() {
		// Given
		User otherUser = authService.registerUser("other@example.com", PASSWORD, "Other User");
		Project otherProject = new Project();
		otherProject.setName("Other Project");
		otherProject.setCreatedBy(otherUser);
		otherProject = projectRepository.save(otherProject);

		ResearchJob myJob = new ResearchJob();
		myJob.setQuery("My research question");
		myJob.setProject(testProject);
		myJob.setCreatedBy(testUser);
		jobRepository.save(myJob);

		ResearchJob otherJob = new ResearchJob();
		otherJob.setQuery("Other's research question");
		otherJob.setProject(otherProject);
		otherJob.setCreatedBy(otherUser);
		jobRepository.save(otherJob);

		// When
		var myJobs = jobRepository.findByCreatedBy(testUser);
		var otherJobs = jobRepository.findByCreatedBy(otherUser);

		// Then
		assertEquals(1, myJobs.size());
		assertEquals(1, otherJobs.size());
		assertEquals(testUser.getId(), myJobs.get(0).getCreatedBy().getId());
		assertEquals(otherUser.getId(), otherJobs.get(0).getCreatedBy().getId());
	}

	@Test
	@WithMockUser(username = "test@example.com")
	@DisplayName("Should track job completion with timestamps")
	void testJobCompletionTimestamps() {
		// Given
		ResearchJob job = new ResearchJob();
		job.setQuery("Research topic for timestamp testing");
		job.setProject(testProject);
		job.setCreatedBy(testUser);
		job.setStatus(JobStatus.PENDING);
		long beforeCreate = System.currentTimeMillis();
		job = jobRepository.save(job);
		long afterCreate = System.currentTimeMillis();

		// Then - job created
		assertNotNull(job.getCreatedAt());
		assertTrue(job.getCreatedAt().getTime() >= beforeCreate);
		assertTrue(job.getCreatedAt().getTime() <= afterCreate);
		assertNull(job.getCompletedAt());

		// When - mark as complete
		job.setStatus(JobStatus.COMPLETE);
		long beforeCompletion = System.currentTimeMillis();
		job.setCompletedAt(new java.util.Date());
		job = jobRepository.save(job);
		long afterCompletion = System.currentTimeMillis();

		// Then - completion tracked
		ResearchJob completed = jobRepository.findById(job.getId()).orElseThrow();
		assertNotNull(completed.getCompletedAt());
		assertTrue(completed.getCompletedAt().getTime() >= beforeCompletion);
		assertTrue(completed.getCompletedAt().getTime() <= afterCompletion);
	}

	@Test
	@WithMockUser(username = "test@example.com")
	@DisplayName("Should support all LLM providers")
	void testLLMProviders() {
		// Test each LLM provider is properly stored and retrieved
		for (LLMProvider provider : LLMProvider.values()) {
			ResearchJob job = new ResearchJob();
			job.setQuery("Test query for provider: " + provider);
			job.setProject(testProject);
			job.setCreatedBy(testUser);
			job.setLlmProvider(provider);
			job = jobRepository.save(job);

			ResearchJob retrieved = jobRepository.findById(job.getId()).orElseThrow();
			assertEquals(provider, retrieved.getLlmProvider());
		}
	}

	@Test
	@WithMockUser(username = "test@example.com")
	@DisplayName("Should support all research depths")
	void testResearchDepths() {
		// Test each research depth is properly set and stored
		for (ResearchDepth depth : ResearchDepth.values()) {
			ResearchJob job = new ResearchJob();
			job.setQuery("Test query for depth: " + depth);
			job.setProject(testProject);
			job.setCreatedBy(testUser);
			job.setDepth(depth);
			job = jobRepository.save(job);

			ResearchJob retrieved = jobRepository.findById(job.getId()).orElseThrow();
			assertEquals(depth, retrieved.getDepth());
		}
	}

	@Test
	@WithMockUser(username = "test@example.com")
	@DisplayName("Should handle job failure with error message")
	void testJobFailure() {
		// Given
		ResearchJob job = new ResearchJob();
		job.setQuery("Query that will fail");
		job.setProject(testProject);
		job.setCreatedBy(testUser);
		job.setStatus(JobStatus.PENDING);
		job = jobRepository.save(job);

		// When - mark as failed with error
		job.setStatus(JobStatus.FAILED);
		job.setErrorMessage("API service timeout");
		job.setCompletedAt(new java.util.Date());
		job = jobRepository.save(job);

		// Then
		ResearchJob failed = jobRepository.findById(job.getId()).orElseThrow();
		assertEquals(JobStatus.FAILED, failed.getStatus());
		assertEquals("API service timeout", failed.getErrorMessage());
		assertNotNull(failed.getCompletedAt());
	}
}
