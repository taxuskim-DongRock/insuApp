# LLM 통합 후 문제 분석 보고서

**날짜**: 2025-10-11  
**문제**: LLM 도입 후에도 특약 79525가 사업방법서와 다른 조건을 보여줌  
**상태**: ⚠️ **원인 파악 완료, 수정 필요**

---

## 🔍 **문제 분석**

### **화면에서 확인된 문제**

1. **보험기간**: 화면에 "5년" 표시 (사업방법서와 다름)
2. **납입기간**: "15년납, 20년납, 30년납, 월납" 표시 (사업방법서와 다름)
3. **오류 메시지**: "전기납을 숫자로 변환할 수 없습니다"

### **원인 분석**

#### **1. 하드코딩된 특약 조건 사용**

**위치**: `ProductService.java` 라인 1404-1416

```java
case "79525": // (무)다(多)사랑암진단특약
case "79527": // (무)다(多)사랑소액암New보장특약
case "79958": // (무)암(갑상선암및기타피부암제외)주요치료특약Ⅱ
case "79959": // (무)갑상선암및기타피부암주요치료특약Ⅱ
case "81815": // (무)상급종합병원,국립암센터통합암주요치료특약
case "81816": // (무)상급종합병원,국립암센터암(갑상선암및기타피부암제외)주요치료특약Ⅱ
case "81817": // (무)상급종합병원,국립암센터갑상선암및기타피부암주요치료특약특약Ⅱ
  terms.put("insuTerm", "종신");
  terms.put("payTerm", "10년납, 15년납, 20년납, 30년납");
  terms.put("ageRange", "10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~70,여:15~70)");
  terms.put("renew", "비갱신형");
  terms.put("specialNotes", "주계약과 동일한 조건");
  break;
```

**문제**: LLM이 도입되었지만, 특정 특약들은 여전히 하드코딩된 조건을 사용

---

#### **2. 파싱 실패 시 기본값 사용**

**위치**: `ProductService.java` 라인 611-614

```java
// 결과 검증
if (isEmptyOrDefault(resultMap)) {
  log.warn("하이브리드 파싱 결과가 비어있음, 기본값 사용: {}", insuCd);
  return getDefaultTerms(insuCd);  // ← 문제: 하드코딩된 조건으로 폴백
}
```

**문제**: LLM 파싱이 실패하거나 신뢰도가 낮을 때, 하드코딩된 조건으로 폴백

---

#### **3. "전기납" 변환 오류**

**위치**: `ProductService.java` 라인 1440-1470

```java
private Integer parseTermToNumber(String termStr) {
  // ...
  if (trimmed.contains("전기납")) {
    return 1; // 전기납은 1회 납입
  }
  // ...
}
```

**문제**: "전기납"이 예상치 못한 위치에서 나타나거나, 변환 로직에 오류

---

## 🔧 **해결 방안**

### **방안 1: 하드코딩된 특약 조건 제거 (권장)**

**목표**: 모든 특약이 LLM 파싱을 사용하도록 수정

#### **수정 내용**

1. **`getSpecialRiderTerms` 메서드 수정**
   ```java
   private Map<String, String> getSpecialRiderTerms(String insuCd) {
     // 하드코딩된 조건 제거
     // 모든 특약이 LLM 파싱을 사용하도록 수정
     
     Map<String, String> terms = new LinkedHashMap<>();
     terms.put("insuTerm", "—");
     terms.put("payTerm", "—");
     terms.put("ageRange", "—");
     terms.put("renew", "—");
     terms.put("specialNotes", "LLM 파싱 필요");
     return terms;
   }
   ```

2. **파싱 실패 시 처리 개선**
   ```java
   if (isEmptyOrDefault(resultMap)) {
     log.warn("하이브리드 파싱 결과가 비어있음, 사업방법서 재시도: {}", insuCd);
     // 기본값 대신 사업방법서 파싱 재시도
     return getBusinessMethodTerms(pdfFile, insuCd);
   }
   ```

---

### **방안 2: LLM 파싱 신뢰도 임계값 조정**

**목표**: LLM이 더 자주 사용되도록 신뢰도 임계값 낮추기

#### **수정 내용**

**위치**: `ImprovedHybridParsingService.java`

```java
// 신뢰도 85% 이상이면 즉시 반환
if (confidence >= 85) {  // ← 85% → 95%로 상향 조정
  log.info("높은 신뢰도 달성 ({}%), 추가 전략 생략", confidence);
  return result;
}
```

---

### **방안 3: "전기납" 변환 로직 개선**

#### **수정 내용**

**위치**: `ProductService.java` 라인 1440-1470

```java
private Integer parseTermToNumber(String termStr) {
  if (termStr == null || termStr.trim().isEmpty() || "—".equals(termStr.trim())) {
    return null;
  }
  
  String trimmed = termStr.trim();
  
  // "종신" -> 999 (종신보험)
  if (trimmed.contains("종신")) {
    return 999;
  }
  
  // "전기납" -> 1 (전기납입)
  if (trimmed.contains("전기납")) {
    return 1;
  }
  
  // "월납" -> 0 (월납)
  if (trimmed.contains("월납")) {
    return 0;
  }
  
  // 숫자 + "년납" 패턴 추출
  java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)년납");
  java.util.regex.Matcher matcher = pattern.matcher(trimmed);
  if (matcher.find()) {
    return Integer.parseInt(matcher.group(1));
  }
  
  // 숫자만 있는 경우
  try {
    return Integer.parseInt(trimmed.replaceAll("[^0-9]", ""));
  } catch (NumberFormatException e) {
    log.warn("납입기간을 숫자로 변환할 수 없습니다: {}", trimmed);
    return null;
  }
}
```

---

## 🎯 **즉시 적용 가능한 해결책**

### **1단계: 하드코딩된 조건 제거**

```java
// ProductService.java의 getSpecialRiderTerms 메서드 수정
private Map<String, String> getSpecialRiderTerms(String insuCd) {
  Map<String, String> terms = new LinkedHashMap<>();
  
  // 모든 특약이 LLM 파싱을 사용하도록 기본값만 반환
  terms.put("insuTerm", "—");
  terms.put("payTerm", "—");
  terms.put("ageRange", "—");
  terms.put("renew", "—");
  terms.put("specialNotes", "LLM 파싱으로 사업방법서에서 추출");
  
  log.info("특약 {} LLM 파싱 사용", insuCd);
  return terms;
}
```

### **2단계: 파싱 실패 시 재시도 로직**

```java
// parseTermsWithPython 메서드 수정
if (isEmptyOrDefault(resultMap)) {
  log.warn("하이브리드 파싱 결과가 비어있음, 사업방법서 재시도: {}", insuCd);
  
  // 사업방법서 기반 파싱 재시도
  Map<String, String> businessMethodResult = getBusinessMethodTerms(pdfFile, insuCd);
  if (!isEmptyOrDefault(businessMethodResult)) {
    log.info("사업방법서 파싱 성공: {}", insuCd);
    return businessMethodResult;
  }
  
  // 최후의 수단으로만 기본값 사용
  log.error("모든 파싱 방법 실패, 기본값 사용: {}", insuCd);
  return getDefaultTerms(insuCd);
}
```

---

## 📊 **예상 효과**

### **수정 전 (현재)**

| 특약 | 파싱 방법 | 결과 | 문제 |
|------|----------|------|------|
| 79525 | 하드코딩 | 종신, 10/15/20/30년납 | 사업방법서와 다름 |
| 79527 | 하드코딩 | 종신, 10/15/20/30년납 | 사업방법서와 다름 |
| 81819 | LLM 파싱 | 90세만기, 100세만기 | 정확함 |

### **수정 후 (예상)**

| 특약 | 파싱 방법 | 결과 | 개선 |
|------|----------|------|------|
| 79525 | LLM 파싱 | 사업방법서 기반 | ✅ 정확 |
| 79527 | LLM 파싱 | 사업방법서 기반 | ✅ 정확 |
| 81819 | LLM 파싱 | 90세만기, 100세만기 | ✅ 정확 |

---

## 🔧 **구현 순서**

### **1단계: 하드코딩 제거 (즉시 적용 가능)**

1. `getSpecialRiderTerms` 메서드에서 특약별 하드코딩 제거
2. 모든 특약이 LLM 파싱 사용하도록 수정

### **2단계: 파싱 실패 처리 개선**

1. `isEmptyOrDefault` 체크 후 사업방법서 재시도
2. 로그 개선으로 파싱 과정 추적 가능

### **3단계: "전기납" 변환 로직 개선**

1. `parseTermToNumber` 메서드 개선
2. 오류 처리 강화

### **4단계: 테스트 및 검증**

1. 79525, 79527 등 특약 테스트
2. 사업방법서와 일치하는지 확인
3. "전기납" 오류 해결 확인

---

## 📋 **수정 파일 목록**

1. **`ProductService.java`**
   - `getSpecialRiderTerms` 메서드 수정
   - `parseTermsWithPython` 메서드 개선
   - `parseTermToNumber` 메서드 강화

2. **`ImprovedHybridParsingService.java`** (선택)
   - 신뢰도 임계값 조정

---

## 🎯 **결론**

**문제 원인**: LLM이 도입되었지만 특정 특약들은 여전히 하드코딩된 조건을 사용

**해결 방법**: 하드코딩된 특약 조건을 제거하고 모든 특약이 LLM 파싱을 사용하도록 수정

**예상 효과**: 
- ✅ 모든 특약이 사업방법서 기반으로 정확한 조건 표시
- ✅ "전기납" 변환 오류 해결
- ✅ LLM 도입의 진정한 효과 발휘

**우선순위**: 높음 (사용자 화면에서 명확히 보이는 문제)

---

**작성일**: 2025-10-11  
**상태**: ⚠️ **원인 파악 완료, 수정 대기**

**다음 액션**: 하드코딩된 특약 조건 제거 및 LLM 파싱 강화

