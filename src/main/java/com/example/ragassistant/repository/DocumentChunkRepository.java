package com.example.ragassistant.repository;

import com.example.ragassistant.model.Document;
import com.example.ragassistant.model.DocumentChunk;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findByDocumentOrderByChunkIndexAsc(Document document);

    @Query(value = """
            SELECT dc.id AS chunkId,
                   ts_rank(dc.search_vector, plainto_tsquery('english', :query)) AS score
            FROM document_chunks dc
            WHERE dc.document_id IN (:documentIds)
              AND dc.search_vector @@ plainto_tsquery('english', :query)
            ORDER BY score DESC
            LIMIT :topK
            """, nativeQuery = true)
    List<ChunkScoreProjection> keywordSearch(
            @Param("query") String query,
            @Param("documentIds") Set<UUID> documentIds,
            @Param("topK") int topK
    );
}
