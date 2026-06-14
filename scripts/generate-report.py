from pathlib import Path
import os

from docx import Document
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor


ROOT = Path.cwd()
OUT = ROOT / "deliverables"
OUT.mkdir(exist_ok=True)
PREVIEW = OUT / "mock-stock-website-demo-preview.png"
VIDEO = OUT / "mock-stock-website-demo.webm"


doc = Document()
section = doc.sections[0]
section.top_margin = Inches(0.8)
section.bottom_margin = Inches(0.75)
section.left_margin = Inches(0.85)
section.right_margin = Inches(0.85)

styles = doc.styles
styles["Normal"].font.name = "맑은 고딕"
styles["Normal"]._element.rPr.rFonts.set(qn("w:eastAsia"), "맑은 고딕")
styles["Normal"].font.size = Pt(10.2)

for name, size, color in [
    ("Heading 1", 16, RGBColor(18, 32, 51)),
    ("Heading 2", 13, RGBColor(31, 95, 191)),
    ("Heading 3", 11.5, RGBColor(24, 34, 48)),
]:
    style = styles[name]
    style.font.name = "맑은 고딕"
    style._element.rPr.rFonts.set(qn("w:eastAsia"), "맑은 고딕")
    style.font.size = Pt(size)
    style.font.bold = True
    style.font.color.rgb = color


def shade_cell(cell, fill):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:fill"), fill)
    tc_pr.append(shd)


def set_cell_text(cell, text, bold=False, color=None):
    cell.text = ""
    p = cell.paragraphs[0]
    p.paragraph_format.space_after = Pt(0)
    r = p.add_run(str(text))
    r.font.name = "맑은 고딕"
    r._element.rPr.rFonts.set(qn("w:eastAsia"), "맑은 고딕")
    r.font.size = Pt(9.2)
    r.font.bold = bold
    if color:
        r.font.color.rgb = color
    cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER


def add_table(headers, rows, widths=None):
    table = doc.add_table(rows=1, cols=len(headers))
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.style = "Table Grid"
    for i, h in enumerate(headers):
        shade_cell(table.rows[0].cells[i], "122033")
        set_cell_text(table.rows[0].cells[i], h, bold=True, color=RGBColor(255, 255, 255))
    for row_index, row in enumerate(rows):
        cells = table.add_row().cells
        for i, value in enumerate(row):
            if row_index % 2 == 1:
                shade_cell(cells[i], "F6F9FD")
            set_cell_text(cells[i], value)
    if widths:
        for row in table.rows:
            for idx, width in enumerate(widths):
                row.cells[idx].width = Inches(width)
    doc.add_paragraph()
    return table


def add_bullets(items):
    for item in items:
        p = doc.add_paragraph(style="List Bullet")
        p.paragraph_format.space_after = Pt(3)
        p.add_run(item)


def add_numbered(items):
    for item in items:
        p = doc.add_paragraph(style="List Number")
        p.paragraph_format.space_after = Pt(3)
        p.add_run(item)


def add_code(text):
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Inches(0.15)
    p.paragraph_format.right_indent = Inches(0.15)
    p.paragraph_format.space_before = Pt(3)
    p.paragraph_format.space_after = Pt(6)
    r = p.add_run(text)
    r.font.name = "Consolas"
    r.font.size = Pt(8.8)
    shd = OxmlElement("w:shd")
    shd.set(qn("w:fill"), "F1F5F9")
    p._p.get_or_add_pPr().append(shd)


def paragraph(text):
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(6)
    p.add_run(text)


title = doc.add_paragraph()
title.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = title.add_run("Java 모의주식투자 웹앱 개인 프로젝트 보고서")
r.font.name = "맑은 고딕"
r._element.rPr.rFonts.set(qn("w:eastAsia"), "맑은 고딕")
r.font.size = Pt(22)
r.font.bold = True
r.font.color.rgb = RGBColor(18, 32, 51)

subtitle = doc.add_paragraph()
subtitle.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = subtitle.add_run("한국투자증권 KIS Open API 기반 모의투자 시스템")
r.font.name = "맑은 고딕"
r._element.rPr.rFonts.set(qn("w:eastAsia"), "맑은 고딕")
r.font.size = Pt(12)
r.font.color.rgb = RGBColor(102, 112, 133)

doc.add_paragraph()
add_table(["항목", "내용"], [
    ["프로젝트명", "Java 프로젝트 모의주식"],
    ["주요 기술", "Java HttpServer, HttpClient, Thread, Collection, JDBC, MySQL"],
    ["외부 데이터", "한국투자증권 KIS Open API"],
    ["저장 방식", "MySQL 테이블 기반 회원/보유/거래 기록 저장"],
    ["실행 주소", "http://localhost:8080/"],
    ["시연 영상", "deliverables/mock-stock-website-demo.webm"],
], widths=[1.7, 5.7])


doc.add_heading("1. 프로젝트 개요", level=1)
paragraph("본 프로젝트는 Java 표준 라이브러리 기반의 모의주식투자 웹앱이다. 사용자는 웹 브라우저에서 로그인한 뒤 국내 주식 종목을 확인하고, 관심 있는 종목을 클릭해 상세 정보를 본 다음 매수할 수 있다. 이미 산 종목은 보유 탭에서 수량을 지정해 매도한다.")
paragraph("핵심 목표는 단순한 주문 연습이 아니라 실제 시세, 거래량, 가격 그래프, 포트폴리오 손익을 한 화면 흐름으로 연결하는 것이다. 한국투자증권 KIS Open API를 이용해 국내주식 현재가와 거래량 정보를 가져오고, 사용자는 종목 상세 화면에서 가격 변화를 확인한 뒤 매수와 매도를 진행한다.")
add_bullets([
    "실제 외부 증권 API를 활용한 Java 웹 프로젝트 구현",
    "사용자 매수/매도 흐름과 포트폴리오 손익 계산 구현",
    "MySQL 저장을 통한 회원/보유/거래 기록 유지",
    "쓰레드 기반 시세 갱신과 소켓 구독 구조 실험",
    "검색, 즐겨찾기, 종목 상세, 가격 그래프 UI 구현",
    "다수 클래스/인터페이스 구조와 실제 실행 가능한 웹 UI 구현",
])


doc.add_heading("2. 제안발표 이후 주제 변화", level=1)
paragraph("초기 제안 단계에서는 Java 미니프로젝트의 기능을 웹으로 옮기고 기본적인 회원, 매매 기능을 구현하는 방향이었다. 진행 중 모의주식투자라는 주제에는 내부 샘플 가격보다 실제 주식 시세와 거래량이 더 적합하다고 판단했다.")
add_table(["구분", "제안발표 단계", "최종 구현"], [
    ["중심 주제", "Java 미니프로젝트 기능 구현", "KIS API 기반 모의주식투자 웹앱"],
    ["가격 데이터", "내부 샘플/모의 가격", "한국투자증권 KIS 현재가"],
    ["종목 수", "소수 종목", "거래량 상위 종목 중심"],
    ["화면", "기본 입력/출력 중심", "검색, 즐겨찾기, 상세 그래프, 포트폴리오"],
    ["저장", "메모리 중심", "MySQL 테이블 기반 저장"],
    ["목적", "기능 구현 연습", "실제 데이터 기반 투자 흐름 구현"],
], widths=[1.3, 2.6, 3.3])


doc.add_heading("3. 자바 기반 프로그램 설계", level=1)
paragraph("프로젝트는 Spring이나 React 없이 Java 표준 기능을 중심으로 구성했다. HttpServer로 웹 서버를 열고, HttpClient로 외부 API를 호출하며, HTML/CSS/JavaScript는 Java 문자열 템플릿에서 렌더링한다. 저장은 MySQL JDBC를 사용한다.")
add_table(["파일", "역할"], [
    ["MiniProjectApp.java", "서버 시작, 포트 설정, KIS/API/DB 초기화"],
    ["MiniHandler.java", "URL별 HTTP 요청 라우팅과 JSON 응답 처리"],
    ["MiniProject.java", "회원가입, 로그인, 매매, 포트폴리오, 시세 상태 관리"],
    ["DomainModels.java", "Member, Stock, Share, TradeLog 등 도메인 모델"],
    ["DatabaseIntegration.java", "MySQL 연결, 테이블 생성, 데이터 저장/로드"],
    ["KisIntegration.java", "KIS 토큰 발급, 거래량 순위, 현재가 조회, 시세 갱신"],
    ["StockCategoryProfiles.java", "업종·테마별 종목 분류 타입"],
    ["BrokerIntegration.java", "소켓 기반 시세 구독 흐름 실험"],
    ["WebPages.java", "HTML/CSS/JavaScript 화면 생성"],
    ["Json.java", "JSON 문자열 생성과 요청 body 파싱"],
], widths=[2.1, 5.2])

doc.add_heading("3.1 패키지/클래스 다이어그램 요약", level=2)
add_code("""MiniProjectApp
 ├─ MiniHandler ── MiniProject ── Member / Stock / Share / TradeLog
 │                 ├─ MySqlDatabase
 │                 ├─ KisQuoteClient / KisQuotePoller ── KIS Open API
 │                 └─ BrokerClient / BrokerServer ── TCP Socket
 └─ WebPages""")

doc.add_heading("3.2 주요 코드 일부", level=2)
paragraph("아래 코드는 발표 때 보여주기 위한 핵심 부분만 줄인 것이다. 전체 코드를 모두 설명하기보다 HTTP 요청이 들어오고, 외부 시세를 조회하고, 매수 결과를 저장하는 흐름을 중심으로 정리했다.")
add_code("""// MiniHandler.java - HTTP 요청을 기능별 메서드로 연결
if ("GET".equals(method) && "/api/state".equals(path)) {
    send(exchange, 200, "application/json", project.stateJson());
}
String json = switch (path) {
    case "/api/stock/buy" -> project.buyStock(body);
    case "/api/stock/sell" -> project.sellStock(body);
    default -> Json.obj("ok", false, "error", "없는 API");
};""")
add_code("""// KisIntegration.java - KIS 현재가 REST API 호출
HttpRequest request = HttpRequest.newBuilder(uri)
    .header("authorization", "Bearer " + token)
    .header("appkey", config.appKey)
    .header("tr_id", "FHKST01010100")
    .GET()
    .build();
HttpResponse<String> response = client.send(request, BodyHandlers.ofString());""")
add_code("""// MiniProject.java - 매수 처리와 거래 기록 저장
Stock stock = findStock(stockName);
long total = (long) stock.price * quantity;
if (currentMember.balance < total) return Json.obj("ok", false);
currentMember.balance -= total;
currentMember.shares.put(stockName, share.buy(quantity, total));
logs.add(new TradeLog(currentMember.uid, stockName, quantity, total, "구매"));
saveDatabase();""")
add_code("""// DatabaseIntegration.java - MySQL 트랜잭션 저장
try (Connection con = DriverManager.getConnection(url, user, password)) {
    con.setAutoCommit(false);
    insertMembers(con, members);
    insertShares(con, members);
    insertTradeLogs(con, logs);
    con.commit();
}""")

doc.add_heading("3.3 상속과 인터페이스 사용 이유", level=2)
paragraph("과제 조건상 클래스와 인터페이스 수가 많아야 했기 때문에 단순히 빈 클래스를 늘리기보다 종목 회사 설명과 업종 분류 구조를 분리했다. CompanyProfile은 회사명과 업종처럼 공통 속성을 갖는 추상 클래스이고, StockCategoryProfile은 업종명과 위험 설명을 제공하는 인터페이스다.")
add_table(["구분", "설계", "이유"], [
    ["추상 클래스", "CompanyProfile", "종목별 회사 설명 구조를 통일"],
    ["구현 클래스", "SamsungElectronicsProfile 등", "회사별 설명과 업종 정보를 분리 관리"],
    ["인터페이스", "StockCategoryProfile", "업종별 분류와 위험 설명 제공 규칙을 통일"],
    ["구현 클래스", "업종·테마별 Category 클래스", "종목을 업종과 테마 기준으로 설명할 수 있게 구성"],
], widths=[1.2, 2.5, 3.5])

doc.add_heading("3.4 AI vs 나의 역할", level=2)
add_table(["영역", "내가 한 결정", "AI 활용"], [
    ["주제 방향", "모의주식투자 웹사이트로 확정", "구현 방식 후보 정리"],
    ["데이터 선택", "한국투자증권 KIS Open API 사용 결정", "API 호출 구조와 Java 코드 작성 보조"],
    ["UI 요구", "종목 클릭 중심, 검색/즐겨찾기, 상세 매매 흐름 요구", "HTML/CSS/JS 구현 보조"],
    ["문제 발견", "가격 이상, 불필요 기능, README 문구, 서버 실행 문제 지적", "원인 분석, 코드 수정, 문서 정리"],
], widths=[1.3, 3.0, 3.0])


doc.add_heading("4. 데이터 흐름과 사용자 시나리오", level=1)
paragraph("사용자는 시장 시세 목록에서 거래량이 많은 종목을 확인하고, 검색이나 즐겨찾기로 원하는 종목을 찾는다. 종목을 클릭하면 현재가, 등락률, 거래량, 회사 설명, 가격 변화 그래프가 표시된다. 매수는 종목 상세 화면에서 진행하고, 매도는 보유 탭에서 사용자가 가진 수량을 기준으로 진행한다.")
add_numbered([
    "웹사이트 접속 후 로그인한다.",
    "시장 시세에서 거래량 상위 종목을 확인하거나 검색한다.",
    "관심 종목을 즐겨찾기에 추가하거나 상세 화면을 연다.",
    "상세 화면에서 가격 그래프를 확인한다.",
    "매수 수량을 입력해 주문한다.",
    "보유 탭에서 평가금액, 손익, 수익률을 확인한다.",
    "보유 종목을 수량 지정 후 매도한다.",
])
add_table(["단계", "내용"], [
    ["입력", "로그인 정보, 종목 선택, 즐겨찾기, 매수/매도 수량"],
    ["외부 입력", "KIS 현재가, 전일대비, 등락률, 누적 거래량"],
    ["처리", "잔액 확인, 보유 수량 확인, 매매 체결, 손익 계산"],
    ["저장", "회원 정보, 보유 주식, 거래 기록을 MySQL 테이블에 저장"],
    ["출력", "시장 시세, 종목 상세, 그래프, 포트폴리오, 거래 기록"],
], widths=[1.4, 5.8])


doc.add_heading("5. 사용자 UI / 화면", level=1)
paragraph("UI는 실제 투자 앱의 흐름을 참고하되 과제 프로젝트에서 설명하기 쉽도록 단순하게 정리했다. 사용자가 종목을 직접 고르고 판단한 뒤 매수하도록 만들었고, 매도는 보유 종목 기준으로만 진행되게 했다.")
add_bullets([
    "상단 요약: 보유 현금, 주식 평가액, 총자산, 실시간 손익, 수익률 표시",
    "시장 시세: 거래량 상위 종목을 10개 단위 페이지로 표시",
    "검색/즐겨찾기: 종목명, 코드, 업종 검색과 즐겨찾기 목록 분리",
    "종목 상세: 현재가, 등락률, 거래량, 회사 설명, 가격 그래프, 매수 입력",
    "보유 탭: 보유 종목별 평가금액, 손익, 수익률, 매도 수량 입력",
    "기록 탭: 매수/매도 시간, 종목, 수량, 금액 확인",
])
if PREVIEW.exists():
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run()
    r.add_picture(str(PREVIEW), width=Inches(6.4))
    caption = doc.add_paragraph("시연 영상 미리보기: 종목 상세, 매수 완료 메시지, 가격 그래프, 보유 종목 손익 화면")
    caption.alignment = WD_ALIGN_PARAGRAPH.CENTER
    caption.runs[0].font.size = Pt(8.5)
    caption.runs[0].font.color.rgb = RGBColor(102, 112, 133)


doc.add_heading("6. 한 달간의 시행착오와 문제 해결", level=1)
add_table(["문제", "해결"], [
    ["저장 문제", "서버 재시작 시 데이터가 사라지는 문제를 MySQL 저장 구조로 변경"],
    ["인코딩 문제", "javac -encoding UTF-8, HTML 응답 charset, 문서 파일 인코딩을 UTF-8 기준으로 정리"],
    ["KIS API 키 설정", "KIS_APP_KEY, KIS_APP_SECRET, KIS_BASE_URL 환경변수를 사용하도록 분리"],
    ["뉴스 기능 제외", "외부 뉴스 API 키 관리 부담과 기사 품질 편차 때문에 최종 버전에서는 제거"],
    ["가격 표시 문제", "API 갱신 전 초기값과 실제 응답값이 섞이지 않도록 갱신 흐름을 확인"],
    ["종목 수 문제", "약 2700개 전체 종목을 계속 갱신하지 않고 거래량 상위 종목 중심으로 제한"],
    ["UI 복잡도", "목적과 맞지 않는 기능은 줄이고 검색, 즐겨찾기, 상세 매매 흐름을 강화"],
    ["서버 실행", "VS Code 작업, PowerShell 실행 스크립트, 시작 작업 등록 스크립트를 준비"],
], widths=[2.1, 5.1])


doc.add_heading("7. Java의 다양한 클래스 활용", level=1)
add_table(["Java 기능", "사용 내용"], [
    ["HttpServer", "별도 프레임워크 없이 웹 서버 구현"],
    ["HttpClient", "KIS API 호출"],
    ["Thread", "백그라운드 시세 갱신과 소켓 서버 흐름 처리"],
    ["ConcurrentHashMap", "회원, 종목, 보유 주식 상태 동시 접근 처리"],
    ["ArrayList / List", "거래 기록, 가격 히스토리, 종목 목록 처리"],
    ["LinkedHashMap", "조회 순서와 JSON 응답 순서 유지"],
    ["JDBC DriverManager", "MySQL 연결과 SQL 실행"],
    ["Files / Path", "초기 설정, 스크립트, 문서 산출물 관리"],
    ["LocalDateTime", "거래 시간, 시세 갱신 시각 표시"],
    ["AtomicLong / AtomicInteger", "회원 번호, 로그 번호 등 식별자 생성"],
], widths=[2.2, 5.0])


doc.add_heading("8. 데이터 처리", level=1)
paragraph("주식 현재가와 거래량은 KIS API를 기준으로 가져오도록 구현했다. 다만 실제 서비스처럼 모든 종목을 계속 갱신하면 개인 PC에서 실행하는 과제 프로젝트에는 부담이 커진다. 그래서 거래량 상위 종목을 우선 선별하고, 화면에서 필요한 종목 중심으로 가격 정보를 갱신하는 방식이 더 적합하다고 판단했다.")
add_table(["데이터", "처리 방식"], [
    ["주식 시세", "KIS Open API 현재가 REST 조회"],
    ["종목 선별", "거래량 기준 상위 종목 중심으로 자동 갱신"],
    ["실시간성", "전체 종목 매초 조회 대신 상위 목록 중심 갱신과 소켓 구독 구조 실험"],
    ["소켓", "모의 증권사 서버의 가격 Tick 구독 구조"],
    ["사용자 데이터", "MySQL members, shares, trade_logs 테이블 저장"],
    ["가격 그래프", "서버가 보관한 최근 가격 히스토리 표시"],
], widths=[2.0, 5.2])

doc.add_heading("8.1 MySQL 테이블 구상", level=2)
add_code("""members(uid, id, password, name, balance)
shares(member_uid, stock_name, quantity, average_price)
trade_logs(id, member_uid, type, stock_name, quantity, price, created_at)""")


doc.add_heading("9. 소켓 서버 구조", level=1)
paragraph("프로젝트에는 REST API만 사용하는 구조에서 한 단계 더 나아가기 위해 모의 증권사 소켓 서버 구조도 추가했다. BrokerIntegration은 ServerSocket과 클라이언트 구독 명령을 이용해 특정 종목의 가격 Tick을 전달하는 구조다. 실제 한국투자증권 WebSocket 실시간 체결가를 완전히 대체하는 것은 아니지만, 멀티 클라이언트 구독과 실시간 전송 구조를 Java Thread 기반으로 실험했다는 점에 의미가 있다.")
add_bullets([
    "서버는 지정 포트에서 소켓 연결을 받고 클라이언트별 요청을 처리한다.",
    "클라이언트는 구독 명령을 보내고 서버는 가격 Tick을 전송한다.",
    "웹 서버는 이 흐름을 받아 포트폴리오 평가와 가격 히스토리에 반영할 수 있다.",
    "향후 개선 시 KIS WebSocket 실시간 체결가 API와 같은 방식으로 확장할 수 있다.",
])


doc.add_heading("10. 실제 동작 기능", level=1)
add_table(["기능", "현재 구현 상태"], [
    ["로그인/회원가입", "테스트 계정과 신규 회원 생성 가능"],
    ["시장 시세", "거래량 상위 종목 중심 표시, 10개 단위 페이지 구성"],
    ["검색", "종목명, 코드, 업종 검색 가능"],
    ["즐겨찾기", "브라우저 localStorage 기반 관심 종목 분리 표시"],
    ["종목 상세", "현재가, 등락률, 거래량, 회사 설명, 그래프 표시"],
    ["매수", "선택 종목 상세 화면에서 수량 입력 후 매수"],
    ["매도", "보유 탭에서 보유 수량 기준으로 매도"],
    ["포트폴리오", "현금, 평가금액, 총자산, 손익, 수익률 계산"],
    ["거래 기록", "매수/매도 기록을 MySQL에 저장"],
], widths=[1.9, 5.3])


doc.add_heading("11. 1분 이내 시연 영상", level=1)
paragraph("시연 영상은 실제 코드 설명보다 사용자가 보는 화면 흐름을 보여주는 용도이다. 이번 제출용 영상은 약 16초 길이로 접속, 종목 선택, 가격 그래프 확인, 매수, 보유 종목 손익 확인 장면이 들어 있다.")
add_table(["구간", "보여줄 화면", "설명"], [
    ["0~5초", "웹사이트 접속과 상단 요약", "Java 서버에서 웹앱이 실행되는 점"],
    ["5~9초", "종목 검색과 상세 화면", "종목 클릭 중심의 매매 흐름"],
    ["9~12초", "가격 그래프와 매수", "선택 종목의 가격 변화와 주문"],
    ["12~16초", "보유 탭과 손익", "매수 후 평가금액과 수익률 표시"],
], widths=[1.2, 2.4, 3.6])
paragraph(f"영상 파일 위치: {VIDEO.as_posix() if VIDEO.exists() else 'deliverables/mock-stock-website-demo.webm'}")


doc.add_heading("12. 발표 시 강조할 점", level=1)
paragraph("발표에서는 기능을 많이 넣었다는 설명보다 왜 이 구조가 모의주식투자라는 목적에 맞는지를 먼저 말하는 것이 좋다. 실제 시세와 거래량을 보고 종목을 고른 뒤 매수/매도하는 흐름이 프로젝트의 중심이다.")
add_bullets([
    "실제 증권 API를 붙여 모의투자 화면의 목적을 분명하게 만들었다.",
    "Java 표준 기능만으로 웹 서버, 외부 API, DB 저장, 쓰레드, 소켓 구조를 연결했다.",
    "사용자가 불편하게 느낄 수 있는 드롭다운 주문 방식을 종목 클릭 중심 UI로 바꿨다.",
    "약 2700개 전체 종목을 무리하게 갱신하지 않고 거래량 상위 종목 중심으로 현실적인 범위를 잡았다.",
    "저장, 인코딩, API 키, 가격 표시, 뉴스 기능 제외 판단 같은 실제 개발 문제를 해결했다.",
])


doc.add_heading("13. 마무리와 향후 개선", level=1)
paragraph("최종 결과물은 Java로 직접 만든 모의주식투자 웹앱이다. 한국투자증권 KIS API로 시세와 거래량을 가져오고, MySQL에 회원·보유주식·거래기록을 저장한다. 사용자는 종목을 검색하거나 즐겨찾기하고, 상세 화면에서 가격 그래프를 확인한 뒤 매수/매도를 진행할 수 있다.")
paragraph("향후 개선한다면 KIS WebSocket 실시간 체결가 연동, 비밀번호 해시와 세션 보안 강화, 패키지 분리, 테스트 코드 추가, HTTPS 배포 환경 적용을 우선적으로 진행할 수 있다.")


doc.save(OUT / os.environ.get("REPORT_OUTPUT", "Java_모의주식투자_개인프로젝트_보고서.docx"))
