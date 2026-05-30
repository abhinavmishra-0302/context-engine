package com.example.ragassistant.service.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MonitoringService {

    private final MeterRegistry meterRegistry;

    public Map<String, Double> customMetricSnapshot() {
        Map<String, Double> snapshot = new LinkedHashMap<>();
        snapshot.put("rag_queries_total", counterValue("rag_queries_total"));
        snapshot.put("cache_hits_total", counterValue("cache_hits_total"));
        snapshot.put("cache_misses_total", counterValue("cache_misses_total"));
        snapshot.put("documents_uploaded_total", counterValue("documents_uploaded_total"));
        snapshot.put("documents_processed_total", counterValue("documents_processed_total"));
        return snapshot;
    }

    private double counterValue(String name) {
        io.micrometer.core.instrument.Counter counter = meterRegistry.find(name).counter();
        return counter == null ? 0.0 : counter.count();
    }
}
