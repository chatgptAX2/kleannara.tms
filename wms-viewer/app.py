import sqlite3, json, math
from flask import Flask, jsonify, request, send_from_directory

app = Flask(__name__, static_folder='static')
DB_PATH = "/home/user/webapp/wms-viewer/wms.db"

TABLE_META = {
    "CMCDM":        {"desc":"공통코드 헤더",        "pk":["CMCDKY"],                               "group":"WMS 마스터"},
    "CMCDV":        {"desc":"공통코드 아이템",       "pk":["CMCDKY","CMCDVL"],                      "group":"WMS 마스터"},
    "WAHMA":        {"desc":"물류센터(Warehouse)",   "pk":["WAREKY"],                               "group":"WMS 마스터"},
    "SKUMA":        {"desc":"상품(SKU) 마스터",      "pk":["OWNRKY","SKUKEY"],                      "group":"WMS 마스터"},
    "BZPTN":        {"desc":"거래처",               "pk":["PTNRKY","PTNRTY","OWNRKY"],             "group":"WMS 마스터"},
    "MEASI":        {"desc":"단위구성 아이템",        "pk":["WAREKY","MEASKY","ITEMNO"],             "group":"WMS 마스터"},
    "SHPDH":        {"desc":"출고문서 헤더",         "pk":["SHPOKY"],                               "group":"WMS 출고"},
    "SHPDI":        {"desc":"출고문서 아이템",        "pk":["SHPOKY","SHPOIT"],                      "group":"WMS 출고"},
    "IFWMS113":     {"desc":"ERP→WMS 출고 오더",    "pk":["MANDT","SEQNO","VBELN","POSNR"],        "group":"WMS 인터페이스"},
    "BZPTN_DETAIL": {"desc":"납품처 상세정보(TMS)",  "pk":["PTNRKY","PTNRTY","OWNRKY","WAREKY"],   "group":"TMS 배차"},
    "VHCMA":        {"desc":"차량 마스터(TMS)",      "pk":["VEHICLE_NO","OWNRKY"],                  "group":"TMS 배차"},
    "ROUTE_COST":   {"desc":"운송경로별 비용(TMS)",  "pk":["ROUTE","PTNRKY","CARCLASS"],           "group":"TMS 배차"},
}

COL_LABELS = {
    # 공통
    "CREDAT":"생성일자","CRETIM":"생성시각","CREUSR":"생성자",
    "LMODAT":"수정일자","LMOTIM":"수정시각","LMOUSR":"수정자",
    "INDBZL":"비즈니스로직","INDARC":"아카이브","UPDCHK":"업데이트체크",
    # CMCDM
    "CMCDKY":"코드키","SHORTX":"코드명","DBFILD":"DB필드명",
    "USARL1":"인자레이블1","USARL2":"인자레이블2","USARL3":"인자레이블3",
    "USARL4":"인자레이블4","USARL5":"인자레이블5","SYONLY":"시스템전용",
    # CMCDV
    "CMCDVL":"코드값","CDESC1":"설명1","CDESC2":"설명2",
    "USARG1":"사용인자1","USARG2":"사용인자2","USARG3":"사용인자3",
    "USARG4":"사용인자4","USARG5":"사용인자5",
    # SKUMA
    "OWNRKY":"Owner키","SKUKEY":"품목코드","DELMAK":"삭제표시",
    "DESC01":"품목명","DESC02":"규격","DESC03":"설명3","DESC04":"설명4",
    "VENDKY":"공급업체코드",
    "ASKU01":"대체코드1","ASKU02":"대체코드2","ASKU03":"대체코드3",
    "ASKU04":"대체코드4","ASKU05":"대체코드5",
    "ASKL01":"대체레이블1","ASKL02":"대체레이블2","ASKL03":"대체레이블3",
    "ASKL04":"대체레이블4","ASKL05":"대체레이블5",
    "EANCOD":"EAN코드","GTINCD":"GTIN코드",
    "SKUG01":"그룹1","SKUG02":"그룹2","SKUG03":"그룹3","SKUG04":"그룹4","SKUG05":"그룹5",
    "SKUL01":"그룹레이블1","SKUL02":"그룹레이블2","SKUL03":"그룹레이블3",
    "SKUL04":"그룹레이블4","SKUL05":"그룹레이블5",
    "GRSWGT":"총중량","NETWGT":"순중량","WGTUNT":"중량단위",
    "LENGTH":"길이","WIDTHW":"폭","HEIGHT":"높이","CUBICM":"부피(CBM)","CAPACT":"용량",
    "DUOMKY":"기본단위","QTDUOM":"기본단위수량","ABCANV":"폐기여부",
    "LOTL01":"LOT레이블1","LOTL02":"LOT레이블2","LOTL03":"LOT레이블3",
    "LOTL04":"LOT레이블4","LOTL05":"LOT레이블5","LOTL06":"LOT레이블6",
    "LOTL07":"LOT레이블7","LOTL08":"LOT레이블8","LOTL09":"LOT레이블9",
    "LOTL10":"LOT레이블10","LOTL11":"LOT레이블11","LOTL12":"LOT레이블12",
    "LOTL13":"LOT레이블13","LOTL14":"LOT레이블14","LOTL15":"LOT레이블15",
    "LOTL16":"LOT레이블16","LOTL17":"LOT레이블17","LOTL18":"LOT레이블18",
    "LOTL19":"LOT레이블19","LOTL20":"LOT레이블20",
    "OUTDMT":"외경","RIMDMT":"림경","INNDPT":"내경","SECTWD":"단면폭",
    "WEIGHT":"중량","DLGORT":"보관장소","BATMNG":"배치관리","LGPRO":"물류처리",
    "CSTDAT":"비용일자","CPSKUG":"복합코드",
    "QTYMON":"월수량","QTYSTD":"박스입수","QTYCNT":"계수수량","BUFMNG":"버퍼관리",
    "MTYPE":"품목구분",
    # BZPTN
    "PTNRKY":"거래처코드","PTNRTY":"거래처유형","NAME01":"거래처명",
    "NAME02":"거래처명2","NAME03":"거래처명3",
    "ADDR01":"주소1","ADDR02":"주소2","ADDR03":"주소3","ADDR04":"주소4","ADDR05":"주소5",
    "CITY01":"도시","REGN01":"지역","POSTCD":"우편번호","NATNKY":"국가코드",
    "TELN01":"전화1","TELN02":"전화2","TELN03":"전화3",
    "FAXTL1":"팩스1","FAXTL2":"팩스2",
    "TAXCD1":"세금코드1","TAXCD2":"세금코드2","VATREG":"부가세등록번호",
    "POBOX1":"사서함1","POBPC1":"사서함우편1",
    "EMAIL1":"이메일1","EMAIL2":"이메일2",
    "CTTN01":"연락처명","CTTT01":"연락처전화1","CTTT02":"연락처전화2","CTTM01":"연락처모바일",
    "SALN01":"영업담당자","SALT01":"영업담당전화1","SALT02":"영업담당전화2","SALM01":"영업담당모바일",
    "EXPTNK":"만료키","CUSTMR":"고객구분",
    "PTNG01":"파트너그룹1","PTNG02":"파트너그룹2","PTNG03":"파트너그룹3",
    "PTNG04":"파트너그룹4","PTNG05":"파트너그룹5",
    "PTNL01":"파트너레이블1","PTNL02":"파트너레이블2","PTNL03":"파트너레이블3",
    "PTNL04":"파트너레이블4","PTNL05":"파트너레이블5",
    "WTOPPM":"최대용량","WTOPMU":"최대단위","WTOPDV":"용량비율","PROCHA":"프로세스채널",
    "DEPTID":"부서ID","DNAME":"부서명","USRID1":"사용자ID1",
    "LOTCD":"LOT코드","LOTCD2":"LOT코드2",
    # MEASI
    "WAREKY":"창고코드","MEASKY":"단위구성키","ITEMNO":"아이템번호","UOMKEY":"단위",
    "QTPUOM":"단위수량","INDDFU":"기본단위여부","DISREC":"입고표시",
    "DISSHP":"출고표시","DISTAS":"작업표시","QTAUOM":"환산수량",
    # SHPDH
    "SHPOKY":"출고번호","SHPMTY":"출고유형","ALSTKY":"할당전략",
    "STATDO":"문서상태","DOCDAT":"문서일자","DOCCAT":"문서분류","PRORTY":"우선순위","DOCUTY":"문서유형",
    "DRELIN":"해제일자","RQSHPD":"요청출고일","RQARRD":"요청도착일","RQARRT":"요청도착시각",
    "LSHPCD":"최종출고코드","DPTNKY":"납품처","PTRCVR":"판매처",
    "PGRC01":"권역","PGRC02":"운송유형","PGRC03":"운송그룹3","PGRC04":"운송사","PGRC05":"운송그룹5",
    "VEHINO":"차량명","DRIVER":"기사명","ESHPKY":"외부출고키","OPURKY":"오픈구매키",
    "LOCADT":"배차일","LOCADK":"배차키","INDDCL":"마감표시","RSNCOD":"사유코드","RSNRET":"반품사유",
    "QTSHPO":"출고지시수량","QTYREF":"참조수량","QTAPPO":"예약수량",
    "QTALOC":"할당수량","QTJCMP":"JIT완료수량","QTSHPD":"출고완료수량","QTSHPC":"취소수량",
    "UNAME1":"사용자명1","DEPTID1":"부서ID1","DNAME1":"부서명1",
    "USRID2":"사용자ID2","UNAME2":"사용자명2","DEPTID2":"부서ID2","DNAME2":"부서명2",
    "USRID3":"사용자ID3","UNAME3":"사용자명3","DEPTID3":"부서ID3","DNAME3":"부서명3",
    "USRID4":"사용자ID4","UNAME4":"사용자명4","DEPTID4":"부서ID4","DNAME4":"부서명4",
    "DOCTXT":"문서텍스트","KEEPTS":"보관타임스탬프",
    "CARNO":"차량번호","CARTON":"차량톤수","LOADTON":"적재톤수","CARART":"차량종류",
    "LOADVOLUME":"적재부피","DRIVERCEL":"기사연락처",
    "DTDIS":"거리","UZDIS":"거리단위","SIGNI":"서명","EXTI2":"외부ID2","SDABW":"납기편차",
    "SAP1":"SAP필드1","SAP2":"SAP필드2","SAP12":"SAP필드12","SAP13":"SAP필드13",
    "TDOBJECT":"텍스트오브젝트","VTXTK":"차량텍스트키","IFID":"인터페이스ID",
    "RSNTXT":"사유텍스트","KVGR1":"고객그룹1","KVGR2":"고객그룹2","KVGR3":"고객그룹3",
    "LFART":"배송유형","ZLGORT":"창고위치","ZKUNNR3":"고객코드3","PRTCHK":"출력확인",
    "VTWEG":"유통경로","TDLNR_NM":"운송사명","TDLNR":"운송사코드",
    # SHPDI
    "SHPOIT":"출고아이템","STATIT":"아이템상태","QTYORG":"원주문수량",
    "QTYUOM":"단위수량","MEASKY":"단위구성키","QTPUOM":"단위수량","DUOMKY":"기본단위",
    "QTDUOM":"기본단위수량","SASTKY":"작업전략","TKFLKY":"작업흐름키",
    "ESHPKY":"외부출고키","ESHPIT":"외부출고아이템","OPURKY":"오픈구매키",
    "REFDKY":"참조문서키","REFDIT":"참조문서아이템","REFCAT":"참조분류","REFDAT":"참조일자",
    "EXSUBS":"대체품여부","PROCHA":"프로세스채널","AREAKY":"구역키",
    "SLAND1":"납품국가","SBKTXT":"특이사항",
    "PO_NO":"PO번호","CHGFLG":"변경플래그","KEEPIT":"보관아이템",
    # IFWMS113
    "MANDT":"클라이언트","SEQNO":"시퀀스번호","VBELN":"납품번호","POSNR":"포지션번호",
    "BWART":"이동유형","PSTYV":"품목범주","ZLIKP_ERDAT":"생성일","ZLIKP_ERZET":"생성시각",
    "ZLIKP_AEDAT":"변경일","VSTEL":"출하지점","LFART":"배송유형","WADAT":"출고예정일",
    "KUNNR":"거래처","KUNAG":"주문처","WERKS":"플랜트","LGORT":"저장위치","MATNR":"자재번호",
    "LFIMG":"납품수량","QTSHP":"오더수량","MEINS":"단위",
    "NETPR":"단가","NETWR":"금액","MWSBP":"세금액","MWSDC":"할인액","WAERK":"통화",
    "BWTAR":"평가유형","VGBEL":"선행문서","VGPOS":"선행문서포지션","VGDAT":"선행문서일",
    "STKNUM":"재고번호","SDATBG":"납기시작일","STATUS":"오더상태","IFFLG":"IF플래그",
    "RETRY":"재시도","ERCOD":"오류코드","ERTXT":"오류텍스트",
    "CUSRID":"고객사용자ID","CUNAME":"고객명","CPSTLZ":"우편번호","LAND1":"국가",
    "TELF1":"전화1","TELE2":"전화2","SMTP_ADDR":"이메일","KUKLA":"고객분류",
    "VTEXT":"유통경로텍스트","ADDR":"주소","CNAME":"연락처명","CPHON":"연락처전화",
    "BNAME":"청구처명","BPHON":"청구처전화",
    "C00101":"사용자필드1","C00102":"사용자필드2","C00103":"사용자필드3",
    "C00104":"사용자필드4","C00105":"사용자필드5","C00106":"사용자필드6",
    "C00107":"사용자필드7","C00108":"사용자필드8","C00109":"사용자필드9","C00110":"사용자필드10",
    "N00101":"숫자필드1","N00102":"숫자필드2","N00103":"숫자필드3",
    "N00104":"숫자필드4","N00105":"숫자필드5",
    "ORDCAT":"오더분류","PTNRTY":"파트너유형","SBKTXT":"특이사항",
    "LOTA01":"LOT속성1","LOTA02":"LOT속성2","LOTA03":"LOT속성3","LOTA04":"LOT속성4",
    "LOTA05":"LOT속성5","LOTA06":"LOT속성6","LOTA07":"LOT속성7","LOTA08":"LOT속성8",
    "LOTA09":"LOT속성9","LOTA10":"LOT속성10","LOTA11":"LOT속성11","LOTA12":"LOT속성12",
    "LOTA13":"LOT속성13","LOTA14":"LOT속성14","LOTA15":"LOT속성15","LOTA16":"LOT속성16",
    "LOTA17":"LOT속성17","LOTA18":"LOT속성18","LOTA19":"LOT속성19","LOTA20":"LOT속성20",
    "LOADTON":"적재톤수","LOADVOLUME":"적재부피","DRIVERCEL":"기사연락처",
    "ITFCNT":"IF처리횟수","ITFFLG":"IF처리플래그","ITFETX":"IF오류텍스트",
    "ITFDAT":"IF처리일","ITFTIM":"IF처리시각",
    "PRCCNT":"처리횟수","PRCFLG":"처리플래그","PRCETX":"처리오류텍스트",
    "PRCDAT":"처리일","PRCTIM":"처리시각","CDATE":"인터페이스일","TDATE":"전송일",
    "IFID":"인터페이스ID","BWARTSAP":"SAP이동유형","LOEKZ":"삭제플래그",
    "LOGSEQNO":"로그시퀀스","VTXTK":"차량텍스트키",
    "SOBKZ":"특수재고","AUGRU":"오더사유","STOKKY":"재고키","LOTNUM":"LOT번호",
    "PO_REV":"PO개정번호","PO_LNO":"PO라인번호",
    "TLOTA01":"타겟LOT1","TLOTA02":"타겟LOT2",
    "VBELV":"선행납품번호","POSNV":"선행포지션","ZKUNNR3":"고객코드3",
    "VOLEH":"부피단위","VTWEG":"유통경로",
    # ── BZPTN_DETAIL (TMS 납품처 상세)
    "ROUTE_CD":"유통경로코드","ITEM_GROUP":"제품군","UNLOAD_TIME":"하차대기시간(분)",
    "INB_TIME_FROM1":"입차가능시작1","INB_TIME_TO1":"입차가능종료1",
    "AREA_CD":"권역","MAX_HEIGHT":"최대진입높이(m)","FORKLIFT_YN":"지게차여부",
    "HANDWORK_YN":"수작업여부","AUTO_PLT":"자동설정PLT","MAX_BOX_QTY":"최대토수",
    "AUTO_ALLOC_YN":"자동배차여부","SINGLE_ITEM_YN":"단수조정품목","NY_TYPE":"N/Y타입",
    "SINGLE_HEIGHT":"단수조정높이","DYNAMIC_YN":"동적유무","LTL_YN":"혼적유무",
    "PRIORITY_YN":"우선배차유무","MIN_QTSIWH":"최소납품수량",
    "LATITUDE":"위도(GPS)","LONGITUDE":"경도(GPS)","DEL_YN":"삭제여부",
    # ── VHCMA (TMS 차량 마스터)
    # ── WAHMA (물류센터)
    "WAREKY":"창고코드","COMPKY":"회사코드","TSPKEY":"태스크분할키",
    "CHKSHA":"출하지역확인","WADN01":"창고담당자명","WADT01":"담당자전화1",
    "WADT02":"담당자전화2","WADM01":"담당자이메일","EXCOMK":"외부회사키",
    "INDOVA":"과배분허용","PLOCOV":"과배분기본위치","INDUAC":"미배분처리여부",
    "DSORKY":"기본정렬키","DRECLO":"기본입고위치",
    "WHTT01":"위도","WHTT02":"경도",
    # ── VHCMA (TMS 차량 마스터)
    "VEHICLE_NO":"차량번호","SHIP_POINT":"출하지점","PRODUCT_GROUP":"제품군",
    # ── ROUTE_COST (TMS 운송경로별 비용)
    "SHPPT":"운송계획지점","ROUTE":"운송경로","CARCLASS":"차량톤수",
    "COST":"비용","UNIT":"통화단위","DATE_START":"효력시작일","DATE_END":"효력종료일",
    "DELIVERY_ZONE":"배송구역","CARRIER":"운송사","VEHICLE_TYPE":"차량구분",
    "VEHICLE_KIND":"차종","VEHICLE_CLASS":"차형","DRIVER_NAME":"기사명",
    "CONTACT_NO":"연락처","AXLE_TYPE":"축형식","LOAD_VOLUME":"적재용적(㎤)",
    "LOAD_WEIGHT":"적재중량(kg)","PALLET_QTY":"적재파렛트수",
    "CARGO_LENGTH":"적재함길이(m)","CARGO_WIDTH":"적재함너비(m)","CARGO_HEIGHT":"적재함높이(m)",
    "FLOOR_TYPE":"바닥형태","USE_YN":"사용여부","OPERABLE_YN":"운행가능여부",
    "DLV_TIME_FROM":"배송가능시작","DLV_TIME_TO":"배송가능종료","VEHICLE_YEAR":"차량연식",
    "DELIVERY_CUSTOMER_1":"배송납품처1","DELIVERY_CUSTOMER_2":"배송납품처2",
    "FIX_YN":"고정차량",
}

# 각 테이블의 전체 컬럼 목록을 DB에서 읽어 캐싱
_TABLE_COLS_CACHE = {}

def get_conn():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def get_table_cols(table):
    """DB PRAGMA로 테이블 전체 컬럼 목록 반환 (캐싱)"""
    if table not in _TABLE_COLS_CACHE:
        conn = sqlite3.connect(DB_PATH)
        rows = conn.execute(f"PRAGMA table_info({table})").fetchall()
        conn.close()
        _TABLE_COLS_CACHE[table] = [r[1] for r in rows]
    return _TABLE_COLS_CACHE[table]

def fmt_date(v):
    if not v or str(v).strip() in ('', ' '): return ''
    s = str(v).strip()
    if len(s)==8 and s.isdigit():
        return f"{s[:4]}-{s[4:6]}-{s[6:]}"
    return s

@app.route('/')
def index():
    return send_from_directory('static', 'index.html')

@app.route('/api/tables')
def api_tables():
    conn = get_conn()
    result = {}
    for tbl, meta in TABLE_META.items():
        cnt = conn.execute(f"SELECT COUNT(*) FROM {tbl}").fetchone()[0]
        col_count = len(get_table_cols(tbl))
        result[tbl] = {**meta, "count": cnt, "col_count": col_count}
    conn.close()
    return jsonify(result)

@app.route('/api/schema/<table>')
def api_schema(table):
    if table not in TABLE_META:
        return jsonify({"error":"Table not found"}), 404
    conn = get_conn()
    cols = conn.execute(f"PRAGMA table_info({table})").fetchall()
    conn.close()
    pk_cols = TABLE_META[table]["pk"]
    return jsonify([{
        "cid":c[0], "name":c[1], "type":c[2],
        "notnull":c[3], "default":c[4], "pk": (c[1] in pk_cols),
        "label": COL_LABELS.get(c[1], c[1])
    } for c in cols])

# ── 테이블별 검색 대상 컬럼 (q 파라미터 사용 시 이 컬럼들만 LIKE 검색) ──
TABLE_SEARCH_COLS = {
    "CMCDM":    ["CMCDKY", "SHORTX"],                      # 코드키, 코드명
    "CMCDV":    ["CMCDKY", "CDESC1"],                      # 코드키, 설명1(코드명)
    "SKUMA":    ["SKUKEY", "DESC01"],                      # 품목코드, 품목명
    "BZPTN":    ["PTNRKY", "NAME01"],                      # 거래처코드, 거래처명
    "MEASI":    ["MEASKY"],                                # 단위구성키
}

@app.route('/api/data/<table>')
def api_data(table):
    if table not in TABLE_META:
        return jsonify({"error":"Table not found"}), 404

    page       = int(request.args.get('page', 1))
    size       = int(request.args.get('size', 50))
    search     = request.args.get('q', '').strip()
    col_filter = request.args.get('col', '').strip()
    val_filter = request.args.get('val', '').strip()
    sort_col   = request.args.get('sort_col', '').strip()
    sort_dir   = request.args.get('sort_dir', 'ASC').strip().upper()
    if sort_dir not in ('ASC', 'DESC'):
        sort_dir = 'ASC'

    offset = (page - 1) * size

    # 전체 컬럼을 DB에서 읽어옴
    all_cols = get_table_cols(table)
    pk_cols  = TABLE_META[table]["pk"]

    # 정렬: 허용 컬럼(실제 테이블 컬럼) 화이트리스트 검사
    if sort_col and sort_col in all_cols:
        order_expr = f"{sort_col} {sort_dir}"
    else:
        order_expr = f"{pk_cols[0]} ASC"

    # 검색: 테이블별 지정 컬럼 우선, 없으면 PK + 앞쪽 컬럼 최대 8개
    search_cols = []
    if search:
        if table in TABLE_SEARCH_COLS:
            # 지정된 컬럼만 검색 (실제 존재하는 컬럼만)
            search_cols = [c for c in TABLE_SEARCH_COLS[table] if c in all_cols]
        else:
            seen = set()
            for c in pk_cols:
                if c in all_cols:
                    search_cols.append(c); seen.add(c)
            for c in all_cols:
                if len(search_cols) >= 8: break
                if c not in seen:
                    search_cols.append(c); seen.add(c)

    where_parts, params = [], []
    if search and search_cols:
        likes = " OR ".join([f"CAST({c} AS TEXT) LIKE ?" for c in search_cols])
        where_parts.append(f"({likes})")
        params.extend([f"%{search}%"] * len(search_cols))
    if col_filter and val_filter and col_filter in all_cols:
        where_parts.append(f"CAST({col_filter} AS TEXT) LIKE ?")
        params.append(f"%{val_filter}%")

    where_sql = f"WHERE {' AND '.join(where_parts)}" if where_parts else ""

    conn = get_conn()
    total = conn.execute(f"SELECT COUNT(*) FROM {table} {where_sql}", params).fetchone()[0]
    rows  = conn.execute(
        f"SELECT * FROM {table} {where_sql} ORDER BY {order_expr} LIMIT ? OFFSET ?",
        params + [size, offset]
    ).fetchall()
    conn.close()

    return jsonify({
        "table":    table,
        "total":    total,
        "page":     page,
        "size":     size,
        "pages":    math.ceil(total / size) if total else 1,
        "columns":  all_cols,
        "pk_cols":  pk_cols,
        "labels":   {c: COL_LABELS.get(c, c) for c in all_cols},
        "rows":     [dict(r) for r in rows],
        "sort_col": sort_col,
        "sort_dir": sort_dir,
        "search_cols": TABLE_SEARCH_COLS.get(table, []),  # 프론트에 전달
    })

@app.route('/api/detail/<table>')
def api_detail(table):
    if table not in TABLE_META:
        return jsonify({"error":"Table not found"}), 404
    pk_cols = TABLE_META[table]["pk"]
    where_parts, params = [], []
    for col in pk_cols:
        val = request.args.get(col)
        if val is None:
            return jsonify({"error": f"Missing PK: {col}"}), 400
        where_parts.append(f"{col} = ?")
        params.append(val)
    conn = get_conn()
    row = conn.execute(
        f"SELECT * FROM {table} WHERE {' AND '.join(where_parts)}", params
    ).fetchone()
    cols_info = conn.execute(f"PRAGMA table_info({table})").fetchall()
    conn.close()
    if not row:
        return jsonify({"error":"Not found"}), 404

    result = {}
    for col_info in cols_info:
        col_name = col_info[1]
        result[col_name] = {
            "value": row[col_name],
            "label": COL_LABELS.get(col_name, col_name),
            "type":  col_info[2],
            "is_pk": col_name in pk_cols
        }
    return jsonify(result)

@app.route('/api/sql', methods=['POST'])
def api_sql():
    import re, datetime
    body = request.get_json()
    sql  = (body or {}).get('sql', '').strip()
    if not sql:
        return jsonify({"error":"No SQL provided"}), 400

    first_word = sql.upper().lstrip().split()[0] if sql.strip() else ''

    # DML (INSERT / UPDATE / DELETE)
    if first_word in ('INSERT', 'UPDATE', 'DELETE'):
        try:
            conn = get_conn()
            cur  = conn.execute(sql)
            conn.commit()
            affected = cur.rowcount
            conn.close()
            return jsonify({"ok": True, "type": first_word, "affected": affected,
                            "message": f"{first_word} 완료 — {affected}행 영향"})
        except Exception as e:
            try: conn.close()
            except: pass
            return jsonify({"error": str(e)}), 400

    if first_word != 'SELECT':
        return jsonify({"error": "SELECT / INSERT / UPDATE / DELETE 만 허용됩니다"}), 403

    try:
        conn = get_conn()
        cur  = conn.execute(sql)
        cols = [d[0] for d in cur.description]
        rows = cur.fetchmany(500)
        conn.close()
        return jsonify({"columns": cols, "rows":[list(r) for r in rows], "count": len(rows)})
    except Exception as e:
        return jsonify({"error": str(e)}), 400


# ─────────────────────────────────────────────
#  범용 CRUD API  (/api/row/…)
# ─────────────────────────────────────────────

@app.route('/api/row/schema/<table>')
def api_row_schema(table):
    """테이블 컬럼 정보 + PK 반환"""
    if table not in TABLE_META:
        return jsonify({"error": "Table not found"}), 404
    conn = get_conn()
    cols = conn.execute(f"PRAGMA table_info({table})").fetchall()
    conn.close()
    pk_cols = TABLE_META[table]["pk"]
    return jsonify({
        "table":   table,
        "desc":    TABLE_META[table]["desc"],
        "pk_cols": pk_cols,
        "columns": [{
            "name":    c[1],
            "type":    c[2] or "TEXT",
            "notnull": bool(c[3]),
            "default": c[4],
            "pk":      c[1] in pk_cols,
            "label":   COL_LABELS.get(c[1], c[1])
        } for c in cols]
    })


@app.route('/api/row/get/<table>')
def api_row_get(table):
    """PK로 단일 행 조회"""
    if table not in TABLE_META:
        return jsonify({"error": "Table not found"}), 404
    pk_cols = TABLE_META[table]["pk"]
    where_parts, params = [], []
    for col in pk_cols:
        val = request.args.get(col)
        if val is None:
            return jsonify({"error": f"Missing PK: {col}"}), 400
        where_parts.append(f"{col} = ?")
        params.append(val)
    conn = get_conn()
    row = conn.execute(
        f"SELECT * FROM {table} WHERE {' AND '.join(where_parts)}", params
    ).fetchone()
    conn.close()
    if not row:
        return jsonify({"error": "Not found"}), 404
    return jsonify(dict(row))


@app.route('/api/row/insert/<table>', methods=['POST'])
def api_row_insert(table):
    """행 삽입"""
    import datetime
    if table not in TABLE_META:
        return jsonify({"error": "Table not found"}), 404
    body = request.get_json() or {}
    if not body:
        return jsonify({"error": "No data provided"}), 400

    # 타임스탬프 자동 주입
    now = datetime.datetime.now()
    nd, nt = now.strftime('%Y%m%d'), now.strftime('%H%M%S')
    for f, v in [('CREDAT', nd), ('CRETIM', nt), ('CREUSR', 'WEB'),
                 ('LMODAT', nd), ('LMOTIM', nt), ('LMOUSR', 'WEB')]:
        body.setdefault(f, v)

    all_cols = get_table_cols(table)
    insert_cols = [c for c in body if c in all_cols]
    if not insert_cols:
        return jsonify({"error": "삽입할 컬럼이 없습니다"}), 400

    vals = [body[c] for c in insert_cols]
    placeholders = ','.join(['?'] * len(insert_cols))
    try:
        conn = get_conn()
        conn.execute(
            f"INSERT INTO {table} ({','.join(insert_cols)}) VALUES ({placeholders})", vals
        )
        conn.commit()
        conn.close()
        return jsonify({"ok": True, "action": "created"})
    except Exception as e:
        try: conn.close()
        except: pass
        return jsonify({"error": str(e)}), 500


@app.route('/api/row/update/<table>', methods=['POST'])
def api_row_update(table):
    """행 수정 (PK 기반)"""
    import datetime
    if table not in TABLE_META:
        return jsonify({"error": "Table not found"}), 404
    body    = request.get_json() or {}
    pk_cols = TABLE_META[table]["pk"]

    # PK 값 추출
    pk_vals = {}
    for col in pk_cols:
        v = body.get(col)
        if v is None or str(v).strip() == '':
            return jsonify({"error": f"PK 필드 누락: {col}"}), 400
        pk_vals[col] = v

    all_cols = get_table_cols(table)
    now = datetime.datetime.now()
    nd, nt = now.strftime('%Y%m%d'), now.strftime('%H%M%S')
    body['LMODAT'] = nd; body['LMOTIM'] = nt; body['LMOUSR'] = 'WEB'

    set_cols = [c for c in body if c in all_cols and c not in pk_cols]
    if not set_cols:
        return jsonify({"error": "수정할 컬럼이 없습니다"}), 400

    set_sql  = ', '.join([f"{c}=?" for c in set_cols])
    set_vals = [body[c] for c in set_cols]
    where_sql = ' AND '.join([f"{c}=?" for c in pk_cols])
    where_vals = [pk_vals[c] for c in pk_cols]

    try:
        conn = get_conn()
        cur  = conn.execute(
            f"UPDATE {table} SET {set_sql} WHERE {where_sql}",
            set_vals + where_vals
        )
        conn.commit()
        affected = cur.rowcount
        conn.close()
        return jsonify({"ok": True, "action": "updated", "affected": affected})
    except Exception as e:
        try: conn.close()
        except: pass
        return jsonify({"error": str(e)}), 500


@app.route('/api/row/delete/<table>', methods=['POST'])
def api_row_delete(table):
    """행 삭제 (PK 기반 — 완전 삭제)"""
    if table not in TABLE_META:
        return jsonify({"error": "Table not found"}), 404
    body    = request.get_json() or {}
    pk_cols = TABLE_META[table]["pk"]

    pk_vals = {}
    for col in pk_cols:
        v = body.get(col)
        if v is None or str(v).strip() == '':
            return jsonify({"error": f"PK 필드 누락: {col}"}), 400
        pk_vals[col] = v

    where_sql  = ' AND '.join([f"{c}=?" for c in pk_cols])
    where_vals = [pk_vals[c] for c in pk_cols]

    try:
        conn = get_conn()
        cur  = conn.execute(f"DELETE FROM {table} WHERE {where_sql}", where_vals)
        conn.commit()
        affected = cur.rowcount
        conn.close()
        return jsonify({"ok": True, "action": "deleted", "affected": affected})
    except Exception as e:
        try: conn.close()
        except: pass
        return jsonify({"error": str(e)}), 500

@app.route('/api/stats')
def api_stats():
    conn = get_conn()
    stats = {}
    for tbl in TABLE_META:
        stats[tbl] = conn.execute(f"SELECT COUNT(*) FROM {tbl}").fetchone()[0]
    # WMS 통계
    try:
        rows = conn.execute("SELECT STATDO, COUNT(*) as cnt FROM SHPDH GROUP BY STATDO ORDER BY cnt DESC LIMIT 10").fetchall()
        stats['shpdh_statuses'] = [dict(r) for r in rows]
    except: pass
    try:
        rows = conn.execute("SELECT STATUS, COUNT(*) as cnt FROM IFWMS113 GROUP BY STATUS ORDER BY cnt DESC").fetchall()
        stats['ifwms113_status'] = [dict(r) for r in rows]
    except: pass
    try:
        rows = conn.execute("SELECT MTYPE, COUNT(*) as cnt FROM SKUMA WHERE MTYPE IS NOT NULL AND MTYPE != ' ' GROUP BY MTYPE ORDER BY cnt DESC").fetchall()
        stats['skuma_types'] = [dict(r) for r in rows]
    except: pass
    try:
        rows = conn.execute("SELECT PTNRTY, COUNT(*) as cnt FROM BZPTN GROUP BY PTNRTY ORDER BY cnt DESC LIMIT 5").fetchall()
        stats['bzptn_types'] = [dict(r) for r in rows]
    except: pass
    # TMS 통계
    try:
        rows = conn.execute("SELECT VEHICLE_TYPE, COUNT(*) as cnt FROM VHCMA WHERE VEHICLE_TYPE IS NOT NULL AND VEHICLE_TYPE != '' GROUP BY VEHICLE_TYPE ORDER BY cnt DESC").fetchall()
        stats['vhcma_types'] = [dict(r) for r in rows]
    except: pass
    try:
        rows = conn.execute("SELECT VEHICLE_CLASS, COUNT(*) as cnt FROM VHCMA WHERE VEHICLE_CLASS IS NOT NULL AND VEHICLE_CLASS != '' GROUP BY VEHICLE_CLASS ORDER BY cnt DESC").fetchall()
        stats['vhcma_classes'] = [dict(r) for r in rows]
    except: pass
    try:
        rows = conn.execute("SELECT AREA_CD, COUNT(*) as cnt FROM BZPTN_DETAIL WHERE AREA_CD IS NOT NULL AND AREA_CD != '' GROUP BY AREA_CD ORDER BY cnt DESC LIMIT 10").fetchall()
        stats['bzptn_detail_areas'] = [dict(r) for r in rows]
    except: pass
    try:
        rows = conn.execute("SELECT CARRIER, COUNT(*) as cnt FROM VHCMA WHERE CARRIER IS NOT NULL AND CARRIER != '' GROUP BY CARRIER ORDER BY cnt DESC LIMIT 10").fetchall()
        stats['vhcma_carriers'] = [dict(r) for r in rows]
    except: pass
    # ROUTE_COST 통계
    try:
        rows = conn.execute("SELECT CARCLASS, COUNT(*) as cnt, ROUND(AVG(COST),0) as avg_cost, MIN(COST) as min_cost, MAX(COST) as max_cost FROM ROUTE_COST GROUP BY CARCLASS ORDER BY CARCLASS").fetchall()
        stats['route_cost_carclass'] = [dict(r) for r in rows]
    except: pass
    try:
        rows = conn.execute("SELECT SUBSTR(ROUTE,1,2) as route_grp, COUNT(DISTINCT ROUTE) as route_cnt, COUNT(*) as rec_cnt FROM ROUTE_COST GROUP BY SUBSTR(ROUTE,1,2) ORDER BY route_grp").fetchall()
        stats['route_cost_groups'] = [dict(r) for r in rows]
    except: pass
    conn.close()
    return jsonify(stats)

@app.route('/api/route_cost/search')
def api_route_cost_search():
    """운송경로별 비용 전용 검색 API"""
    page      = int(request.args.get('page', 1))
    size      = int(request.args.get('size', 50))
    route     = request.args.get('route', '').strip()
    ptnrky    = request.args.get('ptnrky', '').strip()
    carclass  = request.args.get('carclass', '').strip()
    shppt     = request.args.get('shppt', '').strip()
    q         = request.args.get('q', '').strip()

    where_parts, params = [], []
    if route:    where_parts.append("ROUTE LIKE ?");    params.append(f"%{route}%")
    if ptnrky:   where_parts.append("PTNRKY LIKE ?");   params.append(f"%{ptnrky}%")
    if carclass: where_parts.append("CARCLASS = ?");    params.append(carclass)
    if shppt:    where_parts.append("SHPPT = ?");       params.append(shppt)
    if q:
        where_parts.append("(ROUTE LIKE ? OR PTNRKY LIKE ? OR CARCLASS LIKE ?)")
        params.extend([f"%{q}%", f"%{q}%", f"%{q}%"])

    where_sql = f"WHERE {' AND '.join(where_parts)}" if where_parts else ""
    offset    = (page - 1) * size

    conn  = get_conn()
    total = conn.execute(f"SELECT COUNT(*) FROM ROUTE_COST {where_sql}", params).fetchone()[0]
    rows  = conn.execute(
        f"SELECT * FROM ROUTE_COST {where_sql} ORDER BY ROUTE, PTNRKY, CARCLASS LIMIT ? OFFSET ?",
        params + [size, offset]
    ).fetchall()

    # 차량톤수 필터용 목록
    carclasses = [r[0] for r in conn.execute(
        "SELECT DISTINCT CARCLASS FROM ROUTE_COST ORDER BY CARCLASS"
    ).fetchall()]
    shppts = [r[0] for r in conn.execute(
        "SELECT DISTINCT SHPPT FROM ROUTE_COST ORDER BY SHPPT"
    ).fetchall()]
    conn.close()

    import math
    return jsonify({
        "total":      total,
        "page":       page,
        "size":       size,
        "pages":      math.ceil(total / size) if total else 1,
        "rows":       [dict(r) for r in rows],
        "carclasses": carclasses,
        "shppts":     shppts,
    })

@app.route('/api/route_cost/pivot')
def api_route_cost_pivot():
    """경로×납품처 피벗 (차량톤수 컬럼으로 전개)"""
    route    = request.args.get('route', '').strip()
    ptnrky   = request.args.get('ptnrky', '').strip()
    carclass = request.args.get('carclass', '').strip()
    q        = request.args.get('q', '').strip()

    where_parts, params = [], []
    if route:    where_parts.append("ROUTE LIKE ?");    params.append(f"%{route}%")
    if ptnrky:   where_parts.append("PTNRKY LIKE ?");   params.append(f"%{ptnrky}%")
    if carclass: where_parts.append("CARCLASS = ?");    params.append(carclass)
    if q:
        where_parts.append("(ROUTE LIKE ? OR PTNRKY LIKE ?)")
        params.extend([f"%{q}%", f"%{q}%"])

    where_sql = f"WHERE {' AND '.join(where_parts)}" if where_parts else ""
    conn = get_conn()

    # 사용 중인 차량톤수 목록
    ccs = [r[0] for r in conn.execute(
        f"SELECT DISTINCT CARCLASS FROM ROUTE_COST {where_sql} ORDER BY CARCLASS", params
    ).fetchall()]

    # 경로+납품처 기준으로 그룹핑하여 피벗
    rows_raw = conn.execute(
        f"""SELECT SHPPT, ROUTE, PTNRKY, CARCLASS, COST, UNIT, DATE_START, DATE_END
            FROM ROUTE_COST {where_sql} ORDER BY ROUTE, PTNRKY, CARCLASS""", params
    ).fetchall()
    conn.close()

    # pivot: key=(SHPPT,ROUTE,PTNRKY) → {CARCLASS: COST}
    from collections import OrderedDict
    pivot = OrderedDict()
    meta  = {}
    for r in rows_raw:
        key = (r['SHPPT'], r['ROUTE'], r['PTNRKY'])
        if key not in pivot:
            pivot[key] = {}
            meta[key]  = {'UNIT': r['UNIT'], 'DATE_START': r['DATE_START'], 'DATE_END': r['DATE_END']}
        pivot[key][r['CARCLASS']] = r['COST']

    result = []
    for (shppt, route, ptnrky), costs in pivot.items():
        row = {'SHPPT': shppt, 'ROUTE': route, 'PTNRKY': ptnrky,
               **meta[(shppt, route, ptnrky)]}
        for cc in ccs:
            row[cc] = costs.get(cc, None)
        result.append(row)

    return jsonify({"carclasses": ccs, "rows": result, "total": len(result)})

@app.route('/api/shpdh/detail/<shpoky>')
def shpdh_detail(shpoky):
    conn = get_conn()
    header = conn.execute("SELECT * FROM SHPDH WHERE SHPOKY=?", [shpoky]).fetchone()
    if not header:
        conn.close()
        return jsonify({"error":"Not found"}), 404
    items = conn.execute("SELECT * FROM SHPDI WHERE SHPOKY=?", [shpoky]).fetchall()
    conn.close()
    return jsonify({"header": dict(header), "items": [dict(r) for r in items]})

# ─────────────────────────────────────────────
#  납품처 관리 API
# ─────────────────────────────────────────────

@app.route('/api/codes/<cmcdky>')
def api_codes(cmcdky):
    """공통코드 값 목록 반환 (CMCDKY 기준)"""
    conn = get_conn()
    rows = conn.execute(
        "SELECT CMCDVL, CDESC1, USARG1, USARG2, USARG3 "
        "FROM CMCDV WHERE CMCDKY=? ORDER BY CMCDVL", [cmcdky]
    ).fetchall()
    conn.close()
    return jsonify([{
        "value": r["CMCDVL"],
        "label": (r["CDESC1"] or "").strip() or r["CMCDVL"],
        "arg1":  (r["USARG1"] or "").strip(),
        "arg2":  (r["USARG2"] or "").strip(),
        "arg3":  (r["USARG3"] or "").strip(),
    } for r in rows])


@app.route('/api/wahma/list')
def api_wahma_list():
    """물류센터(WAHMA) 목록 조회"""
    q        = request.args.get('q', '').strip()
    page     = int(request.args.get('page', 1))
    size     = int(request.args.get('size', 50))
    sort_col = request.args.get('sort_col', '').strip()
    sort_dir = request.args.get('sort_dir', 'ASC').strip().upper()
    if sort_dir not in ('ASC', 'DESC'):
        sort_dir = 'ASC'
    _wahma_sort_allowed = {
        'WAREKY', 'NAME01', 'ADDR01', 'POSTCD', 'TELN01', 'WADN01', 'LMODAT', 'DELMAK'
    }
    order_expr = f"{sort_col} {sort_dir}" if sort_col in _wahma_sort_allowed \
                 else "WAREKY ASC"
    where_parts, params = [], []
    if q:
        where_parts.append("(WAREKY LIKE ? OR NAME01 LIKE ? OR ADDR01 LIKE ? OR TELN01 LIKE ?)")
        params += [f"%{q}%"] * 4
    where_sql = f"WHERE {' AND '.join(where_parts)}" if where_parts else ""
    offset = (page - 1) * size
    conn  = get_conn()
    total = conn.execute(f"SELECT COUNT(*) FROM WAHMA {where_sql}", params).fetchone()[0]
    rows  = conn.execute(
        f"SELECT WAREKY, COMPKY, NAME01, ADDR01, POSTCD, NATNKY, TELN01, FAXTL1, "
        f"WADN01, WADT01, WADM01, DELMAK, CREDAT, LMODAT, LMOUSR, WHTT01, WHTT02 "
        f"FROM WAHMA {where_sql} ORDER BY {order_expr} LIMIT ? OFFSET ?",
        params + [size, offset]
    ).fetchall()
    conn.close()
    return jsonify({
        "total": total, "page": page, "size": size,
        "pages": math.ceil(total / size) if total else 1,
        "rows": [dict(r) for r in rows],
    })


@app.route('/api/wahma/detail/<wareky>')
def api_wahma_detail(wareky):
    """물류센터 단건 상세 조회"""
    conn = get_conn()
    row  = conn.execute("SELECT * FROM WAHMA WHERE WAREKY=?", [wareky]).fetchone()
    conn.close()
    if not row:
        return jsonify({"error": "Not found"}), 404
    return jsonify(dict(row))


@app.route('/api/wahma/save', methods=['POST'])
def api_wahma_save():
    """물류센터 저장/수정 (UPSERT)"""
    import datetime
    body   = request.get_json() or {}
    wareky = (body.get('WAREKY') or '').strip()
    if not wareky:
        return jsonify({"error": "창고코드(WAREKY)는 필수입니다"}), 400
    now = datetime.datetime.now()
    nd, nt = now.strftime('%Y%m%d'), now.strftime('%H%M%S')
    fields = ['COMPKY','TSPKEY','DELMAK','CHKSHA','NAME01','NAME02','NAME03',
              'ADDR01','ADDR02','ADDR03','ADDR04','ADDR05','CITY01','REGN01',
              'POSTCD','NATNKY','TELN01','TELN02','TELN03','FAXTL1','FAXTL2',
              'TAXCD1','TAXCD2','VATREG','POBOX1','POBPC1','WADN01','WADT01',
              'WADT02','WADM01','EXCOMK','INDOVA','PLOCOV','INDUAC','DSORKY',
              'DRECLO','INDBZL','INDARC','WHTT01','WHTT02']
    conn = get_conn()
    existing = conn.execute("SELECT 1 FROM WAHMA WHERE WAREKY=?", [wareky]).fetchone()
    try:
        if existing:
            set_parts = [f"{f}=?" for f in fields] + ["LMODAT=?","LMOTIM=?","LMOUSR=?","UPDCHK=UPDCHK+1"]
            vals = [body.get(f,'').strip() if isinstance(body.get(f,''), str) else body.get(f,'')
                    for f in fields] + [nd, nt, 'WEB', wareky]
            conn.execute(f"UPDATE WAHMA SET {', '.join(set_parts)} WHERE WAREKY=?", vals)
            action = 'updated'
        else:
            all_fields = ['WAREKY'] + fields + ['CREDAT','CRETIM','CREUSR','LMODAT','LMOTIM','LMOUSR','UPDCHK']
            vals = [wareky] + [body.get(f,'').strip() if isinstance(body.get(f,''), str) else body.get(f,'')
                               for f in fields] + [nd, nt, 'WEB', nd, nt, 'WEB', 0]
            ph = ','.join(['?']*len(all_fields))
            conn.execute(f"INSERT INTO WAHMA ({','.join(all_fields)}) VALUES ({ph})", vals)
            action = 'created'
            # 신규 창고는 CMCDV TMS_SHPPOINT 에도 등록
            name01 = (body.get('NAME01') or wareky).strip()
            conn.execute(
                "INSERT OR REPLACE INTO CMCDV (CMCDKY,CMCDVL,CDESC1,CDESC2,USARG1,USARG2,USARG3,USARG4,USARG5,"
                "CREDAT,CRETIM,CREUSR,LMODAT,LMOTIM,LMOUSR,INDBZL,INDARC,UPDCHK) VALUES "
                "('TMS_SHPPOINT',?,?,'',' ',' ',' ',' ',' ',?,?,'WEB',?,?,'WEB',' ',' ',0)",
                [wareky, name01, nd, nt, nd, nt]
            )
        conn.commit()
        conn.close()
        return jsonify({"ok": True, "action": action})
    except Exception as e:
        conn.close()
        return jsonify({"error": str(e)}), 500


@app.route('/api/delivery/shppoint')
def api_delivery_shppoint():
    """출하지점 목록 반환: WAHMA 테이블 기반 + BZPTN_DETAIL 건수 포함"""
    conn = get_conn()
    # WAHMA 전체 목록
    codes = conn.execute(
        "SELECT WAREKY, NAME01 FROM WAHMA WHERE DELMAK=' ' OR DELMAK='' ORDER BY WAREKY"
    ).fetchall()
    # BZPTN_DETAIL 의 WAREKY 별 건수
    cnts = conn.execute(
        "SELECT WAREKY, COUNT(*) as cnt FROM BZPTN_DETAIL "
        "WHERE WAREKY IS NOT NULL AND WAREKY != '' GROUP BY WAREKY"
    ).fetchall()
    conn.close()
    cnt_map = {r["WAREKY"]: r["cnt"] for r in cnts}
    result = []
    for c in codes:
        v = c["WAREKY"]
        label = (c["NAME01"] or "").strip() or v
        result.append({"value": v, "label": label, "cnt": cnt_map.get(v, 0)})
    return jsonify(result)


@app.route('/api/delivery/list')
def api_delivery_list():
    """납품처 목록 조회 (BZPTN + BZPTN_DETAIL LEFT JOIN)"""
    page      = int(request.args.get('page', 1))
    size      = int(request.args.get('size', 50))
    vstel     = request.args.get('vstel', '').strip()
    werks     = request.args.getlist('werks')
    werks     = [w.strip() for w in werks if w.strip()]
    skug05    = request.args.get('skug05', '').strip()
    ptnrky    = request.args.get('ptnrky', '').strip()
    q         = request.args.get('q', '').strip()
    sort_col  = request.args.get('sort_col', 'b.PTNRKY').strip()
    sort_dir  = request.args.get('sort_dir', 'ASC').strip().upper()
    if sort_dir not in ('ASC', 'DESC'): sort_dir = 'ASC'
    # 허용 컬럼만 정렬에 사용
    _dlv_sort_map = {
        'PTNRKY':'b.PTNRKY','NAME01':'b.NAME01','WAREKY':'d.WAREKY',
        'ITEM_GROUP':'d.ITEM_GROUP','ADDR01':'b.ADDR01','AREA_CD':'d.AREA_CD',
    }
    order_expr = _dlv_sort_map.get(sort_col, 'b.PTNRKY')

    where_parts, params = ["b.PTNRTY = 'CT'"], []
    if vstel:
        where_parts.append("d.WAREKY = ?")
        params.append(vstel)
    if skug05:
        where_parts.append("d.ITEM_GROUP = ?")
        params.append(skug05)
    if ptnrky:
        where_parts.append("(b.PTNRKY LIKE ? OR b.NAME01 LIKE ?)")
        params.extend([f"%{ptnrky}%", f"%{ptnrky}%"])
    if q:
        where_parts.append("(b.PTNRKY LIKE ? OR b.NAME01 LIKE ? OR b.ADDR01 LIKE ? OR b.REGN01 LIKE ?)")
        params.extend([f"%{q}%"] * 4)

    where_sql = "WHERE " + " AND ".join(where_parts)
    offset    = (page - 1) * size

    conn = get_conn()
    total = conn.execute(
        f"SELECT COUNT(*) FROM BZPTN b LEFT JOIN BZPTN_DETAIL d ON b.PTNRKY=d.PTNRKY AND b.PTNRTY=d.PTNRTY AND b.OWNRKY=d.OWNRKY {where_sql}",
        params
    ).fetchone()[0]

    data_sql = f"""
        SELECT b.PTNRKY, b.NAME01, b.PTNRTY, b.OWNRKY,
               b.ADDR01, b.ADDR02, b.REGN01, b.TELN01,
               d.WAREKY, d.ROUTE_CD, d.ITEM_GROUP, d.AREA_CD,
               d.UNLOAD_TIME, d.MAX_HEIGHT, d.AUTO_ALLOC_YN, d.FORKLIFT_YN,
               d.INB_TIME_FROM1, d.INB_TIME_TO1,
               CASE WHEN d.PTNRKY IS NOT NULL THEN 'Y' ELSE 'N' END as HAS_DETAIL
        FROM BZPTN b
        LEFT JOIN BZPTN_DETAIL d ON b.PTNRKY=d.PTNRKY AND b.PTNRTY=d.PTNRTY AND b.OWNRKY=d.OWNRKY
        {where_sql}
        ORDER BY {order_expr} {sort_dir}
        LIMIT ? OFFSET ?
    """
    rows = conn.execute(data_sql, params + [size, offset]).fetchall()
    conn.close()

    return jsonify({
        "total": total, "page": page, "size": size,
        "pages": math.ceil(total / size) if total else 1,
        "rows":  [dict(r) for r in rows],
        "sort_col": sort_col, "sort_dir": sort_dir,
    })


@app.route('/api/delivery/detail/<ptnrky>')
def api_delivery_detail(ptnrky):
    """납품처 상세 조회 (BZPTN + BZPTN_DETAIL)"""
    ptnrty = request.args.get('ptnrty', 'CT')
    ownrky = request.args.get('ownrky', 'KN')
    conn = get_conn()
    bzptn = conn.execute(
        "SELECT * FROM BZPTN WHERE PTNRKY=? AND PTNRTY=? AND OWNRKY=?",
        [ptnrky, ptnrty, ownrky]
    ).fetchone()
    if not bzptn:
        conn.close()
        return jsonify({"error": "Not found"}), 404
    detail = conn.execute(
        "SELECT * FROM BZPTN_DETAIL WHERE PTNRKY=? AND PTNRTY=? AND OWNRKY=?",
        [ptnrky, ptnrty, ownrky]
    ).fetchone()
    conn.close()
    return jsonify({
        "bzptn":  dict(bzptn),
        "detail": dict(detail) if detail else None,
    })


@app.route('/api/delivery/save', methods=['POST'])
def api_delivery_save():
    """납품처 상세(BZPTN_DETAIL) 저장/수정 (UPSERT)"""
    import datetime
    body = request.get_json() or {}

    ptnrky = body.get('PTNRKY', '').strip()
    ptnrty = body.get('PTNRTY', 'CT').strip()
    ownrky = body.get('OWNRKY', 'KN').strip()
    wareky = body.get('WAREKY', '').strip()

    if not ptnrky:
        return jsonify({"error": "납품처코드(PTNRKY)는 필수입니다"}), 400

    now = datetime.datetime.now()
    now_date = now.strftime('%Y%m%d')
    now_time = now.strftime('%H%M%S')

    conn = get_conn()
    existing = conn.execute(
        "SELECT 1 FROM BZPTN_DETAIL WHERE PTNRKY=? AND PTNRTY=? AND OWNRKY=?",
        [ptnrky, ptnrty, ownrky]
    ).fetchone()

    fields = [
        'WAREKY', 'ROUTE_CD', 'ITEM_GROUP', 'UNLOAD_TIME',
        'INB_TIME_FROM1', 'INB_TIME_TO1', 'AREA_CD', 'MAX_HEIGHT',
        'FORKLIFT_YN', 'HANDWORK_YN', 'AUTO_PLT', 'MAX_BOX_QTY',
        'AUTO_ALLOC_YN', 'SINGLE_ITEM_YN', 'NY_TYPE', 'SINGLE_HEIGHT',
        'DYNAMIC_YN', 'LTL_YN', 'PRIORITY_YN', 'MIN_QTSIWH',
        'LATITUDE', 'LONGITUDE', 'DEL_YN',
    ]

    try:
        if existing:
            set_parts = [f"{f}=?" for f in fields]
            set_parts += ["LMODAT=?", "LMOTIM=?", "LMOUSR=?"]
            vals = [body.get(f) for f in fields]
            vals += [now_date, now_time, 'WEB']
            vals += [ptnrky, ptnrty, ownrky]
            conn.execute(
                f"UPDATE BZPTN_DETAIL SET {', '.join(set_parts)} "
                f"WHERE PTNRKY=? AND PTNRTY=? AND OWNRKY=?",
                vals
            )
            action = 'updated'
        else:
            all_fields = ['PTNRKY', 'PTNRTY', 'OWNRKY'] + fields + \
                         ['CREDAT', 'CRETIM', 'CREUSR', 'LMODAT', 'LMOTIM', 'LMOUSR']
            vals = [ptnrky, ptnrty, ownrky]
            vals += [body.get(f) for f in fields]
            vals += [now_date, now_time, 'WEB', now_date, now_time, 'WEB']
            placeholders = ','.join(['?'] * len(all_fields))
            conn.execute(
                f"INSERT INTO BZPTN_DETAIL ({','.join(all_fields)}) VALUES ({placeholders})",
                vals
            )
            action = 'created'
        conn.commit()
        conn.close()
        return jsonify({"ok": True, "action": action})
    except Exception as e:
        conn.close()
        return jsonify({"error": str(e)}), 500


# ─────────────────────────────────────────────
#  차량 관리 API
# ─────────────────────────────────────────────

@app.route('/api/vehicle/list')
def api_vehicle_list():
    """차량 목록 조회 (VHCMA)"""
    page         = int(request.args.get('page', 1))
    size         = int(request.args.get('size', 50))
    ship_point   = request.args.get('ship_point', '').strip()
    product_group= request.args.get('product_group', '').strip()
    delivery_zone= request.args.get('delivery_zone', '').strip()
    carrier      = request.args.get('carrier', '').strip()
    vehicle_type = request.args.get('vehicle_type', '').strip()
    vehicle_kind = request.args.get('vehicle_kind', '').strip()
    vehicle_class= request.args.get('vehicle_class', '').strip()
    vehicle_no   = request.args.get('vehicle_no', '').strip()
    sort_col     = request.args.get('sort_col', '').strip()
    sort_dir     = request.args.get('sort_dir', 'ASC').strip().upper()
    if sort_dir not in ('ASC', 'DESC'): sort_dir = 'ASC'
    _vhc_sort_allowed = {'VEHICLE_NO','SHIP_POINT','PRODUCT_GROUP','DELIVERY_ZONE',
                         'CARRIER','VEHICLE_TYPE','VEHICLE_KIND','VEHICLE_CLASS',
                         'DRIVER_NAME','USE_YN','FIX_YN'}
    order_expr = f"{sort_col} {sort_dir}" if sort_col in _vhc_sort_allowed \
                 else "SHIP_POINT ASC, VEHICLE_NO ASC"

    where_parts, params = [], []
    if ship_point:    where_parts.append("SHIP_POINT = ?");          params.append(ship_point)
    if product_group: where_parts.append("PRODUCT_GROUP = ?");       params.append(product_group)
    if delivery_zone: where_parts.append("DELIVERY_ZONE = ?");       params.append(delivery_zone)
    if carrier:       where_parts.append("CARRIER LIKE ?");          params.append(f"%{carrier}%")
    if vehicle_type:  where_parts.append("VEHICLE_TYPE = ?");        params.append(vehicle_type)
    if vehicle_kind:  where_parts.append("VEHICLE_KIND = ?");        params.append(vehicle_kind)
    if vehicle_class: where_parts.append("VEHICLE_CLASS = ?");       params.append(vehicle_class)
    if vehicle_no:    where_parts.append("VEHICLE_NO LIKE ?");       params.append(f"%{vehicle_no}%")

    where_sql = f"WHERE {' AND '.join(where_parts)}" if where_parts else ""
    offset = (page - 1) * size

    conn  = get_conn()
    total = conn.execute(f"SELECT COUNT(*) FROM VHCMA {where_sql}", params).fetchone()[0]
    rows  = conn.execute(
        f"SELECT * FROM VHCMA {where_sql} ORDER BY {order_expr} LIMIT ? OFFSET ?",
        params + [size, offset]
    ).fetchall()

    # 필터용 유니크 목록 (출하지점은 WAHMA 기준)
    ship_points   = [{"value": r[0], "label": r[1]} for r in conn.execute(
        "SELECT WAREKY, NAME01 FROM WAHMA WHERE DELMAK=' ' OR DELMAK='' ORDER BY WAREKY"
    ).fetchall()]
    prod_groups   = [r[0] for r in conn.execute("SELECT DISTINCT PRODUCT_GROUP FROM VHCMA WHERE PRODUCT_GROUP IS NOT NULL ORDER BY PRODUCT_GROUP").fetchall()]
    zones         = [r[0] for r in conn.execute("SELECT DISTINCT DELIVERY_ZONE FROM VHCMA WHERE DELIVERY_ZONE IS NOT NULL ORDER BY DELIVERY_ZONE").fetchall()]
    carriers      = [r[0] for r in conn.execute("SELECT DISTINCT CARRIER       FROM VHCMA WHERE CARRIER       IS NOT NULL ORDER BY CARRIER").fetchall()]
    vtypes        = [r[0] for r in conn.execute("SELECT DISTINCT VEHICLE_TYPE  FROM VHCMA WHERE VEHICLE_TYPE  IS NOT NULL ORDER BY VEHICLE_TYPE").fetchall()]
    vkinds        = [r[0] for r in conn.execute("SELECT DISTINCT VEHICLE_KIND  FROM VHCMA WHERE VEHICLE_KIND  IS NOT NULL ORDER BY VEHICLE_KIND").fetchall()]
    vclasses      = [r[0] for r in conn.execute("SELECT DISTINCT VEHICLE_CLASS FROM VHCMA WHERE VEHICLE_CLASS IS NOT NULL ORDER BY VEHICLE_CLASS").fetchall()]
    conn.close()

    return jsonify({
        "total": total, "page": page, "size": size,
        "pages": math.ceil(total / size) if total else 1,
        "rows":  [dict(r) for r in rows],
        "ship_points": ship_points, "prod_groups": prod_groups,
        "zones": zones, "carriers": carriers,
        "vtypes": vtypes, "vkinds": vkinds, "vclasses": vclasses,
        "sort_col": sort_col, "sort_dir": sort_dir,
    })


@app.route('/api/vehicle/detail/<vehicle_no>')
def api_vehicle_detail(vehicle_no):
    """차량 상세 조회"""
    ownrky = request.args.get('ownrky', 'KN')
    conn = get_conn()
    row = conn.execute(
        "SELECT * FROM VHCMA WHERE VEHICLE_NO=? AND OWNRKY=?", [vehicle_no, ownrky]
    ).fetchone()
    conn.close()
    if not row:
        return jsonify({"error": "Not found"}), 404
    return jsonify(dict(row))


@app.route('/api/vehicle/save', methods=['POST'])
def api_vehicle_save():
    """차량 저장/수정 (UPSERT)"""
    import datetime
    body   = request.get_json() or {}
    vno    = (body.get('VEHICLE_NO') or '').strip()
    ownrky = (body.get('OWNRKY') or 'KN').strip()

    if not vno:
        return jsonify({"error": "차량번호(VEHICLE_NO)는 필수입니다"}), 400

    now      = datetime.datetime.now()
    now_date = now.strftime('%Y%m%d')
    now_time = now.strftime('%H%M%S')

    fields = [
        'OWNRKY', 'SHIP_POINT', 'PRODUCT_GROUP', 'DELIVERY_ZONE', 'CARRIER',
        'VEHICLE_TYPE', 'VEHICLE_KIND', 'VEHICLE_CLASS', 'DRIVER_NAME', 'CONTACT_NO',
        'AXLE_TYPE', 'LOAD_VOLUME', 'LOAD_WEIGHT', 'PALLET_QTY',
        'CARGO_LENGTH', 'CARGO_WIDTH', 'CARGO_HEIGHT',
        'FLOOR_TYPE', 'USE_YN', 'OPERABLE_YN', 'FIX_YN',
        'DLV_TIME_FROM', 'DLV_TIME_TO', 'VEHICLE_YEAR',
        'DELIVERY_CUSTOMER_1', 'DELIVERY_CUSTOMER_2', 'DEL_YN',
    ]

    conn = get_conn()
    existing = conn.execute(
        "SELECT 1 FROM VHCMA WHERE VEHICLE_NO=? AND OWNRKY=?", [vno, ownrky]
    ).fetchone()

    try:
        if existing:
            set_parts = [f"{f}=?" for f in fields] + ["LMODAT=?", "LMOTIM=?", "LMOUSR=?"]
            vals      = [body.get(f) for f in fields] + [now_date, now_time, 'WEB', vno, ownrky]
            conn.execute(
                f"UPDATE VHCMA SET {', '.join(set_parts)} WHERE VEHICLE_NO=? AND OWNRKY=?",
                vals
            )
            action = 'updated'
        else:
            all_fields = ['VEHICLE_NO'] + fields + ['CREDAT', 'CRETIM', 'CREUSR', 'LMODAT', 'LMOTIM', 'LMOUSR']
            vals       = [vno] + [body.get(f) for f in fields] + [now_date, now_time, 'WEB', now_date, now_time, 'WEB']
            placeholders = ','.join(['?'] * len(all_fields))
            conn.execute(
                f"INSERT INTO VHCMA ({','.join(all_fields)}) VALUES ({placeholders})", vals
            )
            action = 'created'
        conn.commit()
        conn.close()
        return jsonify({"ok": True, "action": action})
    except Exception as e:
        conn.close()
        return jsonify({"error": str(e)}), 500


@app.route('/api/vehicle/delete', methods=['POST'])
def api_vehicle_delete():
    """차량 삭제 (DEL_YN = 'Y' 처리)"""
    import datetime
    body   = request.get_json() or {}
    vno    = (body.get('VEHICLE_NO') or '').strip()
    ownrky = (body.get('OWNRKY') or 'KN').strip()
    if not vno:
        return jsonify({"error": "VEHICLE_NO 필수"}), 400
    now = datetime.datetime.now()
    conn = get_conn()
    conn.execute(
        "UPDATE VHCMA SET DEL_YN='Y', LMODAT=?, LMOTIM=?, LMOUSR=? WHERE VEHICLE_NO=? AND OWNRKY=?",
        [now.strftime('%Y%m%d'), now.strftime('%H%M%S'), 'WEB', vno, ownrky]
    )
    conn.commit()
    conn.close()
    return jsonify({"ok": True})


@app.route('/api/delivery/delete', methods=['POST'])
def api_delivery_delete():
    """납품처 TMS 상세(BZPTN_DETAIL) 삭제 (DEL_YN='Y')"""
    import datetime
    body   = request.get_json() or {}
    ptnrky = (body.get('PTNRKY') or '').strip()
    ptnrty = (body.get('PTNRTY') or 'CT').strip()
    ownrky = (body.get('OWNRKY') or 'KN').strip()
    if not ptnrky:
        return jsonify({"error": "PTNRKY 필수"}), 400
    now = datetime.datetime.now()
    conn = get_conn()
    conn.execute(
        "UPDATE BZPTN_DETAIL SET DEL_YN='Y', LMODAT=?, LMOTIM=?, LMOUSR=? "
        "WHERE PTNRKY=? AND PTNRTY=? AND OWNRKY=?",
        [now.strftime('%Y%m%d'), now.strftime('%H%M%S'), 'WEB', ptnrky, ptnrty, ownrky]
    )
    conn.commit()
    conn.close()
    return jsonify({"ok": True})


@app.route('/api/wahma/delete', methods=['POST'])
def api_wahma_delete():
    """물류센터(WAHMA) 삭제 표시 (DELMAK='X')"""
    import datetime
    body   = request.get_json() or {}
    wareky = (body.get('WAREKY') or '').strip()
    if not wareky:
        return jsonify({"error": "WAREKY 필수"}), 400
    now = datetime.datetime.now()
    conn = get_conn()
    conn.execute(
        "UPDATE WAHMA SET DELMAK='X', LMODAT=?, LMOTIM=?, LMOUSR=? WHERE WAREKY=?",
        [now.strftime('%Y%m%d'), now.strftime('%H%M%S'), 'WEB', wareky]
    )
    conn.commit()
    conn.close()
    return jsonify({"ok": True})


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5050, debug=False)
