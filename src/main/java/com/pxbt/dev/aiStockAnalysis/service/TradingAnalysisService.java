package com.pxbt.dev.aiStockAnalysis.service;

import com.pxbt.dev.aiStockAnalysis.model.*;
import com.pxbt.dev.aiStockAnalysis.util.Ta4jConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TradingAnalysisService {

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private StockDataService stockDataService;

    @Autowired
    private PricePredictionService pricePredictionService;

    @Autowired
    private WyckoffAnalysisService wyckoffAnalysisService;

    private final Random random = new Random();

    public AIAnalysisResult analyzeMarketData(String symbol, double currentPrice) {
        log.info("🔄 Starting ENHANCED analysis for {} - Price: ${}", symbol, currentPrice);

        // GET ENHANCED HISTORICAL DATA (e.g. 200 points for 1D)
        List<PriceUpdate> historicalData = marketDataService.getHistoricalData(symbol, 200);
        int dataPoints = historicalData.size();

        double daysCovered = calculateDaysCovered(historicalData);
        log.info("📊 Using {} data points for {} ({} days of data)",
                dataPoints, symbol, String.format("%.1f", daysCovered));

        // MULTI-TIMEFRAME ANALYSIS (Using AI Prediction Service)
        Map<String, PricePrediction> timeframePredictions = pricePredictionService
                .predictMultipleTimeframes(symbol, currentPrice);

        List<ChartPattern> chartPatterns = detectLongTermPatterns(symbol, currentPrice, historicalData);
        List<FibonacciTimeZone> fibonacciTimeZones = calculateWeeklyFibonacci(symbol, historicalData);

        // WYCKOFF ANALYSIS (MULTI-TIMEFRAME)
        Map<String, List<PriceUpdate>> wyckoffData = new HashMap<>();
        wyckoffData.put("1d", historicalData);
        
        // Fetch 1W and 1M for structure analysis from StockDataService
        try {
            List<PriceUpdate> weeklyData = stockDataService.getHistoricalDataAsPriceUpdate(symbol, "1W", 100);
            if (weeklyData != null && !weeklyData.isEmpty()) {
                wyckoffData.put("1W", weeklyData);
            }
            
            List<PriceUpdate> monthlyData = stockDataService.getHistoricalDataAsPriceUpdate(symbol, "1M", 60);
            if (monthlyData != null && !monthlyData.isEmpty()) {
                wyckoffData.put("1M", monthlyData);
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to fetch higher timeframe data for Wyckoff: {}", e.getMessage());
        }

        Map<String, WyckoffResult> wyckoffResults = wyckoffAnalysisService.analyzeMultiTimeframe(symbol, wyckoffData);
        
        // Calculate Overall Confluence
        WyckoffResult daily = wyckoffResults.getOrDefault("1day", new WyckoffResult("UNKNOWN", "N/A", 0.0, 0.0, 0.0, new ArrayList<>()));
        
        // CREATE RESULT
        AIAnalysisResult result = new AIAnalysisResult();
        result.setSymbol(symbol);
        result.setCurrentPrice(currentPrice);
        result.setTimeframePredictions(timeframePredictions);
        result.setChartPatterns(chartPatterns);
        result.setFibonacciTimeZones(fibonacciTimeZones);
        
        // Populate Multi-Timeframe Wyckoff
        result.setWyckoffTimeframes(wyckoffResults);
        result.setWyckoffPhase(daily.getPhase());
        result.setWyckoffDetails(daily.getDetails());
        
        double avgScore = wyckoffResults.values().stream().mapToDouble(WyckoffResult::getScore).average().orElse(0.0);
        if (avgScore > 0.5) {
            result.setWyckoffPhase("CONFLUENCE_BULLISH (" + daily.getPhase() + ")");
        } else if (avgScore < -0.5) {
            result.setWyckoffPhase("CONFLUENCE_BEARISH (" + daily.getPhase() + ")");
        }
        result.setTimestamp(System.currentTimeMillis());

        log.info("✅ AI Analysis - Signal: {}, Phase: {}, Confidence: {}%, Data Coverage: {} days",
                result.getTradingSignal(), result.getWyckoffPhase(), String.format("%.1f", result.getConfidence() * 100), String.format("%.1f", daysCovered));

        // Collect logs for the result
        result.getAnalysisLogs().add(String.format("📊 Data Points: %d (%s days cover)", dataPoints, String.format("%.1f", daysCovered)));
        
        timeframePredictions.forEach((tf, p) -> {
            result.getAnalysisLogs().add(String.format("🔍 %s Predict [%s] - Signal: %s, Conf: %.1f%% => $%s",
                tf.toUpperCase(), p.getModelName(), p.getTrend(), p.getConfidence() * 100, 
                String.format("%.2f", p.getPredictedPrice())));
        });
        
        result.getAnalysisLogs().add(String.format("🧱 Market Structure: %s (%s)", daily.getPhase(), daily.getDetails()));

        return result;
    }

    /**
     * Analyze price data for specific timeframes
     */
    public AIAnalysisResult analyzePriceData(List<StockPrice> prices, String timeframe) {
        if (prices == null || prices.isEmpty()) {
            log.warn("No price data available for timeframe analysis: {}", timeframe);
            return createEmptyAnalysis("SPY", timeframe);
        }

        // Convert StockPrice to PriceUpdate for compatibility
        List<PriceUpdate> priceUpdates = prices.stream()
                .map(sp -> new PriceUpdate(sp.getSymbol(), sp.getPrice(), sp.getVolume(), sp.getTimestamp(),
                        sp.getOpen(), sp.getHigh(), sp.getLow(), sp.getClose()))
                .collect(Collectors.toList());

        return analyzeMarketDataWithTimeframe(prices.get(0).getSymbol(),
                prices.get(prices.size() - 1).getPrice(),
                priceUpdates,
                timeframe);
    }

    private AIAnalysisResult analyzeMarketDataWithTimeframe(String symbol, double currentPrice,
            List<PriceUpdate> historicalData, String timeframe) {

        log.info("🔄 Starting TIMEFRAME analysis for {} - Timeframe: {}, Price: ${}",
                symbol, timeframe, currentPrice);

        // Re-use core analysis but focus on timeframe
        AIAnalysisResult result = analyzeMarketData(symbol, currentPrice);
        result.setTimeframe(timeframe);
        
        return result;
    }

    private AIAnalysisResult createEmptyAnalysis(String symbol, String timeframe) {
        AIAnalysisResult result = new AIAnalysisResult();
        result.setSymbol(symbol);
        result.setCurrentPrice(0.0);
        result.setTimestamp(System.currentTimeMillis());
        result.setWyckoffPhase("UNKNOWN");
        return result;
    }

    private double calculateDaysCovered(List<PriceUpdate> data) {
        if (data.size() < 2) return 0.0;
        long startTime = data.get(0).getTimestamp();
        long endTime = data.get(data.size() - 1).getTimestamp();
        return (endTime - startTime) / (1000.0 * 60 * 60 * 24);
    }

    private List<ChartPattern> detectLongTermPatterns(String symbol, double currentPrice,
            List<PriceUpdate> historicalData) {
        List<ChartPattern> patterns = new ArrayList<>();
        if (historicalData.size() < 20) return patterns;

        // Simple pattern detection similar to Crypto
        double trend = calculatePriceTrend(historicalData);
        if (trend > 0.05) {
            patterns.add(new ChartPattern(symbol, "UPTREND", currentPrice, 0.8, "Strong bullish momentum detected", System.currentTimeMillis()));
        } else if (trend < -0.05) {
            patterns.add(new ChartPattern(symbol, "DOWNTREND", currentPrice, 0.8, "Strong bearish pressure detected", System.currentTimeMillis()));
        }

        return patterns;
    }

    private List<FibonacciTimeZone> calculateWeeklyFibonacci(String symbol, List<PriceUpdate> historicalData) {
        List<FibonacciTimeZone> zones = new ArrayList<>();
        if (historicalData.size() < 20) return zones;

        double high = historicalData.stream().mapToDouble(PriceUpdate::getPrice).max().orElse(0.0);
        double low = historicalData.stream().mapToDouble(PriceUpdate::getPrice).min().orElse(0.0);
        double range = high - low;

        double[] fibLevels = { 0.382, 0.5, 0.618 };
        for (double level : fibLevels) {
            double price = high - (range * level);
            zones.add(new FibonacciTimeZone(symbol, "FIB_" + (int)(level * 1000), System.currentTimeMillis(), 0L, price, price, 0.7, "Fibonacci Level", "NEUTRAL"));
        }
        return zones;
    }

    private double calculatePriceTrend(List<PriceUpdate> data) {
        if (data.size() < 10) return 0.0;
        double first = data.get(0).getPrice();
        double last = data.get(data.size() - 1).getPrice();
        return (last - first) / first;
    }
}
