package com.example.insu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CORRECTION_LOG 통계 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CorrectionLogStatistics {
    private Integer totalCount;
    private Integer learnedCount;
    private Double avgCorrectedFields;
}






