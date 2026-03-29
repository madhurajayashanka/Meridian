package com.meridian.document.service;

import com.meridian.common.exception.ForbiddenException;
import com.meridian.common.exception.ValidationException;
import com.meridian.document.entity.Document;
import com.meridian.document.repository.DocumentRepository;
import com.meridian.project.entity.Project;
import com.meridian.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final ProjectRepository projectRepository;
    private final RestTemplate restTemplate;

    @Value("${ai.service.url:http://localhost:8080}")
    private String aiServiceUrl;

    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024; // 10 MB

    @Transactional
    public Document uploadDocument(UUID userId, UUID projectId, MultipartFile file) {
        // Validate project ownership
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new ForbiddenException("Project not found or access denied"));

        // Validate file
        if (file == null || file.isEmpty()) {
            throw new ValidationException("File is required");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ValidationException("File exceeds 10 MB limit");
        }

        String originalName = file.getOriginalFilename() != null
            ? file.getOriginalFilename() : "upload";
        String fileType = resolveFileType(originalName);

        // Create DB record immediately as PROCESSING
        Document doc = Document.builder()
            .project(project)
            .user(project.getUser())
            .originalFilename(originalName)
            .s3Key("local/" + UUID.randomUUID() + "." + fileType)
            .fileType(fileType)
            .fileSizeBytes(file.getSize())
            .status("PROCESSING")
            .chunkCount(0)
            .build();
        doc = documentRepository.save(doc);

        // Send to AI service for extraction + embedding (async, best-effort)
        final Document savedDoc = doc;
        final UUID docId = doc.getId();
        try {
            byte[] bytes = file.getBytes();
            sendToAiService(docId.toString(), fileType, originalName, bytes);
            savedDoc.setStatus("READY");
        } catch (Exception e) {
            log.error("AI processing failed for document {}: {}", docId, e.getMessage());
            savedDoc.setStatus("FAILED");
            savedDoc.setErrorMessage(e.getMessage());
        }

        return documentRepository.save(savedDoc);
    }

    private void sendToAiService(String documentId, String fileType,
                                  String filename, byte[] bytes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("document_id", documentId);
        body.add("file_type", fileType);
        body.add("file", new ByteArrayResource(bytes) {
            @Override public String getFilename() { return filename; }
        });

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        Map<?, ?> response = restTemplate.postForObject(
            aiServiceUrl + "/api/v1/documents/process", request, Map.class
        );

        if (response != null && response.get("chunk_count") instanceof Number n) {
            log.info("Document {} processed: {} chunks", documentId, n.intValue());
        }
    }

    private String resolveFileType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".txt")) return "txt";
        throw new ValidationException("Only PDF and TXT files are supported");
    }
}
