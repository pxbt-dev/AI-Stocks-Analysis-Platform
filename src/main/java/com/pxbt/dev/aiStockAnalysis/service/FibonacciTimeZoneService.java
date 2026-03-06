package com.pxbt.dev.aiStockAnalysis.service;

import com.pxbt.dev.aiStockAnalysis.model.StockPrice;
import com.pxbt.dev.aiStockAnalysis.model.FibonacciTimeZone;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FibonacciTimeZoneService {

    // Fibonacci sequence for time zones
    private static final int[] FIBONACCI_SEQUENCE = {1, 2, 3, 5, 8, 13, 21, 34, 55, 89};

    public List<FibonacciTimeZone> calculateTimeZones(String symbol, List<StockPrice> prices) {
        List<FibonacciTimeZone> timeZones = new ArrayList<>();

        if (prices.size() < 10) {
            log.debug("Insufficient data for Fibonacci Time Zones: {} points", prices.size());
            return timeZones;
        }

        try {
            // Find significant highs and lows
            List<StockPrice> significantHighs = findSignificantHighs(prices, 5);
            List<StockPrice> significantLows = findSignificantLows(prices, 5);

            // Calculate time zones from significant points
            timeZones.addAll(calculateZonesFromHighs(symbol, significantHighs, prices));
            timeZones.addAll(calculateZonesFromLows(symbol, significantLows, prices));

            // Sort by importance (more recent = more important)
            timeZones.sort((a, b) -> Long.compare(b.getStartTimestamp(), a.getStartTimestamp()));

            log.debug("⏰ Calculated {} Fibonacci Time Zones for {}", timeZones.size(), symbol);

        } catch (Exception e) {
            log.error("❌ Fibonacci Time Zone calculation failed for {}: {}", symbol, e.getMessage());
        }

        return timeZones;
    }

    private List<FibonacciTimeZone> calculateZonesFromHighs(String symbol, List<StockPrice> highs, List<StockPrice> allPrices) {
        List<FibonacciTimeZone> zones = new ArrayList<>();

        for (StockPrice high : highs) {
            int highIndex = findPriceIndex(allPrices, high);
            if (highIndex == -1) continue;

            long highTimestamp = high.getTimestamp();
            double highPrice = high.getPrice();

            for (int fibNumber : FIBONACCI_SEQUENCE) {
                int futureIndex = highIndex + fibNumber;
                if (futureIndex < allPrices.size()) {
                    StockPrice futurePrice = allPrices.get(futureIndex);

                    FibonacciTimeZone zone = new FibonacciTimeZone(
                            symbol,
                            "FIB_HIGH_" + fibNumber,
                            highTimestamp,
                            futurePrice.getTimestamp(),
                            highPrice,
                            futurePrice.getPrice(),
                            calculateZoneStrength(fibNumber, highIndex, allPrices.size()),
                            "Fibonacci Time Zone from high: " + fibNumber + " periods",
                            "BEARISH" // Zones from highs often indicate resistance
                    );
                    zones.add(zone);
                }
            }
        }

        return zones;
    }

    private List<FibonacciTimeZone> calculateZonesFromLows(String symbol, List<StockPrice> lows, List<StockPrice> allPrices) {
        List<FibonacciTimeZone> zones = new ArrayList<>();

        for (StockPrice low : lows) {
            int lowIndex = findPriceIndex(allPrices, low);
            if (lowIndex == -1) continue;

            long lowTimestamp = low.getTimestamp();
            double lowPrice = low.getPrice();

            for (int fibNumber : FIBONACCI_SEQUENCE) {
                int futureIndex = lowIndex + fibNumber;
                if (futureIndex < allPrices.size()) {
                    StockPrice futurePrice = allPrices.get(futureIndex);

                    FibonacciTimeZone zone = new FibonacciTimeZone(
                            symbol,
                            "FIB_LOW_" + fibNumber,
                            lowTimestamp,
                            futurePrice.getTimestamp(),
                            lowPrice,
                            futurePrice.getPrice(),
                            calculateZoneStrength(fibNumber, lowIndex, allPrices.size()),
                            "Fibonacci Time Zone from low: " + fibNumber + " periods",
                            "BULLISH" // Zones from lows often indicate support
                    );
                    zones.add(zone);
                }
            }
        }

        return zones;
    }

    private List<StockPrice> findSignificantHighs(List<StockPrice> prices, int window) {
        List<StockPrice> highs = new ArrayList<>();

        for (int i = window; i < prices.size() - window; i++) {
            StockPrice current = prices.get(i);
            boolean isHigh = true;

            // Check if this is a local high within the window
            for (int j = i - window; j <= i + window; j++) {
                if (j != i && prices.get(j).getPrice() >= current.getPrice()) {
                    isHigh = false;
                    break;
                }
            }

            if (isHigh && isSignificantMove(prices, i, true)) {
                highs.add(current);
            }
        }

        return highs.stream()
                .sorted((a, b) -> Double.compare(b.getPrice(), a.getPrice()))
                .limit(3) // Top 3 significant highs
                .collect(Collectors.toList());
    }

    private List<StockPrice> findSignificantLows(List<StockPrice> prices, int window) {
        List<StockPrice> lows = new ArrayList<>();

        for (int i = window; i < prices.size() - window; i++) {
            StockPrice current = prices.get(i);
            boolean isLow = true;

            // Check if this is a local low within the window
            for (int j = i - window; j <= i + window; j++) {
                if (j != i && prices.get(j).getPrice() <= current.getPrice()) {
                    isLow = false;
                    break;
                }
            }

            if (isLow && isSignificantMove(prices, i, false)) {
                lows.add(current);
            }
        }

        return lows.stream()
                .sorted(Comparator.comparingDouble(StockPrice::getPrice))
                .limit(3) // Top 3 significant lows
                .collect(Collectors.toList());
    }

    private boolean isSignificantMove(List<StockPrice> prices, int index, boolean isHigh) {
        if (index < 5 || index >= prices.size() - 5) return false;

        StockPrice current = prices.get(index);
        double avgBefore = prices.subList(index - 5, index).stream()
                .mapToDouble(StockPrice::getPrice)
                .average()
                .orElse(current.getPrice());

        double avgAfter = prices.subList(index + 1, index + 6).stream()
                .mapToDouble(StockPrice::getPrice)
                .average()
                .orElse(current.getPrice());

        if (isHigh) {
            // For highs, current should be significantly above both averages
            return current.getPrice() > avgBefore * 1.02 && current.getPrice() > avgAfter * 1.02;
        } else {
            // For lows, current should be significantly below both averages
            return current.getPrice() < avgBefore * 0.98 && current.getPrice() < avgAfter * 0.98;
        }
    }

    private int findPriceIndex(List<StockPrice> prices, StockPrice target) {
        for (int i = 0; i < prices.size(); i++) {
            if (prices.get(i).getTimestamp() == target.getTimestamp() &&
                    prices.get(i).getPrice() == target.getPrice()) {
                return i;
            }
        }
        return -1;
    }

    private double calculateZoneStrength(int fibNumber, int startIndex, int totalPrices) {
        double recencyFactor = 1.0 - (startIndex / (double) totalPrices); // More recent = stronger
        double fibFactor = 1.0 - (fibNumber / 100.0); // Smaller fib numbers = stronger

        return Math.min(0.9, Math.max(0.3, (recencyFactor + fibFactor) / 2));
    }
}
