package com.meridian.project.service;

import com.meridian.auth.entity.User;
import com.meridian.auth.repository.UserRepository;
import com.meridian.common.exception.ForbiddenException;
import com.meridian.common.exception.ValidationException;
import com.meridian.project.entity.Project;
import com.meridian.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for project management
 * Requirement 3: Project Management
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public List<Project> getUserProjects(UUID userId) {
        return projectRepository.findAllByUserId(userId);
    }

    public Optional<Project> getProjectByIdAndUserId(UUID projectId, UUID userId) {
        return projectRepository.findByIdAndUserId(projectId, userId);
    }

    public Project createProject(UUID userId, String name, String description) {
        // Validation
        if (name == null || name.trim().isEmpty() || name.length() > 100) {
            throw new ValidationException("Project name must be 1-100 characters");
        }

        // Check if project name is unique for the user
        Optional<Project> existing = projectRepository.findByUserIdAndName(userId, name);
        if (existing.isPresent()) {
            throw new ValidationException("Project with this name already exists");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ValidationException("User not found"));

        Project project = Project.builder()
            .user(user)
            .name(name)
            .description(description)
            .isArchived(false)
            .build();

        return projectRepository.save(project);
    }

    public Project updateProject(UUID userId, UUID projectId, String name, String description, Boolean isArchived) {
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new ForbiddenException("Project not found or access denied"));

        if (name != null && !name.isEmpty()) {
            if (name.length() > 100) {
                throw new ValidationException("Project name must be 1-100 characters");
            }
            // Check uniqueness
            Optional<Project> existing = projectRepository.findByUserIdAndName(userId, name);
            if (existing.isPresent() && !existing.get().getId().equals(projectId)) {
                throw new ValidationException("Project with this name already exists");
            }
            project.setName(name);
        }

        if (description != null) {
            project.setDescription(description);
        }

        if (isArchived != null) {
            project.setIsArchived(isArchived);
        }

        project.setUpdatedAt(LocalDateTime.now());
        return projectRepository.save(project);
    }

    public void deleteProject(UUID userId, UUID projectId) {
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new ForbiddenException("Project not found or access denied"));

        // Soft delete
        project.setDeletedAt(LocalDateTime.now());
        projectRepository.save(project);
    }

    public void archiveProject(UUID userId, UUID projectId) {
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
            .orElseThrow(() -> new ForbiddenException("Project not found or access denied"));

        project.setIsArchived(true);
        project.setUpdatedAt(LocalDateTime.now());
        projectRepository.save(project);
    }
}
