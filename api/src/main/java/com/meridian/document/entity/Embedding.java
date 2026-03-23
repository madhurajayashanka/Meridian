package com.meridian.document.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Embedding entity storing pgvector embeddings for document chunks and report sections.
 */
@Entity
@Table(name = "embeddings", indexes = {
    @Index(name = "idx_embeddings_document_id", columnList = "document_id"),
    @Index(name = "idx_embeddings_report_id", columnList = "report_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Embedding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @Column(name = "report_id")
    private UUID reportId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, columnDefinition = "vector(1536)")
    private String embedding; // Stored as JSON string representation

    @Column
    private Integer chunkIndex;

    @Column(columnDefinition = "jsonb")
    private String metadata; // JSON metadata

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
