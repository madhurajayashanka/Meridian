package com.meridian.report.controller;

import com.meridian.chat.entity.ChatMessage;
import com.meridian.chat.repository.ChatMessageRepository;
import com.meridian.common.exception.ForbiddenException;
import com.meridian.common.exception.UnauthorizedException;
import com.meridian.project.repository.ProjectRepository;
import com.meridian.report.entity.Report;
import com.meridian.report.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

/**
 * GraphQL controller for report and chat-related queries.
 */
@Controller
@RequiredArgsConstructor
public class ReportGraphQLController {

    private final ReportRepository reportRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ProjectRepository projectRepository;

    @QueryMapping
    public List<Report> reports(@Argument String projectId) {
        UUID userId = getCurrentUserId();

        if (projectId == null || projectId.isBlank()) {
            return reportRepository.findAllByUserId(userId);
        }

        UUID parsedProjectId = UUID.fromString(projectId);
        boolean projectOwnedByUser = projectRepository.findByIdAndUserId(parsedProjectId, userId).isPresent();
        if (!projectOwnedByUser) {
            throw new ForbiddenException("Project not found or access denied");
        }

        return reportRepository.findAllByProjectId(parsedProjectId);
    }

    @QueryMapping
    public Report report(@Argument String id) {
        UUID userId = getCurrentUserId();
        UUID reportId = UUID.fromString(id);

        return reportRepository.findByIdAndUserId(reportId, userId).orElse(null);
    }

    @QueryMapping
    public List<Report> publicReports() {
        return reportRepository.findAllPublic();
    }

    @QueryMapping
    public List<ChatMessage> chatMessages(@Argument String reportId) {
        UUID userId = getCurrentUserId();
        UUID parsedReportId = UUID.fromString(reportId);

        boolean canAccessReport = reportRepository.findByIdAndUserId(parsedReportId, userId).isPresent();
        if (!canAccessReport) {
            throw new ForbiddenException("Report not found or access denied");
        }

        return chatMessageRepository.findAllByReportIdOrderByCreated(parsedReportId);
    }

    @QueryMapping
    public String health() {
        return "ok";
    }

    private UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException("Not authenticated");
        }

        try {
            Object principal = auth.getPrincipal();
            String principalValue = principal instanceof UUID ? principal.toString() : auth.getName();
            return UUID.fromString(principalValue);
        } catch (IllegalArgumentException ex) {
            throw new UnauthorizedException("Not authenticated", ex);
        }
    }
}
