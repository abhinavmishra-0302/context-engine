package com.example.ragassistant.kafka.event;

import java.util.UUID;

public record DocumentProcessingFailedEvent(
        UUID documentId,
        UUID userId,
        String error
) {
}
