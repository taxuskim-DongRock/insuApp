package com.example.insu.mapper;

import com.example.insu.dto.LearnedPattern;
import com.example.insu.dto.PatternSourceStatistics;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * LEARNED_PATTERN 테이블 매퍼
 */
@Mapper
public interface LearnedPatternMapper {
    
    /**
     * 패턴 저장 (UPSERT)
     */
    @Insert("""
        MERGE INTO LEARNED_PATTERN LP
        USING (
            SELECT 
                #{insuCd} as INSU_CD,
                #{fieldName} as FIELD_NAME,
                #{patternValue} as PATTERN_VALUE,
                #{confidenceScore} as CONFIDENCE_SCORE,
                #{learningSource} as LEARNING_SOURCE,
                #{learnedFromLogId} as LEARNED_FROM_LOG_ID,
                #{priority} as PRIORITY
            FROM DUAL
        ) SRC
        ON (LP.INSU_CD = SRC.INSU_CD AND LP.FIELD_NAME = SRC.FIELD_NAME)
        WHEN MATCHED THEN
            UPDATE SET
                PATTERN_VALUE = SRC.PATTERN_VALUE,
                CONFIDENCE_SCORE = 
                    CASE 
                        WHEN LP.LEARNING_SOURCE = 'UW_MAPPING' THEN 100
                        ELSE LEAST(LP.CONFIDENCE_SCORE + 10, 100)
                    END,
                APPLY_COUNT = LP.APPLY_COUNT + 1,
                UPDATED_AT = CURRENT_TIMESTAMP
        WHEN NOT MATCHED THEN
            INSERT (
                PATTERN_ID, INSU_CD, FIELD_NAME, PATTERN_VALUE,
                CONFIDENCE_SCORE, LEARNING_SOURCE, LEARNED_FROM_LOG_ID,
                PRIORITY, IS_ACTIVE, CREATED_AT
            ) VALUES (
                learned_pattern_seq.NEXTVAL,
                SRC.INSU_CD, SRC.FIELD_NAME, SRC.PATTERN_VALUE,
                SRC.CONFIDENCE_SCORE, SRC.LEARNING_SOURCE, SRC.LEARNED_FROM_LOG_ID,
                SRC.PRIORITY, 'Y', CURRENT_TIMESTAMP
            )
    """)
    int upsert(LearnedPattern pattern);
    
    /**
     * 패턴 조회 (파싱 시 사용)
     */
    @Select("""
        SELECT 
            PATTERN_ID as patternId,
            INSU_CD as insuCd,
            FIELD_NAME as fieldName,
            PATTERN_VALUE as patternValue,
            CONFIDENCE_SCORE as confidenceScore,
            PRIORITY as priority
        FROM LEARNED_PATTERN
        WHERE INSU_CD = #{insuCd}
          AND FIELD_NAME = #{fieldName}
          AND IS_ACTIVE = 'Y'
        ORDER BY PRIORITY DESC, CONFIDENCE_SCORE DESC
        FETCH FIRST 1 ROWS ONLY
    """)
    LearnedPattern selectByInsuCdAndField(
        @Param("insuCd") String insuCd, 
        @Param("fieldName") String fieldName
    );
    
    /**
     * 보험코드의 모든 패턴 조회
     */
    @Select("""
        SELECT 
            PATTERN_ID as patternId,
            INSU_CD as insuCd,
            FIELD_NAME as fieldName,
            PATTERN_VALUE as patternValue,
            CONFIDENCE_SCORE as confidenceScore,
            LEARNING_SOURCE as learningSource,
            PRIORITY as priority
        FROM LEARNED_PATTERN
        WHERE INSU_CD = #{insuCd}
          AND IS_ACTIVE = 'Y'
        ORDER BY FIELD_NAME, PRIORITY DESC
    """)
    List<LearnedPattern> selectAllByInsuCd(String insuCd);
    
    /**
     * 패턴 적용 횟수 증가
     */
    @Update("""
        UPDATE LEARNED_PATTERN
        SET APPLY_COUNT = APPLY_COUNT + 1,
            SUCCESS_COUNT = SUCCESS_COUNT + #{successIncrement},
            UPDATED_AT = CURRENT_TIMESTAMP
        WHERE PATTERN_ID = #{patternId}
    """)
    int incrementApplyCount(
        @Param("patternId") Long patternId, 
        @Param("successIncrement") int successIncrement
    );
    
    /**
     * 전체 패턴 수 조회
     */
    @Select("SELECT COUNT(*) FROM LEARNED_PATTERN WHERE IS_ACTIVE = 'Y'")
    int count();
    
    /**
     * 학습 소스별 패턴 수
     */
    @Select("""
        SELECT LEARNING_SOURCE as learningSource, COUNT(*) as cnt
        FROM LEARNED_PATTERN
        WHERE IS_ACTIVE = 'Y'
        GROUP BY LEARNING_SOURCE
    """)
    List<PatternSourceStatistics> selectPatternsBySource();
    
    /**
     * 패턴 비활성화
     */
    @Update("""
        UPDATE LEARNED_PATTERN
        SET IS_ACTIVE = 'N',
            UPDATED_AT = CURRENT_TIMESTAMP
        WHERE PATTERN_ID = #{patternId}
    """)
    int deactivate(Long patternId);
    
    /**
     * 활성 패턴 수 조회
     */
    @Select("SELECT COUNT(*) FROM LEARNED_PATTERN WHERE IS_ACTIVE = 'Y'")
    int countActive();
    
    /**
     * 기간별 패턴 수 조회
     */
    @Select("""
        SELECT COUNT(*) FROM LEARNED_PATTERN 
        WHERE IS_ACTIVE = 'Y' 
        AND CREATED_AT >= #{startDate} 
        AND CREATED_AT <= #{endDate}
    """)
    int countByDateRange(
        @Param("startDate") java.time.LocalDateTime startDate,
        @Param("endDate") java.time.LocalDateTime endDate
    );
    
    /**
     * 모든 패턴 조회
     */
    @Select("""
        SELECT 
            PATTERN_ID as patternId,
            INSU_CD as insuCd,
            FIELD_NAME as fieldName,
            PATTERN_VALUE as patternValue,
            CONFIDENCE_SCORE as confidenceScore,
            LEARNING_SOURCE as learningSource,
            APPLY_COUNT as applyCount,
            PRIORITY as priority
        FROM LEARNED_PATTERN
        WHERE IS_ACTIVE = 'Y'
        ORDER BY CREATED_AT DESC
    """)
    List<LearnedPattern> selectAll();
    
    /**
     * 학습된 패턴 상세 조회 (페이징)
     */
    @Select("""
        SELECT * FROM (
            SELECT ROWNUM rn, t.* FROM (
                SELECT 
                    PATTERN_ID as patternId,
                    INSU_CD as insuCd,
                    FIELD_NAME as fieldName,
                    PATTERN_VALUE as patternValue,
                    CONFIDENCE_SCORE as confidenceScore,
                    APPLY_COUNT as applyCount,
                    SUCCESS_COUNT as successCount,
                    LEARNING_SOURCE as learningSource,
                    CREATED_AT as createdAt,
                    IS_ACTIVE as isActive,
                    PRIORITY as priority
                FROM LEARNED_PATTERN
                WHERE 1=1
                <if test="fieldName != null and fieldName != ''">
                    AND FIELD_NAME = #{fieldName}
                </if>
                <if test="insuCd != null and insuCd != ''">
                    AND INSU_CD = #{insuCd}
                </if>
                ORDER BY CREATED_AT DESC
            ) t WHERE ROWNUM <= #{offset} + #{limit}
        ) WHERE rn > #{offset}
        """)
    List<LearnedPattern> selectDetailed(
        @Param("offset") int offset,
        @Param("limit") int limit,
        @Param("fieldName") String fieldName,
        @Param("insuCd") String insuCd
    );
    
    /**
     * 필드별 패턴 수 조회
     */
    @Select("SELECT COUNT(*) FROM LEARNED_PATTERN WHERE FIELD_NAME = #{fieldName} AND IS_ACTIVE = 'Y'")
    int countByField(@Param("fieldName") String fieldName);
}




