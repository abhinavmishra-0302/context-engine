package com.example.ragassistant.service;

import com.example.ragassistant.dto.ChatHistoryResponse;
import com.example.ragassistant.model.ChatHistory;
import com.example.ragassistant.model.User;
import com.example.ragassistant.repository.ChatHistoryRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private final ChatHistoryRepository chatHistoryRepository;
    private final CurrentUserService currentUserService;

    public void save(User user, String query, String response) {
        chatHistoryRepository.save(ChatHistory.builder()
                .id(UUID.randomUUID())
                .user(user)
                .query(query)
                .response(response)
                .createdAt(Instant.now())
                .build());
    }

    public List<ChatHistoryResponse> listForCurrentUser() {
        User user = currentUserService.getCurrentUser();
        return chatHistoryRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(chat -> new ChatHistoryResponse(chat.getQuery(), chat.getResponse(), chat.getCreatedAt()))
                .toList();
    }
}
