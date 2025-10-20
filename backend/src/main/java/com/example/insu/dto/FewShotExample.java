package com.example.insu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * FEW_SHOT_EXAMPLE 테이블 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FewShotExample {
    private Long exampleId;
    private String insuCd;
    private String productName;
    private String inputText;
    private String outputInsuTerm;
    private String outputPayTerm;
    private String outputAgeRange;
    private String outputRenew;
    private String exampleType;
    private Integer qualityScore;
    private Integer useCount;
    private Double successRate;
    private Character isActive;
    private Integer priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long sourceLogId;
    private Long relatedPatternId;
}






