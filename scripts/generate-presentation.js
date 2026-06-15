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
  const slide = pptx.addSlide(); addHeader(slide, 2, "발표 흐름"); addFooter(slide);
  table(slide, [
    ["순서", "발표 내용", "핵심 질문"],
    ["1", "왜 만들었는가", "과제용 모의투자에서 무엇을 보여줄 것인가"],
    ["2", "사용자는 어떻게 쓰는가", "로그인 없이 종목을 보고 바로 매매할 수 있는가"],
    ["3", "데이터는 어떻게 흐르는가", "KIS 시세, 저장소, 포트폴리오 계산이 어떻게 연결되는가"],
    ["4", "Java 코드는 어떻게 나뉘는가", "서버, 서비스, DB, 외부 API, 화면 역할이 분리되는가"],
    ["5", "어떤 시행착오가 있었는가", "실제 개발 중 바꾼 판단은 무엇인가"],
  ], 0.65, 1.05, 12.0, 4.55, [0.18, 0.36, 0.46]);
  slide.addText("발표는 기능 나열보다 사용 흐름과 데이터 흐름을 먼저 설명한 뒤, 그 흐름을 Java 코드가 어떻게 처리하는지 보여주는 순서로 진행한다.", {
    x: 0.8, y: 5.95, w: 11.6, h: 0.5,
    fontSize: 13.2, bold: true, color: C.blue, margin: 0,
  });
}

{
  const slide = pptx.addSlide(); addHeader(slide, 3, "제안 단계 vs 최종 구현"); addFooter(slide);
  table(slide, [
    ["구분", "제안 단계", "최종 구현"],
    ["주제", "Java 미니프로젝트 기능 구현", "KIS API 기반 모의주식투자 웹앱"],
    ["가격 데이터", "내부 샘플/모의 가격", "한국투자증권 KIS 현재가와 거래량"],
    ["저장", "메모리 중심 구상", "MySQL 테이블 필수 저장"],
    ["화면", "기본 목록과 입력 화면", "종목 상세, 그래프, 포트폴리오"],
    ["매매 흐름", "드롭다운 선택 후 주문", "종목 클릭 후 상세 확인과 매매"],
  ], 0.75, 1.0, 11.85, 4.75, [0.2, 0.38, 0.42]);
  slide.addText("처음에는 기능 구현 중심이었지만, 최종본은 실제 시세와 저장 구조를 갖춘 모의투자 흐름으로 바뀌었다.", {
    x: 0.95, y: 6.05, w: 11.1, h: 0.35,
    fontSize: 13.3, bold: true, color: C.blue, margin: 0,
  });
}

{
  const slide = pptx.addSlide(); addHeader(slide, 4, "프로젝트 핵심 요약"); addFooter(slide);
  card(slide, 0.65, 1.0, 3.0, 1.35, "사용자", "시장 시세에서 종목을 검색하고\n상세 화면에서 매수/매도", C.lightBlue);
  card(slide, 3.85, 1.0, 3.0, 1.35, "시세 데이터", "KIS Open API 현재가와 거래량을\n주기적으로 갱신", C.white);
  card(slide, 7.05, 1.0, 3.0, 1.35, "Java 서버", "HttpServer가 요청을 받고\nMiniProject가 매매 처리", C.white);
  card(slide, 10.25, 1.0, 2.45, 1.35, "저장", "MySQL에 계좌,\n보유, 거래 기록 저장", C.white);
  table(slide, [
    ["질문", "프로젝트에서 보여주는 답"],
    ["무엇을 만들었나?", "실제 시세를 보며 연습하는 Java 기반 모의주식투자 웹앱"],
    ["왜 API가 필요한가?", "내부 더미 가격이 아니라 현재가와 거래량을 기준으로 종목을 판단하기 위해"],
    ["왜 DB가 필요한가?", "서버를 껐다 켜도 보유 종목과 거래 기록이 유지되도록 하기 위해"],
    ["어디가 Java 활용인가?", "HttpServer, HttpClient, Thread, Collection, JDBC, WebSocket 구조를 직접 연결"],
  ], 0.9, 3.05, 11.5, 3.1, [0.25, 0.75]);
}

{
  const slide = pptx.addSlide(); addHeader(slide, 5, "프로젝트 목적"); addFooter(slide);
  card(slide, 0.65, 1.0, 3.9, 1.25, "실제 시세 기반", "KIS Open API에서 현재가, 등락률, 거래량을 받아와 모의투자 화면에 반영", C.lightBlue);
  card(slide, 4.75, 1.0, 3.9, 1.25, "사용자 매매 흐름", "시장 시세에서 종목 클릭 → 상세 확인 → 매수 / 보유 탭에서 매도", C.white);
  card(slide, 8.85, 1.0, 3.8, 1.25, "포트폴리오 손익", "보유 주식 평가금액, 실시간 손익, 수익률을 자동 계산", C.white);
  bullets(slide, [
    "외부 API, 저장소 구조, 소켓 통신, 컬렉션을 하나의 Java 프로젝트 안에서 연결",
    "거래량 상위 종목을 중심으로 사용자가 볼 만한 종목을 먼저 노출",
    "가격 그래프로 단순 매매 입력보다 실제 투자 화면에 가까운 흐름 구현",
  ], 1.0, 3.05, 11.4, 2.0, 15);
}

{
  const slide = pptx.addSlide(); addHeader(slide, 6, "전체 시스템 구조"); addFooter(slide);
  const boxes = [
    ["웹 UI", 0.8, 1.3], ["MiniHandler", 3.0, 1.3], ["MiniProject", 5.3, 1.3],
    ["MySqlDatabase", 7.75, 1.3], ["KisQuotePoller", 3.0, 3.6], ["KisQuoteClient", 5.3, 3.6],
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
  const slide = pptx.addSlide(); addHeader(slide, 7, "코드 구조"); addFooter(slide);
  table(slide, [
    ["묶음", "주요 파일", "역할"],
    ["실행/요청", "MiniProjectApp, MiniHandler", "서버 시작과 API 라우팅"],
    ["핵심 로직", "MiniProject", "모의 계좌, 매매, 포트폴리오, 시세 상태 관리"],
    ["저장소", "ProjectDatabase, MySqlDatabase", "MySQL 필수 저장과 데이터 로드/저장"],
    ["외부 시세", "KisQuoteClient, KisQuotePoller, KisWebSocketQuoteClient, MockBrokerServer", "KIS REST/WebSocket 시세와 모의 소켓 Tick"],
    ["화면/도메인", "MiniDashboardPage, Member, Stock, Share, TradeLog, StockCategories", "웹 UI와 종목/거래 데이터 모델"],
  ], 0.55, 0.92, 12.2, 3.35, [0.19, 0.42, 0.39]);
  codeBox(slide, "요청 라우팅 - MiniHandler.java", `case "/api/stock/buy" -> project.buyStock(body);
case "/api/stock/sell" -> project.sellStock(body);
case "/api/state" -> project.stateJson();`, 0.85, 4.65, 5.7, 1.35);
  codeBox(slide, "매매 저장 - MiniProject.java", `member.shares.compute(stockName, (key, share) -> ...);
logs.add(new TradeLog(member.uid, stockName, quantity, total, "구매"));
saveDatabase();`, 6.85, 4.65, 5.7, 1.35);
}

{
  const slide = pptx.addSlide(); addHeader(slide, 8, "클래스/인터페이스 설계"); addFooter(slide);
  card(slide, 0.65, 0.95, 3.55, 1.12, "서버 계층", "MiniProjectApp → MiniHandler → MiniProject\n요청을 받고 핵심 로직으로 전달", C.lightBlue);
  card(slide, 4.85, 0.95, 3.55, 1.12, "도메인 계층", "Member, Stock, Share, TradeLog\n계좌, 종목, 보유, 거래 내역 표현", C.white);
  card(slide, 9.05, 0.95, 3.55, 1.12, "외부 연동", "KisQuoteClient, MockBrokerServer\nREST API와 소켓 흐름 처리", C.white);
  card(slide, 0.65, 2.55, 5.85, 1.22, "상속", "CompanyProfile 추상 클래스 → 종목별 회사 프로필 클래스\n회사명, 업종, 설명 형식을 공통화", C.white);
  card(slide, 6.75, 2.55, 5.85, 1.22, "인터페이스", "StockCategoryProfile → 업종별 분류/위험 설명 규칙 통일\n상세 화면에서 업종 설명으로 사용", C.white);
  table(slide, [
    ["검증 항목", "결과"],
    ["타입 수", "class / interface / enum 합계 111개"],
    ["패키지", "app, controller, service, domain, repository, external, view, util"],
    ["설계 목적", "조건 맞추기용 더미가 아니라 종목 상세와 설명에 쓰이는 구조"],
  ], 1.05, 4.55, 11.15, 1.55, [0.25, 0.75]);
}

{
  const slide = pptx.addSlide(); addHeader(slide, 9, "클래스/라이브러리 구조 상세"); addFooter(slide);
  table(slide, [
    ["구분", "활용한 Java 클래스/라이브러리", "프로젝트에서 맡은 역할"],
    ["웹 서버", "HttpServer, HttpExchange, Executors", "브라우저 요청 수신, API 응답, 서버 스레드 관리"],
    ["외부 API", "HttpClient, HttpRequest, WebSocket", "KIS 현재가 조회와 WebSocket 구독 시도"],
    ["상태 관리", "ConcurrentHashMap, ArrayList, LinkedHashMap", "종목 상태, 거래 기록, 가격 히스토리 저장"],
    ["DB 저장", "JDBC DriverManager, PreparedStatement, ResultSet", "MySQL 연결, 계좌/보유/거래 기록 저장"],
    ["시간/출력", "LocalDateTime, DateTimeFormatter, StringBuilder", "거래 시간 표시와 HTML/JSON 문자열 생성"],
  ], 0.55, 0.95, 12.2, 4.95, [0.18, 0.43, 0.39]);
}

{
  const slide = pptx.addSlide(); addHeader(slide, 10, "AI vs 나의 역할"); addFooter(slide);
  table(slide, [
    ["구분", "내가 주도한 부분", "AI를 활용한 부분"],
    ["주제", "모의주식투자 웹사이트 방향 결정", "구현 방식 후보 정리"],
    ["요구사항", "KIS API, 거래량 상위, 그래프/즐겨찾기 요구", "Java 코드 작성 보조"],
    ["UI", "불필요한 기능 제거, 종목 클릭 중심 요구", "HTML/CSS/JS 구현 보조"],
    ["문제해결", "가격 이상 지적, MySQL/소켓 구조 요구", "원인 분석과 코드 수정"],
    ["검증", "실행 화면 확인", "컴파일, API 상태, 문서 정리"],
  ], 0.7, 1.0, 12.0, 4.55, [0.18, 0.41, 0.41]);
  slide.addText("핵심은 AI가 방향을 정한 것이 아니라, 내가 불편한 점과 프로젝트 목적을 계속 지적하면서 결과를 좁혀간 점이다.", {
    x: 0.95, y: 6.0, w: 11.2, h: 0.35,
    fontSize: 13.5, bold: true, color: C.blue, margin: 0,
  });
}

{
  const slide = pptx.addSlide(); addHeader(slide, 11, "사용자 시나리오와 Use Case"); addFooter(slide);
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
  const slide = pptx.addSlide(); addHeader(slide, 12, "사용자 UI / 화면 구성"); addFooter(slide);
  if (fs.existsSync(UI_MARKET) && fs.existsSync(UI_DETAIL) && fs.existsSync(UI_PORTFOLIO)) {
    slide.addShape(pptx.ShapeType.roundRect, { x: 0.55, y: 0.92, w: 3.55, h: 5.8, rectRadius: 0.04, fill: { color: C.white }, line: { color: C.line } });
    slide.addImage({ path: UI_MARKET, x: 0.62, y: 1.05, w: 3.4, h: 5.56 });
    slide.addText("시장 시세 / 검색 / 페이지", { x: 0.7, y: 6.7, w: 3.25, h: 0.24, fontSize: 9.2, bold: true, color: C.blue, align: "center", margin: 0 });

    slide.addShape(pptx.ShapeType.roundRect, { x: 4.35, y: 0.92, w: 8.35, h: 3.75, rectRadius: 0.04, fill: { color: C.white }, line: { color: C.line } });
    slide.addImage({ path: UI_DETAIL, x: 4.48, y: 1.05, w: 5.45, h: 3.7 });
    slide.addText("종목 상세 / 매수·매도 입력", { x: 4.55, y: 4.72, w: 5.25, h: 0.24, fontSize: 9.2, bold: true, color: C.blue, align: "center", margin: 0 });
    slide.addText("검색 결과에서 종목을 누르면\n상세 정보와 주문 입력이\n같은 화면에서 이어진다.", {
      x: 10.2, y: 1.35, w: 2.1, h: 1.05,
      fontSize: 11.3, bold: true, color: C.ink, margin: 0.02,
      fit: "shrink",
    });
    slide.addText("시세 확인 → 수량 입력 → 매수\n보유 종목 기준 매도", {
      x: 10.2, y: 2.72, w: 2.05, h: 0.8,
      fontSize: 10.2, color: C.gray, margin: 0.02,
      fit: "shrink",
    });

    slide.addShape(pptx.ShapeType.roundRect, { x: 4.35, y: 5.15, w: 8.35, h: 1.08, rectRadius: 0.04, fill: { color: C.white }, line: { color: C.line } });
    slide.addImage({ path: UI_PORTFOLIO, x: 4.48, y: 5.28, w: 8.08, h: 0.9 });
    slide.addText("보유 종목 / 평가금액 / 손익률", { x: 4.55, y: 6.32, w: 7.95, h: 0.24, fontSize: 9.2, bold: true, color: C.blue, align: "center", margin: 0 });
  } else {
    card(slide, 0.65, 1.0, 3.0, 1.05, "상단 요약", "현금, 평가액, 총자산, 손익, 수익률 표시", C.lightBlue);
    card(slide, 3.85, 1.0, 3.0, 1.05, "시장 시세", "거래량 상위 종목을 10개씩 페이지로 표시", C.white);
    card(slide, 7.05, 1.0, 3.0, 1.05, "종목 상세", "현재가, 그래프, 매수 입력", C.white);
    card(slide, 10.25, 1.0, 2.4, 1.05, "보유 탭", "보유 종목별 매도 수량 입력", C.white);
  }
}

{
  const slide = pptx.addSlide(); addHeader(slide, 13, "데이터 흐름"); addFooter(slide);
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
  const slide = pptx.addSlide(); addHeader(slide, 14, "DB 저장 구조"); addFooter(slide);
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
  slide.addText("평균단가는 DB에 따로 저장하지 않고 purchase_price / quantity로 계산한다.", {
    x: 0.9, y: 6.35, w: 11.3, h: 0.32,
    fontSize: 12.2, bold: true, color: C.blue, margin: 0,
  });
}

{
  const slide = pptx.addSlide(); addHeader(slide, 15, "데이터 처리 방식"); addFooter(slide);
  table(slide, [
    ["데이터", "처리 방식"],
    ["주식 시세", "KIS 현재가 REST API 조회"],
    ["종목 선별", "거래량 기준 상위 종목을 선별해 자동 갱신"],
    ["갱신 범위", "KIS_MARKET_LIMIT 기본 200, 100~300개 제한 / KIS_POLL_LIMIT 기본 100"],
    ["실시간성", "전체 종목 매초 조회 대신 상위 목록 중심 갱신 + 소켓 구독 구조 실험"],
    ["소켓", "모의 증권사 서버의 가격 Tick 구독 구조"],
    ["사용자 데이터", "MySQL 테이블에 상시 저장"],
    ["가격 그래프", "서버가 보관한 최근 가격 히스토리 표시"],
  ], 0.85, 1.0, 11.7, 5.05, [0.25, 0.75]);
}

{
  const slide = pptx.addSlide(); addHeader(slide, 16, "한 달간 시행착오"); addFooter(slide);
  const items = [
    ["실제 시세 연동", "내부 모의 가격과 KIS 응답값이 섞이면서 가격이 이상해 보이는 문제를 확인하고 갱신 흐름을 정리"],
    ["전체 종목 처리 범위", "약 2700개 전체 종목을 계속 갱신하는 대신 거래량 상위 종목 중심으로 현실적인 범위를 선택"],
    ["UI 흐름 개선", "드롭다운 주문 방식 대신 종목 클릭 → 상세 확인 → 매수, 보유 종목 → 매도 흐름으로 변경"],
    ["뉴스 기능 제거", "기사 품질 편차와 API 키 관리 부담 때문에 모의투자 목적에 맞지 않는 기능은 최종 제외"],
    ["DB 저장 전환", "서버를 껐다 켜면 기록이 사라지는 문제를 MySQL 필수 저장 구조로 바꿔 거래 기록을 유지"],
  ];
  items.forEach((item, i) => {
    const x = i < 3 ? 0.75 + i * 4.1 : 2.8 + (i - 3) * 4.1;
    const y = i < 3 ? 1.0 : 3.45;
    card(slide, x, y, 3.65, 1.45, item[0], item[1], i % 2 === 0 ? C.lightBlue : C.white);
  });
}

{
  const slide = pptx.addSlide(); addHeader(slide, 17, "보완 상태와 남은 과제"); addFooter(slide);
  card(slide, 0.7, 1.0, 5.75, 1.2, "로그인 제거", "과제용 프로젝트라 회원 인증보다 종목 조회와 매매 흐름에 집중하도록 단일 모의 계좌 구조로 단순화", C.lightBlue);
  card(slide, 6.85, 1.0, 5.75, 1.2, "WebSocket 보완", "KIS WebSocket 시작/오류 시 REST 폴링으로 자동 전환되도록 코드 보강", C.white);
  card(slide, 0.7, 2.75, 5.75, 1.2, "남은 검증", "실제 KIS 테스트베드에서 구독 성공 여부와 메시지 필드 순서 확인 필요", C.white);
  card(slide, 6.85, 2.75, 5.75, 1.2, "구조 정리 완료", "src/main/java 아래 app/controller/service/domain/repository/external/view/util 패키지로 물리적 분리", C.white);
  card(slide, 0.7, 4.5, 5.75, 1.2, "추가 아이디어", "업종별 위험 등급 표시, 개인화 관심종목 추천, 테스트 코드 보강", C.white);
  card(slide, 6.85, 4.5, 5.75, 1.2, "남은 개선", "테스트 코드 보강, 서비스 세분화, WebSocket 실환경 검증 결과 문서화", C.lightBlue);
}

{
  const slide = pptx.addSlide(); addHeader(slide, 18, "시연 영상 구성"); addFooter(slide);
  table(slide, [
    ["구간", "보여줄 화면", "말할 내용"],
    ["0~8초", "웹사이트 접속과 시장 시세", "상단 자산 요약과 거래량 상위 종목 확인"],
    ["8~17초", "종목코드 검색", "검색창에 009150을 입력해 삼성전기 조회"],
    ["17~30초", "종목 상세와 그래프", "현재가, 등락률, 가격 변화 그래프 확인"],
    ["30~44초", "매수 후 보유 탭", "수량 입력 후 매수, 보유 종목 손익 확인"],
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

