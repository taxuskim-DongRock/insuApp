package com.example.insu.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2: Few-Shot 및 다층 검증 테스트
 */
@SpringBootTest
public class FewShotValidationTest {
    
    @Autowired
    private MultiLayerValidationService validationService;
    
    @Autowired
    private FewShotExamples fewShotExamples;
    
    @Test
    @DisplayName("Phase 2-1: 다층 검증 - 완벽한 데이터")
    public void testPerfectDataValidation() {
        // Given
        Map<String, String> terms = new HashMap<>();
        terms.put("insuTerm", "종신");
        terms.put("payTerm", "10년납, 15년납, 20년납, 30년납");
        terms.put("ageRange", "10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70)");
        terms.put("renew", "비갱신형");
        terms.put("specialNotes", "LLM 통합 파싱");
        
        String pdfText = "보험기간: 종신, 납입기간: 10년납, 15년납, 20년납, 30년납";
        String insuCd = "21686";
        
        // When
        MultiLayerValidationService.ValidationResult result = 
            validationService.validate(terms, pdfText, insuCd);
        
        // Then
        assertNotNull(result);
        System.out.println("\n=== Phase 2-1: 완벽한 데이터 검증 ===");
        System.out.println("신뢰도: " + result.getConfidence() + "%");
        System.out.println("상태: " + result.getStatus());
        System.out.println("통과 여부: " + result.isPassed());
        System.out.println("실패 이유: " + result.getFailureReasons());
        System.out.println("권장사항: " + result.getRecommendations());
        System.out.println("=====================================\n");
        
        assertTrue(result.getConfidence() >= 90, "신뢰도가 90% 미만: " + result.getConfidence());
        assertTrue(result.isPassed(), "검증 실패");
    }
    
    @Test
    @DisplayName("Phase 2-2: 다층 검증 - 불완전한 데이터")
    public void testIncompleteDataValidation() {
        // Given
        Map<String, String> terms = new HashMap<>();
        terms.put("insuTerm", "종신");
        terms.put("payTerm", "—"); // 누락
        terms.put("ageRange", "15~80"); // 간단한 형식
        terms.put("renew", "비갱신형");
        
        String pdfText = "보험기간: 종신";
        String insuCd = "21686";
        
        // When
        MultiLayerValidationService.ValidationResult result = 
            validationService.validate(terms, pdfText, insuCd);
        
        // Then
        System.out.println("\n=== Phase 2-2: 불완전한 데이터 검증 ===");
        System.out.println("신뢰도: " + result.getConfidence() + "%");
        System.out.println("상태: " + result.getStatus());
        System.out.println("통과 여부: " + result.isPassed());
        System.out.println("실패 이유: " + result.getFailureReasons());
        System.out.println("권장사항: " + result.getRecommendations());
        System.out.println("======================================\n");
        
        assertFalse(result.isPassed(), "불완전한 데이터가 통과됨");
        assertTrue(result.getConfidence() < 90, "신뢰도가 너무 높음: " + result.getConfidence());
    }
    
    @Test
    @DisplayName("Phase 2-3: 다층 검증 - 논리 오류")
    public void testLogicErrorValidation() {
        // Given
        Map<String, String> terms = new HashMap<>();
        terms.put("insuTerm", "10년만기"); // 보험기간 10년
        terms.put("payTerm", "20년납"); // 납입기간 20년 (오류!)
        terms.put("ageRange", "15~80");
        terms.put("renew", "비갱신형");
        
        String pdfText = "보험기간: 10년만기, 납입기간: 20년납";
        String insuCd = "81880";
        
        // When
        MultiLayerValidationService.ValidationResult result = 
            validationService.validate(terms, pdfText, insuCd);
        
        // Then
        System.out.println("\n=== Phase 2-3: 논리 오류 검증 ===");
        System.out.println("신뢰도: " + result.getConfidence() + "%");
        System.out.println("상태: " + result.getStatus());
        System.out.println("통과 여부: " + result.isPassed());
        System.out.println("실패 이유: " + result.getFailureReasons());
        System.out.println("권장사항: " + result.getRecommendations());
        System.out.println("=================================\n");
        
        assertFalse(result.isPassed(), "논리 오류가 감지되지 않음");
        assertTrue(result.getFailureReasons().stream()
            .anyMatch(r -> r.contains("보험기간") && r.contains("납입기간")),
            "보험기간/납입기간 오류가 감지되지 않음");
    }
    
    @Test
    @DisplayName("Phase 2-4: Few-Shot 예시 개수 확인")
    public void testFewShotExampleCount() {
        // When
        int count = fewShotExamples.getExampleCount();
        
        // Then
        System.out.println("\n=== Phase 2-4: Few-Shot 예시 ===");
        System.out.println("예시 개수: " + count);
        System.out.println("============================\n");
        
        assertTrue(count >= 5, "Few-Shot 예시가 5개 미만: " + count);
    }
    
    @Test
    @DisplayName("Phase 2-5: Few-Shot 프롬프트 생성")
    public void testFewShotPromptGeneration() {
        // Given
        String pdfText = "보험기간: 종신, 납입기간: 10년납";
        String insuCd = "21686";
        String productName = "(무)흥국생명 다(多)사랑암보험";
        
        // When
        String prompt = fewShotExamples.buildFewShotPrompt(pdfText, insuCd, productName);
        
        // Then
        assertNotNull(prompt);
        assertTrue(prompt.contains("[예시"), "예시가 포함되지 않음");
        assertTrue(prompt.contains(insuCd), "보험코드가 포함되지 않음");
        assertTrue(prompt.contains(productName), "상품명이 포함되지 않음");
        
        System.out.println("\n=== Phase 2-5: Few-Shot 프롬프트 ===");
        System.out.println("프롬프트 길이: " + prompt.length() + " 문자");
        System.out.println("예시 개수: " + countOccurrences(prompt, "[예시"));
        System.out.println("===================================\n");
    }
    
    @Test
    @DisplayName("Phase 2-6: 전체 시스템 정확도 평가")
    public void testOverallSystemAccuracy() {
        // 다양한 테스트 케이스
        Map<String, Map<String, String>> testCases = new HashMap<>();
        
        // 케이스 1: 주계약
        Map<String, String> case1 = new HashMap<>();
        case1.put("insuTerm", "종신");
        case1.put("payTerm", "10년납, 15년납, 20년납, 30년납");
        case1.put("ageRange", "10년납(남:15~80,여:15~80)");
        case1.put("renew", "비갱신형");
        case1.put("specialNotes", "LLM 통합 파싱");
        testCases.put("주계약", case1);
        
        // 케이스 2: 복잡한 특약
        Map<String, String> case2 = new HashMap<>();
        case2.put("insuTerm", "90세만기, 100세만기");
        case2.put("payTerm", "10년납, 15년납, 20년납, 30년납");
        case2.put("ageRange", "90세만기: 10년납(남:15~75,여:15~75); 100세만기: 10년납(남:15~80,여:15~80)");
        case2.put("renew", "비갱신형");
        case2.put("specialNotes", "LLM 통합 파싱");
        testCases.put("복잡한 특약", case2);
        
        // 케이스 3: 갱신형 특약
        Map<String, String> case3 = new HashMap<>();
        case3.put("insuTerm", "5년만기, 10년만기");
        case3.put("payTerm", "전기납");
        case3.put("ageRange", "5년만기: 최초(남:15~80,여:15~80), 갱신(남:20~99,여:20~99)");
        case3.put("renew", "갱신형");
        case3.put("specialNotes", "LLM 통합 파싱");
        testCases.put("갱신형 특약", case3);
        
        String pdfText = "보험 상품 사업방법서";
        int passCount = 0;
        int totalCount = testCases.size();
        
        System.out.println("\n=== Phase 2-6: 전체 시스템 정확도 ===");
        
        for (Map.Entry<String, Map<String, String>> entry : testCases.entrySet()) {
            String caseName = entry.getKey();
            Map<String, String> terms = entry.getValue();
            
            MultiLayerValidationService.ValidationResult result = 
                validationService.validate(terms, pdfText, caseName);
            
            if (result.isPassed()) {
                passCount++;
                System.out.println("✓ " + caseName + " - 통과 (" + result.getConfidence() + "%)");
            } else {
                System.out.println("✗ " + caseName + " - 실패 (" + result.getConfidence() + "%)");
            }
        }
        
        double accuracy = (double) passCount / totalCount * 100;
        System.out.println("\n전체 정확도: " + String.format("%.1f%%", accuracy) + " (" + passCount + "/" + totalCount + ")");
        System.out.println("목표 정확도: 92%");
        System.out.println("====================================\n");
        
        // 92% 이상 달성 검증
        assertTrue(accuracy >= 90, "정확도가 90% 미만: " + accuracy + "%");
    }
    
    /**
     * 문자열에서 특정 단어의 출현 횟수 카운트
     */
    private int countOccurrences(String text, String word) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(word, index)) != -1) {
            count++;
            index += word.length();
        }
        return count;
    }
}


