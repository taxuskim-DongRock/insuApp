# 근본 원인 분석 및 올바른 해결 방안

**날짜**: 2025-10-11  
**문제**: 하드코딩 방식은 유지보수 불가능, UW_CODE_MAPPING 테이블 활용 필요  
**상태**: 🔍 **근본 원인 분석 및 올바른 해결책 구현**

---

## 🔍 **근본 원인 분석**

### **1. UW_CODE_MAPPING 테이블 미연결 원인**

#### **A. 데이터베이스 연결 설정 문제**
```yaml
# application.yml 확인 필요
spring:
  datasource:
    url: jdbc:oracle:thin:@localhost:1521:xe  # 실제 DB 연결 정보 확인
    username: your_username                    # 실제 사용자명 확인
    password: your_password                    # 실제 비밀번호 확인
```

#### **B. UW_CODE_MAPPING 테이블 존재 여부**
- 테이블이 실제로 생성되었는지 확인 필요
- 테이블에 데이터가 삽입되었는지 확인 필요
- 테이블 권한 문제 확인 필요

#### **C. MyBatis 매퍼 설정 문제**
- UwCodeMappingMapper.xml 파일 존재 여부
- 매퍼 스캔 설정 확인
- SQL 쿼리 문법 오류

### **2. 파싱 로직 우선순위 문제**

#### **A. UW_CODE_MAPPING 검증 실패 시 처리**
```java
// 현재 문제: UW_CODE_MAPPING 검증 실패 시 기본값("—") 반환
if (!isEmptyOrDefault(uwMappingResult) && 
    !"EMPTY".equals(uwMappingResult.get("validationSource"))) {
    // 성공 케이스
} else {
    // 실패 시 기본값 반환 - 문제점!
}
```

#### **B. 파싱 전략 우선순위 재조정 필요**
- UW_CODE_MAPPING 검증을 최우선으로 설정
- 검증 실패 시 다른 파싱 전략 시도
- 모든 전략 실패 시에만 기본값 반환

---

## 🛠️ **올바른 해결 방안**

### **Phase 1: 데이터베이스 연결 확인 및 테이블 생성**

#### **1. 데이터베이스 연결 확인**
```bash
# Oracle 데이터베이스 연결 테스트
sqlplus username/password@localhost:1521/xe
```

#### **2. UW_CODE_MAPPING 테이블 생성**
```sql
-- 테이블 존재 여부 확인
SELECT COUNT(*) FROM UW_CODE_MAPPING;

-- 테이블이 없으면 생성
CREATE TABLE UW_CODE_MAPPING (
    SRC_FILE VARCHAR2(20),
    CODE VARCHAR2(10) PRIMARY KEY,
    PRODUCT_NAME VARCHAR2(100),
    MAIN_CODE VARCHAR2(10),
    PERIOD_LABEL VARCHAR2(50),
    PERIOD_VALUE NUMBER,
    PAY_TERM VARCHAR2(100),
    ENTRY_AGE_M VARCHAR2(100),
    ENTRY_AGE_F VARCHAR2(100)
);
```

#### **3. 샘플 데이터 삽입**
```sql
INSERT INTO UW_CODE_MAPPING VALUES 
('UW21239', '79525', '(무)다(多)사랑암진단특약', '21686', '종신', 999, '10년납, 15년납, 20년납, 30년납', '10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~70,여:15~70)', '10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~70,여:15~70)'),
('UW21239', '79527', '(무)다(多)사랑소액암New보장특약', '21686', '종신', 999, '10년납, 15년납, 20년납, 30년납', '10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~70,여:15~70)', '10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~70,여:15~70)'),
('UW21239', '79957', '(무)전이암진단특약', '21686', '종신', 999, '10년납, 15년납, 20년납, 30년납', '10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~70,여:15~70)', '10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~70,여:15~70)'),
('UW21239', '21686', '(무)흥국생명 다(多)사랑암보험', '21686', '종신, 90세만기, 100세만기', 999, '10년납, 15년납, 20년납, 30년납', '종신: 10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~70,여:15~70); 90세만기: 10년납(남:15~75,여:15~75), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~60,여:15~60); 100세만기: 10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~70,여:15~70)', '종신: 10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~70,여:15~70); 90세만기: 10년납(남:15~75,여:15~75), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~60,여:15~60); 100세만기: 10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~70,여:15~70)');
```

### **Phase 2: MyBatis 매퍼 설정 확인**

#### **1. UwCodeMappingMapper.xml 생성**
```xml
<!-- src/main/resources/mappers/UwCodeMappingMapper.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" 
    "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.insu.mapper.UwCodeMappingMapper">
    
    <resultMap id="UwCodeMappingDataResultMap" type="com.example.insu.dto.UwCodeMappingData">
        <result property="srcFile" column="SRC_FILE"/>
        <result property="code" column="CODE"/>
        <result property="productName" column="PRODUCT_NAME"/>
        <result property="mainCode" column="MAIN_CODE"/>
        <result property="periodLabel" column="PERIOD_LABEL"/>
        <result property="periodValue" column="PERIOD_VALUE"/>
        <result property="payTerm" column="PAY_TERM"/>
        <result property="entryAgeM" column="ENTRY_AGE_M"/>
        <result property="entryAgeF" column="ENTRY_AGE_F"/>
    </resultMap>
    
    <select id="selectByCode" parameterType="string" resultMap="UwCodeMappingDataResultMap">
        SELECT SRC_FILE, CODE, PRODUCT_NAME, MAIN_CODE, PERIOD_LABEL, PERIOD_VALUE, PAY_TERM, ENTRY_AGE_M, ENTRY_AGE_F 
        FROM UW_CODE_MAPPING 
        WHERE CODE = #{code} 
        ORDER BY PERIOD_LABEL, PAY_TERM
    </select>
    
    <select id="selectByMainCode" parameterType="string" resultMap="UwCodeMappingDataResultMap">
        SELECT SRC_FILE, CODE, PRODUCT_NAME, MAIN_CODE, PERIOD_LABEL, PERIOD_VALUE, PAY_TERM, ENTRY_AGE_M, ENTRY_AGE_F 
        FROM UW_CODE_MAPPING 
        WHERE MAIN_CODE = #{mainCode} 
        ORDER BY CODE, PERIOD_LABEL, PAY_TERM
    </select>
    
    <select id="selectAll" resultMap="UwCodeMappingDataResultMap">
        SELECT SRC_FILE, CODE, PRODUCT_NAME, MAIN_CODE, PERIOD_LABEL, PERIOD_VALUE, PAY_TERM, ENTRY_AGE_M, ENTRY_AGE_F 
        FROM UW_CODE_MAPPING 
        ORDER BY CODE, PERIOD_LABEL, PAY_TERM
    </select>
    
</mapper>
```

#### **2. MyBatis 설정 확인**
```yaml
# application.yml
mybatis:
  mapper-locations: classpath:mappers/*.xml
  type-aliases-package: com.example.insu.dto
  configuration:
    map-underscore-to-camel-case: true
```

### **Phase 3: 파싱 로직 개선**

#### **1. 하드코딩 제거**
```java
// ProductService.java에서 하드코딩 완전 제거
private Map<String, String> getDefaultTerms(String insuCd) {
    Map<String, String> defaultTerms = new LinkedHashMap<>();
    defaultTerms.put("insuTerm", "—");
    defaultTerms.put("payTerm", "—");
    defaultTerms.put("ageRange", "—");
    defaultTerms.put("renew", "—");
    defaultTerms.put("specialNotes", "모든 파싱 전략 실패");
    return defaultTerms;
}
```

#### **2. UW_CODE_MAPPING 우선순위 최상위 설정**
```java
// parseTermsWithPython 메서드 수정
private Map<String, String> parseTermsWithPython(File pdfFile, String insuCd) {
    try {
        // 1단계: UW_CODE_MAPPING 직접 조회 (최우선)
        log.info("=== UW_CODE_MAPPING 직접 조회 시작: {} ===", insuCd);
        Map<String, String> uwMappingResult = getUwMappingDataDirectly(insuCd);
        
        if (!isEmptyOrDefault(uwMappingResult)) {
            log.info("UW_CODE_MAPPING 직접 조회 성공: {}", insuCd);
            return uwMappingResult;
        }
        
        // 2단계: UW_CODE_MAPPING 기반 검증 파싱
        log.info("=== UW_CODE_MAPPING 검증 파싱 시작: {} ===", insuCd);
        Map<String, String> uwMappingValidationResult = uwMappingHybridParsingService.parseWithUwMappingValidation(pdfFile, insuCd);
        
        if (!isEmptyOrDefault(uwMappingValidationResult) && 
            !"EMPTY".equals(uwMappingValidationResult.get("validationSource"))) {
            log.info("UW_CODE_MAPPING 검증 파싱 성공: {}", insuCd);
            return uwMappingValidationResult;
        }
        
        // 3단계: 기존 하이브리드 파싱
        log.info("=== 기존 하이브리드 파싱 시작: {} ===", insuCd);
        Map<String, String> hybridResult = hybridParsingService.parseWithMultipleStrategies(pdfFile, insuCd);
        
        if (!isEmptyOrDefault(hybridResult)) {
            log.info("하이브리드 파싱 성공: {}", insuCd);
            return hybridResult;
        }
        
        // 최후의 수단으로만 기본값 사용
        log.error("모든 파싱 방법 실패, 기본값 사용: {}", insuCd);
        return getDefaultTerms(insuCd);
        
    } catch (Exception e) {
        log.error("파싱 오류: {}", e.getMessage(), e);
        return getDefaultTerms(insuCd);
    }
}
```

#### **3. UW_CODE_MAPPING 직접 조회 메서드 추가**
```java
private Map<String, String> getUwMappingDataDirectly(String insuCd) {
    try {
        List<UwCodeMappingData> mappingData = uwMappingValidationService.getValidationDataByCode(insuCd);
        
        if (mappingData.isEmpty()) {
            log.warn("UW_CODE_MAPPING에 데이터 없음: {}", insuCd);
            return getDefaultTerms(insuCd);
        }
        
        log.info("UW_CODE_MAPPING 데이터 직접 사용: {} ({} 건)", insuCd, mappingData.size());
        return convertUwMappingToParsedResult(mappingData);
        
    } catch (Exception e) {
        log.error("UW_CODE_MAPPING 직접 조회 오류: {}", e.getMessage(), e);
        return getDefaultTerms(insuCd);
    }
}

private Map<String, String> convertUwMappingToParsedResult(List<UwCodeMappingData> mappingData) {
    Map<String, String> result = new LinkedHashMap<>();
    
    // 보험기간 집합
    Set<String> insuTerms = mappingData.stream()
        .map(UwCodeMappingData::getPeriodLabel)
        .collect(Collectors.toSet());
    
    // 납입기간 집합
    Set<String> payTerms = mappingData.stream()
        .map(UwCodeMappingData::getPayTerm)
        .collect(Collectors.toSet());
    
    result.put("insuTerm", String.join(", ", insuTerms));
    result.put("payTerm", String.join(", ", payTerms));
    result.put("ageRange", buildDetailedAgeRange(mappingData));
    result.put("renew", determineRenewType(mappingData.get(0).getCode()));
    result.put("specialNotes", "UW_CODE_MAPPING 기반 정확한 데이터");
    result.put("validationSource", "UW_CODE_MAPPING_DIRECT");
    
    return result;
}
```

---

## 🚀 **구현 계획**

### **Step 1: 데이터베이스 연결 확인**
1. Oracle 데이터베이스 연결 테스트
2. UW_CODE_MAPPING 테이블 존재 여부 확인
3. 테이블 권한 확인

### **Step 2: 테이블 및 데이터 준비**
1. UW_CODE_MAPPING 테이블 생성
2. 샘플 데이터 삽입
3. 데이터 조회 테스트

### **Step 3: MyBatis 매퍼 설정**
1. UwCodeMappingMapper.xml 생성
2. MyBatis 설정 확인
3. 매퍼 테스트

### **Step 4: 파싱 로직 개선**
1. 하드코딩 완전 제거
2. UW_CODE_MAPPING 우선순위 최상위 설정
3. 직접 조회 메서드 추가

### **Step 5: 테스트 및 검증**
1. 각 특약별 데이터 조회 테스트
2. 파싱 결과 검증
3. UI 화면 확인

---

## 🎯 **예상 결과**

### **UW_CODE_MAPPING 테이블 기반 정확한 데이터**
```
79525: 보험기간: "종신", 납입기간: "10년납, 15년납, 20년납, 30년납", 가입나이: "10년납(남:15~80,여:15~80)..."
79527: 보험기간: "종신", 납입기간: "10년납, 15년납, 20년납, 30년납", 가입나이: "10년납(남:15~80,여:15~80)..."
79957: 보험기간: "종신", 납입기간: "10년납, 15년납, 20년납, 30년납", 가입나이: "10년납(남:15~80,여:15~80)..."
21686: 보험기간: "종신, 90세만기, 100세만기", 납입기간: "10년납, 15년납, 20년납, 30년납", 가입나이: "종신: 10년납(남:15~80,여:15~80)..."
```

### **유지보수 가능한 구조**
- ✅ **하드코딩 제거**: 모든 조건이 UW_CODE_MAPPING 테이블에서 관리
- ✅ **테이블 기반 관리**: 새로운 특약 추가 시 테이블만 업데이트
- ✅ **확장성**: 다른 보험 상품에도 동일한 방식 적용 가능

---

## 🎯 **결론**

### **근본 원인**
1. **UW_CODE_MAPPING 테이블 미연결**: 데이터베이스 연결 또는 테이블 존재 여부 문제
2. **파싱 로직 우선순위 문제**: UW_CODE_MAPPING 검증이 최우선으로 설정되지 않음
3. **하드코딩 의존**: 임시 방편으로 하드코딩 사용

### **올바른 해결 방안**
1. **데이터베이스 연결 확인 및 테이블 생성**
2. **MyBatis 매퍼 설정 완성**
3. **파싱 로직 개선 및 하드코딩 제거**
4. **UW_CODE_MAPPING 우선순위 최상위 설정**

### **유지보수 가능한 구조**
- ✅ **테이블 기반 관리**: 모든 조건을 UW_CODE_MAPPING 테이블에서 중앙 관리
- ✅ **확장성**: 새로운 특약 추가 시 테이블만 업데이트
- ✅ **일관성**: 모든 특약이 동일한 방식으로 처리

---

**작성일**: 2025-10-11  
**상태**: 🔍 **근본 원인 분석 완료, 올바른 해결책 구현 시작**

**하드코딩을 제거하고 UW_CODE_MAPPING 테이블을 올바르게 활용하는 구조로 개선하겠습니다!** 🚀

