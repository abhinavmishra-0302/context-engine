package com.example.ragassistant.service.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

@Service
public class CustomMetricsService {

    private final Counter ragQueriesTotal;
    private final Counter cacheHitsTotal;
    private final Counter cacheMissesTotal;
    private final Counter documentsUploadedTotal;
    private final Counter documentsProcessedTotal;
    private final Timer retrievalDurationMs;
    private final Timer llmDurationMs;
    private final Timer documentProcessingDurationMs;

    public CustomMetricsService(MeterRegistry meterRegistry) {
        this.ragQueriesTotal = meterRegistry.counter("rag_queries_total");
        this.cacheHitsTotal = meterRegistry.counter("cache_hits_total");
        this.cacheMissesTotal = meterRegistry.counter("cache_misses_total");
        this.documentsUploadedTotal = meterRegistry.counter("documents_uploaded_total");
        this.documentsProcessedTotal = meterRegistry.counter("documents_processed_total");
        this.retrievalDurationMs = Timer.builder("retrieval_duration_ms")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.llmDurationMs = Timer.builder("llm_duration_ms")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.documentProcessingDurationMs = Timer.builder("document_processing_duration_ms")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    public void incrementQueryCount() {
        ragQueriesTotal.increment();
    }

    public void incrementCacheHit() {
        cacheHitsTotal.increment();
    }

    public void incrementCacheMiss() {
        cacheMissesTotal.increment();
    }

    public void recordRetrievalDurationMs(double millis) {
        retrievalDurationMs.record(java.time.Duration.ofNanos((long) (millis * 1_000_000)));
    }

    public void recordLlmDurationMs(double millis) {
        llmDurationMs.record(java.time.Duration.ofNanos((long) (millis * 1_000_000)));
    }

    public void recordDocumentProcessingDurationMs(double millis) {
        documentProcessingDurationMs.record(java.time.Duration.ofNanos((long) (millis * 1_000_000)));
    }

    public void incrementDocumentsUploaded() {
        documentsUploadedTotal.increment();
    }

    public void incrementDocumentsProcessed() {
        documentsProcessedTotal.increment();
    }
}
