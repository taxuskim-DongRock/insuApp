package com.example.insu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * LEARNED_PATTERN 테이블 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearnedPattern {
    private Long patternId;
    private String insuCd;
    private String fieldName;           // insuTerm, payTerm, ageRange, renew
    private String patternValue;
    private Integer confidenceScore;    // 0-100
    private Integer applyCount;
    private Integer successCount;
    private Long learnedFromLogId;
    private String learningSource;      // UW_MAPPING, USER_CORRECTION, AUTO_LEARNED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Character isActive;         // Y/N
    private Integer priority;
    
    /**
     * 성공률 계산
     */
    public double getSuccessRate() {
        if (applyCount == null || applyCount == 0) {
            return 0.0;
        }
        return (successCount != null ? successCount : 0) * 100.0 / applyCount;
    }
}






