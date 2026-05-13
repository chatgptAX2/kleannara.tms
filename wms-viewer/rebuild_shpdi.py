"""
SHPDI 테이블 재생성 + xlsx 데이터 INSERT
Oracle DDL → SQLite 변환
"""
import sqlite3
import pandas as pd
import numpy as np

DB_PATH   = '/home/user/webapp/wms-viewer/wms.db'
XLSX_PATH = '/home/user/uploaded_files/SHPDI_데이터.xlsx'

CREATE_SQL = """
CREATE TABLE IF NOT EXISTS SHPDI (
    SHPOKY       TEXT    NOT NULL DEFAULT ' ',
    SHPOIT       TEXT    NOT NULL DEFAULT ' ',
    STATIT       TEXT    NOT NULL DEFAULT ' ',
    SKUKEY       TEXT    NOT NULL DEFAULT ' ',
    QTYORG       REAL    NOT NULL DEFAULT 0,
    QTSHPO       REAL    NOT NULL DEFAULT 0,
    QTYREF       REAL    NOT NULL DEFAULT 0,
    QTAPPO       REAL    NOT NULL DEFAULT 0,
    QTALOC       REAL    NOT NULL DEFAULT 0,
    QTJCMP       REAL    NOT NULL DEFAULT 0,
    QTSHPD       REAL    NOT NULL DEFAULT 0,
    QTSHPC       REAL    NOT NULL DEFAULT 0,
    QTYUOM       REAL    NOT NULL DEFAULT 0,
    MEASKY       TEXT    NOT NULL DEFAULT ' ',
    UOMKEY       TEXT    NOT NULL DEFAULT ' ',
    QTPUOM       REAL    NOT NULL DEFAULT 0,
    DUOMKY       TEXT    NOT NULL DEFAULT ' ',
    QTDUOM       REAL    NOT NULL DEFAULT 0,
    SASTKY       TEXT    NOT NULL DEFAULT ' ',
    ALSTKY       TEXT    NOT NULL DEFAULT ' ',
    TKFLKY       TEXT    NOT NULL DEFAULT ' ',
    ESHPKY       TEXT    NOT NULL DEFAULT ' ',
    ESHPIT       TEXT    NOT NULL DEFAULT ' ',
    OPURKY       TEXT    NOT NULL DEFAULT ' ',
    REFDKY       TEXT    NOT NULL DEFAULT ' ',
    REFDIT       TEXT    NOT NULL DEFAULT ' ',
    REFCAT       TEXT    NOT NULL DEFAULT ' ',
    REFDAT       TEXT    NOT NULL DEFAULT ' ',
    EXSUBS       TEXT    NOT NULL DEFAULT ' ',
    DESC01       TEXT    NOT NULL DEFAULT ' ',
    DESC02       TEXT    NOT NULL DEFAULT ' ',
    ASKU01       TEXT    NOT NULL DEFAULT ' ',
    ASKU02       TEXT    NOT NULL DEFAULT ' ',
    ASKU03       TEXT    NOT NULL DEFAULT ' ',
    ASKU04       TEXT    NOT NULL DEFAULT ' ',
    ASKU05       TEXT    NOT NULL DEFAULT ' ',
    EANCOD       TEXT    NOT NULL DEFAULT ' ',
    GTINCD       TEXT    NOT NULL DEFAULT ' ',
    SKUG01       TEXT    NOT NULL DEFAULT ' ',
    SKUG02       TEXT    NOT NULL DEFAULT ' ',
    SKUG03       TEXT    NOT NULL DEFAULT ' ',
    SKUG04       TEXT    NOT NULL DEFAULT ' ',
    SKUG05       TEXT    NOT NULL DEFAULT ' ',
    GRSWGT       REAL    NOT NULL DEFAULT 0,
    NETWGT       REAL    NOT NULL DEFAULT 0,
    WGTUNT       TEXT    NOT NULL DEFAULT ' ',
    LENGTH       REAL    NOT NULL DEFAULT 0,
    WIDTHW       REAL    NOT NULL DEFAULT 0,
    HEIGHT       REAL    NOT NULL DEFAULT 0,
    CUBICM       REAL    NOT NULL DEFAULT 0,
    CAPACT       REAL    NOT NULL DEFAULT 0,
    PROCHA       TEXT    NOT NULL DEFAULT ' ',
    AREAKY       TEXT    NOT NULL DEFAULT ' ',
    LOTA01       TEXT    NOT NULL DEFAULT ' ',
    LOTA02       TEXT    NOT NULL DEFAULT ' ',
    LOTA03       TEXT    NOT NULL DEFAULT ' ',
    LOTA04       TEXT    NOT NULL DEFAULT ' ',
    LOTA05       TEXT    NOT NULL DEFAULT ' ',
    LOTA06       TEXT    NOT NULL DEFAULT ' ',
    LOTA07       TEXT    NOT NULL DEFAULT ' ',
    LOTA08       TEXT    NOT NULL DEFAULT ' ',
    LOTA09       TEXT    NOT NULL DEFAULT ' ',
    LOTA10       TEXT    NOT NULL DEFAULT ' ',
    LOTA11       TEXT    NOT NULL DEFAULT ' ',
    LOTA12       TEXT    NOT NULL DEFAULT ' ',
    LOTA13       TEXT    NOT NULL DEFAULT ' ',
    LOTA14       TEXT    NOT NULL DEFAULT ' ',
    LOTA15       TEXT    NOT NULL DEFAULT ' ',
    LOTA16       REAL    NOT NULL DEFAULT 0,
    LOTA17       TEXT    NOT NULL DEFAULT ' ',
    LOTA18       REAL    NOT NULL DEFAULT 0,
    LOTA19       REAL    NOT NULL DEFAULT 0,
    LOTA20       REAL    NOT NULL DEFAULT 0,
    AWMSNO       TEXT    NOT NULL DEFAULT ' ',
    SMANDT       TEXT    NOT NULL DEFAULT ' ',
    SEBELN       TEXT    NOT NULL DEFAULT ' ',
    SEBELP       TEXT    NOT NULL DEFAULT ' ',
    SZMBLNO      TEXT    NOT NULL DEFAULT ' ',
    SZMIPNO      TEXT    NOT NULL DEFAULT ' ',
    STRAID       TEXT    NOT NULL DEFAULT ' ',
    SVBELN       TEXT    NOT NULL DEFAULT ' ',
    SPOSNR       TEXT    NOT NULL DEFAULT ' ',
    STKNUM       TEXT    NOT NULL DEFAULT ' ',
    STPNUM       TEXT    NOT NULL DEFAULT ' ',
    SWERKS       TEXT    NOT NULL DEFAULT ' ',
    SLGORT       TEXT    NOT NULL DEFAULT ' ',
    SDATBG       TEXT    NOT NULL DEFAULT ' ',
    STDLNR       TEXT    NOT NULL DEFAULT ' ',
    SSORNU       TEXT    NOT NULL DEFAULT ' ',
    SSORIT       TEXT    NOT NULL DEFAULT ' ',
    SMBLNR       TEXT    NOT NULL DEFAULT ' ',
    SZEILE       TEXT    NOT NULL DEFAULT ' ',
    SMJAHR       TEXT    NOT NULL DEFAULT ' ',
    SXBLNR       TEXT    NOT NULL DEFAULT ' ',
    SAPSTS       TEXT    NOT NULL DEFAULT ' ',
    PTNRKY       TEXT    NOT NULL DEFAULT ' ',
    NAME01       TEXT    NOT NULL DEFAULT ' ',
    SLAND1       TEXT    NOT NULL DEFAULT ' ',
    SBKTXT       TEXT    NOT NULL DEFAULT ' ',
    CREDAT       TEXT    NOT NULL DEFAULT '00000000',
    CRETIM       TEXT    NOT NULL DEFAULT '000000',
    CREUSR       TEXT    NOT NULL DEFAULT ' ',
    LMODAT       TEXT    NOT NULL DEFAULT '00000000',
    LMOTIM       TEXT    NOT NULL DEFAULT '000000',
    LMOUSR       TEXT    NOT NULL DEFAULT ' ',
    INDBZL       TEXT    NOT NULL DEFAULT ' ',
    INDARC       TEXT    NOT NULL DEFAULT ' ',
    UPDCHK       INTEGER NOT NULL DEFAULT 0,
    PO_NO        TEXT    NOT NULL DEFAULT ' ',
    PO_REV       INTEGER,
    PO_LNO       INTEGER,
    TLOTA01      TEXT    NOT NULL DEFAULT ' ',
    TLOTA02      TEXT    NOT NULL DEFAULT ' ',
    STLNUM       TEXT             DEFAULT ' ',
    CHGFLG       TEXT             DEFAULT ' ',
    KEEPIT       TEXT             DEFAULT ' ',
    APPOINTPICKING TEXT          DEFAULT ' ',
    PRIMARY KEY (SHPOKY, SHPOIT)
)
"""

# ---------- REAL 타입 컬럼 목록 (0 치환용) ----------
REAL_COLS = {
    'QTYORG','QTSHPO','QTYREF','QTAPPO','QTALOC','QTJCMP','QTSHPD','QTSHPC',
    'QTYUOM','QTPUOM','QTDUOM','GRSWGT','NETWGT','LENGTH','WIDTHW','HEIGHT',
    'CUBICM','CAPACT','LOTA16','LOTA18','LOTA19','LOTA20',
}
INT_COLS = {'UPDCHK', 'PO_REV', 'PO_LNO'}

def clean_val(col, val):
    """NaN/None → 숫자형 0 or None, 문자형 ' '"""
    if val is None or (isinstance(val, float) and np.isnan(val)):
        if col in REAL_COLS:
            return 0.0
        if col in INT_COLS:
            return None          # NULL 허용 컬럼
        return ' '
    if col in REAL_COLS:
        try:
            return float(val)
        except Exception:
            return 0.0
    if col in INT_COLS:
        try:
            return int(val)
        except Exception:
            return None
    # 문자형: str 변환, 필요 시 zfill
    s = str(val).strip()
    return s if s else ' '

def main():
    print("=== SHPDI 테이블 재생성 시작 ===")

    # 1) xlsx 로드
    print("[1/4] xlsx 읽는 중...")
    df = pd.read_excel(XLSX_PATH, dtype=str)   # 모두 str로 읽어 타입 손실 방지
    print(f"      → {len(df)}행 × {len(df.columns)}컬럼")

    # SHPOKY / SHPOIT 는 TEXT(PK) — 그대로 str
    # 숫자형 컬럼은 clean_val 에서 처리

    # 2) DB 연결 + DROP + CREATE
    print("[2/4] DB 연결 및 테이블 재생성...")
    conn = sqlite3.connect(DB_PATH)
    cur  = conn.cursor()
    cur.execute("DROP TABLE IF EXISTS SHPDI")
    cur.execute(CREATE_SQL)
    conn.commit()
    print("      → DROP + CREATE 완료")

    # 3) INSERT
    print("[3/4] 데이터 INSERT 중...")
    cols     = list(df.columns)          # xlsx 컬럼 순서 사용
    ph       = ','.join(['?'] * len(cols))
    sql_ins  = f"INSERT OR REPLACE INTO SHPDI ({','.join(cols)}) VALUES ({ph})"

    rows = []
    for _, row in df.iterrows():
        cleaned = []
        for c in cols:
            raw = row[c]
            # pandas str read: NaN → float nan
            if isinstance(raw, float) and np.isnan(raw):
                raw = None
            cleaned.append(clean_val(c, raw))
        rows.append(tuple(cleaned))

    cur.executemany(sql_ins, rows)
    conn.commit()

    # 4) 검증
    print("[4/4] 검증...")
    cnt   = cur.execute("SELECT COUNT(*) FROM SHPDI").fetchone()[0]
    ncols = cur.execute("PRAGMA table_info(SHPDI)").fetchall()
    print(f"      → 테이블 컬럼 수 : {len(ncols)}")
    print(f"      → 삽입된 데이터  : {cnt}건")

    # 샘플 출력
    sample = cur.execute("SELECT SHPOKY, SHPOIT, STATIT, SKUKEY, QTSHPO FROM SHPDI LIMIT 5").fetchall()
    print("\n[샘플 5행]")
    for r in sample:
        print(" ", r)

    conn.close()
    print("\n=== 완료 ===")

if __name__ == '__main__':
    main()
