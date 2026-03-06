package com.pxbt.dev.aiStockAnalysis.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceUpdate {
    private String symbol;
    private double price;
    private double volume;
    private long timestamp;
    private double open;
    private double high;
    private double low;
    private double close;

    public PriceUpdate(String symbol, double price, double volume, long timestamp) {
        this.symbol = symbol;
        this.price = price;
        this.volume = volume;
        this.timestamp = timestamp;
        this.open = price;  // Set OHLC to the same price
        this.high = price;
        this.low = price;
        this.close = price;
    }

}
