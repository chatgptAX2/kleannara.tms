-- ============================================================
-- TMS DB (MariaDB) — 전체 테이블 통합 DDL + 시드 데이터
-- 대상 DB  : MariaDB @ 10.2.14.247:3306  Schema: intergration
-- 생성일   : 2026-07-09
-- 용도     : TMS 자체 관리 테이블 이관·백업·신규 서버 초기화
--
-- [실행 방법]
--   mysql -h 10.2.14.247 -u tmsuser -p intergration < /data/tms/source/sql/tms-all-tables.sql
--
-- [WMS Oracle 테이블 (미포함 — 별도 관리)]
--   CMCDM, CMCDV, WAHMA, SKUMA, MEASI, BZPTN,
--   SHPDH, SHPDI, IFWMS113, RECDI
--   → Oracle DB: 10.2.14.190:1522 SID=KNMESWMS  계정: KNRATMS
--
-- [실행 순서]
--   1. DDL (CREATE TABLE IF NOT EXISTS) — 1~8 섹션
--   2. 시드 데이터  (INSERT IGNORE)     — 9 섹션
-- ============================================================

SET NAMES utf8mb4;
SET foreign_key_checks = 0;
SET sql_mode = 'NO_AUTO_VALUE_ON_ZERO';

-- ============================================================
-- 1. 배차전략 — DS_DISPATCH_OBJECTIVE (배차 목적식)
-- ============================================================
CREATE TABLE IF NOT EXISTS DS_DISPATCH_OBJECTIVE (
    OBJ_ID     BIGINT        NOT NULL AUTO_INCREMENT COMMENT '목적식 ID (PK)',
    OBJ_CODE   VARCHAR(30)   NOT NULL                COMMENT '목적식 코드 (MIN_VEHICLES 등)',
    OBJ_NM     VARCHAR(100)                          COMMENT '표시명',
    OBJ_ICON   VARCHAR(10)                           COMMENT '이모지 아이콘',
    OBJ_ALGO   VARCHAR(50)                           COMMENT '알고리즘 코드',
    OBJ_DESC   VARCHAR(200)                          COMMENT '설명',
    SORT_SEQ   INT           DEFAULT 0               COMMENT '정렬순서',
    ACTIVE_YN  VARCHAR(1)    DEFAULT 'Y'             COMMENT '활성 여부 (단일 Y 보장)',
    CREDAT     VARCHAR(8)                            COMMENT '생성일자 (YYYYMMDD)',
    LMODAT     VARCHAR(8)                            COMMENT '수정일자 (YYYYMMDD)',
    PRIMARY KEY (OBJ_ID),
    CONSTRAINT UK_DS_DISPATCH_OBJ UNIQUE (OBJ_CODE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차 목적식';

-- ============================================================
-- 2. 배차전략 — DS_DISPATCH_PROFILE (배차제약 프로파일)
-- ============================================================
CREATE TABLE IF NOT EXISTS DS_DISPATCH_PROFILE (
    PROFILE_ID   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '프로파일 ID (PK)',
    PROFILE_NM   VARCHAR(100)  NOT NULL                COMMENT '프로파일명',
    PROFILE_DESC VARCHAR(200)                          COMMENT '설명',
    ACTIVE_YN    VARCHAR(1)    DEFAULT 'Y'             COMMENT '활성 여부',
    SET_ID       INT                                   COMMENT '연결된 const-set ID',
    OBJECTIVE    VARCHAR(50)   DEFAULT 'MIN_VEHICLES'  COMMENT '목적식 코드',
    NOTE         VARCHAR(200)                          COMMENT '비고',
    CREDAT       VARCHAR(8)                            COMMENT '생성일자 (YYYYMMDD)',
    LMODAT       VARCHAR(8)                            COMMENT '수정일자 (YYYYMMDD)',
    PRIMARY KEY (PROFILE_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차제약 프로파일';

-- OBJECTIVE, NOTE 컬럼이 없는 기존 테이블 대응
ALTER TABLE DS_DISPATCH_PROFILE
    ADD COLUMN IF NOT EXISTS OBJECTIVE VARCHAR(50) DEFAULT 'MIN_VEHICLES' COMMENT '목적식 코드',
    ADD COLUMN IF NOT EXISTS NOTE      VARCHAR(200)                        COMMENT '비고';

-- ============================================================
-- 3. 배차전략 — DS_DISPATCH_CONSTRAINT (배차제약 상세)
-- ============================================================
CREATE TABLE IF NOT EXISTS DS_DISPATCH_CONSTRAINT (
    CONST_ID     BIGINT        NOT NULL AUTO_INCREMENT COMMENT '제약 ID (PK)',
    PROFILE_ID   BIGINT        NOT NULL                COMMENT '프로파일 ID (FK)',
    CONST_TYPE   VARCHAR(30)                           COMMENT '제약 유형',
    CONST_KEY    VARCHAR(50)                           COMMENT '제약 키',
    CONST_VAL    VARCHAR(200)                          COMMENT '제약 값',
    REMARK       VARCHAR(200)                          COMMENT '비고',
    CREDAT       VARCHAR(8)                            COMMENT '생성일자 (YYYYMMDD)',
    LMODAT       VARCHAR(8)                            COMMENT '수정일자 (YYYYMMDD)',
    PRIMARY KEY (CONST_ID),
    INDEX IDX_DS_DISPATCH_CONST_PROFILE (PROFILE_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차제약 상세';

-- ============================================================
-- 4. 배차전략 — DS_DISPATCH_CONST_SET (배차제약 SET)
-- ============================================================
CREATE TABLE IF NOT EXISTS DS_DISPATCH_CONST_SET (
    SET_ID     INT           NOT NULL AUTO_INCREMENT COMMENT 'SET ID (PK)',
    SET_NM     VARCHAR(100)                          COMMENT 'SET명',
    SET_DESC   VARCHAR(200)                          COMMENT '설명',
    ACTIVE_YN  VARCHAR(1)    DEFAULT 'Y'             COMMENT '활성 여부',
    CREDAT     VARCHAR(8)                            COMMENT '생성일자 (YYYYMMDD)',
    LMODAT     VARCHAR(8)                            COMMENT '수정일자 (YYYYMMDD)',
    PRIMARY KEY (SET_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차제약 SET';

-- ============================================================
-- 5. 배차전략 — DS_DISPATCH_CONST_SET_ITEM (Flask 실제 스키마)
-- ============================================================
-- 주의: 기존 ds_dispatch_const_set_item(ITEM_KEY/ITEM_VAL/ITEM_TYPE/REMARK) 구조와
--       Flask 실제 스키마(CONST_ID/ACTIVE_YN/PARAM_VALUE)가 다름 → DROP 후 재생성
DROP TABLE IF EXISTS DS_DISPATCH_CONST_SET_ITEM;
CREATE TABLE IF NOT EXISTS DS_DISPATCH_CONST_SET_ITEM (
    ITEM_ID     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '아이템 ID (PK)',
    SET_ID      INT          NOT NULL                COMMENT 'SET ID (FK)',
    CONST_ID    BIGINT       NOT NULL                COMMENT '제약 ID (FK → DS_DISPATCH_CONST)',
    ACTIVE_YN   VARCHAR(1)   DEFAULT 'Y'             COMMENT '활성 여부',
    PARAM_VALUE VARCHAR(200)                         COMMENT '파라미터 오버라이드 값',
    PRIMARY KEY (ITEM_ID),
    UNIQUE KEY UK_DS_CONST_SET_ITEM (SET_ID, CONST_ID),
    INDEX IDX_DS_CONST_SET_ITEM (SET_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차제약 SET 아이템 (Flask 실제 스키마)';

-- ============================================================
-- 6. 배차전략 — DS_DISPATCH_CONST (Flask 실제 배차제약 상세)
-- ============================================================
CREATE TABLE IF NOT EXISTS DS_DISPATCH_CONST (
    CONST_ID    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '제약 ID (PK)',
    PROFILE_ID  BIGINT       NOT NULL                COMMENT '프로파일 ID (FK)',
    CONST_TYPE  VARCHAR(30)                          COMMENT '제약 유형 (GLOBAL/VEHICLE/PARTNER/CARGO/COST/CARTYPE)',
    CONST_KEY   VARCHAR(50)                          COMMENT '제약 키',
    CONST_VALUE VARCHAR(200)                         COMMENT '제약 값',
    CONST_OP    VARCHAR(10)  DEFAULT '<='            COMMENT '비교 연산자',
    TARGET_ID   VARCHAR(50)                          COMMENT '대상 ID (차종코드/납품처코드 등)',
    TARGET_NM   VARCHAR(100)                         COMMENT '대상명',
    ACTIVE_YN   VARCHAR(1)   DEFAULT 'Y'             COMMENT '활성 여부',
    NOTE        VARCHAR(200)                         COMMENT '비고',
    SORT_SEQ    INT          DEFAULT 0               COMMENT '정렬순서',
    CREDAT      VARCHAR(8)                           COMMENT '생성일자 (YYYYMMDD)',
    LMODAT      VARCHAR(8)                           COMMENT '수정일자 (YYYYMMDD)',
    PRIMARY KEY (CONST_ID),
    INDEX IDX_DS_DISPATCH_CONST_PROFILE (PROFILE_ID),
    INDEX IDX_DS_DISPATCH_CONST_TYPE (CONST_TYPE, CONST_KEY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차제약 상세 (Flask 실제 스키마)';

-- ============================================================
-- 7. 배차전략 — DS_INCH12 / DS_INCH3 (12인치/3인치 롤 적재기준)
-- ============================================================
CREATE TABLE IF NOT EXISTS DS_INCH12 (
    ID          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID (PK)',
    CARTYPE     VARCHAR(50)  NOT NULL                COMMENT '차종명',
    GRM         INT          NOT NULL                COMMENT '평량(g/m²)',
    MAX_ROLLS   INT          DEFAULT 0               COMMENT '최대 롤 수',
    CREDAT      VARCHAR(8)                           COMMENT '생성일자 (YYYYMMDD)',
    LMODAT      VARCHAR(8)                           COMMENT '수정일자 (YYYYMMDD)',
    PRIMARY KEY (ID),
    UNIQUE KEY UK_DS_INCH12 (CARTYPE, GRM)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차전략 12인치 롤 적재기준';

CREATE TABLE IF NOT EXISTS DS_INCH3 (
    ID          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID (PK)',
    CARTYPE     VARCHAR(50)  NOT NULL                COMMENT '차종명',
    GRM         INT          NOT NULL                COMMENT '평량(g/m²)',
    MAX_ROLLS   INT          DEFAULT 0               COMMENT '최대 롤 수',
    CREDAT      VARCHAR(8)                           COMMENT '생성일자 (YYYYMMDD)',
    LMODAT      VARCHAR(8)                           COMMENT '수정일자 (YYYYMMDD)',
    PRIMARY KEY (ID),
    UNIQUE KEY UK_DS_INCH3 (CARTYPE, GRM)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차전략 3인치 롤 적재기준';

-- ============================================================
-- 8. 납품처 TMS 상세 — BZPTN_DETAIL (TMS 추가정보, BZPTN 원장은 WMS Oracle)
-- ============================================================
CREATE TABLE IF NOT EXISTS BZPTN_DETAIL (
    DETAIL_ID       BIGINT        NOT NULL AUTO_INCREMENT COMMENT '상세 ID (PK)',
    PTNRKY          VARCHAR(20)   NOT NULL                COMMENT '납품처코드',
    PTNRTY          VARCHAR(5)    DEFAULT 'CT'            COMMENT '파트너유형',
    OWNRKY          VARCHAR(10)   DEFAULT 'KN'            COMMENT '사업주',
    WAREKY          VARCHAR(10)                           COMMENT '출하창고코드',
    ROUTE_CD        VARCHAR(20)                           COMMENT '루트코드',
    ITEM_GROUP      VARCHAR(10)                           COMMENT '제품군',
    AREA_CD         VARCHAR(20)                           COMMENT '지역코드',
    UNLOAD_TIME     INT                                   COMMENT '하차소요시간(분)',
    MAX_HEIGHT      DECIMAL(6,2)                          COMMENT '최대적재높이(m)',
    AUTO_ALLOC_YN   VARCHAR(1)                            COMMENT '자동배차여부',
    FORKLIFT_YN     VARCHAR(1)                            COMMENT '지게차여부',
    INB_TIME_FROM1  VARCHAR(6)                            COMMENT '입고시작시간',
    INB_TIME_TO1    VARCHAR(6)                            COMMENT '입고종료시간',
    MAX_BOX_QTY     INT                                   COMMENT '최대묶음수',
    DEADLINE_TIME   VARCHAR(6)                            COMMENT '배차마감시간',
    MAX_TON         DECIMAL(6,2)                          COMMENT '최대적재중량(ton)',
    HANDWORK_YN     VARCHAR(1)                            COMMENT '수작업여부',
    AUTO_PLT        VARCHAR(10)                           COMMENT '자동팔레트',
    SINGLE_ITEM_YN  VARCHAR(1)                            COMMENT '단품배차여부',
    NY_TYPE         VARCHAR(10)                           COMMENT 'NY유형',
    SINGLE_HEIGHT   DECIMAL(6,2)                          COMMENT '단품적재높이',
    DYNAMIC_YN      VARCHAR(1)                            COMMENT '다이나믹여부',
    LTL_YN          VARCHAR(1)                            COMMENT 'LTL여부',
    PRIORITY_YN     VARCHAR(1)                            COMMENT '우선배차여부',
    MIN_QTSIWH      DECIMAL(12,4)                         COMMENT '최소배차수량',
    LATITUDE        DECIMAL(10,7)                         COMMENT '위도',
    LONGITUDE       DECIMAL(10,7)                         COMMENT '경도',
    DEL_YN          VARCHAR(1)    DEFAULT 'N'             COMMENT '삭제여부',
    CREDAT          VARCHAR(8)                            COMMENT '생성일자 (YYYYMMDD)',
    CRETIM          VARCHAR(6)                            COMMENT '생성시각 (HHmmss)',
    CREUSR          VARCHAR(20)                           COMMENT '생성자',
    LMODAT          VARCHAR(8)                            COMMENT '수정일자 (YYYYMMDD)',
    LMOTIM          VARCHAR(6)                            COMMENT '수정시각 (HHmmss)',
    LMOUSR          VARCHAR(20)                           COMMENT '수정자',
    PRIMARY KEY (DETAIL_ID),
    CONSTRAINT UK_BZPTN_DETAIL UNIQUE (PTNRKY, PTNRTY, OWNRKY),
    INDEX IDX_BZPTN_DETAIL_WAREKY (WAREKY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='납품처 TMS 상세 (BZPTN 원장은 WMS Oracle)';

-- ============================================================
-- 9. 운송경로비용 — ROUTE_COST
-- ============================================================
CREATE TABLE IF NOT EXISTS ROUTE_COST (
    COST_ID   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '비용 ID (PK)',
    WAREKY    VARCHAR(10)                           COMMENT '출하창고코드',
    PTNRKY    VARCHAR(20)                           COMMENT '납품처코드',
    CARTYPE   VARCHAR(50)                           COMMENT '차종명',
    COST_AMT  DECIMAL(12,2)                         COMMENT '운송비(원)',
    DIST_KM   DECIMAL(8,2)                          COMMENT '거리(km)',
    EFF_DATE  VARCHAR(8)                            COMMENT '적용일자 (YYYYMMDD)',
    EXP_DATE  VARCHAR(8)                            COMMENT '종료일자 (YYYYMMDD)',
    UPDDAT    VARCHAR(8)                            COMMENT '수정일자 (YYYYMMDD)',
    UPDUSR    VARCHAR(20)                           COMMENT '수정자',
    PRIMARY KEY (COST_ID),
    INDEX IDX_ROUTE_COST_PTNRKY (WAREKY, PTNRKY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='경로별 운송비 마스터';

-- ============================================================
-- 10. 차량 제원 마스터 — DS_VEHICLE
-- ============================================================
CREATE TABLE IF NOT EXISTS DS_VEHICLE (
    CARCLASS_CD     VARCHAR(20)   NOT NULL                COMMENT '차종코드 (PK)',
    CARTYPE         VARCHAR(50)                           COMMENT '차종명',
    LENGTH_M        DECIMAL(6,2)                          COMMENT '차량 길이(m)',
    WIDTH_M         VARCHAR(20)                           COMMENT '차량 너비(m)',
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
    UPDDAT          VARCHAR(8)                            COMMENT '수정일자 (YYYYMMDD)',
    UPDUSR          VARCHAR(20)                           COMMENT '수정자',
    PRIMARY KEY (CARCLASS_CD),
    INDEX IDX_DS_VEHICLE_CARTYPE (CARTYPE),
    INDEX IDX_DS_VEHICLE_SORT    (SORT_SEQ)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='차량 제원 마스터';

-- ============================================================
-- 11. 차량 마스터 — VHCMA
-- ============================================================
CREATE TABLE IF NOT EXISTS VHCMA (
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
    CREDAT              VARCHAR(8)                            COMMENT '생성일자 (YYYYMMDD)',
    CRETIM              VARCHAR(6)                            COMMENT '생성시각 (HHmmss)',
    CREUSR              VARCHAR(20)                           COMMENT '생성자',
    LMODAT              VARCHAR(8)                            COMMENT '수정일자 (YYYYMMDD)',
    LMOTIM              VARCHAR(6)                            COMMENT '수정시각 (HHmmss)',
    LMOUSR              VARCHAR(20)                           COMMENT '수정자',
    PRIMARY KEY (VHC_ID),
    CONSTRAINT UK_VHCMA UNIQUE (VEHICLE_NO, OWNRKY),
    INDEX IDX_VHCMA_SHIP_POINT (SHIP_POINT),
    INDEX IDX_VHCMA_DEL_YN     (DEL_YN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='차량 마스터';

-- ============================================================
-- 12. PS배차 — PS_DISPATCH_H (헤더)
-- ============================================================
CREATE TABLE IF NOT EXISTS PS_DISPATCH_H (
    DISPATCH_NO  VARCHAR(20)   NOT NULL                COMMENT '배차번호 (PK)',
    DISPATCH_DT  VARCHAR(8)    NOT NULL                COMMENT '배차일자 (YYYYMMDD)',
    RQSHPD       VARCHAR(8)                            COMMENT '납품요청일 (YYYYMMDD)',
    DPTNKY       VARCHAR(20)                           COMMENT '납품처코드',
    DPTNM        VARCHAR(100)                          COMMENT '납품처명',
    CARTYPE      VARCHAR(50)                           COMMENT '차종명',
    STATUS       VARCHAR(10)   NOT NULL DEFAULT 'DRAFT' COMMENT '상태 (DRAFT/CONFIRMED/CANCELLED)',
    TOTAL_KG     DECIMAL(12,2)                         COMMENT '총 중량(KG)',
    TOTAL_CNT    INT                                   COMMENT '총 건수',
    NOTE         VARCHAR(200)                          COMMENT '비고',
    STKNUM       VARCHAR(20)                           COMMENT 'SAP 선적번호',
    CREDAT       VARCHAR(8)                            COMMENT '생성일자 (YYYYMMDD)',
    CREUSR       VARCHAR(20)                           COMMENT '생성자',
    LMODAT       VARCHAR(8)                            COMMENT '수정일자 (YYYYMMDD)',
    LMOUSR       VARCHAR(20)                           COMMENT '수정자',
    CREATED_AT   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    UPDATED_AT   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (DISPATCH_NO),
    INDEX IDX_PS_DISPATCH_H_RQSHPD (RQSHPD),
    INDEX IDX_PS_DISPATCH_H_DPTNKY (DPTNKY),
    INDEX IDX_PS_DISPATCH_H_STATUS (STATUS)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='PS배차 헤더';

-- ============================================================
-- 13. PS배차 — PS_DISPATCH_D (아이템)
-- ============================================================
CREATE TABLE IF NOT EXISTS PS_DISPATCH_D (
    ITEM_ID      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '아이템 ID (PK)',
    DISPATCH_NO  VARCHAR(20)   NOT NULL                COMMENT '배차번호 (FK → PS_DISPATCH_H)',
    SEQ          INT           NOT NULL                COMMENT '순번',
    SHPOKY       VARCHAR(20)   NOT NULL                COMMENT '납품문서번호',
    SHPOIT       VARCHAR(6)    NOT NULL                COMMENT '납품문서 라인',
    SKUKEY       VARCHAR(30)                           COMMENT '품목코드',
    DESC01       VARCHAR(200)                          COMMENT '품목명',
    QTSHPO       DECIMAL(12,4)                         COMMENT '출하수량',
    UOMKEY       VARCHAR(10)                           COMMENT '단위 (KG, R)',
    DPTNKY       VARCHAR(20)                           COMMENT '납품처코드',
    DPTNM        VARCHAR(100)                          COMMENT '납품처명',
    IS_SPLIT     TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '분할 여부 (0/1)',
    ORG_SHPOKY   VARCHAR(20)                           COMMENT '원본 납품문서번호 (분할 시)',
    ORG_SHPOIT   VARCHAR(6)                            COMMENT '원본 납품문서 라인 (분할 시)',
    GRSWGT       DECIMAL(12,4) DEFAULT 0               COMMENT '묶음당 중량(kg)',
    KG_WEIGHT    DECIMAL(12,4) DEFAULT 0               COMMENT 'KG 환산 중량',
    PRIMARY KEY (ITEM_ID),
    CONSTRAINT UK_PS_DISPATCH_D UNIQUE (DISPATCH_NO, SHPOKY, SHPOIT),
    CONSTRAINT FK_PS_DISPATCH_D_H FOREIGN KEY (DISPATCH_NO)
        REFERENCES PS_DISPATCH_H(DISPATCH_NO) ON DELETE CASCADE,
    INDEX IDX_PS_DISPATCH_D_SHPOKY (SHPOKY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='PS배차 아이템 (ps_dispatch_i 호환)';

-- ============================================================
-- 14. PS배차 — PS_DISPATCH_SPLIT (분할 상세)
-- ============================================================
CREATE TABLE IF NOT EXISTS PS_DISPATCH_SPLIT (
    SPLIT_ID    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '분할 ID (PK)',
    DISP_H_ID   BIGINT       NOT NULL                COMMENT '배차 헤더 ID (FK)',
    ORIG_ITEM   VARCHAR(30)                          COMMENT '원본 아이템 번호',
    SPLIT_SEQ   INT          DEFAULT 1               COMMENT '분할 순번',
    SKUKEY      VARCHAR(30)                          COMMENT 'SKU키',
    QTSHPO      DECIMAL(15,4)                        COMMENT '분할 수량',
    KG_WEIGHT   DECIMAL(15,4)                        COMMENT '분할 중량(kg)',
    NOTE        VARCHAR(200)                         COMMENT '비고',
    CREDAT      VARCHAR(8)                           COMMENT '생성일자 (YYYYMMDD)',
    CRETIM      VARCHAR(6)                           COMMENT '생성시간 (HHmmss)',
    PRIMARY KEY (SPLIT_ID),
    INDEX IDX_PS_DISPATCH_SPLIT_H (DISP_H_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='PS 배차 분할 상세';

-- ============================================================
-- 15. 서류관리 — DOC_FOLDER (서류 폴더)
-- ============================================================
CREATE TABLE IF NOT EXISTS DOC_FOLDER (
    FOLDER_ID   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '폴더 ID (PK)',
    FOLDER_NM   VARCHAR(200) NOT NULL                COMMENT '폴더명',
    PARENT_ID   BIGINT                               COMMENT '상위 폴더 ID (NULL=루트)',
    SORT_SEQ    INT          DEFAULT 0               COMMENT '정렬순서',
    CREDAT      VARCHAR(8)                           COMMENT '생성일자 (YYYYMMDD)',
    CRETIM      VARCHAR(6)                           COMMENT '생성시간 (HHmmss)',
    LMODAT      VARCHAR(8)                           COMMENT '수정일자 (YYYYMMDD)',
    LMOUSR      VARCHAR(50)                          COMMENT '수정자',
    DEL_YN      VARCHAR(1)   DEFAULT 'N'             COMMENT '삭제여부 (Y/N)',
    PRIMARY KEY (FOLDER_ID),
    INDEX IDX_DOC_FOLDER_PARENT (PARENT_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='서류 폴더';

-- ============================================================
-- 16. 서류관리 — DOC_FILE (서류 파일)
-- ============================================================
CREATE TABLE IF NOT EXISTS DOC_FILE (
    FILE_ID      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '파일 ID (PK)',
    FOLDER_ID    BIGINT        NOT NULL                COMMENT '폴더 ID (FK → DOC_FOLDER)',
    FILE_NM      VARCHAR(500)  NOT NULL                COMMENT '원본 파일명',
    FILE_PATH    VARCHAR(1000)                         COMMENT '서버 저장 경로',
    FILE_SIZE    BIGINT                                COMMENT '파일 크기 (bytes)',
    FILE_TYPE    VARCHAR(100)                          COMMENT 'MIME 타입',
    FILE_EXT     VARCHAR(20)                           COMMENT '파일 확장자',
    DOWNLOAD_CNT INT           DEFAULT 0               COMMENT '다운로드 횟수',
    NOTE         VARCHAR(500)                          COMMENT '비고',
    CREDAT       VARCHAR(8)                            COMMENT '생성일자 (YYYYMMDD)',
    CRETIM       VARCHAR(6)                            COMMENT '생성시간 (HHmmss)',
    CREUSR       VARCHAR(50)                           COMMENT '등록자',
    LMODAT       VARCHAR(8)                            COMMENT '수정일자 (YYYYMMDD)',
    LMOUSR       VARCHAR(50)                           COMMENT '수정자',
    DEL_YN       VARCHAR(1)    DEFAULT 'N'             COMMENT '삭제여부 (Y/N)',
    PRIMARY KEY (FILE_ID),
    INDEX IDX_DOC_FILE_FOLDER (FOLDER_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='서류 파일';

-- ============================================================
-- 17. 출고조회 보조 — SHIPMENT_FILTER_PRESET (즐겨찾기)
-- ============================================================
CREATE TABLE IF NOT EXISTS SHIPMENT_FILTER_PRESET (
    PRESET_ID    BIGINT        NOT NULL AUTO_INCREMENT      COMMENT '즐겨찾기 ID (PK)',
    USER_ID      VARCHAR(50)   NOT NULL                     COMMENT '사용자 ID',
    PRESET_NM    VARCHAR(100)  NOT NULL                     COMMENT '즐겨찾기 명칭',
    WAREKY       VARCHAR(10)                                COMMENT '창고코드',
    DATE_FROM    VARCHAR(8)                                 COMMENT '검색 시작일 (YYYYMMDD)',
    DATE_TO      VARCHAR(8)                                 COMMENT '검색 종료일 (YYYYMMDD)',
    STATDO       VARCHAR(10)                                COMMENT '출고상태코드',
    SKUG05       VARCHAR(10)                                COMMENT '품목그룹05',
    LOTA02_LIST  VARCHAR(500)                               COMMENT '플랜트 목록 (콤마 구분)',
    KEYWORD      VARCHAR(200)                               COMMENT '품목코드/품목명 검색어',
    IS_DEFAULT   TINYINT(1)    NOT NULL DEFAULT 0           COMMENT '기본 즐겨찾기 여부',
    CREATED_AT   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '생성일시',
    UPDATED_AT   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
                               ON UPDATE CURRENT_TIMESTAMP  COMMENT '수정일시',
    PRIMARY KEY (PRESET_ID),
    INDEX IDX_SHIPMENT_FILTER_PRESET_USER (USER_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='출고진행현황 검색 즐겨찾기';

-- ============================================================
-- 18. 출고조회 보조 — SHIPMENT_HISTORY (처리이력)
-- ============================================================
CREATE TABLE IF NOT EXISTS SHIPMENT_HISTORY (
    HIST_ID      BIGINT        NOT NULL AUTO_INCREMENT      COMMENT '이력 ID (PK)',
    SHPOKY       VARCHAR(20)   NOT NULL                     COMMENT '출고전표 키',
    SHPOIT       VARCHAR(6)    NOT NULL                     COMMENT '출고아이템번호',
    ACT_TYPE     VARCHAR(20)   NOT NULL                     COMMENT '변경 유형 (DISPATCH/CANCEL/STATUS_CHG 등)',
    OLD_VALUE    VARCHAR(200)                               COMMENT '변경 전 값',
    NEW_VALUE    VARCHAR(200)                               COMMENT '변경 후 값',
    REMARK       VARCHAR(500)                               COMMENT '비고',
    CREATED_AT   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '생성일시',
    CREATED_BY   VARCHAR(50)                                COMMENT '처리자',
    PRIMARY KEY (HIST_ID),
    INDEX IDX_SHIPMENT_HISTORY_SHPOKY (SHPOKY),
    INDEX IDX_SHIPMENT_HISTORY_CREATED (CREATED_AT)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='출고진행현황 처리 이력';

-- ============================================================
-- 9 (시드 데이터) — DS_DISPATCH_OBJECTIVE 기본 목적식 3종
-- ============================================================
INSERT IGNORE INTO DS_DISPATCH_OBJECTIVE
    (OBJ_CODE, OBJ_NM, OBJ_ICON, OBJ_ALGO, OBJ_DESC, SORT_SEQ, ACTIVE_YN, CREDAT, LMODAT)
VALUES
    ('MIN_VEHICLES', '차량 최소화',   '🚛', 'FFD BinPacking', '가능한 적은 차량으로 최대 적재 (FFD 알고리즘)', 10, 'Y', DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
    ('MAX_FILL',     '적재율 최대화', '📊', 'BFD BinPacking', '각 차량을 가장 꽉 채우는 방식 (BFD 알고리즘)', 20, 'N', DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
    ('MIN_COST',     '운송비 최소화', '💰', 'ROUTE_COST',     'ROUTE_COST 기반 최저비용 차종 선택',           30, 'N', DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d'));

SET foreign_key_checks = 1;

-- ============================================================
-- [완료] 전체 TMS 테이블 생성 완료
-- 생성 테이블 목록:
--   배차전략  : DS_DISPATCH_OBJECTIVE, DS_DISPATCH_PROFILE,
--              DS_DISPATCH_CONSTRAINT, DS_DISPATCH_CONST_SET,
--              DS_DISPATCH_CONST_SET_ITEM, DS_DISPATCH_CONST,
--              DS_INCH12, DS_INCH3
--   납품처    : BZPTN_DETAIL
--   운송경로  : ROUTE_COST
--   차량      : DS_VEHICLE, VHCMA
--   배차      : PS_DISPATCH_H, PS_DISPATCH_D, PS_DISPATCH_SPLIT
--   서류      : DOC_FOLDER, DOC_FILE
--   출고보조  : SHIPMENT_FILTER_PRESET, SHIPMENT_HISTORY
--
-- WMS Oracle 테이블 (별도):
--   CMCDM, CMCDV, WAHMA, SKUMA, MEASI, BZPTN,
--   SHPDH, SHPDI, IFWMS113, RECDI
--   → Oracle DB: 10.2.14.190:1522:KNMESWMS  계정: KNRATMS
-- ============================================================
