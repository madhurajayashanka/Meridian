package com.meridian.job.service;

import com.meridian.auth.entity.User;
import com.meridian.auth.repository.UserRepository;
import com.meridian.common.exception.ForbiddenException;
import com.meridian.common.exception.ValidationException;
import com.meridian.document.entity.Document;
import com.meridian.document.repository.DocumentRepository;
import com.meridian.job.client.FastApiClient;
import com.meridian.job.entity.LLMProvider;
import com.meridian.job.entity.ResearchDepth;
import com.meridian.job.entity.ResearchJob;
import com.meridian.job.repository.ResearchJobRepository;
import com.meridian.project.entity.Project;
import com.meridian.project.repository.ProjectRepository;
import com.meridian.report.entity.Report;
import com.meridian.report.repository.ReportRepository;
import com.meridian.report.service.ReportContentStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for research job management and orchestration.
 * Requirement 4: Research Job Submission & Requirement 5: Multi-Agent Orchestration
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ResearchJobService {

    private final ResearchJobRepository jobRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final FastApiClient fastApiClient;
    private final ReportRepository reportRepository;
    private final ReportContentStorageService reportContentStorageService;

    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(\\d+)]");

    /**
     * Create and submit a research job.
     *
     * @param userId User ID
     * @param projectId Project ID
     * @param query Research query
     * @param llmProvider LLM provider (BEDROCK or OPENAI)
     * @param researchDepth Research depth (QUICK, STANDARD, or DEEP)
     * @param documentIds Optional document IDs to augment research
     * @return Created research job
     */
    public ResearchJob createResearchJob(
        UUID userId,
        UUID projectId,
        String query,
        String llmProvider,
        String researchDepth,
        List<UUID> documentIds
    ) {
        // Validate user
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ValidationException("User not found"));

        // Validate project and ownership
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new ForbiddenException("Project not found or access denied"));

        // Validate query length
        if (query == null || query.length() < 10 || query.length() > 500) {
            throw new ValidationException("Query must be between 10 and 500 characters");
        }

        // Validate LLM provider
        LLMProvider provider;
        try {
            provider = LLMProvider.fromValue(llmProvider.toUpperCase())
                .orElse(LLMProvider.BEDROCK);
        } catch (Exception e) {
            provider = LLMProvider.BEDROCK;
        }

        // Validate research depth
        ResearchDepth depth;
        try {
            depth = ResearchDepth.fromValue(researchDepth.toUpperCase())
                .orElse(ResearchDepth.STANDARD);
        } catch (Exception e) {
            depth = ResearchDepth.STANDARD;
        }

        // Validate and fetch documents
        List<Document> documents = validateAndFetchDocuments(userId, projectId, documentIds);

        // Create research job
        ResearchJob job = ResearchJob.builder()
            .user(user)
            .project(project)
            .query(query)
            .status("PENDING")
            .llmProvider(provider.getValue())
            .researchDepth(depth.getValue())
            .build();

        ResearchJob savedJob = jobRepository.save(job);
        log.info("Research job {} created for user {}", savedJob.getId(), userId);

        // Submit to AI service
        try {
            submitJobToAiService(savedJob, documents);
        } catch (Exception e) {
            log.error("Failed to submit job {} to AI service: {}", savedJob.getId(), e.getMessage());
            // Update job status to failed but don't throw - let frontend know
            savedJob.setStatus("FAILED");
            savedJob.setErrorMessage("Failed to submit to AI service: " + e.getMessage());
            jobRepository.save(savedJob);
            throw new ValidationException("Failed to submit research job: " + e.getMessage());
        }

        return savedJob;
    }

    /**
     * Validate and fetch documents for research job.
     */
    private List<Document> validateAndFetchDocuments(
        UUID userId,
        UUID projectId,
        List<UUID> documentIds
    ) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }

        if (documentIds.size() > 5) {
            throw new ValidationException("Maximum 5 documents allowed per research job");
        }

        // Fetch and validate documents belong to user and project
        List<Document> documents = documentRepository.findAllByIds(documentIds);

        if (documents.size() != documentIds.size()) {
            throw new ValidationException("Some documents not found");
        }

        // Verify all documents belong to the project
        boolean allBelongToProject = documents.stream()
            .allMatch(doc -> doc.getProject().getId().equals(projectId));

        if (!allBelongToProject) {
            throw new ForbiddenException("Some documents don't belong to the specified project");
        }

        // Verify all documents are ready
        boolean allReady = documents.stream()
            .allMatch(Document::isReady);

        if (!allReady) {
            throw new ValidationException("Some documents are not ready for use (still processing or failed)");
        }

        return documents;
    }

    /**
     * Submit job to AI service via REST.
     */
    private void submitJobToAiService(ResearchJob job, List<Document> documents) {
        List<String> documentIds = documents.stream()
            .map(d -> d.getId().toString())
            .collect(Collectors.toList());

        try {
            FastApiClient.FastApiJobResponse response = fastApiClient.startJob(
                job.getId().toString(),
                job.getProject().getId().toString(),
                job.getUser().getId().toString(),
                job.getQuery(),
                job.getLlmProvider(),
                job.getResearchDepth(),
                documentIds
            );

            // Update job status to RUNNING
            job.setStatus("RUNNING");
            job.setStartedAt(LocalDateTime.now());
            jobRepository.save(job);

            log.info("Job {} submitted to AI service with status: {}", job.getId(), response.status);
        } catch (Exception e) {
            log.error("Error submitting job to FastAPI: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Get a research job by ID with ownership check.
     */
    public Optional<ResearchJob> getJobByIdAndUserId(UUID jobId, UUID userId) {
        return jobRepository.findByIdAndUserId(jobId, userId);
    }

    /**
     * Get all jobs for a user.
     */
    public List<ResearchJob> getJobsByUserId(UUID userId) {
        return jobRepository.findAllByUserId(userId);
    }

    /**
     * Get all jobs for a project.
     */
    public List<ResearchJob> getJobsByProjectId(UUID projectId) {
        return jobRepository.findAllByProjectId(projectId);
    }

    /**
     * Cancel a running research job.
     */
    public void cancelJob(UUID jobId, UUID userId) {
        ResearchJob job = jobRepository.findByIdAndUserId(jobId, userId)
            .orElseThrow(() -> new ForbiddenException("Job not found or access denied"));

        if (!job.isRunning()) {
            throw new ValidationException("Job is not running and cannot be cancelled");
        }

        try {
            fastApiClient.cancelJob(job.getId().toString());
        } catch (Exception e) {
            log.warn("Error cancelling job on AI service: {}", e.getMessage());
        }

        job.setStatus("CANCELLED");
        job.setUpdatedAt(LocalDateTime.now());
        jobRepository.save(job);

        log.info("Job {} cancelled for user {}", jobId, userId);
    }

    /**
     * Update job status from AI service.
     * Called by webhook or polling from AI service.
     */
    public void updateJobStatus(UUID jobId, String status, String errorMessage) {
        ResearchJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new ValidationException("Job not found"));

        job.setStatus(status);
        if (errorMessage != null) {
            job.setErrorMessage(errorMessage);
        }

        if ("COMPLETE".equals(status)) {
            job.setCompletedAt(LocalDateTime.now());
        }

        jobRepository.save(job);
        log.info("Job {} status updated to {}", jobId, status);
    }

    /**
     * Complete a job and create its associated Report row.
     * Called by the AI service webhook when the workflow finishes.
     */
    public void completeJobWithReport(
        UUID jobId,
        UUID reportId,
        String providedTitle,
        String content,
        String storageUrl,
        Integer providedWordCount,
        Integer providedCitationCount,
        Double providedCriticScore,
        Integer providedRevisionCount
    ) {
        ResearchJob job = jobRepository.findById(jobId)
            .orElseThrow(() -> new ValidationException("Job not found: " + jobId));

        job.setStatus("COMPLETE");
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);

        // Avoid duplicate reports (idempotent webhook)
        if (reportRepository.findByJobId(jobId).isPresent()) {
            log.info("Report already exists for job {}, skipping creation", jobId);
            return;
        }

        UUID effectiveReportId = reportId != null ? reportId : UUID.randomUUID();
        String query = job.getQuery();
        String title = deriveTitle(providedTitle, content, query);
        String s3Key = resolveStorageKey(effectiveReportId, storageUrl, content);

        Report report = Report.builder()
            .id(effectiveReportId)
            .job(job)
            .project(job.getProject())
            .user(job.getUser())
            .title(title)
            .s3Key(s3Key)
            .wordCount(resolveWordCount(providedWordCount, content))
            .citationCount(resolveCitationCount(providedCitationCount, content))
            .criticScore(resolveCriticScore(providedCriticScore))
            .revisionCount(providedRevisionCount != null ? providedRevisionCount : 0)
            .isPublic(false)
            .build();

        reportRepository.save(report);
        log.info("Report {} created for job {}", report.getId(), jobId);
    }

    /**
     * Check health of AI service.
     */
    public boolean isAiServiceHealthy() {
        return fastApiClient.isHealthy();
    }

    private String deriveTitle(String providedTitle, String content, String query) {
        if (providedTitle != null && !providedTitle.isBlank()) {
            return providedTitle.trim();
        }

        if (content != null) {
            for (String line : content.split("\\R")) {
                if (line.startsWith("# ")) {
                    return line.substring(2).trim();
                }
            }
        }

        return "Research: " + query.substring(0, Math.min(query.length(), 490));
    }

    private String resolveStorageKey(UUID reportId, String storageUrl, String content) {
        if (storageUrl != null && !storageUrl.isBlank()) {
            return storageUrl.trim();
        }
        if (content != null && !content.isBlank()) {
            return reportContentStorageService.storeReportContent(reportId, content);
        }
        return "local://report/" + reportId + ".md";
    }

    private Integer resolveWordCount(Integer providedWordCount, String content) {
        if (providedWordCount != null && providedWordCount > 0) {
            return providedWordCount;
        }
        if (content == null || content.isBlank()) {
            return null;
        }
        return content.trim().split("\\s+").length;
    }

    private Integer resolveCitationCount(Integer providedCitationCount, String content) {
        if (providedCitationCount != null) {
            return providedCitationCount;
        }
        if (content == null || content.isBlank()) {
            return 0;
        }

        Matcher matcher = CITATION_PATTERN.matcher(content);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private BigDecimal resolveCriticScore(Double providedCriticScore) {
        if (providedCriticScore == null) {
            return null;
        }
        return BigDecimal.valueOf(providedCriticScore);
    }
}
