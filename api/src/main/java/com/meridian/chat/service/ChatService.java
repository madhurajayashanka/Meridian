package com.meridian.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Proxies RAG chat requests to the FastAPI AI service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final RestTemplate restTemplate;

    @Value("${ai.service.url:http://localhost:8080}")
    private String aiServiceUrl;

    /**
     * Ask the AI service to answer a question grounded in the report.
     * Returns the full assistant response (non-streaming path for GraphQL).
     */
    public String chat(String reportId, String userId, String message, String accessToken) {
        try {
            String url = UriComponentsBuilder
                .fromHttpUrl(aiServiceUrl)
                .path("/api/v1/chat/{reportId}")
                .queryParam("user_id", userId)
                .queryParam("message", message)
                .queryParam("token", accessToken)
                .buildAndExpand(reportId)
                .toUriString();

            // Non-streaming: collect full response via a simple POST
            // The AI endpoint streams SSE; for GraphQL we use a sync wrapper
            Map<?, ?> response = restTemplate.postForObject(url, null, Map.class);
            if (response != null && response.get("response") instanceof String s) {
                return s;
            }
            return "I was unable to generate a response. Please try again.";
        } catch (Exception e) {
            log.error("Chat proxy failed for report {}: {}", reportId, e.getMessage());
            return "Chat service is temporarily unavailable.";
        }
    }
}
