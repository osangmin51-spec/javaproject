package domain;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import util.Json;

public class Member {
    public final long uid;
    public final String name;
    public final Map<String, Share> shares = new ConcurrentHashMap<>();
    public final Map<String, Stock> stocks;
    public int balance = 1_000_000;

    public Member(long uid, String name, Map<String, Stock> stocks) {
        this.uid = uid;
        this.name = name;
        this.stocks = stocks;
    }

    public String toJson() {
        return Json.obj("uid", uid, "balance", balance);
    }
}
