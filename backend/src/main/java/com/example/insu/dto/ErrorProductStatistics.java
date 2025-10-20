package com.example.insu.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 오류 발생 상품 통계 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorProductStatistics {
    private String insuCd;
    private Integer errorCount;
}






