-- ============================================================
-- module-delivery: DDL
-- ============================================================

CREATE TABLE IF NOT EXISTS bzptn_detail (
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
    CREDAT          VARCHAR(8)                            COMMENT '생성일자',
    CRETIM          VARCHAR(6)                            COMMENT '생성시각',
    CREUSR          VARCHAR(20)                           COMMENT '생성자',
    LMODAT          VARCHAR(8)                            COMMENT '수정일자',
    LMOTIM          VARCHAR(6)                            COMMENT '수정시각',
    LMOUSR          VARCHAR(20)                           COMMENT '수정자',

    PRIMARY KEY (DETAIL_ID),
    CONSTRAINT UK_BZPTN_DETAIL UNIQUE (PTNRKY, PTNRTY, OWNRKY),
    INDEX IDX_BZPTN_DETAIL_WAREKY (WAREKY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='납품처 TMS 상세';


CREATE TABLE IF NOT EXISTS route_cost (
    COST_ID   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '비용 ID (PK)',
    WAREKY    VARCHAR(10)                           COMMENT '출하창고코드',
    PTNRKY    VARCHAR(20)                           COMMENT '납품처코드',
    CARTYPE   VARCHAR(50)                           COMMENT '차종명',
    COST_AMT  DECIMAL(12,2)                         COMMENT '운송비(원)',
    DIST_KM   DECIMAL(8,2)                          COMMENT '거리(km)',
    EFF_DATE  VARCHAR(8)                            COMMENT '적용일자',
    EXP_DATE  VARCHAR(8)                            COMMENT '종료일자',
    UPDDAT    VARCHAR(8)                            COMMENT '수정일자',
    UPDUSR    VARCHAR(20)                           COMMENT '수정자',

    PRIMARY KEY (COST_ID),
    INDEX IDX_ROUTE_COST_PTNRKY (WAREKY, PTNRKY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='경로별 운송비 마스터';
