package com.example.ragassistant.service.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ragassistant.service.cache.CacheService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class HybridRetrievalServiceTest {

    @Mock
    private VectorRetriever vectorRetriever;

    @Mock
    private KeywordRetriever keywordRetriever;

    @Mock
    private CacheService cacheService;

    private HybridRetrievalService hybridRetrievalService;

    @BeforeEach
    void setUp() {
        hybridRetrievalService = new HybridRetrievalService(vectorRetriever, keywordRetriever, cacheService);
        ReflectionTestUtils.setField(hybridRetrievalService, "vectorWeight", 0.7d);
        ReflectionTestUtils.setField(hybridRetrievalService, "keywordWeight", 0.3d);
        ReflectionTestUtils.setField(hybridRetrievalService, "retrievalTopK", 10);
    }

    @Test
    void shouldUseCacheWhenPresent() {
        UUID chunkId = UUID.randomUUID();
        List<RetrievalScore> cached = List.of(new RetrievalScore(chunkId, 0.8));
        when(cacheService.getCachedRetrieval(anyList(), anyList())).thenReturn(Optional.of(cached));

        List<RetrievalScore> results = hybridRetrievalService.retrieveChunks("query", List.of(0.1f, 0.2f), Set.of(UUID.randomUUID()));

        assertThat(results).isEqualTo(cached);
    }

    @Test
    void shouldFuseVectorAndKeywordScoresAndCacheResult() {
        UUID chunkA = UUID.randomUUID();
        UUID chunkB = UUID.randomUUID();
        UUID docId = UUID.randomUUID();

        when(cacheService.getCachedRetrieval(anyList(), anyList())).thenReturn(Optional.empty());
        when(vectorRetriever.retrieve(anyList(), anyInt(), anySet())).thenReturn(List.of(
                new RetrievalScore(chunkA, 0.9),
                new RetrievalScore(chunkB, 0.45)
        ));
        when(keywordRetriever.retrieve(any(), anyInt(), anySet())).thenReturn(List.of(
                new RetrievalScore(chunkB, 0.8)
        ));

        List<RetrievalScore> results = hybridRetrievalService.retrieveChunks("garbage collection", List.of(0.3f, 0.9f), Set.of(docId));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).chunkId()).isEqualTo(chunkA);
        assertThat(results.get(1).chunkId()).isEqualTo(chunkB);
        verify(cacheService).cacheRetrieval(anyList(), anyList(), anyList());
    }
}
