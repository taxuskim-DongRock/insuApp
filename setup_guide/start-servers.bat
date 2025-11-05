@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo    보험 앱 서버 시작 스크립트
echo ========================================
echo.

set PROJECT_DIR=%~dp0

:: 백엔드 서버 시작
echo [1/2] 백엔드 서버 시작 중...
if exist "%PROJECT_DIR%backend\mvnw.cmd" (
    echo [INFO] Spring Boot 백엔드 서버를 시작합니다...
    start "백엔드 서버" cmd /k "cd /d %PROJECT_DIR%backend && mvnw.cmd spring-boot:run"
    echo [OK] 백엔드 서버가 백그라운드에서 시작되었습니다.
    echo [INFO] 백엔드 서버: http://localhost:8081
) else (
    echo [ERROR] backend\mvnw.cmd 파일을 찾을 수 없습니다.
)

:: 잠시 대기 (백엔드 서버 시작 시간)
echo [INFO] 백엔드 서버 시작을 위해 10초 대기 중...
timeout /t 10 /nobreak >nul

:: 프론트엔드 서버 시작
echo.
echo [2/2] 프론트엔드 서버 시작 중...
if exist "%PROJECT_DIR%insu_ui\package.json" (
    echo [INFO] React 프론트엔드 서버를 시작합니다...
    start "프론트엔드 서버" cmd /k "cd /d %PROJECT_DIR%insu_ui && npm run dev"
    echo [OK] 프론트엔드 서버가 백그라운드에서 시작되었습니다.
    echo [INFO] 프론트엔드 서버: http://localhost:5173
) else (
    echo [ERROR] insu_ui\package.json 파일을 찾을 수 없습니다.
)

:: ========================================
:: 완료 메시지
:: ========================================
echo.
echo ========================================
echo    서버 시작 완료!
echo ========================================
echo.
echo 접속 URL:
echo - 프론트엔드: http://localhost:5173
echo - 백엔드 API: http://localhost:8081
echo - API 문서: http://localhost:8081/swagger-ui.html
echo.
echo 서버를 중지하려면 각 서버 창에서 Ctrl+C를 누르세요.
echo.
echo 5초 후 브라우저가 자동으로 열립니다...
timeout /t 5 /nobreak >nul

:: 브라우저 자동 열기
start http://localhost:5173

echo [INFO] 브라우저가 열렸습니다.
echo.
pause
