package com.example.ragassistant.dto;

import java.util.List;

public record QueryResponse(String answer, List<String> sources) {
}
