package com.example.ragassistant.controller;

import com.example.ragassistant.service.monitoring.MonitoringService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MonitoringService monitoringService;

    @GetMapping("/custom")
    public Map<String, Double> customMetrics() {
        return monitoringService.customMetricSnapshot();
    }
}
