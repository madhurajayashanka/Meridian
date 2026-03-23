package com.meridian.document.service;

import com.meridian.document.entity.Document;
import com.meridian.document.repository.DocumentRepository;
import com.meridian.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Document Service - Manages document upload, processing, and embedding
 * Task 19 will implement PDF/text extraction and Task 20 will handle embeddings
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DocumentService {

    private final DocumentRepository documentRepository;

    /**
     * Get document by ID
     */
    public Optional<Document> getDocumentById(UUID documentId) {
        return documentRepository.findById(documentId)
            .filter(d -> d.getDeletedAt() == null);
    }

    /**
     * Get documents for a project
     */
    public Page<Document> getDocumentsByProject(UUID projectId, String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            return documentRepository.findByProjectIdAndStatusAndDeletedAtIsNull(projectId, status, pageable);
        }
        return documentRepository.findByProjectIdAndDeletedAtIsNull(projectId, pageable);
    }

    /**
     * Upload document (stores file reference, content extraction in Task 19)
     */
    public Document uploadDocument(
        UUID projectId,
        UUID userId,
        String filename,
        String mimeType,
        byte[] content
    ) {
        // Task 19 will implement S3 storage and text extraction
        Document doc = Document.builder()
            .projectId(projectId)
            .userId(userId)
            .filename(filename)
            .mimeType(mimeType)
            .fileSize(content.length)
            .status("PENDING")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        return documentRepository.save(doc);
    }

    /**
     * Process document (extract text and embeddings)
     * Task 19-20 will implement full processing
     */
    public Document processDocument(UUID documentId) {
        Document doc = getDocumentById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        // Task 19-20: Implement async processing
        return doc;
    }

    /**
     * Delete document (soft delete)
     */
    public void deleteDocument(UUID documentId) {
        Document doc = getDocumentById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        doc.setDeletedAt(Instant.now());
        documentRepository.save(doc);
    }
}
