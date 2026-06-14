# Java 모의주식투자 웹앱

Java 표준 라이브러리 중심으로 만든 모의주식투자 웹 프로젝트입니다. 사용자는 국내 주식 종목을 확인하고, 종목 상세 화면에서 현재가·거래량·가격 추이를 본 뒤 매수/매도를 연습할 수 있습니다. 보유 종목의 평가금액과 손익률은 서버에서 관리하는 최신 시세를 기준으로 계산됩니다.

## 주요 기능

- 회원가입, 로그인, 로그아웃
- 한국투자증권 KIS Open API 기반 국내주식 현재가 조회
- 거래량 상위 종목 중심의 시장 시세 목록
- 시장 시세 기본 200개 종목을 10개씩 페이지로 표시
- 종목 클릭 후 상세 정보, 가격 변화 그래프, 매수/매도 입력 표시
- 보유 종목별 평가금액, 손익, 수익률 계산
- 거래 기록 저장
- MySQL 저장소 지원
- KIS WebSocket 국내주식 체결 구독 시도 지원
- VS Code, IntelliJ IDEA, Windows 시작 작업 실행 스크립트 제공

## 실행 방법

Java 21 이상을 권장합니다.

```powershell
$sources = Get-ChildItem -Filter *.java | Where-Object { $_.Name -ne "MockStockApp.java" } | ForEach-Object { $_.FullName }
javac -encoding UTF-8 -d out $sources
java -cp "out;lib/*" MiniProjectApp 8080
```

브라우저 접속 주소:

```text
http://localhost:8080/
```

기본 테스트 계정:

| 아이디 | 비밀번호 |
| --- | --- |
| `test1` | `1234` |

## KIS Open API 설정

프로젝트의 시세 데이터는 한국투자증권 KIS Open API 연동을 기준으로 설계했습니다. 실행 전에 발급받은 값을 환경변수로 넣습니다.

```powershell
$env:KIS_APP_KEY="발급받은_APP_KEY"
$env:KIS_APP_SECRET="발급받은_APP_SECRET"
$env:KIS_BASE_URL="https://openapi.koreainvestment.com:9443"
$env:KIS_MARKET_LIMIT="200"
$env:KIS_POLL_LIMIT="100"
java -cp "out;lib/*" MiniProjectApp 8080
```

사용하는 REST API:

| 구분 | 값 |
| --- | --- |
| 토큰 발급 | `POST /oauth2/tokenP` |
| 국내주식 현재가 | `GET /uapi/domestic-stock/v1/quotations/inquire-price` |
| 국내주식 거래량 순위 | `GET /uapi/domestic-stock/v1/quotations/volume-rank` |
| 현재가 거래 ID | `FHKST01010100` |
| 거래량 순위 거래 ID | `FHPST01710000` |

종목 수 기준:

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `KIS_MARKET_LIMIT` | `200` | 시장 시세 화면에 보여줄 거래량 상위 종목 수 |
| `KIS_POLL_LIMIT` | `100` | REST 현재가를 반복 조회할 상위 종목 수 |

전체 2700개 종목을 매초 갱신하면 API 호출, JSON 응답 크기, 브라우저 필터링이 모두 `O(n)`으로 커집니다. 그래서 이 프로젝트는 화면 표시 범위를 200개로 늘리되, 반복 현재가 조회는 거래량 상위 100개로 제한했습니다. 검색과 즐겨찾기는 200개 목록 안에서 동작하고, 화면 렌더링은 페이지당 10개만 그립니다.

## WebSocket 시세 구독

REST 방식은 일정 주기마다 서버가 API를 다시 호출하는 구조라 완전한 실시간 체결 전송에는 한계가 있습니다. 그래서 `KIS_USE_WEBSOCKET=true`일 때는 KIS WebSocket 승인키를 발급받고 국내주식 체결 구독을 먼저 시도합니다.

```powershell
$env:KIS_USE_WEBSOCKET="true"
$env:KIS_WS_URL="ws://ops.koreainvestment.com:21000"
$env:KIS_WS_MAX_SUBSCRIPTIONS="100"
java -cp "out;lib/*" MiniProjectApp 8080
```

동작 흐름:

```text
MiniProjectApp
  -> KisWebSocketQuoteClient
  -> KIS WebSocket 승인키 발급
  -> 국내주식 체결 구독 요청
  -> 체결 메시지를 BrokerTick으로 변환
  -> MiniProject.applyKisWebSocketQuote()
  -> 화면 /api/state 갱신
```

WebSocket 모드에서도 시작 직전에 거래량 상위 100개를 조회해 구독 대상을 자동 선별합니다. WebSocket 연결 자체가 실패하면 기존 REST 폴링 방식으로 전환됩니다. 실제 구독 가능 종목 수, 메시지 필드 순서, 운영/모의 환경 주소는 KIS 개발자센터의 WebSocket 문서와 테스트베드 기준으로 확인해야 합니다.

## MySQL 저장소

회원, 보유 종목, 거래 기록은 MySQL 테이블에 저장합니다. `MYSQL_URL`, `MYSQL_USER` 환경변수가 없으면 서버는 실행 테스트용 메모리 모드로 동작하지만, 서버를 종료하면 거래 데이터가 저장되지 않습니다. 즉 현재 저장 구조의 기준은 TSV 파일이 아니라 MySQL입니다.

```powershell
$env:MYSQL_URL="jdbc:mysql://localhost:3306/mock_stock?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul"
$env:MYSQL_USER="root"
$env:MYSQL_PASSWORD="비밀번호"
java -cp "out;lib/*" MiniProjectApp 8080
```

MySQL Connector/J는 `lib/mysql-connector-j-9.7.0.jar`에 포함했습니다. PowerShell 실행 스크립트와 VS Code 실행 설정은 `lib/*`를 클래스패스에 자동으로 넣습니다. 직접 실행할 때는 아래처럼 실행합니다.

```powershell
$sources = Get-ChildItem -Filter *.java | Where-Object { $_.Name -ne "MockStockApp.java" } | ForEach-Object { $_.FullName }
javac -encoding UTF-8 -d out $sources
java -cp "out;lib/*" MiniProjectApp 8080
```

서버 실행 시 테이블이 없으면 자동으로 생성합니다. 같은 스키마는 [`docs/mysql-schema.sql`](docs/mysql-schema.sql)에도 정리했습니다.

저장 테이블:

| 테이블 | 내용 |
| --- | --- |
| `members` | 회원 번호, 이름, 아이디, 비밀번호, 현금 |
| `shares` | 회원별 보유 종목, 수량, 총 매입금액 |
| `trade_logs` | 회원별 매수/매도 기록 |

## 화면 구성

- 상단 요약: 보유 현금, 주식 평가액, 총자산, 실시간 손익, 수익률, 시세 기준 시간
- 시장 시세: 거래량 상위 종목을 10개씩 페이지로 표시
- 종목 상세: 회사 설명, 현재가, 등락률, 거래량, 가격 추이 그래프
- 매매 영역: 선택한 종목의 매수 수량 입력, 보유 중인 경우 매도 수량 입력
- 보유 탭: 보유 종목별 수량, 평균단가, 현재가, 평가금액, 손익률, 매도
- 기록 탭: 매수/매도 거래 기록

## 코드 구조

| 파일 | 역할 |
| --- | --- |
| `MiniProjectApp.java` | 서버 시작, KIS REST/WebSocket 시세 스레드 시작 |
| `MiniHandler.java` | HTTP 라우팅과 API 요청 처리 |
| `MiniProject.java` | 회원, 매매, 포트폴리오, 저장 흐름 관리 |
| `DomainModels.java` | 회원, 종목, 보유 주식, 거래 기록, 게시글 모델 |
| `KisIntegration.java` | KIS 토큰, 현재가 REST, 거래량 순위, WebSocket 구독 |
| `DatabaseIntegration.java` | MySQL 저장소, 스키마 생성, 데이터 로드/저장 |
| `CompanyProfile.java` | 회사 프로필 추상 클래스 |
| `CompanyProfiles.java` | 종목별 회사 설명 클래스 |
| `StockCategoryProfiles.java` | 업종·테마별 종목 분류 클래스 |
| `WebPages.java` | HTML, CSS, JavaScript 화면 렌더링 |
| `Json.java` | JSON 응답 생성과 요청 body 파싱 |

## API 목록

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/` | 메인 화면 |
| `GET` | `/api/state` | 전체 화면 상태 |
| `POST` | `/api/register` | 회원가입 |
| `POST` | `/api/login` | 로그인 |
| `POST` | `/api/logout` | 로그아웃 |
| `POST` | `/api/stock/buy` | 주식 매수 |
| `POST` | `/api/stock/sell` | 주식 매도 |
| `POST` | `/api/board/write` | 게시글 작성 |
| `POST` | `/api/board/delete` | 게시글 삭제 |
| `POST` | `/api/comment/write` | 댓글 작성 |
| `POST` | `/api/comment/delete` | 댓글 삭제 |

## 개발 환경 실행 보조

VS Code에서 폴더를 열면 `.vscode/tasks.json`의 서버 시작 작업을 사용할 수 있습니다.

```text
Terminal > Run Task > start MiniProjectApp server
```

IntelliJ IDEA에는 `.idea/runConfigurations/MiniProjectApp.xml` 실행 설정이 들어 있습니다.

PowerShell 스크립트:

```powershell
.\scripts\start-server.ps1
.\scripts\stop-server.ps1
```

Codex 안에서 실행할 때는 외부 API 연결이나 백그라운드 프로세스 유지가 일반 터미널과 다를 수 있습니다. 그래서 서버는 KIS 연결이 실패하면 내장 모의 증권사 소켓으로 자동 전환되도록 구성했습니다. 또한 `MiniProjectApp`은 메인 스레드를 유지해서 서버가 바로 종료되지 않게 되어 있습니다.

Windows 로그인 시 자동 실행을 등록하려면:

```powershell
.\scripts\install-startup-task.ps1
```

자동 실행을 제거하려면:

```powershell
.\scripts\uninstall-startup-task.ps1
```

## 발표/보고서 자료

발표와 보고서 초안은 `docs/`와 `deliverables/` 폴더에 정리되어 있습니다.

| 경로 | 내용 |
| --- | --- |
| `docs/project-report.md` | 보고서 본문 초안 |
| `docs/presentation-slides.md` | 발표 흐름과 슬라이드 구성 |
| `deliverables/Java_모의주식투자_발표자료.pptx` | 발표용 PPT 파일 |
| `deliverables/Java_모의주식투자_개인프로젝트_보고서.docx` | 보고서 Word 파일 |
