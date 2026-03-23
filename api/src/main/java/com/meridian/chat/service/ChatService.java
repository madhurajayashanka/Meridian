package com.meridian.chat.service;

import com.meridian.chat.entity.ChatMessage;
import com.meridian.chat.repository.ChatMessageRepository;
import com.meridian.report.entity.Report;
import com.meridian.report.repository.ReportRepository;
import com.meridian.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Chat Service - Manages RAG chat on reports
 * Task 20 will implement full RAG chat with pgvector semantic search
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ReportRepository reportRepository;

    /**
     * Get chat messages for a report
     */
    public Page<ChatMessage> getChatMessages(UUID reportId, Pageable pageable) {
        return chatMessageRepository.findByReportIdOrderByCreatedAtDesc(reportId, pageable);
    }

    /**
     * Send a chat message on a report
     * Task 20 will implement RAG lookup and LLM response generation
     */
    public ChatMessage sendChatMessage(UUID reportId, UUID userId, String content) {
        Report report = reportRepository.findById(reportId)
            .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        ChatMessage message = ChatMessage.builder()
            .reportId(reportId)
            .report(report)
            .userId(userId)
            .content(content)
            .createdAt(Instant.now())
            .build();

        // Task 20: Implement RAG chat with semantic search and LLM response
        // For now, just store the message
        return chatMessageRepository.save(message);
    }
}
