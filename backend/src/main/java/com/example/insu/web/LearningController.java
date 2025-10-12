package com.example.insu.web;

import com.example.insu.service.IncrementalLearningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Phase 3: 점진적 학습 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/learning")
@RequiredArgsConstructor
public class LearningController {
    
    private final IncrementalLearningService learningService;
    
    /**
     * 사용자 수정사항 제출
     */
    @PostMapping("/correction")
    public Map<String, Object> submitCorrection(@RequestBody CorrectionRequest request) {
        log.info("사용자 수정 제출: {}", request.getInsuCd());
        
        try {
            learningService.logCorrection(
                request.getInsuCd(),
                request.getOriginalResult(),
                request.getCorrectedResult(),
                request.getPdfText()
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "수정사항이 학습되었습니다");
            response.put("statistics", learningService.getStatistics());
            
            return response;
            
        } catch (Exception e) {
            log.error("수정사항 처리 오류: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "오류: " + e.getMessage());
            return response;
        }
    }
    
    /**
     * 학습 통계 조회
     */
    @GetMapping("/statistics")
    public Map<String, Object> getStatistics() {
        IncrementalLearningService.LearningStatistics stats = learningService.getStatistics();
        
        Map<String, Object> response = new HashMap<>();
        response.put("totalCorrections", stats.getTotalCorrections());
        response.put("learnedPatterns", stats.getLearnedPatterns());
        response.put("fewShotExamples", stats.getFewShotExamples());
        response.put("currentAccuracy", stats.getCurrentAccuracy());
        response.put("improvement", stats.getImprovement());
        
        return response;
    }
    
    /**
     * 학습 데이터 초기화
     */
    @PostMapping("/reset")
    public Map<String, Object> resetLearning() {
        log.warn("학습 데이터 초기화 요청");
        
        learningService.clearLearningData();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "학습 데이터가 초기화되었습니다");
        
        return response;
    }
    
    /**
     * 수정 요청 DTO
     */
    public static class CorrectionRequest {
        private String insuCd;
        private Map<String, String> originalResult;
        private Map<String, String> correctedResult;
        private String pdfText;
        
        // Getters and Setters
        public String getInsuCd() { return insuCd; }
        public void setInsuCd(String insuCd) { this.insuCd = insuCd; }
        
        public Map<String, String> getOriginalResult() { return originalResult; }
        public void setOriginalResult(Map<String, String> originalResult) { this.originalResult = originalResult; }
        
        public Map<String, String> getCorrectedResult() { return correctedResult; }
        public void setCorrectedResult(Map<String, String> correctedResult) { this.correctedResult = correctedResult; }
        
        public String getPdfText() { return pdfText; }
        public void setPdfText(String pdfText) { this.pdfText = pdfText; }
    }
}


