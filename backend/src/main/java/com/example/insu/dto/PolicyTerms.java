package com.example.insu.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PolicyTerms {
  private String ageRange;     // 예: "15~80세"
  private String insuTerm;     // 예: "100세만기" 또는 "90세만기"
  private String payTerm;      // 예: "10/20/25/30년납" 또는 "전기납"
  private String renew;        // 예: "10년, 20년 갱신 / 최종 100세"
  private String specialNotes; // 요약: "최종갱신 100세 · 주계약 기간 초과 불가"
}
