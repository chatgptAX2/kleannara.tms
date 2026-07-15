-- ============================================================
--  module-dispatch: DDL (MariaDB/MySQL 호환)
--  생성일: 2026-07-04  /  출처: wms-viewer/wms.db 현황 기반
-- ============================================================

-- ── PS 배차 헤더 ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS PS_DISPATCH_H (
    DISPATCH_NO  VARCHAR(20)  NOT NULL                  COMMENT '배차번호 (PK)',
    DISPATCH_DT  VARCHAR(8)   DEFAULT ''                COMMENT '배차일자(yyyyMMdd)',
    RQSHPD       VARCHAR(8)   DEFAULT ''                COMMENT '납품요청일(yyyyMMdd)',
    DPTNKY       VARCHAR(20)  DEFAULT ''                COMMENT '납품처코드',
    DPTNM        VARCHAR(100) DEFAULT ''                COMMENT '납품처명',
    CARTYPE      VARCHAR(50)  DEFAULT ''                COMMENT '차종명',
    STATUS       VARCHAR(10)  NOT NULL DEFAULT 'DRAFT'  COMMENT '상태(DRAFT/CONFIRMED/CANCELLED)',
    TOTAL_KG     DECIMAL(12,2) DEFAULT 0               COMMENT '총중량(KG)',
    TOTAL_CNT    INT          DEFAULT 0                 COMMENT '총건수',
    NOTE         VARCHAR(200) DEFAULT ''                COMMENT '비고',
    STKNUM       VARCHAR(20)  DEFAULT NULL              COMMENT 'SAP 선적번호',
    CREDAT       VARCHAR(8)   DEFAULT ''                COMMENT '생성일자',
    CREUSR       VARCHAR(20)  DEFAULT 'SYSTEM'          COMMENT '생성자',
    UPDDAT       VARCHAR(8)   DEFAULT NULL              COMMENT '수정일자',
    PRIMARY KEY (DISPATCH_NO),
    INDEX IDX_PS_DISPATCH_H_RQSHPD  (RQSHPD),
    INDEX IDX_PS_DISPATCH_H_DPTNKY  (DPTNKY),
    INDEX IDX_PS_DISPATCH_H_STATUS  (STATUS)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='PS 배차 헤더';


-- ── PS 배차 아이템 ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS PS_DISPATCH_D (
    DISPATCH_NO  VARCHAR(20)  NOT NULL                  COMMENT '배차번호 (FK)',
    SEQ          INT          NOT NULL                  COMMENT '순번',
    SHPOKY       VARCHAR(20)  DEFAULT ''                COMMENT '납품문서번호',
    SHPOIT       VARCHAR(6)   DEFAULT ''                COMMENT '납품문서라인',
    SKUKEY       VARCHAR(30)  DEFAULT ''                COMMENT '품목코드',
    DESC01       VARCHAR(200) DEFAULT ''                COMMENT '품목명',
    QTSHPO       DECIMAL(12,4) DEFAULT 0               COMMENT '출하수량',
    UOMKEY       VARCHAR(10)  DEFAULT 'KG'             COMMENT '단위',
    DPTNKY       VARCHAR(20)  DEFAULT ''                COMMENT '납품처코드',
    DPTNM        VARCHAR(100) DEFAULT ''                COMMENT '납품처명',
    IS_SPLIT     INT          NOT NULL DEFAULT 0        COMMENT '분할여부(0/1)',
    ORG_SHPOKY   VARCHAR(20)  DEFAULT ''                COMMENT '원본 납품문서번호',
    ORG_SHPOIT   VARCHAR(6)   DEFAULT ''                COMMENT '원본 납품문서라인',
    GRSWGT       DECIMAL(12,4) DEFAULT 0               COMMENT '묶음당 중량(kg)',
    KG_WEIGHT    DECIMAL(12,4) DEFAULT 0               COMMENT 'KG 환산중량',
    PRIMARY KEY (DISPATCH_NO, SEQ),
    CONSTRAINT FK_PS_DISPATCH_D_H FOREIGN KEY (DISPATCH_NO)
        REFERENCES PS_DISPATCH_H(DISPATCH_NO) ON DELETE CASCADE,
    INDEX IDX_PS_DISPATCH_D_SHPOKY (SHPOKY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='PS 배차 아이템';


-- ── PS 배차 분할 이력 ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS PS_DISPATCH_SPLIT (
    SPLIT_KEY   VARCHAR(50)  NOT NULL                  COMMENT '분할키 (PK)',
    ORG_SHPOKY  VARCHAR(20)  DEFAULT ''                COMMENT '원본 납품문서번호',
    ORG_SHPOIT  VARCHAR(6)   DEFAULT ''                COMMENT '원본 납품문서라인',
    NEW_SHPOKY  VARCHAR(20)  DEFAULT ''                COMMENT '신규 납품문서번호',
    NEW_SHPOIT  VARCHAR(6)   DEFAULT ''                COMMENT '신규 납품문서라인',
    SKUKEY      VARCHAR(30)  DEFAULT ''                COMMENT '품목코드',
    DESC01      VARCHAR(200) DEFAULT ''                COMMENT '품목명',
    ORG_QTY     DECIMAL(12,4) DEFAULT 0               COMMENT '원본 수량',
    SPLIT_QTY   DECIMAL(12,4) DEFAULT 0               COMMENT '분할 수량',
    REM_QTY     DECIMAL(12,4) DEFAULT 0               COMMENT '잔여 수량',
    UOMKEY      VARCHAR(10)  DEFAULT 'KG'             COMMENT '단위',
    STATUS      VARCHAR(10)  DEFAULT 'ACTIVE'          COMMENT '상태',
    CREDAT      VARCHAR(8)   DEFAULT ''                COMMENT '생성일자',
    CREUSR      VARCHAR(20)  DEFAULT 'SYSTEM'          COMMENT '생성자',
    PRIMARY KEY (SPLIT_KEY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='PS 배차 분할 이력';


-- ── SAP 선적 연동 ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS PS_SAP_STK (
    STDLNR       VARCHAR(20)  NOT NULL                  COMMENT 'SAP 배송번호 (PK)',
    SAP_STKNUM   VARCHAR(20)  DEFAULT ''                COMMENT 'SAP 선적번호',
    DISPATCH_NO  VARCHAR(20)  DEFAULT ''                COMMENT '배차번호 (FK)',
    RQSHPD_FROM  VARCHAR(8)   DEFAULT ''                COMMENT '납품요청일 FROM',
    RQSHPD_TO    VARCHAR(8)   DEFAULT ''                COMMENT '납품요청일 TO',
    DPTNKY       VARCHAR(20)  DEFAULT ''                COMMENT '납품처코드',
    DPTNKYNM     VARCHAR(100) DEFAULT ''                COMMENT '납품처명',
    CARTYPE      VARCHAR(50)  DEFAULT ''                COMMENT '차종',
    CARCLASS_CD  VARCHAR(20)  DEFAULT ''                COMMENT '차량클래스',
    VEHINO       VARCHAR(20)  DEFAULT ''                COMMENT '차량번호',
    CARNO        VARCHAR(20)  DEFAULT ''                COMMENT '차호',
    DRIVER       VARCHAR(50)  DEFAULT ''                COMMENT '기사명',
    DRIVERCEL    VARCHAR(20)  DEFAULT ''                COMMENT '기사연락처',
    TOTAL_KG     DECIMAL(12,2) DEFAULT 0               COMMENT '총중량',
    SVBELN_CNT   INT          DEFAULT 0                 COMMENT '배송문서 건수',
    STATUS       VARCHAR(10)  DEFAULT 'DRAFT'           COMMENT '상태',
    CREDAT       VARCHAR(8)   DEFAULT ''                COMMENT '생성일자',
    CREUSR       VARCHAR(20)  DEFAULT 'SYSTEM'          COMMENT '생성자',
    PRIMARY KEY (STDLNR)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='SAP 선적 연동';


-- ── 운송비 마스터 ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ROUTE_COST (
    SHPPT       VARCHAR(10)  NOT NULL DEFAULT ''        COMMENT '출하지점',
    ROUTE       VARCHAR(20)  NOT NULL DEFAULT ''        COMMENT '경로코드',
    PTNRKY      VARCHAR(10)  NOT NULL DEFAULT ''        COMMENT '납품처코드',
    CARCLASS    VARCHAR(20)  NOT NULL DEFAULT ''        COMMENT '차량클래스',
    COST        DECIMAL(12,2)                           COMMENT '운송비(원)',
    UNIT        VARCHAR(10)  DEFAULT 'KRW'              COMMENT '통화단위',
    DATE_START  VARCHAR(8)   DEFAULT ''                 COMMENT '적용시작일',
    DATE_END    VARCHAR(8)   DEFAULT ''                 COMMENT '적용종료일',
    CREDAT      VARCHAR(8)   DEFAULT ''                 COMMENT '생성일자',
    CRETIM      VARCHAR(6)   DEFAULT ''                 COMMENT '생성시간',
    CREUSR      VARCHAR(20)  DEFAULT 'ADMIN'            COMMENT '생성자',
    LMODAT      VARCHAR(8)   DEFAULT ''                 COMMENT '수정일자',
    LMOTIM      VARCHAR(6)   DEFAULT ''                 COMMENT '수정시간',
    LMOUSR      VARCHAR(20)  DEFAULT 'ADMIN'            COMMENT '수정자',
    PRIMARY KEY (ROUTE, PTNRKY, CARCLASS),
    INDEX IDX_ROUTE_COST_SHPPT  (SHPPT),
    INDEX IDX_ROUTE_COST_PTNRKY (PTNRKY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='운송비 마스터';
