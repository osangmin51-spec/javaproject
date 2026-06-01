# 모의주식 투자 웹사이트

Java 표준 라이브러리만 사용해 만든 모의주식투자 웹사이트입니다. JDK 내장 `HttpServer`로 웹 화면과 REST 스타일 API를 제공하며, S&P 500 구성종목을 가져와 모의 시세 기반의 주문, 관심종목, 포트폴리오 분석을 연습할 수 있습니다.

> 학습용 모의투자 프로젝트입니다. S&P 500 종목 목록은 공개 웹 데이터를 사용하지만, 가격과 등락률은 실제 실시간 시세가 아니라 시뮬레이션 값입니다. 실제 투자 판단에는 사용하면 안 됩니다.

## 이번 개선 내용

기존 버전은 서버를 껐다 켜면 주문 내역, 보유 종목, 관심종목이 모두 초기화되었습니다. 이번 개선에서는 아래 기능을 추가했습니다.

- 파일 기반 저장소 추가: `data/app-state.tsv`
- 서버 재시작 후에도 예수금, 보유 종목, 주문 내역, 관심종목 유지
- 대기 중인 지정가 주문 자동 체결
- `/api/sim/tick` 실행 시 시세 변동 후 대기 주문을 다시 검사
- 수익률 분석 API와 화면 추가
- 종목별 손익, 손익률, 체결/대기 주문 수, 자산 변화 기록 제공
- 실행 데이터가 GitHub에 올라가지 않도록 `data/`를 `.gitignore`에 추가

## 주요 기능

- S&P 500 구성종목 import
- 등락률 상위 100개 종목 표시
- 10개씩 총 10페이지 페이지네이션
- 시장가/지정가 모의 매수·매도 주문
- 조건 미달 지정가 주문 대기 처리
- 시세 변동 시 대기 지정가 주문 자동 체결
- 예수금, 보유 종목, 평가금액, 손익 표시
- 사용자가 직접 추가/삭제하는 관심종목
- 포트폴리오 수익률 분석
- 파일 저장으로 거래 상태 유지
- VS Code 실행 설정 포함
- GitHub Actions로 컴파일 및 Java 타입 수 검증

## 기술 스택

- Java 21 이상 권장
- JDK 내장 `com.sun.net.httpserver.HttpServer`
- Java `HttpClient`
- HTML, CSS, Vanilla JavaScript
- 파일 기반 저장소, TSV
- PowerShell 실행 스크립트
- GitHub Actions CI

## 실행 방법

### VS Code에서 실행

1. `mock-stock-app.code-workspace` 파일을 VS Code로 엽니다.
2. `Ctrl + Shift + B`로 `compile` 작업을 실행합니다.
3. 터미널에서 실행합니다.

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

포트를 바꾸려면 마지막 숫자를 바꾸면 됩니다.

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

### 주문 내역

시장가 주문은 바로 체결됩니다. 지정가 주문은 조건을 만족하면 체결되고, 조건을 만족하지 않으면 `대기` 상태로 남습니다. 이후 `/api/sim/tick`으로 가격이 변할 때 자동 체결 조건을 다시 검사합니다.

### 보유 종목

현재 보유한 종목의 수량, 평균단가, 평가금액, 손익을 보여줍니다.

### 관심종목

처음에는 비어 있으며, 시장 시세 표의 `관심` 버튼으로 사용자가 직접 추가합니다.

### 수익률 분석

현재 총자산, 총수익률, 체결 주문 수, 대기 주문 수를 요약합니다. 보유 종목별 평가금액, 손익, 손익률도 함께 보여줍니다.

## API 목록

| Method | Path | 설명 |
| --- | --- | --- |
| `GET` | `/` | 웹 화면 HTML |
| `GET` | `/health` | 서버 상태 확인 |
| `GET` | `/api/quotes` | 종목 시세 목록 |
| `GET` | `/api/account` | 계좌, 보유 종목, 관심종목, 주문 내역 |
| `GET` | `/api/analytics` | 포트폴리오 분석 데이터 |
| `POST` | `/api/orders` | 주문 생성 |
| `POST` | `/api/watchlist/add` | 관심종목 추가 |
| `POST` | `/api/watchlist/remove` | 관심종목 삭제 |
| `POST` | `/api/sim/tick` | 모의 시장 가격 변동 및 대기 주문 자동 체결 검사 |
| `POST` | `/api/import/sp500` | S&P 500 구성종목 다시 불러오기 |

### 주문 요청 예시

시장가 매수:

```json
{
  "symbol": "AAPL",
  "side": "BUY",
  "type": "MARKET",
  "quantity": "5"
}
```

지정가 매수:

```json
{
  "symbol": "MSFT",
  "side": "BUY",
  "type": "LIMIT",
  "quantity": "3",
  "limitPrice": "420.00"
}
```

### 분석 응답 예시

```json
{
  "equity": 99996.30,
  "returnPct": -0.00,
  "filledOrders": 2,
  "pendingOrders": 1,
  "fees": 2.00,
  "holdings": [],
  "equityHistory": []
}
```

## 주요 코드 설명

이 프로젝트는 과제 조건을 맞추기 위해 `MockStockApp.java` 하나에 클래스와 인터페이스 100개를 담았습니다. 다만 내부 역할은 controller, service, domain, repository에 가깝게 나누어 유지보수 흐름을 분명히 했습니다.

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

`AppContext`는 서비스, 저장소, 검증기, 컨트롤러를 한곳에서 생성합니다. S&P 500 import를 먼저 시도하고, 저장된 사용자 상태가 있으면 `FileStateStore`로 다시 불러옵니다.

주요 구성:

- `InMemoryDatabase`: 실행 중 사용하는 계좌, 종목, 시세, 주문 저장소
- `FileStateStore`: 계좌 상태를 `data/app-state.tsv`에 저장하고 복원
- `Sp500ImportService`: S&P 500 구성종목 import
- `TradeService`: 주문 접수
- `PendingOrderProcessor`: 대기 지정가 주문 자동 체결 검사
- `MarketDataService`: 시세 조회와 가격 변동
- `AnalyticsService`: 포트폴리오 분석 데이터 생성
- `WatchlistService`: 관심종목 추가/삭제
- `ApiController`: API 요청 처리
- `PageController`: HTML 화면 응답

### 파일 저장

`FileStateStore`는 아래 데이터를 저장합니다.

- 예수금
- 보유 종목
- 관심종목
- 주문 내역
- 자산 변화 기록

저장 파일은 `data/app-state.tsv`이며, 실행 중 주문/관심종목/시세 변동이 발생할 때 갱신됩니다. `data/` 폴더는 사용자 실행 데이터이므로 GitHub에는 올리지 않습니다.

### 지정가 주문 자동 체결

지정가 주문은 처음 주문할 때 조건을 만족하지 않으면 `PENDING` 상태로 저장됩니다. 이후 `/api/sim/tick` 요청이 들어오면 가격이 변하고, `PendingOrderProcessor`가 대기 주문을 다시 검사합니다.

자동 체결 흐름:

1. `/api/sim/tick` 호출
2. `MarketDataService.tick()`으로 모의 가격 변동
3. `PendingOrderProcessor.process()` 실행
4. 지정가 조건을 만족한 주문 체결
5. 변경된 계좌와 주문 내역 파일 저장

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

### 수익률 분석

`AnalyticsService`는 계좌와 현재 시세를 기준으로 분석 데이터를 만듭니다.

제공 데이터:

- 현재 총자산
- 총수익률
- 체결 주문 수
- 대기 주문 수
- 누적 수수료
- 종목별 평가금액
- 종목별 손익
- 종목별 손익률
- 자산 변화 기록

### S&P 500 데이터 import

`Sp500ApiClient`는 공개 S&P 500 구성종목 표를 가져와 종목코드, 회사명, 섹터를 파싱합니다. `Sp500ImportService`는 이 데이터를 DB에 저장하고, 각 종목에 모의 시세를 생성합니다.

관련 클래스:

- `Sp500ApiClient`
- `Sp500ImportService`
- `Stock`
- `Quote`

### 구조 개선 방향

현재는 과제 조건과 GitHub Actions 검증 때문에 단일 파일과 100개 타입 수를 유지합니다. 유지보수 관점에서는 다음 단계에서 교수님께 확인 후 아래처럼 패키지 분리를 하는 것이 좋습니다.

```text
src/main/java/
├── controller/
├── service/
├── domain/
├── repository/
├── persistence/
└── web/
```

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

- 실제 DB 대신 파일 기반 저장을 사용합니다.
- 실시간 주가 API가 아니라 모의 시세를 사용합니다.
- 실제 로그인/회원가입은 구현하지 않았습니다.
- 자산 변화 그래프는 API 데이터와 화면 요약 중심이며, 전문 차트 라이브러리는 사용하지 않았습니다.
- 학습용 프로젝트이므로 실제 투자 서비스로 사용하면 안 됩니다.
