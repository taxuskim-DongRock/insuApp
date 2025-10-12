# 백엔드 시작 가이드

## ⚠️ 현재 상황

**문제**: 백엔드가 백그라운드에서 시작되었으나 포트 8080이 열리지 않음  
**원인**: 시작 오류 발생 가능성 (콘솔 출력 확인 필요)

**Java 프로세스**: ✅ 실행 중 (PID: 28972, 메모리: 1.1GB)  
**포트 8080**: ❌ 리스닝 안 함

---

## 🔧 해결 방법: 콘솔에서 직접 실행

### **방법 1: 새 PowerShell에서 실행 (권장)**

1. **새 PowerShell 창 열기**
   - Windows 키 → "PowerShell" 검색
   - 우클릭 → "관리자 권한으로 실행"

2. **백엔드 실행**
   ```powershell
   cd C:\insu_app\backend
   .\mvnw.cmd spring-boot:run -DskipTests
   ```

3. **시작 로그 확인**
   - "Started Application in X.XXX seconds" 메시지 대기
   - 오류 메시지 확인

---

### **방법 2: 배치 파일 사용**

```powershell
cd C:\insu_app\backend
.\run-backend.bat
```

---

## 📊 정상 시작 시 예상 로그

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.1.5)

2025-10-11T22:00:00.000+09:00  INFO 12345 --- [           main] c.e.insu.BackendApplication              : Starting BackendApplication
2025-10-11T22:00:05.000+09:00  INFO 12345 --- [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port(s): 8080 (http)
2025-10-11T22:00:05.500+09:00  INFO 12345 --- [           main] c.e.insu.BackendApplication              : Started BackendApplication in 5.234 seconds

개선된 하이브리드 파싱 서비스 초기화 - 4 개 전략 로드
  - Python OCR (우선순위: 1)
  - 사업방법서 정규식 (우선순위: 2)
  - 기본 LLM (우선순위: 3)
  - Few-Shot LLM (우선순위: 4)

Few-Shot LLM 파싱 전략 사용 가능
Ollama 서비스 연결 성공

=== 캐시 통계 ===
캐시 크기: 0/1000
히트율: 0.00% (히트: 0, 미스: 0)
================
```

---

## 🚨 일반적인 오류 및 해결

### **오류 1: 포트 8080이 이미 사용 중**

```
***************************
APPLICATION FAILED TO START
***************************

Description:
Web server failed to start. Port 8080 was already in use.
```

**해결**:
```powershell
# 포트 사용 프로세스 찾기
Get-NetTCPConnection -LocalPort 8080 | Select-Object OwningProcess

# 프로세스 종료
Stop-Process -Id <PID> -Force

# 백엔드 재시작
.\mvnw.cmd spring-boot:run -DskipTests
```

---

### **오류 2: Oracle DB 연결 실패**

```
java.sql.SQLException: Listener refused the connection with the following error:
ORA-12505, TNS:listener does not currently know of SID given in connect descriptor
```

**해결**:
```powershell
# Oracle 연결 정보 확인
# application-offline.yml 파일 확인
Get-Content C:\insu_app\backend\src\main\resources\application-offline.yml

# Oracle 서비스 확인
Get-Service -Name "OracleService*"

# Oracle 서비스 시작 (필요 시)
Start-Service -Name "OracleServiceXE"
```

---

### **오류 3: Python 스크립트 실패**

```
Failed to execute Python script: parse_pdf_improved.py
```

**해결**:
```powershell
# Python 설치 확인
python --version

# 패키지 설치
cd C:\insu_app
pip install -r requirements.txt

# Python 경로 확인 (application-offline.yml)
```

---

### **오류 4: Ollama 연결 실패**

```
Ollama 서비스를 사용할 수 없음: Connection refused
```

**해결**:
```powershell
# Ollama 서비스 시작
Start-Process ollama -ArgumentList "serve" -WindowStyle Hidden

# 확인
curl http://localhost:11434
```

---

## 📋 백엔드 시작 체크리스트

### 사전 조건

- [ ] Java 17+ 설치 확인 (`java -version`)
- [ ] Oracle DB 실행 중
- [ ] Python 3.9+ 설치 및 패키지 설치
- [ ] Ollama 서비스 실행 중 (선택)
- [ ] 포트 8080 사용 가능

### 시작 확인

- [ ] `.\mvnw.cmd spring-boot:run -DskipTests` 실행
- [ ] "Started Application in X.XXX seconds" 로그 확인
- [ ] "Tomcat started on port(s): 8080" 로그 확인
- [ ] "개선된 하이브리드 파싱 서비스 초기화" 로그 확인
- [ ] "=== 캐시 통계 ===" 로그 확인 (1분 후)

### API 테스트

- [ ] Health Check: `curl http://localhost:8080/actuator/health`
- [ ] 상품 정보: `curl http://localhost:8080/api/product/info/21686`

---

## 🎯 빠른 시작 명령어

### **전체 시작 순서**

```powershell
# 1. 새 PowerShell (관리자) 열기

# 2. Oracle 서비스 확인
Get-Service -Name "OracleService*"

# 3. Ollama 서비스 시작
Start-Process ollama -ArgumentList "serve" -WindowStyle Hidden

# 4. 백엔드 시작
cd C:\insu_app\backend
.\mvnw.cmd spring-boot:run -DskipTests

# 5. 다른 PowerShell 창에서 확인 (1-2분 후)
curl http://localhost:8080/actuator/health

# 6. API 테스트
Measure-Command {
    Invoke-WebRequest -Uri "http://localhost:8080/api/product/info/21686" -UseBasicParsing
} | Select-Object TotalSeconds
```

---

## 📊 백엔드 모니터링

### **실시간 로그 확인**

백엔드가 실행 중인 PowerShell 창에서 스크롤하여 로그 확인

### **메모리 사용량 확인**

```powershell
Get-Process -Name "java" | Select-Object Id, ProcessName, 
    @{Name='Memory(MB)';Expression={[math]::Round($_.WorkingSet64/1MB,2)}}, 
    @{Name='CPU(s)';Expression={$_.CPU}}
```

### **포트 확인**

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen
```

---

## 🔧 디버그 모드 실행

더 상세한 로그가 필요한 경우:

```powershell
cd C:\insu_app\backend
.\mvnw.cmd spring-boot:run -DskipTests -Dlogging.level.com.example.insu=DEBUG
```

---

## 📝 다음 단계

**백엔드가 성공적으로 시작되면:**

1. **Health Check**
   ```powershell
   curl http://localhost:8080/actuator/health
   # 기대: {"status":"UP"}
   ```

2. **첫 API 테스트**
   ```powershell
   Measure-Command {
       Invoke-WebRequest -Uri "http://localhost:8080/api/product/info/21686" -UseBasicParsing
   } | Select-Object TotalSeconds
   # 기대: 4-6초
   ```

3. **캐시 히트 테스트**
   ```powershell
   Measure-Command {
       Invoke-WebRequest -Uri "http://localhost:8080/api/product/info/21686" -UseBasicParsing
   } | Select-Object TotalSeconds
   # 기대: 0.5초 (90%+ 성능 향상!)
   ```

4. **쿼럼 LLM 테스트**
   ```powershell
   Measure-Command {
       Invoke-WebRequest -Uri "http://localhost:8080/api/product/info/81819" -UseBasicParsing
   } | Select-Object TotalSeconds
   # 기대: 8-12초 (73% 단축!)
   ```

---

**작성일**: 2025-10-11  
**상태**: 백엔드 시작 대기

**액션 필요**: 새 PowerShell에서 `.\mvnw.cmd spring-boot:run -DskipTests` 실행 후 로그 확인


