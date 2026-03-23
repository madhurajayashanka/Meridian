package com.meridian.report.entity;

import com.meridian.auth.entity.User;
import com.meridian.job.entity.ResearchJob;
import com.meridian.project.entity.Project;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Report entity representing the final output from the Synthesizer Agent.
 * Requirement 2.1.6: Report management and storage
 */
@Entity
@Table(name = "reports", indexes = {
    @Index(name = "idx_reports_job_id", columnList = "job_id"),
    @Index(name = "idx_reports_project_id", columnList = "project_id"),
    @Index(name = "idx_reports_user_id", columnList = "user_id"),
    @Index(name = "idx_reports_created_at", columnList = "created_at"),
    @Index(name = "idx_reports_public", columnList = "is_public")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private ResearchJob job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 1024)
    private String s3Key;

    @Column
    private Integer wordCount;

    @Column(nullable = false)
    @Builder.Default
    private Integer citationCount = 0;

    @Column(precision = 3, scale = 1)
    private BigDecimal criticScore;

    @Column(nullable = false)
    @Builder.Default
    private Integer revisionCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isPublic = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime deletedAt;

    public boolean isSoftDeleted() {
        return deletedAt != null;
    }

    public boolean isPublished() {
        return isPublic && deletedAt == null;
    }

    public boolean hasValidCriticScore() {
        return criticScore != null && criticScore.compareTo(BigDecimal.ONE) >= 0 && criticScore.compareTo(new BigDecimal("10.0")) <= 0;
    }
}
