package com.meridian.job.entity;

import com.meridian.auth.entity.User;
import com.meridian.project.entity.Project;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ResearchJob entity representing a user-submitted research task.
 * Requirement 2.1.4: Research job submission and orchestration
 */
@Entity
@Table(name = "research_jobs", indexes = {
    @Index(name = "idx_research_jobs_project_id", columnList = "project_id"),
    @Index(name = "idx_research_jobs_user_id", columnList = "user_id"),
    @Index(name = "idx_research_jobs_status", columnList = "status"),
    @Index(name = "idx_research_jobs_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResearchJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String query;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "PENDING"; // PENDING, RUNNING, COMPLETE, FAILED, CANCELLED

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String llmProvider = "BEDROCK"; // BEDROCK or OPENAI

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String researchDepth = "STANDARD"; // QUICK, STANDARD, or DEEP

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime completedAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    // Helper methods
    public boolean isPending() {
        return "PENDING".equals(status);
    }

    public boolean isRunning() {
        return "RUNNING".equals(status);
    }

    public boolean isComplete() {
        return "COMPLETE".equals(status);
    }

    public boolean isFailed() {
        return "FAILED".equals(status);
    }

    public boolean isCancelled() {
        return "CANCELLED".equals(status);
    }

    public long getExecutionTimeMs() {
        if (startedAt == null || completedAt == null) {
            return 0;
        }
        return java.time.temporal.ChronoUnit.MILLIS.between(startedAt, completedAt);
    }
}
