package com.meridian.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating a project
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateProjectInput {
    private String name;
    private String description;
}
