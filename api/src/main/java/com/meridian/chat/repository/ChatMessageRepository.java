package com.meridian.chat.repository;

import com.meridian.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @Query("SELECT m FROM ChatMessage m WHERE m.report.id = :reportId ORDER BY m.createdAt DESC LIMIT 50")
    List<ChatMessage> findLastMessagesByReportId(UUID reportId);

    @Query("SELECT m FROM ChatMessage m WHERE m.report.id = :reportId ORDER BY m.createdAt ASC")
    List<ChatMessage> findAllByReportIdOrderByCreated(UUID reportId);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.report.id = :reportId")
    long countByReportId(UUID reportId);

    @Query("DELETE FROM ChatMessage m WHERE m.report.id = :reportId")
    void deleteByReportId(UUID reportId);
}
