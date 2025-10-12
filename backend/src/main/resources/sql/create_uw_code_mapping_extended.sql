-- UW_CODE_MAPPING 테이블 확장 스키마 (LLM 프롬프트 템플릿 적용)
-- 날짜: 2025-10-12

-- 1. 기존 테이블이 있으면 삭제
DROP TABLE UW_CODE_MAPPING CASCADE CONSTRAINTS;

-- 2. 확장된 UW_CODE_MAPPING 테이블 생성
CREATE TABLE UW_CODE_MAPPING (
    -- 기존 컬럼들
    SRC_FILE VARCHAR2(20),
    CODE VARCHAR2(10) PRIMARY KEY,
    PRODUCT_NAME VARCHAR2(200),
    MAIN_CODE VARCHAR2(10),
    PERIOD_LABEL VARCHAR2(100),
    PERIOD_VALUE NUMBER,
    PAY_TERM VARCHAR2(200),
    ENTRY_AGE_M VARCHAR2(500),
    ENTRY_AGE_F VARCHAR2(500),
    
    -- LLM 프롬프트 템플릿에서 추가된 컬럼들
    PRODUCT_GROUP VARCHAR2(50),    -- 주계약/선택특약
    TYPE_LABEL VARCHAR2(50),       -- 최초계약
    PERIOD_KIND VARCHAR2(10),      -- E(종신)/S(세만기)/N(년납형)/R(갱신)
    CLASS_TAG VARCHAR2(50)         -- MAIN/A_OPTION/A_RENEWAL 등
);

-- 3. 인덱스 생성 (성능 최적화)
CREATE INDEX IDX_UW_CODE_MAPPING_SRC_FILE ON UW_CODE_MAPPING(SRC_FILE);
CREATE INDEX IDX_UW_CODE_MAPPING_MAIN_CODE ON UW_CODE_MAPPING(MAIN_CODE);
CREATE INDEX IDX_UW_CODE_MAPPING_PRODUCT_GROUP ON UW_CODE_MAPPING(PRODUCT_GROUP);
CREATE INDEX IDX_UW_CODE_MAPPING_PERIOD_KIND ON UW_CODE_MAPPING(PERIOD_KIND);

-- 4. 기존 데이터 마이그레이션 (새 컬럼에 기본값 설정)
INSERT INTO UW_CODE_MAPPING VALUES 
('UW21239', '79525', '(무)다사랑암진단특약', '21686', '종신', 999, '10년납, 15년납, 20년납, 30년납', 
 '10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~70,여:15~70)', 
 '10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~70,여:15~70)',
 '선택특약', '최초계약', 'E', 'A_OPTION');

INSERT INTO UW_CODE_MAPPING VALUES 
('UW21239', '79527', '(무)다사랑소액암New보장특약', '21686', '종신', 999, '10년납, 15년납, 20년납, 30년납', 
 '10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~70,여:15~70)', 
 '10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~70,여:15~70)',
 '선택특약', '최초계약', 'E', 'A_OPTION');

-- 5. 실손상품 샘플 데이터 (LLM 프롬프트 규칙 적용)
INSERT INTO UW_CODE_MAPPING VALUES 
('UW16932', '21690', '(무)실손의료비보험 (종합형, 비위험, 최초계약)', '21690', '종신', 999, '10년납, 15년납, 20년납, 30년납', 
 '10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~70,여:15~70)', 
 '10년납(남:15~80,여:15~80), 15년납(남:15~70,여:15~70), 20년납(남:15~70,여:15~70), 30년납(남:15~70,여:15~70)',
 '주계약', '최초계약', 'E', 'MAIN');

INSERT INTO UW_CODE_MAPPING VALUES 
('UW16932', '21704', '(무)기본형 실손의료비보험 (급여 실손의료비)(갱신형) - 질병급여형', '21690', '갱신형', 999, '전기납', 
 '15~80세', '15~80세', '15~80세', '15~80세',
 '선택특약', '최초계약', 'R', 'A_OPTION');

INSERT INTO UW_CODE_MAPPING VALUES 
('UW16932', '21706', '(무)기본형 실손의료비보험 (급여 실손의료비)(갱신형) - 상해급여형 비위험', '21690', '갱신형', 999, '전기납', 
 '15~80세', '15~80세', '15~80세', '15~80세',
 '선택특약', '최초계약', 'R', 'A_OPTION');

-- 6. 컬럼 설명 주석 추가
COMMENT ON COLUMN UW_CODE_MAPPING.PRODUCT_GROUP IS '상품 그룹: 주계약/선택특약';
COMMENT ON COLUMN UW_CODE_MAPPING.TYPE_LABEL IS '계약 유형: 최초계약';
COMMENT ON COLUMN UW_CODE_MAPPING.PERIOD_KIND IS '기간 종류: E(종신)/S(세만기)/N(년납형)/R(갱신)';
COMMENT ON COLUMN UW_CODE_MAPPING.CLASS_TAG IS '분류 태그: MAIN(주계약)/A_OPTION(선택특약)/A_RENEWAL(갱신특약)';

-- 7. 제약 조건 추가
ALTER TABLE UW_CODE_MAPPING ADD CONSTRAINT CHK_PRODUCT_GROUP 
    CHECK (PRODUCT_GROUP IN ('주계약', '선택특약'));

ALTER TABLE UW_CODE_MAPPING ADD CONSTRAINT CHK_PERIOD_KIND 
    CHECK (PERIOD_KIND IN ('E', 'S', 'N', 'R'));

ALTER TABLE UW_CODE_MAPPING ADD CONSTRAINT CHK_TYPE_LABEL 
    CHECK (TYPE_LABEL = '최초계약');

COMMIT;
