package com.meridian.chat.entity;

import com.meridian.auth.entity.User;
import com.meridian.report.entity.Report;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ChatMessage entity for RAG conversations on completed reports.
 * Requirement 2.1.7: Chat with report (RAG)
 */
@Entity
@Table(name = "chat_messages", indexes = {
    @Index(name = "idx_chat_messages_report_id", columnList = "report_id"),
    @Index(name = "idx_chat_messages_user_id", columnList = "user_id"),
    @Index(name = "idx_chat_messages_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String role; // 'user', 'assistant', or 'system'

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column
    private Integer tokensUsed;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isUserMessage() {
        return "user".equals(role);
    }

    public boolean isAssistantMessage() {
        return "assistant".equals(role);
    }

    public boolean isSystemMessage() {
        return "system".equals(role);
    }
}
