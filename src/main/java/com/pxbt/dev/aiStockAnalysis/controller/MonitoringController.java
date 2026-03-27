package com.pxbt.dev.aiStockAnalysis.controller;

import com.pxbt.dev.aiStockAnalysis.service.SystemMonitorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/monitor")
public class MonitoringController {

    private final SystemMonitorService monitorService;

    public MonitoringController(SystemMonitorService monitorService) {
        this.monitorService = monitorService;
    }

    @GetMapping("/memory")
    public ResponseEntity<Map<String, Object>> getMemoryStats() {
        return ResponseEntity.ok(monitorService.getMemoryStats());
    }
}
