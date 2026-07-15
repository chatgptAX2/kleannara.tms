-- ============================================================
--  module-wms-master: DDL (MariaDB/MySQL 호환)
--  생성일: 2026-07-04  /  출처: wms-viewer/wms.db 현황 기반
-- ============================================================

-- ── 공통코드 마스터 ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS CMCDM (
    CMCDKY  VARCHAR(30)  NOT NULL              COMMENT '코드키',
    SHORTX  VARCHAR(50)  DEFAULT ' '           COMMENT '코드명(축약)',
    DBFILD  VARCHAR(50)  DEFAULT ' '           COMMENT 'DB 필드명',
    USARL1  VARCHAR(100) DEFAULT ' '           COMMENT '사용처1',
    USARL2  VARCHAR(100) DEFAULT ' '           COMMENT '사용처2',
    USARL3  VARCHAR(100) DEFAULT ' '           COMMENT '사용처3',
    USARL4  VARCHAR(100) DEFAULT ' '           COMMENT '사용처4',
    USARL5  VARCHAR(100) DEFAULT ' '           COMMENT '사용처5',
    SYONLY  VARCHAR(1)   DEFAULT ' '           COMMENT '시스템전용',
    CREDAT  VARCHAR(8)   DEFAULT ' '           COMMENT '생성일자',
    CRETIM  VARCHAR(6)   DEFAULT ' '           COMMENT '생성시간',
    CREUSR  VARCHAR(12)  DEFAULT ' '           COMMENT '생성자',
    LMODAT  VARCHAR(8)   DEFAULT ' '           COMMENT '수정일자',
    LMOTIM  VARCHAR(6)   DEFAULT ' '           COMMENT '수정시간',
    LMOUSR  VARCHAR(12)  DEFAULT ' '           COMMENT '수정자',
    INDBZL  VARCHAR(1)   DEFAULT ' '           COMMENT '삭제플래그',
    INDARC  VARCHAR(1)   DEFAULT ' '           COMMENT '아카이브플래그',
    UPDCHK  INT          DEFAULT 0             COMMENT '변경카운터',
    PRIMARY KEY (CMCDKY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='공통코드 마스터';


-- ── 공통코드 값 ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS CMCDV (
    CMCDKY  VARCHAR(30)  NOT NULL              COMMENT '코드키 (FK)',
    CMCDVL  VARCHAR(30)  NOT NULL              COMMENT '코드값',
    CDESC1  VARCHAR(100) DEFAULT ' '          COMMENT '설명1',
    CDESC2  VARCHAR(100) DEFAULT ' '          COMMENT '설명2',
    USARG1  VARCHAR(100) DEFAULT ' '          COMMENT '사용인수1',
    USARG2  VARCHAR(100) DEFAULT ' '          COMMENT '사용인수2',
    USARG3  VARCHAR(100) DEFAULT ' '          COMMENT '사용인수3',
    USARG4  VARCHAR(100) DEFAULT ' '          COMMENT '사용인수4',
    USARG5  VARCHAR(100) DEFAULT ' '          COMMENT '사용인수5',
    CREDAT  VARCHAR(8)   DEFAULT ' '          COMMENT '생성일자',
    CRETIM  VARCHAR(6)   DEFAULT ' '          COMMENT '생성시간',
    CREUSR  VARCHAR(12)  DEFAULT ' '          COMMENT '생성자',
    LMODAT  VARCHAR(8)   DEFAULT ' '          COMMENT '수정일자',
    LMOTIM  VARCHAR(6)   DEFAULT ' '          COMMENT '수정시간',
    LMOUSR  VARCHAR(12)  DEFAULT ' '          COMMENT '수정자',
    INDBZL  VARCHAR(1)   DEFAULT ' '          COMMENT '삭제플래그',
    INDARC  VARCHAR(1)   DEFAULT ' '          COMMENT '아카이브플래그',
    UPDCHK  INT          DEFAULT 0             COMMENT '변경카운터',
    PRIMARY KEY (CMCDKY, CMCDVL),
    CONSTRAINT FK_CMCDV_CMCDM FOREIGN KEY (CMCDKY) REFERENCES CMCDM(CMCDKY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='공통코드 값';


-- ── 물류센터(창고) 마스터 ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS WAHMA (
    WAREKY  VARCHAR(10)  NOT NULL              COMMENT '창고코드 (PK)',
    COMPKY  VARCHAR(10)  DEFAULT ' '          COMMENT '회사코드',
    TSPKEY  VARCHAR(10)  DEFAULT ' '          COMMENT '운송회사',
    DELMAK  VARCHAR(1)   DEFAULT ' '          COMMENT '삭제플래그',
    CHKSHA  VARCHAR(1)   DEFAULT ' '          COMMENT '체크섀도',
    NAME01  VARCHAR(100) DEFAULT ' '          COMMENT '창고명1',
    NAME02  VARCHAR(100) DEFAULT ' '          COMMENT '창고명2',
    NAME03  VARCHAR(100) DEFAULT ' '          COMMENT '창고명3',
    ADDR01  VARCHAR(100) DEFAULT ' '          COMMENT '주소1',
    ADDR02  VARCHAR(100) DEFAULT ' '          COMMENT '주소2',
    ADDR03  VARCHAR(100) DEFAULT ' '          COMMENT '주소3',
    ADDR04  VARCHAR(100) DEFAULT ' '          COMMENT '주소4',
    ADDR05  VARCHAR(100) DEFAULT ' '          COMMENT '주소5',
    CITY01  VARCHAR(50)  DEFAULT ' '          COMMENT '도시',
    REGN01  VARCHAR(20)  DEFAULT ' '          COMMENT '지역',
    POSTCD  VARCHAR(10)  DEFAULT ' '          COMMENT '우편번호',
    NATNKY  VARCHAR(3)   DEFAULT ' '          COMMENT '국가코드',
    TELN01  VARCHAR(20)  DEFAULT ' '          COMMENT '전화1',
    TELN02  VARCHAR(20)  DEFAULT ' '          COMMENT '전화2',
    TELN03  VARCHAR(20)  DEFAULT ' '          COMMENT '전화3',
    CREDAT  VARCHAR(8)   DEFAULT '00000000'   COMMENT '생성일자',
    CRETIM  VARCHAR(6)   DEFAULT '000000'     COMMENT '생성시간',
    CREUSR  VARCHAR(12)  DEFAULT ' '          COMMENT '생성자',
    LMODAT  VARCHAR(8)   DEFAULT '00000000'   COMMENT '수정일자',
    LMOTIM  VARCHAR(6)   DEFAULT '000000'     COMMENT '수정시간',
    LMOUSR  VARCHAR(12)  DEFAULT ' '          COMMENT '수정자',
    INDBZL  VARCHAR(1)   DEFAULT ' '          COMMENT '삭제플래그',
    INDARC  VARCHAR(1)   DEFAULT ' '          COMMENT '아카이브플래그',
    UPDCHK  INT          DEFAULT 0             COMMENT '변경카운터',
    PRIMARY KEY (WAREKY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='물류센터(창고) 마스터';
