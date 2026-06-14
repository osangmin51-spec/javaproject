const fs = require("fs");
const path = require("path");
const PptxGenJS = require("pptxgenjs");

const ROOT = process.cwd();
const OUT = path.join(ROOT, "deliverables");
const VIDEO = path.join(OUT, "mock-stock-website-demo.webm");
const PREVIEW = path.join(OUT, "mock-stock-website-demo-preview.png");
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
  slide.addText("Java 프로젝트 모의주식 | KIS Open API · MySQL · 소켓 기반 모의투자 웹앱", {
    x: 0.45, y: 7.16, w: 8.4, h: 0.16,
    fontSize: 7.5, color: "7A8797", margin: 0,
  });
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
  const slide = pptx.addSlide(); addHeader(slide, 2, "제안발표 전 vs 최종 구현"); addFooter(slide);
  table(slide, [
    ["구분", "제안발표 단계", "최종 구현"],
    ["주제", "Java 미니프로젝트 기능 구현", "실제 증권 API 기반 모의주식투자"],
    ["데이터", "초기 샘플/내부 데이터", "KIS 현재가, 등락률, 누적 거래량"],
    ["저장", "메모리 중심 구상", "MySQL 테이블 저장"],
    ["화면", "기본 목록과 입력 화면", "종목 상세, 그래프, 포트폴리오"],
    ["흐름", "드롭다운 선택 후 주문", "종목 클릭 → 상세 확인 → 매수/매도"],
  ], 0.65, 1.05, 12.0, 4.55, [0.18, 0.36, 0.46]);
  slide.addText("변경 이유: 모의주식투자라는 주제라면 임의 가격보다 실제 시세와 거래량을 쓰는 편이 목적이 더 분명하다고 판단했다.", {
    x: 0.8, y: 5.95, w: 11.6, h: 0.5,
    fontSize: 13.2, bold: true, color: C.blue, margin: 0,
  });
}

{
  const slide = pptx.addSlide(); addHeader(slide, 3, "프로젝트 목적"); addFooter(slide);
  card(slide, 0.65, 1.0, 3.9, 1.25, "실제 시세 기반", "KIS Open API에서 현재가, 등락률, 거래량을 받아와 모의투자 화면에 반영", C.lightBlue);
  card(slide, 4.75, 1.0, 3.9, 1.25, "사용자 매매 흐름", "시장 시세에서 종목 클릭 → 상세 확인 → 매수 / 보유 탭에서 매도", C.white);
  card(slide, 8.85, 1.0, 3.8, 1.25, "포트폴리오 손익", "보유 주식 평가금액, 실시간 손익, 수익률을 자동 계산", C.white);
  bullets(slide, [
    "외부 API, MySQL 저장, 소켓 통신, 컬렉션을 하나의 Java 프로젝트 안에서 연결",
    "거래량 상위 종목을 중심으로 사용자가 볼 만한 종목을 먼저 노출",
    "가격 그래프로 단순 매매 입력보다 실제 투자 화면에 가까운 흐름 구현",
  ], 1.0, 3.05, 11.4, 2.0, 15);
}

{
  const slide = pptx.addSlide(); addHeader(slide, 4, "전체 시스템 구조"); addFooter(slide);
  const boxes = [
    ["웹 UI", 0.8, 1.3], ["MiniHandler", 3.0, 1.3], ["MiniProject", 5.3, 1.3],
    ["MySqlDatabase", 7.75, 1.3], ["KisQuotePoller", 3.0, 3.6], ["KisQuoteClient", 5.3, 3.6],
    ["KIS Open API", 7.75, 3.6], ["Broker Socket", 5.3, 5.3], ["실시간 Tick", 7.75, 5.3],
    ["Broker Socket", 10.0, 3.6],
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
  const slide = pptx.addSlide(); addHeader(slide, 5, "코드 구조"); addFooter(slide);
  table(slide, [
    ["파일", "역할"],
    ["MiniProjectApp.java", "서버 시작점, KIS/DB/소켓 초기화"],
    ["MiniHandler.java", "HTTP 요청 라우팅과 API 응답 처리"],
    ["MiniProject.java", "회원, 매매, 포트폴리오, 시세 상태 핵심 로직"],
    ["DatabaseIntegration.java", "MySQL 연결, 테이블 생성, 저장/로드"],
    ["KisIntegration.java", "KIS 토큰 발급, 거래량 순위, 현재가 조회"],
    ["StockCategoryProfiles.java", "업종·테마별 종목 분류 타입"],
    ["WebPages.java", "HTML/CSS/JavaScript 화면 렌더링"],
  ], 0.7, 0.95, 12.0, 5.6, [0.34, 0.66]);
}

{
  const slide = pptx.addSlide(); addHeader(slide, 6, "클래스/인터페이스 설계"); addFooter(slide);
  card(slide, 0.8, 1.0, 3.4, 1.2, "서버 계층", "MiniProjectApp → MiniHandler → MiniProject\n요청을 받고 핵심 로직으로 전달", C.lightBlue);
  card(slide, 4.95, 1.0, 3.4, 1.2, "도메인 계층", "Member, Stock, Share, TradeLog\n회원/종목/거래 상태 관리", C.white);
  card(slide, 9.05, 1.0, 3.4, 1.2, "외부 연동", "KisQuoteClient, BrokerIntegration\nREST API와 소켓 흐름 처리", C.white);
  card(slide, 0.8, 3.1, 5.5, 1.45, "상속", "CompanyProfile 추상 클래스를 두고 종목별 회사 프로필 클래스가 공통 구조를 상속", C.white);
  card(slide, 6.9, 3.1, 5.5, 1.45, "인터페이스", "StockCategoryProfile 인터페이스로 업종별 분류와 위험 설명 규칙을 통일", C.white);
  slide.addText("설계 이유: 100개 이상 타입 조건을 맞추면서도 회사 설명과 업종 분류를 의미 있는 구조로 분리했다.", {
    x: 0.95, y: 5.55, w: 11.1, h: 0.45,
    fontSize: 14, bold: true, color: C.ink, margin: 0,
  });
}

{
  const slide = pptx.addSlide(); addHeader(slide, 7, "AI vs 나의 역할"); addFooter(slide);
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
  const slide = pptx.addSlide(); addHeader(slide, 8, "사용자 시나리오와 Use Case"); addFooter(slide);
  const steps = ["접속/로그인", "거래량 인기 종목 확인", "검색/즐겨찾기", "종목 클릭", "그래프 확인", "매수", "보유 탭에서 매도", "손익 확인"];
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
  const slide = pptx.addSlide(); addHeader(slide, 9, "사용자 UI / 화면 구성"); addFooter(slide);
  card(slide, 0.65, 1.0, 3.0, 1.05, "상단 요약", "현금, 평가액, 총자산, 손익, 수익률 표시", C.lightBlue);
  card(slide, 3.85, 1.0, 3.0, 1.05, "시장 시세", "거래량 상위 종목을 10개씩 페이지로 표시", C.white);
  card(slide, 7.05, 1.0, 3.0, 1.05, "종목 상세", "현재가, 그래프, 매수 입력", C.white);
  card(slide, 10.25, 1.0, 2.4, 1.05, "보유 탭", "보유 종목별 매도 수량 입력", C.white);
  slide.addShape(pptx.ShapeType.rect, { x: 0.7, y: 2.65, w: 11.9, h: 3.3, fill: { color: C.pale }, line: { color: C.line } });
  slide.addText("시장 시세", { x: 1.0, y: 2.95, w: 2.0, h: 0.3, fontSize: 13, bold: true, color: C.ink, margin: 0 });
  slide.addText("검색창 · 즐겨찾기 · 10개 단위 페이지 · 상세/매수 버튼", { x: 1.0, y: 3.45, w: 5.4, h: 0.25, fontSize: 11, color: C.gray, margin: 0 });
  slide.addText("종목 상세", { x: 7.0, y: 2.95, w: 2.4, h: 0.3, fontSize: 13, bold: true, color: C.ink, margin: 0 });
  slide.addText("현재가 · 등락률 · 가격 변화 그래프\n매수 수량 입력 → 매수", { x: 7.0, y: 3.45, w: 4.3, h: 0.65, fontSize: 11, color: C.gray, margin: 0 });
}

{
  const slide = pptx.addSlide(); addHeader(slide, 10, "데이터 흐름"); addFooter(slide);
  table(slide, [
    ["단계", "내용"],
    ["입력", "로그인 정보, 종목 선택, 즐겨찾기, 매수/매도 수량"],
    ["외부 입력", "KIS 현재가/등락률/거래량"],
    ["처리", "잔액 확인, 보유 수량 확인, 매매 체결, 손익 계산"],
    ["저장", "members, shares, trade_logs MySQL 테이블"],
    ["출력", "시장 시세, 종목 상세, 그래프, 포트폴리오, 거래 기록"],
  ], 1.0, 1.1, 11.3, 4.7, [0.22, 0.78]);
}

{
  const slide = pptx.addSlide(); addHeader(slide, 11, "데이터 처리 방식"); addFooter(slide);
  table(slide, [
    ["데이터", "처리 방식"],
    ["주식 시세", "KIS 현재가 REST API 조회"],
    ["종목 선별", "거래량 기준 상위 종목을 선별해 자동 갱신"],
    ["실시간성", "전체 종목 매초 조회 대신 상위 목록 중심 갱신 + 소켓 구독 구조 실험"],
    ["소켓", "모의 증권사 서버의 가격 Tick 구독 구조"],
    ["사용자 데이터", "MySQL 테이블 저장"],
    ["가격 그래프", "서버가 보관한 최근 가격 히스토리 표시"],
  ], 0.85, 1.0, 11.7, 5.05, [0.25, 0.75]);
}

{
  const slide = pptx.addSlide(); addHeader(slide, 12, "한 달간 시행착오"); addFooter(slide);
  const items = [
    ["저장 문제", "서버 재시작 시 데이터가 사라지는 문제를 MySQL 저장 구조로 변경"],
    ["인코딩", "한글 UI가 깨지지 않도록 UTF-8 기준으로 정리"],
    ["API 키", "KIS 키를 환경변수로 관리"],
    ["가격 표시", "초기값과 API값이 섞이는 문제를 확인하고 갱신 흐름을 보정"],
    ["API 지연", "2700개 전체 조회 대신 거래량 상위 종목 중심으로 범위 축소"],
    ["UI 단순화", "운세/수동 날짜 진행 제거, 검색·즐겨찾기·상세 매매 흐름 강화"],
  ];
  items.forEach((item, i) => {
    const x = 0.75 + (i % 2) * 6.0;
    const y = 1.0 + Math.floor(i / 2) * 1.55;
    card(slide, x, y, 5.45, 1.1, item[0], item[1], i % 2 === 0 ? C.lightBlue : C.white);
  });
}

{
  const slide = pptx.addSlide(); addHeader(slide, 13, "Java 클래스 활용"); addFooter(slide);
  bullets(slide, [
    "HttpServer: 프레임워크 없이 웹 서버 구현",
    "HttpClient: KIS API 호출",
    "Thread: 백그라운드 시세 갱신",
    "ConcurrentHashMap: 회원/종목 상태 동시 접근 처리",
    "ArrayList, LinkedHashMap, List: 거래 기록, 가격 히스토리, 조회 순서 관리",
    "JDBC DriverManager: MySQL 저장소 연결",
    "LocalDateTime, DateTimeFormatter: 거래 시간과 시세 갱신 시각 표시",
  ], 0.95, 1.05, 11.6, 5.4, 14);
}

{
  const slide = pptx.addSlide(); addHeader(slide, 14, "주요 코드 일부"); addFooter(slide);
  codeBox(slide, "HTTP 라우팅 - MiniHandler.java", `if ("GET".equals(method) && "/api/state".equals(path)) {
    send(exchange, 200, "application/json", project.stateJson());
}
String json = switch (path) {
    case "/api/stock/buy" -> project.buyStock(body);
    case "/api/stock/sell" -> project.sellStock(body);
    default -> Json.obj("ok", false, "error", "없는 API");
};`, 0.65, 0.95, 5.8, 2.2);
  codeBox(slide, "KIS 현재가 조회 - KisIntegration.java", `HttpRequest request = HttpRequest.newBuilder(uri)
    .header("authorization", "Bearer " + token)
    .header("appkey", config.appKey)
    .header("tr_id", "FHKST01010100")
    .GET()
    .build();
HttpResponse<String> response = client.send(request, BodyHandlers.ofString());`, 6.8, 0.95, 5.85, 2.2);
  codeBox(slide, "매수 처리 - MiniProject.java", `Stock stock = findStock(stockName);
long total = (long) stock.price * quantity;
if (currentMember.balance < total) return Json.obj("ok", false);
currentMember.balance -= total;
currentMember.shares.put(stockName, share.buy(quantity, total));
logs.add(new TradeLog(currentMember.uid, stockName, quantity, total, "구매"));
saveDatabase();`, 0.65, 3.55, 5.8, 2.45);
  codeBox(slide, "MySQL 저장 - DatabaseIntegration.java", `try (Connection con = DriverManager.getConnection(url, user, password)) {
    con.setAutoCommit(false);
    insertMembers(con, members);
    insertShares(con, members);
    insertTradeLogs(con, logs);
    con.commit();
}`, 6.8, 3.55, 5.85, 2.45);
  slide.addText("발표에서는 전체 코드를 읽기보다 요청 처리, 외부 API, 거래 처리, 저장 흐름이 실제로 연결되어 있다는 점을 보여준다.", {
    x: 0.75, y: 6.3, w: 11.9, h: 0.3,
    fontSize: 12.5, bold: true, color: C.ink, margin: 0,
  });
}

{
  const slide = pptx.addSlide(); addHeader(slide, 15, "시연 영상 구성"); addFooter(slide);
  table(slide, [
    ["구간", "보여줄 화면", "말할 내용"],
    ["0~10초", "로그인과 상단 요약", "Java 웹앱으로 실행되는 점 설명"],
    ["10~25초", "시장 시세와 검색", "거래량 상위 종목과 검색/즐겨찾기"],
    ["25~40초", "종목 상세/그래프", "현재가, 등락률, 그래프가 한 화면에 연결됨"],
    ["40~55초", "매수 후 보유 탭", "종목을 클릭해 매수하고 손익을 확인"],
  ], 0.65, 0.95, 7.0, 3.2, [0.18, 0.36, 0.46]);
  if (fs.existsSync(PREVIEW)) {
    slide.addImage({ path: PREVIEW, x: 8.0, y: 1.0, w: 4.7, h: 2.65 });
  }
  slide.addText("첨부 영상 파일", { x: 0.8, y: 4.75, w: 2.0, h: 0.25, fontSize: 12, bold: true, color: C.blue, margin: 0 });
  slide.addText(fs.existsSync(VIDEO) ? "deliverables/mock-stock-website-demo.webm" : "시연 영상 파일을 같은 폴더에 추가 예정", {
    x: 0.8, y: 5.15, w: 6.8, h: 0.3,
    fontSize: 13, bold: true, color: C.ink, margin: 0,
  });
  slide.addText("영상은 코드 설명보다 실제 실행 화면을 중심으로 짧게 보여주는 용도이다.", {
    x: 0.8, y: 5.75, w: 7.1, h: 0.3,
    fontSize: 12.5, color: C.gray, margin: 0,
  });
}

pptx.writeFile({ fileName: path.join(OUT, process.env.PRESENTATION_OUTPUT || "Java_모의주식투자_발표자료.pptx") })
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
