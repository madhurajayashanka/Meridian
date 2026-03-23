package com.meridian.graphql.resolver;

import com.meridian.common.exception.ResourceNotFoundException;
import com.meridian.project.entity.Project;
import com.meridian.project.service.ProjectService;
import com.meridian.job.entity.ResearchJob;
import com.meridian.job.service.JobService;
import com.meridian.report.entity.Report;
import com.meridian.report.service.ReportService;
import com.meridian.document.entity.Document;
import com.meridian.document.service.DocumentService;
import com.meridian.chat.entity.ChatMessage;
import com.meridian.chat.service.ChatService;
import com.meridian.job.entity.AgentLog;
import com.meridian.job.repository.AgentLogRepository;
import com.meridian.user.entity.User;
import com.meridian.user.service.UserService;
import com.meridian.auth.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

import java.util.*;

/**
 * GraphQL Query Resolver
 * Handles all read-only GraphQL operations (queries)
 */
@Controller
@RequiredArgsConstructor
public class QueryResolver {

    private final UserService userService;
    private final ProjectService projectService;
    private final JobService jobService;
    private final ReportService reportService;
    private final DocumentService documentService;
    private final ChatService chatService;
    private final AgentLogRepository agentLogRepository;

    /**
     * Get current authenticated user
     */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public User me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = auth.getName();
        return userService.getUserById(UUID.fromString(userId))
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    /**
     * Get user by ID
     */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public User user(@Argument String id) {
        return userService.getUserById(UUID.fromString(id))
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    /**
     * Get project by ID (authorization checks in ProjectService)
     */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Project project(@Argument String id) {
        return projectService.getProjectById(UUID.fromString(id))
            .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
    }

    /**
     * List projects for current user with pagination
     */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> projects(
        @Argument(required = false) String search,
        @Argument(required = false) String status,
        @Argument(defaultValue = "10") int pageSize,
        @Argument(defaultValue = "0") int pageNumber
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());

        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<Project> page = projectService.getUserProjects(userId, search, status, pageable);

        return buildProjectConnection(page);
    }

    /**
     * Get job by ID
     */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public ResearchJob job(@Argument String id) {
        return jobService.getJobById(UUID.fromString(id))
            .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + id));
    }

    /**
     * List jobs in a project
     */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> jobsByProject(
        @Argument String projectId,
        @Argument(required = false) String status,
        @Argument(defaultValue = "10") int pageSize,
        @Argument(defaultValue = "0") int pageNumber
    ) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<ResearchJob> page = jobService.getJobsByProject(UUID.fromString(projectId), status, pageable);

        return buildJobConnection(page);
    }

    /**
     * List all jobs for current user
     */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> myJobs(
        @Argument(required = false) String status,
        @Argument(defaultValue = "10") int pageSize,
        @Argument(defaultValue = "0") int pageNumber
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());

        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<ResearchJob> page = jobService.getUserJobs(userId, status, pageable);

        return buildJobConnection(page);
    }

    /**
     * Get report by ID
     */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Report report(@Argument String id) {
        return reportService.getReportById(UUID.fromString(id))
            .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + id));
    }

    /**
     * List reports in a project
     */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<Report> reportsByProject(
        @Argument String projectId,
        @Argument(defaultValue = "10") int pageSize,
        @Argument(defaultValue = "0") int pageNumber
    ) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        return reportService.getReportsByProject(UUID.fromString(projectId), pageable).getContent();
    }

    /**
     * Get document by ID
     */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Document document(@Argument String id) {
        return documentService.getDocumentById(UUID.fromString(id))
            .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));
    }

    /**
     * List documents in a project
     */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> documentsByProject(
        @Argument String projectId,
        @Argument(required = false) String status,
        @Argument(defaultValue = "10") int pageSize,
        @Argument(defaultValue = "0") int pageNumber
    ) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<Document> page = documentService.getDocumentsByProject(
            UUID.fromString(projectId),
            status,
            pageable
        );

        return buildDocumentConnection(page);
    }

    /**
     * Get chat messages for a report
     */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<ChatMessage> chatMessages(
        @Argument String reportId,
        @Argument(defaultValue = "30") int pageSize,
        @Argument(defaultValue = "0") int pageNumber
    ) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        return chatService.getChatMessages(UUID.fromString(reportId), pageable).getContent();
    }

    /**
     * Get agent logs for a job (for debugging and monitoring progress)
     */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<AgentLog> agentLogs(@Argument String jobId) {
        return agentLogRepository.findByJobIdOrderByCreatedAtAsc(UUID.fromString(jobId));
    }

    /**
     * Health check endpoint
     */
    @QueryMapping
    public String health() {
        return "GraphQL API is healthy";
    }

    // Helper methods for building paginated responses

    private Map<String, Object> buildProjectConnection(Page<Project> page) {
        Map<String, Object> result = new HashMap<>();
        result.put("nodes", page.getContent());
        result.put("pageInfo", buildPageInfo(page));
        return result;
    }

    private Map<String, Object> buildJobConnection(Page<ResearchJob> page) {
        Map<String, Object> result = new HashMap<>();
        result.put("nodes", page.getContent());
        result.put("pageInfo", buildPageInfo(page));
        return result;
    }

    private Map<String, Object> buildDocumentConnection(Page<Document> page) {
        Map<String, Object> result = new HashMap<>();
        result.put("nodes", page.getContent());
        result.put("pageInfo", buildPageInfo(page));
        return result;
    }

    private Map<String, Object> buildPageInfo(Page<?> page) {
        Map<String, Object> pageInfo = new HashMap<>();
        pageInfo.put("totalCount", page.getTotalElements());
        pageInfo.put("hasNextPage", page.hasNext());
        pageInfo.put("pageSize", page.getSize());
        pageInfo.put("pageNumber", page.getNumber());
        return pageInfo;
    }
}
