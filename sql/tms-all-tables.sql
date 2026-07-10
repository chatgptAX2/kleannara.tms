-- ============================================================
-- TMS DB (MariaDB / integration 스키마) — 전체 테이블 DDL
-- 대상 DB  : MariaDB @ 10.2.14.247:3306  Schema: integration
-- 갱신일   : 2026-07-10
-- 용도     : TMS 자체 관리 테이블 이관·백업·신규 서버 초기화
--
-- [실행 방법]
--   mysql -h 10.2.14.247 -u appuser -p integration < tms-all-tables.sql
--
-- [WMS Oracle 테이블 (미포함 — Oracle KNRAWMS 스키마 별도 관리)]
--   CMCDM, CMCDV, WAHMA, SKUMA, MEASI, BZPTN, BZPTN_DETAIL,
--   SHPDH, SHPDI, IFWMS113, RECDI
--   → Oracle DB : 10.2.14.190:1522 SID=KNMESWMS
--   → 접속 계정 : KNRATMS (스키마명 KNRAWMS 접두어로 조회)
--
-- [생성 순서]
--   1~8  : 배차전략 테이블 (DS_DISPATCH_*)
--   9    : [Oracle] 납품처 상세 (KNRAWMS.BZPTN_DETAIL — MariaDB 생성 불필요)
--   10   : 운송경로비용 (ROUTE_COST)
--   11   : 차량 제원 (DS_VEHICLE)
--   12   : 차량 마스터 (VHCMA)
--   13   : PS배차 헤더 (PS_DISPATCH_H)
--   14   : PS배차 아이템 (PS_DISPATCH_D)
--   15   : PS배차 분할 (PS_DISPATCH_SPLIT)
--   16   : 서류 폴더 (DOC_FOLDER)
--   17   : 서류 파일 (DOC_FILE)
-- ============================================================

SET NAMES utf8mb4;
SET foreign_key_checks = 0;
SET sql_mode = 'NO_AUTO_VALUE_ON_ZERO';

-- ============================================================
-- 1. DS_DISPATCH_OBJECTIVE — 배차 목적식
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
-- 2. DS_DISPATCH_PROFILE — 배차제약 프로파일
-- ============================================================
CREATE TABLE IF NOT EXISTS DS_DISPATCH_PROFILE (
    PROFILE_ID   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '프로파일 ID (PK)',
    PROFILE_NM   VARCHAR(100)  NOT NULL                COMMENT '프로파일명',
    PROFILE_DESC VARCHAR(200)                          COMMENT '설명',
    ACTIVE_YN    VARCHAR(1)    DEFAULT 'Y'             COMMENT '활성 여부',
    SET_ID       INT                                   COMMENT '연결된 const-set ID',
    OBJECTIVE    VARCHAR(50)   DEFAULT 'MIN_VEHICLES'  COMMENT '목적식 코드',
    NOTE         VARCHAR(200)                          COMMENT '비고',
    SORT_SEQ     INT           DEFAULT 0               COMMENT '정렬순서',
    CREDAT       VARCHAR(8)                            COMMENT '생성일자 (YYYYMMDD)',
    LMODAT       VARCHAR(8)                            COMMENT '수정일자 (YYYYMMDD)',
    PRIMARY KEY (PROFILE_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차제약 프로파일';

ALTER TABLE DS_DISPATCH_PROFILE
    ADD COLUMN IF NOT EXISTS OBJECTIVE VARCHAR(50) DEFAULT 'MIN_VEHICLES' COMMENT '목적식 코드',
    ADD COLUMN IF NOT EXISTS NOTE      VARCHAR(200)                        COMMENT '비고',
    ADD COLUMN IF NOT EXISTS SORT_SEQ  INT         DEFAULT 0               COMMENT '정렬순서';

-- ============================================================
-- 3. DS_DISPATCH_CONSTRAINT — 배차제약 상세 (구버전 호환용)
-- ============================================================
CREATE TABLE IF NOT EXISTS DS_DISPATCH_CONSTRAINT (
    CONSTRAINT_ID  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '제약 ID (PK)',
    PROFILE_ID     BIGINT        NOT NULL                COMMENT '프로파일 ID (FK)',
    OWNRKY         VARCHAR(20)                           COMMENT '사업주 코드',
    CONSTRAINT_TYPE VARCHAR(50)                          COMMENT '제약 유형',
    CONSTRAINT_KEY VARCHAR(100)                          COMMENT '제약 키',
    CONSTRAINT_VAL VARCHAR(200)                          COMMENT '제약 값',
    SORT_SEQ       INT                                   COMMENT '정렬순서',
    IS_ACTIVE      INT           DEFAULT 1               COMMENT '활성 여부 (1=Y)',
    CREATED_AT     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    UPDATED_AT     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (CONSTRAINT_ID),
    INDEX IDX_DS_DISPATCH_CONSTRAINT_PROFILE (PROFILE_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차제약 상세 (구버전 호환용)';

-- ============================================================
-- 4. DS_DISPATCH_CONST_SET — 배차제약 SET
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
-- 5. DS_DISPATCH_CONST_SET_ITEM — 배차제약 SET 아이템
-- ============================================================
DROP TABLE IF EXISTS DS_DISPATCH_CONST_SET_ITEM;
CREATE TABLE IF NOT EXISTS DS_DISPATCH_CONST_SET_ITEM (
    ITEM_ID     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '아이템 ID (PK)',
    SET_ID      INT          NOT NULL                COMMENT 'SET ID (FK → DS_DISPATCH_CONST_SET)',
    CONST_ID    BIGINT       NOT NULL                COMMENT '제약 ID (FK → DS_DISPATCH_CONST)',
    ACTIVE_YN   VARCHAR(1)   DEFAULT 'Y'             COMMENT '활성 여부',
    PARAM_VALUE VARCHAR(200)                         COMMENT '파라미터 오버라이드 값',
    PRIMARY KEY (ITEM_ID),
    UNIQUE KEY UK_DS_CONST_SET_ITEM (SET_ID, CONST_ID),
    INDEX IDX_DS_CONST_SET_ITEM (SET_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차제약 SET 아이템';

-- ============================================================
-- 6. DS_DISPATCH_CONST — 배차제약 상세 (현행 실운영 스키마)
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
    INDEX IDX_DS_DISPATCH_CONST_TYPE    (CONST_TYPE, CONST_KEY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차제약 상세 (현행 실운영)';

-- ============================================================
-- 7. DS_INCH12 / DS_INCH3 — 인치별 롤 적재 기준
-- ============================================================
CREATE TABLE IF NOT EXISTS DS_INCH12 (
    ID        BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID (PK)',
    CARTYPE   VARCHAR(50)  NOT NULL                COMMENT '차종명',
    GRM_COND  VARCHAR(20)                          COMMENT '평량 조건 코드 (LT300/GE300)',
    MAX_COUNT INT          DEFAULT 0               COMMENT '최대 롤 수',
    CREDAT    VARCHAR(8)                           COMMENT '생성일자 (YYYYMMDD)',
    LMODAT    VARCHAR(8)                           COMMENT '수정일자 (YYYYMMDD)',
    PRIMARY KEY (ID),
    UNIQUE KEY UK_DS_INCH12 (CARTYPE, GRM_COND)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차전략 12인치 롤 적재기준';

CREATE TABLE IF NOT EXISTS DS_INCH3 (
    ID        BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID (PK)',
    CARTYPE   VARCHAR(50)  NOT NULL                COMMENT '차종명',
    GRM_COND  VARCHAR(20)                          COMMENT '평량 조건 코드 (LT300/GE300)',
    MAX_COUNT INT          DEFAULT 0               COMMENT '최대 롤 수',
    CREDAT    VARCHAR(8)                           COMMENT '생성일자 (YYYYMMDD)',
    LMODAT    VARCHAR(8)                           COMMENT '수정일자 (YYYYMMDD)',
    PRIMARY KEY (ID),
    UNIQUE KEY UK_DS_INCH3 (CARTYPE, GRM_COND)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차전략 3인치 롤 적재기준';

-- ============================================================
-- 8. DS_VEHICLE — 차량 제원 마스터
-- ============================================================
CREATE TABLE IF NOT EXISTS DS_VEHICLE (
    CARCLASS_CD     VARCHAR(20)   NOT NULL                COMMENT '차종코드 (PK, 예: Z010)',
    CARTYPE         VARCHAR(50)                           COMMENT '차종명 (예: 1톤)',
    LENGTH_M        DECIMAL(6,2)                          COMMENT '차량 길이(m)',
    WIDTH_M         VARCHAR(20)                           COMMENT '차량 너비(m) — 범위 표현 가능 (예: 1.8~2.1)',
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
-- 9. BZPTN_DETAIL — [Oracle KNRAWMS 스키마 소속, MariaDB 생성 불필요]
-- ============================================================
-- !! BZPTN_DETAIL 은 Oracle KNRAWMS.BZPTN_DETAIL 테이블이므로
--    이 파일(MariaDB integration 스키마)에서 CREATE 하지 않습니다.
--    Java 코드에서는 반드시 KNRAWMS.BZPTN_DETAIL 로 접근하십시오.
--
--    DB  : Oracle 10.2.14.190:1522  SID=KNMESWMS
--    계정: KNRATMS  →  스키마: KNRAWMS
-- ============================================================

-- ============================================================
-- 10. ROUTE_COST — 경로별 운송비 마스터
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
    INDEX IDX_ROUTE_COST_PTNRKY (WAREKY, PTNRKY),
    INDEX IDX_ROUTE_COST_CARTYPE (CARTYPE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='경로별 운송비 마스터';

-- ============================================================
-- 11. VHCMA — 차량 마스터 (실차량 등록/관리)
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
    CARTYPE             VARCHAR(50)                           COMMENT '차종명 (DS_VEHICLE 연계)',
    CARCLASS_CD         VARCHAR(20)                           COMMENT '차종코드 (DS_VEHICLE PK)',
    DRIVER_NAME         VARCHAR(50)                           COMMENT '운전자명',
    CONTACT_NO          VARCHAR(20)                           COMMENT '연락처',
    PALLET_QTY          INT                                   COMMENT '팔레트 수량',
    FLOOR_TYPE          VARCHAR(10)                           COMMENT '바닥 유형',
    USE_YN              VARCHAR(1)    DEFAULT 'Y'             COMMENT '사용 여부',
    OPERABLE_YN         VARCHAR(1)    DEFAULT 'Y'             COMMENT '운행 가능 여부',
    FIX_YN              VARCHAR(1)    DEFAULT 'N'             COMMENT '고정 차량 여부',
    DEL_YN              VARCHAR(1)    DEFAULT 'N'             COMMENT '삭제 여부',
    DLV_TIME_FROM       VARCHAR(6)                            COMMENT '배송 시작 시간 (HHmmss)',
    DLV_TIME_TO         VARCHAR(6)                            COMMENT '배송 종료 시간 (HHmmss)',
    VEHICLE_YEAR        VARCHAR(4)                            COMMENT '연식 (YYYY)',
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
    INDEX IDX_VHCMA_CARTYPE    (CARTYPE),
    INDEX IDX_VHCMA_DEL_YN     (DEL_YN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='차량 마스터 (실차량 등록/관리)';

-- ============================================================
-- 12. PS_DISPATCH_H — PS 배차 헤더
-- ============================================================
CREATE TABLE IF NOT EXISTS PS_DISPATCH_H (
    DISP_H_ID   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '배차 헤더 ID (PK, AUTO)',
    DISPATCH_NO VARCHAR(20)   NOT NULL                COMMENT '배차번호 (예: 260509001T)',
    DISPATCH_DT VARCHAR(8)                            COMMENT '배차일자 (YYYYMMDD)',
    RQSHPD      VARCHAR(8)                            COMMENT '납품요청일 (YYYYMMDD)',
    DPTNKY      VARCHAR(20)                           COMMENT '납품처코드',
    DPTNM       VARCHAR(100)                          COMMENT '납품처명',
    CARTYPE     VARCHAR(50)                           COMMENT '차종명',
    CARCLASS_CD VARCHAR(20)                           COMMENT '차종코드',
    STATUS      VARCHAR(10)   NOT NULL DEFAULT 'DRAFT' COMMENT '상태 (DRAFT/CONFIRMED/SAP_CREATED/CANCELLED)',
    TKNUM       VARCHAR(20)                           COMMENT 'SAP 선적번호',
    SVBELN      VARCHAR(20)                           COMMENT 'SAP 납품문서번호',
    TOTAL_KG    DECIMAL(12,2)                         COMMENT '총 중량(KG)',
    TOTAL_CNT   INT                                   COMMENT '총 건수',
    DRIVER_NM   VARCHAR(50)                           COMMENT '운전자명',
    DRIVER_TEL  VARCHAR(20)                           COMMENT '운전자 연락처',
    VHCLNO      VARCHAR(20)                           COMMENT '차량번호',
    NOTE        VARCHAR(200)                          COMMENT '비고',
    CREDAT      VARCHAR(8)                            COMMENT '생성일자 (YYYYMMDD)',
    CREUSR      VARCHAR(20)                           COMMENT '생성자',
    LMODAT      VARCHAR(8)                            COMMENT '수정일자 (YYYYMMDD)',
    LMOUSR      VARCHAR(20)                           COMMENT '수정자',
    CREATED_AT  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP                    COMMENT '생성일시',
    UPDATED_AT  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',
    PRIMARY KEY (DISP_H_ID),
    UNIQUE KEY UK_PS_DISPATCH_H_NO (DISPATCH_NO),
    INDEX IDX_PS_DISPATCH_H_RQSHPD (RQSHPD),
    INDEX IDX_PS_DISPATCH_H_DPTNKY (DPTNKY),
    INDEX IDX_PS_DISPATCH_H_STATUS (STATUS)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='PS 배차 헤더';

-- ============================================================
-- 13. PS_DISPATCH_D — PS 배차 아이템
-- ============================================================
CREATE TABLE IF NOT EXISTS PS_DISPATCH_D (
    ITEM_ID     BIGINT        NOT NULL AUTO_INCREMENT COMMENT '아이템 ID (PK)',
    DISP_H_ID   BIGINT                                COMMENT '배차 헤더 ID (FK → PS_DISPATCH_H)',
    DISPATCH_NO VARCHAR(20)   NOT NULL                COMMENT '배차번호 (FK → PS_DISPATCH_H)',
    SEQ         INT           NOT NULL DEFAULT 0      COMMENT '순번',
    SHPOKY      VARCHAR(20)   NOT NULL                COMMENT '납품문서번호',
    SHPOIT      VARCHAR(6)    NOT NULL                COMMENT '납품문서 라인',
    SKUKEY      VARCHAR(30)                           COMMENT '품목코드',
    DESC01      VARCHAR(200)                          COMMENT '품목명',
    QTSHPO      DECIMAL(12,4)                         COMMENT '출하수량',
    UOMKEY      VARCHAR(10)                           COMMENT '단위 (KG, R)',
    DPTNKY      VARCHAR(20)                           COMMENT '납품처코드',
    DPTNM       VARCHAR(100)                          COMMENT '납품처명',
    IS_SPLIT    TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '분할 여부 (0/1)',
    ORG_SHPOKY  VARCHAR(20)                           COMMENT '원본 납품문서번호 (분할 시)',
    ORG_SHPOIT  VARCHAR(6)                            COMMENT '원본 납품문서 라인 (분할 시)',
    GRSWGT      DECIMAL(12,4) DEFAULT 0               COMMENT '묶음당 중량(kg)',
    KG_WEIGHT   DECIMAL(12,4) DEFAULT 0               COMMENT 'KG 환산 중량',
    SVBELN      VARCHAR(20)                           COMMENT 'SAP 납품문서번호',
    RQSHPD      VARCHAR(8)                            COMMENT '납품요청일 (YYYYMMDD)',
    PRIMARY KEY (ITEM_ID),
    CONSTRAINT UK_PS_DISPATCH_D UNIQUE (DISPATCH_NO, SHPOKY, SHPOIT),
    INDEX IDX_PS_DISPATCH_D_DISP_H  (DISP_H_ID),
    INDEX IDX_PS_DISPATCH_D_SHPOKY  (SHPOKY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='PS 배차 아이템';

-- ============================================================
-- 14. PS_DISPATCH_SPLIT — PS 배차 분할 상세
-- ============================================================
CREATE TABLE IF NOT EXISTS PS_DISPATCH_SPLIT (
    SPLIT_ID  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '분할 ID (PK)',
    DISP_H_ID BIGINT        NOT NULL                COMMENT '배차 헤더 ID (FK)',
    ORIG_ITEM VARCHAR(30)                           COMMENT '원본 아이템 번호',
    SPLIT_SEQ INT           DEFAULT 1               COMMENT '분할 순번',
    SKUKEY    VARCHAR(30)                           COMMENT 'SKU 키',
    QTSHPO    DECIMAL(15,4)                         COMMENT '분할 수량',
    KG_WEIGHT DECIMAL(15,4)                         COMMENT '분할 중량(kg)',
    NOTE      VARCHAR(200)                          COMMENT '비고',
    CREDAT    VARCHAR(8)                            COMMENT '생성일자 (YYYYMMDD)',
    CRETIM    VARCHAR(6)                            COMMENT '생성시간 (HHmmss)',
    PRIMARY KEY (SPLIT_ID),
    INDEX IDX_PS_DISPATCH_SPLIT_H (DISP_H_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='PS 배차 분할 상세';

-- ============================================================
-- 15. DOC_FOLDER — 서류 폴더
-- ============================================================
CREATE TABLE IF NOT EXISTS DOC_FOLDER (
    FOLDER_ID BIGINT       NOT NULL AUTO_INCREMENT COMMENT '폴더 ID (PK)',
    FOLDER_NM VARCHAR(200) NOT NULL                COMMENT '폴더명',
    PARENT_ID BIGINT                               COMMENT '상위 폴더 ID (NULL=루트)',
    SORT_SEQ  INT          DEFAULT 0               COMMENT '정렬순서',
    CREDAT    VARCHAR(8)                           COMMENT '생성일자 (YYYYMMDD)',
    CRETIM    VARCHAR(6)                           COMMENT '생성시간 (HHmmss)',
    LMODAT    VARCHAR(8)                           COMMENT '수정일자 (YYYYMMDD)',
    LMOUSR    VARCHAR(50)                          COMMENT '수정자',
    DEL_YN    VARCHAR(1)   DEFAULT 'N'             COMMENT '삭제여부 (Y/N)',
    PRIMARY KEY (FOLDER_ID),
    INDEX IDX_DOC_FOLDER_PARENT (PARENT_ID),
    INDEX IDX_DOC_FOLDER_DEL    (DEL_YN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='서류 폴더';

-- ============================================================
-- 16. DOC_FILE — 서류 파일
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
    INDEX IDX_DOC_FILE_FOLDER (FOLDER_ID),
    INDEX IDX_DOC_FILE_DEL    (DEL_YN)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='서류 파일';

SET foreign_key_checks = 1;

-- ============================================================
-- [완료] TMS 자체 테이블 DDL — 총 14개 테이블 + 1개 SET_ITEM
-- [수정] BZPTN_DETAIL 은 Oracle KNRAWMS 소속 → MariaDB DDL 제거됨
-- ============================================================
-- 생성 테이블 목록 (integration 스키마):
--   배차전략  : DS_DISPATCH_OBJECTIVE (1)
--              DS_DISPATCH_PROFILE    (2)
--              DS_DISPATCH_CONSTRAINT (3)
--              DS_DISPATCH_CONST_SET  (4)
--              DS_DISPATCH_CONST_SET_ITEM (5)
--              DS_DISPATCH_CONST      (6)
--              DS_INCH12              (7-1)
--              DS_INCH3               (7-2)
--   차량      : DS_VEHICLE            (8)
--              VHCMA                  (11)
--   납품처    : [Oracle] KNRAWMS.BZPTN_DETAIL (9) — MariaDB 생성 제외
--   운송경로  : ROUTE_COST            (10)
--   배차      : PS_DISPATCH_H         (12)
--              PS_DISPATCH_D          (13)
--              PS_DISPATCH_SPLIT      (14)
--   서류      : DOC_FOLDER            (15)
--              DOC_FILE               (16)
-- ============================================================
