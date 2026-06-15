package external;

public class BrokerTick {
    public final String symbol;
    public final int price;
    public final int change;
    public final double percent;
    public final long volume;

    public BrokerTick(String symbol, int price, int change, double percent) {
        this(symbol, price, change, percent, 0);
    }

    public BrokerTick(String symbol, int price, int change, double percent, long volume) {
        this.symbol = symbol;
        this.price = price;
        this.change = change;
        this.percent = percent;
        this.volume = volume;
    }
}
