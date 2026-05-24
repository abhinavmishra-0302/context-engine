package com.example.ragassistant.service;

import com.example.ragassistant.dto.CitationResponse;
import com.example.ragassistant.model.DocumentChunk;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CitationService {

    @Value("${app.rag.citation-snippet-chars:220}")
    private int citationSnippetChars;

    public List<CitationResponse> buildCitations(List<DocumentChunk> chunks) {
        List<CitationResponse> citations = new ArrayList<>();
        Set<java.util.UUID> seen = new LinkedHashSet<>();
        for (DocumentChunk chunk : chunks) {
            if (!seen.add(chunk.getChunkUuid())) {
                continue;
            }
            String snippet = truncate(chunk.getText(), citationSnippetChars);
            citations.add(new CitationResponse(
                    chunk.getDocument().getId(),
                    chunk.getDocument().getFileName(),
                    chunk.getChunkUuid(),
                    chunk.getChunkIndex(),
                    chunk.getPageNumber(),
                    snippet
            ));
        }
        return citations;
    }

    private String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + "...";
    }
}
