package com.meridian.job.repository;

import com.meridian.job.entity.ResearchJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResearchJobRepository extends JpaRepository<ResearchJob, UUID> {

    @Query("SELECT j FROM ResearchJob j WHERE j.project.id = :projectId ORDER BY j.createdAt DESC")
    List<ResearchJob> findAllByProjectId(UUID projectId);

    @Query("SELECT j FROM ResearchJob j WHERE j.user.id = :userId ORDER BY j.createdAt DESC")
    List<ResearchJob> findAllByUserId(UUID userId);

    @Query("SELECT j FROM ResearchJob j WHERE j.id = :jobId AND j.user.id = :userId")
    Optional<ResearchJob> findByIdAndUserId(UUID jobId, UUID userId);

    @Query("SELECT j FROM ResearchJob j WHERE j.status = :status AND j.user.id = :userId ORDER BY j.createdAt DESC")
    List<ResearchJob> findAllByStatusAndUserId(String status, UUID userId);

    @Query("SELECT COUNT(j) FROM ResearchJob j WHERE j.project.id = :projectId AND j.status = 'COMPLETE'")
    long countCompleteByProjectId(UUID projectId);

    @Query("SELECT MAX(j.completedAt) FROM ResearchJob j WHERE j.project.id = :projectId AND j.status = 'COMPLETE'")
    java.time.LocalDateTime findLastCompletionByProjectId(UUID projectId);
}
