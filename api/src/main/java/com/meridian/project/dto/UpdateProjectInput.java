package com.meridian.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating a project
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProjectInput {
    private String id;
    private String name;
    private String description;
    private Boolean isArchived;
}
