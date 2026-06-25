"""
TMS 추가 테이블 생성 + 더미 데이터 30건씩 삽입
- BZPTN_DETAIL : 납품처 상세정보
- VHCMA        : 차량 마스터
"""
import sqlite3, random, datetime

DB_PATH = "/home/user/webapp/wms-viewer/wms.db"

DDL = """
CREATE TABLE IF NOT EXISTS BZPTN_DETAIL (
    PTNRKY          TEXT    NOT NULL,
    PTNRTY          TEXT    NOT NULL DEFAULT 'CT',
    OWNRKY          TEXT    NOT NULL DEFAULT 'KN',
    WAREKY          TEXT    NOT NULL DEFAULT 'W001',

    ROUTE_CD        TEXT,
    ITEM_GROUP      TEXT,
    UNLOAD_TIME     INTEGER DEFAULT 0,

    INB_TIME_FROM1  TEXT,
    INB_TIME_TO1    TEXT,

    AREA_CD         TEXT,
    MAX_HEIGHT      REAL    DEFAULT 0,
    FORKLIFT_YN     TEXT    DEFAULT 'N',
    HANDWORK_YN     TEXT    DEFAULT 'N',

    AUTO_PLT        REAL    DEFAULT 0,
    MAX_BOX_QTY     INTEGER DEFAULT 0,
    AUTO_ALLOC_YN   TEXT    DEFAULT 'N',

    SINGLE_ITEM_YN  TEXT    DEFAULT 'N',
    NY_TYPE         TEXT    DEFAULT 'N',
    SINGLE_HEIGHT   REAL    DEFAULT 0,

    DYNAMIC_YN      TEXT    DEFAULT 'N',
    LTL_YN          TEXT    DEFAULT 'N',
    PRIORITY_YN     TEXT    DEFAULT 'N',
    MIN_QTSIWH      REAL    DEFAULT 0,

    LATITUDE        REAL    DEFAULT 0,
    LONGITUDE       REAL    DEFAULT 0,

    DEL_YN          TEXT    DEFAULT 'N',
    CREDAT          TEXT    DEFAULT ' ',
    CRETIM          TEXT    DEFAULT ' ',
    CREUSR          TEXT    DEFAULT ' ',
    LMODAT          TEXT    DEFAULT ' ',
    LMOTIM          TEXT    DEFAULT ' ',
    LMOUSR          TEXT    DEFAULT ' ',

    PRIMARY KEY (PTNRKY, PTNRTY, OWNRKY, WAREKY)
);

CREATE TABLE IF NOT EXISTS VHCMA (
    VEHICLE_NO          TEXT    NOT NULL,
    OWNRKY              TEXT    NOT NULL DEFAULT 'KN',

    SHIP_POINT          TEXT,
    PRODUCT_GROUP       TEXT,
    DELIVERY_ZONE       TEXT,
    CARRIER             TEXT,

    VEHICLE_TYPE        TEXT,
    VEHICLE_KIND        TEXT,
    VEHICLE_CLASS       TEXT,

    DRIVER_NAME         TEXT,
    CONTACT_NO          TEXT,
    AXLE_TYPE           TEXT,

    LOAD_VOLUME         INTEGER DEFAULT 0,
    LOAD_WEIGHT         REAL    DEFAULT 0,
    PALLET_QTY          INTEGER DEFAULT 0,

    CARGO_LENGTH        REAL    DEFAULT 0,
    CARGO_WIDTH         REAL    DEFAULT 0,
    CARGO_HEIGHT        REAL    DEFAULT 0,

    FLOOR_TYPE          TEXT,

    USE_YN              TEXT    DEFAULT 'Y',
    OPERABLE_YN         TEXT    DEFAULT 'Y',

    DLV_TIME_FROM       TEXT,
    DLV_TIME_TO         TEXT,
    VEHICLE_YEAR        TEXT,

    DELIVERY_CUSTOMER_1 TEXT,
    DELIVERY_CUSTOMER_2 TEXT,

    DEL_YN              TEXT    DEFAULT 'N',
    CREDAT              TEXT    DEFAULT ' ',
    CRETIM              TEXT    DEFAULT ' ',
    CREUSR              TEXT    DEFAULT ' ',
    LMODAT              TEXT    DEFAULT ' ',
    LMOTIM              TEXT    DEFAULT ' ',
    LMOUSR              TEXT    DEFAULT ' ',

    PRIMARY KEY (VEHICLE_NO, OWNRKY)
);
"""

# ── 공통 참조 데이터 ──────────────────────────────────────
CT_PTNRKY = [
    '100001','100004','10001','100020','100021','100026','100032','100033',
    '100035','1000418','100043','100047','1000472','100049','100050',
    '100062','100066','100067','100076','100084','100104','100118',
    '100119','100125','1001254','1001256','1001257','1001260','1001261','1001262',
]
WARE_LIST   = ['W001','W002','W003','W004']
ROUTE_LIST  = ['R01','R02','R03','R04','R05']
ITEM_GRP    = ['FD','BV','HB','CS','FR']
AREA_LIST   = ['서울','경기북','경기남','인천','강원','충청','전라','경상','제주']
YN          = ['Y','N']
CARRIER_LIST= ['CJ대한통운','롯데택배','한진택배','로젠택배','우체국택배']
VH_TYPE     = ['일반','냉동','냉장']
VH_KIND     = ['윙바디','탑차','카고','리프트']
VH_CLASS    = ['1톤','2.5톤','5톤','11톤','18톤']
AXLE        = ['장축','단축','4축']
FLOOR       = ['나무','철재','알루미늄']
SHIP_PT     = ['SP01','SP02','SP03','SP04']
DRIVER_NM   = [
    '김민준','이서준','박도윤','최현우','정시우','강준서','조윤우',
    '윤지호','임채원','오예준','신민재','한지훈','권태양','유원준','남승현',
    '류성민','심재원','배재혁','노현석','문승우','안준혁','장태현',
    '임성빈','곽민준','전승우','황현재','변도윤','엄재원','편민호','탁진우',
]

def yn_val(): return random.choice(YN)
def today(): return datetime.date.today().strftime('%Y%m%d')
def now():   return datetime.datetime.now().strftime('%H%M%S')
def rand_time():
    h = random.randint(7, 11)
    return f"{h:02d}:00"

def make_bzptn_detail():
    rows = []
    for i, pk in enumerate(CT_PTNRKY):
        rows.append((
            # 0~3: PK
            pk, 'CT', 'KN', random.choice(WARE_LIST),
            # 4~5: 유통/제품군
            random.choice(ROUTE_LIST),
            random.choice(ITEM_GRP),
            # 6: UNLOAD_TIME
            random.randint(10, 60),
            # 7~8: 입차시간
            rand_time(),
            f"{random.randint(13,18):02d}:00",
            # 9~12: 권역/높이/지게차/수작업
            random.choice(AREA_LIST),
            round(random.uniform(2.5, 4.5), 2),
            yn_val(), yn_val(),
            # 13~15: AUTO_PLT, MAX_BOX_QTY, AUTO_ALLOC_YN
            round(random.uniform(1.0, 5.0), 2),
            random.randint(50, 500),
            yn_val(),
            # 16~18: SINGLE_ITEM_YN, NY_TYPE, SINGLE_HEIGHT
            yn_val(), random.choice(['N','Y']),
            round(random.uniform(1.0, 3.0), 2),
            # 19~22: DYNAMIC_YN, LTL_YN, PRIORITY_YN, MIN_QTSIWH
            yn_val(), yn_val(), yn_val(),
            round(random.uniform(0.5, 10.0), 2),
            # 23~24: GPS
            round(random.uniform(35.0, 38.0), 8),
            round(random.uniform(126.0, 129.5), 8),
            # 25~31: 공통
            'N',
            today(), now(), 'ADMIN',
            today(), now(), 'ADMIN',
        ))
    return rows

def make_vhcma():
    rows = []
    for i in range(30):
        vno = f"{random.randint(10,99)}{chr(65+i%26)}{random.randint(1000,9999)}"
        w   = random.choice(WARE_LIST)
        vc  = random.choice(VH_CLASS)
        wt_map = {'1톤':1000,'2.5톤':2500,'5톤':5000,'11톤':11000,'18톤':18000}
        vol_map= {'1톤':5000,'2.5톤':10000,'5톤':20000,'11톤':40000,'18톤':65000}
        plt_map= {'1톤':4,'2.5톤':8,'5톤':16,'11톤':24,'18톤':33}
        lw = wt_map[vc] * random.uniform(0.85, 0.95)
        lv = vol_map[vc]
        rows.append((
            vno, 'KN',
            random.choice(SHIP_PT),
            random.choice(ITEM_GRP),
            random.choice(AREA_LIST),
            random.choice(CARRIER_LIST),
            random.choice(VH_TYPE),
            random.choice(VH_KIND),
            vc,
            DRIVER_NM[i % len(DRIVER_NM)],
            f"010-{random.randint(1000,9999)}-{random.randint(1000,9999)}",
            random.choice(AXLE),
            lv,
            round(lw, 2),
            plt_map[vc],
            round(random.uniform(4.0, 8.5), 2),   # CARGO_LENGTH
            round(random.uniform(2.0, 2.5), 2),   # CARGO_WIDTH
            round(random.uniform(2.0, 2.8), 2),   # CARGO_HEIGHT
            random.choice(FLOOR),
            yn_val(),  # USE_YN
            yn_val(),  # OPERABLE_YN
            f"{random.randint(7,9):02d}:00",       # DLV_TIME_FROM
            f"{random.randint(17,20):02d}:00",     # DLV_TIME_TO
            str(random.randint(2015, 2024)),        # VEHICLE_YEAR
            random.choice(CT_PTNRKY),
            random.choice(CT_PTNRKY),
            'N',
            today(), now(), 'ADMIN',
            today(), now(), 'ADMIN',
        ))
    return rows

if __name__ == '__main__':
    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA journal_mode=WAL")

    # 테이블 생성
    conn.executescript(DDL)
    conn.commit()
    print("✅ Tables created: BZPTN_DETAIL, VHCMA")

    # BZPTN_DETAIL 삽입 (컬럼명 지정 방식 — DDL 버전 차이에 안전)
    bd_rows = make_bzptn_detail()
    bd_cols = [
        'PTNRKY','PTNRTY','OWNRKY','WAREKY',
        'ROUTE_CD','ITEM_GROUP','UNLOAD_TIME',
        'INB_TIME_FROM1','INB_TIME_TO1',
        'AREA_CD','MAX_HEIGHT','FORKLIFT_YN','HANDWORK_YN',
        'AUTO_PLT','MAX_BOX_QTY','AUTO_ALLOC_YN',
        'SINGLE_ITEM_YN','NY_TYPE','SINGLE_HEIGHT',
        'DYNAMIC_YN','LTL_YN','PRIORITY_YN','MIN_QTSIWH',
        'LATITUDE','LONGITUDE',
        'DEL_YN','CREDAT','CRETIM','CREUSR','LMODAT','LMOTIM','LMOUSR',
    ]
    ph = ','.join(['?'] * len(bd_cols))
    col_str = ','.join(bd_cols)
    conn.executemany(f"INSERT OR REPLACE INTO BZPTN_DETAIL ({col_str}) VALUES({ph})", bd_rows)
    conn.commit()
    print(f"✅ BZPTN_DETAIL : {len(bd_rows):>5,} rows")

    # VHCMA 삽입
    vh_rows = make_vhcma()
    ph2 = ','.join(['?'] * 33)
    conn.executemany(f"INSERT OR REPLACE INTO VHCMA VALUES({ph2})", vh_rows)
    conn.commit()
    print(f"✅ VHCMA        : {len(vh_rows):>5,} rows")

    # 검증
    print("\n📊 Final row counts:")
    for t in ['BZPTN_DETAIL', 'VHCMA']:
        c = conn.execute(f"SELECT COUNT(*) FROM {t}").fetchone()[0]
        print(f"  {t:<16}: {c:>5,}")

    conn.close()
    print("\n✅ Done!")
