-- ============================================================
-- module-dispatch-config: DDL
-- ============================================================

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
    CREDAT       VARCHAR(8)                            COMMENT '생성일자',
    LMODAT       VARCHAR(8)                            COMMENT '수정일자',
    PRIMARY KEY (PROFILE_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차제약 프로파일';


CREATE TABLE IF NOT EXISTS ds_dispatch_constraint (
    CONST_ID     BIGINT        NOT NULL AUTO_INCREMENT COMMENT '제약 ID (PK)',
    PROFILE_ID   BIGINT        NOT NULL                COMMENT '프로파일 ID (FK)',
    CONST_TYPE   VARCHAR(30)                           COMMENT '제약 유형',
    CONST_KEY    VARCHAR(50)                           COMMENT '제약 키',
    CONST_VAL    VARCHAR(200)                          COMMENT '제약 값',
    REMARK       VARCHAR(200)                          COMMENT '비고',
    CREDAT       VARCHAR(8)                            COMMENT '생성일자',
    LMODAT       VARCHAR(8)                            COMMENT '수정일자',
    PRIMARY KEY (CONST_ID),
    INDEX IDX_DS_DISPATCH_CONST_PROFILE (PROFILE_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차제약 상세';


CREATE TABLE IF NOT EXISTS ds_dispatch_const_set (
    SET_ID     INT           NOT NULL AUTO_INCREMENT COMMENT 'SET ID (PK)',
    SET_NM     VARCHAR(100)                          COMMENT 'SET명',
    SET_DESC   VARCHAR(200)                          COMMENT '설명',
    ACTIVE_YN  VARCHAR(1)    DEFAULT 'Y'             COMMENT '활성 여부',
    CREDAT     VARCHAR(8)                            COMMENT '생성일자',
    LMODAT     VARCHAR(8)                            COMMENT '수정일자',
    PRIMARY KEY (SET_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차제약 SET';


CREATE TABLE IF NOT EXISTS ds_dispatch_const_set_item (
    ITEM_ID    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '아이템 ID (PK)',
    SET_ID     INT           NOT NULL                COMMENT 'SET ID (FK)',
    ITEM_KEY   VARCHAR(50)                           COMMENT '아이템 키',
    ITEM_VAL   VARCHAR(200)                          COMMENT '아이템 값',
    ITEM_TYPE  VARCHAR(20)                           COMMENT '값 유형',
    REMARK     VARCHAR(200)                          COMMENT '비고',
    PRIMARY KEY (ITEM_ID),
    INDEX IDX_DS_CONST_SET_ITEM (SET_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차제약 SET 아이템';


CREATE TABLE IF NOT EXISTS ds_dispatch_const_cartype (
    CARTYPE_ID BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'ID (PK)',
    SET_ID     INT           NOT NULL                COMMENT 'SET ID (FK)',
    CARTYPE    VARCHAR(50)                           COMMENT '차종명',
    MAX_CNT    INT                                   COMMENT '최대 배차 수',
    ACTIVE_YN  VARCHAR(1)    DEFAULT 'Y'             COMMENT '활성 여부',
    PRIMARY KEY (CARTYPE_ID),
    INDEX IDX_DS_CONST_CARTYPE (SET_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차제약 차종';


CREATE TABLE IF NOT EXISTS ds_dispatch_const_region (
    REGION_ID  BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'ID (PK)',
    SET_ID     INT           NOT NULL                COMMENT 'SET ID (FK)',
    REGION_CD  VARCHAR(20)                           COMMENT '지역코드',
    REGION_NM  VARCHAR(50)                           COMMENT '지역명',
    PRIORITY   INT           DEFAULT 0               COMMENT '우선순위',
    PRIMARY KEY (REGION_ID),
    INDEX IDX_DS_CONST_REGION (SET_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차제약 지역';
