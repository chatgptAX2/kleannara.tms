import sqlite3, openpyxl, os, sys

DB_PATH  = "/home/user/webapp/wms-viewer/wms.db"
DATA_DIR = "/home/user/uploaded_files"

def get_conn():
    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA journal_mode=WAL")
    return conn

DDL = """
CREATE TABLE IF NOT EXISTS CMCDM (
  CMCDKY TEXT NOT NULL, SHORTX TEXT DEFAULT ' ', DBFILD TEXT DEFAULT ' ',
  USARL1 TEXT DEFAULT ' ', USARL2 TEXT DEFAULT ' ', USARL3 TEXT DEFAULT ' ',
  USARL4 TEXT DEFAULT ' ', USARL5 TEXT DEFAULT ' ', SYONLY TEXT DEFAULT ' ',
  CREDAT TEXT DEFAULT ' ', CRETIM TEXT DEFAULT ' ', CREUSR TEXT DEFAULT ' ',
  LMODAT TEXT DEFAULT ' ', LMOTIM TEXT DEFAULT ' ', LMOUSR TEXT DEFAULT ' ',
  INDBZL TEXT DEFAULT ' ', INDARC TEXT DEFAULT ' ', UPDCHK INTEGER DEFAULT 0,
  PRIMARY KEY (CMCDKY)
);
CREATE TABLE IF NOT EXISTS CMCDV (
  CMCDKY TEXT NOT NULL, CMCDVL TEXT NOT NULL,
  CDESC1 TEXT DEFAULT ' ', CDESC2 TEXT DEFAULT ' ',
  USARG1 TEXT DEFAULT ' ', USARG2 TEXT DEFAULT ' ', USARG3 TEXT DEFAULT ' ',
  USARG4 TEXT DEFAULT ' ', USARG5 TEXT DEFAULT ' ',
  CREDAT TEXT DEFAULT ' ', CRETIM TEXT DEFAULT ' ', CREUSR TEXT DEFAULT ' ',
  LMODAT TEXT DEFAULT ' ', LMOTIM TEXT DEFAULT ' ', LMOUSR TEXT DEFAULT ' ',
  INDBZL TEXT DEFAULT ' ', INDARC TEXT DEFAULT ' ', UPDCHK INTEGER DEFAULT 0,
  PRIMARY KEY (CMCDKY, CMCDVL)
);
CREATE TABLE IF NOT EXISTS SKUMA (
  OWNRKY TEXT NOT NULL, SKUKEY TEXT NOT NULL,
  DELMAK TEXT DEFAULT ' ', DESC01 TEXT DEFAULT ' ', DESC02 TEXT DEFAULT ' ',
  VENDKY TEXT DEFAULT ' ',
  ASKU01 TEXT DEFAULT ' ', ASKU02 TEXT DEFAULT ' ', ASKU03 TEXT DEFAULT ' ',
  ASKU04 TEXT DEFAULT ' ', ASKU05 TEXT DEFAULT ' ',
  ASKL01 TEXT DEFAULT ' ', ASKL02 TEXT DEFAULT ' ', ASKL03 TEXT DEFAULT ' ',
  ASKL04 TEXT DEFAULT ' ', ASKL05 TEXT DEFAULT ' ',
  EANCOD TEXT DEFAULT ' ', GTINCD TEXT DEFAULT ' ',
  SKUG01 TEXT DEFAULT ' ', SKUG02 TEXT DEFAULT ' ', SKUG03 TEXT DEFAULT ' ',
  SKUG04 TEXT DEFAULT ' ', SKUG05 TEXT DEFAULT ' ',
  SKUL01 TEXT DEFAULT ' ', SKUL02 TEXT DEFAULT ' ', SKUL03 TEXT DEFAULT ' ',
  SKUL04 TEXT DEFAULT ' ', SKUL05 TEXT DEFAULT ' ',
  GRSWGT REAL DEFAULT 0, NETWGT REAL DEFAULT 0, WGTUNT TEXT DEFAULT ' ',
  LENGTH REAL DEFAULT 0, WIDTHW REAL DEFAULT 0, HEIGHT REAL DEFAULT 0,
  CUBICM REAL DEFAULT 0, CAPACT REAL DEFAULT 0,
  DUOMKY TEXT DEFAULT ' ', QTDUOM REAL DEFAULT 0, ABCANV TEXT DEFAULT ' ',
  LOTL01 TEXT DEFAULT ' ', LOTL02 TEXT DEFAULT ' ', LOTL03 TEXT DEFAULT ' ',
  LOTL04 TEXT DEFAULT ' ', LOTL05 TEXT DEFAULT ' ', LOTL06 TEXT DEFAULT ' ',
  LOTL07 TEXT DEFAULT ' ', LOTL08 TEXT DEFAULT ' ', LOTL09 TEXT DEFAULT ' ',
  LOTL10 TEXT DEFAULT ' ', LOTL11 TEXT DEFAULT ' ', LOTL12 TEXT DEFAULT ' ',
  LOTL13 TEXT DEFAULT ' ', LOTL14 TEXT DEFAULT ' ', LOTL15 TEXT DEFAULT ' ',
  LOTL16 TEXT DEFAULT ' ', LOTL17 TEXT DEFAULT ' ', LOTL18 TEXT DEFAULT ' ',
  LOTL19 TEXT DEFAULT ' ', LOTL20 TEXT DEFAULT ' ',
  OUTDMT INTEGER DEFAULT 0, RIMDMT INTEGER DEFAULT 0,
  INNDPT INTEGER DEFAULT 0, SECTWD INTEGER DEFAULT 0,
  WEIGHT REAL DEFAULT 0, DLGORT TEXT DEFAULT ' ', BATMNG TEXT DEFAULT ' ',
  LGPRO TEXT DEFAULT ' ', CSTDAT TEXT DEFAULT ' ', CPSKUG TEXT DEFAULT ' ',
  DESC03 TEXT DEFAULT ' ', DESC04 TEXT DEFAULT ' ',
  QTYMON INTEGER DEFAULT 0, QTYSTD INTEGER DEFAULT 0, QTYCNT INTEGER DEFAULT 0,
  BUFMNG TEXT DEFAULT ' ',
  CREDAT TEXT DEFAULT ' ', CRETIM TEXT DEFAULT ' ', CREUSR TEXT DEFAULT ' ',
  LMODAT TEXT DEFAULT ' ', LMOTIM TEXT DEFAULT ' ', LMOUSR TEXT DEFAULT ' ',
  INDBZL TEXT DEFAULT ' ', INDARC TEXT DEFAULT ' ', UPDCHK INTEGER DEFAULT 0,
  MTYPE TEXT DEFAULT ' ',
  PRIMARY KEY (OWNRKY, SKUKEY)
);
CREATE TABLE IF NOT EXISTS BZPTN (
  PTNRKY TEXT NOT NULL, PTNRTY TEXT NOT NULL, OWNRKY TEXT NOT NULL DEFAULT ' ',
  DELMAK TEXT DEFAULT ' ', NAME01 TEXT DEFAULT ' ', NAME02 TEXT DEFAULT ' ',
  NAME03 TEXT DEFAULT ' ', ADDR01 TEXT DEFAULT ' ', ADDR02 TEXT DEFAULT ' ',
  ADDR03 TEXT DEFAULT ' ', ADDR04 TEXT DEFAULT ' ', ADDR05 TEXT DEFAULT ' ',
  CITY01 TEXT DEFAULT ' ', REGN01 TEXT DEFAULT ' ', POSTCD TEXT DEFAULT ' ',
  NATNKY TEXT DEFAULT ' ', TELN01 TEXT DEFAULT ' ', TELN02 TEXT DEFAULT ' ',
  TELN03 TEXT DEFAULT ' ', FAXTL1 TEXT DEFAULT ' ', FAXTL2 TEXT DEFAULT ' ',
  TAXCD1 TEXT DEFAULT ' ', TAXCD2 TEXT DEFAULT ' ', VATREG TEXT DEFAULT ' ',
  POBOX1 TEXT DEFAULT ' ', POBPC1 TEXT DEFAULT ' ',
  EMAIL1 TEXT DEFAULT ' ', EMAIL2 TEXT DEFAULT ' ',
  CTTN01 TEXT DEFAULT ' ', CTTT01 TEXT DEFAULT ' ', CTTT02 TEXT DEFAULT ' ',
  CTTM01 TEXT DEFAULT ' ', SALN01 TEXT DEFAULT ' ', SALT01 TEXT DEFAULT ' ',
  SALT02 TEXT DEFAULT ' ', SALM01 TEXT DEFAULT ' ',
  EXPTNK TEXT DEFAULT ' ', CUSTMR TEXT DEFAULT ' ',
  PTNG01 TEXT DEFAULT ' ', PTNG02 TEXT DEFAULT ' ', PTNG03 TEXT DEFAULT ' ',
  PTNG04 TEXT DEFAULT ' ', PTNG05 TEXT DEFAULT ' ',
  PTNL01 TEXT DEFAULT ' ', PTNL02 TEXT DEFAULT ' ', PTNL03 TEXT DEFAULT ' ',
  PTNL04 TEXT DEFAULT ' ', PTNL05 TEXT DEFAULT ' ',
  WTOPPM REAL DEFAULT 0, WTOPMU REAL DEFAULT 0, WTOPDV REAL DEFAULT 0,
  PROCHA TEXT DEFAULT ' ',
  CREDAT TEXT DEFAULT ' ', CRETIM TEXT DEFAULT ' ', CREUSR TEXT DEFAULT ' ',
  LMODAT TEXT DEFAULT ' ', LMOTIM TEXT DEFAULT ' ', LMOUSR TEXT DEFAULT ' ',
  INDBZL TEXT DEFAULT ' ', INDARC TEXT DEFAULT ' ', UPDCHK INTEGER DEFAULT 0,
  DEPTID TEXT DEFAULT ' ', DNAME TEXT DEFAULT ' ', USRID1 TEXT DEFAULT ' ',
  LOTCD TEXT DEFAULT ' ', LOTCD2 TEXT DEFAULT ' ',
  PRIMARY KEY (PTNRKY, PTNRTY, OWNRKY)
);
CREATE TABLE IF NOT EXISTS MEASI (
  WAREKY TEXT NOT NULL, MEASKY TEXT NOT NULL, ITEMNO TEXT NOT NULL DEFAULT '000000',
  UOMKEY TEXT DEFAULT ' ', QTPUOM REAL DEFAULT 0,
  INDDFU TEXT DEFAULT ' ', DISREC TEXT DEFAULT ' ',
  DISSHP TEXT DEFAULT ' ', DISTAS TEXT DEFAULT ' ',
  LENGTH REAL DEFAULT 0, WIDTHW REAL DEFAULT 0, HEIGHT REAL DEFAULT 0,
  CUBICM REAL DEFAULT 0, QTAUOM REAL DEFAULT 0,
  CREDAT TEXT DEFAULT ' ', CRETIM TEXT DEFAULT ' ', CREUSR TEXT DEFAULT ' ',
  LMODAT TEXT DEFAULT ' ', LMOTIM TEXT DEFAULT ' ', LMOUSR TEXT DEFAULT ' ',
  INDBZL TEXT DEFAULT ' ', INDARC TEXT DEFAULT ' ', UPDCHK INTEGER DEFAULT 0,
  PRIMARY KEY (WAREKY, MEASKY, ITEMNO)
);
CREATE TABLE IF NOT EXISTS SHPDH (
  SHPOKY TEXT NOT NULL, WAREKY TEXT DEFAULT ' ', SHPMTY TEXT DEFAULT ' ',
  ALSTKY TEXT DEFAULT ' ', STATDO TEXT DEFAULT ' ', DOCDAT TEXT DEFAULT ' ',
  DOCCAT TEXT DEFAULT ' ', PRORTY TEXT DEFAULT ' ', DOCUTY TEXT DEFAULT ' ',
  OWNRKY TEXT DEFAULT ' ', DRELIN TEXT DEFAULT ' ', RQSHPD TEXT DEFAULT ' ',
  RQARRD TEXT DEFAULT ' ', RQARRT TEXT DEFAULT ' ', LSHPCD TEXT DEFAULT ' ',
  DPTNKY TEXT DEFAULT ' ', PTRCVR TEXT DEFAULT ' ',
  PGRC01 TEXT DEFAULT ' ', PGRC02 TEXT DEFAULT ' ', PGRC03 TEXT DEFAULT ' ',
  PGRC04 TEXT DEFAULT ' ', PGRC05 TEXT DEFAULT ' ',
  VEHINO TEXT DEFAULT ' ', DRIVER TEXT DEFAULT ' ',
  ESHPKY TEXT DEFAULT ' ', OPURKY TEXT DEFAULT ' ',
  LOCADT TEXT DEFAULT ' ', LOCADK TEXT DEFAULT ' ',
  INDDCL TEXT DEFAULT ' ', RSNCOD TEXT DEFAULT ' ', RSNRET TEXT DEFAULT ' ',
  QTSHPO REAL DEFAULT 0, QTYREF REAL DEFAULT 0, QTAPPO REAL DEFAULT 0,
  QTALOC REAL DEFAULT 0, QTJCMP REAL DEFAULT 0,
  QTSHPD REAL DEFAULT 0, QTSHPC REAL DEFAULT 0,
  USRID1 TEXT DEFAULT ' ', UNAME1 TEXT DEFAULT ' ',
  DEPTID1 TEXT DEFAULT ' ', DNAME1 TEXT DEFAULT ' ',
  USRID2 TEXT DEFAULT ' ', UNAME2 TEXT DEFAULT ' ',
  DEPTID2 TEXT DEFAULT ' ', DNAME2 TEXT DEFAULT ' ',
  USRID3 TEXT DEFAULT ' ', UNAME3 TEXT DEFAULT ' ',
  DEPTID3 TEXT DEFAULT ' ', DNAME3 TEXT DEFAULT ' ',
  USRID4 TEXT DEFAULT ' ', UNAME4 TEXT DEFAULT ' ',
  DEPTID4 TEXT DEFAULT ' ', DNAME4 TEXT DEFAULT ' ',
  DOCTXT TEXT DEFAULT ' ',
  CREDAT TEXT DEFAULT ' ', CRETIM TEXT DEFAULT ' ', CREUSR TEXT DEFAULT ' ',
  LMODAT TEXT DEFAULT ' ', LMOTIM TEXT DEFAULT ' ', LMOUSR TEXT DEFAULT ' ',
  INDBZL TEXT DEFAULT ' ', INDARC TEXT DEFAULT ' ', UPDCHK INTEGER DEFAULT 0,
  KEEPTS TEXT DEFAULT ' ', TDLNR TEXT, CARNO TEXT, CARTON TEXT,
  LOADTON REAL, CARART TEXT, LOADVOLUME REAL, DRIVERCEL TEXT,
  DTDIS TEXT, UZDIS TEXT, SIGNI TEXT, EXTI2 TEXT, SDABW TEXT,
  SAP1 TEXT, SAP2 TEXT, TDOBJECT TEXT,
  VTXTK TEXT DEFAULT ' ', IFID TEXT DEFAULT ' ', RSNTXT TEXT DEFAULT ' ',
  KVGR1 TEXT, KVGR2 TEXT, KVGR3 TEXT,
  LFART TEXT DEFAULT ' ', ZLGORT TEXT DEFAULT ' ',
  ZKUNNR3 TEXT, PRTCHK TEXT, VTWEG TEXT, TDLNR_NM TEXT DEFAULT ' ',
  PRIMARY KEY (SHPOKY)
);
CREATE TABLE IF NOT EXISTS SHPDI (
  SHPOKY TEXT NOT NULL, SHPOIT TEXT NOT NULL,
  STATIT TEXT DEFAULT ' ', SKUKEY TEXT DEFAULT ' ',
  QTYORG REAL DEFAULT 0, QTSHPO REAL DEFAULT 0, QTYREF REAL DEFAULT 0,
  QTAPPO REAL DEFAULT 0, QTALOC REAL DEFAULT 0, QTJCMP REAL DEFAULT 0,
  QTSHPD REAL DEFAULT 0, QTSHPC REAL DEFAULT 0, QTYUOM REAL DEFAULT 0,
  MEASKY TEXT DEFAULT ' ', UOMKEY TEXT DEFAULT ' ', QTPUOM REAL DEFAULT 0,
  DUOMKY TEXT DEFAULT ' ', QTDUOM REAL DEFAULT 0,
  SASTKY TEXT DEFAULT ' ', ALSTKY TEXT DEFAULT ' ', TKFLKY TEXT DEFAULT ' ',
  ESHPKY TEXT DEFAULT ' ', ESHPIT TEXT DEFAULT ' ', OPURKY TEXT DEFAULT ' ',
  REFDKY TEXT DEFAULT ' ', REFDIT TEXT DEFAULT ' ',
  REFCAT TEXT DEFAULT ' ', REFDAT TEXT DEFAULT ' ', EXSUBS TEXT DEFAULT ' ',
  DESC01 TEXT DEFAULT ' ', DESC02 TEXT DEFAULT ' ',
  ASKU01 TEXT DEFAULT ' ', ASKU02 TEXT DEFAULT ' ', ASKU03 TEXT DEFAULT ' ',
  ASKU04 TEXT DEFAULT ' ', ASKU05 TEXT DEFAULT ' ',
  EANCOD TEXT DEFAULT ' ', GTINCD TEXT DEFAULT ' ',
  SKUG01 TEXT DEFAULT ' ', SKUG02 TEXT DEFAULT ' ', SKUG03 TEXT DEFAULT ' ',
  SKUG04 TEXT DEFAULT ' ', SKUG05 TEXT DEFAULT ' ',
  GRSWGT REAL DEFAULT 0, NETWGT REAL DEFAULT 0, WGTUNT TEXT DEFAULT ' ',
  LENGTH REAL DEFAULT 0, WIDTHW REAL DEFAULT 0, HEIGHT REAL DEFAULT 0,
  CUBICM REAL DEFAULT 0, CAPACT REAL DEFAULT 0,
  PROCHA TEXT DEFAULT ' ', AREAKY TEXT DEFAULT ' ',
  PTNRKY TEXT DEFAULT ' ', NAME01 TEXT DEFAULT ' ', SLAND1 TEXT DEFAULT ' ',
  SBKTXT TEXT DEFAULT ' ',
  CREDAT TEXT DEFAULT ' ', CRETIM TEXT DEFAULT ' ', CREUSR TEXT DEFAULT ' ',
  LMODAT TEXT DEFAULT ' ', LMOTIM TEXT DEFAULT ' ', LMOUSR TEXT DEFAULT ' ',
  INDBZL TEXT DEFAULT ' ', INDARC TEXT DEFAULT ' ', UPDCHK INTEGER DEFAULT 0,
  PO_NO TEXT DEFAULT ' ', CHGFLG TEXT DEFAULT ' ', KEEPIT TEXT DEFAULT ' ',
  PRIMARY KEY (SHPOKY, SHPOIT)
);
CREATE TABLE IF NOT EXISTS IFWMS113 (
  MANDT TEXT NOT NULL, SEQNO INTEGER NOT NULL DEFAULT 0,
  VBELN TEXT NOT NULL DEFAULT ' ', POSNR TEXT NOT NULL DEFAULT ' ',
  BWART TEXT DEFAULT ' ', PSTYV TEXT DEFAULT ' ',
  ZLIKP_ERDAT TEXT DEFAULT ' ', ZLIKP_ERZET TEXT DEFAULT ' ',
  ZLIKP_AEDAT TEXT DEFAULT ' ', VSTEL TEXT DEFAULT ' ',
  LFART TEXT DEFAULT ' ', WADAT TEXT DEFAULT ' ',
  KUNNR TEXT DEFAULT ' ', KUNAG TEXT DEFAULT ' ',
  WERKS TEXT DEFAULT ' ', LGORT TEXT DEFAULT ' ', MATNR TEXT DEFAULT ' ',
  LFIMG REAL DEFAULT 0, QTSHP REAL DEFAULT 0, MEINS TEXT DEFAULT ' ',
  NETPR REAL DEFAULT 0, NETWR REAL DEFAULT 0,
  MWSBP REAL DEFAULT 0, MWSDC REAL DEFAULT 0, WAERK TEXT DEFAULT ' ',
  BWTAR TEXT DEFAULT ' ', VGBEL TEXT DEFAULT ' ', VGPOS TEXT DEFAULT ' ',
  VGDAT TEXT DEFAULT ' ', STKNUM TEXT DEFAULT ' ', SDATBG TEXT DEFAULT ' ',
  STATUS TEXT DEFAULT ' ', IFFLG TEXT DEFAULT ' ',
  RETRY INTEGER DEFAULT 0, ERCOD INTEGER DEFAULT 0,
  ERTXT TEXT DEFAULT ' ', CUSRID TEXT DEFAULT ' ', CUNAME TEXT DEFAULT ' ',
  CPSTLZ TEXT DEFAULT ' ', LAND1 TEXT DEFAULT ' ', TELF1 TEXT DEFAULT ' ',
  TELE2 TEXT DEFAULT ' ', SMTP_ADDR TEXT DEFAULT ' ',
  KUKLA TEXT DEFAULT ' ', VTEXT TEXT DEFAULT ' ',
  ADDR TEXT DEFAULT ' ', CNAME TEXT DEFAULT ' ', CPHON TEXT DEFAULT ' ',
  BNAME TEXT DEFAULT ' ', BPHON TEXT DEFAULT ' ',
  WAREKY TEXT DEFAULT ' ', SKUKEY TEXT DEFAULT ' ',
  DESC01 TEXT DEFAULT ' ', DESC02 TEXT DEFAULT ' ',
  USRID1 TEXT DEFAULT ' ', DEPTID1 TEXT DEFAULT ' ',
  USRID2 TEXT DEFAULT ' ', DEPTID2 TEXT DEFAULT ' ',
  USRID3 TEXT DEFAULT ' ', DEPTID3 TEXT DEFAULT ' ',
  USRID4 TEXT DEFAULT ' ', DEPTID4 TEXT DEFAULT ' ',
  C00101 TEXT DEFAULT ' ', C00102 TEXT DEFAULT ' ',
  C00103 TEXT DEFAULT ' ', C00104 TEXT DEFAULT ' ', C00105 TEXT DEFAULT ' ',
  N00101 REAL DEFAULT 0, N00102 REAL DEFAULT 0,
  C00106 TEXT DEFAULT ' ', C00107 TEXT DEFAULT ' ', C00108 TEXT DEFAULT ' ',
  C00109 TEXT DEFAULT ' ', C00110 TEXT DEFAULT ' ',
  N00103 REAL DEFAULT 0, N00104 REAL DEFAULT 0, N00105 REAL DEFAULT 0,
  ORDCAT TEXT DEFAULT ' ', PTNRTY TEXT DEFAULT ' ',
  SBKTXT TEXT DEFAULT ' ',
  LOTA01 TEXT DEFAULT ' ', LOTA02 TEXT DEFAULT ' ', LOTA03 TEXT DEFAULT ' ',
  LOTA04 TEXT DEFAULT ' ', LOTA05 TEXT DEFAULT ' ', LOTA06 TEXT DEFAULT ' ',
  LOTA07 TEXT DEFAULT ' ', LOTA08 TEXT DEFAULT ' ', LOTA09 TEXT DEFAULT ' ',
  LOTA10 TEXT DEFAULT ' ', LOTA11 TEXT DEFAULT ' ', LOTA12 TEXT DEFAULT ' ',
  LOTA13 TEXT DEFAULT ' ', LOTA14 TEXT DEFAULT ' ', LOTA15 TEXT DEFAULT ' ',
  LOTA16 REAL DEFAULT 0, LOTA17 TEXT DEFAULT ' ',
  LOTA18 REAL DEFAULT 0, LOTA19 REAL DEFAULT 0, LOTA20 REAL DEFAULT 0,
  TDLNR TEXT DEFAULT ' ', CARNO TEXT DEFAULT ' ', CARTON TEXT DEFAULT ' ',
  LOADTON REAL DEFAULT 0, CARART TEXT DEFAULT ' ', LOADVOLUME REAL DEFAULT 0,
  DRIVERCEL TEXT DEFAULT ' ', DTDIS TEXT DEFAULT ' ', UZDIS TEXT DEFAULT ' ',
  SIGNI TEXT DEFAULT ' ', EXTI2 TEXT DEFAULT ' ', SDABW TEXT DEFAULT ' ',
  SAP12 TEXT DEFAULT ' ', SAP13 TEXT DEFAULT ' ', TDOBJECT TEXT DEFAULT ' ',
  ITFCNT INTEGER DEFAULT 0, ITFFLG TEXT DEFAULT ' ',
  ITFETX TEXT DEFAULT ' ', ITFDAT TEXT DEFAULT ' ', ITFTIM TEXT DEFAULT ' ',
  PRCCNT INTEGER DEFAULT 0, PRCFLG TEXT DEFAULT ' ',
  PRCETX TEXT DEFAULT ' ', PRCDAT TEXT DEFAULT ' ', PRCTIM TEXT DEFAULT ' ',
  CDATE TEXT DEFAULT ' ', TDATE TEXT DEFAULT ' ',
  CREDAT TEXT DEFAULT ' ', CRETIM TEXT DEFAULT ' ', CREUSR TEXT DEFAULT ' ',
  LMODAT TEXT DEFAULT ' ', LMOTIM TEXT DEFAULT ' ', LMOUSR TEXT DEFAULT ' ',
  INDBZL TEXT DEFAULT ' ', INDARC TEXT DEFAULT ' ', UPDCHK INTEGER DEFAULT 0,
  IFID TEXT DEFAULT ' ', BWARTSAP TEXT DEFAULT ' ', LOEKZ TEXT DEFAULT ' ',
  LOGSEQNO TEXT, VTXTK TEXT DEFAULT ' ',
  KVGR1 TEXT, KVGR2 TEXT, KVGR3 TEXT,
  SOBKZ TEXT, AUGRU TEXT, STOKKY TEXT, LOTNUM TEXT,
  PO_NO TEXT DEFAULT ' ', PO_REV INTEGER, PO_LNO INTEGER,
  TLOTA01 TEXT DEFAULT ' ', TLOTA02 TEXT DEFAULT ' ',
  VBELV TEXT DEFAULT ' ', POSNV TEXT DEFAULT ' ',
  ZKUNNR3 TEXT, VOLEH TEXT, VTWEG TEXT, TDLNR_NM TEXT DEFAULT ' ',
  PRIMARY KEY (MANDT, SEQNO, VBELN, POSNR)
);
"""

def sv(v):
    """safe value"""
    if v is None: return ' '
    if isinstance(v, float) and v != v: return 0  # nan
    s = str(v).strip()
    return s if s else ' '

def nv(v):
    """numeric value"""
    if v is None: return 0
    try: return float(v)
    except: return 0

def iv(v):
    """int value"""
    try: return int(float(v)) if v is not None else 0
    except: return 0

def read_xlsx(fname, skip_kr_header=False):
    wb = openpyxl.load_workbook(f"{DATA_DIR}/{fname}", read_only=True)
    ws = wb.active
    headers = [c.value for c in next(ws.iter_rows(min_row=1, max_row=1))]
    rows = []
    for row in ws.iter_rows(min_row=2, values_only=True):
        if row[0] is None: continue
        if skip_kr_header and str(row[0]).strip() in ('클라이언트', 'MANDT'): continue
        rows.append(dict(zip(headers, row)))
    wb.close()
    return rows

def load_all(conn):
    # ── CMCDM ──────────────────────────────────────
    data = read_xlsx("CMCDM_데이터.xlsx")
    conn.executemany("INSERT OR REPLACE INTO CMCDM VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        [(sv(d['CMCDKY']),sv(d.get('SHORTX')),sv(d.get('DBFILD')),
          sv(d.get('USARL1')),sv(d.get('USARL2')),sv(d.get('USARL3')),
          sv(d.get('USARL4')),sv(d.get('USARL5')),sv(d.get('SYONLY')),
          sv(d.get('CREDAT')),sv(d.get('CRETIM')),sv(d.get('CREUSR')),
          sv(d.get('LMODAT')),sv(d.get('LMOTIM')),sv(d.get('LMOUSR')),
          sv(d.get('INDBZL')),sv(d.get('INDARC')),iv(d.get('UPDCHK'))) for d in data])
    conn.commit(); print(f"✅ CMCDM  : {len(data):>7,} rows")

    # ── CMCDV ──────────────────────────────────────
    data = read_xlsx("CMCDV_데이터.xlsx")
    conn.executemany("INSERT OR REPLACE INTO CMCDV VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        [(sv(d['CMCDKY']),sv(d.get('CMCDVL')),sv(d.get('CDESC1')),sv(d.get('CDESC2')),
          sv(d.get('USARG1')),sv(d.get('USARG2')),sv(d.get('USARG3')),
          sv(d.get('USARG4')),sv(d.get('USARG5')),
          sv(d.get('CREDAT')),sv(d.get('CRETIM')),sv(d.get('CREUSR')),
          sv(d.get('LMODAT')),sv(d.get('LMOTIM')),sv(d.get('LMOUSR')),
          sv(d.get('INDBZL')),sv(d.get('INDARC')),iv(d.get('UPDCHK'))) for d in data])
    conn.commit(); print(f"✅ CMCDV  : {len(data):>7,} rows")

    # ── SKUMA ──────────────────────────────────────
    data = read_xlsx("SKUMA_데이터.xlsx")
    rows = []
    for d in data:
        rows.append((
            sv(d['OWNRKY']),sv(d['SKUKEY']),sv(d.get('DELMAK')),
            sv(d.get('DESC01')),sv(d.get('DESC02')),sv(d.get('VENDKY')),
            sv(d.get('ASKU01')),sv(d.get('ASKU02')),sv(d.get('ASKU03')),
            sv(d.get('ASKU04')),sv(d.get('ASKU05')),
            sv(d.get('ASKL01')),sv(d.get('ASKL02')),sv(d.get('ASKL03')),
            sv(d.get('ASKL04')),sv(d.get('ASKL05')),
            sv(d.get('EANCOD')),sv(d.get('GTINCD')),
            sv(d.get('SKUG01')),sv(d.get('SKUG02')),sv(d.get('SKUG03')),
            sv(d.get('SKUG04')),sv(d.get('SKUG05')),
            sv(d.get('SKUL01')),sv(d.get('SKUL02')),sv(d.get('SKUL03')),
            sv(d.get('SKUL04')),sv(d.get('SKUL05')),
            nv(d.get('GRSWGT')),nv(d.get('NETWGT')),sv(d.get('WGTUNT')),
            nv(d.get('LENGTH')),nv(d.get('WIDTHW')),nv(d.get('HEIGHT')),
            nv(d.get('CUBICM')),nv(d.get('CAPACT')),
            sv(d.get('DUOMKY')),nv(d.get('QTDUOM')),sv(d.get('ABCANV')),
            sv(d.get('LOTL01')),sv(d.get('LOTL02')),sv(d.get('LOTL03')),
            sv(d.get('LOTL04')),sv(d.get('LOTL05')),sv(d.get('LOTL06')),
            sv(d.get('LOTL07')),sv(d.get('LOTL08')),sv(d.get('LOTL09')),
            sv(d.get('LOTL10')),sv(d.get('LOTL11')),sv(d.get('LOTL12')),
            sv(d.get('LOTL13')),sv(d.get('LOTL14')),sv(d.get('LOTL15')),
            sv(d.get('LOTL16')),sv(d.get('LOTL17')),sv(d.get('LOTL18')),
            sv(d.get('LOTL19')),sv(d.get('LOTL20')),
            iv(d.get('OUTDMT')),iv(d.get('RIMDMT')),
            iv(d.get('INNDPT')),iv(d.get('SECTWD')),
            nv(d.get('WEIGHT')),sv(d.get('DLGORT')),sv(d.get('BATMNG')),
            sv(d.get('LGPRO')),sv(d.get('CSTDAT')),sv(d.get('CPSKUG')),
            sv(d.get('DESC03')),sv(d.get('DESC04')),
            iv(d.get('QTYMON')),iv(d.get('QTYSTD')),iv(d.get('QTYCNT')),
            sv(d.get('BUFMNG')),
            sv(d.get('CREDAT')),sv(d.get('CRETIM')),sv(d.get('CREUSR')),
            sv(d.get('LMODAT')),sv(d.get('LMOTIM')),sv(d.get('LMOUSR')),
            sv(d.get('INDBZL')),sv(d.get('INDARC')),iv(d.get('UPDCHK')),
            sv(d.get('MTYPE'))
        ))
    ph = ','.join(['?']*85)
    conn.executemany(f"INSERT OR REPLACE INTO SKUMA VALUES({ph})", rows)
    conn.commit(); print(f"✅ SKUMA  : {len(rows):>7,} rows")

    # ── BZPTN ──────────────────────────────────────
    # Excel: PTNRKY,PTNRTY,DELMAK,NAME01..EXPTNK,OWNRKY,CUSTMR,PTNG01..PTNL05,WTOPPM..LOTCD2
    # DDL  : PTNRKY,PTNRTY,OWNRKY,DELMAK,NAME01..EXPTNK,CUSTMR,PTNG01..PTNL05,WTOPPM..LOTCD2
    data = read_xlsx("BZPTN_데이터.xlsx")
    rows = []
    for d in data:
        rows.append((
            sv(d['PTNRKY']),sv(d.get('PTNRTY',' ')),sv(d.get('OWNRKY',' ')),  # DDL col3=OWNRKY
            sv(d.get('DELMAK')),sv(d.get('NAME01')),sv(d.get('NAME02')),
            sv(d.get('NAME03')),sv(d.get('ADDR01')),sv(d.get('ADDR02')),
            sv(d.get('ADDR03')),sv(d.get('ADDR04')),sv(d.get('ADDR05')),
            sv(d.get('CITY01')),sv(d.get('REGN01')),sv(d.get('POSTCD')),
            sv(d.get('NATNKY')),sv(d.get('TELN01')),sv(d.get('TELN02')),
            sv(d.get('TELN03')),sv(d.get('FAXTL1')),sv(d.get('FAXTL2')),
            sv(d.get('TAXCD1')),sv(d.get('TAXCD2')),sv(d.get('VATREG')),
            sv(d.get('POBOX1')),sv(d.get('POBPC1')),
            sv(d.get('EMAIL1')),sv(d.get('EMAIL2')),
            sv(d.get('CTTN01')),sv(d.get('CTTT01')),sv(d.get('CTTT02')),
            sv(d.get('CTTM01')),sv(d.get('SALN01')),sv(d.get('SALT01')),
            sv(d.get('SALT02')),sv(d.get('SALM01')),
            sv(d.get('EXPTNK')),sv(d.get('CUSTMR')),
            sv(d.get('PTNG01')),sv(d.get('PTNG02')),sv(d.get('PTNG03')),
            sv(d.get('PTNG04')),sv(d.get('PTNG05')),
            sv(d.get('PTNL01')),sv(d.get('PTNL02')),sv(d.get('PTNL03')),
            sv(d.get('PTNL04')),sv(d.get('PTNL05')),
            nv(d.get('WTOPPM')),nv(d.get('WTOPMU')),nv(d.get('WTOPDV')),
            sv(d.get('PROCHA')),
            sv(d.get('CREDAT')),sv(d.get('CRETIM')),sv(d.get('CREUSR')),
            sv(d.get('LMODAT')),sv(d.get('LMOTIM')),sv(d.get('LMOUSR')),
            sv(d.get('INDBZL')),sv(d.get('INDARC')),iv(d.get('UPDCHK')),
            sv(d.get('DEPTID')),sv(d.get('DNAME')),sv(d.get('USRID1')),
            sv(d.get('LOTCD')),sv(d.get('LOTCD2'))
        ))
    ph = ','.join(['?']*66)
    conn.executemany(f"INSERT OR REPLACE INTO BZPTN VALUES({ph})", rows)
    conn.commit(); print(f"✅ BZPTN  : {len(rows):>7,} rows")

    # ── MEASI ──────────────────────────────────────
    data = read_xlsx("MEASI_데이터.xlsx")
    conn.executemany("INSERT OR REPLACE INTO MEASI VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        [(sv(d['WAREKY']),sv(d['MEASKY']),sv(d.get('ITEMNO','1')),
          sv(d.get('UOMKEY')),nv(d.get('QTPUOM')),
          sv(d.get('INDDFU')),sv(d.get('DISREC')),sv(d.get('DISSHP')),sv(d.get('DISTAS')),
          nv(d.get('LENGTH')),nv(d.get('WIDTHW')),nv(d.get('HEIGHT')),
          nv(d.get('CUBICM')),nv(d.get('QTAUOM')),
          sv(d.get('CREDAT')),sv(d.get('CRETIM')),sv(d.get('CREUSR')),
          sv(d.get('LMODAT')),sv(d.get('LMOTIM')),sv(d.get('LMOUSR')),
          sv(d.get('INDBZL')),sv(d.get('INDARC')),iv(d.get('UPDCHK'))) for d in data])
    conn.commit(); print(f"✅ MEASI  : {len(data):>7,} rows")

    # ── SHPDH ──────────────────────────────────────
    data = read_xlsx("SHPDH_데이터.xlsx")
    rows = []
    for d in data:
        rows.append((
            sv(d['SHPOKY']),sv(d.get('WAREKY')),sv(d.get('SHPMTY')),
            sv(d.get('ALSTKY')),sv(d.get('STATDO')),sv(d.get('DOCDAT')),
            sv(d.get('DOCCAT')),sv(d.get('PRORTY')),sv(d.get('DOCUTY')),
            sv(d.get('OWNRKY')),sv(d.get('DRELIN')),sv(d.get('RQSHPD')),
            sv(d.get('RQARRD')),sv(d.get('RQARRT')),sv(d.get('LSHPCD')),
            sv(d.get('DPTNKY')),sv(d.get('PTRCVR')),
            sv(d.get('PGRC01')),sv(d.get('PGRC02')),sv(d.get('PGRC03')),
            sv(d.get('PGRC04')),sv(d.get('PGRC05')),
            sv(d.get('VEHINO')),sv(d.get('DRIVER')),
            sv(d.get('ESHPKY')),sv(d.get('OPURKY')),
            sv(d.get('LOCADT')),sv(d.get('LOCADK')),
            sv(d.get('INDDCL')),sv(d.get('RSNCOD')),sv(d.get('RSNRET')),
            nv(d.get('QTSHPO')),nv(d.get('QTYREF')),nv(d.get('QTAPPO')),
            nv(d.get('QTALOC')),nv(d.get('QTJCMP')),
            nv(d.get('QTSHPD')),nv(d.get('QTSHPC')),
            sv(d.get('USRID1')),sv(d.get('UNAME1')),
            sv(d.get('DEPTID1')),sv(d.get('DNAME1')),
            sv(d.get('USRID2')),sv(d.get('UNAME2')),
            sv(d.get('DEPTID2')),sv(d.get('DNAME2')),
            sv(d.get('USRID3')),sv(d.get('UNAME3')),
            sv(d.get('DEPTID3')),sv(d.get('DNAME3')),
            sv(d.get('USRID4')),sv(d.get('UNAME4')),
            sv(d.get('DEPTID4')),sv(d.get('DNAME4')),
            sv(d.get('DOCTXT')),
            sv(d.get('CREDAT')),sv(d.get('CRETIM')),sv(d.get('CREUSR')),
            sv(d.get('LMODAT')),sv(d.get('LMOTIM')),sv(d.get('LMOUSR')),
            sv(d.get('INDBZL')),sv(d.get('INDARC')),iv(d.get('UPDCHK')),
            sv(d.get('KEEPTS')),sv(d.get('TDLNR')),sv(d.get('CARNO')),
            sv(d.get('CARTON')),nv(d.get('LOADTON')),sv(d.get('CARART')),
            nv(d.get('LOADVOLUME')),sv(d.get('DRIVERCEL')),
            sv(d.get('DTDIS')),sv(d.get('UZDIS')),sv(d.get('SIGNI')),
            sv(d.get('EXTI2')),sv(d.get('SDABW')),sv(d.get('SAP1')),
            sv(d.get('SAP2')),sv(d.get('TDOBJECT')),
            sv(d.get('VTXTK')),sv(d.get('IFID')),sv(d.get('RSNTXT')),
            sv(d.get('KVGR1')),sv(d.get('KVGR2')),sv(d.get('KVGR3')),
            sv(d.get('LFART')),sv(d.get('ZLGORT')),
            sv(d.get('ZKUNNR3')),sv(d.get('PRTCHK')),sv(d.get('VTWEG')),
            sv(d.get('TDLNR_NM'))
        ))
    ph = ','.join(['?']*92)
    conn.executemany(f"INSERT OR REPLACE INTO SHPDH VALUES({ph})", rows)
    conn.commit(); print(f"✅ SHPDH  : {len(rows):>7,} rows")

    # ── SHPDI ──────────────────────────────────────
    # DDL 69 cols: skip LOTA01-20 / SAP fields from Excel(117 cols)
    data = read_xlsx("SHPDI_데이터.xlsx")
    rows = []
    for d in data:
        rows.append((
            sv(d['SHPOKY']),sv(d['SHPOIT']),sv(d.get('STATIT')),sv(d.get('SKUKEY')),
            nv(d.get('QTYORG')),nv(d.get('QTSHPO')),nv(d.get('QTYREF')),
            nv(d.get('QTAPPO')),nv(d.get('QTALOC')),nv(d.get('QTJCMP')),
            nv(d.get('QTSHPD')),nv(d.get('QTSHPC')),nv(d.get('QTYUOM')),
            sv(d.get('MEASKY')),sv(d.get('UOMKEY')),nv(d.get('QTPUOM')),
            sv(d.get('DUOMKY')),nv(d.get('QTDUOM')),
            sv(d.get('SASTKY')),sv(d.get('ALSTKY')),sv(d.get('TKFLKY')),
            sv(d.get('ESHPKY')),sv(d.get('ESHPIT')),sv(d.get('OPURKY')),
            sv(d.get('REFDKY')),sv(d.get('REFDIT')),
            sv(d.get('REFCAT')),sv(d.get('REFDAT')),sv(d.get('EXSUBS')),
            sv(d.get('DESC01')),sv(d.get('DESC02')),
            sv(d.get('ASKU01')),sv(d.get('ASKU02')),sv(d.get('ASKU03')),
            sv(d.get('ASKU04')),sv(d.get('ASKU05')),
            sv(d.get('EANCOD')),sv(d.get('GTINCD')),
            sv(d.get('SKUG01')),sv(d.get('SKUG02')),sv(d.get('SKUG03')),
            sv(d.get('SKUG04')),sv(d.get('SKUG05')),
            nv(d.get('GRSWGT')),nv(d.get('NETWGT')),sv(d.get('WGTUNT')),
            nv(d.get('LENGTH')),nv(d.get('WIDTHW')),nv(d.get('HEIGHT')),
            nv(d.get('CUBICM')),nv(d.get('CAPACT')),
            sv(d.get('PROCHA')),sv(d.get('AREAKY')),
            sv(d.get('PTNRKY')),sv(d.get('NAME01')),sv(d.get('SLAND1')),
            sv(d.get('SBKTXT')),
            sv(d.get('CREDAT')),sv(d.get('CRETIM')),sv(d.get('CREUSR')),
            sv(d.get('LMODAT')),sv(d.get('LMOTIM')),sv(d.get('LMOUSR')),
            sv(d.get('INDBZL')),sv(d.get('INDARC')),iv(d.get('UPDCHK')),
            sv(d.get('PO_NO')),sv(d.get('CHGFLG')),sv(d.get('KEEPIT'))
        ))
    ph = ','.join(['?']*69)
    conn.executemany(f"INSERT OR REPLACE INTO SHPDI VALUES({ph})", rows)
    conn.commit(); print(f"✅ SHPDI  : {len(rows):>7,} rows")

    # ── IFWMS113 ───────────────────────────────────
    # DDL 139 cols — includes LOTA01~LOTA20 between SBKTXT and TDLNR
    data = read_xlsx("IFWMS113_데이터.xlsx", skip_kr_header=True)
    rows = []
    for d in data:
        mandt = sv(d.get('MANDT'))
        if mandt in (' ', '클라이언트'): continue
        rows.append((
            mandt, iv(d.get('SEQNO')), sv(d.get('VBELN')), sv(d.get('POSNR')),
            sv(d.get('BWART')),sv(d.get('PSTYV')),
            sv(d.get('ZLIKP_ERDAT')),sv(d.get('ZLIKP_ERZET')),
            sv(d.get('ZLIKP_AEDAT')),sv(d.get('VSTEL')),
            sv(d.get('LFART')),sv(d.get('WADAT')),
            sv(d.get('KUNNR')),sv(d.get('KUNAG')),
            sv(d.get('WERKS')),sv(d.get('LGORT')),sv(d.get('MATNR')),
            nv(d.get('LFIMG')),nv(d.get('QTSHP')),sv(d.get('MEINS')),
            nv(d.get('NETPR')),nv(d.get('NETWR')),
            nv(d.get('MWSBP')),nv(d.get('MWSDC')),sv(d.get('WAERK')),
            sv(d.get('BWTAR')),sv(d.get('VGBEL')),sv(d.get('VGPOS')),
            sv(d.get('VGDAT')),sv(d.get('STKNUM')),sv(d.get('SDATBG')),
            sv(d.get('STATUS')),sv(d.get('IFFLG')),
            iv(d.get('RETRY')),iv(d.get('ERCOD')),
            sv(d.get('ERTXT')),sv(d.get('CUSRID')),sv(d.get('CUNAME')),
            sv(d.get('CPSTLZ')),sv(d.get('LAND1')),sv(d.get('TELF1')),
            sv(d.get('TELE2')),sv(d.get('SMTP_ADDR')),
            sv(d.get('KUKLA')),sv(d.get('VTEXT')),
            sv(d.get('ADDR')),sv(d.get('CNAME')),sv(d.get('CPHON')),
            sv(d.get('BNAME')),sv(d.get('BPHON')),
            sv(d.get('WAREKY')),sv(d.get('SKUKEY')),
            sv(d.get('DESC01')),sv(d.get('DESC02')),
            sv(d.get('USRID1')),sv(d.get('DEPTID1')),
            sv(d.get('USRID2')),sv(d.get('DEPTID2')),
            sv(d.get('USRID3')),sv(d.get('DEPTID3')),
            sv(d.get('USRID4')),sv(d.get('DEPTID4')),
            sv(d.get('C00101')),sv(d.get('C00102')),
            sv(d.get('C00103')),sv(d.get('C00104')),sv(d.get('C00105')),
            nv(d.get('N00101')),nv(d.get('N00102')),
            sv(d.get('C00106')),sv(d.get('C00107')),sv(d.get('C00108')),
            sv(d.get('C00109')),sv(d.get('C00110')),
            nv(d.get('N00103')),nv(d.get('N00104')),nv(d.get('N00105')),
            sv(d.get('ORDCAT')),sv(d.get('PTNRTY')),sv(d.get('SBKTXT')),
            # LOTA01~LOTA20
            sv(d.get('LOTA01')),sv(d.get('LOTA02')),sv(d.get('LOTA03')),
            sv(d.get('LOTA04')),sv(d.get('LOTA05')),sv(d.get('LOTA06')),
            sv(d.get('LOTA07')),sv(d.get('LOTA08')),sv(d.get('LOTA09')),
            sv(d.get('LOTA10')),sv(d.get('LOTA11')),sv(d.get('LOTA12')),
            sv(d.get('LOTA13')),sv(d.get('LOTA14')),sv(d.get('LOTA15')),
            nv(d.get('LOTA16')),sv(d.get('LOTA17')),
            nv(d.get('LOTA18')),nv(d.get('LOTA19')),nv(d.get('LOTA20')),
            sv(d.get('TDLNR')),sv(d.get('CARNO')),sv(d.get('CARTON')),
            nv(d.get('LOADTON')),sv(d.get('CARART')),nv(d.get('LOADVOLUME')),
            sv(d.get('DRIVERCEL')),sv(d.get('DTDIS')),sv(d.get('UZDIS')),
            sv(d.get('SIGNI')),sv(d.get('EXTI2')),sv(d.get('SDABW')),
            sv(d.get('SAP12')),sv(d.get('SAP13')),sv(d.get('TDOBJECT')),
            iv(d.get('ITFCNT')),sv(d.get('ITFFLG')),
            sv(d.get('ITFETX')),sv(d.get('ITFDAT')),sv(d.get('ITFTIM')),
            iv(d.get('PRCCNT')),sv(d.get('PRCFLG')),
            sv(d.get('PRCETX')),sv(d.get('PRCDAT')),sv(d.get('PRCTIM')),
            sv(d.get('CDATE')),sv(d.get('TDATE')),
            sv(d.get('CREDAT')),sv(d.get('CRETIM')),sv(d.get('CREUSR')),
            sv(d.get('LMODAT')),sv(d.get('LMOTIM')),sv(d.get('LMOUSR')),
            sv(d.get('INDBZL')),sv(d.get('INDARC')),iv(d.get('UPDCHK')),
            sv(d.get('IFID')),sv(d.get('BWARTSAP')),sv(d.get('LOEKZ')),
            sv(d.get('LOGSEQNO')),sv(d.get('VTXTK')),
            sv(d.get('KVGR1')),sv(d.get('KVGR2')),sv(d.get('KVGR3')),
            sv(d.get('SOBKZ')),sv(d.get('AUGRU')),sv(d.get('STOKKY')),sv(d.get('LOTNUM')),
            sv(d.get('PO_NO')),
            d.get('PO_REV') if d.get('PO_REV') is not None else None,
            d.get('PO_LNO') if d.get('PO_LNO') is not None else None,
            sv(d.get('TLOTA01')),sv(d.get('TLOTA02')),
            sv(d.get('VBELV')),sv(d.get('POSNV')),
            sv(d.get('ZKUNNR3')),sv(d.get('VOLEH')),sv(d.get('VTWEG')),
            sv(d.get('TDLNR_NM'))
        ))
    ph = ','.join(['?']*159)
    conn.executemany(f"INSERT OR REPLACE INTO IFWMS113 VALUES({ph})", rows)
    conn.commit(); print(f"✅ IFWMS113: {len(rows):>7,} rows")

if __name__ == "__main__":
    if os.path.exists(DB_PATH):
        os.remove(DB_PATH)
    conn = get_conn()
    conn.executescript(DDL)
    conn.commit()
    print("✅ Tables created\n")
    load_all(conn)
    print("\n📊 Final row counts:")
    for t in ['CMCDM','CMCDV','SKUMA','BZPTN','MEASI','SHPDH','SHPDI','IFWMS113']:
        c = conn.execute(f"SELECT COUNT(*) FROM {t}").fetchone()[0]
        print(f"  {t:<12}: {c:>8,}")
    conn.close()
    print("\n✅ Done!")
