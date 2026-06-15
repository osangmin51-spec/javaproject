package domain;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import util.Json;

public class PricePoint {
    public final LocalDateTime time;
    public final int price;

    public PricePoint(LocalDateTime time, int price) {
        this.time = time;
        this.price = price;
    }

    public String toJson() {
        return Json.obj("time", time.format(DateTimeFormatter.ofPattern("HH:mm:ss")), "price", price);
    }
}
