package com.example.insu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 검증 결과 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    
    private String status;                    // 검증 상태 (VALID, INVALID, NO_MAPPING_DATA)
    private int confidence;                   // 신뢰도 (0-100)
    private int score;                        // 점수
    private int total;                        // 전체 항목 수
    private List<String> matchedTerms;        // 일치하는 항목들
    private List<String> mismatchedTerms;     // 불일치하는 항목들
    private List<UwCodeMappingData> mappingData; // 매핑 데이터
    private Map<String, String> expectedData; // 예상 데이터
    private Map<String, String> parsedData;   // 파싱된 데이터
    private String message;                   // 메시지
    private String insuCd;                    // 보험코드
}







