package com.example.ragassistant.repository;

import com.example.ragassistant.model.Document;
import com.example.ragassistant.model.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByUserOrderByCreatedAtDesc(User user);

    Optional<Document> findByIdAndUser(UUID id, User user);

    List<Document> findByIdInAndUser(Collection<UUID> ids, User user);
}
