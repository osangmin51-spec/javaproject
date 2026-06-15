package domain;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import util.Json;

public class TradeLog {
    public final long memberUid;
    public final LocalDateTime time;
    public final String stockName;
    public final int quantity;
    public final int price;
    public final String type;

    public TradeLog(long memberUid, String stockName, int quantity, int price, String type) {
        this(memberUid, stockName, quantity, price, type, LocalDateTime.now());
    }

    public TradeLog(long memberUid, String stockName, int quantity, int price, String type, LocalDateTime time) {
        this.memberUid = memberUid;
        this.stockName = stockName;
        this.quantity = quantity;
        this.price = price;
        this.type = type;
        this.time = time;
    }

    public String toJson() {
        return Json.obj("time", time.format(DateTimeFormatter.ofPattern("HH:mm:ss")), "stockName", stockName, "quantity", quantity, "price", price, "type", type);
    }
}
