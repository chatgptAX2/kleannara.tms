#!/usr/bin/env python3
"""
rebuild_db.py — wms.db 전체 재구축 스크립트
sandbox 재시작으로 DB 초기화 시 실행하여 모든 테이블과 데이터를 복원합니다.

실행: cd /home/user/webapp/wms-viewer && python3 rebuild_db.py
"""
import sqlite3, openpyxl, os, datetime, time

DB_PATH  = '/home/user/webapp/wms-viewer/wms.db'
DATA_DIR = '/home/user/webapp/wms-viewer/data'

# ─────────────────────────────────────────────────────────────────────────────
# 유틸
# ─────────────────────────────────────────────────────────────────────────────
def sv(v):
    if v is None: return ' '
    if isinstance(v, float) and v != v: return ' '   # NaN
    s = str(v).strip()
    return s if s else ' '

def nv(v):
    if v is None: return 0.0
    try: return float(v)
    except: return 0.0

def iv(v):
    try: return int(float(v)) if v is not None else 0
    except: return 0

def read_xlsx(fname, skip_kr_header=False):
    path = os.path.join(DATA_DIR, fname)
    print(f"  읽는 중: {path}")
    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    ws = wb.active
    headers = [c.value for c in next(ws.iter_rows(min_row=1, max_row=1))]
    rows = []
    for row in ws.iter_rows(min_row=2, values_only=True):
        if row[0] is None: continue
        if skip_kr_header and str(row[0]).strip() in ('클라이언트', 'MANDT'): continue
        d = {}
        for h, val in zip(headers, row):
            if h:
                if isinstance(val, datetime.datetime): val = val.strftime('%Y%m%d%H%M%S')
                elif isinstance(val, datetime.date):   val = val.strftime('%Y%m%d')
                elif isinstance(val, datetime.time):   val = val.strftime('%H%M%S')
                d[h] = val
        rows.append(d)
    wb.close()
    print(f"    → {len(rows):,}행 로드")
    return rows

def get_conn():
    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    conn.row_factory = sqlite3.Row
    return conn

# ─────────────────────────────────────────────────────────────────────────────
# DDL
# ─────────────────────────────────────────────────────────────────────────────
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
  STDLNR TEXT DEFAULT ' ', SVBELN TEXT DEFAULT ' ',
  LOTA01 TEXT DEFAULT ' ', LOTA02 TEXT DEFAULT ' ', LOTA03 TEXT DEFAULT ' ',
  LOTA07 TEXT DEFAULT ' ', LOTA08 TEXT DEFAULT ' ', LOTA09 TEXT DEFAULT ' ',
  LOTA15 TEXT DEFAULT ' ', LOTA17 TEXT DEFAULT ' ',
  TLOTA01 TEXT DEFAULT ' ', TLOTA02 TEXT DEFAULT ' ',
  PRIMARY KEY (SHPOKY, SHPOIT)
);
CREATE TABLE IF NOT EXISTS WAHMA (
  WAREKY TEXT PRIMARY KEY, NAME01 TEXT DEFAULT ' ', NAME02 TEXT DEFAULT ' ',
  ADDR01 TEXT DEFAULT ' ', ADDR02 TEXT DEFAULT ' ', ADDR03 TEXT DEFAULT ' ',
  CITY01 TEXT DEFAULT ' ', REGN01 TEXT DEFAULT ' ', POSTCD TEXT DEFAULT ' ',
  NATNKY TEXT DEFAULT ' ', TELN01 TEXT DEFAULT ' ',
  DELMAK TEXT DEFAULT ' ',
  CREDAT TEXT DEFAULT ' ', CRETIM TEXT DEFAULT ' ', CREUSR TEXT DEFAULT ' ',
  LMODAT TEXT DEFAULT ' ', LMOTIM TEXT DEFAULT ' ', LMOUSR TEXT DEFAULT ' ',
  INDBZL TEXT DEFAULT ' ', INDARC TEXT DEFAULT ' ', UPDCHK INTEGER DEFAULT 0
);
CREATE TABLE IF NOT EXISTS BZPTN_DETAIL (
  PTNRKY TEXT NOT NULL, PTNRTY TEXT NOT NULL, OWNRKY TEXT NOT NULL DEFAULT ' ',
  WAREKY TEXT DEFAULT ' ',
  ROUTE_CD TEXT DEFAULT ' ', ITEM_GROUP TEXT DEFAULT ' ',
  UNLOAD_TIME REAL DEFAULT 0,
  INB_TIME_FROM1 TEXT DEFAULT ' ', INB_TIME_TO1 TEXT DEFAULT ' ',
  AREA_CD TEXT DEFAULT ' ', MAX_HEIGHT REAL DEFAULT 0,
  FORKLIFT_YN TEXT DEFAULT ' ', HANDWORK_YN TEXT DEFAULT ' ',
  AUTO_PLT REAL DEFAULT 0, MAX_BOX_QTY REAL DEFAULT 0,
  AUTO_ALLOC_YN TEXT DEFAULT ' ',
  SINGLE_ITEM_YN TEXT DEFAULT ' ', NY_TYPE TEXT DEFAULT ' ',
  SINGLE_HEIGHT REAL DEFAULT 0,
  DYNAMIC_YN TEXT DEFAULT ' ', LTL_YN TEXT DEFAULT ' ',
  PRIORITY_YN TEXT DEFAULT ' ', MIN_QTSIWH REAL DEFAULT 0,
  LATITUDE REAL DEFAULT 0, LONGITUDE REAL DEFAULT 0,
  DEL_YN TEXT DEFAULT 'N',
  CREDAT TEXT DEFAULT ' ', CRETIM TEXT DEFAULT ' ', CREUSR TEXT DEFAULT ' ',
  LMODAT TEXT DEFAULT ' ', LMOTIM TEXT DEFAULT ' ', LMOUSR TEXT DEFAULT ' ',
  DEADLINE_TIME TEXT DEFAULT ' ', MAX_TON TEXT DEFAULT ' ', REGION_YN TEXT DEFAULT ' ',
  PRIMARY KEY (PTNRKY, PTNRTY, OWNRKY)
);
CREATE TABLE IF NOT EXISTS VHCMA (
  VEHICLE_NO TEXT NOT NULL, OWNRKY TEXT NOT NULL DEFAULT ' ',
  SHIP_POINT TEXT DEFAULT ' ', PRODUCT_GROUP TEXT DEFAULT ' ',
  DELIVERY_ZONE TEXT DEFAULT ' ', CARRIER TEXT DEFAULT ' ',
  VEHICLE_TYPE TEXT DEFAULT ' ', VEHICLE_KIND TEXT DEFAULT ' ',
  VEHICLE_CLASS TEXT DEFAULT ' ',
  DRIVER_NAME TEXT DEFAULT ' ', CONTACT_NO TEXT DEFAULT ' ',
  AXLE_TYPE TEXT DEFAULT ' ',
  LOAD_VOLUME REAL DEFAULT 0, LOAD_WEIGHT REAL DEFAULT 0,
  PALLET_QTY REAL DEFAULT 0,
  CARGO_LENGTH REAL DEFAULT 0, CARGO_WIDTH REAL DEFAULT 0,
  CARGO_HEIGHT REAL DEFAULT 0,
  FLOOR_TYPE TEXT DEFAULT ' ',
  USE_YN TEXT DEFAULT 'Y', OPERABLE_YN TEXT DEFAULT 'Y',
  DLV_TIME_FROM TEXT DEFAULT ' ', DLV_TIME_TO TEXT DEFAULT ' ',
  VEHICLE_YEAR TEXT DEFAULT ' ',
  DELIVERY_CUSTOMER_1 TEXT DEFAULT ' ', DELIVERY_CUSTOMER_2 TEXT DEFAULT ' ',
  DEL_YN TEXT DEFAULT 'N',
  CREDAT TEXT DEFAULT ' ', CRETIM TEXT DEFAULT ' ', CREUSR TEXT DEFAULT ' ',
  LMODAT TEXT DEFAULT ' ', LMOTIM TEXT DEFAULT ' ', LMOUSR TEXT DEFAULT ' ',
  PRIMARY KEY (VEHICLE_NO, OWNRKY)
);
CREATE TABLE IF NOT EXISTS DS_VEHICLE (
  CARCLASS_CD TEXT PRIMARY KEY,
  CARTYPE TEXT,
  LENGTH_M REAL, WIDTH_M TEXT, HEIGHT_M REAL,
  LOAD_TON REAL,
  SORT_SEQ INTEGER DEFAULT 0,
  UPDDAT TEXT, UPDUSR TEXT,
  PALLET_HEIGHT_M REAL DEFAULT 0,
  INCH12_LT300 INTEGER, INCH12_GE300 INTEGER,
  INCH3_LT300 INTEGER, INCH3_GE300 INTEGER,
  DEFAULT_VEH_CNT INTEGER, PALLET_CNT INTEGER,
  LONG_AXIS_YN TEXT DEFAULT 'N'
);
CREATE TABLE IF NOT EXISTS ROUTE_COST (
  SHPPT TEXT NOT NULL DEFAULT '',
  ROUTE TEXT NOT NULL DEFAULT '',
  PTNRKY TEXT NOT NULL DEFAULT '',
  CARCLASS TEXT NOT NULL DEFAULT '',
  COST REAL,
  UNIT TEXT DEFAULT 'KRW',
  DATE_START TEXT DEFAULT '', DATE_END TEXT DEFAULT '',
  CREDAT TEXT DEFAULT '', CRETIM TEXT DEFAULT '',
  CREUSR TEXT DEFAULT 'ADMIN',
  LMODAT TEXT DEFAULT '', LMOTIM TEXT DEFAULT '',
  LMOUSR TEXT DEFAULT 'ADMIN',
  PRIMARY KEY (SHPPT, ROUTE, PTNRKY, CARCLASS)
);
CREATE TABLE IF NOT EXISTS PS_DISPATCH_H (
  DISPATCH_NO TEXT PRIMARY KEY,
  DISPATCH_DT TEXT DEFAULT '',
  RQSHPD TEXT DEFAULT '',
  DPTNKY TEXT DEFAULT '',
  DPTNM TEXT DEFAULT '',
  CARTYPE TEXT DEFAULT '',
  STATUS TEXT DEFAULT 'DRAFT',
  TOTAL_KG REAL DEFAULT 0,
  TOTAL_CNT INTEGER DEFAULT 0,
  NOTE TEXT DEFAULT '',
  CREDAT TEXT DEFAULT '',
  CREUSR TEXT DEFAULT 'SYSTEM'
);
CREATE TABLE IF NOT EXISTS PS_DISPATCH_D (
  DISPATCH_NO TEXT NOT NULL,
  SEQ INTEGER NOT NULL,
  SHPOKY TEXT DEFAULT '',
  SHPOIT TEXT DEFAULT '',
  SKUKEY TEXT DEFAULT '',
  DESC01 TEXT DEFAULT '',
  QTSHPO REAL DEFAULT 0,
  UOMKEY TEXT DEFAULT 'KG',
  DPTNKY TEXT DEFAULT '',
  DPTNM TEXT DEFAULT '',
  IS_SPLIT INTEGER DEFAULT 0,
  ORG_SHPOKY TEXT DEFAULT '',
  ORG_SHPOIT TEXT DEFAULT '',
  GRSWGT REAL DEFAULT 0,
  KG_WEIGHT REAL DEFAULT 0,
  PRIMARY KEY (DISPATCH_NO, SEQ)
);
CREATE TABLE IF NOT EXISTS PS_DISPATCH_SPLIT (
  SPLIT_KEY TEXT PRIMARY KEY,
  ORG_SHPOKY TEXT DEFAULT '',
  ORG_SHPOIT TEXT DEFAULT '',
  NEW_SHPOKY TEXT DEFAULT '',
  NEW_SHPOIT TEXT DEFAULT '',
  SKUKEY TEXT DEFAULT '',
  DESC01 TEXT DEFAULT '',
  ORG_QTY REAL DEFAULT 0,
  SPLIT_QTY REAL DEFAULT 0,
  REM_QTY REAL DEFAULT 0,
  UOMKEY TEXT DEFAULT 'KG',
  STATUS TEXT DEFAULT 'ACTIVE',
  CREDAT TEXT DEFAULT '',
  CREUSR TEXT DEFAULT 'SYSTEM'
);
CREATE TABLE IF NOT EXISTS PS_SAP_STK (
  STDLNR TEXT PRIMARY KEY,
  SAP_STKNUM TEXT DEFAULT '',
  DISPATCH_NO TEXT DEFAULT '',
  RQSHPD_FROM TEXT DEFAULT '',
  RQSHPD_TO TEXT DEFAULT '',
  DPTNKY TEXT DEFAULT '',
  DPTNKYNM TEXT DEFAULT '',
  CARTYPE TEXT DEFAULT '',
  CARCLASS_CD TEXT DEFAULT '',
  VEHINO TEXT DEFAULT '',
  CARNO TEXT DEFAULT '',
  DRIVER TEXT DEFAULT '',
  DRIVERCEL TEXT DEFAULT '',
  TOTAL_KG REAL DEFAULT 0,
  SVBELN_CNT INTEGER DEFAULT 0,
  STATUS TEXT DEFAULT 'DRAFT',
  CREDAT TEXT DEFAULT '',
  CREUSR TEXT DEFAULT 'SYSTEM'
);
CREATE TABLE IF NOT EXISTS RECDI (
  RECVKY  TEXT NOT NULL,
  SKUKEY  TEXT NOT NULL DEFAULT ' ',
  STATIT  TEXT DEFAULT ' ',
  LOTA01  TEXT DEFAULT ' ',
  LOTA02  TEXT DEFAULT ' ',
  QTYRCV  REAL DEFAULT 0,
  WAREKY  TEXT DEFAULT ' ',
  CREDAT  TEXT DEFAULT ' ',
  CRETIM  TEXT DEFAULT ' ',
  CREUSR  TEXT DEFAULT ' ',
  LMODAT  TEXT DEFAULT ' ',
  LMOTIM  TEXT DEFAULT ' ',
  LMOUSR  TEXT DEFAULT ' ',
  PRIMARY KEY (RECVKY)
);
CREATE TABLE IF NOT EXISTS DOC_FOLDER (
  FOLDER_ID TEXT PRIMARY KEY,
  FOLDER_NM TEXT NOT NULL,
  PARENT_ID TEXT DEFAULT NULL,
  SORT_ORD INTEGER DEFAULT 0,
  SYSTEM_YN TEXT DEFAULT 'N',
  CRTUSR TEXT DEFAULT 'SYSTEM',
  CRTDAT TEXT DEFAULT '',
  CRTTIM TEXT DEFAULT '',
  DEL_YN TEXT DEFAULT 'N'
);
CREATE TABLE IF NOT EXISTS DOC_FILE (
  FILE_ID TEXT PRIMARY KEY,
  FOLDER_ID TEXT NOT NULL,
  ORIG_NM TEXT NOT NULL,
  SAVE_NM TEXT NOT NULL,
  FILE_EXT TEXT DEFAULT '',
  FILE_SIZE INTEGER DEFAULT 0,
  OP_DATE TEXT DEFAULT '',
  UPLOAD_DAT TEXT DEFAULT '',
  UPLOAD_TIM TEXT DEFAULT '',
  UPLOAD_USR TEXT DEFAULT 'USER',
  NOTE TEXT DEFAULT '',
  DEL_YN TEXT DEFAULT 'N'
);
CREATE TABLE IF NOT EXISTS DS_DISPATCH_PROFILE (
  PROFILE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
  PROFILE_NM TEXT    NOT NULL,
  OBJECTIVE  TEXT    DEFAULT '',
  ACTIVE_YN  TEXT    DEFAULT 'Y',
  NOTE       TEXT    DEFAULT '',
  CREDAT     TEXT    DEFAULT '',
  LMODAT     TEXT    DEFAULT '',
  SET_ID     INTEGER DEFAULT NULL
);
CREATE TABLE IF NOT EXISTS DS_DISPATCH_CONST (
  CONST_ID    INTEGER PRIMARY KEY AUTOINCREMENT,
  PROFILE_ID  INTEGER NOT NULL,
  CONST_TYPE  TEXT    DEFAULT '',
  CONST_KEY   TEXT    NOT NULL,
  CONST_VALUE TEXT    DEFAULT '',
  CONST_OP    TEXT    DEFAULT '=',
  TARGET_ID   TEXT    DEFAULT '',
  TARGET_NM   TEXT    DEFAULT '',
  ACTIVE_YN   TEXT    DEFAULT 'Y',
  NOTE        TEXT    DEFAULT '',
  SORT_SEQ    INTEGER DEFAULT 0,
  CREDAT      TEXT    DEFAULT '',
  LMODAT      TEXT    DEFAULT ''
);
CREATE TABLE IF NOT EXISTS DS_DISPATCH_OBJECTIVE (
  OBJ_ID    INTEGER PRIMARY KEY AUTOINCREMENT,
  OBJ_CODE  TEXT    NOT NULL UNIQUE,
  OBJ_NM    TEXT    NOT NULL,
  OBJ_ICON  TEXT    DEFAULT '🎯',
  OBJ_ALGO  TEXT    DEFAULT '',
  OBJ_DESC  TEXT    DEFAULT '',
  SORT_SEQ  INTEGER DEFAULT 0,
  ACTIVE_YN TEXT    DEFAULT 'Y',
  CREDAT    TEXT    DEFAULT '',
  LMODAT    TEXT    DEFAULT ''
);
CREATE TABLE IF NOT EXISTS DS_DISPATCH_CONST_SET (
  SET_ID    INTEGER PRIMARY KEY AUTOINCREMENT,
  SET_NM    TEXT    NOT NULL,
  SET_DESC  TEXT    DEFAULT '',
  ACTIVE_YN TEXT    DEFAULT 'Y',
  CREDAT    TEXT    DEFAULT '',
  LMODAT    TEXT    DEFAULT ''
);
CREATE TABLE IF NOT EXISTS DS_DISPATCH_CONST_SET_ITEM (
  ITEM_ID    INTEGER PRIMARY KEY AUTOINCREMENT,
  SET_ID     INTEGER NOT NULL,
  CONST_ID   INTEGER NOT NULL,
  ACTIVE_YN  TEXT    DEFAULT 'Y',
  PARAM_VALUE TEXT   DEFAULT NULL
);
CREATE TABLE IF NOT EXISTS DS_INCH12 (
  CARTYPE   TEXT,
  GRM_COND  TEXT,
  MAX_COUNT INTEGER DEFAULT 0,
  SORT_SEQ  INTEGER DEFAULT 0,
  UPDDAT    TEXT DEFAULT ''
);
CREATE TABLE IF NOT EXISTS DS_INCH3 (
  CARTYPE   TEXT,
  GRM_COND  TEXT,
  MAX_COUNT INTEGER DEFAULT 0,
  SORT_SEQ  INTEGER DEFAULT 0,
  UPDDAT    TEXT DEFAULT ''
);
"""

# DS_VEHICLE 기준 데이터
today_str = datetime.date.today().strftime('%Y%m%d')
DS_VEHICLE_DATA = [
    ('Z010','1톤',  2.3,'1.4',  1.5,  1.0,  0, today_str, None, 0.15,  2,  2,  2,  2,  5,  2,'N'),
    ('Z014','1.4톤',2.8,'1.6',  1.8,  1.5,  1, today_str, None, 0.15,  2,  2,  3,  3,  3,  3,'N'),
    ('Z020','2톤',  3.6,'1.7',  1.9,  2.0,  2, today_str, None, 0.15,  3,  3,  4,  4,  4,  4,'N'),
    ('Z025','2.5톤',4.0,'1.8',  2.0,  2.5,  3, today_str, None, 0.15,  3,  3,  4,  4,  4,  4,'N'),
    ('Z030','3톤',  4.3,'1.8',  2.0,  3.0,  4, today_str, None, 0.15,  3,  3,  4,  4,  4,  4,'N'),
    ('Z035','3.5톤',4.8,'1.8~2.1',2.1,3.2,  5, today_str, None, 0.15,  3,  3,  5,  5,  3,  6,'N'),
    ('Z045','4.5톤',5.8,'2.2',  2.3,  4.5,  6, today_str, None, 0.15,  4,  4,  8,  8,  3, 10,'N'),
    ('Z050','5톤',  6.2,'2.4',  2.4,  5.0,  7, today_str, None, 0.15,  4,  4, 10, 10,  3, 10,'N'),
    ('Z080','8톤',  7.5,'2.4',  2.4,  8.0,  8, today_str, None, 0.15,  6,  5, 12, 12,  3, 12,'N'),
    ('Z100','10톤', 9.5,'2.4',  2.4, 10.0,  9, today_str, None, 0.15,  8,  6, 14, 14,  3, 16,'N'),
    ('Z110','11톤',10.2,'2.4',  2.4, 11.0, 10, today_str, None, 0.15,  9,  7, 14, 14,  3, 16,'N'),
    ('Z150','15톤',10.2,'2.4',  2.4, 14.0, 11, today_str, None, 0.15, 10,  8, 14, 14,  3, 16,'N'),
    ('Z180','18톤',10.2,'2.4',  2.4, 17.0, 12, today_str, None, 0.15, 12, 10, 15, 15,  3, 18,'N'),
    ('Z200','20톤',12.0,'2.4',  2.5, 20.0, 13, today_str, None, 0.15, 13, 11, 17, 17,  2, 18,'N'),
    ('Z250','25톤',13.5,'2.5',  2.7, 25.0, 14, today_str, None, 0.15, 14, 12, 18, 18,  2, 18,'N'),
    ('Z260','26톤',14.0,'2.5',  2.7, 26.0, 15, today_str, None, 0.15, 14, 12, 18, 18,  2, 18,'N'),
]

# ─────────────────────────────────────────────────────────────────────────────
# 테이블별 데이터 로드
# ─────────────────────────────────────────────────────────────────────────────
def load_cmcdm(conn, data):
    conn.executemany("INSERT OR REPLACE INTO CMCDM VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        [(sv(d['CMCDKY']),sv(d.get('SHORTX')),sv(d.get('DBFILD')),
          sv(d.get('USARL1')),sv(d.get('USARL2')),sv(d.get('USARL3')),
          sv(d.get('USARL4')),sv(d.get('USARL5')),sv(d.get('SYONLY')),
          sv(d.get('CREDAT')),sv(d.get('CRETIM')),sv(d.get('CREUSR')),
          sv(d.get('LMODAT')),sv(d.get('LMOTIM')),sv(d.get('LMOUSR')),
          sv(d.get('INDBZL')),sv(d.get('INDARC')),iv(d.get('UPDCHK'))) for d in data])
    conn.commit()

def load_cmcdv(conn, data):
    conn.executemany("INSERT OR REPLACE INTO CMCDV VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        [(sv(d['CMCDKY']),sv(d.get('CMCDVL')),sv(d.get('CDESC1')),sv(d.get('CDESC2')),
          sv(d.get('USARG1')),sv(d.get('USARG2')),sv(d.get('USARG3')),
          sv(d.get('USARG4')),sv(d.get('USARG5')),
          sv(d.get('CREDAT')),sv(d.get('CRETIM')),sv(d.get('CREUSR')),
          sv(d.get('LMODAT')),sv(d.get('LMOTIM')),sv(d.get('LMOUSR')),
          sv(d.get('INDBZL')),sv(d.get('INDARC')),iv(d.get('UPDCHK'))) for d in data])
    conn.commit()

def load_skuma(conn, data):
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
    conn.commit()

def load_bzptn(conn, data):
    rows = []
    for d in data:
        rows.append((
            sv(d['PTNRKY']),sv(d.get('PTNRTY',' ')),sv(d.get('OWNRKY',' ')),
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
    conn.commit()

def load_measi(conn, data):
    conn.executemany("INSERT OR REPLACE INTO MEASI VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        [(sv(d['WAREKY']),sv(d['MEASKY']),sv(d.get('ITEMNO','1')),
          sv(d.get('UOMKEY')),nv(d.get('QTPUOM')),
          sv(d.get('INDDFU')),sv(d.get('DISREC')),sv(d.get('DISSHP')),sv(d.get('DISTAS')),
          nv(d.get('LENGTH')),nv(d.get('WIDTHW')),nv(d.get('HEIGHT')),
          nv(d.get('CUBICM')),nv(d.get('QTAUOM')),
          sv(d.get('CREDAT')),sv(d.get('CRETIM')),sv(d.get('CREUSR')),
          sv(d.get('LMODAT')),sv(d.get('LMOTIM')),sv(d.get('LMOUSR')),
          sv(d.get('INDBZL')),sv(d.get('INDARC')),iv(d.get('UPDCHK'))) for d in data])
    conn.commit()

def load_shpdh(conn, data):
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
    conn.commit()

def load_shpdi(conn, data):
    rows = []
    for d in data:
        rows.append((
            sv(d['SHPOKY']),sv(d['SHPOIT']),sv(d.get('STATIT','NEW')),sv(d.get('SKUKEY')),
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
            sv(d.get('PO_NO')),sv(d.get('CHGFLG')),sv(d.get('KEEPIT')),
            sv(d.get('STDLNR',' ')),sv(d.get('SVBELN',' '))
        ))
    ph = ','.join(['?']*71)
    conn.executemany(f"INSERT OR REPLACE INTO SHPDI VALUES({ph})", rows)
    conn.commit()


# ─────────────────────────────────────────────────────────────────────────────
# 메인
# ─────────────────────────────────────────────────────────────────────────────
def main():
    t_start = time.time()

    # ① 테이블 생성
    print("=" * 60)
    print("① 테이블 생성 중...")
    conn = get_conn()
    conn.executescript(DDL)
    conn.commit()
    print("  ✅ 테이블 생성 완료")

    # ② DS_VEHICLE 기준 데이터
    print("\n② DS_VEHICLE 기준 데이터 삽입...")
    conn.executemany("""INSERT OR REPLACE INTO DS_VEHICLE VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                     DS_VEHICLE_DATA)
    conn.commit()
    print(f"  ✅ DS_VEHICLE: {len(DS_VEHICLE_DATA)}건")

    # ② DS_INCH12 / DS_INCH3 기준 데이터 (DS_VEHICLE 기반 자동 생성)
    print("\n② DS_INCH12 / DS_INCH3 기준 데이터 삽입...")
    conn.execute("DELETE FROM DS_INCH12")
    conn.execute("DELETE FROM DS_INCH3")
    veh_rows = conn.execute(
        "SELECT CARTYPE,SORT_SEQ,INCH12_LT300,INCH12_GE300,INCH3_LT300,INCH3_GE300 FROM DS_VEHICLE ORDER BY SORT_SEQ"
    ).fetchall()
    inch12_data, inch3_data = [], []
    for r in veh_rows:
        inch12_data.append((r[0], 'LT300', r[2], r[1], today_str))
        inch12_data.append((r[0], 'GE300', r[3], r[1], today_str))
        inch3_data.append((r[0],  'LT300', r[4], r[1], today_str))
        inch3_data.append((r[0],  'GE300', r[5], r[1], today_str))
    conn.executemany("INSERT INTO DS_INCH12 VALUES(?,?,?,?,?)", inch12_data)
    conn.executemany("INSERT INTO DS_INCH3  VALUES(?,?,?,?,?)", inch3_data)
    conn.commit()
    print(f"  ✅ DS_INCH12: {len(inch12_data)}건, DS_INCH3: {len(inch3_data)}건")

    # ③ DS_DISPATCH_OBJECTIVE / PROFILE / CONST / CONST_SET 기본 데이터
    print("\n③ 배차 프로파일 / 목적식 / 제약조건 기본 데이터 삽입...")

    # 목적식 (없을 때만)
    if conn.execute("SELECT COUNT(*) FROM DS_DISPATCH_OBJECTIVE").fetchone()[0] == 0:
        objs = [
            ('MIN_VEHICLES', '차량 최소화',  '🚛', 'FFD BinPacking', '가능한 적은 차량으로 최대 적재 (FFD 알고리즘)', 10),
            ('MAX_FILL',     '적재율 최대화', '📊', 'BFD BinPacking', '각 차량을 가장 꽉 채우는 방식 (BFD 알고리즘)', 20),
            ('MIN_COST',     '운송비 최소화', '💰', 'ROUTE_COST',    'ROUTE_COST 기반 최저비용 차종 선택',           30),
        ]
        for code, nm, icon, algo, desc, seq in objs:
            conn.execute(
                "INSERT INTO DS_DISPATCH_OBJECTIVE"
                " (OBJ_CODE,OBJ_NM,OBJ_ICON,OBJ_ALGO,OBJ_DESC,SORT_SEQ,ACTIVE_YN,CREDAT,LMODAT)"
                " VALUES (?,?,?,?,?,?,'Y',?,?)",
                (code, nm, icon, algo, desc, seq, today_str, today_str)
            )
        conn.commit()

    # 프로파일 (없을 때만)
    if conn.execute("SELECT COUNT(*) FROM DS_DISPATCH_PROFILE").fetchone()[0] == 0:
        profiles = [
            (1, '차량최소화 기본', 'MIN_VEHICLES', 'Y', '차량 수를 최소화합니다 (FFD BinPacking 기본)'),
            (2, '적재율최대화',   'MAX_FILL',     'N', '각 차량의 적재율을 최대화합니다'),
            (3, '운송비최소화',   'MIN_COST',     'N', 'ROUTE_COST 기준 총 운송비를 최소화합니다'),
        ]
        for pid, nm, obj, active, note in profiles:
            conn.execute(
                "INSERT INTO DS_DISPATCH_PROFILE"
                " (PROFILE_ID,PROFILE_NM,OBJECTIVE,ACTIVE_YN,NOTE,CREDAT,LMODAT)"
                " VALUES (?,?,?,?,?,?,?)",
                (pid, nm, obj, active, note, today_str, today_str)
            )
        conn.commit()

    # 제약조건 (없을 때만)
    if conn.execute("SELECT COUNT(*) FROM DS_DISPATCH_CONST").fetchone()[0] == 0:
        # GLOBAL / CARGO / COST / ROLL / BOARD / MIX 고정 제약조건
        # 엑셀 「TMS 배차최적화 제약조건_20260611.xlsx」 §1~§4 기반
        consts = [
            # (PROFILE_ID, CONST_TYPE, CONST_KEY, CONST_VALUE, CONST_OP, TARGET_ID, TARGET_NM, NOTE, SORT_SEQ)
            # ── 전역 제약 (GLOBAL) ──
            (1,'GLOBAL','MAX_VEHICLES_PER_GROUP','99', '<=','','','그룹당 최대 배차 차량 수',10),
            (1,'GLOBAL','ALLOW_SPLIT_ITEM',      'Y',  '=','','','단일 아이템 납품분할 허용',20),
            (1,'GLOBAL','ALLOW_MIXED_LOAD',      'N',  '=','','','우편번호 앞 3자리 동일 납품처 혼적 허용',25),
            (1,'GLOBAL','MIN_FILL_RATIO',        '0',  '>=','','','최소 적재율(%) — 0=제한없음',30),
            (1,'GLOBAL','MAX_FILL_RATIO',        '100','<=','','','최대 적재율(%) — 초과배차 방지',40),
            # ── 화물·적재 제약 (CARGO) ──
            (1,'CARGO','MAX_ROLL_STACK_TIER',    '2',  '<=','','','최대 롤 적재 단수',200),
            (1,'CARGO','MAX_BOARD_HEIGHT_M',     '2.4','<=','','','판지 최대 적재 높이(m)',210),
            (1,'CARGO','ROLL_SINGLE_KG_FALLBACK','600','=', '','','롤 단중 미등록 시 fallback(kg)',220),
            # ── 비용 제약 (COST) ──
            (1,'COST','COST_REF_DATE',           'TODAY','=','','','운송비 기준일 (TODAY or YYYYMMDD)',300),
            (1,'COST','COST_PENALTY_OVER',       '1.5', '=','','','초과적재 비용 패널티 배수',310),
            # ── 원지① 단위 배차 (ROLL_UNIT) ── 엑셀§2-1
            (1,'ROLL_UNIT','ROLL_INTEGER_ONLY',  'Y',  '=','','','원지 정수 롤 단위 강제 (분할 절대 불가)',1000),
            (1,'ROLL_UNIT','ROLL_SPLIT_ALLOWED', 'N',  '=','','','원지 분할 배차 금지 (엑셀§2-1)',1010),
            (1,'ROLL_UNIT','ROLL_MIN_QTY',       '1',  '>=','','','원지 최소 배차 수량 (1롤)',1020),
            # ── 원지② 다단 적재·높이 (ROLL_STACK) ── 엑셀§2-2
            (1,'ROLL_STACK','ROLL_MAX_TIER',         '3',   '<=','','','롤 최대 적재 단수 Hard Cap (3단)',1100),
            (1,'ROLL_STACK','ROLL_PALLET_DEDUCT_M',  '0.15','=', '','','파레트 높이 차감값 (m) — 0.15m 기본',1110),
            (1,'ROLL_STACK','ROLL_PALLET_APPLY_YN',  'Y',   '=', '','','파레트 차감 적용 여부 (납품처 FORKLIFT_YN=N 시 적용)',1120),
            (1,'ROLL_STACK','ROLL_HEIGHT_MARGIN_M',  '0',   '=', '','','적재 높이 안전 여유 마진 (m)',1130),
            # ── 원지③ 인치·평량 혼합 (ROLL_INCH_MIX) ── 엑셀§2-3
            (1,'ROLL_INCH_MIX','ROLL_INCH_MIX_ALLOW', 'Y',            '=','','','인치/평량 혼합 오더 허용 (엑셀§2-3)',1200),
            (1,'ROLL_INCH_MIX','ROLL_2D_PACK_ENGINE', 'Y',            '=','','','혼합 규격 시 2D 바닥 패킹 연산 적용',1210),
            (1,'ROLL_INCH_MIX','ROLL_SAME_INCH_FORCE','N',            '=','','','동일 인치 강제 여부 (N=혼재허용)',1220),
            (1,'ROLL_INCH_MIX','ROLL_REF_12INCH_LT300','2,3,4,6,8,8,8',  '=','','','12인치/평량300미만 차종별 1단 기준수(1.4t~18t)',1230),
            (1,'ROLL_INCH_MIX','ROLL_REF_12INCH_GE300','2,3,4,5,7,7,7',  '=','','','12인치/평량300이상 차종별 1단 기준수(1.4t~18t)',1240),
            (1,'ROLL_INCH_MIX','ROLL_REF_3INCH_LT300', '3,5,10,12,14,14,15','=','','','3인치/평량300미만 차종별 1단 기준수(1.4t~18t)',1250),
            (1,'ROLL_INCH_MIX','ROLL_REF_3INCH_GE300', '3,5,10,12,14,14,15','=','','','3인치/평량300이상 차종별 1단 기준수(1.4t~18t)',1260),
            # ── 원지④ 3D 물리 검증 (ROLL_3D_VERIFY) ── 엑셀§2-4
            (1,'ROLL_3D_VERIFY','ROLL_3D_CHECK_YN',       'Y',    '=', '','','원지 3D 블록 검증 활성 (Dead Space 포함)',1300),
            (1,'ROLL_3D_VERIFY','ROLL_3D_DEAD_SPACE_PCT', '0',    '<=','','','Dead Space 허용 비율 (%) — 0=허용안함',1310),
            (1,'ROLL_3D_VERIFY','ROLL_OVERSIZE_ACTION',   'SPLIT','=', '','','치수 초과 시 처리 방식 (SPLIT=분할/REJECT=제외)',1320),
            # ── 판지① CBM·중량 이중 검증 (BOARD_CBM_WEIGHT) ── 엑셀§3-1
            (1,'BOARD_CBM_WEIGHT','BOARD_CBM_CHECK_YN',  'Y',  '=', '','','판지 CBM 자동계산+Double-Threshold 검증 활성 (엑셀§3-1)',1400),
            (1,'BOARD_CBM_WEIGHT','BOARD_MAX_CBM_RATIO', '100','<=','','','가용 적재함 CBM 상한 (%)',1410),
            (1,'BOARD_CBM_WEIGHT','BOARD_MAX_TON_RATIO', '100','<=','','','중량 상한 (%) — Double-Threshold 중량 기준',1420),
            (1,'BOARD_CBM_WEIGHT','BOARD_DUAL_THRESHOLD','Y',  '=', '','','중량+CBM 동시 초과 이중 임계치 검증 활성',1430),
            # ── 판지② 벌크·속포장 제약 (BOARD_BULK_SPLIT) ── 엑셀§3-2
            (1,'BOARD_BULK_SPLIT','BOARD_BULK_INTEGER_ONLY', 'Y',    '=','','','벌크 1PLT 단위 강제 (개체 내 분할 불가)',1500),
            (1,'BOARD_BULK_SPLIT','BOARD_INNER_SPLIT_ALLOW', 'Y',    '=','','','속포장 속 단위 분할 허용',1510),
            (1,'BOARD_BULK_SPLIT','BOARD_INNER_STACK_ALLOW', 'Y',    '=','','','분할된 속을 판지 위 추가 적재 허용',1520),
            (1,'BOARD_BULK_SPLIT','BOARD_INNER_OVERFLOW_ACT','SPLIT','=','','','속포장 초과 시 처리 (SPLIT=다음 차량 배정)',1530),
            # ── 판지③ 유연 분할 선적 (BOARD_FLEX_SPLIT) ── 엑셀§3-3
            (1,'BOARD_FLEX_SPLIT','BOARD_FLEX_SPLIT_YN', 'Y', '=','','','유연 분할 선적 활성 — 차량 한계 시 초과분만 후속 차량 (엑셀§3-3)',1600),
            (1,'BOARD_FLEX_SPLIT','BOARD_SPLIT_UNIT',    'EA','=','','','분할 단위 (EA=낱개속, PLT=파레트)',1610),
            (1,'BOARD_FLEX_SPLIT','BOARD_SPLIT_OVERFLOW','Y', '=','','','초과분(속단위) 후속 차량 정확 배정 활성',1620),
            # ── 판지④ 3D 물리 검증 (BOARD_3D_VERIFY) ── 엑셀§3-4
            (1,'BOARD_3D_VERIFY','BOARD_3D_CHECK_YN',       'Y',  '=', '','','판지 3D 블록 검증 활성 (Dead Space 포함)',1700),
            (1,'BOARD_3D_VERIFY','BOARD_3D_DEAD_SPACE_PCT', '0',  '<=','','','판지 Dead Space 허용 비율 (%)',1710),
            (1,'BOARD_3D_VERIFY','BOARD_HEIGHT_MAX_M',      '2.4','<=','','','판지 스택 최대 높이 (m, 기본 2.4m)',1720),
            # ── 혼적① Z축 수직 적재 순서 (MIX_Z_AXIS) ── 엑셀§4-1
            (1,'MIX_Z_AXIS','MIX_ROLL_BOTTOM_FORCE','Y','=','','','원지 하단·판지 상단 강제 고정 (물리적 압착 파손 방지 핵심)',1800),
            # ── 혼적② Y축 LIFO 배치 (MIX_Y_LIFO) ── 엑셀§4-2
            (1,'MIX_Y_LIFO','MIX_LIFO_ENABLE',  'Y','=','','','복수 납품처 LIFO 배치 활성 (나중하차→안쪽, 먼저하차→문쪽)',1900),
            (1,'MIX_Y_LIFO','MIX_ZONE_SPLIT_YN','Y','=','','','원지·판지 배송처 상이 시 전후 Zone 분할 적재 자동 전환',1910),
            # ── 혼적③ 이중 복합 검증 (MIX_DUAL_VERIFY) ── 엑셀§4-3
            (1,'MIX_DUAL_VERIFY','MIX_WEIGHT_CHECK_YN','Y','=','','','혼적 총중량 검증 (원지+판지 ≤ 차량 최대 적재 중량)',2000),
            (1,'MIX_DUAL_VERIFY','MIX_HEIGHT_CHECK_YN','Y','=','','','혼적 높이 검증 (파렛트고+원지다단높이+판지높이 ≤ 차량 최대 높이)',2010),
            (1,'MIX_DUAL_VERIFY','MIX_3D_CHECK_YN',    'Y','=','','','혼적 Dead Space 포함 3D 블록 종합 검증',2020),
            # ── 동적 제약 (DYNAMIC) ── 납품처별 DYNAMIC_YN + 동일 구역(AREA_CD) 그룹핑 규칙
            # 각 납품처의 동일 구(AREA_CD) 끼리 동적여부를 설정하는 제약조건
            (1,'DYNAMIC','DYNAMIC_GROUP_BY',      'AREA_CD','=', '','',
             '동적 그룹핑 기준 컬럼 — 동일 AREA_CD(권역) 납품처끼리 동적 배차 그룹 형성',2100),
            (1,'DYNAMIC','DYNAMIC_DEFAULT_YN',    'Y',      '=', '','',
             '납품처 DYNAMIC_YN 미설정 시 기본값 (Y=동적배차 가능, N=불가)',2110),
            (1,'DYNAMIC','DYNAMIC_MIN_GROUP_SIZE','1',      '>=','','',
             '동적 그룹 최소 납품처 수 — 1=단독 납품처도 동적배차 허용',2120),
            (1,'DYNAMIC','DYNAMIC_AREA_FORCE_YN', 'Y',      '=', '','',
             '동일 구역(AREA_CD) 내 DYNAMIC_YN=Y 납품처는 반드시 동적 그룹 편입 강제',2130),
        ]
        for row in consts:
            conn.execute(
                "INSERT INTO DS_DISPATCH_CONST"
                " (PROFILE_ID,CONST_TYPE,CONST_KEY,CONST_VALUE,CONST_OP,"
                "  TARGET_ID,TARGET_NM,ACTIVE_YN,NOTE,SORT_SEQ,CREDAT,LMODAT)"
                " VALUES (?,?,?,?,?,?,?,'Y',?,?,?,?)",
                (row[0],row[1],row[2],row[3],row[4],row[5],row[6],row[7],row[8],today_str,today_str)
            )
        conn.commit()

        # VEHICLE 제약조건: DS_VEHICLE 기준으로 동적 생성 (전체 16개 차종)
        veh_rows = conn.execute("SELECT CARCLASS_CD, CARTYPE, SORT_SEQ FROM DS_VEHICLE ORDER BY SORT_SEQ").fetchall()
        for vr in veh_rows:
            conn.execute(
                "INSERT INTO DS_DISPATCH_CONST"
                " (PROFILE_ID,CONST_TYPE,CONST_KEY,CONST_VALUE,CONST_OP,"
                "  TARGET_ID,TARGET_NM,ACTIVE_YN,NOTE,SORT_SEQ,CREDAT,LMODAT)"
                " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                (1, 'VEHICLE', 'ALLOW_CARTYPE', 'Y', '=',
                 vr[1], vr[1], 'Y', '차종 허용여부', 100 + vr[2], today_str, today_str)
            )
        conn.commit()


    if conn.execute("SELECT COUNT(*) FROM DS_DISPATCH_CONST_SET").fetchone()[0] == 0:
        cur = conn.execute(
            "INSERT INTO DS_DISPATCH_CONST_SET (SET_NM,SET_DESC,ACTIVE_YN,CREDAT,LMODAT) VALUES (?,?,?,?,?)",
            ('기본 제약조건 세트', '기본 배차 제약조건 모음 (글로벌/차량/화물/비용)', 'Y', today_str, today_str)
        )
        set_id = cur.lastrowid
        all_cids = conn.execute("SELECT CONST_ID FROM DS_DISPATCH_CONST ORDER BY CONST_ID").fetchall()
        for c in all_cids:
            conn.execute(
                "INSERT INTO DS_DISPATCH_CONST_SET_ITEM (SET_ID,CONST_ID,ACTIVE_YN,PARAM_VALUE)"
                " VALUES (?,?,?,?)",
                (set_id, c[0], 'Y', None)
            )
        # 활성 프로파일에 세트 연결
        conn.execute(
            "UPDATE DS_DISPATCH_PROFILE SET SET_ID=? WHERE ACTIVE_YN='Y'",
            (set_id,)
        )
        conn.commit()
        print(f"  ✅ 기본 제약조건 세트 생성 (SET_ID={set_id}, {len(all_cids)}개 항목)")
    else:
        print("  (기존 프로파일/제약조건 데이터 유지)")

    # ④ xlsx 데이터 로드
    xlsx_tables = [
        ('CMCDM',  'CMCDM_데이터.xlsx',  load_cmcdm),
        ('CMCDV',  'CMCDV_데이터.xlsx',  load_cmcdv),
        ('SKUMA',  'SKUMA_데이터.xlsx',  load_skuma),
        ('BZPTN',  'BZPTN_데이터.xlsx',  load_bzptn),
        ('MEASI',  'MEASI_데이터.xlsx',  load_measi),
        ('SHPDH',  'SHPDH_데이터.xlsx',  load_shpdh),
        ('SHPDI',  'SHPDI_데이터.xlsx',  load_shpdi),
    ]

    for tbl_name, fname, loader in xlsx_tables:
        print(f"\n③ [{tbl_name}] 로드 중...")
        t0 = time.time()
        data = read_xlsx(fname)
        loader(conn, data)
        cnt = conn.execute(f"SELECT COUNT(*) FROM {tbl_name}").fetchone()[0]
        print(f"  ✅ {tbl_name}: {cnt:,}건 ({time.time()-t0:.1f}s)")

    # ④ DOC_FOLDER 기본 폴더 초기화
    print("\n④ DOC_FOLDER 기본 폴더 설정...")
    today = datetime.date.today().strftime('%Y%m%d')
    now   = datetime.datetime.now().strftime('%H%M%S')
    exists = conn.execute("SELECT 1 FROM DOC_FOLDER WHERE FOLDER_ID='FOLDER_PS_LOGBOOK'").fetchone()
    if not exists:
        conn.execute("""INSERT INTO DOC_FOLDER(FOLDER_ID,FOLDER_NM,PARENT_ID,SORT_ORD,SYSTEM_YN,CRTUSR,CRTDAT,CRTTIM,DEL_YN)
                        VALUES('FOLDER_PS_LOGBOOK','PS운행일지',NULL,1,'Y','SYSTEM',?,?,'N')""", (today, now))
        conn.commit()
        print("  ✅ PS운행일지 폴더 생성")
    else:
        print("  (기존 폴더 유지)")

    # ⑤ 최종 통계
    print("\n" + "=" * 60)
    print("📊 최종 행 수:")
    all_tables = ['CMCDM','CMCDV','SKUMA','BZPTN','MEASI','SHPDH','SHPDI',
                  'DS_VEHICLE','BZPTN_DETAIL','VHCMA','ROUTE_COST',
                  'PS_DISPATCH_H','PS_DISPATCH_D','PS_SAP_STK']
    for t in all_tables:
        try:
            c = conn.execute(f"SELECT COUNT(*) FROM {t}").fetchone()[0]
            print(f"  {t:<20}: {c:>8,}")
        except Exception as e:
            print(f"  {t:<20}: ERROR - {e}")

    conn.close()
    total = time.time() - t_start
    print(f"\n✅ 전체 완료! 소요시간: {total:.1f}초")

if __name__ == '__main__':
    main()
