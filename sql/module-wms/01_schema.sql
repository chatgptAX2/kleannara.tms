-- ============================================================
-- module-wms: DDL
-- WMS 뷰어, 공통코드, 물류센터, 출고문서, 배차전략 스키마
-- Flask SQLite(wms.db) → MariaDB 완전 이관
-- ============================================================

-- ── 공통코드 마스터 ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS CMCDM (
    CMCDKY  VARCHAR(30)  NOT NULL COMMENT '코드키',
    CDESC0  VARCHAR(100)          COMMENT '코드 설명',
    DATEAT  VARCHAR(8)            COMMENT '생성일자',
    ACTIVE  VARCHAR(1)   DEFAULT 'Y' COMMENT '활성여부',
    PRIMARY KEY (CMCDKY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='공통코드 마스터';

-- ── 공통코드 상세 ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS CMCDV (
    CMCDKY  VARCHAR(30)  NOT NULL COMMENT '코드키',
    CMCDVL  VARCHAR(30)  NOT NULL COMMENT '코드값',
    CDESC1  VARCHAR(100)          COMMENT '코드설명1',
    CDESC2  VARCHAR(100)          COMMENT '코드설명2',
    USARG1  VARCHAR(100)          COMMENT '사용자정의1',
    USARG2  VARCHAR(100)          COMMENT '사용자정의2',
    USARG3  VARCHAR(100)          COMMENT '사용자정의3 (우편번호FROM)',
    USARG4  VARCHAR(100)          COMMENT '사용자정의4 (우편번호TO)',
    USARG5  VARCHAR(100)          COMMENT '사용자정의5',
    DATEAT  VARCHAR(8)            COMMENT '생성일자',
    SORTNO  INT          DEFAULT 0 COMMENT '정렬순서',
    PRIMARY KEY (CMCDKY, CMCDVL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='공통코드 상세';

-- ── 물류센터 마스터 ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS WAHMA (
    WAREKY  VARCHAR(10)  NOT NULL COMMENT '물류센터키',
    WARENM  VARCHAR(100)          COMMENT '물류센터명',
    WADDR1  VARCHAR(200)          COMMENT '주소1',
    WADDR2  VARCHAR(200)          COMMENT '주소2',
    POSTCD  VARCHAR(10)           COMMENT '우편번호',
    TELNO   VARCHAR(20)           COMMENT '전화번호',
    FAXNO   VARCHAR(20)           COMMENT '팩스번호',
    USARG1  VARCHAR(100)          COMMENT '사용자정의1',
    USARG2  VARCHAR(100)          COMMENT '사용자정의2',
    USARG3  VARCHAR(100)          COMMENT '사용자정의3',
    ACTIVE  VARCHAR(1)   DEFAULT 'Y' COMMENT '활성여부',
    CREDAT  VARCHAR(8)            COMMENT '생성일자',
    LMODAT  VARCHAR(8)            COMMENT '수정일자',
    PRIMARY KEY (WAREKY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='물류센터 마스터';

-- ── SKU 마스터 ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS SKUMA (
    SKUKEY  VARCHAR(30)  NOT NULL COMMENT 'SKU키',
    SKUNM   VARCHAR(200)          COMMENT 'SKU명',
    MTYPE   VARCHAR(10)           COMMENT '자재유형 (P=평판,R=원지)',
    GRSWGT  DECIMAL(15,4)         COMMENT '총중량(단품)',
    ASKL01  VARCHAR(50)           COMMENT '규격01 (g/m² 평량)',
    ASKL02  VARCHAR(50)           COMMENT '규격02 (폭mm)',
    ASKL03  VARCHAR(50)           COMMENT '규격03 (높이mm)',
    ASKL04  VARCHAR(50)           COMMENT '규격04 (폭mm 판지)',
    ASKL05  VARCHAR(50)           COMMENT '규격05 (길이mm 판지)',
    CUBICM  DECIMAL(15,6)         COMMENT '부피(m³)',
    UOMKEY  VARCHAR(10)           COMMENT '기본단위',
    PRIMARY KEY (SKUKEY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='SKU 마스터';

-- ── 납품처 마스터 ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS BZPTN (
    PTNRKY  VARCHAR(20)  NOT NULL COMMENT '납품처키',
    PTNRTY  VARCHAR(10)  NOT NULL COMMENT '납품처유형 (CT=고객)',
    OWNRKY  VARCHAR(10)           COMMENT '소유자키',
    NAME01  VARCHAR(200)          COMMENT '납품처명',
    ADDR01  VARCHAR(200)          COMMENT '주소1',
    ADDR02  VARCHAR(200)          COMMENT '주소2',
    POSTCD  VARCHAR(10)           COMMENT '우편번호',
    TELNO   VARCHAR(20)           COMMENT '전화번호',
    FAXNO   VARCHAR(20)           COMMENT '팩스번호',
    USARG1  VARCHAR(100)          COMMENT '사용자정의1',
    USARG2  VARCHAR(100)          COMMENT '사용자정의2',
    ACTIVE  VARCHAR(1)   DEFAULT 'Y' COMMENT '활성여부',
    PRIMARY KEY (PTNRKY, PTNRTY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='납품처 마스터';

-- ── 단위 마스터 ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS MEASI (
    MEASKY  VARCHAR(10)  NOT NULL COMMENT '단위키',
    MEASNM  VARCHAR(50)           COMMENT '단위명',
    MEASTP  VARCHAR(10)           COMMENT '단위유형',
    PRIMARY KEY (MEASKY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='단위 마스터';

-- ── 출고문서 헤더 ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS SHPDH (
    SHPOKY  VARCHAR(30)  NOT NULL COMMENT '출고문서키',
    WAREKY  VARCHAR(10)           COMMENT '물류센터키',
    DPTNKY  VARCHAR(20)           COMMENT '납품처키',
    DPTNM   VARCHAR(200)          COMMENT '납품처명',
    RQSHPD  VARCHAR(8)            COMMENT '요청출고일자',
    STATUS  VARCHAR(10)           COMMENT '상태',
    USARG1  VARCHAR(100)          COMMENT '사용자정의1',
    USARG2  VARCHAR(100)          COMMENT '사용자정의2',
    CREDAT  VARCHAR(8)            COMMENT '생성일자',
    PRIMARY KEY (SHPOKY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='출고문서 헤더';

-- ── 출고문서 상세 ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS SHPDI (
    SHPOKY  VARCHAR(30)  NOT NULL COMMENT '출고문서키',
    SHPOIT  INT          NOT NULL COMMENT '출고문서 아이템 번호',
    SKUKEY  VARCHAR(30)           COMMENT 'SKU키',
    SKUNM   VARCHAR(200)          COMMENT 'SKU명',
    DESC01  VARCHAR(200)          COMMENT '품목설명',
    QTSHPO  DECIMAL(15,4)         COMMENT '출고수량',
    UOMKEY  VARCHAR(10)           COMMENT '단위키',
    GRSWGT  DECIMAL(15,4)         COMMENT '총중량',
    WGTUNT  VARCHAR(10)           COMMENT '중량단위',
    LENGTH  DECIMAL(15,4)         COMMENT '길이(mm)',
    WIDTHW  DECIMAL(15,4)         COMMENT '폭(mm)',
    HEIGHT  DECIMAL(15,4)         COMMENT '높이(mm)',
    PRIMARY KEY (SHPOKY, SHPOIT)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='출고문서 상세';

-- ── WMS IF 113 ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS IFWMS113 (
    SEQ     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '순번',
    WAREKY  VARCHAR(10)           COMMENT '물류센터키',
    SHPOKY  VARCHAR(30)           COMMENT '출고문서키',
    SKUKEY  VARCHAR(30)           COMMENT 'SKU키',
    QTSHPO  DECIMAL(15,4)         COMMENT '수량',
    CREDAT  VARCHAR(8)            COMMENT '생성일자',
    CRETIM  VARCHAR(6)            COMMENT '생성시간',
    STATUS  VARCHAR(10)           COMMENT '처리상태',
    PRIMARY KEY (SEQ)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='WMS 인터페이스 113';

-- ── 배차전략 12인치 ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS DS_INCH12 (
    ID          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID (PK)',
    CARTYPE     VARCHAR(50)  NOT NULL COMMENT '차종명',
    GRM         INT          NOT NULL COMMENT '평량(g/m²)',
    MAX_ROLLS   INT          DEFAULT 0 COMMENT '최대 롤 수',
    CREDAT      VARCHAR(8)            COMMENT '생성일자',
    LMODAT      VARCHAR(8)            COMMENT '수정일자',
    PRIMARY KEY (ID),
    UNIQUE KEY UK_DS_INCH12 (CARTYPE, GRM)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차전략 12인치 롤 적재기준';

-- ── 배차전략 3인치 ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS DS_INCH3 (
    ID          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID (PK)',
    CARTYPE     VARCHAR(50)  NOT NULL COMMENT '차종명',
    GRM         INT          NOT NULL COMMENT '평량(g/m²)',
    MAX_ROLLS   INT          DEFAULT 0 COMMENT '최대 롤 수',
    CREDAT      VARCHAR(8)            COMMENT '생성일자',
    LMODAT      VARCHAR(8)            COMMENT '수정일자',
    PRIMARY KEY (ID),
    UNIQUE KEY UK_DS_INCH3 (CARTYPE, GRM)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차전략 3인치 롤 적재기준';

-- ── PS 배차 분할 ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS PS_DISPATCH_SPLIT (
    SPLIT_ID    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '분할 ID (PK)',
    DISP_H_ID   BIGINT       NOT NULL COMMENT '배차 헤더 ID (FK)',
    ORIG_ITEM   VARCHAR(30)           COMMENT '원본 아이템 번호',
    SPLIT_SEQ   INT          DEFAULT 1 COMMENT '분할 순번',
    SKUKEY      VARCHAR(30)           COMMENT 'SKU키',
    QTSHPO      DECIMAL(15,4)         COMMENT '분할 수량',
    KG_WEIGHT   DECIMAL(15,4)         COMMENT '분할 중량(kg)',
    NOTE        VARCHAR(200)          COMMENT '비고',
    CREDAT      VARCHAR(8)            COMMENT '생성일자',
    CRETIM      VARCHAR(6)            COMMENT '생성시간',
    PRIMARY KEY (SPLIT_ID),
    INDEX IDX_PS_DISPATCH_SPLIT_H (DISP_H_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='PS 배차 분할 상세';

-- ── 수령자 정보 ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS RECDI (
    SEQ     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '순번 (PK)',
    SHPOKY  VARCHAR(30)           COMMENT '출고문서키',
    RECNM   VARCHAR(100)          COMMENT '수령자명',
    RECTEL  VARCHAR(20)           COMMENT '수령자연락처',
    RECADDR VARCHAR(200)          COMMENT '수령자주소',
    CREDAT  VARCHAR(8)            COMMENT '생성일자',
    CRETIM  VARCHAR(6)            COMMENT '생성시간',
    PRIMARY KEY (SEQ),
    INDEX IDX_RECDI_SHPOKY (SHPOKY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='수령자 정보';

-- ── DS_DISPATCH_CONST (Flask 실제 스키마 - 기존 MariaDB 스키마와 다름) ──
-- 주의: 기존 module-dispatch-config/01_schema.sql의 ds_dispatch_constraint와
--       ds_dispatch_const_set_item이 Flask 실제 사용 스키마와 다름 → 재작성
CREATE TABLE IF NOT EXISTS DS_DISPATCH_CONST (
    CONST_ID    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '제약 ID (PK)',
    PROFILE_ID  BIGINT       NOT NULL COMMENT '프로파일 ID (FK)',
    CONST_TYPE  VARCHAR(30)           COMMENT '제약 유형 (GLOBAL/VEHICLE/PARTNER/CARGO/COST/CARTYPE)',
    CONST_KEY   VARCHAR(50)           COMMENT '제약 키',
    CONST_VALUE VARCHAR(200)          COMMENT '제약 값',
    CONST_OP    VARCHAR(10)  DEFAULT '<=' COMMENT '비교 연산자 (<=/>=/=/etc)',
    TARGET_ID   VARCHAR(50)           COMMENT '대상 ID (차종코드/납품처코드 등)',
    TARGET_NM   VARCHAR(100)          COMMENT '대상명',
    ACTIVE_YN   VARCHAR(1)   DEFAULT 'Y' COMMENT '활성 여부',
    NOTE        VARCHAR(200)          COMMENT '비고',
    SORT_SEQ    INT          DEFAULT 0 COMMENT '정렬순서',
    CREDAT      VARCHAR(8)            COMMENT '생성일자',
    LMODAT      VARCHAR(8)            COMMENT '수정일자',
    PRIMARY KEY (CONST_ID),
    INDEX IDX_DS_DISPATCH_CONST_PROFILE (PROFILE_ID),
    INDEX IDX_DS_DISPATCH_CONST_TYPE (CONST_TYPE, CONST_KEY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차제약 상세 (Flask 실제 스키마)';

-- ── DS_DISPATCH_PROFILE 수정 (OBJECTIVE 컬럼 추가) ──────────────
-- 기존 ds_dispatch_profile에 OBJECTIVE 컬럼이 없을 경우 추가 필요
-- Flask 스키마: DS_DISPATCH_PROFILE(PROFILE_ID, PROFILE_NM, OBJECTIVE, ACTIVE_YN, SET_ID, NOTE, CREDAT, LMODAT)
ALTER TABLE ds_dispatch_profile
    ADD COLUMN IF NOT EXISTS OBJECTIVE VARCHAR(50) DEFAULT 'MIN_VEHICLES' COMMENT '목적식 코드',
    ADD COLUMN IF NOT EXISTS NOTE      VARCHAR(200)                        COMMENT '비고';

-- ── DS_DISPATCH_CONST_SET_ITEM 재정의 (Flask 실제 스키마) ────────
-- Flask: (ITEM_ID, SET_ID, CONST_ID, ACTIVE_YN, PARAM_VALUE)
-- 기존 MariaDB: (ITEM_ID, SET_ID, ITEM_KEY, ITEM_VAL, ITEM_TYPE, REMARK)
-- → 기존 테이블 삭제 후 재생성 (마이그레이션 필요 시 데이터 백업 후 진행)
DROP TABLE IF EXISTS ds_dispatch_const_set_item;
CREATE TABLE IF NOT EXISTS ds_dispatch_const_set_item (
    ITEM_ID     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '아이템 ID (PK)',
    SET_ID      INT          NOT NULL COMMENT 'SET ID (FK)',
    CONST_ID    BIGINT       NOT NULL COMMENT '제약 ID (FK → DS_DISPATCH_CONST)',
    ACTIVE_YN   VARCHAR(1)   DEFAULT 'Y' COMMENT '활성 여부',
    PARAM_VALUE VARCHAR(200)              COMMENT '파라미터 오버라이드 값',
    PRIMARY KEY (ITEM_ID),
    UNIQUE KEY UK_DS_CONST_SET_ITEM (SET_ID, CONST_ID),
    INDEX IDX_DS_CONST_SET_ITEM (SET_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='배차제약 SET 아이템 (Flask 실제 스키마)';
