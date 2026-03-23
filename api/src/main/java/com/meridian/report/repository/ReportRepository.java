package com.meridian.report.repository;

import com.meridian.report.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

    @Query("SELECT r FROM Report r WHERE r.job.id = :jobId")
    Optional<Report> findByJobId(UUID jobId);

    @Query("SELECT r FROM Report r WHERE r.project.id = :projectId AND r.deletedAt IS NULL ORDER BY r.createdAt DESC")
    List<Report> findAllByProjectId(UUID projectId);

    @Query("SELECT r FROM Report r WHERE r.user.id = :userId AND r.deletedAt IS NULL ORDER BY r.createdAt DESC")
    List<Report> findAllByUserId(UUID userId);

    @Query("SELECT r FROM Report r WHERE r.id = :reportId AND r.user.id = :userId AND r.deletedAt IS NULL")
    Optional<Report> findByIdAndUserId(UUID reportId, UUID userId);

    @Query("SELECT r FROM Report r WHERE r.isPublic = true AND r.deletedAt IS NULL ORDER BY r.createdAt DESC")
    List<Report> findAllPublic();

    @Query("SELECT COUNT(r) FROM Report r WHERE r.project.id = :projectId AND r.deletedAt IS NULL")
    long countByProjectId(UUID projectId);
}
