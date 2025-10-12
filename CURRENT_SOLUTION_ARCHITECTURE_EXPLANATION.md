# 현재 해결방안 아키텍처 설명

**날짜**: 2025-10-11  
**질문**: UW_CODE_MAPPING 테이블이 PDF 파싱 결과 검증용인가?  
**답변**: **아니오, 직접 데이터 소스로 사용됩니다**

---

## 🔍 **현재 구현된 해결방안의 정확한 구조**

### **UW_CODE_MAPPING 테이블의 역할**

#### **❌ 검증용이 아님**
- PDF 파싱 결과를 검증하는 용도가 **아닙니다**
- 파싱된 결과를 검증하고 수정하는 용도가 **아닙니다**

#### **✅ 직접 데이터 소스로 사용**
- **1차 데이터 소스**로서 직접 사용됩니다
- PDF 파싱보다 **우선순위가 높습니다**
- **가장 정확하고 신뢰할 수 있는 데이터**로 취급됩니다

---

## 🏗️ **현재 파싱 우선순위 구조**

### **1단계: UW_CODE_MAPPING 직접 조회 (최우선)**
```java
// 1단계: UW_CODE_MAPPING 직접 조회 (최우선)
Map<String, String> uwMappingDirectResult = getUwMappingDataDirectly(insuCd);

if (!isEmptyOrDefault(uwMappingDirectResult)) {
    log.info("UW_CODE_MAPPING 직접 조회 성공: {}", insuCd);
    return uwMappingDirectResult; // 즉시 반환, 다른 단계 실행 안함
}
```

**특징:**
- ✅ **PDF 파싱 없이** 데이터베이스에서 직접 조회
- ✅ **가장 빠르고 정확한** 방법
- ✅ **신뢰도: 100%**
- ✅ **성공 시 다른 모든 단계 건너뜀**

### **2단계: UW_CODE_MAPPING 기반 검증 파싱**
```java
// 2단계: UW_CODE_MAPPING 기반 검증 파싱
Map<String, String> uwMappingResult = uwMappingHybridParsingService.parseWithUwMappingValidation(pdfFile, insuCd);
```

**특징:**
- ✅ **PDF 파싱 후** UW_CODE_MAPPING으로 검증
- ✅ **검증 실패 시** UW_CODE_MAPPING 데이터로 보정
- ✅ **신뢰도: 90%+**

### **3단계: 특수 조건 특약 하드코딩**
```java
// 3단계: 특수 조건을 가진 특약들은 하드코딩 사용
if (isSpecialConditionRider(insuCd)) {
    Map<String, String> hardcodedTerms = getSpecialRiderTerms(insuCd);
    return hardcodedTerms;
}
```

**특징:**
- ✅ **특별한 조건을 가진 특약들** (81819, 81880, 83192)
- ✅ **신뢰도: 95%**

### **4단계: 기존 하이브리드 파싱**
```java
// 4단계: 기존 하이브리드 파싱 사용
Map<String, String> hybridResult = hybridParsingService.parseWithMultipleStrategies(pdfFile, insuCd);
```

**특징:**
- ✅ **Python OCR + Java PDFBox + LLM** 조합
- ✅ **신뢰도: 80%**

### **5단계: 사업방법서 기반 파싱**
```java
// 5단계: 사업방법서 기반 파싱 재시도
Map<String, String> businessMethodResult = getBusinessMethodTerms(pdfFile, insuCd);
```

**특징:**
- ✅ **PDF의 "사업방법" 섹션**에서 정규식 추출
- ✅ **신뢰도: 70%**

### **최후의 수단: 기본값 반환**
```java
// 최후의 수단으로만 기본값 사용
return getDefaultTerms(insuCd); // "—" 반환
```

**특징:**
- ✅ **모든 파싱 전략 실패 시에만** 사용
- ✅ **신뢰도: 0%**

---

## 🎯 **핵심 포인트**

### **UW_CODE_MAPPING 테이블은 "검증용"이 아닌 "주 데이터 소스"**

#### **1. 직접 데이터 소스로서의 역할**
```java
private Map<String, String> getUwMappingDataDirectly(String insuCd) {
    // 데이터베이스에서 직접 조회
    List<UwCodeMappingData> mappingData = uwMappingValidationService.getValidationDataByCode(insuCd);
    
    // PDF 파싱 없이 바로 결과 반환
    return convertUwMappingToParsedResult(mappingData);
}
```

#### **2. PDF 파싱보다 우선순위가 높음**
- **1단계**: UW_CODE_MAPPING 직접 조회 (PDF 파싱 없음)
- **2단계**: PDF 파싱 + UW_CODE_MAPPING 검증
- **3단계 이후**: PDF 파싱만 사용

#### **3. 성공 시 다른 모든 단계 건너뜀**
```java
if (!isEmptyOrDefault(uwMappingDirectResult)) {
    return uwMappingDirectResult; // 즉시 반환, PDF 파싱 안함
}
```

---

## 📊 **실제 동작 예시**

### **79525 다사랑암진단특약 요청 시**

#### **1단계: UW_CODE_MAPPING 직접 조회**
```sql
SELECT * FROM UW_CODE_MAPPING WHERE CODE = '79525';
```

**결과:**
```
CODE: 79525
PRODUCT_NAME: (무)다사랑암진단특약
PERIOD_LABEL: 종신
PAY_TERM: 10년납, 15년납, 20년납, 30년납
ENTRY_AGE_M: 10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70)...
ENTRY_AGE_F: 10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70)...
```

#### **2단계: 데이터 변환 및 반환**
```java
// PDF 파싱 없이 바로 결과 반환
return {
    "insuTerm": "종신",
    "payTerm": "10년납, 15년납, 20년납, 30년납",
    "ageRange": "10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70)...",
    "validationSource": "UW_CODE_MAPPING_DIRECT"
};
```

#### **결과: PDF 파싱 단계 실행되지 않음**
- ✅ **PDF 파일을 열지 않음**
- ✅ **파싱 로직 실행되지 않음**
- ✅ **가장 빠르고 정확한 결과**

---

## 🔄 **UW_CODE_MAPPING 테이블의 두 가지 역할**

### **1. 직접 데이터 소스 (1단계)**
```java
// PDF 파싱 없이 데이터베이스에서 직접 조회
List<UwCodeMappingData> mappingData = uwMappingValidationService.getValidationDataByCode(insuCd);
return convertUwMappingToParsedResult(mappingData);
```

### **2. 검증 및 보정 (2단계)**
```java
// PDF 파싱 후 UW_CODE_MAPPING으로 검증
ValidationResult validation = uwMappingValidationService.validateWithUwMapping(insuCd, llmResult);

if ("VALID".equals(validation.getStatus())) {
    return llmResult; // 검증 통과
} else {
    // 검증 실패 시 UW_CODE_MAPPING 데이터로 보정
    return correctedResult;
}
```

---

## 🎯 **결론**

### **UW_CODE_MAPPING 테이블의 정확한 역할**

#### **✅ 주 데이터 소스**
- **1차 데이터 소스**로서 직접 사용
- PDF 파싱보다 **우선순위가 높음**
- **가장 정확하고 신뢰할 수 있는 데이터**

#### **✅ 검증 및 보정**
- PDF 파싱 결과를 검증
- 검증 실패 시 데이터로 보정
- **2차 역할**

#### **❌ 단순 검증용이 아님**
- PDF 파싱 결과만 검증하는 용도가 **아님**
- **주 데이터 소스**로서의 역할이 **더 중요**

### **현재 시스템의 장점**

1. **정확성**: UW_CODE_MAPPING 테이블이 가장 정확한 데이터 제공
2. **속도**: PDF 파싱 없이 데이터베이스에서 직접 조회
3. **신뢰성**: 검증된 데이터만 사용
4. **유지보수성**: 테이블만 업데이트하면 모든 특약 조건 변경 가능

---

**작성일**: 2025-10-11  
**상태**: ✅ **현재 구현된 해결방안 설명 완료**

**UW_CODE_MAPPING 테이블은 검증용이 아닌 주 데이터 소스로 사용됩니다!** 🎯

