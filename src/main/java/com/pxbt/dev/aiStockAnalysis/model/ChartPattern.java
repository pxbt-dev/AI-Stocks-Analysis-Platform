package com.pxbt.dev.aiStockAnalysis.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChartPattern {
    private String symbol;
    private String patternType;
    private double priceLevel;
    private double confidence;
    private String description;
    private long timestamp;

    public ChartPattern(String patternType, double priceLevel, double confidence, String description, long timestamp) {
        this.patternType = patternType;
        this.priceLevel = priceLevel;
        this.confidence = confidence;
        this.description = description;
        this.timestamp = timestamp;
        this.symbol = ""; // Default empty symbol
    }

    // Custom methods
    public String getFormattedConfidence() {
        return String.format("%.0f%%", confidence * 100);
    }

    public String getTrendDirection() {
        if (patternType == null) {
            return "NEUTRAL";
        }

        switch (patternType.toUpperCase()) {
            case "UPTREND":
            case "BULLISH_ENGULFING":
            case "DOUBLE_BOTTOM":
            case "BULLISH":
                return "BULLISH";
            case "DOWNTREND":
            case "BEARISH_ENGULFING":
            case "DOUBLE_TOP":
            case "HEAD_SHOULDERS":
            case "BEARISH":
                return "BEARISH";
            default:
                return "NEUTRAL";
        }
    }
}
