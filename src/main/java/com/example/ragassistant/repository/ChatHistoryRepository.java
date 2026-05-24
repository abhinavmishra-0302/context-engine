package com.example.ragassistant.repository;

import com.example.ragassistant.model.ChatHistory;
import com.example.ragassistant.model.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatHistoryRepository extends JpaRepository<ChatHistory, UUID> {

    List<ChatHistory> findByUserOrderByCreatedAtDesc(User user);
}
