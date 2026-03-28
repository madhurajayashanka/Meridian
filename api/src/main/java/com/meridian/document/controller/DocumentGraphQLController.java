package com.meridian.document.controller;

import com.meridian.common.exception.ForbiddenException;
import com.meridian.common.exception.UnauthorizedException;
import com.meridian.document.entity.Document;
import com.meridian.document.repository.DocumentRepository;
import com.meridian.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

/**
 * GraphQL controller for document queries.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class DocumentGraphQLController {

    private final DocumentRepository documentRepository;
    private final ProjectRepository projectRepository;

    @QueryMapping
    public List<Document> documents(@Argument String projectId) {
        UUID userId = getCurrentUserId();
        UUID parsedProjectId = UUID.fromString(projectId);

        boolean projectOwnedByUser = projectRepository
                .findByIdAndUserId(parsedProjectId, userId)
                .isPresent();

        if (!projectOwnedByUser) {
            throw new ForbiddenException("Project not found or access denied");
        }

        return documentRepository.findAllByProjectId(parsedProjectId);
    }

    @QueryMapping
    public Document document(@Argument String id) {
        UUID userId = getCurrentUserId();
        UUID documentId = UUID.fromString(id);

        return documentRepository.findByIdAndUserId(documentId, userId)
                .orElse(null);
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
