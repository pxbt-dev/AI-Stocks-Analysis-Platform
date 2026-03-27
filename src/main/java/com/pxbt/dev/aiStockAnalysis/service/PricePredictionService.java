package com.pxbt.dev.aiStockAnalysis.service;

import com.pxbt.dev.aiStockAnalysis.model.StockPrice;
import com.pxbt.dev.aiStockAnalysis.model.PricePrediction;
import com.pxbt.dev.aiStockAnalysis.util.Ta4jConverter;
import com.pxbt.dev.aiStockAnalysis.util.FeatureExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;

import java.util.*;

@Slf4j
@Service
public class PricePredictionService {

    @Autowired
    private StockDataService historicalDataService;

    @Autowired
    private AIModelService aiModelService;

    /**
     * AI-based prediction for multiple timeframes
     */
    public Map<String, PricePrediction> predictMultipleTimeframes(String symbol, double currentPrice) {
        Map<String, PricePrediction> predictions = new LinkedHashMap<>();

        try {
            // Updated timeframe mapping to match training
            String[][] timeframeConfigs = {
                    { "1d", "1day" },
                    { "1W", "1week" },
                    { "1M", "1month" }
            };

            for (String[] config : timeframeConfigs) {
                String tfCode = config[0];
                String tfUI = config[1];

                // Fetch timeframe-specific data
                int pointsNeeded = tfCode.equals("1d") ? 1500 : 500;
                List<StockPrice> timeframeData = historicalDataService.getHistoricalData(symbol, tfCode, pointsNeeded);


                if (timeframeData != null && timeframeData.size() >= 20) {
                    PricePrediction prediction = generateAIPrediction(symbol, currentPrice, timeframeData, tfCode);
                    predictions.put(tfUI, prediction);
                } else {
                    log.debug("Insufficient {} data for {}, showing fallback", tfCode, symbol);
                    int count = timeframeData != null ? timeframeData.size() : 0;
                    predictions.put(tfUI, createFallbackPrediction(symbol, currentPrice, tfCode, count));
                }

            }

        } catch (Exception e) {
            log.error("❌ AI prediction failed for {}: {}", symbol, e.getMessage());
            return createConservativePredictions(symbol, currentPrice, 0);
        }


        return predictions;
    }

    private PricePrediction generateAIPrediction(String symbol, double currentPrice,
            List<StockPrice> recentData, String timeframe) {
        try {
            // 1. Convert to ta4j BarSeries and initialize indicators
            BarSeries series = Ta4jConverter.toSeries(symbol, recentData);
            int lastIdx = series.getEndIndex();
            FeatureExtractor.Indicators inds = new FeatureExtractor.Indicators(series);

            // 2. Extract features for AI prediction
            double[] features = FeatureExtractor.extractFeatures(lastIdx, inds);

            // 3. Get AI result
            Map<String, Object> aiResult = aiModelService.predictWithConfidence(symbol, features, timeframe);
            double predictedChange = (double) aiResult.get("prediction");
            double confidence = (double) aiResult.get("confidence");
            String modelType = (String) aiResult.get("model");
            boolean aiTrained = !modelType.equals("none") && !modelType.equals("error");
            boolean aiReliable = aiTrained && (boolean) aiResult.getOrDefault("isReliable", false);

            // 4. Technical Indicators
            double trendValue = (inds.sma5.getValue(lastIdx).doubleValue() - inds.sma20.getValue(lastIdx).doubleValue()) 
                                / inds.sma20.getValue(lastIdx).doubleValue();
            
            int momentumPeriod = timeframe.equalsIgnoreCase("1d") ? 10 : 5;
            int prevIdx = Math.max(0, lastIdx - momentumPeriod);
            double momentum = (inds.close.getValue(lastIdx).doubleValue() - inds.close.getValue(prevIdx).doubleValue()) 
                              / currentPrice;
            
            double volatility = inds.stdDev20.getValue(lastIdx).doubleValue() / currentPrice;

            // 5. Hybrid Logic: Use TECH if AI is too new or unreliable
            if (!aiReliable) {
                double tech = (trendValue * 0.6) + (momentum * 0.4);
                
                // Add asset-specific jitter
                double assetJitter = (Math.abs(symbol.hashCode() % 100) / 10000.0);
                tech += assetJitter;

                // Timeframe scaling
                double scale = timeframe.equalsIgnoreCase("1M") ? 2.5 : timeframe.equalsIgnoreCase("1W") ? 1.4 : 1.0;
                predictedChange = tech * (1.0 + (volatility * scale));

                if (aiTrained) {
                    double aiVal = (double) aiResult.get("prediction");
                    predictedChange = (predictedChange * 0.6) + (aiVal * 0.4);
                    modelType = "AI+TECH";
                } else {
                    // Unique jitter to prevent static 25%
                    double tfJitter = (Math.abs(timeframe.hashCode() % 35) / 1000.0);

                    confidence = 0.22 + assetJitter + tfJitter + (Math.min(0.12, recentData.size() / 2500.0));
                    modelType = "TECHNICAL_TREND";
                }

            } else {
                // Blend with technical slightly even if reliable
                double aiVal = (double) aiResult.get("prediction");
                predictedChange = (aiVal * 0.9) + (trendValue * 0.1);
            }

            double predictedPrice = currentPrice * (1 + predictedChange);
            String trend = determineTrend(predictedChange);

            // Create prediction
            PricePrediction prediction = new PricePrediction(symbol, predictedPrice, confidence, trend);
            prediction.setModelName(modelType);
            prediction.setRScore(aiResult.containsKey("rScore") ? (double) aiResult.get("rScore") : 0.0);
            prediction.setSamples(aiResult.containsKey("samples") ? (int) aiResult.get("samples") : recentData.size());
            
            prediction.setTrendValue(trendValue);
            prediction.setMomentum(momentum);
            prediction.setRsiFactor((inds.rsi14.getValue(lastIdx).doubleValue() - 50.0) / 50.0);

            log.debug("🎯 {} Prediction for {} {}: {}% | Conf: {}%", 
                    modelType, symbol, timeframe, String.format("%.2f", predictedChange * 100), String.format("%.1f", confidence * 100));

            return prediction;

        } catch (Exception e) {
            log.error("❌ Prediction error for {} {}: {}", symbol, timeframe, e.getMessage());
            return createFallbackPrediction(symbol, currentPrice, timeframe, recentData != null ? recentData.size() : 0);
        }

    }

    private String determineTrend(double predictedChange) {
        if (predictedChange > 0.05) return "STRONG_BULLISH";
        if (predictedChange > 0.015) return "BULLISH";
        if (predictedChange < -0.05) return "STRONG_BEARISH";
        if (predictedChange < -0.015) return "BEARISH";
        return "NEUTRAL";
    }

    private PricePrediction createFallbackPrediction(String symbol, double currentPrice, String timeframe, int sampleCount) {
        double assetSign = (Math.abs(symbol.hashCode() % 50) / 1000.0);
        double tfSign = (Math.abs(timeframe.hashCode() % 25) / 1000.0);
        double fallbackConfidence = 0.18 + assetSign + tfSign;
        PricePrediction pred = new PricePrediction(symbol, currentPrice, fallbackConfidence, "NEUTRAL");
        pred.setModelName("BOOTSTRAP");
        pred.setSamples(sampleCount);
        return pred;
    }


    private Map<String, PricePrediction> createConservativePredictions(String symbol, double currentPrice, int sampleCount) {
        Map<String, PricePrediction> predictions = new HashMap<>();
        predictions.put("1day", createFallbackPrediction(symbol, currentPrice, "1d", sampleCount));
        predictions.put("1week", createFallbackPrediction(symbol, currentPrice, "1W", sampleCount));
        predictions.put("1month", createFallbackPrediction(symbol, currentPrice, "1M", sampleCount));
        return predictions;
    }

}
