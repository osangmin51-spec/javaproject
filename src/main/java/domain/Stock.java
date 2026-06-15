package domain;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import util.Json;

public class Stock {
    public final String code;
    public final String name;
    public final String market;
    public final String sector;
    public final String description;
    public int price;
    public int quantity;
    public int priceFluct;
    public double nextFluct;
    public long tradingVolume;
    public String quoteSource = "초기 데이터";
    public String lastUpdated = "";
    public final List<PricePoint> history = new ArrayList<>();

    public Stock(String code, String name, String market, String sector, String description, int price, int quantity, int priceFluct, double nextFluct) {
        this.code = code;
        this.name = name;
        this.market = market;
        this.sector = sector;
        this.description = description;
        this.price = price;
        this.quantity = quantity;
        this.priceFluct = priceFluct;
        this.nextFluct = nextFluct;
        this.tradingVolume = quantity;
        recordPrice(price);
    }

    public Stock copy() {
        Stock copy = new Stock(code, name, market, sector, description, price, quantity, priceFluct, nextFluct);
        copy.tradingVolume = tradingVolume;
        copy.quoteSource = quoteSource;
        copy.lastUpdated = lastUpdated;
        synchronized (history) {
            copy.history.clear();
            copy.history.addAll(history);
        }
        return copy;
    }

    public void updatePrice(int nextPrice, int change, double changeRate, long volume) {
        price = nextPrice;
        priceFluct = change;
        nextFluct = 1.0 + changeRate / 100.0;
        if (volume > 0) tradingVolume = volume;
        quoteSource = "내장 모의 시세";
        lastUpdated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        recordPrice(nextPrice);
    }

    public void updatePrice(int nextPrice, int change, double changeRate) {
        updatePrice(nextPrice, change, changeRate, 0);
    }

    public void updateExternalPrice(int nextPrice, int change, double changeRate, long volume) {
        synchronized (history) {
            if (history.size() == 1) {
                history.clear();
                int previousPrice = nextPrice - change;
                if (previousPrice > 0 && previousPrice != nextPrice) {
                    history.add(new PricePoint(LocalDateTime.now().minusMinutes(1), previousPrice));
                }
            }
        }
        quoteSource = "한국투자증권 KIS";
        updatePrice(nextPrice, change, changeRate, volume);
        quoteSource = "한국투자증권 KIS";
    }

    public void updateExternalPrice(int nextPrice, int change, double changeRate) {
        updateExternalPrice(nextPrice, change, changeRate, 0);
    }

    private void recordPrice(int value) {
        synchronized (history) {
            history.add(new PricePoint(LocalDateTime.now(), value));
            while (history.size() > 40) history.remove(0);
        }
    }

    public String toJson() {
        int previous = price - priceFluct;
        double rate = previous == 0 ? 0.0 : priceFluct * 100.0 / previous;
        return Json.obj(
                "name", name,
                "code", code,
                "market", market,
                "sector", sector,
                "description", description,
                "price", price,
                "quantity", quantity,
                "tradingVolume", tradingVolume,
                "priceFluct", priceFluct,
                "changeRate", String.format(Locale.US, "%.2f", rate),
                "nextFluct", String.format(Locale.US, "%.2f", nextFluct),
                "quoteSource", quoteSource,
                "lastUpdated", lastUpdated,
                "history", historyJson()
        );
    }

    private String historyJson() {
        synchronized (history) {
            return Json.array(history.stream().map(PricePoint::toJson).toList());
        }
    }
}
