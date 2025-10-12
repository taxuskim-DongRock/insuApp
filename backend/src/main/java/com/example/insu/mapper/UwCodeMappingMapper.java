package com.example.insu.mapper;

import com.example.insu.dto.UwCodeMappingData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * UW_CODE_MAPPING 테이블 매퍼
 */
@Mapper
public interface UwCodeMappingMapper {
    
    /**
     * 보험코드로 매핑 데이터 조회 (확장된 스키마)
     */
    @Select("SELECT SRC_FILE, CODE, PRODUCT_NAME, MAIN_CODE, PERIOD_LABEL, PERIOD_VALUE, PAY_TERM, ENTRY_AGE_M, ENTRY_AGE_F, " +
            "PRODUCT_GROUP, TYPE_LABEL, PERIOD_KIND, CLASS_TAG " +
            "FROM UW_CODE_MAPPING WHERE CODE = #{code} ORDER BY PERIOD_LABEL, PAY_TERM")
    List<UwCodeMappingData> selectByCode(String code);
    
    /**
     * 주계약 코드로 매핑 데이터 조회 (확장된 스키마)
     */
    @Select("SELECT SRC_FILE, CODE, PRODUCT_NAME, MAIN_CODE, PERIOD_LABEL, PERIOD_VALUE, PAY_TERM, ENTRY_AGE_M, ENTRY_AGE_F, " +
            "PRODUCT_GROUP, TYPE_LABEL, PERIOD_KIND, CLASS_TAG " +
            "FROM UW_CODE_MAPPING WHERE MAIN_CODE = #{mainCode} ORDER BY CODE, PERIOD_LABEL, PAY_TERM")
    List<UwCodeMappingData> selectByMainCode(String mainCode);
    
    /**
     * 모든 매핑 데이터 조회 (확장된 스키마)
     */
    @Select("SELECT SRC_FILE, CODE, PRODUCT_NAME, MAIN_CODE, PERIOD_LABEL, PERIOD_VALUE, PAY_TERM, ENTRY_AGE_M, ENTRY_AGE_F, " +
            "PRODUCT_GROUP, TYPE_LABEL, PERIOD_KIND, CLASS_TAG " +
            "FROM UW_CODE_MAPPING ORDER BY CODE, PERIOD_LABEL, PAY_TERM")
    List<UwCodeMappingData> selectAll();
    
    /**
     * 보험코드와 보험기간으로 매핑 데이터 조회 (확장된 스키마)
     */
    @Select("SELECT SRC_FILE, CODE, PRODUCT_NAME, MAIN_CODE, PERIOD_LABEL, PERIOD_VALUE, PAY_TERM, ENTRY_AGE_M, ENTRY_AGE_F, " +
            "PRODUCT_GROUP, TYPE_LABEL, PERIOD_KIND, CLASS_TAG " +
            "FROM UW_CODE_MAPPING WHERE CODE = #{code} AND PERIOD_LABEL = #{periodLabel}")
    List<UwCodeMappingData> selectByCodeAndPeriod(String code, String periodLabel);
    
    /**
     * 보험코드와 납입기간으로 매핑 데이터 조회 (확장된 스키마)
     */
    @Select("SELECT SRC_FILE, CODE, PRODUCT_NAME, MAIN_CODE, PERIOD_LABEL, PERIOD_VALUE, PAY_TERM, ENTRY_AGE_M, ENTRY_AGE_F, " +
            "PRODUCT_GROUP, TYPE_LABEL, PERIOD_KIND, CLASS_TAG " +
            "FROM UW_CODE_MAPPING WHERE CODE = #{code} AND PAY_TERM = #{payTerm}")
    List<UwCodeMappingData> selectByCodeAndPayTerm(String code, String payTerm);
    
    /**
     * 상품 그룹별 조회 (새로운 기능)
     */
    @Select("SELECT SRC_FILE, CODE, PRODUCT_NAME, MAIN_CODE, PERIOD_LABEL, PERIOD_VALUE, PAY_TERM, ENTRY_AGE_M, ENTRY_AGE_F, " +
            "PRODUCT_GROUP, TYPE_LABEL, PERIOD_KIND, CLASS_TAG " +
            "FROM UW_CODE_MAPPING WHERE PRODUCT_GROUP = #{productGroup} ORDER BY CODE, PERIOD_LABEL, PAY_TERM")
    List<UwCodeMappingData> selectByProductGroup(String productGroup);
    
    /**
     * 기간 종류별 조회 (새로운 기능)
     */
    @Select("SELECT SRC_FILE, CODE, PRODUCT_NAME, MAIN_CODE, PERIOD_LABEL, PERIOD_VALUE, PAY_TERM, ENTRY_AGE_M, ENTRY_AGE_F, " +
            "PRODUCT_GROUP, TYPE_LABEL, PERIOD_KIND, CLASS_TAG " +
            "FROM UW_CODE_MAPPING WHERE PERIOD_KIND = #{periodKind} ORDER BY CODE, PERIOD_LABEL, PAY_TERM")
    List<UwCodeMappingData> selectByPeriodKind(String periodKind);
}

