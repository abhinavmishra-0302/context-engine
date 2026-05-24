package com.example.ragassistant.service;

import com.example.ragassistant.dto.QueryRequest;
import com.example.ragassistant.dto.QueryResponse;
import com.example.ragassistant.exception.AppException;
import com.example.ragassistant.model.Document;
import com.example.ragassistant.model.DocumentChunk;
import com.example.ragassistant.model.DocumentStatus;
import com.example.ragassistant.model.User;
import com.example.ragassistant.repository.DocumentChunkRepository;
import com.example.ragassistant.repository.DocumentRepository;
import com.example.ragassistant.service.vector.VectorSearchResult;
import com.example.ragassistant.service.vector.VectorStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QueryService {

    private static final String CACHE_PREFIX = "rag:answer:";
    private final CurrentUserService currentUserService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final GeminiService geminiService;
    private final ChatHistoryService chatHistoryService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rag.top-k}")
    private int topK;

    @Value("${app.rag.cache-ttl-hours}")
    private long cacheTtlHours;

    public QueryResponse answer(QueryRequest request) {
        User user = currentUserService.getCurrentUser();
        List<Document> documents = validateDocuments(user, request.documentIds());

        String cacheKey = cacheKey(user.getId(), request.query(), request.documentIds());
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            QueryResponse response = deserialize(cached);
            chatHistoryService.save(user, request.query(), response.answer());
            return response;
        }

        List<Float> queryEmbedding = embeddingService.embedText(request.query());
        Set<String> allowedDocumentIds = documents.stream()
                .map(doc -> doc.getId().toString())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<VectorSearchResult> results = vectorStore.search(queryEmbedding, topK, allowedDocumentIds);
        if (results.isEmpty()) {
            QueryResponse empty = new QueryResponse("I could not find relevant context in the selected documents.", List.of());
            cache(cacheKey, empty);
            chatHistoryService.save(user, request.query(), empty.answer());
            return empty;
        }

        List<UUID> chunkIds = results.stream().map(result -> UUID.fromString(result.id())).toList();
        Map<UUID, DocumentChunk> chunkMap = new HashMap<>();
        documentChunkRepository.findAllById(chunkIds).forEach(chunk -> chunkMap.put(chunk.getId(), chunk));

        List<DocumentChunk> orderedChunks = chunkIds.stream()
                .map(chunkMap::get)
                .filter(chunk -> chunk != null)
                .toList();
        String context = buildContext(orderedChunks);
        String prompt = """
                You are a helpful assistant. Answer the question based only on the provided context.

                Context:
                %s

                Question:
                %s

                Answer clearly and concisely.
                """.formatted(context, request.query());

        String answer = geminiService.generateAnswer(prompt);
        List<String> sources = orderedChunks.stream()
                .map(chunk -> chunk.getDocument().getId() + ":" + chunk.getChunkIndex())
                .toList();
        QueryResponse response = new QueryResponse(answer, sources);
        cache(cacheKey, response);
        chatHistoryService.save(user, request.query(), answer);
        return response;
    }

    private List<Document> validateDocuments(User user, List<UUID> documentIds) {
        List<Document> docs = documentRepository.findByIdInAndUser(documentIds, user);
        if (docs.size() != documentIds.size()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "One or more documents are invalid");
        }
        if (docs.stream().anyMatch(doc -> doc.getStatus() != DocumentStatus.READY)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "All selected documents must be READY");
        }
        return docs;
    }

    private String buildContext(List<DocumentChunk> chunks) {
        List<String> parts = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            parts.add("[" + chunk.getDocument().getId() + ":" + chunk.getChunkIndex() + "] " + chunk.getText());
        }
        return String.join("\n\n", parts);
    }

    private String cacheKey(UUID userId, String query, List<UUID> documentIds) {
        List<String> sortedIds = documentIds.stream().map(UUID::toString).sorted().toList();
        String raw = userId + "|" + query.trim().toLowerCase(Locale.ROOT) + "|" + String.join(",", sortedIds);
        return CACHE_PREFIX + sha256(raw);
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

    private void cache(String key, QueryResponse response) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(response), Duration.ofHours(cacheTtlHours));
        } catch (JsonProcessingException ex) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to cache query response");
        }
    }

    private QueryResponse deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, QueryResponse.class);
        } catch (JsonProcessingException ex) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse cached response");
        }
    }
}
