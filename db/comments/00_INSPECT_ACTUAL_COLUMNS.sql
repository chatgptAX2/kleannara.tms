-- =====================================================================
--  [사전 조회] TMS_* 테이블의 '실제' 컬럼 목록 조회
--  ---------------------------------------------------------------------
--  · 목적: 코멘트 DDL 을 실제 컬럼과 100% 일치시키기 위해, 운영 DB의
--          실제 컬럼명을 먼저 조회한다.
--  · 사용: 아래 쿼리를 실행하여 결과(TABLE_NAME, COLUMN_NAME, ...)를
--          회신해 주시면, 그 목록에 정확히 맞춘 COMMENT DDL 을 재생성합니다.
--  · 근거: 서비스 코드(Entity)와 실제 테이블 컬럼명이 달라(예: TMS_ROUTE_COST
--          COST_AMT→COST, EFF_DATE→DATE_START) 코멘트 실행이 실패했음.
--  =====================================================================

-- (A) 전체 TMS_* 테이블의 실제 컬럼 목록 (권장: 이 결과를 회신)
SELECT  t.TABLE_NAME,
        t.COLUMN_ID,
        t.COLUMN_NAME,
        t.DATA_TYPE,
        t.DATA_LENGTH,
        t.NULLABLE,
        c.COMMENTS AS CURRENT_COMMENT      -- 기존 코멘트(있으면)
FROM    ALL_TAB_COLUMNS  t
LEFT JOIN ALL_COL_COMMENTS c
       ON c.OWNER = t.OWNER
      AND c.TABLE_NAME  = t.TABLE_NAME
      AND c.COLUMN_NAME = t.COLUMN_NAME
WHERE   t.OWNER = 'KNRAWMS'
  AND   t.TABLE_NAME IN (
          'TMS_DS_VEHICLE',
          'TMS_DS_INCH12',
          'TMS_DS_INCH3',
          'TMS_DS_DISPATCH_OBJECTIVE',
          'TMS_DS_DISPATCH_PROFILE',
          'TMS_DS_DISPATCH_CONST',
          'TMS_DS_DISPATCH_CONST_SET',
          'TMS_DS_DISPATCH_CONST_SET_ITEM',
          'TMS_PS_DISPATCH_H',
          'TMS_PS_DISPATCH_D',
          'TMS_ROUTE_COST',
          'TMS_DOC_FOLDER',
          'TMS_DOC_FILE',
          'TMS_SHPDH',
          'TMS_SHPDI'
        )
ORDER BY t.TABLE_NAME, t.COLUMN_ID;


-- (B) 테이블 목록만 빠르게 확인하고 싶을 때
-- SELECT TABLE_NAME FROM ALL_TABLES
-- WHERE OWNER='KNRAWMS' AND TABLE_NAME LIKE 'TMS_%' ORDER BY TABLE_NAME;
