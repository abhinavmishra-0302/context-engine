package com.example.ragassistant.service.cache;

import com.example.ragassistant.model.DocumentStatus;
import java.time.Instant;
import java.util.UUID;

public record DocumentCacheEntry(
        UUID documentId,
        String fileName,
        DocumentStatus status,
        Instant createdAt
) {
}
