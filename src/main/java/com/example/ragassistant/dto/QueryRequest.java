package com.example.ragassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record QueryRequest(
        @NotBlank String query,
        @NotEmpty List<UUID> documentIds
) {
}
