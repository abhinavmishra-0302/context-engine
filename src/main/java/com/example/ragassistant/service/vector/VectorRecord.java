package com.example.ragassistant.service.vector;

import java.util.List;
import java.util.Map;

public record VectorRecord(String id, List<Float> embedding, Map<String, Object> metadata) {
}
