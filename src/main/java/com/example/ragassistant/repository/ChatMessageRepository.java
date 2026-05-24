package com.example.ragassistant.repository;

import com.example.ragassistant.model.ChatMessage;
import com.example.ragassistant.model.ChatSession;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findBySessionOrderByCreatedAtAsc(ChatSession session);
}
