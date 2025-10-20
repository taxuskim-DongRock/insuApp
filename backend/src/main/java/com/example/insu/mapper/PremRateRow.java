package com.example.insu.mapper;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
public class PremRateRow {
  private Integer piboAge;     // ISRC_TBL_PIBO_AGE_NB3    (피보험자 나이)
  private Integer insuTerm;    // ISRC_TBL_INSU_YYCT_NB3   (보험기간)
  private Integer payTerm;     // ISRC_TBL_NABI_MMCT_NB3   (납입기간)
  private BigDecimal stndAmt;  // ISRC_STND_CNTA_AMT_NB9   (기준구성금액, 원 단위)
  private BigDecimal manRate;  // INDI_MAN_ISRC_NB7        (남자 요율)
  private BigDecimal fmlRate;  // INDI_FML_ISRC_NB7        (여자 요율)
}
