package external;

public class KisVolumeRankItem {
    public final int rank;
    public final String name;
    public final String code;
    public final int price;
    public final int change;
    public final double changeRate;
    public final long volume;

    public KisVolumeRankItem(int rank, String name, String code, long volume) {
        this(rank, name, code, 0, 0, 0.0, volume);
    }

    public KisVolumeRankItem(int rank, String name, String code, int price, int change, double changeRate, long volume) {
        this.rank = rank;
        this.name = name;
        this.code = code;
        this.price = price;
        this.change = change;
        this.changeRate = changeRate;
        this.volume = volume;
    }

    public KisQuoteTarget toTarget() {
        return new KisQuoteTarget(name, code);
    }
}
