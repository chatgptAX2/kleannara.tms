-- ============================================================
--  module-dispatch-config: 기초 데이터
--  생성일: 2026-07-04  /  출처: wms-viewer/wms.db
-- 순서: DS_DISPATCH_OBJECTIVE → DS_DISPATCH_PROFILE
--       → DS_DISPATCH_CONST → DS_DISPATCH_CONST_SET
--       → DS_DISPATCH_CONST_SET_ITEM → DS_DISPATCH_CONST_ITEM
-- ============================================================

-- ── 배차 목적식 (DS_DISPATCH_OBJECTIVE): 3건 ──
INSERT INTO DS_DISPATCH_OBJECTIVE
    (OBJ_ID, OBJ_CODE, OBJ_NM, OBJ_ICON, OBJ_ALGO, OBJ_DESC, SORT_SEQ, ACTIVE_YN, CREDAT, LMODAT)
VALUES
    ('1', 'MIN_VEHICLES', '차량 최소화', '🚛', 'FFD BinPacking', '가능한 적은 차량으로 최대 적재 (FFD 알고리즘)', '10', 'Y', '20260625', '20260625'),
    ('2', 'MAX_FILL', '적재율 최대화', '📊', 'BFD BinPacking', '각 차량을 가장 꽉 채우는 방식 (BFD 알고리즘)', '20', 'Y', '20260625', '20260625'),
    ('3', 'MIN_COST', '운송비 최소화', '💰', 'ROUTE_COST', 'ROUTE_COST 기반 최저비용 차종 선택', '30', 'Y', '20260625', '20260625')
ON DUPLICATE KEY UPDATE
    OBJ_NM = VALUES(OBJ_NM),
    OBJ_ICON = VALUES(OBJ_ICON),
    OBJ_ALGO = VALUES(OBJ_ALGO),
    OBJ_DESC = VALUES(OBJ_DESC),
    SORT_SEQ = VALUES(SORT_SEQ),
    ACTIVE_YN = VALUES(ACTIVE_YN),
    LMODAT = VALUES(LMODAT);

-- ── 배차 프로파일 (DS_DISPATCH_PROFILE): 3건 ──
INSERT INTO DS_DISPATCH_PROFILE
    (PROFILE_ID, PROFILE_NM, OBJECTIVE, ACTIVE_YN, NOTE, CREDAT, LMODAT, SET_ID)
VALUES
    ('1', '차량최소화 기본', 'MIN_VEHICLES', 'Y', '차량 수를 최소화합니다 (FFD BinPacking 기본)', '20260625', '20260629', NULL),
    ('2', '적재율최대화', 'MAX_FILL', 'N', '각 차량의 적재율을 최대화합니다', '20260625', '20260625', NULL),
    ('3', '운송비최소화', 'MIN_COST', 'N', 'ROUTE_COST 기준 총 운송비를 최소화합니다', '20260625', '20260625', NULL)
ON DUPLICATE KEY UPDATE
    PROFILE_NM = VALUES(PROFILE_NM),
    OBJECTIVE = VALUES(OBJECTIVE),
    ACTIVE_YN = VALUES(ACTIVE_YN),
    NOTE = VALUES(NOTE),
    SET_ID = VALUES(SET_ID),
    LMODAT = VALUES(LMODAT);

-- ── 배차 제약조건 (DS_DISPATCH_CONST): 68건 ──
INSERT INTO DS_DISPATCH_CONST
    (CONST_ID, PROFILE_ID, CONST_TYPE, CONST_KEY, CONST_VALUE, CONST_OP, TARGET_ID, TARGET_NM, ACTIVE_YN, NOTE, SORT_SEQ, CREDAT, LMODAT)
VALUES
    ('1', '1', 'GLOBAL', 'MAX_VEHICLES_PER_GROUP', '99', '<=', '', '', 'Y', '그룹당 최대 배차 차량 수', '10', '20260625', '20260625'),
    ('2', '1', 'GLOBAL', 'ALLOW_SPLIT_ITEM', 'Y', '=', '', '', 'Y', '단일 아이템 납품분할 허용', '20', '20260625', '20260625'),
    ('3', '1', 'GLOBAL', 'ALLOW_MIXED_LOAD', 'N', '=', '', '', 'Y', '우편번호 앞 3자리 동일 납품처 혼적 허용', '25', '20260625', '20260625'),
    ('4', '1', 'GLOBAL', 'MIN_FILL_RATIO', '0', '>=', '', '', 'Y', '최소 적재율(%) — 0=제한없음', '30', '20260625', '20260625'),
    ('5', '1', 'GLOBAL', 'MAX_FILL_RATIO', '100', '<=', '', '', 'Y', '최대 적재율(%) — 초과배차 방지', '40', '20260625', '20260625'),
    ('13', '1', 'CARGO', 'MAX_ROLL_STACK_TIER', '2', '<=', '', '', 'Y', '최대 롤 적재 단수', '200', '20260625', '20260625'),
    ('14', '1', 'CARGO', 'MAX_BOARD_HEIGHT_M', '2.4', '<=', '', '', 'Y', '판지 최대 적재 높이(m)', '210', '20260625', '20260625'),
    ('15', '1', 'CARGO', 'ROLL_SINGLE_KG_FALLBACK', '600', '=', '', '', 'Y', '롤 단중 미등록 시 fallback(kg)', '220', '20260625', '20260625'),
    ('16', '1', 'COST', 'COST_REF_DATE', 'TODAY', '=', '', '', 'Y', '운송비 기준일 (TODAY or YYYYMMDD)', '300', '20260625', '20260625'),
    ('17', '1', 'COST', 'COST_PENALTY_OVER', '1.5', '=', '', '', 'Y', '초과적재 비용 패널티 배수', '310', '20260625', '20260625'),
    ('18', '1', 'VEHICLE', 'ALLOW_CARTYPE', 'Y', '=', '1톤', '1톤', 'Y', '차종 허용여부', '100', '20260629', '20260629'),
    ('19', '1', 'VEHICLE', 'ALLOW_CARTYPE', 'Y', '=', '1.4톤', '1.4톤', 'Y', '차종 허용여부', '101', '20260629', '20260629'),
    ('20', '1', 'VEHICLE', 'ALLOW_CARTYPE', 'Y', '=', '2톤', '2톤', 'Y', '차종 허용여부', '102', '20260629', '20260629'),
    ('21', '1', 'VEHICLE', 'ALLOW_CARTYPE', 'Y', '=', '2.5톤', '2.5톤', 'Y', '차종 허용여부', '103', '20260629', '20260629'),
    ('22', '1', 'VEHICLE', 'ALLOW_CARTYPE', 'Y', '=', '3톤', '3톤', 'Y', '차종 허용여부', '104', '20260629', '20260629'),
    ('23', '1', 'VEHICLE', 'ALLOW_CARTYPE', 'Y', '=', '3.5톤', '3.5톤', 'Y', '차종 허용여부', '105', '20260629', '20260629'),
    ('24', '1', 'VEHICLE', 'ALLOW_CARTYPE', 'Y', '=', '4.5톤', '4.5톤', 'Y', '차종 허용여부', '106', '20260629', '20260629'),
    ('25', '1', 'VEHICLE', 'ALLOW_CARTYPE', 'Y', '=', '5톤', '5톤', 'Y', '차종 허용여부', '107', '20260629', '20260629'),
    ('26', '1', 'VEHICLE', 'ALLOW_CARTYPE', 'Y', '=', '8톤', '8톤', 'Y', '차종 허용여부', '108', '20260629', '20260629'),
    ('27', '1', 'VEHICLE', 'ALLOW_CARTYPE', 'Y', '=', '10톤', '10톤', 'Y', '차종 허용여부', '109', '20260629', '20260629'),
    ('28', '1', 'VEHICLE', 'ALLOW_CARTYPE', 'Y', '=', '11톤', '11톤', 'Y', '차종 허용여부', '110', '20260629', '20260629'),
    ('29', '1', 'VEHICLE', 'ALLOW_CARTYPE', 'Y', '=', '15톤', '15톤', 'Y', '차종 허용여부', '111', '20260629', '20260629'),
    ('30', '1', 'VEHICLE', 'ALLOW_CARTYPE', 'Y', '=', '18톤', '18톤', 'Y', '차종 허용여부', '112', '20260629', '20260629'),
    ('31', '1', 'VEHICLE', 'ALLOW_CARTYPE', 'Y', '=', '20톤', '20톤', 'Y', '차종 허용여부', '113', '20260629', '20260629'),
    ('32', '1', 'VEHICLE', 'ALLOW_CARTYPE', 'Y', '=', '25톤', '25톤', 'Y', '차종 허용여부', '114', '20260629', '20260629'),
    ('33', '1', 'VEHICLE', 'ALLOW_CARTYPE', 'Y', '=', '26톤', '26톤', 'Y', '차종 허용여부', '115', '20260629', '20260629'),
    ('34', '1', 'ROLL_UNIT', 'ROLL_INTEGER_ONLY', 'Y', '=', '', '', 'Y', '원지 정수 롤 단위 강제 (분할 절대 불가)', '1000', '20260629', '20260629'),
    ('35', '1', 'ROLL_UNIT', 'ROLL_SPLIT_ALLOWED', 'N', '=', '', '', 'Y', '원지 분할 배차 금지 (엑셀§2-1)', '1010', '20260629', '20260629'),
    ('36', '1', 'ROLL_UNIT', 'ROLL_MIN_QTY', '1', '>=', '', '', 'Y', '원지 최소 배차 수량 (1롤)', '1020', '20260629', '20260629'),
    ('37', '1', 'ROLL_STACK', 'ROLL_MAX_TIER', '3', '<=', '', '', 'Y', '롤 최대 적재 단수 Hard Cap (3단)', '1100', '20260629', '20260629'),
    ('38', '1', 'ROLL_STACK', 'ROLL_PALLET_DEDUCT_M', '0.15', '=', '', '', 'Y', '파레트 높이 차감값 (m) — 0.15m 기본', '1110', '20260629', '20260629'),
    ('39', '1', 'ROLL_STACK', 'ROLL_PALLET_APPLY_YN', 'Y', '=', '', '', 'Y', '파레트 차감 적용 여부 (납품처 FORKLIFT_YN=N 시 적용)', '1120', '20260629', '20260629'),
    ('40', '1', 'ROLL_STACK', 'ROLL_HEIGHT_MARGIN_M', '0', '=', '', '', 'Y', '적재 높이 안전 여유 마진 (m)', '1130', '20260629', '20260629'),
    ('76', '1', 'ROLL_STACK', 'ROLL_MAX_HEIGHT_M', '0', '<=', '', '', 'Y', '원지 다단 적재높이 상한(m) — 0=차량 톤수별 높이 연동, >0=해당값과 차량높이 중 작은값 적용', '1140', '20260629', '20260629'),
    ('41', '1', 'ROLL_INCH_MIX', 'ROLL_INCH_MIX_ALLOW', 'Y', '=', '', '', 'Y', '인치/평량 혼합 오더 허용 (엑셀§2-3)', '1200', '20260629', '20260629'),
    ('42', '1', 'ROLL_INCH_MIX', 'ROLL_2D_PACK_ENGINE', 'Y', '=', '', '', 'Y', '혼합 규격 시 2D 바닥 패킹 연산 적용', '1210', '20260629', '20260629'),
    ('43', '1', 'ROLL_INCH_MIX', 'ROLL_SAME_INCH_FORCE', 'N', '=', '', '', 'Y', '동일 인치 강제 여부 (N=혼재허용)', '1220', '20260629', '20260629'),
    ('44', '1', 'ROLL_INCH_MIX', 'ROLL_REF_12INCH_LT300', '2,3,4,6,8,8,8', '=', '', '', 'Y', '12인치/평량300미만 차종별 1단 기준수(1.4t~18t)', '1230', '20260629', '20260629'),
    ('45', '1', 'ROLL_INCH_MIX', 'ROLL_REF_12INCH_GE300', '2,3,4,5,7,7,7', '=', '', '', 'Y', '12인치/평량300이상 차종별 1단 기준수(1.4t~18t)', '1240', '20260629', '20260629'),
    ('46', '1', 'ROLL_INCH_MIX', 'ROLL_REF_3INCH_LT300', '3,5,10,12,14,14,15', '=', '', '', 'Y', '3인치/평량300미만 차종별 1단 기준수(1.4t~18t)', '1250', '20260629', '20260629'),
    ('47', '1', 'ROLL_INCH_MIX', 'ROLL_REF_3INCH_GE300', '3,5,10,12,14,14,15', '=', '', '', 'Y', '3인치/평량300이상 차종별 1단 기준수(1.4t~18t)', '1260', '20260629', '20260629'),
    ('48', '1', 'ROLL_3D_VERIFY', 'ROLL_3D_CHECK_YN', 'Y', '=', '', '', 'Y', '원지 3D 블록 검증 활성 (Dead Space 포함)', '1300', '20260629', '20260629'),
    ('49', '1', 'ROLL_3D_VERIFY', 'ROLL_3D_DEAD_SPACE_PCT', '0', '<=', '', '', 'Y', 'Dead Space 허용 비율 (%) — 0=허용안함', '1310', '20260629', '20260629'),
    ('50', '1', 'ROLL_3D_VERIFY', 'ROLL_OVERSIZE_ACTION', 'SPLIT', '=', '', '', 'Y', '치수 초과 시 처리 방식 (SPLIT=분할/REJECT=제외)', '1320', '20260629', '20260629'),
    ('51', '1', 'BOARD_CBM_WEIGHT', 'BOARD_CBM_CHECK_YN', 'Y', '=', '', '', 'Y', '판지 CBM 자동계산 + Double-Threshold 검증 활성 (엑셀§3-1)', '1400', '20260629', '20260629'),
    ('52', '1', 'BOARD_CBM_WEIGHT', 'BOARD_MAX_CBM_RATIO', '100', '<=', '', '', 'Y', '가용 적재함 CBM 상한 (%)', '1410', '20260629', '20260629'),
    ('53', '1', 'BOARD_CBM_WEIGHT', 'BOARD_MAX_TON_RATIO', '100', '<=', '', '', 'Y', '중량 상한 (%) — Double-Threshold 중량 기준', '1420', '20260629', '20260629'),
    ('54', '1', 'BOARD_CBM_WEIGHT', 'BOARD_DUAL_THRESHOLD', 'Y', '=', '', '', 'Y', '중량+CBM 동시 초과 이중 임계치 검증 활성', '1430', '20260629', '20260629'),
    ('55', '1', 'BOARD_BULK_SPLIT', 'BOARD_BULK_INTEGER_ONLY', 'Y', '=', '', '', 'Y', '벌크 1PLT 단위 강제 (개체 내 분할 불가)', '1500', '20260629', '20260629'),
    ('56', '1', 'BOARD_BULK_SPLIT', 'BOARD_INNER_SPLIT_ALLOW', 'Y', '=', '', '', 'Y', '속포장 속 단위 분할 허용', '1510', '20260629', '20260629'),
    ('57', '1', 'BOARD_BULK_SPLIT', 'BOARD_INNER_STACK_ALLOW', 'Y', '=', '', '', 'Y', '분할된 속을 판지 위 추가 적재 허용', '1520', '20260629', '20260629'),
    ('58', '1', 'BOARD_BULK_SPLIT', 'BOARD_INNER_OVERFLOW_ACT', 'SPLIT', '=', '', '', 'Y', '속포장 초과 시 처리 (SPLIT=다음 차량 배정)', '1530', '20260629', '20260629'),
    ('59', '1', 'BOARD_FLEX_SPLIT', 'BOARD_FLEX_SPLIT_YN', 'Y', '=', '', '', 'Y', '유연 분할 선적 활성 — 차량 한계 시 초과분만 후속 차량 (엑셀§3-3)', '1600', '20260629', '20260629'),
    ('60', '1', 'BOARD_FLEX_SPLIT', 'BOARD_SPLIT_UNIT', 'EA', '=', '', '', 'Y', '분할 단위 (EA=낱개속, PLT=파레트)', '1610', '20260629', '20260629'),
    ('61', '1', 'BOARD_FLEX_SPLIT', 'BOARD_SPLIT_OVERFLOW', 'Y', '=', '', '', 'Y', '초과분(속단위) 후속 차량 정확 배정 활성', '1620', '20260629', '20260629'),
    ('62', '1', 'BOARD_3D_VERIFY', 'BOARD_3D_CHECK_YN', 'Y', '=', '', '', 'Y', '판지 3D 블록 검증 활성 (Dead Space 포함)', '1700', '20260629', '20260629'),
    ('63', '1', 'BOARD_3D_VERIFY', 'BOARD_3D_DEAD_SPACE_PCT', '0', '<=', '', '', 'Y', '판지 Dead Space 허용 비율 (%)', '1710', '20260629', '20260629'),
    ('64', '1', 'BOARD_3D_VERIFY', 'BOARD_HEIGHT_MAX_M', '2.4', '<=', '', '', 'Y', '판지 스택 최대 높이 (m, 기본 2.4m)', '1720', '20260629', '20260629'),
    ('65', '1', 'MIX_Z_AXIS', 'MIX_ROLL_BOTTOM_FORCE', 'Y', '=', '', '', 'Y', '원지 하단·판지 상단 강제 고정 (물리적 압착 파손 방지 핵심)', '1800', '20260629', '20260629'),
    ('66', '1', 'MIX_Y_LIFO', 'MIX_LIFO_ENABLE', 'Y', '=', '', '', 'Y', '복수 납품처 LIFO 배치 활성 (나중하차→안쪽, 먼저하차→문쪽)', '1900', '20260629', '20260629'),
    ('67', '1', 'MIX_Y_LIFO', 'MIX_ZONE_SPLIT_YN', 'Y', '=', '', '', 'Y', '원지·판지 배송처 상이 시 전후 Zone 분할 적재 자동 전환', '1910', '20260629', '20260629'),
    ('68', '1', 'MIX_DUAL_VERIFY', 'MIX_WEIGHT_CHECK_YN', 'Y', '=', '', '', 'Y', '혼적 총중량 검증 (원지+판지 ≤ 차량 최대 적재 중량)', '2000', '20260629', '20260629'),
    ('69', '1', 'MIX_DUAL_VERIFY', 'MIX_HEIGHT_CHECK_YN', 'Y', '=', '', '', 'Y', '혼적 높이 검증 (파렛트고+원지다단높이+판지높이 ≤ 차량 최대 높이)', '2010', '20260629', '20260629'),
    ('70', '1', 'MIX_DUAL_VERIFY', 'MIX_3D_CHECK_YN', 'Y', '=', '', '', 'Y', '혼적 Dead Space 포함 3D 블록 종합 검증', '2020', '20260629', '20260629'),
    ('71', '1', 'DYNAMIC_ZONE', 'DYNAMIC_GROUP_BY', 'AREA_CD', '=', '', '', 'Y', '동적 그룹핑 기준 컬럼 — 동일 AREA_CD(권역) 납품처끼리 동적 배차 그룹 형성', '2100', '20260629', '20260629'),
    ('72', '1', 'DYNAMIC_ZONE', 'DYNAMIC_DEFAULT_YN', 'Y', '=', '', '', 'Y', '납품처 DYNAMIC_YN 미설정 시 기본값 (Y=동적배차 가능, N=불가)', '2110', '20260629', '20260629'),
    ('73', '1', 'DYNAMIC_ZONE', 'DYNAMIC_MIN_GROUP_SIZE', '1', '>=', '', '', 'Y', '동적 그룹 최소 납품처 수 — 1=단독 납품처도 동적배차 허용', '2120', '20260629', '20260629'),
    ('74', '1', 'DYNAMIC_ZONE', 'DYNAMIC_AREA_FORCE_YN', 'Y', '=', '', '', 'Y', '동일 구역(AREA_CD) 내 DYNAMIC_YN=Y 납품처는 반드시 동적 그룹 편입 강제', '2130', '20260629', '20260629'),
    ('75', '1', 'GLOBAL', 'BOARD_MIN_FILL_RATIO', '0', '>=', '', '', 'Y', '판지 전용 최소 적재율(%) — 0=미적용, 값설정 시 적재율 하한 강제 (BOARD_BULK_INTEGER_ONLY 연계)', '2140', '20260702', '20260702')
ON DUPLICATE KEY UPDATE
    CONST_TYPE = VALUES(CONST_TYPE),
    CONST_KEY = VALUES(CONST_KEY),
    CONST_VALUE = VALUES(CONST_VALUE),
    CONST_OP = VALUES(CONST_OP),
    TARGET_ID = VALUES(TARGET_ID),
    TARGET_NM = VALUES(TARGET_NM),
    ACTIVE_YN = VALUES(ACTIVE_YN),
    NOTE = VALUES(NOTE),
    SORT_SEQ = VALUES(SORT_SEQ),
    LMODAT = VALUES(LMODAT);

-- ── 제약조건 세트 (DS_DISPATCH_CONST_SET): 1건 ──
INSERT INTO DS_DISPATCH_CONST_SET
    (SET_ID, SET_NM, SET_DESC, ACTIVE_YN, CREDAT, LMODAT)
VALUES
    ('1', '기본 제약조건 세트', '기본 배차 제약조건 모음 (글로벌/차량/화물/비용)', 'Y', '20260629', '20260629')
ON DUPLICATE KEY UPDATE
    SET_NM = VALUES(SET_NM),
    SET_DESC = VALUES(SET_DESC),
    ACTIVE_YN = VALUES(ACTIVE_YN),
    LMODAT = VALUES(LMODAT);

-- ── 세트-제약 연결 (DS_DISPATCH_CONST_SET_ITEM): 67건 ──
INSERT INTO DS_DISPATCH_CONST_SET_ITEM
    (ITEM_ID, SET_ID, CONST_ID, ACTIVE_YN, PARAM_VALUE)
VALUES
    ('1', '1', '1', 'Y', NULL),
    ('2', '1', '2', 'Y', NULL),
    ('3', '1', '3', 'Y', NULL),
    ('4', '1', '4', 'Y', NULL),
    ('5', '1', '5', 'Y', NULL),
    ('13', '1', '13', 'Y', NULL),
    ('14', '1', '14', 'Y', NULL),
    ('15', '1', '15', 'Y', NULL),
    ('16', '1', '16', 'Y', NULL),
    ('17', '1', '17', 'Y', NULL),
    ('18', '1', '18', 'Y', NULL),
    ('19', '1', '19', 'Y', NULL),
    ('20', '1', '20', 'Y', NULL),
    ('21', '1', '21', 'Y', NULL),
    ('22', '1', '22', 'Y', NULL),
    ('23', '1', '23', 'Y', NULL),
    ('24', '1', '24', 'Y', NULL),
    ('25', '1', '25', 'Y', NULL),
    ('26', '1', '26', 'Y', NULL),
    ('27', '1', '27', 'Y', NULL),
    ('28', '1', '28', 'Y', NULL),
    ('29', '1', '29', 'Y', NULL),
    ('30', '1', '30', 'Y', NULL),
    ('31', '1', '31', 'Y', NULL),
    ('32', '1', '32', 'Y', NULL),
    ('33', '1', '33', 'Y', NULL),
    ('34', '1', '34', 'Y', NULL),
    ('35', '1', '35', 'Y', NULL),
    ('36', '1', '36', 'Y', NULL),
    ('37', '1', '37', 'Y', NULL),
    ('38', '1', '38', 'Y', NULL),
    ('39', '1', '39', 'Y', NULL),
    ('40', '1', '40', 'Y', NULL),
    ('41', '1', '41', 'Y', NULL),
    ('42', '1', '42', 'Y', NULL),
    ('43', '1', '43', 'Y', NULL),
    ('44', '1', '44', 'Y', NULL),
    ('45', '1', '45', 'Y', NULL),
    ('46', '1', '46', 'Y', NULL),
    ('47', '1', '47', 'Y', NULL),
    ('48', '1', '48', 'Y', NULL),
    ('49', '1', '49', 'Y', NULL),
    ('50', '1', '50', 'Y', NULL),
    ('51', '1', '51', 'Y', NULL),
    ('52', '1', '52', 'Y', NULL),
    ('53', '1', '53', 'Y', NULL),
    ('54', '1', '54', 'Y', NULL),
    ('55', '1', '55', 'Y', NULL),
    ('56', '1', '56', 'Y', NULL),
    ('57', '1', '57', 'Y', NULL),
    ('58', '1', '58', 'Y', NULL),
    ('59', '1', '59', 'Y', NULL),
    ('60', '1', '60', 'Y', NULL),
    ('61', '1', '61', 'Y', NULL),
    ('62', '1', '62', 'Y', NULL),
    ('63', '1', '63', 'Y', NULL),
    ('64', '1', '64', 'Y', NULL),
    ('65', '1', '65', 'Y', NULL),
    ('66', '1', '66', 'Y', NULL),
    ('67', '1', '67', 'Y', NULL),
    ('68', '1', '68', 'Y', NULL),
    ('69', '1', '69', 'Y', NULL),
    ('70', '1', '70', 'Y', NULL),
    ('71', '1', '71', 'Y', NULL),
    ('72', '1', '72', 'Y', NULL),
    ('73', '1', '73', 'Y', NULL),
    ('74', '1', '74', 'Y', NULL)
ON DUPLICATE KEY UPDATE
    ACTIVE_YN = VALUES(ACTIVE_YN),
    PARAM_VALUE = VALUES(PARAM_VALUE);

-- ── 제약조건 항목 UI (DS_DISPATCH_CONST_ITEM): 58건 ──
INSERT INTO DS_DISPATCH_CONST_ITEM
    (ITEM_CD, ITEM_NM, ITEM_GRP, ITEM_TYPE, DEFAULT_VAL, UNIT, CONST_OP, SORT_SEQ, DESCRIPTION, SOURCE_REF, ACTIVE_YN, SELECT_OPTS, CREDAT, LMODAT)
VALUES
    -- ─── 공통 제약 (COMMON) §1 ───────────────────────────
    ('ENTRY_TON_LIMIT', '납품처 진입 허용 톤수 제한', 'COMMON', 'NUM', '0', 'ton', '<=', '10', '납품처 마스터에 설정된 최대 진입 허용 톤수 초과 차량은 배차 후보에서 제외', '§1-1', 'Y', NULL, '20260715', '20260715'),
    ('PALLET_YN', '납품처 파레트 유무 조건', 'COMMON', 'YN', 'Y', 'Y/N', '=', '20', '납품처 마스터의 파레트 필수 여부 → 파레트 높이 반영 여부 결정', '§1-2', 'Y', NULL, '20260715', '20260715'),
    ('FIXED_VEH_PRIORITY', '고정차량 배차 최우선 반영', 'COMMON', 'YN', 'Y', 'Y/N', '=', '30', '고정 차량 매핑 오더는 알고리즘 연산 전 최우선 사전 할당(Pre-assignment)', '§1-3', 'Y', NULL, '20260715', '20260715'),
    ('DYNAMIC_ALLOW_YN', '동적 라우팅 허용 여부', 'COMMON', 'YN', 'Y', 'Y/N', '=', '40', '납품처 마스터의 동적 라우팅 허용 여부 → 고정 노선 vs 유연 배차 분리', '§1-4', 'Y', NULL, '20260715', '20260715'),
    ('SPLIT_DELIVERY_YN', '납품수량 초과 시 분할 선적', 'COMMON', 'YN', 'Y', 'Y/N', '=', '50', '주문 수량이 최대 차량 적재 능력 초과 시 잔여 수량 오더 분할', '§1-5', 'Y', NULL, '20260715', '20260715'),
    -- ─── 원지 제약 (ROLL) §2 ─────────────────────────────
    ('ROLL_INTEGER_ONLY', '원지 정수 롤 단위 강제 (분할 절대 불가)', 'ROLL', 'YN', 'Y', '', '=', '110', '원지 정수 롤 단위 강제 (분할 절대 불가)', '§2-1', 'Y', NULL, '20260715', '20260715'),
    ('ROLL_SPLIT_ALLOWED', '원지 분할 배차 금지 (엑셀§2-1)', 'ROLL', 'YN', 'N', '', '=', '120', '원지 분할 배차 금지 (엑셀§2-1)', '§2-1', 'Y', NULL, '20260715', '20260715'),
    ('ROLL_MAX_TIER', '롤 최대 적재 단수 Hard Cap (3단)', 'ROLL', 'NUM', '3', 'm', '<=', '130', '롤 최대 적재 단수 Hard Cap (3단)', '§2-2', 'Y', NULL, '20260715', '20260715'),
    ('ROLL_MIN_QTY', '원지 최소 배차 수량 (1롤)', 'ROLL', 'NUM', '1', 'm', '>=', '130', '원지 최소 배차 수량 (1롤)', '§2-1', 'Y', NULL, '20260715', '20260715'),
    ('ROLL_PALLET_DEDUCT_M', '파레트 높이 차감값 (m) — 0.15m 기본', 'ROLL', 'NUM', '0.15', 'm', '=', '140', '파레트 높이 차감값 (m) — 0.15m 기본', '§2-2', 'Y', NULL, '20260715', '20260715'),
    ('ROLL_INCH_MIX_ALLOW', '인치/평량 혼합 오더 허용 (엑셀§2-3)', 'ROLL', 'YN', 'Y', 'm', '=', '150', '인치/평량 혼합 오더 허용 (엑셀§2-3)', '§2-3', 'Y', NULL, '20260715', '20260715'),
    ('ROLL_PALLET_APPLY_YN', '파레트 차감 적용 여부 (납품처 FORKLIFT_YN=N 시 적용)', 'ROLL', 'YN', 'Y', '', '=', '150', '파레트 차감 적용 여부 (납품처 FORKLIFT_YN=N 시 적용)', '§2-2', 'Y', NULL, '20260715', '20260715'),
    ('ROLL_2D_PACK_ENGINE', '혼합 규격 시 2D 바닥 패킹 연산 적용', 'ROLL', 'YN', 'Y', '', '=', '160', '혼합 규격 시 2D 바닥 패킹 연산 적용', '§2-3', 'Y', NULL, '20260715', '20260715'),
    ('ROLL_HEIGHT_MARGIN_M', '적재 높이 안전 여유 마진 (m)', 'ROLL', 'NUM', '0', 'm', '=', '160', '적재 높이 안전 여유 마진 (m)', '§2-2', 'Y', NULL, '20260715', '20260715'),
    ('ROLL_MAX_HEIGHT_M', '원지 다단 적재높이 (m) — 0=차량 톤수별 높이 연동', 'ROLL', 'NUM', '0', 'm', '<=', '165', '원지 다단 적재 최대높이(m). 0=차량유형관리 톤수별 높이 자동연동, >0=설정값과 차량높이 중 작은값 적용', '§2-2', 'Y', NULL, '20260715', '20260715'),
    ('ROLL_3D_CHECK_YN', '원지 3D 블록 검증 활성 (Dead Space 포함)', 'ROLL', 'YN', 'Y', '', '=', '170', '원지 3D 블록 검증 활성 (Dead Space 포함)', '§2-4', 'Y', NULL, '20260715', '20260715'),
    ('ROLL_SAME_INCH_FORCE', '동일 인치 강제 여부 (N=혼재허용)', 'ROLL', 'YN', 'N', '', '=', '170', '동일 인치 강제 여부 (N=혼재허용)', '§2-3', 'Y', NULL, '20260715', '20260715'),
    ('ROLL_3D_DEAD_SPACE_PCT', 'Dead Space 허용 비율 (%) — 0=허용안함', 'ROLL', 'NUM', '0', '%', '<=', '180', 'Dead Space 허용 비율 (%) — 0=허용안함', '§2-4', 'Y', NULL, '20260715', '20260715'),
    ('ROLL_REF_12INCH_LT300', '12인치/평량300미만 차종별 1단 기준수(1.4t~18t)', 'ROLL', 'CSV', '2,3,4,6,8,8,8', '롤', '=', '180', '12인치/평량300미만 차종별 1단 기준수(1.4t~18t)', '§2-3', 'Y', NULL, '20260715', '20260715'),
    ('ROLL_OVERSIZE_ACTION', '치수 초과 시 처리 방식 (SPLIT=분할/REJECT=제외)', 'ROLL', 'SELECT', 'SPLIT', '', '=', '190', '치수 초과 시 처리 방식 (SPLIT=분할/REJECT=제외)', '§2-4', 'Y', '["SPLIT","REJECT"]', '20260715', '20260715'),
    ('ROLL_REF_12INCH_GE300', '12인치/평량300이상 차종별 1단 기준수(1.4t~18t)', 'ROLL', 'CSV', '2,3,4,5,7,7,7', '롤', '=', '190', '12인치/평량300이상 차종별 1단 기준수(1.4t~18t)', '§2-3', 'Y', NULL, '20260715', '20260715'),
    ('ROLL_REF_3INCH_LT300', '3인치/평량300미만 차종별 1단 기준수(1.4t~18t)', 'ROLL', 'CSV', '3,5,10,12,14,14,15', '롤', '=', '200', '3인치/평량300미만 차종별 1단 기준수(1.4t~18t)', '§2-3', 'Y', NULL, '20260715', '20260715'),
    ('ROLL_REF_3INCH_GE300', '3인치/평량300이상 차종별 1단 기준수(1.4t~18t)', 'ROLL', 'CSV', '3,5,10,12,14,14,15', '롤', '=', '210', '3인치/평량300이상 차종별 1단 기준수(1.4t~18t)', '§2-3', 'Y', NULL, '20260715', '20260715'),
    -- ─── 판지 제약 (BOARD) §3 ────────────────────────────
    ('BOARD_CBM_CHECK_YN', '판지 CBM 자동계산 + Double-Threshold 검증 활성 (엑셀§3-1)', 'BOARD', 'YN', 'Y', '', '=', '310', '판지 CBM 자동계산 + Double-Threshold 검증 활성 (엑셀§3-1)', '§3-1', 'Y', NULL, '20260715', '20260715'),
    ('BOARD_MAX_CBM_RATIO', '가용 적재함 CBM 상한 (%)', 'BOARD', 'NUM', '100', 'm', '<=', '320', '가용 적재함 CBM 상한 (%)', '§3-1', 'Y', NULL, '20260715', '20260715'),
    ('BOARD_BULK_INTEGER_ONLY', '벌크 1PLT 단위 강제 (개체 내 분할 불가)', 'BOARD', 'YN', 'Y', '', '=', '330', '벌크 1PLT 단위 강제 (개체 내 분할 불가)', '§3-2', 'Y', NULL, '20260715', '20260715'),
    ('BOARD_MAX_TON_RATIO', '중량 상한 (%) — Double-Threshold 중량 기준', 'BOARD', 'NUM', '100', 'm', '<=', '330', '중량 상한 (%) — Double-Threshold 중량 기준', '§3-1', 'Y', NULL, '20260715', '20260715'),
    ('BOARD_INNER_SPLIT_ALLOW', '속포장 속 단위 분할 허용', 'BOARD', 'YN', 'Y', '', '=', '340', '속포장 속 단위 분할 허용', '§3-2', 'Y', NULL, '20260715', '20260715'),
    ('BOARD_DUAL_THRESHOLD', '중량+CBM 동시 초과 이중 임계치 검증 활성', 'BOARD', 'YN', 'Y', '', '=', '340', '중량+CBM 동시 초과 이중 임계치 검증 활성', '§3-1', 'Y', NULL, '20260715', '20260715'),
    ('BOARD_INNER_STACK_ALLOW', '분할된 속을 판지 위 추가 적재 허용', 'BOARD', 'YN', 'Y', '', '=', '350', '분할된 속을 판지 위 추가 적재 허용', '§3-2', 'Y', NULL, '20260715', '20260715'),
    ('BOARD_FLEX_SPLIT_YN', '유연 분할 선적 활성 — 차량 한계 시 초과분만 후속 차량 (엑셀§3-3)', 'BOARD', 'YN', 'Y', '', '=', '350', '유연 분할 선적 활성 — 차량 한계 시 초과분만 후속 차량 (엑셀§3-3)', '§3-3', 'Y', NULL, '20260715', '20260715'),
    ('BOARD_INNER_OVERFLOW_ACT', '속포장 초과 시 처리 (SPLIT=다음 차량 배정)', 'BOARD', 'SELECT', 'SPLIT', '', '=', '360', '속포장 초과 시 처리 (SPLIT=다음 차량 배정)', '§3-2', 'Y', '["SPLIT","REJECT"]', '20260715', '20260715'),
    ('BOARD_SPLIT_UNIT', '분할 단위 (EA=낱개속, PLT=파레트)', 'BOARD', 'SELECT', 'EA', '', '=', '360', '분할 단위 (EA=낱개속, PLT=파레트)', '§3-3', 'Y', '["EA","PLT"]', '20260715', '20260715'),
    ('BOARD_3D_CHECK_YN', '판지 3D 블록 검증 활성 (Dead Space 포함)', 'BOARD', 'YN', 'Y', '', '=', '370', '판지 3D 블록 검증 활성 (Dead Space 포함)', '§3-4', 'Y', NULL, '20260715', '20260715'),
    ('BOARD_SPLIT_OVERFLOW', '초과분(속단위) 후속 차량 정확 배정 활성', 'BOARD', 'YN', 'Y', '', '=', '370', '초과분(속단위) 후속 차량 정확 배정 활성', '§3-3', 'Y', NULL, '20260715', '20260715'),
    ('BOARD_3D_DEAD_SPACE_PCT', '판지 Dead Space 허용 비율 (%)', 'BOARD', 'NUM', '0', '%', '<=', '380', '판지 Dead Space 허용 비율 (%)', '§3-4', 'Y', NULL, '20260715', '20260715'),
    ('BOARD_HEIGHT_MAX_M', '판지 스택 최대 높이 (m, 기본 2.4m)', 'BOARD', 'NUM', '2.4', 'm', '<=', '390', '판지 스택 최대 높이 (m, 기본 2.4m)', '§3-4', 'Y', NULL, '20260715', '20260715'),
    -- ─── 혼합적재 제약 (MIX) §4 ──────────────────────────
    ('MIX_ROLL_BOTTOM_FORCE', '원지 하단·판지 상단 강제 고정 (물리적 압착 파손 방지 핵심)', 'MIX', 'YN', 'Y', '', '=', '510', '원지 하단·판지 상단 강제 고정 (물리적 압착 파손 방지 핵심)', '§4-1', 'Y', NULL, '20260715', '20260715'),
    ('MIX_LIFO_ENABLE', '복수 납품처 LIFO 배치 활성 (나중하차→안쪽, 먼저하차→문쪽)', 'MIX', 'YN', 'Y', '', '=', '520', '복수 납품처 LIFO 배치 활성 (나중하차→안쪽, 먼저하차→문쪽)', '§4-2', 'Y', NULL, '20260715', '20260715'),
    ('MIX_ZONE_SPLIT_YN', '원지·판지 배송처 상이 시 전후 Zone 분할 적재 자동 전환', 'MIX', 'YN', 'Y', '', '=', '530', '원지·판지 배송처 상이 시 전후 Zone 분할 적재 자동 전환', '§4-2', 'Y', NULL, '20260715', '20260715'),
    ('MIX_WEIGHT_CHECK_YN', '혼적 총중량 검증 (원지+판지 ≤ 차량 최대 적재 중량)', 'MIX', 'YN', 'Y', '', '=', '540', '혼적 총중량 검증 (원지+판지 ≤ 차량 최대 적재 중량)', '§4-3', 'Y', NULL, '20260715', '20260715'),
    ('MIX_HEIGHT_CHECK_YN', '혼적 높이 검증 (파렛트고+원지다단높이+판지높이 ≤ 차량 최대 높이)', 'MIX', 'YN', 'Y', 'm', '=', '550', '혼적 높이 검증 (파렛트고+원지다단높이+판지높이 ≤ 차량 최대 높이)', '§4-3', 'Y', NULL, '20260715', '20260715'),
    ('MIX_3D_CHECK_YN', '혼적 Dead Space 포함 3D 블록 종합 검증', 'MIX', 'YN', 'Y', '', '=', '560', '혼적 Dead Space 포함 3D 블록 종합 검증', '§4-3', 'Y', NULL, '20260715', '20260715'),
    -- ─── 전역 제약 (GLOBAL) ───────────────────────────────
    ('MAX_VEHICLES_PER_GROUP', '그룹당 최대 배차 차량 수', 'GLOBAL', 'NUM', '99', '', '<=', '1010', '그룹당 최대 배차 차량 수', '§GLOBAL', 'Y', NULL, '20260715', '20260715'),
    ('ALLOW_SPLIT_ITEM', '단일 아이템 납품분할 허용', 'GLOBAL', 'YN', 'Y', '', '=', '1020', '단일 아이템 납품분할 허용', '§GLOBAL', 'Y', NULL, '20260715', '20260715'),
    ('ALLOW_MIXED_LOAD', '우편번호 앞 3자리 동일 납품처 혼적 허용', 'GLOBAL', 'YN', 'N', 'm', '=', '1030', '우편번호 앞 3자리 동일 납품처 혼적 허용', '§GLOBAL', 'Y', NULL, '20260715', '20260715'),
    ('MIN_FILL_RATIO', '최소 적재율(%) — 0=제한없음', 'GLOBAL', 'NUM', '0', '%', '>=', '1040', '최소 적재율(%) — 0=제한없음', '§GLOBAL', 'Y', NULL, '20260715', '20260715'),
    ('MAX_FILL_RATIO', '최대 적재율(%) — 초과배차 방지', 'GLOBAL', 'NUM', '100', '%', '<=', '1050', '최대 적재율(%) — 초과배차 방지', '§GLOBAL', 'Y', NULL, '20260715', '20260715'),
    ('BOARD_MIN_FILL_RATIO', '판지 전용 최소 적재율(%) — 0=미적용, 값설정 시 적재율 하한 강제 (BOARD_BULK_INTEGER_ONLY 연계)', 'GLOBAL', 'NUM', '0', 'm', '>=', '1060', '판지 전용 최소 적재율(%) — 0=미적용, 값설정 시 적재율 하한 강제 (BOARD_BULK_INTEGER_ONLY 연계)', '§GLOBAL', 'Y', NULL, '20260715', '20260715'),
    -- ─── 화물 제약 (CARGO) ────────────────────────────────
    ('MAX_ROLL_STACK_TIER', '최대 롤 적재 단수', 'CARGO', 'NUM', '2', '단', '<=', '1110', '최대 롤 적재 단수', '§CARGO', 'Y', NULL, '20260715', '20260715'),
    ('MAX_BOARD_HEIGHT_M', '판지 최대 적재 높이(m)', 'CARGO', 'NUM', '2.4', 'm', '<=', '1120', '판지 최대 적재 높이(m)', '§CARGO', 'Y', NULL, '20260715', '20260715'),
    ('ROLL_SINGLE_KG_FALLBACK', '롤 단중 미등록 시 fallback(kg)', 'CARGO', 'NUM', '600', '', '=', '1130', '롤 단중 미등록 시 fallback(kg)', '§CARGO', 'Y', NULL, '20260715', '20260715'),
    -- ─── 비용 제약 (COST) ─────────────────────────────────
    ('COST_REF_DATE', '운송비 기준일 (TODAY or YYYYMMDD)', 'COST', 'TEXT', 'TODAY', '롤', '=', '1210', '운송비 기준일 (TODAY or YYYYMMDD)', '§COST', 'Y', NULL, '20260715', '20260715'),
    ('COST_PENALTY_OVER', '초과적재 비용 패널티 배수', 'COST', 'NUM', '1.5', '', '=', '1220', '초과적재 비용 패널티 배수', '§COST', 'Y', NULL, '20260715', '20260715'),
    -- ─── 동적배차 구역 (DYNAMIC_ZONE) ────────────────
    ('DYNAMIC_GROUP_BY', '동적 그룹핑 기준 컬럼 — 동일 AREA_CD(권역) 납품처끼리 동적 배차 그룹 형성', 'DYNAMIC_ZONE', 'TEXT', 'AREA_CD', '', '=', '1310', '동적 그룹핑 기준 컬럼 — 동일 AREA_CD(권역) 납품처끼리 동적 배차 그룹 형성', '§DYN', 'Y', NULL, '20260715', '20260715'),
    ('DYNAMIC_DEFAULT_YN', '납품처 DYNAMIC_YN 미설정 시 기본값 (Y=동적배차 가능, N=불가)', 'DYNAMIC_ZONE', 'YN', 'Y', '', '=', '1320', '납품처 DYNAMIC_YN 미설정 시 기본값 (Y=동적배차 가능, N=불가)', '§DYN', 'Y', NULL, '20260715', '20260715'),
    ('DYNAMIC_MIN_GROUP_SIZE', '동적 그룹 최소 납품처 수 — 1=단독 납품처도 동적배차 허용', 'DYNAMIC_ZONE', 'NUM', '1', 'm', '>=', '1330', '동적 그룹 최소 납품처 수 — 1=단독 납품처도 동적배차 허용', '§DYN', 'Y', NULL, '20260715', '20260715'),
    ('DYNAMIC_AREA_FORCE_YN', '동일 구역(AREA_CD) 내 DYNAMIC_YN=Y 납품처는 반드시 동적 그룹 편입 강제', 'DYNAMIC_ZONE', 'YN', 'Y', '', '=', '1340', '동일 구역(AREA_CD) 내 DYNAMIC_YN=Y 납품처는 반드시 동적 그룹 편입 강제', '§DYN', 'Y', NULL, '20260715', '20260715'),
    -- ─── 차량 (VEHICLE) ───────────────────────────────────
    ('ALLOW_CARTYPE', '차종 허용여부', 'VEHICLE', 'YN', 'Y', '', '=', '9010', '차종 허용여부', '', 'Y', NULL, '20260715', '20260715')
ON DUPLICATE KEY UPDATE
    ITEM_NM = VALUES(ITEM_NM),
    ITEM_GRP = VALUES(ITEM_GRP),
    ITEM_TYPE = VALUES(ITEM_TYPE),
    DEFAULT_VAL = VALUES(DEFAULT_VAL),
    UNIT = VALUES(UNIT),
    CONST_OP = VALUES(CONST_OP),
    SORT_SEQ = VALUES(SORT_SEQ),
    DESCRIPTION = VALUES(DESCRIPTION),
    SOURCE_REF = VALUES(SOURCE_REF),
    ACTIVE_YN = VALUES(ACTIVE_YN),
    SELECT_OPTS = VALUES(SELECT_OPTS),
    LMODAT = VALUES(LMODAT);

-- ── 세트별 항목 설정값 (DS_DISPATCH_CONST_SETTING): 현재 데이터 없음 ──
-- 사용자 설정 후 자동 생성됨

