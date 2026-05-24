package com.example.ragassistant.util;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TextChunker {

    public List<String> chunk(String text, int chunkSizeWords, int overlapWords) {
        String normalized = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            return List.of();
        }

        String[] words = normalized.split(" ");
        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < words.length) {
            int end = Math.min(start + chunkSizeWords, words.length);
            StringBuilder chunkBuilder = new StringBuilder();
            for (int i = start; i < end; i++) {
                if (i > start) {
                    chunkBuilder.append(' ');
                }
                chunkBuilder.append(words[i]);
            }
            chunks.add(chunkBuilder.toString());
            if (end == words.length) {
                break;
            }
            start = Math.max(start + 1, end - overlapWords);
        }
        return chunks;
    }
}
