"""
migrate_ds_vehicle_carclass.py
==============================
DS_VEHICLE 테이블 재생성 + TMS_CARCLASS 기준 16건 기준정보 완성.

실행:  python3 migrate_ds_vehicle_carclass.py

변경 내용:
  - DS_VEHICLE Primary Key를 CARTYPE → CARCLASS_CD 로 변경 (테이블 재생성)
  - TMS_CARCLASS 16개 코드 기준 16건 UPSERT
  - 전체 SORT_SEQ TMS_CARCLASS 순서로 재정렬

DDL (신규):
  CARCLASS_CD  TEXT PRIMARY KEY  -- 차량유형코드 (TMS_CARCLASS.CMCDVL)
  CARTYPE      TEXT              -- 차량유형명 (1톤, 1.4톤, ...)

차량제원 기준 (한국 표준 화물차 규격):
  ─────────────────────────────────────────────────────────────────────────────
  코드   차량유형  길이   너비      높이   적재(t) 파렛트 INCH12 LT/GE INCH3 LT/GE
  ─────────────────────────────────────────────────────────────────────────────
  Z010   1톤    2.3m  1.4m    1.5m   1.0    2     2/2    2/2
  Z014   1.4톤  2.8m  1.6m    1.8m   1.5    3     2/2    3/3
  Z020   2톤    3.6m  1.7m    1.9m   2.0    4     3/3    4/4
  Z025   2.5톤  4.0m  1.8m    2.0m   2.5    4     3/3    4/4
  Z030   3톤    4.3m  1.8m    2.0m   3.0    4     3/3    4/4
  Z035   3.5톤  4.8m  1.8~2.1 2.1m   3.2    6     3/3    5/5
  Z045   4.5톤  5.8m  2.2m    2.3m   4.5   10     4/4    8/8
  Z050   5톤    6.2m  2.4m    2.4m   5.0   10     4/4   10/10
  Z080   8톤    7.5m  2.4m    2.4m   8.0   12     6/5   12/12
  Z100   10톤   9.5m  2.4m    2.4m  10.0   16     8/6   14/14
  Z110   11톤  10.2m  2.4m    2.4m  11.0   16     9/7   14/14
  Z150   15톤  10.2m  2.4m    2.4m  14.0   16    10/8   14/14
  Z180   18톤  10.2m  2.4m    2.4m  17.0   18    12/10  15/15
  Z200   20톤  12.0m  2.4m    2.5m  20.0   18    13/11  17/17
  Z250   25톤  13.5m  2.5m    2.7m  25.0   18    14/12  18/18
  Z260   26톤  14.0m  2.5m    2.7m  26.0   18    14/12  18/18
  ─────────────────────────────────────────────────────────────────────────────
"""
import sqlite3
import os
from datetime import datetime

DB_PATH = os.path.join(os.path.dirname(__file__), 'wms.db')
today = datetime.now().strftime('%Y%m%d')

# ─────────────────────────────────────────────────────────────────────────────
# 신규 DDL (CARCLASS_CD PRIMARY KEY)
# ─────────────────────────────────────────────────────────────────────────────
CREATE_DDL = """
CREATE TABLE IF NOT EXISTS DS_VEHICLE (
  CARCLASS_CD      TEXT PRIMARY KEY,          -- 차량유형코드 (TMS_CARCLASS.CMCDVL) PK
  CARTYPE          TEXT,                      -- 차량유형명 (1톤, 1.4톤, ...)
  LENGTH_M         REAL,                      -- 길이(m)
  WIDTH_M          TEXT,                      -- 너비(m) - 범위 가능 "1.8~2.1"
  HEIGHT_M         REAL,                      -- 높이(m)
  LOAD_TON         REAL,                      -- 상차량(ton)
  SORT_SEQ         INTEGER DEFAULT 0,
  UPDDAT           TEXT,
  UPDUSR           TEXT,
  PALLET_HEIGHT_M  REAL    DEFAULT 0,
  INCH12_LT300     INTEGER DEFAULT NULL,
  INCH12_GE300     INTEGER DEFAULT NULL,
  INCH3_LT300      INTEGER DEFAULT NULL,
  INCH3_GE300      INTEGER DEFAULT NULL,
  DEFAULT_VEH_CNT  INTEGER,
  PALLET_CNT       INTEGER,
  LONG_AXIS_YN     TEXT    DEFAULT 'N'
)
"""

# ─────────────────────────────────────────────────────────────────────────────
# 16건 기준정보 (CARCLASS_CD PK 기준)
# (CARCLASS_CD, CARTYPE, LENGTH_M, WIDTH_M, HEIGHT_M, LOAD_TON, SORT_SEQ, UPDDAT, UPDUSR,
#  PALLET_HEIGHT_M, INCH12_LT300, INCH12_GE300, INCH3_LT300, INCH3_GE300,
#  DEFAULT_VEH_CNT, PALLET_CNT, LONG_AXIS_YN)
# ─────────────────────────────────────────────────────────────────────────────
ALL_DATA = [
    ('Z010', '1톤',   2.3, '1.4',  1.5,  1.0,  0, today, None, 0.15,  2,  2,  2,  2,  5,  2, 'N'),
    ('Z014', '1.4톤', 2.8, '1.6',  1.8,  1.5,  1, today, None, 0.15,  2,  2,  3,  3,  3,  3, 'N'),
    ('Z020', '2톤',   3.6, '1.7',  1.9,  2.0,  2, today, None, 0.15,  3,  3,  4,  4,  4,  4, 'N'),
    ('Z025', '2.5톤', 4.0, '1.8',  2.0,  2.5,  3, today, None, 0.15,  3,  3,  4,  4,  4,  4, 'N'),
    ('Z030', '3톤',   4.3, '1.8',  2.0,  3.0,  4, today, None, 0.15,  3,  3,  4,  4,  4,  4, 'N'),
    ('Z035', '3.5톤', 4.8, '1.8~2.1', 2.1, 3.2, 5, today, None, 0.15, 3, 3, 5, 5, 3,  6, 'N'),
    ('Z045', '4.5톤', 5.8, '2.2',  2.3,  4.5,  6, today, None, 0.15,  4,  4,  8,  8,  3, 10, 'N'),
    ('Z050', '5톤',   6.2, '2.4',  2.4,  5.0,  7, today, None, 0.15,  4,  4, 10, 10,  3, 10, 'N'),
    ('Z080', '8톤',   7.5, '2.4',  2.4,  8.0,  8, today, None, 0.15,  6,  5, 12, 12,  3, 12, 'N'),
    ('Z100', '10톤',  9.5, '2.4',  2.4, 10.0,  9, today, None, 0.15,  8,  6, 14, 14,  3, 16, 'N'),
    ('Z110', '11톤', 10.2, '2.4',  2.4, 11.0, 10, today, None, 0.15,  9,  7, 14, 14,  3, 16, 'N'),
    ('Z150', '15톤', 10.2, '2.4',  2.4, 14.0, 11, today, None, 0.15, 10,  8, 14, 14,  3, 16, 'N'),
    ('Z180', '18톤', 10.2, '2.4',  2.4, 17.0, 12, today, None, 0.15, 12, 10, 15, 15,  3, 18, 'N'),
    ('Z200', '20톤', 12.0, '2.4',  2.5, 20.0, 13, today, None, 0.15, 13, 11, 17, 17,  2, 18, 'N'),
    ('Z250', '25톤', 13.5, '2.5',  2.7, 25.0, 14, today, None, 0.15, 14, 12, 18, 18,  2, 18, 'N'),
    ('Z260', '26톤', 14.0, '2.5',  2.7, 26.0, 15, today, None, 0.15, 14, 12, 18, 18,  2, 18, 'N'),
]

UPSERT_SQL = """
INSERT INTO DS_VEHICLE
  (CARCLASS_CD, CARTYPE, LENGTH_M, WIDTH_M, HEIGHT_M, LOAD_TON, SORT_SEQ, UPDDAT, UPDUSR,
   PALLET_HEIGHT_M, INCH12_LT300, INCH12_GE300, INCH3_LT300, INCH3_GE300,
   DEFAULT_VEH_CNT, PALLET_CNT, LONG_AXIS_YN)
VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
ON CONFLICT(CARCLASS_CD) DO UPDATE SET
  CARTYPE=excluded.CARTYPE, LENGTH_M=excluded.LENGTH_M, WIDTH_M=excluded.WIDTH_M,
  HEIGHT_M=excluded.HEIGHT_M, LOAD_TON=excluded.LOAD_TON, SORT_SEQ=excluded.SORT_SEQ,
  UPDDAT=excluded.UPDDAT, PALLET_HEIGHT_M=excluded.PALLET_HEIGHT_M,
  INCH12_LT300=excluded.INCH12_LT300, INCH12_GE300=excluded.INCH12_GE300,
  INCH3_LT300=excluded.INCH3_LT300,  INCH3_GE300=excluded.INCH3_GE300,
  DEFAULT_VEH_CNT=excluded.DEFAULT_VEH_CNT,
  PALLET_CNT=excluded.PALLET_CNT, LONG_AXIS_YN=excluded.LONG_AXIS_YN
"""


def rebuild_table(conn):
    """DS_VEHICLE 테이블이 구(CARTYPE PK) 구조면 신규(CARCLASS_CD PK) 구조로 재생성"""
    cur = conn.cursor()
    cur.execute("SELECT sql FROM sqlite_master WHERE type='table' AND name='DS_VEHICLE'")
    row = cur.fetchone()
    if row is None:
        print("[0] DS_VEHICLE 테이블 없음 → 신규 생성")
        cur.executescript(CREATE_DDL)
        return

    ddl = row[0]
    if 'CARCLASS_CD  TEXT PRIMARY KEY' in ddl or 'CARCLASS_CD TEXT PRIMARY KEY' in ddl:
        print("[0] DS_VEHICLE 이미 CARCLASS_CD PK 구조 → 재생성 불필요")
        return

    print("[0] DS_VEHICLE 구(CARTYPE PK) 구조 감지 → CARCLASS_CD PK로 재생성")
    cur.execute("ALTER TABLE DS_VEHICLE RENAME TO DS_VEHICLE_OLD")
    cur.executescript(CREATE_DDL)
    cur.execute("""
        INSERT INTO DS_VEHICLE
          (CARCLASS_CD, CARTYPE, LENGTH_M, WIDTH_M, HEIGHT_M, LOAD_TON, SORT_SEQ,
           UPDDAT, UPDUSR, PALLET_HEIGHT_M,
           INCH12_LT300, INCH12_GE300, INCH3_LT300, INCH3_GE300,
           DEFAULT_VEH_CNT, PALLET_CNT, LONG_AXIS_YN)
        SELECT
          CARCLASS_CD, CARTYPE, LENGTH_M, WIDTH_M, HEIGHT_M, LOAD_TON, SORT_SEQ,
          UPDDAT, UPDUSR, PALLET_HEIGHT_M,
          INCH12_LT300, INCH12_GE300, INCH3_LT300, INCH3_GE300,
          DEFAULT_VEH_CNT, PALLET_CNT, LONG_AXIS_YN
        FROM DS_VEHICLE_OLD
        WHERE CARCLASS_CD IS NOT NULL
    """)
    cur.execute("DROP TABLE DS_VEHICLE_OLD")
    print("    ✅ 테이블 재생성 완료")


def run():
    conn = sqlite3.connect(DB_PATH)

    # 0) 구조 확인 및 필요시 재생성
    rebuild_table(conn)

    cur = conn.cursor()

    # 1) 16건 UPSERT
    print("[1] 기준정보 16건 UPSERT ...")
    for m in ALL_DATA:
        cur.execute(UPSERT_SQL, m)
        print(f"    {'✅' if cur.rowcount else '⏭ '} {m[0]:<6} {m[1]}")

    conn.commit()

    # 2) 결과 검증
    cur.execute("""
        SELECT SORT_SEQ, CARCLASS_CD, CARTYPE, LENGTH_M, WIDTH_M, HEIGHT_M,
               LOAD_TON, PALLET_CNT, LONG_AXIS_YN
        FROM DS_VEHICLE ORDER BY SORT_SEQ
    """)
    rows = cur.fetchall()
    print(f"\n[결과] DS_VEHICLE 총 {len(rows)}건 (PK=CARCLASS_CD)")
    print(f"{'SEQ':>3} {'코드':<6} {'차량유형':<7} {'L':>5} {'W':>8} {'H':>5} {'TON':>6} {'PAL':>4} 장축")
    for r in rows:
        print(f"{r[0]:>3} {r[1]:<6} {r[2]:<7} {str(r[3]):>5} {str(r[4]):>8} {str(r[5]):>5} {str(r[6]):>6} {str(r[7] or '-'):>4}  {r[8]}")
    print(f"\n✅ 완료")
    conn.close()


if __name__ == '__main__':
    run()
