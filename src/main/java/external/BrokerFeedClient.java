package external;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class BrokerFeedClient {
    private final String host;
    private final int port;
    private final Consumer<BrokerTick> consumer;
    private volatile boolean running = true;

    public BrokerFeedClient(String host, int port, Consumer<BrokerTick> consumer) {
        this.host = host;
        this.port = port;
        this.consumer = consumer;
    }

    public void start() {
        Thread thread = new Thread(this::connectLoop, "broker-feed-client");
        thread.setDaemon(true);
        thread.start();
    }

    private void connectLoop() {
        while (running) {
            try (Socket socket = new Socket(host, port);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
                writer.println("SUB ALL");
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("TICK|")) {
                        parseTick(line);
                    }
                }
            } catch (IOException ex) {
                sleep(1000);
            }
        }
    }

    private void parseTick(String line) {
        String[] cols = line.split("\\|");
        if (cols.length < 5) return;
        try {
            long volume = cols.length > 5 ? Long.parseLong(cols[5]) : 0;
            consumer.accept(new BrokerTick(cols[1], Integer.parseInt(cols[2]), Integer.parseInt(cols[3]), Double.parseDouble(cols[4]), volume));
        } catch (NumberFormatException ignored) {
        }
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
