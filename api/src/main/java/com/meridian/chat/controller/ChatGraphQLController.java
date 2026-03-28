package com.meridian.chat.controller;

import com.meridian.auth.entity.User;
import com.meridian.auth.repository.UserRepository;
import com.meridian.chat.entity.ChatMessage;
import com.meridian.chat.repository.ChatMessageRepository;
import com.meridian.common.exception.ForbiddenException;
import com.meridian.common.exception.UnauthorizedException;
import com.meridian.common.exception.ValidationException;
import com.meridian.report.entity.Report;
import com.meridian.report.repository.ReportRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * GraphQL mutations for report chat interactions.
 */
@Controller
@RequiredArgsConstructor
public class ChatGraphQLController {

    private final ChatMessageRepository chatMessageRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    @MutationMapping
    @Transactional
    public ChatMessage sendChatMessage(@Argument SendChatMessageInput input) {
        UUID userId = getCurrentUserId();

        if (input == null || input.getReportId() == null || input.getReportId().isBlank()) {
            throw new ValidationException("reportId is required");
        }
        if (input.getContent() == null || input.getContent().isBlank()) {
            throw new ValidationException("content is required");
        }

        UUID reportId = UUID.fromString(input.getReportId());
        Report report = reportRepository.findByIdAndUserId(reportId, userId)
            .orElseThrow(() -> new ForbiddenException("Report not found or access denied"));

        User user = userRepository.findByIdActive(userId)
            .orElseThrow(() -> new UnauthorizedException("Not authenticated"));

        String trimmedContent = input.getContent().trim();

        ChatMessage userMessage = ChatMessage.builder()
            .report(report)
            .user(user)
            .role("user")
            .content(trimmedContent)
            .build();
        chatMessageRepository.save(userMessage);

        // Basic assistant response until full RAG chat pipeline is wired.
        String assistantText = "Thanks for your question. Chat is connected now, but deep report-grounded answers are still being rolled out. "
            + "Question received: \"" + trimmedContent + "\"";

        ChatMessage assistantMessage = ChatMessage.builder()
            .report(report)
            .user(user)
            .role("assistant")
            .content(assistantText)
            .build();

        return chatMessageRepository.save(assistantMessage);
    }

    @MutationMapping
    @Transactional
    public Boolean clearChatHistory(@Argument String reportId) {
        UUID userId = getCurrentUserId();
        if (reportId == null || reportId.isBlank()) {
            throw new ValidationException("reportId is required");
        }

        UUID parsedReportId = UUID.fromString(reportId);
        boolean canAccessReport = reportRepository.findByIdAndUserId(parsedReportId, userId).isPresent();
        if (!canAccessReport) {
            throw new ForbiddenException("Report not found or access denied");
        }

        List<ChatMessage> messages = chatMessageRepository.findAllByReportIdOrderByCreated(parsedReportId);
        if (!messages.isEmpty()) {
            chatMessageRepository.deleteAll(messages);
        }
        return true;
    }

    @Data
    public static class SendChatMessageInput {
        private String reportId;
        private String content;
    }

    private UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException("Not authenticated");
        }

        try {
            Object principal = auth.getPrincipal();
            String principalValue = principal instanceof UUID ? principal.toString() : auth.getName();
            return UUID.fromString(principalValue);
        } catch (IllegalArgumentException ex) {
            throw new UnauthorizedException("Not authenticated", ex);
        }
    }
}
