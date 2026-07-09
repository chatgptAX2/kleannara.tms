-- ============================================================
-- module-document: TMS DB 백업 (DDL + INSERT)
-- 서류관리 (DOC_FOLDER, DOC_FILE)
-- 대상 DB : TMS DB (MariaDB)
-- 목적    : 데이터 이관 및 기록 보관
-- 생성일  : 2026-07-08
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- 1. DDL — 테이블 생성 (CREATE TABLE IF NOT EXISTS)
-- ─────────────────────────────────────────────────────────────

-- ── 서류 폴더 ──────────────────────────────────────────────────
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

-- ── 서류 파일 ──────────────────────────────────────────────────
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

-- ─────────────────────────────────────────────────────────────
-- 2. 데이터 백업 — INSERT IGNORE (기존 PK 충돌 시 무시)
-- ─────────────────────────────────────────────────────────────
--
-- [사용법]
--   운영 DB 에서 아래 명령으로 데이터를 추출한 뒤 이 파일에 붙여넣으세요:
--
--   mysqldump --no-create-info --insert-ignore \
--     --tables DOC_FOLDER DOC_FILE \
--     tms_dev > /tmp/doc_data.sql
--
--   또는 수동 INSERT 예시 (아래 템플릿 참조):
-- ─────────────────────────────────────────────────────────────

-- ── DOC_FOLDER 데이터 ────────────────────────────────────────
-- 형식: (FOLDER_ID, FOLDER_NM, PARENT_ID, SORT_SEQ, CREDAT, CRETIM, LMODAT, LMOUSR, DEL_YN)
/*
INSERT IGNORE INTO DOC_FOLDER
    (FOLDER_ID, FOLDER_NM, PARENT_ID, SORT_SEQ, CREDAT, CRETIM, LMODAT, LMOUSR, DEL_YN)
VALUES
    (1, '공문서',   NULL, 1, '20240101', '090000', '20240101', 'ADMIN', 'N'),
    (2, '계약서',   NULL, 2, '20240101', '090000', '20240101', 'ADMIN', 'N'),
    (3, '인수증',      1, 1, '20240115', '093000', '20240115', 'ADMIN', 'N');
-- 실제 데이터로 교체 후 주석 제거하여 사용하세요
*/

-- ── DOC_FILE 데이터 ──────────────────────────────────────────
-- 형식: (FILE_ID, FOLDER_ID, FILE_NM, FILE_PATH, FILE_SIZE, FILE_TYPE, FILE_EXT,
--        DOWNLOAD_CNT, NOTE, CREDAT, CRETIM, CREUSR, LMODAT, LMOUSR, DEL_YN)
/*
INSERT IGNORE INTO DOC_FILE
    (FILE_ID, FOLDER_ID, FILE_NM, FILE_PATH, FILE_SIZE, FILE_TYPE, FILE_EXT,
     DOWNLOAD_CNT, NOTE, CREDAT, CRETIM, CREUSR, LMODAT, LMOUSR, DEL_YN)
VALUES
    (1, 1, '공문서_샘플.pdf',   '/uploads/docs/공문서_샘플.pdf',   102400, 'application/pdf', 'pdf',  0, NULL, '20240101', '090000', 'ADMIN', '20240101', 'ADMIN', 'N'),
    (2, 2, '계약서_2024.docx', '/uploads/docs/계약서_2024.docx', 204800, 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'docx', 0, NULL, '20240101', '090000', 'ADMIN', '20240101', 'ADMIN', 'N');
-- 실제 데이터로 교체 후 주석 제거하여 사용하세요
*/

-- ─────────────────────────────────────────────────────────────
-- 3. 무결성 확인 쿼리 (백업 복원 후 실행)
-- ─────────────────────────────────────────────────────────────
/*
-- 폴더 건수 확인
SELECT COUNT(*) AS folder_cnt FROM DOC_FOLDER WHERE DEL_YN = 'N';

-- 파일 건수 확인
SELECT COUNT(*) AS file_cnt FROM DOC_FILE WHERE DEL_YN = 'N';

-- 폴더별 파일 수 확인
SELECT f.FOLDER_ID, f.FOLDER_NM, COUNT(d.FILE_ID) AS file_cnt
FROM DOC_FOLDER f
LEFT JOIN DOC_FILE d ON d.FOLDER_ID = f.FOLDER_ID AND d.DEL_YN = 'N'
WHERE f.DEL_YN = 'N'
GROUP BY f.FOLDER_ID, f.FOLDER_NM
ORDER BY f.SORT_SEQ;

-- 고아 파일(부모 폴더 없는 파일) 확인
SELECT d.FILE_ID, d.FILE_NM
FROM DOC_FILE d
LEFT JOIN DOC_FOLDER f ON f.FOLDER_ID = d.FOLDER_ID
WHERE f.FOLDER_ID IS NULL;
*/
