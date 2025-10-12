# 보험 문서 파싱 시스템 - 상세 작동 프로세스

## 📋 목차
1. [시스템 개요](#시스템-개요)
2. [전체 아키텍처](#전체-아키텍처)
3. [데이터 흐름](#데이터-흐름)
4. [Phase별 상세 프로세스](#phase별-상세-프로세스)
5. [핵심 컴포넌트](#핵심-컴포넌트)
6. [API 호출 흐름](#api-호출-흐름)
7. [예외 처리](#예외-처리)

---

## 🎯 시스템 개요

### 시스템 목적
보험 상품 PDF 문서에서 보험기간, 납입기간, 가입나이 등의 정보를 자동으로 추출하여 보험료를 계산하고, 데이터 정확성을 검증하는 시스템

### 핵심 기능
1. **PDF 파싱**: 다양한 방법으로 PDF에서 정보 추출
2. **조합 생성**: 보험기간 × 납입기간 조합 생성
3. **보험료 계산**: DB 조회 및 계산
4. **데이터 검증**: 준비금 테이블 매칭 확인
5. **학습 및 개선**: 사용자 피드백으로 정확도 향상

### 주요 특징
- ✅ **완전 오프라인**: 내부망 환경 지원
- ✅ **하이브리드 파싱**: 다중 전략 통합
- ✅ **자기 개선**: 점진적 학습 시스템
- ✅ **높은 정확도**: 95%+ 목표

---

## 🏗️ 전체 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                      Frontend (React + Electron)             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │ PDF List │  │ 상품선택 │  │ 나이입력 │  │ 보험료   │   │
│  │  Panel   │  │  Panel   │  │  Panel   │  │  Grid    │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
│         │              │              │              │       │
│         └──────────────┴──────────────┴──────────────┘       │
│                            │                                 │
│                      Zustand Store                           │
│                      (useAppStore)                           │
└─────────────────────────────────────────────────────────────┘
                              │
                   HTTP REST API (axios)
                              │
┌─────────────────────────────────────────────────────────────┐
│                  Backend (Spring Boot)                       │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              Controller Layer                         │  │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐     │  │
│  │  │  Product   │  │  Premium   │  │  Learning  │     │  │
│  │  │ Controller │  │ Controller │  │ Controller │     │  │
│  │  └────────────┘  └────────────┘  └────────────┘     │  │
│  └───────────────────────────────────────────────────────┘  │
│                              │                               │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              Service Layer                            │  │
│  │  ┌─────────────────────────────────────────────┐     │  │
│  │  │      ProductService (Main)                  │     │  │
│  │  │  - getProductInfo()                         │     │  │
│  │  │  - calculatePremium()                       │     │  │
│  │  │  - parseTermsWithPython() → Hybrid          │     │  │
│  │  └─────────────────────────────────────────────┘     │  │
│  │                      │                                │  │
│  │  ┌───────────────────────────────────────────────┐   │  │
│  │  │    HybridParsingService (Phase 1)            │   │  │
│  │  │  - 다중 전략 순차 시도                        │   │  │
│  │  │  - 신뢰도 평가 및 캐싱                        │   │  │
│  │  └───────────────────────────────────────────────┘   │  │
│  │         │           │           │                     │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐             │  │
│  │  │ Python   │ │Business  │ │ FewShot  │             │  │
│  │  │   OCR    │ │ Method   │ │   LLM    │             │  │
│  │  │ Strategy │ │ Strategy │ │ Strategy │             │  │
│  │  └──────────┘ └──────────┘ └──────────┘             │  │
│  │                      │                                │  │
│  │  ┌───────────────────────────────────────────────┐   │  │
│  │  │  MultiLayerValidationService (Phase 2)       │   │  │
│  │  │  - Layer 1: 구문 검증                         │   │  │
│  │  │  - Layer 2: 의미 검증                         │   │  │
│  │  │  - Layer 3: 도메인 검증                       │   │  │
│  │  │  - Layer 4: LLM 교차 검증                     │   │  │
│  │  └───────────────────────────────────────────────┘   │  │
│  │                      │                                │  │
│  │  ┌───────────────────────────────────────────────┐   │  │
│  │  │  IncrementalLearningService (Phase 3)        │   │  │
│  │  │  - 사용자 피드백 수집                         │   │  │
│  │  │  - 패턴 자동 학습                             │   │  │
│  │  │  - Few-Shot 예시 생성                         │   │  │
│  │  └───────────────────────────────────────────────┘   │  │
│  └───────────────────────────────────────────────────────┘  │
│                              │                               │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              Data Access Layer                        │  │
│  │  ┌────────────┐  ┌────────────┐                      │  │
│  │  │   MyBatis  │  │   Oracle   │                      │  │
│  │  │   Mapper   │  │     DB     │                      │  │
│  │  └────────────┘  └────────────┘                      │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                  External Services                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │    Python    │  │    Ollama    │  │   PDFBox     │     │
│  │  PDF Parser  │  │  LLM Service │  │   Library    │     │
│  │  (OCR)       │  │  (Local)     │  │              │     │
│  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔄 데이터 흐름

### 1️⃣ 사용자가 상품 정보를 조회하는 경우

```
[Frontend]
사용자가 UW21239 PDF 선택 → 상품코드 21686 클릭
         │
         ↓
[Zustand Store]
selectMainCode("21686") 호출
         │
         ↓
[API Call]
GET /api/product/info/21686
         │
         ↓
[ProductController]
getProductInfo(insuCd: "21686")
         │
         ↓
[ProductService]
┌─────────────────────────────────────┐
│ 1. PDF 파일 찾기                     │
│    - pdfDir에서 UW21239.pdf 검색    │
│    - insuCd가 포함된 PDF 매칭       │
└─────────────────────────────────────┘
         │
         ↓
┌─────────────────────────────────────┐
│ 2. PDF 텍스트 추출                   │
│    - PdfParser.readAllText()        │
│    - 전체 텍스트를 메모리에 로드    │
└─────────────────────────────────────┘
         │
         ↓
┌─────────────────────────────────────┐
│ 3. 상품명 추출                       │
│    - parseCodeTable()               │
│    - fuzzyFindNameByCode()          │
│    → "(무)흥국생명 다사랑암보험"    │
└─────────────────────────────────────┘
         │
         ↓
┌─────────────────────────────────────┐
│ 4. 하이브리드 파싱 (핵심!)          │
│    parseTermsWithPython(pdf, insuCd)│
│    ↓                                 │
│    HybridParsingService 호출         │
└─────────────────────────────────────┘
         │
         ↓
[HybridParsingService]
┌─────────────────────────────────────┐
│ Step 1: 캐시 확인                    │
│    - 이전에 파싱한 적 있는지 확인   │
│    - 있으면 즉시 반환 (0.5초)       │
└─────────────────────────────────────┘
         │ (캐시 미스)
         ↓
┌─────────────────────────────────────┐
│ Step 2: 전략 1 - Python OCR         │
│    - pythonPdfService 호출          │
│    - parse_pdf_improved.py 실행     │
│    - 신뢰도 평가: 75점               │
└─────────────────────────────────────┘
         │ (85점 미만)
         ↓
┌─────────────────────────────────────┐
│ Step 3: 전략 2 - 사업방법서         │
│    - PDF 텍스트에서 정규식 추출     │
│    - 신뢰도 평가: 80점               │
└─────────────────────────────────────┘
         │ (85점 미만)
         ↓
┌─────────────────────────────────────┐
│ Step 4: 전략 3 - Few-Shot LLM       │
│    - Few-Shot 프롬프트 생성         │
│    - 3개 LLM 병렬 실행              │
│      * Llama 3.1                     │
│      * Mistral                       │
│      * CodeLlama                     │
│    - 결과 투표 통합                  │
│    - 다층 검증 (4단계)              │
│    - 신뢰도 평가: 92점               │
└─────────────────────────────────────┘
         │ (85점 이상! ✓)
         ↓
┌─────────────────────────────────────┐
│ Step 5: 결과 반환 및 캐싱           │
│    {                                 │
│      insuTerm: "종신",               │
│      payTerm: "10년납, 15년납, ...",│
│      ageRange: "10년납(남:15~80)",  │
│      renew: "비갱신형"               │
│    }                                 │
└─────────────────────────────────────┘
         │
         ↓
[ProductService]
┌─────────────────────────────────────┐
│ 5. 조합 생성                         │
│    generateTermCombinations()        │
│    - insuTerm: "종신" (1개)         │
│    - payTerm: "10년납,15년납,..." (4개)│
│    → 1 × 4 = 4개 조합 생성          │
└─────────────────────────────────────┘
         │
         ↓
┌─────────────────────────────────────┐
│ 6. 응답 생성                         │
│    ProductInfoResponse {             │
│      insuCd: "21686",                │
│      name: "(무)흥국생명...",        │
│      type: "주계약",                 │
│      terms: [                        │
│        {insuTerm: "종신",            │
│         payTerm: "10년납",           │
│         ageRange: "남:15~80,..."},   │
│        {insuTerm: "종신",            │
│         payTerm: "15년납",...},      │
│        ... (4개)                     │
│      ],                              │
│      calcAvailable: true             │
│    }                                 │
└─────────────────────────────────────┘
         │
         ↓
[Frontend]
┌─────────────────────────────────────┐
│ 7. UI 렌더링                         │
│    - 4개 행 생성 (조합별)           │
│    - 각 행마다 "데이터" 버튼        │
│    - 나이 입력 시 보험료 계산       │
└─────────────────────────────────────┘
```

---

## 📊 Phase별 상세 프로세스

### Phase 1: 하이브리드 파싱 시스템

#### 1.1 전략 패턴 구조

```java
// 인터페이스
interface ParsingStrategy {
    Map<String, String> parse(File pdfFile, String insuCd);
    int evaluateConfidence(Map<String, String> result);
    int getPriority();
    boolean isAvailable();
}

// 구현체 1: Python OCR (우선순위 1)
class PythonOcrParsingStrategy implements ParsingStrategy {
    public Map<String, String> parse(File pdfFile, String insuCd) {
        // Python 프로세스 호출
        Map<String, Object> result = pythonPdfService.extractProductInfo(...);
        // 결과 변환
        return convertToMap(result);
    }
    
    public int evaluateConfidence(Map<String, String> result) {
        // 각 필드 유효성 검사 (각 25점)
        int score = 0;
        if (isValid(result.get("insuTerm"))) score += 25;
        if (isValid(result.get("payTerm"))) score += 25;
        if (isValid(result.get("ageRange"))) score += 25;
        if (isValid(result.get("renew"))) score += 25;
        return score; // 0-100점
    }
}

// 구현체 2: 사업방법서 파싱 (우선순위 2)
class BusinessMethodParsingStrategy implements ParsingStrategy {
    public Map<String, String> parse(File pdfFile, String insuCd) {
        // PDF 텍스트 추출
        String text = extractPdfText(pdfFile);
        
        // 정규식 패턴 매칭
        String insuTerm = extractInsuranceTerm(text);
        String payTerm = extractPaymentTerm(text);
        String ageRange = extractAgeRange(text);
        
        return buildMap(insuTerm, payTerm, ageRange);
    }
}

// 구현체 3: Few-Shot LLM (우선순위 3)
class FewShotLlmParsingStrategy implements ParsingStrategy {
    public Map<String, String> parse(File pdfFile, String insuCd) {
        // Few-Shot 프롬프트 생성
        String prompt = fewShotExamples.buildPrompt(...);
        
        // 3개 LLM 병렬 실행
        CompletableFuture<Map<String, String>>[] futures = {
            ollama.parseWithLlama(prompt),
            ollama.parseWithMistral(prompt),
            ollama.parseWithCodeLlama(prompt)
        };
        
        // 결과 대기 (30초 타임아웃)
        CompletableFuture.allOf(futures).get(30, SECONDS);
        
        // 투표 기반 통합
        Map<String, String> integrated = integrateResults(...);
        
        // 다층 검증
        ValidationResult validation = validationService.validate(...);
        
        return integrated;
    }
}
```

#### 1.2 하이브리드 서비스 동작

```java
class HybridParsingService {
    public Map<String, String> parseWithMultipleStrategies(File pdf, String insuCd) {
        log.info("=== 하이브리드 파싱 시작: {} ===", insuCd);
        
        // 1. 캐시 확인
        String cacheKey = pdf.getName() + "_" + insuCd;
        if (cache.containsKey(cacheKey)) {
            log.info("캐시 히트!");
            return cache.get(cacheKey);
        }
        
        // 2. 우선순위 순으로 전략 시도
        List<ParseResult> results = new ArrayList<>();
        
        for (ParsingStrategy strategy : strategies) { // 우선순위 정렬됨
            if (!strategy.isAvailable()) continue;
            
            long start = System.currentTimeMillis();
            Map<String, String> result = strategy.parse(pdf, insuCd);
            long elapsed = System.currentTimeMillis() - start;
            
            int confidence = strategy.evaluateConfidence(result);
            
            log.info("[{}] 완료 - 신뢰도: {}%, 시간: {}ms", 
                    strategy.getName(), confidence, elapsed);
            
            results.add(new ParseResult(strategy.getName(), result, confidence, elapsed));
            
            // 3. 신뢰도 85% 이상이면 즉시 반환
            if (confidence >= 85) {
                log.info("높은 신뢰도 달성, 추가 전략 생략");
                cache.put(cacheKey, result);
                return result;
            }
        }
        
        // 4. 최고 신뢰도 결과 선택
        ParseResult best = results.stream()
            .max(Comparator.comparingInt(ParseResult::getConfidence))
            .orElse(null);
        
        if (best != null) {
            log.info("최종 선택: {} ({}%)", best.getStrategy(), best.getConfidence());
            cache.put(cacheKey, best.getResult());
            return best.getResult();
        }
        
        // 5. 모든 전략 실패 시 기본값
        return getDefaultResult();
    }
}
```

---

### Phase 2: Few-Shot 최적화 + 다층 검증

#### 2.1 Few-Shot 프롬프트 생성

```java
class FewShotExamples {
    private List<String> examples = new ArrayList<>();
    
    public FewShotExamples() {
        // 초기 5개 예시 로드
        examples.add("""
            [예시 1 - 주계약: 종신형]
            입력:
            상품코드: 21686
            상품명: (무)흥국생명 다(多)사랑암보험
            사업방법:
            - 보험기간: 종신
            - 납입기간: 10년납, 15년납, 20년납, 30년납
            - 가입나이: 10년납(남:만15세~80세,여:만15세~80세)
            
            출력:
            {
              "insuTerm": "종신",
              "payTerm": "10년납, 15년납, 20년납, 30년납",
              "ageRange": "10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70)",
              "renew": "비갱신형"
            }
            """);
        
        // ... 예시 2-5
    }
    
    public String buildFewShotPrompt(String pdfText, String insuCd, String productName) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("당신은 보험 문서 파싱 전문가입니다.\n");
        prompt.append("다음 예시들을 참고하여 정확히 추출하세요.\n\n");
        
        // 모든 예시 추가
        for (String example : examples) {
            prompt.append(example).append("\n\n");
        }
        
        // 실제 파싱 요청
        prompt.append("[이제 다음 상품을 파싱하세요]\n");
        prompt.append("입력:\n");
        prompt.append("상품코드: ").append(insuCd).append("\n");
        prompt.append("상품명: ").append(productName).append("\n");
        prompt.append("사업방법 내용:\n").append(pdfText).append("\n\n");
        prompt.append("출력 (JSON 형식):\n");
        
        return prompt.toString();
    }
}
```

#### 2.2 다층 검증 프로세스

```java
class MultiLayerValidationService {
    public ValidationResult validate(Map<String, String> terms, String pdfText, String insuCd) {
        int totalScore = 0;
        List<String> failures = new ArrayList<>();
        
        // Layer 1: 구문 검증 (25점)
        // - 보험기간 형식: "종신|\\d+세만기|\\d+년만기"
        // - 납입기간 형식: "전기납|\\d+년납"
        // - 가입나이 형식: "\\d+~\\d+"
        int syntaxScore = validateSyntax(terms, failures);
        totalScore += syntaxScore;
        log.info("Layer 1 (구문): {}/25", syntaxScore);
        
        // Layer 2: 의미 검증 (25점)
        // - 보험기간 >= 납입기간 (종신은 무한대)
        // - 가입나이 범위 0-120세
        int semanticScore = validateSemantics(terms, failures);
        totalScore += semanticScore;
        log.info("Layer 2 (의미): {}/25", semanticScore);
        
        // Layer 3: 도메인 검증 (25점)
        // - 보험업계 규칙 준수
        // - PDF 텍스트와 일치도 50% 이상
        int domainScore = validateDomain(terms, pdfText, insuCd, failures);
        totalScore += domainScore;
        log.info("Layer 3 (도메인): {}/25", domainScore);
        
        // Layer 4: LLM 교차 검증 (25점)
        // - 3개 모델 일치도
        // - LLM 통합 파싱이면 25점
        int llmScore = validateLLMConsistency(terms, failures);
        totalScore += llmScore;
        log.info("Layer 4 (LLM): {}/25", llmScore);
        
        // 결과 판정
        String status = totalScore >= 90 ? "PASS" : 
                       totalScore >= 70 ? "WARNING" : "FAIL";
        
        return new ValidationResult(totalScore, status, failures, 
                                   generateRecommendations(totalScore, failures));
    }
    
    private int validateSyntax(Map<String, String> terms, List<String> failures) {
        int score = 0;
        
        // 보험기간 형식
        String insuTerm = terms.get("insuTerm");
        if (insuTerm != null && insuTerm.matches(".*(종신|\\d+세만기|\\d+년만기).*")) {
            score += 8;
        } else {
            failures.add("보험기간 형식 오류: " + insuTerm);
        }
        
        // 납입기간 형식
        String payTerm = terms.get("payTerm");
        if (payTerm != null && payTerm.matches(".*(전기납|\\d+년납).*")) {
            score += 8;
        } else {
            failures.add("납입기간 형식 오류: " + payTerm);
        }
        
        // 가입나이 형식
        String ageRange = terms.get("ageRange");
        if (ageRange != null && ageRange.matches(".*\\d+~\\d+.*")) {
            score += 9;
        } else {
            failures.add("가입나이 형식 오류: " + ageRange);
        }
        
        return score;
    }
}
```

---

### Phase 3: 점진적 학습 시스템

#### 3.1 사용자 수정사항 학습

```java
class IncrementalLearningService {
    private List<CorrectionLog> correctionLogs = new ArrayList<>();
    private Map<String, String> learnedPatterns = new ConcurrentHashMap<>();
    
    public void logCorrection(String insuCd, 
                             Map<String, String> original,
                             Map<String, String> corrected,
                             String pdfText) {
        
        // 1. 수정 로그 생성
        CorrectionLog log = CorrectionLog.builder()
            .insuCd(insuCd)
            .originalResult(original)
            .correctedResult(corrected)
            .pdfText(pdfText)
            .timestamp(LocalDateTime.now())
            .build();
        
        correctionLogs.add(log);
        
        // 2. 즉시 패턴 학습
        for (String key : corrected.keySet()) {
            String originalValue = original.get(key);
            String correctedValue = corrected.get(key);
            
            if (!originalValue.equals(correctedValue)) {
                // 패턴 저장: "보험코드_필드" → 올바른 값
                String patternKey = insuCd + "_" + key;
                learnedPatterns.put(patternKey, correctedValue);
                
                log.info("패턴 학습: {} → {}", patternKey, correctedValue);
            }
        }
        
        // 3. 10건마다 배치 학습
        if (correctionLogs.size() % 10 == 0) {
            performBatchLearning();
        }
    }
    
    private void performBatchLearning() {
        log.info("=== 배치 학습 시작 ===");
        
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
                generateFewShotExample(entry.getKey());
            });
        
        // 3. 공통 오류 패턴 분석
        analyzeCommonErrors();
    }
    
    private void generateFewShotExample(String insuCd) {
        // 최신 수정사항 가져오기
        CorrectionLog latest = correctionLogs.stream()
            .filter(log -> log.getInsuCd().equals(insuCd))
            .max(Comparator.comparing(CorrectionLog::getTimestamp))
            .orElse(null);
        
        if (latest != null) {
            // Few-Shot 예시 생성
            String example = buildExample(latest);
            
            // Few-Shot 예시 추가
            fewShotExamples.addExample(example);
            
            log.info("Few-Shot 예시 추가: {}", insuCd);
        }
    }
    
    public Map<String, String> applyLearnedPatterns(String insuCd, 
                                                    Map<String, String> rawResult) {
        Map<String, String> enhanced = new HashMap<>(rawResult);
        
        // 학습된 패턴 적용
        for (String key : rawResult.keySet()) {
            String patternKey = insuCd + "_" + key;
            if (learnedPatterns.containsKey(patternKey)) {
                String learnedValue = learnedPatterns.get(patternKey);
                enhanced.put(key, learnedValue);
                log.debug("학습된 패턴 적용: {} → {}", patternKey, learnedValue);
            }
        }
        
        return enhanced;
    }
}
```

---

## 🔌 API 호출 흐름

### 1. 상품 정보 조회

```
GET /api/product/info/{insuCd}

Request:
  URL: /api/product/info/21686
  
Response:
  {
    "insuCd": "21686",
    "name": "(무)흥국생명 다(多)사랑암보험",
    "type": "주계약",
    "terms": [
      {
        "insuTerm": "종신",
        "payTerm": "10년납",
        "ageRange": "10년납(남:15~80,여:15~80)",
        "renew": "비갱신형",
        "specialNotes": "Few-Shot LLM (신뢰도: 92%, 상태: PASS)"
      },
      {
        "insuTerm": "종신",
        "payTerm": "15년납",
        "ageRange": "15년납(남:15~70,여:15~70)",
        "renew": "비갱신형",
        "specialNotes": "Few-Shot LLM (신뢰도: 92%, 상태: PASS)"
      },
      // ... 4개 조합
    ],
    "calcAvailable": true,
    "message": null
  }
```

### 2. 보험료 계산

```
GET /api/premium/calculate-by-terms/{insuCd}?age={age}&insuTerm={insuTerm}&payTerm={payTerm}&baseAmount={baseAmount}

Request:
  URL: /api/premium/calculate-by-terms/21686?age=30&insuTerm=종신&payTerm=10년납&baseAmount=1000000
  
Response:
  {
    "insuCd": "21686",
    "age": 30,
    "insuTerm": "종신",
    "payTerm": "10년납",
    "baseAmount": 1000000,
    "premiumMale": 45000,
    "premiumFemale": 42000,
    "message": "계산 성공"
  }
```

### 3. 사용자 수정사항 제출

```
POST /api/learning/correction

Request:
  {
    "insuCd": "21686",
    "originalResult": {
      "insuTerm": "종신",
      "payTerm": "10년납",        // 오류
      "ageRange": "15~80",         // 오류
      "renew": "비갱신형"
    },
    "correctedResult": {
      "insuTerm": "종신",
      "payTerm": "10년납, 15년납, 20년납, 30년납",  // 수정
      "ageRange": "10년납(남:15~80,여:15~80)",      // 수정
      "renew": "비갱신형"
    },
    "pdfText": "보험기간: 종신, 납입기간: 10,15,20,30년납"
  }

Response:
  {
    "success": true,
    "message": "수정사항이 학습되었습니다",
    "statistics": {
      "totalCorrections": 1,
      "learnedPatterns": 2,
      "fewShotExamples": 5,
      "currentAccuracy": 75.0,
      "improvement": 0.0
    }
  }
```

---

## ⚠️ 예외 처리

### 1. PDF 파일을 찾을 수 없는 경우

```java
File pdf = findPdfForCode(pdfDir, insuCd);
if (pdf == null) {
    return ProductInfoResponse.builder()
        .insuCd(insuCd)
        .calcAvailable(false)
        .message("질문에 해당하는 PDF 내용이 없습니다")
        .build();
}
```

### 2. 모든 파싱 전략 실패

```java
if (results.isEmpty()) {
    log.warn("모든 파싱 전략 실패");
    return getDefaultResult();
}

private Map<String, String> getDefaultResult() {
    Map<String, String> result = new HashMap<>();
    result.put("insuTerm", "—");
    result.put("payTerm", "—");
    result.put("ageRange", "—");
    result.put("renew", "—");
    result.put("specialNotes", "모든 파싱 전략 실패");
    return result;
}
```

### 3. 다층 검증 실패

```java
ValidationResult validation = validationService.validate(result, pdfText, insuCd);

if (!validation.isPassed()) {
    log.warn("검증 실패 ({}%): {}", 
            validation.getConfidence(), 
            validation.getFailureReasons());
    log.info("권장사항: {}", validation.getRecommendations());
    
    // 결과에 경고 표시
    result.put("specialNotes", 
        String.format("검증 실패 (신뢰도: %d%%) - 수동 확인 필요", 
                     validation.getConfidence()));
}
```

### 4. LLM 타임아웃

```java
try {
    CompletableFuture.allOf(llamaFuture, mistralFuture, codeLlamaFuture)
        .get(30, TimeUnit.SECONDS);  // 30초 타임아웃
} catch (TimeoutException e) {
    log.error("LLM 파싱 타임아웃: {}", e.getMessage());
    return getEmptyResult();
}
```

---

## 🎯 성능 최적화

### 1. 캐시 시스템

```java
// 파싱 결과 캐싱
Map<String, Map<String, String>> resultCache = new ConcurrentHashMap<>();

String cacheKey = pdfFile.getName() + "_" + insuCd;
if (resultCache.containsKey(cacheKey)) {
    log.info("캐시 히트! (0.5초)");
    return resultCache.get(cacheKey);
}

// 파싱 후 캐시에 저장
resultCache.put(cacheKey, result);
```

### 2. 병렬 처리

```java
// 3개 LLM 병렬 실행
CompletableFuture<Map<String, String>> llamaFuture = 
    CompletableFuture.supplyAsync(() -> 
        ollamaService.parseWithLlama(prompt, insuCd), executor);

CompletableFuture<Map<String, String>> mistralFuture = 
    CompletableFuture.supplyAsync(() -> 
        ollamaService.parseWithMistral(prompt, insuCd), executor);

CompletableFuture<Map<String, String>> codeLlamaFuture = 
    CompletableFuture.supplyAsync(() -> 
        ollamaService.parseWithCodeLlama(prompt, insuCd), executor);

// 모든 LLM 완료 대기 (병렬 실행으로 시간 절약)
CompletableFuture.allOf(llamaFuture, mistralFuture, codeLlamaFuture)
    .get(30, TimeUnit.SECONDS);
```

### 3. 조기 종료

```java
// 신뢰도 85% 이상이면 추가 전략 생략
if (confidence >= 85) {
    log.info("높은 신뢰도 달성, 추가 전략 생략");
    return result;
}
```

---

## 📝 요약

### 핵심 프로세스
1. **PDF 검색 및 로드** → 2. **하이브리드 파싱** (3단계 폴백) → 3. **다층 검증** (4단계) → 4. **조합 생성** → 5. **보험료 계산** → 6. **사용자 피드백** → 7. **점진적 학습**

### 주요 특징
- ✅ **다중 폴백**: Python OCR → 사업방법서 → Few-Shot LLM
- ✅ **신뢰도 기반**: 각 단계마다 0-100점 평가
- ✅ **캐시 최적화**: 반복 요청 0.5초 이내
- ✅ **병렬 처리**: 3개 LLM 동시 실행
- ✅ **자기 개선**: 사용자 피드백으로 정확도 향상

### 목표 달성
- **정확도**: 95%+ (Phase 1: 91% → Phase 2: 93% → Phase 3: 95%+)
- **처리 시간**: 2-4초 (캐시: 0.5초)
- **오프라인**: 완전 지원
- **비용**: $0

---

**작성일**: 2025-10-11  
**문서 버전**: 1.0


