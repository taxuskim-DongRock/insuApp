package com.example.insu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UW_CODE_MAPPING 테이블 데이터 DTO (LLM 프롬프트 템플릿 확장)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UwCodeMappingData {
    
    // 기존 컬럼들
    private String srcFile;        // SRC_FILE - 소스 파일명
    private String code;           // CODE - 상품코드
    private String productName;    // PRODUCT_NAME - 상품명칭
    private String mainCode;       // MAIN_CODE - 매칭되는 주계약코드
    private String periodLabel;    // PERIOD_LABEL - 보험기간 라벨
    private Integer periodValue;   // PERIOD_VALUE - 보험기간 세부값
    private String payTerm;        // PAY_TERM - 납입기간
    private String entryAgeM;      // ENTRY_AGE_M - 남자가입나이
    private String entryAgeF;      // ENTRY_AGE_F - 여자가입나이
    
    // LLM 프롬프트 템플릿에서 추가된 컬럼들
    private String productGroup;   // PRODUCT_GROUP - 상품 그룹 (주계약/선택특약)
    private String typeLabel;      // TYPE_LABEL - 계약 유형 (최초계약)
    private String periodKind;     // PERIOD_KIND - 기간 종류 (E/S/N/R)
    private String classTag;       // CLASS_TAG - 분류 태그 (MAIN/A_OPTION 등)
}

