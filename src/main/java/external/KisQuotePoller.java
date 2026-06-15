package external;

import java.time.LocalDateTime;
import java.util.function.Consumer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KisQuotePoller {
    private final KisQuoteClient client;
    private final Map<String, KisQuoteTarget> fallbackTargets;
    private final Consumer<BrokerTick> consumer;
    private final Consumer<List<KisVolumeRankItem>> volumeRankConsumer;
    private final Runnable unavailableCallback;
    private final int marketRankLimit;
    private final int pollTargetLimit;
    private Map<String, KisQuoteTarget> activeTargets = new LinkedHashMap<>();
    private long lastRankRefresh;
    private volatile boolean running = true;
    private volatile boolean unavailableNotified;

    public KisQuotePoller(KisConfig config, Map<String, KisQuoteTarget> targets, Consumer<BrokerTick> consumer) {
        this(config, targets, consumer, ranks -> {});
    }

    public KisQuotePoller(KisConfig config, Map<String, KisQuoteTarget> targets, Consumer<BrokerTick> consumer, Consumer<List<KisVolumeRankItem>> volumeRankConsumer) {
        this(config, targets, consumer, volumeRankConsumer, () -> {});
    }

    public KisQuotePoller(KisConfig config, Map<String, KisQuoteTarget> targets, Consumer<BrokerTick> consumer, Consumer<List<KisVolumeRankItem>> volumeRankConsumer, Runnable unavailableCallback) {
        this.client = new KisQuoteClient(config);
        this.fallbackTargets = new LinkedHashMap<>(targets);
        this.consumer = consumer;
        this.volumeRankConsumer = volumeRankConsumer;
        this.unavailableCallback = unavailableCallback;
        this.marketRankLimit = config.marketRankLimit;
        this.pollTargetLimit = config.pollTargetLimit;
    }

    public void start() {
        Thread thread = new Thread(this::pollLoop, "kis-quote-poller");
        thread.setDaemon(true);
        thread.start();
    }

    private void pollLoop() {
        System.out.println("한국투자증권 KIS 현재가 연동 시작: " + LocalDateTime.now());
        while (running) {
            for (KisQuoteTarget target : refreshTargets().values()) {
                pollTarget(target);
            }
        }
    }

    private synchronized Map<String, KisQuoteTarget> refreshTargets() {
        long now = System.currentTimeMillis();
        if (!activeTargets.isEmpty() && now - lastRankRefresh < 10L * 60L * 1000L) {
            return new LinkedHashMap<>(activeTargets);
        }
        try {
            List<KisVolumeRankItem> ranked = client.volumeRank(marketRankLimit);
            if (!ranked.isEmpty()) {
                volumeRankConsumer.accept(ranked);
                LinkedHashMap<String, KisQuoteTarget> nextTargets = new LinkedHashMap<>();
                ranked.stream().limit(pollTargetLimit).forEach(item -> nextTargets.putIfAbsent(item.name, item.toTarget()));
                fallbackTargets.values().forEach(target -> {
                    if (nextTargets.size() < pollTargetLimit) nextTargets.putIfAbsent(target.name, target);
                });
                activeTargets = nextTargets;
                lastRankRefresh = now;
                System.out.println("KIS 거래량 상위 종목 자동 선별: " + ranked.size() + "개 표시, " + activeTargets.size() + "개 현재가 폴링");
                return new LinkedHashMap<>(activeTargets);
            }
        } catch (Exception ex) {
            System.err.println("KIS 거래량 순위 조회 실패: " + ex.getMessage());
            notifyUnavailable();
        }
        if (activeTargets.isEmpty()) {
            fallbackTargets.values().forEach(target -> {
                if (activeTargets.size() < pollTargetLimit) activeTargets.putIfAbsent(target.name, target);
            });
        }
        return new LinkedHashMap<>(activeTargets);
    }

    private void pollTarget(KisQuoteTarget target) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                consumer.accept(client.inquirePrice(target).toTick());
                Thread.sleep(700);
                return;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                running = false;
                return;
            } catch (Exception ex) {
                if (attempt == 3) {
                    System.err.println("KIS 현재가 조회 실패(" + target.name + "): " + ex.getMessage());
                    notifyUnavailable();
                    sleep(2_000);
                    return;
                }
                sleep(600);
            }
        }
    }

    private void notifyUnavailable() {
        if (unavailableNotified) return;
        unavailableNotified = true;
        unavailableCallback.run();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
