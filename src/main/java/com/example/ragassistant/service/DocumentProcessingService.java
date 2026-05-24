package com.example.ragassistant.service;

import com.example.ragassistant.model.Document;
import com.example.ragassistant.model.DocumentChunk;
import com.example.ragassistant.model.DocumentStatus;
import com.example.ragassistant.repository.DocumentChunkRepository;
import com.example.ragassistant.repository.DocumentRepository;
import com.example.ragassistant.service.vector.VectorRecord;
import com.example.ragassistant.service.vector.VectorStore;
import com.example.ragassistant.util.TextChunker;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessingService {

    private static final int CHUNK_SIZE_WORDS = 400;
    private static final int CHUNK_OVERLAP_WORDS = 80;

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final TextExtractionService textExtractionService;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    @Async
    @Transactional
    public void processDocumentAsync(UUID documentId, Path filePath) {
        Optional<Document> optional = documentRepository.findById(documentId);
        if (optional.isEmpty()) {
            return;
        }
        Document document = optional.get();
        try {
            String rawText = textExtractionService.extract(filePath, document.getFileName());
            List<String> chunks = textChunker.chunk(rawText, CHUNK_SIZE_WORDS, CHUNK_OVERLAP_WORDS);

            List<DocumentChunk> chunkEntities = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                chunkEntities.add(DocumentChunk.builder()
                        .id(UUID.randomUUID())
                        .document(document)
                        .chunkIndex(i)
                        .text(chunks.get(i))
                        .build());
            }
            documentChunkRepository.saveAll(chunkEntities);

            List<List<Float>> embeddings = embeddingService.embedBatch(chunks);
            List<VectorRecord> vectorRecords = new ArrayList<>();
            for (int i = 0; i < chunkEntities.size(); i++) {
                DocumentChunk chunk = chunkEntities.get(i);
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("document_id", documentId.toString());
                metadata.put("chunk_index", chunk.getChunkIndex());
                vectorRecords.add(new VectorRecord(
                        chunk.getId().toString(),
                        embeddings.get(i),
                        metadata
                ));
            }
            vectorStore.upsert(vectorRecords);
            document.setStatus(DocumentStatus.READY);
            documentRepository.save(document);
            log.info("Document {} processed with {} chunks", documentId, chunks.size());
        } catch (Exception ex) {
            log.error("Document processing failed for {}", documentId, ex);
            document.setStatus(DocumentStatus.FAILED);
            documentRepository.save(document);
        }
    }
}
