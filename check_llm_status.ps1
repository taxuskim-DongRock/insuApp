# Ollama LLM 동작 상태 확인 스크립트
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   Ollama LLM 상태 확인" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. Ollama 프로세스 확인
Write-Host "[1단계] Ollama 프로세스 확인" -ForegroundColor Yellow
$ollamaProcess = Get-Process -Name "ollama" -ErrorAction SilentlyContinue

if ($ollamaProcess) {
    Write-Host "  ✓ Ollama 프로세스 실행 중" -ForegroundColor Green
    $ollamaProcess | ForEach-Object {
        $memoryMB = [math]::Round($_.WorkingSet64 / 1MB, 2)
        Write-Host "    - PID: $($_.Id)" -ForegroundColor Cyan
        Write-Host "    - 메모리: $memoryMB MB" -ForegroundColor Cyan
        Write-Host "    - CPU: $($_.CPU) 초" -ForegroundColor Cyan
    }
} else {
    Write-Host "  ✗ Ollama 프로세스를 찾을 수 없습니다" -ForegroundColor Red
    Write-Host "  시작: Start-Process ollama -ArgumentList 'serve' -WindowStyle Hidden" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Ollama를 시작하시겠습니까? (Y/N)" -ForegroundColor Yellow
    $response = Read-Host
    if ($response -eq "Y" -or $response -eq "y") {
        Write-Host "  Ollama 시작 중..." -ForegroundColor Cyan
        Start-Process ollama -ArgumentList "serve" -WindowStyle Hidden
        Start-Sleep -Seconds 5
        Write-Host "  ✓ Ollama 시작됨" -ForegroundColor Green
    } else {
        Write-Host "  종료합니다" -ForegroundColor Red
        exit 1
    }
}

Write-Host ""

# 2. Ollama 서비스 HTTP 확인
Write-Host "[2단계] Ollama 서비스 HTTP 확인" -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://localhost:11434" -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
    Write-Host "  ✓ Ollama 서비스 정상 작동" -ForegroundColor Green
    Write-Host "    - 상태 코드: $($response.StatusCode)" -ForegroundColor Cyan
    Write-Host "    - 응답: $($response.Content)" -ForegroundColor Cyan
} catch {
    Write-Host "  ✗ Ollama 서비스에 연결할 수 없습니다" -ForegroundColor Red
    Write-Host "    오류: $($_.Exception.Message)" -ForegroundColor Yellow
    Write-Host "  Ollama를 재시작해주세요" -ForegroundColor Yellow
    exit 1
}

Write-Host ""

# 3. 설치된 모델 목록 확인
Write-Host "[3단계] 설치된 모델 목록 확인" -ForegroundColor Yellow
try {
    $modelList = & ollama list 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✓ 모델 목록 조회 성공" -ForegroundColor Green
        Write-Host ""
        Write-Host $modelList -ForegroundColor Cyan
        
        # 필수 모델 확인
        $requiredModels = @("llama3.1:8b", "mistral:7b", "codellama:7b")
        $missingModels = @()
        
        foreach ($model in $requiredModels) {
            if ($modelList -notmatch $model) {
                $missingModels += $model
            }
        }
        
        if ($missingModels.Count -eq 0) {
            Write-Host "  ✓ 모든 필수 모델 설치됨" -ForegroundColor Green
        } else {
            Write-Host "  ⚠️  누락된 모델:" -ForegroundColor Yellow
            foreach ($model in $missingModels) {
                Write-Host "    - $model" -ForegroundColor Red
            }
            Write-Host ""
            Write-Host "  다운로드: ollama pull <모델명>" -ForegroundColor Cyan
        }
    } else {
        Write-Host "  ✗ 모델 목록 조회 실패" -ForegroundColor Red
    }
} catch {
    Write-Host "  ✗ ollama 명령어를 찾을 수 없습니다" -ForegroundColor Red
    Write-Host "    PowerShell을 재시작하거나 PATH를 확인하세요" -ForegroundColor Yellow
}

Write-Host ""

# 4. 각 모델 테스트
Write-Host "[4단계] LLM 모델 테스트" -ForegroundColor Yellow
$testModels = @("llama3.1:8b", "mistral:7b", "codellama:7b")

foreach ($model in $testModels) {
    Write-Host "  테스트: $model" -ForegroundColor Cyan
    
    try {
        $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
        $result = & ollama run $model "테스트" --verbose 2>&1 | Select-Object -First 1
        $stopwatch.Stop()
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "    ✓ 정상 작동 (응답 시간: $($stopwatch.Elapsed.TotalSeconds)초)" -ForegroundColor Green
        } else {
            Write-Host "    ✗ 실패" -ForegroundColor Red
        }
    } catch {
        Write-Host "    ✗ 오류: $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host ""

# 5. Ollama API를 통한 모델 테스트
Write-Host "[5단계] Ollama API 직접 테스트" -ForegroundColor Yellow

$testPrompt = @{
    model = "llama3.1:8b"
    prompt = "보험기간: 종신, 납입기간: 10년납, 15년납, 20년납, 30년납으로 파싱하세요"
    stream = $false
} | ConvertTo-Json

Write-Host "  API 호출 중..." -ForegroundColor Cyan

try {
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $response = Invoke-RestMethod -Uri "http://localhost:11434/api/generate" `
        -Method Post `
        -Body $testPrompt `
        -ContentType "application/json" `
        -TimeoutSec 30 `
        -ErrorAction Stop
    $stopwatch.Stop()
    
    Write-Host "  ✓ API 호출 성공" -ForegroundColor Green
    Write-Host "    - 응답 시간: $($stopwatch.Elapsed.TotalSeconds) 초" -ForegroundColor Cyan
    Write-Host "    - 모델: $($response.model)" -ForegroundColor Cyan
    if ($response.response) {
        $responseText = $response.response.Substring(0, [Math]::Min(100, $response.response.Length))
        Write-Host "    - 응답 일부: $responseText..." -ForegroundColor Cyan
    }
    
    # 성능 평가
    if ($stopwatch.Elapsed.TotalSeconds -lt 10) {
        Write-Host "    ✓ 성능: 우수 (10초 이내)" -ForegroundColor Green
    } elseif ($stopwatch.Elapsed.TotalSeconds -lt 20) {
        Write-Host "    ⚠️  성능: 보통 (10-20초)" -ForegroundColor Yellow
    } else {
        Write-Host "    ⚠️  성능: 느림 (20초 이상)" -ForegroundColor Yellow
        Write-Host "       GPU 사용 또는 시스템 리소스 확인 필요" -ForegroundColor Cyan
    }
    
} catch {
    Write-Host "  ✗ API 호출 실패" -ForegroundColor Red
    Write-Host "    오류: $($_.Exception.Message)" -ForegroundColor Yellow
}

Write-Host ""

# 6. 시스템 리소스 확인
Write-Host "[6단계] 시스템 리소스 확인" -ForegroundColor Yellow

# CPU
$cpu = Get-WmiObject Win32_Processor
Write-Host "  CPU:" -ForegroundColor Cyan
Write-Host "    - 이름: $($cpu.Name)" -ForegroundColor White
Write-Host "    - 코어: $($cpu.NumberOfCores)" -ForegroundColor White
Write-Host "    - 스레드: $($cpu.NumberOfLogicalProcessors)" -ForegroundColor White

# 메모리
$memory = Get-WmiObject Win32_OperatingSystem
$totalMemoryGB = [math]::Round($memory.TotalVisibleMemorySize / 1MB, 2)
$freeMemoryGB = [math]::Round($memory.FreePhysicalMemory / 1MB, 2)
$usedMemoryGB = $totalMemoryGB - $freeMemoryGB
$memoryUsagePercent = [math]::Round(($usedMemoryGB / $totalMemoryGB) * 100, 2)

Write-Host "  메모리:" -ForegroundColor Cyan
Write-Host "    - 전체: $totalMemoryGB GB" -ForegroundColor White
Write-Host "    - 사용: $usedMemoryGB GB ($memoryUsagePercent%)" -ForegroundColor White
Write-Host "    - 여유: $freeMemoryGB GB" -ForegroundColor White

if ($freeMemoryGB -lt 4) {
    Write-Host "    ⚠️  여유 메모리 부족 (4GB 이상 권장)" -ForegroundColor Yellow
} else {
    Write-Host "    ✓ 충분한 메모리" -ForegroundColor Green
}

# GPU (NVIDIA)
Write-Host "  GPU:" -ForegroundColor Cyan
try {
    $gpu = & nvidia-smi --query-gpu=name,memory.total,memory.free --format=csv,noheader 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "    - $gpu" -ForegroundColor White
        Write-Host "    ✓ NVIDIA GPU 감지 (가속 가능)" -ForegroundColor Green
    } else {
        Write-Host "    - GPU 없음 (CPU 모드)" -ForegroundColor Yellow
    }
} catch {
    Write-Host "    - GPU 없음 (CPU 모드)" -ForegroundColor Yellow
}

Write-Host ""

# 7. 백엔드 연동 상태 확인
Write-Host "[7단계] 백엔드 연동 상태 확인" -ForegroundColor Yellow

# 백엔드 실행 확인
$javaProcess = Get-Process -Name "java" -ErrorAction SilentlyContinue
if ($javaProcess) {
    Write-Host "  ✓ 백엔드(Java) 프로세스 실행 중" -ForegroundColor Green
    
    # Health Check
    try {
        $healthResponse = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" `
            -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
        Write-Host "  ✓ 백엔드 서비스 정상" -ForegroundColor Green
        
        # Ollama 연동 로그 확인 (API 호출 시 확인 가능)
        Write-Host ""
        Write-Host "  백엔드에서 Ollama 연동 테스트를 위해" -ForegroundColor Cyan
        Write-Host "  다음 API를 호출하세요:" -ForegroundColor Cyan
        Write-Host "  curl http://localhost:8080/api/product/info/81819" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "  백엔드 로그에서 다음을 확인:" -ForegroundColor Cyan
        Write-Host "    - 'Few-Shot LLM 파싱 전략 사용 가능'" -ForegroundColor White
        Write-Host "    - '쿼럼 기반 LLM 파싱 시작'" -ForegroundColor White
        Write-Host "    - '[Llama 3.1] 호출 시작'" -ForegroundColor White
        Write-Host "    - '[Mistral] 호출 시작'" -ForegroundColor White
        Write-Host "    - '[CodeLlama] 호출 시작'" -ForegroundColor White
        
    } catch {
        Write-Host "  ✗ 백엔드 서비스 연결 실패" -ForegroundColor Red
        Write-Host "    백엔드를 먼저 시작하세요" -ForegroundColor Yellow
    }
} else {
    Write-Host "  ✗ 백엔드가 실행되지 않음" -ForegroundColor Yellow
    Write-Host "    백엔드 시작: cd C:\insu_app\backend && .\mvnw.cmd spring-boot:run -DskipTests" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   상태 확인 완료" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 8. 요약 및 권장사항
Write-Host "[요약]" -ForegroundColor Yellow
Write-Host ""

$statusSummary = @{
    "Ollama 프로세스" = if ($ollamaProcess) { "✓ 실행 중" } else { "✗ 중지됨" }
    "Ollama 서비스" = "확인 완료 (위 참조)"
    "LLM 모델" = "확인 완료 (위 참조)"
    "시스템 리소스" = if ($freeMemoryGB -ge 4) { "✓ 충분" } else { "⚠️ 부족" }
    "백엔드 연동" = if ($javaProcess) { "✓ 준비됨" } else { "✗ 백엔드 미실행" }
}

foreach ($key in $statusSummary.Keys) {
    Write-Host "  $key`: $($statusSummary[$key])" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "[다음 단계]" -ForegroundColor Yellow

if (-not $javaProcess) {
    Write-Host "  1. 백엔드 시작" -ForegroundColor White
    Write-Host "     cd C:\insu_app\backend" -ForegroundColor Cyan
    Write-Host "     .\mvnw.cmd spring-boot:run -DskipTests" -ForegroundColor Cyan
    Write-Host ""
}

Write-Host "  2. 쿼럼 LLM 테스트 (복잡한 특약)" -ForegroundColor White
Write-Host "     curl http://localhost:8080/api/product/info/81819" -ForegroundColor Cyan
Write-Host ""
Write-Host "  3. 백엔드 로그에서 쿼럼 메시지 확인" -ForegroundColor White
Write-Host "     '쿼럼 달성 (2/3 합의)' 메시지 확인" -ForegroundColor Cyan
Write-Host ""


