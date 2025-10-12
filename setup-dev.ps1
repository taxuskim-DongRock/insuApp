# 개발 환경 설정 스크립트 (Windows PowerShell)
Write-Host "========================================" -ForegroundColor Green
Write-Host "보험 인수 문서 파싱 시스템 개발 환경 설정" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green

# Java 버전 확인
Write-Host "`n1. Java 환경 확인..." -ForegroundColor Yellow
try {
    $javaVersion = java -version 2>&1 | Select-String "version"
    Write-Host "Java 버전: $javaVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ Java가 설치되지 않았습니다. Java 17+ 설치가 필요합니다." -ForegroundColor Red
    exit 1
}

# Node.js 버전 확인
Write-Host "`n2. Node.js 환경 확인..." -ForegroundColor Yellow
try {
    $nodeVersion = node --version
    $npmVersion = npm --version
    Write-Host "Node.js 버전: $nodeVersion" -ForegroundColor Green
    Write-Host "npm 버전: $npmVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ Node.js가 설치되지 않았습니다. Node.js 18+ 설치가 필요합니다." -ForegroundColor Red
    exit 1
}

# Git 확인
Write-Host "`n3. Git 환경 확인..." -ForegroundColor Yellow
try {
    $gitVersion = git --version
    Write-Host "Git 버전: $gitVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ Git이 설치되지 않았습니다. Git 설치가 필요합니다." -ForegroundColor Red
    exit 1
}

# 백엔드 의존성 설치
Write-Host "`n4. 백엔드 의존성 설치..." -ForegroundColor Yellow
Set-Location backend
try {
    .\mvnw.cmd clean compile
    Write-Host "✅ 백엔드 컴파일 성공" -ForegroundColor Green
} catch {
    Write-Host "❌ 백엔드 컴파일 실패: $_" -ForegroundColor Red
    exit 1
}

# 프론트엔드 의존성 설치
Write-Host "`n5. 프론트엔드 의존성 설치..." -ForegroundColor Yellow
Set-Location ..\insu_ui
try {
    npm install
    Write-Host "✅ 프론트엔드 의존성 설치 성공" -ForegroundColor Green
} catch {
    Write-Host "❌ 프론트엔드 의존성 설치 실패: $_" -ForegroundColor Red
    exit 1
}

# 환경 설정 파일 생성
Write-Host "`n6. 환경 설정 파일 생성..." -ForegroundColor Yellow
Set-Location ..

# application-local.yml 예시 파일 생성
$localConfig = @"
spring:
  datasource:
    url: jdbc:oracle:thin:@localhost:1521:xe
    username: ${env:DB_USERNAME:your_username}
    password: ${env:DB_PASSWORD:your_password}
    driver-class-name: oracle.jdbc.OracleDriver
  
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.Oracle12cDialect

logging:
  level:
    com.example.insu: DEBUG
    org.springframework.web: DEBUG
"@

$localConfig | Out-File -FilePath "backend\src\main\resources\application-local.yml.example" -Encoding UTF8
Write-Host "✅ application-local.yml.example 생성 완료" -ForegroundColor Green

# .env.example 파일 생성
$envExample = @"
# 데이터베이스 설정
DB_USERNAME=your_username
DB_PASSWORD=your_password
DB_URL=jdbc:oracle:thin:@localhost:1521:xe

# Ollama 설정 (선택사항)
OLLAMA_HOST=http://localhost:11434
OLLAMA_MODEL=llama3.1:8b

# API 설정
API_BASE_URL=http://localhost:8081
"@

$envExample | Out-File -FilePath ".env.example" -Encoding UTF8
Write-Host "✅ .env.example 생성 완료" -ForegroundColor Green

# Git 설정 확인
Write-Host "`n7. Git 설정 확인..." -ForegroundColor Yellow
if (!(Test-Path ".git")) {
    Write-Host "Git 저장소가 초기화되지 않았습니다. 초기화하시겠습니까? (y/n)" -ForegroundColor Yellow
    $response = Read-Host
    if ($response -eq "y" -or $response -eq "Y") {
        git init
        git add .
        git commit -m "Initial commit: 개발 환경 설정 완료"
        Write-Host "✅ Git 저장소 초기화 완료" -ForegroundColor Green
    }
}

# Ollama 설정 확인 (선택사항)
Write-Host "`n8. Ollama 설정 확인 (선택사항)..." -ForegroundColor Yellow
try {
    $ollamaVersion = ollama --version 2>&1
    Write-Host "Ollama 버전: $ollamaVersion" -ForegroundColor Green
    Write-Host "Ollama 모델 설치를 확인하시겠습니까? (y/n)" -ForegroundColor Yellow
    $response = Read-Host
    if ($response -eq "y" -or $response -eq "Y") {
        Write-Host "Ollama 모델 설치 중..." -ForegroundColor Yellow
        ollama pull llama3.1:8b
        ollama pull mistral:7b
        Write-Host "✅ Ollama 모델 설치 완료" -ForegroundColor Green
    }
} catch {
    Write-Host "⚠️ Ollama가 설치되지 않았습니다. LLM 기능을 사용하려면 Ollama 설치가 필요합니다." -ForegroundColor Yellow
}

Write-Host "`n========================================" -ForegroundColor Green
Write-Host "✅ 개발 환경 설정 완료!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green

Write-Host "`n다음 단계:" -ForegroundColor Cyan
Write-Host "1. 데이터베이스 스키마 생성:" -ForegroundColor White
Write-Host "   sqlplus user/password@database @backend/src/main/resources/sql/create_uw_code_mapping_extended.sql" -ForegroundColor Gray

Write-Host "`n2. 백엔드 실행:" -ForegroundColor White
Write-Host "   cd backend" -ForegroundColor Gray
Write-Host "   .\mvnw.cmd spring-boot:run" -ForegroundColor Gray

Write-Host "`n3. 프론트엔드 실행:" -ForegroundColor White
Write-Host "   cd insu_ui" -ForegroundColor Gray
Write-Host "   npm run dev" -ForegroundColor Gray

Write-Host "`n4. 브라우저에서 확인:" -ForegroundColor White
Write-Host "   http://localhost:8081 (백엔드 API)" -ForegroundColor Gray
Write-Host "   http://localhost:5173 (프론트엔드 UI)" -ForegroundColor Gray

Write-Host "`n협업을 위한 추가 정보:" -ForegroundColor Cyan
Write-Host "- README.md: 프로젝트 개요 및 사용법" -ForegroundColor White
Write-Host "- COLLABORATION_GUIDE.md: 협업 가이드" -ForegroundColor White
Write-Host "- .env.example: 환경 변수 예시" -ForegroundColor White
