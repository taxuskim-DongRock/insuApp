package com.example.insu.service;

import com.example.insu.dto.UwCodeMappingData;
import com.example.insu.dto.ValidationResult;
import com.example.insu.util.PdfParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * UW_CODE_MAPPING 기반 검증 파싱 전략
 */
@Slf4j
@Service
public class UwMappingValidatedParsingStrategy implements ParsingStrategy {
    
    @Autowired
    private UwCodeMappingValidationService uwMappingValidationService;
    
    @Autowired
    private OllamaService ollamaService;
    
    @Override
    public Map<String, String> parse(File pdfFile, String insuCd) {
        try {
            log.info("=== UW_CODE_MAPPING 검증 파싱 시작: {} ===", insuCd);
            
            // 1. LLM 파싱 실행
            String prompt = buildPrompt(pdfFile, insuCd);
            Map<String, String> llmResult = ollamaService.parseWithLlama(prompt, insuCd).get();
            
            log.debug("LLM 파싱 결과: {}", llmResult);
            
            // 2. UW_CODE_MAPPING 검증
            ValidationResult validation = uwMappingValidationService.validateWithUwMapping(insuCd, llmResult);
            
            if ("VALID".equals(validation.getStatus()) && validation.getConfidence() >= 80) {
                log.info("UW_CODE_MAPPING 검증 통과: {} (신뢰도: {}%)", insuCd, validation.getConfidence());
                llmResult.put("validationSource", "UW_CODE_MAPPING");
                llmResult.put("validationConfidence", String.valueOf(validation.getConfidence()));
                return llmResult;
            } else {
                log.warn("UW_CODE_MAPPING 검증 실패: {} (신뢰도: {}%)", insuCd, validation.getConfidence());
                log.warn("불일치 항목: {}", validation.getMismatchedTerms());
                
                // 검증 실패 시 UW_CODE_MAPPING 데이터로 보정
                return correctWithUwMapping(insuCd, llmResult, validation.getMappingData());
            }
            
        } catch (Exception e) {
            log.error("UW_CODE_MAPPING 검증 파싱 오류: {}", e.getMessage(), e);
            
            // 오류 시 UW_CODE_MAPPING 데이터 직접 사용
            return getUwMappingDataDirectly(insuCd);
        }
    }
    
    /**
     * 프롬프트 생성
     */
    private String buildPrompt(File pdfFile, String insuCd) {
    try {
      String text = PdfParser.readAllText(pdfFile);
      return String.format("""
                다음 보험 상품 문서에서 보험기간, 납입기간, 가입나이, 갱신여부 정보를 JSON 형식으로 추출해줘.
                상품코드: %s
                문서 내용:
                %s
                
                출력 형식:
                {
                    "insuTerm": "보험기간", 
                    "payTerm": "납입기간", 
                    "ageRange": "가입나이",
                    "renew": "갱신여부"
                }
                
                중요:
                - 보험기간: 종신, 90세만기, 100세만기 등 정확히 추출
                - 납입기간: 10년납, 15년납, 20년납, 30년납, 전기납 등 정확히 추출
                - 가입나이: 남:15~80, 여:15~80 형태로 추출
                - 갱신여부: 갱신형 또는 비갱신형
                """, insuCd, text);
        } catch (Exception e) {
            log.error("프롬프트 생성 오류: {}", e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * UW_CODE_MAPPING 데이터로 파싱 결과 보정
     */
    private Map<String, String> correctWithUwMapping(String insuCd, Map<String, String> llmResult, 
                                                    List<UwCodeMappingData> mappingData) {
        if (mappingData == null || mappingData.isEmpty()) {
            log.warn("매핑 데이터 없음, 빈 결과 반환: {}", insuCd);
            return getEmptyResult();
        }
        
        log.info("UW_CODE_MAPPING 데이터로 보정 실행: {}", insuCd);
        
        // UW_CODE_MAPPING에서 해당 보험코드의 모든 조합 가져오기
        Set<String> validInsuTerms = mappingData.stream()
            .map(UwCodeMappingData::getPeriodLabel)
            .collect(Collectors.toSet());
        
        Set<String> validPayTerms = mappingData.stream()
            .map(UwCodeMappingData::getPayTerm)
            .collect(Collectors.toSet());
        
        // 보정된 결과 생성
        Map<String, String> correctedResult = new LinkedHashMap<>();
        correctedResult.put("insuTerm", String.join(", ", validInsuTerms));
        correctedResult.put("payTerm", String.join(", ", validPayTerms));
        correctedResult.put("ageRange", buildAgeRangeString(mappingData));
        correctedResult.put("renew", determineRenewType(insuCd));
        correctedResult.put("specialNotes", "UW_CODE_MAPPING 기반 보정 데이터");
        correctedResult.put("validationSource", "UW_CODE_MAPPING_CORRECTED");
        correctedResult.put("originalLlmResult", llmResult.toString());
        
        log.info("UW_CODE_MAPPING 기반 보정 완료: {}", insuCd);
        return correctedResult;
    }
    
    /**
     * 가입나이 문자열 생성
     */
    private String buildAgeRangeString(List<UwCodeMappingData> mappingData) {
        StringBuilder ageRangeBuilder = new StringBuilder();
        
        // 보험기간별로 그룹화
        Map<String, List<UwCodeMappingData>> groupedByPeriod = mappingData.stream()
            .collect(Collectors.groupingBy(UwCodeMappingData::getPeriodLabel));
        
        for (Map.Entry<String, List<UwCodeMappingData>> entry : groupedByPeriod.entrySet()) {
            String period = entry.getKey();
            List<UwCodeMappingData> periodData = entry.getValue();
            
            if (ageRangeBuilder.length() > 0) {
                ageRangeBuilder.append("; ");
            }
            
            ageRangeBuilder.append(period).append(": ");
            
            // 납입기간별 가입나이 추가
            for (int i = 0; i < periodData.size(); i++) {
                UwCodeMappingData data = periodData.get(i);
                
                if (i > 0) {
                    ageRangeBuilder.append(", ");
                }
                
                ageRangeBuilder.append(String.format("%s(남:%s,여:%s)",
                    data.getPayTerm(),
                    data.getEntryAgeM(),
                    data.getEntryAgeF()));
            }
        }
        
        return ageRangeBuilder.toString();
    }
    
    /**
     * 갱신여부 판단
     */
    private String determineRenewType(String insuCd) {
        // 특약별 갱신여부 판단 로직
        if (insuCd.startsWith("8")) {
            return "갱신형"; // 8로 시작하는 특약들은 대부분 갱신형
        }
        return "비갱신형";
    }
    
    /**
     * UW_CODE_MAPPING 데이터 직접 조회
     */
    private Map<String, String> getUwMappingDataDirectly(String insuCd) {
        List<UwCodeMappingData> mappingData = uwMappingValidationService.getValidationDataByCode(insuCd);
        
        if (mappingData.isEmpty()) {
            log.warn("UW_CODE_MAPPING에 데이터 없음: {}", insuCd);
            return getEmptyResult();
        }
        
        log.info("UW_CODE_MAPPING 데이터 직접 사용: {}", insuCd);
        return correctWithUwMapping(insuCd, new HashMap<>(), mappingData);
    }
    
    /**
     * 빈 결과 반환
     */
    private Map<String, String> getEmptyResult() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("insuTerm", "—");
        result.put("payTerm", "—");
        result.put("ageRange", "—");
        result.put("renew", "—");
        result.put("specialNotes", "UW_CODE_MAPPING 데이터 없음");
        result.put("validationSource", "EMPTY");
        return result;
    }
    
    @Override
    public String getStrategyName() {
        return "UW_CODE_MAPPING 검증";
    }
    
    @Override
    public int getPriority() {
        return 2; // 두 번째 우선순위 (Python OCR 다음)
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // UW_CODE_MAPPING 테이블 접근 가능 여부 확인
            uwMappingValidationService.getValidationDataByCode("TEST");
            return true;
        } catch (Exception e) {
            log.warn("UW_CODE_MAPPING 검증 전략 사용 불가: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public int evaluateConfidence(Map<String, String> result) {
        if (result == null || result.isEmpty()) {
            return 0;
        }
        
        int score = 0;
        
        if (isValidField(result.get("insuTerm"))) score++;
        if (isValidField(result.get("payTerm"))) score++;
        if (isValidField(result.get("ageRange"))) score++;
        if (isValidField(result.get("renew"))) score++;
        
        // UW_CODE_MAPPING 기반 결과는 높은 신뢰도
        if ("UW_CODE_MAPPING".equals(result.get("validationSource")) ||
            "UW_CODE_MAPPING_CORRECTED".equals(result.get("validationSource"))) {
            return Math.max(score * 25, 90); // 최소 90% 신뢰도
        }
        
        return score * 25;
    }
    
    private boolean isValidField(String value) {
        return value != null && !value.isEmpty() && !value.equals("—");
    }
}
