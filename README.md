# KHStudy 미니프로젝트 웹 구현

사용자가 제공한 GitHub `_miniproject` 내용을 기준으로 만든 Java 웹 버전입니다.

원본 참고:

- https://github.com/Kim-dong-young/KHStudy/tree/main/1_JAVA/JavaStudy/src/_miniproject

기존에 진행하던 S&P 500 모의투자 대시보드는 실행 대상에서 제외하고, 이 앱은 원본 미니프로젝트 기능만 담도록 구성했습니다.

## UI 방향

콘솔 메뉴를 그대로 옮기지 않고, 실용성과 편의성을 위해 대시보드 형태로 재구성했습니다.

- 상단 요약: 보유 현금, 주식 평가액, 총 자산, 진행 날짜
- 중앙 작업 영역: 주식 매매와 종목 현황
- 탭 영역: 보유 주식, 거래 기록, 자유 게시판
- 오른쪽 보조 영역: 다음날 진행, 아이템 상점
- 로그인 전에는 로그인/회원가입 영역을 먼저 보여주고, 로그인 후에는 매매 화면에 집중하도록 구성

## 구현한 기능

- 회원가입
- 로그인 / 로그아웃
- 회원별 보유 자산, 진행 날짜 관리
- 주식 현황 조회
- 주식 구매 / 판매
- 보유 주식 조회
- 거래 기록 조회
- 다음날로 넘어가기
- 다음날 진행 시 주가 무작위 변동
- 아이템 상점
- 오늘의운세 아이템
- 주식가격예측 아이템
- 자유 게시판
- 게시글 작성
- 댓글 작성

## 원본 구조와 대응

| 원본 패키지/클래스 | 웹 구현 대응 |
| --- | --- |
| `MemberController` | 회원가입, 로그인, 현재 회원, 잔액, 보유 주식 |
| `StockController` | 주식 목록, 가격 변동, 매매 |
| `TradeLogController` | 거래 기록 테이블 |
| `ItemController` | 아이템 상점, 아이템 구매/사용 |
| `TodayLuck` | 오늘의 운세 메시지 |
| `PredictPrice` | 다음날 주가 변동 힌트 |
| `BulletinController` | 자유 게시판 |
| `CommentController` | 댓글 작성 |
| `MainMenu`, `StockMenu`, `PrivateMenu` | 웹 화면 섹션 |

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

## 참고 사항

- Java 표준 라이브러리와 JDK 내장 `HttpServer`만 사용했습니다.
- 원본 콘솔 프로젝트의 기능 흐름은 유지하되, 화면은 실사용형 웹 대시보드로 재구성했습니다.
- 현재 데이터는 서버 실행 중 메모리에서 관리됩니다.
