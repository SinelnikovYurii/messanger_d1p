package com.messenger.core.controller;

import com.messenger.core.service.PerformanceMonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceMonitoringService performanceMonitoringService;

    /**
     * Получить статистику Hibernate
     */
    @GetMapping("/hibernate-stats")
    public ResponseEntity<String> getHibernateStatistics() {
        performanceMonitoringService.logHibernateStatistics();
        return ResponseEntity.ok("Statistics logged to console");
    }

    /**
     * Сбросить статистику
     */
    @PostMapping("/reset-stats")
    public ResponseEntity<String> resetStatistics() {
        performanceMonitoringService.resetStatistics();
        return ResponseEntity.ok("Statistics reset");
    }

    /**
     * Включить статистику
     */
    @PostMapping("/enable-stats")
    public ResponseEntity<String> enableStatistics() {
        performanceMonitoringService.enableStatistics();
        return ResponseEntity.ok("Statistics enabled");
    }
}
