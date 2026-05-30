package com.example.ragassistant.service.retrieval;

import com.example.ragassistant.service.vector.VectorSearchResult;
import com.example.ragassistant.service.vector.VectorStore;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VectorRetriever {

    private final VectorStore vectorStore;

    public List<RetrievalScore> retrieve(List<Float> queryEmbedding, int topK, Set<UUID> documentIds) {
        Set<String> allowedDocumentIds = documentIds.stream().map(UUID::toString).collect(java.util.stream.Collectors.toSet());
        List<VectorSearchResult> results = vectorStore.search(queryEmbedding, topK, allowedDocumentIds);
        return results.stream()
                .map(result -> {
                    try {
                        return new RetrievalScore(UUID.fromString(result.id()), result.score());
                    } catch (Exception ignored) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
