-- ============================================================
-- [TMS DB 백업] PS제약조건관리 — DDL + 기준 데이터
-- 대상 DB : TMS MariaDB (10.2.14.247:3306/intergration)
-- 관리 목적: 데이터 이관 및 기록 보관
-- 생성일  : 2026-07-08
-- ============================================================

-- ── DDL ──────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ds_dispatch_objective (
    OBJ_ID     BIGINT        NOT NULL AUTO_INCREMENT COMMENT '목적식 ID (PK)',
    OBJ_CODE   VARCHAR(30)   NOT NULL                COMMENT '목적식 코드 (MIN_VEHICLES 등)',
    OBJ_NM     VARCHAR(100)                          COMMENT '표시명',
    OBJ_ICON   VARCHAR(10)                           COMMENT '이모지 아이콘',
    OBJ_ALGO   VARCHAR(50)                           COMMENT '알고리즘 코드',
    OBJ_DESC   VARCHAR(200)                          COMMENT '설명',
    SORT_SEQ   INT           DEFAULT 0               COMMENT '정렬순서',
    ACTIVE_YN  VARCHAR(1)    DEFAULT 'Y'             COMMENT '활성 여부 (단일 Y 보장)',
    CREDAT     VARCHAR(8)                            COMMENT '생성일자',
    LMODAT     VARCHAR(8)                            COMMENT '수정일자',
    PRIMARY KEY (OBJ_ID),
    CONSTRAINT UK_DS_DISPATCH_OBJ UNIQUE (OBJ_CODE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차 목적식';

CREATE TABLE IF NOT EXISTS ds_dispatch_profile (
    PROFILE_ID   BIGINT        NOT NULL AUTO_INCREMENT COMMENT '프로파일 ID (PK)',
    PROFILE_NM   VARCHAR(100)  NOT NULL                COMMENT '프로파일명',
    PROFILE_DESC VARCHAR(200)                          COMMENT '설명',
    ACTIVE_YN    VARCHAR(1)    DEFAULT 'Y'             COMMENT '활성 여부',
    SET_ID       INT                                   COMMENT '연결된 const-set ID',
    SORT_SEQ     INT           DEFAULT 0               COMMENT '정렬순서',
    CREDAT       VARCHAR(8)                            COMMENT '생성일자',
    LMODAT       VARCHAR(8)                            COMMENT '수정일자',
    PRIMARY KEY (PROFILE_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차제약 프로파일';

CREATE TABLE IF NOT EXISTS ds_dispatch_constraint (
    CONSTRAINT_ID BIGINT       NOT NULL AUTO_INCREMENT COMMENT '제약 ID (PK)',
    PROFILE_ID    BIGINT       NOT NULL                COMMENT '프로파일 ID (FK)',
    OWNRKY        VARCHAR(10)                          COMMENT '사업주',
    CONSTRAINT_TYPE VARCHAR(30)                        COMMENT '제약 유형',
    CONSTRAINT_KEY  VARCHAR(50)                        COMMENT '제약 키',
    CONSTRAINT_VAL  VARCHAR(200)                       COMMENT '제약 값',
    IS_ACTIVE     INT          DEFAULT 1               COMMENT '활성 여부 (1=활성)',
    SORT_SEQ      INT          DEFAULT 0               COMMENT '정렬순서',
    CREDAT        VARCHAR(8)                           COMMENT '생성일자',
    LMODAT        VARCHAR(8)                           COMMENT '수정일자',
    PRIMARY KEY (CONSTRAINT_ID),
    INDEX IDX_DS_DISPATCH_CONST_PROFILE (PROFILE_ID, IS_ACTIVE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차제약 상세';

CREATE TABLE IF NOT EXISTS ds_dispatch_const_set (
    CONST_ID    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '세트 ID (PK)',
    PROFILE_ID  BIGINT        NOT NULL                COMMENT '프로파일 ID (FK)',
    OWNRKY      VARCHAR(10)                           COMMENT '사업주',
    CONST_TYPE  VARCHAR(30)                           COMMENT '제약 유형',
    CARTYPE     VARCHAR(50)                           COMMENT '차종',
    REGION      VARCHAR(50)                           COMMENT '지역',
    CONST_VAL   VARCHAR(200)                          COMMENT '제약 값',
    IS_DYNAMIC  INT           DEFAULT 0               COMMENT '동적 여부',
    IS_ACTIVE   INT           DEFAULT 1               COMMENT '활성 여부',
    FORKLIFT_YN VARCHAR(1)                            COMMENT '지게차 여부',
    ENTRY_TON   DECIMAL(6,2)                          COMMENT '진입 가능 톤수',
    CREDAT      VARCHAR(8)                            COMMENT '생성일자',
    LMODAT      VARCHAR(8)                            COMMENT '수정일자',
    PRIMARY KEY (CONST_ID),
    INDEX IDX_DS_CONST_SET_PROFILE (PROFILE_ID, CONST_TYPE, IS_ACTIVE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차제약 세트';

-- ── 기준 데이터 (INSERT) ───────────────────────────────────────

INSERT IGNORE INTO ds_dispatch_objective
    (OBJ_CODE, OBJ_NM, OBJ_ICON, OBJ_ALGO, OBJ_DESC, SORT_SEQ, ACTIVE_YN, CREDAT, LMODAT)
VALUES
    ('MIN_VEHICLES', '차량 최소화',   '🚛', 'FFD BinPacking',
     '가능한 적은 차량으로 최대 적재 (FFD 알고리즘)', 10, 'Y',
     DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
    ('MAX_FILL',     '적재율 최대화', '📊', 'BFD BinPacking',
     '각 차량을 가장 꽉 채우는 방식 (BFD 알고리즘)',  20, 'N',
     DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
    ('MIN_COST',     '운송비 최소화', '💰', 'ROUTE_COST',
     'ROUTE_COST 기반 최저비용 차종 선택',            30, 'N',
     DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d'));
