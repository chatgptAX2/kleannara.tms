-- ============================================================
-- TMS 배차최적화 제약조건 관리 DDL
-- 생성일  : 2026-07-15
-- 수정일  : 2026-07-15 (MySQL TEXT→VARCHAR PK 오류 수정)
-- 참조    : TMS 배차최적화 제약조건_20260611.xlsx
-- 대상 DB : MySQL 5.7+ / MariaDB 10.3+
--            (SQLite 사용 시 VARCHAR → TEXT 변경 가능)
-- ============================================================

-- ① DS_DISPATCH_CONST_ITEM : 제약조건 항목 마스터
--    엑셀 §1(공통5) §2(원지14) §3(판지11) §4(혼합적재6) — 총 36건
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS DS_DISPATCH_CONST_ITEM (

    ITEM_CD      VARCHAR(50)   NOT NULL,
    -- 항목 고유 코드 (영문대문자+숫자+언더스코어)
    -- 예) ENTRY_TON_LIMIT, ROLL_MAX_TIER, MIX_LIFO_ENABLE

    ITEM_NM      VARCHAR(200)  NOT NULL,
    -- 항목 표시명 (UI 목록에 표시되는 이름)

    ITEM_GRP     VARCHAR(20)   NOT NULL,
    -- 제약조건 그룹 구분 코드
    -- COMMON : 공통 제약    (엑셀 §1)
    -- ROLL   : 원지 제약    (엑셀 §2)
    -- BOARD  : 판지 제약    (엑셀 §3)
    -- MIX    : 혼합적재 제약(엑셀 §4)

    ITEM_TYPE    VARCHAR(10)   NOT NULL  DEFAULT 'YN',
    -- 값 입력 유형 코드
    -- YN     : Y / N 두 가지 선택
    -- NUM    : 숫자 (정수 또는 실수)
    -- TEXT   : 자유 문자열
    -- SELECT : 사전 정의 선택목록 (SELECT_OPTS 컬럼 참조)
    -- CSV    : 콤마 구분 숫자 열  (차종별 기준수 등)

    DEFAULT_VAL  VARCHAR(500)  DEFAULT 'Y',
    -- 기본값 (세트 설정이 없을 때 사용)

    UNIT         VARCHAR(20)   DEFAULT '',
    -- 단위 표시 문자열 (예: %, m, ton, 단, 롤)

    CONST_OP     VARCHAR(10)   DEFAULT '=',
    -- 비교 연산자 코드
    -- =  : 같음
    -- <= : 이하
    -- >= : 이상
    -- IN : 목록 포함

    SORT_SEQ     INT           DEFAULT 0,
    -- UI 목록 정렬 순서 (숫자 오름차순)

    DESCRIPTION  VARCHAR(1000) DEFAULT '',
    -- 항목 상세 설명 (UI 툴팁 / 도움말)

    SOURCE_REF   VARCHAR(20)   DEFAULT '',
    -- 엑셀 원문 참조 위치 (예: §1-1, §2-2, §4-3)

    ACTIVE_YN    CHAR(1)       DEFAULT 'Y',
    -- 항목 활성 여부
    -- Y : 활성 (UI 목록 표시)
    -- N : 비활성 (UI 목록 미표시)

    SELECT_OPTS  VARCHAR(500)  DEFAULT NULL,
    -- ITEM_TYPE = SELECT 인 경우 선택 가능한 값 목록 (JSON 배열)
    -- 예) '["SPLIT","REJECT"]'  /  '["EA","PLT"]'

    CREDAT       CHAR(8)       DEFAULT NULL,
    -- 최초 생성일 (YYYYMMDD)

    LMODAT       CHAR(8)       DEFAULT NULL,
    -- 최종 수정일 (YYYYMMDD)

    CONSTRAINT PK_DS_DISPATCH_CONST_ITEM PRIMARY KEY (ITEM_CD)
);

-- ② DS_DISPATCH_CONST_SETTING : 세트별 항목 선택 / 설정값 오버라이드
--    DS_DISPATCH_CONST_SET (세트) × DS_DISPATCH_CONST_ITEM (항목) 교차 테이블
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS DS_DISPATCH_CONST_SETTING (

    SETTING_ID   INT           NOT NULL  AUTO_INCREMENT,
    -- 설정 레코드 고유 식별자 (자동 증가)

    SET_ID       INT           NOT NULL,
    -- 연결된 제약조건 세트 ID (DS_DISPATCH_CONST_SET.SET_ID 참조)

    ITEM_CD      VARCHAR(50)   NOT NULL,
    -- 연결된 항목 코드 (DS_DISPATCH_CONST_ITEM.ITEM_CD 참조)

    USE_YN       CHAR(1)       NOT NULL  DEFAULT 'Y',
    -- 이 세트에서 항목 사용 여부
    -- Y : 적용 (배차 엔진이 이 항목을 참조)
    -- N : 미적용

    SETTING_VAL  VARCHAR(500)  DEFAULT NULL,
    -- 설정값 오버라이드
    -- NULL 이면 DS_DISPATCH_CONST_ITEM.DEFAULT_VAL 을 기본값으로 사용

    NOTE         VARCHAR(500)  DEFAULT '',
    -- 이 세트에서의 비고 / 메모

    LMODAT       CHAR(8)       DEFAULT NULL,
    -- 최종 수정일 (YYYYMMDD)

    CONSTRAINT PK_DS_DISPATCH_CONST_SETTING  PRIMARY KEY (SETTING_ID),
    CONSTRAINT UQ_DCON_SETTING_SET_ITEM      UNIQUE      (SET_ID, ITEM_CD),
    CONSTRAINT FK_DCON_SETTING_ITEM
        FOREIGN KEY (ITEM_CD)
        REFERENCES DS_DISPATCH_CONST_ITEM (ITEM_CD)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

-- ③ 인덱스
-- ------------------------------------------------------------

-- 그룹별 목록 조회 (ITEM_GRP 필터)
CREATE INDEX IF NOT EXISTS IDX_DCON_ITEM_GRP
    ON DS_DISPATCH_CONST_ITEM (ITEM_GRP);

-- 활성 항목만 조회 (ACTIVE_YN 필터)
CREATE INDEX IF NOT EXISTS IDX_DCON_ITEM_ACTIVE
    ON DS_DISPATCH_CONST_ITEM (ACTIVE_YN);

-- 정렬 순서 인덱스
CREATE INDEX IF NOT EXISTS IDX_DCON_ITEM_SEQ
    ON DS_DISPATCH_CONST_ITEM (SORT_SEQ);

-- 세트 ID 기준 설정 조회
CREATE INDEX IF NOT EXISTS IDX_DCON_SETTING_SET
    ON DS_DISPATCH_CONST_SETTING (SET_ID);

-- 항목 코드 기준 설정 조회
CREATE INDEX IF NOT EXISTS IDX_DCON_SETTING_ITEM
    ON DS_DISPATCH_CONST_SETTING (ITEM_CD);

-- 세트 ID + 사용 여부 복합 인덱스 (활성 항목만 필터링 빈번)
CREATE INDEX IF NOT EXISTS IDX_DCON_SETTING_SET_USE
    ON DS_DISPATCH_CONST_SETTING (SET_ID, USE_YN);
