package com.example.ragassistant.dto;

import java.util.UUID;

public record SignupResponse(UUID userId, String token) {
}
