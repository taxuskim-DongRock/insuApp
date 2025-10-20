-- ========================================
-- 깔끔하게 처음부터 시작하기
-- UNIQUE 제약 조건 오류 완전 해결
-- ========================================

-- ========================================
-- Step 1: 기존 데이터 완전 정리
-- ========================================

-- 1. LEARNED_PATTERN 모든 데이터 삭제
DELETE FROM LEARNED_PATTERN;

-- 2. FEW_SHOT_EXAMPLE 모든 데이터 삭제 (외래키 때문에)
DELETE FROM FEW_SHOT_EXAMPLE;

-- 3. CORRECTION_LOG 테스트 데이터 삭제 (선택)
-- DELETE FROM CORRECTION_LOG WHERE USER_ID = 'admin';

-- 4. 시퀀스 초기화
DROP SEQUENCE learned_pattern_seq;
CREATE SEQUENCE learned_pattern_seq START WITH 1 INCREMENT BY 1;

DROP SEQUENCE few_shot_example_seq;
CREATE SEQUENCE few_shot_example_seq START WITH 1 INCREMENT BY 1;

COMMIT;

SELECT '=== 정리 완료 ===' AS INFO FROM DUAL;


-- ========================================
-- Step 2: UW_CODE_MAPPING 데이터 확인
-- ========================================

SELECT '=== UW_CODE_MAPPING 데이터 확인 ===' AS INFO FROM DUAL;

SELECT COUNT(*) AS TOTAL_COUNT FROM UW_CODE_MAPPING;

SELECT 
    CODE,
    PRODUCT_NAME,
    PERIOD_LABEL,
    SUBSTR(PAY_TERM, 1, 30) AS PAY_TERM,
    SUBSTR(ENTRY_AGE_M, 1, 30) AS ENTRY_AGE_M
FROM UW_CODE_MAPPING
WHERE ROWNUM <= 5;


-- ========================================
-- Step 3: LEARNED_PATTERN으로 이관 (단순 INSERT)
-- ========================================

-- 보험기간(insuTerm) 패턴
INSERT INTO LEARNED_PATTERN (
    PATTERN_ID, INSU_CD, FIELD_NAME, PATTERN_VALUE, 
    CONFIDENCE_SCORE, LEARNING_SOURCE, PRIORITY, IS_ACTIVE, CREATED_AT
)
SELECT 
    learned_pattern_seq.NEXTVAL,
    CODE,
    'insuTerm',
    PERIOD_LABEL,
    100,
    'UW_MAPPING',
    100,
    'Y',
    CURRENT_TIMESTAMP
FROM (
    SELECT DISTINCT CODE, PERIOD_LABEL
    FROM UW_CODE_MAPPING
    WHERE PERIOD_LABEL IS NOT NULL
    ORDER BY CODE
);

SELECT 'insuTerm 이관 완료: ' || SQL%ROWCOUNT || '건' AS INFO FROM DUAL;


-- 납입기간(payTerm) 패턴
INSERT INTO LEARNED_PATTERN (
    PATTERN_ID, INSU_CD, FIELD_NAME, PATTERN_VALUE, 
    CONFIDENCE_SCORE, LEARNING_SOURCE, PRIORITY, IS_ACTIVE, CREATED_AT
)
SELECT 
    learned_pattern_seq.NEXTVAL,
    CODE,
    'payTerm',
    PAY_TERM,
    100,
    'UW_MAPPING',
    100,
    'Y',
    CURRENT_TIMESTAMP
FROM (
    SELECT DISTINCT CODE, PAY_TERM
    FROM UW_CODE_MAPPING
    WHERE PAY_TERM IS NOT NULL
    ORDER BY CODE
);

SELECT 'payTerm 이관 완료: ' || SQL%ROWCOUNT || '건' AS INFO FROM DUAL;


-- 가입나이(ageRange) 패턴
INSERT INTO LEARNED_PATTERN (
    PATTERN_ID, INSU_CD, FIELD_NAME, PATTERN_VALUE, 
    CONFIDENCE_SCORE, LEARNING_SOURCE, PRIORITY, IS_ACTIVE, CREATED_AT
)
SELECT 
    learned_pattern_seq.NEXTVAL,
    CODE,
    'ageRange',
    ENTRY_AGE_M,
    100,
    'UW_MAPPING',
    100,
    'Y',
    CURRENT_TIMESTAMP
FROM (
    SELECT DISTINCT CODE, ENTRY_AGE_M
    FROM UW_CODE_MAPPING
    WHERE ENTRY_AGE_M IS NOT NULL
    ORDER BY CODE
);

SELECT 'ageRange 이관 완료: ' || SQL%ROWCOUNT || '건' AS INFO FROM DUAL;


COMMIT;

SELECT '=== 이관 완료! ===' AS INFO FROM DUAL;


-- ========================================
-- Step 4: 결과 확인
-- ========================================

SELECT '=== 최종 결과 ===' AS INFO FROM DUAL;

-- 전체 건수
SELECT COUNT(*) AS TOTAL_PATTERNS FROM LEARNED_PATTERN;

-- 필드별 분포
SELECT 
    FIELD_NAME,
    COUNT(*) AS CNT
FROM LEARNED_PATTERN
GROUP BY FIELD_NAME
ORDER BY FIELD_NAME;

-- 학습 소스별 분포
SELECT 
    LEARNING_SOURCE,
    COUNT(*) AS CNT
FROM LEARNED_PATTERN
GROUP BY LEARNING_SOURCE;

-- 상위 10건 샘플 데이터
SELECT 
    PATTERN_ID,
    INSU_CD,
    FIELD_NAME,
    SUBSTR(PATTERN_VALUE, 1, 40) AS PATTERN_VALUE,
    CONFIDENCE_SCORE,
    LEARNING_SOURCE
FROM LEARNED_PATTERN
WHERE ROWNUM <= 10
ORDER BY PATTERN_ID;

-- 중복 확인 (있으면 안 됨!)
SELECT 
    INSU_CD,
    FIELD_NAME,
    COUNT(*) AS DUPLICATE_COUNT
FROM LEARNED_PATTERN
GROUP BY INSU_CD, FIELD_NAME
HAVING COUNT(*) > 1;

-- 중복이 없으면 "no rows selected" 출력됨 (정상!)

SELECT '완료!' AS STATUS FROM DUAL;






