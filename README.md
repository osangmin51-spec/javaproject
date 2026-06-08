# Java 모의주식투자 웹앱

Java 표준 라이브러리만 사용해 구현한 모의주식투자 웹 프로젝트입니다. 한국투자증권 KIS Open API로 국내주식 현재가, 등락률, 거래량을 받아오고, 회원별 자산과 보유 주식, 거래 기록을 저장해 포트폴리오 손익을 계산합니다.

## 주요 특징

- 회원가입, 로그인, 로그아웃
- 파일 기반 DB 저장
- 한국투자증권 KIS Open API 현재가 연동
- 실시간 가격, 등락률, 거래량 갱신
- 주식 매수 / 매도
- 국내 주요 종목 100개 제공
- 시장 시세 목록을 거래량이 많은 인기 종목 순으로 정렬
- 보유 종목별 평가금액, 손익, 수익률 계산
- 거래 기록 조회
- 종목별 뉴스 조회
- 종목별 최근 가격 변화 추이 그래프
- 뉴스 제목과 본문 키워드 기반 호재/악재/중립 태그
- 게시판과 댓글

## 화면 구성

웹 화면은 매매와 포트폴리오 확인을 중심으로 구성됩니다.

- 상단: 현금, 주식 평가액, 총 자산, 손익, 수익률
- 매매 영역: 종목 선택, 수량 입력, 매수/매도 버튼
- 종목 상세: 종목 클릭 시 종목코드, 시장, 업종, 회사 설명, 현재가, 변동률, 가격 추이 그래프, 관련 뉴스 표시
- 탭 영역: 보유, 기록, 게시판

## 데이터 저장

서버를 껐다 켜도 주요 데이터가 유지되도록 `data/` 폴더에 TSV 파일로 저장합니다.

| 파일 | 저장 내용 |
| --- | --- |
| `data/members.tsv` | 회원 번호, 이름, 아이디, 비밀번호, 현금, 진행 날짜 |
| `data/shares.tsv` | 회원별 보유 종목, 수량, 총 매입가 |
| `data/trades.tsv` | 회원별 거래 종목, 수량, 금액, 거래 구분, 시간 |

## 실시간 가격 구조

앱 실행 시 Java 웹 서버가 한국투자증권 KIS Open API를 주기적으로 호출하고, 브라우저는 `/api/state`를 통해 최신 가격과 포트폴리오 손익을 확인합니다.

```text
웹 화면 -> /api/state -> Java 웹 서버 -> KisQuotePoller -> 한국투자증권 KIS REST API
```

- `KisQuotePoller`: 등록된 100개 종목의 현재가를 한국투자증권 KIS REST API로 조회합니다.
- Java 웹 서버: 현재가, 전일대비, 등락률, 누적 거래량을 종목 데이터에 반영합니다.
- 브라우저: 1초마다 `/api/state`를 호출해 최신 가격, 거래량 순위, 포트폴리오 손익을 보여줍니다.

## 외부 시세 연동

현재 프로젝트는 한국투자증권 KIS Open API를 통해 국내주식 현재가를 조회하는 것을 전제로 합니다. 실제 종목 가격, 전일대비, 등락률, 누적 거래량을 가져오기 때문에 모의투자 화면이 단순 랜덤 데이터가 아니라 외부 증권 시세 기반으로 동작합니다.

한국투자증권 Open API를 선택한 이유는 REST/WebSocket 방식을 제공해 Java, Python, 웹 프로젝트와 연결하기 쉽고, 공식 개발자센터에서 API 문서, 테스트베드, GitHub 샘플코드를 함께 확인할 수 있기 때문입니다. 개발자센터에서는 국내주식, 해외주식 등 여러 투자 상품의 API 문서와 테스트 환경, 샘플코드를 제공하므로 모의주식투자 프로젝트의 외부 데이터 연동 설명에 활용하기 좋습니다.

KIS 연동을 위해 실행 전에 설정해야 하는 환경변수:

```powershell
$env:KIS_APP_KEY="발급받은_APP_KEY"
$env:KIS_APP_SECRET="발급받은_APP_SECRET"
$env:KIS_BASE_URL="https://openapi.koreainvestment.com:9443"
java -cp out MiniProjectApp 8080
```

사용 API:

| 구분 | 값 |
| --- | --- |
| 토큰 발급 | `POST /oauth2/tokenP` |
| 국내주식 현재가 | `GET /uapi/domestic-stock/v1/quotations/inquire-price` |
| 거래 ID | `FHKST01010100` |
| 시장 구분 | `FID_COND_MRKT_DIV_CODE=J` |

모의투자/가상투자 도메인을 사용할 경우 `KIS_BASE_URL` 값을 한국투자증권에서 제공하는 모의투자 URL로 바꿉니다.

## 가격 지표 해석

이 프로젝트의 가격 정보는 한국투자증권 KIS API에서 받은 현재가, 전일대비, 등락률, 누적 거래량을 기준으로 표시합니다.

`nextFluct` 값은 실제 미래 가격 예측값이 아닙니다. 현재 등락률을 배율로 바꾼 내부 계산값입니다.

예를 들어 등락률이 `-6.40%`라면 `nextFluct`는 `1 + (-6.40 / 100) = 0.936`이고, API 응답에서는 반올림되어 `0.94`처럼 보입니다. 즉 뉴스, 거래량, 차트 패턴, 재무 데이터로 미래 변동성을 예측한 값이 아니라 현재 가격 변동을 배율로 표현한 보조 값입니다.

실제 예측형 변동성 지표를 만들려면 최근 N일 가격 데이터, 일별 수익률 표준편차, 거래량 변화, 뉴스 호재/악재 태그, 시장 전체 변동성 같은 데이터를 별도로 조합해야 합니다.

## 뉴스 연동

종목을 클릭하면 `/api/news` API가 해당 회사 관련 뉴스를 조회합니다.

네이버 검색 뉴스 API를 사용하려면 실행 전에 환경변수를 설정합니다.

```powershell
$env:NAVER_CLIENT_ID="발급받은_Client_ID"
$env:NAVER_CLIENT_SECRET="발급받은_Client_Secret"
java -cp out MiniProjectApp 8080
```

네이버 뉴스 API 키가 설정되지 않은 경우 뉴스 영역에는 키 설정 안내가 표시됩니다.

## 실행 방법

Java 21 이상을 권장합니다.

```powershell
$sources = Get-ChildItem -Filter *.java | Where-Object { $_.Name -ne "MockStockApp.java" } | ForEach-Object { $_.FullName }
javac -encoding UTF-8 -d out $sources
java -cp out MiniProjectApp 8080
```

또는 PowerShell 스크립트:

```powershell
.\scripts\run.ps1
```

브라우저에서 접속:

```text
http://localhost:8080/
```

## 기본 계정

앱 시작 시 테스트 계정이 자동 생성됩니다.

| 아이디 | 비밀번호 |
| --- | --- |
| `test1` | `1234` |

## API 목록

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/` | 웹 화면 |
| `GET` | `/api/state` | 전체 화면 상태 |
| `GET` | `/api/news` | 선택 종목 뉴스 조회 |
| `POST` | `/api/register` | 회원가입 |
| `POST` | `/api/login` | 로그인 |
| `POST` | `/api/logout` | 로그아웃 |
| `POST` | `/api/stock/buy` | 주식 구매 |
| `POST` | `/api/stock/sell` | 주식 판매 |
| `POST` | `/api/day/next` | 다음날 진행 |
| `POST` | `/api/board/write` | 게시글 작성 |
| `POST` | `/api/board/delete` | 게시글 삭제 |
| `POST` | `/api/comment/write` | 댓글 작성 |
| `POST` | `/api/comment/delete` | 댓글 삭제 |

## 기술 구성

- Java
- JDK 내장 `HttpServer`
- JDK 내장 `HttpClient`
- 한국투자증권 KIS REST API
- Thread 기반 시세 폴링
- TSV 파일 저장
- HTML / CSS / JavaScript

## 코드 구조

| 파일 | 역할 |
| --- | --- |
| `MiniProjectApp.java` | 앱 시작점 |
| `MiniHandler.java` | HTTP 라우팅과 API 요청 처리 |
| `MiniProject.java` | 회원, 매매, 포트폴리오, DB 저장 흐름 |
| `DomainModels.java` | 회원, 주식, 보유 주식, 게시글, 거래 기록 모델 |
| `KisIntegration.java` | 한국투자증권 KIS 토큰 발급과 국내주식 현재가 조회 |
| `BrokerIntegration.java` | 개발 테스트용 보조 시세 처리 |
| `NewsIntegration.java` | 네이버 검색 뉴스 API 연동 |
| `CompanyProfile.java` | 회사 프로필/뉴스 키워드 인터페이스 |
| `CompanyProfiles.java` | 회사 프로필 구현 클래스 |
| `NewsKeywordProfiles.java` | 뉴스 키워드 구현 클래스 |
| `WebPages.java` | 웹 화면 HTML/CSS/JavaScript 렌더링 |
| `Json.java` | JSON 응답 생성과 요청 body 파싱 |
