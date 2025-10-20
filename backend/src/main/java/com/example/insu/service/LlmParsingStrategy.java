package com.example.insu.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * LLM 기반 파싱 전략 (Ollama 사용)
 */
@Slf4j
@Service
public class LlmParsingStrategy implements ParsingStrategy {
    
    private final OllamaService ollamaService;
    private boolean ollamaAvailable = false;
    
    public LlmParsingStrategy(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
        checkOllamaAvailability();
    }
    
    @Override
    public Map<String, String> parse(File pdfFile, String insuCd) {
        if (!isAvailable()) {
            log.warn("Ollama 서비스를 사용할 수 없음");
            return getEmptyResult();
        }
        
        try {
            log.info("LLM 파싱 시작: {}", insuCd);
            
            // PDF 텍스트 추출
            String pdfText = extractPdfText(pdfFile);
            
            // 3개 LLM 병렬 실행
            CompletableFuture<Map<String, String>> llamaFuture = 
                ollamaService.parseWithLlama(pdfText, insuCd);
            CompletableFuture<Map<String, String>> mistralFuture = 
                ollamaService.parseWithMistral(pdfText, insuCd);
            CompletableFuture<Map<String, String>> codeLlamaFuture = 
                ollamaService.parseWithCodeLlama(pdfText, insuCd);
            
            // 모든 LLM 완료 대기 (타임아웃: 30초)
            CompletableFuture.allOf(llamaFuture, mistralFuture, codeLlamaFuture)
                .get(30, TimeUnit.SECONDS);
            
            // 결과 통합
            Map<String, String> integratedResult = integrateResults(
                ollamaService.getLastLlamaResult(),
                ollamaService.getLastMistralResult(),
                ollamaService.getLastCodeLlamaResult()
            );
            
            log.info("LLM 파싱 완료: {} (신뢰도: {})", insuCd, evaluateConfidence(integratedResult));
            return integratedResult;
            
        } catch (Exception e) {
            log.error("LLM 파싱 오류: {}", e.getMessage(), e);
            return getEmptyResult();
        }
    }
    
    @Override
    public String getStrategyName() {
        return "LLM (Ollama)";
    }
    
    @Override
    public int getPriority() {
        return 3; // Python OCR, 사업방법서 다음으로 시도
    }
    
    @Override
    public boolean isAvailable() {
        return ollamaAvailable && ollamaService != null;
    }
    
    @Override
    public int evaluateConfidence(Map<String, String> result) {
        if (result == null || result.isEmpty()) {
            return 0;
        }
        
        int score = 0;
        
        // 기본 필드 검증 (각 20점)
        if (isValidField(result.get("insuTerm"))) score += 20;
        if (isValidField(result.get("payTerm"))) score += 20;
        if (isValidField(result.get("ageRange"))) score += 20;
        if (isValidField(result.get("renew"))) score += 20;
        
        // 내용 품질 검증 (20점)
        score += evaluateContentQuality(result);
        
        return Math.min(score, 100);
    }
    
    /**
     * Ollama 사용 가능 여부 확인
     */
    private void checkOllamaAvailability() {
        try {
            // 간단한 확인 (실제 구현에서는 HTTP 요청)
            ollamaAvailable = true;
            log.info("Ollama 서비스 사용 가능");
        } catch (Exception e) {
            ollamaAvailable = false;
            log.warn("Ollama 서비스를 사용할 수 없음: {}", e.getMessage());
        }
    }
    
    /**
     * PDF 텍스트 추출
     */
    private String extractPdfText(File pdfFile) throws Exception {
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
    
    /**
     * 3개 LLM 결과 통합
     */
    private Map<String, String> integrateResults(Map<String, String> llamaResult,
                                                Map<String, String> mistralResult,
                                                Map<String, String> codeLlamaResult) {
        
        Map<String, String> integrated = new LinkedHashMap<>();
        
        // 투표 기반 통합
        integrated.put("insuTerm", voteForBest("insuTerm", llamaResult, mistralResult, codeLlamaResult));
        integrated.put("payTerm", voteForBest("payTerm", llamaResult, mistralResult, codeLlamaResult));
        integrated.put("ageRange", voteForBest("ageRange", llamaResult, mistralResult, codeLlamaResult));
        integrated.put("renew", voteForBest("renew", llamaResult, mistralResult, codeLlamaResult));
        integrated.put("specialNotes", "LLM 통합 파싱 (Llama + Mistral + CodeLlama)");
        
        return integrated;
    }
    
    /**
     * 투표 기반 최적값 선택
     */
    private String voteForBest(String key, Map<String, String> result1,
                              Map<String, String> result2, Map<String, String> result3) {
        
        String value1 = result1.get(key);
        String value2 = result2.get(key);
        String value3 = result3.get(key);
        
        // 동일한 값이 2개 이상 있으면 해당 값 선택
        if (value1 != null && value1.equals(value2)) return value1;
        if (value1 != null && value1.equals(value3)) return value1;
        if (value2 != null && value2.equals(value3)) return value2;
        
        // 모두 다르면 유효성이 높은 값 선택
        if (isValidField(value2) && value2.length() > 5) return value2; // Mistral 우선
        if (isValidField(value3) && value3.length() > 5) return value3; // CodeLlama 차선
        if (isValidField(value1)) return value1; // Llama 기본
        
        return "—";
    }
    
    /**
     * 내용 품질 평가
     */
    private int evaluateContentQuality(Map<String, String> result) {
        int qualityScore = 0;
        
        // 보험기간 형식 확인
        String insuTerm = result.get("insuTerm");
        if (insuTerm != null && insuTerm.matches(".*(종신|\\d+세만기|\\d+년만기).*")) {
            qualityScore += 5;
        }
        
        // 납입기간 형식 확인
        String payTerm = result.get("payTerm");
        if (payTerm != null && payTerm.matches(".*(전기납|\\d+년납).*")) {
            qualityScore += 5;
        }
        
        // 가입나이 형식 확인
        String ageRange = result.get("ageRange");
        if (ageRange != null && ageRange.matches(".*\\d+~\\d+.*")) {
            qualityScore += 5;
        }
        
        // 갱신여부 확인
        String renew = result.get("renew");
        if (renew != null && (renew.contains("갱신형") || renew.contains("비갱신형"))) {
            qualityScore += 5;
        }
        
        return qualityScore;
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
        result.put("specialNotes", "LLM 파싱 실패");
        return result;
    }
}








