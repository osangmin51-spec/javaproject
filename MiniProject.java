import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

class MiniProject {
    private final Map<Long, Member> members = new ConcurrentHashMap<>();
    private final Map<String, Stock> marketStocks = new ConcurrentHashMap<>();
    private final List<TradeLog> logs = new ArrayList<>();
    private final AtomicLong memberIds = new AtomicLong(1000);
    private final AtomicLong brokerTicks = new AtomicLong();
    private MySqlDatabase mySqlDatabase;
    private volatile String brokerSource = "내장 모의 증권사 소켓 서버";
    private volatile LocalDateTime lastBrokerTick;

    MiniProject() {
        seedStocks();
        loadDatabase();
        if (members.isEmpty()) {
            createDefaultMember();
        }
    }

    private void createDefaultMember() {
        Member member = new Member(memberIds.incrementAndGet(), "과제용 투자자", copyStocks(marketStocks));
        members.put(member.uid, member);
        saveDatabase();
    }

    String stateJson() {
        Member member = defaultMember();
        return Json.obj(
                "ok", true,
                "member", member.toJson(),
                "broker", brokerJson(),
                "portfolio", portfolioJson(member),
                "stocks", Json.array(stocks(member).stream().map(Stock::toJson).toList()),
                "shares", Json.array(member.shares.values().stream().map(share -> shareJson(member, share)).toList()),
                "logs", Json.array(logs.stream().filter(log -> log.memberUid == member.uid).sorted(Comparator.comparing(TradeLog::time).reversed()).map(TradeLog::toJson).toList())
        );
    }

    String buyStock(Map<String, String> body) {
        Member member = defaultMember();
        String stockName = text(body, "stockName");
        int quantity = number(body, "quantity");
        Stock stock = member.stocks.get(stockName);
        if (stock == null || quantity <= 0 || stock.quantity < quantity) {
            return Json.obj("ok", false, "error", "잘못된 수량 주문입니다.");
        }
        int total = stock.price * quantity;
        if (member.balance < total) {
            return Json.obj("ok", false, "error", "잔액이 부족합니다.");
        }
        member.balance -= total;
        stock.quantity -= quantity;
        member.shares.compute(stockName, (key, share) -> share == null
                ? new Share(stockName, quantity, stock.price)
                : share.buy(quantity, total));
        logs.add(new TradeLog(member.uid, stockName, quantity, total, "구매"));
        saveDatabase();
        return Json.obj("ok", true, "message", stockName + " " + quantity + "주를 구매했습니다.");
    }

    String sellStock(Map<String, String> body) {
        Member member = defaultMember();
        String stockName = text(body, "stockName");
        int quantity = number(body, "quantity");
        Stock stock = member.stocks.get(stockName);
        Share share = member.shares.get(stockName);
        if (stock == null || share == null || quantity <= 0 || share.quantity < quantity) {
            return Json.obj("ok", false, "error", "보유 수량이 부족합니다.");
        }
        int total = stock.price * quantity;
        member.balance += total;
        stock.quantity += quantity;
        share.quantity -= quantity;
        if (share.quantity == 0) member.shares.remove(stockName);
        logs.add(new TradeLog(member.uid, stockName, quantity, total, "판매"));
        saveDatabase();
        return Json.obj("ok", true, "message", stockName + " " + quantity + "주를 판매했습니다.");
    }

    Map<String, Integer> quoteSeeds() {
        Map<String, Integer> seeds = new LinkedHashMap<>();
        stocks(null).forEach(stock -> seeds.put(stock.name, stock.price));
        return seeds;
    }

    Map<String, KisQuoteTarget> kisQuoteTargets() {
        Map<String, KisQuoteTarget> targets = new LinkedHashMap<>();
        List<String> priority = List.of("삼성전자", "SK하이닉스", "현대차", "NAVER", "카카오", "LG에너지솔루션", "삼성바이오로직스");
        priority.forEach(name -> {
            Stock stock = marketStocks.get(name);
            if (stock != null) targets.put(stock.name, new KisQuoteTarget(stock.name, stock.code));
        });
        stocks(null).forEach(stock -> targets.putIfAbsent(stock.name, new KisQuoteTarget(stock.name, stock.code)));
        return targets;
    }

    synchronized void applyKisVolumeRank(List<KisVolumeRankItem> ranked) {
        for (KisVolumeRankItem item : ranked) {
            if (item.name.isBlank() || item.code.isBlank()) continue;
            Stock stock = marketStocks.get(item.name);
            if (stock == null) {
                stock = new Stock(
                        item.code,
                        item.name,
                        "KIS",
                        "거래량 상위",
                        "한국투자증권 KIS 거래량 순위에서 자동 선별된 종목입니다.",
                        100,
                        1_000_000,
                        0,
                        1.0
                );
                stock.quoteSource = "KIS 거래량 순위";
                marketStocks.put(item.name, stock);
            }
            if (item.price > 0) {
                stock.updateExternalPrice(item.price, item.change, item.changeRate, item.volume);
            } else if (item.volume > 0) {
                stock.tradingVolume = item.volume;
            }
            stock.lastUpdated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            Stock snapshot = stock.copy();
            members.values().forEach(member -> member.stocks.merge(item.name, snapshot.copy(), (oldStock, newStock) -> {
                if (item.price > 0) {
                    oldStock.updateExternalPrice(item.price, item.change, item.changeRate, item.volume);
                }
                if (item.volume > 0) oldStock.tradingVolume = item.volume;
                oldStock.lastUpdated = snapshot.lastUpdated;
                return oldStock;
            }));
        }
    }

    void applyBrokerTick(BrokerTick tick) {
        brokerSource = "내장 모의 증권사 소켓 서버";
        lastBrokerTick = LocalDateTime.now();
        brokerTicks.incrementAndGet();
        updateStock(marketStocks.get(tick.symbol), tick);
        members.values().forEach(member -> updateStock(member.stocks.get(tick.symbol), tick));
    }

    void applyKisQuote(BrokerTick tick) {
        brokerSource = "한국투자증권 KIS REST API";
        lastBrokerTick = LocalDateTime.now();
        brokerTicks.incrementAndGet();
        updateExternalStock(marketStocks.get(tick.symbol), tick);
        members.values().forEach(member -> updateExternalStock(member.stocks.get(tick.symbol), tick));
    }

    void applyKisWebSocketQuote(BrokerTick tick) {
        brokerSource = "한국투자증권 KIS WebSocket";
        lastBrokerTick = LocalDateTime.now();
        brokerTicks.incrementAndGet();
        updateExternalStock(marketStocks.get(tick.symbol), tick);
        members.values().forEach(member -> updateExternalStock(member.stocks.get(tick.symbol), tick));
    }

    private void seedStocks() {
        addStock("005930", "삼성전자", "KOSPI", "반도체", "메모리 반도체, 스마트폰, 가전 사업을 운영하는 국내 대표 IT 기업입니다.", 72000, 1000, 2000, 1.05);
        addStock("000660", "SK하이닉스", "KOSPI", "반도체", "DRAM과 NAND 중심의 글로벌 메모리 반도체 기업입니다.", 213000, 700, 3500, 1.04);
        addStock("373220", "LG에너지솔루션", "KOSPI", "2차전지", "전기차와 에너지저장장치용 배터리를 생산하는 배터리 전문 기업입니다.", 352000, 400, -5000, 0.98);
        addStock("207940", "삼성바이오로직스", "KOSPI", "바이오", "바이오의약품 위탁개발생산 CDMO 사업을 영위합니다.", 835000, 120, 8000, 1.02);
        addStock("005380", "현대차", "KOSPI", "자동차", "완성차, 전기차, 수소차, 모빌리티 서비스를 전개하는 자동차 기업입니다.", 246000, 500, 2500, 1.01);
        addStock("000270", "기아", "KOSPI", "자동차", "승용차, SUV, 전기차를 생산하는 현대차그룹 계열 완성차 기업입니다.", 118000, 650, -1200, 0.99);
        addStock("068270", "셀트리온", "KOSPI", "바이오", "바이오시밀러와 항체 치료제 개발 및 생산 기업입니다.", 184000, 450, 1700, 1.03);
        addStock("005490", "POSCO홀딩스", "KOSPI", "철강", "철강과 2차전지 소재, 친환경 인프라 사업을 보유한 지주회사입니다.", 392000, 240, -4500, 0.97);
        addStock("035420", "NAVER", "KOSPI", "인터넷", "검색, 커머스, 콘텐츠, 클라우드 서비스를 제공하는 인터넷 플랫폼 기업입니다.", 184000, 500, 2500, 1.08);
        addStock("035720", "카카오", "KOSPI", "인터넷", "메신저, 광고, 커머스, 콘텐츠, 금융 플랫폼 사업을 운영합니다.", 52000, 900, -800, 0.91);
        addStock("012330", "현대모비스", "KOSPI", "자동차부품", "자동차 핵심 부품과 전동화 부품을 공급하는 부품 기업입니다.", 228000, 300, -4000, 0.96);
        addStock("006400", "삼성SDI", "KOSPI", "2차전지", "전기차 배터리와 전자재료 사업을 운영합니다.", 401000, 250, 4500, 1.02);
        addStock("051910", "LG화학", "KOSPI", "화학", "석유화학, 첨단소재, 생명과학 사업을 보유한 화학 기업입니다.", 365000, 250, -6500, 0.97);
        addStock("105560", "KB금융", "KOSPI", "금융", "은행, 증권, 보험, 카드 계열사를 둔 금융지주사입니다.", 82500, 1000, 900, 1.01);
        addStock("055550", "신한지주", "KOSPI", "금융", "은행과 카드, 증권, 보험 계열을 보유한 금융지주사입니다.", 52300, 1000, 500, 1.01);
        addStock("086790", "하나금융지주", "KOSPI", "금융", "은행, 증권, 카드 등 금융 서비스를 제공하는 금융지주사입니다.", 61500, 900, 650, 1.02);
        addStock("028260", "삼성물산", "KOSPI", "지주", "건설, 상사, 패션, 리조트, 바이오 지분을 보유한 복합 기업입니다.", 142000, 350, -1000, 0.99);
        addStock("066570", "LG전자", "KOSPI", "전자", "가전, TV, 전장 부품, B2B 솔루션을 제공하는 전자 기업입니다.", 98500, 700, 1200, 1.03);
        addStock("096770", "SK이노베이션", "KOSPI", "에너지", "정유, 화학, 윤활유, 배터리 관련 사업을 영위합니다.", 121000, 450, -1500, 0.98);
        addStock("003670", "포스코퓨처엠", "KOSPI", "2차전지소재", "양극재와 음극재 등 2차전지 핵심 소재를 생산합니다.", 268000, 220, 5200, 1.06);
        addStock("012450", "한화에어로스페이스", "KOSPI", "방산", "항공엔진, 방산, 우주항공 시스템 사업을 영위합니다.", 235000, 260, 3800, 1.04);
        addStock("329180", "현대중공업", "KOSPI", "조선", "선박과 해양 플랜트 건조를 담당하는 조선 기업입니다.", 136000, 320, 2100, 1.02);
        addStock("009540", "HD한국조선해양", "KOSPI", "조선", "조선 계열사를 보유한 HD현대그룹의 조선 중간지주사입니다.", 152000, 320, 1800, 1.03);
        addStock("009150", "삼성전기", "KOSPI", "전자부품", "MLCC, 카메라모듈, 패키지기판 등 전자부품을 생산합니다.", 153000, 420, 900, 1.01);
        addStock("323410", "카카오뱅크", "KOSPI", "금융", "모바일 중심의 인터넷전문은행입니다.", 24100, 1200, -300, 0.98);
        addStock("259960", "크래프톤", "KOSPI", "게임", "배틀그라운드 IP 중심의 게임 개발·서비스 기업입니다.", 287000, 210, 3500, 1.02);
        addStock("352820", "하이브", "KOSPI", "엔터테인먼트", "음악, 아티스트 매니지먼트, 팬 플랫폼 사업을 운영합니다.", 203000, 240, -2000, 0.98);
        addStock("036570", "엔씨소프트", "KOSPI", "게임", "온라인 및 모바일 게임을 개발·서비스하는 게임 기업입니다.", 186000, 210, -1700, 0.99);
        addStock("090430", "아모레퍼시픽", "KOSPI", "화장품", "화장품과 생활용품 브랜드를 보유한 뷰티 기업입니다.", 142000, 360, 2200, 1.03);
        addStock("003490", "대한항공", "KOSPI", "항공", "여객과 화물 항공 운송 서비스를 제공하는 항공사입니다.", 23800, 1400, 150, 1.01);
        addStock("051900", "LG생활건강", "KOSPI", "생활소비재", "화장품, 생활용품, 음료 사업을 운영합니다.", 356000, 180, -2500, 0.99);
        addStock("011170", "롯데케미칼", "KOSPI", "화학", "기초화학과 첨단소재 제품을 생산하는 화학 기업입니다.", 100800, 500, 1000, 1.12);
        addStock("010950", "S-Oil", "KOSPI", "정유", "정유, 윤활, 석유화학 사업을 영위하는 에너지 기업입니다.", 69200, 800, -600, 0.99);
        addStock("015760", "한국전력", "KOSPI", "전력", "전력 판매와 전력망 운영을 담당하는 공기업입니다.", 21300, 1600, 250, 1.02);
        addStock("033780", "KT&G", "KOSPI", "소비재", "담배, 건강기능식품, 부동산 관련 사업을 운영합니다.", 94200, 500, 500, 1.01);
        addStock("000810", "삼성화재", "KOSPI", "보험", "손해보험과 자동차보험 서비스를 제공하는 보험사입니다.", 368000, 220, 4000, 1.02);
        addStock("006800", "미래에셋증권", "KOSPI", "증권", "브로커리지, 자산관리, 투자은행 서비스를 제공하는 증권사입니다.", 8250, 2000, 110, 1.02);
        addStock("034020", "두산에너빌리티", "KOSPI", "에너지설비", "발전 설비, 원전, 가스터빈, 담수 설비 사업을 영위합니다.", 21800, 1500, 420, 1.04);
        addStock("086520", "에코프로", "KOSDAQ", "2차전지소재", "2차전지 소재와 환경 사업을 보유한 지주 성격의 기업입니다.", 104000, 300, -1800, 0.98);
        addStock("247540", "에코프로비엠", "KOSDAQ", "2차전지소재", "전기차 배터리용 양극재를 생산하는 소재 기업입니다.", 184000, 300, -2200, 0.98);
        addStock("316140", "우리금융지주", "KOSPI", "금융", "은행과 카드, 캐피탈 등 금융 계열사를 보유한 금융지주사입니다.", 15100, 1200, 180, 1.01);
        addStock("003550", "LG", "KOSPI", "지주", "LG그룹 계열사 지분을 보유한 지주회사입니다.", 82500, 500, -500, 0.99);
        addStock("034730", "SK", "KOSPI", "지주", "에너지, 통신, 반도체 등 SK그룹 계열사를 보유한 지주회사입니다.", 178000, 400, 1300, 1.01);
        addStock("017670", "SK텔레콤", "KOSPI", "통신", "이동통신, 미디어, 데이터센터, AI 서비스를 운영합니다.", 54200, 1000, 300, 1.01);
        addStock("030200", "KT", "KOSPI", "통신", "유무선 통신, 인터넷, 미디어, 클라우드 서비스를 제공합니다.", 38700, 1000, 250, 1.01);
        addStock("032830", "삼성생명", "KOSPI", "보험", "생명보험과 자산운용 서비스를 제공하는 보험사입니다.", 84500, 450, 700, 1.02);
        addStock("086280", "현대글로비스", "KOSPI", "물류", "완성차 물류, 해운, 유통 사업을 수행하는 물류 기업입니다.", 188000, 320, 2400, 1.02);
        addStock("018260", "삼성에스디에스", "KOSPI", "IT서비스", "IT 서비스와 물류 BPO 사업을 제공하는 삼성그룹 IT 기업입니다.", 153000, 250, -900, 0.99);
        addStock("011200", "HMM", "KOSPI", "해운", "컨테이너와 벌크 화물 운송을 담당하는 해운 기업입니다.", 18400, 1500, 220, 1.02);
        addStock("009830", "한화솔루션", "KOSPI", "화학", "케미칼, 태양광, 첨단소재 사업을 운영합니다.", 28600, 900, -250, 0.99);
        addStock("010130", "고려아연", "KOSPI", "비철금속", "아연, 연, 금, 은 등 비철금속 제련 기업입니다.", 512000, 120, 6000, 1.02);
        addStock("047050", "포스코인터내셔널", "KOSPI", "상사", "무역, 에너지, 식량 사업을 운영하는 종합상사입니다.", 53600, 700, -700, 0.99);
        addStock("005830", "DB손해보험", "KOSPI", "보험", "자동차보험과 장기보험을 중심으로 하는 손해보험사입니다.", 98600, 300, 1200, 1.02);
        addStock("071050", "한국금융지주", "KOSPI", "금융", "한국투자증권 등을 보유한 금융지주사입니다.", 73200, 450, 800, 1.02);
        addStock("138040", "메리츠금융지주", "KOSPI", "금융", "보험과 증권 중심의 금융지주사입니다.", 82400, 500, 1100, 1.02);
        addStock("024110", "기업은행", "KOSPI", "은행", "중소기업 금융에 특화된 국책은행입니다.", 14200, 1500, 120, 1.01);
        addStock("010140", "삼성중공업", "KOSPI", "조선", "선박과 해양 플랜트 건조 사업을 운영합니다.", 9820, 2000, 180, 1.03);
        addStock("042660", "한화오션", "KOSPI", "조선", "LNG선, 특수선, 해양 플랜트 사업을 영위하는 조선사입니다.", 32600, 800, 700, 1.03);
        addStock("004020", "현대제철", "KOSPI", "철강", "자동차강판과 봉형강 제품을 생산하는 철강사입니다.", 32100, 700, -200, 0.99);
        addStock("011790", "SKC", "KOSPI", "소재", "2차전지 동박과 반도체 소재 사업을 전개합니다.", 108000, 250, 2300, 1.03);
        addStock("097950", "CJ제일제당", "KOSPI", "식품", "식품과 바이오 소재 사업을 운영하는 식품 기업입니다.", 324000, 160, -1500, 0.99);
        addStock("271560", "오리온", "KOSPI", "식품", "제과와 스낵 제품을 생산·판매하는 식품 기업입니다.", 94200, 300, 600, 1.01);
        addStock("004990", "롯데지주", "KOSPI", "지주", "롯데그룹 계열사를 보유한 지주회사입니다.", 26700, 500, -100, 0.99);
        addStock("161390", "한국타이어앤테크놀로지", "KOSPI", "자동차부품", "자동차 타이어를 생산·판매하는 글로벌 타이어 기업입니다.", 42100, 650, 450, 1.02);
        addStock("000720", "현대건설", "KOSPI", "건설", "건축, 토목, 플랜트 사업을 수행하는 종합건설사입니다.", 35200, 800, 300, 1.01);
        addStock("006360", "GS건설", "KOSPI", "건설", "주택, 인프라, 플랜트 사업을 운영하는 건설사입니다.", 18200, 1000, -180, 0.99);
        addStock("028050", "삼성엔지니어링", "KOSPI", "플랜트", "화공과 산업 플랜트 설계·조달·시공 사업을 수행합니다.", 25400, 900, 220, 1.01);
        addStock("128940", "한미약품", "KOSPI", "제약", "신약 개발과 전문의약품 제조를 담당하는 제약사입니다.", 298000, 160, 3200, 1.02);
        addStock("326030", "SK바이오팜", "KOSPI", "바이오", "중추신경계 신약 개발과 상업화를 추진하는 바이오 기업입니다.", 94500, 240, -1100, 0.99);
        addStock("302440", "SK바이오사이언스", "KOSPI", "바이오", "백신 개발과 생산을 담당하는 바이오 기업입니다.", 61200, 300, 500, 1.01);
        addStock("000100", "유한양행", "KOSPI", "제약", "전문의약품과 신약 개발 파이프라인을 보유한 제약사입니다.", 76200, 400, 1200, 1.02);
        addStock("145020", "휴젤", "KOSDAQ", "바이오", "보툴리눔 톡신과 필러 등 미용의료 제품을 생산합니다.", 214000, 160, 2600, 1.02);
        addStock("196170", "알테오젠", "KOSDAQ", "바이오", "바이오베터와 플랫폼 기술을 개발하는 바이오 기업입니다.", 184000, 240, 4200, 1.04);
        addStock("091990", "셀트리온헬스케어", "KOSDAQ", "바이오", "바이오의약품 유통과 판매를 담당하는 헬스케어 기업입니다.", 67200, 400, -500, 0.99);
        addStock("293490", "카카오게임즈", "KOSDAQ", "게임", "모바일과 PC 게임 퍼블리싱·개발 사업을 운영합니다.", 21400, 600, 250, 1.02);
        addStock("112040", "위메이드", "KOSDAQ", "게임", "게임 개발과 블록체인 게임 플랫폼 사업을 운영합니다.", 42100, 450, -600, 0.98);
        addStock("078340", "컴투스", "KOSDAQ", "게임", "모바일 게임 개발과 퍼블리싱을 담당하는 게임 기업입니다.", 43800, 360, 350, 1.01);
        addStock("035900", "JYP Ent.", "KOSDAQ", "엔터테인먼트", "아티스트 매니지먼트와 음반·공연 사업을 운영합니다.", 68400, 300, 900, 1.02);
        addStock("041510", "에스엠", "KOSDAQ", "엔터테인먼트", "음악 콘텐츠와 아티스트 매니지먼트 사업을 전개합니다.", 87200, 260, -800, 0.99);
        addStock("122870", "와이지엔터테인먼트", "KOSDAQ", "엔터테인먼트", "음악 제작과 아티스트 매니지먼트를 운영합니다.", 43800, 260, 300, 1.01);
        addStock("058470", "리노공업", "KOSDAQ", "반도체장비", "반도체 테스트 소켓과 핀을 생산하는 부품 기업입니다.", 228000, 160, 3500, 1.02);
        addStock("039030", "이오테크닉스", "KOSDAQ", "반도체장비", "레이저 기반 반도체 장비를 생산합니다.", 181000, 160, 2800, 1.02);
        addStock("240810", "원익IPS", "KOSDAQ", "반도체장비", "반도체 증착·열처리 장비를 공급하는 장비 기업입니다.", 35200, 400, 600, 1.02);
        addStock("067310", "하나마이크론", "KOSDAQ", "반도체후공정", "반도체 패키징과 테스트 서비스를 제공합니다.", 21400, 600, -300, 0.99);
        addStock("108320", "LX세미콘", "KOSDAQ", "반도체", "디스플레이 구동칩 등 시스템반도체를 설계합니다.", 78200, 240, 700, 1.01);
        addStock("357780", "솔브레인", "KOSDAQ", "반도체소재", "반도체와 디스플레이 공정 소재를 생산합니다.", 286000, 140, 3600, 1.02);
        addStock("121600", "나노신소재", "KOSDAQ", "소재", "디스플레이와 2차전지 소재를 개발·생산합니다.", 126000, 220, -1300, 0.99);
        addStock("011070", "LG이노텍", "KOSPI", "전자부품", "카메라 모듈, 기판소재, 전장부품을 생산하는 전자부품 기업입니다.", 1160000, 120, -13000, 0.99);
        addStock("010120", "LS ELECTRIC", "KOSPI", "전력기기", "전력기기, 자동화 솔루션, 스마트그리드 사업을 운영합니다.", 227000, 180, -12500, 0.95);
        addStock("006260", "LS", "KOSPI", "지주", "전선, 전력기기, 소재 계열사를 보유한 LS그룹 지주회사입니다.", 410500, 150, -25000, 0.94);
        addStock("014680", "한솔케미칼", "KOSPI", "소재", "반도체와 디스플레이 공정용 화학 소재를 생산합니다.", 256500, 160, -19500, 0.93);
        addStock("086900", "메디톡스", "KOSDAQ", "바이오", "보툴리눔 톡신과 필러 등 바이오 의약품을 개발·판매합니다.", 84700, 220, -1000, 0.99);
        addStock("298380", "에이비엘바이오", "KOSDAQ", "바이오", "이중항체 기반 신약 후보물질을 개발하는 바이오 기업입니다.", 99800, 260, -6200, 0.94);
        addStock("141080", "리가켐바이오", "KOSDAQ", "바이오", "ADC 플랫폼과 항암 신약 후보물질을 개발하는 바이오 기업입니다.", 136100, 240, -9500, 0.93);
        addStock("263750", "펄어비스", "KOSDAQ", "게임", "검은사막 등 게임 IP를 개발·서비스하는 게임 기업입니다.", 40500, 420, -1800, 0.96);
        addStock("095660", "네오위즈", "KOSDAQ", "게임", "PC와 모바일 게임 개발 및 퍼블리싱 사업을 영위합니다.", 19070, 500, -640, 0.97);
        addStock("095340", "ISC", "KOSDAQ", "반도체부품", "반도체 테스트 소켓과 관련 부품을 생산합니다.", 202000, 160, -12000, 0.94);
        addStock("036930", "주성엔지니어링", "KOSDAQ", "반도체장비", "반도체, 디스플레이, 태양광 공정 장비를 생산합니다.", 210000, 220, -40500, 0.84);
        addStock("064760", "티씨케이", "KOSDAQ", "반도체소재", "반도체 공정용 고순도 흑연과 SiC 부품을 생산합니다.", 264000, 120, -10000, 0.96);
        addStock("237690", "에스티팜", "KOSDAQ", "제약", "올리고뉴클레오타이드 원료의약품과 신약 CDMO 사업을 운영합니다.", 119500, 180, -4000, 0.97);
    }

    private void addStock(String code, String name, String market, String sector, String description, int price, int quantity, int priceFluct, double nextFluct) {
        List<String> details = new ArrayList<>();
        details.add(description);
        String profileSummary = CompanyProfiles.summaryFor(name);
        if (!profileSummary.isBlank()) details.add(profileSummary);
        String riskNote = StockCategories.riskNoteFor(sector);
        if (!riskNote.isBlank()) details.add(riskNote);
        marketStocks.put(name, new Stock(code, name, market, sector, String.join(" ", details), price, quantity, priceFluct, nextFluct));
    }

    private void updateStock(Stock stock, BrokerTick tick) {
        if (stock == null) return;
        stock.updatePrice(Math.max(100, tick.price), tick.change, tick.percent, tick.volume);
    }

    private void updateExternalStock(Stock stock, BrokerTick tick) {
        if (stock == null) return;
        stock.updateExternalPrice(Math.max(100, tick.price), tick.change, tick.percent, tick.volume);
    }

    private String brokerJson() {
        return Json.obj(
                "source", brokerSource,
                "protocol", brokerProtocol(),
                "port", brokerSource.contains("소켓") ? 9090 : 0,
                "ticks", brokerTicks.get(),
                "tradeDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                "lastTick", lastBrokerTick == null ? "시세 갱신 대기" : lastBrokerTick.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
    }

    private String brokerProtocol() {
        if (brokerSource.contains("WebSocket")) return "KIS WebSocket";
        if (brokerSource.contains("REST")) return "KIS REST API";
        return "TCP SOCKET SUB ALL";
    }

    private String portfolioJson(Member member) {
        if (member == null) {
            return Json.obj("cash", 0, "stockValue", 0, "totalAsset", 0, "purchase", 0, "profit", 0, "profitRate", "0.00");
        }
        int stockValue = member.shares.values().stream().mapToInt(share -> currentPrice(member, share.stockName) * share.quantity).sum();
        int purchase = member.shares.values().stream().mapToInt(share -> share.purchasePrice).sum();
        int profit = stockValue - purchase;
        double rate = purchase == 0 ? 0.0 : profit * 100.0 / purchase;
        return Json.obj(
                "cash", member.balance,
                "stockValue", stockValue,
                "totalAsset", member.balance + stockValue,
                "purchase", purchase,
                "profit", profit,
                "profitRate", String.format(Locale.US, "%.2f", rate)
        );
    }

    private String shareJson(Member member, Share share) {
        int currentPrice = currentPrice(member, share.stockName);
        int value = currentPrice * share.quantity;
        int profit = value - share.purchasePrice;
        double rate = share.purchasePrice == 0 ? 0.0 : profit * 100.0 / share.purchasePrice;
        return share.toJson(currentPrice, value, profit, rate);
    }

    private int currentPrice(Member member, String stockName) {
        Stock stock = member == null ? marketStocks.get(stockName) : member.stocks.get(stockName);
        return stock == null ? 0 : stock.price;
    }

    private synchronized void loadDatabase() {
        try {
            mySqlDatabase = MySqlDatabase.fromEnv();
            DatabaseSnapshot snapshot = mySqlDatabase.load(marketStocks);
            members.putAll(snapshot.members);
            logs.addAll(snapshot.logs);
            memberIds.set(Math.max(memberIds.get(), snapshot.maxMemberUid));
            System.out.println("MySQL DB 로드 완료: members=" + members.size() + ", trades=" + logs.size());
        } catch (Exception ex) {
            throw new IllegalStateException("MySQL DB 초기화에 실패했습니다. MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD와 MySQL 서버 상태를 확인하세요: " + ex.getMessage(), ex);
        }
    }

    private synchronized void saveDatabase() {
        try {
            mySqlDatabase.save(members, logs);
        } catch (Exception ex) {
            throw new IllegalStateException("MySQL DB 저장에 실패했습니다: " + ex.getMessage(), ex);
        }
    }

    private Member defaultMember() {
        return members.values().stream().min(Comparator.comparingLong(member -> member.uid))
                .orElseThrow(() -> new IllegalStateException("기본 투자자 데이터가 없습니다."));
    }

    private List<Stock> stocks(Member member) {
        Comparator<Stock> popularFirst = Comparator
                .comparingLong((Stock stock) -> stock.tradingVolume)
                .reversed()
                .thenComparing(stock -> stock.name);
        if (member == null) return marketStocks.values().stream().sorted(popularFirst).toList();
        return member.stocks.values().stream().sorted(popularFirst).toList();
    }

    private Map<String, Stock> copyStocks(Map<String, Stock> source) {
        Map<String, Stock> copy = new ConcurrentHashMap<>();
        source.forEach((key, value) -> copy.put(key, value.copy()));
        return copy;
    }

    private String text(Map<String, String> body, String key) {
        return body.getOrDefault(key, "").trim();
    }

    private int number(Map<String, String> body, String key) {
        try {
            return Integer.parseInt(text(body, key));
        } catch (Exception ex) {
            return 0;
        }
    }

}
