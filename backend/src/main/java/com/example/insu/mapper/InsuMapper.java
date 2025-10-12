package com.example.insu.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Map;

@Mapper
public interface InsuMapper {
  Integer existsPremRate(@Param("insuCd") String insuCd);

  int countPremRateByInsuCd(@Param("insuCd") String insuCd);

  PremRateRow selectPremRate(@Param("insuCd") String insuCd,
                             @Param("age") int age,
                             @Param("insuTerm") int insuTerm,
                             @Param("payTerm") int payTerm);
  
  // 데이터 존재 여부 검증용
  int countRsvKeyByInsuCd(@Param("insuCd") String insuCd);
  int countRsvRateByInsuCd(@Param("insuCd") String insuCd);
  
  // 6-2. 준비금 상세 검증
  int countRsvRateByCondition(@Param("insuCd") String insuCd,
                              @Param("age") int age,
                              @Param("insuTerm") int insuTerm,
                              @Param("payTerm") int payTerm);
  
  // 6-3. 보험료 상세 검증
  int countPremRateByCondition(@Param("insuCd") String insuCd,
                               @Param("age") int age,
                               @Param("insuTerm") int insuTerm,
                               @Param("payTerm") int payTerm);
  
  // 7. MIN/MAX 보험료 계산용
  Map<String, Object> selectPremRateForMinMax(@Param("insuCd") String insuCd,
                                              @Param("age") int age,
                                              @Param("insuTerm") int insuTerm,
                                              @Param("payTerm") int payTerm);
  
  // 디버깅용: 실제 데이터 조회
  Map<String, Object> selectPremRateForDebug(@Param("insuCd") String insuCd,
                                             @Param("age") int age,
                                             @Param("insuTerm") int insuTerm,
                                             @Param("payTerm") int payTerm);
}