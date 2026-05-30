package com.example.ragassistant.service;

import com.example.ragassistant.kafka.DocumentUploadProducer;
import com.example.ragassistant.kafka.event.DocumentUploadedEvent;
import com.example.ragassistant.model.Document;
import com.example.ragassistant.model.DocumentChunk;
import com.example.ragassistant.model.DocumentProcessingJob;
import com.example.ragassistant.model.DocumentStatus;
import com.example.ragassistant.model.ProcessingJobStatus;
import com.example.ragassistant.repository.DocumentChunkRepository;
import com.example.ragassistant.repository.DocumentRepository;
import com.example.ragassistant.repository.ProcessingJobRepository;
import com.example.ragassistant.service.cache.CacheService;
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
    private final ProcessingJobRepository processingJobRepository;
    private final TextExtractionService textExtractionService;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final Optional<DocumentUploadProducer> documentUploadProducer;
    private final CacheService cacheService;

    @Transactional
    public void processUploadedDocument(DocumentUploadedEvent event) {
        Optional<Document> optional = documentRepository.findById(event.documentId());
        if (optional.isEmpty()) {
            return;
        }
        Document document = optional.get();
        Path filePath = Path.of(event.fileLocation());
        DocumentProcessingJob job = processingJobRepository.findTopByDocumentOrderByCreatedAtDesc(document)
                .orElseGet(() -> DocumentProcessingJob.builder()
                        .id(UUID.randomUUID())
                        .document(document)
                        .createdAt(java.time.Instant.now())
                        .build());
        job.setStatus(ProcessingJobStatus.PROCESSING);
        job.setErrorMessage(null);
        job.setCompletedAt(null);
        processingJobRepository.save(job);
        document.setStatus(DocumentStatus.PROCESSING);
        documentRepository.save(document);
        cacheService.cacheDocumentMetadata(document);

        try {
            List<DocumentChunk> chunkEntities = new ArrayList<>();
            List<String> chunkPayloads = new ArrayList<>();
            List<TextExtractionService.ExtractedPage> pages = textExtractionService.extractPages(filePath, document.getFileName());
            int chunkIndex = 0;
            for (TextExtractionService.ExtractedPage page : pages) {
                List<String> pageChunks = textChunker.chunk(page.text(), CHUNK_SIZE_WORDS, CHUNK_OVERLAP_WORDS);
                for (String pageChunk : pageChunks) {
                    UUID chunkId = UUID.randomUUID();
                    chunkEntities.add(DocumentChunk.builder()
                            .id(chunkId)
                            .chunkUuid(chunkId)
                            .document(document)
                            .chunkIndex(chunkIndex++)
                            .pageNumber(page.pageNumber())
                            .text(pageChunk)
                            .build());
                    chunkPayloads.add(pageChunk);
                }
            }
            if (chunkEntities.isEmpty()) {
                markFailed(document, job, "No chunks could be generated from document content");
                return;
            }
            documentChunkRepository.saveAll(chunkEntities);

            List<List<Float>> embeddings = embeddingService.embedBatch(chunkPayloads);
            List<VectorRecord> vectorRecords = new ArrayList<>();
            for (int i = 0; i < chunkEntities.size(); i++) {
                DocumentChunk chunk = chunkEntities.get(i);
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("document_id", document.getId().toString());
                metadata.put("document_name", document.getFileName());
                metadata.put("chunk_id", chunk.getChunkUuid().toString());
                metadata.put("chunk_index", chunk.getChunkIndex());
                metadata.put("page_number", chunk.getPageNumber());
                vectorRecords.add(new VectorRecord(
                        chunk.getId().toString(),
                        embeddings.get(i),
                        metadata
                ));
            }
            vectorStore.upsert(vectorRecords);
            document.setStatus(DocumentStatus.READY);
            documentRepository.save(document);
            cacheService.cacheDocumentMetadata(document);
            job.setStatus(ProcessingJobStatus.COMPLETED);
            job.setCompletedAt(java.time.Instant.now());
            processingJobRepository.save(job);
            documentUploadProducer.ifPresent(producer ->
                    producer.publishCompleted(document.getId(), document.getUser().getId(), chunkEntities.size()));
            log.info("Document {} processed with {} chunks", document.getId(), chunkEntities.size());
        } catch (Exception ex) {
            log.error("Document processing failed for {}", document.getId(), ex);
            markFailed(document, job, ex.getMessage());
        }
    }

    private void markFailed(Document document, DocumentProcessingJob job, String errorMessage) {
        document.setStatus(DocumentStatus.FAILED);
        documentRepository.save(document);
        cacheService.cacheDocumentMetadata(document);
        job.setStatus(ProcessingJobStatus.FAILED);
        job.setErrorMessage(errorMessage == null ? "Unknown processing error" : errorMessage);
        job.setCompletedAt(java.time.Instant.now());
        processingJobRepository.save(job);
        documentUploadProducer.ifPresent(producer ->
                producer.publishFailed(document.getId(), document.getUser().getId(), job.getErrorMessage()));
    }
}
