# AI Stocks Analysis Platform

A machine learning-powered stock analysis and price prediction platform built with Spring Boot. It combines technical analysis, Wyckoff theory, Fibonacci time zones, and Weka ML models to analyse stock price movements across multiple timeframes.

> **Disclaimer:** This platform is for research and educational purposes only. It is not financial advice and should not be used for live trading decisions.

---

## Features

- **Real-time price updates** via WebSocket (SockJS + STOMP)
- **Multi-timeframe predictions** — 1-minute, daily, and weekly horizons
- **Technical indicators** — RSI, MACD, Bollinger Bands, SMA, EMA, and more (via TA4J)
- **Wyckoff market analysis** — phase detection and volume analysis
- **Fibonacci time zone calculations** — cyclic pattern detection
- **Chart pattern recognition** — automated pattern identification
- **ML model training** — Random Forest and Linear Regression models (Weka), retrained daily at 3 AM
- **Interactive dashboard** — candlestick charts with zoom via Chart.js
- **Observability** — Prometheus metrics, health checks, and memory monitoring

Tracked symbols: `SPY`, `AAPL`, `MSFT`, `GOOG`, `TSLA`, `NVDA`, `META`

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.1.9 |
| ML | Weka 3.8.6 (Random Forest, Linear Regression) |
| Technical Analysis | TA4J 0.15 |
| Data Sources | Yahoo Finance API, Finnhub API |
| Frontend | Thymeleaf, Chart.js 3.9.1, Vanilla JS |
| Build | Maven 3.9+ |
| Deployment | Docker, Heroku / Railway (Procfile) |

---

## Prerequisites

- Java 21
- Maven 3.9+
- A [Finnhub](https://finnhub.io/) API key (free tier is sufficient)

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/pxbt-dev/AI-Stocks-Analysis-Platform.git
cd AI-Stocks-Analysis-Platform
```

### 2. Configure environment variables

| Variable | Description | Default |
|---|---|---|
| `FINNHUB_API_KEY` | Finnhub API key for market data | *(set in `application.properties`)* |
| `PORT` | Server port | `8080` |
| `JAVA_OPTS` | JVM options | — |

You can set them in your shell or create a `.env` file if using a runner that supports it.

### 3. Build and run

```bash
# Build (skip tests for faster startup)
mvn clean package -DskipTests

# Run
java -jar target/ai-stocks-predictor-1.0.0.jar
```

Or use the Spring Boot Maven plugin:

```bash
mvn spring-boot:run
```

The dashboard will be available at [http://localhost:8080](http://localhost:8080).

---

## Docker

```bash
# Build the image
docker build -t ai-stocks-predictor .

# Run the container
docker run -p 8080:8080 -e FINNHUB_API_KEY=<your-key> ai-stocks-predictor
```

---

## Project Structure

```
src/main/java/com/pxbt/dev/aiStockAnalysis/
├── controller/         REST endpoints and Thymeleaf page routing
├── service/            Core business logic (analysis, predictions, ML, data fetching)
├── model/              Domain models and DTOs
├── util/               Feature extraction and TA4J conversion utilities
└── config/             HTTP client configuration

src/main/resources/
├── application.properties
├── templates/chart.html   Main dashboard
└── static/                CSS and JavaScript

models/                 Pre-trained Weka models (per symbol and timeframe)
historical_data/        CSV data storage
```

---

## Key Configuration

All settings are in [src/main/resources/application.properties](src/main/resources/application.properties):

```properties
server.port=8080
finnhub.api.key=<your-key>
app.training.enabled=true          # Enable/disable daily ML retraining
```

---

## Monitoring & Observability

| Endpoint | Description |
|---|---|
| `/actuator/health` | Health check |
| `/actuator/prometheus` | Prometheus metrics |
| `/actuator/metrics` | Spring Micrometer metrics |

---

## Deployment

The project includes a `Procfile` for Heroku and Railway deployments. The Dockerfile uses a multi-stage build optimised for memory-constrained environments (`-XX:MaxRAMPercentage=75`).

---

## License

This project is provided as-is for educational and research purposes.
