package com.meridian.document.repository;

import com.meridian.document.entity.Embedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmbeddingRepository extends JpaRepository<Embedding, UUID> {

    @Query("SELECT e FROM Embedding e WHERE e.document.id = :documentId ORDER BY e.chunkIndex ASC")
    List<Embedding> findAllByDocumentId(UUID documentId);

    @Query("SELECT e FROM Embedding e WHERE e.reportId = :reportId ORDER BY e.chunkIndex ASC")
    List<Embedding> findAllByReportId(UUID reportId);

    @Query("DELETE FROM Embedding e WHERE e.document.id = :documentId")
    void deleteByDocumentId(UUID documentId);

    @Query("DELETE FROM Embedding e WHERE e.reportId = :reportId")
    void deleteByReportId(UUID reportId);

    @Query("SELECT COUNT(e) FROM Embedding e WHERE e.document.id = :documentId")
    long countByDocumentId(UUID documentId);
}
