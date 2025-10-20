-- ========================================
-- 증분 학습 시스템 - 최종 완성 버전
-- UW_CODE_MAPPING 실제 구조 반영
-- PRIMARY KEY: (CODE, MAIN_CODE, PERIOD_LABEL, PAY_TERM, TYPE_LABEL)
-- ========================================

-- ========================================
-- 데이터 구조 이해
-- ========================================
-- UW_CODE_MAPPING은 복합 키를 가지므로 같은 CODE가 여러 행에 존재
-- 예: CODE=21686이 납입기간별로 10년납, 15년납, 20년납... 여러 행
-- 
-- LEARNED_PATTERN은 (INSU_CD, FIELD_NAME) UNIQUE
-- 따라서: 각 CODE당 하나의 통합된 값만 저장해야 함
-- ========================================

-- ========================================
-- Step 1: LEARNED_PATTERN 완전 초기화
-- ========================================

-- 외래키 일시 비활성화 (있으면)
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE LEARNED_PATTERN DISABLE CONSTRAINT FK_PATTERN_LOG';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

-- 테이블 비우기
TRUNCATE TABLE LEARNED_PATTERN;

-- 시퀀스 재생성
BEGIN
    EXECUTE IMMEDIATE 'DROP SEQUENCE learned_pattern_seq';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

CREATE SEQUENCE learned_pattern_seq START WITH 1 INCREMENT BY 1;

SELECT '=== 초기화 완료 ===' AS INFO FROM DUAL;


-- ========================================
-- Step 2: UW_CODE_MAPPING 데이터 분석
-- ========================================

SELECT '=== UW_CODE_MAPPING 분석 ===' AS INFO FROM DUAL;

-- 전체 건수
SELECT '총 행 수: ' || COUNT(*) AS INFO FROM UW_CODE_MAPPING;

-- CODE별 행 수
SELECT 
    CODE,
    COUNT(*) AS ROW_COUNT
FROM UW_CODE_MAPPING
GROUP BY CODE
ORDER BY CODE;

SELECT '=== 분석 완료 ===' AS INFO FROM DUAL;


-- ========================================
-- Step 3: LEARNED_PATTERN 이관 (통합 방식)
-- ========================================

-- 전략: 각 CODE별로 가장 대표적인 값 하나만 선택
-- ROW_NUMBER()로 각 CODE의 첫 번째 행만 선택

-- 3-1. 보험기간(insuTerm)
-- 각 CODE별 첫 번째 PERIOD_LABEL 선택
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
        ROW_NUMBER() OVER (
            PARTITION BY CODE 
            ORDER BY 
                CASE WHEN PERIOD_LABEL = '종신' THEN 1 ELSE 2 END,
                PERIOD_LABEL
        ) AS RN
    FROM UW_CODE_MAPPING
    WHERE PERIOD_LABEL IS NOT NULL
)
WHERE RN = 1;

SELECT 'insuTerm 이관: ' || SQL%ROWCOUNT || '건' AS INFO FROM DUAL;


-- 3-2. 납입기간(payTerm)
-- 각 CODE별로 모든 납입기간을 집계하여 통합
-- Oracle 버전 호환성: DISTINCT를 서브쿼리에서 먼저 처리
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


-- 3-3. 가입나이(ageRange)
-- 각 CODE별 첫 번째 ENTRY_AGE_M 선택 (가장 넓은 범위)
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
        ROW_NUMBER() OVER (
            PARTITION BY CODE 
            ORDER BY LENGTH(ENTRY_AGE_M) DESC  -- 가장 긴 것 (상세한 것) 선택
        ) AS RN
    FROM UW_CODE_MAPPING
    WHERE ENTRY_AGE_M IS NOT NULL
)
WHERE RN = 1;

SELECT 'ageRange 이관: ' || SQL%ROWCOUNT || '건' AS INFO FROM DUAL;


-- ========================================
-- Step 4: 갱신여부 추가 (필요 시)
-- ========================================

-- UW_CODE_MAPPING에 갱신여부 정보가 있다면 추가
-- 현재는 기본값으로 '비갱신형' 설정
INSERT INTO LEARNED_PATTERN (
    PATTERN_ID, INSU_CD, FIELD_NAME, PATTERN_VALUE, 
    CONFIDENCE_SCORE, LEARNING_SOURCE, PRIORITY, IS_ACTIVE
)
SELECT 
    learned_pattern_seq.NEXTVAL,
    CODE,
    'renew',
    '비갱신형',  -- 기본값
    100,
    'UW_MAPPING',
    100,
    'Y'
FROM (
    SELECT DISTINCT CODE
    FROM UW_CODE_MAPPING
);

SELECT 'renew 이관: ' || SQL%ROWCOUNT || '건' AS INFO FROM DUAL;


COMMIT;

SELECT '=== 이관 완료! ===' AS INFO FROM DUAL;


-- ========================================
-- Step 5: 결과 확인
-- ========================================

SELECT '=== 최종 결과 ===' AS INFO FROM DUAL;

-- 전체 건수
SELECT 'LEARNED_PATTERN 총 건수: ' || COUNT(*) AS INFO FROM LEARNED_PATTERN;

-- 필드별 분포
SELECT 
    FIELD_NAME,
    COUNT(*) AS PATTERN_COUNT
FROM LEARNED_PATTERN
GROUP BY FIELD_NAME
ORDER BY FIELD_NAME;

-- 학습 소스
SELECT 
    LEARNING_SOURCE,
    COUNT(*) AS COUNT
FROM LEARNED_PATTERN
GROUP BY LEARNING_SOURCE;

-- 중복 확인 (반드시 없어야 함!)
SELECT '=== 중복 확인 (없어야 정상) ===' AS INFO FROM DUAL;

SELECT 
    INSU_CD,
    FIELD_NAME,
    COUNT(*) AS DUPLICATE_COUNT
FROM LEARNED_PATTERN
GROUP BY INSU_CD, FIELD_NAME
HAVING COUNT(*) > 1;

-- "no rows selected" 출력되면 정상!

-- 샘플 데이터 확인 (처음 15건)
SELECT '=== 샘플 데이터 ===' AS INFO FROM DUAL;

SELECT 
    PATTERN_ID,
    INSU_CD,
    FIELD_NAME,
    SUBSTR(PATTERN_VALUE, 1, 50) AS PATTERN_VALUE,
    CONFIDENCE_SCORE
FROM LEARNED_PATTERN
WHERE ROWNUM <= 15
ORDER BY INSU_CD, FIELD_NAME;

-- 외래키 재활성화
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE LEARNED_PATTERN ENABLE CONSTRAINT FK_PATTERN_LOG';
EXCEPTION WHEN OTHERS THEN NULL;
END;
/

SELECT '완료!' AS STATUS FROM DUAL;


-- ========================================
-- Step 6: 데이터 검증 (품질 체크)
-- ========================================

SELECT '=== 데이터 품질 검증 ===' AS INFO FROM DUAL;

-- 각 CODE가 4개 필드를 모두 가지고 있는지 확인
SELECT 
    INSU_CD,
    COUNT(DISTINCT FIELD_NAME) AS FIELD_COUNT
FROM LEARNED_PATTERN
GROUP BY INSU_CD
HAVING COUNT(DISTINCT FIELD_NAME) < 4
ORDER BY INSU_CD;

-- 4개 미만이면 누락된 필드가 있음
-- "no rows selected"면 모든 CODE가 4개 필드를 가짐 (정상!)

SELECT '=== 검증 완료 ===' AS INFO FROM DUAL;

