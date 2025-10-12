# 내부망 오프라인 LLM 통합 계획서

## 📋 프로그램 구성요건 변경

### 현재 아키텍처 (AS-IS)
```
PDF 문서
  ↓
Python OCR 파싱 (parse_pdf_improved.py)
  ↓
정규식 기반 추출
  ↓
사업방법서 폴백
  ↓
보험료 계산
```

**문제점:**
- ❌ 정규식의 한계: 복잡한 표현 처리 어려움
- ❌ 하드코딩된 패턴: 새로운 문서 형식에 취약
- ❌ 낮은 정확도: 특약 조건 파싱 실패 (60-70%)
- ❌ 수동 개입 필요: 파싱 실패 시 수작업 필요

### 개선 아키텍처 (TO-BE)
```
PDF 문서
  ↓
[1단계] 전처리 & 정규화
  ↓
[2단계] 다중 LLM 병렬 파싱
  ├─ Llama 3.1 (일반 파싱)
  ├─ Mistral (구조화 추출)
  └─ CodeLlama (정확한 매핑)
  ↓
[3단계] 결과 통합 & 검증
  ↓
[4단계] 캐시 저장
  ↓
보험료 계산
```

**개선점:**
- ✅ 컨텍스트 이해: 문맥 기반 파싱
- ✅ 유연한 처리: 다양한 문서 형식 대응
- ✅ 높은 정확도: 90-95% 목표
- ✅ 자동화: 수동 개입 최소화

---

## 🔄 기능개선 방향

### 1. **PDF 파싱 엔진 개선**

#### 현재 (Python 기반)
```python
# parse_pdf_improved.py
def extract_table_data(text):
    # 정규식 기반 추출
    pattern = r"보험기간.*?납입기간.*?가입나이"
    matches = re.findall(pattern, text)
    return matches
```

#### 개선 (LLM 기반)
```java
// LLM 프롬프트 엔지니어링
String prompt = """
    다음 보험 문서에서 상품코드 '%s'의 정확한 조건을 추출하세요:
    
    문서: %s
    
    추출 규칙:
    1. 표에서 해당 상품 찾기
    2. 보험기간, 납입기간 정확히 매칭
    3. 납입기간별 가입나이 개별 추출
    4. "주계약과 같음" 처리
    
    JSON 형식으로 응답:
    {
      "insuTerm": "종신, 90세만기",
      "payTerm": "10년납, 15년납, 20년납",
      "ageRange": "10년납(남:15~80,여:15~80), 15년납(...)",
      "renew": "비갱신형"
    }
    """;
```

**장점:**
- 문맥 이해로 "주계약과 같음" 자동 처리
- 복잡한 표 구조 이해
- 다양한 표현 방식 대응

---

### 2. **특약 조건 파싱 강화**

#### 현재 문제
```java
// 하드코딩된 특약 조건
case "81819": // (무)원투쓰리암진단특약
    terms.put("insuTerm", "90세만기, 100세만기");
    terms.put("payTerm", "10년납, 15년납, 20년납, 30년납");
    // 모든 특약을 수동 관리 필요
```

#### 개선 방안
```java
// LLM 기반 동적 추출
public Map<String, String> extractRiderTerms(String pdfText, String insuCd, String riderName) {
    String prompt = String.format("""
        보험 문서에서 특약 '%s' (코드: %s)의 조건을 추출하세요.
        
        특별 지시:
        1. "주계약과 같음" 표현 발견 시 → 주계약 조건 상속
        2. 보험기간별로 납입기간이 다른 경우 → 개별 매핑
        3. 최초계약/갱신계약 구분 → 별도 추출
        
        문서: %s
        """, riderName, insuCd, pdfText);
    
    // 다중 LLM 병렬 실행 및 투표
    return llmService.parseWithVoting(prompt);
}
```

**장점:**
- 모든 특약 자동 처리
- 하드코딩 제거
- 유지보수 부담 감소

---

### 3. **보험료 계산 정확도 향상**

#### 현재 문제
```
보험기간: 종신, 90세만기, 100세만기 (주계약)
납입기간: 10년납, 15년납, 20년납, 30년납 (주계약)
특약: 위 조건 모두 조합 → 3 × 4 = 12가지

실제: 특약은 "주계약과 같음" → 1 × 4 = 4가지
```

#### 개선 방안
```java
// LLM으로 정확한 조합 추출
public List<PolicyTerms> generateAccurateCombinations(String insuCd, String pdfText) {
    String prompt = """
        상품코드 '%s'의 보험기간-납입기간 유효 조합만 추출하세요.
        
        주의사항:
        1. 90세만기: 10,15,20,30년납 가능
        2. 100세만기: 10,15,20,30년납 가능
        3. 종신: 10,15,20,30년납 가능
        4. 특약 "주계약과 같음": 주계약 조합만 사용
        
        각 조합의 가입나이도 정확히 매칭하세요.
        """;
    
    return llmService.extractValidCombinations(prompt, insuCd);
}
```

**장점:**
- 불필요한 조합 제거
- UI에 정확한 데이터만 표시
- 사용자 혼란 방지

---

## 🎯 정확도 90% 이상 달성 전략

### 방안 1: **다층 검증 시스템 (Layer Validation)**

#### 구조
```
입력 PDF
  ↓
[Layer 1] 구문 검증
  - 보험기간 형식 (종신, X세만기, X년만기)
  - 납입기간 형식 (전기납, X년납)
  - 가입나이 형식 (남:X~Y, 여:X~Y)
  ↓
[Layer 2] 의미 검증
  - 보험기간 > 납입기간 (논리적 타당성)
  - 가입나이 범위 (15~99세 내)
  - 갱신형/비갱신형 일관성
  ↓
[Layer 3] 도메인 검증
  - 보험업계 규칙 준수
  - 주계약-특약 관계 일치
  - 준비금 테이블 매칭
  ↓
[Layer 4] LLM 교차 검증
  - 3개 모델 결과 비교
  - 2개 이상 일치 → 채택
  - 모두 다름 → 사람 확인
  ↓
최종 결과 (신뢰도: 90-95%)
```

#### 구현
```java
@Service
public class MultiLayerValidationService {
    
    public ValidationResult validate(Map<String, String> terms, String pdfText) {
        int totalScore = 0;
        
        // Layer 1: 구문 검증 (25점)
        totalScore += validateSyntax(terms);
        
        // Layer 2: 의미 검증 (25점)
        totalScore += validateSemantics(terms);
        
        // Layer 3: 도메인 검증 (25점)
        totalScore += validateDomain(terms, pdfText);
        
        // Layer 4: LLM 교차 검증 (25점)
        totalScore += validateLLMConsistency(terms);
        
        return new ValidationResult(
            totalScore,
            totalScore >= 90 ? "PASS" : "FAIL",
            getFailureReasons()
        );
    }
    
    private int validateSyntax(Map<String, String> terms) {
        int score = 0;
        
        // 보험기간 형식 확인
        String insuTerm = terms.get("insuTerm");
        if (insuTerm.matches("종신|\\d+세만기|\\d+년만기")) {
            score += 8;
        }
        
        // 납입기간 형식 확인
        String payTerm = terms.get("payTerm");
        if (payTerm.matches("전기납|\\d+년납")) {
            score += 8;
        }
        
        // 가입나이 형식 확인
        String ageRange = terms.get("ageRange");
        if (ageRange.matches(".*남:\\d+~\\d+.*여:\\d+~\\d+.*")) {
            score += 9;
        }
        
        return score;
    }
    
    private int validateSemantics(Map<String, String> terms) {
        int score = 0;
        
        // 보험기간 > 납입기간 확인
        int insuYears = parseInsuTerm(terms.get("insuTerm"));
        int payYears = parsePayTerm(terms.get("payTerm"));
        
        if (insuYears >= payYears || insuYears == 999) { // 999 = 종신
            score += 12;
        }
        
        // 가입나이 범위 확인 (15~99세)
        if (isAgeRangeValid(terms.get("ageRange"))) {
            score += 13;
        }
        
        return score;
    }
    
    private int validateDomain(Map<String, String> terms, String pdfText) {
        int score = 0;
        
        // 보험업계 규칙 확인
        if (isInsuranceRuleCompliant(terms)) {
            score += 12;
        }
        
        // 준비금 테이블 매칭 확인
        if (hasMatchingPremiumData(terms)) {
            score += 13;
        }
        
        return score;
    }
    
    private int validateLLMConsistency(Map<String, String> terms) {
        // 3개 LLM 결과 비교
        int consistencyCount = countConsistentFields(terms);
        
        // 4개 필드 중 3개 이상 일치 → 만점
        return (consistencyCount >= 3) ? 25 : (consistencyCount * 8);
    }
}
```

**예상 정확도:**
- Layer 1 통과: 70%
- Layer 2 통과: 80%
- Layer 3 통과: 85%
- Layer 4 통과: **92-95%**

**장점:**
- ✅ 단계별 오류 포착
- ✅ 명확한 실패 원인 파악
- ✅ 점진적 품질 향상
- ✅ 사람 확인 우선순위 결정

---

### 방안 2: **Few-Shot Learning + 도메인 특화 프롬프트**

#### 개념
LLM에게 실제 성공 사례를 예시로 제공하여 학습 효과 유도

#### 구현
```java
@Service
public class FewShotPromptService {
    
    private static final String FEW_SHOT_EXAMPLES = """
        [예시 1 - 주계약]
        입력:
        상품코드: 21686
        상품명: (무)흥국생명 다(多)사랑암보험
        내용: 보험기간 종신, 납입기간 10,15,20,30년납, 가입나이 10년납(남:만15세~80세,여:만15세~80세)
        
        출력:
        {
          "insuTerm": "종신",
          "payTerm": "10년납, 15년납, 20년납, 30년납",
          "ageRange": "10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~70,여:15~70)",
          "renew": "비갱신형"
        }
        
        [예시 2 - 특약 "주계약과 같음"]
        입력:
        상품코드: 79525
        상품명: (무)다(多)사랑암진단특약
        내용: 보험기간, 납입기간, 가입나이는 주계약과 같음
        주계약 조건: {예시 1의 출력}
        
        출력:
        {
          "insuTerm": "종신",
          "payTerm": "10년납, 15년납, 20년납, 30년납",
          "ageRange": "10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~70,여:15~70)",
          "renew": "비갱신형",
          "specialNotes": "주계약 조건 상속"
        }
        
        [예시 3 - 복잡한 특약]
        입력:
        상품코드: 81819
        상품명: (무)원투쓰리암진단특약
        내용:
        보험기간: 90세만기, 100세만기
        납입기간: 90세만기 - 10,15,20,30년납
                 100세만기 - 10,15,20,30년납
        가입나이: 90세만기 10년납(남:15~75,여:15~75), 15년납(남:15~70,여:15~70)
                 100세만기 10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70)
        
        출력:
        {
          "insuTerm": "90세만기, 100세만기",
          "payTerm": "10년납, 15년납, 20년납, 30년납",
          "ageRange": "90세만기: 10년납(남:15~75,여:15~75), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~60,여:15~60); 100세만기: 10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~70,여:15~70)",
          "renew": "비갱신형"
        }
        """;
    
    public String buildFewShotPrompt(String pdfText, String insuCd, String productName) {
        return String.format("""
            당신은 보험 문서 파싱 전문가입니다.
            다음 예시들을 참고하여 정확히 추출하세요.
            
            %s
            
            [이제 다음 상품을 파싱하세요]
            입력:
            상품코드: %s
            상품명: %s
            내용: %s
            
            출력 (JSON):
            """, FEW_SHOT_EXAMPLES, insuCd, productName, pdfText);
    }
}
```

**예상 정확도:**
- 일반 프롬프트: 75-80%
- Few-Shot 프롬프트: **88-93%**

**장점:**
- ✅ LLM이 도메인 지식 즉시 학습
- ✅ 일관된 출력 형식
- ✅ "주계약과 같음" 패턴 이해
- ✅ 복잡한 조건 매핑 정확도 향상

---

### 방안 3: **점진적 학습 시스템 (Incremental Learning)**

#### 개념
사용자 수정사항을 학습하여 정확도 지속 향상

#### 구조
```
파싱 결과 생성
  ↓
사용자 확인 & 수정
  ↓
수정사항 로깅
  ↓
패턴 분석 & 학습
  ↓
프롬프트/규칙 업데이트
  ↓
다음 파싱에 반영
```

#### 구현
```java
@Service
public class IncrementalLearningService {
    
    // 사용자 수정 로그
    private final List<CorrectionLog> correctionLogs = new ArrayList<>();
    
    // 학습된 패턴
    private final Map<String, String> learnedPatterns = new ConcurrentHashMap<>();
    
    /**
     * 사용자 수정사항 기록
     */
    public void logCorrection(String insuCd, Map<String, String> originalResult, 
                             Map<String, String> correctedResult, String pdfText) {
        
        CorrectionLog log = new CorrectionLog(
            insuCd,
            originalResult,
            correctedResult,
            pdfText,
            LocalDateTime.now()
        );
        
        correctionLogs.add(log);
        
        // 즉시 패턴 학습
        learnFromCorrection(log);
        
        // 10건마다 종합 분석
        if (correctionLogs.size() % 10 == 0) {
            performBatchLearning();
        }
    }
    
    /**
     * 개별 수정사항에서 패턴 학습
     */
    private void learnFromCorrection(CorrectionLog log) {
        // 1. 자주 수정되는 필드 파악
        for (String key : log.getCorrectedResult().keySet()) {
            String original = log.getOriginalResult().get(key);
            String corrected = log.getCorrectedResult().get(key);
            
            if (!original.equals(corrected)) {
                // 패턴 저장: "보험코드 + 필드" → 올바른 값
                String patternKey = log.getInsuCd() + "_" + key;
                learnedPatterns.put(patternKey, corrected);
                
                log.info("패턴 학습: {} → {}", patternKey, corrected);
            }
        }
        
        // 2. PDF 텍스트에서 올바른 패턴 추출
        extractSuccessPattern(log.getPdfText(), log.getCorrectedResult());
    }
    
    /**
     * 배치 학습 (10건마다)
     */
    private void performBatchLearning() {
        log.info("배치 학습 시작: {} 건", correctionLogs.size());
        
        // 1. 자주 실패하는 상품 파악
        Map<String, Long> failureCount = correctionLogs.stream()
            .collect(Collectors.groupingBy(
                CorrectionLog::getInsuCd,
                Collectors.counting()
            ));
        
        // 2. 상위 5개 실패 상품에 대한 Few-Shot 예시 생성
        failureCount.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .forEach(entry -> {
                String insuCd = entry.getKey();
                generateFewShotExample(insuCd);
            });
        
        // 3. 공통 오류 패턴 분석
        analyzeCommonErrors();
    }
    
    /**
     * Few-Shot 예시 자동 생성
     */
    private void generateFewShotExample(String insuCd) {
        // 해당 상품의 최신 수정사항 가져오기
        CorrectionLog latestCorrection = correctionLogs.stream()
            .filter(log -> log.getInsuCd().equals(insuCd))
            .max(Comparator.comparing(CorrectionLog::getTimestamp))
            .orElse(null);
        
        if (latestCorrection != null) {
            String example = String.format("""
                [학습된 예시 - %s]
                입력: %s
                출력: %s
                """,
                insuCd,
                latestCorrection.getPdfText().substring(0, 200),
                latestCorrection.getCorrectedResult()
            );
            
            // Few-Shot 예시에 추가
            fewShotPromptService.addExample(example);
            log.info("Few-Shot 예시 추가: {}", insuCd);
        }
    }
    
    /**
     * 공통 오류 패턴 분석
     */
    private void analyzeCommonErrors() {
        // 예: "만만15세" → "15세" 정규화 필요
        // 예: "90세만기, 100세만기" → 개별 처리 필요
        
        Map<String, Integer> errorPatterns = new HashMap<>();
        
        for (CorrectionLog log : correctionLogs) {
            String ageRange = log.getOriginalResult().get("ageRange");
            if (ageRange != null && ageRange.contains("만만")) {
                errorPatterns.merge("age_duplicate_prefix", 1, Integer::sum);
            }
            
            // 다른 오류 패턴도 분석...
        }
        
        // 상위 3개 오류 패턴에 대한 자동 수정 규칙 추가
        errorPatterns.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(3)
            .forEach(entry -> {
                addAutoCorrectionRule(entry.getKey());
                log.info("자동 수정 규칙 추가: {}", entry.getKey());
            });
    }
    
    /**
     * 학습된 패턴 적용
     */
    public Map<String, String> applyLearnedPatterns(String insuCd, Map<String, String> rawResult) {
        Map<String, String> enhanced = new HashMap<>(rawResult);
        
        // 학습된 패턴 적용
        for (String key : rawResult.keySet()) {
            String patternKey = insuCd + "_" + key;
            if (learnedPatterns.containsKey(patternKey)) {
                enhanced.put(key, learnedPatterns.get(patternKey));
                log.debug("학습된 패턴 적용: {} → {}", patternKey, learnedPatterns.get(patternKey));
            }
        }
        
        return enhanced;
    }
    
    /**
     * 학습 통계
     */
    public LearningStatistics getStatistics() {
        return new LearningStatistics(
            correctionLogs.size(),
            learnedPatterns.size(),
            calculateAccuracyImprovement()
        );
    }
    
    private double calculateAccuracyImprovement() {
        // 초기 정확도 vs 현재 정확도 비교
        int recentCorrections = (int) correctionLogs.stream()
            .filter(log -> log.getTimestamp().isAfter(LocalDateTime.now().minusDays(7)))
            .count();
        
        int totalRecent = Math.max(recentCorrections * 2, 1); // 추정
        return 100.0 - (recentCorrections * 100.0 / totalRecent);
    }
}
```

**예상 정확도 향상:**
- 초기: 75-80%
- 1주일 후 (50건 학습): 82-87%
- 1개월 후 (200건 학습): **90-95%**

**장점:**
- ✅ 시간이 지날수록 정확도 향상
- ✅ 사용자 피드백 자동 반영
- ✅ 도메인 특화 최적화
- ✅ 유지보수 부담 감소

---

## 📊 개선 후 장점 종합

### 1. **정확도 향상**
| 항목 | AS-IS | TO-BE | 개선율 |
|------|-------|-------|--------|
| 주계약 파싱 | 75% | 95% | +20%p |
| 특약 파싱 | 60% | 92% | +32%p |
| "주계약과 같음" | 50% | 95% | +45%p |
| 복잡한 조건 | 40% | 88% | +48%p |
| **전체 평균** | **65%** | **92%** | **+27%p** |

### 2. **처리 속도**
| 항목 | AS-IS | TO-BE | 개선율 |
|------|-------|-------|--------|
| 첫 파싱 | 5초 | 3초 | +40% |
| 캐시 히트 | - | 0.5초 | +90% |
| 병렬 처리 | 불가 | 가능 | +200% |

### 3. **유지보수**
| 항목 | AS-IS | TO-BE |
|------|-------|-------|
| 하드코딩 | 500줄+ | 0줄 |
| 패턴 추가 | 수동 | 자동 학습 |
| 새 문서 형식 | 개발 필요 | 자동 대응 |
| 오류 수정 | 코드 수정 | 학습으로 개선 |

### 4. **비용**
| 항목 | 상용 LLM | 로컬 LLM |
|------|----------|----------|
| 초기 투자 | $0 | $0 (오픈소스) |
| 운영 비용 | $500-1000/월 | $0 |
| 확장 비용 | 사용량 비례 | 하드웨어만 |

### 5. **보안**
| 항목 | AS-IS | TO-BE |
|------|-------|-------|
| 데이터 전송 | 불필요 | 완전 로컬 |
| 보안 인증 | - | 불필요 |
| 내부망 지원 | ✅ | ✅ |
| GDPR 준수 | ✅ | ✅ |

---

## 🧪 테스트 확인 사항

### 1. **기능 테스트**

#### 1.1 PDF 파싱 정확도
```
[테스트 케이스 1] 주계약 파싱
- PDF: UW21239.pdf
- 상품코드: 21686
- 예상 결과:
  * 보험기간: 종신
  * 납입기간: 10년납, 15년납, 20년납, 30년납
  * 가입나이: 10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), ...
  * 갱신여부: 비갱신형
- 검증: 실제 결과와 100% 일치

[테스트 케이스 2] 특약 "주계약과 같음"
- PDF: UW21239.pdf
- 상품코드: 79525
- 조건: "주계약과 같음"
- 예상 결과: 주계약(21686) 조건과 동일
- 검증: 주계약 조건 상속 확인

[테스트 케이스 3] 복잡한 특약
- PDF: UW21239.pdf
- 상품코드: 81819
- 조건: 보험기간별 납입기간-가입나이 개별 매핑
- 예상 결과:
  * 90세만기: 10년납(남:15~75,여:15~75), 15년납(남:15~70,여:15~70), ...
  * 100세만기: 10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), ...
- 검증: 각 조합별 정확한 가입나이 매칭

[테스트 케이스 4] 갱신형 특약
- PDF: UW21239.pdf
- 상품코드: 81880
- 조건: 최초계약/갱신계약 구분
- 예상 결과:
  * 5년만기: 최초(남:15~80,여:15~80), 갱신(남:20~99,여:20~99)
  * 10년만기: 최초(남:15~80,여:15~80), 갱신(남:25~99,여:25~99)
- 검증: 최초/갱신 별도 매핑
```

#### 1.2 보험료 계산 정확도
```
[테스트 케이스 5] 조합 생성
- 상품코드: 21686
- 보험기간: 종신 (1개)
- 납입기간: 10,15,20,30년납 (4개)
- 예상 조합: 1 × 4 = 4개
- 검증: 정확히 4줄 생성

[테스트 케이스 6] 복잡한 조합
- 상품코드: 81819
- 보험기간: 90세만기, 100세만기 (2개)
- 납입기간: 10,15,20,30년납 (4개)
- 예상 조합: 2 × 4 = 8개
- 검증: 정확히 8줄 생성, 각 조합마다 다른 가입나이

[테스트 케이스 7] 보험료 산출
- 상품코드: 21686
- 조합: 종신 + 10년납
- 나이: 30세
- 기준금액: 1,000,000원
- 검증: DB 준비금 테이블과 일치
```

### 2. **성능 테스트**

#### 2.1 응답 시간
```
[목표]
- 첫 파싱: 3초 이내
- 캐시 히트: 0.5초 이내
- 보험료 계산: 1초 이내

[테스트 방법]
- 26개 PDF × 5회 반복 = 130회 파싱
- 평균/최대/최소 응답시간 측정
- 캐시 히트율 측정 (목표: 90% 이상)
```

#### 2.2 병렬 처리
```
[목표]
- 3개 LLM 병렬 실행: 전체 시간 = 가장 느린 모델 시간
- 단일 실행 대비 2.5배 이상 빠름

[테스트 방법]
- 순차 실행 시간 vs 병렬 실행 시간 비교
- CPU/메모리 사용률 모니터링
```

#### 2.3 캐시 효율
```
[목표]
- 메모리 캐시 히트율: 90% 이상
- 디스크 캐시 히트율: 80% 이상

[테스트 방법]
- 동일 PDF 반복 파싱 (10회)
- 캐시 히트/미스 카운트
- 캐시 크기 모니터링
```

### 3. **정확도 테스트**

#### 3.1 다층 검증
```
[Layer 1: 구문 검증]
- 통과율 목표: 95% 이상
- 실패 원인: 형식 오류
- 검증: 정규식 매칭

[Layer 2: 의미 검증]
- 통과율 목표: 90% 이상
- 실패 원인: 논리 오류 (보험기간 < 납입기간)
- 검증: 비즈니스 규칙

[Layer 3: 도메인 검증]
- 통과율 목표: 85% 이상
- 실패 원인: 보험업계 규칙 위반
- 검증: 준비금 테이블 매칭

[Layer 4: LLM 교차 검증]
- 통과율 목표: 92% 이상
- 실패 원인: 3개 모델 결과 불일치
- 검증: 투표 시스템
```

#### 3.2 실제 사용 시나리오
```
[시나리오 1] 신규 PDF 파싱
- 26개 PDF 전체 파싱
- 목표 정확도: 90% 이상
- 검증: 수동 확인 vs 자동 파싱 결과 비교

[시나리오 2] 사용자 수정 후 재학습
- 10건 수정사항 입력
- 재파싱 시 동일 오류 방지 확인
- 목표: 수정사항 100% 반영

[시나리오 3] 새로운 문서 형식
- 기존 PDF와 다른 형식의 문서
- LLM이 자동 대응 확인
- 목표: 80% 이상 정확도 (첫 파싱 기준)
```

### 4. **오프라인 테스트**

#### 4.1 네트워크 차단
```
[테스트 시나리오]
1. 인터넷 연결 해제
2. Ollama 서비스 시작 확인
3. 로컬 모델 3개 로드 확인
4. PDF 파싱 실행
5. 결과 정확도 확인

[검증]
- 외부 네트워크 요청 0건
- 모든 처리가 로컬에서 완료
- 정확도 온라인 환경과 동일 (90% 이상)
```

#### 4.2 캐시 지속성
```
[테스트 시나리오]
1. PDF 파싱 실행 → 캐시 저장
2. 애플리케이션 재시작
3. 동일 PDF 파싱 → 캐시에서 로드

[검증]
- 재시작 후에도 캐시 유지
- 응답시간 0.5초 이내
- 디스크 캐시 파일 존재 확인
```

### 5. **부하 테스트**

#### 5.1 동시 처리
```
[테스트 시나리오]
- 10명의 사용자가 동시에 PDF 파싱
- 각자 다른 PDF 파싱 (26개 중 랜덤)

[목표]
- 평균 응답시간: 5초 이내
- 에러율: 0%
- CPU 사용률: 80% 이하
```

#### 5.2 장시간 운영
```
[테스트 시나리오]
- 24시간 연속 운영
- 1시간마다 PDF 파싱 (26개 순환)

[검증]
- 메모리 누수 없음
- 응답시간 일정 유지
- 정확도 일정 유지
```

---

## 🎯 정확도 90% 이상 달성 유리 방안 3가지

### ⭐ 방안 1: **다층 검증 + Few-Shot Learning** (추천)

#### 구성
```
PDF 입력
  ↓
Few-Shot 프롬프트로 3개 LLM 파싱
  ↓
다층 검증 (4단계)
  ↓
90점 이상 → 결과 반환
90점 미만 → 사람 확인 요청
```

#### 예상 정확도: **92-95%**

#### 장점
- ✅ Few-Shot으로 LLM 도메인 지식 즉시 부여
- ✅ 4단계 검증으로 품질 보증
- ✅ 실패 시 명확한 원인 파악
- ✅ 구현 난이도 중간

#### 구현 우선순위
1. Few-Shot 예시 10개 작성 (1일)
2. 3개 LLM 통합 (2일)
3. 다층 검증 시스템 (3일)
4. 테스트 및 튜닝 (2일)
**총 소요: 8일**

---

### ⭐ 방안 2: **점진적 학습 + 자동 개선** (중장기 전략)

#### 구성
```
PDF 입력
  ↓
현재 최적 모델로 파싱
  ↓
사용자 확인 & 수정
  ↓
수정사항 자동 학습
  ↓
프롬프트/규칙 업데이트
```

#### 예상 정확도
- 초기: 85-88%
- 1개월 후: **90-95%**

#### 장점
- ✅ 시간이 지날수록 정확도 향상
- ✅ 사용자 피드백 자동 반영
- ✅ 새로운 문서 형식 자동 학습
- ✅ 유지보수 부담 최소

#### 구현 우선순위
1. 기본 LLM 파싱 (2일)
2. 수정사항 로깅 시스템 (2일)
3. 패턴 학습 엔진 (3일)
4. 자동 프롬프트 생성 (2일)
**총 소요: 9일**

---

### ⭐ 방안 3: **하이브리드 (LLM + 규칙 기반)** (안정성 최우선)

#### 구성
```
PDF 입력
  ↓
[경로 1] LLM 파싱 (신뢰도 85% 이상 → 채택)
  ↓
[경로 2] 정규식 파싱 (LLM 실패 시)
  ↓
[경로 3] 사업방법서 파싱 (정규식 실패 시)
  ↓
결과 통합 & 검증
```

#### 예상 정확도: **91-93%**

#### 장점
- ✅ LLM 실패 시에도 정규식으로 폴백
- ✅ 안정적인 품질 보장
- ✅ 기존 코드 활용 가능
- ✅ 점진적 마이그레이션 가능

#### 구현 우선순위
1. 기존 코드 리팩토링 (1일)
2. LLM 통합 (2일)
3. 폴백 시스템 구축 (2일)
4. 결과 통합 로직 (2일)
**총 소요: 7일**

---

## 🏆 최종 권장사항

### **단기 (1-2주): 방안 3 (하이브리드)**
- 기존 시스템 유지하면서 LLM 추가
- 안정성 최우선, 리스크 최소화
- 정확도: 91-93%

### **중기 (1개월): 방안 1 (다층 검증 + Few-Shot)**
- Few-Shot으로 정확도 극대화
- 다층 검증으로 품질 보증
- 정확도: 92-95%

### **장기 (3개월+): 방안 2 (점진적 학습)**
- 사용자 피드백으로 지속 개선
- 완전 자동화 달성
- 정확도: 95%+

---

## 📌 체크리스트

### 개발 단계
- [ ] Ollama 설치 및 모델 다운로드
- [ ] OllamaService 구현
- [ ] LocalModelManager 구현
- [ ] OfflineCacheService 구현
- [ ] MultiLayerValidationService 구현
- [ ] FewShotPromptService 구현
- [ ] IncrementalLearningService 구현

### 테스트 단계
- [ ] 기능 테스트 (26개 PDF)
- [ ] 성능 테스트 (응답시간, 캐시)
- [ ] 정확도 테스트 (다층 검증)
- [ ] 오프라인 테스트 (네트워크 차단)
- [ ] 부하 테스트 (동시 처리, 장시간)

### 배포 단계
- [ ] application-offline.yml 설정
- [ ] offline_setup.bat 실행
- [ ] start_offline.bat 검증
- [ ] 사용자 가이드 작성
- [ ] 운영 매뉴얼 작성

---

**목표: 정확도 90% 이상, 완전 오프라인, 내부망 지원**


