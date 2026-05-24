package com.example.ragassistant.repository;

import com.example.ragassistant.model.ChatSession;
import com.example.ragassistant.model.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    Optional<ChatSession> findByIdAndUser(UUID id, User user);
}
