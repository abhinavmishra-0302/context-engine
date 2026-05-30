package com.example.ragassistant.kafka.event;

import java.util.UUID;

public record DocumentProcessingCompletedEvent(
        UUID documentId,
        UUID userId,
        int chunks
) {
}
