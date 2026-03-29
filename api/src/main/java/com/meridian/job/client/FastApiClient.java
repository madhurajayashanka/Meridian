package com.meridian.job.client;

import com.meridian.config.CorrelationIdFilter;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * FastAPI client for communicating with the AI service.
 * Handles job submission, cancellation, and status checks.
 * Requirement 4: Submit research jobs and trigger AI service execution
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FastApiClient {

    private final RestTemplate restTemplate;

    @Value("${ai.service.url:http://localhost:8080}")
    private String aiServiceUrl;

    @Value("${internal.api.key:}")
    private String internalApiKey;

    @Value("${ai.service.timeout:30000}")
    private Integer timeout;

    /** Build headers that propagate correlation ID and service key to the AI service. */
    private HttpHeaders correlationHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            headers.set(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);
        }
        if (internalApiKey != null && !internalApiKey.isBlank()) {
            headers.set("X-Service-Key", internalApiKey);
        }
        return headers;
    }

    /**
     * Start a new research job on the AI service.
     */
    @CircuitBreaker(name = "ai-service", fallbackMethod = "startJobFallback")
    @Retry(name = "ai-service")
    public FastApiJobResponse startJob(
        String jobId,
        String projectId,
        String userId,
        String query,
        String llmProvider,
        String researchDepth,
        List<String> documentIds
    ) {
        try {
            String url = UriComponentsBuilder
                .fromHttpUrl(aiServiceUrl)
                .path("/api/v1/jobs/start")
                .queryParam("job_id", jobId)
                .queryParam("query", query)
                .queryParam("project_id", projectId)
                .queryParam("user_id", userId)
                .queryParam("llm_provider", llmProvider.toLowerCase())
                .queryParam("research_depth", researchDepth.toLowerCase())
                .buildAndExpand()
                .toUriString();

            if (documentIds != null && !documentIds.isEmpty()) {
                url += "&document_ids=" + String.join("&document_ids=", documentIds);
            }

            log.info("Starting research job {} on FastAPI: {}", jobId, url);

            ResponseEntity<FastApiJobResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(correlationHeaders()),
                FastApiJobResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Job {} started successfully on AI service", jobId);
                return response.getBody();
            } else {
                log.warn("Unexpected response from AI service: {}", response.getStatusCode());
                throw new FastApiException("Unexpected response status: " + response.getStatusCode());
            }
        } catch (RestClientException e) {
            log.error("Failed to start job {} on AI service: {}", jobId, e.getMessage());
            throw new FastApiException("Failed to start job on AI service: " + e.getMessage(), e);
        }
    }

    /** Circuit breaker fallback — AI service is unavailable. */
    private FastApiJobResponse startJobFallback(String jobId, String projectId, String userId,
            String query, String llmProvider, String researchDepth,
            List<String> documentIds, Throwable t) {
        log.error("AI service circuit open for job {}: {}", jobId, t.getMessage());
        throw new FastApiException("AI service is currently unavailable. Please try again later.", t);
    }

    /**
     * Get job status from AI service.
     */
    public FastApiJobStatusResponse getJobStatus(String jobId) {
        try {
            String url = UriComponentsBuilder
                .fromHttpUrl(aiServiceUrl)
                .path("/api/v1/jobs/{jobId}")
                .buildAndExpand(jobId)
                .toUriString();

            log.debug("Fetching job status for {}", jobId);

            ResponseEntity<FastApiJobStatusResponse> response = restTemplate.getForEntity(
                url,
                FastApiJobStatusResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            } else {
                throw new FastApiException("Failed to get job status: " + response.getStatusCode());
            }
        } catch (RestClientException e) {
            log.error("Failed to get job status for {}: {}", jobId, e.getMessage());
            throw new FastApiException("Failed to get job status: " + e.getMessage(), e);
        }
    }

    /**
     * Cancel a running research job.
     *
     * @param jobId Research job ID
     */
    public void cancelJob(String jobId) {
        try {
            String url = UriComponentsBuilder
                .fromHttpUrl(aiServiceUrl)
                .path("/api/v1/jobs/{jobId}/cancel")
                .buildAndExpand(jobId)
                .toUriString();

            log.info("Cancelling job {}", jobId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

            HttpEntity<?> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Job {} cancelled successfully", jobId);
            } else {
                log.warn("Failed to cancel job {}: {}", jobId, response.getStatusCode());
            }
        } catch (RestClientException e) {
            log.error("Error cancelling job {}: {}", jobId, e.getMessage());
            throw new FastApiException("Failed to cancel job: " + e.getMessage(), e);
        }
    }

    /**
     * Health check for the AI service.
     */
    public boolean isHealthy() {
        try {
            String url = UriComponentsBuilder
                .fromHttpUrl(aiServiceUrl)
                .path("/health")
                .toUriString();

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("AI service health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Exception for AI service errors.
     */
    public static class FastApiException extends RuntimeException {
        public FastApiException(String message) {
            super(message);
        }

        public FastApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Response from FastAPI job start endpoint.
     */
    public static class FastApiJobResponse {
        public String job_id;
        public String status;
        public String message;
    }

    /**
     * Response from FastAPI job status endpoint.
     */
    public static class FastApiJobStatusResponse {
        public String job_id;
        public String status;
        public String query;
        public String error;
        public java.time.LocalDateTime completed_at;
        public java.util.List<Map<String, Object>> agent_logs;
    }
}
