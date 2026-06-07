import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class MiniProjectApp {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        MiniProject project = new MiniProject();

        KisConfig kisConfig = KisConfig.fromEnv();
        if (kisConfig == null) {
            MockBrokerServer brokerServer = new MockBrokerServer(9090, project.quoteSeeds());
            brokerServer.start();
            BrokerFeedClient brokerClient = new BrokerFeedClient("127.0.0.1", 9090, project::applyBrokerTick);
            brokerClient.start();
            System.out.println("모의 증권사 소켓 서버 실행 중: tcp://127.0.0.1:9090");
        } else {
            KisQuotePoller poller = new KisQuotePoller(kisConfig, project.kisQuoteTargets(), project::applyKisQuote);
            poller.start();
            System.out.println("한국투자증권 KIS 현재가 REST API 연동 모드");
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new MiniHandler(project));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("KH 미니프로젝트 웹앱 실행 중: http://localhost:" + port);
    }
}
