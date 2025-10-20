-- ========================================
-- LISTAGG DISTINCT 오류 해결
-- ORA-30482: DISTINCT 옵션은 이 함수에 사용할 수 없습니다
-- ========================================

-- 문제: Oracle 버전에 따라 LISTAGG에 DISTINCT 미지원
-- 해결: 서브쿼리에서 먼저 DISTINCT 처리

-- ========================================
-- 방법 1: 서브쿼리 DISTINCT (권장) ⭐⭐⭐⭐⭐
-- ========================================

-- ❌ 틀린 방법 (오류 발생)
/*
SELECT 
    CODE,
    LISTAGG(DISTINCT PAY_TERM, ', ') WITHIN GROUP (ORDER BY PAY_TERM)
FROM UW_CODE_MAPPING
GROUP BY CODE;
*/

-- ✅ 올바른 방법 1: 서브쿼리에서 DISTINCT
SELECT 
    CODE,
    LISTAGG(PAY_TERM, ', ') WITHIN GROUP (ORDER BY PAY_TERM) AS PAY_TERM
FROM (
    SELECT DISTINCT CODE, PAY_TERM
    FROM UW_CODE_MAPPING
    WHERE PAY_TERM IS NOT NULL
)
GROUP BY CODE;

-- ✅ 올바른 방법 2: ROW_NUMBER로 중복 제거
SELECT 
    CODE,
    LISTAGG(PAY_TERM, ', ') WITHIN GROUP (ORDER BY PAY_TERM) AS PAY_TERM
FROM (
    SELECT CODE, PAY_TERM
    FROM (
        SELECT 
            CODE, 
            PAY_TERM,
            ROW_NUMBER() OVER (PARTITION BY CODE, PAY_TERM ORDER BY ROWNUM) AS RN
        FROM UW_CODE_MAPPING
        WHERE PAY_TERM IS NOT NULL
    )
    WHERE RN = 1
)
GROUP BY CODE;


-- ========================================
-- 납입기간 이관 (완전 버전)
-- ========================================

INSERT INTO LEARNED_PATTERN (
    PATTERN_ID, INSU_CD, FIELD_NAME, PATTERN_VALUE, 
    CONFIDENCE_SCORE, LEARNING_SOURCE, PRIORITY, IS_ACTIVE
)
SELECT 
    learned_pattern_seq.NEXTVAL,
    CODE,
    'payTerm',
    AGGREGATED_PAY_TERM,
    100,
    'UW_MAPPING',
    100,
    'Y'
FROM (
    SELECT 
        CODE,
        LISTAGG(PAY_TERM, ', ') WITHIN GROUP (ORDER BY PAY_TERM) AS AGGREGATED_PAY_TERM
    FROM (
        -- 서브쿼리에서 먼저 DISTINCT 처리
        SELECT DISTINCT CODE, PAY_TERM
        FROM UW_CODE_MAPPING
        WHERE PAY_TERM IS NOT NULL
    )
    GROUP BY CODE
);

SELECT 'payTerm 이관: ' || SQL%ROWCOUNT || '건' AS INFO FROM DUAL;


-- ========================================
-- 결과 예시
-- ========================================

-- UW_CODE_MAPPING 원본
/*
CODE    PAY_TERM
21686   10년납
21686   15년납
21686   20년납
21686   30년납
*/

-- LEARNED_PATTERN 결과
/*
INSU_CD  FIELD_NAME  PATTERN_VALUE
21686    payTerm     10년납, 15년납, 20년납, 30년납
*/

-- 완벽하게 통합됨! ✅






