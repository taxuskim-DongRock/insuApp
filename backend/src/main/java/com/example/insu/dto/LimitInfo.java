package com.example.insu.dto;

import java.math.BigDecimal;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LimitInfo {
  private String insuCd;
  private String name;     // 명칭(3.보험코드와 맵핑)
  private BigDecimal minWon;     // 최소 원화 (예: 1_000_000)
  private BigDecimal maxWon;     // 최대 원화
  private String display;  // "최소 1백만 ~ 최대 4천만" 등
  private String message;  // 미발견 안내 등
}
