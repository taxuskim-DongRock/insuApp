package com.example.insu.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Python OCR 기반 파싱 전략 (기존 방식)
 */
@Slf4j
@Service
public class PythonOcrParsingStrategy implements ParsingStrategy {
    
    private final PythonPdfService pythonPdfService;
    
    public PythonOcrParsingStrategy(PythonPdfService pythonPdfService) {
        this.pythonPdfService = pythonPdfService;
    }
    
    @Override
    public Map<String, String> parse(File pdfFile, String insuCd) {
        try {
            log.info("Python OCR 파싱 시작: {}", insuCd);
            
            // Python 서비스로 파싱
            Map<String, Object> result = pythonPdfService.extractProductInfo(
                pdfFile.getAbsolutePath(), 
                insuCd
            );
            
            // 오류 확인
            if (result.containsKey("error")) {
                log.warn("Python OCR 파싱 실패: {}", result.get("error"));
                return getEmptyResult();
            }
            
            // terms 추출
            if (!result.containsKey("terms")) {
                log.warn("Python OCR 파싱 결과에 terms 없음");
                return getEmptyResult();
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> terms = (Map<String, Object>) result.get("terms");
            
            Map<String, String> resultMap = new LinkedHashMap<>();
            resultMap.put("insuTerm", (String) terms.getOrDefault("insuTerm", "—"));
            resultMap.put("payTerm", (String) terms.getOrDefault("payTerm", "—"));
            resultMap.put("ageRange", (String) terms.getOrDefault("ageRange", "—"));
            resultMap.put("renew", (String) terms.getOrDefault("renew", "—"));
            resultMap.put("specialNotes", (String) terms.getOrDefault("specialNotes", "Python OCR 파싱"));
            
            log.info("Python OCR 파싱 완료: {} (신뢰도: {})", insuCd, evaluateConfidence(resultMap));
            return resultMap;
            
        } catch (Exception e) {
            log.error("Python OCR 파싱 오류: {}", e.getMessage(), e);
            return getEmptyResult();
        }
    }
    
    @Override
    public String getStrategyName() {
        return "Python OCR";
    }
    
    @Override
    public int getPriority() {
        return 1; // 가장 먼저 시도
    }
    
    @Override
    public boolean isAvailable() {
        // Python 서비스가 사용 가능한지 확인
        return pythonPdfService != null;
    }
    
    @Override
    public int evaluateConfidence(Map<String, String> result) {
        if (result == null || result.isEmpty()) {
            return 0;
        }
        
        int score = 0;
        int totalFields = 4;
        
        // 각 필드가 유효한지 확인
        if (isValidField(result.get("insuTerm"))) score += 25;
        if (isValidField(result.get("payTerm"))) score += 25;
        if (isValidField(result.get("ageRange"))) score += 25;
        if (isValidField(result.get("renew"))) score += 25;
        
        return score;
    }
    
    private boolean isValidField(String value) {
        return value != null && !value.isEmpty() && !value.equals("—");
    }
    
    private Map<String, String> getEmptyResult() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("insuTerm", "—");
        result.put("payTerm", "—");
        result.put("ageRange", "—");
        result.put("renew", "—");
        result.put("specialNotes", "Python OCR 파싱 실패");
        return result;
    }
}


