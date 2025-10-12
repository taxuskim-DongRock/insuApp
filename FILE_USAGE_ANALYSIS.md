# 소스 파일 사용 분석 보고서

## 📋 Service Layer 파일 분석

### ✅ 필수 파일 (사용 중)

| 파일명 | 사용처 | 역할 | 상태 |
|--------|--------|------|------|
| **ProductService.java** | 메인 서비스 | 상품 정보, 보험료 계산 | ✅ 필수 |
| **ImprovedHybridParsingService.java** | ProductService | Caffeine Cache 적용 파싱 | ✅ 필수 |
| **ParsingStrategy.java** | 인터페이스 | 파싱 전략 정의 | ✅ 필수 |
| **PythonOcrParsingStrategy.java** | ImprovedHybridParsingService | Python OCR 전략 | ✅ 필수 |
| **BusinessMethodParsingStrategy.java** | ImprovedHybridParsingService | 정규식 전략 | ✅ 필수 |
| **LlmParsingStrategy.java** | ImprovedHybridParsingService | 기본 LLM 전략 | ✅ 필수 |
| **FewShotLlmParsingStrategy.java** | ImprovedHybridParsingService | Few-Shot LLM 전략 | ✅ 필수 |
| **QuorumLlmService.java** | FewShotLlmParsingStrategy | 쿼럼 기반 LLM | ✅ 필수 |
| **FewShotExamples.java** | FewShotLlmParsingStrategy | Few-Shot 예시 관리 | ✅ 필수 |
| **MultiLayerValidationService.java** | FewShotLlmParsingStrategy | 다층 검증 | ✅ 필수 |
| **IncrementalLearningService.java** | LearningController | 점진적 학습 | ✅ 필수 |
| **PythonPdfService.java** | PythonOcrParsingStrategy | Python 연동 | ✅ 필수 |
| **OllamaService.java** | QuorumLlmService, LlmParsingStrategy | Ollama 연동 | ✅ 필수 |

### ⚠️ 중복 파일 (삭제 대상)

| 파일명 | 문제 | 이유 | 상태 |
|--------|------|------|------|
| **HybridParsingService.java** | ImprovedHybridParsingService와 중복 | 구버전 (Caffeine 미적용) | ⚠️ 삭제 |

### ⚠️ 개념 설명용 파일 (삭제 대상)

| 파일명 | 문제 | 이유 | 상태 |
|--------|------|------|------|
| **OfflineLLMService.java** | 실제 사용 안 됨 | 개념 설명용 더미 | ⚠️ 삭제 |
| **LocalModelManager.java** | 실제 사용 안 됨 | 개념 설명용 더미 | ⚠️ 삭제 |
| **OfflineCacheService.java** | 실제 사용 안 됨 | 개념 설명용 더미 | ⚠️ 삭제 |
| **PdfService.java** | 확인 필요 | 사용 여부 불명 | ⚠️ 확인 |

---

## 🔍 상세 분석

### 1. HybridParsingService vs ImprovedHybridParsingService

**HybridParsingService.java:**
```java
// 구버전 - HashMap 사용 (메모리 누수 위험)
private final Map<String, Map<String, String>> resultCache = new HashMap<>();

public Map<String, String> parseWithMultipleStrategies(...) {
    // 수동 캐시 관리
    if (resultCache.containsKey(cacheKey)) { ... }
}
```

**ImprovedHybridParsingService.java:**
```java
// 신버전 - Caffeine Cache 사용 (개선됨)
@Cacheable(value = "parsingCache", key = "...")
public Map<String, String> parseWithMultipleStrategies(...) {
    // Spring Cache가 자동 관리
}
```

**ProductService.java (라인 30):**
```java
private final ImprovedHybridParsingService hybridParsingService;
// ✅ ImprovedHybridParsingService 사용 중
```

**결론: HybridParsingService.java는 더 이상 사용되지 않음** ⚠️

---

### 2. 개념 설명용 파일들

**OfflineLLMService.java, LocalModelManager.java, OfflineCacheService.java:**
- 이전 단계에서 개념 설명용으로 생성
- 실제 ProductService나 다른 서비스에서 사용 안 됨
- OllamaService, QuorumLlmService가 실제 구현체

**결론: 삭제 대상** ⚠️

---

## 📋 삭제 대상 파일 목록

### Service Layer (4개)

1. **HybridParsingService.java**
   - 이유: ImprovedHybridParsingService로 대체됨
   - 영향: 없음 (ProductService가 Improved 사용)

2. **OfflineLLMService.java**
   - 이유: 개념 설명용 더미 파일
   - 영향: 없음 (실제 사용 안 됨)

3. **LocalModelManager.java**
   - 이유: 개념 설명용 더미 파일
   - 영향: 없음 (실제 사용 안 됨)

4. **OfflineCacheService.java**
   - 이유: 개념 설명용 더미 파일
   - 영향: 없음 (CacheConfig가 실제 구현)

### 확인 필요 (1개)

5. **PdfService.java**
   - 확인 필요: 실제 사용 여부 불명

---

## ✅ 최종 유지 파일 (13개)

1. ProductService.java
2. ImprovedHybridParsingService.java
3. ParsingStrategy.java (인터페이스)
4. PythonOcrParsingStrategy.java
5. BusinessMethodParsingStrategy.java
6. LlmParsingStrategy.java
7. FewShotLlmParsingStrategy.java
8. QuorumLlmService.java
9. OllamaService.java
10. FewShotExamples.java
11. MultiLayerValidationService.java
12. IncrementalLearningService.java
13. PythonPdfService.java

---

**삭제 권장: 4-5개 파일**


