-- ============================================================
-- module-shipment: DDL
-- 대상 DB : MariaDB 10.11+  (utf8mb4)
-- 실행 순서: 01_schema.sql → 02_seed_data.sql
--
-- [주의]
-- SHPDH / SHPDI / SKUMA / MEASI / BZPTN / CMCDV 테이블은
-- SAP 연계 테이블로 별도 import 스크립트로 생성/관리됩니다.
-- 본 스크립트는 module-shipment 전용 보조 테이블만 생성합니다.
-- ============================================================


-- ──────────────────────────────────────────────────────────────
-- 1. 출고 조회 즐겨찾기 (사용자별 검색조건 저장)
-- ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS shipment_filter_preset (
    PRESET_ID    BIGINT        NOT NULL AUTO_INCREMENT      COMMENT '즐겨찾기 ID (PK)',
    USER_ID      VARCHAR(50)   NOT NULL                     COMMENT '사용자 ID',
    PRESET_NM    VARCHAR(100)  NOT NULL                     COMMENT '즐겨찾기 명칭',
    WAREKY       VARCHAR(10)                                COMMENT '창고코드',
    DATE_FROM    VARCHAR(8)                                 COMMENT '검색 시작일 (yyyyMMdd)',
    DATE_TO      VARCHAR(8)                                 COMMENT '검색 종료일 (yyyyMMdd)',
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


-- ──────────────────────────────────────────────────────────────
-- 2. 출고 처리 이력 로그 (상태변경 감사 추적)
-- ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS shipment_history (
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
