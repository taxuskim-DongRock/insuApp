package com.example.insu.service;

import com.example.insu.dto.CorrectionLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Phase 3: 점진적 학습 서비스
 * 사용자 피드백을 통해 파싱 정확도를 지속적으로 향상
 */
@Slf4j
@Service
public class IncrementalLearningService {
    
    private final FewShotExamples fewShotExamples;
    
    // 사용자 수정 로그
    private final List<CorrectionLog> correctionLogs = Collections.synchronizedList(new ArrayList<>());
    
    // 학습된 패턴 (보험코드 + 필드 → 올바른 값)
    private final Map<String, String> learnedPatterns = new ConcurrentHashMap<>();
    
    // 통계
    private int totalCorrections = 0;
    private int patternsLearned = 0;
    private double initialAccuracy = 0.0;
    
    public IncrementalLearningService(FewShotExamples fewShotExamples) {
        this.fewShotExamples = fewShotExamples;
    }
    
    /**
     * 사용자 수정사항 기록 및 학습
     */
    public void logCorrection(String insuCd, Map<String, String> originalResult,
                             Map<String, String> correctedResult, String pdfText) {
        
        log.info("사용자 수정 로깅: {} (수정 필드: {})", insuCd, 
                countCorrectedFields(originalResult, correctedResult));
        
        // 수정 로그 생성
        CorrectionLog correctionLog = CorrectionLog.builder()
            .insuCd(insuCd)
            .originalResult(new HashMap<>(originalResult))
            .correctedResult(new HashMap<>(correctedResult))
            .pdfText(pdfText)
            .timestamp(LocalDateTime.now())
            .build();
        
        correctionLogs.add(correctionLog);
        totalCorrections++;
        
        // 즉시 패턴 학습
        learnFromCorrection(correctionLog);
        
        // 10건마다 배치 학습
        if (correctionLogs.size() % 10 == 0) {
            performBatchLearning();
        }
        
        log.info("학습 완료 - 총 수정: {}, 학습된 패턴: {}", totalCorrections, patternsLearned);
    }
    
    /**
     * 개별 수정사항에서 패턴 학습
     */
    private void learnFromCorrection(CorrectionLog correctionLog) {
        for (String key : correctionLog.getCorrectedResult().keySet()) {
            String original = correctionLog.getOriginalResult().get(key);
            String corrected = correctionLog.getCorrectedResult().get(key);
            
            if (original != null && corrected != null && !original.equals(corrected)) {
                // 패턴 저장: "보험코드_필드" → 올바른 값
                String patternKey = correctionLog.getInsuCd() + "_" + key;
                learnedPatterns.put(patternKey, corrected);
                patternsLearned++;
                
                log.info("패턴 학습: {} → {}", patternKey, corrected);
                
                // PDF 텍스트에서 성공 패턴 추출
                extractSuccessPattern(correctionLog.getPdfText(), key, corrected);
            }
        }
    }
    
    /**
     * PDF 텍스트에서 성공 패턴 추출
     */
    private void extractSuccessPattern(String pdfText, String field, String value) {
        // PDF 텍스트에서 해당 값이 어떻게 표현되었는지 분석
        if (pdfText.contains(value)) {
            log.debug("PDF에서 정확한 값 발견: {} = {}", field, value);
            // 향후: 주변 컨텍스트를 추출하여 정규식 패턴 생성
        }
    }
    
    /**
     * 배치 학습 (10건마다)
     */
    private void performBatchLearning() {
        log.info("=== 배치 학습 시작: {} 건 ===", correctionLogs.size());
        
        // 1. 자주 실패하는 상품 파악
        Map<String, Long> failureCount = correctionLogs.stream()
            .collect(Collectors.groupingBy(
                CorrectionLog::getInsuCd,
                Collectors.counting()
            ));
        
        log.info("실패 빈도 통계: {}", failureCount);
        
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
        
        log.info("=== 배치 학습 완료 ===");
    }
    
    /**
     * Few-Shot 예시 자동 생성
     */
    private void generateFewShotExample(String insuCd) {
        // 해당 상품의 최신 수정사항 가져오기
        CorrectionLog latestCorrection = correctionLogs.stream()
            .filter(corrLog -> corrLog.getInsuCd().equals(insuCd))
            .max(Comparator.comparing(CorrectionLog::getTimestamp))
            .orElse(null);
        
        if (latestCorrection == null) {
            return;
        }
        
        // Few-Shot 예시 생성
        String example = buildExampleFromLog(latestCorrection);
        
        // Few-Shot 예시에 추가
        fewShotExamples.addExample(example);
        log.info("Few-Shot 예시 추가: {} (총 {} 개)", insuCd, fewShotExamples.getExampleCount());
    }
    
    /**
     * 수정 로그에서 Few-Shot 예시 생성
     */
    private String buildExampleFromLog(CorrectionLog correctionLog) {
        Map<String, String> result = correctionLog.getCorrectedResult();
        
        return String.format("""
            [학습된 예시 - %s]
            입력:
            상품코드: %s
            PDF 내용: %s
            
            출력:
            {
              "insuTerm": "%s",
              "payTerm": "%s",
              "ageRange": "%s",
              "renew": "%s"
            }
            """,
            correctionLog.getInsuCd(),
            correctionLog.getInsuCd(),
            truncateText(correctionLog.getPdfText(), 200),
            result.get("insuTerm"),
            result.get("payTerm"),
            result.get("ageRange"),
            result.get("renew")
        );
    }
    
    /**
     * 공통 오류 패턴 분석
     */
    private void analyzeCommonErrors() {
        Map<String, Integer> errorPatterns = new HashMap<>();
        
        for (CorrectionLog log : correctionLogs) {
            // 가입나이 중복 "만만" 패턴
            String ageRange = log.getOriginalResult().get("ageRange");
            if (ageRange != null && ageRange.contains("만만")) {
                errorPatterns.merge("age_duplicate_prefix", 1, Integer::sum);
            }
            
            // 보험기간/납입기간 불일치
            String insuTerm = log.getOriginalResult().get("insuTerm");
            String payTerm = log.getOriginalResult().get("payTerm");
            if (insuTerm != null && payTerm != null && 
                !insuTerm.equals(log.getCorrectedResult().get("insuTerm")) &&
                !payTerm.equals(log.getCorrectedResult().get("payTerm"))) {
                errorPatterns.merge("term_mismatch", 1, Integer::sum);
            }
        }
        
        log.info("공통 오류 패턴: {}", errorPatterns);
        
        // 상위 3개 오류 패턴에 대한 자동 수정 규칙 추가
        errorPatterns.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(3)
            .forEach(entry -> {
                addAutoCorrectionRule(entry.getKey());
            });
    }
    
    /**
     * 자동 수정 규칙 추가
     */
    private void addAutoCorrectionRule(String errorPattern) {
        log.info("자동 수정 규칙 추가: {}", errorPattern);
        // 향후: 자동 수정 규칙을 적용하여 파싱 개선
    }
    
    /**
     * 학습된 패턴 적용
     */
    public Map<String, String> applyLearnedPatterns(String insuCd, Map<String, String> rawResult) {
        Map<String, String> enhanced = new HashMap<>(rawResult);
        boolean applied = false;
        
        // 학습된 패턴 적용
        for (String key : rawResult.keySet()) {
            String patternKey = insuCd + "_" + key;
            if (learnedPatterns.containsKey(patternKey)) {
                String learnedValue = learnedPatterns.get(patternKey);
                enhanced.put(key, learnedValue);
                applied = true;
                log.debug("학습된 패턴 적용: {} → {}", patternKey, learnedValue);
            }
        }
        
        if (applied) {
            enhanced.put("specialNotes", 
                enhanced.getOrDefault("specialNotes", "") + " [학습 패턴 적용]");
        }
        
        return enhanced;
    }
    
    /**
     * 학습 통계 조회
     */
    public LearningStatistics getStatistics() {
        double currentAccuracy = calculateCurrentAccuracy();
        double improvement = currentAccuracy - initialAccuracy;
        
        return new LearningStatistics(
            correctionLogs.size(),
            learnedPatterns.size(),
            fewShotExamples.getExampleCount(),
            currentAccuracy,
            improvement
        );
    }
    
    /**
     * 현재 정확도 계산
     */
    private double calculateCurrentAccuracy() {
        if (correctionLogs.isEmpty()) {
            return initialAccuracy;
        }
        
        // 최근 7일간의 수정 건수
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        long recentCorrections = correctionLogs.stream()
            .filter(log -> log.getTimestamp().isAfter(sevenDaysAgo))
            .count();
        
        // 추정 총 파싱 건수 (수정 건수의 10배로 가정)
        long estimatedTotal = Math.max(recentCorrections * 10, 1);
        
        // 정확도 = (총 건수 - 수정 건수) / 총 건수 * 100
        return (estimatedTotal - recentCorrections) * 100.0 / estimatedTotal;
    }
    
    /**
     * 초기 정확도 설정
     */
    public void setInitialAccuracy(double accuracy) {
        this.initialAccuracy = accuracy;
        log.info("초기 정확도 설정: {}%", accuracy);
    }
    
    /**
     * 학습 데이터 초기화
     */
    public void clearLearningData() {
        correctionLogs.clear();
        learnedPatterns.clear();
        totalCorrections = 0;
        patternsLearned = 0;
        log.info("학습 데이터 초기화 완료");
    }
    
    /**
     * 텍스트 자르기
     */
    private String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
    
    /**
     * 수정된 필드 개수 카운트
     */
    private int countCorrectedFields(Map<String, String> original, Map<String, String> corrected) {
        int count = 0;
        for (String key : original.keySet()) {
            String originalValue = original.get(key);
            String correctedValue = corrected.get(key);
            if (originalValue != null && correctedValue != null && !originalValue.equals(correctedValue)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 학습 통계 클래스
     */
    public static class LearningStatistics {
        private final int totalCorrections;
        private final int learnedPatterns;
        private final int fewShotExamples;
        private final double currentAccuracy;
        private final double improvement;
        
        public LearningStatistics(int totalCorrections, int learnedPatterns,
                                int fewShotExamples, double currentAccuracy, double improvement) {
            this.totalCorrections = totalCorrections;
            this.learnedPatterns = learnedPatterns;
            this.fewShotExamples = fewShotExamples;
            this.currentAccuracy = currentAccuracy;
            this.improvement = improvement;
        }
        
        public int getTotalCorrections() { return totalCorrections; }
        public int getLearnedPatterns() { return learnedPatterns; }
        public int getFewShotExamples() { return fewShotExamples; }
        public double getCurrentAccuracy() { return currentAccuracy; }
        public double getImprovement() { return improvement; }
        
        @Override
        public String toString() {
            return String.format(
                "LearningStatistics{corrections=%d, patterns=%d, examples=%d, accuracy=%.1f%%, improvement=+%.1f%%}",
                totalCorrections, learnedPatterns, fewShotExamples, currentAccuracy, improvement
            );
        }
    }
}

