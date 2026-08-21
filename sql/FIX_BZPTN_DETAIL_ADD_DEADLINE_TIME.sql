-- =====================================================================
--  FIX: 납품처관리 — 배차마감시간(DEADLINE_TIME) 컬럼 추가
-- ---------------------------------------------------------------------
--  대상 DB   : Oracle 19C  (스키마 KNRAWMS)
--  대상 테이블: KNRAWMS.BZPTN_DETAIL
--
--  배경:
--    납품처관리 화면(List/상세/저장)은 납품처별 '배차마감시간(DEADLINE_TIME)'을
--    조회·저장하나 운영 Oracle 의 BZPTN_DETAIL 테이블에 해당 컬럼이 없어
--    List 조회 시 ORA-00904: "D"."DEADLINE_TIME": 부적합한 식별자 오류가 발생했다.
--    → DEADLINE_TIME VARCHAR2(6) 컬럼을 추가한다. (HHMISS/HHMI 형식 문자열, NULL 허용)
--
--  참고:
--    - 엔티티(BzptnDetail) 매핑: @Column(name="DEADLINE_TIME", length=6) String
--    - 애플리케이션은 컬럼 미존재를 런타임 감지(hasCol)하여 List 조회는 우회하나,
--      실제 값 저장/조회 및 상세조회(JPA 엔티티 경로)를 위해서는 본 컬럼 추가가 필요하다.
--
--  ※ 이미 컬럼이 있으면 아래 익명블록이 중복을 무시한다.
-- =====================================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt
      FROM ALL_TAB_COLUMNS
     WHERE OWNER = 'KNRAWMS'
       AND TABLE_NAME = 'BZPTN_DETAIL'
       AND COLUMN_NAME = 'DEADLINE_TIME';

    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE KNRAWMS.BZPTN_DETAIL ADD (DEADLINE_TIME VARCHAR2(6))';
    END IF;
END;
/

-- 확인
SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, NULLABLE
  FROM ALL_TAB_COLUMNS
 WHERE OWNER='KNRAWMS' AND TABLE_NAME='BZPTN_DETAIL'
   AND COLUMN_NAME='DEADLINE_TIME';
