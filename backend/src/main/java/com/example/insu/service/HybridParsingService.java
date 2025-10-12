package com.example.insu.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.example.insu.dto.UwCodeMappingData;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * 하이브리드 파싱 서비스: 정규식 파싱 + LLM 파싱 결합
 */
@Slf4j
@Service
public class HybridParsingService {
    
    private final PdfService pdfService;
    private final AdvancedLlmParsingStrategy llmParsingStrategy;
    private final ProductService productService;
    private final AdvancedMappingService advancedMappingService;
    
    public HybridParsingService(PdfService pdfService, 
                               AdvancedLlmParsingStrategy llmParsingStrategy,
                               ProductService productService,
                               AdvancedMappingService advancedMappingService) {
        this.pdfService = pdfService;
        this.llmParsingStrategy = llmParsingStrategy;
        this.productService = productService;
        this.advancedMappingService = advancedMappingService;
    }
    
    /**
     * 하이브리드 방식으로 문서 파싱
     */
    public List<UwCodeMappingData> parseDocument(File pdfFile, String docId) {
        log.info("===== 하이브리드 파싱 시작: {} =====", docId);
        
        List<UwCodeMappingData> result = new ArrayList<>();
        
        try {
            // 1단계: 기존 정규식 파싱 (빠른 처리)
            log.info("1단계: 정규식 파싱 시작");
            Map<String, String> regexResults = pdfService.parsePdfCodes(pdfFile);
            log.info("정규식 파싱 결과: {} 개 항목", regexResults.size());
            
            // 2단계: LLM 파싱 (정확한 처리)
            log.info("2단계: LLM 파싱 시작");
            Map<String, String> llmResults = new HashMap<>();
            if (llmParsingStrategy.isAvailable()) {
                llmResults = llmParsingStrategy.parse(pdfFile, docId);
                log.info("LLM 파싱 결과: {} 개 항목", llmResults.size());
            } else {
                log.warn("LLM 파싱 사용 불가, 정규식 결과만 사용");
            }
            
            // 3단계: 결과 병합 및 검증
            log.info("3단계: 결과 병합 및 검증");
            Map<String, String> mergedResults = mergeResults(regexResults, llmResults);
            log.info("병합된 결과: {} 개 항목", mergedResults.size());
            
            // 4단계: 고급 매핑 규칙 적용
            log.info("4단계: 고급 매핑 규칙 적용");
            result = advancedMappingService.processAdvancedMapping(mergedResults, docId);
            log.info("최종 결과: {} 개 UW_CODE_MAPPING 항목", result.size());
            
        } catch (Exception e) {
            log.error("하이브리드 파싱 실패: {}", e.getMessage(), e);
        }
        
        log.info("===== 하이브리드 파싱 완료 =====");
        return result;
    }
    
    /**
     * 정규식과 LLM 결과 병합
     */
    private Map<String, String> mergeResults(Map<String, String> regexResults, 
                                           Map<String, String> llmResults) {
        Map<String, String> merged = new LinkedHashMap<>();
        
        // 1. 정규식 결과를 기본으로 사용
        merged.putAll(regexResults);
        log.debug("정규식 결과 병합: {} 개", regexResults.size());
        
        // 2. LLM 결과로 보완 및 개선
        for (Map.Entry<String, String> entry : llmResults.entrySet()) {
            String code = entry.getKey();
            String llmName = entry.getValue();
            String regexName = regexResults.get(code);
            
            if (regexName == null) {
                // 정규식에서 찾지 못한 코드 추가
                merged.put(code, llmName);
                log.debug("LLM에서 발견된 새로운 코드: {} -> {}", code, llmName);
            } else if (isBetterName(llmName, regexName)) {
                // LLM이 더 정확한 상품명을 제공한 경우 교체
                merged.put(code, llmName);
                log.debug("상품명 개선: {} -> {} (이전: {})", code, llmName, regexName);
            }
        }
        
        return merged;
    }
    
    /**
     * 상품명 품질 비교
     */
    private boolean isBetterName(String llmName, String regexName) {
        if (llmName == null) return false;
        if (regexName == null) return true;
        
        // LLM이 더 긴 이름을 제공하면 더 정확할 가능성이 높음
        if (llmName.length() > regexName.length()) {
            return true;
        }
        
        // LLM이 "(무)" 패턴을 포함하면 더 정확
        if (llmName.contains("(무)") && !regexName.contains("(무)")) {
            return true;
        }
        
        // LLM이 "특약" 키워드를 포함하면 더 정확
        if (llmName.contains("특약") && !regexName.contains("특약")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * UW_CODE_MAPPING 데이터 형식으로 변환
     */
    private List<UwCodeMappingData> convertToUwCodeMappingData(Map<String, String> parsedResults, String docId) {
        List<UwCodeMappingData> result = new ArrayList<>();
        
        for (Map.Entry<String, String> entry : parsedResults.entrySet()) {
            String code = entry.getKey();
            String productName = entry.getValue();
            
            // 기본값 설정
            UwCodeMappingData data = UwCodeMappingData.builder()
                .srcFile(docId)
                .code(code)
                .productName(productName)
                .mainCode(determineMainCode(code, productName))
                .periodLabel("종신") // 기본값
                .periodValue(999)    // 기본값
                .payTerm("10년납, 15년납, 20년납, 30년납") // 기본값
                .entryAgeM("15~80세") // 기본값
                .entryAgeF("15~80세") // 기본값
                .productGroup(determineProductGroup(productName))
                .typeLabel("최초계약")
                .periodKind("E") // 종신
                .classTag(determineClassTag(productName))
                .build();
            
            result.add(data);
            log.debug("UW_CODE_MAPPING 데이터 생성: {} -> {}", code, productName);
        }
        
        return result;
    }
    
    /**
     * 주계약 코드 결정
     */
    private String determineMainCode(String code, String productName) {
        // 주계약인 경우 자기 자신이 주계약
        if (isMainContract(productName)) {
            return code;
        }
        
        // 특약인 경우 기본 주계약 매핑 (실제로는 더 복잡한 로직 필요)
        return "21686"; // 기본값
    }
    
    /**
     * 상품 그룹 결정
     */
    private String determineProductGroup(String productName) {
        if (isMainContract(productName)) {
            return "주계약";
        }
        return "선택특약";
    }
    
    /**
     * 클래스 태그 결정
     */
    private String determineClassTag(String productName) {
        if (isMainContract(productName)) {
            return "MAIN";
        }
        return "A_OPTION";
    }
    
    /**
     * 주계약 여부 판단
     */
    private boolean isMainContract(String productName) {
        if (productName == null) return false;
        
        String lowerName = productName.toLowerCase();
        return lowerName.contains("주계약") || 
               lowerName.contains("최초계약") ||
               (!lowerName.contains("특약") && !lowerName.contains("부가"));
    }
}
