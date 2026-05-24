package com.example.ragassistant.service.vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.rag.vector-provider", havingValue = "faiss", matchIfMissing = true)
public class FaissVectorStore implements VectorStore {

    private final Map<String, VectorRecord> records = new ConcurrentHashMap<>();

    @Override
    public void upsert(List<VectorRecord> records) {
        records.forEach(record -> this.records.put(record.id(), record));
    }

    @Override
    public List<VectorSearchResult> search(List<Float> queryEmbedding, int topK, Set<String> documentIds) {
        List<VectorSearchResult> scored = new ArrayList<>();
        for (VectorRecord record : records.values()) {
            String documentId = String.valueOf(record.metadata().get("document_id"));
            if (!documentIds.isEmpty() && !documentIds.contains(documentId)) {
                continue;
            }
            scored.add(new VectorSearchResult(
                    record.id(),
                    cosineSimilarity(queryEmbedding, record.embedding()),
                    record.metadata()
            ));
        }
        scored.sort(Comparator.comparing(VectorSearchResult::score).reversed());
        return scored.stream().limit(topK).toList();
    }

    private double cosineSimilarity(List<Float> a, List<Float> b) {
        int size = Math.min(a.size(), b.size());
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < size; i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
