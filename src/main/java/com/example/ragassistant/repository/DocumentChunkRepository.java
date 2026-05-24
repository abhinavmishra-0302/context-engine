package com.example.ragassistant.repository;

import com.example.ragassistant.model.Document;
import com.example.ragassistant.model.DocumentChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findByDocumentOrderByChunkIndexAsc(Document document);
}
