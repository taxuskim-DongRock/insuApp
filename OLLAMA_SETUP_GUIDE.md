# Ollama 설치 및 설정 가이드

## ✅ Ollama 설치 완료!

**설치 버전**: 0.12.5  
**설치 크기**: 1.09 GB  
**설치 위치**: `C:\Users\%USERNAME%\AppData\Local\Programs\Ollama`

---

## ⚠️ 설치 후 작업 필요

### 문제: ollama 명령어를 찾을 수 없음

**원인:** Ollama가 시스템 PATH에 자동 추가되지 않음 (PowerShell 재시작 필요)

**해결 방법 3가지:**

---

### 방법 1: PowerShell 재시작 (권장) ⭐⭐⭐⭐⭐

```powershell
# 1. 현재 PowerShell 종료
exit

# 2. 새 PowerShell 열기 (관리자 권한)
# Windows 키 → "PowerShell" 검색 → 우클릭 → "관리자 권한으로 실행"

# 3. Ollama 확인
ollama --version

# 기대 출력:
# ollama version is 0.12.5
```

---

### 방법 2: 직접 경로로 실행

```powershell
# Ollama 실행 파일 직접 경로
$env:OLLAMA_PATH = "$env:LOCALAPPDATA\Programs\Ollama\ollama.exe"

# 버전 확인
& $env:OLLAMA_PATH --version

# 모델 다운로드
& $env:OLLAMA_PATH pull llama3.1:8b
& $env:OLLAMA_PATH pull mistral:7b
& $env:OLLAMA_PATH pull codellama:7b

# 서비스 시작
& $env:OLLAMA_PATH serve
```

---

### 방법 3: PATH에 수동 추가

```powershell
# 현재 세션에만 추가
$env:Path += ";$env:LOCALAPPDATA\Programs\Ollama"

# 확인
ollama --version

# 영구 추가 (시스템 환경변수)
[Environment]::SetEnvironmentVariable(
    "Path",
    [Environment]::GetEnvironmentVariable("Path", "User") + ";$env:LOCALAPPDATA\Programs\Ollama",
    "User"
)
```

---

## 🚀 다음 단계: 모델 다운로드

### PowerShell 재시작 후 실행

```powershell
# 1. Llama 3.1 8B 다운로드 (~4.7GB)
ollama pull llama3.1:8b

# 예상 시간: 5-15분 (인터넷 속도에 따라)
# 진행률 표시: ████████████████████ 100%

# 2. Mistral 7B 다운로드 (~4.1GB)
ollama pull mistral:7b

# 예상 시간: 5-15분

# 3. CodeLlama 7B 다운로드 (~3.8GB)
ollama pull codellama:7b

# 예상 시간: 5-15분

# 총 소요 시간: 15-45분
# 총 다운로드: ~12.6GB
```

---

## 📋 설치 확인

### 1. 모델 목록 확인

```powershell
ollama list

# 기대 출력:
# NAME                    ID              SIZE      MODIFIED
# llama3.1:8b            xxx             4.7 GB    2 minutes ago
# mistral:7b             xxx             4.1 GB    5 minutes ago
# codellama:7b           xxx             3.8 GB    8 minutes ago
```

### 2. Ollama 서비스 시작

```powershell
# 백그라운드로 서비스 시작
Start-Process ollama -ArgumentList "serve" -WindowStyle Hidden

# 또는 별도 터미널에서
ollama serve

# 확인
curl http://localhost:11434

# 기대 출력:
# Ollama is running
```

### 3. 모델 테스트

```powershell
# 간단한 테스트
ollama run llama3.1:8b "Hello, test"

# 정상 작동 시:
# Hello! How can I help you today?
```

---

## 🎯 완료 체크리스트

### Ollama 설치
- [x] winget으로 Ollama 설치 완료
- [ ] PowerShell 재시작
- [ ] ollama --version 확인

### 모델 다운로드
- [ ] llama3.1:8b 다운로드 (4.7GB)
- [ ] mistral:7b 다운로드 (4.1GB)
- [ ] codellama:7b 다운로드 (3.8GB)
- [ ] ollama list 확인

### 서비스 실행
- [ ] ollama serve 실행
- [ ] http://localhost:11434 확인
- [ ] 모델 테스트

---

## 💡 팁

### 디스크 공간 확인

```powershell
# 사용 가능한 디스크 공간 확인
Get-PSDrive C | Select-Object Used,Free

# Ollama 모델 저장 위치
# C:\Users\%USERNAME%\.ollama\models
```

**필요 공간:**
- Ollama 프로그램: 1.09 GB
- 3개 모델: 12.6 GB
- **총**: ~14 GB

### 다운로드 속도 개선

```powershell
# 동시 다운로드 제한 해제 (선택)
$env:OLLAMA_NUM_PARALLEL = 3

# 다운로드 재개 (중단된 경우)
ollama pull llama3.1:8b --insecure-skip-verify
```

### 문제 해결

**문제 1: 다운로드 느림**
- 해결: 시간대 변경 (새벽 시간대 다운로드)
- 또는: 다른 네트워크 사용

**문제 2: 디스크 공간 부족**
- 해결: 불필요한 파일 삭제
- 또는: 다른 드라이브에 설치 (OLLAMA_MODELS 환경변수)

**문제 3: 모델이 실행 안 됨**
- 해결: RAM 16GB 이상 확인
- 또는: GPU 사용 설정 (NVIDIA)

---

## 🚀 빠른 시작 스크립트

```powershell
# setup_ollama.ps1
Write-Host "=== Ollama 설정 시작 ===" -ForegroundColor Green

# PATH 추가
$env:Path += ";$env:LOCALAPPDATA\Programs\Ollama"

# 버전 확인
Write-Host "`n1. Ollama 버전 확인..." -ForegroundColor Yellow
ollama --version

# 모델 다운로드
Write-Host "`n2. 모델 다운로드 중..." -ForegroundColor Yellow
Write-Host "  - Llama 3.1 8B (~4.7GB)..."
ollama pull llama3.1:8b

Write-Host "  - Mistral 7B (~4.1GB)..."
ollama pull mistral:7b

Write-Host "  - CodeLlama 7B (~3.8GB)..."
ollama pull codellama:7b

# 목록 확인
Write-Host "`n3. 설치된 모델 목록:" -ForegroundColor Yellow
ollama list

# 서비스 시작
Write-Host "`n4. Ollama 서비스 시작..." -ForegroundColor Yellow
Start-Process ollama -ArgumentList "serve" -WindowStyle Hidden

Start-Sleep -Seconds 5

# 확인
Write-Host "`n5. 서비스 확인..." -ForegroundColor Yellow
curl http://localhost:11434

Write-Host "`n=== 설정 완료! ===" -ForegroundColor Green
```

**실행:**
```powershell
.\setup_ollama.ps1
```

---

## 📊 예상 소요 시간

| 작업 | 시간 |
|------|------|
| Ollama 설치 | ✅ 완료 (5분) |
| PowerShell 재시작 | 1분 |
| Llama 3.1 다운로드 | 5-15분 |
| Mistral 다운로드 | 5-15분 |
| CodeLlama 다운로드 | 5-15분 |
| 서비스 시작 및 확인 | 2분 |
| **총계** | **23-63분** |

---

## 🎯 다음 작업

### PowerShell 재시작 후:

1. **모델 다운로드** (15-45분)
   ```powershell
   ollama pull llama3.1:8b
   ollama pull mistral:7b
   ollama pull codellama:7b
   ```

2. **서비스 시작**
   ```powershell
   ollama serve
   ```

3. **백엔드 재시작**
   ```powershell
   cd C:\insu_app\backend
   .\mvnw spring-boot:run
   ```

4. **쿼럼 LLM 작동 확인**
   - 로그에서 "쿼럼 달성" 메시지 확인
   - 응답 시간 50% 단축 확인

---

**작성일**: 2025-10-11  
**상태**: ✅ Ollama 설치 완료, PowerShell 재시작 필요

