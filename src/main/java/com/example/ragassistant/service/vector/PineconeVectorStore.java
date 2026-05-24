package com.example.ragassistant.service.vector;

import com.example.ragassistant.exception.AppException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
@ConditionalOnProperty(name = "app.rag.vector-provider", havingValue = "pinecone")
public class PineconeVectorStore implements VectorStore {

    @Value("${app.pinecone.api-key}")
    private String apiKey;

    @Value("${app.pinecone.index-host}")
    private String indexHost;

    @Value("${app.pinecone.namespace:default}")
    private String namespace;

    @Override
    public void upsert(List<VectorRecord> records) {
        validateConfig();
        List<Map<String, Object>> vectors = new ArrayList<>();
        for (VectorRecord record : records) {
            vectors.add(Map.of(
                    "id", record.id(),
                    "values", record.embedding(),
                    "metadata", record.metadata()
            ));
        }
        RestClient client = RestClient.builder().baseUrl("https://" + indexHost).build();
        try {
            client.post()
                    .uri("/vectors/upsert")
                    .header("Api-Key", apiKey)
                    .body(Map.of("vectors", vectors, "namespace", namespace))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.error("Pinecone upsert failed", ex);
            throw new AppException(HttpStatus.BAD_GATEWAY, "Failed to upsert vectors");
        }
    }

    @Override
    public List<VectorSearchResult> search(List<Float> queryEmbedding, int topK, Set<String> documentIds) {
        validateConfig();
        RestClient client = RestClient.builder().baseUrl("https://" + indexHost).build();
        Map<String, Object> body = new HashMap<>();
        body.put("vector", queryEmbedding);
        body.put("topK", topK);
        body.put("includeMetadata", true);
        body.put("namespace", namespace);
        if (!documentIds.isEmpty()) {
            body.put("filter", Map.of("document_id", Map.of("$in", documentIds)));
        }
        try {
            JsonNode response = client.post()
                    .uri("/query")
                    .header("Api-Key", apiKey)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            List<VectorSearchResult> results = new ArrayList<>();
            if (response == null || !response.has("matches")) {
                return results;
            }
            for (JsonNode match : response.get("matches")) {
                Map<String, Object> metadata = new HashMap<>();
                JsonNode metadataNode = match.path("metadata");
                metadataNode.fields().forEachRemaining(entry -> metadata.put(entry.getKey(), entry.getValue().asText()));
                results.add(new VectorSearchResult(
                        match.path("id").asText(),
                        match.path("score").asDouble(),
                        metadata
                ));
            }
            return results;
        } catch (Exception ex) {
            log.error("Pinecone query failed", ex);
            throw new AppException(HttpStatus.BAD_GATEWAY, "Failed to query vectors");
        }
    }

    private void validateConfig() {
        if (apiKey == null || apiKey.isBlank() || indexHost == null || indexHost.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Pinecone configuration is missing");
        }
    }
}
