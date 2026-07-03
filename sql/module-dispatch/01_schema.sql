-- ============================================================
-- module-dispatch: DDL
-- ============================================================

CREATE TABLE IF NOT EXISTS ps_dispatch_h (
    DISPATCH_NO  VARCHAR(20)   NOT NULL                COMMENT '배차번호 (PK, 예: 260509001T)',
    DISPATCH_DT  VARCHAR(8)    NOT NULL                COMMENT '배차일자 (yyyyMMdd)',
    RQSHPD       VARCHAR(8)                            COMMENT '납품요청일 (yyyyMMdd)',
    DPTNKY       VARCHAR(20)                           COMMENT '납품처코드',
    DPTNM        VARCHAR(100)                          COMMENT '납품처명',
    CARTYPE      VARCHAR(50)                           COMMENT '차종명 (예: 5톤, 18톤)',
    STATUS       VARCHAR(10)   NOT NULL DEFAULT 'DRAFT' COMMENT '상태 (DRAFT/CONFIRMED/CANCELLED)',
    TOTAL_KG     DECIMAL(12,2)                         COMMENT '총 중량(KG)',
    TOTAL_CNT    INT                                   COMMENT '총 건수',
    NOTE         VARCHAR(200)                          COMMENT '비고',
    STKNUM       VARCHAR(20)                           COMMENT 'SAP 선적번호 (RFC 생성 후)',
    CREDAT       VARCHAR(8)                            COMMENT '생성일자',
    CREUSR       VARCHAR(20)                           COMMENT '생성자',
    LMODAT       VARCHAR(8)                            COMMENT '수정일자',
    LMOUSR       VARCHAR(20)                           COMMENT '수정자',
    CREATED_AT   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    UPDATED_AT   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    PRIMARY KEY (DISPATCH_NO),
    INDEX IDX_PS_DISPATCH_H_RQSHPD (RQSHPD),
    INDEX IDX_PS_DISPATCH_H_DPTNKY (DPTNKY),
    INDEX IDX_PS_DISPATCH_H_STATUS (STATUS)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='PS배차 헤더';


CREATE TABLE IF NOT EXISTS ps_dispatch_d (
    ITEM_ID      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '아이템 ID (PK)',
    DISPATCH_NO  VARCHAR(20)   NOT NULL                COMMENT '배차번호 (FK → ps_dispatch_h)',
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
    CONSTRAINT FK_PS_DISPATCH_D_H FOREIGN KEY (DISPATCH_NO) REFERENCES ps_dispatch_h(DISPATCH_NO) ON DELETE CASCADE,
    INDEX IDX_PS_DISPATCH_D_SHPOKY (SHPOKY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='PS배차 아이템 (ps_dispatch_i 호환명)';

-- JPA Entity ps_dispatch_i와 동일 구조로 VIEW 생성 (선택 사항)
-- CREATE OR REPLACE VIEW ps_dispatch_i AS SELECT * FROM ps_dispatch_d;
