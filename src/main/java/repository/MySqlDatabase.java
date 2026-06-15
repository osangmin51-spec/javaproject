package repository;

import domain.Member;
import domain.Share;
import domain.Stock;
import domain.TradeLog;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;

class MySqlConfig {
    final String url;
    final String user;
    final String password;

    MySqlConfig(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    static MySqlConfig fromEnv() {
        String url = System.getenv("MYSQL_URL");
        String user = System.getenv("MYSQL_USER");
        String password = System.getenv("MYSQL_PASSWORD");
        if (blank(url) || blank(user) || blank(password)) {
            throw new IllegalStateException("MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD 환경변수는 필수입니다. MySQL 설정 후 다시 실행하세요.");
        }
        return new MySqlConfig(url, user, password);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

public class MySqlDatabase implements ProjectDatabase {
    private final MySqlConfig config;

    public MySqlDatabase(MySqlConfig config) {
        this.config = config;
    }

    public static MySqlDatabase fromEnv() {
        return new MySqlDatabase(MySqlConfig.fromEnv());
    }

    public String name() {
        return "MySQL";
    }

    public DatabaseSnapshot load(Map<String, Stock> marketStocks) throws Exception {
        try (Connection connection = connection()) {
            initSchema(connection);
            Map<Long, Member> members = new ConcurrentHashMap<>();
            long maxUid = 1000;

            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("select uid, name, balance from members")) {
                while (rs.next()) {
                    long uid = rs.getLong("uid");
                    Member member = new Member(uid, rs.getString("name"), copyStocks(marketStocks));
                    member.balance = rs.getInt("balance");
                    members.put(uid, member);
                    maxUid = Math.max(maxUid, uid);
                }
            }

            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("select member_uid, stock_name, quantity, purchase_price from shares")) {
                while (rs.next()) {
                    Member member = members.get(rs.getLong("member_uid"));
                    if (member == null) continue;
                    String stockName = rs.getString("stock_name");
                    member.shares.put(stockName, new Share(stockName, rs.getInt("quantity"), rs.getInt("purchase_price"), true));
                }
            }

            List<TradeLog> logs = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("select member_uid, stock_name, quantity, price, trade_type, traded_at from trade_logs order by traded_at")) {
                while (rs.next()) {
                    logs.add(new TradeLog(
                            rs.getLong("member_uid"),
                            rs.getString("stock_name"),
                            rs.getInt("quantity"),
                            rs.getInt("price"),
                            rs.getString("trade_type"),
                            rs.getTimestamp("traded_at").toLocalDateTime()
                    ));
                }
            }
            return new DatabaseSnapshot(members, logs, maxUid);
        }
    }

    public void save(Map<Long, Member> members, List<TradeLog> logs) throws Exception {
        try (Connection connection = connection()) {
            initSchema(connection);
            connection.setAutoCommit(false);
            try {
                clearTables(connection);
                try (PreparedStatement ps = connection.prepareStatement("insert into members(uid, name, balance) values(?, ?, ?)")) {
                    for (Member member : members.values().stream().sorted(Comparator.comparingLong(member -> member.uid)).toList()) {
                        ps.setLong(1, member.uid);
                        ps.setString(2, member.name);
                        ps.setInt(3, member.balance);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                try (PreparedStatement ps = connection.prepareStatement("insert into shares(member_uid, stock_name, quantity, purchase_price) values(?, ?, ?, ?)")) {
                    for (Member member : members.values()) {
                        for (Share share : member.shares.values()) {
                            ps.setLong(1, member.uid);
                            ps.setString(2, share.stockName);
                            ps.setInt(3, share.quantity);
                            ps.setInt(4, share.purchasePrice);
                            ps.addBatch();
                        }
                    }
                    ps.executeBatch();
                }

                try (PreparedStatement ps = connection.prepareStatement("insert into trade_logs(member_uid, stock_name, quantity, price, trade_type, traded_at) values(?, ?, ?, ?, ?, ?)")) {
                    for (TradeLog log : logs) {
                        ps.setLong(1, log.memberUid);
                        ps.setString(2, log.stockName);
                        ps.setInt(3, log.quantity);
                        ps.setInt(4, log.price);
                        ps.setString(5, log.type);
                        ps.setTimestamp(6, java.sql.Timestamp.valueOf(log.time));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(config.url, config.user, config.password);
    }

    private void initSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    create table if not exists members (
                        uid bigint primary key,
                        name varchar(100) not null,
                        balance int not null
                    )
                    """);
            dropColumnIfPresent(connection, "members", "login_id");
            dropColumnIfPresent(connection, "members", "pwd");
            statement.executeUpdate("""
                    create table if not exists shares (
                        member_uid bigint not null,
                        stock_name varchar(120) not null,
                        quantity int not null,
                        purchase_price int not null,
                        primary key(member_uid, stock_name),
                        constraint fk_shares_member
                            foreign key(member_uid) references members(uid)
                            on delete cascade
                    )
                    """);
            statement.executeUpdate("""
                    create table if not exists trade_logs (
                        id bigint primary key auto_increment,
                        member_uid bigint not null,
                        stock_name varchar(120) not null,
                        quantity int not null,
                        price int not null,
                        trade_type varchar(20) not null,
                        traded_at datetime not null,
                        index idx_trade_logs_member_time(member_uid, traded_at),
                        constraint fk_trade_logs_member
                            foreign key(member_uid) references members(uid)
                            on delete cascade
                    )
                    """);
        }
    }

    private void clearTables(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("delete from trade_logs");
            statement.executeUpdate("delete from shares");
            statement.executeUpdate("delete from members");
        }
    }

    private void dropColumnIfPresent(Connection connection, String tableName, String columnName) throws Exception {
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            if (!rs.next()) return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("alter table " + tableName + " drop column " + columnName);
        }
    }

    private Map<String, Stock> copyStocks(Map<String, Stock> source) {
        Map<String, Stock> copy = new ConcurrentHashMap<>();
        source.forEach((key, value) -> copy.put(key, value.copy()));
        return copy;
    }
}
