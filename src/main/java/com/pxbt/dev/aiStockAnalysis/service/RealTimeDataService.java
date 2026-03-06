package com.pxbt.dev.aiStockAnalysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pxbt.dev.aiStockAnalysis.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
@Service
@EnableScheduling
public class RealTimeDataService {

    private final Map<String, Deque<PriceUpdate>> priceCache = new ConcurrentHashMap<>();
    private final Map<String, Object> latestAggregatedData = new ConcurrentHashMap<>();

    @Autowired
    private StockDataService stockDataService;

    @Autowired
    private PricePredictionService predictionService;

    @Autowired
    private ChartPatternService chartPatternService;

    @Autowired
    private FibonacciTimeZoneService fibonacciTimeZoneService;

    @Autowired
    private MarketDataService marketDataService;

    private ObjectMapper objectMapper = new ObjectMapper();

    private final List<String> symbols = Arrays.asList("SPY", "AAPL", "MSFT", "GOOG");

    @PostConstruct
    public void init() {
        log.info("🚀 INITIALIZING RealTimeDataService - Stock Market Polling Mode");

        // Run initial fetch in background
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(5000);
                log.info("📊 Fetching initial stock prices...");
                refreshStockPrices();
            } catch (Exception e) {
                log.error("❌ Initial price fetch failed: {}", e.getMessage());
            }
        });
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 60000)
    public void refreshStockPrices() {
        log.debug("🔄 Polling latest stock prices...");
        for (int i = 0; i < symbols.size(); i++) {
            String symbol = symbols.get(i);
            try {
                if (i > 0) {
                    Thread.sleep(2000); // Avoid rate limiting
                }
                PriceUpdate update = stockDataService.getCurrentPrice(symbol);
                if (update != null) {
                    processUpdate(update);
                }
            } catch (Exception e) {
                log.error("❌ Failed to refresh stock price for {}: {}", symbol, e.getMessage());
            }
        }
    }

    private void processUpdate(PriceUpdate priceUpdate) {
        try {
            String symbol = priceUpdate.getSymbol();
            updatePriceCache(symbol, priceUpdate);

            AIAnalysisResult analysis = analyzeWithAI(priceUpdate);

            Map<String, Object> data = new HashMap<>();
            data.put("price", priceUpdate.getPrice());
            data.put("volume", priceUpdate.getVolume());
            data.put("timestamp", priceUpdate.getTimestamp());
            data.put("analysis", analysis);

            latestAggregatedData.put(symbol, data);

            log.debug("✅ Updated analysis for {}", symbol);
        } catch (Exception e) {
            log.error("❌ Error processing {} update: {}", priceUpdate.getSymbol(), e.getMessage());
        }
    }

    private void updatePriceCache(String symbol, PriceUpdate priceUpdate) {
        priceCache.computeIfAbsent(symbol, k -> new ConcurrentLinkedDeque<>()).add(priceUpdate);
        Deque<PriceUpdate> symbolCache = priceCache.get(symbol);
        while (symbolCache.size() > 100) {
            symbolCache.removeFirst();
        }
    }

    public Map<String, Object> getAllLatestData() {
        return latestAggregatedData;
    }

    public AIAnalysisResult analyzeWithAI(PriceUpdate update) {
        try {
            double currentPrice = update.getPrice();
            List<PriceUpdate> cachedHistory = marketDataService.getHistoricalData(update.getSymbol(), 90);
            List<StockPrice> historicalData = cachedHistory.stream()
                    .map(pu -> new StockPrice(
                            pu.getSymbol(), pu.getPrice(), pu.getVolume(), pu.getTimestamp(),
                            pu.getOpen(), pu.getHigh(), pu.getLow(), pu.getClose()))
                    .collect(java.util.stream.Collectors.toList());

            List<ChartPattern> patterns = chartPatternService.detectPatterns(update.getSymbol(), historicalData);
            patterns = ensureValidChartPatterns(patterns, update.getSymbol());

            List<FibonacciTimeZone> fibZones = fibonacciTimeZoneService.calculateTimeZones(update.getSymbol(),
                    historicalData);
            Map<String, PricePrediction> timeframePredictions = predictionService
                    .predictMultipleTimeframes(update.getSymbol(), currentPrice);

            return new AIAnalysisResult(
                    update.getSymbol(),
                    currentPrice,
                    timeframePredictions,
                    patterns,
                    fibZones,
                    System.currentTimeMillis());
        } catch (Exception e) {
            log.error("❌ AI ANALYSIS ERROR for {}: {}", update.getSymbol(), e.getMessage());
            return new AIAnalysisResult(update.getSymbol(), update.getPrice(), new HashMap<>(), new ArrayList<>(),
                    new ArrayList<>(), System.currentTimeMillis());
        }
    }

    private List<ChartPattern> ensureValidChartPatterns(List<ChartPattern> patterns, String symbol) {
        if (patterns == null)
            return new ArrayList<>();
        return patterns.stream()
                .map(pattern -> {
                    if (pattern.getPatternType() == null) {
                        return new ChartPattern("NEUTRAL", pattern.getPriceLevel(), pattern.getConfidence(),
                                pattern.getDescription() != null ? pattern.getDescription() : "No pattern detected",
                                pattern.getTimestamp());
                    }
                    return pattern;
                })
                .toList();
    }
}
