// src/main/java/com/example/insu/dto/DiscountDto.java
package com.example.insu.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DiscountDto {

  /**
   * 고액계약 할인 규칙 목록
   * 예) thresholdWon=5,000,000, ratePercent=1.0 -> 500만원 이상 1.0% 할인
   */
  @Singular("high")          // builder().high(rule).high(rule2) 형태 허용
  private List<HighAmount> highAmount;

  /**
   * 보험료 할인 조건(자격/행동 기반) 문구 목록
   * 예) "부부계약 동시가입 2% 할인", "무사고 2년 유지 시 1% 할인"
   */
  @Singular("condition")     // builder().condition("...").condition("...") 허용
  private List<String> premium;

  @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
  public static class HighAmount {
    /** 임계금액(원) - 예: 10000000 = 1,000만원 */
    private BigDecimal thresholdWon;
    /** 할인율(%) - 예: 2.0 = 2% */
    private BigDecimal ratePercent;
    /** 원문 근거/비고(선택) */
    private String note;
  }
}
