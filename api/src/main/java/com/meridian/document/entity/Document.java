package com.meridian.document.entity;

import com.meridian.auth.entity.User;
import com.meridian.project.entity.Project;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Document entity representing user-uploaded PDF or TXT files for RAG.
 * Requirement 2.1.8: Users can upload documents
 */
@Entity
@Table(name = "documents", indexes = {
    @Index(name = "idx_documents_project_id", columnList = "project_id"),
    @Index(name = "idx_documents_user_id", columnList = "user_id"),
    @Index(name = "idx_documents_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(nullable = false, length = 1024)
    private String s3Key;

    @Column(nullable = false, length = 10)
    private String fileType; // 'pdf' or 'txt'

    @Column(nullable = false)
    private Long fileSizeBytes;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "PROCESSING"; // PROCESSING, READY, FAILED

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column
    private Integer chunkCount;

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

    public boolean isReady() {
        return "READY".equals(status);
    }

    public boolean isFailed() {
        return "FAILED".equals(status);
    }
}
