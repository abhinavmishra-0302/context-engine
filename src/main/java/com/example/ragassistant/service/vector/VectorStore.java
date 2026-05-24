package com.example.ragassistant.service.vector;

import java.util.List;
import java.util.Set;

public interface VectorStore {

    void upsert(List<VectorRecord> records);

    List<VectorSearchResult> search(List<Float> queryEmbedding, int topK, Set<String> documentIds);
}
