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
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;

public class LocalFileDatabase implements ProjectDatabase {
    private final Path path;

    public LocalFileDatabase(Path path) {
        this.path = path;
    }

    public String name() {
        return "로컬 파일 저장소";
    }

    public DatabaseSnapshot load(Map<String, Stock> marketStocks) throws Exception {
        Files.createDirectories(path.getParent());
        if (!Files.exists(path)) {
            return new DatabaseSnapshot(new ConcurrentHashMap<>(), new ArrayList<>(), 1000);
        }
        Map<Long, Member> members = new ConcurrentHashMap<>();
        List<TradeLog> logs = new ArrayList<>();
        long maxUid = 1000;
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\t", -1);
            if (parts.length == 0) continue;
            if ("MEMBER".equals(parts[0]) && parts.length >= 4) {
                long uid = Long.parseLong(parts[1]);
                Member member = new Member(uid, unescape(parts[2]), copyStocks(marketStocks));
                member.balance = Integer.parseInt(parts[3]);
                members.put(uid, member);
                maxUid = Math.max(maxUid, uid);
            } else if ("SHARE".equals(parts[0]) && parts.length >= 5) {
                Member member = members.get(Long.parseLong(parts[1]));
                if (member != null) {
                    member.shares.put(unescape(parts[2]), new Share(unescape(parts[2]), Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), true));
                }
            } else if ("TRADE".equals(parts[0]) && parts.length >= 7) {
                logs.add(new TradeLog(
                        Long.parseLong(parts[1]),
                        unescape(parts[2]),
                        Integer.parseInt(parts[3]),
                        Integer.parseInt(parts[4]),
                        unescape(parts[5]),
                        LocalDateTime.parse(parts[6])
                ));
            }
        }
        return new DatabaseSnapshot(members, logs, maxUid);
    }

    public void save(Map<Long, Member> members, List<TradeLog> logs) throws Exception {
        Files.createDirectories(path.getParent());
        List<String> lines = new ArrayList<>();
        for (Member member : members.values().stream().sorted(Comparator.comparingLong(member -> member.uid)).toList()) {
            lines.add(String.join("\t", "MEMBER", String.valueOf(member.uid), escape(member.name), String.valueOf(member.balance)));
            for (Share share : member.shares.values()) {
                lines.add(String.join("\t", "SHARE", String.valueOf(member.uid), escape(share.stockName), String.valueOf(share.quantity), String.valueOf(share.purchasePrice)));
            }
        }
        for (TradeLog log : logs) {
            lines.add(String.join("\t", "TRADE", String.valueOf(log.memberUid), escape(log.stockName), String.valueOf(log.quantity), String.valueOf(log.price), escape(log.type), log.time.toString()));
        }
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    private Map<String, Stock> copyStocks(Map<String, Stock> source) {
        Map<String, Stock> copy = new ConcurrentHashMap<>();
        source.forEach((key, value) -> copy.put(key, value.copy()));
        return copy;
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private String unescape(String value) {
        return value.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\");
    }
}
