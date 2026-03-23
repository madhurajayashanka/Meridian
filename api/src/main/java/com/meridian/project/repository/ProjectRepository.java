package com.meridian.project.repository;

import com.meridian.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    @Query("SELECT p FROM Project p WHERE p.user.id = :userId AND p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    List<Project> findAllByUserId(UUID userId);

    @Query("SELECT p FROM Project p WHERE p.user.id = :userId AND p.id = :projectId AND p.deletedAt IS NULL")
    Optional<Project> findByIdAndUserId(UUID projectId, UUID userId);

    @Query("SELECT p FROM Project p WHERE p.user.id = :userId AND p.name = :name AND p.deletedAt IS NULL")
    Optional<Project> findByUserIdAndName(UUID userId, String name);

    @Query("SELECT COUNT(p) FROM Project p WHERE p.user.id = :userId AND p.deletedAt IS NULL")
    long countByUserId(UUID userId);
}
