import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

class BrokerTick {
    final String symbol;
    final int price;
    final int change;
    final double percent;
    final long volume;

    BrokerTick(String symbol, int price, int change, double percent) {
        this(symbol, price, change, percent, 0);
    }

    BrokerTick(String symbol, int price, int change, double percent, long volume) {
        this.symbol = symbol;
        this.price = price;
        this.change = change;
        this.percent = percent;
        this.volume = volume;
    }
}

class BrokerQuote {
    final String symbol;
    int price;
    int change;
    double percent;
    long volume;

    BrokerQuote(String symbol, int price) {
        this.symbol = symbol;
        this.price = price;
        this.volume = 10_000;
    }

    BrokerTick move(Random random) {
        int previous = price;
        double drift = 0.992 + random.nextDouble() * 0.016;
        price = Math.max(100, (int) Math.round(price * drift));
        change = price - previous;
        percent = previous == 0 ? 0.0 : change * 100.0 / previous;
        volume += 500 + random.nextInt(25_000);
        return new BrokerTick(symbol, price, change, percent, volume);
    }
}

class MockBrokerServer {
    private final int port;
    private final Map<String, BrokerQuote> quotes = new ConcurrentHashMap<>();
    private final CopyOnWriteArraySet<BrokerClientSession> clients = new CopyOnWriteArraySet<>();
    private final ExecutorService clientPool = Executors.newCachedThreadPool();
    private final Random random = new Random();
    private volatile boolean running;

    MockBrokerServer(int port, Map<String, Integer> seeds) {
        this.port = port;
        seeds.forEach((symbol, price) -> quotes.put(symbol, new BrokerQuote(symbol, price)));
    }

    void start() {
        running = true;
        Thread acceptThread = new Thread(this::acceptLoop, "mock-broker-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        Thread tickThread = new Thread(this::tickLoop, "mock-broker-tick");
        tickThread.setDaemon(true);
        tickThread.start();
    }

    private void acceptLoop() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (running) {
                Socket socket = serverSocket.accept();
                BrokerClientSession session = new BrokerClientSession(socket, quotes.keySet(), clients);
                clients.add(session);
                clientPool.submit(session);
            }
        } catch (IOException ex) {
            System.err.println("모의 증권사 소켓 서버 시작 실패: " + ex.getMessage());
        }
    }

    private void tickLoop() {
        while (running) {
            try {
                Thread.sleep(1000);
                quotes.values().stream()
                        .map(quote -> quote.move(random))
                        .forEach(tick -> clients.forEach(client -> client.sendTick(tick)));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }
}

class BrokerClientSession implements Runnable {
    private final Socket socket;
    private final Collection<String> symbols;
    private final CopyOnWriteArraySet<BrokerClientSession> clients;
    private final CopyOnWriteArraySet<String> subscriptions = new CopyOnWriteArraySet<>();
    private PrintWriter writer;

    BrokerClientSession(Socket socket, Collection<String> symbols, CopyOnWriteArraySet<BrokerClientSession> clients) {
        this.socket = socket;
        this.symbols = symbols;
        this.clients = clients;
    }

    @Override
    public void run() {
        try (Socket autoClose = socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(autoClose.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(autoClose.getOutputStream(), StandardCharsets.UTF_8), true)) {
            writer = out;
            out.println("HELLO|MOCK_BROKER|COMMANDS=SUB,UNSUB,LIST,QUIT");
            String line;
            while ((line = reader.readLine()) != null) {
                handle(line.trim().toUpperCase(Locale.ROOT), out);
            }
        } catch (IOException ignored) {
        } finally {
            clients.remove(this);
        }
    }

    void sendTick(BrokerTick tick) {
        PrintWriter out = writer;
        if (out == null) return;
        if (subscriptions.contains("ALL") || subscriptions.contains(tick.symbol.toUpperCase(Locale.ROOT))) {
            out.println("TICK|" + tick.symbol + "|" + tick.price + "|" + tick.change + "|" + String.format(Locale.US, "%.2f", tick.percent) + "|" + tick.volume + "|" + LocalDateTime.now());
        }
    }

    private void handle(String command, PrintWriter out) {
        command = command.replace("\uFEFF", "");
        if ("LIST".equals(command)) {
            out.println("SYMBOLS|" + String.join(",", symbols));
            return;
        }
        if (command.startsWith("SUB ")) {
            String target = command.substring(4).trim();
            subscriptions.add(target.isBlank() ? "ALL" : target);
            out.println("OK|SUB|" + target);
            return;
        }
        if (command.startsWith("UNSUB ")) {
            String target = command.substring(6).trim();
            subscriptions.remove(target);
            out.println("OK|UNSUB|" + target);
            return;
        }
        if ("QUIT".equals(command)) {
            out.println("BYE");
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            return;
        }
        out.println("ERROR|UNKNOWN_COMMAND");
    }
}

class BrokerFeedClient {
    private final String host;
    private final int port;
    private final Consumer<BrokerTick> consumer;
    private volatile boolean running = true;

    BrokerFeedClient(String host, int port, Consumer<BrokerTick> consumer) {
        this.host = host;
        this.port = port;
        this.consumer = consumer;
    }

    void start() {
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
