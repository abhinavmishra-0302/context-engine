package com.example.ragassistant.kafka;

import com.example.ragassistant.kafka.event.DocumentUploadedEvent;
import com.example.ragassistant.service.DocumentProcessingService;
import com.example.ragassistant.service.monitoring.CustomMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class DocumentProcessingConsumer {

    private final DocumentProcessingService documentProcessingService;
    private final CustomMetricsService customMetricsService;

    @KafkaListener(topics = "${app.kafka.topics.document-uploaded}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleDocumentUploaded(DocumentUploadedEvent event) {
        long start = System.nanoTime();
        try {
            documentProcessingService.processUploadedDocument(event);
            customMetricsService.incrementDocumentsProcessed();
        } finally {
            customMetricsService.recordDocumentProcessingDurationMs((System.nanoTime() - start) / 1_000_000.0);
        }
    }
}
