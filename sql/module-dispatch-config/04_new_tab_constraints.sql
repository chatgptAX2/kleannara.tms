-- ============================================================
-- [마이그레이션] PS제약조건관리 신규 탭 — DS_DISPATCH_CONST 데이터
-- 대상 DB : TMS MariaDB (integration)
-- 생성일  : 2026-07-13
-- 목적    : 첨부파일(TMS 배차최적화 제약조건_20260611.xlsx) 기반
--           원지/판지/혼합적재/동적구역 신규 탭 제약조건 데이터 등록
-- 
-- ■ 신규 탭 12개:
--   원지: ROLL_UNIT, ROLL_STACK, ROLL_INCH_MIX, ROLL_3D_VERIFY
--   판지: BOARD_CBM_WEIGHT, BOARD_BULK_SPLIT, BOARD_FLEX_SPLIT, BOARD_3D_VERIFY
--   혼적: MIX_Z_AXIS, MIX_Y_LIFO, MIX_DUAL_VERIFY
--   동적: DYNAMIC_ZONE
--
-- ■ 증상: "기본제약SET" 선택 시 위 탭들이 안 보임
-- ■ 원인: MariaDB DS_DISPATCH_CONST 테이블에 해당 CONST_TYPE 행이 없어
--         _dconSpRenderTabs()의 typeCnt[t] === undefined → 탭 미표시
-- ■ 해결: DS_DISPATCH_CONST에 데이터 삽입 후
--         DS_DISPATCH_CONST_SET_ITEM에도 SET_ID별로 연결
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- Step 1: DS_DISPATCH_CONST — 신규 탭 기본 제약조건 데이터 삽입
--         (INSERT IGNORE: 이미 존재하면 건너뜀)
-- ─────────────────────────────────────────────────────────────

INSERT IGNORE INTO DS_DISPATCH_CONST
  (PROFILE_ID, CONST_TYPE, CONST_KEY, CONST_VALUE, CONST_OP, TARGET_ID, TARGET_NM, ACTIVE_YN, NOTE, SORT_SEQ, CREDAT, LMODAT)
VALUES
  -- ── 원지① 단위 배차 (ROLL_UNIT) ── 엑셀§2-1
  (1, 'ROLL_UNIT', 'ROLL_INTEGER_ONLY',  'Y', '=', '', '', 'Y', '원지 정수 롤 단위 강제 (분할 절대 불가)',                        1000, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'ROLL_UNIT', 'ROLL_SPLIT_ALLOWED', 'N', '=', '', '', 'Y', '원지 분할 배차 금지 (엑셀§2-1)',                               1010, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'ROLL_UNIT', 'ROLL_MIN_QTY',       '1', '>=','', '', 'Y', '원지 최소 배차 수량 (1롤)',                                    1020, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),

  -- ── 원지② 다단 적재·높이 (ROLL_STACK) ── 엑셀§2-2
  (1, 'ROLL_STACK', 'ROLL_MAX_TIER',         '3',    '<=','', '', 'Y', '롤 최대 적재 단수 Hard Cap (3단)',                      1100, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'ROLL_STACK', 'ROLL_PALLET_DEDUCT_M',  '0.15', '=', '', '', 'Y', '파레트 높이 차감값 (m) — 0.15m 기본',                  1110, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'ROLL_STACK', 'ROLL_PALLET_APPLY_YN',  'Y',    '=', '', '', 'Y', '파레트 차감 적용 여부 (납품처 FORKLIFT_YN=N 시 적용)', 1120, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'ROLL_STACK', 'ROLL_HEIGHT_MARGIN_M',  '0',    '=', '', '', 'Y', '적재 높이 안전 여유 마진 (m)',                          1130, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),

  -- ── 원지③ 인치·평량 혼합 (ROLL_INCH_MIX) ── 엑셀§2-3
  (1, 'ROLL_INCH_MIX', 'ROLL_INCH_MIX_ALLOW',   'Y',               '=', '', '', 'Y', '인치/평량 혼합 오더 허용 (엑셀§2-3)',               1200, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'ROLL_INCH_MIX', 'ROLL_2D_PACK_ENGINE',    'Y',               '=', '', '', 'Y', '혼합 규격 시 2D 바닥 패킹 연산 적용',               1210, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'ROLL_INCH_MIX', 'ROLL_SAME_INCH_FORCE',   'N',               '=', '', '', 'Y', '동일 인치 강제 여부 (N=혼재허용)',                   1220, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'ROLL_INCH_MIX', 'ROLL_REF_12INCH_LT300',  '2,3,4,6,8,8,8',  '=', '', '', 'Y', '12인치/평량300미만 차종별 1단 기준수(1.4t~18t)',    1230, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'ROLL_INCH_MIX', 'ROLL_REF_12INCH_GE300',  '2,3,4,5,7,7,7',  '=', '', '', 'Y', '12인치/평량300이상 차종별 1단 기준수(1.4t~18t)',    1240, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'ROLL_INCH_MIX', 'ROLL_REF_3INCH_LT300',   '3,5,10,12,14,14,15','=','','', 'Y', '3인치/평량300미만 차종별 1단 기준수(1.4t~18t)',    1250, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'ROLL_INCH_MIX', 'ROLL_REF_3INCH_GE300',   '3,5,10,12,14,14,15','=','','', 'Y', '3인치/평량300이상 차종별 1단 기준수(1.4t~18t)',    1260, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),

  -- ── 원지④ 3D 물리 검증 (ROLL_3D_VERIFY) ── 엑셀§2-4
  (1, 'ROLL_3D_VERIFY', 'ROLL_3D_CHECK_YN',      'Y',      '=', '', '', 'Y', '원지 3D 블록 검증 활성 (Dead Space 포함)',           1300, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'ROLL_3D_VERIFY', 'ROLL_3D_DEAD_SPACE_PCT', '0',     '<=','', '', 'Y', 'Dead Space 허용 비율 (%) — 0=허용안함',              1310, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'ROLL_3D_VERIFY', 'ROLL_OVERSIZE_ACTION',  'SPLIT',  '=', '', '', 'Y', '치수 초과 시 처리 방식 (SPLIT=분할/REJECT=제외)',    1320, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),

  -- ── 판지① CBM·중량 이중 검증 (BOARD_CBM_WEIGHT) ── 엑셀§3-1
  (1, 'BOARD_CBM_WEIGHT', 'BOARD_CBM_CHECK_YN',    'Y',   '=', '', '', 'Y', '판지 CBM 자동계산 + Double-Threshold 검증 활성 (엑셀§3-1)', 1400, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'BOARD_CBM_WEIGHT', 'BOARD_MAX_CBM_RATIO',   '100', '<=','', '', 'Y', '가용 적재함 CBM 상한 (%)',                                  1410, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'BOARD_CBM_WEIGHT', 'BOARD_MAX_TON_RATIO',   '100', '<=','', '', 'Y', '중량 상한 (%) — Double-Threshold 중량 기준',                1420, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'BOARD_CBM_WEIGHT', 'BOARD_DUAL_THRESHOLD',  'Y',   '=', '', '', 'Y', '중량+CBM 동시 초과 이중 임계치 검증 활성',                  1430, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),

  -- ── 판지② 벌크·속포장 제약 (BOARD_BULK_SPLIT) ── 엑셀§3-2
  (1, 'BOARD_BULK_SPLIT', 'BOARD_BULK_INTEGER_ONLY',  'Y',     '=', '', '', 'Y', '벌크 1PLT 단위 강제 (개체 내 분할 불가)',          1500, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'BOARD_BULK_SPLIT', 'BOARD_INNER_SPLIT_ALLOW',  'Y',     '=', '', '', 'Y', '속포장 속 단위 분할 허용',                         1510, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'BOARD_BULK_SPLIT', 'BOARD_INNER_STACK_ALLOW',  'Y',     '=', '', '', 'Y', '분할된 속을 판지 위 추가 적재 허용',                1520, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'BOARD_BULK_SPLIT', 'BOARD_INNER_OVERFLOW_ACT', 'SPLIT', '=', '', '', 'Y', '속포장 초과 시 처리 (SPLIT=다음 차량 배정)',        1530, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),

  -- ── 판지③ 유연 분할 선적 (BOARD_FLEX_SPLIT) ── 엑셀§3-3
  (1, 'BOARD_FLEX_SPLIT', 'BOARD_FLEX_SPLIT_YN',  'Y',  '=', '', '', 'Y', '유연 분할 선적 활성 — 차량 한계 시 초과분만 후속 차량 (엑셀§3-3)', 1600, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'BOARD_FLEX_SPLIT', 'BOARD_SPLIT_UNIT',      'EA', '=', '', '', 'Y', '분할 단위 (EA=낱개속, PLT=파레트)',                              1610, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'BOARD_FLEX_SPLIT', 'BOARD_SPLIT_OVERFLOW',  'Y',  '=', '', '', 'Y', '초과분(속단위) 후속 차량 정확 배정 활성',                        1620, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),

  -- ── 판지④ 3D 물리 검증 (BOARD_3D_VERIFY) ── 엑셀§3-4
  (1, 'BOARD_3D_VERIFY', 'BOARD_3D_CHECK_YN',      'Y',   '=', '', '', 'Y', '판지 3D 블록 검증 활성 (Dead Space 포함)',  1700, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'BOARD_3D_VERIFY', 'BOARD_3D_DEAD_SPACE_PCT', '0',  '<=','', '', 'Y', '판지 Dead Space 허용 비율 (%)',             1710, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'BOARD_3D_VERIFY', 'BOARD_HEIGHT_MAX_M',      '2.4','<=','', '', 'Y', '판지 스택 최대 높이 (m, 기본 2.4m)',        1720, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),

  -- ── 혼적① Z축 수직 적재 순서 (MIX_Z_AXIS) ── 엑셀§4-1
  (1, 'MIX_Z_AXIS', 'MIX_ROLL_BOTTOM_FORCE', 'Y', '=', '', '', 'Y', '원지 하단·판지 상단 강제 고정 (물리적 압착 파손 방지 핵심)', 1800, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),

  -- ── 혼적② Y축 LIFO 배치 (MIX_Y_LIFO) ── 엑셀§4-2
  (1, 'MIX_Y_LIFO', 'MIX_LIFO_ENABLE',   'Y', '=', '', '', 'Y', '복수 납품처 LIFO 배치 활성 (나중하차→안쪽, 먼저하차→문쪽)',    1900, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'MIX_Y_LIFO', 'MIX_ZONE_SPLIT_YN', 'Y', '=', '', '', 'Y', '원지·판지 배송처 상이 시 전후 Zone 분할 적재 자동 전환', 1910, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),

  -- ── 혼적③ 이중 복합 검증 (MIX_DUAL_VERIFY) ── 엑셀§4-3
  (1, 'MIX_DUAL_VERIFY', 'MIX_WEIGHT_CHECK_YN', 'Y', '=', '', '', 'Y', '혼적 총중량 검증 (원지+판지 ≤ 차량 최대 적재 중량)',              2000, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'MIX_DUAL_VERIFY', 'MIX_HEIGHT_CHECK_YN', 'Y', '=', '', '', 'Y', '혼적 높이 검증 (파렛트고+원지다단높이+판지높이 ≤ 차량 최대 높이)', 2010, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'MIX_DUAL_VERIFY', 'MIX_3D_CHECK_YN',     'Y', '=', '', '', 'Y', '혼적 Dead Space 포함 3D 블록 종합 검증',                          2020, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),

  -- ── 동적 구역 제약 (DYNAMIC_ZONE) ──
  (1, 'DYNAMIC_ZONE', 'DYNAMIC_GROUP_BY',       'AREA_CD', '=', '', '', 'Y', '동적 그룹핑 기준 컬럼 — 동일 AREA_CD(권역) 납품처끼리 동적 배차 그룹 형성', 2100, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'DYNAMIC_ZONE', 'DYNAMIC_DEFAULT_YN',     'Y',       '=', '', '', 'Y', '납품처 DYNAMIC_YN 미설정 시 기본값 (Y=동적배차 가능, N=불가)',              2110, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'DYNAMIC_ZONE', 'DYNAMIC_MIN_GROUP_SIZE', '1',       '>=','', '', 'Y', '동적 그룹 최소 납품처 수 — 1=단독 납품처도 동적배차 허용',                   2120, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  (1, 'DYNAMIC_ZONE', 'DYNAMIC_AREA_FORCE_YN',  'Y',       '=', '', '', 'Y', '동일 구역(AREA_CD) 내 DYNAMIC_YN=Y 납품처는 반드시 동적 그룹 편입 강제',     2130, DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d'));


-- ─────────────────────────────────────────────────────────────
-- Step 2: DS_DISPATCH_CONST_SET_ITEM — 모든 세트에 신규 탭 연결
--
-- 대상: DS_DISPATCH_CONST_SET에 존재하는 모든 SET_ID
-- 방식: 삽입된 신규 CONST 행들을 각 세트에 ITEM으로 연결
--       (INSERT IGNORE: UNIQUE KEY 충돌 방지)
--
-- ※ DS_DISPATCH_CONST_SET_ITEM에 UNIQUE KEY가 없는 경우를 위한
--   안전 처리: SET_ID + CONST_ID 조합 중복 체크 후 삽입
-- ─────────────────────────────────────────────────────────────

-- 모든 세트에 대해 신규 탭 CONST 행들을 SET_ITEM으로 연결
-- (아직 연결되지 않은 항목만 삽입)
INSERT INTO DS_DISPATCH_CONST_SET_ITEM (SET_ID, CONST_ID, ACTIVE_YN, PARAM_VALUE)
SELECT s.SET_ID, c.CONST_ID, 'Y', c.CONST_VALUE
FROM DS_DISPATCH_CONST_SET s
CROSS JOIN DS_DISPATCH_CONST c
WHERE c.CONST_TYPE IN (
  'ROLL_UNIT','ROLL_STACK','ROLL_INCH_MIX','ROLL_3D_VERIFY',
  'BOARD_CBM_WEIGHT','BOARD_BULK_SPLIT','BOARD_FLEX_SPLIT','BOARD_3D_VERIFY',
  'MIX_Z_AXIS','MIX_Y_LIFO','MIX_DUAL_VERIFY',
  'DYNAMIC_ZONE'
)
AND NOT EXISTS (
  SELECT 1 FROM DS_DISPATCH_CONST_SET_ITEM i
  WHERE i.SET_ID = s.SET_ID AND i.CONST_ID = c.CONST_ID
);


-- ─────────────────────────────────────────────────────────────
-- 검증 쿼리 (실행 후 확인용)
-- ─────────────────────────────────────────────────────────────

/*
-- DS_DISPATCH_CONST 신규 탭 데이터 확인
SELECT CONST_TYPE, COUNT(*) AS cnt
FROM DS_DISPATCH_CONST
WHERE CONST_TYPE IN (
  'ROLL_UNIT','ROLL_STACK','ROLL_INCH_MIX','ROLL_3D_VERIFY',
  'BOARD_CBM_WEIGHT','BOARD_BULK_SPLIT','BOARD_FLEX_SPLIT','BOARD_3D_VERIFY',
  'MIX_Z_AXIS','MIX_Y_LIFO','MIX_DUAL_VERIFY','DYNAMIC_ZONE'
)
GROUP BY CONST_TYPE
ORDER BY CONST_TYPE;

-- SET_ITEM 연결 확인 (SET_ID별 신규 탭 항목 수)
SELECT i.SET_ID, s.SET_NM, c.CONST_TYPE, COUNT(*) AS cnt
FROM DS_DISPATCH_CONST_SET_ITEM i
JOIN DS_DISPATCH_CONST c ON c.CONST_ID = i.CONST_ID
JOIN DS_DISPATCH_CONST_SET s ON s.SET_ID = i.SET_ID
WHERE c.CONST_TYPE IN (
  'ROLL_UNIT','ROLL_STACK','ROLL_INCH_MIX','ROLL_3D_VERIFY',
  'BOARD_CBM_WEIGHT','BOARD_BULK_SPLIT','BOARD_FLEX_SPLIT','BOARD_3D_VERIFY',
  'MIX_Z_AXIS','MIX_Y_LIFO','MIX_DUAL_VERIFY','DYNAMIC_ZONE'
)
GROUP BY i.SET_ID, s.SET_NM, c.CONST_TYPE
ORDER BY i.SET_ID, c.CONST_TYPE;
*/
