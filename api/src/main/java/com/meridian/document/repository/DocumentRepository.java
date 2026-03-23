package com.meridian.document.repository;

import com.meridian.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    @Query("SELECT d FROM Document d WHERE d.project.id = :projectId AND d.deletedAt IS NULL ORDER BY d.createdAt DESC")
    List<Document> findAllByProjectId(UUID projectId);

    @Query("SELECT d FROM Document d WHERE d.user.id = :userId AND d.deletedAt IS NULL ORDER BY d.createdAt DESC")
    List<Document> findAllByUserId(UUID userId);

    @Query("SELECT d FROM Document d WHERE d.id = :documentId AND d.user.id = :userId AND d.deletedAt IS NULL")
    Optional<Document> findByIdAndUserId(UUID documentId, UUID userId);

    @Query("SELECT d FROM Document d WHERE d.id IN :documentIds AND d.deletedAt IS NULL")
    List<Document> findAllByIds(List<UUID> documentIds);

    @Query("SELECT COUNT(d) FROM Document d WHERE d.project.id = :projectId AND d.status = 'READY' AND d.deletedAt IS NULL")
    long countReadyByProjectId(UUID projectId);
}
