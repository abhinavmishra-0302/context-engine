package com.example.ragassistant.service;

import com.example.ragassistant.exception.AppException;
import com.example.ragassistant.model.ChatMessage;
import com.example.ragassistant.model.ChatRole;
import com.example.ragassistant.model.ChatSession;
import com.example.ragassistant.model.User;
import com.example.ragassistant.repository.ChatMessageRepository;
import com.example.ragassistant.repository.ChatSessionRepository;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMemoryService {

    private final CurrentUserService currentUserService;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Value("${app.rag.memory-max-messages:8}")
    private int memoryMaxMessages;

    @Value("${app.rag.memory-max-chars-total:3000}")
    private int memoryMaxCharsTotal;

    @Value("${app.rag.memory-max-chars-per-message:600}")
    private int memoryMaxCharsPerMessage;

    @Transactional
    public UUID createSessionForCurrentUser() {
        User user = currentUserService.getCurrentUser();
        ChatSession session = ChatSession.builder()
                .id(UUID.randomUUID())
                .user(user)
                .createdAt(Instant.now())
                .build();
        chatSessionRepository.save(session);
        return session.getId();
    }

    public ChatSession requireOwnedSession(User user, UUID sessionId) {
        return chatSessionRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Session does not exist for current user"));
    }

    public List<ChatMessage> loadRecentMessages(ChatSession session) {
        List<ChatMessage> all = chatMessageRepository.findBySessionOrderByCreatedAtAsc(session);
        if (all.size() <= memoryMaxMessages) {
            return all;
        }
        return all.subList(all.size() - memoryMaxMessages, all.size());
    }

    @Transactional
    public void saveTurn(ChatSession session, String query, String answer) {
        chatMessageRepository.save(ChatMessage.builder()
                .id(UUID.randomUUID())
                .session(session)
                .role(ChatRole.USER)
                .content(query)
                .createdAt(Instant.now())
                .build());
        chatMessageRepository.save(ChatMessage.builder()
                .id(UUID.randomUUID())
                .session(session)
                .role(ChatRole.ASSISTANT)
                .content(answer)
                .createdAt(Instant.now())
                .build());
    }

    public String formatConversationHistory(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return "None";
        }

        ArrayDeque<String> lines = new ArrayDeque<>();
        int totalChars = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            String content = truncate(message.getContent(), memoryMaxCharsPerMessage).replaceAll("\\s+", " ");
            String line = message.getRole().name() + ": " + content;
            if (lines.isEmpty() && line.length() > memoryMaxCharsTotal) {
                lines.addFirst(truncate(line, memoryMaxCharsTotal));
                break;
            }
            if (totalChars + line.length() > memoryMaxCharsTotal) {
                break;
            }
            lines.addFirst(line);
            totalChars += line.length();
        }
        if (lines.isEmpty()) {
            return "None";
        }
        return String.join("\n", lines);
    }

    private String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + "...";
    }
}
