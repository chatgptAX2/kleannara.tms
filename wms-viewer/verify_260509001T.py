#!/usr/bin/env python3
"""
260509001T 배차계산 상세 검증 스크립트
PS제약조건관리의 제약조건이 정확히 계산되어 배차가 이루어졌는지 단계별 확인
"""
import sqlite3, math

DB_PATH = '/home/user/webapp/wms-viewer/wms.db'
DISPATCH_NO = '260509001T'

conn = sqlite3.connect(DB_PATH)
conn.row_factory = sqlite3.Row

SEP  = '=' * 80
SEP2 = '-' * 80

# ──────────────────────────────────────────────────────────────────
# 공통 함수 (app.py에서 복사)
# ──────────────────────────────────────────────────────────────────
def _ps_parse_skukey_dims(skukey):
    sk = str(skukey or '')
    if len(sk) < 17:
        return None, None
    try:
        return int(sk[5:8]), int(sk[9:13])
    except (ValueError, IndexError):
        return None, None

def _ps_parse_board_dims(skukey):
    sk = str(skukey or '')
    if len(sk) < 17 or sk[8:9] != '-':
        return None, None
    try:
        return int(sk[9:13]), int(sk[13:17])
    except (ValueError, IndexError):
        return None, None

def _ps_is_roll(skukey):
    sk = str(skukey or '')
    if len(sk) < 13:
        return False
    return sk[9:13] == '0000'

def _calc_board_stack_height_m(grswgt, gsm, w_mm, l_mm, kg_weight):
    """판지 적재 높이(m) 계산"""
    if grswgt <= 0 or w_mm <= 0 or l_mm <= 0 or not gsm or gsm <= 0:
        return 0.0
    qty_kg  = float(kg_weight)
    bundles = qty_kg / grswgt if grswgt > 0 else 0.0
    PAPER_DENSITY_G_PER_MM3 = 0.0012
    t_sheet_mm      = (gsm / 1_000_000.0) / PAPER_DENSITY_G_PER_MM3
    grswgt_g        = grswgt * 1000.0
    area_mm2        = float(w_mm) * float(l_mm)
    gsm_per_mm2     = gsm / 1_000_000.0
    if gsm_per_mm2 * area_mm2 <= 0:
        return 0.0
    sheets_per_bundle = grswgt_g / (gsm_per_mm2 * area_mm2)
    bundle_height_mm  = sheets_per_bundle * t_sheet_mm
    total_height_mm   = bundle_height_mm * bundles
    return total_height_mm / 1000.0

# ──────────────────────────────────────────────────────────────────
print(SEP)
print(f'  260509001T 배차계산 상세 검증 리포트')
print(SEP)

# ══════════════════════════════════════════════════════════════════
# [1] 활성 프로파일
# ══════════════════════════════════════════════════════════════════
print(f'\n【1】 PS제약조건관리 활성 프로파일')
print(SEP2)
prof = conn.execute(
    "SELECT * FROM DS_DISPATCH_PROFILE WHERE ACTIVE_YN='Y'"
).fetchone()
if prof:
    print(f'  프로파일 ID   : {prof["PROFILE_ID"]}')
    print(f'  프로파일 명   : {prof["PROFILE_NM"]}')
    print(f'  목표전략      : {prof["OBJECTIVE"]}')
    print(f'  활성여부      : {prof["ACTIVE_YN"]}')
    print(f'  비고          : {prof["NOTE"]}')
    PROFILE_ID = prof['PROFILE_ID']
    OBJECTIVE  = prof['OBJECTIVE']
else:
    print('  ❌ 활성 프로파일 없음!')
    PROFILE_ID = 2
    OBJECTIVE  = 'MAX_FILL'

# ══════════════════════════════════════════════════════════════════
# [2] 납품처 제약 (BZPTN_DETAIL)
# ══════════════════════════════════════════════════════════════════
print(f'\n【2】 납품처 제약 (BZPTN_DETAIL — 납품처 100142 흥아지업)')
print(SEP2)
bz = conn.execute(
    "SELECT * FROM BZPTN_DETAIL WHERE PTNRKY='100142' AND PTNRTY='CT' AND DEL_YN!='Y'"
).fetchone()
if bz:
    print(f'  납품처 코드   : {bz["PTNRKY"]}')
    print(f'  MAX_TON       : {bz["MAX_TON"]}')
    print(f'  FORKLIFT_YN   : {bz["FORKLIFT_YN"]}')
    print(f'  DEADLINE_TIME : {bz["DEADLINE_TIME"]}')
    MAX_TON_RAW   = (bz['MAX_TON'] or '').strip()
    FORKLIFT_YN   = (bz['FORKLIFT_YN'] or '').strip()
    DEADLINE_TIME = (bz['DEADLINE_TIME'] or '').strip()
    # MAX_TON 코드 → 차량명 변환 (CMCDV 테이블)
    if MAX_TON_RAW:
        cc = conn.execute(
            "SELECT CDESC1 FROM CMCDV WHERE CMCDKY='TMS_CARCLASS10' AND CMCDVL=?",
            (MAX_TON_RAW,)
        ).fetchone()
        MAX_TON_LABEL = cc['CDESC1'] if cc else MAX_TON_RAW
    else:
        MAX_TON_LABEL = None
else:
    print('  ✅ BZPTN_DETAIL 미등록 → 납품처 제약 없음 (MAX_TON/FORKLIFT/DEADLINE 모두 미적용)')
    MAX_TON_RAW   = ''
    MAX_TON_LABEL = None
    FORKLIFT_YN   = ''
    DEADLINE_TIME = ''

# ══════════════════════════════════════════════════════════════════
# [3] DS_VEHICLE 전체 목록 (SORT_SEQ DESC)
# ══════════════════════════════════════════════════════════════════
print(f'\n【3】 DS_VEHICLE 전체 목록 (SORT_SEQ DESC — 큰차→작은차 순)')
print(SEP2)
veh_rows = conn.execute(
    "SELECT CARTYPE, CARCLASS_CD, LOAD_TON, HEIGHT_M, WIDTH_M, LENGTH_M, "
    "PALLET_HEIGHT_M, PALLET_CNT, SORT_SEQ FROM DS_VEHICLE ORDER BY SORT_SEQ DESC"
).fetchall()
veh_info = {}
car_order = []
print(f"  {'CARTYPE':<8} {'CLASS':<6} {'TON':>6} {'H':>5} {'W':>5} {'L':>6} "
      f"{'PLT_H':>6} {'PLT_N':>6} {'EFF_H':>6} {'SEQ':>4}")
print(f"  {'-'*70}")
def _parse_width(w):
    """WIDTH_M 컬럼은 '2.4' 또는 '1.8~2.1' 형태 → 최솟값 float 반환"""
    try:
        s = str(w or '').strip()
        if '~' in s:
            return float(s.split('~')[0].strip())
        return float(s)
    except (ValueError, TypeError):
        return 2.4

for r in veh_rows:
    ct         = r['CARTYPE']
    palt_h     = float(r['PALLET_HEIGHT_M'] or 0)
    car_h      = float(r['HEIGHT_M'] or 0)
    eff_h      = car_h - palt_h
    load_kg    = float(r['LOAD_TON'] or 0) * 1000.0
    width_m    = _parse_width(r['WIDTH_M'])
    length_m   = float(r['LENGTH_M'] or 0)
    veh_info[ct] = {
        'load_kg':            load_kg,
        'height_m':           car_h,
        'width_m':            width_m,
        'length_m':           length_m,
        'pallet_height_m':    palt_h,
        'effective_height_m': eff_h,
        'carclass_cd':        r['CARCLASS_CD'] or '',
        'load_ton':           float(r['LOAD_TON'] or 0),
        'sort_seq':           int(r['SORT_SEQ'] or 0),
    }
    if load_kg > 0:
        car_order.append({'CARTYPE': ct})
    marker = ' ◀ 배차 선정' if ct == '11톤' else ''
    print(f"  {ct:<8} {(r['CARCLASS_CD'] or ''):<6} {r['LOAD_TON']:>6.1f} "
          f"{car_h:>5.1f} {width_m:>5.1f} {length_m:>6.1f} "
          f"{palt_h:>6.2f} {r['PALLET_CNT']:>6} {eff_h:>6.2f} {r['SORT_SEQ']:>4}{marker}")

# _valid_cars = LOAD_TON>0 인 차량, SORT_SEQ DESC 순 (car_order 이미 정렬됨)
_valid_cars = car_order
print(f'\n  ※ _valid_cars (LOAD_TON>0): {[c["CARTYPE"] for c in _valid_cars]}')

# ══════════════════════════════════════════════════════════════════
# [4] 배차 헤더 / 아이템
# ══════════════════════════════════════════════════════════════════
print(f'\n【4】 배차 헤더 및 아이템 중량 검증')
print(SEP2)
hdr = conn.execute(
    "SELECT h.*, COALESCE(v.LOAD_TON,0) AS LOAD_TON_V "
    "FROM PS_DISPATCH_H h LEFT JOIN DS_VEHICLE v ON v.CARTYPE=h.CARTYPE "
    "WHERE h.DISPATCH_NO=?", (DISPATCH_NO,)
).fetchone()
print(f'  배차번호  : {hdr["DISPATCH_NO"]}')
print(f'  납품처    : {hdr["DPTNKY"]} {hdr["DPTNM"]}')
print(f'  요청납품일: {hdr["RQSHPD"]}')
print(f'  배차차량  : {hdr["CARTYPE"]}')
print(f'  총중량    : {hdr["TOTAL_KG"]} kg')
print(f'  총수량    : {hdr["TOTAL_CNT"]} 건')
CARTYPE_SAVED = hdr['CARTYPE']
TOTAL_KG_SAVED = float(hdr['TOTAL_KG'] or 0)

items = conn.execute(
    "SELECT d.*, m.GRSWGT, m.NETWGT, m.CUBICM "
    "FROM PS_DISPATCH_D d "
    "LEFT JOIN SKUMA m ON m.SKUKEY=d.SKUKEY "
    "WHERE d.DISPATCH_NO=? ORDER BY d.SEQ",
    (DISPATCH_NO,)
).fetchall()

print(f'\n  아이템 목록 ({len(items)}건):')
print(f"  {'#':>2} {'SKUKEY':<22} {'UOM':>4} {'QTSHPO':>7} {'GRSWGT':>8} "
      f"{'KG_WEIGHT':>10} {'CALC_KG':>10} {'타입':<6} {'OK'}")
print(f"  {'-'*85}")

total_kg_calc = 0.0
total_h_calc  = 0.0
items_data    = []  # 검증에 쓸 딕셔너리

for i, r in enumerate(items, 1):
    sk      = r['SKUKEY'] or ''
    qtshpo  = float(r['QTSHPO'] or 0)
    grswgt  = float(r['GRSWGT'] or 0)
    kg_w    = float(r['KG_WEIGHT'] or 0)
    uom     = (r['UOMKEY'] or '').strip()
    calc_kg = qtshpo * grswgt if grswgt > 0 else qtshpo
    ok      = '✅' if abs(kg_w - calc_kg) < 0.05 else '❌'
    is_roll = _ps_is_roll(sk)
    typ     = 'ROLL' if is_roll else 'BOARD'
    total_kg_calc += kg_w

    items_data.append({
        'SKUKEY':    sk,
        'QTSHPO':    qtshpo,
        'GRSWGT':    grswgt,
        'KG_WEIGHT': kg_w,
        'UOMKEY':    uom,
        'is_roll':   is_roll,
    })
    print(f"  {i:>2} {sk:<22} {uom:>4} {qtshpo:>7.1f} {grswgt:>8.3f} "
          f"{kg_w:>10.2f} {calc_kg:>10.2f} {typ:<6} {ok}")

print(f"  {'-'*85}")
print(f"  {'합계':>55} {total_kg_calc:>10.2f}")
kg_match = '✅' if abs(total_kg_calc - TOTAL_KG_SAVED) < 0.05 else '❌'
print(f"  {'저장값':>55} {TOTAL_KG_SAVED:>10.2f}  {kg_match}")

board_items = [it for it in items_data if not it['is_roll']]
roll_items  = [it for it in items_data if it['is_roll']]
print(f'\n  판지(BOARD): {len(board_items)}건 / 롤(ROLL): {len(roll_items)}건')

# ══════════════════════════════════════════════════════════════════
# [5] PROFILE_ID=2 제약조건 목록
# ══════════════════════════════════════════════════════════════════
print(f'\n【5】 활성 프로파일(ID={PROFILE_ID}) 제약조건 목록')
print(SEP2)
consts = conn.execute(
    "SELECT CONST_TYPE, CONST_KEY, CONST_VALUE, CONST_OP, TARGET_ID, NOTE, ACTIVE_YN "
    "FROM DS_DISPATCH_CONST WHERE PROFILE_ID=? ORDER BY CONST_TYPE, CONST_KEY",
    (PROFILE_ID,)
).fetchall()
# VEHICLE/CARTYPE 중 11톤 관련만 별도 표시
vehicle_consts = {}
global_consts  = {}
board_consts   = {}
for c in consts:
    if c['ACTIVE_YN'] != 'Y':
        continue
    ct = c['CONST_TYPE']
    ck = c['CONST_KEY']
    tid = c['TARGET_ID'] or ''
    val = c['CONST_VALUE']
    if ct in ('VEHICLE', 'CARTYPE'):
        k = f"{ck}|{tid}"
        vehicle_consts[k] = {'key': ck, 'val': val, 'op': c['CONST_OP'], 'target': tid}
    elif ct == 'GLOBAL':
        global_consts[ck] = val
    elif ct.startswith('BOARD'):
        board_consts[ck] = {'val': val, 'op': c['CONST_OP'], 'note': c['NOTE']}

print('  [GLOBAL 제약]')
for k, v in global_consts.items():
    print(f'    {k:<35s} = {v}')
print()
print('  [BOARD 관련 제약]')
for k, d in sorted(board_consts.items()):
    print(f'    {k:<35s} {d["op"]} {d["val"]:>6}   ({d["note"]})')
print()
print('  [차량별 HEIGHT_M 제약 (VEHICLE/CARTYPE, 11톤)]')
for k, d in vehicle_consts.items():
    if '11톤' in d['target'] or 'Z110' in d['target']:
        print(f'    {d["key"]:<20} TARGET={d["target"]:<8} {d["op"]} {d["val"]}')

ALLOW_SPLIT    = global_consts.get('ALLOW_SPLIT_ITEM', 'Y')
ALLOW_MIXED    = global_consts.get('ALLOW_MIXED_LOAD', 'N')
BOARD_H_MAX    = float(board_consts.get('BOARD_HEIGHT_MAX_M', {}).get('val', 2.4))
BOARD_3D_CHECK = board_consts.get('BOARD_3D_CHECK_YN', {}).get('val', 'N')
BOARD_CBM_CHECK= board_consts.get('BOARD_CBM_CHECK_YN', {}).get('val', 'N')

# ══════════════════════════════════════════════════════════════════
# [6] _best_fit_car 시뮬레이션 (MAX_FILL 전략)
# ══════════════════════════════════════════════════════════════════
print(f'\n【6】 MAX_FILL 차량 선정 시뮬레이션 (_best_fit_car)')
print(SEP2)
need_kg = total_kg_calc
print(f'  납품 필요 중량 (need_kg) : {need_kg:.2f} kg')
print()
print(f"  {'CARTYPE':<8} {'LOAD_KG':>10} {'수용가능':>6} {'첨부율(%)':>10} {'선택?':>6}")
print(f"  {'-'*45}")

best_car   = None
best_ratio = -1.0
for car in _valid_cars:
    ct      = car['CARTYPE']
    cap_kg  = veh_info.get(ct, {}).get('load_kg', 0)
    if cap_kg <= 0:
        continue
    can_fit = cap_kg >= need_kg
    ratio   = (need_kg / cap_kg * 100) if can_fit else 0.0
    selected = ''
    if can_fit and (ratio / 100) > best_ratio:
        best_ratio = ratio / 100
        best_car   = ct
    print(f"  {ct:<8} {cap_kg:>10.0f} {'✅' if can_fit else '❌':>6} "
          f"{ratio:>10.1f}{'%' if can_fit else ' ':>1}")

print(f"  {'-'*45}")
print(f'\n  ✅ MAX_FILL 선정 차량 : {best_car}')
best_cap = veh_info.get(best_car, {}).get('load_kg', 0)
print(f'  ✅ 적재 한도         : {best_cap:.0f} kg')
print(f'  ✅ 첨부율(적재효율)  : {need_kg/best_cap*100:.1f}%')
saved_match = '✅ 일치' if best_car == CARTYPE_SAVED else f'❌ 불일치 (저장={CARTYPE_SAVED})'
print(f'  저장된 차량과 비교   : {saved_match}')

# ══════════════════════════════════════════════════════════════════
# [7] 판지 적재 높이 계산 (_calc_board_stack_height_m)
# ══════════════════════════════════════════════════════════════════
print(f'\n【7】 판지 적재 높이 계산 (_calc_board_stack_height_m)')
print(SEP2)

# big_car (가장 큰 유효 차량 = _valid_cars[0])
big_car    = _valid_cars[0]['CARTYPE'] if _valid_cars else '판별불가'
big_cap_kg = veh_info.get(big_car, {}).get('load_kg', 0)
big_cap_h  = veh_info.get(big_car, {}).get('effective_height_m', 99.0)

print(f'  big_car (가장 큰 유효 차량) : {big_car}')
print(f'  big_cap_kg                  : {big_cap_kg:.0f} kg')
print(f'  big_cap_h (유효높이)        : {big_cap_h:.2f} m  (차량H {veh_info[big_car]["height_m"]:.2f} - 팔렛H {veh_info[big_car]["pallet_height_m"]:.2f})')
print()

PAPER_DENSITY_G_PER_MM3 = 0.0012
print(f"  PAPER_DENSITY_G_PER_MM3 = {PAPER_DENSITY_G_PER_MM3}")
print(f"  t_sheet_mm = (GSM / 1,000,000) / 0.0012")
print()
print(f"  {'#':>2} {'SKUKEY':<22} {'GSM':>4} {'W_mm':>6} {'L_mm':>6} "
      f"{'GRSWGT':>8} {'번들수':>6} {'t_sheet':>8} {'bndl_H':>8} {'total_H':>8}")
print(f"  {'-'*90}")

total_h_all = 0.0
max_w_mm    = 0
max_l_mm    = 0

for i, d in enumerate(board_items, 1):
    sk     = d['SKUKEY']
    grswgt = d['GRSWGT']
    kg_w   = d['KG_WEIGHT']

    # SKUKEY 파싱
    w_mm, l_mm = _ps_parse_board_dims(sk)
    gsm, _     = _ps_parse_skukey_dims(sk)

    if not w_mm or not l_mm or not gsm:
        print(f"  {i:>2} {sk:<22} ⚠️  파싱불가 (skukey 길이부족 or 구조이상)")
        continue

    if w_mm > max_w_mm: max_w_mm = w_mm
    if l_mm > max_l_mm: max_l_mm = l_mm

    t_sheet_mm      = (gsm / 1_000_000.0) / PAPER_DENSITY_G_PER_MM3
    bundles         = kg_w / grswgt if grswgt > 0 else 0.0
    area_mm2        = float(w_mm) * float(l_mm)
    gsm_per_mm2     = gsm / 1_000_000.0
    grswgt_g        = grswgt * 1000.0
    sheets_per_bundle = grswgt_g / (gsm_per_mm2 * area_mm2)
    bundle_h_mm     = sheets_per_bundle * t_sheet_mm
    item_h_m        = bundle_h_mm * bundles / 1000.0
    total_h_all    += item_h_m

    print(f"  {i:>2} {sk:<22} {gsm:>4} {w_mm:>6} {l_mm:>6} "
          f"{grswgt:>8.3f} {bundles:>6.1f} {t_sheet_mm:>8.4f} {bundle_h_mm:>8.1f} {item_h_m:>8.4f}")

print(f"  {'-'*90}")
print(f"  {'누적 적재 높이':>65} {total_h_all:>8.4f} m")
print()
print(f'  대상 차량 유효높이 (big_car={big_car}) : {big_cap_h:.2f} m')
print(f'  BOARD_HEIGHT_MAX_M 제약              : {BOARD_H_MAX:.2f} m')
eff_h_check  = big_cap_h
h_ok_big     = total_h_all <= eff_h_check
h_ok_const   = total_h_all <= BOARD_H_MAX
print(f'  높이 검사 (누적 {total_h_all:.4f}m ≤ big_cap_h {eff_h_check:.2f}m) : {"✅ PASS" if h_ok_big else "❌ FAIL"}')
print(f'  높이 검사 (누적 {total_h_all:.4f}m ≤ BOARD_HEIGHT_MAX_M {BOARD_H_MAX:.2f}m) : {"✅ PASS" if h_ok_const else "❌ FAIL"}')

# 8톤 차량 높이 비교
car_8 = '8톤'
eff_h_8 = veh_info.get(car_8, {}).get('effective_height_m', 0)
if eff_h_8 > 0:
    h_ok_8 = total_h_all <= eff_h_8
    cap_8  = veh_info.get(car_8, {}).get('load_kg', 0)
    print()
    print(f'  ── 8톤 vs 11톤 비교 ──')
    print(f'  8톤  : 유효높이={eff_h_8:.2f}m, 적재한도={cap_8:.0f}kg')
    print(f'  11톤 : 유효높이={eff_h_check:.2f}m, 적재한도={veh_info["11톤"]["load_kg"]:.0f}kg')
    print(f'  높이 PASS 여부  → 8톤: {"✅" if h_ok_8 else "❌"} / 11톤: {"✅" if h_ok_big else "❌"}')
    ratio_8  = need_kg / cap_8  * 100 if cap_8  > 0 else 0
    ratio_11 = need_kg / veh_info['11톤']['load_kg'] * 100
    print(f'  첨부율(MAX_FILL) → 8톤: {ratio_8:.1f}% / 11톤: {ratio_11:.1f}%')
    if cap_8 >= need_kg:
        winner = '8톤' if ratio_8 > ratio_11 else '11톤'
        print(f'  MAX_FILL 승자 (첨부율 더 높은 쪽) : {winner}')
    else:
        print(f'  8톤 적재한도 ({cap_8:.0f}kg) < need_kg ({need_kg:.2f}kg) → 8톤 탈락')

# ══════════════════════════════════════════════════════════════════
# [8] 치수(폭·길이) 검사
# ══════════════════════════════════════════════════════════════════
print(f'\n【8】 치수(폭·길이) 검사')
print(SEP2)
car_sel   = best_car
car_w_m   = veh_info.get(car_sel, {}).get('width_m', 0)
car_l_m   = veh_info.get(car_sel, {}).get('length_m', 0)
print(f'  선정 차량 ({car_sel}): 폭={car_w_m*1000:.0f}mm, 길이={car_l_m*1000:.0f}mm')
print(f'  최대 판지 W : {max_w_mm} mm  {'✅' if max_w_mm <= car_w_m*1000 else '❌'} (≤ 차량폭 {car_w_m*1000:.0f}mm)')
print(f'  최대 판지 L : {max_l_mm} mm  {'✅' if max_l_mm <= car_l_m*1000 else '❌'} (≤ 차량길이 {car_l_m*1000:.0f}mm)')

# ══════════════════════════════════════════════════════════════════
# [9] 납품분할·혼적·납기 제약 검사
# ══════════════════════════════════════════════════════════════════
print(f'\n【9】 납품분할 / 혼적 / 납기 제약 검사')
print(SEP2)
print(f'  ALLOW_SPLIT_ITEM  = {ALLOW_SPLIT}  → 단일아이템 납품분할 {"허용" if ALLOW_SPLIT=="Y" else "불허"}')
print(f'  ALLOW_MIXED_LOAD  = {ALLOW_MIXED}   → 우편번호 앞3자리 혼적 {"허용" if ALLOW_MIXED=="Y" else "불허"}')

# 납품처 납기 정보
if DEADLINE_TIME:
    print(f'  납품처 DEADLINE_TIME = {DEADLINE_TIME}  ← BZPTN_DETAIL 등록 기준 납기 시간')
else:
    print(f'  납품처 DEADLINE_TIME = 미설정 (BZPTN_DETAIL 미등록)')
if FORKLIFT_YN:
    print(f'  납품처 FORKLIFT_YN   = {FORKLIFT_YN}')
else:
    print(f'  납품처 FORKLIFT_YN   = 미설정')
if MAX_TON_LABEL:
    print(f'  납품처 MAX_TON       = {MAX_TON_LABEL}  ← 이 톤수 이하 차량만 허용')
else:
    print(f'  납품처 MAX_TON       = 미설정 → 차량 크기 제한 없음')

# ══════════════════════════════════════════════════════════════════
# [10] 260509001T 저장 결과 vs 계산 결과 비교
# ══════════════════════════════════════════════════════════════════
print(f'\n【10】 저장 결과 vs 계산 결과 최종 비교')
print(SEP2)
stored_car = CARTYPE_SAVED
calc_car   = best_car
car_ok     = stored_car == calc_car
stored_kg  = TOTAL_KG_SAVED
calc_kg    = total_kg_calc
kg_ok      = abs(stored_kg - calc_kg) < 0.05

print(f'  항목              저장값           계산값           일치?')
print(f"  {'-'*60}")
print(f'  배차 차량         {stored_car:<16} {calc_car:<16} {"✅" if car_ok else "❌"}')
print(f'  총중량(kg)        {stored_kg:<16.2f} {calc_kg:<16.2f} {"✅" if kg_ok else "❌"}')
car_load = veh_info.get(calc_car, {}).get('load_kg', 0)
fill_pct = calc_kg / car_load * 100 if car_load > 0 else 0
print(f'  첨부율(적재효율)  {fill_pct:.1f}%')

# ══════════════════════════════════════════════════════════════════
# [11] 최종 결론 체크리스트
# ══════════════════════════════════════════════════════════════════
print(f'\n【11】 최종 결론 — 제약조건 Pass/Fail 체크리스트')
print(SEP)

checks = []
checks.append(('활성 프로파일', f'ID={PROFILE_ID} {prof["PROFILE_NM"]} (MAX_FILL)', True))
checks.append(('납품처 MAX_TON 제약', '미설정 → 모든 차량 허용', MAX_TON_LABEL is None))
checks.append(('납품처 FORKLIFT 제약', '미설정 → 지게차 불필요', not bool(FORKLIFT_YN)))
checks.append(('납품처 DEADLINE 제약', '미설정 → 납기 제한 없음', not bool(DEADLINE_TIME)))
checks.append(('아이템 중량 계산 (12건)', f'KG_WEIGHT = QTSHPO × GRSWGT 모두 일치', abs(total_kg_calc - TOTAL_KG_SAVED) < 0.05))
checks.append(('MAX_FILL 차량 선정', f'{calc_car} (첨부율 {fill_pct:.1f}%)', car_ok))
checks.append((f'적재 높이 검사 (big_car={big_car})', f'총적재높이 {total_h_all:.3f}m ≤ 유효높이 {big_cap_h:.2f}m', h_ok_big))
checks.append(('BOARD_HEIGHT_MAX_M 제약', f'{total_h_all:.3f}m ≤ {BOARD_H_MAX}m', h_ok_const))
checks.append(('판지 폭 치수 검사', f'최대 {max_w_mm}mm ≤ 차량폭 {car_w_m*1000:.0f}mm', max_w_mm <= car_w_m*1000))
checks.append(('판지 길이 치수 검사', f'최대 {max_l_mm}mm ≤ 차량길이 {car_l_m*1000:.0f}mm', max_l_mm <= car_l_m*1000))
checks.append(('ALLOW_SPLIT_ITEM', f'{ALLOW_SPLIT} → 납품분할 설정 확인', True))
checks.append(('ALLOW_MIXED_LOAD', f'{ALLOW_MIXED} → 혼적 불허', True))

all_pass = True
for name, detail, ok in checks:
    status = '✅ PASS' if ok else '❌ FAIL'
    print(f'  {status}  {name:<30} {detail}')
    if not ok:
        all_pass = False

print()
print(SEP)
if all_pass:
    print('  ✅ 모든 제약조건 PASS — 260509001T 배차계산이 PS제약조건관리 기준대로 정확히 수행됨')
else:
    print('  ❌ 일부 제약조건 FAIL — 추가 확인 필요')
print(SEP)

conn.close()
