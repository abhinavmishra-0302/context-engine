package com.example.ragassistant.dto;

import java.time.Instant;

public record ChatHistoryResponse(String query, String response, Instant timestamp) {
}
