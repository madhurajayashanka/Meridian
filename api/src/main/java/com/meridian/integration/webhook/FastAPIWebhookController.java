package com.meridian.integration.webhook;

import com.meridian.job.service.ResearchJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class FastAPIWebhookController {

    private final ResearchJobService jobService;

    @Value("${webhook.secret:}")
    private String webhookSecret;

    /** Verify HMAC-SHA256 signature from AI service. Skip in dev when secret is blank. */
    private boolean isValidSignature(String payload, String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("WEBHOOK_SECRET not configured — skipping signature check (dev mode)");
            return true;
        }
        if (signature == null || !signature.startsWith("sha256=")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : expected) hex.append(String.format("%02x", b));
            String expectedHex = "sha256=" + hex;
            // Constant-time comparison
            return MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Signature verification error", e);
            return false;
        }
    }

    @PostMapping("/jobs/{jobId}/status")
    public ResponseEntity<Map<String, Object>> updateJobStatus(
        @PathVariable UUID jobId,
        @RequestBody JobStatusWebhook webhook
    ) {
        try {
            log.info(
                "Received job status update: jobId={}, status={}, progress={}",
                jobId,
                webhook.status,
                webhook.progress
            );

            jobService.updateJobStatus(jobId, webhook.status, webhook.error);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Status updated");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing job status webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/jobs/{jobId}/complete")
    public ResponseEntity<Map<String, Object>> completeJob(
        @PathVariable UUID jobId,
        @RequestBody String rawBody,
        @RequestHeader(value = "X-Webhook-Signature", required = false) String signature
    ) {
        if (!isValidSignature(rawBody, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid signature"));
        }
        try {
            JobCompleteWebhook webhook = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(rawBody, JobCompleteWebhook.class);
            log.info("Received job completion: jobId={}, reportId={}", jobId, webhook.reportId);

            jobService.completeJobWithReport(
                jobId,
                webhook.reportId,
                webhook.title,
                webhook.content,
                webhook.storageUrl,
                webhook.wordCount,
                webhook.citationCount,
                webhook.criticScore,
                webhook.revisionCount
            );

            return ResponseEntity.ok(Map.of("success", true, "message", "Job completed"));
        } catch (Exception e) {
            log.error("Error processing job completion webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/jobs/{jobId}/failed")
    public ResponseEntity<Map<String, Object>> failJob(
        @PathVariable UUID jobId,
        @RequestBody JobFailureWebhook webhook
    ) {
        try {
            log.warn("Received job failure: jobId={}, error={}", jobId, webhook.error);

            jobService.updateJobStatus(jobId, "FAILED", webhook.error);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Failure recorded");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing job failure webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "webhook-receiver-healthy"));
    }

    public static class JobStatusWebhook {
        public UUID jobId;
        public String status;
        public Integer progress;
        public String error;
        public Long timestamp;

        public JobStatusWebhook() {
        }
    }

    public static class JobCompleteWebhook {
        public UUID jobId;
        public UUID reportId;
        public String title;
        public String content;
        public String storageUrl;
        public Integer wordCount;
        public Integer citationCount;
        public Double criticScore;
        public Integer revisionCount;
        public Long timestamp;

        public JobCompleteWebhook() {
        }
    }

    public static class JobFailureWebhook {
        public UUID jobId;
        public String error;
        public Integer progress;
        public Long timestamp;

        public JobFailureWebhook() {
        }
    }
}
