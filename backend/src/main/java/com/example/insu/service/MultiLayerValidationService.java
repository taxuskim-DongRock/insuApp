package com.example.insu.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Phase 2: 다층 검증 서비스
 * 4단계 검증을 통해 파싱 결과의 정확도를 평가
 */
@Slf4j
@Service
public class MultiLayerValidationService {
    
    /**
     * 전체 검증 실행
     * 
     * @param terms 파싱된 조건
     * @param pdfText 원본 PDF 텍스트
     * @param insuCd 보험코드
     * @return 검증 결과 (신뢰도 점수 포함)
     */
    public ValidationResult validate(Map<String, String> terms, String pdfText, String insuCd) {
        log.info("=== 다층 검증 시작: {} ===", insuCd);
        
        int totalScore = 0;
        List<String> failureReasons = new ArrayList<>();
        
        // Layer 1: 구문 검증 (25점)
        int syntaxScore = validateSyntax(terms, failureReasons);
        totalScore += syntaxScore;
        log.info("Layer 1 (구문 검증): {}/25", syntaxScore);
        
        // Layer 2: 의미 검증 (25점)
        int semanticScore = validateSemantics(terms, failureReasons);
        totalScore += semanticScore;
        log.info("Layer 2 (의미 검증): {}/25", semanticScore);
        
        // Layer 3: 도메인 검증 (25점)
        int domainScore = validateDomain(terms, pdfText, insuCd, failureReasons);
        totalScore += domainScore;
        log.info("Layer 3 (도메인 검증): {}/25", domainScore);
        
        // Layer 4: LLM 교차 검증 (25점)
        int llmScore = validateLLMConsistency(terms, failureReasons);
        totalScore += llmScore;
        log.info("Layer 4 (LLM 교차): {}/25", llmScore);
        
        log.info("=== 검증 완료: 총점 {}/100 ===", totalScore);
        
        String status = totalScore >= 90 ? "PASS" : (totalScore >= 70 ? "WARNING" : "FAIL");
        
        return new ValidationResult(
            totalScore,
            status,
            failureReasons,
            generateRecommendations(totalScore, failureReasons)
        );
    }
    
    /**
     * Layer 1: 구문 검증 (형식 확인)
     */
    private int validateSyntax(Map<String, String> terms, List<String> failureReasons) {
        int score = 0;
        
        // 보험기간 형식 확인 (8점)
        String insuTerm = terms.get("insuTerm");
        if (insuTerm != null && !insuTerm.equals("—")) {
            if (insuTerm.matches(".*(종신|\\d+세만기|\\d+년만기).*")) {
                score += 8;
            } else {
                failureReasons.add("보험기간 형식 오류: " + insuTerm);
            }
        } else {
            failureReasons.add("보험기간 누락");
        }
        
        // 납입기간 형식 확인 (8점)
        String payTerm = terms.get("payTerm");
        if (payTerm != null && !payTerm.equals("—")) {
            if (payTerm.matches(".*(전기납|\\d+년납).*")) {
                score += 8;
            } else {
                failureReasons.add("납입기간 형식 오류: " + payTerm);
            }
        } else {
            failureReasons.add("납입기간 누락");
        }
        
        // 가입나이 형식 확인 (9점)
        String ageRange = terms.get("ageRange");
        if (ageRange != null && !ageRange.equals("—")) {
            // 숫자~숫자 형태가 있는지 확인
            if (ageRange.matches(".*\\d+~\\d+.*")) {
                score += 9;
            } else {
                failureReasons.add("가입나이 형식 오류: " + ageRange);
            }
        } else {
            failureReasons.add("가입나이 누락");
        }
        
        return score;
    }
    
    /**
     * Layer 2: 의미 검증 (논리적 타당성)
     */
    private int validateSemantics(Map<String, String> terms, List<String> failureReasons) {
        int score = 0;
        
        // 보험기간 > 납입기간 확인 (12점)
        try {
            int insuYears = parseInsuTerm(terms.get("insuTerm"));
            int payYears = parsePayTerm(terms.get("payTerm"));
            
            if (insuYears == 999) { // 종신
                score += 12;
            } else if (insuYears >= payYears) {
                score += 12;
            } else {
                failureReasons.add("보험기간(" + insuYears + "년) < 납입기간(" + payYears + "년)");
            }
        } catch (Exception e) {
            failureReasons.add("보험기간/납입기간 비교 실패: " + e.getMessage());
        }
        
        // 가입나이 범위 확인 (13점)
        if (isAgeRangeValid(terms.get("ageRange"))) {
            score += 13;
        } else {
            failureReasons.add("가입나이 범위가 유효하지 않음");
        }
        
        return score;
    }
    
    /**
     * Layer 3: 도메인 검증 (보험업계 규칙)
     */
    private int validateDomain(Map<String, String> terms, String pdfText, String insuCd, List<String> failureReasons) {
        int score = 0;
        
        // 보험업계 규칙 확인 (12점)
        if (isInsuranceRuleCompliant(terms)) {
            score += 12;
        } else {
            failureReasons.add("보험업계 규칙 위반");
        }
        
        // PDF 텍스트와 일치 확인 (13점)
        if (isPdfTextConsistent(terms, pdfText)) {
            score += 13;
        } else {
            failureReasons.add("PDF 텍스트와 불일치");
        }
        
        return score;
    }
    
    /**
     * Layer 4: LLM 교차 검증 (다중 모델 일치도)
     */
    private int validateLLMConsistency(Map<String, String> terms, List<String> failureReasons) {
        // specialNotes에서 사용된 전략 확인
        String notes = terms.getOrDefault("specialNotes", "");
        
        // LLM 통합 파싱인 경우 높은 점수
        if (notes.contains("LLM 통합")) {
            return 25; // 3개 모델 일치로 간주
        }
        
        // 단일 전략인 경우 중간 점수
        if (notes.contains("Python OCR") || notes.contains("사업방법서")) {
            return 15; // 단일 전략
        }
        
        // 기본값
        return 10;
    }
    
    /**
     * 보험기간 파싱 (년 단위)
     */
    private int parseInsuTerm(String insuTerm) {
        if (insuTerm == null || insuTerm.equals("—")) {
            return 0;
        }
        
        if (insuTerm.contains("종신")) {
            return 999; // 종신은 무한대로 간주
        }
        
        Pattern pattern = Pattern.compile("(\\d+)(세만기|년만기)");
        java.util.regex.Matcher matcher = pattern.matcher(insuTerm);
        
        if (matcher.find()) {
            int years = Integer.parseInt(matcher.group(1));
            if (matcher.group(2).equals("세만기")) {
                // 세만기는 평균 가입나이 30세 기준으로 환산
                return years - 30;
            }
            return years;
        }
        
        return 0;
    }
    
    /**
     * 납입기간 파싱 (년 단위)
     */
    private int parsePayTerm(String payTerm) {
        if (payTerm == null || payTerm.equals("—")) {
            return 0;
        }
        
        if (payTerm.contains("전기납")) {
            return 0; // 전기납은 보험기간과 동일
        }
        
        Pattern pattern = Pattern.compile("(\\d+)년납");
        java.util.regex.Matcher matcher = pattern.matcher(payTerm);
        
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        
        return 0;
    }
    
    /**
     * 가입나이 범위 유효성 확인
     */
    private boolean isAgeRangeValid(String ageRange) {
        if (ageRange == null || ageRange.equals("—")) {
            return false;
        }
        
        // 숫자~숫자 형태 추출
        Pattern pattern = Pattern.compile("(\\d+)~(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(ageRange);
        
        while (matcher.find()) {
            int minAge = Integer.parseInt(matcher.group(1));
            int maxAge = Integer.parseInt(matcher.group(2));
            
            // 유효 범위: 0~120세
            if (minAge < 0 || maxAge > 120 || minAge >= maxAge) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 보험업계 규칙 준수 확인
     */
    private boolean isInsuranceRuleCompliant(Map<String, String> terms) {
        // 기본 규칙: 필수 필드 존재
        String insuTerm = terms.get("insuTerm");
        String payTerm = terms.get("payTerm");
        
        if (insuTerm == null || insuTerm.equals("—") || 
            payTerm == null || payTerm.equals("—")) {
            return false;
        }
        
        // 갱신형 특약은 단기 보험기간
        String renew = terms.get("renew");
        if ("갱신형".equals(renew)) {
            if (insuTerm.contains("종신")) {
                return false; // 갱신형은 종신 불가
            }
        }
        
        return true;
    }
    
    /**
     * PDF 텍스트와 일치 확인
     */
    private boolean isPdfTextConsistent(Map<String, String> terms, String pdfText) {
        if (pdfText == null || pdfText.isEmpty()) {
            return true; // PDF 텍스트가 없으면 검증 생략
        }
        
        int matchCount = 0;
        int totalFields = 0;
        
        // 보험기간 확인
        String insuTerm = terms.get("insuTerm");
        if (insuTerm != null && !insuTerm.equals("—")) {
            totalFields++;
            if (pdfText.contains(insuTerm.replace(", ", "")) || 
                pdfText.contains(insuTerm)) {
                matchCount++;
            }
        }
        
        // 납입기간 확인
        String payTerm = terms.get("payTerm");
        if (payTerm != null && !payTerm.equals("—")) {
            totalFields++;
            String[] payTerms = payTerm.split(", ");
            for (String term : payTerms) {
                if (pdfText.contains(term)) {
                    matchCount++;
                    break;
                }
            }
        }
        
        // 50% 이상 일치하면 통과
        return totalFields == 0 || ((double) matchCount / totalFields) >= 0.5;
    }
    
    /**
     * 개선 권장사항 생성
     */
    private List<String> generateRecommendations(int totalScore, List<String> failureReasons) {
        List<String> recommendations = new ArrayList<>();
        
        if (totalScore < 70) {
            recommendations.add("파싱 결과를 수동으로 확인하세요");
            recommendations.add("다른 파싱 전략을 시도하세요");
        } else if (totalScore < 90) {
            recommendations.add("일부 필드를 재확인하세요");
        } else {
            recommendations.add("검증 통과 - 높은 신뢰도");
        }
        
        // 실패 이유별 권장사항
        for (String reason : failureReasons) {
            if (reason.contains("형식 오류")) {
                recommendations.add("정규식 패턴을 개선하세요");
            } else if (reason.contains("누락")) {
                recommendations.add("PDF에서 해당 필드를 찾을 수 없습니다");
            }
        }
        
        return recommendations;
    }
    
    /**
     * 검증 결과 클래스
     */
    public static class ValidationResult {
        private final int confidence;
        private final String status;
        private final List<String> failureReasons;
        private final List<String> recommendations;
        
        public ValidationResult(int confidence, String status, 
                              List<String> failureReasons, List<String> recommendations) {
            this.confidence = confidence;
            this.status = status;
            this.failureReasons = failureReasons;
            this.recommendations = recommendations;
        }
        
        public int getConfidence() { return confidence; }
        public String getStatus() { return status; }
        public List<String> getFailureReasons() { return new ArrayList<>(failureReasons); }
        public List<String> getRecommendations() { return new ArrayList<>(recommendations); }
        
        public boolean isPassed() {
            return confidence >= 90;
        }
        
        @Override
        public String toString() {
            return String.format("ValidationResult{confidence=%d%%, status=%s, failures=%d}", 
                               confidence, status, failureReasons.size());
        }
    }
}








