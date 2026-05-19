# 모의주식 투자 웹사이트

Java 표준 라이브러리만 사용한 단일 파일 모의주식투자 웹사이트입니다. S&P 500 구성종목을 가져와 등락률 상위 100개를 10개씩 페이지로 보여주고, 관심종목과 모의 주문을 연습할 수 있습니다.

## 특징

- 외부 프레임워크 없이 JDK 내장 `HttpServer` 사용
- 클래스/인터페이스 총 100개로 구성
- S&P 500 구성종목 import
- 등락률 상위 100개 표시
- 10개씩 총 10페이지 페이지네이션
- 모의 매수/매도 주문
- 사용자가 직접 추가/삭제하는 관심종목
- VS Code 실행 설정 포함

> S&P 500 종목 목록은 공개 웹 표에서 가져오며, 가격과 등락률은 모의투자용 시뮬레이션 값입니다. 실제 투자 판단용 실시간 시세가 아닙니다.

## VS Code에서 실행

1. 이 폴더를 VS Code로 엽니다.
2. `Ctrl+Shift+B`로 `compile` 작업을 실행합니다.
3. 터미널에서 실행합니다.

```powershell
.\scripts\run.ps1
```

브라우저 주소:

```text
http://localhost:8080
```

## 직접 실행

```powershell
javac -encoding UTF-8 -d out MockStockApp.java
java -cp out MockStockApp 8080
```

## 파일 구성

- `MockStockApp.java`: 웹 서버, 화면, API, 도메인 로직이 들어 있는 단일 Java 소스
- `.vscode/tasks.json`: VS Code 빌드/실행 작업
- `.vscode/launch.json`: VS Code Java 디버그 실행 설정
- `scripts/run.ps1`: 컴파일 후 서버 실행 스크립트
- `.github/workflows/java-ci.yml`: GitHub Actions 컴파일 검증
- `out/`: 컴파일 결과 폴더

## GitHub에 올리기

```powershell
git remote add origin https://github.com/<사용자명>/<저장소명>.git
git branch -M main
git push -u origin main
```
