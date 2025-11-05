@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo    보험 앱 개발 환경 설치 스크립트
echo ========================================
echo.

:: 관리자 권한 확인
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo [ERROR] 관리자 권한이 필요합니다. 관리자로 실행해주세요.
    pause
    exit /b 1
)

:: 설치 디렉토리 설정
set INSTALL_DIR=C:\dev-tools
set PROJECT_DIR=%~dp0

echo [INFO] 설치 디렉토리: %INSTALL_DIR%
echo [INFO] 프로젝트 디렉토리: %PROJECT_DIR%
echo.

:: 디렉토리 생성
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"

:: ========================================
:: 1. Java 17 설치
:: ========================================
echo [1/8] Java 17 설치 확인 중...
java -version >nul 2>&1
if %errorLevel% neq 0 (
    echo [INFO] Java가 설치되지 않았습니다. Java 17을 설치합니다...
    
    :: OpenJDK 17 다운로드 및 설치
    set JAVA_URL=https://download.java.net/java/GA/jdk17.0.2/dfd4a8d0985749f896bed50d7138ee7f/8/GPL/openjdk-17.0.2_windows-x64_bin.zip
    set JAVA_ZIP=%INSTALL_DIR%\openjdk-17.zip
    set JAVA_DIR=%INSTALL_DIR%\jdk-17.0.2
    
    echo [INFO] Java 17 다운로드 중...
    powershell -Command "Invoke-WebRequest -Uri '%JAVA_URL%' -OutFile '%JAVA_ZIP%'"
    
    if exist "%JAVA_ZIP%" (
        echo [INFO] Java 17 압축 해제 중...
        powershell -Command "Expand-Archive -Path '%JAVA_ZIP%' -DestinationPath '%INSTALL_DIR%' -Force"
        
        :: 환경변수 설정
        setx JAVA_HOME "%JAVA_DIR%" /M
        setx PATH "%JAVA_DIR%\bin;%PATH%" /M
        
        echo [INFO] Java 17 설치 완료
        del "%JAVA_ZIP%"
    ) else (
        echo [ERROR] Java 17 다운로드 실패
    )
) else (
    echo [INFO] Java가 이미 설치되어 있습니다.
)

:: ========================================
:: 2. Node.js 18 설치
:: ========================================
echo.
echo [2/8] Node.js 18 설치 확인 중...
node --version >nul 2>&1
if %errorLevel% neq 0 (
    echo [INFO] Node.js가 설치되지 않았습니다. Node.js 18을 설치합니다...
    
    set NODE_URL=https://nodejs.org/dist/v18.19.0/node-v18.19.0-x64.msi
    set NODE_MSI=%INSTALL_DIR%\nodejs-installer.msi
    
    echo [INFO] Node.js 18 다운로드 중...
    powershell -Command "Invoke-WebRequest -Uri '%NODE_URL%' -OutFile '%NODE_MSI%'"
    
    if exist "%NODE_MSI%" (
        echo [INFO] Node.js 18 설치 중...
        msiexec /i "%NODE_MSI%" /quiet /norestart
        
        :: PATH 업데이트
        setx PATH "%PROGRAMFILES%\nodejs;%PATH%" /M
        
        echo [INFO] Node.js 18 설치 완료
        del "%NODE_MSI%"
    ) else (
        echo [ERROR] Node.js 18 다운로드 실패
    )
) else (
    echo [INFO] Node.js가 이미 설치되어 있습니다.
)

:: ========================================
:: 3. Python 3.11 설치
:: ========================================
echo.
echo [3/8] Python 3.11 설치 확인 중...
python --version >nul 2>&1
if %errorLevel% neq 0 (
    echo [INFO] Python이 설치되지 않았습니다. Python 3.11을 설치합니다...
    
    set PYTHON_URL=https://www.python.org/ftp/python/3.11.7/python-3.11.7-amd64.exe
    set PYTHON_EXE=%INSTALL_DIR%\python-installer.exe
    
    echo [INFO] Python 3.11 다운로드 중...
    powershell -Command "Invoke-WebRequest -Uri '%PYTHON_URL%' -OutFile '%PYTHON_EXE%'"
    
    if exist "%PYTHON_EXE%" (
        echo [INFO] Python 3.11 설치 중...
        "%PYTHON_EXE%" /quiet InstallAllUsers=1 PrependPath=1 Include_test=0
        
        echo [INFO] Python 3.11 설치 완료
        del "%PYTHON_EXE%"
    ) else (
        echo [ERROR] Python 3.11 다운로드 실패
    )
) else (
    echo [INFO] Python이 이미 설치되어 있습니다.
)

:: ========================================
:: 4. Oracle Database Express Edition 설치
:: ========================================
echo.
echo [4/8] Oracle Database 설치 확인 중...
sqlplus /nolog >nul 2>&1
if %errorLevel% neq 0 (
    echo [INFO] Oracle Database가 설치되지 않았습니다.
    echo [INFO] Oracle XE 21c를 설치합니다...
    
    set ORACLE_URL=https://download.oracle.com/otn-pub/otn_software/db-express/oracle-database-xe-21c-1.0-1.x64.exe
    set ORACLE_EXE=%INSTALL_DIR%\oracle-xe-installer.exe
    
    echo [INFO] Oracle XE 21c 다운로드 중...
    powershell -Command "Invoke-WebRequest -Uri '%ORACLE_URL%' -OutFile '%ORACLE_EXE%'"
    
    if exist "%ORACLE_EXE%" (
        echo [INFO] Oracle XE 21c 설치 중...
        echo [WARNING] Oracle 설치 시 관리자 비밀번호를 설정해주세요.
        "%ORACLE_EXE%" /silent /responseFile="%INSTALL_DIR%\oracle_response.rsp"
        
        echo [INFO] Oracle XE 21c 설치 완료
        del "%ORACLE_EXE%"
    ) else (
        echo [ERROR] Oracle XE 21c 다운로드 실패
        echo [INFO] 수동으로 Oracle XE를 설치해주세요.
    )
) else (
    echo [INFO] Oracle Database가 이미 설치되어 있습니다.
)

:: ========================================
:: 5. Python 패키지 설치
:: ========================================
echo.
echo [5/8] Python 패키지 설치 중...
if exist "%PROJECT_DIR%requirements.txt" (
    echo [INFO] Python 의존성 패키지 설치 중...
    python -m pip install --upgrade pip
    python -m pip install -r "%PROJECT_DIR%requirements.txt"
    echo [INFO] Python 패키지 설치 완료
) else (
    echo [WARNING] requirements.txt 파일을 찾을 수 없습니다.
)

:: ========================================
:: 6. Node.js 패키지 설치
:: ========================================
echo.
echo [6/8] Node.js 패키지 설치 중...
if exist "%PROJECT_DIR%insu_ui\package.json" (
    echo [INFO] 프론트엔드 의존성 패키지 설치 중...
    cd /d "%PROJECT_DIR%insu_ui"
    npm install
    echo [INFO] Node.js 패키지 설치 완료
) else (
    echo [WARNING] package.json 파일을 찾을 수 없습니다.
)

:: ========================================
:: 7. Maven 의존성 설치
:: ========================================
echo.
echo [7/8] Maven 의존성 설치 중...
if exist "%PROJECT_DIR%backend\pom.xml" (
    echo [INFO] 백엔드 의존성 패키지 설치 중...
    cd /d "%PROJECT_DIR%backend"
    call mvnw.cmd clean compile
    echo [INFO] Maven 의존성 설치 완료
) else (
    echo [WARNING] pom.xml 파일을 찾을 수 없습니다.
)

:: ========================================
:: 8. 환경 설정 파일 생성
:: ========================================
echo.
echo [8/8] 환경 설정 파일 생성 중...

:: Oracle 응답 파일 생성
set ORACLE_RESPONSE=%INSTALL_DIR%\oracle_response.rsp
(
echo [GENERAL]
echo RESPONSEFILE_VERSION="11.2.0"
echo DECLINE_SECURITY_UPDATES=true
echo.
echo [SYSTEM]
echo SELECTED_LANGUAGES=en,ko
echo.
echo [oracle.install.db.config.starterdb]
echo SID=XE
echo PASSWORD=oracle123
echo SYSPASSWORD=oracle123
echo SYSTEMPASSWORD=oracle123
echo DATABASETYPE=MULTIPURPOSE
echo CHARACTERSET=AL32UTF8
echo NATIONALCHARACTERSET=AL16UTF16
echo.
echo [oracle.install.db.config.starterdb.control]
echo CONTROLFILE=("C:\oraclexe\oradata\XE\control01.ctl", "C:\oraclexe\oradata\XE\control02.ctl")
echo.
echo [oracle.install.db.config.starterdb.datafile]
echo ORACLE_BASE=C:\oraclexe
echo ORACLE_HOME=C:\oraclexe\app\oracle\product\21c\dbhomeXE
echo SID=XE
echo.
echo [oracle.install.db.config.starterdb.templist]
echo TEMPLIST=("General_Purpose.dbc")
echo.
echo [oracle.install.db.config.starterdb.automemory]
echo TOTALMEMORY=2048
) > "%ORACLE_RESPONSE%"

:: 환경 변수 설정 파일 생성
set ENV_FILE=%PROJECT_DIR%\.env
(
echo # 개발 환경 설정
echo JAVA_HOME=%INSTALL_DIR%\jdk-17.0.2
echo ORACLE_HOME=C:\oraclexe\app\oracle\product\21c\dbhomeXE
echo ORACLE_SID=XE
echo.
echo # 데이터베이스 연결 정보
echo DB_URL=jdbc:oracle:thin:@//localhost:1521/XEPDB1
echo DB_USERNAME=devown
echo DB_PASSWORD=own20250101
echo.
echo # 백엔드 설정
echo BACKEND_PORT=8081
echo.
echo # 프론트엔드 설정
echo VITE_API_BASE=http://localhost:8081
) > "%ENV_FILE%"

:: ========================================
:: 설치 완료 메시지
:: ========================================
echo.
echo ========================================
echo    설치 완료!
echo ========================================
echo.
echo 설치된 소프트웨어:
echo - Java 17
echo - Node.js 18
echo - Python 3.11
echo - Oracle Database XE 21c
echo - Python 패키지 (requirements.txt)
echo - Node.js 패키지 (package.json)
echo - Maven 의존성 (pom.xml)
echo.
echo 다음 단계:
echo 1. 시스템을 재시작하여 환경변수가 적용되도록 합니다.
echo 2. Oracle Database 서비스를 시작합니다.
echo 3. 백엔드 서버를 시작합니다: cd backend ^&^& mvnw.cmd spring-boot:run
echo 4. 프론트엔드 서버를 시작합니다: cd insu_ui ^&^& npm run dev
echo.
echo 환경 설정 파일: %ENV_FILE%
echo.

:: 시스템 재시작 확인
set /p RESTART="시스템을 재시작하시겠습니까? (y/n): "
if /i "%RESTART%"=="y" (
    echo [INFO] 시스템을 재시작합니다...
    shutdown /r /t 30 /c "개발 환경 설치 완료로 인한 시스템 재시작"
) else (
    echo [INFO] 수동으로 시스템을 재시작해주세요.
)

pause
