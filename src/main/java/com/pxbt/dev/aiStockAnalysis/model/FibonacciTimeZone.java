package com.pxbt.dev.aiStockAnalysis.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FibonacciTimeZone {
    private String symbol;
    private String zoneType;
    private long startTimestamp;
    private long endTimestamp;
    private double startPrice;
    private double endPrice;
    private double strength;
    private String description;
    private String bias; // BULLISH or BEARISH

    public String getFormattedStrength() {
        return String.format("%.0f%%", strength * 100);
    }

    public String getDuration() {
        long duration = endTimestamp - startTimestamp;
        long days = duration / (24 * 60 * 60 * 1000);
        return days + " days";
    }

    public double getPriceChange() {
        return ((endPrice - startPrice) / startPrice) * 100;
    }
}
