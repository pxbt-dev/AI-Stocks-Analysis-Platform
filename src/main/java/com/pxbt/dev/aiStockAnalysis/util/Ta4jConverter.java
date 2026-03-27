package com.pxbt.dev.aiStockAnalysis.util;

import com.pxbt.dev.aiStockAnalysis.model.StockPrice;
import com.pxbt.dev.aiStockAnalysis.model.PriceUpdate;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DoubleNum;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public class Ta4jConverter {

    /**
     * Converts a list of StockPrice to a ta4j BarSeries
     */
    public static BarSeries toSeries(String symbol, List<StockPrice> prices) {
        BarSeries series = new BaseBarSeriesBuilder()
                .withName(symbol)
                .withNumTypeOf(DoubleNum.class) // Memory efficient
                .build();

        for (StockPrice p : prices) {
            ZonedDateTime time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(p.getTimestamp()), ZoneId.systemDefault());
            
            // Handle case where OHLC might be zero
            double open = p.getOpen() != 0 ? p.getOpen() : p.getPrice();
            double high = p.getHigh() != 0 ? p.getHigh() : p.getPrice();
            double low = p.getLow() != 0 ? p.getLow() : p.getPrice();
            double close = p.getClose() != 0 ? p.getClose() : p.getPrice();
            
            series.addBar(time, open, high, low, close, p.getVolume());
        }

        return series;
    }

    /**
     * Converts a list of PriceUpdate to a ta4j BarSeries
     */
    public static BarSeries priceUpdateToSeries(String symbol, List<PriceUpdate> updates) {
        BarSeries series = new BaseBarSeriesBuilder()
                .withName(symbol)
                .withNumTypeOf(DoubleNum.class)
                .build();

        for (PriceUpdate p : updates) {
            ZonedDateTime time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(p.getTimestamp()), ZoneId.systemDefault());
            series.addBar(time, p.getOpen(), p.getHigh(), p.getLow(), p.getClose(), p.getVolume());
        }

        return series;
    }
}
