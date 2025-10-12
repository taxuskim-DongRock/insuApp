@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul

REM =============================================================================
REM  run-backend.bat
REM  - Spring Boot (mvnw) 실행 스크립트
REM  - 기본 프로파일: local  (필요시 첫 번째 인자로 변경 가능)
REM  - 선택적으로 .env-db 파일에서 DB_* 환경변수 로드
REM =============================================================================

REM 스크립트 위치로 이동 (backend 루트에 두는 걸 권장)
pushd "%~dp0"

REM -------- 옵션: 프로파일 인자 처리 ------------------------------------------
set "PROFILE=local"
if not "%~1"=="" set "PROFILE=%~1"

REM -------- 옵션: .env-db 파일에서 DB 환경변수 로드 -----------------------------
if exist ".env-db" (
  echo [INFO] Loading .env-db ...
  for /f "usebackq tokens=1,* delims== eol=#" %%A in (".env-db") do (
    set "k=%%A"
    set "v=%%B"
    if defined k (
      REM 따옴표 제거
      set "v=!v:"=!"
      set "!k!=!v!"
    )
  )
)

REM -------- 안내 출력 -----------------------------------------------------------
echo [INFO] SPRING_PROFILES_ACTIVE=%PROFILE%
if defined DB_USER     echo [INFO] DB_USER=%DB_USER%
if defined DB_PASSWORD echo [INFO] DB_PASSWORD=********
if defined DB_HOST     echo [INFO] DB_HOST=%DB_HOST%
if defined DB_PORT     echo [INFO] DB_PORT=%DB_PORT%
if defined DB_SERVICE  echo [INFO] DB_SERVICE=%DB_SERVICE%
echo.

REM -------- mvnw 존재 확인 ------------------------------------------------------
if not exist "mvnw.cmd" (
  echo [ERROR] mvnw.cmd not found. Place this script in the backend folder.
  popd & endlocal & exit /b 1
)

REM -------- 실행 옵션(인코딩/메모리) -------------------------------------------
set "JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8"
set "MAVEN_OPTS=-Xms256m -Xmx1024m"

REM -------- Spring Boot 실행 ----------------------------------------------------
echo [INFO] Starting Spring Boot with profile=%PROFILE% ...
call mvnw.cmd "-Dspring-boot.run.profiles=%PROFILE%" spring-boot:run
set "EC=%ERRORLEVEL%"

REM -------- 종료 처리 -----------------------------------------------------------
if not "%EC%"=="0" (
  echo.
  echo [ERROR] Spring Boot exited with code %EC%
  echo Press any key to close...
  pause >nul
)

popd
endlocal
