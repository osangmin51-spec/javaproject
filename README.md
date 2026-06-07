# Java 모의주식투자 웹앱

Java 표준 라이브러리만 사용해 구현한 모의주식투자 웹 프로젝트입니다. 회원별 자산과 보유 주식, 거래 기록을 저장하고, 내부 모의 증권사 서버가 보내는 실시간 가격을 구독해 포트폴리오 손익을 계산합니다.

## 주요 특징

- 회원가입, 로그인, 로그아웃
- 파일 기반 DB 저장
- 모의 증권사 TCP 소켓 서버
- 실시간 가격 구독
- 주식 매수 / 매도
- 보유 종목별 평가금액, 손익, 수익률 계산
- 거래 기록 조회
- 게시판과 댓글
- 아이템 상점

## 화면 구성

웹 화면은 매매와 포트폴리오 확인을 중심으로 구성됩니다.

- 상단: 현금, 주식 평가액, 총 자산, 손익, 수익률
- 가격 연결: 가격 소스, 구독 명령, 소켓 포트, 마지막 수신 시각
- 매매 영역: 종목 선택, 수량 입력, 매수/매도 버튼
- 탭 영역: 보유, 기록, 게시판
- 보조 기능: 아이템 상점

## 데이터 저장

서버를 껐다 켜도 주요 데이터가 유지되도록 `data/` 폴더에 TSV 파일로 저장합니다.

| 파일 | 저장 내용 |
| --- | --- |
| `data/members.tsv` | 회원 번호, 이름, 아이디, 비밀번호, 현금, 진행 날짜 |
| `data/shares.tsv` | 회원별 보유 종목, 수량, 총 매입가 |
| `data/trades.tsv` | 회원별 거래 종목, 수량, 금액, 거래 구분, 시간 |

## 실시간 가격 구조

앱 실행 시 웹 서버와 함께 내부 모의 증권사 서버가 실행됩니다.

```text
웹 화면 -> /api/state -> Java 웹 서버 -> BrokerFeedClient -> TCP 9090 -> MockBrokerServer
```

- `MockBrokerServer`: 종목 가격을 주기적으로 변동시키고 구독자에게 전송합니다.
- `BrokerFeedClient`: 웹 서버 내부에서 소켓 서버에 접속해 `SUB ALL` 명령으로 전체 종목을 구독합니다.
- 브라우저: 1초마다 `/api/state`를 호출해 최신 가격과 손익을 보여줍니다.

지원 소켓 명령:

| 명령 | 설명 |
| --- | --- |
| `LIST` | 구독 가능한 종목 목록 조회 |
| `SUB ALL` | 전체 종목 실시간 가격 구독 |
| `SUB 삼성전자` | 특정 종목 가격 구독 |
| `UNSUB 삼성전자` | 특정 종목 구독 해제 |
| `QUIT` | 소켓 연결 종료 |

## 외부 시세 연동

현재 프로젝트는 API Key 없이 실행 가능한 내부 모의 증권사 서버를 사용합니다. 외부 시세 데이터가 필요한 경우 한국투자증권 KIS Open API 같은 증권사 API를 연결 대상으로 둘 수 있습니다.

실제 증권사 API를 사용할 때 필요한 정보:

- API Key
- Secret
- 계좌 정보
- 모의투자 신청 정보
- WebSocket 실시간 시세 접속 정보

## 실행 방법

Java 21 이상을 권장합니다.

```powershell
javac -encoding UTF-8 -d out MiniProjectApp.java
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

내부 소켓 서버:

```text
tcp://127.0.0.1:9090
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
| `POST` | `/api/register` | 회원가입 |
| `POST` | `/api/login` | 로그인 |
| `POST` | `/api/logout` | 로그아웃 |
| `POST` | `/api/stock/buy` | 주식 구매 |
| `POST` | `/api/stock/sell` | 주식 판매 |
| `POST` | `/api/day/next` | 다음날 진행 |
| `POST` | `/api/item/buy` | 아이템 구매 |
| `POST` | `/api/item/use` | 아이템 사용 |
| `POST` | `/api/board/write` | 게시글 작성 |
| `POST` | `/api/board/delete` | 게시글 삭제 |
| `POST` | `/api/comment/write` | 댓글 작성 |
| `POST` | `/api/comment/delete` | 댓글 삭제 |

## 기술 구성

- Java
- JDK 내장 `HttpServer`
- TCP Socket
- Thread 기반 멀티 클라이언트 처리
- TSV 파일 저장
- HTML / CSS / JavaScript
