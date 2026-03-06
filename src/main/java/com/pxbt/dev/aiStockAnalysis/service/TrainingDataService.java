package com.pxbt.dev.aiStockAnalysis.service;

import com.pxbt.dev.aiStockAnalysis.model.StockPrice;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class TrainingDataService {

    @Autowired
    private AIModelService aiModelService;

    @Autowired
    private StockDataService historicalDataService;

    @Value("${app.training.enabled:true}") // Add this line
    private boolean trainingEnabled;

    @PostConstruct
    public void init() {
        if (!trainingEnabled) { // Add this check
            log.info("🚫 ML training disabled by configuration");
            return;
        }

        // Train on startup (async to not block)
        CompletableFuture.runAsync(() -> {
            try {
                log.info("⏳ AI Training standby: waiting 60s for system stabilization...");
                Thread.sleep(60000); // Wait 60 seconds for data to load
                collectTrainingData();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    // Scheduled training
    @Scheduled(cron = "0 0 3 * * *") // 3 AM daily
    public void scheduledTraining() {
        log.info("🔄 Daily ML retraining starting...");
        collectTrainingData();
    }

    /**
     * Comprehensive training data collection for all symbols and timeframes
     */
    public void collectTrainingData() {
        log.info("📚 Starting comprehensive AI training data collection...");

        String[] symbols = { "SPY", "AAPL", "MSFT", "GOOG" };
        String[] timeframes = { "1d", "1W", "1M" }; // Restricted to what YahooFinance provides easily

        int totalTrained = 0;

        for (String symbol : symbols) {
            for (String timeframe : timeframes) {
                try {
                    boolean trained = collectSymbolTrainingData(symbol, timeframe);
                    if (trained) {
                        totalTrained++;
                        log.info("✅ Successfully trained {} - {}", symbol, timeframe);
                    } else {
                        log.warn("⚠️ Skipped training for {} - {} (insufficient data)", symbol, timeframe);
                    }
                } catch (Exception e) {
                    log.error("❌ Training failed for {} {}: {}", symbol, timeframe, e.getMessage());
                }
            }
        }

        log.info("🎯 Training completed: {} models trained successfully", totalTrained);
    }

    /**
     * Collect training data for specific symbol and timeframe
     * 
     * @return true if training was successful, false if insufficient data
     */
    public boolean collectSymbolTrainingData(String symbol, String timeframe) {
        // Use StockDataService for fetching historical data
        List<StockPrice> fullData = historicalDataService.getHistoricalData(symbol, timeframe,
                getRequiredPointsForTimeframe(timeframe));

        if (fullData == null || fullData.size() < getMinDataPoints(timeframe)) {
            log.debug("❌ Insufficient data for {} {}: only {} points (need {}+)",
                    symbol, timeframe,
                    fullData != null ? fullData.size() : 0,
                    getMinDataPoints(timeframe));
            return false;
        }

        List<double[]> featuresList = new ArrayList<>();
        List<Double> targetChanges = new ArrayList<>();

        log.info("🤖 Processing {} data points for {} - {} ML training",
                fullData.size(), symbol, timeframe);

        // Different window sizes based on timeframe
        int windowSize = getWindowSize(timeframe);
        int futureOffset = getFutureOffset(timeframe);

        // Slide window through historical data
        int trainingSamples = 0;
        for (int i = windowSize; i < fullData.size() - futureOffset; i++) {
            List<StockPrice> windowData = fullData.subList(i - windowSize, i);

            // Extract features and calculate actual future change
            double[] features = extractFeaturesForTraining(windowData, timeframe);
            double actualChange = calculateActualChange(fullData, i, timeframe);

            // Only include meaningful samples (filter out noise)
            if (Math.abs(actualChange) < getMaxChangeFilter(timeframe)) {
                featuresList.add(features);
                targetChanges.add(actualChange);
                trainingSamples++;
            }
        }

        // Train the model with collected data
        int minSamples = getMinTrainingSamples(timeframe);
        if (trainingSamples >= minSamples) {
            aiModelService.trainModel(timeframe, featuresList, targetChanges);
            log.info("✅ Trained {} model with {} quality samples", timeframe, trainingSamples);
            return true;
        } else {
            log.warn("⚠️ Insufficient quality samples for {} {}: {} (need {}+)",
                    symbol, timeframe, trainingSamples, minSamples);
            return false;
        }
    }

    /**
     * Helper methods for different timeframes
     */
    private int getRequiredPointsForTimeframe(String timeframe) {
        return switch (timeframe) {
            case "1d" -> 1460; // 4 years daily
            case "1W" -> 208; // 4 years weekly
            case "1M" -> 48; // 4 years monthly
            default -> 100;
        };
    }

    private int getMinDataPoints(String timeframe) {
        return switch (timeframe) {
            case "1h", "4h" -> 500; // Need more for short-term
            case "1d" -> 400; // Daily
            case "1W" -> 200; // Weekly (need ~4 years)
            case "1M" -> 100; // Monthly (need ~8 years)
            default -> 100;
        };
    }

    private int getWindowSize(String timeframe) {
        return switch (timeframe) {
            case "1h" -> 50; // 50 hours for hourly
            case "4h" -> 40; // 40 * 4h = 160h window
            case "1d" -> 50; // 50 days
            case "1W" -> 40; // 40 weeks (~9 months)
            case "1M" -> 30; // 30 months (~2.5 years)
            default -> 50;
        };
    }

    private int getFutureOffset(String timeframe) {
        return switch (timeframe) {
            case "1h" -> 24; // Predict 24 hours ahead
            case "4h" -> 12; // Predict 48 hours ahead (12 * 4h)
            case "1d" -> 7; // Predict 7 days ahead
            case "1W" -> 4; // Predict 4 weeks ahead (~1 month)
            case "1M" -> 3; // Predict 3 months ahead
            default -> 1;
        };
    }

    private double getMaxChangeFilter(String timeframe) {
        return switch (timeframe) {
            case "1h", "4h" -> 0.5; // Filter >50% hourly changes
            case "1d" -> 0.3; // Filter >30% daily changes
            case "1W" -> 0.5; // Filter >50% weekly changes
            case "1M" -> 0.8; // Filter >80% monthly changes
            default -> 0.5;
        };
    }

    private int getMinTrainingSamples(String timeframe) {
        return switch (timeframe) {
            case "1h", "4h" -> 100; // Need lots of short-term samples
            case "1d" -> 80; // Daily
            case "1W" -> 50; // Weekly
            case "1M" -> 30; // Monthly (harder to get)
            default -> 50;
        };
    }

    /**
     * Enhanced feature extraction with 15 technical indicators
     */
    private double[] extractFeaturesForTraining(List<StockPrice> windowData, String timeframe) {
        int featureSize = 11;

        double[] features = new double[featureSize];
        double[] prices = windowData.stream().mapToDouble(StockPrice::getPrice).toArray();

        // Comprehensive feature set for AI training
        features[0] = calculateSMA(prices, 5); // 5-period Simple Moving Average
        features[1] = calculateSMA(prices, 20); // 20-period SMA
        features[2] = calculateEMA(prices, 12); // 12-period Exponential Moving Average
        features[3] = calculateVolatility(prices, 20); // 20-period volatility
        features[4] = calculateMomentum(prices, 10); // 10-period price momentum
        features[5] = calculatePriceRateOfChange(prices, 10); // Rate of Change
        features[6] = calculateZScore(prices); // Statistical Z-score
        features[7] = calculateTrendStrength(prices); // Trend strength (SMA20 vs SMA50)
        features[8] = calculateSupportResistance(prices); // Support/resistance position
        features[9] = calculateBollingerPosition(prices); // Bollinger Bands position
        features[10] = calculatePriceAcceleration(prices); // Price acceleration

        return features;
    }

    /**
     * Calculate actual price change for training targets
     */
    private double calculateActualChange(List<StockPrice> data, int currentIndex, String timeframe) {
        int futureIndex = currentIndex + getFutureOffset(timeframe);
        if (futureIndex >= data.size())
            return 0.0;

        double currentPrice = data.get(currentIndex).getPrice();
        double futurePrice = data.get(futureIndex).getPrice();

        return (futurePrice - currentPrice) / currentPrice;
    }

    // ===== TECHNICAL INDICATOR CALCULATIONS =====

    private double calculateSMA(double[] prices, int period) {
        if (prices.length < period)
            return prices[prices.length - 1];
        double sum = 0;
        for (int i = prices.length - period; i < prices.length; i++) {
            sum += prices[i];
        }
        return sum / period;
    }

    private double calculateEMA(double[] prices, int period) {
        double multiplier = 2.0 / (period + 1);
        double ema = prices[0];
        for (int i = 1; i < prices.length; i++) {
            ema = (prices[i] * multiplier) + (ema * (1 - multiplier));
        }
        return ema;
    }

    private double calculateVolatility(double[] prices, int period) {
        if (prices.length < period)
            return 0.0;

        double mean = calculateSMA(prices, period);
        double sum = 0.0;
        int start = Math.max(0, prices.length - period);
        int count = prices.length - start;

        for (int i = start; i < prices.length; i++) {
            sum += Math.pow(prices[i] - mean, 2);
        }

        return Math.sqrt(sum / count);
    }

    private double calculateMomentum(double[] prices, int period) {
        if (prices.length < period)
            return 0.0;
        return prices[prices.length - 1] - prices[prices.length - period];
    }

    private double calculatePriceRateOfChange(double[] prices, int period) {
        if (prices.length < period)
            return 0.0;
        return ((prices[prices.length - 1] - prices[prices.length - period]) / prices[prices.length - period]) * 100;
    }

    private double calculateZScore(double[] prices) {
        if (prices.length < 2)
            return 0.0;
        double mean = calculateSMA(prices, prices.length);
        double stdDev = calculateVolatility(prices, prices.length);
        return stdDev == 0 ? 0.0 : (prices[prices.length - 1] - mean) / stdDev;
    }

    private double calculateTrendStrength(double[] prices) {
        if (prices.length < 20)
            return 0.0;
        double sma20 = calculateSMA(prices, Math.min(20, prices.length));
        double sma50 = calculateSMA(prices, Math.min(50, prices.length));
        return (sma20 - sma50) / sma50;
    }

    private double calculateSupportResistance(double[] prices) {
        if (prices.length < 10)
            return 0.0;
        double current = prices[prices.length - 1];
        double avg = calculateSMA(prices, prices.length);
        return (current - avg) / avg;
    }

    private double calculateBollingerPosition(double[] prices) {
        if (prices.length < 20)
            return 0.5;
        double sma20 = calculateSMA(prices, 20);
        double stdDev = calculateVolatility(prices, 20);
        double upperBand = sma20 + (2 * stdDev);
        double lowerBand = sma20 - (2 * stdDev);
        double currentPrice = prices[prices.length - 1];

        return (currentPrice - lowerBand) / (upperBand - lowerBand);
    }

    private double calculatePriceAcceleration(double[] prices) {
        if (prices.length < 3)
            return 0;
        double change1 = (prices[prices.length - 1] - prices[prices.length - 2]) / prices[prices.length - 2];
        double change2 = (prices[prices.length - 2] - prices[prices.length - 3]) / prices[prices.length - 3];
        return change1 - change2;
    }

}
