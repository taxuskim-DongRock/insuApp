package com.example.insu.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PremiumCalcRequest {
  private String insuCd;       // 예: "21791"
  private String gender;       // "M" 또는 "F"
  private Integer age;         // 피보험자 나이 (예: 15)
  private Integer insuTerm;    // 보험기간 (예: 10 또는 20)
  private Integer payTerm;     // 납입기간 (전기납이면 null 또는 동일한 값)
  private Boolean jeongi;      // 전기납 여부 (true면 payTerm=insuTerm 강제)
  private Integer amountManwon;// 계약금액(만원단위) 예: 100 ~ 1000
}