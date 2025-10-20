package com.example.insu.service;

import com.example.insu.util.PdfParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 파싱 실패 시 다단계 폴백 전략 서비스
 * 
 * 여러 파싱 전략을 순차적으로 시도하여
 * 최소한의 정보라도 추출할 수 있도록 보장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParsingFallbackService {
    
    private final ImprovedHybridParsingService hybridParsingService;
    
    @Autowired(required = false)
    private UwMappingValidatedParsingStrategy uwMappingStrategy;
    
    @Autowired(required = false)
    private FewShotLlmParsingStrategy fewShotLlmStrategy;
    
    @Autowired(required = false)
    private LlmParsingStrategy basicLlmStrategy;
    
    /**
     * 다단계 폴백을 적용한 파싱
     */
    public Map<String, String> parseWithFallback(File pdfFile, String insuCd) {
        log.info("=== 폴백 파싱 시작: {} ===", insuCd);
        
        List<Exception> allErrors = new ArrayList<>();
        
        // 1순위: 하이브리드 파싱 (캐시 포함)
        try {
            log.info("시도 1: 하이브리드 파싱");
                Map<String, String> result = hybridParsingService.parseWithMultipleStrategies(pdfFile, insuCd);
            
            if (isValidResult(result)) {
                log.info("✅ 하이브리드 파싱 성공: {}", insuCd);
                return result;
            } else {
                log.warn("하이브리드 파싱 결과 불완전: {}", result);
            }
        } catch (Exception e) {
            log.warn("하이브리드 파싱 실패: {}", e.getMessage());
            allErrors.add(e);
        }
        
        // 2순위: UW_CODE_MAPPING 직접 조회
        if (uwMappingStrategy != null) {
            try {
                log.info("시도 2: UW_CODE_MAPPING 직접 파싱");
                Map<String, String> result = uwMappingStrategy.parse(pdfFile, insuCd);
                
                if (isValidResult(result)) {
                    log.info("✅ UW_CODE_MAPPING 파싱 성공: {}", insuCd);
                    return result;
                }
            } catch (Exception e) {
                log.warn("UW_CODE_MAPPING 파싱 실패: {}", e.getMessage());
                allErrors.add(e);
            }
        }
        
        // 3순위: Few-Shot LLM
        if (fewShotLlmStrategy != null && fewShotLlmStrategy.isAvailable()) {
            try {
                log.info("시도 3: Few-Shot LLM 파싱");
                Map<String, String> result = fewShotLlmStrategy.parse(pdfFile, insuCd);
                
                if (isValidResult(result)) {
                    log.info("✅ Few-Shot LLM 파싱 성공: {}", insuCd);
                    return result;
                }
            } catch (Exception e) {
                log.warn("Few-Shot LLM 파싱 실패: {}", e.getMessage());
                allErrors.add(e);
            }
        }
        
        // 4순위: 기본 LLM
        if (basicLlmStrategy != null && basicLlmStrategy.isAvailable()) {
            try {
                log.info("시도 4: 기본 LLM 파싱");
                Map<String, String> result = basicLlmStrategy.parse(pdfFile, insuCd);
                
                if (isValidResult(result)) {
                    log.info("✅ 기본 LLM 파싱 성공: {}", insuCd);
                    return result;
                }
            } catch (Exception e) {
                log.warn("기본 LLM 파싱 실패: {}", e.getMessage());
                allErrors.add(e);
            }
        }
        
        // 5순위: 부분 복구
        log.warn("모든 파싱 전략 실패, 부분 복구 시도: {}", insuCd);
        return partialRecovery(pdfFile, insuCd, allErrors);
    }
    
    /**
     * 부분 복구 - 최소한의 정보라도 추출
     */
    private Map<String, String> partialRecovery(File pdfFile, String insuCd, 
                                                 List<Exception> errors) {
        Map<String, String> result = new LinkedHashMap<>();
        
        // 기본값 설정
        result.put("insuTerm", "—");
        result.put("payTerm", "—");
        result.put("ageRange", "—");
        result.put("renew", "—");
        
        try {
            log.info("부분 복구 시작: PDF 텍스트 직접 분석");
            String text = PdfParser.readAllText(pdfFile);
            
            // 1. 보험기간 추출 시도
            if (text.contains("종신")) {
                result.put("insuTerm", "종신");
                log.info("부분 복구: 보험기간 = 종신");
            } else if (text.matches(".*\\d+세만기.*")) {
                // 세만기 패턴 찾기
                java.util.regex.Pattern pattern = 
                    java.util.regex.Pattern.compile("(\\d+)세만기");
                java.util.regex.Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    result.put("insuTerm", matcher.group(1) + "세만기");
                    log.info("부분 복구: 보험기간 = {}세만기", matcher.group(1));
                }
            }
            
            // 2. 납입기간 추출 시도
            if (text.contains("전기납")) {
                result.put("payTerm", "전기납");
                log.info("부분 복구: 납입기간 = 전기납");
            } else if (text.contains("일시납")) {
                result.put("payTerm", "일시납");
                log.info("부분 복구: 납입기간 = 일시납");
            } else {
                // 년납 패턴 찾기
                java.util.regex.Pattern pattern = 
                    java.util.regex.Pattern.compile("(\\d+)년납");
                java.util.regex.Matcher matcher = pattern.matcher(text);
                List<String> payTerms = new ArrayList<>();
                while (matcher.find()) {
                    payTerms.add(matcher.group(1) + "년납");
                }
                if (!payTerms.isEmpty()) {
                    result.put("payTerm", String.join(", ", payTerms));
                    log.info("부분 복구: 납입기간 = {}", payTerms);
                }
            }
            
            // 3. 갱신여부 추출 시도
            if (text.contains("갱신형")) {
                result.put("renew", "갱신형");
                log.info("부분 복구: 갱신여부 = 갱신형");
            } else if (text.contains("비갱신")) {
                result.put("renew", "비갱신형");
                log.info("부분 복구: 갱신여부 = 비갱신형");
            }
            
            // 4. 가입나이 추출 시도 (간단한 패턴만)
            java.util.regex.Pattern agePattern = 
                java.util.regex.Pattern.compile("만?(\\d+)세\\s*~\\s*(\\d+)세");
            java.util.regex.Matcher ageMatcher = agePattern.matcher(text);
            if (ageMatcher.find()) {
                String ageRange = String.format("만%s세 ~ %s세", 
                                               ageMatcher.group(1), 
                                               ageMatcher.group(2));
                result.put("ageRange", ageRange);
                log.info("부분 복구: 가입나이 = {}", ageRange);
            }
            
        } catch (Exception e) {
            log.error("부분 복구도 실패: {}", e.getMessage(), e);
        }
        
        // 에러 정보 추가
        String errorSummary = errors.stream()
            .map(Throwable::getMessage)
            .collect(Collectors.joining("; "));
        
        result.put("specialNotes", "⚠️ 파싱 부분 실패 - 수동 확인 필요");
        result.put("errors", errorSummary);
        result.put("validationSource", "PARTIAL_RECOVERY");
        
        log.warn("부분 복구 완료: {} - {}", insuCd, result);
        
        return result;
    }
    
    /**
     * 파싱 결과 유효성 검증
     */
    private boolean isValidResult(Map<String, String> result) {
        if (result == null || result.isEmpty()) {
            return false;
        }
        
        // 최소 2개 필드가 유효한 값을 가져야 함
        int validFields = 0;
        
        for (String field : Arrays.asList("insuTerm", "payTerm", "ageRange", "renew")) {
            String value = result.get(field);
            if (value != null && !value.isEmpty() && !value.equals("—")) {
                validFields++;
            }
        }
        
        return validFields >= 2;
    }
    
    /**
     * 폴백 통계 조회
     */
    public Map<String, Object> getFallbackStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("fallbackEnabled", true);
        stats.put("strategies", Arrays.asList(
            "Hybrid (Cache)",
            "UW_CODE_MAPPING",
            "Few-Shot LLM",
            "Basic LLM",
            "Partial Recovery"
        ));
        return stats;
    }
}

