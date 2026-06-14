import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MiniProjectApp {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        MiniProject project = new MiniProject();
        AtomicBoolean mockBrokerStarted = new AtomicBoolean(false);
        Runnable mockFallback = () -> startMockBroker(project, mockBrokerStarted, "KIS 연결이 불가능해 내장 모의 증권사 소켓으로 전환합니다.");

        KisConfig kisConfig = KisConfig.fromEnv();
        if (kisConfig == null) {
            startMockBroker(project, mockBrokerStarted, "KIS 환경변수가 없어 내장 모의 증권사 소켓을 사용합니다.");
        } else {
            if (kisConfig.webSocketEnabled) {
                KisWebSocketQuoteClient webSocketClient = new KisWebSocketQuoteClient(kisConfig, project.kisQuoteTargets(), project::applyKisWebSocketQuote, project::applyKisVolumeRank);
                if (webSocketClient.start()) {
                    System.out.println("한국투자증권 KIS WebSocket 실시간 체결 구독 모드");
                } else {
                    startRestPoller(kisConfig, project, mockFallback);
                }
            } else {
                startRestPoller(kisConfig, project, mockFallback);
            }
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new MiniHandler(project));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("KH 미니프로젝트 웹앱 실행 중: http://localhost:" + port);
        new CountDownLatch(1).await();
    }

    private static void startRestPoller(KisConfig kisConfig, MiniProject project, Runnable unavailableCallback) {
        KisQuotePoller poller = new KisQuotePoller(kisConfig, project.kisQuoteTargets(), project::applyKisQuote, project::applyKisVolumeRank, unavailableCallback);
        poller.start();
        System.out.println("한국투자증권 KIS 현재가 REST API 연동 모드");
    }

    private static void startMockBroker(MiniProject project, AtomicBoolean started, String reason) {
        if (!started.compareAndSet(false, true)) return;
        MockBrokerServer brokerServer = new MockBrokerServer(9090, project.quoteSeeds());
        brokerServer.start();
        BrokerFeedClient brokerClient = new BrokerFeedClient("127.0.0.1", 9090, project::applyBrokerTick);
        brokerClient.start();
        System.out.println(reason);
        System.out.println("모의 증권사 소켓 서버 실행 중: tcp://127.0.0.1:9090");
    }
}
