package com.example.ragassistant.service.cache;

import com.example.ragassistant.dto.QueryResponse;
import com.example.ragassistant.exception.AppException;
import com.example.ragassistant.model.Document;
import com.example.ragassistant.service.retrieval.RetrievalScore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rag.query-cache-ttl-hours:6}")
    private long queryCacheTtlHours;

    @Value("${app.rag.retrieval-cache-ttl-hours:1}")
    private long retrievalCacheTtlHours;

    @Value("${app.rag.document-cache-ttl-hours:24}")
    private long documentCacheTtlHours;

    public Optional<QueryResponse> getCachedResponse(String query, UUID sessionId, List<UUID> documentIds) {
        String key = "rag:query:" + hashForQuery(query, sessionId, documentIds);
        String payload = redisTemplate.opsForValue().get(key);
        if (payload == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(payload, QueryResponse.class));
        } catch (JsonProcessingException ex) {
            redisTemplate.delete(key);
            log.warn("Failed to parse query cache payload for key {}", key, ex);
            return Optional.empty();
        }
    }

    public void cacheResponse(String query, UUID sessionId, List<UUID> documentIds, QueryResponse response) {
        String key = "rag:query:" + hashForQuery(query, sessionId, documentIds);
        writeJson(key, response, Duration.ofHours(queryCacheTtlHours));
    }

    public Optional<List<RetrievalScore>> getCachedRetrieval(List<Float> queryEmbedding, List<UUID> documentIds) {
        String key = "rag:retrieval:" + hashForRetrieval(queryEmbedding, documentIds);
        String payload = redisTemplate.opsForValue().get(key);
        if (payload == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(payload, new TypeReference<List<RetrievalScore>>() {
            }));
        } catch (JsonProcessingException ex) {
            redisTemplate.delete(key);
            log.warn("Failed to parse retrieval cache payload for key {}", key, ex);
            return Optional.empty();
        }
    }

    public void cacheRetrieval(List<Float> queryEmbedding, List<UUID> documentIds, List<RetrievalScore> results) {
        String key = "rag:retrieval:" + hashForRetrieval(queryEmbedding, documentIds);
        writeJson(key, results, Duration.ofHours(retrievalCacheTtlHours));
    }

    public Optional<DocumentCacheEntry> getCachedDocumentMetadata(UUID documentId) {
        String payload = redisTemplate.opsForValue().get(documentCacheKey(documentId));
        if (payload == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(payload, DocumentCacheEntry.class));
        } catch (JsonProcessingException ex) {
            redisTemplate.delete(documentCacheKey(documentId));
            return Optional.empty();
        }
    }

    public void cacheDocumentMetadata(Document document) {
        DocumentCacheEntry entry = new DocumentCacheEntry(
                document.getId(),
                document.getFileName(),
                document.getStatus(),
                document.getCreatedAt()
        );
        writeJson(documentCacheKey(document.getId()), entry, Duration.ofHours(documentCacheTtlHours));
    }

    public void invalidateDocumentCache(UUID documentId) {
        redisTemplate.delete(documentCacheKey(documentId));
    }

    private String hashForQuery(String query, UUID sessionId, List<UUID> documentIds) {
        List<String> sortedIds = documentIds.stream().map(UUID::toString).sorted().toList();
        String raw = (query == null ? "" : query.trim().toLowerCase(Locale.ROOT))
                + "|" + (sessionId == null ? "no-session" : sessionId)
                + "|" + String.join(",", sortedIds);
        return sha256(raw);
    }

    private String hashForRetrieval(List<Float> queryEmbedding, List<UUID> documentIds) {
        List<String> sortedIds = documentIds.stream().map(UUID::toString).sorted().toList();
        String embedding = queryEmbedding.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        return sha256(embedding + "|" + String.join(",", sortedIds));
    }

    private String documentCacheKey(UUID documentId) {
        return "document:" + documentId;
    }

    private void writeJson(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException ex) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to write cache entry");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to build cache key");
        }
    }
}
