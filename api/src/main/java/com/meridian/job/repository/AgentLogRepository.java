package com.meridian.job.repository;

import com.meridian.job.entity.AgentLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * AgentLog Repository - Data access for Agent Logs
 */
@Repository
public interface AgentLogRepository extends JpaRepository<AgentLog, UUID> {

    /**
     * Find agent logs for a job ordered by creation date
     */
    List<AgentLog> findByJobIdOrderByCreatedAtAsc(UUID jobId);
}
