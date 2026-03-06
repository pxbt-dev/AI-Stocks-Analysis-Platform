package com.pxbt.dev.aiStockAnalysis.dto;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class OHLCData {
    private long timestamp;
    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;

    public OHLCData(long timestamp, double open, double high, double low, double close, double volume) {
        this.timestamp = timestamp;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;

    }
}
