package com.example.insu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * LEARNING_STATISTICS 테이블 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningStatistics {
    private Long statId;
    private LocalDate statDate;
    private Integer totalCorrections;
    private Integer totalPatterns;
    private Integer totalFewShotExamples;
    private Double initialAccuracy;
    private Double currentAccuracy;
    private Double accuracyImprovement;
    private Integer dailyParsingCount;
    private Integer dailyCorrectionCount;
    private Double dailySuccessRate;
    private Double insuTermAccuracy;
    private Double payTermAccuracy;
    private Double ageRangeAccuracy;
    private Double renewAccuracy;
    private String top5ErrorProducts;
    private String top5SuccessProducts;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    /**
     * 정확도 향상 계산
     */
    public Double getImprovement() {
        if (initialAccuracy == null || currentAccuracy == null) {
            return 0.0;
        }
        return currentAccuracy - initialAccuracy;
    }
}






