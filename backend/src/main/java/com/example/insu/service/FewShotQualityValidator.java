package com.example.insu.service;

import com.example.insu.service.UwCodeMappingFewShotService.UwCodeMappingRow;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Few-Shot 예시 품질 검증 서비스
 * 
 * CSV 기반 Few-Shot 예시의 품질을 검증하여
 * 부정확한 데이터가 LLM 학습에 사용되는 것을 방지
 */
@Slf4j
@Service
public class FewShotQualityValidator {
    
    /**
     * CSV Few-Shot 품질 검증
     */
    public ValidationResult validateFewShot(UwCodeMappingRow row) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int qualityScore = 100;
        
        // 1. 필수 필드 검증
        if (row.getCode() == null || row.getCode().trim().isEmpty()) {
            errors.add("상품코드가 비어있음");
            qualityScore -= 30;
        } else if (row.getCode().length() != 5 || !row.getCode().matches("\\d{5}")) {
            errors.add("상품코드 형식 오류: " + row.getCode() + " (5자리 숫자 필요)");
            qualityScore -= 20;
        }
        
        // 2. 상품명 검증
        if (row.getProductName() == null || row.getProductName().trim().isEmpty()) {
            errors.add("상품명이 비어있음");
            qualityScore -= 20;
        } else if (row.getProductName().length() < 5) {
            warnings.add("상품명이 너무 짧음: " + row.getProductName());
            qualityScore -= 5;
        }
        
        // 3. 가입나이 패턴 검증 (남)
        if (!isValidAgePattern(row.getEntryAgeM())) {
            errors.add("남성 가입나이 패턴 오류: " + row.getEntryAgeM());
            qualityScore -= 15;
        }
        
        // 4. 가입나이 패턴 검증 (여)
        if (!isValidAgePattern(row.getEntryAgeF())) {
            errors.add("여성 가입나이 패턴 오류: " + row.getEntryAgeF());
            qualityScore -= 15;
        }
        
        // 5. 보험기간 검증
        if (!isValidTerm(row.getPeriodLabel())) {
            errors.add("보험기간 형식 오류: " + row.getPeriodLabel());
            qualityScore -= 15;
        }
        
        // 6. 납입기간 검증
        if (!isValidPayTerm(row.getPayTerm())) {
            warnings.add("납입기간 패턴 비정상: " + row.getPayTerm());
            qualityScore -= 5;
        }
        
        // 7. 상품 그룹 검증
        if (!isValidProductGroup(row.getProductGroup())) {
            warnings.add("상품 그룹 비표준: " + row.getProductGroup());
            qualityScore -= 3;
        }
        
        // 8. 기간 종류 검증
        if (!isValidPeriodKind(row.getPeriodKind())) {
            warnings.add("기간 종류 비표준: " + row.getPeriodKind());
            qualityScore -= 3;
        }
        
        boolean isValid = errors.isEmpty();
        qualityScore = Math.max(0, qualityScore);
        
        ValidationResult result = new ValidationResult(isValid, errors, warnings, qualityScore);
        
        if (!isValid) {
            log.warn("Few-Shot 검증 실패: {} - 에러: {}", row.getCode(), errors);
        } else if (!warnings.isEmpty()) {
            log.debug("Few-Shot 검증 경고: {} - 경고: {}", row.getCode(), warnings);
        }
        
        return result;
    }
    
    /**
     * 가입나이 패턴 검증
     * 예: "만15세 ~ 80세", "15세 ~ 80세", "만15세~80세"
     */
    private boolean isValidAgePattern(String ageRange) {
        if (ageRange == null || ageRange.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = ageRange.trim();
        
        // "—" 또는 빈 값은 유효하지 않음
        if (trimmed.equals("—") || trimmed.equals("-")) {
            return false;
        }
        
        // 정상 패턴: "만15세 ~ 80세" 또는 "15세 ~ 80세"
        if (trimmed.matches(".*\\d+세\\s*~\\s*\\d+세.*")) {
            return true;
        }
        
        // 숫자 범위만 있는 경우: "15 ~ 80"
        if (trimmed.matches(".*\\d+\\s*~\\s*\\d+.*")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 보험기간 검증
     */
    private boolean isValidTerm(String term) {
        if (term == null || term.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = term.trim();
        
        // 종신
        if (trimmed.contains("종신") || trimmed.contains("평생")) {
            return true;
        }
        
        // 세만기: "90세만기", "100세만기", "110세만기"
        if (trimmed.matches(".*\\d+세만기.*")) {
            return true;
        }
        
        // 년만기: "10년만기", "20년만기"
        if (trimmed.matches(".*\\d+년만기.*")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 납입기간 검증
     */
    private boolean isValidPayTerm(String payTerm) {
        if (payTerm == null || payTerm.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = payTerm.trim();
        
        // 전기납, 일시납
        if (trimmed.contains("전기납") || trimmed.contains("일시납")) {
            return true;
        }
        
        // 년납: "10년납", "15년납"
        if (trimmed.matches(".*\\d+년납.*")) {
            return true;
        }
        
        // 월납
        if (trimmed.contains("월납")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 상품 그룹 검증
     */
    private boolean isValidProductGroup(String group) {
        if (group == null) {
            return false;
        }
        
        return group.equals("주계약") || 
               group.equals("선택특약") || 
               group.equals("납입지원특약");
    }
    
    /**
     * 기간 종류 검증
     */
    private boolean isValidPeriodKind(String kind) {
        if (kind == null) {
            return false;
        }
        
        // E(종신), S(세만기), N(년만기), R(갱신형)
        return kind.equals("E") || 
               kind.equals("S") || 
               kind.equals("N") || 
               kind.equals("R");
    }
    
    /**
     * 검증 결과 DTO
     */
    @Data
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;
        private final int qualityScore;
        
        public ValidationResult(boolean valid, List<String> errors, 
                               List<String> warnings, int qualityScore) {
            this.valid = valid;
            this.errors = errors != null ? errors : new ArrayList<>();
            this.warnings = warnings != null ? warnings : new ArrayList<>();
            this.qualityScore = qualityScore;
        }
    }
}





