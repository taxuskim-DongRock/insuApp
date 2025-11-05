@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo    보험 앱 개발 환경 정리 스크립트
echo ========================================
echo.

set PROJECT_DIR=%~dp0

echo [WARNING] 이 스크립트는 다음 작업을 수행합니다:
echo - 백엔드 서버 프로세스 종료
echo - 프론트엔드 서버 프로세스 종료
echo - 임시 파일 및 캐시 정리
echo - 로그 파일 정리
echo.
set /p CONFIRM="계속하시겠습니까? (y/n): "
if /i not "%CONFIRM%"=="y" (
    echo 작업이 취소되었습니다.
    pause
    exit /b 0
)

:: ========================================
:: 서버 프로세스 종료
:: ========================================
echo.
echo [1/4] 서버 프로세스 종료 중...

:: Spring Boot 프로세스 종료
echo [INFO] Spring Boot 프로세스 종료 중...
for /f "tokens=2" %%i in ('tasklist /fi "imagename eq java.exe" /fo csv ^| findstr "spring-boot"') do (
    echo [INFO] 프로세스 %%i 종료 중...
    taskkill /pid %%i /f >nul 2>&1
)

:: Node.js 프로세스 종료
echo [INFO] Node.js 프로세스 종료 중...
for /f "tokens=2" %%i in ('tasklist /fi "imagename eq node.exe" /fo csv ^| findstr "vite"') do (
    echo [INFO] 프로세스 %%i 종료 중...
    taskkill /pid %%i /f >nul 2>&1
)

:: ========================================
:: 임시 파일 정리
:: ========================================
echo.
echo [2/4] 임시 파일 정리 중...

:: 백엔드 임시 파일
if exist "%PROJECT_DIR%backend\target" (
    echo [INFO] 백엔드 target 디렉토리 정리 중...
    rmdir /s /q "%PROJECT_DIR%backend\target" >nul 2>&1
)

if exist "%PROJECT_DIR%backend\.mvn" (
    echo [INFO] 백엔드 .mvn 디렉토리 정리 중...
    rmdir /s /q "%PROJECT_DIR%backend\.mvn" >nul 2>&1
)

:: 프론트엔드 임시 파일
if exist "%PROJECT_DIR%insu_ui\dist" (
    echo [INFO] 프론트엔드 dist 디렉토리 정리 중...
    rmdir /s /q "%PROJECT_DIR%insu_ui\dist" >nul 2>&1
)

if exist "%PROJECT_DIR%insu_ui\dist-electron" (
    echo [INFO] 프론트엔드 dist-electron 디렉토리 정리 중...
    rmdir /s /q "%PROJECT_DIR%insu_ui\dist-electron" >nul 2>&1
)

if exist "%PROJECT_DIR%insu_ui\node_modules\.cache" (
    echo [INFO] Node.js 캐시 정리 중...
    rmdir /s /q "%PROJECT_DIR%insu_ui\node_modules\.cache" >nul 2>&1
)

:: ========================================
:: 로그 파일 정리
:: ========================================
echo.
echo [3/4] 로그 파일 정리 중...

:: 백엔드 로그
if exist "%PROJECT_DIR%backend\logs" (
    echo [INFO] 백엔드 로그 정리 중...
    del /q "%PROJECT_DIR%backend\logs\*" >nul 2>&1
)

:: 프론트엔드 로그
if exist "%PROJECT_DIR%insu_ui\logs" (
    echo [INFO] 프론트엔드 로그 정리 중...
    del /q "%PROJECT_DIR%insu_ui\logs\*" >nul 2>&1
)

:: 시스템 임시 파일
echo [INFO] 시스템 임시 파일 정리 중...
del /q /f /s "%TEMP%\*" >nul 2>&1
del /q /f /s "%LOCALAPPDATA%\Temp\*" >nul 2>&1

:: ========================================
:: Python 캐시 정리
:: ========================================
echo.
echo [4/4] Python 캐시 정리 중...

:: __pycache__ 디렉토리 정리
for /r "%PROJECT_DIR%" %%d in (__pycache__) do (
    if exist "%%d" (
        echo [INFO] Python 캐시 정리 중: %%d
        rmdir /s /q "%%d" >nul 2>&1
    )
)

:: .pyc 파일 정리
for /r "%PROJECT_DIR%" %%f in (*.pyc) do (
    if exist "%%f" (
        echo [INFO] Python 컴파일 파일 정리 중: %%f
        del /q "%%f" >nul 2>&1
    )
)

:: pip 캐시 정리
echo [INFO] pip 캐시 정리 중...
python -m pip cache purge >nul 2>&1

:: ========================================
:: 완료 메시지
:: ========================================
echo.
echo ========================================
echo    정리 완료!
echo ========================================
echo.
echo 정리된 항목:
echo - 서버 프로세스 종료
echo - 임시 파일 및 캐시 정리
echo - 로그 파일 정리
echo - Python 캐시 정리
echo.
echo 개발 환경을 다시 시작하려면 start-servers.bat을 실행하세요.
echo.
pause
