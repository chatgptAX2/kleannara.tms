import sqlite3, json, math, os, uuid, shutil
from flask import Flask, jsonify, request, send_from_directory, send_file, abort
from werkzeug.utils import secure_filename
import datetime

app = Flask(__name__, static_folder='static')
DB_PATH = "/home/user/webapp/wms-viewer/wms.db"

# ── 서류관리 업로드 경로 ──
DOC_UPLOAD_BASE = "/home/user/webapp/wms-viewer/uploads"
os.makedirs(DOC_UPLOAD_BASE, exist_ok=True)

# 허용 확장자
DOC_ALLOWED_EXT = {
    'pdf', 'png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp', 'tiff', 'tif',
    'svg', 'heic', 'heif'
}

def _doc_allowed(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in DOC_ALLOWED_EXT

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
    # SHPDI — 출고문서 아이템 (명세서 기반 전체 한글명)
    "SHPOKY":"출고문서번호","SHPOIT":"출고아이템번호",
    "STATIT":"아이템상태","SKUKEY":"SKU키",
    "QTYORG":"원주문수량","QTSHPO":"출고오더수량","QTYREF":"참조수량",
    "QTAPPO":"지정수량","QTALOC":"배분수량","QTJCMP":"작업완료수량",
    "QTSHPD":"출고수량","QTSHPC":"출고확인수량",
    "QTYUOM":"단위별수량","MEASKY":"단위구성키","UOMKEY":"단위","QTPUOM":"단위당수량",
    "DUOMKY":"기본단위","QTDUOM":"기본단위수량",
    "SASTKY":"재고지정전략키","ALSTKY":"배분전략키","TKFLKY":"작업흐름키",
    "ESHPKY":"외부출고번호","ESHPIT":"외부출고아이템",
    "OPURKY":"오너구매오더번호",
    "REFDKY":"총괄지시번호","REFDIT":"총괄지시아이템","REFCAT":"총괄지시문서타입","REFDAT":"총괄지시일자",
    "EXSUBS":"대체품여부",
    "DESC01":"품목설명1","DESC02":"품목설명2",
    "ASKU01":"보조SKU1","ASKU02":"보조SKU2","ASKU03":"보조SKU3","ASKU04":"보조SKU4","ASKU05":"보조SKU5",
    "EANCOD":"EAN코드","GTINCD":"GTIN코드",
    "SKUG01":"품목유형1","SKUG02":"품목유형2","SKUG03":"품목유형3","SKUG04":"품목유형4","SKUG05":"제품군",
    "GRSWGT":"총중량","NETWGT":"순중량","WGTUNT":"중량단위",
    "LENGTH":"길이","WIDTHW":"너비","HEIGHT":"높이","CUBICM":"부피(㎥)","CAPACT":"용량",
    "PROCHA":"배분변경금지","AREAKY":"구역키",
    "LOTA01":"저장위치","LOTA02":"플랜트","LOTA03":"포장타입","LOTA04":"호기","LOTA05":"LOT속성5",
    "LOTA06":"재고상태","LOTA07":"주문번호MTO","LOTA08":"국가코드","LOTA09":"거래처코드MTO",
    "LOTA10":"보류사유명","LOTA11":"제조일자","LOTA12":"입고일자","LOTA13":"유통기한",
    "LOTA14":"개별바코드","LOTA15":"LOT ID","LOTA16":"평량","LOTA17":"컨테이너유형",
    "LOTA18":"LOT속성18","LOTA19":"LOT속성19","LOTA20":"LOT속성20",
    "AWMSNO":"AWMS번호",
    "SMANDT":"SAP클라이언트","SEBELN":"SAP구매오더번호","SEBELP":"SAP구매오더아이템",
    "SZMBLNO":"SAP BL번호","SZMIPNO":"SAP청구아이템번호","STRAID":"SAP거래ID",
    "SVBELN":"SAP오더번호","SPOSNR":"SAP오더아이템번호",
    "STKNUM":"선적번호","STPNUM":"선적아이템",
    "SWERKS":"SAP출하지점","SLGORT":"SAP저장위치","SDATBG":"SAP납기일",
    "STDLNR":"SAP운송사","SSORNU":"SAP eNpos번호","SSORIT":"SAP eNpos아이템",
    "SMBLNR":"SAP자재문서번호","SZEILE":"SAP자재문서아이템","SMJAHR":"SAP자재문서연도",
    "SXBLNR":"SAP HKT참조문서번호","SAPSTS":"SAP Mvt",
    "PTNRKY":"파트너키","NAME01":"하역지점","SLAND1":"납품국가","SBKTXT":"총괄지시텍스트",
    "INDBZL":"비즈니스로직지시자","INDARC":"아카이브지시자","UPDCHK":"업데이트체크",
    "PO_NO":"PO번호","PO_REV":"PO개정차수","PO_LNO":"PO라인번호",
    "TLOTA01":"저장위치TO","TLOTA02":"플랜트TO",
    "STLNUM":"변경선적번호","CHGFLG":"주문변경상태","KEEPIT":"아이템진행락","APPOINTPICKING":"지정피킹",
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
    "ROUTE_CD":"유통경로코드","ITEM_GROUP":"제품군","UNLOAD_TIME":"하차대기시간(분)","DEADLINE_TIME":"납기시간(HH:MM)",
    "INB_TIME_FROM1":"입차가능시작1","INB_TIME_TO1":"입차가능종료1",
    "AREA_CD":"권역","MAX_HEIGHT":"최대진입높이(m)","FORKLIFT_YN":"지게차여부",
    "HANDWORK_YN":"수작업여부","AUTO_PLT":"자동설정PLT","MAX_BOX_QTY":"최대박스수량","MAX_TON":"최대톤수(TMS_CARCLASS10)",
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
    "CONTACT_NO":"연락처","PALLET_QTY":"적재파렛트수",
    "CARTYPE":"차량유형(DS_VEHICLE)","CARCLASS_CD":"차량유형코드",
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
    from flask import make_response
    resp = make_response(send_from_directory('static', 'index.html'))
    resp.headers['Cache-Control'] = 'no-cache, no-store, must-revalidate'
    resp.headers['Pragma']        = 'no-cache'
    resp.headers['Expires']       = '0'
    return resp

@app.route('/api/tables')
def api_tables():
    conn = get_conn()
    result = {}
    for tbl, meta in TABLE_META.items():
        try:
            cnt = conn.execute(f"SELECT COUNT(*) FROM {tbl}").fetchone()[0]
            col_count = len(get_table_cols(tbl))
            result[tbl] = {**meta, "count": cnt, "col_count": col_count}
        except Exception:
            # DB에 테이블이 없는 경우 count=0으로 처리 (500 방지)
            result[tbl] = {**meta, "count": 0, "col_count": 0}
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

    def _split_statements(raw):
        """세미콜론으로 SQL 구문을 분리 (문자열 리터럴 내부 세미콜론 무시)."""
        stmts = []
        buf   = []
        in_sq = False   # 작은따옴표 안
        in_dq = False   # 큰따옴표 안
        i = 0
        while i < len(raw):
            ch = raw[i]
            if ch == "'" and not in_dq:
                in_sq = not in_sq
            elif ch == '"' and not in_sq:
                in_dq = not in_dq
            if ch == ';' and not in_sq and not in_dq:
                s = ''.join(buf).strip()
                if s:
                    stmts.append(s)
                buf = []
            else:
                buf.append(ch)
            i += 1
        s = ''.join(buf).strip()
        if s:
            stmts.append(s)
        return stmts

    body = request.get_json()
    sql  = (body or {}).get('sql', '').strip()
    if not sql:
        return jsonify({"error":"No SQL provided"}), 400

    stmts = _split_statements(sql)
    if not stmts:
        return jsonify({"error":"No SQL provided"}), 400

    # ── 구문이 1개인 경우 기존 방식과 동일하게 처리 ──────────────────────
    if len(stmts) == 1:
        single = stmts[0]
        first_word = single.upper().split()[0] if single.strip() else ''

        if first_word in ('INSERT', 'UPDATE', 'DELETE'):
            try:
                conn = get_conn()
                cur  = conn.execute(single)
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
            cur  = conn.execute(single)
            cols = [d[0] for d in cur.description]
            rows = cur.fetchmany(500)
            conn.close()
            return jsonify({"columns": cols, "rows":[list(r) for r in rows], "count": len(rows)})
        except Exception as e:
            return jsonify({"error": str(e)}), 400

    # ── 구문이 2개 이상인 경우: 다건 순차 실행 ───────────────────────────
    ALLOWED = ('SELECT','INSERT','UPDATE','DELETE')
    for s in stmts:
        fw = s.upper().split()[0] if s.strip() else ''
        if fw not in ALLOWED:
            return jsonify({"error": f"허용되지 않는 구문 포함: {fw} — SELECT / INSERT / UPDATE / DELETE 만 허용"}), 403

    results  = []   # 각 구문 결과
    conn = get_conn()
    try:
        for idx, s in enumerate(stmts, 1):
            fw = s.upper().split()[0]
            try:
                cur = conn.execute(s)
                if fw == 'SELECT':
                    cols = [d[0] for d in cur.description]
                    rows = cur.fetchmany(500)
                    results.append({
                        "index": idx, "type": "SELECT", "ok": True,
                        "columns": cols, "rows": [list(r) for r in rows],
                        "count": len(rows), "sql_preview": s[:120]
                    })
                else:
                    affected = cur.rowcount
                    results.append({
                        "index": idx, "type": fw, "ok": True,
                        "affected": affected,
                        "message": f"{fw} 완료 — {affected}행 영향",
                        "sql_preview": s[:120]
                    })
            except Exception as e:
                conn.rollback()
                results.append({
                    "index": idx, "type": fw if s.strip() else "?",
                    "ok": False, "error": str(e), "sql_preview": s[:120]
                })
                # 오류 발생 시 이후 구문 중단
                break
        conn.commit()
    finally:
        conn.close()

    total    = len(results)
    ok_cnt   = sum(1 for r in results if r.get('ok'))
    err_cnt  = total - ok_cnt
    total_affected = sum(r.get('affected', 0) for r in results if r.get('ok') and r.get('type') != 'SELECT')

    return jsonify({
        "multi": True, "results": results,
        "total": total, "ok_count": ok_cnt, "error_count": err_cnt,
        "total_affected": total_affected
    })


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
        "SELECT CMCDVL, CDESC1, CDESC2, USARG1, USARG2, USARG3 "
        "FROM CMCDV WHERE CMCDKY=? ORDER BY CMCDVL", [cmcdky]
    ).fetchall()
    conn.close()
    return jsonify([{
        "value":  r["CMCDVL"],
        "label":  (r["CDESC1"] or "").strip() or r["CMCDVL"],
        "desc2":  (r["CDESC2"] or "").strip(),
        "arg1":   (r["USARG1"] or "").strip(),
        "arg2":   (r["USARG2"] or "").strip(),
        "arg3":   (r["USARG3"] or "").strip(),
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
        'UNLOAD_TIME':'d.UNLOAD_TIME','FORKLIFT_YN':'d.FORKLIFT_YN',
        'MAX_BOX_QTY':'d.MAX_BOX_QTY','DEADLINE_TIME':'d.DEADLINE_TIME',
        'MAX_TON':'d.MAX_TON',
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
               d.INB_TIME_FROM1, d.INB_TIME_TO1, d.MAX_BOX_QTY, d.DEADLINE_TIME, d.MAX_TON,
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
        'LATITUDE', 'LONGITUDE', 'DEL_YN', 'DEADLINE_TIME', 'MAX_TON',
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
        'VEHICLE_TYPE', 'VEHICLE_KIND', 'VEHICLE_CLASS',
        'CARTYPE',       # DS_VEHICLE 차량유형 (선택 팝업에서 선택한 CARTYPE)
        'DRIVER_NAME', 'CONTACT_NO',
        'PALLET_QTY', 'FLOOR_TYPE', 'USE_YN', 'OPERABLE_YN', 'FIX_YN',
        'DLV_TIME_FROM', 'DLV_TIME_TO', 'VEHICLE_YEAR',
        'DELIVERY_CUSTOMER_1', 'DELIVERY_CUSTOMER_2', 'DEL_YN',
        'CARCLASS_CD',   # DS_VEHICLE 차량코드 (CARCLASS_CD)
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


@app.route('/api/carclass')
def api_carclass():
    """고정차량 관리: TMS_CARCLASS10 공통코드 + DS_VEHICLE 통합 조회 (PS 제품군)
    - carclasses: 공통코드 전체 (TMS_CARCLASS10 기준)
    - vehicles: DS_VEHICLE 독립 조회 (CARTYPE 기준)
    - merged: TMS_CARCLASS10 기준 LEFT JOIN → 모든 톤수 + 제원정보 통합
    """
    conn = get_conn()
    # TMS_CARCLASS10 공통코드 전체 조회 (PS 제품군; USARG1=사용유무)
    # CMCDV 테이블은 USARG5까지만 존재 — 차량 치수는 DS_VEHICLE에서 조회
    cc_rows = conn.execute("""
        SELECT CMCDVL, CDESC1, USARG1, USARG2, USARG3, USARG4, USARG5
        FROM CMCDV
        WHERE CMCDKY = 'TMS_CARCLASS10'
        ORDER BY CMCDVL
    """).fetchall()

    # DS_VEHICLE 전체 조회 (CARCLASS_CD PK 기준)
    vhc_rows = conn.execute("""
        SELECT CARCLASS_CD, CARTYPE, LENGTH_M, WIDTH_M, HEIGHT_M, LOAD_TON, SORT_SEQ, UPDDAT, UPDUSR
        FROM DS_VEHICLE
        ORDER BY SORT_SEQ
    """).fetchall()

    # DS_VEHICLE을 CARCLASS_CD → dict 맵으로 변환
    vhc_map = {r['CARCLASS_CD']: dict(r) for r in vhc_rows}

    # TMS_CARCLASS10 기준으로 DS_VEHICLE 데이터 병합
    # CMCDVL(차량유형코드)이 DS_VEHICLE.CARCLASS_CD(PK)와 1:1 매핑
    merged = []
    for r in cc_rows:
        cc = dict(r)
        cdesc1 = (cc.get('CDESC1') or '').strip()
        vhc = vhc_map.get(cc['CMCDVL'])  # CMCDVL(=CARCLASS_CD PK)로 DS_VEHICLE 매핑
        merged.append({
            # 공통코드 필드
            'CMCDVL': cc['CMCDVL'],
            'CDESC1': cdesc1,
            'USARG1': cc['USARG1'],
            'USARG2': cc['USARG2'],
            'USARG3': cc['USARG3'],
            'USARG4': cc['USARG4'],
            'USARG5': cc['USARG5'],
            'USARG6': str(vhc['LENGTH_M']) if vhc and vhc['LENGTH_M'] else '',  # 길이(M) — DS_VEHICLE에서
            'USARG7': str(vhc['WIDTH_M'])  if vhc and vhc['WIDTH_M']  else '',  # 너비(M) — DS_VEHICLE에서
            'USARG8': str(vhc['HEIGHT_M']) if vhc and vhc['HEIGHT_M'] else '',  # 높이(M) — DS_VEHICLE에서
            # DS_VEHICLE 필드
            'CARCLASS_CD': vhc['CARCLASS_CD'] if vhc else None,
            'CARTYPE':  vhc['CARTYPE']  if vhc else None,
            'LENGTH_M': vhc['LENGTH_M'] if vhc else None,
            'WIDTH_M':  vhc['WIDTH_M']  if vhc else None,
            'HEIGHT_M': vhc['HEIGHT_M'] if vhc else None,
            'LOAD_TON': vhc['LOAD_TON'] if vhc else None,
            'SORT_SEQ': vhc['SORT_SEQ'] if vhc else None,
            'UPDDAT':   vhc['UPDDAT']   if vhc else None,
            'UPDUSR':   vhc['UPDUSR']   if vhc else None,
            'HAS_VHC':  bool(vhc),      # DS_VEHICLE 레코드 존재 여부
        })

    conn.close()

    cc_list  = [dict(r) for r in cc_rows]
    vhc_list = [dict(r) for r in vhc_rows]

    return jsonify({"carclasses": cc_list, "vehicles": vhc_list, "merged": merged})


@app.route('/api/carclass-by-product')
def api_carclass_by_product():
    """제품군별 차량톤수 공통코드 조회 (차량유형 선택 다이얼로그용)
    query param: product_group (예: '10' → TMS_CARCLASS10, '20' → TMS_CARCLASS20)
    Returns:
      - cmcdky: 조회한 공통코드 키
      - header: CMCDM USARL1~8 컬럼 라벨 목록 [{col, label}]
      - rows: CMCDV 전체 [{CMCDVL, CDESC1, USARG1~8}]
    """
    product_group = (request.args.get('product_group') or '').strip()
    # 제품군 코드 → 공통코드 키 매핑 (10=PS, 20=HL, 기타=TMS_CARCLASS10 기본)
    cmcdky_map = {'10': 'TMS_CARCLASS10', '20': 'TMS_CARCLASS20'}
    cmcdky = cmcdky_map.get(product_group, 'TMS_CARCLASS10')

    conn = get_conn()
    try:
        # CMCDM 헤더 라벨 조회 (CMCDM은 USARL5까지만 존재)
        m_row = conn.execute(
            """SELECT USARL1, USARL2, USARL3, USARL4, USARL5
               FROM CMCDM WHERE CMCDKY=?""", (cmcdky,)
        ).fetchone()
        header = []
        if m_row:
            lbl_keys = ['USARL1','USARL2','USARL3','USARL4','USARL5']
            arg_keys = ['USARG1','USARG2','USARG3','USARG4','USARG5']
            for lk, ak in zip(lbl_keys, arg_keys):
                lbl = (m_row[lk] or '').strip()
                if lbl:
                    header.append({'col': ak, 'label': lbl})

        # CMCDV 상세 전체 조회 (USARG5까지만 존재; 치수는 DS_VEHICLE에서 병합)
        v_rows = conn.execute(
            """SELECT CMCDVL, CDESC1,
                      USARG1, USARG2, USARG3, USARG4, USARG5
               FROM CMCDV WHERE CMCDKY=? ORDER BY CMCDVL""", (cmcdky,)
        ).fetchall()
        # DS_VEHICLE 치수 맵 (CARCLASS_CD → {LENGTH_M, WIDTH_M, HEIGHT_M, LOAD_TON})
        vhc_map2 = {r['CARCLASS_CD']: dict(r) for r in conn.execute(
            "SELECT CARCLASS_CD, LENGTH_M, WIDTH_M, HEIGHT_M, LOAD_TON FROM DS_VEHICLE"
        ).fetchall()}
        rows = []
        for r in v_rows:
            d = dict(r)
            # 공백 트림
            for k in d:
                if isinstance(d[k], str):
                    d[k] = d[k].strip()
            # USARG6/7/8 가상 필드: DS_VEHICLE에서 치수 보완
            vhc = vhc_map2.get(d['CMCDVL'])
            d['USARG6'] = str(vhc['LENGTH_M']) if vhc and vhc['LENGTH_M'] else ''
            d['USARG7'] = str(vhc['WIDTH_M'])  if vhc and vhc['WIDTH_M']  else ''
            d['USARG8'] = str(vhc['HEIGHT_M']) if vhc and vhc['HEIGHT_M'] else ''
            rows.append(d)

        return jsonify({"ok": True, "cmcdky": cmcdky, "header": header, "rows": rows})
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


@app.route('/api/ds-vehicle')
def api_ds_vehicle():
    """DS_VEHICLE 차량 제원 목록 (적재 시각화용 경량 API)
    Returns: [{CARTYPE, LENGTH_M, WIDTH_M, HEIGHT_M, LOAD_TON, LOAD_KG, PALLET_HEIGHT_M, SORT_SEQ,
               PALLET_CNT, LONG_AXIS_YN, INCH12_LT300, INCH12_GE300, INCH3_LT300, INCH3_GE300, CARCLASS_CD}]
    """
    conn = get_conn()
    try:
        rows = conn.execute(
            """SELECT CARTYPE, LENGTH_M, WIDTH_M, HEIGHT_M, LOAD_TON, PALLET_HEIGHT_M, SORT_SEQ,
                      PALLET_CNT, LONG_AXIS_YN, INCH12_LT300, INCH12_GE300,
                      INCH3_LT300, INCH3_GE300, CARCLASS_CD
               FROM DS_VEHICLE ORDER BY SORT_SEQ"""
        ).fetchall()
        result = []
        for r in rows:
            d = dict(r)
            load_ton = float(d.get('LOAD_TON') or 0)
            d['LOAD_KG'] = round(load_ton * 1000, 1) if load_ton > 0 else None
            # WIDTH_M 범위 처리 (예: '1.8~2.1' → 최솟값)
            w_raw = str(d.get('WIDTH_M') or '')
            try:
                d['WIDTH_M_NUM'] = float(w_raw.split('~')[0].strip()) if '~' in w_raw else float(w_raw)
            except (ValueError, TypeError):
                d['WIDTH_M_NUM'] = 2.4
            result.append(d)
        return jsonify({"ok": True, "vehicles": result})
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


@app.route('/api/carclass/save', methods=['POST'])
def api_carclass_save():
    """고정차량 관리 저장: CMCDV(TMS_CARCLASS10) 또는 DS_VEHICLE 수정"""
    import datetime
    body = request.get_json() or {}
    table = (body.get('table') or '').strip()  # 'carclass' | 'vehicle'
    now   = datetime.datetime.now()
    nowdt = now.strftime('%Y%m%d')
    nowtm = now.strftime('%H%M%S')

    conn = get_conn()
    try:
        if table == 'carclass':
            # CMCDV TMS_CARCLASS10 행 업데이트
            # CMCDV는 USARG5까지만 존재; USARG6/7/8(치수)은 DS_VEHICLE에 저장
            key = (body.get('CMCDVL') or '').strip()
            if not key:
                return jsonify({"error": "CMCDVL 필수"}), 400
            fields = {
                'USARG1': body.get('USARG1'),  # 사용유무
                'USARG2': body.get('USARG2'),
                'USARG3': body.get('USARG3'),
                'USARG4': body.get('USARG4'),
                'USARG5': body.get('USARG5'),
                'CDESC1': body.get('CDESC1'),  # 차량톤수명
            }
            sets = []
            vals = []
            for col, val in fields.items():
                if val is not None:
                    sets.append(f"{col}=?")
                    vals.append(str(val).strip())
            sets += ["LMODAT=?", "LMOTIM=?", "LMOUSR=?"]
            vals += [nowdt, nowtm, 'WEB']
            vals += ['TMS_CARCLASS10', key]
            conn.execute(f"UPDATE CMCDV SET {','.join(sets)} WHERE CMCDKY=? AND CMCDVL=?", vals)
            conn.commit()
            conn.close()
            return jsonify({"ok": True})

        elif table == 'vehicle':
            # DS_VEHICLE 행 업데이트 (CARCLASS_CD PK)
            # CARCLASS_CD 없으면 CARTYPE으로 조회하여 자동 보완
            carclass_cd = (body.get('CARCLASS_CD') or '').strip()
            if not carclass_cd:
                cartype_kw = (body.get('CARTYPE') or '').strip()
                if cartype_kw:
                    row = conn.execute(
                        "SELECT CARCLASS_CD FROM DS_VEHICLE WHERE CARTYPE=? LIMIT 1", [cartype_kw]
                    ).fetchone()
                    if row:
                        carclass_cd = row['CARCLASS_CD'] or ''
            if not carclass_cd:
                return jsonify({"error": "CARCLASS_CD 필수 (CARTYPE으로도 조회 불가)"}), 400

            # 존재하면 UPDATE, 없으면 INSERT
            exists = conn.execute("SELECT 1 FROM DS_VEHICLE WHERE CARCLASS_CD=?", [carclass_cd]).fetchone()
            if exists:
                fields = {}
                for col in ['CARTYPE', 'LENGTH_M', 'WIDTH_M', 'HEIGHT_M', 'LOAD_TON', 'SORT_SEQ', 'PALLET_HEIGHT_M',
                            'INCH12_LT300', 'INCH12_GE300', 'INCH3_LT300', 'INCH3_GE300', 'DEFAULT_VEH_CNT',
                            'PALLET_CNT', 'LONG_AXIS_YN']:
                    if body.get(col) is not None:
                        fields[col] = body.get(col)
                if fields:
                    sets = [f"{c}=?" for c in fields] + ["UPDDAT=?", "UPDUSR=?"]
                    vals = list(fields.values()) + [nowdt, 'WEB', carclass_cd]
                    conn.execute(f"UPDATE DS_VEHICLE SET {','.join(sets)} WHERE CARCLASS_CD=?", vals)
            else:
                # 신규 INSERT
                max_seq = conn.execute("SELECT COALESCE(MAX(SORT_SEQ),0)+1 FROM DS_VEHICLE").fetchone()[0]
                conn.execute("""
                    INSERT INTO DS_VEHICLE (CARCLASS_CD, CARTYPE, LENGTH_M, WIDTH_M, HEIGHT_M, LOAD_TON, SORT_SEQ, UPDDAT, UPDUSR,
                                           PALLET_HEIGHT_M, INCH12_LT300, INCH12_GE300, INCH3_LT300, INCH3_GE300,
                                           DEFAULT_VEH_CNT, PALLET_CNT, LONG_AXIS_YN)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, [
                    carclass_cd,
                    body.get('CARTYPE') or None,
                    body.get('LENGTH_M') or None,
                    body.get('WIDTH_M') or None,
                    body.get('HEIGHT_M') or None,
                    body.get('LOAD_TON') or None,
                    max_seq,
                    nowdt, 'WEB',
                    float(body.get('PALLET_HEIGHT_M') or 0),
                    body.get('INCH12_LT300') or None,
                    body.get('INCH12_GE300') or None,
                    body.get('INCH3_LT300')  or None,
                    body.get('INCH3_GE300')  or None,
                    (int(body['DEFAULT_VEH_CNT']) if body.get('DEFAULT_VEH_CNT') not in (None,'') else None),
                    (int(body['PALLET_CNT'])   if body.get('PALLET_CNT')   not in (None,'') else None),
                    (body.get('LONG_AXIS_YN') or 'N'),
                ])
            conn.commit()
            conn.close()
            return jsonify({"ok": True})

        elif table == 'vehicle_delete':
            # DS_VEHICLE 삭제 (CARCLASS_CD PK)
            carclass_cd = (body.get('CARCLASS_CD') or '').strip()
            if not carclass_cd:
                return jsonify({"error": "CARCLASS_CD 필수"}), 400
            conn.execute("DELETE FROM DS_VEHICLE WHERE CARCLASS_CD=?", [carclass_cd])
            conn.commit()
            conn.close()
            return jsonify({"ok": True})

        else:
            conn.close()
            return jsonify({"error": f"알 수 없는 table: {table}"}), 400

    except Exception as e:
        conn.close()
        return jsonify({"error": str(e)}), 500


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


# ═══════════════════════════════════════════════════════════════
# ═══════════════════════════════════════════════════════════════
#  환산단위 헬퍼 함수  (SZF_GET_CONVERT_QTY Python 완전 동일 구현)
#
#  오라클 원본 로직:
#    SELECT MAX(DECODE(INDDFU,'V', UOMKEY,' '))  AS D_UOMKEY   ← 기준단위 UOMKEY
#         , SUM(DECODE(to_uom, UOMKEY, QTPUOM, 0)) AS C_QTPUOM  ← 목표단위의 QTPUOM
#         , SUM(DECODE(to_uom, UOMKEY, QTAUOM, 0)) AS C_QTAUOM  ← 목표단위의 QTAUOM
#    FROM MEASI WHERE WAREKY=P_WAREKY AND MEASKY=P_SKUKEY
#
#    IF D_UOMKEY == from_uom:
#        result = ROUND(qty / (C_QTPUOM / C_QTAUOM), 5)   ← 기준단위이면 직접 환산
#    ELSE:
#        result = ROUND(SZF_GET_DEFAULT_QTY(...) / (C_QTPUOM / C_QTAUOM), 5)
#        ※ SZF_GET_DEFAULT_QTY: qty를 기준단위로 먼저 변환
#           = qty * (from_uom.QTPUOM / from_uom.QTAUOM)
#
#  SKUG05 예외 (오라클 함수 동일):
#    - SKUG05='20' AND to_uom IN ('R','SOK') → '' (빈값)
#    - SKUG05='10' AND to_uom = 'EA'         → EA/SOK 비율 계산
# ═══════════════════════════════════════════════════════════════

def _convert_qty(conn, wareky, measky, qty, from_uom, to_uom, skug05=''):
    """
    SZF_GET_CONVERT_QTY 완전 동일 로직.
    qty(from_uom 기준) → to_uom 기준 수량 반환.
    변환 불가 / 예외 시 None 반환 (0과 구분).
    """
    if qty is None:
        return None
    try:
        qty = float(qty)
    except (ValueError, TypeError):
        return None

    # 동일 단위면 그대로
    if from_uom == to_uom:
        return round(qty, 5)

    # SKUG05 예외: 제품군 20 은 R, SOK 환산 불가 → 빈값
    skug05 = str(skug05 or '').strip()
    if skug05 == '20' and to_uom in ('R', 'SOK'):
        return None

    rows = conn.execute(
        "SELECT UOMKEY, QTPUOM, QTAUOM, INDDFU FROM MEASI WHERE WAREKY=? AND MEASKY=?",
        (wareky, measky)
    ).fetchall()

    if not rows:
        return None

    # SKUG05='10' + to_uom='EA' 예외: EA/SOK 비율 계산
    if skug05 == '10' and to_uom == 'EA':
        try:
            ea_sum  = sum(float(r['QTAUOM'] or 0) for r in rows if str(r['UOMKEY']).strip() == 'EA')
            sok_sum = sum(float(r['QTAUOM'] or 0) for r in rows if str(r['UOMKEY']).strip() == 'SOK')
            if sok_sum and sok_sum != 0:
                return round(ea_sum / sok_sum, 5)
        except Exception:
            pass
        return None

    # MEASI 맵 구성: uomkey → (qtpuom, qtauom)
    uom_map  = {}   # { uomkey: (qtpuom, qtauom) }
    d_uomkey = None # INDDFU='V' 인 기준단위

    for r in rows:
        uk  = str(r['UOMKEY']  or '').strip()
        qtp = float(r['QTPUOM'] or 0)
        qta = float(r['QTAUOM'] or 0)
        idf = str(r['INDDFU']  or '').strip()
        if idf == 'V':
            d_uomkey = uk
        if uk and uk not in uom_map:
            uom_map[uk] = (qtp, qta)

    # 목표단위 C_QTPUOM / C_QTAUOM
    if to_uom not in uom_map:
        return None
    c_qtpuom, c_qtauom = uom_map[to_uom]
    if c_qtpuom == 0 or c_qtauom == 0:
        return None
    ratio = c_qtpuom / c_qtauom   # 목표단위 환산비율

    try:
        if d_uomkey == from_uom:
            # 입력단위 = 기준단위 → 직접 환산
            result = qty / ratio
        else:
            # 입력단위 → 기준단위로 먼저 변환 (SZF_GET_DEFAULT_QTY)
            if from_uom not in uom_map:
                return None
            f_qtp, f_qta = uom_map[from_uom]
            if f_qta == 0:
                return None
            default_qty = qty * (f_qtp / f_qta)   # → 기준단위 수량
            result = default_qty / ratio
        return round(result, 5)
    except (ZeroDivisionError, TypeError):
        return None


# ─────────────────────────────────────────────────────────────────
#  출고예정정보(주문정보) 조회 API
# ─────────────────────────────────────────────────────────────────

@app.route('/api/shipment/schedule', methods=['POST'])
def api_shipment_schedule():
    """
    출고예정정보(주문정보) 조회.
    원본 쿼리(SZF_GET_CONVERT_QTY) → Python _convert_qty() 함수로 환산.
    """
    body    = request.get_json() or {}
    wareky  = body.get('wareky',  '').strip()
    rqshpd_from = body.get('rqshpd_from', '').strip().replace('-','')
    rqshpd_to   = body.get('rqshpd_to',  '').strip().replace('-','')
    stknum      = body.get('stknum',  '').strip()
    svbelns     = body.get('svbeln',  [])   # 납품문서번호 다중 리스트
    lota02s     = body.get('lota02',  [])   # 플랜트 리스트
    shpmtys     = body.get('shpmty',  [])   # 출하유형 리스트
    statit      = body.get('statit',  '').strip()
    skug05      = body.get('skug05',  '').strip()
    dptnky      = body.get('dptnky',  '').strip()
    ptnrky      = body.get('ptnrky',  '').strip()   # 납품처 코드
    alloc_st    = body.get('alloc_status', '').strip()  # 배차상태: 'done'|'notdone'|''(전체)
    skukey      = body.get('skukey', '').strip()        # 품목코드
    page    = int(body.get('page', 1))
    size    = int(body.get('size', 100))
    offset  = (page - 1) * size

    conn = get_conn()

    # ── WHERE 조건 조립 ──────────────────────────────────────────
    where = ["1=1"]
    params = []

    if wareky:
        where.append("SH.WAREKY = ?"); params.append(wareky)
    if rqshpd_from:
        where.append("SH.RQSHPD >= ?"); params.append(rqshpd_from)
    if rqshpd_to:
        where.append("SH.RQSHPD <= ?"); params.append(rqshpd_to)
    if stknum:
        where.append("SI.STDLNR = ?"); params.append(stknum)
    # 납품문서번호(SVBELN) 다중 검색
    if svbelns:
        clean_svbelns = [v.strip() for v in svbelns if v.strip()]
        if clean_svbelns:
            ph_sv = ','.join('?' * len(clean_svbelns))
            where.append(f"SI.SVBELN IN ({ph_sv})")
            params.extend(clean_svbelns)
    if statit:
        where.append("SI.STATIT = ?"); params.append(statit)
    if skug05:
        where.append("SI.SKUG05 = ?"); params.append(skug05)
    if dptnky:
        where.append("SH.DPTNKY = ?"); params.append(dptnky)
    # 납품처 코드/명칭 검색 (코드 정확일치 또는 코드/명칭 부분일치)
    if ptnrky:
        where.append("(SI.PTNRKY = ? OR SI.PTNRKY LIKE ? OR SH.DPTNKY = ? OR SH.DPTNKY LIKE ? OR SI.NAME01 LIKE ?)")
        params.extend([ptnrky, f"%{ptnrky}%", ptnrky, f"%{ptnrky}%", f"%{ptnrky}%"])
    if skukey:
        where.append("SI.SKUKEY LIKE ?"); params.append(f"%{skukey}%")
    # 배차상태: STDLNR(가선적번호) 유무로 판단
    if alloc_st == 'done':
        where.append("(SI.STDLNR IS NOT NULL AND TRIM(SI.STDLNR) != '')")
    elif alloc_st == 'notdone':
        where.append("(SI.STDLNR IS NULL OR TRIM(SI.STDLNR) = '')")

    # 출하유형 (SHPMTY) 다중선택
    default_shpmty = ['201','205','206','208','221','231']  # 출하유형
    use_shpmty = shpmtys if shpmtys else default_shpmty
    ph = ','.join('?' * len(use_shpmty))
    where.append(f"SH.SHPMTY IN ({ph})"); params.extend(use_shpmty)

    # 플랜트 (LOTA02) 다중선택
    default_lota02 = ['P100','P200','P300','P400']
    use_lota02 = lota02s if lota02s else default_lota02
    ph2 = ','.join('?' * len(use_lota02))
    where.append(f"SI.LOTA02 IN ({ph2})"); params.extend(use_lota02)

    where_sql = " AND ".join(where)

    # ── 기본 쿼리 (환산 컬럼 제외, 먼저 로우 추출) ───────────────
    base_sql = f"""
        SELECT
            COALESCE(SI.STDLNR,' ')                                AS STKNUM,
            SI.SVBELN                                               AS SVBELN,
            SI.SHPOKY                                               AS SHPOKY,
            SH.RQSHPD                                               AS RQSHPD,
            SH.STATDO                                               AS STATDO,
            COALESCE(ST.CDESC1,' ')                                 AS STATDONM,
            COALESCE(ST.USARG1,' ')                                 AS STATDONO,
            SI.SHPOIT                                               AS SHPOIT,
            SI.STATIT                                               AS STATIT,
            COALESCE((SELECT CDESC1 FROM CMCDV
                      WHERE CMCDKY='STATIT' AND CMCDVL=SI.STATIT),' ') AS STATNM,
            COALESCE(TRIM(NULL),' ')                            AS APPOINTPICKING,
            SI.CHGFLG                                               AS CHGFLG,
            CASE WHEN SI.STATIT='NEW' THEN 'V' ELSE '' END          AS STATUS_NEW,
            CASE WHEN SI.STATIT IN ('FAL','PAL') THEN 'V' ELSE '' END AS STATUS_ALO,
            CASE WHEN SI.STATIT IN ('FPC','PPC') THEN 'V' ELSE '' END AS STATUS_PCK,
            CASE WHEN SI.STATIT IN ('FSH','PSH') THEN 'V' ELSE '' END AS STATUS_SHP,
            SI.SKUG05                                               AS SKUG05,
            COALESCE(CD.CDESC1,' ')                                 AS SKUG05NM,
            SI.SKUKEY                                               AS SKUKEY,
            SI.DESC01                                               AS DESC01,
            CASE WHEN SH.SHPMTY='231' THEN COALESCE(SH.PTRCVR,' ')
                 ELSE COALESCE(SH.DPTNKY,' ') END                  AS DPTNKY,
            CASE WHEN SH.SHPMTY='231' THEN COALESCE(VD.NAME01,' ')
                 ELSE COALESCE(CT.NAME01,' ') END                  AS DPTNKYNM,
            ' '                                                 AS SAPSTS,
            SI.QTSHPO                                               AS QTSHPO,
            SI.QTYORG                                               AS QTYORG,
            SI.UOMKEY                                               AS UOMKEY,
            SI.QTALOC                                               AS QTALOC,
            (SI.QTSHPO - SI.QTALOC)                                 AS QTUALO,
            SI.QTJCMP                                               AS QTJCMP,
            SI.QTSHPD                                               AS QTSHPD,
            SI.DUOMKY                                               AS DUOMKY,
            SI.NAME01                                               AS NAME01,
            M.SKUL01                                                AS SKUL01,
            SI.LOTA02                                               AS LOTA02,
            COALESCE((SELECT CDESC1 FROM CMCDV
                      WHERE CMCDKY='LOTA02' AND CMCDVL=SI.LOTA02),' ') AS LOTA02NM,
            COALESCE(TRIM(SI.LOTA03),' ')                           AS LOTA03,
            SI.LOTA01                                               AS LOTA01,
            ' '                                                     AS LOTA01NM,
            COALESCE(TRIM(SH.SAP2),' ')                             AS SAP2,
            SI.LOTA08                                               AS LOTA08,
            COALESCE((SELECT CDESC1 FROM CMCDV
                      WHERE CMCDKY='NATNKY' AND CMCDVL=SI.LOTA08),' ') AS LOTA08NM,
            SI.LOTA07                                               AS LOTA07,
            SI.LOTA15                                               AS LOTA15,
            SI.LOTA17                                               AS LOTA17,
            COALESCE((SELECT NAME01 FROM BZPTN
                      WHERE OWNRKY=SH.OWNRKY AND PTNRTY='CT'
                        AND PTNRKY=SI.LOTA09),' ')                  AS LOTA09NM,
            SH.DOCTXT                                               AS DOCTXT,
            SI.ALSTKY                                               AS ALSTKY,
            SH.PRTCHK                                               AS PRTCHK,
            SI.CREDAT                                               AS CREDAT,
            SI.CRETIM                                               AS CRETIM,
            SI.CREUSR                                               AS CREUSR,
            SI.LMODAT                                               AS LMODAT,
            SI.LMOTIM                                               AS LMOTIM,
            SI.LMOUSR                                               AS LMOUSR,
            SI.TLOTA01                                              AS TLOTA01,
            ' '                                                     AS TLOTA01NM,
            SI.TLOTA02                                              AS TLOTA02,
            COALESCE((SELECT CDESC1 FROM CMCDV
                      WHERE CMCDKY='LOTA02' AND CMCDVL=SI.TLOTA02),' ') AS TLOTA02NM,
            SH.DOCUTY                                               AS DOCUTYNM,
            SH.WAREKY                                               AS WAREKY,
            SI.MEASKY                                               AS MEASKY,
            CASE WHEN TRIM(SI.SKUG05)='10' AND M.GRSWGT IS NOT NULL AND M.GRSWGT > 0
                 THEN M.GRSWGT
                 ELSE NULL
            END                                                     AS PLTKG,
            (SELECT M.QTAUOM FROM MEASI M
             WHERE M.WAREKY=SH.WAREKY AND M.MEASKY=SI.MEASKY
               AND TRIM(M.UOMKEY)='SOK'
             LIMIT 1)                                               AS SOK_PER_R
        FROM SHPDI SI
        INNER JOIN SHPDH SH ON SH.SHPOKY = SI.SHPOKY
        LEFT  JOIN BZPTN CT ON CT.OWNRKY=SH.OWNRKY AND CT.PTNRTY='CT' AND CT.PTNRKY=SH.DPTNKY
        LEFT  JOIN BZPTN VD ON VD.OWNRKY=SH.OWNRKY AND VD.PTNRTY='VD' AND VD.PTNRKY=SH.PTRCVR
        LEFT  JOIN CMCDV ST ON ST.CMCDKY='STATDO' AND ST.CMCDVL=SH.STATDO
        LEFT  JOIN CMCDV CD ON CD.CMCDKY='SKUG05' AND CD.CMCDVL=SI.SKUG05
        LEFT  JOIN SKUMA M  ON SI.SKUKEY=M.SKUKEY AND SH.OWNRKY=M.OWNRKY
        WHERE {where_sql}
        ORDER BY SI.SVBELN, SI.SHPOKY, SI.SHPOIT
        LIMIT ? OFFSET ?
    """

    # 전체 건수
    count_sql = f"""
        SELECT COUNT(*) FROM SHPDI SI
        INNER JOIN SHPDH SH ON SH.SHPOKY = SI.SHPOKY
        LEFT  JOIN SKUMA M  ON SI.SKUKEY=M.SKUKEY AND SH.OWNRKY=M.OWNRKY
        WHERE {where_sql}
    """

    try:
        total = conn.execute(count_sql, params).fetchone()[0]
        rows  = conn.execute(base_sql, params + [size, offset]).fetchall()
    except Exception as e:
        conn.close()
        return jsonify({"error": str(e)}), 400

    # ── MEASI bulk 로드 (N+1 방지) ──────────────────────────────────
    # 조회된 row의 (WAREKY, MEASKY) 쌍을 수집 → 한 번에 MEASI 전체 로드
    wk_mk_pairs = set()
    for r in rows:
        wk = r['WAREKY'] if 'WAREKY' in r.keys() else '1100'
        mk = r['MEASKY'] or ''
        if mk:
            wk_mk_pairs.add((str(wk), str(mk)))

    # measi_bulk: { (wareky, measky): { uomkey: (qtpuom, qtauom, inddfu) } }
    measi_bulk = {}
    if wk_mk_pairs:
        # IN 절로 한 번에 조회
        measky_list = list({mk for _, mk in wk_mk_pairs})
        ph_m = ','.join('?' * len(measky_list))
        measi_rows = conn.execute(
            f"SELECT WAREKY, MEASKY, UOMKEY, QTPUOM, QTAUOM, INDDFU "
            f"FROM MEASI WHERE MEASKY IN ({ph_m})",
            measky_list
        ).fetchall()
        for mr in measi_rows:
            key = (str(mr['WAREKY']), str(mr['MEASKY']))
            uom = str(mr['UOMKEY'] or '').strip()
            if not uom:
                continue
            if key not in measi_bulk:
                measi_bulk[key] = {}
            measi_bulk[key][uom] = (
                float(mr['QTPUOM'] or 0),
                float(mr['QTAUOM'] or 0),
                str(mr['INDDFU'] or '').strip()
            )

    # ── in-memory 환산 함수 (SZF_GET_CONVERT_QTY 동일 로직) ─────────
    def _fmt(v):
        """None → 빈문자열, 0.0 → '0', 소수점 유효자리만 표시"""
        if v is None:
            return ''
        if v == 0:
            return 0
        return round(v, 5)

    def _conv_mem(uom_map, qty, from_uom, to_uom, skug05_s):
        """메모리 uom_map으로 환산 (DB 쿼리 없음)"""
        if qty is None:
            return None
        try:
            qty = float(qty)
        except (ValueError, TypeError):
            return None
        if from_uom == to_uom:
            return round(qty, 5)
        skug05_s = str(skug05_s or '').strip()
        if skug05_s == '20' and to_uom in ('R', 'SOK'):
            return None
        if not uom_map:
            return None
        # SKUG05='10' + to_uom='EA' 예외
        if skug05_s == '10' and to_uom == 'EA':
            try:
                ea_sum  = sum(v[1] for k, v in uom_map.items() if k == 'EA')
                sok_sum = sum(v[1] for k, v in uom_map.items() if k == 'SOK')
                if sok_sum and sok_sum != 0:
                    return round(ea_sum / sok_sum, 5)
            except Exception:
                pass
            return None
        # 기준단위 탐색
        d_uomkey = next((uk for uk, v in uom_map.items() if v[2] == 'V'), None)
        if to_uom not in uom_map:
            return None
        c_qtpuom, c_qtauom, _ = uom_map[to_uom]
        if c_qtpuom == 0 or c_qtauom == 0:
            return None
        ratio = c_qtpuom / c_qtauom
        try:
            if d_uomkey == from_uom:
                result_v = qty / ratio
            else:
                if from_uom not in uom_map:
                    return None
                f_qtp, f_qta, _ = uom_map[from_uom]
                if f_qta == 0:
                    return None
                default_qty = qty * (f_qtp / f_qta)
                result_v = default_qty / ratio
            return round(result_v, 5)
        except (ZeroDivisionError, TypeError):
            return None

    result = []
    _PLT_CAP_KG = 1200.0
    for r in rows:
        d = dict(r)
        wareky_r = str(d.get('WAREKY') or '1100')
        measky   = str(d.get('MEASKY') or '')
        duomky   = str(d.get('DUOMKY') or '').strip()
        skug05   = str(d.get('SKUG05') or '').strip()
        qtshpo   = float(d.get('QTSHPO') or 0)
        qtaloc   = float(d.get('QTALOC') or 0)
        qtjcmp   = float(d.get('QTJCMP') or 0)
        qtshpd   = float(d.get('QTSHPD') or 0)
        qtualo   = qtshpo - qtaloc

        # 환산 기준수량 결정
        is_new_unalloc = (qtualo == qtshpo and d.get('STATIT') == 'NEW')
        qty_for_calc   = qtualo if is_new_unalloc else qtaloc

        # bulk 로드된 uom_map 참조
        uom_map = measi_bulk.get((wareky_r, measky), {})

        def conv(qty_v, to_uom, _umap=uom_map, _from=duomky, _g5=skug05):
            return _conv_mem(_umap, qty_v, _from, to_uom, _g5)

        bag_val = conv(qty_for_calc, 'BAG')
        box_val = conv(qty_for_calc, 'BOX')
        pal_val = conv(qty_for_calc, 'PAL')
        sok_val = conv(qty_for_calc, 'SOK')
        ea_val  = conv(qty_for_calc, 'EA')
        kg_val  = conv(qty_for_calc, 'KG')

        # BOXBAG: BAG / BOX 비율
        if (bag_val is None or bag_val == 0 or
                box_val is None or box_val == 0):
            boxbag = 0
        else:
            boxbag = round(bag_val / box_val, 4)

        # BOX 환산 헬퍼
        def box_conv(qty_v, _umap=uom_map, _from=duomky, _g5=skug05):
            if _from != 'BAG' or not qty_v:
                return 0
            bx = _conv_mem(_umap, qty_v, _from, 'BOX', _g5)
            if not bx or bx == 0:
                return 0
            return round(qty_v / bx, 4)

        d['TOT']        = _fmt(kg_val)
        d['BOXBAG']     = boxbag
        d['BAG']        = _fmt(bag_val)
        d['BOX']        = _fmt(box_val)
        d['PLT']        = _fmt(pal_val)
        d['SOK']        = _fmt(sok_val)
        d['EA']         = _fmt(ea_val)
        d['QTUALO']     = qtualo
        d['QTUALOBOX']  = box_conv(qtualo)
        d['QTALOCBOX']  = box_conv(qtaloc)
        d['QTJCMPBOX']  = box_conv(qtjcmp)
        d['QTSHPDBOX']  = box_conv(qtshpd)
        # PLT개수(원지/판지): SKUMA.GRSWGT 기반 계산
        pltkg = float(d.get('PLTKG') or 0)
        if skug05 == '10' and qtshpo and qtshpo > 0:
            sk_prefix = (d.get('SKUKEY') or '')[:1].upper()
            if sk_prefix == 'H':
                d['PLT_CNT'] = math.ceil(qtshpo / _PLT_CAP_KG)
            elif pltkg > 0:
                total_kg = qtshpo * pltkg
                d['PLT_CNT'] = math.ceil(total_kg / _PLT_CAP_KG)
            else:
                d['PLT_CNT'] = ''
        else:
            d['PLT_CNT'] = ''
        # SOK_PER_R: 1R당 SOK 수
        sok_per_r = d.get('SOK_PER_R')
        d['SOK_PER_R'] = float(sok_per_r) if sok_per_r is not None else ''
        # 내부 처리용 키 제거
        d.pop('WAREKY', None)
        d.pop('MEASKY', None)
        d.pop('PLTKG',  None)
        result.append(d)

    conn.close()
    return jsonify({"total": total, "page": page, "size": size, "rows": result})


@app.route('/api/shipment/schedule/filter-opts', methods=['GET'])
def api_shipment_filter_opts():
    """출고예정정보 검색조건 옵션 (WAREKY, STATIT, SKUG05, LOTA02, max_date)"""
    conn = get_conn()
    wareky_list = [r[0] for r in conn.execute(
        "SELECT DISTINCT WAREKY FROM SHPDH WHERE WAREKY IS NOT NULL AND WAREKY!='' ORDER BY WAREKY"
    ).fetchall()]
    statit_list = conn.execute(
        "SELECT CMCDVL AS value, CDESC1 AS label FROM CMCDV WHERE CMCDKY='STATIT' ORDER BY CMCDVL"
    ).fetchall()
    skug05_list = conn.execute(
        "SELECT CMCDVL AS value, CDESC1 AS label FROM CMCDV WHERE CMCDKY='SKUG05' ORDER BY CMCDVL"
    ).fetchall()
    lota02_list = conn.execute(
        "SELECT DISTINCT LOTA02 FROM SHPDI WHERE LOTA02 IS NOT NULL AND TRIM(LOTA02)!='' ORDER BY LOTA02"
    ).fetchall()
    # 실제 조회 가능한 최신 날짜 = SHPDI와 조인 가능한 SHPDH 기준
    # (SHPDH만으로 MAX를 구하면 SHPDI에 없는 날짜가 포함될 수 있음)
    max_date_row = conn.execute("""
        SELECT MAX(h.RQSHPD) FROM SHPDH h
        WHERE EXISTS (SELECT 1 FROM SHPDI i WHERE i.SHPOKY = h.SHPOKY)
        AND h.RQSHPD IS NOT NULL AND h.RQSHPD != ''
    """).fetchone()
    max_date = max_date_row[0] if max_date_row and max_date_row[0] else ''
    min_date_row = conn.execute("""
        SELECT MIN(h.RQSHPD) FROM SHPDH h
        WHERE EXISTS (SELECT 1 FROM SHPDI i WHERE i.SHPOKY = h.SHPOKY)
        AND h.RQSHPD IS NOT NULL AND h.RQSHPD != ''
    """).fetchone()
    min_date = min_date_row[0] if min_date_row and min_date_row[0] else ''
    conn.close()
    return jsonify({
        "wareky":   wareky_list,
        "statit":   [dict(r) for r in statit_list],
        "skug05":   [dict(r) for r in skug05_list],
        "lota02":   [r[0] for r in lota02_list],
        "max_date": max_date,   # YYYYMMDD — 실제 조회 가능한 최신 날짜
        "min_date": min_date,   # YYYYMMDD — 실제 조회 가능한 최초 날짜
    })


# ─────────────────────────────────────────────
#  배차전략 관리 API
# ─────────────────────────────────────────────

@app.route('/api/dispatch/strategy', methods=['GET'])
def api_dispatch_strategy_get():
    """배차전략 기준표 3종 조회 (PS=TMS_CARCLASS10 / HL=TMS_CARCLASS20 분리)"""
    conn = get_conn()
    inch12 = conn.execute(
        "SELECT CARTYPE,GRM_COND,MAX_COUNT,SORT_SEQ FROM DS_INCH12 ORDER BY SORT_SEQ,GRM_COND"
    ).fetchall()
    inch3 = conn.execute(
        "SELECT CARTYPE,GRM_COND,MAX_COUNT,SORT_SEQ FROM DS_INCH3 ORDER BY SORT_SEQ,GRM_COND"
    ).fetchall()
    vehicle = conn.execute(
        """SELECT CARTYPE,LENGTH_M,WIDTH_M,HEIGHT_M,LOAD_TON,SORT_SEQ,PALLET_HEIGHT_M,
                  INCH12_LT300,INCH12_GE300,INCH3_LT300,INCH3_GE300,CARCLASS_CD,DEFAULT_VEH_CNT,
                  PALLET_CNT,LONG_AXIS_YN
           FROM DS_VEHICLE ORDER BY SORT_SEQ"""
    ).fetchall()

    # TMS_CARCLASS10 (PS 탭) — CMCDVL(코드), CDESC1(라벨), USARG1(사용여부)
    carclass10 = conn.execute(
        "SELECT CMCDVL, CDESC1, COALESCE(USARG1,'Y') AS USARG1 FROM CMCDV WHERE CMCDKY='TMS_CARCLASS10' ORDER BY CMCDVL"
    ).fetchall()
    # TMS_CARCLASS20 (HL 탭) — 동일 구조
    carclass20 = conn.execute(
        "SELECT CMCDVL, CDESC1, COALESCE(USARG1,'Y') AS USARG1 FROM CMCDV WHERE CMCDKY='TMS_CARCLASS20' ORDER BY CMCDVL"
    ).fetchall()

    conn.close()

    def _vrow(r):
        d = dict(r)
        return d

    return jsonify({
        "inch12":            [dict(r) for r in inch12],
        "inch3":             [dict(r) for r in inch3],
        "vehicle":           [_vrow(r) for r in vehicle],
        # 하위 호환: 기존 carclass_options (TMS_CARCLASS10)
        "carclass_options":  [{"code": r[0], "label": r[1], "use_yn": r[2]} for r in carclass10],
        # PS / HL 탭별 공통코드
        "carclass_ps":       [{"code": r[0], "label": r[1], "use_yn": r[2]} for r in carclass10],
        "carclass_hl":       [{"code": r[0], "label": r[1], "use_yn": r[2]} for r in carclass20],
    })


@app.route('/api/dispatch/strategy/save', methods=['POST'])
def api_dispatch_strategy_save():
    """배차전략 기준표 저장 (table: inch12|inch3|vehicle, rows: [...])"""
    from datetime import datetime
    body  = request.json or {}
    table = body.get('table', '')
    rows  = body.get('rows', [])
    today = datetime.now().strftime('%Y%m%d')
    conn  = get_conn()
    try:
        if table == 'inch12':
            conn.execute("DELETE FROM DS_INCH12")
            for r in rows:
                conn.execute(
                    "INSERT INTO DS_INCH12 (CARTYPE,GRM_COND,MAX_COUNT,SORT_SEQ,UPDDAT) VALUES (?,?,?,?,?)",
                    (r['CARTYPE'], r['GRM_COND'], int(r['MAX_COUNT']), int(r.get('SORT_SEQ',0)), today)
                )
        elif table == 'inch3':
            conn.execute("DELETE FROM DS_INCH3")
            for r in rows:
                conn.execute(
                    "INSERT INTO DS_INCH3 (CARTYPE,GRM_COND,MAX_COUNT,SORT_SEQ,UPDDAT) VALUES (?,?,?,?,?)",
                    (r['CARTYPE'], r['GRM_COND'], int(r['MAX_COUNT']), int(r.get('SORT_SEQ',0)), today)
                )
        elif table == 'vehicle':
            conn.execute("DELETE FROM DS_VEHICLE")
            for r in rows:
                conn.execute(
                    """INSERT INTO DS_VEHICLE
                       (CARCLASS_CD,CARTYPE,LENGTH_M,WIDTH_M,HEIGHT_M,LOAD_TON,SORT_SEQ,UPDDAT,PALLET_HEIGHT_M,
                        INCH12_LT300,INCH12_GE300,INCH3_LT300,INCH3_GE300,DEFAULT_VEH_CNT,PALLET_CNT,LONG_AXIS_YN)
                       VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                    ((r.get('CARCLASS_CD') or None),
                     r.get('CARTYPE') or None,
                     float(r.get('LENGTH_M') or 0), str(r.get('WIDTH_M') or ''),
                     float(r.get('HEIGHT_M') or 0), float(r.get('LOAD_TON') or 0),
                     int(r.get('SORT_SEQ', 0)), today,
                     float(r.get('PALLET_HEIGHT_M') or 0),
                     (int(r['INCH12_LT300']) if r.get('INCH12_LT300') not in (None,'') else None),
                     (int(r['INCH12_GE300']) if r.get('INCH12_GE300') not in (None,'') else None),
                     (int(r['INCH3_LT300'])  if r.get('INCH3_LT300')  not in (None,'') else None),
                     (int(r['INCH3_GE300'])  if r.get('INCH3_GE300')  not in (None,'') else None),
                     (int(r['DEFAULT_VEH_CNT']) if r.get('DEFAULT_VEH_CNT') not in (None,'') else None),
                     (int(r['PALLET_CNT'])   if r.get('PALLET_CNT')   not in (None,'') else None),
                     (r.get('LONG_AXIS_YN') or 'N'),
                    )
                )

            # ── CMCDV.USARG1 동기화 ──────────────────────────────────────────────
            # 저장된 각 row의 tab('PS'/'HL'), carclass_cd, use_yn 기준으로
            # TMS_CARCLASS10(PS) / TMS_CARCLASS20(HL) 공통코드 USARG1을 갱신한다.
            # tab 값이 없으면 CARCLASS_CD가 등록된 CMCDKY 모두 업데이트.
            for r in rows:
                cc     = (r.get('CARCLASS_CD') or '').strip()
                use_yn = (r.get('USE_YN') or 'Y').upper()
                tab    = (r.get('tab') or '').upper()   # 'PS' or 'HL' or ''
                if not cc:
                    continue
                if tab == 'PS':
                    # PS탭 USE_YN → TMS_CARCLASS10
                    conn.execute(
                        "UPDATE CMCDV SET USARG1=? WHERE CMCDKY='TMS_CARCLASS10' AND CMCDVL=?",
                        (use_yn, cc)
                    )
                    # USE_YN_HL이 있으면 TMS_CARCLASS20도 별도 반영
                    use_yn_hl = (r.get('USE_YN_HL') or '').upper()
                    if use_yn_hl:
                        conn.execute(
                            "UPDATE CMCDV SET USARG1=? WHERE CMCDKY='TMS_CARCLASS20' AND CMCDVL=?",
                            (use_yn_hl, cc)
                        )
                elif tab == 'HL':
                    conn.execute(
                        "UPDATE CMCDV SET USARG1=? WHERE CMCDKY='TMS_CARCLASS20' AND CMCDVL=?",
                        (use_yn, cc)
                    )
                else:
                    # tab 미전달 시 해당 코드가 속한 모든 테이블 업데이트
                    existing = conn.execute(
                        "SELECT DISTINCT CMCDKY FROM CMCDV WHERE CMCDVL=? AND CMCDKY IN ('TMS_CARCLASS10','TMS_CARCLASS20')",
                        (cc,)
                    ).fetchall()
                    for erow in existing:
                        conn.execute(
                            "UPDATE CMCDV SET USARG1=? WHERE CMCDKY=? AND CMCDVL=?",
                            (use_yn, erow[0], cc)
                        )

            # ── DS_INCH12 / DS_INCH3 동기화 ─────────────────────────────────────
            # DS_VEHICLE의 INCH12_LT300/GE300, INCH3_LT300/GE300 값을
            # 배차 엔진이 실제로 읽는 DS_INCH12/DS_INCH3 테이블에 반영한다.
            # vehicle 저장 시 항상 재구성하여 두 테이블 간 불일치를 방지한다.
            conn.execute("DELETE FROM DS_INCH12")
            conn.execute("DELETE FROM DS_INCH3")
            for i, r in enumerate(rows):
                ct = (r.get('CARTYPE') or '').strip()
                if not ct:
                    continue
                sort_seq = int(r.get('SORT_SEQ', i))
                # 12인치 적재수량 → DS_INCH12
                for grm_cond, col_key in (('LT300', 'INCH12_LT300'), ('GE300', 'INCH12_GE300')):
                    val = r.get(col_key)
                    if val not in (None, ''):
                        conn.execute(
                            "INSERT INTO DS_INCH12 (CARTYPE,GRM_COND,MAX_COUNT,SORT_SEQ,UPDDAT) VALUES (?,?,?,?,?)",
                            (ct, grm_cond, int(val), sort_seq, today)
                        )
                # 3인치 적재수량 → DS_INCH3
                for grm_cond, col_key in (('LT300', 'INCH3_LT300'), ('GE300', 'INCH3_GE300')):
                    val = r.get(col_key)
                    if val not in (None, ''):
                        conn.execute(
                            "INSERT INTO DS_INCH3 (CARTYPE,GRM_COND,MAX_COUNT,SORT_SEQ,UPDDAT) VALUES (?,?,?,?,?)",
                            (ct, grm_cond, int(val), sort_seq, today)
                        )

        else:
            return jsonify({"error": "unknown table"}), 400
        conn.commit()
        return jsonify({"ok": True, "saved": len(rows)})
    except Exception as e:
        conn.rollback()
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


@app.route('/api/dispatch/simulate', methods=['POST'])
def api_dispatch_simulate():
    """
    자동배차 시뮬레이션
    body: { rqshpd_from, rqshpd_to, dptnky (optional) }
    반환: 납품처 × 날짜 단위로 묶인 배차 결과
    """
    body        = request.json or {}
    date_from   = body.get('rqshpd_from', '').replace('-','')
    date_to     = body.get('rqshpd_to',   '').replace('-','')
    dptnky_filt = body.get('dptnky', '').strip()

    conn = get_conn()

    # ── 배차기준표 로드 ──
    inch12_rows = conn.execute(
        "SELECT CARTYPE,GRM_COND,MAX_COUNT,SORT_SEQ FROM DS_INCH12 ORDER BY SORT_SEQ,GRM_COND"
    ).fetchall()
    inch3_rows = conn.execute(
        "SELECT CARTYPE,GRM_COND,MAX_COUNT,SORT_SEQ FROM DS_INCH3 ORDER BY SORT_SEQ,GRM_COND"
    ).fetchall()
    vehicle_rows = conn.execute(
        "SELECT CARTYPE,LOAD_TON,SORT_SEQ FROM DS_VEHICLE ORDER BY SORT_SEQ"
    ).fetchall()

    # 기준표 dict 변환: {cartype: {LT300: cnt, GE300: cnt}}
    def build_inch_map(rows):
        m = {}
        for r in rows:
            ct = r['CARTYPE']
            if ct not in m:
                m[ct] = {}
            m[ct][r['GRM_COND']] = r['MAX_COUNT']
        return m

    inch12_map  = build_inch_map(inch12_rows)
    inch3_map   = build_inch_map(inch3_rows)
    # 차량 순서 리스트 (순서대로 비교)
    car_order   = [dict(r) for r in vehicle_rows]  # [{CARTYPE, LOAD_TON, SORT_SEQ}]
    load_ton_map = {r['CARTYPE']: r['LOAD_TON'] for r in vehicle_rows}

    # ── 원지 아이템 조회 (UOMKEY=KG, 인치 판별 가능한 것만) ──
    where_parts = ["d.UOMKEY = 'KG'"]
    params = []
    if date_from:
        where_parts.append("h.RQSHPD >= ?"); params.append(date_from)
    if date_to:
        where_parts.append("h.RQSHPD <= ?"); params.append(date_to)
    if dptnky_filt:
        where_parts.append("h.DPTNKY = ?"); params.append(dptnky_filt)

    where_sql = "WHERE " + " AND ".join(where_parts) if where_parts else ""

    sql = f"""
        SELECT
            h.RQSHPD,
            h.DPTNKY,
            d.SVBELN,
            d.SKUKEY,
            LOWER(SUBSTR(d.SKUKEY,3,3)) AS inch_code,
            SUBSTR(d.SKUKEY,6,3)         AS grammage,
            d.QTSHPO,
            d.UOMKEY
        FROM SHPDI d
        JOIN SHPDH h ON d.SHPOKY = h.SHPOKY
        {where_sql}
        ORDER BY h.RQSHPD, h.DPTNKY, d.SVBELN
    """
    items = conn.execute(sql, params).fetchall()
    conn.close()

    INCH12_CODES = {'s11','a11','am1','sm1'}
    INCH3_CODES  = {'sr1','ir1','al1','rw1'}

    def get_inch(code):
        if code in INCH12_CODES: return '12인치'
        if code in INCH3_CODES:  return '3인치'
        return None

    def get_grm_cond(grm_str):
        try:
            g = int(grm_str)
            return 'LT300' if g < 300 else 'GE300'
        except:
            return 'GE300'

    def find_car_by_count(inch_type, grm_cond, count):
        """원지 개수로 적합 차량 찾기 (가장 작은 적재 가능 차량)"""
        imap = inch12_map if inch_type == '12인치' else inch3_map
        for car in car_order:
            ct = car['CARTYPE']
            if ct not in imap: continue
            max_cnt = imap[ct].get(grm_cond, 0)
            if max_cnt >= count:
                return ct
        # 모두 초과 시 최대 차량
        return car_order[-1]['CARTYPE'] if car_order else '18톤'

    # ── 납품처 × 날짜 × SVBELN 단위로 그루핑 ──
    from collections import defaultdict
    # key: (rqshpd, dptnky) → svbeln → items
    group = defaultdict(lambda: defaultdict(list))
    for row in items:
        key    = (row['RQSHPD'], row['DPTNKY'])
        svbeln = row['SVBELN']
        inch_t = get_inch(row['inch_code'])
        if inch_t is None:
            continue  # 인치 판별 불가 원지는 제외
        group[key][svbeln].append({
            'skukey':   row['SKUKEY'],
            'inch':     inch_t,
            'grm_cond': get_grm_cond(row['grammage']),
            'grammage': row['grammage'],
            'qty_kg':   row['QTSHPO'],
        })

    # ── 납품처 단위 배차 결과 계산 ──
    results = []
    for (rqshpd, dptnky), svbeln_dict in sorted(group.items()):
        # 납품처의 모든 SVBELN 목록과 원지 집계
        svbeln_list = list(svbeln_dict.keys())
        all_items   = [it for its in svbeln_dict.values() for it in its]

        # 인치별 그루핑
        from itertools import groupby
        inch_groups = defaultdict(list)
        for it in all_items:
            inch_groups[it['inch']].append(it)

        # 각 인치 유형별 대표 grm_cond (가장 많은 것 or GE300 우선)
        car_by_inch = {}
        total_count_by_inch = {}
        for inch_t, its in inch_groups.items():
            total_cnt = len(its)
            # 평량 조건: 과반수 이상이 GE300이면 GE300
            ge300_cnt = sum(1 for i in its if i['grm_cond'] == 'GE300')
            grm_cond  = 'GE300' if ge300_cnt > total_cnt / 2 else 'LT300'
            car_by_inch[inch_t]         = find_car_by_count(inch_t, grm_cond, total_cnt)
            total_count_by_inch[inch_t] = total_cnt

        # 최종 차량: 인치별 차량 중 더 큰 것 선택
        def car_sort_key(ct):
            for i, c in enumerate(car_order):
                if c['CARTYPE'] == ct: return i
            return 999

        if car_by_inch:
            final_car_by_inch = max(car_by_inch.items(), key=lambda x: car_sort_key(x[1]))
            final_car = final_car_by_inch[1]
        else:
            final_car = '판별불가'

        # 상차량 기준 검증 (총 KG vs 상차량)
        total_kg     = sum(it['qty_kg'] for it in all_items)
        load_ton_car = load_ton_map.get(final_car, 0)
        load_kg_cap  = load_ton_car * 1000
        over_weight  = total_kg > load_kg_cap if load_kg_cap > 0 else False

        # 상차량 초과 시 더 큰 차량으로 업그레이드
        if over_weight:
            for car in car_order:
                ct = car['CARTYPE']
                if load_ton_map.get(ct, 0) * 1000 >= total_kg:
                    final_car    = ct
                    load_ton_car = car['LOAD_TON']
                    load_kg_cap  = load_ton_car * 1000
                    over_weight  = total_kg > load_kg_cap
                    break

        results.append({
            'rqshpd':      rqshpd,
            'dptnky':      dptnky,
            'svbeln_list': svbeln_list,
            'svbeln_count':len(svbeln_list),
            'total_items': len(all_items),
            'total_kg':    round(total_kg, 2),
            'inch_summary': {k: {'count': total_count_by_inch[k], 'car': v}
                             for k, v in car_by_inch.items()},
            'final_car':   final_car,
            'load_ton':    load_ton_car,
            'load_kg_cap': load_kg_cap,
            'over_weight': over_weight,
            'weight_ratio':round(total_kg / load_kg_cap * 100, 1) if load_kg_cap > 0 else 0,
        })

    return jsonify({"ok": True, "total": len(results), "rows": results})


# ═══════════════════════════════════════════════════════════════════════════
#  PS 배차 API  (PS 제품군 원지 배차 관리)
# ═══════════════════════════════════════════════════════════════════════════

# ── 인치 판별 상수 ──────────────────────────────────────────────────────────
PS_INCH12_CODES = {'a11','ab1','ag1','am1','111','s11','i11','k11','sm1',
                   's12','i12','k12','a12',          # 12인치 추가 코드
                   'st1','su1','sh1','ks1','kc1'}    # 기타 12인치 계열
PS_INCH3_CODES  = {'ar1','ae1','aj1','al1','sr1','ir1','rw1',
                   's72','i72','s32','s31','sp2','sz2','sc2',  # 3인치(72mm) 계열
                   'sn1','sy2','b42','b41','s51','l41',        # 기타 3인치 계열
                   'ra1','rp1','rs1','rg1'}                    # R계열 3인치

def _ps_get_inch(skukey):
    """SKUKEY[2:5] 로 인치 판별 → '12인치'|'3인치'|''"""
    m = str(skukey or '').lower()[2:5]
    if m in PS_INCH12_CODES: return '12인치'
    if m in PS_INCH3_CODES:  return '3인치'
    return ''

def _ps_get_grm(skukey):
    """SKUKEY[5:8] 로 평량 구간 → 'LT300'|'GE300'"""
    try:
        g = int(str(skukey or '')[5:8])
        return 'GE300' if g >= 300 else 'LT300'
    except Exception:
        return 'LT300'

# ── 원지/판지 물리 치수 계산 헬퍼 ────────────────────────────────────────────
import math

def _ps_is_roll(skukey):
    """
    원지(롤) 여부 판별
    SKUKEY 구조: [0:2]=prefix, [2:5]=inchCode, [5:8]=gsm, [8]='-', [9:13]=width_mm, [13:17]=length_mm
    롤 원지: length_mm == '0000' (길이 무한)
    """
    sk = str(skukey or '')
    if len(sk) < 17:
        return False
    # 하이픈(-) 위치 확인: 표준은 [8]
    if sk[8:9] != '-':
        return False
    length_part = sk[13:17]
    return length_part == '0000'

def _ps_is_board(skukey):
    """
    판지(평판) 여부 판별
    SKUKEY[13:17] != '0000' 이고 숫자인 경우 → 평판 판지
    """
    sk = str(skukey or '')
    if len(sk) < 17:
        return False
    if sk[8:9] != '-':
        return False
    length_part = sk[13:17]
    return length_part != '0000' and length_part.isdigit()

def _ps_parse_skukey_dims(skukey):
    """
    SKUKEY에서 gsm(평량)과 width_mm(너비) 파싱
    Returns: (gsm:int, width_mm:int) or (None, None)
    SKUKEY 구조: [0:2]=prefix, [2:5]=inchCode, [5:8]=gsm, [8]='-', [9:13]=width_mm, [13:17]=length_mm
    """
    sk = str(skukey or '')
    if len(sk) < 17:
        return None, None
    try:
        gsm      = int(sk[5:8])
        width_mm = int(sk[9:13])
        return gsm, width_mm
    except (ValueError, IndexError):
        return None, None

def _ps_parse_board_dims(skukey):
    """
    판지(평판) SKUKEY에서 가로(mm)와 세로(mm) 파싱
    SKUKEY[9:13]=가로(mm), SKUKEY[13:17]=세로(mm)
    Returns: (width_mm:int, length_mm:int) or (None, None)
    """
    sk = str(skukey or '')
    if len(sk) < 17 or sk[8:9] != '-':
        return None, None
    try:
        w = int(sk[9:13])
        l = int(sk[13:17])
        return w, l
    except (ValueError, IndexError):
        return None, None

def _ps_calc_roll_diameter(weight_kg, gsm, width_mm):
    """
    원지 롤 직경(mm) 계산 (올바른 공식)

    원지 롤 = 종이를 원통형으로 감은 구조
    ① 종이 두께(t): t(m) = gsm(g/m²) / (ρ_paper(kg/m³) × 1000)
       - ρ_paper ≈ 1200 kg/m³ (일반 코팅지 기준)
    ② 감긴 길이(L): M(g) = gsm × W(m) × L(m)  →  L = M*1000 / (gsm × W)
    ③ 롤 직경(D): 단면적 = L × t = π × D² / 4  →  D = sqrt(4 × L × t / π)

    Returns: diameter_mm(float) or None
    """
    if not gsm or not width_mm or gsm <= 0 or width_mm <= 0:
        return None
    try:
        RHO_PAPER_KG_M3 = 1200.0            # 코팅지 밀도 (kg/m³)
        width_m  = width_mm / 1000.0
        t_m      = (gsm / 1000.0) / RHO_PAPER_KG_M3   # 종이 두께(m)
        weight_g = weight_kg * 1000.0
        # 감긴 길이(m): M(g) = gsm(g/m²) × W(m) × L(m)
        L_m  = weight_g / (gsm * width_m)
        # 롤 직경(m): L × t = π × D² / 4  →  D = sqrt(4Lt/π)
        d_sq = (4.0 * L_m * t_m) / math.pi
        if d_sq <= 0:
            return None
        return math.sqrt(d_sq) * 1000.0    # m → mm
    except Exception:
        return None

def _ps_calc_roll_cbm_per_roll(gsm, width_mm, weight_kg=600.0):
    """
    원지 롤 1개당 CBM(m³) 계산
    원지는 원통형이므로 가로/세로를 너비(width_mm)로 대체
    CBM = π × (D/2)² × W  (m³)
      D  = 롤 직경(m) — weight_kg 기준으로 역산
      W  = 롤 너비(m) = width_mm / 1000

    Returns: cbm_m3(float) or 0.0
    """
    if not gsm or not width_mm or gsm <= 0 or width_mm <= 0:
        return 0.0
    try:
        diam_mm = _ps_calc_roll_diameter(weight_kg, gsm, width_mm)
        if not diam_mm or diam_mm <= 0:
            return 0.0
        r_m = (diam_mm / 1000.0) / 2.0   # 반지름(m)
        w_m = width_mm / 1000.0           # 너비(m)
        return math.pi * r_m * r_m * w_m
    except Exception:
        return 0.0

def _ps_parse_vehicle_width(width_str):
    """
    '1.8~2.1' 또는 '2.4' 형태 파싱 → 최솟값(보수적 기준) 반환
    Returns: float (m 단위), 파싱 실패 시 2.4
    """
    try:
        s = str(width_str or '').strip()
        if '~' in s:
            return float(s.split('~')[0].strip())
        return float(s)
    except (ValueError, TypeError):
        return 2.4

def _ps_get_vehicle_info(conn):
    """DS_VEHICLE 전체 데이터 → {CARTYPE: {height_m, width_m, length_m, load_kg, pallet_height_m, effective_height_m, carclass_cd}} dict
    TMS_CARCLASS10 USARG1='Y' 인 차량만 포함 — PS배차 자동배차에 적용"""
    rows = conn.execute(
        """SELECT v.CARTYPE, v.LENGTH_M, v.WIDTH_M, v.HEIGHT_M, v.LOAD_TON, v.PALLET_HEIGHT_M, v.CARCLASS_CD
           FROM DS_VEHICLE v
           LEFT JOIN CMCDV c ON c.CMCDKY='TMS_CARCLASS10' AND c.CMCDVL=v.CARCLASS_CD
           WHERE COALESCE(c.USARG1,'Y') = 'Y'"""
    ).fetchall()
    result = {}
    for r in rows:
        h_m       = float(r['HEIGHT_M'] or 0)
        pal_h_m   = float(r['PALLET_HEIGHT_M'] or 0)
        eff_h_m   = max(0.0, h_m - pal_h_m)   # 파렛트 높이 차감 후 실제 가용 높이
        result[r['CARTYPE']] = {
            'height_m':           h_m,
            'pallet_height_m':    pal_h_m,
            'effective_height_m': eff_h_m,
            'width_m':            _ps_parse_vehicle_width(r['WIDTH_M']),
            'length_m':           float(r['LENGTH_M'] or 0),
            'load_kg':            float(r['LOAD_TON'] or 0) * 1000.0,
            'carclass_cd':        (r['CARCLASS_CD'] or '').strip(),
        }
    return result

def _ps_car_order(conn):
    """DS_VEHICLE 차량 순서 (SORT_SEQ 역순 = 18톤→1.4톤)
    TMS_CARCLASS10 USARG1='Y'(사용) 인 차량만 포함 — PS배차 자동배차에 적용"""
    rows = conn.execute(
        """SELECT v.CARTYPE, v.LOAD_TON, v.SORT_SEQ
           FROM DS_VEHICLE v
           LEFT JOIN CMCDV c ON c.CMCDKY='TMS_CARCLASS10' AND c.CMCDVL=v.CARCLASS_CD
           WHERE COALESCE(c.USARG1,'Y') = 'Y'
           ORDER BY v.SORT_SEQ DESC"""
    ).fetchall()
    return [dict(r) for r in rows]

def _ps_load_strategy(conn):
    """DS_INCH12, DS_INCH3 → {cartype: {grm_cond: max_count}}"""
    def _build(rows):
        m = {}
        for r in rows:
            ct = r['CARTYPE']
            if ct not in m: m[ct] = {}
            m[ct][r['GRM_COND']] = r['MAX_COUNT']
        return m
    inch12 = conn.execute("SELECT CARTYPE,GRM_COND,MAX_COUNT FROM DS_INCH12").fetchall()
    inch3  = conn.execute("SELECT CARTYPE,GRM_COND,MAX_COUNT FROM DS_INCH3").fetchall()
    return _build(inch12), _build(inch3)

def _ps_find_car(inch_map, inch_type, grm_cond, count, car_order, veh_info=None):
    """인치×평량×개수 → 적합한 최소 차량 타입 (없으면 LOAD_TON 입력된 최대 차량)
    car_order: SORT_SEQ DESC (큰→작은) → reversed = 작은→큰 순 탐색하여
    count를 수용하는 첫 번째(가장 작은) 차량 반환.

    veh_info 제공 시 초과 폴백을 LOAD_TON > 0 인 가장 큰 차량으로 반환
    (car_order[0]은 4.5톤 등 LOAD_TON 미입력 차량일 수 있으므로 veh_info 필터링 필요)
    """
    for car in reversed(car_order):   # 작은 차량부터 탐색
        ct  = car['CARTYPE']
        cap = inch_map.get(ct, {}).get(grm_cond, 0)
        if cap >= count:
            return ct
    # 모든 차량 초과 → LOAD_TON 입력된 가장 큰 유효 차량 반환
    if veh_info:
        for car in car_order:          # 큰→작은 순 (SORT_SEQ DESC)
            ct = car['CARTYPE']
            if veh_info.get(ct, {}).get('load_kg', 0) > 0:
                return ct
    return car_order[0]['CARTYPE'] if car_order else '판별불가'

def _ps_next_dispatch_no(conn, dt):
    """배차번호 채번: YYMMDD + 순번3자리 + T
    예) 납품일 20260622 → 260622001T, 260622002T, ...
    dt: YYYYMMDD 형식 8자리
    """
    # dt가 8자리(YYYYMMDD)면 앞 2자리(CC) 제거해 YYMMDD 6자리로 변환
    if dt and len(dt) == 8:
        yymmdd = dt[2:]          # '20260622' → '260622'
    else:
        yymmdd = (dt or '')[-6:] # 예외 처리: 마지막 6자리 사용
    # 패턴: 260622001T ~ 260622999T
    # LIKE 조건: '260622___T' (6자리 + 3자리 숫자 + T)
    pattern = f"{yymmdd}%T"
    row = conn.execute(
        "SELECT MAX(DISPATCH_NO) FROM PS_DISPATCH_H WHERE DISPATCH_NO LIKE ?",
        (pattern,)
    ).fetchone()
    last = row[0] if row and row[0] else None
    if last and len(last) == 10:
        # 마지막 4자리에서 T 제외한 3자리 숫자 파싱: '260622001T' → seq=1
        try:
            seq = int(last[6:9]) + 1
        except (ValueError, IndexError):
            seq = 1
    else:
        seq = 1
    return f"{yymmdd}{seq:03d}T"


@app.route('/api/ps-dispatch/search', methods=['GET'])
def api_ps_dispatch_search():
    """
    PS 배차용 납품문서 조회 (원지 + 판지, SKUG05='10' 제품군 고정)
    params: date_from, date_to, dptnky, shpoky, shpmty, status(dispatched/undispatched/all)
    """
    date_from = request.args.get('date_from','').replace('-','')
    date_to   = request.args.get('date_to','').replace('-','')
    dptnky    = request.args.get('dptnky','').strip()
    shpoky    = request.args.get('shpoky','').strip()
    shpmty    = request.args.getlist('shpmty')           # 출하유형 코드 복수 (201/205/206/208/221/231)
    disp_stat = request.args.get('status','all')        # all|dispatched|undispatched

    conn = get_conn()
    try:
        # SKUG05='10' (제품군 10: 원지·판지 모두 포함) 고정 필터
        # UOMKEY='KG' 조건 제거 → 원지(UOMKEY=KG)·판지(UOMKEY=R) 모두 조회
        wheres = ["TRIM(i.SKUG05)='10'", "h.WAREKY='1100'"]
        params = []
        if date_from:
            wheres.append("h.RQSHPD >= ?"); params.append(date_from)
        if date_to:
            wheres.append("h.RQSHPD <= ?"); params.append(date_to)
        if dptnky:
            # 납품처코드 또는 납품처명(BZPTN.NAME01) 기준 필터
            wheres.append("(h.DPTNKY LIKE ? OR TRIM(COALESCE(b.NAME01,'')) LIKE ?)")
            params += [f'%{dptnky}%', f'%{dptnky}%']
        if shpoky:
            wheres.append("(i.SHPOKY LIKE ? OR i.SVBELN LIKE ?)"); params += [f'%{shpoky}%', f'%{shpoky}%']
        if shpmty:   # 복수 선택: IN (?,?,...)
            ph = ','.join('?' * len(shpmty))
            wheres.append(f"h.SHPMTY IN ({ph})")
            params.extend(shpmty)

        sql = f"""
            SELECT i.SHPOKY, i.SHPOIT, i.SKUKEY, i.DESC01,
                   TRIM(COALESCE(i.SVBELN,'')) AS SVBELN,
                   i.UOMKEY, CAST(i.QTSHPO AS REAL) QTSHPO,
                   TRIM(COALESCE(i.SKUG05,'')) AS SKUG05,
                   h.DPTNKY,
                   TRIM(COALESCE(b.NAME01,'')) AS DPTNM,
                   h.DOCDAT, h.RQSHPD,
                   h.SHPMTY,
                   TRIM(COALESCE(c.CDESC1,'')) AS SHPMTY_NM,
                   SUBSTR(UPPER(i.SKUKEY),3,3) INCH_CODE,
                   COALESCE(m.GRSWGT, 0) AS GRSWGT,
                   TRIM(COALESCE(i.LOTA03,'')) AS LOTA03,
                   COALESCE(rd.QTYRCV, 0) AS UNIT_WEIGHT
            FROM SHPDI i
            JOIN SHPDH h ON i.SHPOKY=h.SHPOKY
            LEFT JOIN BZPTN b ON b.PTNRKY=h.DPTNKY AND b.PTNRTY='CT'
            LEFT JOIN CMCDV c ON c.CMCDKY='TASOTY' AND c.CMCDVL=h.SHPMTY
            LEFT JOIN SKUMA m ON m.SKUKEY=i.SKUKEY
            LEFT JOIN RECDI rd ON rd.SKUKEY=i.SKUKEY
            WHERE {' AND '.join(wheres)}
            ORDER BY h.RQSHPD, h.DPTNKY, i.SHPOKY, i.SHPOIT
        """
        rows = conn.execute(sql, params).fetchall()
        result = []
        # 배차 상태 판단:
        #   DISPATCHED=True  → SHPDI.STDLNR 채번됨(배차 생성 완료)
        #   IS_SAVED=True    → PS_DISPATCH_H STATUS='DRAFT' 저장까지 완료(배차저장)
        #   STDLNR           → 해당 가선적번호 값
        disp_info = {}   # key → {'stdlnr': str, 'is_saved': bool}
        drows = conn.execute(
            """SELECT i.SHPOKY||'|'||i.SHPOIT AS k,
                      TRIM(COALESCE(i.STDLNR,''))   AS STDLNR,
                      COALESCE(h.STATUS,'')          AS STATUS
               FROM SHPDI i
               LEFT JOIN PS_DISPATCH_H h ON h.DISPATCH_NO = TRIM(i.STDLNR)
               WHERE i.STATIT='NEW'
                 AND TRIM(i.STDLNR) != ''"""
        ).fetchall()
        for dr in drows:
            disp_info[dr['k']] = {
                'stdlnr':   dr['STDLNR'],
                'is_saved': dr['STATUS'] in ('DRAFT', 'CONFIRMED'),
            }

        for r in rows:
            key = f"{r['SHPOKY']}|{r['SHPOIT']}"
            info    = disp_info.get(key, {})
            is_disp = bool(info)            # STDLNR 채번 여부
            is_saved = info.get('is_saved', False)  # PS_DISPATCH_H 저장 여부
            stdlnr   = info.get('stdlnr', '')       # 가선적번호
            if disp_stat == 'dispatched' and not is_disp: continue
            if disp_stat == 'undispatched' and is_disp:  continue
            inch  = _ps_get_inch(r['SKUKEY'])
            grm   = _ps_get_grm(r['SKUKEY'])
            # 상품 유형 판별: 원지(roll) / 판지(board)
            sku_type = 'roll' if _ps_is_roll(r['SKUKEY']) else \
                       'board' if _ps_is_board(r['SKUKEY']) else 'other'
            # 판지(R 단위)의 KG 환산: QTSHPO(R) × GRSWGT(kg/R)
            qtshpo  = float(r['QTSHPO'] or 0)
            grswgt  = float(r['GRSWGT'] or 0)
            uomkey  = (r['UOMKEY'] or '').strip()
            # RECDI 테이블 기반 원지 단일 롤 중량 (0이면 fallback 600)
            unit_weight = float(r['UNIT_WEIGHT'] or 0)
            # GRSWGT가 없으면 SKUKEY에서 GSM×치수로 역산 (판지 전용)
            if grswgt <= 0 and uomkey == 'R' and _ps_is_board(r['SKUKEY']):
                gsm_val, _ = _ps_parse_skukey_dims(r['SKUKEY'])
                w_v, l_v   = _ps_parse_board_dims(r['SKUKEY'])
                if gsm_val and w_v and l_v:
                    # 기본 리머(500장) 기준: GRSWGT = 500 × gsm/1e6 × w_mm × l_mm (kg)
                    gsm_val = float(gsm_val)
                    grswgt = round(500.0 * (gsm_val / 1_000_000.0) * float(w_v) * float(l_v) / 1000.0, 2)
            if uomkey == 'R' and grswgt > 0:
                kg_weight = round(qtshpo * grswgt, 2)
            elif uomkey == 'KG':
                kg_weight = qtshpo
            else:
                kg_weight = qtshpo  # 기타 단위는 그대로

            # ── 원지 롤 수 계산 (RECDI 기반 단일 롤 중량 우선, fallback 600kg) ──
            # UOMKEY='KG': KG_WEIGHT(=QTSHPO) ÷ UNIT_WEIGHT(롤당kg), 올림
            # UOMKEY='R': QTSHPO 그대로 (이미 롤 단위)
            ROLL_SINGLE_KG = unit_weight if unit_weight > 0 else 600.0
            if uomkey == 'R' and sku_type == 'roll':
                roll_count_calc = int(qtshpo)  # R 단위는 이미 롤 수
            else:
                roll_count_calc = math.ceil(kg_weight / ROLL_SINGLE_KG) if kg_weight > 0 else 0

            # ── 원지(roll) CBM 계산 ───────────────────────────────────────
            # 원지는 원통형: 가로/세로를 너비(width_mm)로 대체
            # CBM_per_roll = π × (D/2)² × W  (D=직경, W=너비, ROLL_SINGLE_KG 기준)
            # 총 CBM = CBM_per_roll × 롤수
            roll_cbm = 0.0
            if sku_type == 'roll':
                gsm_v, w_v = _ps_parse_skukey_dims(r['SKUKEY'])
                if gsm_v and w_v:
                    cbm_per_roll = _ps_calc_roll_cbm_per_roll(gsm_v, w_v, ROLL_SINGLE_KG)
                    roll_cnt     = roll_count_calc if roll_count_calc > 0 else 0
                    roll_cbm     = round(cbm_per_roll * roll_cnt, 4)

            # ── 판지(board) CBM 계산 ──────────────────────────────────────
            # 판지는 직육면체(묶음): 가로 × 세로 × 높이(두께 역산)
            # 높이 역산: GRSWGT(kg/묶음) ÷ (gsm/1e6 × w_mm × l_mm × density)
            #   density = 1200 kg/m³ (코팅지 기준)
            # 총 CBM = 1묶음 CBM × 묶음 수 (묶음 수 = qtshpo, UOMKEY='R')
            board_cbm = 0.0
            if sku_type == 'board':
                bw_mm, bl_mm = _ps_parse_board_dims(r['SKUKEY'])
                bgsm, _      = _ps_parse_skukey_dims(r['SKUKEY'])
                if bw_mm and bl_mm and bgsm and bgsm > 0 and grswgt > 0:
                    PAPER_DENSITY = 1200.0          # kg/m³
                    area_m2       = (bw_mm / 1000.0) * (bl_mm / 1000.0)
                    # 1장 두께(m) = gsm(g/m²) / (density(kg/m³) × 1000)
                    t_sheet_m     = (bgsm / 1000.0) / PAPER_DENSITY
                    # 1묶음 장수 = GRSWGT_g / (gsm/1e6 × area_mm²)
                    grswgt_g      = grswgt * 1000.0
                    area_mm2      = float(bw_mm) * float(bl_mm)
                    gsm_per_mm2   = bgsm / 1_000_000.0
                    sheets_per_b  = grswgt_g / (gsm_per_mm2 * area_mm2) if gsm_per_mm2 * area_mm2 > 0 else 0
                    # 1묶음 높이(m) = 장수 × 1장 두께
                    h_per_b_m     = sheets_per_b * t_sheet_m
                    # 1묶음 CBM = 가로m × 세로m × 높이m
                    cbm_per_b     = area_m2 * h_per_b_m
                    # 묶음 수 = UOMKEY='R' → QTSHPO(R 수량)
                    bundles       = qtshpo if uomkey == 'R' else (kg_weight / grswgt if grswgt > 0 else 0)
                    board_cbm     = round(cbm_per_b * bundles, 4)

            result.append({
                'SHPOKY':    r['SHPOKY'],
                'SVBELN':    r['SVBELN'],
                'SHPOIT':    r['SHPOIT'],
                'SKUKEY':    r['SKUKEY'],
                'DESC01':    r['DESC01'],
                'UOMKEY':    uomkey,
                'QTSHPO':    qtshpo,
                'GRSWGT':    grswgt,       # kg/R (묶음당 중량)
                'KG_WEIGHT': kg_weight,    # 실제 KG 중량 (배차 엔진 사용)
                'ROLL_CBM':    roll_cbm,       # 원지 총 CBM (원통형 계산, roll만 값 있음)
                'BOARD_CBM':   board_cbm,     # 판지 총 CBM (직육면체 역산, board만 값 있음)
                'UNIT_WEIGHT': unit_weight,   # 단일 롤 중량 kg (RECDI.QTYRCV, 0=미등록)
                'ROLL_COUNT':  roll_count_calc if sku_type == 'roll' else 0,  # 롤 수 (원지만)
                'SKUG05':    r['SKUG05'],
                'SKU_TYPE':  sku_type,   # 'roll'|'board'|'other'
                'DPTNKY':    r['DPTNKY'],
                'DPTNM':     r['DPTNM'],
                'DOCDAT':    r['DOCDAT'],
                'RQSHPD':    r['RQSHPD'],
                'SHPMTY':    r['SHPMTY'],
                'SHPMTY_NM': r['SHPMTY_NM'],
                'INCH':      '' if sku_type == 'board' else inch,
                'GRM_COND':  grm,
                'DISPATCHED': is_disp,
                'IS_SAVED':  is_saved,    # 배차저장 완료 여부 (PS_DISPATCH_H 존재)
                'STDLNR':    stdlnr,      # 가선적번호 (배차저장 시 채번)
                'LOTA03':    r['LOTA03'],  # 포장타입
            })
        return jsonify({"ok": True, "total": len(result), "rows": result})
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


def _calc_board_stack_height_m(item, skuma_map):
    """
    판지 1속(ream/묶음) 단위 적재 높이(m) 계산 — 모듈 레벨 공유 함수

    ■ 계산 원리:
      - 1속(ream) = GRSWGT(kg)의 묶음
      - 1장 두께(mm) = GSM / 1,000,000 / PAPER_DENSITY(0.0012 g/mm³)
      - 1속 장수 = GRSWGT_g / (gsm/1e6 × W_mm × L_mm)
      - 1속 높이(m) = 장수 × 장두께(mm) / 1000

    ■ 주의: 아이템의 QTSHPO(속 수량)와 무관하게 항상 '1속의 높이'만 반환.
      실제 배차 시 판지는 여러 속을 차량 바닥에 나란히 배치하므로
      전체 수량을 수직 합산하면 수 미터로 과산정됨.
      STEP-1 그룹분할 및 STEP-2 치수검사에서 '1속 높이'를 사용해
      적재 가능 여부만 판단한다.
    """
    sk     = item.get('SKUKEY', '')
    sm     = skuma_map.get(sk, {})
    grswgt = sm.get('grswgt', 0)   # kg/속
    w_mm   = sm.get('w_mm', 0)
    l_mm   = sm.get('l_mm', 0)

    if grswgt <= 0 or w_mm <= 0 or l_mm <= 0:
        w2, l2 = _ps_parse_board_dims(sk)
        if w2 and l2:
            w_mm, l_mm = w2, l2

    if grswgt <= 0 or w_mm <= 0 or l_mm <= 0:
        return 0.0

    gsm, _ = _ps_parse_skukey_dims(sk)
    if not gsm or gsm <= 0:
        return 0.0

    PAPER_DENSITY_G_PER_MM3 = 0.0012
    t_sheet_mm  = (gsm / 1_000_000.0) / PAPER_DENSITY_G_PER_MM3  # 1장 두께(mm)

    grswgt_g    = grswgt * 1000.0                     # 1속 무게(g)
    area_mm2    = float(w_mm) * float(l_mm)           # 1장 면적(mm²)
    gsm_per_mm2 = gsm / 1_000_000.0                   # 단위면적당 무게(g/mm²)
    if gsm_per_mm2 * area_mm2 <= 0:
        return 0.0
    sheets_per_bundle = grswgt_g / (gsm_per_mm2 * area_mm2)  # 1속 장수
    bundle_height_mm  = sheets_per_bundle * t_sheet_mm        # 1속 높이(mm)
    return bundle_height_mm / 1000.0  # 1속 높이(m) — 수량 합산 없음


def _ps_get_item_cbm(item, skuma_map):
    """
    아이템 1건의 총 CBM(m³) 반환 — 모듈 레벨 공유 함수
    우선순위:
      1) SKUMA.CUBICM > 0  → CUBICM × 묶음 수
      2) 치수(w_mm × l_mm × 두께 역산) × 묶음 수
      3) 계산 불가 → 0.0
    """
    sk     = item.get('SKUKEY', '')
    sm     = skuma_map.get(sk, {})
    grswgt = sm.get('grswgt', 0)
    w_mm   = sm.get('w_mm', 0)
    l_mm   = sm.get('l_mm', 0)

    if not w_mm or not l_mm:
        w2, l2 = _ps_parse_board_dims(sk)
        w_mm = w2 or w_mm
        l_mm = l2 or l_mm

    if item.get('KG_WEIGHT') is not None:
        qty_kg = float(item['KG_WEIGHT'])
    else:
        uom    = (item.get('UOMKEY') or '').strip()
        qtshpo = float(item.get('QTSHPO') or 0)
        qty_kg = qtshpo * grswgt if (uom == 'R' and grswgt > 0) else qtshpo
    if qty_kg <= 0:
        return 0.0

    bundles = (qty_kg / grswgt) if grswgt > 0 else 1.0

    cubicm_per_bundle = sm.get('cubicm', 0.0)
    if cubicm_per_bundle and cubicm_per_bundle > 0:
        return cubicm_per_bundle * bundles

    if w_mm > 0 and l_mm > 0 and grswgt > 0:
        gsm, _ = _ps_parse_skukey_dims(sk)
        if gsm and gsm > 0:
            PAPER_DENSITY  = 0.0012
            t_sheet_mm     = (gsm / 1_000_000.0) / PAPER_DENSITY
            gsm_per_mm2    = gsm / 1_000_000.0
            area_mm2       = float(w_mm) * float(l_mm)
            if gsm_per_mm2 * area_mm2 > 0:
                grswgt_g          = grswgt * 1000.0
                sheets_per_bundle = grswgt_g / (gsm_per_mm2 * area_mm2)
                bundle_h_mm       = sheets_per_bundle * t_sheet_mm
                bundle_cbm        = (w_mm / 1000.0) * (l_mm / 1000.0) * (bundle_h_mm / 1000.0)
                return bundle_cbm * bundles

    return 0.0


@app.route('/api/ps-dispatch/auto', methods=['POST'])
def api_ps_dispatch_auto():
    """
    자동배차 실행 (강화 버전)
    body: { items: [{SHPOKY,SHPOIT,SKUKEY,DESC01,QTSHPO,DPTNKY,DPTNM,RQSHPD,...}] }

    ■ 원지(롤, SKUKEY[9:13]='0000'):
      1) 인치×평량 기준 차량 선정 (DS_INCH12/DS_INCH3 전략)
      2) 중량 초과 시 차량 업그레이드 or 분할
      3) 배차 후 잔여 적재 여유(load_spare_kg) > 0 이면:
         - 동일 납품처×납품일의 미배차 원지 중 여유 중량 이하인 것 추가 배차
         - 추가 적재 시 2단 적재 높이 검사:
           D(mm) = sqrt(4×W_g / (π × gsm/1e6 × width_mm))
           2단 높이(m) = 2×D/1000 → 차량 HEIGHT_M 초과 불가

    ■ 판지(평판, SKUKEY[9:13]!='0000'):
      1순위) 중량 기준: LOAD_TON×1000 >= 납품수량(KG) 인 최소 차량에 배차
      2순위) 치수 최적화:
         - 판지 크기: SKUKEY[9:13]=가로(mm), SKUKEY[13:17]=세로(mm)
         - 적재 높이: 건당 중량(GRSWGT)과 GSM으로 역산
           h_per_sheet(mm) = GRSWGT*1000 / (gsm/1e6 × w_mm × l_mm)
           total_height(m) = sum(h_per_sheet × 매수) / 1000
         - 차량 치수(LENGTH_M, WIDTH_M, HEIGHT_M)와 비교하여 초과 시 차량 업그레이드
    → 납품처×납품일 단위로 차량 배차 결과 반환 (미저장, preview)
    """
    from datetime import datetime
    from collections import defaultdict

    body  = request.json or {}
    items = body.get('items', [])
    if not items:
        return jsonify({"error": "items 없음"}), 400

    conn = get_conn()
    try:
        car_order             = _ps_car_order(conn)
        inch12_map, inch3_map = _ps_load_strategy(conn)
        veh_info              = _ps_get_vehicle_info(conn)  # {CARTYPE: {height_m, width_m, length_m, load_kg}}

        # SKUMA 판지 치수 정보 로드 (SKUKEY → GRSWGT, ASKL04, ASKL05)
        skuma_rows = conn.execute(
            "SELECT SKUKEY, GRSWGT, ASKL04, ASKL05 FROM SKUMA WHERE MTYPE='P'"
        ).fetchall()
        skuma_map = {}  # SKUKEY → {grswgt, w_mm, l_mm}
        for sr in skuma_rows:
            try:
                w = int(sr['ASKL04']) if sr['ASKL04'] and str(sr['ASKL04']).strip().isdigit() else 0
                l = int(sr['ASKL05']) if sr['ASKL05'] and str(sr['ASKL05']).strip().isdigit() else 0
            except Exception:
                w, l = 0, 0
            skuma_map[sr['SKUKEY']] = {
                'grswgt': float(sr['GRSWGT'] or 0),
                'w_mm':   w,
                'l_mm':   l,
            }

        # ── 차량 정렬 헬퍼 (sort_key 작을수록 큰 차량, DS_VEHICLE.SORT_SEQ DESC 정렬)
        def _sort_key(ct):
            for i, c in enumerate(car_order):
                if c['CARTYPE'] == ct: return i
            return 999

        # LOAD_TON이 실제 입력된 차량만 배차 대상으로 사용
        _valid_cars = [c for c in car_order if veh_info.get(c['CARTYPE'], {}).get('load_kg', 0) > 0]

        def _upgrade_by_kg(cur_car, need_kg):
            """
            중량 기준으로 need_kg를 수용할 수 있는 최소 차량 반환 (하위 호환용 — 내부에서 _best_fit_car 호출)
            """
            return _best_fit_car(need_kg)

        def _best_fit_car(need_kg):
            """
            적재 효율 최적 차량 선정 — 납품처별 중량합계 기준
            전략:
              1) need_kg 를 수용 가능한 차량 중 (load_kg >= need_kg)
                 → 첨부율(= need_kg / load_kg)이 가장 높은(꽉 채우는) 차량 선택
              2) 모든 차량이 need_kg 초과 → 가장 큰 유효 차량 반환
                 (이후 분할 로직에서 처리)
            - _valid_cars: LOAD_TON이 입력된 차량만, SORT_SEQ DESC(큰→작은) 순
            """
            best_car   = None
            best_ratio = -1.0   # 첨부율 (0.0 ~ 1.0): 높을수록 효율적

            for car in _valid_cars:          # 큰 차량 → 작은 차량 순
                ct      = car['CARTYPE']
                cap_kg  = veh_info.get(ct, {}).get('load_kg', 0)
                if cap_kg <= 0:
                    continue
                if cap_kg >= need_kg:
                    ratio = need_kg / cap_kg  # 첨부율
                    if ratio > best_ratio:    # 더 높은 첨부율 = 더 꽉 채우는 차량
                        best_ratio = ratio
                        best_car   = ct

            if best_car is None:
                # 모든 차량 초과 → 가장 큰 유효 차량 (분할 배차 진입)
                best_car = _valid_cars[0]['CARTYPE'] if _valid_cars else '판별불가'
            return best_car

        # 단일 원지 롤 표준 중량 (직경·높이 계산 기준)
        # QTSHPO는 아이템 전체 합산 KG → 개별 롤 직경 계산 시 단일 롤 중량 사용
        ROLL_SINGLE_KG = 600.0

        def _roll_single_diam_mm(skukey):
            """
            단일 롤 직경(mm) 계산 — 표준 중량 ROLL_SINGLE_KG 기준
            Returns: diam_mm(float) or None
            """
            gsm, width_mm = _ps_parse_skukey_dims(skukey)
            if not gsm or not width_mm:
                return None
            return _ps_calc_roll_diameter(ROLL_SINGLE_KG, gsm, width_mm)

        def _check_height_ok(item, cartype):
            """
            원지 롤 2단 적재 높이 검사
            - 직경 계산은 단일 롤 ROLL_SINGLE_KG(600kg) 기준 (QTSHPO는 여러 롤 합계)
            - 2단 높이 = 직경 × 2 ≤ 차량 effective_height_m
            Returns: (ok:bool, height_2tier_m:float)
            """
            sk = item.get('SKUKEY', '')
            if not _ps_is_roll(sk):
                return True, 0.0
            diam_mm = _roll_single_diam_mm(sk)
            if diam_mm is None:
                return True, 0.0   # 계산 불가 시 허용 처리
            height_2tier_m = 2.0 * diam_mm / 1000.0
            # 파렛트 높이를 차감한 실제 가용 높이로 검사
            car_h = veh_info.get(cartype, {}).get('effective_height_m', 99.0)
            return height_2tier_m <= car_h, height_2tier_m

        # _calc_board_stack_height_m 는 모듈 레벨 함수로 이동됨 (api_dcon_auto 공유)

        def _check_board_dims_ok(items_list, cartype, skuma_map):
            """
            판지 치수 검사 (차량 길이/너비/높이 모두 확인)
            Returns: (ok:bool, max_w_mm, max_l_mm, max_bundle_height_m)

            ■ 높이 검사 방식:
              판지는 여러 속을 차량 바닥에 나란히 배치하므로 전체 수량 합산이 아닌
              '아이템 중 최대 1속(ream) 높이'가 차량 높이를 초과하는지만 검사.
              (_calc_board_stack_height_m 이 1속 높이를 반환하므로 max 사용)
            """
            ci = veh_info.get(cartype, {})
            # 파렛트 높이를 차감한 실제 가용 높이로 검사
            car_h = ci.get('effective_height_m', 99.0)
            car_w = ci.get('width_m', 99.0) * 1000.0   # mm
            car_l = ci.get('length_m', 99.0) * 1000.0  # mm

            max_w, max_l = 0, 0
            max_bundle_h = 0.0      # 아이템 중 최대 1속 높이
            for it in items_list:
                sk = it.get('SKUKEY', '')
                sm = skuma_map.get(sk, {})
                w  = sm.get('w_mm', 0)
                l  = sm.get('l_mm', 0)
                if not w or not l:
                    w2, l2 = _ps_parse_board_dims(sk)
                    w, l = (w2 or 0), (l2 or 0)
                max_w = max(max_w, w)
                max_l = max(max_l, l)
                bundle_h = _calc_board_stack_height_m(it, skuma_map)  # 1속 높이
                max_bundle_h = max(max_bundle_h, bundle_h)

            width_ok  = (max_w == 0 or max_w <= car_w)
            length_ok = (max_l == 0 or max_l <= car_l)
            height_ok = (max_bundle_h == 0.0 or max_bundle_h <= car_h)
            return (width_ok and length_ok and height_ok), max_w, max_l, max_bundle_h

        # ──────────────────────────────────────────────────────────────────
        # CBM 관련 헬퍼 함수: _ps_get_item_cbm 은 모듈 레벨 함수로 이동됨
        # ──────────────────────────────────────────────────────────────────

        def _ps_get_car_cbm(cartype, veh_info):
            """
            차량 적재함 CBM(m³) = LENGTH_M × WIDTH_M × effective_height_m
            WIDTH_M이 범위("1.8~2.1")이면 _ps_parse_vehicle_width 처리된 값 사용
            HEIGHT는 파렛트 차감 후 effective_height_m 사용
            """
            ci = veh_info.get(cartype, {})
            l  = ci.get('length_m', 0.0)
            w  = ci.get('width_m',  0.0)   # 이미 _ps_parse_vehicle_width 처리됨
            h  = ci.get('effective_height_m', 0.0)
            if l <= 0 or w <= 0 or h <= 0:
                return 0.0
            return l * w * h

        def _upgrade_by_cbm(cur_car, need_cbm, veh_info, car_order):
            """
            CBM 기준으로 need_cbm을 수용할 수 있는 최소 차량 반환
            - car_order: 큰 차량(18톤)→작은 차량(1.4톤) 순
            - reversed → 작은 차량부터 탐색, 적재 CBM >= need_cbm인 최소 차량
            - 모두 초과하면 가장 큰 차량 반환
            """
            best = None
            for car in reversed(_valid_cars):
                ct    = car['CARTYPE']
                c_cbm = _ps_get_car_cbm(ct, veh_info)
                if c_cbm > 0 and c_cbm >= need_cbm:
                    best = ct
            if best is None:
                best = _valid_cars[0]['CARTYPE'] if _valid_cars else cur_car
            return best

        # ──────────────────────────────────────────────────────────────────
        # skuma_map 에 CUBICM 컬럼 추가 로드 (기존 skuma_map 보완)
        # ──────────────────────────────────────────────────────────────────
        skuma_cubicm_rows = conn.execute(
            "SELECT SKUKEY, CUBICM FROM SKUMA WHERE MTYPE='P'"
        ).fetchall()
        for sc in skuma_cubicm_rows:
            sk = sc['SKUKEY']
            if sk in skuma_map:
                skuma_map[sk]['cubicm'] = float(sc['CUBICM'] or 0)
            else:
                skuma_map[sk] = {'grswgt': 0, 'w_mm': 0, 'l_mm': 0,
                                 'cubicm': float(sc['CUBICM'] or 0)}

        # ── 납품처×납품일 그룹핑
        groups = defaultdict(list)
        for it in items:
            key = (it.get('DPTNKY',''), it.get('DPTNM',''), it.get('RQSHPD',''))
            groups[key].append(it)

        # ──────────────────────────────────────────────────────────────────
        # ★ 납품처 TMS 정보 일괄 조회 (DEADLINE_TIME / FORKLIFT_YN / MAX_TON)
        #   dptnky 목록을 한 번에 SELECT하여 딕셔너리로 캐싱
        # ──────────────────────────────────────────────────────────────────
        all_dptnky_list = list({k[0] for k in groups.keys() if k[0]})
        ptnr_info = {}   # {dptnky: {deadline_time, forklift_yn, max_ton, max_ton_label, max_load_kg}}
        if all_dptnky_list:
            placeholders = ','.join('?' * len(all_dptnky_list))
            ptnr_rows = conn.execute(
                f"SELECT PTNRKY, DEADLINE_TIME, FORKLIFT_YN, MAX_TON "
                f"FROM BZPTN_DETAIL "
                f"WHERE PTNRKY IN ({placeholders}) AND PTNRTY='CT' AND DEL_YN!='Y'",
                all_dptnky_list
            ).fetchall()
            # TMS_CARCLASS10 코드 → 톤수명 매핑 로드 (PS 제품군)
            carclass_rows = conn.execute(
                "SELECT CMCDVL, CDESC1 FROM CMCDV WHERE CMCDKY='TMS_CARCLASS10'"
            ).fetchall()
            carclass_map = {r['CMCDVL']: r['CDESC1'] for r in carclass_rows}
            # 톤수명 → DS_VEHICLE.LOAD_TON(kg) 매핑
            # (DS_VEHICLE.CARTYPE = TMS_CARCLASS10.CDESC1)
            veh_load_map = {c['CARTYPE']: veh_info.get(c['CARTYPE'], {}).get('load_kg', 0)
                            for c in car_order}
            for pr in ptnr_rows:
                dk = pr['PTNRKY']
                mt = (pr['MAX_TON'] or '').strip()
                mt_label = carclass_map.get(mt, mt) if mt else ''
                # 최대톤수 코드 → 차량명 → 허용 최대 load_kg
                mt_load_kg = veh_load_map.get(mt_label, 0) if mt_label else 0
                ptnr_info[dk] = {
                    'deadline_time': (pr['DEADLINE_TIME'] or '').strip(),
                    'forklift_yn':   (pr['FORKLIFT_YN'] or '').strip(),
                    'max_ton':       mt,
                    'max_ton_label': mt_label,
                    'max_load_kg':   mt_load_kg,
                }

        # 자동배차 실행 시각 (납기시간 비교용)
        now_dt = datetime.now()
        now_hhmm = now_dt.strftime('%H:%M')

        def _cap_valid_cars(dptnky):
            """
            납품처 MAX_TON 기준으로 배차 허용 차량 목록 반환
            - MAX_TON 미설정 또는 해당 차량 LOAD_TON 미확인 → _valid_cars 그대로 반환
            - MAX_TON 설정 → 해당 톤수 이하(load_kg ≤ max_load_kg) 차량만 반환
              단, 필터 결과가 빈 경우 _valid_cars 그대로 (예외 방지)
            """
            pi = ptnr_info.get(dptnky, {})
            max_load_kg = pi.get('max_load_kg', 0)
            if max_load_kg <= 0:
                return _valid_cars   # 제한 없음
            filtered = [c for c in _valid_cars
                        if veh_info.get(c['CARTYPE'], {}).get('load_kg', 0) <= max_load_kg]
            return filtered if filtered else _valid_cars   # 빈 경우 원본 반환

        def _build_ptnr_notes(dptnky, rqshpd, assigned_car):
            """
            납품처 TMS 조건 체크 노트 생성
            ① 최대톤수: 배정 차량 > 허용 톤수 → 경고
            ② 지게차: FORKLIFT_YN 정보 표시
            ③ 납기시간: DEADLINE_TIME vs 당일 now_hhmm 비교 → 초과 경고
            Returns: (notes:list[str], warnings:list[str])
              notes   = 정보성 메시지
              warnings = 경고 메시지 (모달에서 강조 표시)
            """
            pi = ptnr_info.get(dptnky, {})
            notes, warnings = [], []
            if not pi:
                return notes, warnings

            # ① 최대톤수 체크
            max_ton_label = pi.get('max_ton_label', '')
            max_load_kg   = pi.get('max_load_kg', 0)
            if max_ton_label:
                assigned_load_kg = veh_info.get(assigned_car, {}).get('load_kg', 0)
                if max_load_kg > 0 and assigned_load_kg > max_load_kg:
                    warnings.append(
                        f"[최대톤수초과] 납품처 허용 {max_ton_label}({max_load_kg:.0f}kg) < "
                        f"배정차량 {assigned_car}({assigned_load_kg:.0f}kg) — 수동확인필요"
                    )
                else:
                    notes.append(
                        f"[최대톤수OK] 납품처 허용 {max_ton_label}({max_load_kg:.0f}kg) / "
                        f"배정 {assigned_car}({assigned_load_kg:.0f}kg)"
                    )

            # ② 지게차 여부 표시
            forklift = pi.get('forklift_yn', '')
            if forklift == 'Y':
                notes.append("[지게차] ✅ 납품처 지게차 사용가능")
            elif forklift == 'N':
                notes.append("[지게차] ⚠ 납품처 지게차 없음 — 수작업 하차 필요")

            # ③ 납기시간 체크 (당일 납품 기준)
            deadline = pi.get('deadline_time', '')
            if deadline:
                # 납품일(RQSHPD=YYYYMMDD)과 현재 날짜가 같을 때만 시간 비교
                today_str = now_dt.strftime('%Y%m%d')
                rqshpd_str = (rqshpd or '').replace('-', '')
                if rqshpd_str == today_str:
                    if now_hhmm > deadline:
                        warnings.append(
                            f"[납기시간초과] 납기시간 {deadline} < 현재시각 {now_hhmm} "
                            f"— 당일 납기 불가 가능성, 수동확인필요"
                        )
                    else:
                        notes.append(
                            f"[납기시간OK] 납기시간 {deadline} / 현재시각 {now_hhmm} (당일 납기 가능)"
                        )
                else:
                    # 납품일이 당일이 아닌 경우: 납기시간 정보만 표시
                    notes.append(f"[납기시간] 납품처 납기시간 {deadline} (납품일 {rqshpd_str})")

            return notes, warnings

        all_vehicles = []   # 최종 배차 결과

        for (dptnky, dptnm, rqshpd), grp_items in sorted(groups.items()):

            # ── 아이템 분류: 원지(롤) vs 판지(평판) vs 기타
            # [FIX] UOMKEY='R' 단독 조건 제거 — Ream(속) 포장 판지도 UOMKEY='R'을 사용하므로
            #   반드시 SKUKEY[0]=='H'(원지 접두어) 또는 SKUKEY[13:17]=='0000'(롤길이) 조건을
            #   함께 확인해야 합니다. SKUKEY[0]=='F'(판지) 이면서 UOMKEY='R'인 경우는
            #   판지 Ream 포장으로 판단합니다.
            def _is_roll_it(it):
                sk  = (it.get('SKUKEY') or '').strip()
                uom = (it.get('UOMKEY') or '').strip()
                # SKUKEY 기반 원지 판별이 최우선 (길이파트=='0000' → 롤)
                if _ps_is_roll(sk): return True
                # UOMKEY='R' + SKUKEY[0]=='H'(원지 접두어)인 경우만 롤로 인정
                if uom == 'R' and len(sk) > 0 and sk[0] == 'H': return True
                return False
            def _is_board_it(it):
                if _is_roll_it(it): return False
                return _ps_is_board(it.get('SKUKEY', ''))
            roll_items  = [it for it in grp_items if _is_roll_it(it)]
            board_items = [it for it in grp_items if _is_board_it(it)]
            other_items = [it for it in grp_items
                           if not _is_roll_it(it)
                           and not _is_board_it(it)]

            # 이 납품처에 적용될 허용 차량 목록 (MAX_TON 필터 적용)
            # _valid_cars를 납품처별 제한 목록으로 임시 교체 → 내부 헬퍼 함수가 자동 적용
            _orig_valid_cars = _valid_cars
            _valid_cars = _cap_valid_cars(dptnky)
            _max_ton_applied = (len(_valid_cars) < len(_orig_valid_cars))

            # =================================================================
            # ① 원지(롤) 배차
            # 우선순위: ①롤 수 vs MAX_COUNT → ②중량 → ③CBM
            # =================================================================

            # ── 원지 롤 수 계산 헬퍼 (단일 아이템) ──────────────────────────
            def _item_roll_count(it):
                """아이템 1건의 실제 롤 수 반환
                - UOMKEY='KG': KG_WEIGHT(또는 QTSHPO) ÷ UNIT_WEIGHT(RECDI 기반, fallback 600), 올림
                - UOMKEY='R' + is_roll: QTSHPO 그대로 (원지 R단위)
                - ROLL_COUNT가 이미 계산된 경우 해당 값 사용
                """
                sk  = (it.get('SKUKEY') or '').strip()
                uom = (it.get('UOMKEY') or '').strip()
                if uom == 'R' and _ps_is_roll(sk):
                    return int(it.get('QTSHPO') or 0)
                # UNIT_WEIGHT: RECDI 기반 단일 롤 중량, 없으면 600 fallback
                unit_w = float(it.get('UNIT_WEIGHT') or 0)
                single_kg = unit_w if unit_w > 0 else ROLL_SINGLE_KG
                kg = float(it.get('KG_WEIGHT') or it.get('QTSHPO') or 0)
                return math.ceil(kg / single_kg) if kg > 0 else 0

            if roll_items:
                # ── 전체 롤 수 집계 (인치×평량별) ────────────────────────
                # 아이템 건수가 아닌 실제 롤 수(KG÷600)를 기준으로 MAX_COUNT 비교
                inch12_rolls = defaultdict(int)   # {grm_cond: total_roll_count}
                inch3_rolls  = defaultdict(int)
                for it in roll_items:
                    inch = _ps_get_inch(it.get('SKUKEY',''))
                    grm  = _ps_get_grm(it.get('SKUKEY',''))
                    rc   = _item_roll_count(it)
                    if inch == '12인치': inch12_rolls[grm] += rc
                    elif inch == '3인치': inch3_rolls[grm]  += rc

                # ─────────────────────────────────────────────────────────
                # [1순위] 롤 수 기준 최소 차량 선정
                #   _ps_find_car에 실제 롤 수(rc)를 전달하여 MAX_COUNT와 비교
                # ─────────────────────────────────────────────────────────
                car_candidates = []
                for grm, rc in inch12_rolls.items():
                    car_candidates.append(_ps_find_car(inch12_map, '12인치', grm, rc, car_order, veh_info))
                for grm, rc in inch3_rolls.items():
                    car_candidates.append(_ps_find_car(inch3_map, '3인치', grm, rc, car_order, veh_info))

                base_car = (min(car_candidates, key=_sort_key)
                            if car_candidates
                            else (car_order[-1]['CARTYPE'] if car_order else '판별불가'))

                total_kg = sum(float(it.get('QTSHPO', 0)) for it in roll_items)

                # ─────────────────────────────────────────────────────────
                # [2순위] 중량 기준 첨부율 최적 차량 선정
                # ─────────────────────────────────────────────────────────
                kg_best = _best_fit_car(total_kg)
                # 롤 수 기준 차량이 중량 기준 차량보다 크면 롤 수 기준 유지 (안전)
                if _sort_key(kg_best) < _sort_key(base_car):
                    base_car = kg_best

                # 인치 기준 vs 중량 기준 → 더 큰(sort_key 작은) 차량 선택
                if car_candidates:
                    inch_car = min(car_candidates, key=_sort_key)
                    if _sort_key(inch_car) < _sort_key(base_car):
                        base_car = inch_car

                # ────────────────────────────────────────────────────────────
                # ★ 납품처 MAX_TON 기반 최대차량 결정
                #   _valid_cars = _cap_valid_cars(dptnky) 가 이미 적용된 상태
                #   → big_car 는 납품처 허용 톤수 이하의 최대 유효 차량
                # ────────────────────────────────────────────────────────────
                big_car = _valid_cars[0]['CARTYPE'] if _valid_cars else base_car
                big_cap = veh_info.get(big_car, {}).get('load_kg', 0) or 99_999_999.0
                # 가장 큰 유효 차량의 인치별 MAX_COUNT (분할 기준)
                big_inch12_max = inch12_map.get(big_car, {})  # {grm_cond: max_count}
                big_inch3_max  = inch3_map.get(big_car, {})

                # ──────────────────────────────────────────────────────────
                # [전처리] 납품분할(ITEM SPLIT):
                #   단일 아이템 KG 이 big_cap 을 초과하거나,
                #   단일 아이템 롤 수 가 big_car 의 MAX_COUNT 를 초과하는 경우
                #   해당 아이템을 여러 청크(가상 분할 아이템)로 미리 분해합니다.
                #   - KG 분할: chunk_kg = min(big_cap, per_roll_kg × max_rolls_per_car)
                #   - 롤 수 분할: 인치 타입 판별 가능한 경우 MAX_COUNT 단위로 분할
                #   분할된 청크는 '_SPLIT_N' 접미사를 SHPOIT 에 추가하여 구분합니다.
                # ──────────────────────────────────────────────────────────
                def _split_roll_item(it, big_cap_kg, b_inch12, b_inch3):
                    """
                    단일 원지 아이템을 big_cap_kg 및 인치별 MAX_COUNT 기준으로 분할.
                    분할이 필요 없으면 [it] 그대로 반환.
                    분할 시 각 청크는 원본 딕셔너리의 복사본에 다음 필드를 추가:
                      _SPLIT_FROM  : 원본 SHPOIT
                      _SPLIT_IDX   : 1-based 분할 순번
                      _SPLIT_TOTAL : 분할 총 수
                      QTSHPO       : 이 청크의 KG (UOMKEY='KG') or 롤 수 (UOMKEY='R')
                      KG_WEIGHT    : 이 청크의 실제 KG
                    """
                    sk      = (it.get('SKUKEY') or '').strip()
                    uom     = (it.get('UOMKEY') or '').strip()
                    inch    = _ps_get_inch(sk)
                    grm     = _ps_get_grm(sk)

                    # 최대 롤 수 한도 (인치 타입 기반)
                    if inch == '12인치':
                        max_rolls = b_inch12.get(grm, 0)
                    elif inch == '3인치':
                        max_rolls = b_inch3.get(grm, 0)
                    else:
                        max_rolls = 0

                    if uom == 'R' and _ps_is_roll(sk):
                        # R단위: QTSHPO = 롤 수
                        total_rolls = int(it.get('QTSHPO') or 0)
                        # 단일 롤 중량: KG_WEIGHT → SKUMA.GRSWGT → SHPDI.GRSWGT → UNIT_WEIGHT → fallback 600
                        # KG_WEIGHT가 이미 계산돼 있으면 우선 사용
                        _pre_kg = float(it.get('KG_WEIGHT') or 0)
                        if _pre_kg > 0:
                            total_kg = _pre_kg
                        else:
                            # SKUMA.GRSWGT
                            _skuma_gw = skuma_map.get(sk, {}).get('grswgt', 0)
                            if _skuma_gw > 0:
                                total_kg = total_rolls * _skuma_gw
                            else:
                                # SHPDI.GRSWGT (아이템 컬럼)
                                _item_gw = float(it.get('GRSWGT') or 0)
                                if _item_gw > 0:
                                    total_kg = total_rolls * _item_gw
                                else:
                                    unit_w_it    = float(it.get('UNIT_WEIGHT') or 0)
                                    single_kg_it = unit_w_it if unit_w_it > 0 else ROLL_SINGLE_KG
                                    total_kg = total_rolls * single_kg_it
                        per_roll_kg = total_kg / total_rolls if total_rolls > 0 else ROLL_SINGLE_KG

                        # 청크당 최대 롤 수 결정
                        if max_rolls > 0:
                            rolls_by_mc = max_rolls
                        else:
                            rolls_by_mc = total_rolls  # MAX_COUNT 없으면 분할 안 함

                        # big_cap_kg 기준 최대 롤 수
                        rolls_by_kg = max(1, int(big_cap_kg / per_roll_kg)) if per_roll_kg > 0 else total_rolls
                        chunk_rolls = min(rolls_by_mc, rolls_by_kg, total_rolls)
                        if chunk_rolls <= 0 or chunk_rolls >= total_rolls:
                            return [it]  # 분할 불필요

                        chunks = []
                        remain = total_rolls
                        idx = 1
                        total_parts = math.ceil(total_rolls / chunk_rolls)
                        while remain > 0:
                            c_rolls = min(chunk_rolls, remain)
                            c_kg    = round(c_rolls * per_roll_kg, 4)
                            chunk   = dict(it)
                            chunk['QTSHPO']      = c_rolls
                            chunk['KG_WEIGHT']   = c_kg
                            chunk['_SPLIT_FROM']  = it.get('SHPOIT', '')
                            chunk['_SPLIT_IDX']   = idx
                            chunk['_SPLIT_TOTAL'] = total_parts
                            chunks.append(chunk)
                            remain -= c_rolls
                            idx    += 1
                        return chunks

                    else:
                        # KG단위: QTSHPO = KG
                        total_kg    = float(it.get('KG_WEIGHT') or it.get('QTSHPO') or 0)
                        total_rolls = _item_roll_count(it)
                        unit_w_it   = float(it.get('UNIT_WEIGHT') or 0)
                        single_kg_it = unit_w_it if unit_w_it > 0 else ROLL_SINGLE_KG
                        per_roll_kg = total_kg / total_rolls if total_rolls > 0 else single_kg_it

                        # 청크당 최대 롤 수 결정
                        if max_rolls > 0:
                            rolls_by_mc = max_rolls
                        else:
                            rolls_by_mc = total_rolls

                        rolls_by_kg = max(1, int(big_cap_kg / per_roll_kg)) if per_roll_kg > 0 else total_rolls
                        chunk_rolls = min(rolls_by_mc, rolls_by_kg)
                        if chunk_rolls <= 0 or chunk_rolls >= total_rolls:
                            return [it]  # 분할 불필요

                        chunks = []
                        remain_rolls = total_rolls
                        remain_kg    = total_kg
                        idx = 1
                        total_parts = math.ceil(total_rolls / chunk_rolls)
                        while remain_rolls > 0:
                            c_rolls = min(chunk_rolls, remain_rolls)
                            c_kg    = round(c_rolls * per_roll_kg, 4)
                            if remain_rolls <= chunk_rolls:
                                # 마지막 청크: 남은 KG 전부 (반올림 오차 방지)
                                c_kg = round(remain_kg, 4)
                            chunk = dict(it)
                            chunk['QTSHPO']       = c_kg
                            chunk['KG_WEIGHT']    = c_kg
                            chunk['_SPLIT_FROM']  = it.get('SHPOIT', '')
                            chunk['_SPLIT_IDX']   = idx
                            chunk['_SPLIT_TOTAL'] = total_parts
                            chunks.append(chunk)
                            remain_rolls -= c_rolls
                            remain_kg    -= c_kg
                            idx          += 1
                        return chunks

                # 납품분할 전처리: 분할이 필요한 아이템을 청크 목록으로 교체
                split_items = []   # 분할 완료된 원지 아이템 목록 (분할 불필요 아이템 포함)
                split_notes_pre = []  # 분할 관련 사전 노트
                for it in roll_items:
                    chunks = _split_roll_item(it, big_cap, big_inch12_max, big_inch3_max)
                    if len(chunks) > 1:
                        split_notes_pre.append(
                            f"[납품분할] {it.get('SHPOKY','')}#{it.get('SHPOIT','')} "
                            f"({it.get('SKUKEY','')}): "
                            f"{float(it.get('QTSHPO',0)):.1f}{'롤' if it.get('UOMKEY','')=='R' else 'kg'} "
                            f"→ {len(chunks)}개 분할 "
                            f"(허용차량 {big_car} / 적재한도 {big_cap:.0f}kg)"
                        )
                    split_items.extend(chunks)

                # 이후 분할 로직은 split_items 기준으로 진행
                # ──────────────────────────────────────────────────────────
                # 3단계: 차량 분할 — First Fit Decreasing (FFD) Bin Packing
                #   목표: 납품처 허용 최대차량(big_car)의 적재 한도(big_cap)를 기준으로
                #         가능한 적은 차량에 최대 적재하여 운송 효율을 극대화한다.
                #
                #   알고리즘:
                #     1) split_items를 KG 내림차순 정렬 (FFD 핵심 — 큰 항목 먼저 배치)
                #     2) 각 아이템을 기존 빈(차량) 중 "처음으로 들어갈 수 있는" 빈에 배치
                #        (First Fit: 넣을 수 있는 첫 번째 빈 선택)
                #     3) 어떤 빈에도 못 들어가면 새 빈(차량) 생성
                #   분할 조건 (한 빈에 추가 불가 기준):
                #     ① 중량: cur_kg + item_kg > big_cap
                #     ② 롤 수: 인치×평량별 누적 롤 수 > big_car MAX_COUNT
                # ──────────────────────────────────────────────────────────

                # KG 내림차순 정렬 (FFD: 큰 아이템 먼저 → 빈 낭비 최소화)
                # KG_WEIGHT 기준으로 정렬 (롤 수가 아닌 실제 중량)
                def _get_kg_for_sort(it):
                    """정렬용 KG: KG_WEIGHT → SKUMA.GRSWGT×QTSHPO → SHPDI.GRSWGT×QTSHPO → QTSHPO"""
                    if it.get('KG_WEIGHT') and float(it.get('KG_WEIGHT') or 0) > 0:
                        return float(it['KG_WEIGHT'])
                    sk  = (it.get('SKUKEY') or '').strip()
                    uom = (it.get('UOMKEY') or '').strip()
                    qty = float(it.get('QTSHPO') or 0)
                    if uom == 'R' and _ps_is_roll(sk):
                        gw = skuma_map.get(sk, {}).get('grswgt', 0)
                        if gw > 0: return qty * gw
                        item_gw = float(it.get('GRSWGT') or 0)
                        if item_gw > 0: return qty * item_gw
                        return qty * ROLL_SINGLE_KG
                    return qty

                split_items_ffd = sorted(
                    split_items,
                    key=_get_kg_for_sort,
                    reverse=True
                )

                # 빈(Bin) 구조: {items, total_kg, v12, v3}
                bins_ffd = []  # [{items:[], total_kg:float, v12:defaultdict, v3:defaultdict}]

                def _can_fit_in_bin(b, item_kg, inch, grm, rc):
                    """아이템이 빈에 들어갈 수 있는지 확인 (중량 + 롤 수 동시 검사)"""
                    # ① 중량 체크
                    if b['total_kg'] + item_kg > big_cap:
                        return False
                    # ② 롤 수 MAX_COUNT 체크
                    if inch == '12인치':
                        mc = big_inch12_max.get(grm, 0)
                        if mc > 0 and (b['v12'][grm] + rc) > mc:
                            return False
                    elif inch == '3인치':
                        mc = big_inch3_max.get(grm, 0)
                        if mc > 0 and (b['v3'][grm] + rc) > mc:
                            return False
                    return True

                for it in split_items_ffd:
                    qty_kg = _get_kg_for_sort(it)   # 실제 KG 사용 (롤 수 아님)
                    inch   = _ps_get_inch(it.get('SKUKEY', ''))
                    grm    = _ps_get_grm(it.get('SKUKEY', ''))
                    rc     = _item_roll_count(it)

                    # First Fit: 들어갈 수 있는 첫 번째 빈에 배치
                    placed = False
                    for b in bins_ffd:
                        if _can_fit_in_bin(b, qty_kg, inch, grm, rc):
                            b['items'].append(it)
                            b['total_kg'] += qty_kg
                            if inch == '12인치': b['v12'][grm] += rc
                            elif inch == '3인치': b['v3'][grm]  += rc
                            placed = True
                            break

                    if not placed:
                        # 새 빈(차량) 생성
                        new_bin = {
                            'items':    [it],
                            'total_kg': qty_kg,
                            'v12':      defaultdict(int),
                            'v3':       defaultdict(int),
                        }
                        if inch == '12인치': new_bin['v12'][grm] = rc
                        elif inch == '3인치': new_bin['v3'][grm]  = rc
                        bins_ffd.append(new_bin)

                # FFD 결과를 veh_list 형식으로 변환
                veh_list = [{'items': b['items'], 'total_kg': b['total_kg']} for b in bins_ffd]

                # 4단계: 각 차량에 최적 톤수 배정
                for veh in veh_list:
                    veh_kg  = veh['total_kg']

                    # ── [1순위] 실제 롤 수 기준 차량 선정 ────────────────
                    v12_rolls = defaultdict(int)
                    v3_rolls  = defaultdict(int)
                    for it in veh['items']:
                        inch = _ps_get_inch(it.get('SKUKEY',''))
                        grm  = _ps_get_grm(it.get('SKUKEY',''))
                        rc   = _item_roll_count(it)
                        if inch == '12인치': v12_rolls[grm] += rc
                        elif inch == '3인치': v3_rolls[grm]  += rc

                    cands2 = (
                        [_ps_find_car(inch12_map, '12인치', g, rc, car_order, veh_info) for g, rc in v12_rolls.items()] +
                        [_ps_find_car(inch3_map,  '3인치',  g, rc, car_order, veh_info) for g, rc in v3_rolls.items()]
                    )
                    # 롤 수 기준 최소 필요 차량
                    veh_car = min(cands2, key=_sort_key) if cands2 else _best_fit_car(veh_kg)

                    # ── [2순위] 중량 기준 업그레이드 ─────────────────────
                    veh_car_by_kg = _best_fit_car(veh_kg)
                    if _sort_key(veh_car_by_kg) < _sort_key(veh_car):
                        veh_car = veh_car_by_kg   # 중량이 더 큰 차량 필요 시 교체

                    veh_cap_kg = veh_info.get(veh_car, {}).get('load_kg', 0)
                    veh_fill   = (veh_kg / veh_cap_kg * 100) if veh_cap_kg > 0 else 0

                    # ── 4-H단계: 원지 1단 적재 높이 검사 → 초과 시 업그레이드 ──
                    # 직경 계산은 단일 롤 ROLL_SINGLE_KG(600kg) 기준
                    # (QTSHPO는 여러 롤 합산 KG이므로 직경 계산에는 사용하지 않음)
                    # 1단 적재 높이 = 직경 D(mm) / 1000 (m) ≤ 차량 effective_height_m
                    roll_h_notes = []
                    seen_skukeys = set()   # 동일 SKUKEY 중복 검사 방지
                    for it in veh['items']:
                        sk = it.get('SKUKEY', '')
                        if not _ps_is_roll(sk) or sk in seen_skukeys:
                            continue
                        seen_skukeys.add(sk)
                        diam_mm = _roll_single_diam_mm(sk)
                        if diam_mm is None:
                            continue
                        roll_h_m  = diam_mm / 1000.0   # 1단 적재 높이 = 롤 직경
                        car_eff_h = veh_info.get(veh_car, {}).get('effective_height_m', 99.0)
                        if roll_h_m > car_eff_h:
                            # 현재 차량 높이 초과 → 수용 가능한 더 큰 차량 탐색
                            upgraded = False
                            for car in car_order:   # 큰 차량 → 작은 차량 순
                                ct = car['CARTYPE']
                                if _sort_key(ct) >= _sort_key(veh_car):
                                    continue        # 현재 차량보다 작거나 같은 차량 스킵
                                eff_h = veh_info.get(ct, {}).get('effective_height_m', 0)
                                if eff_h > 0 and eff_h >= roll_h_m:
                                    roll_h_notes.append(
                                        f"[높이초과 업그레이드] {veh_car}→{ct} "
                                        f"(롤직경 {diam_mm:.0f}mm={roll_h_m:.2f}m > "
                                        f"차량가용높이 {car_eff_h:.2f}m → {ct}높이 {eff_h:.2f}m)"
                                    )
                                    veh_car = ct
                                    upgraded = True
                                    break
                            if not upgraded:
                                roll_h_notes.append(
                                    f"[높이초과-수동확인필요] {sk} "
                                    f"롤직경 {diam_mm:.0f}mm={roll_h_m:.2f}m > "
                                    f"최대차량 가용높이 "
                                    f"{veh_info.get(veh_car,{}).get('effective_height_m',0):.2f}m"
                                )

                    # ── 4-C단계: 원지 CBM 검사 (3인치 기준표 MAX_COUNT 기반) ──
                    # 3인치 기준표(DS_INCH3) 차량별 MAX_COUNT × CBM_per_roll(3인치 표준)을
                    # 차량 적재함 CBM 상한으로 사용하여 원지 총 CBM 검사
                    # → 초과 시 더 큰 차량으로 업그레이드
                    roll_cbm_notes = []
                    total_roll_cbm = 0.0
                    for it in veh['items']:
                        sk = it.get('SKUKEY', '')
                        if not _ps_is_roll(sk):
                            continue
                        gsm_v, w_v = _ps_parse_skukey_dims(sk)
                        if gsm_v and w_v:
                            unit_w_cbm   = float(it.get('UNIT_WEIGHT') or 0)
                            single_cbm   = unit_w_cbm if unit_w_cbm > 0 else ROLL_SINGLE_KG
                            cbm_per_r = _ps_calc_roll_cbm_per_roll(gsm_v, w_v, single_cbm)
                            r_cnt     = _item_roll_count(it)
                            total_roll_cbm += cbm_per_r * r_cnt

                    if total_roll_cbm > 0:
                        # 현재 차량의 3인치 기준 MAX_COUNT 기반 CBM 상한 계산
                        # = inch3_map[cartype][grm_cond] × CBM_per_roll(3인치 표준)
                        # 3인치 표준 롤: gsm=200(LT300), width=1400mm
                        _cbm_3inch_std = _ps_calc_roll_cbm_per_roll(200, 1400, 600.0)
                        cur_grm_cond   = 'GE300' if any(
                            _ps_get_grm(it.get('SKUKEY','')) == 'GE300'
                            for it in veh['items']
                            if _ps_is_roll(it.get('SKUKEY',''))
                        ) else 'LT300'
                        cur_max_cnt    = inch3_map.get(veh_car, {}).get(cur_grm_cond, 0)
                        cbm_cap_from_inch3 = cur_max_cnt * _cbm_3inch_std if cur_max_cnt > 0 else 0.0

                        # 차량 적재함 실제 CBM도 추가 참조
                        car_cbm_actual = _ps_get_car_cbm(veh_car, veh_info)

                        # 두 기준 중 더 큰 값을 CBM 상한으로 사용 (보수적)
                        cbm_limit = max(cbm_cap_from_inch3, car_cbm_actual)

                        if cbm_limit > 0 and total_roll_cbm > cbm_limit:
                            # CBM 초과 → 더 큰 차량 업그레이드
                            cbm_upgraded = False
                            for car in car_order:   # 큰 차량 → 작은 차량 순
                                ct = car['CARTYPE']
                                if _sort_key(ct) >= _sort_key(veh_car):
                                    continue
                                ct_max_cnt  = inch3_map.get(ct, {}).get(cur_grm_cond, 0)
                                ct_cbm_cap  = ct_max_cnt * _cbm_3inch_std if ct_max_cnt > 0 else 0.0
                                ct_car_cbm  = _ps_get_car_cbm(ct, veh_info)
                                ct_cbm_lim  = max(ct_cbm_cap, ct_car_cbm)
                                if ct_cbm_lim > 0 and ct_cbm_lim >= total_roll_cbm:
                                    roll_cbm_notes.append(
                                        f"[CBM초과 업그레이드] {veh_car}→{ct} "
                                        f"(원지CBM {total_roll_cbm:.3f}m³ > 기준CBM {cbm_limit:.3f}m³ "
                                        f"→ {ct}기준CBM {ct_cbm_lim:.3f}m³)"
                                    )
                                    veh_car = ct
                                    cbm_limit = ct_cbm_lim
                                    cbm_upgraded = True
                                    break
                            if not cbm_upgraded:
                                roll_cbm_notes.append(
                                    f"[CBM초과-수동확인필요] 원지CBM {total_roll_cbm:.3f}m³ > "
                                    f"최대차량기준CBM {cbm_limit:.3f}m³"
                                )
                        elif cbm_limit > 0:
                            roll_cbm_notes.append(
                                f"[CBM OK] 원지CBM {total_roll_cbm:.3f}m³ / "
                                f"기준CBM {cbm_limit:.3f}m³ "
                                f"(적재율 {total_roll_cbm/cbm_limit*100:.0f}%)"
                            )

                    veh_load_cap = veh_info.get(veh_car, {}).get('load_kg', 0)
                    veh_fill_final = (veh_kg / veh_load_cap * 100) if veh_load_cap > 0 else 0

                    # 롤 수 vs MAX_COUNT 요약 노트 생성
                    roll_count_notes = []
                    for grm, rc in v12_rolls.items():
                        mc = inch12_map.get(veh_car, {}).get(grm, 0)
                        roll_count_notes.append(
                            f"[12인치 롤수체크] {grm} {rc}롤 / MAX_COUNT={mc} "
                            f"({'OK' if mc == 0 or rc <= mc else '초과→업그레이드'})"
                        )
                    for grm, rc in v3_rolls.items():
                        mc = inch3_map.get(veh_car, {}).get(grm, 0)
                        roll_count_notes.append(
                            f"[3인치 롤수체크] {grm} {rc}롤 / MAX_COUNT={mc} "
                            f"({'OK' if mc == 0 or rc <= mc else '초과→업그레이드'})"
                        )

                    # 5단계: 여유 중량 기반 추가 배차 (원지 롤)
                    #   ‣ 2단 적재 가능 항목은 2단 기준 적재 롤 수(max_count × 2)로 재계산하여
                    #     인치 기준 차량 다운그레이드(더 작은 차량으로 변경) 가능 여부 검토
                    load_spare_kg    = veh_load_cap - veh_kg
                    added_items      = []
                    added_kg         = 0.0
                    # 배차 결과 노트: ①납품분할 ②롤수 ③중량 ④CBM ⑤높이 순
                    add_notes        = (
                        split_notes_pre +          # ★ [1] 납품분할 안내 노트
                        roll_count_notes +          # [2] 롤수 vs MAX_COUNT
                        [
                            f"[효율최적-중량기준] {veh_car} 선정 "
                            f"(적재필요 {veh_kg:.1f}kg / "
                            f"차량적재 {veh_load_cap:.0f}kg / "
                            f"첨부율 {veh_fill_final:.1f}%)"
                        ] + roll_cbm_notes + roll_h_notes
                    )
                    tier2_items      = []   # 2단 적재 확정 항목 (다운그레이드 검토용)

                    if load_spare_kg > 0:
                        # 이미 배차된 키 집합 (veh_list 전체)
                        all_dispatched_keys = set()
                        for v2 in veh_list:
                            for x in v2['items']:
                                all_dispatched_keys.add((x.get('SHPOKY',''), x.get('SHPOIT','')))

                        # 추가배차 후보: split_items 기준 (납품분할 청크 포함)
                        candidates_for_add = [
                            it for it in split_items
                            if (it.get('SHPOKY',''), it.get('SHPOIT','')) not in all_dispatched_keys
                            and float(it.get('QTSHPO', 0)) <= load_spare_kg
                        ]
                        # 중량 내림차순으로 정렬하여 꽉 채우기
                        candidates_for_add.sort(key=lambda x: float(x.get('QTSHPO', 0)), reverse=True)

                        # 현재까지 배차된 롤 수 (인치×평량별)
                        cur_v12_rolls = defaultdict(int, v12_rolls)
                        cur_v3_rolls  = defaultdict(int, v3_rolls)

                        for cand in candidates_for_add:
                            cand_kg = float(cand.get('QTSHPO', 0))
                            if added_kg + cand_kg > load_spare_kg:
                                continue
                            # ── [1순위] 롤 수 MAX_COUNT 초과 여부 사전 검사 ──
                            c_inch = _ps_get_inch(cand.get('SKUKEY',''))
                            c_grm  = _ps_get_grm(cand.get('SKUKEY',''))
                            c_rc   = _item_roll_count(cand)
                            if c_inch == '12인치':
                                new_rc = cur_v12_rolls[c_grm] + c_rc
                                mc     = inch12_map.get(veh_car, {}).get(c_grm, 0)
                            elif c_inch == '3인치':
                                new_rc = cur_v3_rolls[c_grm] + c_rc
                                mc     = inch3_map.get(veh_car, {}).get(c_grm, 0)
                            else:
                                new_rc, mc = 0, 0
                            if mc > 0 and new_rc > mc:
                                add_notes.append(
                                    f"{cand.get('SHPOKY','')}#{cand.get('SHPOIT','')} "
                                    f"롤수초과({new_rc}>{mc}) (추가배차 제외)"
                                )
                                continue
                            # 2단 적재 높이 검사
                            h_ok, h2m = _check_height_ok(cand, veh_car)
                            if not h_ok:
                                car_h = veh_info.get(veh_car, {}).get('effective_height_m', 0)
                                pal_h = veh_info.get(veh_car, {}).get('pallet_height_m', 0)
                                pal_note = f" (파렛트{pal_h}m 차감)" if pal_h > 0 else ""
                                add_notes.append(
                                    f"{cand.get('SHPOKY','')}#{cand.get('SHPOIT','')} "
                                    f"2단높이 {h2m:.2f}m > 가용높이 {car_h}m{pal_note} (제외)"
                                )
                                continue
                            # 2단 적재 가능 → 추가 배차 확정, tier2 목록에도 기록
                            added_items.append(cand)
                            tier2_items.append(cand)
                            added_kg += cand_kg
                            all_dispatched_keys.add((cand.get('SHPOKY',''), cand.get('SHPOIT','')))
                            # ★ [1순위] 롤 수 카운터 갱신 (다음 후보의 MAX_COUNT 검사에 반영)
                            if c_inch == '12인치':
                                cur_v12_rolls[c_grm] += c_rc
                            elif c_inch == '3인치':
                                cur_v3_rolls[c_grm] += c_rc

                    # ── 2단 적재 기준 인치 차량 다운그레이드 검토 ─────────────
                    # 2단 적재 가능 항목이 있으면 현재 차량의 인치 기준 max_count를
                    # 2배(2단)로 확장하여 더 작은 차량으로 변경 가능한지 확인합니다.
                    # 롤 수(KG÷600) 기준, 2단 항목은 0.5 환산 (2단 2롤 = 1단 1롤 공간)
                    if tier2_items:
                        all_final_items = veh['items'] + tier2_items

                        # 인치×평량별 실제 롤 수 집계 (2단 항목은 0.5 환산)
                        tier2_keys = {(x.get('SHPOKY',''), x.get('SHPOIT','')) for x in tier2_items}
                        t2_v12 = defaultdict(float)
                        t2_v3  = defaultdict(float)
                        for it in all_final_items:
                            inch = _ps_get_inch(it.get('SKUKEY',''))
                            grm  = _ps_get_grm(it.get('SKUKEY',''))
                            key  = (it.get('SHPOKY',''), it.get('SHPOIT',''))
                            rc   = _item_roll_count(it)
                            # 2단 적재 항목: 2롤이 1단 1롤 공간 → 0.5 환산
                            w_factor = 0.5 if key in tier2_keys else 1.0
                            if inch == '12인치': t2_v12[grm] += rc * w_factor
                            elif inch == '3인치': t2_v3[grm]  += rc * w_factor

                        # 2단 환산 기준 최소 차량 후보 (롤 수 올림)
                        tier2_cands = []
                        for grm, cnt_f in t2_v12.items():
                            tier2_cands.append(
                                _ps_find_car(inch12_map, '12인치', grm, math.ceil(cnt_f), car_order, veh_info)
                            )
                        for grm, cnt_f in t2_v3.items():
                            tier2_cands.append(
                                _ps_find_car(inch3_map, '3인치', grm, math.ceil(cnt_f), car_order, veh_info)
                            )

                        if tier2_cands:
                            tier2_inch_car = min(tier2_cands, key=_sort_key)
                            # 중량은 반드시 충족해야 함
                            final_total_kg = veh_kg + added_kg
                            tier2_kg_car   = _best_fit_car(final_total_kg)
                            # 인치 후보와 중량 후보 중 더 큰 차량 선택 (안전 우선)
                            if _sort_key(tier2_inch_car) > _sort_key(veh_car):
                                # 2단 환산으로 더 작은 차량 가능 → 중량도 만족하면 다운그레이드
                                if _sort_key(tier2_kg_car) >= _sort_key(tier2_inch_car):
                                    downgraded_car = tier2_inch_car
                                else:
                                    downgraded_car = tier2_kg_car
                                if _sort_key(downgraded_car) > _sort_key(veh_car):
                                    add_notes.append(
                                        f"[2단적재 다운그레이드] {veh_car}→{downgraded_car} "
                                        f"(2단환산 롤수 기준: {dict(t2_v12) or ''}{dict(t2_v3) or ''})"
                                    )
                                    veh_car      = downgraded_car
                                    veh_load_cap = veh_info.get(veh_car, {}).get('load_kg', 0)
                            elif tier2_cands:
                                add_notes.append(
                                    f"[2단적재 적용] 현재차량 {veh_car} 유지 "
                                    f"(2단환산 인치최소 {tier2_inch_car})"
                                )

                    final_items = veh['items'] + added_items
                    final_kg    = veh_kg + added_kg

                    # ── 원지 롤 개수 집계 ──
                    # 각 아이템의 QTSHPO(KG) ÷ ROLL_SINGLE_KG(600) → 반올림 = 롤 개수
                    # SKUKEY별 롤 개수를 합산하여 총 롤 수 계산
                    # _item_roll_count 기반으로 일관성 있게 집계
                    # (UOMKEY='R' 원지: QTSHPO 그대로 / UOMKEY='KG': KG÷600 올림)
                    roll_count_by_sku = {}   # {skukey: roll_cnt}
                    for it in final_items:
                        if not _ps_is_roll(it.get('SKUKEY', '')):
                            continue
                        sk  = it.get('SKUKEY', '')
                        cnt = _item_roll_count(it)
                        roll_count_by_sku[sk] = roll_count_by_sku.get(sk, 0) + cnt
                    total_roll_count = sum(roll_count_by_sku.values())

                    # ── 납품처 조건 체크 노트 추가 ──
                    ptnr_notes, ptnr_warns = _build_ptnr_notes(dptnky, rqshpd, veh_car)
                    if _max_ton_applied:
                        pi = ptnr_info.get(dptnky, {})
                        add_notes.insert(0,
                            f"[최대톤수 적용] 납품처 허용 {pi.get('max_ton_label','')} 기준 "
                            f"배차 차량 제한 적용"
                        )
                    add_notes = ptnr_warns + add_notes + ptnr_notes

                    # 납품분할 여부 및 분할 청크 수 집계
                    split_count = sum(
                        1 for it in final_items if it.get('_SPLIT_FROM') is not None
                    )
                    all_vehicles.append({
                        'dptnky':          dptnky,
                        'dptnm':           dptnm,
                        'rqshpd':          rqshpd,
                        'cartype':         veh_car,
                        'carclass_cd':     veh_info.get(veh_car, {}).get('carclass_cd', ''),
                        'total_kg':        round(final_kg, 2),
                        'load_cap':        veh_load_cap,
                        'spare_kg':        round(veh_load_cap - final_kg, 2),
                        'items':           final_items,
                        'item_cnt':        len(final_items),
                        'added_cnt':       len(added_items),
                        'added_kg':        round(added_kg, 2),
                        'material_type':   'ROLL',
                        'roll_count':      total_roll_count,        # ★ 원지 총 롤 개수
                        'roll_count_sku':  roll_count_by_sku,       # ★ SKUKEY별 롤 개수
                        'roll_cbm':        round(total_roll_cbm, 4),# ★ 원지 총 CBM
                        'split_yn':        'Y' if split_count > 0 else 'N',  # ★ 납품분할 여부
                        'split_count':     split_count,             # ★ 분할 청크 수
                        'notes':           add_notes,
                        'ptnr_warns':      ptnr_warns,
                        'forklift_yn':     ptnr_info.get(dptnky, {}).get('forklift_yn', ''),
                        'deadline_time':   ptnr_info.get(dptnky, {}).get('deadline_time', ''),
                        'max_ton_label':   ptnr_info.get(dptnky, {}).get('max_ton_label', ''),
                    })

            # =================================================================
            # ② 판지(평판) 배차 — 1순위: 중량 최적화, 2순위: CBM 검사
            # =================================================================
            if board_items:
                # ─────────────────────────────────────────────────────────
                # [STEP-1] 가장 큰 차량 기준으로 중량·높이 초과 시 차량 분할
                # ─────────────────────────────────────────────────────────
                # _valid_cars[0] = 가장 큰 유효 차량(LOAD_TON이 입력된 차량)
                big_car    = _valid_cars[0]['CARTYPE'] if _valid_cars else '판별불가'
                big_cap_kg = veh_info.get(big_car, {}).get('load_kg', 0) or 99_999_999.0
                big_cap_h  = veh_info.get(big_car, {}).get('effective_height_m', 99.0) or 99.0

                # ── 판지 KG 중량 헬퍼 (R단위 → KG 변환) ─────────────────
                def _board_kg(it):
                    """판지 아이템의 실제 KG 중량 반환
                    - UOMKEY='R': KG_WEIGHT (서버가 미리 계산) or QTSHPO×GRSWGT
                    - UOMKEY='KG': QTSHPO 그대로
                    """
                    # 검색 API가 KG_WEIGHT를 계산해서 전달한 경우
                    if it.get('KG_WEIGHT') is not None:
                        return float(it['KG_WEIGHT'])
                    uom = (it.get('UOMKEY') or '').strip()
                    qty = float(it.get('QTSHPO') or 0)
                    if uom == 'R':
                        grswgt = skuma_map.get(it.get('SKUKEY',''), {}).get('grswgt', 0)
                        return qty * grswgt if grswgt > 0 else qty
                    return qty   # KG or other

                # ─────────────────────────────────────────────────────────────
                # 최대 차량 적재 중량 단위로 아이템 묶기 (그룹 분할)
                # 분할 조건: 중량 초과
                #
                # ■ 높이 검사 제거 이유:
                #   판지는 여러 속(ream)을 차량 바닥에 나란히 배치하는 방식으로
                #   적재하므로, QTSHPO(속 수량) × 1속높이를 수직 합산하면
                #   실제와 동떨어진 수 미터의 높이가 산출되어 소형차 분산 배차 발생.
                #   → STEP-1 에서는 중량 기준으로만 그룹 분할.
                #   → 1속 높이가 차량 높이 자체를 초과하는지(단독 Over-Tall) 여부는
                #     STEP-2 _check_board_dims_ok 에서 처리.
                # ─────────────────────────────────────────────────────────────
                veh_list_b  = []      # [{items, total_kg, total_h}]
                cur_items_b = []
                cur_kg_b    = 0.0
                cur_h_b     = 0.0
                for it in board_items:
                    qty_kg  = _board_kg(it)   # R→KG 변환 적용
                    item_h  = _calc_board_stack_height_m(it, skuma_map)  # 1속 높이(m)
                    # 중량 초과 시에만 새 그룹 시작 (높이 누적 합산 제거)
                    kg_over = cur_items_b and (cur_kg_b + qty_kg > big_cap_kg)
                    if kg_over:
                        veh_list_b.append({'items': cur_items_b, 'total_kg': cur_kg_b, 'total_h': cur_h_b})
                        cur_items_b = []
                        cur_kg_b    = 0.0
                        cur_h_b     = 0.0
                    cur_items_b.append(it)
                    cur_kg_b += qty_kg
                    cur_h_b   = max(cur_h_b, item_h)  # 그룹 내 최대 1속 높이 추적
                if cur_items_b:
                    veh_list_b.append({'items': cur_items_b, 'total_kg': cur_kg_b, 'total_h': cur_h_b})

                # ─────────────────────────────────────────────────────────
                # [STEP-2] 각 그룹별 차량 선정: 1순위 중량 → 2순위 CBM
                # ─────────────────────────────────────────────────────────
                for veh in veh_list_b:
                    veh_kg   = veh['total_kg']
                    veh_items = veh['items']
                    board_notes = []

                    # ── 1순위: 적재 효율 최적 차량 선정 (첨부율 최대화) ─────
                    # 납품처별 중량합계(veh_kg) 기준으로 적재 가능한 차량 중
                    # 첨부율(= veh_kg / load_cap)이 가장 높은 차량 선택
                    veh_car   = _best_fit_car(veh_kg)
                    cap_kg_v  = veh_info.get(veh_car, {}).get('load_kg', 0)
                    fill_pct  = (veh_kg / cap_kg_v * 100) if cap_kg_v > 0 else 0
                    kg_note = (
                        f"[효율최적-중량기준] {veh_car} 선정 "
                        f"(적재필요 {veh_kg:.1f}kg / "
                        f"차량적재 {cap_kg_v:.0f}kg / "
                        f"첨부율 {fill_pct:.1f}%)"
                    )
                    board_notes.append(kg_note)

                    # ── 2순위: CBM 검사 → 초과 시 업그레이드 ─────────────
                    total_cbm = sum(
                        _ps_get_item_cbm(it, skuma_map) for it in veh_items
                    )
                    car_cbm   = _ps_get_car_cbm(veh_car, veh_info)

                    if total_cbm > 0 and car_cbm > 0:
                        cbm_ratio = total_cbm / car_cbm * 100
                        if total_cbm > car_cbm:
                            # CBM 초과 → 더 큰 차량으로 업그레이드
                            cbm_car_new = _upgrade_by_cbm(veh_car, total_cbm, veh_info, car_order)
                            new_car_cbm = _ps_get_car_cbm(cbm_car_new, veh_info)
                            if cbm_car_new != veh_car:
                                board_notes.append(
                                    f"[CBM초과 업그레이드] {veh_car}→{cbm_car_new} "
                                    f"(화물CBM {total_cbm:.3f}m³ > 차량CBM {car_cbm:.3f}m³ → "
                                    f"새차량CBM {new_car_cbm:.3f}m³)"
                                )
                                veh_car = cbm_car_new
                            else:
                                # 최대 차량도 CBM 초과 → 경고 노트만 추가
                                board_notes.append(
                                    f"[CBM초과-수동확인필요] 화물CBM {total_cbm:.3f}m³ > "
                                    f"최대차량CBM {car_cbm:.3f}m³ "
                                    f"(적재율 {cbm_ratio:.0f}%)"
                                )
                        else:
                            board_notes.append(
                                f"[CBM OK] 화물CBM {total_cbm:.3f}m³ / "
                                f"차량CBM {car_cbm:.3f}m³ (적재율 {cbm_ratio:.0f}%)"
                            )
                    elif total_cbm == 0:
                        board_notes.append("[CBM] 치수정보 없음 (CBM 검사 생략)")

                    # ── 최종 차량 정보 ─────────────────────────────────────
                    veh_load_cap = veh_info.get(veh_car, {}).get('load_kg', 0)

                    # ── 치수(L×W×H) 상세 검사 (기존 로직 유지: 경고 노트) ──
                    dims_ok, max_w, max_l, total_h = _check_board_dims_ok(
                        veh_items, veh_car, skuma_map
                    )
                    if not dims_ok:
                        # ── 치수(높이·폭·길이) 초과 → 최소 적합 차량으로 업그레이드 ──
                        # 탐색 방향: 작은 차량 → 큰 차량 (reversed car_order)
                        #   • 중량도 수용 가능하고 치수도 OK인 가장 작은 차량 선택
                        #   • 중량 기준(_best_fit_car)보다 작은 차는 건너뜀
                        upgraded_by_dim = False
                        dim_best_car  = None
                        dim_best_dims = None   # (ok, mw, ml, th)
                        for car in reversed(car_order):   # 작은→큰 순
                            ct       = car['CARTYPE']
                            cap_kg_c = veh_info.get(ct, {}).get('load_kg', 0)
                            if cap_kg_c < veh_kg:         # 중량 미달 차량 제외
                                continue
                            ok2, mw2, ml2, th2 = _check_board_dims_ok(veh_items, ct, skuma_map)
                            if ok2:
                                dim_best_car  = ct
                                dim_best_dims = (ok2, mw2, ml2, th2)
                                # 작은→큰 탐색이므로 처음 OK 차량이 최소 적합 차량
                                break

                        if dim_best_car is not None:
                            _, mw2, ml2, th2 = dim_best_dims
                            prev_car = veh_car
                            if dim_best_car != veh_car:
                                board_notes.append(
                                    f"[치수기준 업그레이드] {veh_car}→{dim_best_car} "
                                    f"(적재높이 {th2:.2f}m, 최대 {mw2}×{ml2}mm)"
                                )
                                veh_car      = dim_best_car
                                veh_load_cap = veh_info.get(dim_best_car, {}).get('load_kg', 0)
                            else:
                                board_notes.append(
                                    f"[치수OK-재확인] {veh_car} 유지 "
                                    f"(적재높이 {th2:.2f}m, 최대 {mw2}×{ml2}mm)"
                                )
                            max_w, max_l, total_h = mw2, ml2, th2
                            upgraded_by_dim = True
                        if not upgraded_by_dim:
                            ci = veh_info.get(veh_car, {})
                            board_notes.append(
                                f"[치수초과-수동확인필요] 최대 {max_w}×{max_l}mm, "
                                f"적재높이 {total_h:.2f}m vs 차량가용높이 "
                                f"{ci.get('effective_height_m', ci.get('height_m', 0)):.2f}m"
                            )
                    else:
                        ci = veh_info.get(veh_car, {})
                        board_notes.append(
                            f"[치수OK] 최대 {max_w}×{max_l}mm, "
                            f"적재높이 {total_h:.2f}m / "
                            f"가용높이 {ci.get('effective_height_m', ci.get('height_m', 0)):.2f}m"
                        )

                    # ── 납품처 조건 체크 노트 추가 ──
                    ptnr_notes, ptnr_warns = _build_ptnr_notes(dptnky, rqshpd, veh_car)
                    if _max_ton_applied:
                        pi = ptnr_info.get(dptnky, {})
                        board_notes.insert(0,
                            f"[최대톤수 적용] 납품처 허용 {pi.get('max_ton_label','')} 기준 "
                            f"배차 차량 제한 적용"
                        )
                    board_notes = ptnr_warns + board_notes + ptnr_notes

                    all_vehicles.append({
                        'dptnky':        dptnky,
                        'dptnm':         dptnm,
                        'rqshpd':        rqshpd,
                        'cartype':       veh_car,
                        'carclass_cd':   veh_info.get(veh_car, {}).get('carclass_cd', ''),
                        'total_kg':      round(veh_kg, 2),
                        'load_cap':      veh_load_cap,
                        'spare_kg':      round(veh_load_cap - veh_kg, 2),
                        'items':         veh_items,
                        'item_cnt':      len(veh_items),
                        'added_cnt':     0,
                        'added_kg':      0.0,
                        'material_type': 'BOARD',
                        'board_dims':    {
                            'max_w_mm':       max_w,
                            'max_l_mm':       max_l,
                            'total_height_m': round(total_h, 3),
                            'total_cbm':      round(total_cbm, 4),
                            'car_cbm':        round(car_cbm, 4),
                        },
                        'notes':         board_notes,
                        'ptnr_warns':    ptnr_warns,
                        'forklift_yn':   ptnr_info.get(dptnky, {}).get('forklift_yn', ''),
                        'deadline_time': ptnr_info.get(dptnky, {}).get('deadline_time', ''),
                        'max_ton_label': ptnr_info.get(dptnky, {}).get('max_ton_label', ''),
                    })

            # =================================================================
            # ③ 기타 아이템 (원지/판지 외) 배차 — 중량 기준
            # =================================================================
            if other_items:
                total_kg = sum(float(it.get('QTSHPO', 0)) for it in other_items)
                big_car  = car_order[0]['CARTYPE'] if car_order else '판별불가'
                big_cap  = veh_info.get(big_car, {}).get('load_kg', 0)

                veh_list_o = []
                cur_items_o, cur_kg_o = [], 0.0
                for it in other_items:
                    qty_kg = float(it.get('QTSHPO', 0))
                    if cur_kg_o + qty_kg > big_cap and cur_items_o:
                        veh_list_o.append({'items': cur_items_o, 'total_kg': cur_kg_o})
                        cur_items_o, cur_kg_o = [], 0.0
                    cur_items_o.append(it)
                    cur_kg_o += qty_kg
                if cur_items_o:
                    veh_list_o.append({'items': cur_items_o, 'total_kg': cur_kg_o})

                for veh in veh_list_o:
                    veh_kg  = veh['total_kg']
                    veh_car = '판별불가'
                    # _valid_cars (MAX_TON 필터 적용된 목록) 기준으로 차량 선택
                    for car in reversed(_valid_cars):
                        ct = car['CARTYPE']
                        if veh_info.get(ct, {}).get('load_kg', 0) >= veh_kg:
                            veh_car = ct
                    # _valid_cars 내에 적합한 차량 없으면 전체 car_order에서 재탐색
                    if veh_car == '판별불가':
                        for car in reversed(car_order):
                            ct = car['CARTYPE']
                            if veh_info.get(ct, {}).get('load_kg', 0) >= veh_kg:
                                veh_car = ct
                    veh_load_cap = veh_info.get(veh_car, {}).get('load_kg', 0)

                    # ── 납품처 조건 체크 노트 추가 ──
                    other_notes = []
                    ptnr_notes_o, ptnr_warns_o = _build_ptnr_notes(dptnky, rqshpd, veh_car)
                    if _max_ton_applied:
                        pi = ptnr_info.get(dptnky, {})
                        other_notes.insert(0,
                            f"[최대톤수 적용] 납품처 허용 {pi.get('max_ton_label','')} 기준 "
                            f"배차 차량 제한 적용"
                        )
                    other_notes = ptnr_warns_o + other_notes + ptnr_notes_o

                    all_vehicles.append({
                        'dptnky':        dptnky,
                        'dptnm':         dptnm,
                        'rqshpd':        rqshpd,
                        'cartype':       veh_car,
                        'carclass_cd':   veh_info.get(veh_car, {}).get('carclass_cd', ''),
                        'total_kg':      round(veh_kg, 2),
                        'load_cap':      veh_load_cap,
                        'spare_kg':      round(veh_load_cap - veh_kg, 2),
                        'items':         veh['items'],
                        'item_cnt':      len(veh['items']),
                        'added_cnt':     0,
                        'added_kg':      0.0,
                        'material_type': 'OTHER',
                        'notes':         other_notes,
                        'ptnr_warns':    ptnr_warns_o,
                        'forklift_yn':   ptnr_info.get(dptnky, {}).get('forklift_yn', ''),
                        'deadline_time': ptnr_info.get(dptnky, {}).get('deadline_time', ''),
                        'max_ton_label': ptnr_info.get(dptnky, {}).get('max_ton_label', ''),
                    })

            # ── 납품처별 _valid_cars 임시 교체 복원 ──
            _valid_cars = _orig_valid_cars

        return jsonify({"ok": True, "total": len(all_vehicles), "vehicles": all_vehicles})
    except Exception as e:
        import traceback
        return jsonify({"error": str(e), "trace": traceback.format_exc()}), 500
    finally:
        conn.close()


@app.route('/api/ps-dispatch/save', methods=['POST'])
def api_ps_dispatch_save():
    """
    배차 저장 (자동/수동 공통)
    body: { vehicles: [{dptnky, dptnm, rqshpd, cartype, carclass_cd, total_kg, items:[...]}] }
    처리:
      1. PS_DISPATCH_H + PS_DISPATCH_D 삽입
      2. SHPDI.STDLNR = DISPATCH_NO  (가선적번호 채번)
      3. SHPDH.VEHINO = carclass_cd  (배차 차량유형코드: Z010, Z014 등)
    """
    from datetime import datetime
    body     = request.json or {}
    vehicles = body.get('vehicles', [])
    if not vehicles:
        return jsonify({"error": "vehicles 없음"}), 400

    today = datetime.now().strftime('%Y%m%d')
    conn  = get_conn()
    try:
        saved = []
        for veh in vehicles:
            dt           = (veh.get('rqshpd') or today).replace('-','')
            dispatch_no  = _ps_next_dispatch_no(conn, dt)
            dptnky       = veh.get('dptnky','')
            dptnm        = veh.get('dptnm','')
            cartype      = veh.get('cartype','')
            carclass_cd  = (veh.get('carclass_cd') or '').strip()
            total_kg     = float(veh.get('total_kg', 0))
            items        = veh.get('items', [])

            conn.execute(
                """INSERT INTO PS_DISPATCH_H
                   (DISPATCH_NO,DISPATCH_DT,RQSHPD,DPTNKY,DPTNM,CARTYPE,STATUS,
                    TOTAL_KG,TOTAL_CNT,CREDAT,CREUSR)
                   VALUES (?,?,?,?,?,?,'DRAFT',?,?,?,?)""",
                (dispatch_no, today, dt, dptnky, dptnm, cartype,
                 total_kg, len(items), today, 'SYSTEM')
            )

            # 아이템 INSERT + 관련 SHPOKY 수집
            shpoky_set = set()   # SHPDH 업데이트용 (SHPOKY 기준)
            shpdi_keys = []      # SHPDI 업데이트용 (SHPOKY+SHPOIT 기준)
            for seq, it in enumerate(items, 1):
                shpoky = it.get('SHPOKY','')
                shpoit = it.get('SHPOIT','')
                conn.execute(
                    """INSERT INTO PS_DISPATCH_D
                       (DISPATCH_NO,SEQ,SHPOKY,SHPOIT,SKUKEY,DESC01,
                        QTSHPO,UOMKEY,DPTNKY,DPTNM,IS_SPLIT,ORG_SHPOKY,ORG_SHPOIT,
                        GRSWGT,KG_WEIGHT)
                       VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                    (dispatch_no, seq,
                     shpoky, shpoit,
                     it.get('SKUKEY',''), it.get('DESC01',''),
                     float(it.get('QTSHPO',0)), it.get('UOMKEY','KG'),
                     it.get('DPTNKY',''), it.get('DPTNM',''),
                     int(it.get('IS_SPLIT',0)),
                     it.get('ORG_SHPOKY',''), it.get('ORG_SHPOIT',''),
                     float(it.get('GRSWGT',0) or 0),
                     float(it.get('KG_WEIGHT',0) or 0))
                )
                if shpoky and shpoit:
                    shpdi_keys.append((shpoky, shpoit))
                if shpoky:
                    shpoky_set.add(shpoky)

            # ① SHPDI.STDLNR = DISPATCH_NO (가선적번호 채번)
            for shpoky, shpoit in shpdi_keys:
                conn.execute(
                    """UPDATE SHPDI
                       SET STDLNR = ?,
                           LMODAT = strftime('%Y%m%d','now'),
                           LMOUSR = 'WEB'
                       WHERE SHPOKY = ? AND SHPOIT = ?
                         AND STATIT = 'NEW'""",
                    (dispatch_no, shpoky, shpoit)
                )

            # ② SHPDH.VEHINO = carclass_cd, CARTON = carclass_cd, 차량 배정 필드 초기화
            if shpoky_set:
                ph = ','.join('?' * len(shpoky_set))
                conn.execute(
                    f"""UPDATE SHPDH
                        SET VEHINO    = ?,
                            CARTON    = ?,
                            CARNO     = NULL,
                            DRIVER    = NULL,
                            DRIVERCEL = NULL,
                            LMODAT    = strftime('%Y%m%d','now'),
                            LMOUSR    = 'WEB'
                        WHERE SHPOKY IN ({ph})""",
                    [carclass_cd or None, carclass_cd or None] + list(shpoky_set)
                )

            saved.append(dispatch_no)
        conn.commit()
        return jsonify({"ok": True, "saved": len(saved), "dispatch_nos": saved})
    except Exception as e:
        conn.rollback()
        import traceback
        return jsonify({"error": str(e), "trace": traceback.format_exc()}), 500
    finally:
        conn.close()


@app.route('/api/ps-dispatch/list', methods=['GET'])
def api_ps_dispatch_list():
    """
    저장된 배차 목록 조회
    params: date_from, date_to, dptnky, status, dispatch_no
    """
    date_from   = request.args.get('date_from','').replace('-','')
    date_to     = request.args.get('date_to','').replace('-','')
    dptnky      = request.args.get('dptnky','').strip()
    status      = request.args.get('status','').strip()
    dispatch_no = request.args.get('dispatch_no','').strip()

    conn = get_conn()
    try:
        wheres, params = [], []
        if date_from:
            wheres.append("h.RQSHPD >= ?"); params.append(date_from)
        if date_to:
            wheres.append("h.RQSHPD <= ?"); params.append(date_to)
        if dptnky:
            wheres.append("(h.DPTNKY LIKE ? OR h.DPTNM LIKE ?)")
            params += [f'%{dptnky}%', f'%{dptnky}%']
        if status:
            wheres.append("h.STATUS=?"); params.append(status)
        if dispatch_no:
            wheres.append("h.DISPATCH_NO LIKE ?"); params.append(f'%{dispatch_no}%')

        where_sql = ("WHERE " + " AND ".join(wheres)) if wheres else ""
        sql = f"""
            SELECT h.DISPATCH_NO, h.DISPATCH_DT, h.RQSHPD,
                   h.DPTNKY, h.DPTNM, h.CARTYPE, h.STATUS,
                   h.TOTAL_KG, h.TOTAL_CNT, h.NOTE, h.CREDAT,
                   COALESCE(v.LOAD_TON, 0) AS LOAD_TON
            FROM PS_DISPATCH_H h
            LEFT JOIN DS_VEHICLE v ON v.CARTYPE = h.CARTYPE  -- h.CARTYPE은 PS_DISPATCH_H 차량유형명, DS_VEHICLE.CARTYPE도 유형명
            {where_sql}
            ORDER BY h.RQSHPD DESC, h.DISPATCH_NO
        """
        rows = conn.execute(sql, params).fetchall()
        result = []
        for r in rows:
            d = dict(r)
            # 적재가능중량 (LOAD_TON × 1000 kg)
            load_ton = float(d.get('LOAD_TON') or 0)
            load_kg  = round(load_ton * 1000, 1) if load_ton > 0 else None
            d['LOAD_KG'] = load_kg   # None = DS_VEHICLE 미등록
            # 상세 아이템
            detail = conn.execute(
                """SELECT d.SEQ, d.SHPOKY, d.SHPOIT, d.SKUKEY, d.DESC01, d.QTSHPO, d.UOMKEY,
                          d.DPTNKY, d.DPTNM, d.IS_SPLIT, d.ORG_SHPOKY, d.ORG_SHPOIT,
                          COALESCE(d.GRSWGT,0) AS GRSWGT,
                          COALESCE(d.KG_WEIGHT,0) AS KG_WEIGHT,
                          COALESCE(rd.QTYRCV, 0) AS UNIT_WEIGHT
                   FROM PS_DISPATCH_D d
                   LEFT JOIN RECDI rd ON rd.SKUKEY=d.SKUKEY
                   WHERE d.DISPATCH_NO=? ORDER BY d.SEQ""",
                (d['DISPATCH_NO'],)
            ).fetchall()
            items = [dict(x) for x in detail]
            d['items'] = items
            # ── 원지 차량 롤 수 합산 ──────────────────────────────
            # UNIT_WEIGHT(RECDI 기반 단일 롤 중량) 사용, 없으면 600 fallback
            # UOMKEY='KG' + is_roll: KG_WEIGHT ÷ UNIT_WEIGHT(fallback 600), 올림
            # UOMKEY='R'  + is_roll: QTSHPO 자체가 롤 수 (원지 R단위)
            # 판지(UOMKEY='R' but is_roll=False)는 롤 수 제외
            total_roll = 0
            for it in items:
                sk  = (it.get('SKUKEY') or '').strip()
                uom = (it.get('UOMKEY') or '').strip()
                if not _ps_is_roll(sk):
                    continue   # 판지·기타는 제외
                if uom == 'R':
                    total_roll += int(it.get('QTSHPO') or 0)
                elif uom == 'KG':
                    kg       = float(it.get('KG_WEIGHT') or it.get('QTSHPO') or 0)
                    unit_w   = float(it.get('UNIT_WEIGHT') or 0)
                    single_w = unit_w if unit_w > 0 else 600.0
                    if kg > 0:
                        total_roll += math.ceil(kg / single_w)
            d['ROLL_COUNT'] = total_roll   # 0 이면 원지 아이템 없음(판지 배차 등)
            result.append(d)
        return jsonify({"ok": True, "total": len(result), "rows": result})
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


@app.route('/api/ps-dispatch/confirm', methods=['POST'])
def api_ps_dispatch_confirm():
    """배차 확정 (DRAFT → CONFIRMED)"""
    body         = request.json or {}
    dispatch_nos = body.get('dispatch_nos', [])
    if not dispatch_nos:
        return jsonify({"error": "dispatch_nos 없음"}), 400
    conn = get_conn()
    try:
        placeholders = ','.join('?' * len(dispatch_nos))
        conn.execute(
            f"UPDATE PS_DISPATCH_H SET STATUS='CONFIRMED' WHERE DISPATCH_NO IN ({placeholders})",
            dispatch_nos
        )
        conn.commit()
        return jsonify({"ok": True, "confirmed": len(dispatch_nos)})
    except Exception as e:
        conn.rollback()
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


@app.route('/api/ps-confirm/list', methods=['GET', 'POST'])
def api_ps_confirm_list_compat():
    """구버전 호환 라우트 — /api/ps-sap/list 로 위임
    브라우저 캐시에 구버전 index.html이 남아 있는 경우 404 대신 정상 응답 반환.
    GET 쿼리파라미터(from_date, to_date) → POST body 변환 후 위임.
    """
    from flask import request as _req
    if _req.method == 'GET':
        # date_from/date_to 또는 from_date/to_date 모두 지원
        df = (_req.args.get('date_from','') or _req.args.get('from_date','') or '').replace('-','')
        dt = (_req.args.get('date_to','')   or _req.args.get('to_date','')   or '').replace('-','')
        body = {
            'rqshpd_from': df,
            'rqshpd_to':   dt,
            'stknum':      _req.args.get('stknum','') or '',
            'dptnky':      _req.args.get('dptnky','') or '',
        }
    else:
        body = _req.json or {}
    # 실제 요청 객체를 직접 수정하는 대신 내부 함수 로직을 공유하도록 위임
    from flask import current_app
    with current_app.test_request_context(
        '/api/ps-sap/list', method='POST',
        json=body, content_type='application/json'
    ):
        return api_ps_sap_list()


@app.route('/api/ps-sap/list', methods=['POST'])
def api_ps_sap_list():
    """배차확정(SAP전송) 탭 - 가선적번호(DISPATCH_NO) 목록
    SHPDI.STATIT='NEW' AND STDLNR NOT NULL 기준으로
    가선적번호별 차량정보 + 납품문서 집계 반환.
    """
    body        = request.json or {}
    rqshpd_from = body.get('rqshpd_from', '').strip().replace('-', '')
    rqshpd_to   = body.get('rqshpd_to',   '').strip().replace('-', '')
    stknum_kw   = body.get('stknum',  '').strip()
    dptnky_kw   = body.get('dptnky',  '').strip()   # 납품처 코드 or 명 LIKE 검색

    conn = get_conn()
    try:
        where = ["SI.STATIT = 'NEW'",
                 "TRIM(SI.STDLNR) != ''"]
        params = []
        if rqshpd_from:
            where.append("SH.RQSHPD >= ?"); params.append(rqshpd_from)
        if rqshpd_to:
            where.append("SH.RQSHPD <= ?"); params.append(rqshpd_to)
        if stknum_kw:
            where.append("SI.STDLNR LIKE ?"); params.append(f'%{stknum_kw}%')
        if dptnky_kw:
            # 납품처 코드(DPTNKY) 또는 납품처명(BZPTN.NAME01) LIKE 검색
            where.append("(SH.DPTNKY LIKE ? OR CT.NAME01 LIKE ?)")
            params.extend([f'%{dptnky_kw}%', f'%{dptnky_kw}%'])
        where_sql = ' AND '.join(where)

        # ── 차량유형 코드→명칭 맵 (TMS_CARCLASS10 기준)
        cc_rows = conn.execute(
            "SELECT CMCDVL, CDESC1 FROM CMCDV WHERE CMCDKY='TMS_CARCLASS10'"
        ).fetchall()
        cc_code2name = {r['CMCDVL']: r['CDESC1'] for r in cc_rows}  # Z010→1톤
        cc_name2code = {r['CDESC1']: r['CMCDVL'] for r in cc_rows}  # 1톤→Z010

        raw_rows = conn.execute(f"""
            SELECT
                SI.STDLNR                                        AS STDLNR,
                -- SAP 선적번호: RFC 선적생성 후 PS_DISPATCH_H.STKNUM에 저장된 TKNUM
                NULLIF(TRIM(COALESCE(PH.STKNUM, '')), '')        AS SAP_STKNUM,
                COUNT(DISTINCT SI.SVBELN)                        AS SVBELN_CNT,
                COUNT(DISTINCT SI.SHPOKY)                        AS SHPOKY_CNT,
                COUNT(*)                                         AS ITEM_CNT,
                -- PS_DISPATCH_H.TOTAL_KG 우선 사용 (배차저장 시 KG_WEIGHT 기반 정확한 값)
                -- SKUMA.NETWGT는 롤/판지 품목에 NULL이어서 0으로 산출되는 경우 있음
                COALESCE(
                    MAX(PH.TOTAL_KG),
                    ROUND(SUM(SI.QTSHPO * COALESCE(M.NETWGT, 0)),1)
                )                                                AS TOTAL_KG,
                MIN(SH.RQSHPD)                                   AS RQSHPD_FROM,
                MAX(SH.RQSHPD)                                   AS RQSHPD_TO,
                MAX(NULLIF(TRIM(SH.CARTON), ''))                 AS CARTON,
                MAX(NULLIF(TRIM(SH.CARNO),  ''))                 AS CARNO,
                MAX(NULLIF(TRIM(SH.VEHINO), ''))                 AS VEHINO,
                MAX(NULLIF(TRIM(SH.DRIVER), ''))                 AS DRIVER,
                MAX(NULLIF(TRIM(SH.DRIVERCEL), ''))              AS DRIVERCEL,
                MAX(NULLIF(TRIM(SH.TDLNR), ''))                  AS TDLNR,
                MAX(SH.LMODAT)                                   AS LMODAT,
                -- PS_DISPATCH_H 에서 CARTYPE(명칭), STATUS 보강
                -- STDLNR = DISPATCH_NO 로 1:1 대응
                MAX(PH.CARTYPE)                                  AS PH_CARTYPE,
                MAX(PH.STATUS)                                   AS PH_STATUS,
                -- 납품처: 선적번호 내 납품처가 복수이면 '복수' 표시
                CASE
                    WHEN COUNT(DISTINCT SH.DPTNKY) > 1
                    THEN '(' || COUNT(DISTINCT SH.DPTNKY) || '개 납품처)'
                    ELSE MAX(SH.DPTNKY)
                END                                              AS DPTNKY,
                CASE
                    WHEN COUNT(DISTINCT SH.DPTNKY) > 1
                    THEN '(' || COUNT(DISTINCT SH.DPTNKY) || '개 납품처)'
                    ELSE MAX(COALESCE(CT.NAME01, SH.DPTNKY))
                END                                              AS DPTNKYNM
            FROM SHPDI SI
            JOIN SHPDH SH ON SI.SHPOKY = SH.SHPOKY
            LEFT JOIN SKUMA M  ON M.SKUKEY  = SI.SKUKEY
            LEFT JOIN BZPTN CT ON CT.PTNRKY = SH.DPTNKY
                               AND CT.PTNRTY = 'CT'
            LEFT JOIN PS_DISPATCH_H PH ON PH.DISPATCH_NO = SI.STDLNR
            WHERE {where_sql}
            GROUP BY SI.STDLNR
            ORDER BY MIN(SH.RQSHPD) DESC, SI.STDLNR
        """, params).fetchall()

        rows = []
        for r in raw_rows:
            d = dict(r)
            # ── 차량유형 코드/명칭 정규화 ─────────────────────────────────
            # 우선순위: PS_DISPATCH_H.CARTYPE(명칭) > SHPDH.CARTON > SHPDH.VEHINO
            # CARTON/VEHINO 는 코드(Z010) 또는 명칭(1톤) 혼재 가능 → 정규화
            cartype = ''
            carclass_cd = ''

            # 1순위: PS_DISPATCH_H.CARTYPE (명칭, 가장 신뢰 가능)
            ph_cartype = (d.get('PH_CARTYPE') or '').strip()
            if ph_cartype:
                cartype     = ph_cartype
                carclass_cd = cc_name2code.get(ph_cartype, '')

            # 2순위: SHPDH.CARTON (코드 or 명칭 혼재)
            if not cartype:
                carton = (d.get('CARTON') or '').strip()
                if carton:
                    if carton in cc_code2name:
                        # 코드 형식 (Z010 등)
                        carclass_cd = carton
                        cartype     = cc_code2name[carton]
                    elif carton in cc_name2code:
                        # 명칭 형식 (1톤 등)
                        cartype     = carton
                        carclass_cd = cc_name2code[carton]
                    else:
                        cartype     = carton  # 알 수 없는 값은 그대로
                        carclass_cd = ''

            # 3순위: SHPDH.VEHINO (코드 or 명칭 혼재)
            if not cartype:
                vehino = (d.get('VEHINO') or '').strip()
                if vehino:
                    if vehino in cc_code2name:
                        carclass_cd = vehino
                        cartype     = cc_code2name[vehino]
                    elif vehino in cc_name2code:
                        cartype     = vehino
                        carclass_cd = cc_name2code[vehino]
                    else:
                        cartype     = vehino
                        carclass_cd = ''

            d['CARTYPE']     = cartype      # 항상 명칭 (예: 1톤, 15톤)
            d['CARCLASS_CD'] = carclass_cd  # 항상 코드 (예: Z010, Z150)
            rows.append(d)

        return jsonify({"ok": True, "rows": rows, "total": len(rows)})
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


@app.route('/api/ps-sap/items', methods=['POST'])
def api_ps_sap_items():
    """배차확정 탭 3D 시각화용 — 가선적번호(STDLNR)의 품목 상세 + 차량 제원 반환.
    응답: { ok, cartype, carclass_cd, veh: {LENGTH_M, WIDTH_M_NUM, HEIGHT_M, LOAD_TON, PALLET_HEIGHT_M},
            items: [{SKUKEY, DESC01, QTSHPO, UOMKEY, KG_WEIGHT, UNIT_WEIGHT, GRSWGT, SHPOKY, SVBELN}] }
    """
    body    = request.json or {}
    stknum  = body.get('stknum', '').strip()
    cartype = body.get('cartype', '').strip()        # 차량유형 명칭 (예: "15톤")
    if not stknum:
        return jsonify({"error": "stknum 필수"}), 400

    conn = get_conn()
    try:
        # ── 1. 품목 상세 (SHPDI + SKUMA 제원) ────────────────────────────
        item_rows = conn.execute("""
            SELECT
                SI.SHPOKY,
                SI.SVBELN,
                SI.SKUKEY,
                SI.DESC01,
                CAST(SI.QTSHPO AS REAL)              AS QTSHPO,
                SI.UOMKEY,
                ROUND(SI.QTSHPO * COALESCE(M.NETWGT,0), 1) AS KG_WEIGHT,
                COALESCE(M.NETWGT, 0)                AS UNIT_WEIGHT,
                COALESCE(M.GRSWGT, 0)                AS GRSWGT
            FROM SHPDI SI
            LEFT JOIN SKUMA M ON M.SKUKEY = SI.SKUKEY
            WHERE SI.STATIT = 'NEW' AND SI.STDLNR = ?
            ORDER BY SI.SVBELN, SI.SHPOKY, CAST(SI.SHPOIT AS INTEGER)
        """, [stknum]).fetchall()

        items = [dict(r) for r in item_rows]

        # ── 2. 차량 제원 (DS_VEHICLE) ────────────────────────────────────
        veh = None
        if cartype:
            vrow = conn.execute(
                """SELECT CARTYPE, LENGTH_M, WIDTH_M, HEIGHT_M, LOAD_TON,
                          PALLET_HEIGHT_M, CARCLASS_CD
                   FROM DS_VEHICLE WHERE CARTYPE = ? LIMIT 1""",
                [cartype]
            ).fetchone()
            if vrow:
                d = dict(vrow)
                # WIDTH_M 범위 처리 ('1.8~2.1' → 최솟값)
                w_raw = str(d.get('WIDTH_M') or '')
                try:
                    d['WIDTH_M_NUM'] = float(w_raw.split('~')[0].strip()) if '~' in w_raw else float(w_raw)
                except (ValueError, TypeError):
                    d['WIDTH_M_NUM'] = 2.4
                veh = d

        return jsonify({"ok": True, "items": items, "veh": veh,
                        "cartype": cartype, "total": len(items)})
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


@app.route('/api/ps-sap/docs', methods=['POST'])
def api_ps_sap_docs():
    """선택한 가선적번호(DISPATCH_NO)에 매핑된 납품문서 상세 목록"""
    body   = request.json or {}
    stknum = body.get('stknum', '').strip()   # UI에서 STKNUM 키로 전달 (= STDLNR 값)
    if not stknum:
        return jsonify({"error": "stknum 필수"}), 400

    conn = get_conn()
    try:
        rows = conn.execute("""
            SELECT
                SI.STDLNR                                  AS STKNUM,
                SI.SVBELN,
                SI.SHPOKY,
                SI.SHPOIT,
                SI.STATIT,
                SI.SKUKEY,
                SI.DESC01,
                SI.QTSHPO,
                SI.UOMKEY,
                SI.QTSHPD,
                ROUND(SI.QTSHPO * COALESCE(M.NETWGT,0), 1) AS LINE_KG,
                COALESCE(M.NETWGT, 0)                      AS NETWGT,
                SH.RQSHPD,
                SH.DPTNKY,
                COALESCE(CT.NAME01, SH.DPTNKY)             AS DPTNKYNM,
                SH.SHPMTY,
                SH.CARTON,
                SH.CARNO,
                SH.VEHINO,
                SH.DRIVER,
                SH.DRIVERCEL
            FROM SHPDI SI
            JOIN SHPDH SH ON SI.SHPOKY = SH.SHPOKY
            LEFT JOIN SKUMA M  ON M.SKUKEY  = SI.SKUKEY
            LEFT JOIN BZPTN CT ON CT.PTNRKY = SH.DPTNKY
            WHERE SI.STATIT = 'NEW' AND SI.STDLNR = ?
            ORDER BY SI.SVBELN, SI.SHPOKY, CAST(SI.SHPOIT AS INTEGER)
        """, [stknum]).fetchall()
        return jsonify({"ok": True, "rows": [dict(r) for r in rows], "total": len(rows)})
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


@app.route('/api/ps-sap/vehicle-search', methods=['POST'])
def api_ps_sap_vehicle_search():
    """SAP탭 차량선택 팝업 - VEHINO(CARCLASS_CD)로 VHCMA 검색
    body: { vehino: 'Z010', vehicle_no: '검색어', driver_name: '검색어' }
    VHCMA.VEHICLE_CLASS = DS_VEHICLE.CARTYPE 기준으로 JOIN
    """
    body        = request.json or {}
    vehino      = body.get('vehino', '').strip()       # CARCLASS_CD (Z010 등)
    vehicle_no  = body.get('vehicle_no', '').strip()
    driver_name = body.get('driver_name', '').strip()

    conn = get_conn()
    try:
        where_parts, params = [], []
        if vehino:
            # DS_VEHICLE.CARCLASS_CD = vehino → CARTYPE 확보 → VHCMA.VEHICLE_CLASS 매칭
            where_parts.append("""
                V.VEHICLE_CLASS IN (
                    SELECT CARTYPE FROM DS_VEHICLE WHERE CARCLASS_CD = ?
                )
            """); params.append(vehino)
        if vehicle_no:
            where_parts.append("V.VEHICLE_NO LIKE ?"); params.append(f'%{vehicle_no}%')
        if driver_name:
            where_parts.append("V.DRIVER_NAME LIKE ?"); params.append(f'%{driver_name}%')

        where_sql = f"WHERE {' AND '.join(where_parts)}" if where_parts else ""
        rows = conn.execute(f"""
            SELECT V.VEHICLE_NO, V.VEHICLE_KIND, V.VEHICLE_CLASS, V.DRIVER_NAME, V.CONTACT_NO,
                   V.CARRIER, V.CARTYPE, V.CARCLASS_CD, V.USE_YN,
                   D.CARCLASS_CD AS DS_CARCLASS_CD
            FROM VHCMA V
            LEFT JOIN DS_VEHICLE D ON D.CARTYPE = V.VEHICLE_CLASS
            {where_sql}
            ORDER BY V.VEHICLE_NO
            LIMIT 100
        """, params).fetchall()
        return jsonify({"ok": True, "rows": [dict(r) for r in rows], "total": len(rows)})
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500
    finally:
        conn.close()


@app.route('/api/ps-sap/assign-vehicle', methods=['POST'])
def api_ps_sap_assign_vehicle():
    """SAP탭 차량 배정: SHPDH에 차량번호/기사/연락처 저장
    body: { stdlnr: 'PS-...', vehicle_no: '12가3456', driver_name: '홍길동', contact_no: '010-...' }
    - SHPDH.CARNO    = vehicle_no
    - SHPDH.DRIVER   = driver_name
    - SHPDH.DRIVERCEL= contact_no
    """
    body       = request.json or {}
    stdlnr     = body.get('stdlnr', '').strip()
    vehicle_no = body.get('vehicle_no', '').strip()
    driver_name= body.get('driver_name', '').strip()
    contact_no = body.get('contact_no', '').strip()
    if not stdlnr:
        return jsonify({"ok": False, "error": "stdlnr 필수"}), 400

    from datetime import datetime
    today = datetime.now().strftime('%Y%m%d')

    conn = get_conn()
    try:
        # 차량 마스터에서 VEHICLE_CLASS 조회 → DS_VEHICLE JOIN으로 CARCLASS_CD 확보
        vhc_row = conn.execute(
            """SELECT V.VEHICLE_CLASS, D.CARCLASS_CD, D.CARTYPE
               FROM VHCMA V
               LEFT JOIN DS_VEHICLE D ON D.CARTYPE = V.VEHICLE_CLASS
               WHERE V.VEHICLE_NO = ?""", [vehicle_no]
        ).fetchone()
        carclass_cd = vhc_row['CARCLASS_CD'] if vhc_row else None  # Z010 등
        cartype     = vhc_row['CARTYPE']     if vhc_row else None  # 1톤 등

        # 해당 가선적번호에 속한 SHPOKY 목록
        shpoky_rows = conn.execute(
            "SELECT DISTINCT SHPOKY FROM SHPDI WHERE STATIT='NEW' AND STDLNR=?", [stdlnr]
        ).fetchall()
        shpoky_list = [r['SHPOKY'] for r in shpoky_rows]
        if not shpoky_list:
            return jsonify({"ok": False, "error": "해당 가선적번호에 매핑된 주문 없음"}), 404

        ph = ','.join('?' * len(shpoky_list))
        conn.execute(
            f"""UPDATE SHPDH SET CARNO=?, DRIVER=?, DRIVERCEL=?,
                VEHINO=?, CARTON=?,
                LMODAT=?, LMOTIM=STRFTIME('%H%M%S','now'), LMOUSR='WEB'
                WHERE SHPOKY IN ({ph})""",
            [vehicle_no, driver_name, contact_no, carclass_cd, carclass_cd, today] + shpoky_list
        )
        conn.commit()
        return jsonify({"ok": True, "updated": len(shpoky_list),
                        "carno": vehicle_no, "driver": driver_name, "drivercel": contact_no,
                        "carton": carclass_cd, "cartype": cartype})
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500
    finally:
        conn.close()


@app.route('/api/ps-sap/delete', methods=['POST'])
def api_ps_sap_delete():
    """배차 삭제: 가선적번호(DISPATCH_NO)를 초기화하여 재배차 대상으로 복원
    body: { stknums: ['PS-20260504-001', ...] }
    - SHPDI.STDLNR = NULL  (가선적번호 초기화)
    - SHPDH.VEHINO = NULL  (배차 차량유형 초기화)
    - PS_DISPATCH_H.STATUS = 'CANCELLED'
    응답에 복원용 vehicles 데이터 포함 (배차탭 미저장 상태 복원용)
    """
    body    = request.json or {}
    stknums = body.get('stknums', [])
    if not stknums:
        return jsonify({"error": "stknums 필수"}), 400

    conn = get_conn()
    try:
        ph = ','.join('?' * len(stknums))

        # ① 삭제 전 PS_DISPATCH_H + PS_DISPATCH_D 에서 복원용 데이터 조회
        # PS_DISPATCH_H에 CARCLASS_CD 컬럼 없음 → DS_VEHICLE에서 CARTYPE 기준 CARCLASS_CD 조회
        disp_rows = conn.execute(
            f"""SELECT h.DISPATCH_NO, h.CARTYPE,
                       COALESCE(v.CARCLASS_CD,'') AS CARCLASS_CD,
                       h.RQSHPD,
                       h.DPTNKY, h.DPTNM, h.TOTAL_KG, h.TOTAL_CNT,
                       COALESCE(v.LOAD_TON,0)*1000 AS LOAD_KG
                FROM PS_DISPATCH_H h
                LEFT JOIN DS_VEHICLE v ON v.CARTYPE = h.CARTYPE
                WHERE h.DISPATCH_NO IN ({ph})""",
            stknums
        ).fetchall()

        disp_detail = conn.execute(
            f"""SELECT d.DISPATCH_NO, d.SHPOKY, d.SHPOIT, d.SKUKEY, d.DESC01,
                       d.QTSHPO, d.UOMKEY, d.DPTNKY, d.DPTNM, d.GRSWGT, d.KG_WEIGHT,
                       d.IS_SPLIT, d.ORG_SHPOKY, d.ORG_SHPOIT
                FROM PS_DISPATCH_D d
                WHERE d.DISPATCH_NO IN ({ph})
                ORDER BY d.DISPATCH_NO, d.SEQ""",
            stknums
        ).fetchall()

        # dispatch_no → items 맵
        items_map = {}
        for row in disp_detail:
            dn = row['DISPATCH_NO']
            if dn not in items_map:
                items_map[dn] = []
            items_map[dn].append(dict(row))

        # 복원용 vehicles 배열 (배차탭 psopState.vehicles 형식)
        restore_vehicles = []
        for r in disp_rows:
            dn = r['DISPATCH_NO']
            restore_vehicles.append({
                'DISPATCH_NO': dn,   # 복원 식별용 (STDLNR은 null로 프론트에서 처리)
                'cartype':     r['CARTYPE'] or '',
                'carclass_cd': r['CARCLASS_CD'] or '',
                'rqshpd':      r['RQSHPD'] or '',
                'dptnky':      r['DPTNKY'] or '',
                'dptnm':       r['DPTNM'] or '',
                'total_kg':    float(r['TOTAL_KG'] or 0),
                'total_cnt':   int(r['TOTAL_CNT'] or 0),
                'load_kg':     float(r['LOAD_KG'] or 0) or None,
                'items':       items_map.get(dn, []),
            })

        # ② 삭제 대상 SHPOKY 수집 (SHPDH.VEHINO 초기화에 사용)
        shpoky_rows = conn.execute(
            f"SELECT DISTINCT SHPOKY FROM SHPDI"
            f" WHERE STATIT='NEW' AND STDLNR IN ({ph})",
            stknums
        ).fetchall()
        shpoky_list = [r['SHPOKY'] for r in shpoky_rows]

        # ③ SHPDI.STDLNR → ' ' (기본값 공백 복원, NOT NULL 제약)
        result = conn.execute(
            f"UPDATE SHPDI SET STDLNR=' ', LMODAT=strftime('%Y%m%d','now'), LMOUSR='WEB'"
            f" WHERE STATIT='NEW' AND STDLNR IN ({ph})",
            stknums
        )
        affected = result.rowcount

        # ④ SHPDH.VEHINO → NULL
        if shpoky_list:
            ph2 = ','.join('?' * len(shpoky_list))
            conn.execute(
                f"UPDATE SHPDH SET VEHINO=NULL, LMODAT=strftime('%Y%m%d','now'), LMOUSR='WEB'"
                f" WHERE SHPOKY IN ({ph2})",
                shpoky_list
            )

        # ⑤ PS_DISPATCH_H.STATUS → 'CANCELLED'
        conn.execute(
            f"UPDATE PS_DISPATCH_H SET STATUS='CANCELLED'"
            f" WHERE DISPATCH_NO IN ({ph})",
            stknums
        )
        conn.commit()
        return jsonify({
            "ok": True,
            "affected": affected,
            "stknums": stknums,
            "restore_vehicles": restore_vehicles,   # 배차탭 복원용
        })
    except Exception as e:
        conn.rollback()
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


@app.route('/api/ps-sap/transmit', methods=['POST'])
def api_ps_sap_transmit():
    """SAP 전송 (시뮬레이션 - 실제 SAP I/F 없는 경우 로그만 기록)
    body: { stknums: ['1001806886', ...] }
    """
    body    = request.json or {}
    stknums = body.get('stknums', [])
    if not stknums:
        return jsonify({"error": "stknums 필수"}), 400
    # 실제 SAP 연동 시 여기에 RFC/HTTP 호출 추가
    # 현재는 전송 시뮬레이션만 수행
    return jsonify({
        "ok": True,
        "transmitted": len(stknums),
        "stknums": stknums,
        "message": f"{len(stknums)}건 SAP 전송 완료 (시뮬레이션)"
    })


# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
#  SAP RFC 선적생성 / 선적삭제  (Z_TMS_SHIPMENT_CRDL)
#  RFC IMPORTING:
#    I_GUBUN : 'C' = 선적생성 / 'D' = 선적삭제
#    I_TKNUM : 선적번호 (삭제 시 사용)
#    I_VBELN : 납품문서 (선적생성 시 단건 사용 - 미사용)
#  TABLES:
#    T_VBELN : VBELN(납품문서) 목록 (선적생성 시 SVBELN 목록 전달)
#  RFC EXPORTING:
#    E_RETURN : {TYPE, CODE, MESSAGE, MESSAGE_V1..V4}
#    E_TKNUM  : 생성된 선적번호
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

# ══════════════════════════════════════════════════════════════
#  SAP RFC 연결 설정
#  - pyrfc(SAP NW RFC SDK 기반) 우선 사용
#  - pyrfc 미설치 시 → mock fallback (개발/테스트용)
#
#  JCo 커넥션 파라미터:
#    개발: ASHOST=10.2.14.210, SYSNUM=01, CLIENT=100
#    운영: ASHOST=10.2.14.200, SYSNUM=01, CLIENT=100
#    공통: USERID=WMS001, PASSWD=Klean22709290, LANG=KO
# ══════════════════════════════════════════════════════════════

# pyrfc import 시도 — 설치되지 않은 환경에서는 None으로 처리
try:
    import pyrfc as _pyrfc
    _PYRFC_AVAILABLE = True
except ImportError:
    _pyrfc = None
    _PYRFC_AVAILABLE = False


# ── SAP JCo 커넥션 파라미터 (환경별) ────────────────────────
_SAP_CONN_PARAMS = {
    'dev': {
        'ashost': '10.2.14.210',
        'sysnr':  '01',
        'sysid':  'DPQ',
        'client': '100',
        'user':   'WMS001',
        'passwd': 'Klean22709290',
        'lang':   'KO',
    },
    'prod': {
        'ashost': '10.2.14.200',
        'sysnr':  '01',
        'sysid':  'DPP',
        'client': '100',
        'user':   'WMS001',
        'passwd': 'Klean22709290',
        'lang':   'KO',
    },
}


def _get_wms_ifc_url(env: str = 'dev') -> str:
    """
    WMS_IFC301 공통처리 API URL (선적생성/삭제 후 WMS 동기화 호출용).
    환경 분기:
      - 'prod' : 운영환경  → wms.kleannara.com
      - 그 외  : 개발환경  → wmsdev.kleannara.com
    """
    if env == 'prod':
        return 'https://wms.kleannara.com/common/tmsApi/json/WMS_IFC301.data'
    else:
        return 'https://wmsdev.kleannara.com/common/tmsApi/json/WMS_IFC301.data'


def _detect_env() -> str:
    """
    실행 환경 자동 감지.
    - FLASK_ENV 또는 APP_ENV 환경변수가 'production' → 'prod'
    - 그 외(개발서버, 로컬) → 'dev'
    """
    import os
    flask_env = os.environ.get('FLASK_ENV', '').lower()
    app_env   = os.environ.get('APP_ENV', '').lower()
    if flask_env == 'production' or app_env == 'production':
        return 'prod'
    return 'dev'


def _call_wms_ifc301(stdlnr: str, tknum: str, gubun: str, env: str) -> dict:
    """
    선적생성/삭제 후 WMS_IFC301 공통처리 API 호출.
    params:
      stdlnr : 가선적번호 (= DISPATCH_NO / STDLNR)
      tknum  : SAP 선적번호 (생성 시 E_TKNUM, 삭제 시 I_TKNUM)
      gubun  : 'C'=선적생성 / 'D'=선적삭제
      env    : 'dev' | 'prod'
    """
    import requests as _req
    wms_url = _get_wms_ifc_url(env)
    payload = {
        "GUBUN":  gubun,
        "STDLNR": stdlnr,
        "TKNUM":  tknum,
    }
    try:
        resp = _req.post(wms_url, json=payload,
                         timeout=(5, 10),   # (connect timeout, read timeout)
                         verify=False)      # 개발환경 자체서명 인증서 허용
        resp.raise_for_status()
        return {"ok": True, "status_code": resp.status_code, "body": resp.text[:500]}
    except Exception as ex:
        return {"ok": False, "error": str(ex)}


def _call_sap_rfc_shipment(gubun: str, svbeln_list: list, tknum: str, env: str) -> dict:
    """
    Z_TMS_SHIPMENT_CRDL RFC 직접 호출 (pyrfc 사용).

    호출 흐름:
      1) pyrfc 설치 확인
      2) _SAP_CONN_PARAMS[env] 로 Connection 생성
      3) call_function('Z_TMS_SHIPMENT_CRDL', ...) 실행
      4) E_RETURN.TYPE == 'S' → 성공
      5) 연결 실패 / pyrfc 미설치 → mock fallback

    params:
      gubun       : 'C' 선적생성 / 'D' 선적삭제
      svbeln_list : 선적생성 시 납품문서 목록 (T_VBELN)
      tknum       : 선적삭제 시 SAP 선적번호 (I_TKNUM)
      env         : 'dev' | 'prod'
    """
    # ── pyrfc 미설치 → mock ──────────────────────────────────
    if not _PYRFC_AVAILABLE:
        return _sap_rfc_mock(gubun, tknum, reason='pyrfc_not_installed')

    conn_params = _SAP_CONN_PARAMS.get(env, _SAP_CONN_PARAMS['dev'])

    try:
        # ── SAP 연결 ──────────────────────────────────────────
        with _pyrfc.Connection(**conn_params) as conn:

            # T_VBELN 테이블 파라미터 구성
            t_vbeln = [{'VBELN': str(v).zfill(10)} for v in svbeln_list]

            # RFC 호출
            result = conn.call(
                'Z_TMS_SHIPMENT_CRDL',
                I_GUBUN = gubun,
                I_TKNUM = tknum or '',
                I_VBELN = '',
                T_VBELN = t_vbeln,
            )

        # ── 결과 파싱 ─────────────────────────────────────────
        e_return  = result.get('E_RETURN') or {}
        e_tknum   = (result.get('E_TKNUM') or '').strip()
        ret_type  = (e_return.get('TYPE') or '').strip()
        ret_msg   = (e_return.get('MESSAGE') or '').strip()

        # MESSAGE_V1~V4 조합
        for vi in ('MESSAGE_V1', 'MESSAGE_V2', 'MESSAGE_V3', 'MESSAGE_V4'):
            v = (e_return.get(vi) or '').strip()
            if v:
                ret_msg = f"{ret_msg} {v}".strip()

        ok_flag = (ret_type == 'S')

        return {
            "ok":       ok_flag,
            "E_RETURN": e_return,
            "E_TKNUM":  e_tknum,
            "mock":     False,
        }

    except Exception as ex:
        # ── 연결 실패 또는 RFC 오류 ──────────────────────────
        err_str = str(ex)
        # RFC 자체 오류(ABAP exception 등)는 mock 없이 에러 반환
        # 네트워크/연결 오류는 mock fallback
        is_conn_err = any(k in err_str.lower() for k in
                          ('connection', 'timeout', 'unreachable', 'refused',
                           'network', 'logon', 'communicationerror'))
        if is_conn_err:
            return _sap_rfc_mock(gubun, tknum, reason=f'conn_error: {err_str[:120]}')
        return {
            "ok":       False,
            "E_RETURN": {"TYPE": "E", "CODE": "", "MESSAGE": err_str[:300]},
            "E_TKNUM":  "",
            "mock":     False,
        }


def _sap_rfc_mock(gubun: str, tknum: str, reason: str = '') -> dict:
    """
    SAP RFC 연결 불가 시 개발/테스트용 mock 응답 반환.
    reason 문자열을 메시지에 포함하여 원인 파악 용이.
    """
    import time, random
    if gubun == 'C':
        # 선적생성 mock: 현재 시각 기반 더미 TKNUM
        ts        = int(time.time() * 1000) % 10_000_000
        mock_tknum = f"9{ts:07d}"
        return {
            "ok":   True,
            "E_RETURN": {
                "TYPE":    "S",
                "CODE":    "488",
                "MESSAGE": f"[MOCK] 선적문서 생성됨 [{mock_tknum}]  ({reason})",
            },
            "E_TKNUM": mock_tknum,
            "mock":    True,
        }
    else:
        return {
            "ok":   True,
            "E_RETURN": {
                "TYPE":    "S",
                "CODE":    "000",
                "MESSAGE": f"[MOCK] 선적문서 삭제 처리됨  ({reason})",
            },
            "E_TKNUM": "",
            "mock":    True,
        }


@app.route('/api/ps-sap/shipment-create', methods=['POST'])
def api_ps_sap_shipment_create():
    """
    SAP 선적생성 (I_GUBUN='C')
    body: { stknums: ['26XXXXX001T', ...] }
      - stknums : 가선적번호(STDLNR/DISPATCH_NO) 목록 — 1건 이상
      - 가선적번호별로 해당 SVBELN(SAP납품문서) 목록을 T_VBELN으로 전달
    반환: {
      ok, results: [
        { stdlnr, ok, tknum, mock, message, wms_result }
      ]
    }
    """
    body    = request.json or {}
    stknums = body.get('stknums', [])
    if not stknums:
        return jsonify({"error": "stknums 필수"}), 400

    env  = _detect_env()
    conn = get_conn()
    results = []

    try:
        for stdlnr in stknums:
            # ── 1. 해당 가선적번호의 SAP납품문서(SVBELN) 목록 조회 ──
            svbeln_rows = conn.execute(
                """SELECT DISTINCT SI.SVBELN
                   FROM SHPDI SI
                   WHERE SI.STATIT = 'NEW'
                     AND TRIM(SI.STDLNR) = ?
                     AND TRIM(COALESCE(SI.SVBELN,'')) != ''
                   ORDER BY SI.SVBELN""",
                [stdlnr.strip()]
            ).fetchall()
            svbeln_list = [r['SVBELN'] for r in svbeln_rows]

            if not svbeln_list:
                results.append({
                    "stdlnr": stdlnr, "ok": False,
                    "message": "SAP납품문서(SVBELN)가 없습니다."
                })
                continue

            # ── 2. SAP RFC Z_TMS_SHIPMENT_CRDL (I_GUBUN='C') 호출 ──
            rfc_result = _call_sap_rfc_shipment(
                gubun='C', svbeln_list=svbeln_list, tknum='', env=env
            )

            tknum   = rfc_result.get('E_TKNUM', '')
            ok_flag = rfc_result.get('ok', False)
            msg     = (rfc_result.get('E_RETURN') or {}).get('MESSAGE', '')
            is_mock = rfc_result.get('mock', False)

            # ── 3. RFC 성공 시 WMS_IFC301 공통처리 호출 ──
            # ※ MOCK 응답이더라도 ok_flag=True 이면 WMS 호출 시도 (tknum 필요)
            wms_result = None
            db_update_err = None
            if ok_flag:
                if tknum:
                    wms_result = _call_wms_ifc301(
                        stdlnr=stdlnr, tknum=tknum, gubun='C', env=env
                    )
                    # DB: PS_DISPATCH_H.STKNUM 업데이트 (SAP선적번호 기록)
                    try:
                        import datetime as _dt
                        conn.execute(
                            "UPDATE PS_DISPATCH_H SET STKNUM=?, UPDDAT=? WHERE DISPATCH_NO=?",
                            (tknum, _dt.datetime.now().strftime('%Y%m%d'), stdlnr)
                        )
                        conn.commit()
                    except Exception as db_ex:
                        db_update_err = str(db_ex)
                        app.logger.error(f"[shipment-create] DB STKNUM 업데이트 실패: {db_ex} / stdlnr={stdlnr} tknum={tknum}")
                else:
                    # RFC 성공이지만 TKNUM 없음 → 응답 메시지에 기록
                    msg = f"{msg} [경고: SAP TKNUM 미반환]".strip()

            results.append({
                "stdlnr":        stdlnr,
                "ok":            ok_flag,
                "tknum":         tknum,
                "mock":          is_mock,
                "message":       msg,
                "svbeln_cnt":    len(svbeln_list),
                "wms_result":    wms_result,
                "db_update_err": db_update_err,
                "env":           env,
            })

    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()

    all_ok = all(r['ok'] for r in results)
    return jsonify({"ok": all_ok, "results": results, "env": env})


@app.route('/api/ps-sap/shipment-delete', methods=['POST'])
def api_ps_sap_shipment_delete():
    """
    SAP 선적삭제 (I_GUBUN='D')
    body: { items: [{ stdlnr, tknum }, ...] }
      - stdlnr : 가선적번호 (DISPATCH_NO)
      - tknum  : SAP 선적번호 (STKNUM) — 삭제 대상
    반환: {
      ok, results: [
        { stdlnr, tknum, ok, mock, message, wms_result }
      ]
    }
    """
    body  = request.json or {}
    items = body.get('items', [])
    if not items:
        return jsonify({"error": "items 필수 [{stdlnr, tknum}, ...]"}), 400

    env  = _detect_env()
    conn = get_conn()
    results = []

    try:
        for item in items:
            stdlnr = (item.get('stdlnr') or '').strip()
            tknum  = (item.get('tknum')  or '').strip()

            if not tknum:
                results.append({
                    "stdlnr": stdlnr, "tknum": tknum, "ok": False,
                    "message": "SAP 선적번호(TKNUM)가 없습니다. 선적생성 후 삭제하세요."
                })
                continue

            # ── 1. SAP RFC Z_TMS_SHIPMENT_CRDL (I_GUBUN='D') 호출 ──
            rfc_result = _call_sap_rfc_shipment(
                gubun='D', svbeln_list=[], tknum=tknum, env=env
            )

            ok_flag = rfc_result.get('ok', False)
            msg     = (rfc_result.get('E_RETURN') or {}).get('MESSAGE', '')
            is_mock = rfc_result.get('mock', False)

            # ── 2. RFC 성공 시 WMS_IFC301 공통처리 호출 ──
            wms_result   = None
            db_update_err = None
            if ok_flag:
                wms_result = _call_wms_ifc301(
                    stdlnr=stdlnr, tknum=tknum, gubun='D', env=env
                )
                # DB: PS_DISPATCH_H.STKNUM 초기화
                try:
                    import datetime as _dt
                    conn.execute(
                        "UPDATE PS_DISPATCH_H SET STKNUM=NULL, UPDDAT=? WHERE DISPATCH_NO=?",
                        (_dt.datetime.now().strftime('%Y%m%d'), stdlnr)
                    )
                    conn.commit()
                except Exception as db_ex:
                    db_update_err = str(db_ex)
                    app.logger.error(f"[shipment-delete] DB STKNUM 초기화 실패: {db_ex} / stdlnr={stdlnr} tknum={tknum}")

            results.append({
                "stdlnr":        stdlnr,
                "tknum":         tknum,
                "ok":            ok_flag,
                "mock":          is_mock,
                "message":       msg,
                "wms_result":    wms_result,
                "db_update_err": db_update_err,
                "env":           env,
            })

    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()

    all_ok = all(r['ok'] for r in results)
    return jsonify({"ok": all_ok, "results": results, "env": env})


@app.route('/api/ps-dispatch/load-for-edit', methods=['POST'])
def api_ps_dispatch_load_for_edit():
    """
    저장된 배차(DRAFT) → 미저장 편집 상태로 불러오기
    body: { dispatch_nos: ["26XXXXXNNN", ...] }
    반환: { ok, vehicles: [...], search_rows: [...] }
      - vehicles : psopState.vehicles 형식 (STDLNR=null, _pendingId 생성)
      - search_rows: 해당 배차의 items를 납품문서 검색 형식으로 반환 (searchRows 병합용)
    """
    import math
    body         = request.json or {}
    dispatch_nos = body.get('dispatch_nos', [])
    if not dispatch_nos:
        return jsonify({"error": "dispatch_nos 없음"}), 400

    conn = get_conn()
    try:
        ph = ','.join('?' * len(dispatch_nos))

        # ── 1. PS_DISPATCH_H 조회 ──────────────────────────
        disp_rows = conn.execute(
            f"""SELECT h.DISPATCH_NO, h.CARTYPE, h.STATUS,
                       COALESCE(v.CARCLASS_CD,'') AS CARCLASS_CD,
                       h.RQSHPD,
                       h.DPTNKY, h.DPTNM,
                       COALESCE(h.TOTAL_KG, 0) AS TOTAL_KG,
                       COALESCE(h.TOTAL_CNT,0) AS TOTAL_CNT,
                       COALESCE(v.LOAD_TON,0)*1000 AS LOAD_KG
                FROM PS_DISPATCH_H h
                LEFT JOIN DS_VEHICLE v ON v.CARTYPE = h.CARTYPE
                WHERE h.DISPATCH_NO IN ({ph})
                ORDER BY h.RQSHPD, h.DISPATCH_NO""",
            dispatch_nos
        ).fetchall()

        if not disp_rows:
            return jsonify({"error": "해당 배차번호를 찾을 수 없습니다"}), 404

        # DRAFT 아닌 것 체크
        non_draft = [r['DISPATCH_NO'] for r in disp_rows if r['STATUS'] != 'DRAFT']
        if non_draft:
            return jsonify({"error": f"DRAFT 상태가 아닌 배차는 불러올 수 없습니다: {non_draft}"}), 400

        # ── 2. PS_DISPATCH_D 조회 (items) ─────────────────
        # SKUMA에 SKU_TYPE/SHPMTY 없음 → SHPDH에서 SHPMTY 조회, SKU_TYPE은 Python에서 계산
        disp_detail = conn.execute(
            f"""SELECT d.DISPATCH_NO, d.SEQ, d.SHPOKY, d.SHPOIT,
                       d.SKUKEY, d.DESC01, d.QTSHPO, d.UOMKEY,
                       d.DPTNKY, d.DPTNM, d.IS_SPLIT, d.ORG_SHPOKY, d.ORG_SHPOIT,
                       COALESCE(d.GRSWGT, 0) AS GRSWGT,
                       COALESCE(d.KG_WEIGHT, 0) AS KG_WEIGHT,
                       COALESCE(sh.SHPMTY,'') AS SHPMTY,
                       COALESCE(cm.CDESC1,'') AS SHPMTY_NM,
                       COALESCE(sh.RQSHPD, h.RQSHPD,'') AS RQSHPD,
                       COALESCE(d.DPTNM, bz.NAME01, '') AS DPTNM_FULL
                FROM PS_DISPATCH_D d
                JOIN PS_DISPATCH_H h ON h.DISPATCH_NO = d.DISPATCH_NO
                LEFT JOIN SHPDH sh ON sh.SHPOKY = d.SHPOKY
                LEFT JOIN CMCDV cm ON cm.CMCDKY='TASOTY' AND cm.CMCDVL=sh.SHPMTY
                LEFT JOIN BZPTN bz ON bz.PTNRKY = d.DPTNKY AND bz.PTNRTY = 'CT'
                WHERE d.DISPATCH_NO IN ({ph})
                ORDER BY d.DISPATCH_NO, d.SEQ""",
            dispatch_nos
        ).fetchall()

        # dispatch_no → items 맵
        items_map = {}
        for row in disp_detail:
            dn = row['DISPATCH_NO']
            if dn not in items_map:
                items_map[dn] = []
            it = dict(row)
            it['DPTNM']    = it.get('DPTNM_FULL') or it.get('DPTNM') or ''
            # SKU_TYPE: SKUKEY 기반 roll/board/other 판별
            sk = (it.get('SKUKEY') or '').strip()
            it['SKU_TYPE'] = 'roll' if _ps_is_roll(sk) else 'board' if _ps_is_board(sk) else 'other'
            items_map[dn].append(it)

        # ── 3. vehicles 배열 생성 ─────────────────────────
        vehicles = []
        for r in disp_rows:
            dn    = r['DISPATCH_NO']
            items = items_map.get(dn, [])
            vehicles.append({
                'DISPATCH_NO': dn,
                'cartype':     r['CARTYPE']     or '',
                'carclass_cd': r['CARCLASS_CD'] or '',
                'rqshpd':      r['RQSHPD']      or '',
                'dptnky':      r['DPTNKY']      or '',
                'dptnm':       r['DPTNM']       or '',
                'total_kg':    float(r['TOTAL_KG'] or 0),
                'total_cnt':   int(r['TOTAL_CNT'] or 0),
                'load_kg':     float(r['LOAD_KG'] or 0) or None,
                'items':       items,
            })

        # ── 4. search_rows: items → 납품문서 목록 형식으로 변환 ──
        # items_map의 가공된 아이템(SKU_TYPE 포함)을 사용
        search_rows = []
        seen_keys   = set()
        for dn, its in items_map.items():
            for it in its:
                key = f"{it['SHPOKY']}|{it['SHPOIT']}"
                if key in seen_keys:
                    continue
                seen_keys.add(key)
                search_rows.append({
                    'SHPOKY':     it.get('SHPOKY', ''),
                    'SHPOIT':     it.get('SHPOIT', ''),
                    'SKUKEY':     it.get('SKUKEY', '')   or '',
                    'DESC01':     it.get('DESC01', '')   or '',
                    'QTSHPO':     float(it.get('QTSHPO') or 0),
                    'UOMKEY':     it.get('UOMKEY', 'KG') or 'KG',
                    'DPTNKY':     it.get('DPTNKY', '')   or '',
                    'DPTNM':      it.get('DPTNM', '')    or '',
                    'GRSWGT':     float(it.get('GRSWGT')    or 0),
                    'KG_WEIGHT':  float(it.get('KG_WEIGHT') or 0),
                    'SKU_TYPE':   it.get('SKU_TYPE', '')  or '',
                    'SHPMTY':     it.get('SHPMTY', '')    or '',
                    'SHPMTY_NM':  it.get('SHPMTY_NM', '') or '',
                    'RQSHPD':     it.get('RQSHPD', '')    or '',
                    'IS_SPLIT':   int(it.get('IS_SPLIT') or 0),
                    'ORG_SHPOKY': it.get('ORG_SHPOKY', '') or '',
                    'DISPATCHED': True,   # 배차된 상태
                })

        return jsonify({
            "ok":          True,
            "vehicles":    vehicles,
            "search_rows": search_rows,
            "count":       len(vehicles),
        })
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


@app.route('/api/ps-dispatch/delete', methods=['POST'])
def api_ps_dispatch_delete():
    """배차 삭제 (DRAFT 상태만)"""
    body         = request.json or {}
    dispatch_nos = body.get('dispatch_nos', [])
    if not dispatch_nos:
        return jsonify({"error": "dispatch_nos 없음"}), 400
    conn = get_conn()
    try:
        placeholders = ','.join('?' * len(dispatch_nos))
        # DRAFT 상태 검증
        locked = conn.execute(
            f"SELECT DISPATCH_NO FROM PS_DISPATCH_H WHERE DISPATCH_NO IN ({placeholders}) AND STATUS<>'DRAFT'",
            dispatch_nos
        ).fetchall()
        if locked:
            return jsonify({"error": f"확정된 배차는 삭제할 수 없습니다: {[r[0] for r in locked]}"}), 400
        conn.execute(f"DELETE FROM PS_DISPATCH_D WHERE DISPATCH_NO IN ({placeholders})", dispatch_nos)
        conn.execute(f"DELETE FROM PS_DISPATCH_H WHERE DISPATCH_NO IN ({placeholders})", dispatch_nos)
        conn.commit()
        return jsonify({"ok": True, "deleted": len(dispatch_nos)})
    except Exception as e:
        conn.rollback()
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


@app.route('/api/ps-dispatch/split', methods=['POST'])
def api_ps_dispatch_split():
    """
    납품분할
    body: {
      org_shpoky, org_shpoit,   # 원본 납품문서
      splits: [{skukey, desc01, org_qty, split_qty, uomkey}]
    }
    → NEW_SHPOKY = {ORG_SHPOKY}-S01 / S02 ... 신규 채번
    → NEW_SHPOIT = 원본 org_shpoit 그대로 유지
    → PS_DISPATCH_SPLIT 삽입 + 분할된 가상 아이템 반환
    """
    from datetime import datetime
    body       = request.json or {}
    org_shpoky = body.get('org_shpoky','')
    org_shpoit = body.get('org_shpoit','')
    splits     = body.get('splits', [])

    if not org_shpoky or not org_shpoit:
        return jsonify({"error": "원본 납품문서 정보 없음"}), 400
    if not splits:
        return jsonify({"error": "splits 없음"}), 400

    today  = datetime.now().strftime('%Y%m%d')
    conn   = get_conn()
    try:
        # ── 신규 SHPOKY 채번: {ORG_SHPOKY}-S01, S02 ... ──
        # 이미 이 원본문서에서 분할된 최대 시퀀스 조회
        existing_rows = conn.execute(
            "SELECT NEW_SHPOKY FROM PS_DISPATCH_SPLIT WHERE ORG_SHPOKY=? AND STATUS='ACTIVE'",
            (org_shpoky,)
        ).fetchall()
        # 기존 분할번호에서 Sxx 부분 추출해 최대값 파악
        existing_seq = 0
        prefix_base  = f"{org_shpoky}-S"
        for row in existing_rows:
            nk = (row[0] or '')
            if nk.startswith(prefix_base):
                try:
                    seq_part   = int(nk[len(prefix_base):])
                    existing_seq = max(existing_seq, seq_part)
                except ValueError:
                    pass
        next_seq = existing_seq + 1   # 다음 분할 시퀀스 시작 번호

        result_items = []
        for i, sp in enumerate(splits):
            new_shpoky = f"{org_shpoky}-S{(next_seq + i):02d}"   # 예: 2004335315-S01
            new_shpoit = org_shpoit                               # 아이템번호는 원본 유지
            split_key  = f"SPL-{new_shpoky}-{new_shpoit}"
            org_qty    = float(sp.get('org_qty', 0))
            split_qty  = float(sp.get('split_qty', 0))
            rem_qty    = org_qty - split_qty

            conn.execute(
                """INSERT OR REPLACE INTO PS_DISPATCH_SPLIT
                   (SPLIT_KEY,ORG_SHPOKY,ORG_SHPOIT,NEW_SHPOKY,NEW_SHPOIT,
                    SKUKEY,DESC01,ORG_QTY,SPLIT_QTY,REM_QTY,UOMKEY,STATUS,CREDAT,CREUSR)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,  'ACTIVE',?,?)""",
                (split_key, org_shpoky, org_shpoit,
                 new_shpoky,   # ★ 신규 채번된 납품문서번호
                 new_shpoit,   # ★ 원본 아이템번호 유지
                 sp.get('skukey',''), sp.get('desc01',''),
                 org_qty, split_qty, rem_qty,
                 sp.get('uomkey','KG'),
                 today, 'SYSTEM')
            )
            result_items.append({
                'SPLIT_KEY':  split_key,
                'SHPOKY':     new_shpoky,   # ★ 신규 채번 납품문서번호
                'SHPOIT':     new_shpoit,   # 원본 아이템번호
                'SKUKEY':     sp.get('skukey',''),
                'DESC01':     sp.get('desc01',''),
                'QTSHPO':     split_qty,
                'UOMKEY':     sp.get('uomkey','KG'),
                'IS_SPLIT':   1,
                'ORG_SHPOKY': org_shpoky,
                'ORG_SHPOIT': org_shpoit,
            })
        conn.commit()
        return jsonify({"ok": True, "splits": result_items})
    except Exception as e:
        conn.rollback()
        import traceback
        return jsonify({"error": str(e), "trace": traceback.format_exc()}), 500
    finally:
        conn.close()


@app.route('/api/ps-dispatch/update-item', methods=['POST'])
def api_ps_dispatch_update_item():
    """
    수동배차: 납품문서 아이템을 특정 배차번호에 추가/이동
    body: { dispatch_no, action:'add'|'remove', items:[{SHPOKY,SHPOIT,...}] }
    """
    body        = request.json or {}
    dispatch_no = body.get('dispatch_no','')
    action      = body.get('action','add')
    items       = body.get('items', [])
    if not dispatch_no or not items:
        return jsonify({"error": "파라미터 없음"}), 400

    conn = get_conn()
    try:
        if action == 'add':
            # 현재 max SEQ 조회
            row = conn.execute(
                "SELECT MAX(SEQ) FROM PS_DISPATCH_D WHERE DISPATCH_NO=?", (dispatch_no,)
            ).fetchone()
            max_seq = (row[0] or 0)
            for i, it in enumerate(items, 1):
                seq = max_seq + i
                conn.execute(
                    """INSERT OR REPLACE INTO PS_DISPATCH_D
                       (DISPATCH_NO,SEQ,SHPOKY,SHPOIT,SKUKEY,DESC01,
                        QTSHPO,UOMKEY,DPTNKY,DPTNM,IS_SPLIT,ORG_SHPOKY,ORG_SHPOIT)
                       VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                    (dispatch_no, seq,
                     it.get('SHPOKY',''), it.get('SHPOIT',''),
                     it.get('SKUKEY',''), it.get('DESC01',''),
                     float(it.get('QTSHPO',0)), it.get('UOMKEY','KG'),
                     it.get('DPTNKY',''), it.get('DPTNM',''),
                     int(it.get('IS_SPLIT',0)),
                     it.get('ORG_SHPOKY',''), it.get('ORG_SHPOIT',''))
                )
            # TOTAL_KG, TOTAL_CNT 갱신
            conn.execute("""
                UPDATE PS_DISPATCH_H
                SET TOTAL_KG  = (SELECT COALESCE(SUM(QTSHPO),0) FROM PS_DISPATCH_D WHERE DISPATCH_NO=?),
                    TOTAL_CNT = (SELECT COUNT(*) FROM PS_DISPATCH_D WHERE DISPATCH_NO=?)
                WHERE DISPATCH_NO=?
            """, (dispatch_no, dispatch_no, dispatch_no))

        elif action == 'remove':
            for it in items:
                conn.execute(
                    "DELETE FROM PS_DISPATCH_D WHERE DISPATCH_NO=? AND SHPOKY=? AND SHPOIT=?",
                    (dispatch_no, it.get('SHPOKY',''), it.get('SHPOIT',''))
                )
            conn.execute("""
                UPDATE PS_DISPATCH_H
                SET TOTAL_KG  = (SELECT COALESCE(SUM(QTSHPO),0) FROM PS_DISPATCH_D WHERE DISPATCH_NO=?),
                    TOTAL_CNT = (SELECT COUNT(*) FROM PS_DISPATCH_D WHERE DISPATCH_NO=?)
                WHERE DISPATCH_NO=?
            """, (dispatch_no, dispatch_no, dispatch_no))

        conn.commit()
        # 갱신된 헤더 반환
        h = conn.execute(
            "SELECT * FROM PS_DISPATCH_H WHERE DISPATCH_NO=?", (dispatch_no,)
        ).fetchone()
        return jsonify({"ok": True, "header": dict(h) if h else {}})
    except Exception as e:
        conn.rollback()
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


@app.route('/api/ps-dispatch/create-manual', methods=['POST'])
def api_ps_dispatch_create_manual():
    """
    수기배차: 선택된 납품문서로 새 배차번호 생성
    body: { cartype, carclass_cd, items:[{SHPOKY,SHPOIT,...}] }
    처리:
      1. PS_DISPATCH_H + PS_DISPATCH_D 삽입
      2. SHPDI.STDLNR = DISPATCH_NO  (가선적번호 채번)
      3. SHPDH.VEHINO = carclass_cd  (배차 차량유형코드)
    """
    from datetime import datetime
    body        = request.json or {}
    cartype     = body.get('cartype','')
    carclass_cd = (body.get('carclass_cd') or '').strip()
    items       = body.get('items', [])
    if not items:
        return jsonify({"error": "items 없음"}), 400

    today = datetime.now().strftime('%Y%m%d')
    conn  = get_conn()
    try:
        rqshpd      = (items[0].get('RQSHPD') or today).replace('-','')
        dptnky      = items[0].get('DPTNKY','')
        dptnm       = items[0].get('DPTNM','')
        dispatch_no = _ps_next_dispatch_no(conn, rqshpd)
        total_kg    = sum(float(it.get('QTSHPO',0)) for it in items)

        conn.execute(
            """INSERT INTO PS_DISPATCH_H
               (DISPATCH_NO,DISPATCH_DT,RQSHPD,DPTNKY,DPTNM,CARTYPE,STATUS,
                TOTAL_KG,TOTAL_CNT,CREDAT,CREUSR)
               VALUES (?,?,?,?,?,?,'DRAFT',?,?,?,?)""",
            (dispatch_no, today, rqshpd, dptnky, dptnm, cartype,
             total_kg, len(items), today, 'SYSTEM')
        )

        shpoky_set = set()
        for seq, it in enumerate(items, 1):
            shpoky = it.get('SHPOKY','')
            shpoit = it.get('SHPOIT','')
            conn.execute(
                """INSERT INTO PS_DISPATCH_D
                   (DISPATCH_NO,SEQ,SHPOKY,SHPOIT,SKUKEY,DESC01,
                    QTSHPO,UOMKEY,DPTNKY,DPTNM,IS_SPLIT,ORG_SHPOKY,ORG_SHPOIT)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                (dispatch_no, seq,
                 shpoky, shpoit,
                 it.get('SKUKEY',''), it.get('DESC01',''),
                 float(it.get('QTSHPO',0)), it.get('UOMKEY','KG'),
                 it.get('DPTNKY',''), it.get('DPTNM',''),
                 0, '', '')
            )
            # ① SHPDI.STDLNR = DISPATCH_NO
            if shpoky and shpoit:
                conn.execute(
                    """UPDATE SHPDI
                       SET STDLNR = ?,
                           LMODAT = strftime('%Y%m%d','now'),
                           LMOUSR = 'WEB'
                       WHERE SHPOKY = ? AND SHPOIT = ?
                         AND STATIT = 'NEW'""",
                    (dispatch_no, shpoky, shpoit)
                )
            if shpoky:
                shpoky_set.add(shpoky)

        # ② SHPDH.VEHINO = carclass_cd, CARTON = carclass_cd, 차량 배정 필드 초기화
        if shpoky_set:
            ph = ','.join('?' * len(shpoky_set))
            conn.execute(
                f"""UPDATE SHPDH
                    SET VEHINO    = ?,
                        CARTON    = ?,
                        CARNO     = NULL,
                        DRIVER    = NULL,
                        DRIVERCEL = NULL,
                        LMODAT    = strftime('%Y%m%d','now'),
                        LMOUSR    = 'WEB'
                    WHERE SHPOKY IN ({ph})""",
                [carclass_cd or None, carclass_cd or None] + list(shpoky_set)
            )

        conn.commit()
        return jsonify({"ok": True, "dispatch_no": dispatch_no,
                        "cartype": cartype, "carclass_cd": carclass_cd,
                        "total_kg": total_kg})
    except Exception as e:
        conn.rollback()
        import traceback
        return jsonify({"error": str(e), "trace": traceback.format_exc()}), 500
    finally:
        conn.close()


def _migrate_db():
    """
    앱 기동 시 DB 스키마 마이그레이션 (컬럼 누락 시 자동 추가)
    ALTER TABLE ADD COLUMN은 이미 존재해도 오류 발생 → 존재 여부 먼저 확인
    """
    migrations = [
        # (테이블명, 컬럼명, 컬럼 DDL)
        ('DS_VEHICLE',    'PALLET_HEIGHT_M', 'REAL DEFAULT 0'),
        ('BZPTN_DETAIL',  'DEADLINE_TIME',   "TEXT DEFAULT ''"),
        ('BZPTN_DETAIL',  'MAX_TON',         "TEXT DEFAULT ''"),
        ('BZPTN_DETAIL',  'REGION_YN',       "TEXT DEFAULT ''"),
        ('PS_DISPATCH_D', 'GRSWGT',          'REAL DEFAULT 0'),
        ('PS_DISPATCH_D', 'KG_WEIGHT',       'REAL DEFAULT 0'),
    ]
    conn = sqlite3.connect(DB_PATH)
    try:
        for table, col, ddl in migrations:
            existing = [r[1] for r in conn.execute(f"PRAGMA table_info({table})").fetchall()]
            if col not in existing:
                conn.execute(f"ALTER TABLE {table} ADD COLUMN {col} {ddl}")
                conn.commit()
                print(f"[migrate] {table}.{col} 컬럼 추가 완료")
    except Exception as e:
        print(f"[migrate] 마이그레이션 오류: {e}")
    finally:
        conn.close()

_migrate_db()   # 앱 기동 시 자동 실행


# ══════════════════════════════════════════════════════════════════
#  서류관리 (Document Management)
#  테이블: DOC_FOLDER (폴더), DOC_FILE (파일)
#  파일 실체: DOC_UPLOAD_BASE/<folder_id>/<uuid>_<original>
# ══════════════════════════════════════════════════════════════════

def _init_doc_tables():
    """서류관리 테이블 초기화"""
    conn = sqlite3.connect(DB_PATH)
    try:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS DOC_FOLDER (
                FOLDER_ID   TEXT PRIMARY KEY,
                FOLDER_NM   TEXT NOT NULL,
                PARENT_ID   TEXT DEFAULT NULL,
                SORT_ORD    INTEGER DEFAULT 0,
                SYSTEM_YN   TEXT DEFAULT 'N',
                CRTUSR      TEXT DEFAULT 'SYSTEM',
                CRTDAT      TEXT DEFAULT '',
                CRTTIM      TEXT DEFAULT '',
                DEL_YN      TEXT DEFAULT 'N'
            )""")
        conn.execute("""
            CREATE TABLE IF NOT EXISTS DOC_FILE (
                FILE_ID     TEXT PRIMARY KEY,
                FOLDER_ID   TEXT NOT NULL,
                ORIG_NM     TEXT NOT NULL,
                SAVE_NM     TEXT NOT NULL,
                FILE_EXT    TEXT DEFAULT '',
                FILE_SIZE   INTEGER DEFAULT 0,
                OP_DATE     TEXT DEFAULT '',
                UPLOAD_DAT  TEXT DEFAULT '',
                UPLOAD_TIM  TEXT DEFAULT '',
                UPLOAD_USR  TEXT DEFAULT 'USER',
                NOTE        TEXT DEFAULT '',
                DEL_YN      TEXT DEFAULT 'N',
                FOREIGN KEY(FOLDER_ID) REFERENCES DOC_FOLDER(FOLDER_ID)
            )""")
        conn.commit()
        # 기본 폴더: PS운행일지 (시스템 폴더)
        exists = conn.execute(
            "SELECT 1 FROM DOC_FOLDER WHERE FOLDER_ID='FOLDER_PS_LOGBOOK'"
        ).fetchone()
        if not exists:
            today = datetime.date.today().strftime('%Y%m%d')
            now   = datetime.datetime.now().strftime('%H%M%S')
            conn.execute("""
                INSERT INTO DOC_FOLDER(FOLDER_ID,FOLDER_NM,PARENT_ID,SORT_ORD,SYSTEM_YN,CRTUSR,CRTDAT,CRTTIM,DEL_YN)
                VALUES('FOLDER_PS_LOGBOOK','PS운행일지',NULL,1,'Y','SYSTEM',?,?,'N')
            """, (today, now))
            conn.commit()
            print("[doc] 기본 폴더 'PS운행일지' 생성 완료")
    except Exception as e:
        print(f"[doc] 테이블 초기화 오류: {e}")
    finally:
        conn.close()

_init_doc_tables()


@app.route('/api/doc/folders', methods=['GET'])
def api_doc_folders():
    """폴더 목록 (트리 구조용)"""
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    try:
        rows = conn.execute("""
            SELECT f.FOLDER_ID, f.FOLDER_NM, f.PARENT_ID, f.SORT_ORD, f.SYSTEM_YN,
                   f.CRTUSR, f.CRTDAT, f.CRTTIM,
                   COUNT(CASE WHEN d.DEL_YN != 'Y' THEN 1 END) AS FILE_CNT
            FROM DOC_FOLDER f
            LEFT JOIN DOC_FILE d ON d.FOLDER_ID = f.FOLDER_ID
            WHERE f.DEL_YN != 'Y'
            GROUP BY f.FOLDER_ID
            ORDER BY f.SORT_ORD, f.FOLDER_NM
        """).fetchall()
        return jsonify({'ok': True, 'folders': [dict(r) for r in rows]})
    except Exception as e:
        return jsonify({'ok': False, 'error': str(e)}), 500
    finally:
        conn.close()


@app.route('/api/doc/folders/create', methods=['POST'])
def api_doc_folder_create():
    """폴더 생성 (사용자 직접 추가)"""
    data      = request.get_json(force=True)
    folder_nm = (data.get('folder_nm') or '').strip()
    parent_id = (data.get('parent_id') or None)
    if not folder_nm:
        return jsonify({'ok': False, 'error': '폴더명을 입력하세요'}), 400

    conn = sqlite3.connect(DB_PATH)
    try:
        today = datetime.date.today().strftime('%Y%m%d')
        now   = datetime.datetime.now().strftime('%H%M%S')
        # 같은 부모 아래 최대 sort_ord
        max_ord = conn.execute(
            "SELECT COALESCE(MAX(SORT_ORD),0) FROM DOC_FOLDER WHERE COALESCE(PARENT_ID,'')=COALESCE(?,'') AND DEL_YN!='Y'",
            (parent_id,)
        ).fetchone()[0]
        folder_id = 'F_' + uuid.uuid4().hex[:12].upper()
        conn.execute("""
            INSERT INTO DOC_FOLDER(FOLDER_ID,FOLDER_NM,PARENT_ID,SORT_ORD,SYSTEM_YN,CRTUSR,CRTDAT,CRTTIM,DEL_YN)
            VALUES(?,?,?,?,'N','USER',?,?,'N')
        """, (folder_id, folder_nm, parent_id, max_ord + 1, today, now))
        conn.commit()
        # 실제 디렉토리 생성
        os.makedirs(os.path.join(DOC_UPLOAD_BASE, folder_id), exist_ok=True)
        return jsonify({'ok': True, 'folder_id': folder_id, 'folder_nm': folder_nm})
    except Exception as e:
        return jsonify({'ok': False, 'error': str(e)}), 500
    finally:
        conn.close()


@app.route('/api/doc/folders/rename', methods=['POST'])
def api_doc_folder_rename():
    """폴더 이름 변경 (시스템 폴더 제외)"""
    data      = request.get_json(force=True)
    folder_id = (data.get('folder_id') or '').strip()
    new_nm    = (data.get('folder_nm') or '').strip()
    if not folder_id or not new_nm:
        return jsonify({'ok': False, 'error': '파라미터 오류'}), 400
    conn = sqlite3.connect(DB_PATH)
    try:
        row = conn.execute("SELECT SYSTEM_YN FROM DOC_FOLDER WHERE FOLDER_ID=?", (folder_id,)).fetchone()
        if not row:
            return jsonify({'ok': False, 'error': '폴더 없음'}), 404
        if row[0] == 'Y':
            return jsonify({'ok': False, 'error': '시스템 폴더는 이름을 변경할 수 없습니다'}), 403
        conn.execute("UPDATE DOC_FOLDER SET FOLDER_NM=? WHERE FOLDER_ID=?", (new_nm, folder_id))
        conn.commit()
        return jsonify({'ok': True})
    except Exception as e:
        return jsonify({'ok': False, 'error': str(e)}), 500
    finally:
        conn.close()


@app.route('/api/doc/folders/delete', methods=['POST'])
def api_doc_folder_delete():
    """폴더 삭제 (시스템 폴더 제외, 파일 있으면 거부)"""
    data      = request.get_json(force=True)
    folder_id = (data.get('folder_id') or '').strip()
    if not folder_id:
        return jsonify({'ok': False, 'error': '파라미터 오류'}), 400
    conn = sqlite3.connect(DB_PATH)
    try:
        row = conn.execute("SELECT SYSTEM_YN FROM DOC_FOLDER WHERE FOLDER_ID=? AND DEL_YN!='Y'", (folder_id,)).fetchone()
        if not row:
            return jsonify({'ok': False, 'error': '폴더 없음'}), 404
        if row[0] == 'Y':
            return jsonify({'ok': False, 'error': '시스템 폴더는 삭제할 수 없습니다'}), 403
        cnt = conn.execute("SELECT COUNT(*) FROM DOC_FILE WHERE FOLDER_ID=? AND DEL_YN!='Y'", (folder_id,)).fetchone()[0]
        if cnt > 0:
            return jsonify({'ok': False, 'error': f'파일 {cnt}건이 있어 삭제할 수 없습니다. 파일을 먼저 삭제하세요.'}), 409
        conn.execute("UPDATE DOC_FOLDER SET DEL_YN='Y' WHERE FOLDER_ID=?", (folder_id,))
        conn.commit()
        return jsonify({'ok': True})
    except Exception as e:
        return jsonify({'ok': False, 'error': str(e)}), 500
    finally:
        conn.close()


@app.route('/api/doc/files', methods=['GET'])
def api_doc_files():
    """특정 폴더의 파일 목록"""
    folder_id = request.args.get('folder_id', '').strip()
    op_from   = request.args.get('op_from', '').strip()   # 운행일자 from (YYYYMMDD)
    op_to     = request.args.get('op_to', '').strip()     # 운행일자 to
    up_from   = request.args.get('up_from', '').strip()   # 업로드일자 from
    up_to     = request.args.get('up_to', '').strip()     # 업로드일자 to
    kw        = request.args.get('kw', '').strip()        # 파일명 키워드

    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    try:
        sql = """
            SELECT f.FILE_ID, f.FOLDER_ID, fo.FOLDER_NM,
                   f.ORIG_NM, f.SAVE_NM, f.FILE_EXT, f.FILE_SIZE,
                   f.OP_DATE, f.UPLOAD_DAT, f.UPLOAD_TIM, f.UPLOAD_USR, f.NOTE
            FROM DOC_FILE f
            JOIN DOC_FOLDER fo ON fo.FOLDER_ID = f.FOLDER_ID
            WHERE f.DEL_YN != 'Y' AND fo.DEL_YN != 'Y'
        """
        params = []
        if folder_id:
            sql += " AND f.FOLDER_ID = ?"; params.append(folder_id)
        if op_from:
            sql += " AND f.OP_DATE >= ?"; params.append(op_from)
        if op_to:
            sql += " AND f.OP_DATE <= ?"; params.append(op_to)
        if up_from:
            sql += " AND f.UPLOAD_DAT >= ?"; params.append(up_from)
        if up_to:
            sql += " AND f.UPLOAD_DAT <= ?"; params.append(up_to)
        if kw:
            sql += " AND f.ORIG_NM LIKE ?"; params.append(f'%{kw}%')
        sql += " ORDER BY f.UPLOAD_DAT DESC, f.UPLOAD_TIM DESC"
        rows = conn.execute(sql, params).fetchall()
        return jsonify({'ok': True, 'files': [dict(r) for r in rows]})
    except Exception as e:
        return jsonify({'ok': False, 'error': str(e)}), 500
    finally:
        conn.close()


@app.route('/api/doc/upload', methods=['POST'])
def api_doc_upload():
    """파일 업로드 (멀티파트)"""
    folder_id = (request.form.get('folder_id') or '').strip()
    op_date   = (request.form.get('op_date') or '').strip()    # 운행일자 YYYYMMDD
    note      = (request.form.get('note') or '').strip()

    if not folder_id:
        return jsonify({'ok': False, 'error': 'folder_id 필수'}), 400

    # 폴더 존재 확인
    conn = sqlite3.connect(DB_PATH)
    try:
        row = conn.execute("SELECT FOLDER_ID FROM DOC_FOLDER WHERE FOLDER_ID=? AND DEL_YN!='Y'", (folder_id,)).fetchone()
        if not row:
            return jsonify({'ok': False, 'error': '폴더가 존재하지 않습니다'}), 404
    finally:
        conn.close()

    if 'file' not in request.files:
        return jsonify({'ok': False, 'error': '파일이 없습니다'}), 400

    files = request.files.getlist('file')
    if not files or all(f.filename == '' for f in files):
        return jsonify({'ok': False, 'error': '파일을 선택하세요'}), 400

    today = datetime.date.today().strftime('%Y%m%d')
    now   = datetime.datetime.now().strftime('%H%M%S')
    saved = []

    conn = sqlite3.connect(DB_PATH)
    try:
        folder_dir = os.path.join(DOC_UPLOAD_BASE, folder_id)
        os.makedirs(folder_dir, exist_ok=True)

        for f in files:
            if f.filename == '':
                continue
            orig_nm = f.filename
            ext     = orig_nm.rsplit('.', 1)[1].lower() if '.' in orig_nm else ''
            if ext and ext not in DOC_ALLOWED_EXT:
                return jsonify({'ok': False, 'error': f'{orig_nm}: 허용되지 않는 파일 형식입니다 ({ext})'}), 400

            file_id = 'DOC_' + uuid.uuid4().hex[:16].upper()
            save_nm = file_id + ('.' + ext if ext else '')
            save_path = os.path.join(folder_dir, save_nm)
            f.save(save_path)
            file_size = os.path.getsize(save_path)

            conn.execute("""
                INSERT INTO DOC_FILE(FILE_ID,FOLDER_ID,ORIG_NM,SAVE_NM,FILE_EXT,FILE_SIZE,
                                     OP_DATE,UPLOAD_DAT,UPLOAD_TIM,UPLOAD_USR,NOTE,DEL_YN)
                VALUES(?,?,?,?,?,?,?,?,?,'USER',?,'N')
            """, (file_id, folder_id, orig_nm, save_nm, ext, file_size,
                  op_date, today, now, note))
            saved.append({'file_id': file_id, 'orig_nm': orig_nm, 'file_size': file_size})

        conn.commit()
        return jsonify({'ok': True, 'saved': saved})
    except Exception as e:
        conn.rollback()
        return jsonify({'ok': False, 'error': str(e)}), 500
    finally:
        conn.close()


@app.route('/api/doc/download/<file_id>', methods=['GET'])
def api_doc_download(file_id):
    """파일 다운로드"""
    conn = sqlite3.connect(DB_PATH)
    try:
        row = conn.execute(
            "SELECT FOLDER_ID, ORIG_NM, SAVE_NM FROM DOC_FILE WHERE FILE_ID=? AND DEL_YN!='Y'",
            (file_id,)
        ).fetchone()
    finally:
        conn.close()

    if not row:
        abort(404)

    folder_id, orig_nm, save_nm = row
    file_path = os.path.join(DOC_UPLOAD_BASE, folder_id, save_nm)
    if not os.path.exists(file_path):
        abort(404)
    return send_file(file_path, as_attachment=True, download_name=orig_nm)


@app.route('/api/doc/preview/<file_id>', methods=['GET'])
def api_doc_preview(file_id):
    """파일 미리보기 (인라인 표시)"""
    conn = sqlite3.connect(DB_PATH)
    try:
        row = conn.execute(
            "SELECT FOLDER_ID, ORIG_NM, SAVE_NM, FILE_EXT FROM DOC_FILE WHERE FILE_ID=? AND DEL_YN!='Y'",
            (file_id,)
        ).fetchone()
    finally:
        conn.close()

    if not row:
        abort(404)

    folder_id, orig_nm, save_nm, ext = row
    file_path = os.path.join(DOC_UPLOAD_BASE, folder_id, save_nm)
    if not os.path.exists(file_path):
        abort(404)

    mime_map = {
        'pdf': 'application/pdf',
        'png': 'image/png', 'jpg': 'image/jpeg', 'jpeg': 'image/jpeg',
        'gif': 'image/gif', 'bmp': 'image/bmp', 'webp': 'image/webp',
        'tiff': 'image/tiff', 'tif': 'image/tiff', 'svg': 'image/svg+xml',
        'heic': 'image/heic', 'heif': 'image/heif',
    }
    mime = mime_map.get(ext, 'application/octet-stream')
    return send_file(file_path, mimetype=mime, as_attachment=False, download_name=orig_nm)


@app.route('/api/doc/delete', methods=['POST'])
def api_doc_delete():
    """파일 삭제 (논리 삭제 + 실체 파일도 제거)"""
    data    = request.get_json(force=True)
    file_id = (data.get('file_id') or '').strip()
    if not file_id:
        return jsonify({'ok': False, 'error': 'file_id 필수'}), 400

    conn = sqlite3.connect(DB_PATH)
    try:
        row = conn.execute(
            "SELECT FOLDER_ID, SAVE_NM FROM DOC_FILE WHERE FILE_ID=? AND DEL_YN!='Y'",
            (file_id,)
        ).fetchone()
        if not row:
            return jsonify({'ok': False, 'error': '파일 없음'}), 404
        folder_id, save_nm = row
        conn.execute("UPDATE DOC_FILE SET DEL_YN='Y' WHERE FILE_ID=?", (file_id,))
        conn.commit()
        # 실체 파일 삭제
        file_path = os.path.join(DOC_UPLOAD_BASE, folder_id, save_nm)
        if os.path.exists(file_path):
            os.remove(file_path)
        return jsonify({'ok': True})
    except Exception as e:
        return jsonify({'ok': False, 'error': str(e)}), 500
    finally:
        conn.close()


@app.route('/api/doc/update', methods=['POST'])
def api_doc_update():
    """파일 메타 수정 (운행일자, 비고)"""
    data    = request.get_json(force=True)
    file_id = (data.get('file_id') or '').strip()
    op_date = (data.get('op_date') or '').strip()
    note    = (data.get('note') or '').strip()
    if not file_id:
        return jsonify({'ok': False, 'error': 'file_id 필수'}), 400
    conn = sqlite3.connect(DB_PATH)
    try:
        conn.execute("UPDATE DOC_FILE SET OP_DATE=?, NOTE=? WHERE FILE_ID=? AND DEL_YN!='Y'",
                     (op_date, note, file_id))
        conn.commit()
        return jsonify({'ok': True})
    except Exception as e:
        return jsonify({'ok': False, 'error': str(e)}), 500
    finally:
        conn.close()


# ══════════════════════════════════════════════════════════════════
#  배차 제약 조건 관리 (DS_DISPATCH_CONSTRAINT / DS_DISPATCH_PROFILE)
# ══════════════════════════════════════════════════════════════════

def _init_constraint_tables():
    """
    배차 최적화 제약 조건 관리 테이블 초기화

    ① DS_DISPATCH_PROFILE  : 배차 프로파일 (이름·목적식·활성여부)
    ② DS_DISPATCH_CONST    : 개별 제약 조건 행
       constraint_type 분류:
         VEHICLE  – 차량 제약 (차종 허용여부·최대적재율 등)
         PARTNER  – 납품처 제약 (MAX_TON·MAX_HEIGHT·납기·지게차 등)
         CARGO    – 화물 제약 (롤수·인치·평량·높이·CBM 등)
         GLOBAL   – 전역 제약 (최대차량수·단일품목강제 등)
    """
    conn = sqlite3.connect(DB_PATH)
    try:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS DS_DISPATCH_PROFILE (
                PROFILE_ID   INTEGER PRIMARY KEY AUTOINCREMENT,
                PROFILE_NM   TEXT    NOT NULL,
                OBJECTIVE    TEXT    NOT NULL DEFAULT 'MIN_VEHICLES',
                  -- MIN_VEHICLES: 차량최소화
                  -- MAX_FILL:     적재율최대화
                  -- MIN_COST:     운송비최소화
                ACTIVE_YN    TEXT    NOT NULL DEFAULT 'Y',
                NOTE         TEXT    DEFAULT '',
                CREDAT       TEXT,
                LMODAT       TEXT
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS DS_DISPATCH_CONST (
                CONST_ID         INTEGER PRIMARY KEY AUTOINCREMENT,
                PROFILE_ID       INTEGER NOT NULL,
                CONST_TYPE       TEXT    NOT NULL,
                  -- VEHICLE / PARTNER / CARGO / GLOBAL
                CONST_KEY        TEXT    NOT NULL,
                  -- 제약 항목 식별자 (예: MAX_LOAD_RATIO, ALLOW_CARTYPE, etc.)
                CONST_VALUE      TEXT,
                  -- 값 (숫자·문자·Y/N 등, JSON 허용)
                CONST_OP         TEXT    DEFAULT '<=',
                  -- 연산자: <= >= = != IN BETWEEN
                TARGET_ID        TEXT    DEFAULT '',
                  -- 적용 대상 (CARTYPE 코드 or PTNRKY or '' = 전체)
                TARGET_NM        TEXT    DEFAULT '',
                  -- 적용 대상 이름 (표시용)
                ACTIVE_YN        TEXT    DEFAULT 'Y',
                NOTE             TEXT    DEFAULT '',
                SORT_SEQ         INTEGER DEFAULT 0,
                CREDAT           TEXT,
                LMODAT           TEXT
            )
        """)
        # 기본 프로파일이 없으면 삽입
        cnt = conn.execute("SELECT COUNT(*) FROM DS_DISPATCH_PROFILE").fetchone()[0]
        if cnt == 0:
            today = __import__('datetime').date.today().strftime('%Y%m%d')
            profiles = [
                ('차량최소화 기본', 'MIN_VEHICLES', 'Y', '차량 수를 최소화합니다 (FFD BinPacking 기본)'),
                ('적재율최대화',   'MAX_FILL',     'N', '각 차량의 적재율을 최대화합니다'),
                ('운송비최소화',   'MIN_COST',     'N', 'ROUTE_COST 기준 총 운송비를 최소화합니다'),
            ]
            for nm, obj, act, note in profiles:
                conn.execute(
                    "INSERT INTO DS_DISPATCH_PROFILE (PROFILE_NM,OBJECTIVE,ACTIVE_YN,NOTE,CREDAT,LMODAT)"
                    " VALUES (?,?,?,?,?,?)",
                    (nm, obj, act, note, today, today)
                )
            conn.commit()

            # 기본 제약 조건 삽입 (프로파일 1 기준)
            today2 = __import__('datetime').date.today().strftime('%Y%m%d')
            defaults = [
                # (PROFILE_ID, CONST_TYPE, CONST_KEY,          CONST_VALUE, CONST_OP, TARGET_ID, TARGET_NM, NOTE, SORT_SEQ)
                # ── 전역 제약 ──
                (1,'GLOBAL','MAX_VEHICLES_PER_GROUP','99','<=','','','그룹당 최대 배차 차량 수',10),
                (1,'GLOBAL','ALLOW_SPLIT_ITEM','Y','=','','','단일 아이템 납품분할 허용',20),
                (1,'GLOBAL','ALLOW_MIXED_LOAD','N','=','','','우편번호 앞 3자리 동일 납품처 혼적 허용',25),
                (1,'GLOBAL','MIN_FILL_RATIO','0','>=','','','최소 적재율(%) — 0=제한없음',30),
                (1,'GLOBAL','MAX_FILL_RATIO','100','<=','','','최대 적재율(%) — 초과배차 방지',40),
                # ── 차량 제약 ──
                (1,'VEHICLE','ALLOW_CARTYPE','Y','=','1.4톤','1.4톤','차종 허용여부',100),
                (1,'VEHICLE','ALLOW_CARTYPE','Y','=','3.5톤','3.5톤','차종 허용여부',101),
                (1,'VEHICLE','ALLOW_CARTYPE','Y','=','5톤',  '5톤',  '차종 허용여부',102),
                (1,'VEHICLE','ALLOW_CARTYPE','Y','=','5톤축','5톤축','차종 허용여부',103),
                (1,'VEHICLE','ALLOW_CARTYPE','Y','=','11톤', '11톤', '차종 허용여부',104),
                (1,'VEHICLE','ALLOW_CARTYPE','Y','=','15톤', '15톤', '차종 허용여부',105),
                (1,'VEHICLE','ALLOW_CARTYPE','Y','=','18톤', '18톤', '차종 허용여부',106),
                # ── 화물 제약 ──
                (1,'CARGO','MAX_ROLL_STACK_TIER','2','<=','','','최대 롤 적재 단수',200),
                (1,'CARGO','MAX_BOARD_HEIGHT_M','2.4','<=','','','판지 최대 적재 높이(m)',210),
                (1,'CARGO','ROLL_SINGLE_KG_FALLBACK','600','=','','','롤 단중 미등록 시 fallback(kg)',220),
                # ── 비용 제약 (MIN_COST 목적식용) ──
                (1,'COST','COST_REF_DATE','TODAY','=','','','운송비 기준일 (TODAY or YYYYMMDD)',300),
                (1,'COST','COST_PENALTY_OVER','1.5','=','','','초과적재 비용 패널티 배수',310),
            ]
            for row in defaults:
                conn.execute(
                    "INSERT INTO DS_DISPATCH_CONST"
                    " (PROFILE_ID,CONST_TYPE,CONST_KEY,CONST_VALUE,CONST_OP,"
                    "  TARGET_ID,TARGET_NM,NOTE,SORT_SEQ,ACTIVE_YN,CREDAT,LMODAT)"
                    " VALUES (?,?,?,?,?,?,?,?,?,'Y',?,?)",
                    (*row, today2, today2)
                )
            conn.commit()
        conn.close()
    except Exception as e:
        print(f"[init_constraint] 오류: {e}")
        conn.close()

_init_constraint_tables()

# ── 기존 DB 마이그레이션: 새로 추가된 기본 제약 누락분 보완 ──────────
def _migrate_constraint_defaults():
    """앱 업데이트로 추가된 기본 제약 행이 기존 DB에 없으면 INSERT OR IGNORE로 보완.
    앱 시작 시마다 실행 — 중복 방지(NOT EXISTS) 처리되어 있어 안전.

    [v2] 신규탭 12개(원지/판지/혼적/동적구역) 데이터 + DS_DISPATCH_CONST_SET_ITEM 연결 추가.
         DB 파일이 초기화되거나 새 환경에서 앱을 띄울 때도 신규탭이 자동으로 복원됨.
    """
    try:
        conn = sqlite3.connect(DB_PATH)
        today_m = __import__('datetime').date.today().strftime('%Y%m%d')

        # ── ① DS_DISPATCH_CONST_SET_ITEM PARAM_VALUE 컬럼 마이그레이션 ──────────
        # _init_objective_tables()에서 PARAM_VALUE 없이 테이블을 생성한 구버전 DB 보완
        try:
            conn.execute("ALTER TABLE DS_DISPATCH_CONST_SET_ITEM ADD COLUMN PARAM_VALUE TEXT DEFAULT NULL")
            conn.commit()
        except Exception:
            pass  # 이미 존재하면 무시

        # ── ② DS_DISPATCH_CONST 기존 누락 GLOBAL 항목 보완 ──────────────────────
        legacy_defaults = [
            # (PROFILE_ID, CONST_TYPE, CONST_KEY, CONST_VALUE, CONST_OP, TARGET_ID, TARGET_NM, NOTE, SORT_SEQ)
            (1,'GLOBAL','ALLOW_MIXED_LOAD','N','=','','','우편번호 앞 3자리 동일 납품처 혼적 허용',25),
        ]
        for row in legacy_defaults:
            conn.execute(
                "INSERT OR IGNORE INTO DS_DISPATCH_CONST"
                " (PROFILE_ID,CONST_TYPE,CONST_KEY,CONST_VALUE,CONST_OP,"
                "  TARGET_ID,TARGET_NM,NOTE,SORT_SEQ,ACTIVE_YN,CREDAT,LMODAT)"
                " SELECT ?,?,?,?,?,?,?,?,?,'Y',?,?"
                " WHERE NOT EXISTS ("
                "   SELECT 1 FROM DS_DISPATCH_CONST"
                "   WHERE PROFILE_ID=? AND CONST_TYPE=? AND CONST_KEY=?"
                " )",
                (*row, today_m, today_m, row[0], row[1], row[2])
            )

        # ── ③ 신규탭 12개 기본 데이터 (Excel §2~§4 기반) ─────────────────────────
        # 원지(ROLL_UNIT/ROLL_STACK/ROLL_INCH_MIX/ROLL_3D_VERIFY) 4개
        # 판지(BOARD_CBM_WEIGHT/BOARD_BULK_SPLIT/BOARD_FLEX_SPLIT/BOARD_3D_VERIFY) 4개
        # 혼적(MIX_Z_AXIS/MIX_Y_LIFO/MIX_DUAL_VERIFY) 3개
        # 동적구역(DYNAMIC_ZONE) 1개
        new_tab_defaults = [
            # ── 원지① 단위 배차 (ROLL_UNIT) ── 엑셀§2-1
            (1,'ROLL_UNIT','ROLL_INTEGER_ONLY',  'Y','=','','','원지 정수 롤 단위 강제 (분할 절대 불가)',1000),
            (1,'ROLL_UNIT','ROLL_SPLIT_ALLOWED', 'N','=','','','원지 분할 배차 금지 (엑셀§2-1)',        1010),
            (1,'ROLL_UNIT','ROLL_MIN_QTY',       '1','>=','','','원지 최소 배차 수량 (1롤)',            1020),
            # ── 원지② 다단 적재·높이 (ROLL_STACK) ── 엑셀§2-2
            (1,'ROLL_STACK','ROLL_MAX_TIER',        '3',   '<=','','','롤 최대 적재 단수 Hard Cap (3단)',                     1100),
            (1,'ROLL_STACK','ROLL_PALLET_DEDUCT_M', '0.15','=', '','','파레트 높이 차감값 (m) — 0.15m 기본',                 1110),
            (1,'ROLL_STACK','ROLL_PALLET_APPLY_YN', 'Y',   '=', '','','파레트 차감 적용 여부 (납품처 FORKLIFT_YN=N 시 적용)',1120),
            (1,'ROLL_STACK','ROLL_HEIGHT_MARGIN_M', '0',   '=', '','','적재 높이 안전 여유 마진 (m)',                         1130),
            # ── 원지③ 인치·평량 혼합 (ROLL_INCH_MIX) ── 엑셀§2-3
            (1,'ROLL_INCH_MIX','ROLL_INCH_MIX_ALLOW',  'Y',              '=','','','인치/평량 혼합 오더 허용 (엑셀§2-3)',            1200),
            (1,'ROLL_INCH_MIX','ROLL_2D_PACK_ENGINE',  'Y',              '=','','','혼합 규격 시 2D 바닥 패킹 연산 적용',            1210),
            (1,'ROLL_INCH_MIX','ROLL_SAME_INCH_FORCE', 'N',              '=','','','동일 인치 강제 여부 (N=혼재허용)',               1220),
            (1,'ROLL_INCH_MIX','ROLL_REF_12INCH_LT300','2,3,4,6,8,8,8', '=','','','12인치/평량300미만 차종별 1단 기준수(1.4t~18t)',1230),
            (1,'ROLL_INCH_MIX','ROLL_REF_12INCH_GE300','2,3,4,5,7,7,7', '=','','','12인치/평량300이상 차종별 1단 기준수(1.4t~18t)',1240),
            (1,'ROLL_INCH_MIX','ROLL_REF_3INCH_LT300', '3,5,10,12,14,14,15','=','','','3인치/평량300미만 차종별 1단 기준수(1.4t~18t)',1250),
            (1,'ROLL_INCH_MIX','ROLL_REF_3INCH_GE300', '3,5,10,12,14,14,15','=','','','3인치/평량300이상 차종별 1단 기준수(1.4t~18t)',1260),
            # ── 원지④ 3D 물리 검증 (ROLL_3D_VERIFY) ── 엑셀§2-4
            (1,'ROLL_3D_VERIFY','ROLL_3D_CHECK_YN',      'Y',    '=', '','','원지 3D 블록 검증 활성 (Dead Space 포함)',        1300),
            (1,'ROLL_3D_VERIFY','ROLL_3D_DEAD_SPACE_PCT','0',    '<=','','','Dead Space 허용 비율 (%) — 0=허용안함',           1310),
            (1,'ROLL_3D_VERIFY','ROLL_OVERSIZE_ACTION',  'SPLIT','=', '','','치수 초과 시 처리 방식 (SPLIT=분할/REJECT=제외)', 1320),
            # ── 판지① CBM·중량 이중 검증 (BOARD_CBM_WEIGHT) ── 엑셀§3-1
            (1,'BOARD_CBM_WEIGHT','BOARD_CBM_CHECK_YN',  'Y',  '=', '','','판지 CBM 자동계산 + Double-Threshold 검증 활성 (엑셀§3-1)',1400),
            (1,'BOARD_CBM_WEIGHT','BOARD_MAX_CBM_RATIO', '100','<=','','','가용 적재함 CBM 상한 (%)',                                   1410),
            (1,'BOARD_CBM_WEIGHT','BOARD_MAX_TON_RATIO', '100','<=','','','중량 상한 (%) — Double-Threshold 중량 기준',                 1420),
            (1,'BOARD_CBM_WEIGHT','BOARD_DUAL_THRESHOLD','Y',  '=', '','','중량+CBM 동시 초과 이중 임계치 검증 활성',                   1430),
            # ── 판지② 벌크·속포장 제약 (BOARD_BULK_SPLIT) ── 엑셀§3-2
            (1,'BOARD_BULK_SPLIT','BOARD_BULK_INTEGER_ONLY', 'Y',    '=','','','벌크 1PLT 단위 강제 (개체 내 분할 불가)',   1500),
            (1,'BOARD_BULK_SPLIT','BOARD_INNER_SPLIT_ALLOW', 'Y',    '=','','','속포장 속 단위 분할 허용',                  1510),
            (1,'BOARD_BULK_SPLIT','BOARD_INNER_STACK_ALLOW', 'Y',    '=','','','분할된 속을 판지 위 추가 적재 허용',         1520),
            (1,'BOARD_BULK_SPLIT','BOARD_INNER_OVERFLOW_ACT','SPLIT','=','','','속포장 초과 시 처리 (SPLIT=다음 차량 배정)',1530),
            # ── 판지③ 유연 분할 선적 (BOARD_FLEX_SPLIT) ── 엑셀§3-3
            (1,'BOARD_FLEX_SPLIT','BOARD_FLEX_SPLIT_YN','Y', '=','','','유연 분할 선적 활성 — 차량 한계 시 초과분만 후속 차량 (엑셀§3-3)',1600),
            (1,'BOARD_FLEX_SPLIT','BOARD_SPLIT_UNIT',   'EA','=','','','분할 단위 (EA=낱개속, PLT=파레트)',                               1610),
            (1,'BOARD_FLEX_SPLIT','BOARD_SPLIT_OVERFLOW','Y','=','','','초과분(속단위) 후속 차량 정확 배정 활성',                          1620),
            # ── 판지④ 3D 물리 검증 (BOARD_3D_VERIFY) ── 엑셀§3-4
            (1,'BOARD_3D_VERIFY','BOARD_3D_CHECK_YN',      'Y',  '=', '','','판지 3D 블록 검증 활성 (Dead Space 포함)',1700),
            (1,'BOARD_3D_VERIFY','BOARD_3D_DEAD_SPACE_PCT','0',  '<=','','','판지 Dead Space 허용 비율 (%)',             1710),
            (1,'BOARD_3D_VERIFY','BOARD_HEIGHT_MAX_M',     '2.4','<=','','','판지 스택 최대 높이 (m, 기본 2.4m)',        1720),
            # ── 혼적① Z축 수직 적재 순서 (MIX_Z_AXIS) ── 엑셀§4-1
            (1,'MIX_Z_AXIS','MIX_ROLL_BOTTOM_FORCE','Y','=','','','원지 하단·판지 상단 강제 고정 (물리적 압착 파손 방지 핵심)',1800),
            # ── 혼적② Y축 LIFO 배치 (MIX_Y_LIFO) ── 엑셀§4-2
            (1,'MIX_Y_LIFO','MIX_LIFO_ENABLE',  'Y','=','','','복수 납품처 LIFO 배치 활성 (나중하차→안쪽, 먼저하차→문쪽)',   1900),
            (1,'MIX_Y_LIFO','MIX_ZONE_SPLIT_YN','Y','=','','','원지·판지 배송처 상이 시 전후 Zone 분할 적재 자동 전환',1910),
            # ── 혼적③ 이중 복합 검증 (MIX_DUAL_VERIFY) ── 엑셀§4-3
            (1,'MIX_DUAL_VERIFY','MIX_WEIGHT_CHECK_YN','Y','=','','','혼적 총중량 검증 (원지+판지 ≤ 차량 최대 적재 중량)',             2000),
            (1,'MIX_DUAL_VERIFY','MIX_HEIGHT_CHECK_YN','Y','=','','','혼적 높이 검증 (파렛트고+원지다단높이+판지높이 ≤ 차량 최대 높이)',2010),
            (1,'MIX_DUAL_VERIFY','MIX_3D_CHECK_YN',    'Y','=','','','혼적 Dead Space 포함 3D 블록 종합 검증',                         2020),
            # ── 동적 구역 제약 (DYNAMIC_ZONE) ──
            (1,'DYNAMIC_ZONE','DYNAMIC_GROUP_BY',      'AREA_CD','=', '','','동적 그룹핑 기준 컬럼 — 동일 AREA_CD(권역) 납품처끼리 동적 배차 그룹 형성',2100),
            (1,'DYNAMIC_ZONE','DYNAMIC_DEFAULT_YN',    'Y',      '=', '','','납품처 DYNAMIC_YN 미설정 시 기본값 (Y=동적배차 가능, N=불가)',              2110),
            (1,'DYNAMIC_ZONE','DYNAMIC_MIN_GROUP_SIZE','1',      '>=','','','동적 그룹 최소 납품처 수 — 1=단독 납품처도 동적배차 허용',                   2120),
            (1,'DYNAMIC_ZONE','DYNAMIC_AREA_FORCE_YN', 'Y',      '=', '','','동일 구역(AREA_CD) 내 DYNAMIC_YN=Y 납품처는 반드시 동적 그룹 편입 강제',    2130),
        ]
        for row in new_tab_defaults:
            conn.execute(
                "INSERT OR IGNORE INTO DS_DISPATCH_CONST"
                " (PROFILE_ID,CONST_TYPE,CONST_KEY,CONST_VALUE,CONST_OP,"
                "  TARGET_ID,TARGET_NM,NOTE,SORT_SEQ,ACTIVE_YN,CREDAT,LMODAT)"
                " SELECT ?,?,?,?,?,?,?,?,?,'Y',?,?"
                " WHERE NOT EXISTS ("
                "   SELECT 1 FROM DS_DISPATCH_CONST"
                "   WHERE PROFILE_ID=? AND CONST_TYPE=? AND CONST_KEY=?"
                " )",
                (*row, today_m, today_m, row[0], row[1], row[2])
            )
        conn.commit()

        # ── ④ DS_DISPATCH_CONST_SET_ITEM: 모든 세트에 신규탭 연결 ──────────────
        # DS_DISPATCH_CONST_SET에 존재하는 모든 SET_ID에 대해
        # 신규탭 CONST 행이 SET_ITEM에 없으면 자동 연결 (중복 방지)
        new_tab_types = (
            'ROLL_UNIT','ROLL_STACK','ROLL_INCH_MIX','ROLL_3D_VERIFY',
            'BOARD_CBM_WEIGHT','BOARD_BULK_SPLIT','BOARD_FLEX_SPLIT','BOARD_3D_VERIFY',
            'MIX_Z_AXIS','MIX_Y_LIFO','MIX_DUAL_VERIFY',
            'DYNAMIC_ZONE',
        )
        placeholders = ','.join(['?'] * len(new_tab_types))
        # 모든 세트 ID 조회
        set_ids = [r[0] for r in conn.execute("SELECT SET_ID FROM DS_DISPATCH_CONST_SET").fetchall()]
        for sid in set_ids:
            # 해당 세트에 아직 연결되지 않은 신규탭 CONST_ID만 삽입
            conn.execute(
                f"INSERT OR IGNORE INTO DS_DISPATCH_CONST_SET_ITEM (SET_ID, CONST_ID, ACTIVE_YN, PARAM_VALUE)"
                f" SELECT ?, c.CONST_ID, 'Y', c.CONST_VALUE"
                f" FROM DS_DISPATCH_CONST c"
                f" WHERE c.CONST_TYPE IN ({placeholders})"
                f"   AND NOT EXISTS ("
                f"     SELECT 1 FROM DS_DISPATCH_CONST_SET_ITEM i"
                f"     WHERE i.SET_ID=? AND i.CONST_ID=c.CONST_ID"
                f"   )",
                (sid, *new_tab_types, sid)
            )
        conn.commit()
        conn.close()
        print(f"[migrate_constraint] 신규탭 12개 마이그레이션 완료 (세트 {len(set_ids)}개 반영)")
    except Exception as e:
        print(f"[migrate_constraint] 오류: {e}")

_migrate_constraint_defaults()


# ══════════════════════════════════════════════════════════════════
#  목적식 마스터 + 제약조건 조합(Set) 테이블 초기화
# ══════════════════════════════════════════════════════════════════

def _init_objective_tables():
    """
    ③ DS_DISPATCH_OBJECTIVE : 목적식 마스터 (CRUD 가능)
    ④ DS_DISPATCH_CONST_SET : 제약조건 조합(세트) 마스터
    ⑤ DS_DISPATCH_CONST_SET_ITEM : 조합에 속한 제약조건 항목
    """
    conn = sqlite3.connect(DB_PATH)
    try:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS DS_DISPATCH_OBJECTIVE (
                OBJ_ID    INTEGER PRIMARY KEY AUTOINCREMENT,
                OBJ_CODE  TEXT    NOT NULL UNIQUE,
                OBJ_NM    TEXT    NOT NULL,
                OBJ_ICON  TEXT    DEFAULT '🎯',
                OBJ_ALGO  TEXT    DEFAULT '',
                OBJ_DESC  TEXT    DEFAULT '',
                SORT_SEQ  INTEGER DEFAULT 0,
                ACTIVE_YN TEXT    DEFAULT 'Y',
                CREDAT    TEXT,
                LMODAT    TEXT
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS DS_DISPATCH_CONST_SET (
                SET_ID    INTEGER PRIMARY KEY AUTOINCREMENT,
                SET_NM    TEXT    NOT NULL,
                SET_DESC  TEXT    DEFAULT '',
                ACTIVE_YN TEXT    DEFAULT 'Y',
                CREDAT    TEXT,
                LMODAT    TEXT
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS DS_DISPATCH_CONST_SET_ITEM (
                ITEM_ID     INTEGER PRIMARY KEY AUTOINCREMENT,
                SET_ID      INTEGER NOT NULL,
                CONST_ID    INTEGER NOT NULL,
                ACTIVE_YN   TEXT    DEFAULT 'Y',
                PARAM_VALUE TEXT    DEFAULT NULL
            )
        """)
        # 프로파일에 SET_ID 컬럼 추가 (없으면)
        try:
            conn.execute("ALTER TABLE DS_DISPATCH_PROFILE ADD COLUMN SET_ID INTEGER DEFAULT NULL")
        except Exception:
            pass
        # 기본 목적식 삽입
        cnt = conn.execute("SELECT COUNT(*) FROM DS_DISPATCH_OBJECTIVE").fetchone()[0]
        if cnt == 0:
            today = __import__('datetime').date.today().strftime('%Y%m%d')
            objs = [
                ('MIN_VEHICLES', '차량 최소화',  '🚛', 'FFD BinPacking', '가능한 적은 차량으로 최대 적재 (FFD 알고리즘)', 10),
                ('MAX_FILL',     '적재율 최대화', '📊', 'BFD BinPacking', '각 차량을 가장 꽉 채우는 방식 (BFD 알고리즘)', 20),
                ('MIN_COST',     '운송비 최소화', '💰', 'ROUTE_COST',    'ROUTE_COST 기반 최저비용 차종 선택',           30),
            ]
            for code, nm, icon, algo, desc, seq in objs:
                conn.execute(
                    "INSERT INTO DS_DISPATCH_OBJECTIVE (OBJ_CODE,OBJ_NM,OBJ_ICON,OBJ_ALGO,OBJ_DESC,SORT_SEQ,ACTIVE_YN,CREDAT,LMODAT)"
                    " VALUES (?,?,?,?,?,?,'Y',?,?)",
                    (code, nm, icon, algo, desc, seq, today, today)
                )
        conn.commit()
        conn.close()
    except Exception as e:
        print(f"[init_objective] 오류: {e}")
        try: conn.close()
        except: pass

_init_objective_tables()


# ── 목적식 목록 조회 ──────────────────────────────────────────────
@app.route('/api/dispatch-objective/list', methods=['GET'])
def api_obj_list():
    conn = get_conn()
    try:
        rows = conn.execute(
            "SELECT * FROM DS_DISPATCH_OBJECTIVE ORDER BY SORT_SEQ, OBJ_ID"
        ).fetchall()
        return jsonify({"ok": True, "rows": [dict(r) for r in rows]})
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


# ── 목적식 저장 (INSERT / UPDATE) ────────────────────────────────
@app.route('/api/dispatch-objective/save', methods=['POST'])
def api_obj_save():
    data = request.get_json(force=True) or {}
    conn = get_conn()
    try:
        today = __import__('datetime').date.today().strftime('%Y%m%d')
        obj_id  = data.get('OBJ_ID')
        code    = (data.get('OBJ_CODE') or '').strip().upper()
        nm      = (data.get('OBJ_NM')   or '').strip()
        icon    = (data.get('OBJ_ICON') or '🎯').strip()
        algo    = (data.get('OBJ_ALGO') or '').strip()
        desc    = (data.get('OBJ_DESC') or '').strip()
        seq     = int(data.get('SORT_SEQ', 0))
        active  = data.get('ACTIVE_YN', 'Y')
        if not code or not nm:
            return jsonify({"ok": False, "error": "OBJ_CODE, OBJ_NM 필수"}), 400
        if obj_id:
            conn.execute(
                "UPDATE DS_DISPATCH_OBJECTIVE SET OBJ_CODE=?,OBJ_NM=?,OBJ_ICON=?,OBJ_ALGO=?,OBJ_DESC=?,SORT_SEQ=?,ACTIVE_YN=?,LMODAT=? WHERE OBJ_ID=?",
                (code, nm, icon, algo, desc, seq, active, today, obj_id)
            )
        else:
            conn.execute(
                "INSERT INTO DS_DISPATCH_OBJECTIVE (OBJ_CODE,OBJ_NM,OBJ_ICON,OBJ_ALGO,OBJ_DESC,SORT_SEQ,ACTIVE_YN,CREDAT,LMODAT)"
                " VALUES (?,?,?,?,?,?,?,?,?)",
                (code, nm, icon, algo, desc, seq, active, today, today)
            )
        conn.commit()
        return jsonify({"ok": True})
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500
    finally:
        conn.close()


# ── 목적식 삭제 ──────────────────────────────────────────────────
@app.route('/api/dispatch-objective/delete', methods=['POST'])
def api_obj_delete():
    data = request.get_json(force=True) or {}
    obj_id = data.get('OBJ_ID')
    if not obj_id:
        return jsonify({"ok": False, "error": "OBJ_ID 필수"}), 400
    conn = get_conn()
    try:
        conn.execute("DELETE FROM DS_DISPATCH_OBJECTIVE WHERE OBJ_ID=?", (obj_id,))
        conn.commit()
        return jsonify({"ok": True})
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500
    finally:
        conn.close()


# ── 목적식 활성화 (단일 활성 보장) ──────────────────────────────────
@app.route('/api/dispatch-objective/activate', methods=['POST'])
def api_obj_activate():
    """
    선택한 OBJ_ID를 ACTIVE_YN='Y'로, 나머지는 모두 'N'으로 설정.
    → 항상 딱 하나만 '적용중' 상태.
    body: { OBJ_ID: N }
    """
    data   = request.get_json(force=True) or {}
    obj_id = data.get('OBJ_ID')
    if not obj_id:
        return jsonify({"ok": False, "error": "OBJ_ID 필수"}), 400
    today = __import__('datetime').date.today().strftime('%Y%m%d')
    conn = get_conn()
    try:
        # 전체 비활성화 → 선택한 것만 활성화
        conn.execute("UPDATE DS_DISPATCH_OBJECTIVE SET ACTIVE_YN='N', LMODAT=?", (today,))
        conn.execute("UPDATE DS_DISPATCH_OBJECTIVE SET ACTIVE_YN='Y', LMODAT=? WHERE OBJ_ID=?", (today, obj_id))
        conn.commit()
        row = conn.execute("SELECT * FROM DS_DISPATCH_OBJECTIVE WHERE OBJ_ID=?", (obj_id,)).fetchone()
        return jsonify({"ok": True, "activated": dict(row) if row else {}})
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500
    finally:
        conn.close()


# ── 현재 활성 목적식 조회 ─────────────────────────────────────────
@app.route('/api/dispatch-objective/active', methods=['GET'])
def api_obj_active():
    """
    ACTIVE_YN='Y'인 목적식 1건 반환.
    없으면 첫 번째 목적식 반환 (fallback).
    """
    conn = get_conn()
    try:
        row = conn.execute(
            "SELECT * FROM DS_DISPATCH_OBJECTIVE WHERE ACTIVE_YN='Y' ORDER BY OBJ_ID LIMIT 1"
        ).fetchone()
        if not row:
            row = conn.execute(
                "SELECT * FROM DS_DISPATCH_OBJECTIVE ORDER BY SORT_SEQ, OBJ_ID LIMIT 1"
            ).fetchone()
        if not row:
            return jsonify({"ok": False, "error": "목적식 없음"}), 404
        # 해당 목적식과 연결된 활성 프로파일도 함께 반환
        prof = conn.execute(
            "SELECT * FROM DS_DISPATCH_PROFILE WHERE OBJECTIVE=? AND ACTIVE_YN='Y' ORDER BY PROFILE_ID LIMIT 1",
            (row['OBJ_CODE'],)
        ).fetchone()
        if not prof:
            prof = conn.execute(
                "SELECT * FROM DS_DISPATCH_PROFILE WHERE OBJECTIVE=? ORDER BY PROFILE_ID LIMIT 1",
                (row['OBJ_CODE'],)
            ).fetchone()
        return jsonify({
            "ok": True,
            "objective": dict(row),
            "profile":   dict(prof) if prof else None
        })
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500
    finally:
        conn.close()


# ── 제약조건 조합(Set) 목록 조회 ──────────────────────────────────
@app.route('/api/dispatch-const-set/list', methods=['GET'])
def api_set_list():
    conn = get_conn()
    try:
        sets = conn.execute(
            "SELECT s.*, (SELECT COUNT(*) FROM DS_DISPATCH_CONST_SET_ITEM i WHERE i.SET_ID=s.SET_ID) as ITEM_CNT"
            " FROM DS_DISPATCH_CONST_SET s ORDER BY s.SET_ID"
        ).fetchall()
        return jsonify({"ok": True, "rows": [dict(r) for r in sets]})
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


# ── 제약조건 조합 저장 (INSERT / UPDATE) ──────────────────────────
@app.route('/api/dispatch-const-set/save', methods=['POST'])
def api_set_save():
    data = request.get_json(force=True) or {}
    conn = get_conn()
    try:
        today  = __import__('datetime').date.today().strftime('%Y%m%d')
        set_id = data.get('SET_ID')
        nm     = (data.get('SET_NM')   or '').strip()
        desc   = (data.get('SET_DESC') or '').strip()
        active = data.get('ACTIVE_YN', 'Y')
        if not nm:
            return jsonify({"ok": False, "error": "SET_NM 필수"}), 400
        if set_id:
            conn.execute(
                "UPDATE DS_DISPATCH_CONST_SET SET SET_NM=?,SET_DESC=?,ACTIVE_YN=?,LMODAT=? WHERE SET_ID=?",
                (nm, desc, active, today, set_id)
            )
        else:
            cur = conn.execute(
                "INSERT INTO DS_DISPATCH_CONST_SET (SET_NM,SET_DESC,ACTIVE_YN,CREDAT,LMODAT) VALUES (?,?,?,?,?)",
                (nm, desc, active, today, today)
            )
            set_id = cur.lastrowid
        conn.commit()
        return jsonify({"ok": True, "SET_ID": set_id})
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500
    finally:
        conn.close()


# ── 제약조건 조합 삭제 ────────────────────────────────────────────
@app.route('/api/dispatch-const-set/delete', methods=['POST'])
def api_set_delete():
    data = request.get_json(force=True) or {}
    set_id = data.get('SET_ID')
    if not set_id:
        return jsonify({"ok": False, "error": "SET_ID 필수"}), 400
    conn = get_conn()
    try:
        conn.execute("DELETE FROM DS_DISPATCH_CONST_SET_ITEM WHERE SET_ID=?", (set_id,))
        conn.execute("DELETE FROM DS_DISPATCH_CONST_SET WHERE SET_ID=?", (set_id,))
        # 이 조합을 참조하던 프로파일의 SET_ID 초기화
        conn.execute("UPDATE DS_DISPATCH_PROFILE SET SET_ID=NULL WHERE SET_ID=?", (set_id,))
        conn.commit()
        return jsonify({"ok": True})
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500
    finally:
        conn.close()


# ── 조합에 속한 제약조건 항목 조회 ───────────────────────────────
@app.route('/api/dispatch-const-set/items', methods=['GET'])
def api_set_items():
    set_id = request.args.get('set_id')
    conn = get_conn()
    try:
        if set_id:
            rows = conn.execute(
                "SELECT i.ITEM_ID, i.SET_ID, i.CONST_ID, i.ACTIVE_YN, i.PARAM_VALUE,"
                "       c.CONST_TYPE, c.CONST_KEY, c.CONST_OP, c.CONST_VALUE,"
                "       c.TARGET_ID, c.TARGET_NM, c.NOTE, c.SORT_SEQ"
                " FROM DS_DISPATCH_CONST_SET_ITEM i"
                " JOIN DS_DISPATCH_CONST c ON c.CONST_ID=i.CONST_ID"
                " WHERE i.SET_ID=? ORDER BY c.CONST_TYPE, c.SORT_SEQ, c.CONST_ID",
                (set_id,)
            ).fetchall()
        else:
            rows = []
        return jsonify({"ok": True, "rows": [dict(r) for r in rows]})
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


# ── 세트별 전체 제약조건 조회 (미포함 항목도 포함, 세트 파라미터 오버라이드 반영) ─
@app.route('/api/dispatch-const-set/full', methods=['GET'])
def api_set_full():
    """세트에 포함 여부와 무관하게 전체 제약조건 반환 + 세트 포함 여부/PARAM_VALUE 포함"""
    set_id = request.args.get('set_id')
    conn = get_conn()
    try:
        # 전체 제약조건
        all_consts = conn.execute(
            "SELECT c.CONST_ID, c.PROFILE_ID, c.CONST_TYPE, c.CONST_KEY, c.CONST_OP,"
            "       c.CONST_VALUE, c.TARGET_ID, c.TARGET_NM, c.NOTE, c.ACTIVE_YN, c.SORT_SEQ,"
            "       p.PROFILE_NM"
            " FROM DS_DISPATCH_CONST c"
            " JOIN DS_DISPATCH_PROFILE p ON p.PROFILE_ID=c.PROFILE_ID"
            " ORDER BY c.CONST_TYPE, c.SORT_SEQ, c.CONST_ID"
        ).fetchall()
        # 세트 포함 아이템 (PARAM_VALUE 포함)
        included_map = {}
        if set_id:
            items = conn.execute(
                "SELECT CONST_ID, ITEM_ID, ACTIVE_YN, PARAM_VALUE"
                " FROM DS_DISPATCH_CONST_SET_ITEM WHERE SET_ID=?",
                (set_id,)
            ).fetchall()
            for it in items:
                included_map[it['CONST_ID']] = dict(it)
        result = []
        for c in all_consts:
            d = dict(c)
            item_info = included_map.get(d['CONST_ID'])
            d['IN_SET']     = 1 if item_info else 0
            d['ITEM_ID']    = item_info['ITEM_ID']    if item_info else None
            d['ITEM_ACTIVE']= item_info['ACTIVE_YN']  if item_info else 'N'
            d['PARAM_VALUE']= item_info['PARAM_VALUE'] if item_info else None
            result.append(d)
        return jsonify({"ok": True, "rows": result})
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


# ── 차량유형(DS_VEHICLE) 목록 조회 (제약조건 세트 CARTYPE 탭용) ──
@app.route('/api/dispatch-const-set/vehicle-types', methods=['GET'])
def api_set_vehicle_types():
    """DS_VEHICLE 전체 목록 반환 (CARTYPE 탭에서 참조)
    Returns: { ok, vehicles: [{CARCLASS_CD, CARTYPE, LENGTH_M, WIDTH_M, HEIGHT_M,
                               LOAD_TON, PALLET_HEIGHT_M, SORT_SEQ,
                               PALLET_CNT, LONG_AXIS_YN, DEFAULT_VEH_CNT,
                               USE_YN_PS, USE_YN_HL}] }
    USE_YN_PS: TMS_CARCLASS10.USARG1 (PS탭 사용여부)
    USE_YN_HL: TMS_CARCLASS20.USARG1 (HL탭 사용여부)
    """
    conn = get_conn()
    try:
        rows = conn.execute(
            """SELECT v.CARCLASS_CD, v.CARTYPE, v.LENGTH_M, v.WIDTH_M, v.HEIGHT_M,
                      v.LOAD_TON, v.PALLET_HEIGHT_M, v.SORT_SEQ,
                      v.PALLET_CNT, v.LONG_AXIS_YN, v.DEFAULT_VEH_CNT,
                      COALESCE(c10.USARG1,'Y') AS USE_YN_PS,
                      COALESCE(c20.USARG1,'Y') AS USE_YN_HL
               FROM DS_VEHICLE v
               LEFT JOIN CMCDV c10 ON c10.CMCDKY='TMS_CARCLASS10' AND c10.CMCDVL=v.CARCLASS_CD
               LEFT JOIN CMCDV c20 ON c20.CMCDKY='TMS_CARCLASS20' AND c20.CMCDVL=v.CARCLASS_CD
               ORDER BY v.SORT_SEQ"""
        ).fetchall()
        return jsonify({"ok": True, "vehicles": [dict(r) for r in rows]})
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500
    finally:
        conn.close()


# ── 세트의 CARTYPE 항목 저장/수정 (DS_DISPATCH_CONST 자동 생성 포함) ──
@app.route('/api/dispatch-const-set/cartype/save', methods=['POST'])
def api_set_cartype_save():
    """차량유형별 제약조건 세트 저장
    body: {
      set_id: int,
      items: [{
        carclass_cd: str,   # DS_VEHICLE.CARCLASS_CD
        cartype: str,        # DS_VEHICLE.CARTYPE
        field: str,          # 'LENGTH_M'|'HEIGHT_M'|'WIDTH_M'|'PALLET_HEIGHT_M'|'LOAD_TON'|'LOAD_CBM'|'PALLET_CNT'|'ALLOW_CARTYPE'|'LONG_AXIS_YN'
        active_yn: 'Y'|'N', # 이 항목 적용 여부
        param_value: str,    # 오버라이드 값 (null=DS_VEHICLE 기본값 사용)
      }]
    }
    전략:
      1) DS_DISPATCH_CONST에 CONST_TYPE='CARTYPE', TARGET_ID=CARCLASS_CD, CONST_KEY=field 행이 없으면 INSERT
      2) DS_DISPATCH_CONST_SET_ITEM에 (SET_ID, CONST_ID) UPSERT
    """
    import datetime
    data   = request.get_json(force=True) or {}
    set_id = data.get('set_id')
    items  = data.get('items', [])
    if not set_id:
        return jsonify({"ok": False, "error": "set_id 필수"}), 400

    today = datetime.date.today().strftime('%Y%m%d')
    conn  = get_conn()
    try:
        # 기존 CARTYPE 세트 아이템 삭제 (CONST_TYPE='CARTYPE' 인 것만)
        conn.execute("""
            DELETE FROM DS_DISPATCH_CONST_SET_ITEM
            WHERE SET_ID=?
              AND CONST_ID IN (
                  SELECT CONST_ID FROM DS_DISPATCH_CONST WHERE CONST_TYPE='CARTYPE'
              )
        """, (set_id,))

        saved = 0
        for it in items:
            if (it.get('active_yn') or 'N') == 'N':
                continue  # 비활성은 저장 안 함 (= 적용 안 함)

            carclass_cd = (it.get('carclass_cd') or '').strip()
            cartype     = (it.get('cartype') or '').strip()
            field       = (it.get('field') or '').strip()
            param_val   = it.get('param_value')  # None 가능

            if not carclass_cd or not field:
                continue

            # DS_DISPATCH_CONST에 CARTYPE 행 찾기 or 생성
            # PROFILE_ID=0 (전역 템플릿) 사용, 없으면 첫 번째 프로파일 ID 사용
            existing = conn.execute(
                "SELECT CONST_ID FROM DS_DISPATCH_CONST"
                " WHERE CONST_TYPE='CARTYPE' AND CONST_KEY=? AND TARGET_ID=?",
                (field, carclass_cd)
            ).fetchone()

            if existing:
                const_id = existing['CONST_ID']
            else:
                # DS_VEHICLE에서 기본값 조회
                vrow = conn.execute(
                    "SELECT * FROM DS_VEHICLE WHERE CARCLASS_CD=?", (carclass_cd,)
                ).fetchone()
                default_val = None
                if vrow:
                    vd = dict(vrow)
                    default_val = vd.get(field)
                    if default_val is not None:
                        default_val = str(default_val)

                # PROFILE_ID: 존재하는 프로파일 중 첫 번째 사용 (또는 1)
                pid_row = conn.execute(
                    "SELECT PROFILE_ID FROM DS_DISPATCH_PROFILE ORDER BY PROFILE_ID LIMIT 1"
                ).fetchone()
                profile_id = pid_row['PROFILE_ID'] if pid_row else 1

                # CONST_OP 결정
                op_map = {
                    'ALLOW_CARTYPE': '=', 'LONG_AXIS_YN': '=',
                    'PALLET_CNT': '<=', 'DEFAULT_VEH_CNT': '<=',
                }
                const_op = op_map.get(field, '<=')

                cur2 = conn.execute(
                    "INSERT INTO DS_DISPATCH_CONST"
                    " (PROFILE_ID, CONST_TYPE, CONST_KEY, CONST_VALUE, CONST_OP,"
                    "  TARGET_ID, TARGET_NM, ACTIVE_YN, NOTE, SORT_SEQ, CREDAT, LMODAT)"
                    " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    (profile_id, 'CARTYPE', field, default_val, const_op,
                     carclass_cd, cartype, 'Y', f'차량유형관리 연동', 0, today, today)
                )
                const_id = cur2.lastrowid

            # DS_DISPATCH_CONST_SET_ITEM INSERT
            conn.execute(
                "INSERT INTO DS_DISPATCH_CONST_SET_ITEM (SET_ID, CONST_ID, ACTIVE_YN, PARAM_VALUE)"
                " VALUES (?,?,?,?)",
                (set_id, const_id, 'Y', param_val)
            )
            saved += 1

        conn.commit()
        return jsonify({"ok": True, "saved": saved})
    except Exception as e:
        conn.rollback()
        return jsonify({"ok": False, "error": str(e)}), 500
    finally:
        conn.close()


# ── 납품처 진입 허용 톤수 목록 조회 (ENTRY_TON 탭용) ─────────────
# ── 동적 지역 묶음 배차 납품처 목록 조회 (REGION 탭용) ──────────
@app.route('/api/dispatch-const-set/region/list', methods=['GET'])
def api_set_region_list():
    """TMS_REGION 기반 납품처 지역 그룹 목록 반환
    SHPDH.DPTNKY → BZPTN.POSTCD → TMS_REGION(USARG3~USARG4) 범위 매핑
    우편번호 겹침 방지: MIN(CMCDVL) 기준 첫 번째 REGION 적용
    Returns: {
      ok,
      regions: [{ cmcdvl, region_nm, sido, postcd_from, postcd_to,
                  partners: [{ptnrky, name01, postcd, area_cd, region_yn}] }],
      unmatched: [{ptnrky, name01, postcd, area_cd}]   # 우편번호 미매핑 납품처
    }
    """
    conn = get_conn()
    try:
        # ① TMS_REGION 전체 목록
        tms_regions = conn.execute(
            """SELECT CMCDVL, CDESC1, CDESC2, USARG3, USARG4
               FROM CMCDV WHERE CMCDKY='TMS_REGION'
               ORDER BY CMCDVL"""
        ).fetchall()

        # ② SHPDH 기반 납품처 + BZPTN 명칭·우편번호 + BZPTN_DETAIL 기존 설정
        partners_raw = conn.execute(
            """SELECT DISTINCT h.DPTNKY AS PTNRKY,
                      COALESCE(b.NAME01, h.DPTNKY) AS NAME01,
                      COALESCE(b.POSTCD, '') AS POSTCD,
                      COALESCE(d.AREA_CD, '') AS AREA_CD,
                      COALESCE(d.WAREKY, 'W001') AS WAREKY,
                      COALESCE(b.OWNRKY, 'KN') AS OWNRKY,
                      COALESCE(d.REGION_YN, '') AS REGION_YN
               FROM SHPDH h
               LEFT JOIN BZPTN b ON b.PTNRKY=h.DPTNKY AND b.PTNRTY='CT'
               LEFT JOIN BZPTN_DETAIL d ON d.PTNRKY=h.DPTNKY AND d.PTNRTY='CT'
                                       AND (d.DEL_YN IS NULL OR d.DEL_YN != 'Y')
               WHERE h.DPTNKY IS NOT NULL AND trim(h.DPTNKY) != ''
               ORDER BY h.DPTNKY"""
        ).fetchall()

        # ③ 우편번호 → TMS_REGION 매핑 (Python에서 처리: 범위 중 첫 번째 매칭)
        region_list = [dict(r) for r in tms_regions]
        partners    = [dict(r) for r in partners_raw]

        # 지역별 납품처 그룹핑
        region_map   = {}   # cmcdvl → {meta + partners[]}
        unmatched    = []   # 우편번호 매핑 불가 납품처

        for p in partners:
            postcd = (p['POSTCD'] or '').strip()
            matched_region = None
            if postcd:
                for reg in region_list:
                    pf = (reg['USARG3'] or '').strip()
                    pt = (reg['USARG4'] or '').strip()
                    if pf and pt and pf <= postcd <= pt:
                        matched_region = reg
                        break
            if matched_region:
                key = matched_region['CMCDVL']
                if key not in region_map:
                    region_map[key] = {
                        'cmcdvl':     key,
                        'region_nm':  matched_region['CDESC1'],
                        'sido':       matched_region['CDESC2'],
                        'postcd_from': matched_region['USARG3'],
                        'postcd_to':   matched_region['USARG4'],
                        'partners':   [],
                    }
                region_map[key]['partners'].append(p)
            else:
                unmatched.append(p)

        # 시/도 → 구/군 정렬
        sorted_regions = sorted(
            region_map.values(),
            key=lambda r: (r['sido'], r['cmcdvl'])
        )
        # 각 지역 내 납품처도 코드순 정렬
        for reg in sorted_regions:
            reg['partners'].sort(key=lambda p: p['PTNRKY'])

        return jsonify({
            "ok": True,
            "regions":   sorted_regions,
            "unmatched": sorted(unmatched, key=lambda p: p['PTNRKY']),
        })
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500
    finally:
        conn.close()


# ── 동적 지역 묶음 배차 설정 저장 (BZPTN_DETAIL.REGION_YN) ──────
@app.route('/api/dispatch-const-set/region/save', methods=['POST'])
def api_set_region_save():
    """납품처 REGION_YN 일괄 저장
    body: {
      items: [{ptnrky, ptnrty, ownrky, wareky, region_yn}]
        region_yn: 'Y'=동일지역 묶음배차 허용 / 'N'=제외 / ''=미설정(기본:허용)
    }
    Returns: { ok, saved: int }
    """
    data  = request.get_json(force=True) or {}
    items = data.get('items', [])
    if not items:
        return jsonify({"ok": True, "saved": 0})
    conn = get_conn()
    try:
        import datetime as _dt
        today  = _dt.date.today().strftime('%Y%m%d')
        now_tm = _dt.datetime.now().strftime('%H%M%S')
        saved  = 0
        for it in items:
            ptnrky    = (it.get('ptnrky') or '').strip()
            ptnrty    = (it.get('ptnrty') or 'CT').strip()
            ownrky    = (it.get('ownrky') or 'KN').strip()
            wareky    = (it.get('wareky') or 'W001').strip() or 'W001'
            region_yn = (it.get('region_yn') or '').strip().upper()
            if not ptnrky:
                continue
            if region_yn not in ('Y', 'N', ''):
                region_yn = ''
            # BZPTN_DETAIL에 REGION_YN 컬럼이 없을 수 있으므로 동적으로 컬럼 추가
            conn.execute(
                """INSERT INTO BZPTN_DETAIL (PTNRKY, PTNRTY, OWNRKY, WAREKY, REGION_YN, LMODAT, LMOTIM, LMOUSR)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                   ON CONFLICT(PTNRKY, PTNRTY, OWNRKY, WAREKY) DO UPDATE SET
                     REGION_YN=excluded.REGION_YN,
                     LMODAT=excluded.LMODAT,
                     LMOTIM=excluded.LMOTIM,
                     LMOUSR=excluded.LMOUSR""",
                (ptnrky, ptnrty, ownrky, wareky, region_yn or None, today, now_tm, 'DCON_SET')
            )
            saved += 1
        conn.commit()
        return jsonify({"ok": True, "saved": saved})
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500
    finally:
        conn.close()


@app.route('/api/dispatch-const-set/entry-ton/list', methods=['GET'])
def api_set_entry_ton_list():
    """납품처 진입 허용 톤수 제한 목록 반환
    BZPTN_DETAIL 기준 + SHPDH 출고예정 납품처 UNION
    Returns: {
      ok,
      partners: [{ptnrky, ptnrty, ownrky, name01, area_cd, max_ton, auto_alloc_yn, wareky}],
      carclasses: [{value, label}]   # TMS_CARCLASS10 코드
    }
    """
    conn = get_conn()
    try:
        # 납품처 목록 — BZPTN_DETAIL 기준으로 조회 (설정된 값 우선 반영)
        # BZPTN_DETAIL에 없는 납품처는 SHPDH 출고예정 기준으로 추가 (UNION)
        partners = conn.execute(
            """SELECT PTNRKY, PTNRTY, OWNRKY, WAREKY, NAME01, AREA_CD, MAX_TON, AUTO_ALLOC_YN
               FROM (
                 -- BZPTN_DETAIL에 설정된 납품처 (기존 설정값 보존)
                 SELECT d.PTNRKY,
                        'CT'                           AS PTNRTY,
                        COALESCE(b.OWNRKY, 'KN')       AS OWNRKY,
                        COALESCE(d.WAREKY, 'W001')     AS WAREKY,
                        COALESCE(b.NAME01, d.PTNRKY)   AS NAME01,
                        d.AREA_CD,
                        d.MAX_TON,
                        d.AUTO_ALLOC_YN,
                        1 AS _src
                 FROM BZPTN_DETAIL d
                 LEFT JOIN BZPTN b ON b.PTNRKY=d.PTNRKY AND b.PTNRTY='CT'
                 WHERE (d.DEL_YN IS NULL OR d.DEL_YN != 'Y')

                 UNION

                 -- SHPDH 출고예정 납품처 중 BZPTN_DETAIL에 없는 것 추가
                 SELECT DISTINCT
                        h.DPTNKY                       AS PTNRKY,
                        'CT'                           AS PTNRTY,
                        COALESCE(b.OWNRKY, 'KN')       AS OWNRKY,
                        'W001'                         AS WAREKY,
                        COALESCE(b.NAME01, h.DPTNKY)   AS NAME01,
                        NULL                           AS AREA_CD,
                        NULL                           AS MAX_TON,
                        NULL                           AS AUTO_ALLOC_YN,
                        2 AS _src
                 FROM SHPDH h
                 LEFT JOIN BZPTN b ON b.PTNRKY=h.DPTNKY AND b.PTNRTY='CT'
                 WHERE h.DPTNKY IS NOT NULL AND trim(h.DPTNKY) != ''
                   AND h.DPTNKY NOT IN (
                     SELECT PTNRKY FROM BZPTN_DETAIL
                     WHERE DEL_YN IS NULL OR DEL_YN != 'Y'
                   )
               )
               ORDER BY AREA_CD NULLS LAST, PTNRKY"""
        ).fetchall()
        # TMS_CARCLASS10 코드 목록 (톤수 선택 드롭다운)
        carclasses = conn.execute(
            "SELECT CMCDVL as value, CDESC1 as label FROM CMCDV WHERE CMCDKY='TMS_CARCLASS10' ORDER BY CMCDVL"
        ).fetchall()
        return jsonify({
            "ok": True,
            "partners": [dict(r) for r in partners],
            "carclasses": [dict(r) for r in carclasses],
        })
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500
    finally:
        conn.close()


# ── 납품처 진입 허용 톤수 일괄 저장 (BZPTN_DETAIL.MAX_TON 업데이트) ──
@app.route('/api/dispatch-const-set/entry-ton/save', methods=['POST'])
def api_set_entry_ton_save():
    """납품처 MAX_TON 일괄 저장
    body: {
      items: [{ptnrky, ptnrty, ownrky, max_ton}]  # max_ton='' 이면 NULL
    }
    Returns: { ok, saved: int }
    """
    data  = request.get_json(force=True) or {}
    items = data.get('items', [])
    if not items:
        return jsonify({"ok": True, "saved": 0})
    conn = get_conn()
    try:
        import datetime as _dt
        today  = _dt.date.today().strftime('%Y%m%d')
        now_tm = _dt.datetime.now().strftime('%H%M%S')
        saved  = 0
        for it in items:
            ptnrky = (it.get('ptnrky') or '').strip()
            ptnrty = (it.get('ptnrty') or 'CT').strip()
            ownrky = (it.get('ownrky') or 'KN').strip()
            max_ton = (it.get('max_ton') or '').strip() or None
            if not ptnrky:
                continue
            wareky = (it.get('wareky') or 'W001').strip() or 'W001'
            conn.execute(
                """INSERT INTO BZPTN_DETAIL (PTNRKY, PTNRTY, OWNRKY, WAREKY, MAX_TON, LMODAT, LMOTIM, LMOUSR)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                   ON CONFLICT(PTNRKY, PTNRTY, OWNRKY, WAREKY) DO UPDATE SET
                     MAX_TON=excluded.MAX_TON,
                     LMODAT=excluded.LMODAT,
                     LMOTIM=excluded.LMOTIM,
                     LMOUSR=excluded.LMOUSR""",
                (ptnrky, ptnrty, ownrky, wareky, max_ton, today, now_tm, 'DCON_SET')
            )
            saved += 1
        conn.commit()
        return jsonify({"ok": True, "saved": saved})
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500
    finally:
        conn.close()


# ── 납품처 지게차 여부 목록 조회 (FORKLIFT 탭용) ──────────────────
@app.route('/api/dispatch-const-set/forklift/list', methods=['GET'])
def api_set_forklift_list():
    """납품처 지게차 여부 목록 반환
    BZPTN_DETAIL 기준 + SHPDH 출고예정 납품처 UNION
    Returns: {
      ok,
      partners: [{ptnrky, ptnrty, ownrky, name01, area_cd, forklift_yn, auto_alloc_yn, wareky}]
    }
    지게차 여부별 배차 동작:
      FORKLIFT_YN='Y' → 지게차 사용가능  → 파렛트 높이(m) 미적용 (적재 높이 = 차량 높이 그대로)
      FORKLIFT_YN='N' → 지게차 없음      → 파렛트 높이(m) 적용  (적재 가용 높이 = 차량 높이 - 파렛트 높이)
    """
    conn = get_conn()
    try:
        # 납품처 목록 — BZPTN_DETAIL 기준으로 조회 (설정된 값 우선 반영)
        # BZPTN_DETAIL에 없는 납품처는 SHPDH 출고예정 기준으로 추가 (UNION)
        partners = conn.execute(
            """SELECT PTNRKY, PTNRTY, OWNRKY, WAREKY, NAME01, AREA_CD, FORKLIFT_YN, AUTO_ALLOC_YN
               FROM (
                 -- BZPTN_DETAIL에 설정된 납품처 (기존 FORKLIFT_YN 값 보존)
                 SELECT d.PTNRKY,
                        'CT'                           AS PTNRTY,
                        COALESCE(b.OWNRKY, 'KN')       AS OWNRKY,
                        COALESCE(d.WAREKY, 'W001')     AS WAREKY,
                        COALESCE(b.NAME01, d.PTNRKY)   AS NAME01,
                        d.AREA_CD,
                        d.FORKLIFT_YN,
                        d.AUTO_ALLOC_YN,
                        1 AS _src
                 FROM BZPTN_DETAIL d
                 LEFT JOIN BZPTN b ON b.PTNRKY=d.PTNRKY AND b.PTNRTY='CT'
                 WHERE (d.DEL_YN IS NULL OR d.DEL_YN != 'Y')

                 UNION

                 -- SHPDH 출고예정 납품처 중 BZPTN_DETAIL에 없는 것 추가
                 SELECT DISTINCT
                        h.DPTNKY                       AS PTNRKY,
                        'CT'                           AS PTNRTY,
                        COALESCE(b.OWNRKY, 'KN')       AS OWNRKY,
                        'W001'                         AS WAREKY,
                        COALESCE(b.NAME01, h.DPTNKY)   AS NAME01,
                        NULL                           AS AREA_CD,
                        NULL                           AS FORKLIFT_YN,
                        NULL                           AS AUTO_ALLOC_YN,
                        2 AS _src
                 FROM SHPDH h
                 LEFT JOIN BZPTN b ON b.PTNRKY=h.DPTNKY AND b.PTNRTY='CT'
                 WHERE h.DPTNKY IS NOT NULL AND trim(h.DPTNKY) != ''
                   AND h.DPTNKY NOT IN (
                     SELECT PTNRKY FROM BZPTN_DETAIL
                     WHERE DEL_YN IS NULL OR DEL_YN != 'Y'
                   )
               )
               ORDER BY AREA_CD NULLS LAST, PTNRKY"""
        ).fetchall()
        return jsonify({"ok": True, "partners": [dict(r) for r in partners]})
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500
    finally:
        conn.close()


# ── 납품처 지게차 여부 일괄 저장 (BZPTN_DETAIL.FORKLIFT_YN 업데이트) ──
@app.route('/api/dispatch-const-set/forklift/save', methods=['POST'])
def api_set_forklift_save():
    """납품처 FORKLIFT_YN 일괄 저장
    body: {
      items: [{ptnrky, ptnrty, ownrky, forklift_yn}]  # forklift_yn: 'Y'|'N'|''
    }
    Returns: { ok, saved: int }
    납품처 관리(BZPTN_DETAIL)와 데이터 매핑 일관성 유지:
      - FORKLIFT_YN 값을 BZPTN_DETAIL에 직접 업데이트
      - 배차 엔진(PS 자동배차)은 이 값을 실시간으로 참조
      - FORKLIFT_YN='Y': effective_height = height_m (파렛트 높이 미차감)
      - FORKLIFT_YN='N': effective_height = height_m - pallet_height_m (파렛트 높이 차감)
    """
    data  = request.get_json(force=True) or {}
    items = data.get('items', [])
    if not items:
        return jsonify({"ok": True, "saved": 0})
    conn = get_conn()
    try:
        import datetime as _dt
        today  = _dt.date.today().strftime('%Y%m%d')
        now_tm = _dt.datetime.now().strftime('%H%M%S')
        saved  = 0
        for it in items:
            ptnrky    = (it.get('ptnrky') or '').strip()
            ptnrty    = (it.get('ptnrty') or 'CT').strip()
            ownrky    = (it.get('ownrky') or 'KN').strip()
            forklift_yn = (it.get('forklift_yn') or '').strip().upper()
            if not ptnrky:
                continue
            if forklift_yn not in ('Y', 'N', ''):
                forklift_yn = ''
            wareky = (it.get('wareky') or 'W001').strip() or 'W001'
            conn.execute(
                """INSERT INTO BZPTN_DETAIL (PTNRKY, PTNRTY, OWNRKY, WAREKY, FORKLIFT_YN, LMODAT, LMOTIM, LMOUSR)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                   ON CONFLICT(PTNRKY, PTNRTY, OWNRKY, WAREKY) DO UPDATE SET
                     FORKLIFT_YN=excluded.FORKLIFT_YN,
                     LMODAT=excluded.LMODAT,
                     LMOTIM=excluded.LMOTIM,
                     LMOUSR=excluded.LMOUSR""",
                (ptnrky, ptnrty, ownrky, wareky, forklift_yn or None, today, now_tm, 'DCON_SET')
            )
            saved += 1
        conn.commit()
        return jsonify({"ok": True, "saved": saved})
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500
    finally:
        conn.close()


# ── 납품처 동적 여부 목록 조회 (DYNAMIC 탭용) ──────────────────────
@app.route('/api/dispatch-const-set/dynamic/list', methods=['GET'])
def api_set_dynamic_list():
    """납품처 동적 여부 목록 반환
    BZPTN_DETAIL 기준 + SHPDH 출고예정 납품처 UNION
    Returns: {
      ok,
      partners: [{ptnrky, ptnrty, ownrky, name01, area_cd, dynamic_yn, auto_alloc_yn, wareky}]
    }
    동적 여부별 배차 동작:
      DYNAMIC_YN='Y' → 동적배차 가능  (납품처 동적 그룹핑 허용)
      DYNAMIC_YN='N' → 동적배차 불가  (납품처 동적 그룹핑 제외)
      DYNAMIC_YN=NULL/'' → 미설정 → 동적배차 가능 (기본값)
    """
    conn = get_conn()
    try:
        # 납품처 목록 — BZPTN_DETAIL 기준으로 조회 (설정된 값 우선 반영)
        # BZPTN_DETAIL에 없는 납품처는 SHPDH 출고예정 기준으로 추가 (UNION)
        partners = conn.execute(
            """SELECT PTNRKY, PTNRTY, OWNRKY, WAREKY, NAME01, AREA_CD, DYNAMIC_YN, AUTO_ALLOC_YN
               FROM (
                 -- BZPTN_DETAIL에 설정된 납품처 (기존 DYNAMIC_YN 값 보존)
                 SELECT d.PTNRKY,
                        'CT'                           AS PTNRTY,
                        COALESCE(b.OWNRKY, 'KN')       AS OWNRKY,
                        COALESCE(d.WAREKY, 'W001')     AS WAREKY,
                        COALESCE(b.NAME01, d.PTNRKY)   AS NAME01,
                        d.AREA_CD,
                        d.DYNAMIC_YN,
                        d.AUTO_ALLOC_YN,
                        1 AS _src
                 FROM BZPTN_DETAIL d
                 LEFT JOIN BZPTN b ON b.PTNRKY=d.PTNRKY AND b.PTNRTY='CT'
                 WHERE (d.DEL_YN IS NULL OR d.DEL_YN != 'Y')

                 UNION

                 -- SHPDH 출고예정 납품처 중 BZPTN_DETAIL에 없는 것 추가
                 SELECT DISTINCT
                        h.DPTNKY                       AS PTNRKY,
                        'CT'                           AS PTNRTY,
                        COALESCE(b.OWNRKY, 'KN')       AS OWNRKY,
                        'W001'                         AS WAREKY,
                        COALESCE(b.NAME01, h.DPTNKY)   AS NAME01,
                        NULL                           AS AREA_CD,
                        NULL                           AS DYNAMIC_YN,
                        NULL                           AS AUTO_ALLOC_YN,
                        2 AS _src
                 FROM SHPDH h
                 LEFT JOIN BZPTN b ON b.PTNRKY=h.DPTNKY AND b.PTNRTY='CT'
                 WHERE h.DPTNKY IS NOT NULL AND trim(h.DPTNKY) != ''
                   AND h.DPTNKY NOT IN (
                     SELECT PTNRKY FROM BZPTN_DETAIL
                     WHERE DEL_YN IS NULL OR DEL_YN != 'Y'
                   )
               )
               ORDER BY AREA_CD NULLS LAST, PTNRKY"""
        ).fetchall()
        return jsonify({"ok": True, "partners": [dict(r) for r in partners]})
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500
    finally:
        conn.close()


# ── 납품처 동적 여부 일괄 저장 (BZPTN_DETAIL.DYNAMIC_YN 업데이트) ──
@app.route('/api/dispatch-const-set/dynamic/save', methods=['POST'])
def api_set_dynamic_save():
    """납품처 DYNAMIC_YN 일괄 저장
    body: {
      items: [{ptnrky, ptnrty, ownrky, dynamic_yn}]  # dynamic_yn: 'Y'|'N'|''
    }
    Returns: { ok, saved: int }
    납품처 관리(BZPTN_DETAIL)와 데이터 매핑 일관성 유지:
      - DYNAMIC_YN 값을 BZPTN_DETAIL에 직접 업데이트
      - 배차 엔진은 이 값을 실시간으로 참조
      - DYNAMIC_YN='Y': 동적배차 가능 (납품처 동적 그룹핑 허용)
      - DYNAMIC_YN='N': 동적배차 불가 (납품처 동적 그룹핑 제외)
      - DYNAMIC_YN=NULL: 미설정 → 동적배차 가능 처리 (기본값)
    """
    data  = request.get_json(force=True) or {}
    items = data.get('items', [])
    if not items:
        return jsonify({"ok": True, "saved": 0})
    conn = get_conn()
    try:
        import datetime as _dt
        today  = _dt.date.today().strftime('%Y%m%d')
        now_tm = _dt.datetime.now().strftime('%H%M%S')
        saved  = 0
        for it in items:
            ptnrky    = (it.get('ptnrky') or '').strip()
            ptnrty    = (it.get('ptnrty') or 'CT').strip()
            ownrky    = (it.get('ownrky') or 'KN').strip()
            dynamic_yn = (it.get('dynamic_yn') or '').strip().upper()
            if not ptnrky:
                continue
            if dynamic_yn not in ('Y', 'N', ''):
                dynamic_yn = ''
            wareky = (it.get('wareky') or 'W001').strip() or 'W001'
            conn.execute(
                """INSERT INTO BZPTN_DETAIL (PTNRKY, PTNRTY, OWNRKY, WAREKY, DYNAMIC_YN, LMODAT, LMOTIM, LMOUSR)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                   ON CONFLICT(PTNRKY, PTNRTY, OWNRKY, WAREKY) DO UPDATE SET
                     DYNAMIC_YN=excluded.DYNAMIC_YN,
                     LMODAT=excluded.LMODAT,
                     LMOTIM=excluded.LMOTIM,
                     LMOUSR=excluded.LMOUSR""",
                (ptnrky, ptnrty, ownrky, wareky, dynamic_yn or None, today, now_tm, 'DCON_SET')
            )
            saved += 1
        conn.commit()
        return jsonify({"ok": True, "saved": saved})
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500
    finally:
        conn.close()


# ── 조합 아이템 일괄 저장 (체크박스 + PARAM_VALUE 포함) ─────────
@app.route('/api/dispatch-const-set/items/save', methods=['POST'])
def api_set_items_save():
    """body: { set_id, items: [{const_id, active_yn, param_value}, ...] }
       하위 호환: const_ids 배열도 지원
    """
    data = request.get_json(force=True) or {}
    set_id = data.get('set_id')
    if not set_id:
        return jsonify({"ok": False, "error": "set_id 필수"}), 400

    # items 배열 우선, 없으면 const_ids 배열 하위 호환
    items = data.get('items')
    if items is None:
        const_ids = data.get('const_ids', [])
        items = [{'const_id': cid, 'active_yn': 'Y', 'param_value': None} for cid in const_ids]

    conn = get_conn()
    try:
        conn.execute("DELETE FROM DS_DISPATCH_CONST_SET_ITEM WHERE SET_ID=?", (set_id,))
        for it in items:
            cid   = it.get('const_id')
            yn    = (it.get('active_yn') or 'Y').strip()
            pval  = it.get('param_value')  # None이면 NULL
            if not cid:
                continue
            conn.execute(
                "INSERT INTO DS_DISPATCH_CONST_SET_ITEM (SET_ID,CONST_ID,ACTIVE_YN,PARAM_VALUE)"
                " VALUES (?,?,?,?)",
                (set_id, cid, yn, pval)
            )
        conn.commit()
        return jsonify({"ok": True, "saved": len(items)})
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500
    finally:
        conn.close()


# ── 프로파일에 조합 연결 ─────────────────────────────────────────
@app.route('/api/dispatch-constraint/profiles/link-set', methods=['POST'])
def api_profile_link_set():
    """body: { profile_id, set_id }"""
    data    = request.get_json(force=True) or {}
    prof_id = data.get('profile_id')
    set_id  = data.get('set_id')   # None이면 연결 해제
    if not prof_id:
        return jsonify({"ok": False, "error": "profile_id 필수"}), 400
    conn = get_conn()
    try:
        today = __import__('datetime').date.today().strftime('%Y%m%d')
        conn.execute(
            "UPDATE DS_DISPATCH_PROFILE SET SET_ID=?, LMODAT=? WHERE PROFILE_ID=?",
            (set_id, today, prof_id)
        )
        conn.commit()
        return jsonify({"ok": True})
    except Exception as e:
        return jsonify({"ok": False, "error": str(e)}), 500
    finally:
        conn.close()


# ── 전체 제약조건 목록 (조합 구성용: 프로파일 무관) ──────────────
@app.route('/api/dispatch-constraint/all', methods=['GET'])
def api_dcon_all():
    """조합 구성 시 선택할 수 있는 전체 제약조건 반환 (PROFILE_ID별 그룹)"""
    conn = get_conn()
    try:
        rows = conn.execute(
            "SELECT c.*, p.PROFILE_NM"
            " FROM DS_DISPATCH_CONST c"
            " JOIN DS_DISPATCH_PROFILE p ON p.PROFILE_ID=c.PROFILE_ID"
            " ORDER BY c.CONST_TYPE, c.SORT_SEQ, c.CONST_ID"
        ).fetchall()
        return jsonify({"ok": True, "rows": [dict(r) for r in rows]})
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


# ── 프로파일 목록 조회 ──────────────────────────────────────────────
@app.route('/api/dispatch-constraint/profiles', methods=['GET'])
def api_dcon_profiles():
    conn = get_conn()
    try:
        rows = conn.execute(
            "SELECT * FROM DS_DISPATCH_PROFILE ORDER BY PROFILE_ID"
        ).fetchall()
        return jsonify({"ok": True, "rows": [dict(r) for r in rows]})
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


# ── 프로파일 저장 (INSERT / UPDATE) ────────────────────────────────
@app.route('/api/dispatch-constraint/profiles/save', methods=['POST'])
def api_dcon_profile_save():
    import datetime
    body    = request.get_json() or {}
    today   = datetime.date.today().strftime('%Y%m%d')
    pid     = body.get('PROFILE_ID')
    nm      = (body.get('PROFILE_NM') or '').strip()
    obj     = (body.get('OBJECTIVE')  or 'MIN_VEHICLES').strip()
    act     = (body.get('ACTIVE_YN')  or 'Y').strip()
    note    = (body.get('NOTE')       or '').strip()
    if not nm:
        return jsonify({"error": "PROFILE_NM 필수"}), 400
    conn = get_conn()
    try:
        if pid:
            conn.execute(
                "UPDATE DS_DISPATCH_PROFILE SET PROFILE_NM=?,OBJECTIVE=?,ACTIVE_YN=?,NOTE=?,LMODAT=?"
                " WHERE PROFILE_ID=?",
                (nm, obj, act, note, today, pid)
            )
        else:
            conn.execute(
                "INSERT INTO DS_DISPATCH_PROFILE (PROFILE_NM,OBJECTIVE,ACTIVE_YN,NOTE,CREDAT,LMODAT)"
                " VALUES (?,?,?,?,?,?)",
                (nm, obj, act, note, today, today)
            )
            pid = conn.execute("SELECT last_insert_rowid()").fetchone()[0]
        conn.commit()
        return jsonify({"ok": True, "PROFILE_ID": pid})
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


# ── 프로파일 삭제 ───────────────────────────────────────────────────
@app.route('/api/dispatch-constraint/profiles/delete', methods=['POST'])
def api_dcon_profile_delete():
    body = request.get_json() or {}
    pid  = body.get('PROFILE_ID')
    if not pid:
        return jsonify({"error": "PROFILE_ID 필수"}), 400
    conn = get_conn()
    try:
        conn.execute("DELETE FROM DS_DISPATCH_CONST   WHERE PROFILE_ID=?", (pid,))
        conn.execute("DELETE FROM DS_DISPATCH_PROFILE WHERE PROFILE_ID=?", (pid,))
        conn.commit()
        return jsonify({"ok": True})
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


# ── 제약 조건 목록 조회 ─────────────────────────────────────────────
@app.route('/api/dispatch-constraint/list', methods=['GET'])
def api_dcon_list():
    pid = request.args.get('profile_id', '')
    conn = get_conn()
    try:
        if pid:
            rows = conn.execute(
                "SELECT * FROM DS_DISPATCH_CONST WHERE PROFILE_ID=? ORDER BY SORT_SEQ,CONST_ID",
                (pid,)
            ).fetchall()
        else:
            rows = conn.execute(
                "SELECT * FROM DS_DISPATCH_CONST ORDER BY PROFILE_ID,SORT_SEQ,CONST_ID"
            ).fetchall()
        return jsonify({"ok": True, "rows": [dict(r) for r in rows]})
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


# ── 제약 조건 저장 (INSERT / UPDATE) ────────────────────────────────
@app.route('/api/dispatch-constraint/save', methods=['POST'])
def api_dcon_save():
    import datetime
    body  = request.get_json() or {}
    today = datetime.date.today().strftime('%Y%m%d')

    # ── 배열(rows) 일괄 저장 모드 ─────────────────────────────────────
    rows = body.get('rows')
    if rows is not None:
        if not isinstance(rows, list):
            return jsonify({"ok": False, "error": "rows 는 배열이어야 합니다"}), 400
        conn = get_conn()
        try:
            saved_ids = []
            for row in rows:
                pid = row.get('PROFILE_ID')
                if not pid:
                    return jsonify({"ok": False, "error": "PROFILE_ID 필수"}), 400
                cid = row.get('CONST_ID')
                fields = {
                    'PROFILE_ID': pid,
                    'CONST_TYPE':  (row.get('CONST_TYPE')  or 'GLOBAL').strip(),
                    'CONST_KEY':   (row.get('CONST_KEY')   or '').strip(),
                    'CONST_VALUE': (row.get('CONST_VALUE') or '').strip(),
                    'CONST_OP':    (row.get('CONST_OP')    or '<=').strip(),
                    'TARGET_ID':   (row.get('TARGET_ID')   or '').strip(),
                    'TARGET_NM':   (row.get('TARGET_NM')   or '').strip(),
                    'ACTIVE_YN':   (row.get('ACTIVE_YN')   or 'Y').strip(),
                    'NOTE':        (row.get('NOTE')         or '').strip(),
                    'SORT_SEQ':    int(row.get('SORT_SEQ')  or 0),
                    'LMODAT':      today,
                }
                if not fields['CONST_KEY']:
                    continue  # CONST_KEY 없는 행은 건너뜀
                if cid:
                    sets = ', '.join(f"{k}=?" for k in fields if k != 'PROFILE_ID')
                    vals = [fields[k] for k in fields if k != 'PROFILE_ID'] + [cid]
                    conn.execute(f"UPDATE DS_DISPATCH_CONST SET {sets} WHERE CONST_ID=?", vals)
                    saved_ids.append(cid)
                else:
                    fields['CREDAT'] = today
                    cols = ', '.join(fields.keys())
                    phs  = ', '.join('?' * len(fields))
                    conn.execute(f"INSERT INTO DS_DISPATCH_CONST ({cols}) VALUES ({phs})",
                                 list(fields.values()))
                    new_id = conn.execute("SELECT last_insert_rowid()").fetchone()[0]
                    saved_ids.append(new_id)
            conn.commit()
            return jsonify({"ok": True, "saved": len(saved_ids), "ids": saved_ids})
        except Exception as e:
            return jsonify({"ok": False, "error": str(e)}), 500
        finally:
            conn.close()

    # ── 단일 행 저장 모드 (기존 방식 유지) ──────────────────────────────
    cid   = body.get('CONST_ID')
    pid   = body.get('PROFILE_ID')
    if not pid:
        return jsonify({"error": "PROFILE_ID 필수"}), 400
    fields = {
        'PROFILE_ID': pid,
        'CONST_TYPE':  (body.get('CONST_TYPE')  or 'GLOBAL').strip(),
        'CONST_KEY':   (body.get('CONST_KEY')   or '').strip(),
        'CONST_VALUE': (body.get('CONST_VALUE') or '').strip(),
        'CONST_OP':    (body.get('CONST_OP')    or '<=').strip(),
        'TARGET_ID':   (body.get('TARGET_ID')   or '').strip(),
        'TARGET_NM':   (body.get('TARGET_NM')   or '').strip(),
        'ACTIVE_YN':   (body.get('ACTIVE_YN')   or 'Y').strip(),
        'NOTE':        (body.get('NOTE')         or '').strip(),
        'SORT_SEQ':    int(body.get('SORT_SEQ')  or 0),
        'LMODAT':      today,
    }
    if not fields['CONST_KEY']:
        return jsonify({"error": "CONST_KEY 필수"}), 400
    conn = get_conn()
    try:
        if cid:
            sets = ', '.join(f"{k}=?" for k in fields if k != 'PROFILE_ID')
            vals = [fields[k] for k in fields if k != 'PROFILE_ID'] + [cid]
            conn.execute(f"UPDATE DS_DISPATCH_CONST SET {sets} WHERE CONST_ID=?", vals)
        else:
            fields['CREDAT'] = today
            cols = ', '.join(fields.keys())
            phs  = ', '.join('?' * len(fields))
            conn.execute(f"INSERT INTO DS_DISPATCH_CONST ({cols}) VALUES ({phs})",
                         list(fields.values()))
            cid = conn.execute("SELECT last_insert_rowid()").fetchone()[0]
        conn.commit()
        return jsonify({"ok": True, "CONST_ID": cid})
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


# ── 제약 조건 삭제 ───────────────────────────────────────────────────
@app.route('/api/dispatch-constraint/delete', methods=['POST'])
def api_dcon_delete():
    body = request.get_json() or {}
    ids  = body.get('ids', [])
    if not ids:
        return jsonify({"error": "ids 필수"}), 400
    conn = get_conn()
    try:
        ph = ','.join('?' * len(ids))
        conn.execute(f"DELETE FROM DS_DISPATCH_CONST WHERE CONST_ID IN ({ph})", ids)
        conn.commit()
        return jsonify({"ok": True})
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


# ── 제약 조건 일괄 복사 (프로파일 복사) ─────────────────────────────
@app.route('/api/dispatch-constraint/copy-profile', methods=['POST'])
def api_dcon_copy_profile():
    import datetime
    body      = request.get_json() or {}
    src_pid   = body.get('src_profile_id')
    new_nm    = (body.get('new_name') or '').strip()
    today     = datetime.date.today().strftime('%Y%m%d')
    if not src_pid or not new_nm:
        return jsonify({"error": "src_profile_id, new_name 필수"}), 400
    conn = get_conn()
    try:
        src = conn.execute(
            "SELECT * FROM DS_DISPATCH_PROFILE WHERE PROFILE_ID=?", (src_pid,)
        ).fetchone()
        if not src:
            return jsonify({"error": "원본 프로파일 없음"}), 404
        conn.execute(
            "INSERT INTO DS_DISPATCH_PROFILE (PROFILE_NM,OBJECTIVE,ACTIVE_YN,NOTE,CREDAT,LMODAT)"
            " VALUES (?,?,?,?,?,?)",
            (new_nm, src['OBJECTIVE'], 'N', f"복사본: {src['PROFILE_NM']}", today, today)
        )
        new_pid = conn.execute("SELECT last_insert_rowid()").fetchone()[0]
        src_rows = conn.execute(
            "SELECT * FROM DS_DISPATCH_CONST WHERE PROFILE_ID=?", (src_pid,)
        ).fetchall()
        for r in src_rows:
            d = dict(r)
            conn.execute(
                "INSERT INTO DS_DISPATCH_CONST"
                " (PROFILE_ID,CONST_TYPE,CONST_KEY,CONST_VALUE,CONST_OP,"
                "  TARGET_ID,TARGET_NM,ACTIVE_YN,NOTE,SORT_SEQ,CREDAT,LMODAT)"
                " VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                (new_pid, d['CONST_TYPE'], d['CONST_KEY'], d['CONST_VALUE'],
                 d['CONST_OP'], d['TARGET_ID'], d['TARGET_NM'],
                 d['ACTIVE_YN'], d['NOTE'], d['SORT_SEQ'], today, today)
            )
        conn.commit()
        return jsonify({"ok": True, "new_profile_id": new_pid})
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


# ── 제약 조건 참조 데이터 (차량목록·납품처·CONST_KEY 정의) ──────────
@app.route('/api/dispatch-constraint/meta', methods=['GET'])
def api_dcon_meta():
    """제약 조건 편집에 필요한 참조 데이터 일괄 반환"""
    conn = get_conn()
    try:
        vehicles = conn.execute(
            "SELECT CARCLASS_CD, CARTYPE, LOAD_TON, LENGTH_M, WIDTH_M, HEIGHT_M,"
            "       PALLET_HEIGHT_M, SORT_SEQ,"
            "       INCH12_LT300, INCH12_GE300, INCH3_LT300, INCH3_GE300,"
            "       ROUND(CAST(LENGTH_M AS REAL)*CAST(WIDTH_M AS REAL)*CAST(HEIGHT_M AS REAL),2) AS LOAD_CBM"
            " FROM DS_VEHICLE ORDER BY SORT_SEQ"
        ).fetchall()
        carclasses = conn.execute(
            "SELECT CMCDVL, CDESC1 FROM CMCDV WHERE CMCDKY='TMS_CARCLASS10' ORDER BY CMCDVL"
        ).fetchall()
        # 파트너(납품처) 샘플 — 너무 많으니 ROUTE_COST에 등록된 것만
        partners = conn.execute(
            """SELECT DISTINCT rc.PTNRKY,
                      COALESCE(b.NAME01, rc.PTNRKY) AS PTNRNM,
                      bd.ROUTE_CD, bd.MAX_TON, bd.DEADLINE_TIME, bd.FORKLIFT_YN
               FROM ROUTE_COST rc
               LEFT JOIN BZPTN         b  ON b.PTNRKY  = rc.PTNRKY
               LEFT JOIN BZPTN_DETAIL  bd ON bd.PTNRKY = rc.PTNRKY AND bd.PTNRTY='CT'
               ORDER BY rc.PTNRKY
               LIMIT 300"""
        ).fetchall()
        # CONST_KEY 정의 (코드 → 설명)
        const_key_defs = [
            # GLOBAL
            {"type":"GLOBAL","key":"MAX_VEHICLES_PER_GROUP","label":"그룹당 최대 차량 수",       "op_default":"<=","value_type":"int",    "example":"5"},
            {"type":"GLOBAL","key":"ALLOW_SPLIT_ITEM",       "label":"납품분할 허용",              "op_default":"=", "value_type":"yn",    "example":"Y"},
            {"type":"GLOBAL","key":"ALLOW_MIXED_LOAD",       "label":"혼적 허용 (우편번호 앞 3자리 동일)", "op_default":"=", "value_type":"yn",    "example":"N"},
            {"type":"GLOBAL","key":"MIN_FILL_RATIO",         "label":"최소 적재율 (%)",            "op_default":">=","value_type":"float", "example":"50"},
            {"type":"GLOBAL","key":"MAX_FILL_RATIO",         "label":"최대 적재율 (%)",            "op_default":"<=","value_type":"float", "example":"100"},
            {"type":"GLOBAL","key":"FORCE_SAME_CARTYPE",     "label":"동일 차종 강제",             "op_default":"=", "value_type":"yn",    "example":"N"},
            # VEHICLE
            {"type":"VEHICLE","key":"ALLOW_CARTYPE",         "label":"차종 허용 여부",             "op_default":"=", "value_type":"yn",    "example":"Y"},
            {"type":"VEHICLE","key":"MAX_LOAD_RATIO",        "label":"차종별 최대 적재율 (%)",     "op_default":"<=","value_type":"float", "example":"95"},
            {"type":"VEHICLE","key":"MIN_LOAD_RATIO",        "label":"차종별 최소 적재율 (%)",     "op_default":">=","value_type":"float", "example":"30"},
            {"type":"VEHICLE","key":"PRIORITY_CARTYPE",      "label":"우선 사용 차종 순위",        "op_default":"=", "value_type":"int",   "example":"1"},
            # PARTNER
            {"type":"PARTNER","key":"MAX_TON_OVERRIDE",      "label":"납품처 최대 톤수 재정의",    "op_default":"=", "value_type":"text",  "example":"Z035"},
            {"type":"PARTNER","key":"DEADLINE_OVERRIDE",     "label":"납기시간 재정의 (HH:MM)",    "op_default":"=", "value_type":"text",  "example":"14:00"},
            {"type":"PARTNER","key":"FORKLIFT_REQUIRED",     "label":"지게차 필수 여부",           "op_default":"=", "value_type":"yn",    "example":"Y"},
            {"type":"PARTNER","key":"FORCE_CARTYPE",         "label":"특정 납품처 강제 차종",      "op_default":"=", "value_type":"text",  "example":"5톤"},
            {"type":"PARTNER","key":"EXCLUDE_PARTNER",       "label":"납품처 배차 제외",           "op_default":"=", "value_type":"yn",    "example":"Y"},
            # CARGO
            {"type":"CARGO","key":"MAX_ROLL_STACK_TIER",     "label":"롤 최대 적재 단수",          "op_default":"<=","value_type":"int",   "example":"2"},
            {"type":"CARGO","key":"MAX_BOARD_HEIGHT_M",      "label":"판지 최대 적재 높이 (m)",    "op_default":"<=","value_type":"float", "example":"2.4"},
            {"type":"CARGO","key":"ROLL_SINGLE_KG_FALLBACK", "label":"롤 단중 fallback (kg)",     "op_default":"=", "value_type":"float", "example":"600"},
            {"type":"CARGO","key":"FORCE_INCH_SEPARATION",   "label":"인치 혼재 금지",             "op_default":"=", "value_type":"yn",    "example":"N"},
            {"type":"CARGO","key":"MAX_CBM_RATIO",           "label":"최대 CBM 적재율 (%)",        "op_default":"<=","value_type":"float", "example":"100"},
            # COST
            {"type":"COST","key":"COST_REF_DATE",            "label":"운송비 기준일",              "op_default":"=", "value_type":"text",  "example":"TODAY"},
            {"type":"COST","key":"COST_PENALTY_OVER",        "label":"초과 적재 패널티 배수",      "op_default":"=", "value_type":"float", "example":"1.5"},
            {"type":"COST","key":"COST_WEIGHT_VEHICLE_CNT",  "label":"목적식 차량수 가중치",       "op_default":"=", "value_type":"float", "example":"1.0"},
            {"type":"COST","key":"COST_WEIGHT_FILL",         "label":"목적식 적재율 가중치",       "op_default":"=", "value_type":"float", "example":"1.0"},
        ]
        return jsonify({
            "ok": True,
            "vehicles":      [dict(r) for r in vehicles],
            "carclasses":    [dict(r) for r in carclasses],
            "partners":      [dict(r) for r in partners],
            "const_key_defs": const_key_defs,
        })
    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        conn.close()


# ══════════════════════════════════════════════════════════════════
#  목적식 기반 자동배차  /api/dispatch-constraint/auto
# ══════════════════════════════════════════════════════════════════
@app.route('/api/dispatch-constraint/auto', methods=['POST'])
def api_dcon_auto():
    """
    제약 조건 프로파일 기반 자동배차 (목적식 선택)
    body: { profile_id, items: [...] }

    목적식(OBJECTIVE):
      MIN_VEHICLES : 차량 수 최소화 — FFD BinPacking (기존 동일)
      MAX_FILL     : 적재율 최대화 — Best-Fit Decreasing by fill ratio
      MIN_COST     : 운송비 최소화 — ROUTE_COST 기반 최저비용 차종 선택
    """
    import datetime
    from collections import defaultdict

    body       = request.get_json() or {}
    profile_id = body.get('profile_id')
    items      = body.get('items', [])

    # ── 날짜/납품처 기반 자동 아이템 조회 ──────────────────────────
    date_str = body.get('date', '')           # 'YYYY-MM-DD' or 'YYYYMMDD'
    ptnrky_filter = body.get('ptnrky', '')
    if not items and date_str:
        date_yyyymmdd = date_str.replace('-', '')
        _conn_pre = get_conn()
        _sql = """
            SELECT h.SHPOKY, h.DPTNKY, b.NAME01 AS DPTNM,
                   h.RQSHPD,
                   i.SHPOIT, i.SKUKEY, i.QTSHPO,
                   i.DESC01 AS SKUNM,
                   i.GRSWGT, i.WGTUNT, i.UOMKEY,
                   i.LENGTH, i.WIDTHW AS WIDTH_MM, i.HEIGHT
            FROM SHPDH h
            JOIN SHPDI i ON h.SHPOKY = i.SHPOKY
            LEFT JOIN BZPTN b ON h.DPTNKY = b.PTNRKY
            WHERE h.RQSHPD = ?
        """
        _params = [date_yyyymmdd]
        if ptnrky_filter:
            _sql += " AND h.DPTNKY = ?"
            _params.append(ptnrky_filter)
        _rows = _conn_pre.execute(_sql, _params).fetchall()
        _conn_pre.close()
        items = [dict(r) for r in _rows]

    if not items:
        return jsonify({"error": "items 없음 — 해당 날짜의 출고 데이터가 없습니다"}), 400

    conn = get_conn()
    try:
        # ── 프로파일 로드 ────────────────────────────────────────────
        if profile_id:
            prof = conn.execute(
                "SELECT * FROM DS_DISPATCH_PROFILE WHERE PROFILE_ID=?", (profile_id,)
            ).fetchone()
        else:
            prof = conn.execute(
                "SELECT * FROM DS_DISPATCH_PROFILE WHERE ACTIVE_YN='Y' ORDER BY PROFILE_ID LIMIT 1"
            ).fetchone()

        if not prof:
            return jsonify({"error": "활성 프로파일 없음"}), 404

        objective  = prof['OBJECTIVE']   # MIN_VEHICLES / MAX_FILL / MIN_COST
        pid        = prof['PROFILE_ID']

        # ── 제약 조건 로드 → 딕셔너리로 변환 ───────────────────────
        const_rows = conn.execute(
            "SELECT * FROM DS_DISPATCH_CONST WHERE PROFILE_ID=? AND ACTIVE_YN='Y' ORDER BY SORT_SEQ",
            (pid,)
        ).fetchall()
        # 편의 접근: {CONST_KEY: {row dict}}  (마지막 값 우선, TARGET_ID 없는 것)
        C = {}   # 전체 딕셔너리
        C_by_target = defaultdict(dict)   # TARGET_ID별 제약
        for r in const_rows:
            d = dict(r)
            key = d['CONST_KEY']
            tid = d['TARGET_ID'] or ''
            if tid:
                C_by_target[tid][key] = d
            else:
                C[key] = d

        def _cval(key, default=None):
            """전역 제약 값 반환 (CONST_VALUE 문자열)"""
            r = C.get(key)
            return r['CONST_VALUE'] if r else default

        def _cfloat(key, default=0.0):
            try: return float(_cval(key, default))
            except: return default

        def _cbool(key, default='Y'):
            return (_cval(key, default) or 'Y').upper() == 'Y'

        # ── 허용 차종 필터 ───────────────────────────────────────────
        allowed_cartypes = set()
        for r in const_rows:
            if r['CONST_TYPE']=='VEHICLE' and r['CONST_KEY']=='ALLOW_CARTYPE':
                if (r['CONST_VALUE'] or 'Y').upper() == 'Y' and r['TARGET_ID']:
                    allowed_cartypes.add(r['TARGET_ID'])

        # ── 차량 마스터 로드 ─────────────────────────────────────────
        car_order  = _ps_car_order(conn)
        veh_info   = _ps_get_vehicle_info(conn)
        inch12_map, inch3_map = _ps_load_strategy(conn)
        skuma_rows = conn.execute(
            "SELECT SKUKEY, GRSWGT, ASKL04, ASKL05, CUBICM FROM SKUMA WHERE MTYPE='P'"
        ).fetchall()
        skuma_map = {}
        for sr in skuma_rows:
            try:
                w = int(sr['ASKL04']) if sr['ASKL04'] and str(sr['ASKL04']).strip().isdigit() else 0
                l = int(sr['ASKL05']) if sr['ASKL05'] and str(sr['ASKL05']).strip().isdigit() else 0
            except: w, l = 0, 0
            skuma_map[sr['SKUKEY']] = {
                'grswgt': float(sr['GRSWGT'] or 0),
                'w_mm': w, 'l_mm': l,
                'cubicm': float(sr['CUBICM'] or 0)
            }

        # ROUTE_COST 로드 (MIN_COST 목적식)
        today_str = datetime.date.today().strftime('%Y%m%d')
        cost_ref  = _cval('COST_REF_DATE', 'TODAY')
        cost_date = today_str if cost_ref == 'TODAY' else cost_ref
        route_cost_rows = conn.execute(
            """SELECT rc.PTNRKY, cc.CDESC1 AS CARTYPE, rc.COST, rc.CARCLASS
               FROM ROUTE_COST rc
               LEFT JOIN CMCDV cc ON cc.CMCDKY='TMS_CARCLASS10' AND cc.CMCDVL=rc.CARCLASS
               WHERE rc.DATE_START<=? AND rc.DATE_END>=?""",
            (cost_date, cost_date)
        ).fetchall()
        # {ptnrky: {cartype: cost}}
        route_cost_map = defaultdict(dict)
        for r in route_cost_rows:
            if r['CARTYPE']:
                route_cost_map[r['PTNRKY']][r['CARTYPE']] = float(r['COST'] or 0)

        # ── 헬퍼: 제약 적용 유효 차량 목록 ──────────────────────────
        def _get_valid_cars(dptnky=''):
            """허용 차종 + 납품처 MAX_TON + 차종별 제약 적용"""
            cars = [c for c in car_order
                    if veh_info.get(c['CARTYPE'], {}).get('load_kg', 0) > 0]
            # 허용 차종 필터 (ALLOW_CARTYPE 제약)
            if allowed_cartypes:
                cars = [c for c in cars if c['CARTYPE'] in allowed_cartypes]
            # 납품처 MAX_TON (BZPTN_DETAIL 기반)
            if dptnky:
                ptnr = conn.execute(
                    "SELECT MAX_TON FROM BZPTN_DETAIL WHERE PTNRKY=? AND PTNRTY='CT'",
                    (dptnky,)
                ).fetchone()
                if ptnr:
                    mt = (ptnr['MAX_TON'] or '').strip()
                    if mt:
                        cc = conn.execute(
                            "SELECT CDESC1 FROM CMCDV WHERE CMCDKY='TMS_CARCLASS10' AND CMCDVL=?", (mt,)
                        ).fetchone()
                        if cc:
                            mt_label = cc['CDESC1']
                            mt_kg = veh_info.get(mt_label, {}).get('load_kg', 0)
                            if mt_kg > 0:
                                filtered = [c for c in cars
                                            if veh_info.get(c['CARTYPE'],{}).get('load_kg',0) <= mt_kg]
                                if filtered:
                                    cars = filtered
                # 납품처 FORCE_CARTYPE 제약
                force_ct = (C_by_target.get(dptnky, {}).get('FORCE_CARTYPE') or {}).get('CONST_VALUE','')
                if force_ct and any(c['CARTYPE'] == force_ct for c in cars):
                    cars = [c for c in cars if c['CARTYPE'] == force_ct]
            return cars

        def _sort_key(ct):
            for i, c in enumerate(car_order):
                if c['CARTYPE'] == ct: return i
            return 999

        # ── 목적식별 차량 선정 함수 ──────────────────────────────────
        ROLL_SINGLE_KG  = _cfloat('ROLL_SINGLE_KG_FALLBACK', 600.0)
        MIN_FILL        = _cfloat('MIN_FILL_RATIO',  0.0) / 100.0
        MAX_FILL        = _cfloat('MAX_FILL_RATIO', 100.0) / 100.0
        penalty         = _cfloat('COST_PENALTY_OVER', 1.5)
        # 판지 전용 최소 적재율 (BOARD_MIN_FILL_RATIO): 설정 없으면 MIN_FILL 상속
        _board_min_raw  = _cfloat('BOARD_MIN_FILL_RATIO', -1.0)
        BOARD_MIN_FILL  = (_board_min_raw / 100.0) if _board_min_raw >= 0 else MIN_FILL

        def _select_car_min_vehicles(need_kg, valid_cars, material_type=''):
            """MIN_VEHICLES: 첨부율 최대 (best_fit_car).
            material_type='BOARD'이면 BOARD_MIN_FILL_RATIO 하한을 준수합니다.
            MIN_FILL(또는 BOARD_MIN_FILL) 조건을 만족하는 차량 중 적재율 최대 차량 선정.
            조건 만족 차량 없으면 need_kg를 수용하는 가장 작은 차량으로 폴백합니다.
            """
            min_f = BOARD_MIN_FILL if material_type == 'BOARD' else MIN_FILL
            best, best_ratio = None, -1.0
            fallback, fallback_ratio = None, -1.0   # min_fill 미달이지만 적재 가능한 후보
            for c in valid_cars:
                cap = veh_info.get(c['CARTYPE'],{}).get('load_kg',0)
                if cap <= 0: continue
                if cap >= need_kg:
                    r = need_kg / cap
                    # 하한 체크: min_fill > 0이면 적용
                    if min_f > 0 and r < min_f:
                        # 폴백 후보 (하한 미달, 하한 조건 없을 때 최선)
                        if r > fallback_ratio:
                            fallback_ratio, fallback = r, c['CARTYPE']
                        continue
                    if r > best_ratio:
                        best_ratio, best = r, c['CARTYPE']
            # min_fill 조건 충족 차량 없으면: 폴백 후보 → 가장 큰 차량 순으로 폴백
            if best is None:
                best = fallback if fallback else (valid_cars[0]['CARTYPE'] if valid_cars else '판별불가')
            return best

        def _select_car_max_fill(need_kg, valid_cars):
            """MAX_FILL: 적재율 최대화 — MIN_FILL 하한 준수하면서 가장 꽉 채우는 차량"""
            best, best_ratio = None, -1.0
            for c in valid_cars:
                cap = veh_info.get(c['CARTYPE'],{}).get('load_kg',0)
                if cap <= 0: continue
                ratio = need_kg / cap
                if ratio > MAX_FILL: continue   # 초과 금지
                if ratio < MIN_FILL: continue   # 하한 미달 금지
                if cap >= need_kg and ratio > best_ratio:
                    best_ratio, best = ratio, c['CARTYPE']
            # MIN_FILL 조건 충족하는 차량 없으면 일반 best_fit으로 폴백
            if best is None:
                best = _select_car_min_vehicles(need_kg, valid_cars)
            return best

        def _select_car_min_cost(need_kg, valid_cars, dptnky, material_type=''):
            """MIN_COST: ROUTE_COST 기준 최저비용 + 패널티 보정"""
            costs = route_cost_map.get(dptnky, {})
            best_car, best_cost = None, float('inf')
            for c in valid_cars:
                ct  = c['CARTYPE']
                cap = veh_info.get(ct,{}).get('load_kg',0)
                if cap <= 0: continue
                base_cost = costs.get(ct, 0)
                if base_cost <= 0:
                    # ROUTE_COST 미등록 → LOAD_TON 비례 추정 비용 (100,000원/톤 기준)
                    base_cost = float(cap) / 1000.0 * 100_000.0
                # 초과 적재 패널티
                actual_cost = base_cost
                if cap < need_kg:
                    actual_cost = base_cost * penalty   # 초과 시 패널티 배수
                elif cap >= need_kg:
                    # 여유가 너무 크면(낭비) 미세 패널티
                    waste_ratio = (cap - need_kg) / cap
                    actual_cost = base_cost * (1.0 + waste_ratio * 0.1)
                if actual_cost < best_cost:
                    best_cost, best_car = actual_cost, ct
            if best_car is None:
                best_car = _select_car_min_vehicles(need_kg, valid_cars, material_type)
            return best_car

        def _select_car(need_kg, valid_cars, dptnky='', material_type=''):
            """목적식 + 재질 유형에 따른 차량 선정.
            material_type='BOARD'이면 BOARD_MIN_FILL_RATIO 하한이 적용됩니다.
            """
            if objective == 'MAX_FILL':
                return _select_car_max_fill(need_kg, valid_cars)
            elif objective == 'MIN_COST':
                return _select_car_min_cost(need_kg, valid_cars, dptnky, material_type)
            else:  # MIN_VEHICLES (default)
                return _select_car_min_vehicles(need_kg, valid_cars, material_type)

        # ── 제약 파라미터 ────────────────────────────────────────────
        ALLOW_SPLIT        = _cbool('ALLOW_SPLIT_ITEM', 'Y')
        ALLOW_MIXED_LOAD   = _cbool('ALLOW_MIXED_LOAD', 'N')
        MAX_STACK          = int(_cfloat('MAX_ROLL_STACK_TIER', 2))
        MAX_B_HGT          = _cfloat('MAX_BOARD_HEIGHT_M', 2.4)
        # 판지 벌크 정수 강제 여부: Y=정수 단위만 허용, N=소수점 허용
        BOARD_BULK_INT_ONLY = _cbool('BOARD_BULK_INTEGER_ONLY', 'Y')
        # 속포장 내부 분할 허용 여부: Y=허용, N=불가
        BOARD_INNER_SPLIT   = _cbool('BOARD_INNER_SPLIT_ALLOW', 'Y')

        # ── 그룹핑 + 배차 ────────────────────────────────────────────
        # ALLOW_MIXED_LOAD='Y': 우편번호 앞 3자리가 같은 납품처끼리 혼적 그룹 구성
        if ALLOW_MIXED_LOAD:
            # BZPTN에서 DPTNKY별 우편번호 앞 3자리 조회
            all_dptnky_items = list({it.get('DPTNKY','') for it in items if it.get('DPTNKY','')})
            postcd_map = {}  # {DPTNKY: zip3}
            if all_dptnky_items:
                ph_m = ','.join('?'*len(all_dptnky_items))
                postcd_rows = conn.execute(
                    f"SELECT PTNRKY, POSTCD FROM BZPTN WHERE PTNRKY IN ({ph_m}) AND PTNRTY='CT'",
                    all_dptnky_items
                ).fetchall()
                postcd_map = {r['PTNRKY']: (r['POSTCD'] or '').strip()[:3] for r in postcd_rows}

            groups = defaultdict(list)
            for it in items:
                dptnky = it.get('DPTNKY','')
                rqshpd = it.get('RQSHPD','')
                zip3   = postcd_map.get(dptnky, '')
                # zip3 있으면 혼적 그룹 키, 없으면 납품처 단독 키
                key = ('_ZIP_'+zip3, zip3, rqshpd) if zip3 else (dptnky, it.get('DPTNM',''), rqshpd)
                groups[key].append(it)
        else:
            groups = defaultdict(list)
            for it in items:
                key = (it.get('DPTNKY',''), it.get('DPTNM',''), it.get('RQSHPD',''))
                groups[key].append(it)

        # 납품처 TMS 정보 캐싱
        all_dptnky = list({k[0] for k in groups if k[0]})
        ptnr_info  = {}
        if all_dptnky:
            ph2 = ','.join('?'*len(all_dptnky))
            pr2 = conn.execute(
                f"SELECT PTNRKY,DEADLINE_TIME,FORKLIFT_YN,MAX_TON,DYNAMIC_YN FROM BZPTN_DETAIL"
                f" WHERE PTNRKY IN ({ph2}) AND PTNRTY='CT'",
                all_dptnky
            ).fetchall()
            cc_map = {r['CMCDVL']:r['CDESC1'] for r in conn.execute(
                "SELECT CMCDVL,CDESC1 FROM CMCDV WHERE CMCDKY='TMS_CARCLASS10'"
            ).fetchall()}
            for p in pr2:
                dk = p['PTNRKY']
                mt = (p['MAX_TON'] or '').strip()
                mt_label = cc_map.get(mt, mt) if mt else ''
                mt_kg    = veh_info.get(mt_label, {}).get('load_kg', 0) if mt_label else 0
                ptnr_info[dk] = {
                    'deadline_time': (p['DEADLINE_TIME'] or '').strip(),
                    'forklift_yn':   (p['FORKLIFT_YN']   or '').strip(),
                    'max_ton_label': mt_label,
                    'max_load_kg':   mt_kg,
                    # 동적 제약: DYNAMIC_YN='N' → 동적배차 불가 / Y 또는 미설정 → 가능(기본)
                    'dynamic_yn':    (p['DYNAMIC_YN']     or '').strip().upper(),
                }

        now_dt   = datetime.datetime.now()
        now_hhmm = now_dt.strftime('%H:%M')

        # ── 공유 헬퍼 (기존 api_ps_dispatch_auto와 동일 공식 재사용) ──
        def _item_roll_kg_c(it):
            """롤 아이템 실제 KG 계산
            우선순위: KG_WEIGHT 필드 → SKUMA.GRSWGT×QTSHPO → SHPDI.GRSWGT×QTSHPO → QTSHPO×ROLL_SINGLE_KG
            SKUMA 미등록 SKU는 SHPDI.GRSWGT(=i.GRSWGT) 컬럼을 fallback으로 사용
            """
            if it.get('KG_WEIGHT') is not None and float(it.get('KG_WEIGHT') or 0) > 0:
                return float(it['KG_WEIGHT'])
            sk  = (it.get('SKUKEY') or '').strip()
            uom = (it.get('UOMKEY') or '').strip()
            qty = float(it.get('QTSHPO') or 0)
            if uom == 'R' and _ps_is_roll(sk):
                # 1순위: SKUMA.GRSWGT
                gw = skuma_map.get(sk, {}).get('grswgt', 0)
                if gw > 0:
                    return qty * gw
                # 2순위: SHPDI.GRSWGT (아이템 자체 중량)
                item_gw = float(it.get('GRSWGT') or 0)
                if item_gw > 0:
                    return qty * item_gw
                # 3순위: fallback (단일 롤 기본 중량)
                return qty * ROLL_SINGLE_KG
            return qty

        def _item_roll_count_c(it):
            sk  = (it.get('SKUKEY') or '').strip()
            uom = (it.get('UOMKEY') or '').strip()
            if uom == 'R' and _ps_is_roll(sk):
                return int(it.get('QTSHPO') or 0)
            unit_w = float(it.get('UNIT_WEIGHT') or 0)
            single = unit_w if unit_w > 0 else ROLL_SINGLE_KG
            kg = _item_roll_kg_c(it)
            return math.ceil(kg / single) if kg > 0 else 0

        def _roll_diam_c(sk):
            gsm, wmm = _ps_parse_skukey_dims(sk)
            if not gsm or not wmm: return None
            return _ps_calc_roll_diameter(ROLL_SINGLE_KG, gsm, wmm)

        def _board_kg_c(it):
            """판지 아이템 총 중량(kg) 계산.
            우선순위: KG_WEIGHT 필드 → SKUMA.GRSWGT×QTSHPO → SHPDI.GRSWGT×QTSHPO → QTSHPO 그대로
            UOMKEY='R': Ream(속) 단위 수량 × 속당 중량
            """
            if it.get('KG_WEIGHT') is not None and float(it.get('KG_WEIGHT') or 0) > 0:
                return float(it['KG_WEIGHT'])
            uom = (it.get('UOMKEY') or '').strip()
            qty = float(it.get('QTSHPO') or 0)
            if uom == 'R':
                # 1순위: SKUMA.GRSWGT
                gw = skuma_map.get(it.get('SKUKEY',''), {}).get('grswgt', 0)
                if gw > 0:
                    return qty * gw
                # 2순위: SHPDI.GRSWGT (아이템 컬럼)
                item_gw = float(it.get('GRSWGT') or 0)
                if item_gw > 0:
                    return qty * item_gw
                return qty
            return qty

        def _board_qty_warn(it):
            """BOARD_BULK_INTEGER_ONLY=Y 일 때 비정수 Ream 수량 경고 반환 (없으면 None)
            판지 Ream(R) 수량이 정수가 아닌 경우 경고 문자열 반환.
            BOARD_INNER_SPLIT_ALLOW=Y 이면 속 단위 분할이 허용되어 실질 위반 아님 — 정보 노트로만 출력.
            """
            if not BOARD_BULK_INT_ONLY:
                return None
            uom = (it.get('UOMKEY') or '').strip()
            qty = float(it.get('QTSHPO') or 0)
            if uom == 'R' and qty != int(qty):
                action = '속단위분할허용(BOARD_INNER_SPLIT_ALLOW=Y)' if BOARD_INNER_SPLIT else '분할불가'
                return (f"[BOARD_BULK_INTEGER_ONLY] {it.get('SKUKEY','')} "
                        f"수량={qty}R (비정수) → {action}")

        def _board_h_c(it):
            return _calc_board_stack_height_m(it, skuma_map)

        def _can_fit_roll_bin(b, item_kg, inch, grm, rc, big_cap, b_i12, b_i3):
            if b['total_kg'] + item_kg > big_cap: return False
            if inch == '12인치':
                mc = b_i12.get(grm, 0)
                if mc > 0 and b['v12'][grm] + rc > mc: return False
            elif inch == '3인치':
                mc = b_i3.get(grm, 0)
                if mc > 0 and b['v3'][grm] + rc > mc: return False
            return True

        all_vehicles = []

        for (dptnky, dptnm, rqshpd), grp_items in sorted(groups.items()):
            # ── 혼적 그룹 대표값 처리 ─────────────────────────────────
            is_mixed = ALLOW_MIXED_LOAD and dptnky.startswith('_ZIP_')
            if is_mixed:
                # 혼적 그룹: grp_items에서 실제 납품처 목록 추출
                mixed_dptnky_set = []
                mixed_dptnm_set  = []
                seen_dk = set()
                for _it in grp_items:
                    _dk = _it.get('DPTNKY','')
                    if _dk and _dk not in seen_dk:
                        seen_dk.add(_dk)
                        mixed_dptnky_set.append(_dk)
                        mixed_dptnm_set.append(_it.get('DPTNM',''))
                # 대표 납품처 = 첫 번째 납품처 (유효차 조회 기준)
                rep_dptnky = mixed_dptnky_set[0] if mixed_dptnky_set else ''
                rep_dptnm  = '혼적(' + '/'.join(mixed_dptnm_set) + ')'
                # 혼적 그룹 valid_cars: 모든 납품처 제약 교집합 (가장 작은 차량 집합)
                valid_cars_sets = [_get_valid_cars(dk) for dk in mixed_dptnky_set]
                valid_cars_sets = [s for s in valid_cars_sets if s]
                if valid_cars_sets:
                    # 공통 차종만 허용: 모든 납품처가 허용하는 차종 교집합
                    common_types = set(c['CARTYPE'] for c in valid_cars_sets[0])
                    for vcs in valid_cars_sets[1:]:
                        common_types &= set(c['CARTYPE'] for c in vcs)
                    valid_cars = [c for c in valid_cars_sets[0] if c['CARTYPE'] in common_types]
                else:
                    valid_cars = []
                # 실제 dptnky/dptnm 을 대표값으로 교체
                dptnky = rep_dptnky
                dptnm  = rep_dptnm
            else:
                valid_cars = _get_valid_cars(dptnky)
                rep_dptnm  = dptnm  # 비혼적 시 대표명 = 납품처명

            if not valid_cars:
                valid_cars = [c for c in car_order if veh_info.get(c['CARTYPE'],{}).get('load_kg',0)>0]

            big_car = valid_cars[0]['CARTYPE'] if valid_cars else '판별불가'
            big_cap = veh_info.get(big_car, {}).get('load_kg', 0) or 99_999_999.0
            b_i12   = inch12_map.get(big_car, {})
            b_i3    = inch3_map.get(big_car, {})

            # ── 4) 동적 가능 여부 조건 (엑셀 1. 개요 §4) ──────────────
            # DYNAMIC_YN='N' → 동적배차 불가 (고정노선 전용)
            # DYNAMIC_YN='Y' 또는 미설정 → 동적배차 가능 (기본값)
            pi_base = ptnr_info.get(dptnky, {})
            dyn_yn  = pi_base.get('dynamic_yn', '')   # 'Y' / 'N' / ''
            is_dynamic_blocked = (dyn_yn == 'N')       # True → 고정노선 전용 오더

            def _is_roll_item(it):
                """롤지(원지) 여부: SKUKEY 기반 판별 우선, UOMKEY='R'은 SKUKEY[0]='H'와 함께 판단.
                [FIX] UOMKEY='R' 단독 판별 제거 — 판지 Ream(속) 포장도 UOMKEY='R'을 사용하므로
                SKUKEY[0]=='H'(원지 접두어) 또는 SKUKEY[13:17]=='0000'(롤 길이) 조건 필수."""
                sk  = (it.get('SKUKEY') or '').strip()
                uom = (it.get('UOMKEY') or '').strip()
                # SKUKEY 기반 원지 판별 최우선
                if _ps_is_roll(sk): return True
                # UOMKEY='R' 이면서 SKUKEY 접두어가 'H'(원지)인 경우만 롤로 인정
                if uom == 'R' and len(sk) > 0 and sk[0] == 'H': return True
                return False

            def _is_board_item(it):
                """판지(평판) 여부: _is_roll_item=False 이고 SKUKEY 기준 판지"""
                if _is_roll_item(it): return False
                return _ps_is_board(it.get('SKUKEY', ''))

            roll_items  = [it for it in grp_items if _is_roll_item(it)]
            board_items = [it for it in grp_items if _is_board_item(it)]
            other_items = [it for it in grp_items
                           if not _is_roll_item(it) and not _is_board_item(it)]

            # ── 혼적 여부 감지 (엑셀 4. 혼합적재 §1) ─────────────────
            # 원지 + 판지 동시 존재 = 혼적
            # 혼적 시 Z축(상하) 강제: 원지 하단(바닥), 판지 상단 (파손 방지 핵심)
            # 혼적 시 Y축(전후) 분할: 배송처 다중 시 나중 하차→안쪽, 먼저 하차→문 쪽 (LIFO)
            is_mixed_load = bool(roll_items and board_items)  # 원지+판지 동시 존재

            # ── 원지 배차 ─────────────────────────────────────────────
            if roll_items:
                # 납품분할 전처리 (ALLOW_SPLIT 제약 적용)
                split_items = []
                split_notes_pre = []
                for it in roll_items:
                    if ALLOW_SPLIT:
                        # 기존 _split_roll_item 재활용 (inline 구현)
                        sk  = (it.get('SKUKEY') or '').strip()
                        uom = (it.get('UOMKEY') or '').strip()
                        inch_t = _ps_get_inch(sk)
                        grm_t  = _ps_get_grm(sk)
                        max_rc = (b_i12 if inch_t=='12인치' else b_i3).get(grm_t, 0)

                        if uom == 'R' and _ps_is_roll(sk):
                            total_rolls = int(it.get('QTSHPO') or 0)
                            # per_roll_kg: SKUMA.GRSWGT → SHPDI.GRSWGT → ROLL_SINGLE_KG 순으로 fallback
                            total_kg_it = _item_roll_kg_c(it)
                            per_roll_kg = total_kg_it / max(total_rolls, 1) if total_kg_it > 0 else ROLL_SINGLE_KG
                            rolls_by_kg = max(1, int(big_cap / per_roll_kg)) if per_roll_kg > 0 else total_rolls
                            chunk_rolls = min(max_rc or total_rolls, rolls_by_kg, total_rolls)
                            if chunk_rolls > 0 and chunk_rolls < total_rolls:
                                remain, idx = total_rolls, 1
                                while remain > 0:
                                    c_r = min(chunk_rolls, remain)
                                    c_kg = round(c_r * per_roll_kg, 4)
                                    chunk = dict(it)
                                    chunk.update({'QTSHPO':c_r,'KG_WEIGHT':c_kg,
                                                  '_SPLIT_FROM':it.get('SHPOIT',''),'_SPLIT_IDX':idx})
                                    split_items.append(chunk)
                                    remain -= c_r; idx += 1
                                split_notes_pre.append(
                                    f"[납품분할] {it.get('SHPOKY','')}#{it.get('SHPOIT','')} → {idx-1}개"
                                )
                                continue
                        # KG_WEIGHT가 없으면 실제 KG 계산해서 미리 설정
                        if not it.get('KG_WEIGHT') or float(it.get('KG_WEIGHT') or 0) <= 0:
                            it = dict(it)
                            it['KG_WEIGHT'] = _item_roll_kg_c(it)
                        split_items.append(it)
                    else:
                        if not it.get('KG_WEIGHT') or float(it.get('KG_WEIGHT') or 0) <= 0:
                            it = dict(it)
                            it['KG_WEIGHT'] = _item_roll_kg_c(it)
                        split_items.append(it)

                # 목적식별 정렬 — KG 기준 (롤 수가 아닌 실제 중량으로 정렬해야 FFD 효율 최대화)
                if objective == 'MAX_FILL':
                    split_items_s = sorted(split_items,
                                           key=lambda x: _item_roll_kg_c(x), reverse=False)
                else:  # MIN_VEHICLES / MIN_COST → FFD (내림차순 — 무거운 것 먼저)
                    split_items_s = sorted(split_items,
                                           key=lambda x: _item_roll_kg_c(x), reverse=True)

                # FFD / BFD Bin Packing
                bins_ffd = []
                for it in split_items_s:
                    qty_kg = _item_roll_kg_c(it)   # ← 수정: 실제 KG 사용 (롤 수 아님)
                    inch   = _ps_get_inch(it.get('SKUKEY',''))
                    grm    = _ps_get_grm(it.get('SKUKEY',''))
                    rc     = _item_roll_count_c(it)

                    placed = False
                    # MAX_FILL: Best-Fit (가장 여유가 적은 빈 우선)
                    # MIN_VEHICLES: First-Fit
                    search_bins = bins_ffd
                    if objective == 'MAX_FILL':
                        search_bins = sorted(bins_ffd, key=lambda b: big_cap - b['total_kg'])

                    for b in search_bins:
                        if _can_fit_roll_bin(b, qty_kg, inch, grm, rc, big_cap, b_i12, b_i3):
                            b['items'].append(it)
                            b['total_kg'] += qty_kg
                            if inch=='12인치': b['v12'][grm] += rc
                            elif inch=='3인치': b['v3'][grm] += rc
                            placed = True
                            break
                    if not placed:
                        nb = {'items':[it],'total_kg':qty_kg,'v12':defaultdict(int),'v3':defaultdict(int)}
                        if inch=='12인치': nb['v12'][grm] = rc
                        elif inch=='3인치': nb['v3'][grm] = rc
                        bins_ffd.append(nb)

                for b in bins_ffd:
                    veh_kg = b['total_kg']
                    # 인치 기준 차량
                    v12r, v3r = defaultdict(int), defaultdict(int)
                    for it in b['items']:
                        inch=_ps_get_inch(it.get('SKUKEY','')); grm=_ps_get_grm(it.get('SKUKEY',''))
                        rc=_item_roll_count_c(it)
                        if inch=='12인치': v12r[grm]+=rc
                        elif inch=='3인치': v3r[grm]+=rc
                    cands = (
                        [_ps_find_car(inch12_map,'12인치',g,rc,car_order,veh_info) for g,rc in v12r.items()] +
                        [_ps_find_car(inch3_map, '3인치', g,rc,car_order,veh_info) for g,rc in v3r.items()]
                    )
                    inch_car = min(cands, key=_sort_key) if cands else big_car

                    # 목적식 차량 선정
                    cost_car = _select_car(veh_kg, valid_cars, dptnky)
                    # 인치 기준 > 목적식 기준 중 더 큰 차량
                    veh_car  = inch_car if _sort_key(inch_car) <= _sort_key(cost_car) else cost_car

                    # 높이/CBM 검사 (기존 동일)
                    # ── 2) 원지 높이 제약 (엑셀 2. 원지 §1-2) ──────────
                    # - 파레트 유무 조건 (엑셀 1. 개요 §2, 2. 원지 §1-2):
                    #   FORKLIFT_YN='Y' → 지게차 사용 → 파렛트 높이 미차감 (height_m)
                    #   FORKLIFT_YN 미설정/N → 파렛트 15cm 차감 (effective_height_m)
                    # - MAX_STACK = Hard Cap 3단 (엑셀: 최대 적재 단수 3단으로 제한)
                    fk_yn = ptnr_info.get(dptnky, {}).get('forklift_yn', '')
                    def _roll_eff_h(cartype):
                        """원지용 effective_height: FORKLIFT_YN='Y'이면 height_m(파렛트 미차감)
                        엑셀 §1-2: 납품처가 파레트 적재 요구 시 차량 내부 가용 높이에서 15cm 차감
                        """
                        vi = veh_info.get(cartype, {})
                        if fk_yn == 'Y':
                            return vi.get('height_m', 99.0)        # 지게차O → 파렛트 미적용
                        return vi.get('effective_height_m', 99.0)  # 지게차X → 파렛트 15cm 차감

                    notes = split_notes_pre[:]

                    # 동적 제약 구분 노트 (엑셀 §4)
                    if is_dynamic_blocked:
                        notes.append(f"[동적배차불가] DYNAMIC_YN=N → 고정노선 전용 오더")
                    elif dyn_yn == 'Y':
                        notes.append(f"[동적배차가능] DYNAMIC_YN=Y")
                    # 미설정 시 노트 생략 (기본 가능)

                    for it in b['items']:
                        sk = it.get('SKUKEY','')
                        if not _ps_is_roll(sk): continue
                        dmm = _roll_diam_c(sk)
                        if dmm is None: continue

                        # Hard Cap: MAX_STACK(=3단) 초과 불가 (엑셀 §1-2: Hard Cap)
                        actual_stack = min(MAX_STACK, 3)  # 엑셀 명시 최대 3단
                        stack_h = dmm / 1000.0 * actual_stack
                        eff_h   = _roll_eff_h(veh_car)

                        if stack_h > eff_h:
                            upgraded = False
                            for c in car_order:
                                ct = c['CARTYPE']
                                if _sort_key(ct) >= _sort_key(veh_car): continue
                                if _roll_eff_h(ct) >= stack_h:
                                    notes.append(
                                        f"[높이업그레이드] {veh_car}→{ct}"
                                        + (f" (지게차Y:파렛트미적용)" if fk_yn == 'Y' else
                                           f" (파렛트15cm차감적용)")
                                    )
                                    veh_car = ct; upgraded = True; break
                            if not upgraded:
                                notes.append(
                                    f"[높이초과-수동확인] {sk} "
                                    f"롤직경{dmm:.0f}mm×{actual_stack}단={stack_h:.2f}m "
                                    f"> 차량가용{eff_h:.2f}m"
                                    + (f" (지게차Y)" if fk_yn == 'Y' else "")
                                )

                    cap = veh_info.get(veh_car,{}).get('load_kg',0)
                    fill = (veh_kg/cap*100) if cap > 0 else 0
                    # 비용 계산
                    cost_val = route_cost_map.get(dptnky, {}).get(veh_car, 0)

                    total_rc = sum(_item_roll_count_c(it) for it in b['items'] if _ps_is_roll(it.get('SKUKEY','')))
                    notes.append(
                        f"[{objective}] {veh_car} 선정 "
                        f"(적재{veh_kg:.0f}kg / 한도{cap:.0f}kg / 적재율{fill:.1f}%"
                        + (f" / 운송비{cost_val:,.0f}원" if cost_val > 0 else "") + ")"
                    )

                    pi = ptnr_info.get(dptnky, {})
                    if is_mixed:
                        notes.append(f"[혼적] 납품처: {rep_dptnm}")
                    # 혼적(원지+판지 동시) Z축 강제 노트 (엑셀 4. §1-1)
                    if is_mixed_load:
                        notes.append(
                            "[혼적-Z축] 원지 하단(바닥) / 판지 상단 배치 강제 "
                            "(파손 방지: 원지 압착으로 인한 판지 손상 방지)"
                        )
                        notes.append(
                            "[혼적-Y축] 복수납품처 LIFO: 나중 하차→차량 안쪽(캡방향) / "
                            "먼저 하차→문 쪽 배치 (배송순서 기준)"
                        )
                    all_vehicles.append({
                        'dptnky':        dptnky,  'dptnm':   dptnm,
                        'rqshpd':        rqshpd,  'cartype': veh_car,
                        'total_kg':      round(veh_kg, 2),
                        'load_cap':      cap,
                        'spare_kg':      round(cap - veh_kg, 2),
                        'fill_ratio':    round(fill, 1),
                        'items':         b['items'],
                        'item_cnt':      len(b['items']),
                        'material_type': 'ROLL',
                        'roll_count':    total_rc,
                        'route_cost':    cost_val,
                        'objective':     objective,
                        'profile_id':    pid,
                        'profile_nm':    prof['PROFILE_NM'],
                        'notes':         notes,
                        'is_mixed':      is_mixed,
                        'is_mixed_load': is_mixed_load,
                        'mixed_dptnm':   rep_dptnm if is_mixed else '',
                        'forklift_yn':   pi.get('forklift_yn',''),
                        'dynamic_yn':    pi.get('dynamic_yn',''),
                        'deadline_time': pi.get('deadline_time',''),
                        'max_ton_label': pi.get('max_ton_label',''),
                    })

            # ── 판지 배차 ─────────────────────────────────────────────
            # 엑셀 3. 판지 §1-1: CBM + 중량 이중 한계 검증 (Double-Threshold Check)
            # 중량: total_kg ≤ 차량 load_kg
            # 높이: total_h ≤ effective_height_m (파렛트 15cm 차감 후 가용 높이)
            # 엑셀 §1-3: 속포장 분할 — 차량 한계 도달 시 초과분(속단위)만 분할 → 후속 차량
            if board_items:
                big_h = veh_info.get(big_car,{}).get('effective_height_m', 99.0)
                # 판지용 가용 CBM (차량 길이 × 너비 × effective_height_m)
                big_len_m = veh_info.get(big_car,{}).get('length_m', 99.0)
                big_wid_m = veh_info.get(big_car,{}).get('width_m',  99.0)
                big_cbm   = big_len_m * big_wid_m * big_h  # 가용 적재함 부피(m³)

                veh_list_b, cur_b, cur_kg_b, cur_h_b, cur_cbm_b = [], [], 0.0, 0.0, 0.0
                for it in board_items:
                    qty_kg  = _board_kg_c(it)
                    item_h  = _board_h_c(it)   # 1속 높이(m)
                    item_cbm = _ps_get_item_cbm(it, skuma_map)
                    capped_h = min(item_h, MAX_B_HGT)
                    # Double-Threshold: 중량 OR CBM 초과 시 새 차량
                    # ※ 높이(ho) 검사 제거: 판지는 여러 속을 바닥에 나란히 배치하므로
                    #   수량×1속높이 합산은 실제보다 훨씬 큰 값이 됨.
                    #   1속 높이가 차량 높이 자체보다 큰 경우는 STEP-2 치수검사에서 처리.
                    ko = cur_b and (cur_kg_b + qty_kg > big_cap)
                    co = cur_b and item_cbm > 0 and big_cbm > 0 and (cur_cbm_b + item_cbm > big_cbm)
                    if ko or co:
                        veh_list_b.append({
                            'items': cur_b, 'total_kg': cur_kg_b,
                            'total_h': cur_h_b, 'total_cbm': cur_cbm_b,
                            'split_reason': ('중량초과' if ko else 'CBM초과'),
                        })
                        cur_b, cur_kg_b, cur_h_b, cur_cbm_b = [], 0.0, 0.0, 0.0
                    cur_b.append(it); cur_kg_b += qty_kg
                    cur_h_b = max(cur_h_b, capped_h)  # 그룹 내 최대 1속 높이 추적
                    cur_cbm_b += item_cbm
                if cur_b:
                    veh_list_b.append({
                        'items': cur_b, 'total_kg': cur_kg_b,
                        'total_h': cur_h_b, 'total_cbm': cur_cbm_b,
                        'split_reason': '',
                    })

                for vb_idx, vb in enumerate(veh_list_b):
                    veh_kg = vb['total_kg']
                    veh_car = _select_car(veh_kg, valid_cars, dptnky, material_type='BOARD')
                    cap = veh_info.get(veh_car,{}).get('load_kg',0)
                    fill = (veh_kg/cap*100) if cap>0 else 0
                    cost_val = route_cost_map.get(dptnky,{}).get(veh_car,0)

                    # 실제 선정 차량 기준 CBM 재계산
                    veh_eff_h   = veh_info.get(veh_car,{}).get('effective_height_m', big_h)
                    veh_len_m   = veh_info.get(veh_car,{}).get('length_m', big_len_m)
                    veh_wid_m   = veh_info.get(veh_car,{}).get('width_m',  big_wid_m)
                    veh_cbm_cap = veh_len_m * veh_wid_m * veh_eff_h
                    total_cbm   = vb.get('total_cbm', 0.0)
                    cbm_fill    = (total_cbm / veh_cbm_cap * 100.0) if veh_cbm_cap > 0 else 0.0

                    notes_b = [
                        f"[{objective}] {veh_car} 선정 "
                        f"(적재{veh_kg:.0f}kg / 한도{cap:.0f}kg / 적재율{fill:.1f}%"
                        + (f" / CBM{total_cbm:.2f}m³/{veh_cbm_cap:.1f}m³({cbm_fill:.0f}%)" if total_cbm > 0 else "")
                        + (f" / 운송비{cost_val:,.0f}원" if cost_val>0 else "") + ")"
                    ]
                    # BOARD_BULK_INTEGER_ONLY: 비정수 Ream 수량 검증 노트
                    for it_b in vb['items']:
                        bqw = _board_qty_warn(it_b)
                        if bqw:
                            notes_b.append(bqw)
                    # 속포장 분할 노트 (엑셀 §1-3: 속단위 분할 선적)
                    if vb_idx > 0 and vb.get('split_reason'):
                        notes_b.append(f"[분할선적-판지] {vb['split_reason']}으로 인한 후속 차량 배차")
                    # 동적 제약 구분 노트 (엑셀 §4)
                    if is_dynamic_blocked:
                        notes_b.append(f"[동적배차불가] DYNAMIC_YN=N → 고정노선 전용 오더")
                    elif dyn_yn == 'Y':
                        notes_b.append(f"[동적배차가능] DYNAMIC_YN=Y")

                    pi = ptnr_info.get(dptnky, {})
                    if is_mixed:
                        notes_b.append(f"[혼적] 납품처: {rep_dptnm}")
                    # 혼적(원지+판지 동시) Z축/LIFO 노트 (엑셀 4. §1-1, §1-2)
                    if is_mixed_load:
                        notes_b.append(
                            "[혼적-Z축] 원지 하단(바닥) / 판지 상단 배치 강제"
                        )
                        notes_b.append(
                            "[혼적-Y축] LIFO: 나중 하차→안쪽 / 먼저 하차→문 쪽"
                        )
                    all_vehicles.append({
                        'dptnky':        dptnky,  'dptnm':   dptnm,
                        'rqshpd':        rqshpd,  'cartype': veh_car,
                        'total_kg':      round(veh_kg, 2),
                        'load_cap':      cap,
                        'spare_kg':      round(cap - veh_kg, 2),
                        'fill_ratio':    round(fill, 1),
                        'items':         vb['items'],
                        'item_cnt':      len(vb['items']),
                        'material_type': 'BOARD',
                        'roll_count':    0,
                        'total_cbm':     round(total_cbm, 4),
                        'cbm_cap':       round(veh_cbm_cap, 2),
                        'cbm_fill':      round(cbm_fill, 1),
                        'route_cost':    cost_val,
                        'objective':     objective,
                        'profile_id':    pid,
                        'profile_nm':    prof['PROFILE_NM'],
                        'notes':         notes_b,
                        'is_mixed':      is_mixed,
                        'is_mixed_load': is_mixed_load,
                        'mixed_dptnm':   rep_dptnm if is_mixed else '',
                        'forklift_yn':   pi.get('forklift_yn',''),
                        'dynamic_yn':    pi.get('dynamic_yn',''),
                        'deadline_time': pi.get('deadline_time',''),
                        'max_ton_label': pi.get('max_ton_label',''),
                    })

            # ── 기타 품목(BOX 등) 배차 ───────────────────────────────────
            # other_items: SKUKEY가 원지/판지 형식이 아닌 BOX, 낱개 등
            # GRSWGT(중량) 또는 QTSHPO(수량)을 KG로 간주해 차량 선정
            if other_items:
                # KG 계산: GRSWGT > 0 → GRSWGT 사용, 아니면 QTSHPO
                def _other_kg(it):
                    gw = float(it.get('KG_WEIGHT') or it.get('GRSWGT') or 0)
                    return gw if gw > 0 else float(it.get('QTSHPO') or 0)

                total_other_kg = sum(_other_kg(it) for it in other_items)

                # ALLOW_MIXED_LOAD 여부와 무관하게 납품처 단위 처리
                # 목적식에 따른 차량 선정
                veh_car_o = _select_car(total_other_kg, valid_cars, dptnky)
                cap_o     = veh_info.get(veh_car_o, {}).get('load_kg', 0)
                fill_o    = (total_other_kg / cap_o * 100) if cap_o > 0 else 0
                cost_o    = route_cost_map.get(dptnky, {}).get(veh_car_o, 0)

                notes_o   = [
                    f"[{objective}] {veh_car_o} 선정 "
                    f"(기타품목 {len(other_items)}건 / "
                    f"적재{total_other_kg:.0f}kg / 한도{cap_o:.0f}kg / 적재율{fill_o:.1f}%"
                    + (f" / 운송비{cost_o:,.0f}원" if cost_o > 0 else "") + ")"
                ]
                if is_dynamic_blocked:
                    notes_o.append(f"[동적배차불가] DYNAMIC_YN=N → 고정노선 전용 오더")

                pi_o = ptnr_info.get(dptnky, {})
                all_vehicles.append({
                    'dptnky':        dptnky,  'dptnm':   dptnm,
                    'rqshpd':        rqshpd,  'cartype': veh_car_o,
                    'total_kg':      round(total_other_kg, 2),
                    'load_cap':      cap_o,
                    'spare_kg':      round(cap_o - total_other_kg, 2),
                    'fill_ratio':    round(fill_o, 1),
                    'items':         other_items,
                    'item_cnt':      len(other_items),
                    'material_type': 'OTHER',
                    'roll_count':    0,
                    'total_cbm':     0.0,
                    'cbm_cap':       0.0,
                    'cbm_fill':      0.0,
                    'route_cost':    cost_o,
                    'objective':     objective,
                    'profile_id':    pid,
                    'profile_nm':    prof['PROFILE_NM'],
                    'notes':         notes_o,
                    'is_mixed':      is_mixed,
                    'is_mixed_load': False,
                    'mixed_dptnm':   '',
                    'forklift_yn':   pi_o.get('forklift_yn', ''),
                    'dynamic_yn':    pi_o.get('dynamic_yn', ''),
                    'deadline_time': pi_o.get('deadline_time', ''),
                    'max_ton_label': pi_o.get('max_ton_label', ''),
                })

        # ── 응답 필드 정규화 (JS _dconRenderResult 호환) ────────────────
        for v in all_vehicles:
            v['used_kg']    = v.get('total_kg', 0)
            v['load_ton']   = round(v.get('load_cap', 0) / 1000.0, 1)
            v['cartype_nm'] = v.get('cartype', '')
            v['cost']       = v.get('route_cost', 0)
            # dynamic_yn 기본값 보장 (응답 필드 일관성)
            if 'dynamic_yn' not in v:
                v['dynamic_yn'] = ''
            # total_cbm / cbm_cap / cbm_fill 기본값 (원지/기타 차량)
            if 'total_cbm' not in v:
                v['total_cbm'] = 0.0
            if 'cbm_cap' not in v:
                v['cbm_cap']   = 0.0
            if 'cbm_fill' not in v:
                v['cbm_fill']  = 0.0
            # items 필드에서 weight_kg 정규화
            for it in v.get('items', []):
                if 'KG_WEIGHT' not in it:
                    it['KG_WEIGHT'] = it.get('GRSWGT', 0)

        # ── 요약 통계 ────────────────────────────────────────────────
        total_cost      = sum(v.get('route_cost',0) or 0 for v in all_vehicles)
        avg_fill        = (sum(v.get('fill_ratio',0) for v in all_vehicles) / len(all_vehicles)
                           if all_vehicles else 0)
        # 동적 제약 요약 (엑셀 §4)
        dynamic_blocked_cnt = sum(1 for v in all_vehicles if v.get('dynamic_yn') == 'N')
        dynamic_ok_cnt      = len(all_vehicles) - dynamic_blocked_cnt
        # 판지 CBM 합계
        total_cbm_all   = sum(v.get('total_cbm', 0) or 0 for v in all_vehicles)

        return jsonify({
            "ok":        True,
            "objective": objective,
            "profile_id":  pid,
            "profile_nm":  prof['PROFILE_NM'],
            "total_vehicles":        len(all_vehicles),
            "total_cost":            round(total_cost, 0),
            "avg_fill_ratio":        round(avg_fill, 1),
            "total_cbm":             round(total_cbm_all, 4),
            # 동적 제약 요약
            "dynamic_blocked_cnt":   dynamic_blocked_cnt,
            "dynamic_ok_cnt":        dynamic_ok_cnt,
            "vehicles":              all_vehicles,
        })
    except Exception as e:
        import traceback
        return jsonify({"error": str(e), "trace": traceback.format_exc()}), 500
    finally:
        conn.close()


if __name__ == '__main__':
    # ── 앱 기동 시 DB 스키마 마이그레이션 (컬럼 없으면 자동 추가) ──
    _mig_conn = sqlite3.connect(DB_PATH)
    try:
        _existing = [r[1] for r in _mig_conn.execute('PRAGMA table_info(PS_DISPATCH_H)').fetchall()]
        if 'STKNUM' not in _existing:
            _mig_conn.execute('ALTER TABLE PS_DISPATCH_H ADD COLUMN STKNUM TEXT DEFAULT NULL')
            app.logger.info('DB migration: PS_DISPATCH_H.STKNUM 컬럼 추가')
        if 'UPDDAT' not in _existing:
            _mig_conn.execute('ALTER TABLE PS_DISPATCH_H ADD COLUMN UPDDAT TEXT DEFAULT NULL')
            app.logger.info('DB migration: PS_DISPATCH_H.UPDDAT 컬럼 추가')
        _mig_conn.commit()
    except Exception as _e:
        app.logger.error(f'DB migration 실패: {_e}')
    finally:
        _mig_conn.close()

    app.run(host='0.0.0.0', port=5050, debug=False)
