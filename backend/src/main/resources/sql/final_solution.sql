-- ========================================
-- 최종 해결책 - UNIQUE 제약 조건 완전 해결
-- 문제: UW_CODE_MAPPING에 복잡한 중복 구조
-- ========================================

-- ========================================
-- Step 1: 진단 - 실제 데이터 구조 확인
-- ========================================

SELECT '=== UW_CODE_MAPPING 데이터 구조 분석 ===' AS INFO FROM DUAL;

-- 전체 데이터 확인
SELECT 
    CODE,
    PERIOD_LABEL,
    SUBSTR(PAY_TERM, 1, 20) AS PAY_TERM,
    SUBSTR(ENTRY_AGE_M, 1, 20) AS ENTRY_AGE_M
FROM UW_CODE_MAPPING
ORDER BY CODE, PERIOD_LABEL;

-- CODE별 개수
SELECT 
    CODE,
    COUNT(*) AS ROW_COUNT
FROM UW_CODE_MAPPING
GROUP BY CODE
ORDER BY CODE;

-- (CODE, PERIOD_LABEL) 조합 확인
SELECT 
    CODE,
    PERIOD_LABEL,
    COUNT(*) AS CNT
FROM UW_CODE_MAPPING
GROUP BY CODE, PERIOD_LABEL
ORDER BY CODE, PERIOD_LABEL;


-- ========================================
-- Step 2: LEARNED_PATTERN 완전 초기화
-- ========================================

-- 외래키 일시 비활성화
ALTER TABLE LEARNED_PATTERN DISABLE CONSTRAINT FK_PATTERN_LOG;

-- 테이블 TRUNCATE (빠른 삭제)
TRUNCATE TABLE LEARNED_PATTERN;

-- 시퀀스 재생성
DROP SEQUENCE learned_pattern_seq;
CREATE SEQUENCE learned_pattern_seq START WITH 1 INCREMENT BY 1;

SELECT '=== LEARNED_PATTERN 초기화 완료 ===' AS INFO FROM DUAL;


-- ========================================
-- Step 3: 확실한 이관 (ROW_NUMBER 사용)
-- ========================================

-- 보험기간(insuTerm) - 각 CODE별 첫 번째 행만 선택
INSERT INTO LEARNED_PATTERN (
    PATTERN_ID, INSU_CD, FIELD_NAME, PATTERN_VALUE, 
    CONFIDENCE_SCORE, LEARNING_SOURCE, PRIORITY, IS_ACTIVE
)
SELECT 
    learned_pattern_seq.NEXTVAL,
    CODE,
    'insuTerm',
    PERIOD_LABEL,
    100,
    'UW_MAPPING',
    100,
    'Y'
FROM (
    SELECT 
        CODE,
        PERIOD_LABEL,
        ROW_NUMBER() OVER (PARTITION BY CODE ORDER BY PERIOD_LABEL) AS RN
    FROM UW_CODE_MAPPING
    WHERE PERIOD_LABEL IS NOT NULL
)
WHERE RN = 1;

SELECT 'insuTerm 이관: ' || SQL%ROWCOUNT || '건' AS INFO FROM DUAL;


-- 납입기간(payTerm) - 각 CODE별 첫 번째 행만 선택
INSERT INTO LEARNED_PATTERN (
    PATTERN_ID, INSU_CD, FIELD_NAME, PATTERN_VALUE, 
    CONFIDENCE_SCORE, LEARNING_SOURCE, PRIORITY, IS_ACTIVE
)
SELECT 
    learned_pattern_seq.NEXTVAL,
    CODE,
    'payTerm',
    PAY_TERM,
    100,
    'UW_MAPPING',
    100,
    'Y'
FROM (
    SELECT 
        CODE,
        PAY_TERM,
        ROW_NUMBER() OVER (PARTITION BY CODE ORDER BY PAY_TERM) AS RN
    FROM UW_CODE_MAPPING
    WHERE PAY_TERM IS NOT NULL
)
WHERE RN = 1;

SELECT 'payTerm 이관: ' || SQL%ROWCOUNT || '건' AS INFO FROM DUAL;


-- 가입나이(ageRange) - 각 CODE별 첫 번째 행만 선택
INSERT INTO LEARNED_PATTERN (
    PATTERN_ID, INSU_CD, FIELD_NAME, PATTERN_VALUE, 
    CONFIDENCE_SCORE, LEARNING_SOURCE, PRIORITY, IS_ACTIVE
)
SELECT 
    learned_pattern_seq.NEXTVAL,
    CODE,
    'ageRange',
    ENTRY_AGE_M,
    100,
    'UW_MAPPING',
    100,
    'Y'
FROM (
    SELECT 
        CODE,
        ENTRY_AGE_M,
        ROW_NUMBER() OVER (PARTITION BY CODE ORDER BY ENTRY_AGE_M) AS RN
    FROM UW_CODE_MAPPING
    WHERE ENTRY_AGE_M IS NOT NULL
)
WHERE RN = 1;

SELECT 'ageRange 이관: ' || SQL%ROWCOUNT || '건' AS INFO FROM DUAL;

COMMIT;

SELECT '=== 이관 완료! ===' AS INFO FROM DUAL;


-- ========================================
-- Step 4: 결과 확인
-- ========================================

-- 전체 건수
SELECT COUNT(*) AS TOTAL_LEARNED_PATTERNS FROM LEARNED_PATTERN;

-- 필드별 분포
SELECT 
    FIELD_NAME,
    COUNT(*) AS CNT
FROM LEARNED_PATTERN
GROUP BY FIELD_NAME
ORDER BY FIELD_NAME;

-- 중복 확인 (없어야 정상!)
SELECT 
    INSU_CD,
    FIELD_NAME,
    COUNT(*) AS CNT
FROM LEARNED_PATTERN
GROUP BY INSU_CD, FIELD_NAME
HAVING COUNT(*) > 1;

-- 중복이 없으면 "no rows selected" (정상!)

-- 샘플 데이터 (처음 10건)
SELECT 
    PATTERN_ID,
    INSU_CD,
    FIELD_NAME,
    SUBSTR(PATTERN_VALUE, 1, 30) AS VALUE,
    LEARNING_SOURCE
FROM LEARNED_PATTERN
WHERE ROWNUM <= 10
ORDER BY PATTERN_ID;

SELECT '성공!' AS STATUS FROM DUAL;


-- ========================================
-- Step 5: 외래키 다시 활성화
-- ========================================

-- 외래키 재활성화
ALTER TABLE LEARNED_PATTERN ENABLE CONSTRAINT FK_PATTERN_LOG;

SELECT '완료!' AS STATUS FROM DUAL;






