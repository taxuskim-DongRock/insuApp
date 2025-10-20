package com.example.insu.mapper;

import com.example.insu.dto.FewShotExample;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * FEW_SHOT_EXAMPLE 테이블 매퍼
 */
@Mapper
public interface FewShotExampleMapper {
    
    /**
     * Few-Shot 예시 저장
     */
    @Insert("""
        INSERT INTO FEW_SHOT_EXAMPLE (
            EXAMPLE_ID, INSU_CD, PRODUCT_NAME,
            INPUT_TEXT, OUTPUT_INSU_TERM, OUTPUT_PAY_TERM, 
            OUTPUT_AGE_RANGE, OUTPUT_RENEW,
            EXAMPLE_TYPE, QUALITY_SCORE, SOURCE_LOG_ID, IS_ACTIVE
        ) VALUES (
            few_shot_example_seq.NEXTVAL,
            #{insuCd}, #{productName}, #{inputText},
            #{outputInsuTerm}, #{outputPayTerm}, #{outputAgeRange}, #{outputRenew},
            #{exampleType}, #{qualityScore}, #{sourceLogId}, 'Y'
        )
    """)
    @Options(useGeneratedKeys = true, keyProperty = "exampleId", keyColumn = "EXAMPLE_ID")
    int insert(FewShotExample example);
    
    /**
     * 상위 Few-Shot 예시 조회
     */
    @Select("""
        SELECT 
            EXAMPLE_ID as exampleId, INSU_CD as insuCd,
            PRODUCT_NAME as productName,
            INPUT_TEXT as inputText,
            OUTPUT_INSU_TERM as outputInsuTerm,
            OUTPUT_PAY_TERM as outputPayTerm,
            OUTPUT_AGE_RANGE as outputAgeRange,
            OUTPUT_RENEW as outputRenew,
            QUALITY_SCORE as qualityScore,
            USE_COUNT as useCount
        FROM FEW_SHOT_EXAMPLE
        WHERE IS_ACTIVE = 'Y'
        ORDER BY QUALITY_SCORE DESC, USE_COUNT DESC
        FETCH FIRST #{limit} ROWS ONLY
    """)
    List<FewShotExample> selectTopExamples(int limit);
    
    /**
     * 전체 예시 수 조회
     */
    @Select("SELECT COUNT(*) FROM FEW_SHOT_EXAMPLE WHERE IS_ACTIVE = 'Y'")
    int count();
    
    /**
     * 특정 상품코드의 예시 수 조회
     */
    @Select("SELECT COUNT(*) FROM FEW_SHOT_EXAMPLE WHERE INSU_CD = #{insuCd} AND IS_ACTIVE = 'Y'")
    int countByInsuCd(@Param("insuCd") String insuCd);
    
    /**
     * 사용 횟수 증가
     */
    @Update("""
        UPDATE FEW_SHOT_EXAMPLE
        SET USE_COUNT = USE_COUNT + 1,
            UPDATED_AT = CURRENT_TIMESTAMP
        WHERE EXAMPLE_ID = #{exampleId}
    """)
    int incrementUseCount(Long exampleId);
    
    /**
     * 활성 예시 수 조회
     */
    @Select("SELECT COUNT(*) FROM FEW_SHOT_EXAMPLE WHERE IS_ACTIVE = 'Y'")
    int countActive();
    
    /**
     * 평균 품질 조회
     */
    @Select("SELECT AVG(QUALITY_SCORE) FROM FEW_SHOT_EXAMPLE WHERE IS_ACTIVE = 'Y'")
    Double getAverageQuality();
    
    /**
     * 모든 예시 조회
     */
    @Select("""
        SELECT 
            EXAMPLE_ID as exampleId,
            INSU_CD as insuCd,
            PRODUCT_NAME as productName,
            INPUT_TEXT as inputText,
            OUTPUT_INSU_TERM as outputInsuTerm,
            OUTPUT_PAY_TERM as outputPayTerm,
            OUTPUT_AGE_RANGE as outputAgeRange,
            OUTPUT_RENEW as outputRenew,
            EXAMPLE_TYPE as exampleType,
            QUALITY_SCORE as qualityScore,
            SOURCE_LOG_ID as sourceLogId,
            CREATED_AT as createdAt
        FROM FEW_SHOT_EXAMPLE
        WHERE IS_ACTIVE = 'Y'
        ORDER BY CREATED_AT DESC
    """)
    List<FewShotExample> selectAll();
    
    /**
     * Few-Shot 예시 상세 조회 (페이징)
     */
    @Select("""
        SELECT * FROM (
            SELECT ROWNUM rn, t.* FROM (
                SELECT 
                    EXAMPLE_ID as exampleId,
                    INSU_CD as insuCd,
                    PRODUCT_NAME as productName,
                    INPUT_TEXT as inputText,
                    OUTPUT_INSU_TERM as outputInsuTerm,
                    OUTPUT_PAY_TERM as outputPayTerm,
                    OUTPUT_AGE_RANGE as outputAgeRange,
                    OUTPUT_RENEW as outputRenew,
                    EXAMPLE_TYPE as exampleType,
                    QUALITY_SCORE as qualityScore,
                    SOURCE_LOG_ID as sourceLogId,
                    CREATED_AT as createdAt,
                    IS_ACTIVE as isActive
                FROM FEW_SHOT_EXAMPLE
                WHERE 1=1
                <if test="insuCd != null and insuCd != ''">
                    AND INSU_CD = #{insuCd}
                </if>
                ORDER BY CREATED_AT DESC
            ) t WHERE ROWNUM <= #{offset} + #{limit}
        ) WHERE rn > #{offset}
        """)
    List<FewShotExample> selectDetailed(
        @Param("offset") int offset,
        @Param("limit") int limit,
        @Param("insuCd") String insuCd
    );
}


