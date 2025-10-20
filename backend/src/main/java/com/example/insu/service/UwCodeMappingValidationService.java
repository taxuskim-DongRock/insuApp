package com.example.insu.service;

import com.example.insu.dto.UwCodeMappingData;
import com.example.insu.dto.ValidationResult;
import com.example.insu.mapper.UwCodeMappingMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * UW_CODE_MAPPING 테이블 기반 검증 서비스
 */
@Slf4j
@Service
public class UwCodeMappingValidationService {
    
    @Autowired
    private UwCodeMappingMapper uwCodeMappingMapper;
    
    /**
     * UW_CODE_MAPPING 테이블에서 보험코드 기준 검증 데이터 조회
     */
    public List<UwCodeMappingData> getValidationDataByCode(String insuCd) {
        try {
            List<UwCodeMappingData> mappingData = uwCodeMappingMapper.selectByCode(insuCd);
            log.debug("UW_CODE_MAPPING 데이터 조회 완료: {} ({} 건)", insuCd, mappingData.size());
            return mappingData;
        } catch (Exception e) {
            log.error("UW_CODE_MAPPING 데이터 조회 오류: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 주계약 코드 기준 검증 데이터 조회
     */
    public List<UwCodeMappingData> getValidationDataByMainCode(String mainCode) {
        try {
            List<UwCodeMappingData> mappingData = uwCodeMappingMapper.selectByMainCode(mainCode);
            log.debug("UW_CODE_MAPPING 주계약 데이터 조회 완료: {} ({} 건)", mainCode, mappingData.size());
            return mappingData;
        } catch (Exception e) {
            log.error("UW_CODE_MAPPING 주계약 데이터 조회 오류: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * LLM 파싱 결과와 UW_CODE_MAPPING 데이터 비교 검증
     */
    public ValidationResult validateWithUwMapping(String insuCd, Map<String, String> parsedResult) {
        try {
            List<UwCodeMappingData> mappingData = getValidationDataByCode(insuCd);
            
            if (mappingData.isEmpty()) {
                return ValidationResult.builder()
                    .insuCd(insuCd)
                    .status("NO_MAPPING_DATA")
                    .confidence(0)
                    .message("UW_CODE_MAPPING에 데이터 없음")
                    .build();
            }
            
            return validateAgainstMappingData(insuCd, parsedResult, mappingData);
            
        } catch (Exception e) {
            log.error("UW_CODE_MAPPING 검증 오류: {}", e.getMessage(), e);
            return ValidationResult.builder()
                .insuCd(insuCd)
                .status("ERROR")
                .confidence(0)
                .message("검증 중 오류 발생: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * 매핑 데이터와 파싱 결과 비교
     */
    private ValidationResult validateAgainstMappingData(String insuCd, Map<String, String> parsed, 
                                                      List<UwCodeMappingData> mappingData) {
        List<String> matchedTerms = new ArrayList<>();
        List<String> mismatchedTerms = new ArrayList<>();
        
        // 파싱된 보험기간과 납입기간 조합 검증
        String parsedInsuTerm = parsed.get("insuTerm");
        String parsedPayTerm = parsed.get("payTerm");
        
        log.debug("파싱 결과 검증 - 보험코드: {}, 보험기간: {}, 납입기간: {}", 
                  insuCd, parsedInsuTerm, parsedPayTerm);
        
        // UW_CODE_MAPPING에서 해당 조합 찾기
        boolean foundMatch = false;
        int matchCount = 0;
        int totalCombinations = mappingData.size();
        
        for (UwCodeMappingData mapping : mappingData) {
            boolean termMatch = isTermMatch(parsedInsuTerm, mapping.getPeriodLabel());
            boolean payTermMatch = isPayTermMatch(parsedPayTerm, mapping.getPayTerm());
            
            if (termMatch && payTermMatch) {
                matchedTerms.add(String.format("보험기간: %s, 납입기간: %s", 
                    mapping.getPeriodLabel(), mapping.getPayTerm()));
                matchCount++;
                foundMatch = true;
            }
        }
        
        int confidence = totalCombinations > 0 ? (matchCount * 100) / totalCombinations : 0;
        
        if (foundMatch && confidence >= 70) {
            log.info("UW_CODE_MAPPING 검증 통과: {} (일치: {}/{}, 신뢰도: {}%)", 
                     insuCd, matchCount, totalCombinations, confidence);
            
            return ValidationResult.builder()
                .insuCd(insuCd)
                .status("VALID")
                .confidence(confidence)
                .score(matchCount)
                .total(totalCombinations)
                .matchedTerms(matchedTerms)
                .mappingData(mappingData)
                .parsedData(parsed)
                .build();
        } else {
            mismatchedTerms.add(String.format("파싱 결과 - 보험기간: %s, 납입기간: %s", 
                parsedInsuTerm, parsedPayTerm));
            mismatchedTerms.add(String.format("UW_CODE_MAPPING에서 %d/%d 조합만 일치", matchCount, totalCombinations));
            
            log.warn("UW_CODE_MAPPING 검증 실패: {} (일치: {}/{}, 신뢰도: {}%)", 
                     insuCd, matchCount, totalCombinations, confidence);
            
            return ValidationResult.builder()
                .insuCd(insuCd)
                .status("INVALID")
                .confidence(confidence)
                .score(matchCount)
                .total(totalCombinations)
                .mismatchedTerms(mismatchedTerms)
                .mappingData(mappingData)
                .parsedData(parsed)
                .build();
        }
    }
    
    /**
     * 보험기간 매칭 검사
     */
    private boolean isTermMatch(String parsed, String mapping) {
        if (parsed == null || mapping == null) return false;
        
        // 정규화 후 비교
        String normalizedParsed = normalizeTerm(parsed);
        String normalizedMapping = normalizeTerm(mapping);
        
        // 완전 일치 또는 포함 관계 확인
        return normalizedParsed.equals(normalizedMapping) ||
               normalizedParsed.contains(normalizedMapping) || 
               normalizedMapping.contains(normalizedParsed);
    }
    
    /**
     * 납입기간 매칭 검사
     */
    private boolean isPayTermMatch(String parsed, String mapping) {
        if (parsed == null || mapping == null) return false;
        
        // 파싱된 납입기간에 매핑 데이터의 납입기간이 포함되어 있는지 확인
        String normalizedParsed = normalizePayTerm(parsed);
        String normalizedMapping = normalizePayTerm(mapping);
        
        // 완전 일치 또는 포함 관계 확인
        return normalizedParsed.equals(normalizedMapping) ||
               normalizedParsed.contains(normalizedMapping);
    }
    
    /**
     * 보험기간 정규화
     */
    private String normalizeTerm(String term) {
        if (term == null) return "";
        
        return term.trim()
            .replaceAll("\\s+", " ")
            .replaceAll("종신보험", "종신")
            .replaceAll("\\d+세만기", "$0")
            .toLowerCase();
    }
    
    /**
     * 납입기간 정규화
     */
    private String normalizePayTerm(String payTerm) {
        if (payTerm == null) return "";
        
        return payTerm.trim()
            .replaceAll("\\s+", " ")
            .replaceAll("\\d+년\\s*납", "$0")
            .replaceAll("전기납입", "전기납")
            .toLowerCase();
    }
    
    /**
     * 가입나이 정규화
     */
    private String normalizeAgeRange(String ageRange) {
        if (ageRange == null) return "";
        
        return ageRange.trim()
            .replaceAll("\\s+", " ")
            .replaceAll("만\\s*", "")
            .replaceAll("세", "")
            .replaceAll("남\\s*:", "남:")
            .replaceAll("여\\s*:", "여:")
            .replaceAll(",\\s*", ", ")
            .toLowerCase();
    }
}







