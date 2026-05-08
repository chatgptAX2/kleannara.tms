import sqlite3, openpyxl, re

DB_PATH   = "/home/user/webapp/wms-viewer/wms.db"
XLSX_PATH = "/home/user/uploaded_files/운송경로 별 비용_업로드.xlsx"

DDL = """
CREATE TABLE IF NOT EXISTS ROUTE_COST (
    SHPPT       TEXT NOT NULL DEFAULT '',
    ROUTE       TEXT NOT NULL DEFAULT '',
    PTNRKY      TEXT NOT NULL DEFAULT '',
    CARCLASS    TEXT NOT NULL DEFAULT '',
    COST        REAL,
    UNIT        TEXT DEFAULT 'KRW',
    DATE_START  TEXT DEFAULT '',
    DATE_END    TEXT DEFAULT '',
    CREDAT      TEXT DEFAULT '',
    CRETIM      TEXT DEFAULT '',
    CREUSR      TEXT DEFAULT 'ADMIN',
    LMODAT      TEXT DEFAULT '',
    LMOTIM      TEXT DEFAULT '',
    LMOUSR      TEXT DEFAULT 'ADMIN',
    PRIMARY KEY (ROUTE, PTNRKY, CARCLASS)
);
"""

def clean_date(v):
    """2025.01.01 또는 20250101 → 20250101"""
    if v is None: return ''
    s = str(v).strip()
    s = s.replace('.', '').replace('-', '').replace('/', '')
    return s[:8] if len(s) >= 8 else s

def sv(v):
    if v is None: return ''
    return str(v).strip()

def nv(v):
    if v is None: return None
    try: return float(v)
    except: return None

conn = sqlite3.connect(DB_PATH)
conn.execute("PRAGMA journal_mode=WAL")

# 기존 테이블 삭제 후 재생성
conn.execute("DROP TABLE IF EXISTS ROUTE_COST")
conn.execute(DDL)
conn.commit()
print("✅ ROUTE_COST 테이블 생성 완료")

# 엑셀 로드
wb = openpyxl.load_workbook(XLSX_PATH)
ws = wb.active

rows_inserted = 0
errors = 0
skipped = 0

from datetime import datetime
now = datetime.now()
credat = now.strftime('%Y%m%d')
cretim = now.strftime('%H%M%S')

batch = []
for i, row in enumerate(ws.iter_rows(min_row=3, values_only=True), start=3):
    # 빈 행 스킵
    if row[0] is None and row[1] is None:
        skipped += 1
        continue

    shppt      = sv(row[0])
    route      = sv(row[1])
    ptnrky     = sv(row[2])
    carclass   = sv(row[3])
    cost       = nv(row[4])
    unit       = sv(row[5]) if row[5] else 'KRW'
    date_start = clean_date(row[6])
    date_end   = clean_date(row[7])

    # PK 필수값 검증
    if not route or not ptnrky or not carclass:
        errors += 1
        print(f"  ⚠️  Row {i}: PK 누락 → {row}")
        continue

    batch.append((
        shppt, route, ptnrky, carclass,
        cost, unit, date_start, date_end,
        credat, cretim, 'ADMIN',
        credat, cretim, 'ADMIN'
    ))

conn.executemany("""
    INSERT OR REPLACE INTO ROUTE_COST
    (SHPPT, ROUTE, PTNRKY, CARCLASS, COST, UNIT, DATE_START, DATE_END,
     CREDAT, CRETIM, CREUSR, LMODAT, LMOTIM, LMOUSR)
    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
""", batch)
conn.commit()
rows_inserted = len(batch)

# 검증
cnt = conn.execute("SELECT COUNT(*) FROM ROUTE_COST").fetchone()[0]
routes = conn.execute("SELECT COUNT(DISTINCT ROUTE) FROM ROUTE_COST").fetchone()[0]
ptnrky = conn.execute("SELECT COUNT(DISTINCT PTNRKY) FROM ROUTE_COST").fetchone()[0]
carclasses = conn.execute("SELECT CARCLASS, COUNT(*) cnt FROM ROUTE_COST GROUP BY CARCLASS ORDER BY CARCLASS").fetchall()

print(f"✅ 데이터 로드 완료: {rows_inserted}건 삽입 (오류: {errors}, 스킵: {skipped})")
print(f"   총 행수: {cnt}")
print(f"   고유 경로: {routes}개 | 고유 납품처: {ptnrky}개")
print(f"   차량톤수 분포:")
for cc, c in carclasses:
    print(f"     {cc}: {c}건")

conn.close()
