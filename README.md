# 보험 인수 문서 PDF 파싱 및 UW_CODE_MAPPING 생성 시스템

## 📋 프로젝트 개요

이 프로젝트는 보험 인수 문서(PDF)를 자동으로 파싱하여 UW_CODE_MAPPING 테이블에 적재 가능한 데이터를 생성하는 시스템입니다.

### 주요 기능
- 📄 PDF 텍스트 추출 및 파싱
- 🤖 LLM 기반 지능형 파싱
- 🔄 하이브리드 파싱 (정규식 + LLM)
- 📊 복잡한 매핑 규칙 지원 (시리즈형, 고지형, 다중상품)
- 🚫 자동 필터링 (갱신계약, 제도성 특약 제외)
- 🌐 REST API 제공

## 🏗️ 시스템 아키텍처

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Frontend UI   │    │   Backend API   │    │   Database      │
│   (React/TS)    │◄──►│  (Spring Boot)  │◄──►│   (Oracle)      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                │
                                ▼
                       ┌─────────────────┐
                       │   PDF Parser    │
                       │   (PDFBox)      │
                       └─────────────────┘
                                │
                                ▼
                       ┌─────────────────┐
                       │   LLM Service   │
                       │   (Ollama)      │
                       └─────────────────┘
```

## 🚀 빠른 시작

### 필수 요구사항
- Java 17+
- Node.js 18+
- Oracle Database
- Ollama (선택사항)

### 1. 저장소 클론
```bash
git clone <repository-url>
cd insu_app
```

### 2. 백엔드 설정
```bash
cd backend
# 데이터베이스 스키마 생성
sqlplus user/password@database @src/main/resources/sql/create_uw_code_mapping_extended.sql

# 애플리케이션 실행
./mvnw.cmd spring-boot:run
```

### 3. 프론트엔드 설정
```bash
cd ../insu_ui
npm install
npm run dev
```

### 4. Ollama 설정 (선택사항)
```bash
# Ollama 설치 후
ollama pull llama3.1:8b
ollama pull mistral:7b
```

## 📁 프로젝트 구조

```
insu_app/
├── backend/                    # Spring Boot 백엔드
│   ├── src/main/java/         # Java 소스 코드
│   │   └── com/example/insu/
│   │       ├── service/       # 비즈니스 로직
│   │       ├── web/          # REST 컨트롤러
│   │       ├── dto/          # 데이터 전송 객체
│   │       ├── mapper/       # MyBatis 매퍼
│   │       └── util/         # 유틸리티
│   └── src/main/resources/   # 설정 파일
├── insu_ui/                   # React 프론트엔드
│   ├── src/renderer/         # 렌더러 프로세스
│   └── electron/             # Electron 메인 프로세스
├── insuPdf/                   # PDF 샘플 파일
└── parse_results/             # 파싱 결과
```

## 🔧 개발 환경 설정

### 백엔드 개발
```bash
cd backend
./mvnw.cmd clean compile
./mvnw.cmd test
```

### 프론트엔드 개발
```bash
cd insu_ui
npm run dev
npm run build
```

### 데이터베이스 연결
```yaml
# application-local.yml
spring:
  datasource:
    url: jdbc:oracle:thin:@localhost:1521:xe
    username: your_username
    password: your_password
```

## 📚 API 문서

### 기본 파싱 API
```bash
# PDF 코드 조회
GET /api/pdf/codes?file=UW16932.pdf&type=main

# 상품 정보 조회
GET /api/product/{code}

# 관련 코드 조회
GET /api/product/{code}/related-codes
```

### 고급 파싱 API
```bash
# 하이브리드 파싱
POST /api/advanced/parse-document?fileName=UW16932.pdf&docId=UW16932

# CSV 내보내기
GET /api/advanced/export-csv/{docId}

# 파싱 통계
GET /api/advanced/parsing-stats
```

## 🧪 테스트

### 단위 테스트
```bash
cd backend
./mvnw.cmd test
```

### 통합 테스트
```bash
# 백엔드 실행 후
curl -X GET "http://localhost:8081/api/pdf/codes?file=UW16932.pdf&type=main"
```

## 📖 주요 문서

- [시스템 아키텍처](SYSTEM_ARCHITECTURE_AND_FLOW.md)
- [현재 상태](CURRENT_STATUS.md)
- [LLM 통합 완료 보고서](LLM_PROMPT_TEMPLATE_INTEGRATION_COMPLETE.md)
- [UW_CODE_MAPPING 구현](UW_CODE_MAPPING_IMPLEMENTATION_COMPLETE.md)

## 🤝 기여 방법

### 브랜치 전략
- `main`: 프로덕션 준비 코드
- `develop`: 개발 통합 브랜치
- `feature/*`: 새로운 기능 개발
- `bugfix/*`: 버그 수정

### 커밋 메시지 규칙
```
<type>(<scope>): <subject>

<body>

<footer>
```

**타입**:
- `feat`: 새로운 기능
- `fix`: 버그 수정
- `docs`: 문서 수정
- `style`: 코드 스타일 변경
- `refactor`: 코드 리팩토링
- `test`: 테스트 추가/수정
- `chore`: 빌드 과정 또는 보조 도구 변경

### Pull Request 프로세스
1. 기능 브랜치 생성
2. 개발 및 테스트
3. Pull Request 생성
4. 코드 리뷰
5. 머지

## 🐛 알려진 이슈

- [ ] Ollama 서비스 의존성 문제
- [ ] 복잡한 표 구조 인식률 개선 필요
- [ ] CSV 파싱 인용부호 처리 미흡

## 📞 지원

문제가 발생하거나 질문이 있으시면 이슈를 생성해주세요.

## 📄 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다.
