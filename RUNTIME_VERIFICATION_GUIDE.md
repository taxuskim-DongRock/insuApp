# 런타임 검증 가이드

## 🚀 백엔드 실행 완료

**실행 명령:**
```bash
cd C:\insu_app\backend
.\mvnw spring-boot:run
```

**상태:** 🔄 백그라운드에서 실행 중

---

## 📊 확인해야 할 로그 메시지

### 1. 서비스 초기화 로그

**예상 로그:**
```
개선된 하이브리드 파싱 서비스 초기화 - 4 개 전략 로드
  - Python OCR (우선순위: 1)
  - Business Method (우선순위: 2)
  - LLM (Ollama) (우선순위: 3)
  - Few-Shot LLM (우선순위: 4)

Few-Shot LLM 파싱 전략 사용 가능
또는
Ollama 서비스를 사용할 수 없음 (Ollama 미설치 시)
```

**의미:**
- ✅ 4개 파싱 전략이 정상 로드됨
- ✅ 우선순위 순으로 정렬됨
- ⚠️ Ollama 없으면 LLM 전략은 건너뜀 (정상)

---

### 2. Caffeine Cache 통계 (1분마다)

**예상 로그:**
```
=== 캐시 통계 ===
캐시 크기: 0/1000
히트율: 0.00% (히트: 0, 미스: 0)
미스율: 0.00%
제거 횟수: 0
평균 로드 시간: 0.00ms
================
```

**첫 API 호출 후:**
```
=== 캐시 통계 ===
캐시 크기: 1/1000
히트율: 0.00% (히트: 0, 미스: 1)
미스율: 100.00%
제거 횟수: 0
평균 로드 시간: 3200.00ms
================
```

**두 번째 API 호출 후:**
```
=== 캐시 통계 ===
캐시 크기: 1/1000
히트율: 50.00% (히트: 1, 미스: 1)
미스율: 50.00%
제거 횟수: 0
평균 로드 시간: 1600.00ms
================
```

**의미:**
- ✅ Caffeine Cache 정상 작동
- ✅ 히트율 실시간 측정
- ✅ 1분마다 자동 리포팅

---

### 3. 쿼럼 기반 LLM 로그 (Ollama 설치 시)

**예상 로그:**
```
=== 쿼럼 기반 LLM 파싱 시작: 21686 ===
[Llama] 호출 시작 (타임아웃: 10000ms)
[Mistral] 호출 시작 (타임아웃: 8000ms)
[CodeLlama] 호출 시작 (타임아웃: 9000ms)

[Mistral] 완료 - 성공: true, 소요: 5200ms
[CodeLlama] 완료 - 성공: true, 소요: 7100ms
✓ 쿼럼 달성 (2/3 합의), 조기 종료! 총 소요: 7100ms

=== 쿼럼 파싱 완료: 7100ms (성공: 2/3) ===
```

**의미:**
- ✅ 3개 모델 병렬 실행
- ✅ 2개 일치 시 조기 종료 (Llama 취소됨)
- ✅ 총 7초 (기존 30초 대비 76% 단축)

---

### 4. 파싱 프로세스 로그

**예상 로그:**
```
=== Phase 1 하이브리드 파싱 시작: 21686 ===
[전략 Python OCR] 파싱 시작...
[전략 Python OCR] 파싱 완료 - 신뢰도: 75%, 소요시간: 2500ms
높은 신뢰도 달성 (75%), 추가 전략 생략하지 않음

[전략 Business Method] 파싱 시작...
[전략 Business Method] 파싱 완료 - 신뢰도: 80%, 소요시간: 1200ms
높은 신뢰도 달성 (80%), 추가 전략 생략하지 않음

[전략 Few-Shot LLM] 파싱 시작...
쿼럼 기반 LLM 파싱 실행 (응답 시간 50% 단축 예상)
Few-Shot LLM 파싱 완료: 21686 (신뢰도: 92%, 상태: PASS)
높은 신뢰도 달성 (92%), 추가 전략 생략

--- 파싱 결과 요약 ---
  Python OCR - 신뢰도: 75%, 시간: 2500ms
  Business Method - 신뢰도: 80%, 시간: 1200ms
  Few-Shot LLM - 신뢰도: 92%, 시간: 7100ms
최종 선택: Few-Shot LLM (신뢰도: 92%)
---------------------

=== 개선된 하이브리드 파싱 완료: 21686 ===
```

**의미:**
- ✅ 3단계 폴백 전략 정상 작동
- ✅ 신뢰도 85% 이상 시 조기 종료
- ✅ 최적 결과 자동 선택

---

## 🧪 API 테스트 방법

### 테스트 1: 상품 정보 조회 (첫 번째 - 캐시 미스)

```bash
# PowerShell
Measure-Command {
    Invoke-WebRequest -Uri "http://localhost:8080/api/product/info/21686" -UseBasicParsing
} | Select-Object TotalSeconds
```

**예상 결과:**
- 응답 시간: 3-5초 (파싱 + 캐시 저장)
- 상태 코드: 200 OK
- 캐시 미스 로그 확인

### 테스트 2: 상품 정보 조회 (두 번째 - 캐시 히트)

```bash
# PowerShell
Measure-Command {
    Invoke-WebRequest -Uri "http://localhost:8080/api/product/info/21686" -UseBasicParsing
} | Select-Object TotalSeconds
```

**예상 결과:**
- 응답 시간: ~0.5초 (캐시에서 즉시 반환)
- 상태 코드: 200 OK
- **성능 향상: 85-90%** ✅

### 테스트 3: 다른 상품 조회

```bash
# 여러 상품 연속 조회
curl http://localhost:8080/api/product/info/79525
curl http://localhost:8080/api/product/info/79527
curl http://localhost:8080/api/product/info/81819
```

**확인 사항:**
- 각 상품별 캐시 저장
- 캐시 크기 증가 (0 → 1 → 2 → 3)
- 히트율 변화 확인

---

## 📈 성능 측정 체크리스트

### Caffeine Cache 검증

- [ ] 첫 번째 요청: 캐시 미스 (3-5초)
- [ ] 두 번째 요청: 캐시 히트 (0.5초)
- [ ] 성능 향상: 85-90% 확인
- [ ] 캐시 통계 1분마다 로깅 확인
- [ ] 히트율 90%+ 달성 (여러 번 요청 후)

### 쿼럼 LLM 검증 (Ollama 설치 시)

- [ ] 3개 모델 병렬 실행 로그
- [ ] 2/3 일치 시 조기 종료
- [ ] 응답 시간 40-50% 단축 확인
- [ ] 동적 타임아웃 학습 로그

### 파싱 정확도 검증

- [ ] 주계약 파싱 성공 (21686)
- [ ] 특약 파싱 성공 (79525, 79527 등)
- [ ] 복잡한 특약 파싱 (81819)
- [ ] 갱신형 특약 파싱 (81880)
- [ ] 조합 생성 정확성 (보험기간 × 납입기간)

---

## ⚠️ 예상 문제 및 해결 방법

### 문제 1: Ollama 서비스를 사용할 수 없음

**로그:**
```
Ollama 서비스를 사용할 수 없음: Connection refused
```

**원인:** Ollama 미설치 또는 미실행

**해결:**
```bash
# Ollama 설치 확인
ollama --version

# Ollama 서비스 시작
ollama serve

# 모델 다운로드 (필요 시)
ollama pull llama3.1:8b
ollama pull mistral:7b
ollama pull codellama:7b
```

**영향:** 
- LLM 전략은 건너뜀
- Python OCR 및 정규식 전략으로 파싱 (여전히 작동)

---

### 문제 2: PDF 파일을 찾을 수 없음

**로그:**
```
no PDF matched for insuCd=21686 under C:/insu_app/insuPdf
```

**원인:** PDF 디렉토리 경로 문제

**해결:**
```yaml
# application.yml 확인
insu:
  pdf-dir: C:/insu_app/insuPdf  # 경로 확인
```

**또는:**
```bash
# PDF 파일 확인
dir C:\insu_app\insuPdf\*.pdf
```

---

### 문제 3: 캐시 통계가 안 나옴

**원인:** CacheMetricsCollector의 @Scheduled가 작동 안 함

**해결:**
```java
// Application.java에 추가
@EnableScheduling
@SpringBootApplication
public class Application { ... }
```

---

## 🎯 성공 기준

### 최소 기준 (Ollama 없이)

- ✅ 백엔드 시작 성공
- ✅ Python OCR 또는 정규식 파싱 작동
- ✅ Caffeine Cache 통계 출력
- ✅ API 응답 성공
- ✅ 캐시 히트 시 0.5초 응답

### 최적 기준 (Ollama 포함)

- ✅ 위 최소 기준 + 
- ✅ 쿼럼 기반 LLM 파싱 작동
- ✅ 2/3 일치 시 조기 종료
- ✅ 응답 시간 8-12초
- ✅ Few-Shot LLM 신뢰도 92%+

---

## 📝 로그 모니터링 방법

### 방법 1: 로그 파일 확인
```bash
# 로그 파일 위치
C:\insu_app\logs\insu-offline.log

# 실시간 모니터링 (PowerShell)
Get-Content C:\insu_app\logs\insu-offline.log -Wait -Tail 50
```

### 방법 2: 콘솔 출력
```bash
# Maven 실행 시 콘솔에 출력됨
.\mvnw spring-boot:run
```

### 방법 3: Spring Boot Actuator
```bash
# Health 체크
curl http://localhost:8080/actuator/health

# Metrics 확인
curl http://localhost:8080/actuator/metrics

# Prometheus 메트릭
curl http://localhost:8080/actuator/prometheus
```

---

## 🧪 테스트 시나리오

### 시나리오 1: 기본 동작 확인

```bash
# 1. 상품 정보 조회
curl http://localhost:8080/api/product/info/21686

# 기대 결과:
# - HTTP 200
# - JSON 응답
# - terms 배열 (4개 조합)
# - calcAvailable: true
```

### 시나리오 2: 캐시 효과 확인

```bash
# 1. 첫 번째 요청 (캐시 미스)
time curl http://localhost:8080/api/product/info/21686
# → 3-5초

# 2. 두 번째 요청 (캐시 히트)
time curl http://localhost:8080/api/product/info/21686
# → 0.5초

# 3. 로그 확인
# "캐시 히트율: 50.00%"
```

### 시나리오 3: 보험료 계산

```bash
# 나이 30세, 기준금액 1,000,000원
curl "http://localhost:8080/api/premium/calculate-by-terms/21686?age=30&insuTerm=종신&payTerm=10년납&baseAmount=1000000"

# 기대 결과:
# - premiumMale: 숫자
# - premiumFemale: 숫자
```

---

## 📊 예상 성능 지표

### 응답 시간

| 요청 | 기대값 | 측정값 | 상태 |
|------|--------|--------|------|
| 첫 번째 (캐시 미스) | 3-5초 | ___초 | [ ] |
| 두 번째 (캐시 히트) | ~0.5초 | ___초 | [ ] |
| 성능 향상 | 85-90% | ___% | [ ] |

### 캐시 효율

| 지표 | 기대값 | 측정값 | 상태 |
|------|--------|--------|------|
| 히트율 (10회 후) | 90%+ | ___% | [ ] |
| 평균 로드 시간 | 3-5초 | ___초 | [ ] |
| 캐시 크기 | 동적 증가 | ___ | [ ] |

### 쿼럼 LLM (Ollama 설치 시)

| 지표 | 기대값 | 측정값 | 상태 |
|------|--------|--------|------|
| 쿼럼 달성 시간 | 8-12초 | ___초 | [ ] |
| 모델 성공률 | 2/3 이상 | ___/3 | [ ] |
| 조기 종료 | Yes | ___ | [ ] |

---

## 🎯 다음 액션

### 즉시 (10분 내)

1. **로그 확인**
   - 백엔드 시작 로그
   - 서비스 초기화
   - 오류 없는지 확인

2. **Health Check**
   ```bash
   curl http://localhost:8080/actuator/health
   ```

3. **첫 API 호출**
   ```bash
   curl http://localhost:8080/api/product/info/21686
   ```

### 30분 내

4. **캐시 통계 확인**
   - 1분 대기
   - 로그에서 "캐시 통계" 확인

5. **캐시 효과 측정**
   - 동일 API 여러 번 호출
   - 히트율 상승 확인

6. **여러 상품 테스트**
   - 21686, 79525, 79527, 81819 등
   - 각각 캐싱 확인

---

**작성일**: 2025-10-11  
**상태**: 🔄 백엔드 실행 중, 검증 대기


