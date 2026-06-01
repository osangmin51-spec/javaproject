# 모의주식 투자 웹사이트

Java 표준 라이브러리만으로 만든 모의주식투자 웹사이트입니다. 별도 프레임워크 없이 JDK 내장 `HttpServer`를 사용해 웹 페이지와 API를 제공하며, S&P 500 구성종목을 가져와 모의 시세 기반으로 주문, 관심종목, 포트폴리오를 연습할 수 있습니다.

> 이 프로젝트는 학습용 모의투자 서비스입니다. S&P 500 종목 목록은 공개 웹 데이터를 가져오지만, 가격과 등락률은 실제 실시간 시세가 아니라 시뮬레이션 값입니다. 실제 투자 판단에는 사용하면 안 됩니다.

## 주요 기능

- S&P 500 구성종목 import
- 등락률 상위 100개 종목 표시
- 10개씩 총 10페이지 페이지네이션
- 시장가/지정가 모의 매수·매도 주문
- 예수금, 보유 종목, 평가금액, 손익 표시
- 사용자가 직접 추가/삭제하는 관심종목
- 가짜 뉴스/가짜 순위표 제거
- VS Code 실행 설정 포함
- GitHub Actions로 컴파일 및 Java 타입 수 검증

## 기술 스택

- Java 21 이상 권장
- JDK 내장 `com.sun.net.httpserver.HttpServer`
- Java `HttpClient`
- HTML, CSS, Vanilla JavaScript
- PowerShell 실행 스크립트
- GitHub Actions CI

## 실행 방법

### VS Code에서 실행

1. `mock-stock-app.code-workspace` 파일을 VS Code로 엽니다.
2. `Ctrl + Shift + B`로 `compile` 작업을 실행합니다.
3. 터미널에서 아래 명령을 실행합니다.

```powershell
.\scripts\run.ps1
```

브라우저에서 접속합니다.

```text
http://localhost:8080
```

### 직접 실행

```powershell
javac -encoding UTF-8 -d out MockStockApp.java
java -cp out MockStockApp 8080
```

포트를 바꾸고 싶으면 마지막 숫자만 바꾸면 됩니다.

```powershell
java -cp out MockStockApp 9090
```

## 화면 구성

### 계좌 현황

예수금, 보유 종목 평가금액, 총자산, 수익률을 보여줍니다.

### 주문하기

종목코드, 매매구분, 주문유형, 수량, 지정가를 입력해 모의 주문을 넣습니다.

- `BUY`: 매수
- `SELL`: 매도
- `MARKET`: 시장가
- `LIMIT`: 지정가

### S&P 500 등락률 상위 100

S&P 500 구성종목 중 모의 등락률이 높은 순서로 상위 100개를 보여줍니다. 한 페이지에 10개씩 표시되며, 1페이지는 1~10위, 2페이지는 11~20위입니다.

### 보유 종목

현재 보유한 종목의 수량, 평균단가, 평가금액, 손익을 보여줍니다.

### 관심종목

처음에는 비어 있으며, 시장 시세 표의 `관심` 버튼으로 사용자가 직접 추가합니다.

## API 목록

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/` | 웹 화면 HTML |
| `GET` | `/health` | 서버 상태 확인 |
| `GET` | `/api/quotes` | 종목 시세 목록 |
| `GET` | `/api/account` | 계좌, 보유 종목, 관심종목, 주문 내역 |
| `POST` | `/api/orders` | 주문 생성 |
| `POST` | `/api/watchlist/add` | 관심종목 추가 |
| `POST` | `/api/watchlist/remove` | 관심종목 삭제 |
| `POST` | `/api/sim/tick` | 모의 시장 가격 변동 |
| `POST` | `/api/import/sp500` | S&P 500 구성종목 다시 불러오기 |

### 주문 요청 예시

```json
{
  "symbol": "AAPL",
  "side": "BUY",
  "type": "MARKET",
  "quantity": "5"
}
```

지정가 주문 예시:

```json
{
  "symbol": "MSFT",
  "side": "BUY",
  "type": "LIMIT",
  "quantity": "3",
  "limitPrice": "420.00"
}
```

## 주요 코드 설명

이 프로젝트는 과제 조건에 맞춰 `MockStockApp.java` 하나에 클래스와 인터페이스 100개를 담았습니다. 역할별 핵심 코드는 아래와 같습니다.

### 서버 시작

`MockStockApp`은 프로그램의 진입점입니다. 실행 인자로 포트를 받고, `AppContext`를 만든 뒤 `ServerFactory`로 HTTP 서버를 띄웁니다.

```java
public class MockStockApp {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        AppContext app = new AppContext();
        HttpServer server = ServerFactory.create(port, app);
        server.start();
    }
}
```

### 애플리케이션 조립

`AppContext`는 서비스, 저장소, 검증기, 컨트롤러를 한곳에서 생성합니다. S&P 500 import를 먼저 시도하고, 실패하면 데모 종목으로 fallback합니다.

주요 역할:

- `InMemoryDatabase`: 계좌, 종목, 시세, 주문 저장소
- `Sp500ImportService`: S&P 500 구성종목 import
- `TradeService`: 주문 접수
- `MarketDataService`: 시세 조회와 가격 변동
- `WatchlistService`: 관심종목 추가/삭제
- `ApiController`: API 요청 처리
- `PageController`: HTML 화면 응답

### 라우팅

`ServerFactory`는 URL과 컨트롤러를 연결합니다. `Router`는 요청 메서드와 경로를 기준으로 알맞은 컨트롤러를 찾습니다.

```java
router.add(HttpMethod.GET, "/", app.pageController);
router.add(HttpMethod.GET, "/api/quotes", app.apiController);
router.add(HttpMethod.POST, "/api/orders", app.apiController);
router.add(HttpMethod.POST, "/api/watchlist/add", app.apiController);
```

### 화면 렌더링

`HtmlRenderer`는 HTML, CSS, JavaScript를 반환합니다. 화면에서는 `/api/quotes`, `/api/account`를 호출해 데이터를 그리고, 등락률 상위 100개를 정렬한 뒤 10개씩 페이지로 나눕니다.

핵심 로직:

- `renderQuotes(items)`: 등락률 기준 정렬
- `renderQuotePage()`: 현재 페이지의 10개 종목 표시
- `setQuotePage(page)`: 페이지 이동
- `addWatch(symbol)`: 관심종목 추가
- `removeWatch(symbol)`: 관심종목 삭제

### S&P 500 데이터 import

`Sp500ApiClient`는 공개 S&P 500 구성종목 표를 가져와 종목코드, 회사명, 섹터를 파싱합니다. `Sp500ImportService`는 이 데이터를 DB에 저장하고, 각 종목에 모의 시세를 생성합니다.

관련 클래스:

- `Sp500ApiClient`
- `Sp500ImportService`
- `Stock`
- `Quote`

### 모의 시세

`SimulatedMarketDataService`는 현재 시세 목록을 반환하고, `/api/sim/tick` 요청이 들어오면 `RandomWalkPriceEngine`으로 가격을 조금씩 변동시킵니다.

관련 클래스:

- `MarketDataService`
- `SimulatedMarketDataService`
- `PriceEngine`
- `RandomWalkPriceEngine`
- `PriceChangedEvent`

### 주문 처리

`DefaultTradeService`는 요청 데이터를 `Order`로 만들고 검증 후 주문 유형에 맞는 실행기로 넘깁니다.

검증 클래스:

- `SymbolValidator`: 존재하는 종목인지 확인
- `QuantityValidator`: 수량이 올바른지 확인
- `CashValidator`: 매수 가능한 예수금인지 확인
- `HoldingValidator`: 매도 가능한 보유 수량인지 확인

실행 클래스:

- `MarketOrderExecutor`: 시장가 주문 즉시 체결
- `LimitOrderExecutor`: 지정가 조건 충족 시 체결, 아니면 대기

### 포트폴리오와 계좌

`Account`, `Portfolio`, `Holding`, `Money`가 계좌와 보유 종목을 표현합니다. 매수하면 예수금이 차감되고 보유 수량과 평균단가가 갱신됩니다. 매도하면 보유 수량이 줄고 예수금이 증가합니다.

관련 클래스:

- `Account`
- `Portfolio`
- `Holding`
- `Money`
- `AccountSnapshot`
- `PerformanceCalculator`

### 관심종목

`Watchlist`는 사용자가 직접 추가한 종목만 저장합니다. 기본 관심종목은 넣지 않았습니다.

관련 클래스:

- `Watchlist`
- `WatchlistService`
- `DefaultWatchlistService`

## 프로젝트 구조

```text
.
├── MockStockApp.java
├── README.md
├── LICENSE
├── .editorconfig
├── .gitignore
├── mock-stock-app.code-workspace
├── scripts/
│   └── run.ps1
├── .vscode/
│   ├── launch.json
│   ├── settings.json
│   └── tasks.json
└── .github/
    └── workflows/
        └── java-ci.yml
```

## GitHub Actions

`.github/workflows/java-ci.yml`은 push 또는 pull request 때 자동으로 실행됩니다.

검증 내용:

- Java 컴파일
- `MockStockApp.java` 안의 클래스/인터페이스 수가 100개인지 확인

## 제한사항

- 데이터는 서버 메모리에 저장되므로 서버를 재시작하면 주문과 관심종목이 초기화됩니다.
- 실시간 주가 API가 아니라 모의 시세를 사용합니다.
- 실제 로그인/회원가입/DB 저장은 구현하지 않았습니다.
- 학습용 프로젝트이므로 실제 투자 서비스로 사용하면 안 됩니다.
