package com.meridian.project.controller;

import com.meridian.auth.entity.User;
import com.meridian.project.dto.CreateProjectInput;
import com.meridian.project.dto.UpdateProjectInput;
import com.meridian.project.entity.Project;
import com.meridian.project.service.ProjectService;
import com.meridian.job.repository.ResearchJobRepository;
import com.meridian.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * GraphQL Controller for project operations
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ProjectGraphQLController {

    private final ProjectService projectService;
    private final ResearchJobRepository researchJobRepository;

    // =========================================================================
    // Field Resolvers
    // =========================================================================

    @SchemaMapping(typeName = "Project", field = "jobCount")
    public int jobCount(Project project) {
        return (int) researchJobRepository.countByProjectId(project.getId());
    }

    @SchemaMapping(typeName = "Project", field = "owner")
    public User owner(Project project) {
        return project.getUser();
    }

    @SchemaMapping(typeName = "Project", field = "lastActivityAt")
    public LocalDateTime lastActivityAt(Project project) {
        return researchJobRepository.findLastCompletionByProjectId(project.getId());
    }

    // =========================================================================
    // Queries
    // =========================================================================

    @QueryMapping
    public List<Project> projects() {
        UUID userId = getCurrentUserId();
        return projectService.getUserProjects(userId);
    }

    @QueryMapping
    public Project project(@Argument String id) {
        UUID userId = getCurrentUserId();
        UUID projectId = UUID.fromString(id);
        return projectService.getProjectByIdAndUserId(projectId, userId).orElse(null);
    }

    // =========================================================================
    // Mutations
    // =========================================================================

    @MutationMapping
    public Project createProject(@Argument CreateProjectInput input) {
        UUID userId = getCurrentUserId();
        return projectService.createProject(userId, input.getName(), input.getDescription());
    }

    @MutationMapping
    public Project updateProject(@Argument UpdateProjectInput input) {
        UUID userId = getCurrentUserId();
        UUID projectId = UUID.fromString(input.getId());
        return projectService.updateProject(userId, projectId, input.getName(), input.getDescription(), input.getIsArchived());
    }

    @MutationMapping
    public Boolean deleteProject(@Argument String id) {
        UUID userId = getCurrentUserId();
        UUID projectId = UUID.fromString(id);
        projectService.deleteProject(userId, projectId);
        return true;
    }

    @MutationMapping
    public Boolean archiveProject(@Argument String id) {
        UUID userId = getCurrentUserId();
        UUID projectId = UUID.fromString(id);
        projectService.archiveProject(userId, projectId);
        return true;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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
