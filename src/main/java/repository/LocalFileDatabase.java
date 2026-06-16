package repository;

import domain.Member;
import domain.Share;
import domain.Stock;
import domain.TradeLog;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LocalFileDatabase implements ProjectDatabase {
    private final Path path;

    public LocalFileDatabase(Path path) {
        this.path = path;
    }

    public static LocalFileDatabase defaultPath() {
        return new LocalFileDatabase(Path.of("data", "local-database.tsv"));
    }

    public String name() {
        return "Local TSV";
    }

    public DatabaseSnapshot load(Map<String, Stock> marketStocks) throws Exception {
        Map<Long, Member> members = new ConcurrentHashMap<>();
        List<TradeLog> logs = new ArrayList<>();
        long maxUid = 1000;
        if (!Files.exists(path)) {
            return new DatabaseSnapshot(members, logs, maxUid);
        }

        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] parts = line.split("\t", -1);
            if (parts.length == 0) continue;
            switch (parts[0]) {
                case "M" -> {
                    if (parts.length < 4) continue;
                    long uid = Long.parseLong(parts[1]);
                    Member member = new Member(uid, decode(parts[2]), copyStocks(marketStocks));
                    member.balance = Integer.parseInt(parts[3]);
                    members.put(uid, member);
                    maxUid = Math.max(maxUid, uid);
                }
                case "S" -> {
                    if (parts.length < 5) continue;
                    Member member = members.get(Long.parseLong(parts[1]));
                    if (member == null) continue;
                    member.shares.put(decode(parts[2]), new Share(decode(parts[2]), Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), true));
                }
                case "T" -> {
                    if (parts.length < 7) continue;
                    logs.add(new TradeLog(
                            Long.parseLong(parts[1]),
                            decode(parts[2]),
                            Integer.parseInt(parts[3]),
                            Integer.parseInt(parts[4]),
                            decode(parts[5]),
                            LocalDateTime.parse(parts[6])
                    ));
                }
                default -> {
                }
            }
        }
        return new DatabaseSnapshot(members, logs, maxUid);
    }

    public void save(Map<Long, Member> members, List<TradeLog> logs) throws Exception {
        Files.createDirectories(path.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("# type\tmember_uid\tname_or_stock\tquantity_or_balance\tpurchase_or_price\ttype\ttime");
        for (Member member : members.values().stream().sorted(Comparator.comparingLong(member -> member.uid)).toList()) {
            lines.add(String.join("\t", "M", String.valueOf(member.uid), encode(member.name), String.valueOf(member.balance)));
            for (Share share : member.shares.values().stream().sorted(Comparator.comparing(share -> share.stockName)).toList()) {
                lines.add(String.join("\t",
                        "S",
                        String.valueOf(member.uid),
                        encode(share.stockName),
                        String.valueOf(share.quantity),
                        String.valueOf(share.purchasePrice)
                ));
            }
        }
        for (TradeLog log : logs.stream().sorted(Comparator.comparing(log -> log.time)).toList()) {
            lines.add(String.join("\t",
                    "T",
                    String.valueOf(log.memberUid),
                    encode(log.stockName),
                    String.valueOf(log.quantity),
                    String.valueOf(log.price),
                    encode(log.type),
                    log.time.toString()
            ));
        }
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    private Map<String, Stock> copyStocks(Map<String, Stock> source) {
        Map<String, Stock> copy = new ConcurrentHashMap<>();
        source.forEach((key, value) -> copy.put(key, value.copy()));
        return copy;
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
