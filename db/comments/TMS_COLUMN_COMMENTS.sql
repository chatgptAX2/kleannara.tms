-- =====================================================================
--  TMS_* 테이블 컬럼 코멘트 DDL (Oracle KNRAWMS 스키마)
--  ---------------------------------------------------------------------
--  · 대상: kleannara.tms 서비스에서 실제 사용되는 TMS_* 테이블 전체 컬럼
--  · 근거: JPA Entity / JdbcTemplate SQL 에서 참조되는 실제 컬럼 기준
--  · 실행: 운영 DBA 가 직접 실행 (본 파일은 코멘트(COMMENT ON) 전용, 스키마 변경 없음)
--  · 표기: 스키마 접두사 KNRAWMS. 포함. 필요 시 일괄 치환.
--  · 주의: 아래는 서비스 코드에서 확인된 컬럼만 포함. DB 에 존재하나
--          코드 미참조인 컬럼(감사/예비 컬럼 등)은 제외되어 있을 수 있음.
--  ---------------------------------------------------------------------
--  · [중요] 코드의 일부 JPA Entity 컬럼명이 실제 테이블과 다를 수 있음.
--    따라서 100% 정확한 코멘트를 위해서는 먼저 00_INSPECT_ACTUAL_COLUMNS.sql
--    로 실제 컬럼을 조회하여 대조할 것을 권장.
--  · [정정이력 v2]
--    - TMS_ROUTE_COST : 실제 컬럼(PTNRKY/CARCLASS/COST/DATE_START/DATE_END)으로
--      정정. (기존 COST_ID/COST_AMT/EFF_DATE/EXP_DATE/UPDDAT/UPDUSR 는 실제
--      테이블에 없어 ORA-00904 실패 → 제거/치환)
--    - TMS_DOC_FILE.OP_DATE : 운영 DB 미적용 컬럼일 수 있어 기본 주석 처리.
--  =====================================================================


-- =====================================================================
-- 1) TMS_DS_VEHICLE  : 차량 제원 마스터 (차량유형관리)
-- =====================================================================
COMMENT ON TABLE  KNRAWMS.TMS_DS_VEHICLE                    IS '차량 제원 마스터 - 차종별 규격/적재기준(차량유형관리 화면)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_VEHICLE.CARCLASS_CD        IS '차종코드 (PK, 예: Z010) - 공통코드 TMS_CARCLASS10/20 매핑키';
COMMENT ON COLUMN KNRAWMS.TMS_DS_VEHICLE.CARTYPE            IS '차종명 (예: 1.4톤, 3.5톤, 18톤)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_VEHICLE.LENGTH_M           IS '차량(적재함) 길이 (m)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_VEHICLE.WIDTH_M            IS '차량(적재함) 너비 (m) - 범위 표기 가능(예: 1.8~2.1)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_VEHICLE.HEIGHT_M           IS '차량(적재함) 높이 (m)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_VEHICLE.LOAD_TON           IS '적재가능 중량 (ton)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_VEHICLE.SORT_SEQ           IS '정렬순서 (DESC 로 큰 차→작은 차 정렬에 사용)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_VEHICLE.PALLET_HEIGHT_M    IS '파렛트 높이 (m) - 원지 세워적재 가용높이 차감용';
COMMENT ON COLUMN KNRAWMS.TMS_DS_VEHICLE.PALLET_CNT         IS '파렛트 수';
COMMENT ON COLUMN KNRAWMS.TMS_DS_VEHICLE.LONG_AXIS_YN       IS '장축여부 (Y/N)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_VEHICLE.INCH12_LT300       IS '12인치 원지 평량 300 미만 1단 최대 적재수량';
COMMENT ON COLUMN KNRAWMS.TMS_DS_VEHICLE.INCH12_GE300       IS '12인치 원지 평량 300 이상 1단 최대 적재수량';
COMMENT ON COLUMN KNRAWMS.TMS_DS_VEHICLE.INCH3_LT300        IS '3인치 원지 평량 300 미만 1단 최대 적재수량';
COMMENT ON COLUMN KNRAWMS.TMS_DS_VEHICLE.INCH3_GE300        IS '3인치 원지 평량 300 이상 1단 최대 적재수량';
COMMENT ON COLUMN KNRAWMS.TMS_DS_VEHICLE.DEFAULT_VEH_CNT    IS '기본 배차 대수';
COMMENT ON COLUMN KNRAWMS.TMS_DS_VEHICLE.UPDDAT             IS '수정일자 (YYYYMMDD)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_VEHICLE.UPDUSR             IS '수정자';


-- =====================================================================
-- 2) TMS_DS_INCH12 : 12인치 원지 차종별 1단 적재수량 기준
-- =====================================================================
COMMENT ON TABLE  KNRAWMS.TMS_DS_INCH12                     IS '12인치 원지 차종/평량별 1단 최대 적재수량 기준';
COMMENT ON COLUMN KNRAWMS.TMS_DS_INCH12.CARTYPE            IS '차종명 (예: 5톤, 18톤)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_INCH12.GRM_COND          IS '평량 조건 (GE300=300 이상 / LT300=300 미만)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_INCH12.MAX_COUNT         IS '해당 차종/평량의 1단 최대 적재 롤 수';


-- =====================================================================
-- 3) TMS_DS_INCH3 : 3인치 원지 차종별 1단 적재수량 기준
-- =====================================================================
COMMENT ON TABLE  KNRAWMS.TMS_DS_INCH3                      IS '3인치 원지 차종/평량별 1단 최대 적재수량 기준';
COMMENT ON COLUMN KNRAWMS.TMS_DS_INCH3.CARTYPE            IS '차종명 (예: 5톤, 18톤)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_INCH3.GRM_COND          IS '평량 조건 (GE300=300 이상 / LT300=300 미만)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_INCH3.MAX_COUNT         IS '해당 차종/평량의 1단 최대 적재 롤 수';


-- =====================================================================
-- 4) TMS_DS_DISPATCH_OBJECTIVE : 배차 목적식 마스터
-- =====================================================================
COMMENT ON TABLE  KNRAWMS.TMS_DS_DISPATCH_OBJECTIVE        IS '배차 목적식(최적화 목표) 마스터';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_OBJECTIVE.OBJ_ID    IS '목적식 ID (PK)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_OBJECTIVE.OBJ_CODE  IS '목적식 코드 (MIN_VEHICLES=차량최소화 / MAX_FILL=적재율최대 / MIN_COST=비용최소)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_OBJECTIVE.OBJ_NM    IS '목적식 표시명';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_OBJECTIVE.OBJ_ICON  IS '목적식 아이콘(이모지)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_OBJECTIVE.OBJ_ALGO  IS '알고리즘 코드';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_OBJECTIVE.OBJ_DESC  IS '목적식 설명';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_OBJECTIVE.SORT_SEQ  IS '정렬순서';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_OBJECTIVE.ACTIVE_YN IS '활성여부 (Y/N) - 스코프별 단일 활성 보장';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_OBJECTIVE.SCOPE     IS '적용 스코프 (PS=판지 / HL=생활). 기존 데이터는 COALESCE 로 PS 귀속';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_OBJECTIVE.CREDAT    IS '생성일자 (YYYYMMDD)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_OBJECTIVE.LMODAT    IS '수정일자 (YYYYMMDD)';


-- =====================================================================
-- 5) TMS_DS_DISPATCH_PROFILE : 배차 프로파일(목적식+제약세트 조합)
-- =====================================================================
COMMENT ON TABLE  KNRAWMS.TMS_DS_DISPATCH_PROFILE         IS '배차 프로파일 - 목적식과 제약조건 세트를 조합한 배차 설정 단위';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_PROFILE.PROFILE_ID IS '프로파일 ID (PK)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_PROFILE.PROFILE_NM IS '프로파일명';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_PROFILE.OBJECTIVE  IS '연결된 목적식 코드 (=TMS_DS_DISPATCH_OBJECTIVE.OBJ_CODE)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_PROFILE.SET_ID     IS '연결된 제약조건 세트 ID (=TMS_DS_DISPATCH_CONST_SET.SET_ID)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_PROFILE.ACTIVE_YN  IS '활성여부 (Y/N)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_PROFILE.NOTE       IS '비고';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_PROFILE.SCOPE      IS '적용 스코프 (PS=판지 / HL=생활)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_PROFILE.CREDAT     IS '생성일자 (YYYYMMDD)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_PROFILE.LMODAT     IS '수정일자 (YYYYMMDD)';


-- =====================================================================
-- 6) TMS_DS_DISPATCH_CONST : 배차 제약조건(개별 제약 항목)
-- =====================================================================
COMMENT ON TABLE  KNRAWMS.TMS_DS_DISPATCH_CONST           IS '배차 제약조건 개별 항목 마스터';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST.CONST_ID    IS '제약 ID (PK)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST.PROFILE_ID  IS '연결 프로파일 ID (=TMS_DS_DISPATCH_PROFILE.PROFILE_ID)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST.CONST_TYPE  IS '제약 유형 (GLOBAL/VEHICLE/PARTNER/CARTYPE/ENTRY_TON/FORKLIFT/DYNAMIC/PTNR_MULTI/REGION 등)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST.CONST_KEY   IS '제약 키 (제약 파라미터 명)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST.CONST_VALUE IS '제약 값';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST.CONST_OP    IS '비교 연산자 (=, <=, >= 등)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST.TARGET_ID   IS '적용 대상 ID (차종코드/납품처코드 등)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST.TARGET_NM   IS '적용 대상명';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST.ACTIVE_YN   IS '활성여부 (Y/N)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST.NOTE        IS '비고';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST.SORT_SEQ    IS '정렬순서';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST.SCOPE       IS '적용 스코프 (PS=판지 / HL=생활)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST.CREDAT      IS '생성일자 (YYYYMMDD)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST.LMODAT      IS '수정일자 (YYYYMMDD)';


-- =====================================================================
-- 7) TMS_DS_DISPATCH_CONST_SET : 제약조건 세트(묶음)
-- =====================================================================
COMMENT ON TABLE  KNRAWMS.TMS_DS_DISPATCH_CONST_SET       IS '배차 제약조건 세트(여러 제약 항목의 묶음)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST_SET.SET_ID    IS '세트 ID (PK)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST_SET.SET_NM    IS '세트명';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST_SET.SET_DESC  IS '세트 설명';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST_SET.ACTIVE_YN IS '활성여부 (Y/N)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST_SET.SCOPE     IS '적용 스코프 (PS=판지 / HL=생활)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST_SET.CREDAT    IS '생성일자 (YYYYMMDD)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST_SET.LMODAT    IS '수정일자 (YYYYMMDD)';


-- =====================================================================
-- 8) TMS_DS_DISPATCH_CONST_SET_ITEM : 세트-제약 매핑(항목)
-- =====================================================================
COMMENT ON TABLE  KNRAWMS.TMS_DS_DISPATCH_CONST_SET_ITEM  IS '제약조건 세트와 개별 제약(CONST)의 매핑 항목';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST_SET_ITEM.ITEM_ID     IS '항목 ID (PK)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST_SET_ITEM.SET_ID      IS '세트 ID (=TMS_DS_DISPATCH_CONST_SET.SET_ID)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST_SET_ITEM.CONST_ID    IS '제약 ID (=TMS_DS_DISPATCH_CONST.CONST_ID)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST_SET_ITEM.ACTIVE_YN   IS '활성여부 (Y/N)';
COMMENT ON COLUMN KNRAWMS.TMS_DS_DISPATCH_CONST_SET_ITEM.PARAM_VALUE IS '항목별 파라미터 값(제약 값 오버라이드)';


-- =====================================================================
-- 9) TMS_PS_DISPATCH_H : PS 배차 헤더
-- =====================================================================
COMMENT ON TABLE  KNRAWMS.TMS_PS_DISPATCH_H               IS 'PS 배차 헤더 - 배차(가선적) 단위 헤더';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_H.DISPATCH_NO   IS '배차번호 (PK, 예: 260509001T)';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_H.RQSHPD        IS '납품요청일 (YYYYMMDD)';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_H.DPTNKY        IS '납품처코드';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_H.DPTNM         IS '납품처명';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_H.CARTYPE       IS '배정 차종 (예: 5톤, 18톤)';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_H.CARCLASS_CD   IS '배정 차종코드';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_H.TOTAL_KG      IS '총 중량 (KG)';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_H.TOTAL_CNT     IS '총 아이템 건수';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_H.LOAD_KG       IS '배정 차량 적재가능 중량 (KG)';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_H.MATERIAL_TYPE IS '자재유형 (ROLL=원지 / BOARD=판지 / OTHER=기타)';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_H.STAT_CD       IS '상태 (PENDING=대기 / CONFIRMED=확정 / CANCELLED=취소)';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_H.CREATED_AT    IS '생성일시';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_H.UPDATED_AT    IS '수정일시';


-- =====================================================================
-- 10) TMS_PS_DISPATCH_D : PS 배차 아이템(상세)
-- =====================================================================
COMMENT ON TABLE  KNRAWMS.TMS_PS_DISPATCH_D              IS 'PS 배차 아이템 상세 - 배차헤더 1:N 품목 상세';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_D.ITEM_ID      IS '아이템 ID (PK)';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_D.DISPATCH_NO  IS '배차번호 (FK → TMS_PS_DISPATCH_H)';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_D.SEQ          IS '순번';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_D.SHPOKY       IS '납품문서번호';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_D.SHPOIT       IS '납품문서 라인번호';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_D.SKUKEY       IS '품목코드';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_D.DESC01       IS '품목명';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_D.QTSHPO       IS '출하수량';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_D.UOMKEY       IS '단위 (KG=중량 / R=롤)';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_D.DPTNKY       IS '납품처코드';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_D.DPTNM        IS '납품처명';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_D.IS_SPLIT     IS '분할여부 (0=원본 / 1=분할)';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_D.ORG_SHPOKY   IS '원본 납품문서번호 (분할 시)';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_D.ORG_SHPOIT   IS '원본 납품문서 라인 (분할 시)';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_D.GRSWGT       IS '묶음당 중량 (KG)';
COMMENT ON COLUMN KNRAWMS.TMS_PS_DISPATCH_D.KG_WEIGHT    IS 'KG 환산 총중량 (QTSHPO×NETWGT 기반)';


-- =====================================================================
-- 11) TMS_ROUTE_COST : 경로별 운송비 마스터
--   ★ 정정: 실제 배차엔진 SQL(module-wms)이 사용하는 컬럼명 기준.
--     실제 컬럼 = PTNRKY, CARCLASS, COST, DATE_START, DATE_END
--     (기존 Entity RouteCost.java 의 COST_AMT/EFF_DATE/EXP_DATE/COST_ID 등은
--      논리 alias 였고 실제 테이블 컬럼이 아니라 코멘트 실행이 실패했음)
--   ※ WAREKY / DIST_KM 은 module-delivery Repository 에만 나타나 실제 존재
--     여부 불확실 → 00_INSPECT_ACTUAL_COLUMNS.sql 결과로 최종 확정 권장.
-- =====================================================================
COMMENT ON TABLE  KNRAWMS.TMS_ROUTE_COST                 IS '경로별(납품처×차종) 운송비 마스터';
COMMENT ON COLUMN KNRAWMS.TMS_ROUTE_COST.PTNRKY          IS '납품처코드';
COMMENT ON COLUMN KNRAWMS.TMS_ROUTE_COST.CARCLASS        IS '차종코드 (CMCDV TMS_CARCLASS10 매핑)';
COMMENT ON COLUMN KNRAWMS.TMS_ROUTE_COST.COST            IS '운송비 (원)';
COMMENT ON COLUMN KNRAWMS.TMS_ROUTE_COST.DATE_START      IS '적용 시작일자 (YYYYMMDD)';
COMMENT ON COLUMN KNRAWMS.TMS_ROUTE_COST.DATE_END        IS '적용 종료일자 (YYYYMMDD)';


-- =====================================================================
-- 12) TMS_DOC_FOLDER : 문서 관리 폴더
-- =====================================================================
COMMENT ON TABLE  KNRAWMS.TMS_DOC_FOLDER                 IS '문서관리 폴더(트리)';
COMMENT ON COLUMN KNRAWMS.TMS_DOC_FOLDER.FOLDER_ID       IS '폴더 ID (PK)';
COMMENT ON COLUMN KNRAWMS.TMS_DOC_FOLDER.FOLDER_NM       IS '폴더명';
COMMENT ON COLUMN KNRAWMS.TMS_DOC_FOLDER.PARENT_ID       IS '상위 폴더 ID (최상위=NULL)';
COMMENT ON COLUMN KNRAWMS.TMS_DOC_FOLDER.SORT_SEQ        IS '정렬순서';
COMMENT ON COLUMN KNRAWMS.TMS_DOC_FOLDER.CREDAT          IS '생성일자 (YYYYMMDD)';
COMMENT ON COLUMN KNRAWMS.TMS_DOC_FOLDER.CRETIM          IS '생성시각 (HHMMSS)';
COMMENT ON COLUMN KNRAWMS.TMS_DOC_FOLDER.LMODAT          IS '수정일자 (YYYYMMDD)';
COMMENT ON COLUMN KNRAWMS.TMS_DOC_FOLDER.DEL_YN          IS '삭제여부 (Y/N, 논리삭제)';


-- =====================================================================
-- 13) TMS_DOC_FILE : 문서 관리 파일
-- =====================================================================
COMMENT ON TABLE  KNRAWMS.TMS_DOC_FILE                   IS '문서관리 첨부파일';
COMMENT ON COLUMN KNRAWMS.TMS_DOC_FILE.FILE_ID           IS '파일 ID (PK)';
COMMENT ON COLUMN KNRAWMS.TMS_DOC_FILE.FOLDER_ID         IS '소속 폴더 ID (=TMS_DOC_FOLDER.FOLDER_ID)';
COMMENT ON COLUMN KNRAWMS.TMS_DOC_FILE.FILE_NM           IS '파일명(원본)';
COMMENT ON COLUMN KNRAWMS.TMS_DOC_FILE.FILE_PATH         IS '저장 경로';
COMMENT ON COLUMN KNRAWMS.TMS_DOC_FILE.FILE_SIZE         IS '파일 크기 (bytes)';
COMMENT ON COLUMN KNRAWMS.TMS_DOC_FILE.FILE_TYPE         IS '파일 MIME 타입';
COMMENT ON COLUMN KNRAWMS.TMS_DOC_FILE.FILE_EXT          IS '파일 확장자';
-- ★ 정정: OP_DATE(운행일자) 컬럼은 운영 DB 미적용 상태일 수 있음(코드가 존재
--   여부를 동적 확인 후 없으면 NULL 대체). 컬럼 추가(FIX_DOC_FILE_ADD_OP_DATE.sql)
--   적용 후에만 아래 코멘트 실행 가능하므로 기본 주석 처리함.
-- COMMENT ON COLUMN KNRAWMS.TMS_DOC_FILE.OP_DATE        IS '작업(문서/운행) 일자';
COMMENT ON COLUMN KNRAWMS.TMS_DOC_FILE.NOTE              IS '비고';
COMMENT ON COLUMN KNRAWMS.TMS_DOC_FILE.CREDAT            IS '생성일자 (YYYYMMDD)';
COMMENT ON COLUMN KNRAWMS.TMS_DOC_FILE.CRETIM            IS '생성시각 (HHMMSS)';
COMMENT ON COLUMN KNRAWMS.TMS_DOC_FILE.CREUSR            IS '생성자';
COMMENT ON COLUMN KNRAWMS.TMS_DOC_FILE.LMODAT            IS '수정일자 (YYYYMMDD)';
COMMENT ON COLUMN KNRAWMS.TMS_DOC_FILE.DEL_YN            IS '삭제여부 (Y/N, 논리삭제)';
COMMENT ON COLUMN KNRAWMS.TMS_DOC_FILE.DOWNLOAD_CNT      IS '다운로드 횟수';


-- =====================================================================
-- 14) TMS_SHPDH : 납품(출고예정) 문서 헤더  [WMS/SAP 연동 원천]
--     ※ WMS 원천 테이블. 코드에서 참조되는 컬럼 위주로 코멘트.
-- =====================================================================
COMMENT ON TABLE  KNRAWMS.TMS_SHPDH                      IS '납품(출고예정) 문서 헤더 - WMS 연동 원천';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDH.SHPOKY              IS '출고문서키 (PK)';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDH.WAREKY             IS '창고코드';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDH.OWNRKY             IS '화주코드';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDH.DPTNKY             IS '납품처코드';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDH.PTRCVR             IS '인수처(수령처)';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDH.RQSHPD             IS '납품요청일 (YYYYMMDD)';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDH.DOCDAT             IS '문서일자 (YYYYMMDD)';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDH.STATDO             IS '오더상태 (OCN=오더취소 등)';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDH.SHPMTY             IS '출하유형 (201=판매출고 등)';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDH.DOCUTY             IS '문서유형';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDH.PRTCHK             IS '출력체크';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDH.VEHINO             IS '차량번호(입력)';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDH.CARTON             IS '박스수';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDH.CARNO              IS '차량번호';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDH.DRIVER             IS '기사명';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDH.DRIVERCEL          IS '기사 연락처';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDH.CREDAT             IS '생성일자 (YYYYMMDD)';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDH.CREUSR             IS '생성자';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDH.LMODAT             IS '수정일자 (YYYYMMDD)';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDH.LMOUSR             IS '수정자';


-- =====================================================================
-- 15) TMS_SHPDI : 납품(출고예정) 문서 아이템  [WMS/SAP 연동 원천]
--     ※ WMS 원천 테이블. 코드에서 참조되는 컬럼 위주로 코멘트.
-- =====================================================================
COMMENT ON TABLE  KNRAWMS.TMS_SHPDI                      IS '납품(출고예정) 문서 아이템 - WMS 연동 원천';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.SHPOKY             IS '출고문서키 (=TMS_SHPDH.SHPOKY)';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.SHPOIT             IS '출고문서 라인번호';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.SKUKEY             IS '품목코드';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.DESC01             IS '품목명';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.DESC02             IS '품목 부가설명 / 연동구분(OFFLINE=미연동, ONLINE=연동)';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.SKUG05             IS '제품군 (10=판지사업부(PS) / 20=생활(HL))';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.MEASKY             IS '단위환산 측정키(MEASI 조인)';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.UOMKEY             IS '단위 (KG=중량 / R=롤)';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.QTSHPO             IS '출하(주문)수량';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.QTUALO             IS '미할당 수량';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.QTALOC             IS '할당 수량';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.QTJCMP             IS '작업완료 수량';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.QTSHPD             IS '출하 수량';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.STATIT             IS '아이템 상태 (FCO=취소 등)';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.STDLNR             IS '가선적번호 (배차번호 기록)';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.SVBELN             IS 'SAP 납품문서번호';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.STKNUM             IS 'SAP 선적번호(선적생성 시 기록)';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.LOTA01             IS 'LOT 속성01';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.LOTA02             IS 'LOT 속성02 (플랜트)';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.LOTA03             IS 'LOT 속성03';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.TLOTA01            IS '목표 LOT 속성01';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.TLOTA02            IS '목표 LOT 속성02';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.ALSTKY             IS '할당 재고키';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.SPOSNR             IS 'SAP 아이템 번호';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.CREDAT             IS '생성일자 (YYYYMMDD)';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.CRETIM             IS '생성시각 (HHMMSS)';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.CREUSR             IS '생성자';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.LMODAT             IS '수정일자 (YYYYMMDD)';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.LMOTIM             IS '수정시각 (HHMMSS)';
COMMENT ON COLUMN KNRAWMS.TMS_SHPDI.LMOUSR             IS '수정자';

-- =====================================================================
--  END OF FILE
-- =====================================================================
