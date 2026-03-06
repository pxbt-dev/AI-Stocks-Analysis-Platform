package com.pxbt.dev.aiStockAnalysis.controller;

import com.pxbt.dev.aiStockAnalysis.dto.ChartDataResponseDto;
import com.pxbt.dev.aiStockAnalysis.model.StockPrice;
import com.pxbt.dev.aiStockAnalysis.model.AIAnalysisResult;
import com.pxbt.dev.aiStockAnalysis.service.RealTimeDataService;
import com.pxbt.dev.aiStockAnalysis.service.StockDataService;
import com.pxbt.dev.aiStockAnalysis.service.TradingAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/chart")
public class ApiDataController {

    @Autowired
    private StockDataService stockDataService;

    @Autowired
    private TradingAnalysisService tradingAnalysisService;

    @Autowired
    private RealTimeDataService realTimeDataService;

    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> getLatestData() {
        return ResponseEntity.ok(realTimeDataService.getAllLatestData());
    }

    @GetMapping("/data")
    public ResponseEntity<ChartDataResponseDto> getChartData(
            @RequestParam String symbol,
            @RequestParam String timeframe) {

        log.info("📈 Chart data requested - Symbol: {}, Timeframe: {}", symbol, timeframe);

        try {
            List<StockPrice> historicalData = stockDataService.getHistoricalData(symbol, timeframe, 100);

            AIAnalysisResult analysis = tradingAnalysisService.analyzePriceData(historicalData, timeframe);

            ChartDataResponseDto response = new ChartDataResponseDto(historicalData, analysis, timeframe);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Failed to get chart data for {} {}: {}", symbol, timeframe, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
