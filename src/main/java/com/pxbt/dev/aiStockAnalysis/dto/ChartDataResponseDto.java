package com.pxbt.dev.aiStockAnalysis.dto;

import com.pxbt.dev.aiStockAnalysis.model.StockPrice;
import com.pxbt.dev.aiStockAnalysis.model.AIAnalysisResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@RequiredArgsConstructor
public class ChartDataResponseDto {
    private List<StockPrice> prices;
    private AIAnalysisResult analysis; // Use existing AIAnalysisResult
    private String timeframe;

    public ChartDataResponseDto(List<StockPrice> prices, AIAnalysisResult analysis, String timeframe) {
        this.prices = prices;
        this.analysis = analysis;
        this.timeframe = timeframe;
    }
}
