# 보험 앱 개발 환경 설치 가이드

## 개요
이 프로젝트는 보험 상품 정보를 PDF에서 자동으로 파싱하고, 사용자 수정을 통해 학습하며, 보험료를 계산하는 종합적인 보험 정보 관리 시스템입니다.

## 시스템 요구사항
- **운영체제**: Windows 10/11
- **Java**: OpenJDK 17 이상
- **Node.js**: 18.x 이상
- **Python**: 3.11.x 이상
- **데이터베이스**: Oracle Database XE 21c
- **메모리**: 최소 8GB RAM 권장
- **디스크**: 최소 10GB 여유 공간

## 자동 설치 스크립트

### 1. 완전 자동 설치 (관리자 권한 필요)
```batch
setup-dev-environment.bat
```
- 모든 필수 소프트웨어를 자동으로 다운로드하고 설치
- 환경변수 자동 설정
- 모든 의존성 패키지 자동 설치
- 시스템 재시작 권장

### 2. 빠른 설치 (소프트웨어가 이미 설치된 경우)
```batch
quick-setup.bat
```
- 필수 소프트웨어 설치 확인
- Python, Node.js, Maven 의존성만 설치
- 빠른 설정 (5분 내 완료)

## 수동 설치 가이드

### 1. Java 17 설치
1. [Adoptium](https://adoptium.net/temurin/releases/)에서 Java 17 다운로드
2. 설치 후 환경변수 설정:
   - `JAVA_HOME`: Java 설치 경로
   - `PATH`에 `%JAVA_HOME%\bin` 추가

### 2. Node.js 18 설치
1. [Node.js 공식 사이트](https://nodejs.org/)에서 LTS 버전 다운로드
2. 설치 후 터미널에서 확인:
   ```bash
   node --version
   npm --version
   ```

### 3. Python 3.11 설치
1. [Python 공식 사이트](https://www.python.org/downloads/)에서 3.11.x 다운로드
2. 설치 시 "Add Python to PATH" 체크
3. 설치 후 터미널에서 확인:
   ```bash
   python --version
   pip --version
   ```

### 4. Oracle Database XE 설치
1. [Oracle XE 다운로드](https://www.oracle.com/database/technologies/xe-downloads.html)
2. 설치 시 관리자 비밀번호 설정
3. 서비스 시작 확인

## 프로젝트 설정

### 1. 의존성 설치
```bash
# Python 패키지 설치
pip install -r requirements.txt

# Node.js 패키지 설치
cd insu_ui
npm install

# Maven 의존성 설치
cd backend
mvnw.cmd clean compile
```

### 2. 데이터베이스 설정
1. Oracle XE 서비스 시작
2. 데이터베이스 사용자 생성:
   ```sql
   CREATE USER devown IDENTIFIED BY own20250101;
   GRANT CONNECT, RESOURCE TO devown;
   GRANT CREATE TABLE TO devown;
   ```
3. 테이블 생성 스크립트 실행:
   ```bash
   cd backend/src/main/resources/sql
   sqlplus devown/own20250101@localhost:1521/XEPDB1 @complete_setup.sql
   ```

## 서버 시작

### 자동 시작
```batch
start-servers.bat
```
- 백엔드와 프론트엔드 서버를 동시에 시작
- 브라우저 자동 열기

### 수동 시작
```bash
# 백엔드 서버 시작
cd backend
mvnw.cmd spring-boot:run

# 프론트엔드 서버 시작 (새 터미널)
cd insu_ui
npm run dev
```

## 접속 URL
- **프론트엔드**: http://localhost:5173
- **백엔드 API**: http://localhost:8081
- **API 문서**: http://localhost:8081/swagger-ui.html

## 개발 환경 정리

### 자동 정리
```batch
cleanup-dev-environment.bat
```
- 서버 프로세스 종료
- 임시 파일 및 캐시 정리
- 로그 파일 정리

### 수동 정리
```bash
# 백엔드 정리
cd backend
mvnw.cmd clean

# 프론트엔드 정리
cd insu_ui
npm run build
```

## 문제 해결

### 1. Java 관련 문제
- `JAVA_HOME` 환경변수 확인
- Java 버전 확인: `java -version`
- Maven 버전 확인: `mvnw.cmd --version`

### 2. Node.js 관련 문제
- Node.js 버전 확인: `node --version`
- npm 캐시 정리: `npm cache clean --force`
- node_modules 재설치: `rm -rf node_modules && npm install`

### 3. Python 관련 문제
- Python 버전 확인: `python --version`
- pip 업그레이드: `python -m pip install --upgrade pip`
- 가상환경 사용 권장

### 4. Oracle 관련 문제
- Oracle 서비스 상태 확인
- 방화벽 포트 1521 확인
- 연결 문자열 확인: `jdbc:oracle:thin:@//localhost:1521/XEPDB1`

### 5. 포트 충돌 문제
- 포트 8081 (백엔드) 사용 중인 프로세스 확인
- 포트 5173 (프론트엔드) 사용 중인 프로세스 확인
- 프로세스 종료: `taskkill /f /pid <PID>`

## 개발 도구

### 1. IDE 설정
- **IntelliJ IDEA**: Spring Boot 플러그인 설치
- **VS Code**: Java, Python, TypeScript 확장 설치
- **Eclipse**: Spring Tools 4 설치

### 2. 데이터베이스 도구
- **Oracle SQL Developer**: 데이터베이스 관리
- **DBeaver**: 범용 데이터베이스 도구
- **DataGrip**: JetBrains 데이터베이스 도구

### 3. API 테스트 도구
- **Postman**: API 테스트
- **Insomnia**: API 클라이언트
- **Swagger UI**: API 문서 및 테스트

## 프로젝트 구조
```
insu_app/
├── backend/                 # Spring Boot 백엔드
│   ├── src/main/java/      # Java 소스 코드
│   ├── src/main/resources/ # 설정 파일 및 SQL
│   └── pom.xml            # Maven 의존성
├── insu_ui/               # React 프론트엔드
│   ├── src/               # TypeScript 소스 코드
│   ├── package.json       # Node.js 의존성
│   └── vite.config.ts     # Vite 설정
├── insuCsv/               # CSV 데이터 파일
├── insuPdf/               # PDF 파일
└── requirements.txt       # Python 의존성
```

## 라이선스
이 프로젝트는 MIT 라이선스를 따릅니다.

## 지원
문제가 발생하면 다음을 확인해주세요:
1. 시스템 요구사항 충족 여부
2. 모든 소프트웨어의 최신 버전 사용
3. 환경변수 설정 확인
4. 방화벽 및 포트 설정 확인

추가 도움이 필요하면 프로젝트 이슈를 등록해주세요.
