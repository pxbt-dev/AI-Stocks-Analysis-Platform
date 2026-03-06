package com.pxbt.dev.aiStockAnalysis.controller;

import com.pxbt.dev.aiStockAnalysis.dto.OHLCData;
import com.pxbt.dev.aiStockAnalysis.model.StockPrice;
import com.pxbt.dev.aiStockAnalysis.service.StockDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/historical")
public class HistoricalDataController {

    private final StockDataService stockDataService;

    public HistoricalDataController(StockDataService stockDataService) {
        this.stockDataService = stockDataService;
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<List<OHLCData>> getHistoricalData(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1d") String timeframe,
            @RequestParam(defaultValue = "100") int limit) {

        log.info("📈 Historical data requested - Symbol: {}, Timeframe: {}, Limit: {}", symbol, timeframe, limit);

        try {
            // Fetch historical data from StockDataService
            List<StockPrice> StockPrices = stockDataService.getHistoricalData(symbol, timeframe, limit);

            if (StockPrices == null || StockPrices.isEmpty()) {
                log.warn("❌ No historical data found for {} {}", symbol, timeframe);
                return ResponseEntity.notFound().build();
            }

            // ✅ Convert StockPrice to OHLCData for frontend
            List<OHLCData> ohlcData = StockPrices.stream()
                    .map(price -> new OHLCData(
                            price.getTimestamp(),
                            price.getOpen(),
                            price.getHigh(),
                            price.getLow(),
                            price.getClose(),
                            price.getVolume()))
                    .collect(Collectors.toList());

            log.info("✅ Returning {} OHLC data points for {}", ohlcData.size(), symbol);
            return ResponseEntity.ok(ohlcData);

        } catch (Exception e) {
            log.error("❌ Failed to get historical data for {} {}: {}", symbol, timeframe, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
