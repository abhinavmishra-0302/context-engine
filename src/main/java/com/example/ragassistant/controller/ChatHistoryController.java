package com.example.ragassistant.controller;

import com.example.ragassistant.dto.ChatHistoryResponse;
import com.example.ragassistant.service.ChatHistoryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;

    @GetMapping("/history")
    public List<ChatHistoryResponse> history() {
        return chatHistoryService.listForCurrentUser();
    }
}
