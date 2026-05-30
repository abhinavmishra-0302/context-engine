package com.example.ragassistant.service;

import com.example.ragassistant.dto.DocumentResponse;
import com.example.ragassistant.dto.DocumentUploadResponse;
import com.example.ragassistant.exception.AppException;
import com.example.ragassistant.kafka.DocumentUploadProducer;
import com.example.ragassistant.kafka.event.DocumentUploadedEvent;
import com.example.ragassistant.model.Document;
import com.example.ragassistant.model.DocumentProcessingJob;
import com.example.ragassistant.model.DocumentStatus;
import com.example.ragassistant.model.ProcessingJobStatus;
import com.example.ragassistant.model.User;
import com.example.ragassistant.repository.DocumentRepository;
import com.example.ragassistant.repository.ProcessingJobRepository;
import com.example.ragassistant.service.cache.CacheService;
import com.example.ragassistant.service.monitoring.CustomMetricsService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final CurrentUserService currentUserService;
    private final DocumentRepository documentRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final DocumentProcessingService documentProcessingService;
    private final Optional<DocumentUploadProducer> documentUploadProducer;
    private final CacheService cacheService;
    private final CustomMetricsService customMetricsService;

    @Value("${app.storage.upload-dir}")
    private String uploadDir;

    public DocumentUploadResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "File is required");
        }
        User user = currentUserService.getCurrentUser();
        UUID documentId = UUID.randomUUID();
        String safeName = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");

        Document document = Document.builder()
                .id(documentId)
                .user(user)
                .fileName(safeName)
                .status(DocumentStatus.UPLOADED)
                .createdAt(Instant.now())
                .build();
        documentRepository.save(document);
        cacheService.cacheDocumentMetadata(document);
        processingJobRepository.save(DocumentProcessingJob.builder()
                .id(UUID.randomUUID())
                .document(document)
                .status(ProcessingJobStatus.UPLOADED)
                .createdAt(Instant.now())
                .build());

        Path savedFile = storeFile(documentId, safeName, file);
        DocumentUploadedEvent event = new DocumentUploadedEvent(documentId, user.getId(), savedFile.toAbsolutePath().toString(), safeName);
        if (documentUploadProducer.isPresent()) {
            documentUploadProducer.get().publishUploaded(event);
        } else {
            documentProcessingService.processUploadedDocument(event);
        }
        customMetricsService.incrementDocumentsUploaded();
        return new DocumentUploadResponse(documentId, DocumentStatus.UPLOADED);
    }

    public List<DocumentResponse> listCurrentUserDocuments() {
        User user = currentUserService.getCurrentUser();
        return documentRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .peek(cacheService::cacheDocumentMetadata)
                .map(doc -> new DocumentResponse(doc.getId(), doc.getFileName(), doc.getStatus()))
                .toList();
    }

    private Path storeFile(UUID documentId, String fileName, MultipartFile file) {
        try {
            Path root = Path.of(uploadDir);
            Files.createDirectories(root);
            Path target = root.resolve(documentId + "_" + fileName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException ex) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store uploaded file");
        }
    }
}
