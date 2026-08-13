-- =====================================================================
--  FIX: 서류관리 파일 업로드 — 운행일자(OP_DATE) 컬럼 추가
-- ---------------------------------------------------------------------
--  대상 DB   : Oracle 19C  (스키마 KNRAWMS)
--  대상 테이블: KNRAWMS.DOC_FILE
--
--  배경:
--    프론트(_dmRenderFileTable / dmDoUpload)는 파일별 '운행일자(OP_DATE)'를
--    전송·표시하나 DOC_FILE 테이블에 해당 컬럼이 없어 저장/조회가 불가했다.
--    → OP_DATE VARCHAR2(8) 컬럼을 추가한다. (YYYYMMDD 형식, NULL 허용)
--
--  ※ 이미 컬럼이 있으면 ORA-01430 이 발생하나 무해하다(아래 익명블록은 중복 무시).
-- =====================================================================

DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt
      FROM ALL_TAB_COLUMNS
     WHERE OWNER = 'KNRAWMS'
       AND TABLE_NAME = 'DOC_FILE'
       AND COLUMN_NAME = 'OP_DATE';

    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE KNRAWMS.DOC_FILE ADD (OP_DATE VARCHAR2(8))';
    END IF;
END;
/

-- 확인
SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH, NULLABLE
  FROM ALL_TAB_COLUMNS
 WHERE OWNER='KNRAWMS' AND TABLE_NAME='DOC_FILE'
   AND COLUMN_NAME='OP_DATE';
