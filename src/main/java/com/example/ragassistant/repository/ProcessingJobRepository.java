package com.example.ragassistant.repository;

import com.example.ragassistant.model.Document;
import com.example.ragassistant.model.DocumentProcessingJob;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingJobRepository extends JpaRepository<DocumentProcessingJob, UUID> {

    Optional<DocumentProcessingJob> findTopByDocumentOrderByCreatedAtDesc(Document document);
}
