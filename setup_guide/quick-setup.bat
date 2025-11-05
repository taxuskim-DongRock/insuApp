@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo    보험 앱 빠른 개발 환경 설정
echo ========================================
echo.

set PROJECT_DIR=%~dp0

:: ========================================
:: 필수 소프트웨어 설치 확인
:: ========================================
echo [1/4] 필수 소프트웨어 확인 중...

:: Java 확인
java -version >nul 2>&1
if %errorLevel% neq 0 (
    echo [ERROR] Java가 설치되지 않았습니다.
    echo [INFO] https://adoptium.net/temurin/releases/ 에서 Java 17을 설치해주세요.
    goto :error
) else (
    echo [OK] Java 설치됨
)

:: Node.js 확인
node --version >nul 2>&1
if %errorLevel% neq 0 (
    echo [ERROR] Node.js가 설치되지 않았습니다.
    echo [INFO] https://nodejs.org/ 에서 Node.js 18을 설치해주세요.
    goto :error
) else (
    echo [OK] Node.js 설치됨
)

:: Python 확인
python --version >nul 2>&1
if %errorLevel% neq 0 (
    echo [ERROR] Python이 설치되지 않았습니다.
    echo [INFO] https://www.python.org/downloads/ 에서 Python 3.11을 설치해주세요.
    goto :error
) else (
    echo [OK] Python 설치됨
)

:: Oracle 확인
sqlplus /nolog >nul 2>&1
if %errorLevel% neq 0 (
    echo [WARNING] Oracle Database가 설치되지 않았습니다.
    echo [INFO] https://www.oracle.com/database/technologies/xe-downloads.html 에서 Oracle XE를 설치해주세요.
) else (
    echo [OK] Oracle Database 설치됨
)

:: ========================================
:: Python 패키지 설치
:: ========================================
echo.
echo [2/4] Python 패키지 설치 중...
if exist "%PROJECT_DIR%requirements.txt" (
    echo [INFO] Python 의존성 설치 중...
    python -m pip install --upgrade pip
    python -m pip install -r "%PROJECT_DIR%requirements.txt"
    echo [OK] Python 패키지 설치 완료
) else (
    echo [WARNING] requirements.txt 파일을 찾을 수 없습니다.
)

:: ========================================
:: Node.js 패키지 설치
:: ========================================
echo.
echo [3/4] Node.js 패키지 설치 중...
if exist "%PROJECT_DIR%insu_ui\package.json" (
    echo [INFO] 프론트엔드 의존성 설치 중...
    cd /d "%PROJECT_DIR%insu_ui"
    npm install
    echo [OK] Node.js 패키지 설치 완료
) else (
    echo [WARNING] package.json 파일을 찾을 수 없습니다.
)

:: ========================================
:: Maven 의존성 설치
:: ========================================
echo.
echo [4/4] Maven 의존성 설치 중...
if exist "%PROJECT_DIR%backend\pom.xml" (
    echo [INFO] 백엔드 의존성 설치 중...
    cd /d "%PROJECT_DIR%backend"
    call mvnw.cmd clean compile
    echo [OK] Maven 의존성 설치 완료
) else (
    echo [WARNING] pom.xml 파일을 찾을 수 없습니다.
)

:: ========================================
:: 완료 메시지
:: ========================================
echo.
echo ========================================
echo    설정 완료!
echo ========================================
echo.
echo 다음 명령어로 서버를 시작할 수 있습니다:
echo.
echo 백엔드 시작:
echo   cd backend
echo   mvnw.cmd spring-boot:run
echo.
echo 프론트엔드 시작:
echo   cd insu_ui
echo   npm run dev
echo.
echo 브라우저에서 http://localhost:5173 으로 접속하세요.
echo.
pause
exit /b 0

:error
echo.
echo [ERROR] 필수 소프트웨어가 설치되지 않았습니다.
echo 위의 링크에서 필요한 소프트웨어를 설치한 후 다시 실행해주세요.
echo.
pause
exit /b 1
