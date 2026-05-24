package com.example.ragassistant.service;

import com.example.ragassistant.dto.DocumentResponse;
import com.example.ragassistant.dto.DocumentUploadResponse;
import com.example.ragassistant.exception.AppException;
import com.example.ragassistant.model.Document;
import com.example.ragassistant.model.DocumentStatus;
import com.example.ragassistant.model.User;
import com.example.ragassistant.repository.DocumentRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
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
    private final DocumentProcessingService documentProcessingService;

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
                .status(DocumentStatus.PROCESSING)
                .createdAt(Instant.now())
                .build();
        documentRepository.save(document);

        Path savedFile = storeFile(documentId, safeName, file);
        documentProcessingService.processDocumentAsync(documentId, savedFile);
        return new DocumentUploadResponse(documentId, DocumentStatus.PROCESSING);
    }

    public List<DocumentResponse> listCurrentUserDocuments() {
        User user = currentUserService.getCurrentUser();
        return documentRepository.findByUserOrderByCreatedAtDesc(user).stream()
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
