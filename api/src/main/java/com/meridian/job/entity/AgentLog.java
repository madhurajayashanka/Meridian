package com.meridian.job.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * AgentLog Entity - Tracks execution of each agent in the workflow
 */
@Entity
@Table(name = "agent_logs", indexes = {
    @Index(name = "idx_agent_logs_job_id", columnList = "job_id"),
    @Index(name = "idx_agent_logs_agent", columnList = "agent")
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

    @Column(nullable = false, length = 50)
    private String agent; // planner|research|analysis|critic|synthesizer

    @Column(nullable = false, length = 50)
    private String status; // running|complete|failed

    @Column(nullable = false)
    private Integer progress; // 0-100

    @Column(nullable = false)
    private Integer durationMs; // Execution time in milliseconds

    @Column(length = 1000)
    private String error;

    @Column(columnDefinition = "TEXT")
    private String partialOutput; // JSON string

    @CreationTimestamp
    @Column(nullable = false, updatable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;
}
