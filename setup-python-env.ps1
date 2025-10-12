# Python 환경 설정 스크립트
param(
    [string]$Environment = "full",  # full, minimal, dev, ai
    [string]$PythonVersion = "3.11"
)

Write-Host "========================================" -ForegroundColor Green
Write-Host "Python 환경 설정 시작" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green

# Python 버전 확인
Write-Host "`n1. Python 환경 확인..." -ForegroundColor Yellow
try {
    $pythonVersion = python --version 2>&1
    Write-Host "Python 버전: $pythonVersion" -ForegroundColor Green
    
    # pip 업그레이드
    Write-Host "pip 업그레이드 중..." -ForegroundColor Yellow
    python -m pip install --upgrade pip
    Write-Host "✅ pip 업그레이드 완료" -ForegroundColor Green
} catch {
    Write-Host "❌ Python이 설치되지 않았습니다. Python $PythonVersion+ 설치가 필요합니다." -ForegroundColor Red
    Write-Host "다운로드: https://www.python.org/downloads/" -ForegroundColor Yellow
    exit 1
}

# 가상환경 생성
Write-Host "`n2. 가상환경 생성..." -ForegroundColor Yellow
$venvName = "insu-env"
if (Test-Path $venvName) {
    Write-Host "기존 가상환경 삭제 중..." -ForegroundColor Yellow
    Remove-Item -Recurse -Force $venvName
}

python -m venv $venvName
Write-Host "✅ 가상환경 생성 완료: $venvName" -ForegroundColor Green

# 가상환경 활성화
Write-Host "`n3. 가상환경 활성화..." -ForegroundColor Yellow
& "$venvName\Scripts\Activate.ps1"
Write-Host "✅ 가상환경 활성화 완료" -ForegroundColor Green

# 요구사항 파일 선택
Write-Host "`n4. 패키지 설치..." -ForegroundColor Yellow
$requirementsFile = switch ($Environment) {
    "minimal" { "requirements-minimal.txt" }
    "dev" { "requirements-dev.txt" }
    "ai" { "requirements-ai.txt" }
    default { "requirements.txt" }
}

if (Test-Path $requirementsFile) {
    Write-Host "설치할 requirements 파일: $requirementsFile" -ForegroundColor Cyan
    Write-Host "패키지 설치 중... (시간이 걸릴 수 있습니다)" -ForegroundColor Yellow
    
    pip install -r $requirementsFile
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ 패키지 설치 완료" -ForegroundColor Green
    } else {
        Write-Host "❌ 패키지 설치 실패" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "❌ Requirements 파일을 찾을 수 없습니다: $requirementsFile" -ForegroundColor Red
    exit 1
}

# 설치된 패키지 확인
Write-Host "`n5. 설치된 패키지 확인..." -ForegroundColor Yellow
pip list | Select-String -Pattern "(pandas|numpy|PyPDF2|requests|cx_Oracle)"

# 환경 정보 저장
Write-Host "`n6. 환경 정보 저장..." -ForegroundColor Yellow
$envInfo = @"
# Python 환경 정보
생성일: $(Get-Date)
Python 버전: $pythonVersion
환경: $Environment
Requirements 파일: $requirementsFile

# 가상환경 활성화 방법
# Windows PowerShell:
& "$venvName\Scripts\Activate.ps1"

# Windows CMD:
$venvName\Scripts\activate.bat

# Linux/Mac:
source $venvName/bin/activate
"@

$envInfo | Out-File -FilePath "python-env-info.txt" -Encoding UTF8
Write-Host "✅ 환경 정보 저장 완료: python-env-info.txt" -ForegroundColor Green

Write-Host "`n========================================" -ForegroundColor Green
Write-Host "✅ Python 환경 설정 완료!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green

Write-Host "`n다음 단계:" -ForegroundColor Cyan
Write-Host "1. 가상환경 활성화:" -ForegroundColor White
Write-Host "   & `"$venvName\Scripts\Activate.ps1`"" -ForegroundColor Gray

Write-Host "`n2. Python 스크립트 실행:" -ForegroundColor White
Write-Host "   python batch_parse_all.py" -ForegroundColor Gray

Write-Host "`n3. Jupyter Notebook 실행 (개발 환경인 경우):" -ForegroundColor White
Write-Host "   jupyter notebook" -ForegroundColor Gray

Write-Host "`n사용 가능한 환경:" -ForegroundColor Cyan
Write-Host "- full: 모든 패키지 (기본)" -ForegroundColor White
Write-Host "- minimal: 최소 필수 패키지만" -ForegroundColor White
Write-Host "- dev: 개발 도구 포함" -ForegroundColor White
Write-Host "- ai: AI/LLM 패키지 포함" -ForegroundColor White

Write-Host "`n예시:" -ForegroundColor Yellow
Write-Host ".\setup-python-env.ps1 -Environment minimal" -ForegroundColor Gray
Write-Host ".\setup-python-env.ps1 -Environment dev" -ForegroundColor Gray
