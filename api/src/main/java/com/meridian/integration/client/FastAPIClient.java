package com.meridian.integration.client;

import com.meridian.common.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.*;

/**
 * FastAPI Client - Handles communication with FastAPI AI service
 *
 * Provides typed methods for:
 * - Submitting research jobs
 * - Querying job status
 * - Processing documents
 * - RAG queries
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FastAPIClient {

    private final RestTemplate restTemplate;

    @Value("${ai-service.url:http://ai-service:8080}")
    private String aiServiceUrl;

    @Value("${ai-service.timeout.connect:5000}")
    private int connectTimeout;

    @Value("${ai-service.timeout.read:30000}")
    private int readTimeout;

    /**
     * Response from FastAPI job submission
     */
    public static class JobSubmissionResponse {
        public String job_id;
        public String status;
        public String message;

        public JobSubmissionResponse() {}
    }

    /**
     * Response from FastAPI job status query
     */
    public static class JobStatusResponse {
        public String job_id;
        public String status;
        public String query;
        public Integer progress;
        public List<Map<String, Object>> agent_logs;
        public String error;
        public long completed_at;

        public JobStatusResponse() {}
    }

    /**
     * Submit a research job to FastAPI
     *
     * @return Job ID if successful
     * @throws InvalidRequestException if submission fails
     */
    public String submitResearchJob(
        UUID jobId,
        UUID userId,
        String query,
        String llmProvider,
        String researchDepth,
        List<UUID> documentIds
    ) {
        try {
            StringBuilder url = new StringBuilder(aiServiceUrl);
            url.append("/api/v1/jobs/start");
            url.append("?query=").append(urlEncode(query));
            url.append("&project_id=").append(jobId); // Using job ID as context
            url.append("&user_id=").append(userId);
            url.append("&llm_provider=").append(llmProvider);
            url.append("&research_depth=").append(researchDepth);

            if (documentIds != null && !documentIds.isEmpty()) {
                for (UUID docId : documentIds) {
                    url.append("&document_ids=").append(docId);
                }
            }

            log.info("Submitting research job to FastAPI: jobId={}, query={}", jobId, query);

            JobSubmissionResponse response = restTemplate.postForObject(
                url.toString(),
                null,
                JobSubmissionResponse.class
            );

            if (response != null && response.job_id != null) {
                log.info("FastAPI job submitted successfully: fastapi_job_id={}", response.job_id);
                return response.job_id;
            } else {
                throw new InvalidRequestException("Invalid response from FastAPI");
            }

        } catch (RestClientException e) {
            log.error("Failed to submit job to FastAPI: {}", e.getMessage(), e);
            throw new InvalidRequestException("Failed to contact AI service: " + e.getMessage());
        }
    }

    /**
     * Query job status from FastAPI
     */
    public JobStatusResponse getJobStatus(UUID jobId) {
        try {
            String url = aiServiceUrl + "/api/v1/jobs/" + jobId;
            log.debug("Querying job status from FastAPI: jobId={}", jobId);

            return restTemplate.getForObject(url, JobStatusResponse.class);

        } catch (RestClientException e) {
            log.error("Failed to query job status from FastAPI: {}", e.getMessage());
            return null; // Return null if AI service is unavailable
        }
    }

    /**
     * Process a document (extract text, generate embeddings)
     */
    public Map<String, Object> processDocument(UUID documentId, byte[] content, String filename) {
        try {
            String url = aiServiceUrl + "/api/v1/documents/process";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("document_id", documentId);
            requestBody.put("filename", filename);
            requestBody.put("content_base64", Base64.getEncoder().encodeToString(content));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.info("Processing document: documentId={}, filename={}", documentId, filename);

            Map<String, Object> response = restTemplate.postForObject(
                url,
                entity,
                Map.class
            );

            log.info("Document processing initiated: documentId={}", documentId);
            return response;

        } catch (RestClientException e) {
            log.error("Failed to process document: {}", e.getMessage(), e);
            throw new InvalidRequestException("Document processing failed: " + e.getMessage());
        }
    }

    /**
     * Submit RAG chat query
     */
    public String submitChatQuery(UUID reportId, String query) {
        try {
            String url = aiServiceUrl + "/ai/chat/" + reportId;

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("query", query);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);

            log.debug("Submitting chat query: reportId={}", reportId);

            String response = restTemplate.postForObject(url, entity, String.class);
            return response;

        } catch (RestClientException e) {
            log.error("Failed to submit chat query: {}", e.getMessage());
            throw new InvalidRequestException("Chat query failed: " + e.getMessage());
        }
    }

    /**
     * Check if FastAPI service is healthy
     */
    public boolean isHealthy() {
        try {
            String url = aiServiceUrl + "/health";
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            log.info("FastAPI health check: {}", response);
            return response != null && "ok".equalsIgnoreCase(response.get("status").toString());
        } catch (Exception e) {
            log.warn("FastAPI health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * URL encode a string
     */
    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return value;
        }
    }
}
