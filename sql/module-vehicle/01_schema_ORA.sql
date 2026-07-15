-- ============================================================
--  module-vehicle: DDL (Oracle 19C 호환)
--  생성일: 2026-07-15  /  원본: 01_schema.sql (MariaDB/MySQL)
--  변환규칙:
--    CREATE TABLE IF NOT EXISTS → DECLARE/EXECUTE IMMEDIATE (ORA-955 무시)
--    VARCHAR(n)               → VARCHAR2(n CHAR)
--    INT                      → NUMBER(10)
--    DECIMAL(m,n)             → NUMBER(m,n)
--    DEFAULT ''               → DEFAULT ' '
--    ENGINE=InnoDB ...        → 제거
--    COMMENT '...' (인라인)  → COMMENT ON COLUMN 별도 문장
-- ============================================================

-- ── 배차 차량 클래스 마스터 (DS_VEHICLE) ───────────────────
DECLARE
BEGIN
    EXECUTE IMMEDIATE '
        CREATE TABLE DS_VEHICLE (
            CARCLASS_CD     VARCHAR2(20  CHAR) NOT NULL,
            CARTYPE         VARCHAR2(50  CHAR) DEFAULT '' '',
            LENGTH_M        NUMBER(6,2)        DEFAULT 0,
            WIDTH_M         VARCHAR2(10  CHAR) DEFAULT '' '',
            HEIGHT_M        NUMBER(6,2)        DEFAULT 0,
            LOAD_TON        NUMBER(6,2)        DEFAULT 0,
            SORT_SEQ        NUMBER(10)         DEFAULT 0,
            UPDDAT          VARCHAR2(8   CHAR) DEFAULT '' '',
            UPDUSR          VARCHAR2(20  CHAR) DEFAULT '' '',
            PALLET_HEIGHT_M NUMBER(6,3)        DEFAULT 0,
            INCH12_LT300    NUMBER(10),
            INCH12_GE300    NUMBER(10),
            INCH3_LT300     NUMBER(10),
            INCH3_GE300     NUMBER(10),
            DEFAULT_VEH_CNT NUMBER(10),
            PALLET_CNT      NUMBER(10),
            LONG_AXIS_YN    VARCHAR2(1   CHAR) DEFAULT ''N'',
            CONSTRAINT PK_DS_VEHICLE PRIMARY KEY (CARCLASS_CD)
        )
    ';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

COMMENT ON TABLE  DS_VEHICLE                 IS '배차 차량 클래스 마스터';
COMMENT ON COLUMN DS_VEHICLE.CARCLASS_CD     IS '차량클래스코드 (PK)';
COMMENT ON COLUMN DS_VEHICLE.CARTYPE         IS '차종명';
COMMENT ON COLUMN DS_VEHICLE.LENGTH_M        IS '적재함 길이(m)';
COMMENT ON COLUMN DS_VEHICLE.WIDTH_M         IS '적재함 폭(m)';
COMMENT ON COLUMN DS_VEHICLE.HEIGHT_M        IS '적재함 높이(m)';
COMMENT ON COLUMN DS_VEHICLE.LOAD_TON        IS '최대 적재톤수';
COMMENT ON COLUMN DS_VEHICLE.SORT_SEQ        IS '정렬순서';
COMMENT ON COLUMN DS_VEHICLE.UPDDAT          IS '수정일자';
COMMENT ON COLUMN DS_VEHICLE.UPDUSR          IS '수정자';
COMMENT ON COLUMN DS_VEHICLE.PALLET_HEIGHT_M IS '파레트 높이(m)';
COMMENT ON COLUMN DS_VEHICLE.INCH12_LT300    IS '12인치/평량300미만 1단기준수';
COMMENT ON COLUMN DS_VEHICLE.INCH12_GE300    IS '12인치/평량300이상 1단기준수';
COMMENT ON COLUMN DS_VEHICLE.INCH3_LT300     IS '3인치/평량300미만 1단기준수';
COMMENT ON COLUMN DS_VEHICLE.INCH3_GE300     IS '3인치/평량300이상 1단기준수';
COMMENT ON COLUMN DS_VEHICLE.DEFAULT_VEH_CNT IS '기본 배차 대수';
COMMENT ON COLUMN DS_VEHICLE.PALLET_CNT      IS '파레트 최대 수량';
COMMENT ON COLUMN DS_VEHICLE.LONG_AXIS_YN    IS '장축 여부';


-- ── 12인치 롤 1단 기준수 (DS_INCH12) ──────────────────────
DECLARE
BEGIN
    EXECUTE IMMEDIATE '
        CREATE TABLE DS_INCH12 (
            CARTYPE   VARCHAR2(50 CHAR) NOT NULL,
            GRM_COND  VARCHAR2(20 CHAR) NOT NULL,
            MAX_COUNT NUMBER(10)        DEFAULT 0,
            SORT_SEQ  NUMBER(10)        DEFAULT 0,
            UPDDAT    VARCHAR2(8  CHAR) DEFAULT '' '',
            CONSTRAINT PK_DS_INCH12 PRIMARY KEY (CARTYPE, GRM_COND)
        )
    ';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

COMMENT ON TABLE  DS_INCH12           IS '12인치 롤 1단 기준수';
COMMENT ON COLUMN DS_INCH12.CARTYPE   IS '차종명 (FK)';
COMMENT ON COLUMN DS_INCH12.GRM_COND  IS '평량조건 (LT300/GE300)';
COMMENT ON COLUMN DS_INCH12.MAX_COUNT IS '1단 최대 적재수';
COMMENT ON COLUMN DS_INCH12.SORT_SEQ  IS '정렬순서';
COMMENT ON COLUMN DS_INCH12.UPDDAT    IS '수정일자';


-- ── 3인치 롤 1단 기준수 (DS_INCH3) ────────────────────────
DECLARE
BEGIN
    EXECUTE IMMEDIATE '
        CREATE TABLE DS_INCH3 (
            CARTYPE   VARCHAR2(50 CHAR) NOT NULL,
            GRM_COND  VARCHAR2(20 CHAR) NOT NULL,
            MAX_COUNT NUMBER(10)        DEFAULT 0,
            SORT_SEQ  NUMBER(10)        DEFAULT 0,
            UPDDAT    VARCHAR2(8  CHAR) DEFAULT '' '',
            CONSTRAINT PK_DS_INCH3 PRIMARY KEY (CARTYPE, GRM_COND)
        )
    ';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

COMMENT ON TABLE  DS_INCH3           IS '3인치 롤 1단 기준수';
COMMENT ON COLUMN DS_INCH3.CARTYPE   IS '차종명 (FK)';
COMMENT ON COLUMN DS_INCH3.GRM_COND  IS '평량조건 (LT300/GE300)';
COMMENT ON COLUMN DS_INCH3.MAX_COUNT IS '1단 최대 적재수';
COMMENT ON COLUMN DS_INCH3.SORT_SEQ  IS '정렬순서';
COMMENT ON COLUMN DS_INCH3.UPDDAT    IS '수정일자';


-- ── 실차량 마스터 (VHCMA) ─────────────────────────────────
DECLARE
BEGIN
    EXECUTE IMMEDIATE '
        CREATE TABLE VHCMA (
            VEHICLE_NO          VARCHAR2(20  CHAR) NOT NULL,
            OWNRKY              VARCHAR2(10  CHAR) NOT NULL DEFAULT '' '',
            SHIP_POINT          VARCHAR2(10  CHAR) DEFAULT '' '',
            PRODUCT_GROUP       VARCHAR2(20  CHAR) DEFAULT '' '',
            DELIVERY_ZONE       VARCHAR2(20  CHAR) DEFAULT '' '',
            CARRIER             VARCHAR2(50  CHAR) DEFAULT '' '',
            VEHICLE_TYPE        VARCHAR2(50  CHAR) DEFAULT '' '',
            VEHICLE_KIND        VARCHAR2(30  CHAR) DEFAULT '' '',
            VEHICLE_CLASS       VARCHAR2(20  CHAR) DEFAULT '' '',
            DRIVER_NAME         VARCHAR2(50  CHAR) DEFAULT '' '',
            CONTACT_NO          VARCHAR2(20  CHAR) DEFAULT '' '',
            AXLE_TYPE           VARCHAR2(10  CHAR) DEFAULT '' '',
            LOAD_VOLUME         NUMBER(8,2)         DEFAULT 0,
            LOAD_WEIGHT         NUMBER(8,2)         DEFAULT 0,
            PALLET_QTY          NUMBER(6,1)         DEFAULT 0,
            CARGO_LENGTH        NUMBER(6,2)         DEFAULT 0,
            CARGO_WIDTH         NUMBER(6,2)         DEFAULT 0,
            CARGO_HEIGHT        NUMBER(6,2)         DEFAULT 0,
            FLOOR_TYPE          VARCHAR2(10  CHAR) DEFAULT '' '',
            USE_YN              VARCHAR2(1   CHAR) DEFAULT ''Y'',
            OPERABLE_YN         VARCHAR2(1   CHAR) DEFAULT ''Y'',
            DLV_TIME_FROM       VARCHAR2(4   CHAR) DEFAULT '' '',
            DLV_TIME_TO         VARCHAR2(4   CHAR) DEFAULT '' '',
            VEHICLE_YEAR        VARCHAR2(4   CHAR) DEFAULT '' '',
            DELIVERY_CUSTOMER_1 VARCHAR2(10  CHAR) DEFAULT '' '',
            DELIVERY_CUSTOMER_2 VARCHAR2(10  CHAR) DEFAULT '' '',
            DEL_YN              VARCHAR2(1   CHAR) DEFAULT ''N'',
            CREDAT              VARCHAR2(8   CHAR) DEFAULT '' '',
            CRETIM              VARCHAR2(6   CHAR) DEFAULT '' '',
            CREUSR              VARCHAR2(12  CHAR) DEFAULT '' '',
            LMODAT              VARCHAR2(8   CHAR) DEFAULT '' '',
            LMOTIM              VARCHAR2(6   CHAR) DEFAULT '' '',
            LMOUSR              VARCHAR2(12  CHAR) DEFAULT '' '',
            CONSTRAINT PK_VHCMA PRIMARY KEY (VEHICLE_NO, OWNRKY)
        )
    ';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

COMMENT ON TABLE  VHCMA                      IS '실차량 마스터';
COMMENT ON COLUMN VHCMA.VEHICLE_NO           IS '차량번호 (PK)';
COMMENT ON COLUMN VHCMA.OWNRKY               IS '소유회사';
COMMENT ON COLUMN VHCMA.SHIP_POINT           IS '출하지점';
COMMENT ON COLUMN VHCMA.PRODUCT_GROUP        IS '제품그룹';
COMMENT ON COLUMN VHCMA.DELIVERY_ZONE        IS '배송구역';
COMMENT ON COLUMN VHCMA.CARRIER              IS '운수사';
COMMENT ON COLUMN VHCMA.VEHICLE_TYPE         IS '차종';
COMMENT ON COLUMN VHCMA.VEHICLE_KIND         IS '차량종류';
COMMENT ON COLUMN VHCMA.VEHICLE_CLASS        IS '차량클래스';
COMMENT ON COLUMN VHCMA.DRIVER_NAME          IS '기사명';
COMMENT ON COLUMN VHCMA.CONTACT_NO           IS '연락처';
COMMENT ON COLUMN VHCMA.AXLE_TYPE            IS '축수구분';
COMMENT ON COLUMN VHCMA.LOAD_VOLUME          IS '적재용적(CBM)';
COMMENT ON COLUMN VHCMA.LOAD_WEIGHT          IS '적재중량(톤)';
COMMENT ON COLUMN VHCMA.PALLET_QTY           IS '파레트 수';
COMMENT ON COLUMN VHCMA.CARGO_LENGTH         IS '적재함 길이(m)';
COMMENT ON COLUMN VHCMA.CARGO_WIDTH          IS '적재함 폭(m)';
COMMENT ON COLUMN VHCMA.CARGO_HEIGHT         IS '적재함 높이(m)';
COMMENT ON COLUMN VHCMA.FLOOR_TYPE           IS '바닥타입';
COMMENT ON COLUMN VHCMA.USE_YN               IS '사용여부';
COMMENT ON COLUMN VHCMA.OPERABLE_YN          IS '운행가능여부';
COMMENT ON COLUMN VHCMA.DLV_TIME_FROM        IS '배송가능시간From';
COMMENT ON COLUMN VHCMA.DLV_TIME_TO          IS '배송가능시간To';
COMMENT ON COLUMN VHCMA.VEHICLE_YEAR         IS '연식';
COMMENT ON COLUMN VHCMA.DELIVERY_CUSTOMER_1  IS '전담납품처1';
COMMENT ON COLUMN VHCMA.DELIVERY_CUSTOMER_2  IS '전담납품처2';
COMMENT ON COLUMN VHCMA.DEL_YN               IS '삭제여부';
COMMENT ON COLUMN VHCMA.CREDAT               IS '생성일자';
COMMENT ON COLUMN VHCMA.CRETIM               IS '생성시간';
COMMENT ON COLUMN VHCMA.CREUSR               IS '생성자';
COMMENT ON COLUMN VHCMA.LMODAT               IS '수정일자';
COMMENT ON COLUMN VHCMA.LMOTIM               IS '수정시간';
COMMENT ON COLUMN VHCMA.LMOUSR               IS '수정자';
