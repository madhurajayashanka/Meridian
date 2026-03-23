package com.meridian.job.controller;

import com.meridian.job.entity.ResearchJob;
import com.meridian.job.service.ResearchJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GraphQL controller for research job operations.
 * Requirement 4: Research Job Submission
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ResearchJobGraphQLController {

    private final ResearchJobService jobService;

    /**
     * Query all jobs for a project.
     */
    @QueryMapping
    public List<ResearchJob> jobs(
        @Argument(required = false) String projectId
    ) {
        UUID userId = getCurrentUserId();
        if (projectId == null || projectId.isEmpty()) {
            log.info("Fetching all jobs for user {}", userId);
            return jobService.getJobsByUserId(userId);
        }
        log.info("Fetching jobs for project {} (user: {})", projectId, userId);
        return jobService.getJobsByProjectId(UUID.fromString(projectId));
    }

    /**
     * Query a specific job by ID.
     */
    @QueryMapping
    public ResearchJob job(
        @Argument String id
    ) {
        UUID userId = getCurrentUserId();
        return jobService.getJobByIdAndUserId(UUID.fromString(id), userId)
            .orElseThrow(() -> new IllegalArgumentException("Job not found or access denied"));
    }

    /**
     * Create and submit a new research job.
     */
    @MutationMapping
    public ResearchJob createResearchJob(
        @Argument Map<String, Object> input
    ) {
        UUID userId = getCurrentUserId();
        
        String projectId = (String) input.get("projectId");
        String query = (String) input.get("query");
        String llmProvider = (String) input.getOrDefault("llmProvider", "BEDROCK");
        String researchDepth = (String) input.getOrDefault("researchDepth", "STANDARD");
        
        @SuppressWarnings("unchecked")
        List<String> documentIds = (List<String>) input.get("documentIds");
        List<UUID> docIds = documentIds != null
            ? documentIds.stream().map(UUID::fromString).toList()
            : List.of();

        log.info("Creating research job for project {} (user: {})", projectId, userId);

        return jobService.createResearchJob(
            userId,
            UUID.fromString(projectId),
            query,
            llmProvider,
            researchDepth,
            docIds
        );
    }

    /**
     * Cancel a running research job.
     */
    @MutationMapping
    public Boolean cancelResearchJob(
        @Argument String id
    ) {
        UUID userId = getCurrentUserId();
        UUID jobId = UUID.fromString(id);
        log.info("Cancelling research job {} (user: {})", jobId, userId);

        jobService.cancelJob(jobId, userId);
        return true;
    }

    /**
     * Get current authenticated user ID.
     */
    private UUID getCurrentUserId() {
        String principal = SecurityContextHolder.getContext()
            .getAuthentication()
            .getName();
        return UUID.fromString(principal);
    }
}
