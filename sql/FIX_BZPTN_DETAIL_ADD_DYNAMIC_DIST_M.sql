-- =====================================================================
--  FEAT: 납품처관리 — 동적 허용 거리(DYNAMIC_DIST_M) 컬럼 추가
-- ---------------------------------------------------------------------
--  대상 DB   : Oracle 19C  (스키마 KNRAWMS)
--  대상 테이블: KNRAWMS.BZPTN_DETAIL
--
--  배경:
--    납품처별로 동적 허용할 경우 허용되는 거리값(M 단위)을 저장하기 위해
--    '동적 허용 거리(DYNAMIC_DIST_M)' 컬럼을 신규 추가한다.
--    → DYNAMIC_DIST_M NUMBER(10,2)  (미터(M) 단위, NULL 허용)
--
--  참고:
--    - 엔티티(BzptnDetail) 매핑: @Column(name="DYNAMIC_DIST_M") Double
--    - 납품처관리 상세 영역(화면)에서 조회/저장한다.
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
       AND COLUMN_NAME = 'DYNAMIC_DIST_M';

    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE KNRAWMS.BZPTN_DETAIL ADD (DYNAMIC_DIST_M NUMBER(10,2))';
    END IF;
END;
/

-- 확인
SELECT COLUMN_NAME, DATA_TYPE, DATA_PRECISION, DATA_SCALE, NULLABLE
  FROM ALL_TAB_COLUMNS
 WHERE OWNER='KNRAWMS' AND TABLE_NAME='BZPTN_DETAIL'
   AND COLUMN_NAME='DYNAMIC_DIST_M';
