package com.example.ragassistant.kafka.event;

import java.util.UUID;

public record DocumentUploadedEvent(
        UUID documentId,
        UUID userId,
        String fileLocation,
        String fileName
) {
}
