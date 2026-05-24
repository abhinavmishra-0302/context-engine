package com.example.ragassistant.service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private static final int BATCH_SIZE = 32;
    private final GeminiService geminiService;

    public List<Float> embedText(String text) {
        return geminiService.createEmbeddings(List.of(text)).get(0);
    }

    public List<List<Float>> embedBatch(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        List<List<Float>> result = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, texts.size());
            result.addAll(geminiService.createEmbeddings(texts.subList(i, end)));
        }
        return result;
    }
}
