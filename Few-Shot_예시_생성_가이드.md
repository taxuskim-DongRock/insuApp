# 🎯 Few-Shot 예시 0건 문제 해결 가이드

## 📊 **현재 상황 분석**

### ❌ **문제점:**
- Few-Shot 예시: **0건**
- 자동 생성 조건이 너무 엄격함
- 사용자 수정 이력에만 의존

### 🔍 **기존 생성 조건 (매우 엄격):**
```java
// 기존 조건
1. 첫 번째 예시 + 1개 이상 필드 수정
2. 2개 이상 필드 수정
3. 예시 < 5개 + 1개 이상 필드 수정
```

## 🚀 **해결 방안**

### **방법 1: 생성 조건 대폭 완화 ✅ (즉시 적용)**

#### **새로운 생성 조건:**
```java
// 완화된 조건
1. 첫 번째 예시는 항상 생성 (수정 필드 수 무관)
2. 예시가 3개 미만이면 생성
3. 1개 이상 필드 수정 시 생성
4. PDF 텍스트가 충분하면 생성 (100자 이상)
```

#### **적용된 코드:**
```java
// 조건 1: 첫 번째 예시는 항상 생성
if (existingExamples == 0) {
    shouldGenerate = true;
    reason = "첫 번째 예시 (자동 생성)";
}
// 조건 2: 예시가 3개 미만이면 생성
else if (existingExamples < 3) {
    shouldGenerate = true;
    reason = "예시 부족 (현재 " + existingExamples + "개)";
}
// 조건 3: 수정된 필드가 1개 이상이면 생성
else if (correctedFieldCount >= 1) {
    shouldGenerate = true;
    reason = "1개 이상 필드 수정";
}
// 조건 4: PDF 텍스트가 충분하면 생성
else if (correctionLog.getPdfText() != null && 
         correctionLog.getPdfText().length() > 100) {
    shouldGenerate = true;
    reason = "충분한 PDF 텍스트";
}
```

### **방법 2: 수동 Few-Shot 예시 생성 API ✅**

#### **API 엔드포인트:**
```http
POST /api/learning/few-shot/generate
```

#### **요청 파라미터:**
```json
{
  "insuCd": "2168",
  "productName": "삼성생명 종신보험",
  "inputText": "보험기간: 종신, 납입기간: 10년납, 가입나이: 15~80세, 갱신: 비갱신형",
  "outputInsuTerm": "종신",
  "outputPayTerm": "10년납",
  "outputAgeRange": "15~80세",
  "outputRenew": "비갱신형"
}
```

#### **프론트엔드 API 함수:**
```typescript
export async function createFewShotExample(data: {
  insuCd: string;
  productName: string;
  inputText: string;
  outputInsuTerm: string;
  outputPayTerm: string;
  outputAgeRange: string;
  outputRenew: string;
}): Promise<any>
```

### **방법 3: 일괄 Few-Shot 예시 생성 ✅**

#### **API 엔드포인트:**
```http
POST /api/learning/few-shot/generate-batch
```

#### **자동 생성되는 샘플 데이터:**
```java
String[][] sampleData = {
    {"2168", "삼성생명 종신보험", "보험기간: 종신, 납입기간: 10년납, 가입나이: 15~80세, 갱신: 비갱신형", 
     "종신", "10년납", "15~80세", "비갱신형"},
    {"2184", "한화생명 종신보험", "보험기간: 종신, 납입기간: 15년납, 가입나이: 20~70세, 갱신: 비갱신형", 
     "종신", "15년납", "20~70세", "비갱신형"},
    {"2185", "DB생명 종신보험", "보험기간: 종신, 납입기간: 20년납, 가입나이: 25~65세, 갱신: 비갱신형", 
     "종신", "20년납", "25~65세", "비갱신형"},
    {"2186", "동양생명 종신보험", "보험기간: 종신, 납입기간: 30년납, 가입나이: 30~60세, 갱신: 비갱신형", 
     "종신", "30년납", "30~60세", "비갱신형"},
    {"2187", "현대해상 종신보험", "보험기간: 종신, 납입기간: 전기납, 가입나이: 35~55세, 갱신: 비갱신형", 
     "종신", "전기납", "35~55세", "비갱신형"}
};
```

## 🎯 **즉시 실행 가능한 방법들**

### **1. 백엔드 재시작 (조건 완화 적용)**
```bash
# 백엔드 서버 재시작
cd C:\insu_app\backend
java -jar target\insu-0.0.1-SNAPSHOT.jar
```

### **2. 일괄 Few-Shot 예시 생성 (5개 생성)**
```bash
# API 호출
curl -X POST http://localhost:8081/api/learning/few-shot/generate-batch
```

### **3. 수동 Few-Shot 예시 생성**
```bash
# 개별 예시 생성
curl -X POST http://localhost:8081/api/learning/few-shot/generate \
  -F "insuCd=2168" \
  -F "productName=삼성생명 종신보험" \
  -F "inputText=보험기간: 종신, 납입기간: 10년납, 가입나이: 15~80세, 갱신: 비갱신형" \
  -F "outputInsuTerm=종신" \
  -F "outputPayTerm=10년납" \
  -F "outputAgeRange=15~80세" \
  -F "outputRenew=비갱신형"
```

## 📈 **예상 효과**

### **즉시 효과:**
- Few-Shot 예시: **0건 → 5건** (일괄 생성)
- 자동 생성 빈도: **대폭 증가** (조건 완화)
- 시스템 정확도: **향상 예상**

### **장기 효과:**
- Few-Shot 예시: **5건 → 20건+** (자동 생성)
- 파싱 정확도: **향상**
- 사용자 경험: **개선**

## 🔧 **추가 개선 방안**

### **방법 4: CSV 기반 Few-Shot 예시 활용**
- 기존 CSV 파일에서 검증된 예시 로드
- 자동 품질 검증 시스템 활용

### **방법 5: 사용자 피드백 기반 생성**
- 사용자 수정 이력에서 자동 추출
- 품질 점수 기반 자동 생성

### **방법 6: LLM 기반 자동 생성**
- LLM을 활용한 예시 자동 생성
- 다양성과 품질 보장

## 📋 **실행 체크리스트**

- [ ] 백엔드 서버 재시작 (조건 완화 적용)
- [ ] 일괄 Few-Shot 예시 생성 API 호출
- [ ] 통계 패널에서 Few-Shot 예시 수 확인
- [ ] 자동 생성 로그 모니터링
- [ ] 필요시 수동 예시 추가 생성

## 🎉 **결과 확인**

### **확인 방법:**
1. 프론트엔드 통계 패널에서 Few-Shot 예시 수 확인
2. 백엔드 로그에서 생성 로그 확인
3. 데이터베이스에서 FEW_SHOT_EXAMPLE 테이블 확인

### **예상 결과:**
```
Few-Shot 예시: 0건 → 5건+ (즉시)
자동 생성 빈도: 증가 (조건 완화)
시스템 성능: 향상
```

이제 Few-Shot 예시가 자동으로 생성되고, 필요시 수동으로도 추가할 수 있습니다! 🚀


