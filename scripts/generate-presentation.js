const fs = require("fs");
const path = require("path");
const PptxGenJS = require("pptxgenjs");

const ROOT = process.cwd();
const OUT = path.join(ROOT, "deliverables");
const VIDEO = path.join(OUT, "mock-stock-website-demo.webm");
const PREVIEW = path.join(OUT, "mock-stock-website-demo-preview.png");
const UI_MARKET = path.join(OUT, "ui-screenshots", "ui-crop-01-market.png");
const UI_DETAIL = path.join(OUT, "ui-screenshots", "ui-crop-02-detail-order.png");
const UI_PORTFOLIO = path.join(OUT, "ui-screenshots", "ui-crop-03-portfolio.png");
const UI_SEARCH = path.join(OUT, "ui-screenshots", "ui-crop-04-search-favorite.png");
fs.mkdirSync(OUT, { recursive: true });

const pptx = new PptxGenJS();
pptx.author = "Java 모의주식투자 프로젝트";
pptx.subject = "개인 프로젝트 발표 자료";
pptx.title = "Java 프로젝트 모의주식";
pptx.company = "개인 프로젝트";
pptx.lang = "ko-KR";
pptx.defineLayout({ name: "CUSTOM_WIDE", width: 13.333, height: 7.5 });
pptx.layout = "CUSTOM_WIDE";
pptx.theme = {
  headFontFace: "Malgun Gothic",
  bodyFontFace: "Malgun Gothic",
  lang: "ko-KR",
};

const C = {
  navy: "122033",
  blue: "1F5FBF",
  lightBlue: "EAF2FF",
  green: "0B7F55",
  red: "BD3D3A",
  gray: "667085",
  line: "D7DEEA",
  pale: "F6F9FD",
  white: "FFFFFF",
  ink: "182230",
};

function addHeader(slide, no, title) {
  slide.addText(String(no).padStart(2, "0"), {
    x: 0.35, y: 0.2, w: 0.45, h: 0.25,
    fontFace: "Malgun Gothic", fontSize: 8, bold: true, color: C.blue,
    margin: 0,
  });
  slide.addText(title, {
    x: 0.9, y: 0.15, w: 9.8, h: 0.35,
    fontFace: "Malgun Gothic", fontSize: 16, bold: true, color: C.ink,
    margin: 0,
  });
  slide.addShape(pptx.ShapeType.line, {
    x: 0.35, y: 0.65, w: 12.6, h: 0,
    line: { color: C.line, width: 1 },
  });
}

function addFooter(slide) {
  // 발표 화면 아래쪽에 반복 문구가 보이지 않도록 비워 둔다.
}

function titleSlide() {
  const slide = pptx.addSlide();
  slide.background = { color: C.navy };
  slide.addShape(pptx.ShapeType.rect, {
    x: 0, y: 0, w: 13.333, h: 7.5,
    fill: { color: C.navy }, line: { color: C.navy },
  });
  slide.addShape(pptx.ShapeType.rect, {
    x: 0.65, y: 0.6, w: 0.08, h: 5.9,
    fill: { color: C.blue }, line: { color: C.blue },
  });
  slide.addText("개인 프로젝트 발표", {
    x: 0.95, y: 0.8, w: 4.2, h: 0.35,
    fontSize: 13, color: "B9D2FF", bold: true, margin: 0,
  });
  slide.addText("Java 프로젝트 모의주식", {
    x: 0.9, y: 1.55, w: 9.8, h: 1.2,
    fontSize: 36, bold: true, color: C.white, fit: "shrink",
    margin: 0.02,
  });
  slide.addText("KIS Open API 기반 모의주식투자 웹앱", {
    x: 0.95, y: 2.9, w: 8.8, h: 0.65,
    fontSize: 17, color: "D7E5FF", margin: 0.02,
  });
  slide.addShape(pptx.ShapeType.roundRect, {
    x: 0.95, y: 4.35, w: 4.1, h: 0.58,
    rectRadius: 0.06, fill: { color: C.blue }, line: { color: C.blue },
  });
  slide.addText("KIS Open API · MySQL · Java HttpServer", {
    x: 1.15, y: 4.5, w: 3.7, h: 0.24,
    fontSize: 11, bold: true, color: C.white, margin: 0,
  });
  slide.addShape(pptx.ShapeType.rect, {
    x: 8.2, y: 1.0, w: 4.2, h: 4.7,
    fill: { color: "182B45", transparency: 10 }, line: { color: "28415F" },
  });
  ["시장 시세", "종목 상세", "검색/즐겨찾기", "매수/매도", "포트폴리오"].forEach((text, i) => {
    slide.addShape(pptx.ShapeType.roundRect, {
      x: 8.55, y: 1.25 + i * 0.72, w: 3.45, h: 0.46,
      rectRadius: 0.05,
      fill: { color: i === 1 ? C.blue : "243A58" },
      line: { color: "38516F" },
    });
    slide.addText(text, {
      x: 8.78, y: 1.37 + i * 0.72, w: 2.8, h: 0.18,
      fontSize: 10.5, bold: true, color: C.white, margin: 0,
    });
  });
  slide.addText("실제 시세를 보고\n모의 매매를 연습하는\nJava 웹앱", {
    x: 8.55, y: 5.08, w: 3.6, h: 0.7,
    fontSize: 15, bold: true, color: "D7E5FF",
    margin: 0.02,
  });
}

function card(slide, x, y, w, h, title, body, fill = C.white) {
  slide.addShape(pptx.ShapeType.roundRect, {
    x, y, w, h, rectRadius: 0.06,
    fill: { color: fill }, line: { color: C.line, width: 1 },
  });
  slide.addText(title, {
    x: x + 0.18, y: y + 0.16, w: w - 0.36, h: 0.26,
    fontSize: 12, bold: true, color: C.blue, margin: 0,
  });
  slide.addText(body, {
    x: x + 0.18, y: y + 0.52, w: w - 0.36, h: h - 0.65,
    fontSize: 10.7, color: C.ink, fit: "shrink", breakLine: false,
    margin: 0.03,
  });
}

function bullets(slide, items, x, y, w, h, fontSize = 13.5) {
  slide.addText(items.map((text) => ({ text, options: { bullet: { type: "ul" } } })), {
    x, y, w, h,
    fontFace: "Malgun Gothic", fontSize, color: C.ink,
    paraSpaceAfterPt: 6, fit: "shrink", breakLine: false,
  });
}

function codeBox(slide, title, code, x, y, w, h) {
  slide.addText(title, {
    x, y, w, h: 0.24,
    fontFace: "Malgun Gothic", fontSize: 10.5, bold: true, color: C.blue,
    margin: 0,
  });
  slide.addShape(pptx.ShapeType.roundRect, {
    x, y: y + 0.32, w, h: h - 0.32, rectRadius: 0.04,
    fill: { color: "111827" }, line: { color: "334155", width: 0.8 },
  });
  slide.addText(code, {
    x: x + 0.15, y: y + 0.46, w: w - 0.3, h: h - 0.58,
    fontFace: "Consolas", fontSize: 6.9, color: "E5E7EB",
    fit: "shrink", breakLine: false, margin: 0,
  });
}

function table(slide, rows, x, y, w, h, colW) {
  const rowH = h / rows.length;
  rows.forEach((row, r) => {
    let cx = x;
    row.forEach((cell, c) => {
      const cw = colW[c] * w;
      slide.addShape(pptx.ShapeType.rect, {
        x: cx, y: y + r * rowH, w: cw, h: rowH,
        fill: { color: r === 0 ? C.navy : (r % 2 ? C.white : C.pale) },
        line: { color: C.line, width: 0.6 },
      });
      slide.addText(cell, {
        x: cx + 0.08, y: y + r * rowH + 0.08,
        w: cw - 0.16, h: rowH - 0.14,
        fontSize: r === 0 ? 10 : 9.2, bold: r === 0,
        color: r === 0 ? C.white : C.ink,
        fit: "shrink", breakLine: false, margin: 0,
      });
      cx += cw;
    });
  });
}

function connector(slide, x1, y1, x2, y2) {
  slide.addShape(pptx.ShapeType.line, {
    x: x1, y: y1, w: x2 - x1, h: y2 - y1,
    line: { color: C.blue, width: 2, endArrowType: "triangle" },
  });
}

titleSlide();

{
  const slide = pptx.addSlide(); addHeader(slide, 2, "제안 단계 vs 최종 구현"); addFooter(slide);
  table(slide, [
    ["구분", "제안 단계", "최종 구현"],
    ["주제", "Java 미니프로젝트 기능 구현", "KIS API 기반 모의주식투자 웹앱"],
    ["가격 데이터", "내부 샘플/모의 가격", "한국투자증권 KIS 현재가와 거래량"],
    ["저장", "메모리 중심 구상", "MySQL 우선 + TSV fallback"],
    ["화면", "기본 목록과 입력 화면", "종목 상세, 그래프, 포트폴리오"],
    ["매매 흐름", "드롭다운 선택 후 주문", "종목 클릭 후 상세 확인과 매매"],
  ], 0.75, 1.0, 11.85, 4.75, [0.2, 0.38, 0.42]);
  slide.addText("처음에는 기능 구현 중심이었지만, 최종본은 실제 시세와 저장 구조를 갖춘 모의투자 흐름으로 바뀌었다.", {
    x: 0.95, y: 6.05, w: 11.1, h: 0.35,
    fontSize: 13.3, bold: true, color: C.blue, margin: 0,
  });
}

{
  const slide = pptx.addSlide(); addHeader(slide, 3, "전체 시스템 구조"); addFooter(slide);
  const boxes = [
    ["웹 UI", 0.8, 1.3], ["MiniHandler", 3.0, 1.3], ["MiniProject", 5.3, 1.3],
    ["MySqlDatabase", 7.4, 1.3], ["LocalFileDatabase", 9.65, 1.3], ["KisQuotePoller", 3.0, 3.6], ["KisQuoteClient", 5.3, 3.6],
    ["KIS Open API", 7.75, 3.6], ["MockBrokerServer", 5.3, 5.3], ["가격 Tick", 7.75, 5.3],
    ["BrokerFeedClient", 10.0, 3.6],
  ];
  boxes.forEach(([text, x, y]) => {
    slide.addShape(pptx.ShapeType.roundRect, {
      x, y, w: 1.8, h: 0.7, rectRadius: 0.05,
      fill: { color: text.includes("API") ? C.lightBlue : C.white },
      line: { color: C.blue, width: 1.2 },
    });
    slide.addText(text, {
      x: x + 0.08, y: y + 0.22, w: 1.64, h: 0.2,
      fontSize: 10.3, bold: true, align: "center", color: C.ink, margin: 0,
    });
  });
  connector(slide, 2.6, 1.65, 3.0, 1.65); connector(slide, 4.8, 1.65, 5.3, 1.65); connector(slide, 7.1, 1.65, 7.75, 1.65);
  connector(slide, 4.8, 3.95, 5.3, 3.95); connector(slide, 7.1, 3.95, 7.75, 3.95); connector(slide, 7.1, 5.65, 7.75, 5.65);
  connector(slide, 9.55, 3.95, 10.0, 3.95);
  slide.addText("브라우저는 /api/state를 주기적으로 호출하고, 서버는 백그라운드 Thread에서 시세와 포트폴리오 상태를 갱신한다.", {
    x: 0.9, y: 6.55, w: 11.4, h: 0.25,
    fontSize: 12.5, color: C.gray, margin: 0,
  });
}

{
  const slide = pptx.addSlide(); addHeader(slide, 4, "패키지 구조 다이어그램"); addFooter(slide);
  const boxes = [
    ["app", "MiniProjectApp\n서버 시작, 포트 설정", 0.75, 1.0, C.lightBlue],
    ["controller", "MiniHandler\nHTTP 라우팅", 3.05, 1.0, C.white],
    ["service", "MiniProject\n매매, 포트폴리오, 시세 상태", 5.35, 1.0, C.lightBlue],
    ["repository", "MySqlDatabase\nLocalFileDatabase\n계좌·보유·거래 저장", 2.0, 3.05, C.white],
    ["external", "KisQuoteClient / Poller\nKIS 현재가·거래량", 5.0, 3.05, C.white],
    ["view", "MiniDashboardPage\nHTML/CSS/JS 생성", 8.0, 3.05, C.white],
    ["domain", "Member / Stock / Share\nTradeLog", 3.5, 5.05, C.pale],
    ["util", "Json\n응답 생성·body 파싱", 6.5, 5.05, C.pale],
  ];
  boxes.forEach(([title, body, x, y, fill]) => card(slide, x, y, 2.1, 1.1, title, body, fill));
  connector(slide, 2.85, 1.55, 3.05, 1.55);
  connector(slide, 5.15, 1.55, 5.35, 1.55);
  connector(slide, 6.4, 2.1, 3.05, 3.05);
  connector(slide, 6.4, 2.1, 6.05, 3.05);
  connector(slide, 6.4, 2.1, 8.5, 3.05);
  connector(slide, 5.95, 4.15, 4.45, 5.05);
  connector(slide, 6.15, 4.15, 7.0, 5.05);
  slide.addText("요청 흐름: Browser → controller → service → repository/external → JSON 응답 → view 갱신", {
    x: 0.85, y: 6.55, w: 11.5, h: 0.35,
    fontSize: 12.5, bold: true, color: C.blue, margin: 0,
  });
}

{
  const slide = pptx.addSlide(); addHeader(slide, 5, "\uD074\uB798\uC2A4/\uB77C\uC774\uBE0C\uB7EC\uB9AC \uAD6C\uC870 \uC0C1\uC138 / Class Detail"); addFooter(slide);
  table(slide, [
    ["\uACC4\uCE35", "\uC8FC\uC694 \uD074\uB798\uC2A4", "Java \uAE30\uB2A5", "\uC5ED\uD560"],
    ["\uC2E4\uD589/\uC11C\uBC84", "MiniProjectApp, MiniHandler", "HttpServer, HttpExchange", "\uC6F9 \uC11C\uBC84 \uC2DC\uC791\uACFC API \uB77C\uC6B0\uD305"],
    ["\uC11C\uBE44\uC2A4", "MiniProject", "ConcurrentHashMap, ArrayList", "\uB9E4\uC218/\uB9E4\uB3C4, \uD3EC\uD2B8\uD3F4\uB9AC\uC624, \uC2DC\uC138 \uC0C1\uD0DC"],
    ["\uC800\uC7A5\uC18C", "MySqlDatabase, LocalFileDatabase", "JDBC, Files, Path", "MySQL \uC6B0\uC120 + TSV fallback"],
    ["\uC678\uBD80 API", "KisQuoteClient, KisQuotePoller", "HttpClient, Thread", "KIS \uD604\uC7AC\uAC00\uC640 \uAC70\uB798\uB7C9 \uC21C\uC704 \uAC31\uC2E0"],
    ["\uD654\uBA74", "MiniDashboardPage", "String template, JavaScript", "\uAC80\uC0C9, \uC990\uACA8\uCC3E\uAE30, \uADF8\uB798\uD504, \uC8FC\uBB38 UI"],
  ], 0.55, 0.95, 12.25, 4.2, [0.16, 0.28, 0.25, 0.31]);
  slide.addText("\uD074\uB798\uC2A4\uAC00 \uB9CE\uC544 \uBCF4\uC774\uC9C0\uB9CC \uBC1C\uD45C\uC5D0\uC11C\uB294 \uACC4\uCE35\uBCC4 \uCC45\uC784\uC744 \uC911\uC2EC\uC73C\uB85C \uC124\uBA85\uD55C\uB2E4.", {
    x: 0.85, y: 5.75, w: 11.4, h: 0.35,
    fontSize: 12.8, bold: true, color: C.blue, margin: 0,
  });
}

{
  const slide = pptx.addSlide(); addHeader(slide, 6, "\uC8FC\uC694 \uCF54\uB4DC \uD750\uB984 / Code Flow"); addFooter(slide);
  codeBox(slide, "MiniProject.buyStock", `int total = stock.price * quantity;
if (member.balance < total) return error;
member.balance -= total;
member.shares.compute(stockName, (key, share) ->
    share == null ? new Share(stockName, quantity, stock.price)
                  : share.buy(quantity, total));
logs.add(new TradeLog(member.uid, stockName, quantity, total, "BUY"));
saveDatabase();`, 0.55, 0.9, 4.05, 2.55);
  codeBox(slide, "KisQuoteClient", `GET /uapi/domestic-stock/v1/quotations/inquire-price
tr_id: FHKST01010100
price = output.stck_prpr
change = output.prdy_vrss
volume = output.acml_vol`, 4.85, 0.9, 3.85, 2.55);
  codeBox(slide, "MiniProject.loadDatabase", `try {
    database = MySqlDatabase.fromEnv();
    snapshot = database.load(marketStocks);
} catch (Exception ex) {
    database = LocalFileDatabase.defaultPath();
    snapshot = database.load(marketStocks);
}`, 8.95, 0.9, 3.85, 2.55);
  card(slide, 0.75, 4.15, 3.55, 1.15, "\uC8FC\uBB38", "\uD604\uC7AC\uAC00\u00D7\uC218\uB7C9 \uACC4\uC0B0 \uD6C4 \uC794\uC561\uACFC \uBCF4\uC720 \uC218\uB7C9 \uAC80\uC99D", C.lightBlue);
  card(slide, 4.9, 4.15, 3.55, 1.15, "\uC2DC\uC138", "\uAC70\uB798\uB7C9 \uC21C\uC704\uB294 \uC120\uBCC4\uC6A9, \uD45C\uC2DC \uAC00\uACA9\uC740 \uD604\uC7AC\uAC00 API \uAC12", C.white);
  card(slide, 9.05, 4.15, 3.55, 1.15, "\uC800\uC7A5", "MySQL \uC2E4\uD328 \uC2DC LocalFileDatabase\uAC00 TSV\uB85C \uBCF4\uC874", C.white);
}

{
  const slide = pptx.addSlide(); addHeader(slide, 7, "데이터 흐름"); addFooter(slide);
  table(slide, [
    ["단계", "내용"],
    ["입력", "종목 선택, 즐겨찾기, 매수/매도 수량"],
    ["외부 입력", "KIS 현재가/등락률/거래량"],
    ["처리", "잔액 확인, 보유 수량 확인, 매매 체결, 손익 계산"],
    ["저장", "members(uid, name, balance) / shares / trade_logs"],
    ["주요 컬럼", "purchase_price, trade_type, traded_at"],
    ["출력", "시장 시세, 종목 상세, 그래프, 포트폴리오, 거래 기록"],
  ], 1.0, 1.1, 11.3, 4.7, [0.22, 0.78]);
}

{
  const slide = pptx.addSlide(); addHeader(slide, 8, "DB 저장 구조"); addFooter(slide);
  table(slide, [
    ["테이블", "주요 컬럼", "저장되는 내용"],
    ["members", "uid, name, balance", "단일 모의 계좌와 현재 현금 잔액"],
    ["shares", "member_uid, stock_name, quantity, purchase_price", "보유 종목, 수량, 총 매입금액"],
    ["trade_logs", "id, member_uid, stock_name, quantity, price, trade_type, traded_at", "매수/매도 거래 기록과 거래 시각"],
  ], 0.65, 1.0, 12.0, 2.35, [0.18, 0.42, 0.40]);
  const steps = [
    ["1. 매수 요청", "브라우저가 /api/stock/buy 호출"],
    ["2. 잔액 확인", "MiniProject가 가격×수량 계산"],
    ["3. 보유 갱신", "shares 수량과 매입금액 반영"],
    ["4. 거래 저장", "trade_logs에 거래 내역 추가"],
    ["5. 화면 갱신", "/api/state로 평가금액과 손익 표시"],
  ];
  steps.forEach((step, i) => {
    const x = 0.65 + i * 2.55;
    card(slide, x, 4.05, 2.25, 1.15, step[0], step[1], i === 2 ? C.lightBlue : C.white);
    if (i < steps.length - 1) connector(slide, x + 2.25, 4.63, x + 2.48, 4.63);
  });
  slide.addText("평균단가는 DB에 따로 저장하지 않고 purchase_price / quantity로 계산한다. MySQL 실패 시 같은 데이터를 data/local-database.tsv에 저장한다.", {
    x: 0.9, y: 6.35, w: 11.3, h: 0.32,
    fontSize: 12.2, bold: true, color: C.blue, margin: 0,
  });
}

{
  const slide = pptx.addSlide(); addHeader(slide, 9, "사용자 시나리오와 Use Case"); addFooter(slide);
  const steps = ["웹사이트 접속", "거래량 인기 종목 확인", "검색/즐겨찾기", "종목 클릭", "그래프 확인", "매수", "보유 탭에서 매도", "손익 확인"];
  steps.forEach((text, i) => {
    const x = 0.55 + (i % 4) * 3.15;
    const y = 1.2 + Math.floor(i / 4) * 2.0;
    slide.addShape(pptx.ShapeType.roundRect, {
      x, y, w: 2.55, h: 0.75, rectRadius: 0.06,
      fill: { color: i === 4 || i === 5 ? C.lightBlue : C.white },
      line: { color: C.blue },
    });
    slide.addText(`${i + 1}. ${text}`, {
      x: x + 0.12, y: y + 0.24, w: 2.3, h: 0.2,
      fontSize: 10.8, bold: true, color: C.ink, align: "center", margin: 0,
    });
    if (i % 4 !== 3) connector(slide, x + 2.55, y + 0.38, x + 3.0, y + 0.38);
  });
  slide.addText("사용자 흐름이 목록에서 아무 종목이나 고르는 방식이 아니라, 실제 투자 앱처럼 종목을 먼저 보고 판단하는 흐름이 되도록 바꿨다.", {
    x: 0.8, y: 5.8, w: 11.7, h: 0.35,
    fontSize: 14, color: C.ink, bold: true, margin: 0,
  });
}

{
  const slide = pptx.addSlide(); addHeader(slide, 10, "실행 화면과 UI 캡처"); addFooter(slide);
  const shots = [
    [UI_MARKET, "시장 시세", "거래량 상위 종목을 10개 단위 페이지로 확인"],
    [UI_SEARCH, "검색 / 즐겨찾기", "종목코드 검색과 별표 기반 관심종목 등록"],
    [UI_DETAIL, "종목 상세 / 주문", "현재가, 등락률, 그래프 확인 후 매수"],
    [UI_PORTFOLIO, "포트폴리오", "보유 종목, 평가금액, 손익률, 매도 흐름"],
  ];
  shots.forEach((shot, i) => {
    const x = i % 2 === 0 ? 0.65 : 6.95;
    const y = i < 2 ? 0.95 : 4.0;
    slide.addShape(pptx.ShapeType.roundRect, {
      x, y, w: 5.75, h: 2.35, rectRadius: 0.05,
      fill: { color: C.white }, line: { color: C.line, width: 0.8 },
    });
    if (fs.existsSync(shot[0])) {
      slide.addImage({ path: shot[0], x: x + 0.15, y: y + 0.18, w: 2.85, h: 1.62 });
    }
    slide.addText(shot[1], {
      x: x + 3.15, y: y + 0.25, w: 2.25, h: 0.28,
      fontSize: 12.5, bold: true, color: C.blue, margin: 0,
    });
    slide.addText(shot[2], {
      x: x + 3.15, y: y + 0.68, w: 2.3, h: 0.85,
      fontSize: 10.2, color: C.ink, fit: "shrink", margin: 0.02,
    });
  });
  slide.addText("말로 설명하던 UI 흐름을 실제 화면 캡처로 보여주면 사용자가 어떤 순서로 투자 연습을 하는지 바로 이해할 수 있다.", {
    x: 0.85, y: 6.75, w: 11.7, h: 0.28,
    fontSize: 11.5, bold: true, color: C.blue, margin: 0,
  });
}

{
  const slide = pptx.addSlide(); addHeader(slide, 11, "한 달간 시행착오"); addFooter(slide);
  const items = [
    ["실제 시세 연동", "KIS REST 호출 실패 뒤 내장 모의 시세가 가격을 덮는 문제를 찾아 KIS 출처 종목은 모의 Tick이 덮지 못하게 수정"],
    ["전체 종목 처리 범위", "약 2700개 전체 종목을 계속 갱신하는 대신 거래량 상위 종목 중심으로 현실적인 범위를 선택"],
    ["UI 흐름 개선", "드롭다운 주문 방식 대신 종목 클릭 → 상세 확인 → 매수, 보유 종목 → 매도 흐름으로 변경"],
    ["뉴스 기능 제거", "기사 품질 편차와 API 키 관리 부담 때문에 모의투자 목적에 맞지 않는 기능은 최종 제외"],
    ["DB 저장 전환", "서버를 껐다 켜면 기록이 사라지는 문제를 MySQL 우선 저장과 TSV fallback 구조로 보완"],
  ];
  items.forEach((item, i) => {
    const x = i < 3 ? 0.75 + i * 4.1 : 2.8 + (i - 3) * 4.1;
    const y = i < 3 ? 1.0 : 3.45;
    card(slide, x, y, 3.65, 1.45, item[0], item[1], i % 2 === 0 ? C.lightBlue : C.white);
  });
}

{
  const slide = pptx.addSlide(); addHeader(slide, 12, "실제 시세 연동 여부 확인"); addFooter(slide);
  codeBox(slide, "/api/state 응답에서 확인하는 값", `\"broker\": {\n  \"source\": \"한국투자증권 KIS REST API\",\n  \"protocol\": \"KIS REST API\"\n}\n\"quoteSource\": \"한국투자증권 KIS\"`, 0.75, 1.0, 5.7, 2.35);
  table(slide, [
    ["상황", "동작", "발표 때 설명"],
    ["KIS 환경변수 있음", "KIS REST API 현재가 사용", "표시 가격은 현재가 API의 stck_prpr 값"],
    ["KIS 환경변수 없음", "내장 모의 증권사 소켓 사용", "API 키 없이도 화면 시연은 가능"],
    ["KIS 호출 일부 실패", "REST 폴링 유지", "KIS 출처 종목은 모의 Tick이 덮지 못함"],
  ], 6.75, 1.0, 5.85, 2.35, [0.25, 0.33, 0.42]);
  card(slide, 0.75, 3.85, 3.65, 1.45, "검증 결과", "현재 실행 상태: source=KIS REST API\nKIS 출처 99개, 내장 모의 시세 0개", C.lightBlue);
  card(slide, 4.85, 3.85, 3.65, 1.45, "가격 질문 대응", "삼성전자 337,000원은 프로젝트가 만든 값이 아니라 KIS 응답 stck_prpr 원문값", C.white);
  card(slide, 8.95, 3.85, 3.65, 1.45, "혼란 방지", "브로커 source와 종목별 quoteSource를 함께 보면 실제/모의 모드 구분 가능", C.white);
}

{
  const slide = pptx.addSlide(); addHeader(slide, 13, "환경 변수 설정과 실행 방법"); addFooter(slide);
  table(slide, [
    ["구분", "환경변수", "역할"],
    ["KIS REST", "KIS_APP_KEY / KIS_APP_SECRET / KIS_BASE_URL", "한국투자증권 현재가·거래량 조회"],
    ["갱신 범위", "KIS_MARKET_LIMIT / KIS_POLL_LIMIT", "거래량 순위 조회와 현재가 폴링 대상 수 조절"],
    ["MySQL", "MYSQL_URL / MYSQL_USER / MYSQL_PASSWORD", "모의 계좌, 보유 종목, 거래 기록 저장"],
    ["WebSocket", "KIS_USE_WEBSOCKET / KIS_WS_URL", "실시간 체결 구독 시도 후 실패 시 REST 전환"],
  ], 0.65, 0.95, 12.0, 2.45, [0.18, 0.38, 0.44]);
  codeBox(slide, "PowerShell 실행 예시", `$env:KIS_APP_KEY=\"발급받은_APP_KEY\"\n$env:KIS_APP_SECRET=\"발급받은_APP_SECRET\"\n$env:MYSQL_URL=\"jdbc:mysql://localhost:3306/mock_stock\"\n$env:MYSQL_USER=\"root\"\n# MYSQL_PASSWORD는 계정에 비밀번호가 있을 때만 설정\njava -cp \"out;lib/*\" app.MiniProjectApp 8080`, 0.75, 3.75, 6.15, 2.5);
  card(slide, 7.25, 3.75, 5.35, 1.1, "현재 최종본 기준", "MySQL을 먼저 시도하고, 환경변수 누락 또는 연결 실패 시 data/local-database.tsv로 전환한다.", C.lightBlue);
  card(slide, 7.25, 5.15, 5.35, 1.1, "KIS 키가 없을 때", "시세는 내장 모의 증권사 소켓으로 전환된다. 저장은 MySQL 우선 + TSV fallback 구조다.", C.white);
}

{
  const slide = pptx.addSlide(); addHeader(slide, 14, "테스트 / 검증 결과 요약"); addFooter(slide);
  table(slide, [
    ["검증 항목", "결과", "의미"],
    ["Java 컴파일", "javac -encoding UTF-8 통과", "패키지 분리 후에도 전체 소스 컴파일 가능"],
    ["/api/state 응답", "HTTP 200", "웹앱 상태 JSON 정상 제공"],
    ["시세 출처", "KIS 99개 / 내장 모의 0개", "현재 실행 화면은 KIS REST API 기준"],
    ["가격 파싱", "거래량 순위 가격 미사용", "표시 가격은 inquire-price의 stck_prpr 사용"],
    ["시연 영상", "약 35초", "검색, 즐겨찾기, 매수, 보유, 매도 흐름 포함"],
  ], 0.65, 0.95, 12.0, 3.8, [0.26, 0.3, 0.44]);
  bullets(slide, [
    "검증 기준은 화면만 보는 것이 아니라 API 응답, quoteSource, 컴파일 결과를 함께 확인했다.",
    "KIS 호출 제한이나 WebSocket 실환경 구독은 향후 별도 테스트 항목으로 남긴다.",
  ], 0.85, 5.25, 11.4, 0.85, 12.5);
}

{
  const slide = pptx.addSlide(); addHeader(slide, 15, "향후 개선 로드맵"); addFooter(slide);
  table(slide, [
    ["개선 항목", "구체 계획", "예상 효과"],
    ["WebSocket 실계정 검증", "KIS 테스트베드에서 구독 성공, 실패 코드, 메시지 필드 순서 확인", "REST 폴링보다 자연스러운 체결가 갱신"],
    ["서비스 계층 세분화", "MiniProject를 TradingService, PortfolioService, MarketService로 분리", "발표 후 유지보수와 기능 추가가 쉬워짐"],
    ["테스트 코드 보강", "매수/매도, DB 저장, API 파싱 단위 테스트 작성", "수정 후 회귀 오류를 빠르게 확인"],
    ["업종 위험 표시", "StockCategories 값을 UI 배지로 표시", "종목 선택 때 투자 판단 정보 보강"],
    ["개인화 관심종목", "즐겨찾기와 거래 기록 기반 추천 기준 설계", "단순 목록보다 사용자 중심 화면으로 확장"],
  ], 0.65, 0.95, 12.0, 4.35, [0.23, 0.47, 0.3]);
  slide.addText("로드맵은 기능을 더 넣겠다는 말보다, 현재 한계와 다음 검증 순서를 구체적으로 보여주는 용도다.", {
    x: 0.85, y: 6.35, w: 11.5, h: 0.35,
    fontSize: 12.3, bold: true, color: C.blue, margin: 0,
  });
}

{
  const slide = pptx.addSlide(); addHeader(slide, 16, "시연 영상 구성"); addFooter(slide);
  table(slide, [
    ["구간", "보여줄 화면", "말할 내용"],
    ["0~8초", "웹사이트 접속과 시장 시세", "상단 자산 요약과 거래량 상위 종목 확인"],
    ["8~16초", "종목코드 검색", "검색창에 005930을 입력해 삼성전자 조회"],
    ["16~25초", "상세 화면과 주문", "현재가, 그래프, 즐겨찾기, 1주 매수"],
    ["25~35초", "보유/기록/매도", "보유 탭 확인, 기록 탭 이동, 매도 후 시장 페이지 확인"],
  ], 0.65, 0.95, 7.0, 3.2, [0.18, 0.36, 0.46]);
  if (fs.existsSync(PREVIEW)) {
    slide.addImage({ path: PREVIEW, x: 8.0, y: 1.0, w: 4.7, h: 2.65 });
  }
  slide.addText("첨부 영상 파일", { x: 0.8, y: 4.75, w: 2.0, h: 0.25, fontSize: 12, bold: true, color: C.blue, margin: 0 });
  slide.addText(fs.existsSync(VIDEO) ? "deliverables/mock-stock-website-demo.webm" : "시연 영상 파일을 같은 폴더에 추가 예정", {
    x: 0.8, y: 5.15, w: 6.8, h: 0.3,
    fontSize: 13, bold: true, color: C.ink, margin: 0,
  });
}


pptx.writeFile({ fileName: path.join(OUT, process.env.PRESENTATION_OUTPUT || "Java_모의주식투자_발표자료.pptx") })
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });

