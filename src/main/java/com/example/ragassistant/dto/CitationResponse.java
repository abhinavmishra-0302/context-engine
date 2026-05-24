package com.example.ragassistant.dto;

import java.util.UUID;

public record CitationResponse(
        UUID documentId,
        String documentName,
        UUID chunkId,
        Integer chunkIndex,
        Integer page,
        String snippet
) {
}
