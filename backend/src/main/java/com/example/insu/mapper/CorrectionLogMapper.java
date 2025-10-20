package com.example.insu.mapper;

import com.example.insu.dto.CorrectionLog;
import com.example.insu.dto.CorrectionLogStatistics;
import com.example.insu.dto.ErrorProductStatistics;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * CORRECTION_LOG 테이블 매퍼
 */
@Mapper
public interface CorrectionLogMapper {
    
    /**
     * 수정 로그 저장
     */
    @Insert("""
        INSERT INTO CORRECTION_LOG (
            LOG_ID, INSU_CD, SRC_FILE, PRODUCT_NAME,
            ORIGINAL_INSU_TERM, ORIGINAL_PAY_TERM, ORIGINAL_AGE_RANGE, 
            ORIGINAL_RENEW, ORIGINAL_SPECIAL_NOTES, ORIGINAL_VALIDATION_SOURCE,
            CORRECTED_INSU_TERM, CORRECTED_PAY_TERM, CORRECTED_AGE_RANGE, 
            CORRECTED_RENEW, CORRECTED_SPECIAL_NOTES,
            PDF_TEXT, CORRECTED_FIELD_COUNT, CORRECTION_REASON, USER_ID,
            IS_LEARNED, CREATED_AT
        ) VALUES (
            correction_log_seq.NEXTVAL, #{insuCd}, #{srcFile}, #{productName},
            #{originalInsuTerm}, #{originalPayTerm}, #{originalAgeRange}, 
            #{originalRenew}, #{originalSpecialNotes}, #{originalValidationSource},
            #{correctedInsuTerm}, #{correctedPayTerm}, #{correctedAgeRange}, 
            #{correctedRenew}, #{correctedSpecialNotes},
            #{pdfText}, #{correctedFieldCount}, #{correctionReason}, #{userId},
            'N', CURRENT_TIMESTAMP
        )
    """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "LOG_ID")
    int insert(CorrectionLog correctionLog);
    
    /**
     * ID로 조회
     */
    @Select("""
        SELECT 
            LOG_ID as id, INSU_CD as insuCd, SRC_FILE as srcFile, 
            PRODUCT_NAME as productName,
            ORIGINAL_INSU_TERM as originalInsuTerm, 
            ORIGINAL_PAY_TERM as originalPayTerm,
            ORIGINAL_AGE_RANGE as originalAgeRange, 
            ORIGINAL_RENEW as originalRenew,
            CORRECTED_INSU_TERM as correctedInsuTerm, 
            CORRECTED_PAY_TERM as correctedPayTerm,
            CORRECTED_AGE_RANGE as correctedAgeRange, 
            CORRECTED_RENEW as correctedRenew,
            PDF_TEXT as pdfText, CORRECTED_FIELD_COUNT as correctedFieldCount,
            CORRECTION_REASON as correctionReason, USER_ID as userId,
            IS_LEARNED as isLearned, CREATED_AT as timestamp
        FROM CORRECTION_LOG
        WHERE LOG_ID = #{logId}
    """)
    CorrectionLog selectById(Long logId);
    
    /**
     * 보험코드로 조회
     */
    @Select("""
        SELECT 
            LOG_ID as id, INSU_CD as insuCd, SRC_FILE as srcFile,
            ORIGINAL_INSU_TERM as originalInsuTerm, 
            CORRECTED_INSU_TERM as correctedInsuTerm,
            ORIGINAL_PAY_TERM as originalPayTerm,
            CORRECTED_PAY_TERM as correctedPayTerm,
            ORIGINAL_AGE_RANGE as originalAgeRange,
            CORRECTED_AGE_RANGE as correctedAgeRange,
            ORIGINAL_RENEW as originalRenew,
            CORRECTED_RENEW as correctedRenew,
            PDF_TEXT as pdfText,
            CREATED_AT as timestamp
        FROM CORRECTION_LOG
        WHERE INSU_CD = #{insuCd}
        ORDER BY CREATED_AT DESC
    """)
    List<CorrectionLog> selectByInsuCd(String insuCd);
    
    /**
     * 미학습 로그 조회 (배치 학습용)
     */
    @Select("""
        SELECT 
            LOG_ID as id, INSU_CD as insuCd,
            ORIGINAL_INSU_TERM as originalInsuTerm, 
            ORIGINAL_PAY_TERM as originalPayTerm,
            ORIGINAL_AGE_RANGE as originalAgeRange,
            ORIGINAL_RENEW as originalRenew,
            CORRECTED_INSU_TERM as correctedInsuTerm, 
            CORRECTED_PAY_TERM as correctedPayTerm,
            CORRECTED_AGE_RANGE as correctedAgeRange,
            CORRECTED_RENEW as correctedRenew,
            PDF_TEXT as pdfText,
            CREATED_AT as timestamp
        FROM CORRECTION_LOG
        WHERE IS_LEARNED = 'N'
        ORDER BY CREATED_AT ASC
        FETCH FIRST #{limit} ROWS ONLY
    """)
    List<CorrectionLog> selectUnlearnedLogs(@Param("limit") int limit);
    
    /**
     * 학습 완료 처리
     */
    @Update("""
        UPDATE CORRECTION_LOG
        SET IS_LEARNED = 'Y',
            LEARNED_AT = CURRENT_TIMESTAMP,
            LEARNING_PATTERN_ID = #{patternId}
        WHERE LOG_ID = #{logId}
    """)
    int markAsLearned(@Param("logId") Long logId, @Param("patternId") Long patternId);
    
    /**
     * 기간별 통계 조회
     */
    @Select("""
        SELECT 
            COUNT(*) as totalCount,
            COUNT(CASE WHEN IS_LEARNED = 'Y' THEN 1 END) as learnedCount,
            AVG(CORRECTED_FIELD_COUNT) as avgCorrectedFields
        FROM CORRECTION_LOG
        WHERE CREATED_AT >= #{startDate} 
          AND CREATED_AT < #{endDate}
    """)
    CorrectionLogStatistics selectStatistics(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    /**
     * 전체 건수 조회
     */
    @Select("SELECT COUNT(*) FROM CORRECTION_LOG")
    int count();
    
    /**
     * 미학습 건수 조회
     */
    @Select("SELECT COUNT(*) FROM CORRECTION_LOG WHERE IS_LEARNED = 'N'")
    int countUnlearned();
    
    /**
     * 자주 틀리는 상품 Top N
     */
    @Select("""
        SELECT INSU_CD as insuCd, COUNT(*) as errorCount
        FROM CORRECTION_LOG
        WHERE CREATED_AT >= TRUNC(SYSDATE) - #{days}
        GROUP BY INSU_CD
        ORDER BY errorCount DESC
        FETCH FIRST #{limit} ROWS ONLY
    """)
    List<ErrorProductStatistics> selectTopErrorProducts(
        @Param("days") int days, 
        @Param("limit") int limit
    );
    
    /**
     * 최신 수정 로그 조회
     */
    @Select("""
        SELECT 
            LOG_ID as id, INSU_CD as insuCd, SRC_FILE as srcFile,
            ORIGINAL_INSU_TERM as originalInsuTerm, 
            CORRECTED_INSU_TERM as correctedInsuTerm,
            ORIGINAL_PAY_TERM as originalPayTerm,
            CORRECTED_PAY_TERM as correctedPayTerm,
            ORIGINAL_AGE_RANGE as originalAgeRange,
            CORRECTED_AGE_RANGE as correctedAgeRange,
            ORIGINAL_RENEW as originalRenew,
            CORRECTED_RENEW as correctedRenew,
            PDF_TEXT as pdfText,
            CREATED_AT as timestamp
        FROM CORRECTION_LOG
        ORDER BY CREATED_AT DESC
        FETCH FIRST 1 ROWS ONLY
    """)
    CorrectionLog selectLatest();
    
    /**
     * 최근 수정 로그 조회
     */
    @Select("""
        SELECT 
            LOG_ID as id, INSU_CD as insuCd, SRC_FILE as srcFile,
            ORIGINAL_INSU_TERM as originalInsuTerm, 
            CORRECTED_INSU_TERM as correctedInsuTerm,
            ORIGINAL_PAY_TERM as originalPayTerm,
            CORRECTED_PAY_TERM as correctedPayTerm,
            ORIGINAL_AGE_RANGE as originalAgeRange,
            CORRECTED_AGE_RANGE as correctedAgeRange,
            ORIGINAL_RENEW as originalRenew,
            CORRECTED_RENEW as correctedRenew,
            PDF_TEXT as pdfText,
            CORRECTED_FIELD_COUNT as correctedFieldCount,
            CREATED_AT as timestamp
        FROM CORRECTION_LOG
        ORDER BY CREATED_AT DESC
        FETCH FIRST #{limit} ROWS ONLY
    """)
    List<CorrectionLog> selectRecent(@Param("limit") int limit);
    
    /**
     * 학습된 수정 건수 조회
     */
    @Select("SELECT COUNT(*) FROM CORRECTION_LOG WHERE IS_LEARNED = 'Y'")
    int countLearned();
    
    /**
     * 수정 사항 상세 조회 (페이징) - Oracle 대문자 컬럼명 문제 해결
     */
    @Select("SELECT LOG_ID as \"ID\", INSU_CD as \"INSUCD\", SRC_FILE as \"SRCFILE\", " +
            "PRODUCT_NAME as \"PRODUCTNAME\", " +
            "NVL(ORIGINAL_INSU_TERM, '') as \"ORIGINALINSUTERM\", " +
            "NVL(ORIGINAL_PAY_TERM, '') as \"ORIGINALPAYTERM\", " +
            "NVL(ORIGINAL_AGE_RANGE, '') as \"ORIGINALAGERANGE\", " +
            "NVL(ORIGINAL_RENEW, '') as \"ORIGINALRENEW\", " +
            "NVL(CORRECTED_INSU_TERM, '') as \"CORRECTEDINSUTERM\", " +
            "NVL(CORRECTED_PAY_TERM, '') as \"CORRECTEDPAYTERM\", " +
            "NVL(CORRECTED_AGE_RANGE, '') as \"CORRECTEDAGERANGE\", " +
            "NVL(CORRECTED_RENEW, '') as \"CORRECTEDRENEW\", " +
            "CORRECTED_FIELD_COUNT as \"CORRECTEDFIELDCOUNT\", " +
            "NVL(CORRECTION_REASON, '') as \"CORRECTIONREASON\", " +
            "NVL(USER_ID, '') as \"USERID\", " +
            "IS_LEARNED as \"ISLEARNED\", " +
            "CREATED_AT as \"TIMESTAMP\" " +
            "FROM CORRECTION_LOG " +
            "WHERE ROWNUM <= #{limit}")
    List<Map<String, Object>> selectDetailed(
        @Param("offset") int offset,
        @Param("limit") int limit,
        @Param("insuCd") String insuCd,
        @Param("startDate") String startDate,
        @Param("endDate") String endDate
    );
    
    /**
     * 수정 사항 전체 조회 (테스트용)
     */
    @Select("SELECT LOG_ID, INSU_CD, CREATED_AT FROM CORRECTION_LOG WHERE ROWNUM <= 5")
    List<Map<String, Object>> selectAllDetailed();
    
    @Select("SELECT LOG_ID as id, INSU_CD as insuCd, " +
            "NVL(ORIGINAL_INSU_TERM, '') as originalInsuTerm, " +
            "NVL(CORRECTED_INSU_TERM, '') as correctedInsuTerm, " +
            "NVL(ORIGINAL_PAY_TERM, '') as originalPayTerm, " +
            "NVL(CORRECTED_PAY_TERM, '') as correctedPayTerm, " +
            "NVL(ORIGINAL_AGE_RANGE, '') as originalAgeRange, " +
            "NVL(CORRECTED_AGE_RANGE, '') as correctedAgeRange, " +
            "NVL(ORIGINAL_RENEW, '') as originalRenew, " +
            "NVL(CORRECTED_RENEW, '') as correctedRenew " +
            "FROM CORRECTION_LOG WHERE ROWNUM <= 3")
    List<Map<String, Object>> selectTestData();
    
}




