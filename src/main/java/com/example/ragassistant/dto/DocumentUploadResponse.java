package com.example.ragassistant.dto;

import com.example.ragassistant.model.DocumentStatus;
import java.util.UUID;

public record DocumentUploadResponse(UUID documentId, DocumentStatus status) {
}
