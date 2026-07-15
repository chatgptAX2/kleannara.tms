-- ============================================================
-- TMS 배차최적화 제약조건 관리 DDL
-- 생성일: 2026-07-15
-- 참조: TMS 배차최적화 제약조건_20260611.xlsx
-- ============================================================

-- ① DS_DISPATCH_CONST_ITEM: 제약조건 항목 마스터
--    엑셀 §1(공통) §2(원지) §3(판지) §4(혼합적재) 기반
CREATE TABLE IF NOT EXISTS DS_DISPATCH_CONST_ITEM (
    ITEM_CD      TEXT    PRIMARY KEY,
        -- 항목 고유 코드 (영문대문자_숫자)
        -- 예: ENTRY_TON_LIMIT, ROLL_MAX_TIER
    ITEM_NM      TEXT    NOT NULL,
        -- 항목명 (한글 표시명)
    ITEM_GRP     TEXT    NOT NULL,
        -- 제약조건 그룹 구분
        -- COMMON : 공통 제약 (§1 — 납품처 진입 톤수, 고정차량 배차 등)
        -- ROLL   : 원지(Paper Roll) 제약 (§2 — 단위배차, 다단적재, 인치/평량)
        -- BOARD  : 판지(Cardboard) 제약 (§3 — CBM/중량, 벌크/속포장, 분할)
        -- MIX    : 혼합적재 제약 (§4 — Z축 순서, Y축 LIFO, 이중 복합 검증)
    ITEM_TYPE    TEXT    NOT NULL DEFAULT 'YN',
        -- 값 유형:
        -- YN     : Y 또는 N 선택
        -- NUM    : 숫자 (정수 또는 실수)
        -- TEXT   : 자유 문자열
        -- SELECT : 사전 정의된 선택목록 (SELECT_OPTS 참조)
        -- CSV    : 콤마로 구분된 숫자 열 (차종별 기준수 등)
    DEFAULT_VAL  TEXT    DEFAULT 'Y',
        -- 기본값 (세트 설정 없을 때 사용)
    UNIT         TEXT    DEFAULT '',
        -- 단위 표시 문자열 (%, m, ton, 단, 롤 등)
    CONST_OP     TEXT    DEFAULT '=',
        -- 연산자: = / <= / >= / IN / BETWEEN
    SORT_SEQ     INTEGER DEFAULT 0,
        -- 목록 표시 정렬 순서
    DESCRIPTION  TEXT    DEFAULT '',
        -- 상세 설명 (UI 툴팁/도움말)
    SOURCE_REF   TEXT    DEFAULT '',
        -- 엑셀 참조 위치 (§1-1, §2-2 등)
    ACTIVE_YN    TEXT    DEFAULT 'Y',
        -- 항목 활성 여부 (N=비활성=목록 미표시)
    SELECT_OPTS  TEXT    DEFAULT NULL,
        -- ITEM_TYPE=SELECT 시 JSON 배열 문자열
        -- 예: '["SPLIT","REJECT"]'
    CREDAT       TEXT,   -- 생성일 YYYYMMDD
    LMODAT       TEXT    -- 최종수정일 YYYYMMDD
);

-- ② DS_DISPATCH_CONST_SETTING: 세트별 항목 선택/설정값 오버라이드
--    DS_DISPATCH_CONST_SET(세트) 1개 + DS_DISPATCH_CONST_ITEM 1개의 교차 테이블
CREATE TABLE IF NOT EXISTS DS_DISPATCH_CONST_SETTING (
    SETTING_ID   INTEGER PRIMARY KEY AUTOINCREMENT,
    SET_ID       INTEGER NOT NULL,
        -- DS_DISPATCH_CONST_SET.SET_ID 참조
    ITEM_CD      TEXT    NOT NULL,
        -- DS_DISPATCH_CONST_ITEM.ITEM_CD 참조
    USE_YN       TEXT    NOT NULL DEFAULT 'Y',
        -- 이 세트에서 항목 사용 여부 (Y=적용, N=미적용)
    SETTING_VAL  TEXT    DEFAULT NULL,
        -- 설정값 오버라이드
        -- NULL이면 DS_DISPATCH_CONST_ITEM.DEFAULT_VAL 사용
    NOTE         TEXT    DEFAULT '',
        -- 이 세트에서의 비고/메모
    LMODAT       TEXT,   -- 최종수정일 YYYYMMDD
    UNIQUE(SET_ID, ITEM_CD)
        -- 세트 + 항목 조합은 유일
);

-- ③ 인덱스
CREATE INDEX IF NOT EXISTS IDX_DCON_ITEM_GRP
    ON DS_DISPATCH_CONST_ITEM(ITEM_GRP);

CREATE INDEX IF NOT EXISTS IDX_DCON_ITEM_ACTIVE
    ON DS_DISPATCH_CONST_ITEM(ACTIVE_YN);

CREATE INDEX IF NOT EXISTS IDX_DCON_SETTING_SET
    ON DS_DISPATCH_CONST_SETTING(SET_ID);

CREATE INDEX IF NOT EXISTS IDX_DCON_SETTING_ITEM
    ON DS_DISPATCH_CONST_SETTING(ITEM_CD);
