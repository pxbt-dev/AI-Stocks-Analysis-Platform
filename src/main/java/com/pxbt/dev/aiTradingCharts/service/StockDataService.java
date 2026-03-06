package com.pxbt.dev.aiTradingCharts.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pxbt.dev.aiTradingCharts.model.CryptoPrice;
import com.pxbt.dev.aiTradingCharts.model.PriceUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StockDataService {

    @Value("${finnhub.api.key:}")
    private String finnhubApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String YF_CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=%s&range=%s";

    private static final Set<String> INDEX_SYMBOLS = Set.of("SPY");

    private final Map<String, String> tickerMapping = new LinkedHashMap<>() {
        {
            put("SPY", "SPY");
            put("AAPL", "AAPL");
            put("MSFT", "MSFT");
            put("GOOG", "GOOG");
        }
    };

    private HttpHeaders yahooHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
        headers.set("Accept", "application/json");
        headers.set("Accept-Language", "en-US,en;q=0.9");
        return headers;
    }

    private String[] toYahooIntervalRange(String timeframe, int limit) {
        return switch (timeframe.toLowerCase()) {
            case "1w" -> new String[] { "1wk", limit <= 52 ? "1y" : "5y" };
            case "1m" -> new String[] { "1mo", limit <= 12 ? "1y" : "5y" };
            case "4h" -> new String[] { "60m", "60d" };
            case "1h" -> new String[] { "60m", "60d" };
            default -> new String[] { "1d", limit <= 100 ? "6mo" : "2y" };
        };
    }

    /**
     * Fetch historical OHLCV data via Yahoo Finance v8/chart API.
     * Returns an empty list on failure — no mock data.
     */
    public List<CryptoPrice> getHistoricalData(String symbol, String timeframe, int limit) {
        String ticker = tickerMapping.getOrDefault(symbol, symbol);
        log.info("📡 Fetching historical data from Yahoo Finance v8/chart for {} (ticker: {}) interval: {}",
                symbol, ticker, timeframe);

        try {
            String[] ir = toYahooIntervalRange(timeframe, limit);
            String url = String.format(YF_CHART_URL, ticker.replace("^", "%5E"), ir[0], ir[1]);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(yahooHeaders()), String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<CryptoPrice> results = parseChartResponse(response.getBody(), symbol, limit);
                if (!results.isEmpty()) {
                    log.info("✅ Loaded {} historical points for {} ({})", results.size(), symbol, timeframe);
                    return results;
                }
            }

            log.warn("⚠️ Empty chart response for {} — returning no data.", symbol);

        } catch (Exception e) {
            log.error("❌ Yahoo v8/chart failed for {}: {} — returning no data.", symbol, e.getMessage());
        }

        return Collections.emptyList();
    }

    private List<CryptoPrice> parseChartResponse(String body, String symbol, int limit) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode result = root.path("chart").path("result");
            if (result.isMissingNode() || !result.isArray() || result.isEmpty())
                return List.of();

            JsonNode timestamps = result.get(0).path("timestamp");
            JsonNode quote = result.get(0).path("indicators").path("quote").get(0);
            if (timestamps.isMissingNode() || quote == null)
                return List.of();

            JsonNode opens = quote.path("open");
            JsonNode highs = quote.path("high");
            JsonNode lows = quote.path("low");
            JsonNode closes = quote.path("close");
            JsonNode volumes = quote.path("volume");

            List<CryptoPrice> results = new ArrayList<>();
            for (int i = 0; i < timestamps.size(); i++) {
                if (closes.get(i) == null || closes.get(i).isNull())
                    continue;

                long ts = timestamps.get(i).asLong() * 1000L;
                double open = opens.get(i) != null && !opens.get(i).isNull() ? opens.get(i).asDouble() : 0.0;
                double high = highs.get(i) != null && !highs.get(i).isNull() ? highs.get(i).asDouble() : 0.0;
                double low = lows.get(i) != null && !lows.get(i).isNull() ? lows.get(i).asDouble() : 0.0;
                double close = closes.get(i).asDouble();
                double vol = volumes.get(i) != null && !volumes.get(i).isNull() ? volumes.get(i).asDouble() : 0.0;

                results.add(new CryptoPrice(symbol, close, vol, ts, open, high, low, close));
            }

            results.sort(Comparator.comparing(CryptoPrice::getTimestamp));
            if (results.size() > limit)
                results = results.subList(results.size() - limit, results.size());
            return results;

        } catch (Exception e) {
            log.error("❌ Failed to parse Yahoo chart JSON for {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    /**
     * Get real-time price via Finnhub.
     * Returns null on failure — no mock data.
     * Callers (RealTimeDataService) already null-check before processing.
     */
    public PriceUpdate getCurrentPrice(String symbol) {
        String finnhubTicker = tickerMapping.getOrDefault(symbol, symbol);

        if (finnhubApiKey == null || finnhubApiKey.isBlank() || finnhubApiKey.contains("YOUR_KEY")) {
            log.warn("⚠️ Finnhub API key not configured — no real-time price for {}", symbol);
            return null;
        }

        try {
            String url = String.format(
                    "https://finnhub.io/api/v1/quote?symbol=%s&token=%s", finnhubTicker, finnhubApiKey);
            JsonNode response = restTemplate.getForObject(url, JsonNode.class);

            if (response == null || !response.has("c") || response.get("c").asDouble() == 0) {
                log.warn("⚠️ Empty Finnhub response for {} — skipping update.", symbol);
                return null;
            }

            // No multiplier needed for SPY proxy
            double price = response.get("c").asDouble();

            return new PriceUpdate(symbol, price, 0.0, System.currentTimeMillis());

        } catch (Exception e) {
            log.error("❌ Finnhub quote failed for {}: {} — skipping update.", symbol, e.getMessage());
            return null;
        }
    }

    public List<PriceUpdate> getHistoricalDataAsPriceUpdate(String symbol, String timeframe, int limit) {
        return getHistoricalData(symbol, timeframe, limit).stream()
                .map(cp -> new PriceUpdate(symbol, cp.getClose(), cp.getVolume(), cp.getTimestamp()))
                .collect(Collectors.toList());
    }
}
