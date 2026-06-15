package domain;

import java.util.Locale;
import util.Json;

public class Share {
    public final String stockName;
    public int quantity;
    public int purchasePrice;

    public Share(String stockName, int quantity, int unitPrice) {
        this.stockName = stockName;
        this.quantity = quantity;
        this.purchasePrice = unitPrice * quantity;
    }

    public Share(String stockName, int quantity, int purchasePrice, boolean alreadyTotal) {
        this.stockName = stockName;
        this.quantity = quantity;
        this.purchasePrice = alreadyTotal ? purchasePrice : purchasePrice * quantity;
    }

    public Share buy(int orderQuantity, int totalPrice) {
        quantity += orderQuantity;
        purchasePrice += totalPrice;
        return this;
    }

    public String toJson(int currentPrice, int value, int profit, double profitRate) {
        int average = quantity == 0 ? 0 : purchasePrice / quantity;
        return Json.obj(
                "stockName", stockName,
                "quantity", quantity,
                "purchasePrice", purchasePrice,
                "averagePrice", average,
                "currentPrice", currentPrice,
                "value", value,
                "profit", profit,
                "profitRate", String.format(Locale.US, "%.2f", profitRate)
        );
    }
}
