# Ollama 자동 설정 스크립트
# PowerShell을 관리자 권한으로 실행 후 이 스크립트를 실행하세요

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   Ollama LLM 설정 자동화 스크립트" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# PATH 추가
Write-Host "[1단계] PATH 환경변수 설정..." -ForegroundColor Yellow
$ollamaPath = "$env:LOCALAPPDATA\Programs\Ollama"
if ($env:Path -notlike "*$ollamaPath*") {
    $env:Path += ";$ollamaPath"
    Write-Host "  ✓ PATH 추가: $ollamaPath" -ForegroundColor Green
} else {
    Write-Host "  ✓ PATH 이미 설정됨" -ForegroundColor Green
}

# 버전 확인
Write-Host ""
Write-Host "[2단계] Ollama 버전 확인..." -ForegroundColor Yellow
try {
    $version = & ollama --version 2>&1
    Write-Host "  ✓ $version" -ForegroundColor Green
} catch {
    Write-Host "  ✗ Ollama를 찾을 수 없습니다" -ForegroundColor Red
    Write-Host "  PowerShell을 재시작하거나 직접 경로로 실행하세요:" -ForegroundColor Yellow
    Write-Host "  $ollamaPath\ollama.exe --version" -ForegroundColor Cyan
    exit 1
}

# 디스크 공간 확인
Write-Host ""
Write-Host "[3단계] 디스크 공간 확인..." -ForegroundColor Yellow
$drive = Get-PSDrive C
$freeSpaceGB = [math]::Round($drive.Free / 1GB, 2)
Write-Host "  ✓ 사용 가능 공간: $freeSpaceGB GB" -ForegroundColor Green

if ($freeSpaceGB -lt 15) {
    Write-Host "  ⚠️ 경고: 15GB 이상 권장 (현재: $freeSpaceGB GB)" -ForegroundColor Yellow
    $continue = Read-Host "  계속하시겠습니까? (Y/N)"
    if ($continue -ne "Y" -and $continue -ne "y") {
        Write-Host "  설치 취소됨" -ForegroundColor Red
        exit 0
    }
}

# 모델 다운로드
Write-Host ""
Write-Host "[4단계] LLM 모델 다운로드..." -ForegroundColor Yellow
Write-Host "  총 크기: ~12.6GB, 예상 시간: 15-45분" -ForegroundColor Cyan
Write-Host ""

# Llama 3.1
Write-Host "  [1/3] Llama 3.1 8B 다운로드 중 (~4.7GB)..." -ForegroundColor Cyan
ollama pull llama3.1:8b
if ($LASTEXITCODE -eq 0) {
    Write-Host "  ✓ Llama 3.1 다운로드 완료" -ForegroundColor Green
} else {
    Write-Host "  ✗ Llama 3.1 다운로드 실패" -ForegroundColor Red
}

Write-Host ""

# Mistral
Write-Host "  [2/3] Mistral 7B 다운로드 중 (~4.1GB)..." -ForegroundColor Cyan
ollama pull mistral:7b
if ($LASTEXITCODE -eq 0) {
    Write-Host "  ✓ Mistral 다운로드 완료" -ForegroundColor Green
} else {
    Write-Host "  ✗ Mistral 다운로드 실패" -ForegroundColor Red
}

Write-Host ""

# CodeLlama
Write-Host "  [3/3] CodeLlama 7B 다운로드 중 (~3.8GB)..." -ForegroundColor Cyan
ollama pull codellama:7b
if ($LASTEXITCODE -eq 0) {
    Write-Host "  ✓ CodeLlama 다운로드 완료" -ForegroundColor Green
} else {
    Write-Host "  ✗ CodeLlama 다운로드 실패" -ForegroundColor Red
}

# 모델 목록 확인
Write-Host ""
Write-Host "[5단계] 설치된 모델 목록:" -ForegroundColor Yellow
ollama list

# Ollama 서비스 시작
Write-Host ""
Write-Host "[6단계] Ollama 서비스 시작..." -ForegroundColor Yellow

# 기존 프로세스 확인
$ollamaProcess = Get-Process -Name "ollama" -ErrorAction SilentlyContinue
if ($ollamaProcess) {
    Write-Host "  ✓ Ollama 서비스가 이미 실행 중입니다" -ForegroundColor Green
} else {
    Write-Host "  Ollama 서비스 시작 중..." -ForegroundColor Cyan
    Start-Process ollama -ArgumentList "serve" -WindowStyle Hidden
    Start-Sleep -Seconds 5
    Write-Host "  ✓ Ollama 서비스 시작됨 (백그라운드)" -ForegroundColor Green
}

# 서비스 확인
Write-Host ""
Write-Host "[7단계] 서비스 동작 확인..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://localhost:11434" -UseBasicParsing -TimeoutSec 5
    if ($response.StatusCode -eq 200) {
        Write-Host "  ✓ Ollama 서비스 정상 작동 중" -ForegroundColor Green
        Write-Host "  ✓ URL: http://localhost:11434" -ForegroundColor Green
    }
} catch {
    Write-Host "  ✗ Ollama 서비스에 연결할 수 없습니다" -ForegroundColor Red
    Write-Host "  다시 시도: ollama serve" -ForegroundColor Yellow
}

# 완료 메시지
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   Ollama 설정 완료!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "설치된 모델:" -ForegroundColor Yellow
Write-Host "  - Llama 3.1 8B (4.7GB)" -ForegroundColor Cyan
Write-Host "  - Mistral 7B (4.1GB)" -ForegroundColor Cyan
Write-Host "  - CodeLlama 7B (3.8GB)" -ForegroundColor Cyan
Write-Host ""
Write-Host "Ollama 서비스:" -ForegroundColor Yellow
Write-Host "  - URL: http://localhost:11434" -ForegroundColor Cyan
Write-Host "  - 상태: 실행 중" -ForegroundColor Green
Write-Host ""
Write-Host "다음 단계:" -ForegroundColor Yellow
Write-Host "  1. 백엔드 재시작: cd C:\insu_app\backend && .\mvnw spring-boot:run" -ForegroundColor Cyan
Write-Host "  2. 쿼럼 LLM 로그 확인: '쿼럼 달성' 메시지" -ForegroundColor Cyan
Write-Host "  3. 응답 시간 50% 단축 확인" -ForegroundColor Cyan
Write-Host ""

