package com.example.ragassistant.service.retrieval;

import com.example.ragassistant.service.cache.CacheService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HybridRetrievalService {

    private final VectorRetriever vectorRetriever;
    private final KeywordRetriever keywordRetriever;
    private final CacheService cacheService;

    @Value("${app.rag.vector-weight:0.7}")
    private double vectorWeight;

    @Value("${app.rag.keyword-weight:0.3}")
    private double keywordWeight;

    @Value("${app.rag.retrieval-top-k:10}")
    private int retrievalTopK;

    public List<RetrievalScore> retrieveChunks(String query, List<Float> queryEmbedding, Set<UUID> documentIds) {
        List<UUID> sortedDocIds = documentIds.stream().sorted().toList();
        return cacheService.getCachedRetrieval(queryEmbedding, sortedDocIds)
                .orElseGet(() -> {
                    List<RetrievalScore> vectorResults = vectorRetriever.retrieve(queryEmbedding, retrievalTopK, documentIds);
                    List<RetrievalScore> keywordResults = keywordRetriever.retrieve(query, retrievalTopK, documentIds);
                    List<RetrievalScore> fused = fuse(vectorResults, keywordResults);
                    cacheService.cacheRetrieval(queryEmbedding, sortedDocIds, fused);
                    return fused;
                });
    }

    private List<RetrievalScore> fuse(List<RetrievalScore> vectorResults, List<RetrievalScore> keywordResults) {
        double maxVector = vectorResults.stream().mapToDouble(RetrievalScore::score).max().orElse(1.0);
        double maxKeyword = keywordResults.stream().mapToDouble(RetrievalScore::score).max().orElse(1.0);
        if (maxVector <= 0.0) {
            maxVector = 1.0;
        }
        if (maxKeyword <= 0.0) {
            maxKeyword = 1.0;
        }

        Map<UUID, Double> fused = new HashMap<>();
        for (RetrievalScore score : vectorResults) {
            double normalizedVector = score.score() / maxVector;
            fused.merge(score.chunkId(), vectorWeight * normalizedVector, Double::sum);
        }
        for (RetrievalScore score : keywordResults) {
            double normalizedKeyword = score.score() / maxKeyword;
            fused.merge(score.chunkId(), keywordWeight * normalizedKeyword, Double::sum);
        }

        List<RetrievalScore> sorted = new ArrayList<>();
        for (Map.Entry<UUID, Double> entry : fused.entrySet()) {
            sorted.add(new RetrievalScore(entry.getKey(), entry.getValue()));
        }
        sorted.sort(Comparator.comparing(RetrievalScore::score).reversed());
        return sorted.stream().limit(retrievalTopK).toList();
    }
}
