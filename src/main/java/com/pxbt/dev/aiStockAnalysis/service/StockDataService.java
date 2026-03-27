package com.pxbt.dev.aiStockAnalysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pxbt.dev.aiStockAnalysis.model.PriceUpdate;
import com.pxbt.dev.aiStockAnalysis.model.StockPrice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for fetching historical and real-time stock data from Yahoo Finance
 * and Finnhub APIs.
 */
@Slf4j
@Service
public class StockDataService {

    @Value("${finnhub.api.key:}")
    private String finnhubApiKey;

    @Autowired
    private RestTemplate restTemplate;

    private final Map<String, String> tickerMapping = new LinkedHashMap<>() {
        {
            put("SPY", "SPY");
            put("AAPL", "AAPL");
            put("MSFT", "MSFT");
            put("GOOG", "GOOG");
        }
    };

    /**
     * Fetch historical stock prices from Yahoo Finance API (v8/chart).
     */
    public List<StockPrice> getHistoricalData(String symbol, String timeframe, int limit) {
        String yahooTicker = tickerMapping.getOrDefault(symbol, symbol);
        log.info("📊 Fetching Yahoo Finance data for {} (Ticker: {}) - {} timeframe", symbol, yahooTicker, timeframe);

        String range = timeframeToRange(timeframe);
        String interval = timeframeToInterval(timeframe);

        String url = String.format(
                "https://query1.finance.yahoo.com/v8/finance/chart/%s?range=%s&interval=%s",
                yahooTicker, range, interval);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/121.0.0.0 Safari/537.36");
            headers.set("Accept", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);

            if (response.getBody() == null || !response.getBody().has("chart")) {
                log.error("❌ Invalid Yahoo Finance response for {}", symbol);
                return List.of();
            }

            return parseYahooChartJson(response.getBody(), symbol, limit);

        } catch (Exception e) {
            log.error("❌ Yahoo Finance API error for {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    /**
     * Wrapper for MarketDataService to use PriceUpdate model.
     */
    public List<PriceUpdate> getHistoricalDataAsPriceUpdate(String symbol, String timeframe, int limit) {
        return getHistoricalData(symbol, timeframe, limit).stream()
                .map(sp -> new PriceUpdate(sp.getSymbol(), sp.getPrice(), sp.getVolume(), sp.getTimestamp(),
                        sp.getOpen(), sp.getHigh(), sp.getLow(), sp.getClose()))
                .collect(Collectors.toList());
    }

    private List<StockPrice> parseYahooChartJson(JsonNode root, String symbol, int limit) {
        try {
            JsonNode result = root.path("chart").path("result").get(0);
            if (result == null)
                return List.of();

            JsonNode timestamps = result.path("timestamp");
            JsonNode indicators = result.path("indicators").path("quote").get(0);
            JsonNode adjClose = result.path("indicators").path("adjclose").get(0);

            // Multiplier for SPY to show index-like price ($6k range) as requested
            double multiplier = symbol.equals("SPY") ? 10.0 : 1.0;

            List<StockPrice> results = new ArrayList<>();
            for (int i = 0; i < timestamps.size(); i++) {
                long ts = timestamps.get(i).asLong() * 1000;
                double close = adjClose != null && adjClose.has("adjclose")
                        ? adjClose.path("adjclose").get(i).asDouble()
                        : indicators.path("close").get(i).asDouble();

                double open = indicators.path("open").get(i).asDouble();
                double high = indicators.path("high").get(i).asDouble();
                double low = indicators.path("low").get(i).asDouble();
                double vol = indicators.path("volume").get(i).asDouble();

                if (close > 0) {
                    results.add(new StockPrice(symbol,
                            close * multiplier,
                            vol, ts,
                            open * multiplier,
                            high * multiplier,
                            low * multiplier,
                            close * multiplier));
                }
            }

            results.sort(Comparator.comparingLong(StockPrice::getTimestamp));
            if (results.size() > limit)
                results = results.subList(results.size() - limit, results.size());
            return results;

        } catch (Exception e) {
            log.error("❌ Error parsing Yahoo JSON for {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    /**
     * Get real-time price via Finnhub (fallback to Yahoo for indices).
     */
    public PriceUpdate getCurrentPrice(String symbol) {
        String ticker = tickerMapping.getOrDefault(symbol, symbol);
        double multiplier = symbol.equals("SPY") ? 10.0 : 1.0;

        // If it's an index ticker (^GSPC) or Finnhub token is missing, use Yahoo
        if (ticker.startsWith("^") || finnhubApiKey == null || finnhubApiKey.isBlank()) {
            return getLatestFromYahoo(symbol, ticker, multiplier);
        }

        try {
            String url = String.format("https://finnhub.io/api/v1/quote?symbol=%s&token=%s", ticker, finnhubApiKey);
            JsonNode response = restTemplate.getForObject(url, JsonNode.class);

            if (response == null || !response.has("c") || response.get("c").asDouble() == 0) {
                log.warn("⚠️ Finnhub fallback to Yahoo for {}", symbol);
                return getLatestFromYahoo(symbol, ticker, multiplier);
            }

            double price = response.get("c").asDouble() * multiplier;
            return new PriceUpdate(symbol, price, 0.0, System.currentTimeMillis());

        } catch (Exception e) {
            log.error("❌ Finnhub error for {}: {} - using Yahoo fallback", symbol, e.getMessage());
            return getLatestFromYahoo(symbol, ticker, multiplier);
        }
    }

    private PriceUpdate getLatestFromYahoo(String symbol, String yahooTicker, double multiplier) {
        try {
            List<StockPrice> data = getHistoricalData(symbol, "1d", 1);
            if (!data.isEmpty()) {
                StockPrice latest = data.get(0);
                // Multiplier is already applied in getHistoricalData -> parseYahooChartJson
                return new PriceUpdate(symbol, latest.getPrice(), latest.getVolume(), latest.getTimestamp());
            }
        } catch (Exception e) {
            log.error("❌ Yahoo latest price fetch failed for {}: {}", symbol, e.getMessage());
        }
        return null;
    }

    private String timeframeToRange(String timeframe) {
        return switch (timeframe) {
            case "1m", "5m", "15m", "30m", "1h" -> "1d";
            case "4h", "1d" -> "10y";

            case "1W" -> "5y";
            case "1M" -> "10y";
            default -> "2y";
        };
    }

    private String timeframeToInterval(String timeframe) {
        return switch (timeframe) {
            case "1m", "5m", "15m", "30m", "1h", "4h" -> "1h"; // Yahoo v8/chart limits
            case "1d" -> "1d";
            case "1W" -> "1wk";
            case "1M" -> "1mo";
            default -> "1d";
        };
    }
}
