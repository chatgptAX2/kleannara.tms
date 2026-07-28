-- ============================================================================
--  Oracle 19C 마이그레이션
--  PS_DISPATCH_H.DISP_DATE / PS_DISPATCH_D.DISP_D_ID 컬럼 추가
--
--  배경: /api/ps-sap/list (SapRfcService.sapList) 등 SAP 연동 쿼리가
--        아래 2개 컬럼을 참조하나 운영 테이블에 존재하지 않아
--        ORA-00904(부적합한 식별자)가 발생함.
--          · SELECT ... h.DISP_DATE ... COUNT(d.DISP_D_ID) AS ITEM_CNT
--          · WHERE h.DISP_DATE >= ? / <= ?   ORDER BY h.DISP_DATE DESC
--          · UPDATE ... WHERE DISP_D_ID = ?  (update-item)
--
--  실행 계정: KNRAWMS (스키마 소유자)
--  ※ 이미 존재하는 컬럼/객체는 예외를 무시하도록 방어 처리.
-- ============================================================================

-- ── 1) PS_DISPATCH_H.DISP_DATE (배차일자 YYYYMMDD) ──────────────────────────
--    필터/정렬용 문자열 8자리. 기존 행은 DISPATCH_DT 로 백필.
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'PS_DISPATCH_H' AND COLUMN_NAME = 'DISP_DATE';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE PS_DISPATCH_H ADD (DISP_DATE VARCHAR2(8 CHAR) DEFAULT '' '' )';
        -- 기존 행 백필: 배차일자(DISPATCH_DT) 우선, 없으면 납품요청일(RQSHPD)
        EXECUTE IMMEDIATE q'[UPDATE PS_DISPATCH_H
                              SET DISP_DATE = NVL(NULLIF(TRIM(DISPATCH_DT), ''), RQSHPD)
                              WHERE DISP_DATE IS NULL OR TRIM(DISP_DATE) = '']';
        COMMIT;
    END IF;
END;
/

COMMENT ON COLUMN PS_DISPATCH_H.DISP_DATE IS '배차일자(yyyyMMdd) - SAP 연동 조회/정렬용';

CREATE INDEX IDX_PS_DISPATCH_H_DISPDATE ON PS_DISPATCH_H (DISP_DATE);


-- ── 2) PS_DISPATCH_D.DISP_D_ID (아이템 식별자, 자동 채번) ────────────────────
--    COUNT(d.DISP_D_ID), WHERE DISP_D_ID=? 등에서 사용되는 단일 식별 컬럼.
--    시퀀스 + BEFORE INSERT 트리거로 자동 채번, 기존 행은 ROWNUM 으로 백필.
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'PS_DISPATCH_D' AND COLUMN_NAME = 'DISP_D_ID';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE PS_DISPATCH_D ADD (DISP_D_ID NUMBER(12))';
    END IF;
END;
/

-- 시퀀스 (없으면 생성)
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_PS_DISPATCH_D_ID';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE SEQ_PS_DISPATCH_D_ID START WITH 1 INCREMENT BY 1 NOCACHE';
    END IF;
END;
/

-- 기존 행 백필 (NULL 인 것만 시퀀스로 채움)
DECLARE
    v_id NUMBER;
BEGIN
    FOR r IN (SELECT ROWID rid FROM PS_DISPATCH_D WHERE DISP_D_ID IS NULL ORDER BY DISPATCH_NO, SEQ) LOOP
        SELECT SEQ_PS_DISPATCH_D_ID.NEXTVAL INTO v_id FROM DUAL;
        UPDATE PS_DISPATCH_D SET DISP_D_ID = v_id WHERE ROWID = r.rid;
    END LOOP;
    COMMIT;
END;
/

-- UNIQUE 제약 (식별자 유일성 보장). 이미 있으면 무시.
DECLARE
    v_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt FROM USER_CONSTRAINTS WHERE CONSTRAINT_NAME = 'UK_PS_DISPATCH_D_ID';
    IF v_cnt = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE PS_DISPATCH_D ADD CONSTRAINT UK_PS_DISPATCH_D_ID UNIQUE (DISP_D_ID)';
    END IF;
END;
/

-- 신규 INSERT 자동 채번 트리거 (DISP_D_ID 미지정 시 시퀀스 사용)
CREATE OR REPLACE TRIGGER TRG_PS_DISPATCH_D_ID
    BEFORE INSERT ON PS_DISPATCH_D
    FOR EACH ROW
    WHEN (NEW.DISP_D_ID IS NULL)
BEGIN
    :NEW.DISP_D_ID := SEQ_PS_DISPATCH_D_ID.NEXTVAL;
END;
/

COMMENT ON COLUMN PS_DISPATCH_D.DISP_D_ID IS '배차 아이템 식별자(자동 채번) - SAP 연동 조회용';

-- ============================================================================
--  참고: 위 DDL 실행 후 /api/ps-sap/list, /items, /update-item 의
--        DISP_DATE / DISP_D_ID 참조 ORA-00904 가 해소됨.
-- ============================================================================
