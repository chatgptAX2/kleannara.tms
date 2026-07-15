-- ============================================================
--  module-vehicle: DDL (MariaDB/MySQL 호환)
--  생성일: 2026-07-04  /  출처: wms-viewer/wms.db 현황 기반
-- ============================================================

-- ── 배차 차량 클래스 마스터 ───────────────────────────────────
CREATE TABLE IF NOT EXISTS DS_VEHICLE (
    CARCLASS_CD     VARCHAR(20)  NOT NULL              COMMENT '차량클래스코드 (PK)',
    CARTYPE         VARCHAR(50)  DEFAULT ' '          COMMENT '차종명',
    LENGTH_M        DECIMAL(6,2) DEFAULT 0             COMMENT '적재함 길이(m)',
    WIDTH_M         VARCHAR(10)  DEFAULT ' '          COMMENT '적재함 폭(m)',
    HEIGHT_M        DECIMAL(6,2) DEFAULT 0             COMMENT '적재함 높이(m)',
    LOAD_TON        DECIMAL(6,2) DEFAULT 0             COMMENT '최대 적재톤수',
    SORT_SEQ        INT          DEFAULT 0             COMMENT '정렬순서',
    UPDDAT          VARCHAR(8)   DEFAULT ''           COMMENT '수정일자',
    UPDUSR          VARCHAR(20)  DEFAULT ''           COMMENT '수정자',
    PALLET_HEIGHT_M DECIMAL(6,3) DEFAULT 0             COMMENT '파레트 높이(m)',
    INCH12_LT300    INT                                COMMENT '12인치/평량300미만 1단기준수',
    INCH12_GE300    INT                                COMMENT '12인치/평량300이상 1단기준수',
    INCH3_LT300     INT                                COMMENT '3인치/평량300미만 1단기준수',
    INCH3_GE300     INT                                COMMENT '3인치/평량300이상 1단기준수',
    DEFAULT_VEH_CNT INT                                COMMENT '기본 배차 대수',
    PALLET_CNT      INT                                COMMENT '파레트 최대 수량',
    LONG_AXIS_YN    VARCHAR(1)   DEFAULT 'N'          COMMENT '장축 여부',
    PRIMARY KEY (CARCLASS_CD)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차 차량 클래스 마스터';


-- ── 12인치 롤 1단 기준수 (차종×평량구간) ─────────────────────
CREATE TABLE IF NOT EXISTS DS_INCH12 (
    CARTYPE   VARCHAR(50)  NOT NULL              COMMENT '차종명 (FK)',
    GRM_COND  VARCHAR(20)  NOT NULL              COMMENT '평량조건 (LT300/GE300)',
    MAX_COUNT INT          DEFAULT 0             COMMENT '1단 최대 적재수',
    SORT_SEQ  INT          DEFAULT 0             COMMENT '정렬순서',
    UPDDAT    VARCHAR(8)   DEFAULT ''           COMMENT '수정일자',
    PRIMARY KEY (CARTYPE, GRM_COND)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='12인치 롤 1단 기준수';


-- ── 3인치 롤 1단 기준수 (차종×평량구간) ──────────────────────
CREATE TABLE IF NOT EXISTS DS_INCH3 (
    CARTYPE   VARCHAR(50)  NOT NULL              COMMENT '차종명 (FK)',
    GRM_COND  VARCHAR(20)  NOT NULL              COMMENT '평량조건 (LT300/GE300)',
    MAX_COUNT INT          DEFAULT 0             COMMENT '1단 최대 적재수',
    SORT_SEQ  INT          DEFAULT 0             COMMENT '정렬순서',
    UPDDAT    VARCHAR(8)   DEFAULT ''           COMMENT '수정일자',
    PRIMARY KEY (CARTYPE, GRM_COND)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='3인치 롤 1단 기준수';


-- ── 실차량 마스터 ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS VHCMA (
    VEHICLE_NO          VARCHAR(20)  NOT NULL              COMMENT '차량번호 (PK)',
    OWNRKY              VARCHAR(10)  NOT NULL DEFAULT ' ' COMMENT '소유회사',
    SHIP_POINT          VARCHAR(10)  DEFAULT ' '          COMMENT '출하지점',
    PRODUCT_GROUP       VARCHAR(20)  DEFAULT ' '          COMMENT '제품그룹',
    DELIVERY_ZONE       VARCHAR(20)  DEFAULT ' '          COMMENT '배송구역',
    CARRIER             VARCHAR(50)  DEFAULT ' '          COMMENT '운수사',
    VEHICLE_TYPE        VARCHAR(50)  DEFAULT ' '          COMMENT '차종',
    VEHICLE_KIND        VARCHAR(30)  DEFAULT ' '          COMMENT '차량종류',
    VEHICLE_CLASS       VARCHAR(20)  DEFAULT ' '          COMMENT '차량클래스',
    DRIVER_NAME         VARCHAR(50)  DEFAULT ' '          COMMENT '기사명',
    CONTACT_NO          VARCHAR(20)  DEFAULT ' '          COMMENT '연락처',
    AXLE_TYPE           VARCHAR(10)  DEFAULT ' '          COMMENT '축수구분',
    LOAD_VOLUME         DECIMAL(8,2) DEFAULT 0             COMMENT '적재용적(CBM)',
    LOAD_WEIGHT         DECIMAL(8,2) DEFAULT 0             COMMENT '적재중량(톤)',
    PALLET_QTY          DECIMAL(6,1) DEFAULT 0             COMMENT '파레트 수',
    CARGO_LENGTH        DECIMAL(6,2) DEFAULT 0             COMMENT '적재함 길이(m)',
    CARGO_WIDTH         DECIMAL(6,2) DEFAULT 0             COMMENT '적재함 폭(m)',
    CARGO_HEIGHT        DECIMAL(6,2) DEFAULT 0             COMMENT '적재함 높이(m)',
    FLOOR_TYPE          VARCHAR(10)  DEFAULT ' '          COMMENT '바닥타입',
    USE_YN              VARCHAR(1)   DEFAULT 'Y'          COMMENT '사용여부',
    OPERABLE_YN         VARCHAR(1)   DEFAULT 'Y'          COMMENT '운행가능여부',
    DLV_TIME_FROM       VARCHAR(4)   DEFAULT ' '          COMMENT '배송가능시간From',
    DLV_TIME_TO         VARCHAR(4)   DEFAULT ' '          COMMENT '배송가능시간To',
    VEHICLE_YEAR        VARCHAR(4)   DEFAULT ' '          COMMENT '연식',
    DELIVERY_CUSTOMER_1 VARCHAR(10)  DEFAULT ' '          COMMENT '전담납품처1',
    DELIVERY_CUSTOMER_2 VARCHAR(10)  DEFAULT ' '          COMMENT '전담납품처2',
    DEL_YN              VARCHAR(1)   DEFAULT 'N'          COMMENT '삭제여부',
    CREDAT              VARCHAR(8)   DEFAULT ' '          COMMENT '생성일자',
    CRETIM              VARCHAR(6)   DEFAULT ' '          COMMENT '생성시간',
    CREUSR              VARCHAR(12)  DEFAULT ' '          COMMENT '생성자',
    LMODAT              VARCHAR(8)   DEFAULT ' '          COMMENT '수정일자',
    LMOTIM              VARCHAR(6)   DEFAULT ' '          COMMENT '수정시간',
    LMOUSR              VARCHAR(12)  DEFAULT ' '          COMMENT '수정자',
    PRIMARY KEY (VEHICLE_NO, OWNRKY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='실차량 마스터';
