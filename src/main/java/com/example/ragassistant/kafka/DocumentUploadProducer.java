package com.example.ragassistant.kafka;

import com.example.ragassistant.kafka.event.DocumentProcessingCompletedEvent;
import com.example.ragassistant.kafka.event.DocumentProcessingFailedEvent;
import com.example.ragassistant.kafka.event.DocumentUploadedEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class DocumentUploadProducer {

    @Value("${app.kafka.topics.document-uploaded}")
    private String uploadedTopic;

    @Value("${app.kafka.topics.document-processing-completed}")
    private String completedTopic;

    @Value("${app.kafka.topics.document-processing-failed}")
    private String failedTopic;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishUploaded(DocumentUploadedEvent event) {
        kafkaTemplate.send(uploadedTopic, event.documentId().toString(), event);
    }

    public void publishCompleted(UUID documentId, UUID userId, int chunks) {
        kafkaTemplate.send(completedTopic, documentId.toString(), new DocumentProcessingCompletedEvent(documentId, userId, chunks));
    }

    public void publishFailed(UUID documentId, UUID userId, String error) {
        kafkaTemplate.send(failedTopic, documentId.toString(), new DocumentProcessingFailedEvent(documentId, userId, error));
    }
}
