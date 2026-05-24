package com.example.ragassistant.controller;

import com.example.ragassistant.service.StreamingService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/query")
@RequiredArgsConstructor
public class StreamController {

    private final StreamingService streamingService;

    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream(
            @RequestParam String query,
            @RequestParam(required = false) UUID sessionId,
            @RequestParam(required = false) List<UUID> documentIds
    ) {
        return streamingService.streamQuery(query, sessionId, documentIds);
    }
}
