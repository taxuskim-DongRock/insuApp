package com.example.insu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Phase 3: 사용자 수정 로그 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorrectionLog {
    
    /**
     * 로그 ID
     */
    private Long id;
    
    /**
     * 보험코드
     */
    private String insuCd;
    
    /**
     * 원본 파싱 결과
     */
    private Map<String, String> originalResult;
    
    /**
     * 사용자 수정 결과
     */
    private Map<String, String> correctedResult;
    
    /**
     * PDF 텍스트 (학습용)
     */
    private String pdfText;
    
    /**
     * 수정 시간
     */
    private LocalDateTime timestamp;
    
    /**
     * 수정 이유 (옵션)
     */
    private String reason;
    
    /**
     * 사용자 ID (옵션)
     */
    private String userId;
    
    /**
     * 수정된 필드 개수
     */
    public int getCorrectedFieldCount() {
        int count = 0;
        for (String key : originalResult.keySet()) {
            String original = originalResult.get(key);
            String corrected = correctedResult.get(key);
            if (original != null && corrected != null && !original.equals(corrected)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 주요 수정 필드 반환
     */
    public String getPrimaryCorrectedField() {
        for (String key : originalResult.keySet()) {
            String original = originalResult.get(key);
            String corrected = correctedResult.get(key);
            if (original != null && corrected != null && !original.equals(corrected)) {
                return key;
            }
        }
        return null;
    }
}


