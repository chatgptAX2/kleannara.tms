-- =====================================================================
--  FIX: PS제약조건관리 "원지+판지 혼적 허용(ALLOW_MATERIAL_MIX)" 저장 실패
--        오류 메시지: "DB에서 해당 제약조건을 찾을 수 없습니다 (마스터 등록 필요)"
-- ---------------------------------------------------------------------
--  대상 DB   : Oracle 19C  (스키마 KNRAWMS)
--  대상 테이블: KNRAWMS.DS_DISPATCH_CONST        (제약조건 마스터)
--              KNRAWMS.DS_DISPATCH_CONST_SET_ITEM (세트-항목 연결)  ← 선택
--
--  원인:
--    ALLOW_MATERIAL_MIX 키는 화면 드롭다운(DCON_PARAM_META)에는 노출되지만
--    DS_DISPATCH_CONST 마스터 행이 DB에 존재하지 않아, 저장 시 프론트의
--    마스터 매칭(_dconSpSave 3-tier)이 실패하여 저장이 중단됨.
--    ( MIX_3D_CHECK_YN 은 통상 CONST_ID=70 로 이미 존재하나, 없을 수도 있어
--      본 스크립트에서 함께 find-or-create 처리한다. )
--
--  동작:
--    ① 두 키(ALLOW_MATERIAL_MIX, MIX_3D_CHECK_YN)의 마스터 행이 없으면
--       첫 번째 프로파일(PROFILE_ID 최소값)에 신규 INSERT.  이미 있으면 무시.
--    ② (선택) 특정 세트(SET_ID)에 항목으로 자동 연결 — 하단 블록 참고.
--
--  ※ CONST_ID / ITEM_ID 는 시퀀스 미사용 환경을 고려해 MAX+1 로 채번한다.
--  ※ CONST_TYPE 은 재질혼적 노멀라이저(_dconNormalizeMixMaterialType)가
--     화면에서 CARGO → MIX_MATERIAL 로 표시 변환하므로, 저장은 'CARGO' 로 둔다.
--     (자동배차 엔진은 CONST_KEY 만 참조하므로 CONST_TYPE 값은 엔진 동작에 무관)
-- =====================================================================

-- ── ① ALLOW_MATERIAL_MIX 마스터 find-or-create ───────────────────────
INSERT INTO KNRAWMS.DS_DISPATCH_CONST
    (CONST_ID, PROFILE_ID, CONST_TYPE, CONST_KEY, CONST_VALUE, CONST_OP,
     TARGET_ID, TARGET_NM, ACTIVE_YN, NOTE, SORT_SEQ, CREDAT, LMODAT)
SELECT
    (SELECT NVL(MAX(CONST_ID),0)+1 FROM KNRAWMS.DS_DISPATCH_CONST),
    (SELECT MIN(PROFILE_ID)       FROM KNRAWMS.DS_DISPATCH_PROFILE),
    'CARGO',                                  -- CONST_TYPE (화면에서 MIX_MATERIAL 로 표시)
    'ALLOW_MATERIAL_MIX',                     -- CONST_KEY
    'N',                                      -- CONST_VALUE (기본 N: 재질별 차량 분리)
    '=',                                      -- CONST_OP
    NULL, NULL,                               -- TARGET_ID, TARGET_NM
    'Y',                                      -- ACTIVE_YN
    '원지+판지 혼적 허용 (Y=한 차량 혼적 / N=재질별 차량 분리 배차)',  -- NOTE
    900,                                      -- SORT_SEQ
    TO_CHAR(SYSDATE,'YYYYMMDD'),
    TO_CHAR(SYSDATE,'YYYYMMDD')
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM KNRAWMS.DS_DISPATCH_CONST
     WHERE CONST_KEY = 'ALLOW_MATERIAL_MIX'
);

-- ── ② MIX_3D_CHECK_YN 마스터 find-or-create (누락 대비) ──────────────
INSERT INTO KNRAWMS.DS_DISPATCH_CONST
    (CONST_ID, PROFILE_ID, CONST_TYPE, CONST_KEY, CONST_VALUE, CONST_OP,
     TARGET_ID, TARGET_NM, ACTIVE_YN, NOTE, SORT_SEQ, CREDAT, LMODAT)
SELECT
    (SELECT NVL(MAX(CONST_ID),0)+1 FROM KNRAWMS.DS_DISPATCH_CONST),
    (SELECT MIN(PROFILE_ID)       FROM KNRAWMS.DS_DISPATCH_PROFILE),
    'CARGO',
    'MIX_3D_CHECK_YN',
    'Y',                                      -- CONST_VALUE (기본 Y: 3D 물리검증 적용)
    '=',
    NULL, NULL,
    'Y',
    '[혼적] 3D 물리검증 활성 (Y=바닥면적·높이 3D 검증 / N=중량만)',
    901,
    TO_CHAR(SYSDATE,'YYYYMMDD'),
    TO_CHAR(SYSDATE,'YYYYMMDD')
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM KNRAWMS.DS_DISPATCH_CONST
     WHERE CONST_KEY = 'MIX_3D_CHECK_YN'
);

COMMIT;

-- ── 확인 쿼리 ────────────────────────────────────────────────────────
--  두 키의 마스터 행이 정상 생성되었는지 확인한다.
SELECT CONST_ID, PROFILE_ID, CONST_TYPE, CONST_KEY, CONST_VALUE,
       CONST_OP, ACTIVE_YN, SORT_SEQ
  FROM KNRAWMS.DS_DISPATCH_CONST
 WHERE CONST_KEY IN ('ALLOW_MATERIAL_MIX','MIX_3D_CHECK_YN')
 ORDER BY CONST_KEY;


-- =====================================================================
--  (선택) ③ 특정 세트에 항목으로 자동 연결
-- ---------------------------------------------------------------------
--  마스터만 만들면 화면 [항목 선택] 후 저장이 정상 동작한다.
--  아래 블록은 "특정 세트에 지금 즉시 활성화"하고 싶을 때만 실행한다.
--  :SET_ID 를 대상 세트 ID 로 바꾸고, PARAM_VALUE(Y/N)는 원하는 값으로 설정.
--
--  ※ SET_ID 확인: SELECT SET_ID, SET_NM FROM KNRAWMS.DS_DISPATCH_CONST_SET ORDER BY SET_ID;
-- =====================================================================
/*
INSERT INTO KNRAWMS.DS_DISPATCH_CONST_SET_ITEM
    (ITEM_ID, SET_ID, CONST_ID, ACTIVE_YN, PARAM_VALUE)
SELECT
    (SELECT NVL(MAX(ITEM_ID),0)+1 FROM KNRAWMS.DS_DISPATCH_CONST_SET_ITEM),
    &SET_ID,                                                  -- ← 대상 세트 ID
    (SELECT CONST_ID FROM KNRAWMS.DS_DISPATCH_CONST WHERE CONST_KEY='ALLOW_MATERIAL_MIX'),
    'Y',                                                      -- 항목 활성
    'Y'                                                       -- PARAM_VALUE (혼적 허용=Y / 분리=N)
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM KNRAWMS.DS_DISPATCH_CONST_SET_ITEM
     WHERE SET_ID = &SET_ID
       AND CONST_ID = (SELECT CONST_ID FROM KNRAWMS.DS_DISPATCH_CONST WHERE CONST_KEY='ALLOW_MATERIAL_MIX')
);

COMMIT;
*/
