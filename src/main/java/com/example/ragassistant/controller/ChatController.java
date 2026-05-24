package com.example.ragassistant.controller;

import com.example.ragassistant.dto.ChatMessageRequest;
import com.example.ragassistant.dto.ChatSessionResponse;
import com.example.ragassistant.dto.QueryRequest;
import com.example.ragassistant.dto.QueryResponse;
import com.example.ragassistant.service.ChatMemoryService;
import com.example.ragassistant.service.QueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatMemoryService chatMemoryService;
    private final QueryService queryService;

    @PostMapping("/session")
    public ChatSessionResponse createSession() {
        return new ChatSessionResponse(chatMemoryService.createSessionForCurrentUser());
    }

    @PostMapping("/message")
    public QueryResponse message(@Valid @RequestBody ChatMessageRequest request) {
        return queryService.answer(new QueryRequest(request.query(), request.documentIds(), request.sessionId()));
    }
}
