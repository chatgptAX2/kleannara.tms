-- ============================================================
--  module-dispatch-config: DDL (MariaDB/MySQL 호환)
--  생성일: 2026-07-04  /  출처: wms-viewer/wms.db 현황 기반
-- ============================================================

-- ── 배차 목적식 ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS DS_DISPATCH_OBJECTIVE (
    OBJ_ID    INT          NOT NULL AUTO_INCREMENT  COMMENT '목적식 ID (PK)',
    OBJ_CODE  VARCHAR(30)  NOT NULL                 COMMENT '목적식 코드',
    OBJ_NM    VARCHAR(100) NOT NULL                 COMMENT '표시명',
    OBJ_ICON  VARCHAR(10)  DEFAULT '🎯'            COMMENT '아이콘',
    OBJ_ALGO  VARCHAR(50)  DEFAULT ''              COMMENT '알고리즘 코드',
    OBJ_DESC  VARCHAR(200) DEFAULT ''              COMMENT '설명',
    SORT_SEQ  INT          DEFAULT 0                COMMENT '정렬순서',
    ACTIVE_YN VARCHAR(1)   DEFAULT 'Y'             COMMENT '활성여부',
    CREDAT    VARCHAR(8)                             COMMENT '생성일자',
    LMODAT    VARCHAR(8)                             COMMENT '수정일자',
    PRIMARY KEY (OBJ_ID),
    CONSTRAINT UK_DS_DISPATCH_OBJ UNIQUE (OBJ_CODE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차 목적식';


-- ── 배차 프로파일 ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS DS_DISPATCH_PROFILE (
    PROFILE_ID  INT          NOT NULL AUTO_INCREMENT  COMMENT '프로파일 ID (PK)',
    PROFILE_NM  VARCHAR(100) NOT NULL                 COMMENT '프로파일명',
    OBJECTIVE   VARCHAR(30)  NOT NULL DEFAULT 'MIN_VEHICLES' COMMENT '목적식 코드',
    ACTIVE_YN   VARCHAR(1)   NOT NULL DEFAULT 'Y'    COMMENT '활성여부',
    NOTE        VARCHAR(200) DEFAULT ''              COMMENT '비고',
    SET_ID      INT          DEFAULT NULL             COMMENT '연결 CONST_SET ID',
    CREDAT      VARCHAR(8)                             COMMENT '생성일자',
    LMODAT      VARCHAR(8)                             COMMENT '수정일자',
    PRIMARY KEY (PROFILE_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차 프로파일';


-- ── 배차 제약조건 마스터 ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS DS_DISPATCH_CONST (
    CONST_ID    INT          NOT NULL AUTO_INCREMENT  COMMENT '제약 ID (PK)',
    PROFILE_ID  INT          NOT NULL                 COMMENT '프로파일 ID (FK)',
    CONST_TYPE  VARCHAR(30)  NOT NULL                 COMMENT '제약유형',
    CONST_KEY   VARCHAR(50)  NOT NULL                 COMMENT '제약키',
    CONST_VALUE VARCHAR(200) DEFAULT NULL             COMMENT '제약값',
    CONST_OP    VARCHAR(10)  DEFAULT '<='            COMMENT '연산자',
    TARGET_ID   VARCHAR(50)  DEFAULT ''              COMMENT '적용대상ID',
    TARGET_NM   VARCHAR(100) DEFAULT ''              COMMENT '적용대상명',
    ACTIVE_YN   VARCHAR(1)   DEFAULT 'Y'             COMMENT '활성여부',
    NOTE        VARCHAR(200) DEFAULT ''              COMMENT '비고',
    SORT_SEQ    INT          DEFAULT 0                COMMENT '정렬순서',
    CREDAT      VARCHAR(8)                             COMMENT '생성일자',
    LMODAT      VARCHAR(8)                             COMMENT '수정일자',
    PRIMARY KEY (CONST_ID),
    INDEX IDX_DS_CONST_PROFILE (PROFILE_ID),
    INDEX IDX_DS_CONST_TYPE_KEY (CONST_TYPE, CONST_KEY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차 제약조건 마스터';


-- ── 제약조건 항목 UI 마스터 ───────────────────────────────────
CREATE TABLE IF NOT EXISTS DS_DISPATCH_CONST_ITEM (
    ITEM_CD     VARCHAR(50)  NOT NULL                 COMMENT '항목코드 (PK) = CONST_KEY',
    ITEM_NM     VARCHAR(200) NOT NULL                 COMMENT '항목명(한글)',
    ITEM_GRP    VARCHAR(30)  NOT NULL                 COMMENT '그룹: COMMON/ROLL/BOARD/MIX/GLOBAL/CARGO/COST/DYNAMIC_ZONE/VEHICLE',
    ITEM_TYPE   VARCHAR(20)  NOT NULL DEFAULT 'YN'   COMMENT '값유형: YN/NUM/TEXT/SELECT/CSV',
    DEFAULT_VAL VARCHAR(200) DEFAULT 'Y'             COMMENT '기본값',
    UNIT        VARCHAR(20)  DEFAULT ''              COMMENT '단위',
    CONST_OP    VARCHAR(10)  DEFAULT '='             COMMENT '연산자',
    SORT_SEQ    INT          DEFAULT 0                COMMENT '정렬순서',
    DESCRIPTION VARCHAR(500) DEFAULT ''              COMMENT '상세설명',
    SOURCE_REF  VARCHAR(20)  DEFAULT ''              COMMENT '참조(§번호)',
    ACTIVE_YN   VARCHAR(1)   DEFAULT 'Y'             COMMENT '활성여부',
    SELECT_OPTS VARCHAR(500) DEFAULT NULL             COMMENT 'SELECT 타입 옵션(JSON배열)',
    CREDAT      VARCHAR(8)                             COMMENT '생성일자',
    LMODAT      VARCHAR(8)                             COMMENT '수정일자',
    PRIMARY KEY (ITEM_CD)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='제약조건 항목 UI 마스터';


-- ── 제약조건 세트 ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS DS_DISPATCH_CONST_SET (
    SET_ID    INT          NOT NULL AUTO_INCREMENT  COMMENT '세트 ID (PK)',
    SET_NM    VARCHAR(100) NOT NULL                 COMMENT '세트명',
    SET_DESC  VARCHAR(200) DEFAULT ''              COMMENT '설명',
    ACTIVE_YN VARCHAR(1)   DEFAULT 'Y'             COMMENT '활성여부',
    CREDAT    VARCHAR(8)                             COMMENT '생성일자',
    LMODAT    VARCHAR(8)                             COMMENT '수정일자',
    PRIMARY KEY (SET_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='제약조건 세트';


-- ── 세트-제약 연결 ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS DS_DISPATCH_CONST_SET_ITEM (
    ITEM_ID     INT          NOT NULL AUTO_INCREMENT  COMMENT '아이템 ID (PK)',
    SET_ID      INT          NOT NULL                 COMMENT '세트 ID (FK)',
    CONST_ID    INT          NOT NULL                 COMMENT '제약 ID (FK)',
    ACTIVE_YN   VARCHAR(1)   DEFAULT 'Y'             COMMENT '활성여부',
    PARAM_VALUE VARCHAR(200) DEFAULT NULL             COMMENT '파라미터 오버라이드값',
    PRIMARY KEY (ITEM_ID),
    UNIQUE KEY UK_SET_CONST (SET_ID, CONST_ID),
    INDEX IDX_DSC_SET (SET_ID),
    INDEX IDX_DSC_CONST (CONST_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='세트-제약 연결';


-- ── 세트별 항목 설정값 ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS DS_DISPATCH_CONST_SETTING (
    SETTING_ID  INT          NOT NULL AUTO_INCREMENT  COMMENT '설정 ID (PK)',
    SET_ID      INT          NOT NULL                 COMMENT '세트 ID (FK)',
    ITEM_CD     VARCHAR(50)  NOT NULL                 COMMENT '항목코드 (FK → DS_DISPATCH_CONST_ITEM)',
    USE_YN      VARCHAR(1)   NOT NULL DEFAULT 'Y'    COMMENT '사용여부',
    SETTING_VAL VARCHAR(200) DEFAULT NULL             COMMENT '설정값 오버라이드',
    NOTE        VARCHAR(200) DEFAULT ''              COMMENT '비고',
    LMODAT      VARCHAR(8)                             COMMENT '수정일자',
    PRIMARY KEY (SETTING_ID),
    UNIQUE KEY UK_SET_ITEM (SET_ID, ITEM_CD),
    CONSTRAINT FK_DSC_SETTING_SET  FOREIGN KEY (SET_ID)   REFERENCES DS_DISPATCH_CONST_SET(SET_ID),
    CONSTRAINT FK_DSC_SETTING_ITEM FOREIGN KEY (ITEM_CD)  REFERENCES DS_DISPATCH_CONST_ITEM(ITEM_CD)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='세트별 항목 설정값';
