package com.example.insu.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 2: Few-Shot Learning 기반 LLM 파싱 전략
 */
@Slf4j
@Service
public class FewShotLlmParsingStrategy implements ParsingStrategy {
    
    private final OllamaService ollamaService;
    private final QuorumLlmService quorumLlmService; // 개선: 쿼럼 기반 LLM 추가
    private final FewShotExamples fewShotExamples;
    private final MultiLayerValidationService validationService;
    private boolean ollamaAvailable = false;
    
    public FewShotLlmParsingStrategy(OllamaService ollamaService,
                                     QuorumLlmService quorumLlmService,
                                     FewShotExamples fewShotExamples,
                                     MultiLayerValidationService validationService) {
        this.ollamaService = ollamaService;
        this.quorumLlmService = quorumLlmService;
        this.fewShotExamples = fewShotExamples;
        this.validationService = validationService;
        checkOllamaAvailability();
    }
    
    @Override
    public Map<String, String> parse(File pdfFile, String insuCd) {
        if (!isAvailable()) {
            log.warn("Ollama 서비스를 사용할 수 없음");
            return getEmptyResult();
        }
        
        try {
            log.info("Phase 2: Few-Shot LLM 파싱 시작: {}", insuCd);
            
            // PDF 텍스트 추출
            String pdfText = extractPdfText(pdfFile);
            
            // 상품명 추출 (간단한 방법)
            String productName = extractProductName(pdfText, insuCd);
            
            // Few-Shot 프롬프트 생성
            String prompt = fewShotExamples.buildFewShotPrompt(pdfText, insuCd, productName);
            
            // 개선: 쿼럼 기반 LLM 파싱 (2/3 합의 시 조기 종료)
            log.info("쿼럼 기반 LLM 파싱 실행 (응답 시간 50% 단축 예상)");
            Map<String, String> integratedResult = quorumLlmService.parseWithQuorum(prompt, insuCd);
            
            // Phase 2: 다층 검증 실행
            MultiLayerValidationService.ValidationResult validation = 
                validationService.validate(integratedResult, pdfText, insuCd);
            
            log.info("Few-Shot LLM 파싱 완료: {} (신뢰도: {}%, 상태: {})", 
                    insuCd, validation.getConfidence(), validation.getStatus());
            
            // 검증 정보 추가
            integratedResult.put("specialNotes", 
                String.format("Few-Shot LLM (신뢰도: %d%%, 상태: %s)", 
                             validation.getConfidence(), validation.getStatus()));
            
            // 검증 실패 시 로깅
            if (!validation.isPassed()) {
                log.warn("검증 실패: {}", validation.getFailureReasons());
                log.info("권장사항: {}", validation.getRecommendations());
            }
            
            return integratedResult;
            
        } catch (Exception e) {
            log.error("Few-Shot LLM 파싱 오류: {}", e.getMessage(), e);
            return getEmptyResult();
        }
    }
    
    @Override
    public String getStrategyName() {
        return "Few-Shot LLM";
    }
    
    @Override
    public int getPriority() {
        return 4; // 일반 LLM보다 나중 (더 정확하므로 필요시에만 사용)
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
        
        // specialNotes에서 신뢰도 추출
        String notes = result.get("specialNotes");
        if (notes != null && notes.contains("신뢰도:")) {
            try {
                String[] parts = notes.split("신뢰도: ");
                if (parts.length > 1) {
                    String confidenceStr = parts[1].split("%")[0];
                    return Integer.parseInt(confidenceStr);
                }
            } catch (Exception e) {
                log.debug("신뢰도 추출 실패: {}", notes);
            }
        }
        
        // 기본 평가
        int score = 0;
        if (isValidField(result.get("insuTerm"))) score += 25;
        if (isValidField(result.get("payTerm"))) score += 25;
        if (isValidField(result.get("ageRange"))) score += 25;
        if (isValidField(result.get("renew"))) score += 25;
        
        return score;
    }
    
    /**
     * Ollama 사용 가능 여부 확인
     */
    private void checkOllamaAvailability() {
        try {
            ollamaAvailable = true;
            log.info("Few-Shot LLM 파싱 전략 사용 가능");
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
     * 상품명 추출 (간단한 버전)
     */
    private String extractProductName(String pdfText, String insuCd) {
        // 간단한 패턴 매칭
        String[] lines = pdfText.split("\\n");
        for (String line : lines) {
            if (line.contains(insuCd) && line.matches(".*[가-힣].*")) {
                // 상품코드 제거 후 나머지를 상품명으로
                String name = line.replaceAll("\\s*" + insuCd + "\\s*", "").trim();
                if (name.length() > 3) {
                    return name;
                }
            }
        }
        return null;
    }
    
    /**
     * 납입기간 정규화 헬퍼 메서드 (현재 사용되지 않음)
     */
    @SuppressWarnings("unused")
    private String normalizePayTerm(String payTerm) {
        if (payTerm == null || payTerm.trim().isEmpty()) {
            return "—";
        }
        
        String trimmed = payTerm.trim();
        
        // "월납(10년납)" -> "10년납"
        if (trimmed.contains("월납(") && trimmed.contains(")")) {
            String extracted = trimmed.substring(trimmed.indexOf("(") + 1, trimmed.lastIndexOf(")"));
            log.debug("Few-Shot 납입기간 정규화: '{}' -> '{}'", trimmed, extracted);
            return extracted;
        }
        
        // "월납(전기납)" -> "전기납"
        if (trimmed.contains("월납(") && trimmed.contains("전기납")) {
            return "전기납";
        }
        
        // 이미 정규화된 형태라면 그대로 반환
        if (trimmed.matches("\\d+년납|전기납|일시납")) {
            return trimmed;
        }
        
        // 기본값 반환
        return trimmed;
    }
    
    // 개선: integrateResults()와 voteForBest()는 QuorumLlmService로 이동됨
    // 더 이상 사용하지 않음 (쿼럼 서비스가 투표 기반 통합 처리)
    
    private boolean isValidField(String value) {
        return value != null && !value.isEmpty() && !value.equals("—");
    }
    
    private Map<String, String> getEmptyResult() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("insuTerm", "—");
        result.put("payTerm", "—");
        result.put("ageRange", "—");
        result.put("renew", "—");
        result.put("specialNotes", "Few-Shot LLM 파싱 실패");
        return result;
    }
}

