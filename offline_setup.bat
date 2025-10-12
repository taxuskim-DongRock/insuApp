@echo off
echo ========================================
echo    보험 문서 파싱 시스템 오프라인 설치
echo ========================================

echo.
echo [1단계] Java 환경 확인...
java -version
if %errorlevel% neq 0 (
    echo Java가 설치되지 않았습니다. OpenJDK 17+ 설치가 필요합니다.
    pause
    exit /b 1
)

echo.
echo [2단계] Ollama 설치...
winget install Ollama.Ollama
if %errorlevel% neq 0 (
    echo Ollama 설치 실패. 수동 설치가 필요합니다.
    echo https://ollama.ai/download 에서 다운로드하세요.
    pause
)

echo.
echo [3단계] Ollama 서비스 시작...
ollama serve &
timeout /t 5 /nobreak > nul

echo.
echo [4단계] 모델 다운로드...
echo Llama 3.1 8B 다운로드 중... (약 4.7GB)
ollama pull llama3.1:8b

echo Mistral 7B 다운로드 중... (약 4.1GB)  
ollama pull mistral:7b

echo CodeLlama 7B 다운로드 중... (약 3.8GB)
ollama pull codellama:7b

echo.
echo [5단계] 디렉토리 생성...
mkdir C:\insu_app\models 2>nul
mkdir C:\insu_app\cache 2>nul
mkdir C:\insu_app\logs 2>nul

echo.
echo [6단계] Spring Boot 애플리케이션 빌드...
cd C:\insu_app\backend
call mvn clean package -DskipTests

echo.
echo [7단계] 설정 파일 생성...
echo # 오프라인 모드 설정 > C:\insu_app\application-offline.yml
echo spring: >> C:\insu_app\application-offline.yml
echo   profiles: >> C:\insu_app\application-offline.yml
echo     active: offline >> C:\insu_app\application-offline.yml
echo   ollama: >> C:\insu_app\application-offline.yml
echo     url: http://localhost:11434 >> C:\insu_app\application-offline.yml
echo   cache: >> C:\insu_app\application-offline.yml
echo     enabled: true >> C:\insu_app\application-offline.yml
echo     directory: C:\insu_app\cache >> C:\insu_app\application-offline.yml

echo.
echo ========================================
echo    오프라인 설치 완료!
echo ========================================
echo.
echo 사용법:
echo 1. 백엔드 시작: cd C:\insu_app\backend ^&^& java -jar target\insu-backend.jar
echo 2. 프론트엔드 시작: cd C:\insu_ui ^&^& npm run dev
echo.
echo 주의사항:
echo - 인터넷 연결 없이도 사용 가능합니다
echo - 모든 처리가 로컬에서 완료됩니다
echo - 민감한 데이터가 외부로 전송되지 않습니다
echo.
pause

