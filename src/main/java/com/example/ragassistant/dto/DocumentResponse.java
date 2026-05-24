package com.example.ragassistant.dto;

import com.example.ragassistant.model.DocumentStatus;
import java.util.UUID;

public record DocumentResponse(UUID documentId, String fileName, DocumentStatus status) {
}
