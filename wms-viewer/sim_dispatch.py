
# 자동배차 분산 원인 시뮬레이션 스크립트
import sqlite3

# ===== 실제 DB에서 데이터 로드 =====
conn = sqlite3.connect('/home/user/webapp/wms-viewer/wms.db')
conn.row_factory = sqlite3.Row

# 차량 제원
cars_raw = conn.execute('''
    SELECT CARTYPE, LOAD_TON, WIDTH_M, LENGTH_M, HEIGHT_M
    FROM DS_VEHICLE
    ORDER BY LOAD_TON
''').fetchall()
conn.close()

# car_order: 큰→작은 순
veh_info = {}
car_order_list = []
for c in reversed(cars_raw):
    ct = c['CARTYPE']
    try:
        w = float(c['WIDTH_M'])
    except:
        w = 1.8  # "1.8~2.1" 같은 경우 기본값
    veh_info[ct] = {
        'load_kg': float(c['LOAD_TON'] or 0) * 1000,
        'width_m': w,
        'length_m': float(c['LENGTH_M'] or 0),
        'height_m': float(c['HEIGHT_M'] or 0),
        'effective_height_m': float(c['HEIGHT_M'] or 0),
    }
    car_order_list.append(ct)

print("=== 차량 제원 (큰→작은 순) ===")
for ct in car_order_list:
    v = veh_info[ct]
    print(f"  {ct}: 적재{v['load_kg']}kg, W={v['width_m']}m, L={v['length_m']}m, H={v['height_m']}m")

# ===== 아이템 데이터 =====
board_items = [
    {'SHPOKY':'2004429088','SHPOIT':'20','SKUKEY':'F2S11240-06450840A','KG_WEIGHT':97.5,   'UOMKEY':'R','QTSHPO':1.5},
    {'SHPOKY':'2004429088','SHPOIT':'30','SKUKEY':'F3SM1240-06400940A','KG_WEIGHT':90.24,  'UOMKEY':'R','QTSHPO':1.25},
    {'SHPOKY':'2004429089','SHPOIT':'10','SKUKEY':'F3S11450-05350990B','KG_WEIGHT':1570.8, 'UOMKEY':'R','QTSHPO':13.2},
    {'SHPOKY':'2004429090','SHPOIT':'10','SKUKEY':'F3S11400-07750635B','KG_WEIGHT':490.0,  'UOMKEY':'R','QTSHPO':5.0},
]

def parse_board_dims(sk):
    try:
        w = int(sk[9:13])
        l = int(sk[13:17])
        return w, l
    except:
        return 0, 0

def board_kg(it):
    return float(it.get('KG_WEIGHT') or 0)

def best_fit_car(need_kg):
    best_car, best_ratio = None, -1.0
    for ct in car_order_list:
        cap_kg = veh_info[ct]['load_kg']
        if cap_kg <= 0:
            continue
        if cap_kg >= need_kg:
            ratio = need_kg / cap_kg
            if ratio > best_ratio:
                best_ratio = ratio
                best_car = ct
    if best_car is None:
        best_car = car_order_list[0]
        best_ratio = 0.0
    return best_car, best_ratio

def check_board_dims_ok(items_list, cartype):
    ci = veh_info.get(cartype, {})
    car_w_mm = ci.get('width_m', 99.0) * 1000
    car_l_mm = ci.get('length_m', 99.0) * 1000
    max_w, max_l = 0, 0
    for it in items_list:
        w, l = parse_board_dims(it['SKUKEY'])
        max_w = max(max_w, w)
        max_l = max(max_l, l)
    width_ok  = (max_w == 0 or max_w <= car_w_mm)
    length_ok = (max_l == 0 or max_l <= car_l_mm)
    return (width_ok and length_ok), max_w, max_l

# ==========================================
print()
print("=" * 65)
print("분석1. 아이템별 판지 치수 확인")
print("=" * 65)
for it in board_items:
    w, l = parse_board_dims(it['SKUKEY'])
    kg = board_kg(it)
    print(f"  {it['SHPOKY']}#{it['SHPOIT']}  {it['SKUKEY']}  W={w}mm  L={l}mm  {kg}kg")

# ==========================================
print()
print("=" * 65)
print("분석2. 만약 합산 배차했다면?")
print("=" * 65)
total_kg = sum(board_kg(it) for it in board_items)
car, ratio = best_fit_car(total_kg)
ok, max_w, max_l = check_board_dims_ok(board_items, car)
print(f"  합산 중량: {total_kg:.2f}kg → _best_fit_car → {car} (첨부율 {ratio*100:.1f}%)")
print(f"  치수검사({car}): max_W={max_w}mm vs {veh_info[car]['width_m']*1000:.0f}mm, max_L={max_l}mm vs {veh_info[car]['length_m']*1000:.0f}mm → {'OK' if ok else '초과!'}")

# ==========================================
print()
print("=" * 65)
print("분석3. 실제 코드 [STEP-1] — big_car 기준 중량/높이 그룹분할")
print("=" * 65)
big_car = car_order_list[0]  # 가장 큰 차량
big_cap_kg = veh_info[big_car]['load_kg']
big_cap_h  = veh_info[big_car]['effective_height_m']
print(f"  big_car={big_car}, big_cap_kg={big_cap_kg}kg")
print()

veh_list_b = []
cur_items_b, cur_kg_b, cur_h_b = [], 0.0, 0.0
for it in board_items:
    qty_kg = board_kg(it)
    item_h = 0.0
    kg_over = bool(cur_items_b) and (cur_kg_b + qty_kg > big_cap_kg)
    h_over  = bool(cur_items_b) and item_h > 0 and (cur_h_b + item_h > big_cap_h)
    key = it['SHPOKY']+'#'+it['SHPOIT']
    print(f"  → {key}: {qty_kg}kg | 누적={cur_kg_b}kg → 추가시={cur_kg_b+qty_kg:.2f}kg | kg_over={kg_over}")
    if kg_over or h_over:
        veh_list_b.append({'items': cur_items_b[:], 'total_kg': cur_kg_b})
        keys_prev = [x['SHPOKY']+'#'+x['SHPOIT'] for x in cur_items_b]
        print(f"    ★ 중량초과! 이전그룹 확정: {keys_prev} / {cur_kg_b}kg")
        cur_items_b, cur_kg_b, cur_h_b = [], 0.0, 0.0
    cur_items_b.append(it)
    cur_kg_b += qty_kg
if cur_items_b:
    veh_list_b.append({'items': cur_items_b[:], 'total_kg': cur_kg_b})

print()
print(f"  [STEP-1] 결과: {len(veh_list_b)}개 그룹")
for i, v in enumerate(veh_list_b):
    keys = [x['SHPOKY']+'#'+x['SHPOIT'] for x in v['items']]
    print(f"    그룹{i+1}: {keys} / {v['total_kg']:.2f}kg")

# ==========================================
print()
print("=" * 65)
print("분석4. [STEP-2] 각 그룹별 차량 선정 및 치수검사")
print("=" * 65)
for i, veh in enumerate(veh_list_b):
    veh_kg = veh['total_kg']
    veh_car, ratio = best_fit_car(veh_kg)
    keys = [x['SHPOKY']+'#'+x['SHPOIT'] for x in veh['items']]
    print(f"\n  그룹{i+1} ({keys}, {veh_kg:.2f}kg)")
    print(f"    1순위 중량기준: {veh_car} (첨부율 {ratio*100:.1f}%)")

    ok, max_w, max_l = check_board_dims_ok(veh['items'], veh_car)
    car_w_mm = veh_info[veh_car]['width_m'] * 1000
    car_l_mm = veh_info[veh_car]['length_m'] * 1000
    print(f"    치수검사: max_W={max_w}mm vs 차량W={car_w_mm:.0f}mm, max_L={max_l}mm vs 차량L={car_l_mm:.0f}mm → {'OK' if ok else '초과!'}")

    final_car = veh_car
    if not ok:
        print(f"    치수초과 → 업그레이드 탐색 (작은→큰 순):")
        for ct in reversed(car_order_list):
            cap_kg = veh_info[ct]['load_kg']
            if cap_kg < veh_kg:
                print(f"      {ct}: 중량미달({cap_kg}kg < {veh_kg}kg) 스킵")
                continue
            ok2, mw2, ml2 = check_board_dims_ok(veh['items'], ct)
            cw2 = veh_info[ct]['width_m']*1000
            cl2 = veh_info[ct]['length_m']*1000
            print(f"      {ct}: W={cw2:.0f}mm L={cl2:.0f}mm → {'OK' if ok2 else '초과'}")
            if ok2:
                final_car = ct
                print(f"      → 업그레이드: {veh_car} → {ct}")
                break
    print(f"    *** 최종 선정 차량: {final_car} ***")

# ==========================================
print()
print("=" * 65)
print("분석5. 071T (1570.8kg) 단독 배차 — 2톤 선택 이유")
print("=" * 65)
kg_071 = 1570.8
car_071, ratio_071 = best_fit_car(kg_071)
print(f"  need_kg={kg_071}kg")
for ct in car_order_list:
    cap = veh_info[ct]['load_kg']
    if cap >= kg_071:
        r = kg_071/cap
        print(f"    {ct}: {cap}kg, 첨부율={r*100:.1f}%  {'← 최고 첨부율(선택됨)' if ct == car_071 else ''}")
    else:
        print(f"    {ct}: {cap}kg, 중량미달")
print(f"  → 선정: {car_071} (첨부율 {ratio_071*100:.1f}%)")
