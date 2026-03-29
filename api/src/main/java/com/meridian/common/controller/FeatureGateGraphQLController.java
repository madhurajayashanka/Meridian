package com.meridian.common.controller;

import com.meridian.common.exception.ForbiddenException;
import com.meridian.common.exception.UnauthorizedException;
import com.meridian.common.exception.ValidationException;
import com.meridian.document.entity.Document;
import com.meridian.document.repository.DocumentRepository;
import com.meridian.document.repository.EmbeddingRepository;
import com.meridian.report.entity.Report;
import com.meridian.report.repository.ReportRepository;
import com.meridian.report.service.ReportContentStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Implements previously-stubbed GraphQL mutations:
 * deleteDocument, deleteReport, publishReport, unpublishReport, exportReport.
 * uploadDocument (GraphQL scalar Upload) is handled via REST — this stub keeps schema valid.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class FeatureGateGraphQLController {

    private final DocumentRepository documentRepository;
    private final EmbeddingRepository embeddingRepository;
    private final ReportRepository reportRepository;
    private final ReportContentStorageService reportContentStorageService;

    // ── uploadDocument via GraphQL scalar Upload is not supported (binary over HTTP/2 multipart).
    // Use REST POST /api/documents/upload instead. This stub keeps the schema resolvable.
    @MutationMapping
    public Object uploadDocument(@Argument String projectId, @Argument Object file) {
        throw new ValidationException("Use REST POST /api/documents/upload for file uploads.");
    }

    @MutationMapping
    @Transactional
    public Boolean deleteDocument(@Argument String id) {
        UUID userId = currentUserId();
        UUID docId = UUID.fromString(id);

        Document doc = documentRepository.findByIdAndUserId(docId, userId)
            .orElseThrow(() -> new ForbiddenException("Document not found or access denied"));

        embeddingRepository.deleteByDocumentId(docId);
        doc.setDeletedAt(LocalDateTime.now());
        documentRepository.save(doc);
        log.info("Document {} soft-deleted by user {}", docId, userId);
        return true;
    }

    @MutationMapping
    @Transactional
    public Boolean deleteReport(@Argument String id) {
        UUID userId = currentUserId();
        UUID reportId = UUID.fromString(id);

        Report report = reportRepository.findByIdAndUserId(reportId, userId)
            .orElseThrow(() -> new ForbiddenException("Report not found or access denied"));

        report.setDeletedAt(LocalDateTime.now());
        reportRepository.save(report);
        log.info("Report {} soft-deleted by user {}", reportId, userId);
        return true;
    }

    @MutationMapping
    @Transactional
    public Boolean publishReport(@Argument String id) {
        UUID userId = currentUserId();
        UUID reportId = UUID.fromString(id);

        Report report = reportRepository.findByIdAndUserId(reportId, userId)
            .orElseThrow(() -> new ForbiddenException("Report not found or access denied"));

        report.setIsPublic(true);
        reportRepository.save(report);
        return true;
    }

    @MutationMapping
    @Transactional
    public Boolean unpublishReport(@Argument String id) {
        UUID userId = currentUserId();
        UUID reportId = UUID.fromString(id);

        Report report = reportRepository.findByIdAndUserId(reportId, userId)
            .orElseThrow(() -> new ForbiddenException("Report not found or access denied"));

        report.setIsPublic(false);
        reportRepository.save(report);
        return true;
    }

    @MutationMapping
    public String exportReport(@Argument String id, @Argument String format) {
        UUID userId = currentUserId();
        UUID reportId = UUID.fromString(id);

        Report report = reportRepository.findByIdAndUserId(reportId, userId)
            .orElseThrow(() -> new ForbiddenException("Report not found or access denied"));

        String content = reportContentStorageService.readReportContent(report.getS3Key())
            .orElseThrow(() -> new ValidationException("Report content not available"));

        // For now only markdown export is supported; PDF export requires a rendering library
        if (!"markdown".equalsIgnoreCase(format) && !"md".equalsIgnoreCase(format)) {
            throw new ValidationException("Supported export formats: markdown. PDF export coming soon.");
        }

        return content;
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
