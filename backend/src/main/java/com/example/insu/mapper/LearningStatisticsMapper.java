package com.example.insu.mapper;

import com.example.insu.dto.LearningStatistics;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;

/**
 * LEARNING_STATISTICS 테이블 매퍼
 */
@Mapper
public interface LearningStatisticsMapper {
    
    /**
     * 통계 저장/업데이트 (UPSERT)
     */
    @Insert("""
        MERGE INTO LEARNING_STATISTICS LS
        USING (SELECT #{statDate} as STAT_DATE FROM DUAL) SRC
        ON (LS.STAT_DATE = SRC.STAT_DATE)
        WHEN MATCHED THEN
            UPDATE SET
                TOTAL_CORRECTIONS = #{totalCorrections},
                TOTAL_PATTERNS = #{totalPatterns},
                TOTAL_FEW_SHOT_EXAMPLES = #{totalFewShotExamples},
                CURRENT_ACCURACY = #{currentAccuracy},
                DAILY_CORRECTION_COUNT = #{dailyCorrectionCount},
                UPDATED_AT = CURRENT_TIMESTAMP
        WHEN NOT MATCHED THEN
            INSERT (
                STAT_ID, STAT_DATE, TOTAL_CORRECTIONS, TOTAL_PATTERNS,
                TOTAL_FEW_SHOT_EXAMPLES,
                CURRENT_ACCURACY, DAILY_CORRECTION_COUNT, CREATED_AT
            ) VALUES (
                learning_statistics_seq.NEXTVAL,
                #{statDate}, #{totalCorrections}, #{totalPatterns},
                #{totalFewShotExamples},
                #{currentAccuracy}, #{dailyCorrectionCount}, CURRENT_TIMESTAMP
            )
    """)
    int upsert(LearningStatistics statistics);
    
    /**
     * 날짜별 통계 조회
     */
    @Select("""
        SELECT 
            STAT_ID as statId,
            STAT_DATE as statDate,
            TOTAL_CORRECTIONS as totalCorrections,
            TOTAL_PATTERNS as totalPatterns,
            TOTAL_FEW_SHOT_EXAMPLES as totalFewShotExamples,
            INITIAL_ACCURACY as initialAccuracy,
            CURRENT_ACCURACY as currentAccuracy,
            ACCURACY_IMPROVEMENT as accuracyImprovement,
            DAILY_CORRECTION_COUNT as dailyCorrectionCount
        FROM LEARNING_STATISTICS
        WHERE STAT_DATE = #{statDate}
    """)
    LearningStatistics selectByDate(LocalDate statDate);
    
    /**
     * 최신 통계 조회
     */
    @Select("""
        SELECT 
            STAT_ID as statId,
            STAT_DATE as statDate,
            TOTAL_CORRECTIONS as totalCorrections,
            TOTAL_PATTERNS as totalPatterns,
            TOTAL_FEW_SHOT_EXAMPLES as totalFewShotExamples,
            CURRENT_ACCURACY as currentAccuracy,
            ACCURACY_IMPROVEMENT as accuracyImprovement
        FROM LEARNING_STATISTICS
        ORDER BY STAT_DATE DESC
        FETCH FIRST 1 ROWS ONLY
    """)
    LearningStatistics selectLatest();
    
    /**
     * 최근 30일 통계 조회
     */
    @Select("""
        SELECT 
            STAT_ID as statId,
            STAT_DATE as statDate,
            TOTAL_CORRECTIONS as totalCorrections,
            TOTAL_PATTERNS as totalPatterns,
            TOTAL_FEW_SHOT_EXAMPLES as totalFewShotExamples,
            CURRENT_ACCURACY as currentAccuracy,
            ACCURACY_IMPROVEMENT as accuracyImprovement
        FROM LEARNING_STATISTICS
        WHERE STAT_DATE >= TRUNC(SYSDATE) - 30
        ORDER BY STAT_DATE DESC
    """)
    java.util.List<LearningStatistics> selectLast30Days();
    
    /**
     * 날짜 범위별 통계 조회
     */
    @Select("""
        SELECT 
            STAT_ID as statId,
            STAT_DATE as statDate,
            TOTAL_CORRECTIONS as totalCorrections,
            TOTAL_PATTERNS as totalPatterns,
            TOTAL_FEW_SHOT_EXAMPLES as totalFewShotExamples,
            CURRENT_ACCURACY as currentAccuracy,
            ACCURACY_IMPROVEMENT as accuracyImprovement
        FROM LEARNING_STATISTICS
        WHERE STAT_DATE >= #{startDate} AND STAT_DATE <= #{endDate}
        ORDER BY STAT_DATE DESC
    """)
    java.util.List<LearningStatistics> selectByDateRange(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}




