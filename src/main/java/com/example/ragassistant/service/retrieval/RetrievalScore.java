package com.example.ragassistant.service.retrieval;

import java.util.UUID;

public record RetrievalScore(UUID chunkId, double score) {
}
