package com.example.ragassistant.service.retrieval;

import com.example.ragassistant.repository.DocumentChunkRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KeywordRetriever {

    private final DocumentChunkRepository documentChunkRepository;

    public List<RetrievalScore> retrieve(String query, int topK, Set<UUID> documentIds) {
        if (documentIds.isEmpty()) {
            return List.of();
        }
        return documentChunkRepository.keywordSearch(query, documentIds, topK).stream()
                .map(row -> new RetrievalScore(row.getChunkId(), row.getScore() == null ? 0.0 : row.getScore()))
                .toList();
    }
}
