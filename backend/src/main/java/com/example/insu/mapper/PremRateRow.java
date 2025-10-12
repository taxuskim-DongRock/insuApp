package com.example.insu.mapper;

import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
public class PremRateRow {
  private BigDecimal stndAmt;  // ISRC_STND_CNTA_AMT_NB9  (기준구성금액, 원 단위)
  private BigDecimal manRate;  // INDI_MAN_ISRC_NB7       (남자 요율)
  private BigDecimal fmlRate;  // INDI_FML_ISRC_NB7       (여자 요율)
}
