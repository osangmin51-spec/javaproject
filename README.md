# Java 프로젝트 모의주식

Java 표준 라이브러리 중심으로 만든 모의주식투자 웹앱입니다. 사용자는 브라우저에서 국내 주식 종목을 검색하고, 종목 상세 화면에서 현재가와 가격 변화 그래프를 확인한 뒤 매수/매도를 연습할 수 있습니다. 보유 종목의 평가금액, 손익, 수익률은 서버에서 계산해 화면에 보여줍니다.

## 주요 기능

- 회원가입, 로그인, 로그아웃
- Salt 기반 SHA-256 비밀번호 해시 저장
- HttpOnly 쿠키 기반 로그인 세션 관리
- 한국투자증권 KIS Open API 기반 국내주식 현재가 조회 구조
- 거래량 상위 종목 중심의 시장 시세 목록
- 시장 시세 종목 10개 단위 페이지 표시
- 종목 검색, 즐겨찾기, 종목 상세 화면
- 가격 변화 그래프, 회사 설명, 매수/매도 입력
- 보유 종목 평가금액, 손익, 수익률 계산
- MySQL 회원/보유/거래 기록 저장
- KIS WebSocket 국내주식 체결 구독 시도 및 REST fallback
- VS Code, IntelliJ IDEA, Windows 실행 스크립트 제공

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

전체 종목을 계속 갱신하면 API 호출, JSON 응답 크기, 브라우저 필터링이 모두 커집니다. 그래서 화면 표시 범위는 거래량 상위 종목 중심으로 제한하고, 화면 렌더링은 페이지당 10개만 처리합니다.

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

WebSocket 모드에서도 시작 직전에 거래량 상위 종목을 조회해 구독 대상을 자동 선별합니다. WebSocket 연결 시작이 실패하거나 실행 중 오류가 발생하면 기존 REST 폴링 방식으로 전환됩니다.

다만 실제 구독 성공 여부, 구독 가능 종목 수, 메시지 필드 순서, 운영/모의 환경 주소는 KIS 개발자센터의 WebSocket 문서와 테스트베드 기준으로 별도 확인이 필요합니다.

## 보안과 세션

로그인 성공 시 서버가 `MSTOCK_SESSION` 세션 토큰을 발급하고 브라우저에는 `HttpOnly` 쿠키로 저장합니다. API 요청은 이 쿠키를 기준으로 회원을 찾습니다.

비밀번호는 회원가입 시 평문으로 저장하지 않고 `Salt + SHA-256` 형식으로 저장합니다. 이전 실행 데이터에 평문 비밀번호가 남아 있더라도 로그인에 성공하면 해시 형식으로 자동 업그레이드합니다.

아직 실제 배포 수준의 보안까지 완성된 것은 아닙니다. HTTPS, 세션 만료 시간, CSRF 방어는 향후 배포 단계에서 추가해야 합니다.

## MySQL 저장소

회원, 보유 종목, 거래 기록은 MySQL 테이블에 저장할 수 있습니다. `MYSQL_URL`, `MYSQL_USER` 환경변수가 없으면 테스트용 메모리 모드로 실행됩니다.

```powershell
$env:MYSQL_URL="jdbc:mysql://localhost:3306/mock_stock?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul"
$env:MYSQL_USER="root"
$env:MYSQL_PASSWORD="비밀번호"
java -cp "out;lib/*" MiniProjectApp 8080
```

MySQL Connector/J는 `lib/mysql-connector-j-9.7.0.jar`에 포함되어 있습니다.

저장 테이블:

| 테이블 | 내용 |
| --- | --- |
| `members` | 회원 번호, 이름, 아이디, 비밀번호 해시, 현금 |
| `shares` | 회원별 보유 종목, 수량, 총 매입금액 |
| `trade_logs` | 회원별 매수/매도 기록 |

## 코드 구조

| 파일 | 역할 |
| --- | --- |
| `MiniProjectApp.java` | 서버 시작, KIS REST/WebSocket 시세 스레드 시작 |
| `MiniHandler.java` | HTTP 라우팅, API 요청 처리, 세션 쿠키 처리 |
| `MiniProject.java` | 회원, 세션, 매매, 포트폴리오, 저장 흐름 관리 |
| `DomainModels.java` | 회원, 종목, 보유 주식, 거래 기록, 게시글 모델 |
| `KisIntegration.java` | KIS 토큰, 현재가 REST, 거래량 순위, WebSocket 구독 |
| `DatabaseIntegration.java` | MySQL 저장소, 스키마 생성, 데이터 로드/저장 |
| `PasswordHasher.java` | 비밀번호 Salt 생성, SHA-256 해시, 검증 |
| `BrokerIntegration.java` | 모의 증권사 소켓 서버와 시세 Tick 구독 |
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

## 남은 보완점

- KIS WebSocket은 승인키 발급, 구독 메시지 전송, 체결 메시지 파싱, REST fallback까지 구현했지만 실제 테스트베드 구독 성공 여부는 별도 환경에서 확인해야 합니다.
- 현재 소스는 과제 제출과 컴파일 검증을 쉽게 하기 위해 루트 디렉터리 중심으로 유지했습니다. 이후에는 `controller`, `service`, `repository`, `model` 패키지로 분리하는 것이 좋습니다.
- HTTPS, 세션 만료 시간, CSRF 방어, 테스트 코드, 업종별 위험 등급 UI 표시, 개인화 관심종목 추천은 향후 개선 항목입니다.

## 발표/보고서 자료

| 경로 | 내용 |
| --- | --- |
| `deliverables/Java_모의주식투자_발표자료.pptx` | 발표용 PPT |
| `deliverables/Java_모의주식투자_개인프로젝트_보고서.docx` | 개인 프로젝트 보고서 |
| `deliverables/mock-stock-website-demo.webm` | 웹사이트 시연 영상 |
