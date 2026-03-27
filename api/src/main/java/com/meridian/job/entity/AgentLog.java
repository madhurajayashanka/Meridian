package com.meridian.job.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * AgentLog Entity - Tracks execution of each agent in the workflow
 */
@Entity
@Table(name = "agent_logs", indexes = {
    @Index(name = "idx_agent_logs_job_id", columnList = "job_id"),
    @Index(name = "idx_agent_logs_agent_name", columnList = "agent_name")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, columnDefinition = "uuid")
    private UUID jobId;

    @Column(name = "agent_name", nullable = false, length = 100)
    private String agent; // planner|research|analysis|critic|synthesizer

    @Column(nullable = false, length = 50)
    private String status; // RUNNING|COMPLETE|FAILED

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "duration_ms")
    private Integer durationMs; // Execution time in milliseconds

    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;
}
