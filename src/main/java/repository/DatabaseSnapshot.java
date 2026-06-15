package repository;

import domain.Member;
import domain.TradeLog;
import java.util.List;
import java.util.Map;

public class DatabaseSnapshot {
    public final Map<Long, Member> members;
    public final List<TradeLog> logs;
    public final long maxMemberUid;

    public DatabaseSnapshot(Map<Long, Member> members, List<TradeLog> logs, long maxMemberUid) {
        this.members = members;
        this.logs = logs;
        this.maxMemberUid = maxMemberUid;
    }
}
