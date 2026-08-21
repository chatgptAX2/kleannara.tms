-- =====================================================================
--  BZPTN_DISTANCE (납품처간 이동거리) — 동적 대상 기능용 참조 스키마 / 진단
-- ---------------------------------------------------------------------
--  대상 DB   : Oracle 19C  (스키마 KNRAWMS)
--  용도       : 납품처관리 > 납품처 상세 > '동적 대상' 기능
--
--  기능 로직:
--    SELECT * FROM KNRAWMS.BZPTN_DISTANCE
--     WHERE (PTNRKY_FROM = :ptnrky OR PTNRKY_TO = :ptnrky)
--       AND USE_YN = 'Y'
--       AND DISTANCE < (BZPTN_DETAIL.DYNAMIC_DIST_M 값)
--    → 체크박스 선택 후 '삭제(미사용)' 시 USE_YN='N' 업데이트
--
--  ※ 애플리케이션(DeliveryService)이 기대하는 컬럼명:
--     PTNRKY_FROM, PTNRKY_TO, DISTANCE, DURATION, USE_YN,
--     CREDAT, CRETIM, CREUSR
--    운영 테이블의 실제 컬럼명이 다르면 아래 (1) 진단 SQL 결과를 알려주세요.
-- =====================================================================

-- (1) 진단: 테이블/컬럼 존재 여부 확인  ── 먼저 실행해서 결과 공유
SELECT COLUMN_ID, COLUMN_NAME, DATA_TYPE, DATA_LENGTH, DATA_PRECISION, DATA_SCALE, NULLABLE
  FROM ALL_TAB_COLUMNS
 WHERE OWNER='KNRAWMS' AND TABLE_NAME='BZPTN_DISTANCE'
 ORDER BY COLUMN_ID;

-- (2) 테이블이 없을 경우에만 생성 (참조 스키마)
--     이미 존재하면 이 블록은 건너뜀. 컬럼 구성이 다르면 (1) 결과로 조정.
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt
      FROM ALL_TABLES
     WHERE OWNER='KNRAWMS' AND TABLE_NAME='BZPTN_DISTANCE';

    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE KNRAWMS.BZPTN_DISTANCE (
                PTNRKY_FROM   VARCHAR2(20)  NOT NULL,   -- 거래처(출발) 코드
                PTNRKY_TO     VARCHAR2(20)  NOT NULL,   -- 거래처(도착) 코드
                DISTANCE      NUMBER(12,2),             -- 거리(m)
                DURATION      NUMBER(10),               -- 이동거리(분)
                USE_YN        VARCHAR2(1)   DEFAULT ''Y'',  -- 사용여부 (Y/N)
                CREDAT        VARCHAR2(8),              -- 생성일자 (YYYYMMDD)
                CRETIM        VARCHAR2(6),              -- 생성시간 (HHMISS)
                CREUSR        VARCHAR2(20),             -- 생성자
                CONSTRAINT PK_BZPTN_DISTANCE PRIMARY KEY (PTNRKY_FROM, PTNRKY_TO)
            )';
    END IF;
END;
/

-- (3) 조회 성능용 인덱스(없을 때만)
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM ALL_INDEXES
     WHERE OWNER='KNRAWMS' AND INDEX_NAME='IX_BZPTN_DISTANCE_FROM';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX KNRAWMS.IX_BZPTN_DISTANCE_FROM ON KNRAWMS.BZPTN_DISTANCE (PTNRKY_FROM, USE_YN)';
    END IF;

    SELECT COUNT(*) INTO v_cnt FROM ALL_INDEXES
     WHERE OWNER='KNRAWMS' AND INDEX_NAME='IX_BZPTN_DISTANCE_TO';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX KNRAWMS.IX_BZPTN_DISTANCE_TO ON KNRAWMS.BZPTN_DISTANCE (PTNRKY_TO, USE_YN)';
    END IF;
END;
/

-- (4) 확인
SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, NULLABLE
  FROM ALL_TAB_COLUMNS
 WHERE OWNER='KNRAWMS' AND TABLE_NAME='BZPTN_DISTANCE'
 ORDER BY COLUMN_ID;
