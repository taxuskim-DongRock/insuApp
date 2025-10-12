package com.example.insu.dto;

import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductInfoResponse {
  private String insuCd;       // 5자리 코드
  private String name;         // 3.보험코드 명칭
  private String type;         // 주계약/특약
  private List<PolicyTerms> terms;   // 4.사업방법 파싱 요약 (납입기간별 여러 조건)
  private Boolean calcAvailable; // RVT_PREM_RATE 존재 여부
  private DiscountDto discount; // 보험료할인
  private String message;      // 오류/부가메시지 (PDF 미존재 등)
  private Integer age;
  private Integer insuTerm;
  private Integer payTerm;
}
