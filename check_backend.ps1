# 백엔드 상태 확인 스크립트
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   백엔드 상태 확인 스크립트" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. Java 프로세스 확인
Write-Host "[1단계] Java 프로세스 확인..." -ForegroundColor Yellow
$javaProcesses = Get-Process -Name "java" -ErrorAction SilentlyContinue
if ($javaProcesses) {
    Write-Host "  ✓ Java 프로세스 실행 중 ($($javaProcesses.Count)개)" -ForegroundColor Green
    $javaProcesses | ForEach-Object {
        Write-Host "    - PID: $($_.Id), 메모리: $([math]::Round($_.WorkingSet64 / 1MB, 2)) MB" -ForegroundColor Cyan
    }
} else {
    Write-Host "  ✗ Java 프로세스를 찾을 수 없습니다" -ForegroundColor Red
    Write-Host "  백엔드가 시작되지 않았거나 오류가 발생했습니다" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "백엔드 수동 시작:" -ForegroundColor Yellow
    Write-Host "  cd C:\insu_app\backend" -ForegroundColor Cyan
    Write-Host "  .\mvnw.cmd spring-boot:run -DskipTests" -ForegroundColor Cyan
    exit 1
}

Write-Host ""

# 2. 포트 8080 확인
Write-Host "[2단계] 포트 8080 리스닝 확인..." -ForegroundColor Yellow
$port8080 = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($port8080) {
    Write-Host "  ✓ 포트 8080이 리스닝 중입니다" -ForegroundColor Green
    Write-Host "    - PID: $($port8080.OwningProcess)" -ForegroundColor Cyan
} else {
    Write-Host "  ⚠️  포트 8080이 아직 리스닝하지 않습니다" -ForegroundColor Yellow
    Write-Host "  백엔드가 아직 시작 중일 수 있습니다 (30초-1분 대기)" -ForegroundColor Cyan
}

Write-Host ""

# 3. Health Check (최대 60초 대기)
Write-Host "[3단계] Health Check (최대 60초 대기)..." -ForegroundColor Yellow
$maxAttempts = 12
$attemptCount = 0
$healthCheckSuccess = $false

while ($attemptCount -lt $maxAttempts) {
    $attemptCount++
    Write-Host "  시도 $attemptCount/$maxAttempts..." -ForegroundColor Cyan
    
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            Write-Host "  ✓ 백엔드 정상 작동 중!" -ForegroundColor Green
            Write-Host "    - 상태 코드: $($response.StatusCode)" -ForegroundColor Cyan
            Write-Host "    - 응답: $($response.Content)" -ForegroundColor Cyan
            $healthCheckSuccess = $true
            break
        }
    } catch {
        if ($attemptCount -lt $maxAttempts) {
            Write-Host "    ⏳ 대기 중... (5초 후 재시도)" -ForegroundColor Yellow
            Start-Sleep -Seconds 5
        }
    }
}

if (-not $healthCheckSuccess) {
    Write-Host "  ✗ Health Check 실패 (60초 초과)" -ForegroundColor Red
    Write-Host "  백엔드 로그를 확인하세요:" -ForegroundColor Yellow
    Write-Host "    Get-Content C:\insu_app\logs\insu-offline.log -Tail 50" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "  또는 콘솔 출력 확인:" -ForegroundColor Yellow
    Write-Host "    별도 터미널에서 실행 (백그라운드 아님):" -ForegroundColor Cyan
    Write-Host "    cd C:\insu_app\backend" -ForegroundColor Cyan
    Write-Host "    .\mvnw.cmd spring-boot:run -DskipTests" -ForegroundColor Cyan
    exit 1
}

Write-Host ""

# 4. Ollama 서비스 확인
Write-Host "[4단계] Ollama 서비스 확인..." -ForegroundColor Yellow
$ollamaProcess = Get-Process -Name "ollama" -ErrorAction SilentlyContinue
if ($ollamaProcess) {
    Write-Host "  ✓ Ollama 프로세스 실행 중" -ForegroundColor Green
    
    try {
        $ollamaResponse = Invoke-WebRequest -Uri "http://localhost:11434" -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
        Write-Host "  ✓ Ollama 서비스 정상 작동" -ForegroundColor Green
        Write-Host "    - 응답: $($ollamaResponse.Content)" -ForegroundColor Cyan
    } catch {
        Write-Host "  ⚠️  Ollama 서비스에 연결할 수 없습니다" -ForegroundColor Yellow
        Write-Host "  재시작: Start-Process ollama -ArgumentList 'serve' -WindowStyle Hidden" -ForegroundColor Cyan
    }
} else {
    Write-Host "  ✗ Ollama 프로세스를 찾을 수 없습니다" -ForegroundColor Red
    Write-Host "  시작: Start-Process ollama -ArgumentList 'serve' -WindowStyle Hidden" -ForegroundColor Cyan
}

Write-Host ""

# 5. 테스트 API 호출
Write-Host "[5단계] 테스트 API 호출..." -ForegroundColor Yellow
Write-Host "  상품 정보 조회 중: 21686" -ForegroundColor Cyan

try {
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $apiResponse = Invoke-WebRequest -Uri "http://localhost:8080/api/product/info/21686" -UseBasicParsing -TimeoutSec 30 -ErrorAction Stop
    $stopwatch.Stop()
    
    Write-Host "  ✓ API 호출 성공!" -ForegroundColor Green
    Write-Host "    - 상태 코드: $($apiResponse.StatusCode)" -ForegroundColor Cyan
    Write-Host "    - 응답 시간: $($stopwatch.Elapsed.TotalSeconds) 초" -ForegroundColor Cyan
    Write-Host "    - 응답 크기: $($apiResponse.Content.Length) bytes" -ForegroundColor Cyan
    
    # JSON 파싱
    $jsonData = $apiResponse.Content | ConvertFrom-Json
    if ($jsonData.terms) {
        Write-Host "    - 조합 개수: $($jsonData.terms.Count)" -ForegroundColor Cyan
        Write-Host "    - 보험기간: $($jsonData.terms[0].insuTerm)" -ForegroundColor Cyan
        Write-Host "    - 납입기간: $($jsonData.terms[0].payTerm)" -ForegroundColor Cyan
    }
    
} catch {
    Write-Host "  ✗ API 호출 실패: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "  백엔드 로그를 확인하세요" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   확인 완료!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "다음 테스트:" -ForegroundColor Yellow
Write-Host "  # 캐시 히트 테스트 (0.5초 예상)" -ForegroundColor Cyan
Write-Host "  Measure-Command {" -ForegroundColor Cyan
Write-Host "      Invoke-WebRequest -Uri 'http://localhost:8080/api/product/info/21686' -UseBasicParsing" -ForegroundColor Cyan
Write-Host "  } | Select-Object TotalSeconds" -ForegroundColor Cyan
Write-Host ""


