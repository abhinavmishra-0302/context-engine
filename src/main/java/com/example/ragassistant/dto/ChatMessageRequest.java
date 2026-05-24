package com.example.ragassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ChatMessageRequest(
        @NotNull UUID sessionId,
        @NotBlank String query,
        List<UUID> documentIds
) {
}
