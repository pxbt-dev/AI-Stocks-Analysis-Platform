package com.pxbt.dev.aiStockAnalysis.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class SystemMonitorService {

    public Map<String, Object> getMemoryStats() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long allocatedMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = allocatedMemory - freeMemory;

        Map<String, Object> stats = new HashMap<>();
        stats.put("used", usedMemory / (1024 * 1024)); // MB
        stats.put("total", allocatedMemory / (1024 * 1024)); // MB
        stats.put("max", maxMemory / (1024 * 1024)); // MB
        stats.put("percentUsed", (double) usedMemory / maxMemory * 100);
        stats.put("timestamp", System.currentTimeMillis());

        return stats;
    }
}
