package com.example.insu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 패턴 소스별 통계 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatternSourceStatistics {
    private String learningSource;
    private Integer cnt;
}






