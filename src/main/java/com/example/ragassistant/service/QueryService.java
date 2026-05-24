package com.example.ragassistant.service;

import com.example.ragassistant.dto.QueryRequest;
import com.example.ragassistant.dto.QueryResponse;
import com.example.ragassistant.exception.AppException;
import com.example.ragassistant.model.ChatMessage;
import com.example.ragassistant.model.ChatSession;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryService {

    private static final String CACHE_PREFIX = "rag:answer:";
    private final CurrentUserService currentUserService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final GeminiService geminiService;
    private final ChatHistoryService chatHistoryService;
    private final ChatMemoryService chatMemoryService;
    private final CitationService citationService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rag.top-k}")
    private int topK;

    @Value("${app.rag.cache-ttl-hours}")
    private long cacheTtlHours;

    @Value("${app.rag.max-context-chars-per-chunk:1400}")
    private int maxContextCharsPerChunk;

    public QueryResponse answer(QueryRequest request) {
        return answerInternal(request, null);
    }

    public QueryResponse answerStreaming(QueryRequest request, Consumer<String> tokenConsumer) {
        return answerInternal(request, tokenConsumer);
    }

    private QueryResponse answerInternal(QueryRequest request, Consumer<String> tokenConsumer) {
        long overallStart = System.nanoTime();
        User user = currentUserService.getCurrentUser();
        List<Document> documents = resolveDocuments(user, request.documentIds());
        ChatSession session = resolveSession(user, request.sessionId());
        List<ChatMessage> history = session == null ? List.of() : chatMemoryService.loadRecentMessages(session);

        List<UUID> resolvedDocumentIds = documents.stream().map(Document::getId).toList();
        String cacheKey = cacheKey(user.getId(), request.query(), resolvedDocumentIds, request.sessionId());
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            QueryResponse response = deserialize(cached);
            if (response != null) {
                log.info("Cache hit for query");
                if (tokenConsumer != null && !response.answer().isBlank()) {
                    tokenConsumer.accept(response.answer());
                }
                persistResponse(user, session, request.query(), response.answer());
                return response;
            }
            redisTemplate.delete(cacheKey);
        }
        log.info("Cache miss for query");

        long retrievalStart = System.nanoTime();
        List<Float> queryEmbedding = embeddingService.embedText(request.query());
        Set<String> allowedDocumentIds = documents.stream()
                .map(doc -> doc.getId().toString())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<VectorSearchResult> results = vectorStore.search(queryEmbedding, topK, allowedDocumentIds);
        long retrievalMs = elapsedMs(retrievalStart);
        if (results.isEmpty()) {
            QueryResponse empty = new QueryResponse("I could not find relevant context in the selected documents.", List.of());
            cache(cacheKey, empty);
            persistResponse(user, session, request.query(), empty.answer());
            if (tokenConsumer != null) {
                tokenConsumer.accept(empty.answer());
            }
            log.info("Query completed with no results in {} ms", elapsedMs(overallStart));
            return empty;
        }

        List<UUID> chunkIds = results.stream()
                .map(result -> parseUuid(result.id()))
                .filter(id -> id != null)
                .toList();
        Map<UUID, DocumentChunk> chunkMap = new HashMap<>();
        documentChunkRepository.findAllById(chunkIds).forEach(chunk -> chunkMap.put(chunk.getId(), chunk));

        List<DocumentChunk> orderedChunks = chunkIds.stream()
                .map(chunkMap::get)
                .filter(chunk -> chunk != null)
                .toList();
        if (orderedChunks.isEmpty()) {
            QueryResponse empty = new QueryResponse("I could not find relevant context in the selected documents.", List.of());
            cache(cacheKey, empty);
            persistResponse(user, session, request.query(), empty.answer());
            if (tokenConsumer != null) {
                tokenConsumer.accept(empty.answer());
            }
            log.info("Query completed with no persisted chunks in {} ms", elapsedMs(overallStart));
            return empty;
        }

        String context = buildRetrievedContext(orderedChunks);
        String conversationHistory = session == null ? "None" : chatMemoryService.formatConversationHistory(history);
        String prompt = """
                You are a helpful assistant.

                Conversation History:
                %s

                Retrieved Context:
                %s

                Question:
                %s

                Answer clearly and concisely.
                """.formatted(conversationHistory, context, request.query());

        long llmStart = System.nanoTime();
        String answer = tokenConsumer == null
                ? geminiService.generateAnswer(prompt)
                : geminiService.streamAnswer(prompt, tokenConsumer);
        long llmMs = elapsedMs(llmStart);
        QueryResponse response = new QueryResponse(answer, citationService.buildCitations(orderedChunks));
        cache(cacheKey, response);
        persistResponse(user, session, request.query(), answer);
        log.info(
                "Query completed in {} ms (retrieval={} ms, llm={} ms, docs={}, chunks={})",
                elapsedMs(overallStart),
                retrievalMs,
                llmMs,
                documents.size(),
                orderedChunks.size()
        );
        return response;
    }

    private List<Document> resolveDocuments(User user, List<UUID> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            List<Document> docs = documentRepository.findByUserOrderByCreatedAtDesc(user).stream()
                    .filter(doc -> doc.getStatus() == DocumentStatus.READY)
                    .toList();
            if (docs.isEmpty()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "No READY documents available for current user");
            }
            return docs;
        }

        List<Document> docs = documentRepository.findByIdInAndUser(documentIds, user);
        if (docs.size() != documentIds.size()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "One or more documents are invalid");
        }
        if (docs.stream().anyMatch(doc -> doc.getStatus() != DocumentStatus.READY)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "All selected documents must be READY");
        }
        return docs;
    }

    private ChatSession resolveSession(User user, UUID sessionId) {
        if (sessionId == null) {
            return null;
        }
        return chatMemoryService.requireOwnedSession(user, sessionId);
    }

    private String buildRetrievedContext(List<DocumentChunk> chunks) {
        List<String> parts = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            String snippet = truncate(chunk.getText(), maxContextCharsPerChunk);
            parts.add(
                    "[doc=" + chunk.getDocument().getFileName()
                            + ", page=" + chunk.getPageNumber()
                            + ", chunk=" + chunk.getChunkIndex()
                            + "] " + snippet
            );
        }
        return String.join("\n\n", parts);
    }

    private String cacheKey(UUID userId, String query, List<UUID> documentIds, UUID sessionId) {
        List<String> sortedIds = documentIds.stream().map(UUID::toString).sorted().toList();
        String raw = userId
                + "|" + query.trim().toLowerCase(Locale.ROOT)
                + "|" + String.join(",", sortedIds)
                + "|" + (sessionId == null ? "no-session" : sessionId.toString());
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
            log.warn("Failed to parse cached response, cache entry will be ignored", ex);
            return null;
        }
    }

    private void persistResponse(User user, ChatSession session, String query, String answer) {
        chatHistoryService.save(user, query, answer);
        if (session != null) {
            chatMemoryService.saveTurn(session, query, answer);
        }
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }
}
