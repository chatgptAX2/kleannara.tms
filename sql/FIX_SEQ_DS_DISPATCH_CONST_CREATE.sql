-- ============================================================
-- [FIX] SEQ_DS_DISPATCH_CONST 시퀀스 생성 (부재 시)
-- 대상 DB : Oracle KNRAWMS
-- 증상    : PS제약조건관리 > '차량유형별' 등 저장 시
--           ORA-02289: sequence does not exist (SEQ_DS_DISPATCH_CONST)
-- 원인    : 스키마 배포 시 시퀀스가 생성되지 않음
--           (DS_DISPATCH_CONST INSERT가 SEQ_DS_DISPATCH_CONST.NEXTVAL 사용)
-- 조치    : 시퀀스가 없으면 현재 MAX(CONST_ID)+1 부터 시작하도록 생성.
--           애플리케이션 코드에는 이미 시퀀스 부재 시 MAX+1 폴백이 있으나,
--           시퀀스를 생성해 두면 정상 채번 경로로 동작한다.
--
-- ※ 재실행 안전: 이미 존재하면 건너뜀.
-- ============================================================
DECLARE
    v_cnt   NUMBER;
    v_start NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_cnt
      FROM ALL_SEQUENCES
     WHERE SEQUENCE_OWNER = 'KNRAWMS'
       AND SEQUENCE_NAME  = 'SEQ_DS_DISPATCH_CONST';

    IF v_cnt = 0 THEN
        -- 기존 데이터의 최대 CONST_ID + 1 을 시작값으로 (없으면 1)
        SELECT NVL(MAX(CONST_ID), 0) + 1 INTO v_start
          FROM KNRAWMS.DS_DISPATCH_CONST;

        EXECUTE IMMEDIATE
            'CREATE SEQUENCE KNRAWMS.SEQ_DS_DISPATCH_CONST ' ||
            'START WITH ' || v_start || ' INCREMENT BY 1 NOCACHE NOCYCLE';

        DBMS_OUTPUT.PUT_LINE('SEQ_DS_DISPATCH_CONST created. START WITH = ' || v_start);
    ELSE
        DBMS_OUTPUT.PUT_LINE('SEQ_DS_DISPATCH_CONST already exists. skip.');
    END IF;
END;
/

-- 확인용
SELECT SEQUENCE_NAME, LAST_NUMBER
  FROM ALL_SEQUENCES
 WHERE SEQUENCE_OWNER = 'KNRAWMS'
   AND SEQUENCE_NAME  = 'SEQ_DS_DISPATCH_CONST';
