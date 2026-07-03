-- ============================================================
-- module-vehicle: DDL
-- ============================================================

CREATE TABLE IF NOT EXISTS ds_vehicle (
    CARCLASS_CD     VARCHAR(20)   NOT NULL                COMMENT '차종코드 (PK, 예: Z010)',
    CARTYPE         VARCHAR(50)                           COMMENT '차종명 (예: 1톤)',
    LENGTH_M        DECIMAL(6,2)                          COMMENT '차량 길이(m)',
    WIDTH_M         VARCHAR(20)                           COMMENT '차량 너비(m) – 범위 가능 예: 1.8~2.1',
    HEIGHT_M        DECIMAL(6,2)                          COMMENT '차량 높이(m)',
    LOAD_TON        DECIMAL(6,2)                          COMMENT '적재가능 중량(ton)',
    SORT_SEQ        INT           DEFAULT 0               COMMENT '정렬순서',
    PALLET_HEIGHT_M DECIMAL(6,3)  DEFAULT 0               COMMENT '팔레트 높이(m)',
    PALLET_CNT      INT                                   COMMENT '팔레트 수',
    LONG_AXIS_YN    VARCHAR(1)    DEFAULT 'N'             COMMENT '장축 여부 (Y/N)',
    INCH12_LT300    INT                                   COMMENT '12인치 LT300 최대 롤 수',
    INCH12_GE300    INT                                   COMMENT '12인치 GE300 최대 롤 수',
    INCH3_LT300     INT                                   COMMENT '3인치 LT300 최대 롤 수',
    INCH3_GE300     INT                                   COMMENT '3인치 GE300 최대 롤 수',
    DEFAULT_VEH_CNT INT                                   COMMENT '기본 배차 대수',
    UPDDAT          VARCHAR(8)                            COMMENT '수정일자',
    UPDUSR          VARCHAR(20)                           COMMENT '수정자',
    PRIMARY KEY (CARCLASS_CD),
    INDEX IDX_DS_VEHICLE_CARTYPE (CARTYPE),
    INDEX IDX_DS_VEHICLE_SORT    (SORT_SEQ)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='차량 제원 마스터';


CREATE TABLE IF NOT EXISTS vhcma (
    VHC_ID              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '차량 ID (PK)',
    VEHICLE_NO          VARCHAR(20)   NOT NULL                COMMENT '차량번호',
    OWNRKY              VARCHAR(10)   DEFAULT 'KN'            COMMENT '사업주 코드',
    SHIP_POINT          VARCHAR(10)                           COMMENT '출하지점',
    PRODUCT_GROUP       VARCHAR(10)                           COMMENT '제품군',
    DELIVERY_ZONE       VARCHAR(20)                           COMMENT '배송권역',
    CARRIER             VARCHAR(100)                          COMMENT '운송사',
    VEHICLE_TYPE        VARCHAR(20)                           COMMENT '차량유형',
    VEHICLE_KIND        VARCHAR(20)                           COMMENT '차량종류',
    VEHICLE_CLASS       VARCHAR(20)                           COMMENT '차량등급',
    CARTYPE             VARCHAR(50)                           COMMENT '차종명',
    CARCLASS_CD         VARCHAR(20)                           COMMENT '차종코드',
    DRIVER_NAME         VARCHAR(50)                           COMMENT '운전자명',
    CONTACT_NO          VARCHAR(20)                           COMMENT '연락처',
    PALLET_QTY          INT                                   COMMENT '팔레트 수량',
    FLOOR_TYPE          VARCHAR(10)                           COMMENT '바닥 유형',
    USE_YN              VARCHAR(1)    DEFAULT 'Y'             COMMENT '사용 여부',
    OPERABLE_YN         VARCHAR(1)    DEFAULT 'Y'             COMMENT '운행 가능 여부',
    FIX_YN              VARCHAR(1)    DEFAULT 'N'             COMMENT '고정 차량 여부',
    DEL_YN              VARCHAR(1)    DEFAULT 'N'             COMMENT '삭제 여부',
    DLV_TIME_FROM       VARCHAR(6)                            COMMENT '배송 시작 시간',
    DLV_TIME_TO         VARCHAR(6)                            COMMENT '배송 종료 시간',
    VEHICLE_YEAR        VARCHAR(4)                            COMMENT '연식',
    DELIVERY_CUSTOMER_1 VARCHAR(20)                           COMMENT '전담 납품처 1',
    DELIVERY_CUSTOMER_2 VARCHAR(20)                           COMMENT '전담 납품처 2',
    CREDAT              VARCHAR(8)                            COMMENT '생성일자',
    CRETIM              VARCHAR(6)                            COMMENT '생성시각',
    CREUSR              VARCHAR(20)                           COMMENT '생성자',
    LMODAT              VARCHAR(8)                            COMMENT '수정일자',
    LMOTIM              VARCHAR(6)                            COMMENT '수정시각',
    LMOUSR              VARCHAR(20)                           COMMENT '수정자',

    PRIMARY KEY (VHC_ID),
    CONSTRAINT UK_VHCMA UNIQUE (VEHICLE_NO, OWNRKY),
    INDEX IDX_VHCMA_SHIP_POINT (SHIP_POINT),
    INDEX IDX_VHCMA_DEL_YN     (DEL_YN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='차량 마스터';
