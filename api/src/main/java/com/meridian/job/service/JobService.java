package com.meridian.job.service;

import com.meridian.common.exception.InvalidRequestException;
import com.meridian.common.exception.ResourceNotFoundException;
import com.meridian.job.entity.ResearchJob;
import com.meridian.job.repository.ResearchJobRepository;
import com.meridian.project.entity.Project;
import com.meridian.project.repository.ProjectRepository;
import com.meridian.integration.client.FastAPIClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Job Service - Manages research job lifecycle and FastAPI integration
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class JobService {

    private final ResearchJobRepository jobRepository;
    private final ProjectRepository projectRepository;
    private final FastAPIClient fastAPIClient;

    /**
     * Create and submit a research job to FastAPI
     */
    public ResearchJob createAndSubmitJob(
        UUID projectId,
        UUID userId,
        String query,
        String llmProvider,
        String researchDepth,
        List<UUID> documentIds
    ) {
        // Validate project exists and user owns it
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (!project.getUserId().equals(userId)) {
            throw new InvalidRequestException("User does not own this project");
        }

        // Create job entity with PENDING status
        ResearchJob job = ResearchJob.builder()
            .projectId(projectId)
            .project(project)
            .userId(userId)
            .query(query)
            .status("PENDING")
            .llmProvider(llmProvider)
            .researchDepth(researchDepth)
            .progress(0)
            .createdAt(Instant.now())
            .build();

        ResearchJob savedJob = jobRepository.save(job);

        // Submit to FastAPI asynchronously
        submitToFastAPI(savedJob, documentIds);

        return savedJob;
    }

    /**
     * Get job by ID
     */
    public java.util.Optional<ResearchJob> getJobById(UUID jobId) {
        return jobRepository.findById(jobId)
            .filter(j -> j.getDeletedAt() == null);
    }

    /**
     * Get jobs for a project
     */
    public Page<ResearchJob> getJobsByProject(
        UUID projectId,
        String status,
        Pageable pageable
    ) {
        if (status != null && !status.isBlank()) {
            return jobRepository.findByProjectIdAndStatusAndDeletedAtIsNull(
                projectId, status, pageable
            );
        } else {
            return jobRepository.findByProjectIdAndDeletedAtIsNull(projectId, pageable);
        }
    }

    /**
     * Get jobs for current user
     */
    public Page<ResearchJob> getUserJobs(
        UUID userId,
        String status,
        Pageable pageable
    ) {
        if (status != null && !status.isBlank()) {
            return jobRepository.findByUserIdAndStatusAndDeletedAtIsNull(
                userId, status, pageable
            );
        } else {
            return jobRepository.findByUserIdAndDeletedAtIsNull(userId, pageable);
        }
    }

    /**
     * Cancel a job
     */
    public ResearchJob cancelJob(UUID jobId) {
        ResearchJob job = getJobById(jobId)
            .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        if (!job.getStatus().equals("PENDING") && !job.getStatus().equals("RUNNING")) {
            throw new InvalidRequestException("Cannot cancel job with status: " + job.getStatus());
        }

        job.setStatus("CANCELLED");
        job.setUpdatedAt(Instant.now());
        return jobRepository.save(job);
    }

    /**
     * Delete job (soft delete)
     */
    public void deleteJob(UUID jobId) {
        ResearchJob job = getJobById(jobId)
            .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        job.setDeletedAt(Instant.now());
        jobRepository.save(job);
    }

    /**
     * Update job status from FastAPI webhook
     */
    @Transactional
    public ResearchJob updateJobStatus(UUID jobId, String status, Integer progress, String errorMessage) {
        ResearchJob job = getJobById(jobId)
            .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        job.setStatus(status);
        if (progress != null) {
            job.setProgress(progress);
        }
        if (errorMessage != null) {
            job.setErrorMessage(errorMessage);
        }

        if ("RUNNING".equals(status) && job.getStartedAt() == null) {
            job.setStartedAt(Instant.now());
        }

        if ("COMPLETE".equals(status) || "FAILED".equals(status)) {
            job.setCompletedAt(Instant.now());
        }

        job.setUpdatedAt(Instant.now());
        return jobRepository.save(job);
    }

    /**
     * Submit job to FastAPI service using typed client
     */
    private void submitToFastAPI(ResearchJob job, List<UUID> documentIds) {
        try {
            // Use FastAPIClient for cleaner API interaction
            String fastApiJobId = fastAPIClient.submitResearchJob(
                job.getId(),
                job.getUserId(),
                job.getQuery(),
                job.getLlmProvider(),
                job.getResearchDepth(),
                documentIds
            );

            log.info("Job submitted to FastAPI: jobId={}, fastApiJobId={}", job.getId(), fastApiJobId);

            // Update job to RUNNING
            job.setStatus("RUNNING");
            job.setStartedAt(Instant.now());
            jobRepository.save(job);

        } catch (InvalidRequestException e) {
            log.error("Failed to submit job to FastAPI: {}", e.getMessage());
            job.setStatus("FAILED");
            job.setErrorMessage("FastAPI submission failed: " + e.getMessage());
            jobRepository.save(job);
        }
    }
}
