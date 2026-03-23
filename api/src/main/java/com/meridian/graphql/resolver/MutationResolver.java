package com.meridian.graphql.resolver;

import com.meridian.auth.dto.AuthResponse;
import com.meridian.auth.dto.LoginRequest;
import com.meridian.auth.dto.RegisterRequest;
import com.meridian.auth.dto.RefreshRequest;
import com.meridian.auth.service.AuthService;
import com.meridian.auth.util.JwtUtil;
import com.meridian.common.exception.InvalidRequestException;
import com.meridian.common.exception.ResourceNotFoundException;
import com.meridian.document.entity.Document;
import com.meridian.document.service.DocumentService;
import com.meridian.job.entity.ResearchJob;
import com.meridian.job.service.JobService;
import com.meridian.project.entity.Project;
import com.meridian.project.service.ProjectService;
import com.meridian.report.entity.Report;
import com.meridian.report.service.ReportService;
import com.meridian.chat.entity.ChatMessage;
import com.meridian.chat.service.ChatService;
import com.meridian.user.entity.User;
import com.meridian.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

import java.util.*;

/**
 * GraphQL Mutation Resolver
 * Handles all write operations (mutations)
 */
@Controller
@RequiredArgsConstructor
public class MutationResolver {

    private final AuthService authService;
    private final UserService userService;
    private final ProjectService projectService;
    private final JobService jobService;
    private final ReportService reportService;
    private final DocumentService documentService;
    private final ChatService chatService;
    private final JwtUtil jwtUtil;

    // ==================== Authentication Mutations ====================

    /**
     * Register new user
     */
    @MutationMapping
    public Map<String, Object> register(
        @Argument String email,
        @Argument String password,
        @Argument String name
    ) {
        RegisterRequest request = RegisterRequest.builder()
            .email(email)
            .password(password)
            .name(name)
            .build();

        com.meridian.auth.dto.AuthResponse response = authService.register(request);
        return buildAuthResponseMap(response);
    }

    /**
     * Login with email and password
     */
    @MutationMapping
    public Map<String, Object> login(
        @Argument String email,
        @Argument String password
    ) {
        LoginRequest request = LoginRequest.builder()
            .email(email)
            .password(password)
            .build();

        com.meridian.auth.dto.AuthResponse response = authService.login(request);
        return buildAuthResponseMap(response);
    }

    /**
     * Refresh access token
     */
    @MutationMapping
    public Map<String, Object> refreshToken(@Argument String refreshToken) {
        RefreshRequest request = RefreshRequest.builder()
            .refreshToken(refreshToken)
            .build();

        com.meridian.auth.dto.AuthResponse response = authService.refreshToken(request);
        return buildAuthResponseMap(response);
    }

    /**
     * Change password (invalidates all refresh tokens)
     */
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public User changePassword(
        @Argument String currentPassword,
        @Argument String newPassword
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());

        return authService.changePassword(userId, currentPassword, newPassword);
    }

    // ==================== Project Mutations ====================

    /**
     * Create a new project
     */
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public Project createProject(
        @Argument String name,
        @Argument(required = false) String description,
        @Argument(defaultValue = "false") boolean isPublic
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());

        return projectService.createProject(userId, name, description, isPublic);
    }

    /**
     * Update project details
     */
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public Project updateProject(
        @Argument String id,
        @Argument(required = false) String name,
        @Argument(required = false) String description,
        @Argument(required = false) Boolean isPublic,
        @Argument(required = false) String status
    ) {
        UUID projectId = UUID.fromString(id);
        return projectService.updateProject(projectId, name, description, isPublic, status);
    }

    /**
     * Delete project (soft delete)
     */
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public boolean deleteProject(@Argument String id) {
        projectService.deleteProject(UUID.fromString(id));
        return true;
    }

    // ==================== Research Job Mutations ====================

    /**
     * Submit a research query (creates new job, triggers FastAPI)
     */
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public ResearchJob submitResearchJob(
        @Argument String projectId,
        @Argument String query,
        @Argument(defaultValue = "BEDROCK") String llmProvider,
        @Argument(defaultValue = "STANDARD") String researchDepth,
        @Argument(required = false) List<String> documentIds
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());

        // Validate query length (10-500 chars per Requirement 5.2)
        if (query == null || query.trim().length() < 10 || query.length() > 500) {
            throw new InvalidRequestException("Query must be between 10 and 500 characters");
        }

        List<UUID> docIds = documentIds != null
            ? documentIds.stream().map(UUID::fromString).toList()
            : new ArrayList<>();

        return jobService.createAndSubmitJob(
            UUID.fromString(projectId),
            userId,
            query,
            llmProvider,
            researchDepth,
            docIds
        );
    }

    /**
     * Cancel a research job
     */
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public ResearchJob cancelJob(@Argument String jobId) {
        return jobService.cancelJob(UUID.fromString(jobId));
    }

    /**
     * Delete a job (soft delete)
     */
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public boolean deleteJob(@Argument String jobId) {
        jobService.deleteJob(UUID.fromString(jobId));
        return true;
    }

    // ==================== Document Mutations ====================

    /**
     * Upload a document to a project
     */
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public Document uploadDocument(
        @Argument String projectId,
        @Argument String filename,
        @Argument String mimeType,
        @Argument int fileSize,
        @Argument String content
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());

        // Validate file size (max 50MB)
        if (fileSize > 50 * 1024 * 1024) {
            throw new InvalidRequestException("File size exceeds 50MB limit");
        }

        // Validate MIME type
        if (!isValidMimeType(mimeType)) {
            throw new InvalidRequestException("Unsupported file type: " + mimeType);
        }

        byte[] decodedContent = Base64.getDecoder().decode(content);

        return documentService.uploadDocument(
            UUID.fromString(projectId),
            userId,
            filename,
            mimeType,
            decodedContent
        );
    }

    /**
     * Process a document (extract text, generate embeddings)
     * This may trigger async processing
     */
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public Document processDocument(@Argument String documentId) {
        return documentService.processDocument(UUID.fromString(documentId));
    }

    /**
     * Delete a document
     */
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public boolean deleteDocument(@Argument String documentId) {
        documentService.deleteDocument(UUID.fromString(documentId));
        return true;
    }

    // ==================== Chat Mutations ====================

    /**
     * Submit a chat query on a report
     * Returns response synchronously (or queues async if streaming)
     */
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public ChatMessage submitReportChat(
        @Argument String reportId,
        @Argument String content
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = UUID.fromString(auth.getName());

        if (content == null || content.trim().isEmpty() || content.length() > 5000) {
            throw new InvalidRequestException("Query must be between 1 and 5000 characters");
        }

        return chatService.sendChatMessage(
            UUID.fromString(reportId),
            userId,
            content
        );
    }

    /**
     * Update report visibility
     */
    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public Report updateReportVisibility(
        @Argument String reportId,
        @Argument boolean isPublic
    ) {
        return reportService.updateReportVisibility(UUID.fromString(reportId), isPublic);
    }

    // ==================== Helper Methods ====================

    /**
     * Build auth response map for GraphQL
     */
    private Map<String, Object> buildAuthResponseMap(com.meridian.auth.dto.AuthResponse response) {
        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", response.getAccessToken());
        result.put("refreshToken", response.getRefreshToken());
        result.put("expiresIn", response.getExpiresIn());
        result.put("tokenType", response.getTokenType());
        result.put("user", response.getUser());
        return result;
    }

    /**
     * Validate file MIME type
     */
    private boolean isValidMimeType(String mimeType) {
        Set<String> validTypes = Set.of(
            "application/pdf",
            "text/plain",
            "text/markdown",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/csv"
        );
        return validTypes.contains(mimeType);
    }
}
