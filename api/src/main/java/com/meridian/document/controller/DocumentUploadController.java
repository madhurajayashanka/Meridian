package com.meridian.document.controller;

import com.meridian.common.exception.UnauthorizedException;
import com.meridian.document.entity.Document;
import com.meridian.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * REST endpoint for document upload (multipart/form-data).
 * GraphQL scalar Upload is not well-supported for binary; REST is cleaner here.
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentUploadController {

    private final DocumentService documentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(
        @RequestParam("projectId") UUID projectId,
        @RequestParam("file") MultipartFile file
    ) {
        UUID userId = currentUserId();
        Document doc = documentService.uploadDocument(userId, projectId, file);

        return ResponseEntity.ok(Map.of(
            "id", doc.getId().toString(),
            "originalFilename", doc.getOriginalFilename(),
            "status", doc.getStatus(),
            "chunkCount", doc.getChunkCount(),
            "fileType", doc.getFileType(),
            "fileSizeBytes", doc.getFileSizeBytes()
        ));
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException("Not authenticated");
        }
        try {
            Object p = auth.getPrincipal();
            return p instanceof UUID u ? u : UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Not authenticated");
        }
    }
}
