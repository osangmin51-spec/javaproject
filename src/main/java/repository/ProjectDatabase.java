package repository;

import domain.Member;
import domain.Stock;
import domain.TradeLog;
import java.util.List;
import java.util.Map;

public interface ProjectDatabase {
    DatabaseSnapshot load(Map<String, Stock> marketStocks) throws Exception;

    void save(Map<Long, Member> members, List<TradeLog> logs) throws Exception;

    String name();
}
