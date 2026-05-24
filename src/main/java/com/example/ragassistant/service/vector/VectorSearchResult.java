package com.example.ragassistant.service.vector;

import java.util.Map;

public record VectorSearchResult(String id, double score, Map<String, Object> metadata) {
}
