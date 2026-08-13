-- ============================================================
-- module-document: DDL
-- 서류관리 (폴더/파일 업로드·다운로드·미리보기)
-- Flask SQLite(wms.db) → MariaDB 완전 이관
-- ============================================================

-- ── 서류 폴더 ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS DOC_FOLDER (
    FOLDER_ID   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '폴더 ID (PK)',
    FOLDER_NM   VARCHAR(200) NOT NULL                COMMENT '폴더명',
    PARENT_ID   BIGINT                               COMMENT '상위 폴더 ID (NULL=루트)',
    SORT_SEQ    INT          DEFAULT 0               COMMENT '정렬순서',
    CREDAT      VARCHAR(8)                           COMMENT '생성일자',
    CRETIM      VARCHAR(6)                           COMMENT '생성시간',
    LMODAT      VARCHAR(8)                           COMMENT '수정일자',
    LMOUSR      VARCHAR(50)                          COMMENT '수정자',
    DEL_YN      VARCHAR(1)   DEFAULT 'N'             COMMENT '삭제여부',
    PRIMARY KEY (FOLDER_ID),
    INDEX IDX_DOC_FOLDER_PARENT (PARENT_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='서류 폴더';

-- ── 서류 파일 ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS DOC_FILE (
    FILE_ID     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '파일 ID (PK)',
    FOLDER_ID   BIGINT       NOT NULL                COMMENT '폴더 ID (FK)',
    FILE_NM     VARCHAR(500) NOT NULL                COMMENT '원본 파일명',
    FILE_PATH   VARCHAR(1000)                        COMMENT '서버 저장 경로',
    FILE_SIZE   BIGINT                               COMMENT '파일 크기 (bytes)',
    FILE_TYPE   VARCHAR(100)                         COMMENT 'MIME 타입',
    FILE_EXT    VARCHAR(20)                          COMMENT '파일 확장자',
    DOWNLOAD_CNT INT         DEFAULT 0               COMMENT '다운로드 횟수',
    OP_DATE     VARCHAR(8)                           COMMENT '운행일자 (YYYYMMDD)',
    NOTE        VARCHAR(500)                         COMMENT '비고',
    CREDAT      VARCHAR(8)                           COMMENT '생성일자',
    CRETIM      VARCHAR(6)                           COMMENT '생성시간',
    CREUSR      VARCHAR(50)                          COMMENT '등록자',
    LMODAT      VARCHAR(8)                           COMMENT '수정일자',
    LMOUSR      VARCHAR(50)                          COMMENT '수정자',
    DEL_YN      VARCHAR(1)   DEFAULT 'N'             COMMENT '삭제여부',
    PRIMARY KEY (FILE_ID),
    INDEX IDX_DOC_FILE_FOLDER (FOLDER_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='서류 파일';
