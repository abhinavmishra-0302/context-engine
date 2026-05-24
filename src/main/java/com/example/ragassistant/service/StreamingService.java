package com.example.ragassistant.service;

import com.example.ragassistant.dto.QueryRequest;
import com.example.ragassistant.dto.QueryResponse;
import com.example.ragassistant.exception.AppException;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingService {

    private final QueryService queryService;

    @Qualifier("streamingTaskExecutor")
    private final org.springframework.core.task.TaskExecutor streamingTaskExecutor;

    public SseEmitter streamQuery(String query, UUID sessionId, List<UUID> documentIds) {
        if (query == null || query.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "query must not be blank");
        }
        SseEmitter emitter = new SseEmitter(0L);
        streamingTaskExecutor.execute(() -> runStreamingQuery(emitter, query, sessionId, documentIds));
        return emitter;
    }

    private void runStreamingQuery(SseEmitter emitter, String query, UUID sessionId, List<UUID> documentIds) {
        try {
            QueryRequest request = new QueryRequest(query, documentIds, sessionId);
            QueryResponse response = queryService.answerStreaming(request, token -> sendToken(emitter, token));
            emitter.send(SseEmitter.event().name("sources").data(response.sources()));
            emitter.send(SseEmitter.event().name("complete").data("done"));
            emitter.complete();
        } catch (Exception ex) {
            log.warn("Streaming query failed", ex);
            try {
                String message = ex instanceof AppException appException
                        ? appException.getMessage()
                        : "Streaming failed";
                emitter.send(SseEmitter.event().name("error").data(message));
            } catch (IOException ioException) {
                log.debug("Failed to emit streaming error event", ioException);
            }
            emitter.completeWithError(ex);
        }
    }

    private void sendToken(SseEmitter emitter, String token) {
        try {
            emitter.send(SseEmitter.event().name("token").data(token));
        } catch (IOException ex) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Client disconnected while streaming");
        }
    }
}
