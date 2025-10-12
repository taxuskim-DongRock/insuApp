package com.example.insu.service;

import com.example.insu.dto.UwCodeMappingData;
import com.example.insu.dto.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * UW_CODE_MAPPING 기반 하이브리드 파싱 서비스
 */
@Slf4j
@Service
public class UwMappingHybridParsingService {
    
    @Autowired
    private UwCodeMappingValidationService uwMappingValidationService;
    
    @Autowired
    private ImprovedHybridParsingService hybridParsingService;
    
    /**
     * UW_CODE_MAPPING 검증을 통한 하이브리드 파싱
     */
    @Cacheable(value = "uwMappingParsingCache", key = "#root.target.generateCacheKey(#pdfFile, #insuCd)")
    public Map<String, String> parseWithUwMappingValidation(File pdfFile, String insuCd) {
        try {
            log.info("=== UW_CODE_MAPPING 하이브리드 파싱 시작: {} ===", insuCd);
            
            // 1. 기존 하이브리드 파싱 실행
            Map<String, String> parsedResult = hybridParsingService.parseWithMultipleStrategies(pdfFile, insuCd);
            
            // 2. UW_CODE_MAPPING 검증
            ValidationResult validation = uwMappingValidationService.validateWithUwMapping(insuCd, parsedResult);
            
            // 3. 검증 결과에 따른 후처리
            if ("VALID".equals(validation.getStatus()) && validation.getConfidence() >= 80) {
                log.info("UW_CODE_MAPPING 검증 통과: {} (신뢰도: {}%)", insuCd, validation.getConfidence());
                parsedResult.put("validationSource", "UW_CODE_MAPPING");
                parsedResult.put("validationConfidence", String.valueOf(validation.getConfidence()));
                parsedResult.put("validationStatus", validation.getStatus());
                return parsedResult;
            } else {
                log.warn("UW_CODE_MAPPING 검증 실패, 보정 실행: {} (신뢰도: {}%)", 
                         insuCd, validation.getConfidence());
                
                // UW_CODE_MAPPING 데이터로 보정
                Map<String, String> correctedResult = correctWithUwMapping(insuCd, validation.getMappingData());
                correctedResult.put("originalParsedResult", parsedResult.toString());
                correctedResult.put("validationSource", "UW_CODE_MAPPING_CORRECTED");
                correctedResult.put("validationConfidence", String.valueOf(validation.getConfidence()));
                correctedResult.put("validationStatus", validation.getStatus());
                
                return correctedResult;
            }
            
        } catch (Exception e) {
            log.error("UW_CODE_MAPPING 하이브리드 파싱 오류: {}", e.getMessage(), e);
            
            // 오류 시 UW_CODE_MAPPING 데이터 직접 사용
            return getUwMappingDataDirectly(insuCd);
        }
    }
    
    /**
     * UW_CODE_MAPPING 데이터로 직접 보정
     */
    private Map<String, String> correctWithUwMapping(String insuCd, List<UwCodeMappingData> mappingData) {
        if (mappingData == null || mappingData.isEmpty()) {
            log.warn("매핑 데이터 없음, 빈 결과 반환: {}", insuCd);
            return getEmptyResult();
        }
        
        log.info("UW_CODE_MAPPING 데이터로 보정 실행: {}", insuCd);
        
        // UW_CODE_MAPPING 데이터를 파싱 결과 형태로 변환
        return convertUwMappingToParsedResult(insuCd, mappingData);
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
        return convertUwMappingToParsedResult(insuCd, mappingData);
    }
    
    /**
     * UW_CODE_MAPPING 데이터를 파싱 결과 형태로 변환
     */
    private Map<String, String> convertUwMappingToParsedResult(String insuCd, List<UwCodeMappingData> mappingData) {
        Map<String, String> result = new LinkedHashMap<>();
        
        // 보험기간 집합
        Set<String> insuTerms = mappingData.stream()
            .map(UwCodeMappingData::getPeriodLabel)
            .collect(Collectors.toSet());
        
        // 납입기간 집합
        Set<String> payTerms = mappingData.stream()
            .map(UwCodeMappingData::getPayTerm)
            .collect(Collectors.toSet());
        
        result.put("insuTerm", String.join(", ", insuTerms));
        result.put("payTerm", String.join(", ", payTerms));
        result.put("ageRange", buildDetailedAgeRange(mappingData));
        result.put("renew", determineRenewType(mappingData.get(0).getCode()));
        result.put("specialNotes", "UW_CODE_MAPPING 기반 정확한 데이터");
        result.put("mappingDataCount", String.valueOf(mappingData.size()));
        
        return result;
    }
    
    /**
     * 상세 가입나이 문자열 생성
     */
    private String buildDetailedAgeRange(List<UwCodeMappingData> mappingData) {
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
        result.put("validationConfidence", "0");
        result.put("validationStatus", "NO_DATA");
        return result;
    }
    
    /**
     * 캐시 키 생성
     */
    public String generateCacheKey(File pdfFile, String insuCd) {
        try {
            String pdfHash = java.nio.file.Files.readString(pdfFile.toPath()).hashCode() + "";
            String parserVersion = "uw_mapping_v1.0";
            return pdfHash + "_" + insuCd + "_" + parserVersion;
        } catch (Exception e) {
            log.warn("캐시 키 생성 실패: {}", e.getMessage());
            return insuCd + "_uw_mapping_v1.0";
        }
    }
    
    /**
     * UW_CODE_MAPPING 데이터 통계 조회
     */
    public Map<String, Object> getUwMappingStatistics() {
        try {
            // 모든 데이터 조회 (임시로 빈 리스트 사용)
            List<UwCodeMappingData> allData = new ArrayList<>();
            // TODO: UwCodeMappingMapper에 selectAll() 메서드 추가 필요
            
            Map<String, Object> statistics = new HashMap<>();
            statistics.put("totalRecords", allData.size());
            
            // 보험코드별 통계
            Map<String, Long> codeCounts = allData.stream()
                .collect(Collectors.groupingBy(UwCodeMappingData::getCode, Collectors.counting()));
            statistics.put("codeCounts", codeCounts);
            
            // 보험기간별 통계
            Map<String, Long> periodCounts = allData.stream()
                .collect(Collectors.groupingBy(UwCodeMappingData::getPeriodLabel, Collectors.counting()));
            statistics.put("periodCounts", periodCounts);
            
            // 납입기간별 통계
            Map<String, Long> payTermCounts = allData.stream()
                .collect(Collectors.groupingBy(UwCodeMappingData::getPayTerm, Collectors.counting()));
            statistics.put("payTermCounts", payTermCounts);
            
            log.info("UW_CODE_MAPPING 통계 조회 완료: {} 건", allData.size());
            return statistics;
            
        } catch (Exception e) {
            log.error("UW_CODE_MAPPING 통계 조회 오류: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }
}
