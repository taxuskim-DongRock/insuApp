package com.example.insu.dto;

import java.math.BigDecimal;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PremiumCalcResponse {
  private String insuCd;
  private String gender;
  private Long inputPremium;
  private BigDecimal stdAmount; // ISRC_STND_CNTA_AMT_NB9
  private BigDecimal rate;      // 남/녀 순수요율
  private BigDecimal result;    // 계산 결과
  private String message;   // 에러/부가 정보
  private Integer age;
  private Integer insuTerm;
  private Integer payTerm;
  private BigDecimal amountWon;
  private Integer amountManwon;
  private BigDecimal premiumWon;
}
