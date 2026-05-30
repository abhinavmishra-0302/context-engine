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
import com.example.ragassistant.service.cache.CacheService;
import com.example.ragassistant.service.monitoring.CustomMetricsService;
import com.example.ragassistant.service.retrieval.HybridRetrievalService;
import com.example.ragassistant.service.retrieval.RetrievalScore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryService {

    private final CurrentUserService currentUserService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final EmbeddingService embeddingService;
    private final HybridRetrievalService hybridRetrievalService;
    private final GeminiService geminiService;
    private final ChatHistoryService chatHistoryService;
    private final ChatMemoryService chatMemoryService;
    private final CitationService citationService;
    private final CacheService cacheService;
    private final CustomMetricsService customMetricsService;
    private final ObjectMapper objectMapper;

    @Value("${app.rag.max-context-chars-per-chunk:1400}")
    private int maxContextCharsPerChunk;

    public QueryResponse answer(QueryRequest request) {
        return answerInternal(request, null);
    }

    public QueryResponse answerStreaming(QueryRequest request, Consumer<String> tokenConsumer) {
        return answerInternal(request, tokenConsumer);
    }

    private QueryResponse answerInternal(QueryRequest request, Consumer<String> tokenConsumer) {
        customMetricsService.incrementQueryCount();
        UUID queryId = UUID.randomUUID();
        long overallStart = System.nanoTime();
        User user = currentUserService.getCurrentUser();
        List<Document> documents = resolveDocuments(user, request.documentIds());
        ChatSession session = resolveSession(user, request.sessionId());
        List<ChatMessage> history = session == null ? List.of() : chatMemoryService.loadRecentMessages(session);
        List<UUID> resolvedDocumentIds = documents.stream().map(Document::getId).toList();
        QueryResponse cachedResponse = cacheService.getCachedResponse(request.query(), request.sessionId(), resolvedDocumentIds)
                .orElse(null);
        if (cachedResponse != null) {
            customMetricsService.incrementCacheHit();
            if (tokenConsumer != null && !cachedResponse.answer().isBlank()) {
                tokenConsumer.accept(cachedResponse.answer());
            }
            persistResponse(user, session, request.query(), cachedResponse.answer());
            logStructured(queryId, user, request.sessionId(), resolvedDocumentIds, 0, 0, true, cachedResponse.answer().length(), elapsedMs(overallStart));
            return cachedResponse;
        }
        customMetricsService.incrementCacheMiss();

        long retrievalStart = System.nanoTime();
        List<Float> queryEmbedding = embeddingService.embedText(request.query());
        Set<UUID> allowedDocumentIds = documents.stream().map(Document::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<RetrievalScore> results = hybridRetrievalService.retrieveChunks(request.query(), queryEmbedding, allowedDocumentIds);
        long retrievalMs = elapsedMs(retrievalStart);
        customMetricsService.recordRetrievalDurationMs(retrievalMs);

        if (results.isEmpty()) {
            QueryResponse empty = new QueryResponse("I could not find relevant context in the selected documents.", List.of());
            cacheService.cacheResponse(request.query(), request.sessionId(), resolvedDocumentIds, empty);
            persistResponse(user, session, request.query(), empty.answer());
            if (tokenConsumer != null) {
                tokenConsumer.accept(empty.answer());
            }
            logStructured(queryId, user, request.sessionId(), resolvedDocumentIds, retrievalMs, 0, false, empty.answer().length(), elapsedMs(overallStart));
            return empty;
        }

        List<UUID> chunkIds = results.stream()
                .map(RetrievalScore::chunkId)
                .toList();
        Map<UUID, DocumentChunk> chunkMap = new HashMap<>();
        documentChunkRepository.findAllById(chunkIds).forEach(chunk -> chunkMap.put(chunk.getId(), chunk));

        List<DocumentChunk> orderedChunks = chunkIds.stream()
                .map(chunkMap::get)
                .filter(chunk -> chunk != null)
                .toList();
        if (orderedChunks.isEmpty()) {
            QueryResponse empty = new QueryResponse("I could not find relevant context in the selected documents.", List.of());
            cacheService.cacheResponse(request.query(), request.sessionId(), resolvedDocumentIds, empty);
            persistResponse(user, session, request.query(), empty.answer());
            if (tokenConsumer != null) {
                tokenConsumer.accept(empty.answer());
            }
            logStructured(queryId, user, request.sessionId(), resolvedDocumentIds, retrievalMs, 0, false, empty.answer().length(), elapsedMs(overallStart));
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
        customMetricsService.recordLlmDurationMs(llmMs);
        QueryResponse response = new QueryResponse(answer, citationService.buildCitations(orderedChunks));
        cacheService.cacheResponse(request.query(), request.sessionId(), resolvedDocumentIds, response);
        persistResponse(user, session, request.query(), answer);
        logStructured(queryId, user, request.sessionId(), resolvedDocumentIds, retrievalMs, llmMs, false, answer.length(), elapsedMs(overallStart));
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

    private void persistResponse(User user, ChatSession session, String query, String answer) {
        chatHistoryService.save(user, query, answer);
        if (session != null) {
            chatMemoryService.saveTurn(session, query, answer);
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

    private void logStructured(
            UUID queryId,
            User user,
            UUID sessionId,
            List<UUID> documentIds,
            long retrievalMs,
            long llmMs,
            boolean cacheHit,
            int responseSize,
            long totalTimeMs
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("queryId", queryId);
        payload.put("userId", user.getId());
        payload.put("sessionId", sessionId);
        payload.put("documentIds", documentIds);
        payload.put("retrievalTime", retrievalMs);
        payload.put("llmTime", llmMs);
        payload.put("cacheHit", cacheHit);
        payload.put("responseSize", responseSize);
        payload.put("totalTime", totalTimeMs);
        try {
            log.info(objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            log.info("queryId={}, userId={}, cacheHit={}", queryId, user.getId(), cacheHit);
        }
    }
}
