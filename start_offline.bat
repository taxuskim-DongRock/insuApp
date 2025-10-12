@echo off
echo ========================================
echo    보험 문서 파싱 시스템 오프라인 실행
echo ========================================

echo.
echo [1단계] Ollama 서비스 확인...
tasklist /FI "IMAGENAME eq ollama.exe" 2>NUL | find /I /N "ollama.exe">NUL
if "%ERRORLEVEL%"=="0" (
    echo Ollama 서비스가 이미 실행 중입니다.
) else (
    echo Ollama 서비스 시작 중...
    start /B ollama serve
    timeout /t 10 /nobreak > nul
)

echo.
echo [2단계] 모델 상태 확인...
ollama list
if %errorlevel% neq 0 (
    echo Ollama 서비스에 연결할 수 없습니다.
    echo 오프라인 설치를 먼저 실행하세요.
    pause
    exit /b 1
)

echo.
echo [3단계] 백엔드 서비스 시작...
cd C:\insu_app\backend
echo Spring Boot 애플리케이션 시작 중...
start /B java -jar target\insu-backend.jar --spring.profiles.active=offline
timeout /t 15 /nobreak > nul

echo.
echo [4단계] 프론트엔드 서비스 시작...
cd C:\insu_ui
echo React 애플리케이션 시작 중...
start /B npm run dev
timeout /t 10 /nobreak > nul

echo.
echo ========================================
echo    오프라인 시스템 실행 완료!
echo ========================================
echo.
echo 접속 정보:
echo - 백엔드: http://localhost:8080
echo - 프론트엔드: http://localhost:5173
echo - Ollama: http://localhost:11434
echo.
echo 특징:
echo ✓ 완전 오프라인 실행
echo ✓ 내부망 환경 지원
echo ✓ 민감한 데이터 보호
echo ✓ 로컬 LLM 활용
echo.
echo 시스템을 종료하려면 Ctrl+C를 누르세요.
echo.
pause

