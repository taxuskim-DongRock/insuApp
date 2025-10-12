// src/main/java/com/example/insu/dto/ProductDto.java
package com.example.insu.dto;

import lombok.*;

public class ProductDto {

  @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
  public static class Terms {
    private Age age;            // 가입나이(공통/남/여)
    private String insuTerm;    // 보험기간(보장기간)
    private String payTerm;     // 납입기간
    private String renew;       // 갱신 요약(예: "5년/10년 갱신 / 최종 100세")
    private String specialNotes;// (주) 특이사항 요약

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Age {
      private String common;    // 예: "0세 ~ 30세"
      private String male;      // 예: "30세 ~ 75세"
      private String female;    // 예: "30세 ~ 75세"
    }
  }
}
