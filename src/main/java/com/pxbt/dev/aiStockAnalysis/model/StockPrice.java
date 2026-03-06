package com.pxbt.dev.aiStockAnalysis.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockPrice {
    private String symbol;
    private double price;
    private double volume;
    private long timestamp;
    private double open;
    private double high;
    private double low;
    private double close;
}
