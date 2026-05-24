package com.example.ragassistant.service;

import com.example.ragassistant.exception.AppException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com/v1beta").build();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${app.gemini.api-key}")
    private String apiKey;

    @Value("${app.gemini.embedding-model}")
    private String embeddingModel;

    @Value("${app.gemini.chat-model}")
    private String chatModel;

    public List<List<Float>> createEmbeddings(List<String> inputs) {
        requireApiKey();
        try {
            List<List<Float>> embeddings = new ArrayList<>();
            String modelName = normalizeModelName(embeddingModel);
            for (String input : inputs) {
                JsonNode node = restClient.post()
                        .uri("/models/{model}:embedContent", modelName)
                        .header("x-goog-api-key", apiKey)
                        .body(Map.of(
                                "content", Map.of(
                                        "parts", List.of(Map.of("text", input))
                                )
                        ))
                        .retrieve()
                        .body(JsonNode.class);
                if (node == null || node.path("embedding").path("values").isMissingNode()) {
                    throw new AppException(HttpStatus.BAD_GATEWAY, "Invalid embedding response from Gemini");
                }
                List<Float> vector = new ArrayList<>();
                for (JsonNode valueNode : node.path("embedding").path("values")) {
                    vector.add(valueNode.floatValue());
                }
                embeddings.add(vector);
            }
            return embeddings;
        } catch (Exception ex) {
            log.error("Embedding generation failed", ex);
            throw new AppException(HttpStatus.BAD_GATEWAY, "Failed to generate embeddings");
        }
    }

    public String generateAnswer(String prompt) {
        requireApiKey();
        try {
            String modelName = normalizeModelName(chatModel);
            JsonNode node = restClient.post()
                    .uri("/models/{model}:generateContent", modelName)
                    .header("x-goog-api-key", apiKey)
                    .body(Map.of(
                            "contents", List.of(
                                    Map.of(
                                            "role", "user",
                                            "parts", List.of(Map.of("text", prompt))
                                    )
                            )
                    ))
                    .retrieve()
                    .body(JsonNode.class);
            if (node == null) {
                throw new AppException(HttpStatus.BAD_GATEWAY, "Invalid response from LLM");
            }
            JsonNode candidates = node.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                StringBuilder builder = new StringBuilder();
                for (JsonNode candidate : candidates) {
                    JsonNode parts = candidate.path("content").path("parts");
                    if (!parts.isArray()) {
                        continue;
                    }
                    for (JsonNode part : parts) {
                        String text = part.path("text").asText("");
                        if (!text.isBlank()) {
                            if (!builder.isEmpty()) {
                                builder.append('\n');
                            }
                            builder.append(text);
                        }
                    }
                }
                if (!builder.isEmpty()) {
                    return builder.toString();
                }
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            log.error("LLM completion failed", ex);
            throw new AppException(HttpStatus.BAD_GATEWAY, "Failed to generate answer from LLM");
        }
    }

    public String streamAnswer(String prompt, Consumer<String> tokenConsumer) {
        requireApiKey();
        try {
            String modelName = normalizeModelName(chatModel);
            String body = objectMapper.writeValueAsString(Map.of(
                    "contents", List.of(
                            Map.of(
                                    "role", "user",
                                    "parts", List.of(Map.of("text", prompt))
                            )
                    )
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                            + modelName + ":streamGenerateContent?alt=sse"))
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<java.util.stream.Stream<String>> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() >= 400) {
                throw new AppException(HttpStatus.BAD_GATEWAY, "LLM streaming request failed");
            }

            StringBuilder answer = new StringBuilder();
            try (java.util.stream.Stream<String> lines = response.body()) {
                lines.forEach(line -> {
                    String trimmed = line == null ? "" : line.trim();
                    if (!trimmed.startsWith("data:")) {
                        return;
                    }
                    String data = trimmed.substring("data:".length()).trim();
                    if (data.isBlank()) {
                        return;
                    }
                    String eventText = extractAnswerText(safeReadTree(data));
                    if (eventText.isBlank()) {
                        return;
                    }
                    String delta = eventText;
                    String current = answer.toString();
                    if (eventText.startsWith(current)) {
                        delta = eventText.substring(current.length());
                    } else if (current.endsWith(eventText)) {
                        delta = "";
                    }
                    if (!delta.isBlank()) {
                        tokenConsumer.accept(delta);
                        answer.append(delta);
                    }
                });
            }
            return answer.toString();
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("LLM streaming failed", ex);
            throw new AppException(HttpStatus.BAD_GATEWAY, "Failed to stream answer from LLM");
        }
    }

    private void requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Gemini API key is not configured");
        }
    }

    private String normalizeModelName(String modelName) {
        String clean = Objects.requireNonNullElse(modelName, "").trim();
        if (clean.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Gemini model is not configured");
        }
        if (clean.startsWith("models/")) {
            return clean.substring("models/".length());
        }
        return clean;
    }

    private JsonNode safeReadTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String extractAnswerText(JsonNode node) {
        if (node == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        JsonNode candidates = node.path("candidates");
        if (!candidates.isArray()) {
            return "";
        }
        for (JsonNode candidate : candidates) {
            JsonNode parts = candidate.path("content").path("parts");
            if (!parts.isArray()) {
                continue;
            }
            for (JsonNode part : parts) {
                String text = part.path("text").asText("");
                if (!text.isBlank()) {
                    builder.append(text);
                }
            }
        }
        return builder.toString();
    }
}
