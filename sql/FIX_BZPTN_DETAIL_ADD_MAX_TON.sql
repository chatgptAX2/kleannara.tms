-- =====================================================================
--  FIX: 납품처관리 — 최대적재중량(MAX_TON) 컬럼 추가
-- ---------------------------------------------------------------------
--  대상 DB   : Oracle 19C  (스키마 KNRAWMS)
--  대상 테이블: KNRAWMS.BZPTN_DETAIL
--
--  배경:
--    납품처관리 화면(List/상세/저장)은 납품처별 '최대적재중량(MAX_TON, ton)'을
--    조회·저장하나 운영 Oracle 의 BZPTN_DETAIL 테이블에 해당 컬럼이 없어
--    List 조회 시 ORA-00904: "D"."MAX_TON": 부적합한 식별자 오류가 발생했다.
--    → MAX_TON NUMBER(6,2) 컬럼을 추가한다. (ton 단위, NULL 허용)
--
--  참고:
--    - MariaDB DDL(sql/module-delivery/01_schema.sql)에는 이미
--      MAX_TON DECIMAL(6,2) 로 정의되어 있으나, 운영 데이터는 Oracle
--      KNRAWMS.BZPTN_DETAIL 을 사용하므로 이 스크립트로 Oracle 에 반영한다.
--    - 애플리케이션은 컬럼 미존재를 런타임 감지(hasMaxTonColumn)하여
--      우회하나, 실제 값 저장/조회를 위해서는 본 컬럼 추가가 필요하다.
--
--  ※ 이미 컬럼이 있으면 ORA-01430 이 발생하나 무해하다(아래 익명블록은 중복 무시).
-- =====================================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt
      FROM ALL_TAB_COLUMNS
     WHERE OWNER = 'KNRAWMS'
       AND TABLE_NAME = 'BZPTN_DETAIL'
       AND COLUMN_NAME = 'MAX_TON';

    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE KNRAWMS.BZPTN_DETAIL ADD (MAX_TON NUMBER(6,2))';
    END IF;
END;
/

-- 확인
SELECT COLUMN_NAME, DATA_TYPE, DATA_PRECISION, DATA_SCALE, NULLABLE
  FROM ALL_TAB_COLUMNS
 WHERE OWNER='KNRAWMS' AND TABLE_NAME='BZPTN_DETAIL'
   AND COLUMN_NAME='MAX_TON';
