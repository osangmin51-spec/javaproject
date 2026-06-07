import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class Member {
    final long uid;
    final String name;
    final String id;
    final String pwd;
    final Map<String, Share> shares = new ConcurrentHashMap<>();
    final Map<String, Stock> stocks;
    final Map<String, Integer> items = new ConcurrentHashMap<>();
    int day = 1;
    int balance = 1_000_000;

    Member(long uid, String name, String id, String pwd, Map<String, Stock> stocks) {
        this.uid = uid;
        this.name = name;
        this.id = id;
        this.pwd = pwd;
        this.stocks = stocks;
    }

    String toJson() {
        return Json.obj("uid", uid, "name", name, "id", id, "day", day, "balance", balance);
    }
}

class Stock {
    final String code;
    final String name;
    final String market;
    final String sector;
    final String description;
    int price;
    int quantity;
    int priceFluct;
    double nextFluct;
    final List<PricePoint> history = new ArrayList<>();

    Stock(String code, String name, String market, String sector, String description, int price, int quantity, int priceFluct, double nextFluct) {
        this.code = code;
        this.name = name;
        this.market = market;
        this.sector = sector;
        this.description = description;
        this.price = price;
        this.quantity = quantity;
        this.priceFluct = priceFluct;
        this.nextFluct = nextFluct;
        recordPrice(price);
    }

    Stock copy() {
        Stock copy = new Stock(code, name, market, sector, description, price, quantity, priceFluct, nextFluct);
        synchronized (history) {
            copy.history.clear();
            copy.history.addAll(history);
        }
        return copy;
    }

    void updatePrice(int nextPrice, int change, double changeRate) {
        price = nextPrice;
        priceFluct = change;
        nextFluct = 1.0 + changeRate / 100.0;
        recordPrice(nextPrice);
    }

    void updateExternalPrice(int nextPrice, int change, double changeRate) {
        synchronized (history) {
            if (history.size() == 1) {
                history.clear();
                int previousPrice = nextPrice - change;
                if (previousPrice > 0 && previousPrice != nextPrice) {
                    history.add(new PricePoint(LocalDateTime.now().minusMinutes(1), previousPrice));
                }
            }
        }
        updatePrice(nextPrice, change, changeRate);
    }

    private void recordPrice(int value) {
        synchronized (history) {
            history.add(new PricePoint(LocalDateTime.now(), value));
            while (history.size() > 40) history.remove(0);
        }
    }

    String toJson() {
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
                "priceFluct", priceFluct,
                "changeRate", String.format(Locale.US, "%.2f", rate),
                "nextFluct", String.format(Locale.US, "%.2f", nextFluct),
                "history", historyJson()
        );
    }

    private String historyJson() {
        synchronized (history) {
            return Json.array(history.stream().map(PricePoint::toJson).toList());
        }
    }
}

class PricePoint {
    final LocalDateTime time;
    final int price;

    PricePoint(LocalDateTime time, int price) {
        this.time = time;
        this.price = price;
    }

    String toJson() {
        return Json.obj("time", time.format(DateTimeFormatter.ofPattern("HH:mm:ss")), "price", price);
    }
}

class Share {
    final String stockName;
    int quantity;
    int purchasePrice;

    Share(String stockName, int quantity, int unitPrice) {
        this.stockName = stockName;
        this.quantity = quantity;
        this.purchasePrice = unitPrice * quantity;
    }

    Share(String stockName, int quantity, int purchasePrice, boolean alreadyTotal) {
        this.stockName = stockName;
        this.quantity = quantity;
        this.purchasePrice = alreadyTotal ? purchasePrice : purchasePrice * quantity;
    }

    Share buy(int orderQuantity, int totalPrice) {
        quantity += orderQuantity;
        purchasePrice += totalPrice;
        return this;
    }

    String toJson(int currentPrice, int value, int profit, double profitRate) {
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

class Item {
    final String code;
    final String name;
    final int price;
    final String description;

    Item(String code, String name, int price, String description) {
        this.code = code;
        this.name = name;
        this.price = price;
        this.description = description;
    }

    String toJson(int owned) {
        return Json.obj("code", code, "name", name, "price", price, "description", description, "owned", owned);
    }
}

class BoardPost {
    final int id;
    final String author;
    String title;
    String content;
    int views;
    final LocalDateTime createdAt = LocalDateTime.now();
    final List<Comment> comments = new ArrayList<>();

    BoardPost(int id, String author, String title, String content) {
        this.id = id;
        this.author = author;
        this.title = title;
        this.content = content;
    }

    int id() {
        return id;
    }

    String toJson() {
        return Json.obj("id", id, "author", author, "title", title, "content", content, "views", views,
                "createdAt", createdAt.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                "comments", Json.array(comments.stream().map(Comment::toJson).toList()));
    }
}

class Comment {
    final int id;
    final String author;
    String content;
    final LocalDateTime createdAt = LocalDateTime.now();

    Comment(int id, String author, String content) {
        this.id = id;
        this.author = author;
        this.content = content;
    }

    String toJson() {
        return Json.obj("id", id, "author", author, "content", content, "createdAt", createdAt.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }
}

class TradeLog {
    final long memberUid;
    final LocalDateTime time;
    final String stockName;
    final int quantity;
    final int price;
    final String type;

    TradeLog(long memberUid, String stockName, int quantity, int price, String type) {
        this(memberUid, stockName, quantity, price, type, LocalDateTime.now());
    }

    TradeLog(long memberUid, String stockName, int quantity, int price, String type, LocalDateTime time) {
        this.memberUid = memberUid;
        this.stockName = stockName;
        this.quantity = quantity;
        this.price = price;
        this.type = type;
        this.time = time;
    }

    LocalDateTime time() {
        return time;
    }

    String toJson() {
        return Json.obj("time", time.format(DateTimeFormatter.ofPattern("HH:mm:ss")), "stockName", stockName, "quantity", quantity, "price", price, "type", type);
    }
}
