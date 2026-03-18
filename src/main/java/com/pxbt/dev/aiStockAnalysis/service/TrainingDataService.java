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

    @Value("${app.training.enabled:true}")
    private boolean trainingEnabled;

    @PostConstruct
    public void init() {
        if (!trainingEnabled) {
            log.info("ML training disabled by configuration");
            return;
        }

        // Train on startup (async to not block)
        CompletableFuture.runAsync(() -> {
            try {
                log.info("AI Training standby: waiting 45s for system stabilization...");
                Thread.sleep(45000); 
                collectTrainingData();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @Scheduled(cron = "0 0 3 * * *") // 3 AM daily
    public void scheduledTraining() {
        log.info("Daily ML retraining starting...");
        collectTrainingData();
    }

    public void collectTrainingData() {
        log.info("Starting comprehensive AI training data collection...");

        String[] symbols = { "SPY", "AAPL", "MSFT", "GOOG", "TSLA", "NVDA", "META" };
        String[] timeframes = { "1d", "1W", "1M" }; 

        int totalTrained = 0;

        for (String symbol : symbols) {
            for (String timeframe : timeframes) {
                try {
                    boolean trained = collectSymbolTrainingData(symbol, timeframe);
                    if (trained) {
                        totalTrained++;
                    }
                } catch (Exception e) {
                    log.error("Training failed for {} {}: {}", symbol, timeframe, e.getMessage());
                }
            }
        }

        log.info("Training completed: {} specific models generated", totalTrained);
    }

    public boolean collectSymbolTrainingData(String symbol, String timeframe) {
        // Fetch historical data
        List<StockPrice> fullData = historicalDataService.getHistoricalData(symbol, timeframe,
                getRequiredPointsForTimeframe(timeframe));

        if (fullData == null || fullData.size() < getMinDataPoints(timeframe)) {
            log.debug("Insufficient data for {} {}: {} points", symbol, timeframe, fullData != null ? fullData.size() : 0);
            return false;
        }

        List<double[]> featuresList = new ArrayList<>();
        List<Double> targetChanges = new ArrayList<>();

        int windowSize = getWindowSize(timeframe);
        int futureOffset = getFutureOffset(timeframe);

        for (int i = windowSize; i < fullData.size() - futureOffset; i++) {
            List<StockPrice> windowData = fullData.subList(i - windowSize, i);

            // Extract features (normalized to be price-independent)
            double[] features = extractFeaturesForTraining(windowData);
            double actualChange = calculateActualChange(fullData, i, timeframe);

            if (Math.abs(actualChange) < getMaxChangeFilter(timeframe)) {
                featuresList.add(features);
                targetChanges.add(actualChange);
            }
        }

        if (featuresList.size() >= getMinTrainingSamples(timeframe)) {
            aiModelService.trainModel(symbol, timeframe, featuresList, targetChanges);
            return true;
        }
        return false;
    }

    private double[] extractFeaturesForTraining(List<StockPrice> windowData) {
        double[] prices = windowData.stream().mapToDouble(StockPrice::getPrice).toArray();
        double current = prices[prices.length - 1];

        // MUST MATCH PricePredictionService.extractAdvancedFeatures exactly
        return new double[] {
                calculateSMA(prices, 5) / current,
                calculateSMA(prices, 20) / current,
                calculateEMA(prices, 12) / current,
                calculateVolatility(prices, 20) / current,
                calculateMomentum(prices, 10) / current,
                calculatePriceRateOfChange(prices, 10),
                calculateZScore(prices),
                calculateTrendStrength(prices),
                calculateSupportResistance(prices),
                calculateBollingerPosition(prices),
                calculatePriceAcceleration(prices)
        };
    }

    private double calculateActualChange(List<StockPrice> data, int currentIndex, String timeframe) {
        int futureIndex = currentIndex + getFutureOffset(timeframe);
        if (futureIndex >= data.size()) return 0.0;

        double currentPrice = data.get(currentIndex).getPrice();
        double futurePrice = data.get(futureIndex).getPrice();

        return (futurePrice - currentPrice) / currentPrice;
    }

    // ===== TECHNICAL INDICATORS (Unified with PricePredictionService) =====

    private double calculateSMA(double[] prices, int period) {
        if (prices.length < period) return prices[prices.length - 1];
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
        if (prices.length < 2) return 0.0;
        double mean = calculateSMA(prices, period);
        double sum = 0.0;
        int p = Math.min(period, prices.length);
        for (int i = prices.length - p; i < prices.length; i++) {
            sum += Math.pow(prices[i] - mean, 2);
        }
        return Math.sqrt(sum / p);
    }

    private double calculateMomentum(double[] prices, int period) {
        if (prices.length < period) return 0.0;
        return prices[prices.length - 1] - prices[prices.length - period];
    }

    private double calculatePriceRateOfChange(double[] prices, int period) {
        if (prices.length < period) return 0.0;
        return (prices[prices.length - 1] - prices[prices.length - period]) / prices[prices.length - period];
    }

    private double calculateZScore(double[] prices) {
        if (prices.length < 5) return 0.0;
        double mean = calculateSMA(prices, prices.length);
        double stdDev = calculateVolatility(prices, prices.length);
        return stdDev == 0 ? 0.0 : (prices[prices.length - 1] - mean) / stdDev;
    }

    private double calculateTrendStrength(double[] prices) {
        double sma20 = calculateSMA(prices, Math.min(20, prices.length));
        double sma50 = calculateSMA(prices, Math.min(50, prices.length));
        return (sma20 - sma50) / sma50;
    }

    private double calculateSupportResistance(double[] prices) {
        double current = prices[prices.length - 1];
        double avg = calculateSMA(prices, prices.length);
        return (current - avg) / avg;
    }

    private double calculateBollingerPosition(double[] prices) {
        if (prices.length < 20) return 0.5;
        double sma20 = calculateSMA(prices, 20);
        double stdDev = calculateVolatility(prices, 20);
        double upperBand = sma20 + (2 * stdDev);
        double lowerBand = sma20 - (2 * stdDev);
        if (upperBand == lowerBand) return 0.5;
        return (prices[prices.length - 1] - lowerBand) / (upperBand - lowerBand);
    }

    private double calculatePriceAcceleration(double[] prices) {
        if (prices.length < 3) return 0;
        double change1 = (prices[prices.length - 1] - prices[prices.length - 2]);
        double change2 = (prices[prices.length - 2] - prices[prices.length - 3]);
        return (change1 - change2) / prices[prices.length - 3];
    }

    private int getRequiredPointsForTimeframe(String timeframe) {
        return switch (timeframe) {
            case "1d" -> 1460;
            case "1W" -> 260;
            case "1M" -> 120;
            default -> 100;
        };
    }

    private int getMinDataPoints(String timeframe) {
        return switch (timeframe) {
            case "1d" -> 400;
            case "1W" -> 150;
            case "1M" -> 60;
            default -> 100;
        };
    }

    private int getWindowSize(String timeframe) {
        return switch (timeframe) {
            case "1d" -> 50;
            case "1W" -> 40;
            case "1M" -> 30;
            default -> 30;
        };
    }

    private int getFutureOffset(String timeframe) {
        return switch (timeframe) {
            case "1d" -> 7;
            case "1W" -> 4;
            case "1M" -> 3;
            default -> 1;
        };
    }

    private double getMaxChangeFilter(String timeframe) {
        return switch (timeframe) {
            case "1d" -> 0.35;
            case "1W" -> 0.5;
            case "1M" -> 0.8;
            default -> 0.5;
        };
    }

    private int getMinTrainingSamples(String timeframe) {
        return switch (timeframe) {
            case "1d" -> 80;
            case "1W" -> 50;
            case "1M" -> 20;
            default -> 50;
        };
    }
}
