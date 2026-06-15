package external;

import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.URI;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import util.Json;

public class KisWebSocketQuoteClient implements WebSocket.Listener {
    private final KisQuoteClient quoteClient;
    private final KisConfig config;
    private final Map<String, KisQuoteTarget> targets;
    private final Consumer<BrokerTick> consumer;
    private final Consumer<List<KisVolumeRankItem>> volumeRankConsumer;
    private final Runnable unavailableCallback;
    private final CountDownLatch openLatch = new CountDownLatch(1);
    private final StringBuilder buffer = new StringBuilder();
    private volatile WebSocket webSocket;

    public KisWebSocketQuoteClient(KisConfig config, Map<String, KisQuoteTarget> targets, Consumer<BrokerTick> consumer) {
        this(config, targets, consumer, ranks -> {});
    }

    public KisWebSocketQuoteClient(KisConfig config, Map<String, KisQuoteTarget> targets, Consumer<BrokerTick> consumer, Consumer<List<KisVolumeRankItem>> volumeRankConsumer) {
        this(config, targets, consumer, volumeRankConsumer, () -> {});
    }

    public KisWebSocketQuoteClient(KisConfig config, Map<String, KisQuoteTarget> targets, Consumer<BrokerTick> consumer, Consumer<List<KisVolumeRankItem>> volumeRankConsumer, Runnable unavailableCallback) {
        this.config = config;
        this.targets = new LinkedHashMap<>(targets);
        this.consumer = consumer;
        this.volumeRankConsumer = volumeRankConsumer;
        this.unavailableCallback = unavailableCallback;
        this.quoteClient = new KisQuoteClient(config);
    }

    public boolean start() {
        try {
            refreshVolumeRankTargets();
            String approvalKey = quoteClient.approvalKey();
            webSocket = HttpClient.newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(URI.create(config.webSocketUrl), this)
                    .join();
            if (!openLatch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("WebSocket 연결 시간이 초과되었습니다.");
            }
            subscribe(approvalKey);
            return true;
        } catch (Exception ex) {
            System.err.println("KIS WebSocket 시작 실패: " + ex.getMessage());
            return false;
        }
    }

    private void refreshVolumeRankTargets() {
        try {
            List<KisVolumeRankItem> ranked = quoteClient.volumeRank(config.marketRankLimit);
            if (ranked.isEmpty()) return;
            volumeRankConsumer.accept(ranked);
            targets.clear();
            for (KisVolumeRankItem item : ranked) {
                if (targets.size() >= config.pollTargetLimit) break;
                targets.putIfAbsent(item.name, item.toTarget());
            }
            System.out.println("KIS WebSocket 구독 대상 거래량 상위 자동 선별: " + targets.size() + "개");
        } catch (Exception ex) {
            System.err.println("KIS WebSocket 거래량 순위 선별 실패, 기존 종목으로 구독 시도: " + ex.getMessage());
        }
    }

    private void subscribe(String approvalKey) {
        int limit = Math.min(config.webSocketMaxSubscriptions, targets.size());
        targets.values().stream().limit(limit).forEach(target -> {
            String message = Json.obj(
                    "header", Json.obj(
                            "approval_key", approvalKey,
                            "custtype", "P",
                            "tr_type", "1",
                            "content-type", "utf-8"
                    ),
                    "body", Json.obj(
                            "input", Json.obj(
                                    "tr_id", "H0STCNT0",
                                    "tr_key", target.code
                            )
                    )
            );
            webSocket.sendText(message, true);
        });
        System.out.println("KIS WebSocket 국내주식 체결 구독 시도: " + limit + "개 종목");
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        WebSocket.Listener.super.onOpen(webSocket);
        openLatch.countDown();
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        buffer.append(data);
        if (last) {
            handleMessage(buffer.toString());
            buffer.setLength(0);
        }
        webSocket.request(1);
        return WebSocket.Listener.super.onText(webSocket, data, last);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        System.err.println("KIS WebSocket 오류: " + error.getMessage());
        unavailableCallback.run();
    }

    private void handleMessage(String message) {
        BrokerTick tick = parseTick(message);
        if (tick != null) {
            consumer.accept(tick);
        }
    }

    private BrokerTick parseTick(String message) {
        try {
            if (!message.contains("H0STCNT0")) return null;
            String[] blocks = message.split("\\|", -1);
            if (blocks.length < 4) return null;
            String payload = blocks[3];
            String[] values = payload.split("\\^", -1);
            if (values.length < 15) return null;
            String code = values[0];
            KisQuoteTarget target = targets.values().stream().filter(item -> item.code.equals(code)).findFirst().orElse(null);
            if (target == null) return null;
            int price = parseInt(values[2]);
            int change = values.length > 5 ? parseInt(values[5]) : 0;
            double rate = values.length > 5 && price - change != 0 ? change * 100.0 / (price - change) : 0.0;
            long volume = values.length > 13 ? parseLong(values[13]) : 0;
            if (price <= 0) return null;
            return new BrokerTick(target.name, price, change, rate, volume);
        } catch (Exception ex) {
            return null;
        }
    }

    private int parseInt(String value) {
        if (value == null || value.isBlank()) return 0;
        return Integer.parseInt(value.replace(",", "").trim());
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) return 0L;
        return Long.parseLong(value.replace(",", "").trim());
    }
}
