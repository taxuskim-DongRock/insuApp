package com.example.insu.integration;

import com.example.insu.dto.*;
import com.example.insu.mapper.*;
import com.example.insu.service.IncrementalLearningService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 증분 학습 시스템 통합 테스트
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IncrementalLearningIntegrationTest {
    
    @Autowired
    private IncrementalLearningService learningService;
    
    @Autowired
    private CorrectionLogMapper correctionLogMapper;
    
    @Autowired
    private LearnedPatternMapper learnedPatternMapper;
    
    @Autowired
    private LearningStatisticsMapper statisticsMapper;
    
    private static final String TEST_INSU_CD = "TEST001";
    
    @Test
    @Order(1)
    @DisplayName("시나리오 1: 첫 수정 → CORRECTION_LOG 저장")
    @Transactional
    void test1_FirstCorrection() {
        // Given
        Map<String, String> original = new HashMap<>();
        original.put("insuTerm", "종신");
        original.put("payTerm", "10년납");
        original.put("ageRange", "15~80");
        original.put("renew", "비갱신형");
        
        Map<String, String> corrected = new HashMap<>();
        corrected.put("insuTerm", "종신");
        corrected.put("payTerm", "10년납, 15년납, 20년납");  // 수정
        corrected.put("ageRange", "남:15~80, 여:15~80");      // 수정
        corrected.put("renew", "비갱신형");
        
        // When
        learningService.logCorrection(TEST_INSU_CD, original, corrected, "PDF 텍스트", "테스트용 수정 이유 1");
        
        // Then
        int logCount = correctionLogMapper.count();
        assertTrue(logCount > 0, "CORRECTION_LOG에 데이터가 저장되어야 함");
        
        System.out.println("✓ 시나리오 1 통과: CORRECTION_LOG 저장 완료 (총 " + logCount + "건)");
    }
    
    @Test
    @Order(2)
    @DisplayName("시나리오 2: 학습 패턴 생성 → LEARNED_PATTERN 조회")
    @Transactional
    void test2_PatternCreation() {
        // Given (시나리오 1의 수정사항)
        Map<String, String> original = new HashMap<>();
        original.put("insuTerm", "종신");
        original.put("payTerm", "10년납");
        original.put("ageRange", "15~80");
        original.put("renew", "비갱신형");
        
        Map<String, String> corrected = new HashMap<>();
        corrected.put("insuTerm", "종신");
        corrected.put("payTerm", "10년납, 15년납, 20년납");
        corrected.put("ageRange", "남:15~80, 여:15~80");
        corrected.put("renew", "비갱신형");
        
        // When
        learningService.logCorrection(TEST_INSU_CD, original, corrected, "PDF 텍스트", "테스트용 수정 이유 2");
        
        // Then
        LearnedPattern payTermPattern = learnedPatternMapper.selectByInsuCdAndField(
            TEST_INSU_CD, "payTerm"
        );
        
        assertNotNull(payTermPattern, "payTerm 패턴이 생성되어야 함");
        assertEquals("10년납, 15년납, 20년납", payTermPattern.getPatternValue());
        
        LearnedPattern ageRangePattern = learnedPatternMapper.selectByInsuCdAndField(
            TEST_INSU_CD, "ageRange"
        );
        
        assertNotNull(ageRangePattern, "ageRange 패턴이 생성되어야 함");
        assertEquals("남:15~80, 여:15~80", ageRangePattern.getPatternValue());
        
        System.out.println("✓ 시나리오 2 통과: 학습 패턴 생성 완료");
    }
    
    @Test
    @Order(3)
    @DisplayName("시나리오 3: 학습 패턴 적용 → 파싱 정확도 향상")
    @Transactional
    void test3_PatternApplication() {
        // Given: 먼저 학습
        Map<String, String> original = new HashMap<>();
        original.put("insuTerm", "종신");
        original.put("payTerm", "10년납");
        original.put("ageRange", "15~80");
        original.put("renew", "비갱신형");
        
        Map<String, String> corrected = new HashMap<>();
        corrected.put("insuTerm", "종신");
        corrected.put("payTerm", "10년납, 15년납, 20년납");
        corrected.put("ageRange", "남:15~80, 여:15~80");
        corrected.put("renew", "비갱신형");
        
        learningService.logCorrection(TEST_INSU_CD, original, corrected, "PDF 텍스트", "테스트용 수정 이유 3");
        
        // When: 학습된 패턴 적용
        Map<String, String> newRawResult = new HashMap<>();
        newRawResult.put("insuTerm", "종신");
        newRawResult.put("payTerm", "10년납");  // 다시 잘못 파싱됨
        newRawResult.put("ageRange", "15~80");  // 다시 잘못 파싱됨
        newRawResult.put("renew", "비갱신형");
        
        Map<String, String> enhanced = learningService.applyLearnedPatterns(
            TEST_INSU_CD, newRawResult
        );
        
        // Then: 학습된 값으로 자동 교체되어야 함
        assertEquals("10년납, 15년납, 20년납", enhanced.get("payTerm"), 
            "학습된 payTerm이 적용되어야 함");
        assertEquals("남:15~80, 여:15~80", enhanced.get("ageRange"),
            "학습된 ageRange가 적용되어야 함");
        assertTrue(enhanced.get("specialNotes").contains("학습 패턴 적용"),
            "specialNotes에 학습 적용 표시가 있어야 함");
        
        System.out.println("✓ 시나리오 3 통과: 학습 패턴 적용 성공");
    }
    
    @Test
    @Order(4)
    @DisplayName("시나리오 4: 배치 학습 (10건 수정)")
    @Transactional
    void test4_BatchLearning() {
        // Given: 10건의 수정사항
        for (int i = 1; i <= 10; i++) {
            String insuCd = "TEST" + String.format("%03d", i);
            
            Map<String, String> original = new HashMap<>();
            original.put("insuTerm", "종신");
            original.put("payTerm", "10년납");
            original.put("ageRange", "15~80");
            original.put("renew", "비갱신형");
            
            Map<String, String> corrected = new HashMap<>();
            corrected.put("insuTerm", "종신");
            corrected.put("payTerm", "10년납, 15년납");
            corrected.put("ageRange", "남:15~80, 여:15~80");
            corrected.put("renew", "비갱신형");
            
            learningService.logCorrection(insuCd, original, corrected, "PDF " + i, "배치 테스트용 수정 이유 " + i);
        }
        
        // When: 배치 학습 실행
        learningService.performBatchLearning();
        
        // Then: 학습 통계 확인
        LearningStatistics stats = learningService.getStatistics();
        assertNotNull(stats, "통계가 조회되어야 함");
        assertTrue(stats.getTotalCorrections() >= 10, 
            "총 수정 건수가 10건 이상이어야 함");
        
        System.out.println("✓ 시나리오 4 통과: 배치 학습 완료");
        System.out.println("  총 수정: " + stats.getTotalCorrections());
        System.out.println("  학습 패턴: " + stats.getTotalPatterns());
    }
    
    @Test
    @Order(5)
    @DisplayName("시나리오 5: 통계 대시보드 조회")
    void test5_StatisticsDashboard() {
        // When
        LearningStatistics stats = learningService.getStatistics();
        
        // Then
        assertNotNull(stats, "통계가 조회되어야 함");
        assertTrue(stats.getTotalCorrections() >= 0, "총 수정 건수는 0 이상");
        assertTrue(stats.getTotalPatterns() >= 0, "총 패턴 수는 0 이상");
        
        System.out.println("✓ 시나리오 5 통과: 통계 조회 성공");
        System.out.println("  총 수정: " + stats.getTotalCorrections());
        System.out.println("  학습 패턴: " + stats.getTotalPatterns());
        System.out.println("  Few-Shot 예시: " + stats.getTotalFewShotExamples());
        System.out.println("  현재 정확도: " + stats.getCurrentAccuracy() + "%");
    }
    
    @Test
    @Order(6)
    @DisplayName("시나리오 6: 초기 정확도 설정 및 개선 추적")
    @Transactional
    void test6_AccuracyImprovement() {
        // Given: 초기 정확도 75%
        learningService.setInitialAccuracy(75.0);
        
        // When: 여러 수정사항 입력
        for (int i = 1; i <= 5; i++) {
            Map<String, String> original = new HashMap<>();
            original.put("insuTerm", "종신");
            original.put("payTerm", "10년납");
            original.put("ageRange", "15~80");
            original.put("renew", "비갱신형");
            
            Map<String, String> corrected = new HashMap<>();
            corrected.put("insuTerm", "종신");
            corrected.put("payTerm", "10년납, 15년납, 20년납");
            corrected.put("ageRange", "남:15~80, 여:15~80");
            corrected.put("renew", "비갱신형");
            
            learningService.logCorrection("IMPROVE" + i, original, corrected, "PDF " + i, "정확도 개선 테스트용 수정 이유 " + i);
        }
        
        // Then: 통계 확인
        LearningStatistics stats = learningService.getStatistics();
        assertNotNull(stats);
        assertTrue(stats.getCurrentAccuracy() >= 75.0, 
            "현재 정확도는 초기 정확도 이상이어야 함");
        
        System.out.println("✓ 시나리오 6 통과: 정확도 개선 추적 완료");
        System.out.println("  초기 정확도: 75.0%");
        System.out.println("  현재 정확도: " + stats.getCurrentAccuracy() + "%");
        System.out.println("  정확도 향상: +" + stats.getImprovement() + "%");
    }
}

