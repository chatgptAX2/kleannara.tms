-- =====================================================================
--  FIX: 납품처 상세조회 ORA-00904 — BzptnDetail 엔티티 매핑 컬럼 일괄 보정
-- ---------------------------------------------------------------------
--  대상 DB   : Oracle 19C  (스키마 KNRAWMS)
--  대상 테이블: KNRAWMS.BZPTN_DETAIL
--
--  배경:
--    납품처 상세조회는 JPA 엔티티(BzptnDetail)로 조회하며, 엔티티에 @Column 으로
--    매핑된 모든 컬럼을 SELECT 한다. 이 중 하나라도 운영 Oracle 테이블에 없으면
--    상세조회 SELECT 가 ORA-00904 로 실패한다. (예: "BD1_0"."MAX_TON")
--    → 엔티티가 요구하는 컬럼을 '없을 때만' 일괄 추가하여 스키마를 일치시킨다.
--
--  타입 근거(엔티티 @Column):
--    String  → VARCHAR2(length),  Integer → NUMBER(10),
--    Double  → NUMBER,            Long(PK) → 별도(추가하지 않음, 기존 유지)
--
--  ※ 각 컬럼은 ALL_TAB_COLUMNS 확인 후 없을 때만 ADD 하므로 반복 실행해도 안전.
--    (이미 있는 컬럼은 무시. 기존 데이터/타입은 변경하지 않음)
-- =====================================================================

DECLARE
    -- 컬럼이 없으면 지정 DDL 로 추가하는 헬퍼
    PROCEDURE add_col(p_col IN VARCHAR2, p_def IN VARCHAR2) IS
        v_cnt NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_cnt
          FROM ALL_TAB_COLUMNS
         WHERE OWNER='KNRAWMS' AND TABLE_NAME='BZPTN_DETAIL'
           AND COLUMN_NAME=p_col;
        IF v_cnt = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE KNRAWMS.BZPTN_DETAIL ADD (' || p_col || ' ' || p_def || ')';
            DBMS_OUTPUT.PUT_LINE('ADDED  : ' || p_col || ' ' || p_def);
        ELSE
            DBMS_OUTPUT.PUT_LINE('EXISTS : ' || p_col);
        END IF;
    END;
BEGIN
    -- 기본/속성 컬럼 (누락 가능성 있는 것 모두 보정)
    add_col('WAREKY',         'VARCHAR2(10)');
    add_col('ROUTE_CD',       'VARCHAR2(20)');
    add_col('ITEM_GROUP',     'VARCHAR2(10)');
    add_col('AREA_CD',        'VARCHAR2(20)');
    add_col('UNLOAD_TIME',    'NUMBER(10)');
    add_col('MAX_HEIGHT',     'NUMBER');
    add_col('AUTO_ALLOC_YN',  'VARCHAR2(1)');
    add_col('FORKLIFT_YN',    'VARCHAR2(1)');
    add_col('INB_TIME_FROM1', 'VARCHAR2(6)');
    add_col('INB_TIME_TO1',   'VARCHAR2(6)');
    add_col('MAX_BOX_QTY',    'NUMBER(10)');
    add_col('DEADLINE_TIME',  'VARCHAR2(6)');
    add_col('MAX_TON',        'NUMBER(6,2)');
    add_col('DYNAMIC_DIST_M', 'NUMBER(10,2)');
    add_col('HANDWORK_YN',    'VARCHAR2(1)');
    add_col('AUTO_PLT',       'VARCHAR2(10)');
    add_col('SINGLE_ITEM_YN', 'VARCHAR2(1)');
    add_col('NY_TYPE',        'VARCHAR2(10)');
    add_col('SINGLE_HEIGHT',  'NUMBER');
    add_col('DYNAMIC_YN',     'VARCHAR2(1)');
    add_col('LTL_YN',         'VARCHAR2(1)');
    add_col('PRIORITY_YN',    'VARCHAR2(1)');
    add_col('MIN_QTSIWH',     'NUMBER');
    add_col('LATITUDE',       'NUMBER');
    add_col('LONGITUDE',      'NUMBER');
    add_col('DEL_YN',         'VARCHAR2(1)');
    add_col('CREDAT',         'VARCHAR2(8)');
    add_col('CRETIM',         'VARCHAR2(6)');
    add_col('CREUSR',         'VARCHAR2(20)');
    add_col('LMODAT',         'VARCHAR2(8)');
    add_col('LMOTIM',         'VARCHAR2(6)');
    add_col('LMOUSR',         'VARCHAR2(20)');
END;
/

-- 확인: 엔티티가 요구하는 컬럼 현황
SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, DATA_PRECISION, DATA_SCALE, NULLABLE
  FROM ALL_TAB_COLUMNS
 WHERE OWNER='KNRAWMS' AND TABLE_NAME='BZPTN_DETAIL'
 ORDER BY COLUMN_ID;
