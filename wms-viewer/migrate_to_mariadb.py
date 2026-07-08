#!/usr/bin/env python3
"""
SQLite(wms.db) → MariaDB 데이터 이관 스크립트
사용법:
  python3 migrate_to_mariadb.py \
    --sqlite wms.db \
    --host 10.2.14.247 --port 3306 \
    --db intergration --user tmsuser --password 패스워드

주의:
  1. MariaDB에 스키마(01_schema.sql) 먼저 적용 후 실행
  2. 중복 데이터는 IGNORE 처리 (기존 MariaDB 데이터 보존)
  3. 이관 순서: 코드 테이블 → 마스터 → 트랜잭션
"""

import argparse
import sqlite3
import logging
import sys

logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(message)s')
logger = logging.getLogger(__name__)


def get_mariadb_conn(host, port, db, user, password):
    try:
        import pymysql
        conn = pymysql.connect(
            host=host, port=int(port), database=db,
            user=user, password=password,
            charset='utf8mb4',
            autocommit=False
        )
        logger.info(f"MariaDB 연결 성공: {host}:{port}/{db}")
        return conn
    except ImportError:
        logger.error("pymysql 미설치: pip3 install pymysql")
        sys.exit(1)
    except Exception as e:
        logger.error(f"MariaDB 연결 실패: {e}")
        sys.exit(1)


# 이관 대상 테이블 (SQLite 테이블명 → MariaDB 테이블명, 이관 순서 중요)
MIGRATE_TABLES = [
    # 공통코드
    ("CMCDM",                    "CMCDM"),
    ("CMCDV",                    "CMCDV"),
    # 물류센터/납품처/SKU 마스터
    ("WAHMA",                    "WAHMA"),
    ("SKUMA",                    "SKUMA"),
    ("BZPTN",                    "BZPTN"),
    ("MEASI",                    "MEASI"),
    # 출고문서 (WMS에서 받은 데이터, 참조만)
    ("SHPDH",                    "SHPDH"),
    ("SHPDI",                    "SHPDI"),
    # 차량/납품처 상세
    ("DS_VEHICLE",               "DS_VEHICLE"),
    ("BZPTN_DETAIL",             "BZPTN_DETAIL"),
    ("VHCMA",                    "VHCMA"),
    # 운송비
    ("ROUTE_COST",               "ROUTE_COST"),
    # 배차전략
    ("DS_INCH12",                "DS_INCH12"),
    ("DS_INCH3",                 "DS_INCH3"),
    # 배차설정 (목적식 → 프로파일 → 제약)
    ("DS_DISPATCH_OBJECTIVE",    "DS_DISPATCH_OBJECTIVE"),
    ("DS_DISPATCH_CONST_SET",    "DS_DISPATCH_CONST_SET"),
    ("DS_DISPATCH_PROFILE",      "ds_dispatch_profile"),
    ("DS_DISPATCH_CONST",        "DS_DISPATCH_CONST"),
    ("DS_DISPATCH_CONST_SET_ITEM","ds_dispatch_const_set_item"),
    # PS 배차
    ("PS_DISPATCH_H",            "PS_DISPATCH_H"),
    ("PS_DISPATCH_D",            "PS_DISPATCH_D"),
    ("PS_DISPATCH_SPLIT",        "PS_DISPATCH_SPLIT"),
    # 서류
    ("DOC_FOLDER",               "DOC_FOLDER"),
    ("DOC_FILE",                 "DOC_FILE"),
    # 기타
    ("RECDI",                    "RECDI"),
    ("IFWMS113",                 "IFWMS113"),
]

# MariaDB에서 컬럼명 차이가 있는 경우 매핑 (SQLite컬럼 → MariaDB컬럼)
COLUMN_REMAP = {
    # DS_DISPATCH_CONST: SQLite의 OBJECTIVE → MariaDB의 OBJECTIVE (동일)
    # DS_DISPATCH_PROFILE: SQLite에 OBJECTIVE 있음
}


def migrate_table(sqlite_conn, maria_conn, sqlite_tbl, maria_tbl):
    """단일 테이블 이관"""
    cursor_s = sqlite_conn.cursor()
    cursor_m = maria_conn.cursor()

    # SQLite 레코드 수 확인
    try:
        cursor_s.execute(f"SELECT COUNT(*) FROM {sqlite_tbl}")
        total = cursor_s.fetchone()[0]
        if total == 0:
            logger.info(f"  {sqlite_tbl}: 데이터 없음 (SKIP)")
            return 0
    except Exception as e:
        logger.warning(f"  {sqlite_tbl}: 테이블 없음 또는 오류 ({e}) (SKIP)")
        return 0

    # 컬럼 목록 조회
    cursor_s.execute(f"PRAGMA table_info({sqlite_tbl})")
    columns = [row[1] for row in cursor_s.fetchall()]

    # 전체 조회
    cursor_s.execute(f"SELECT * FROM {sqlite_tbl}")
    rows = cursor_s.fetchall()

    if not rows:
        logger.info(f"  {sqlite_tbl}: 데이터 없음 (SKIP)")
        return 0

    # MariaDB INSERT IGNORE
    placeholders = ', '.join(['%s'] * len(columns))
    col_names    = ', '.join([f'`{c}`' for c in columns])
    sql = f"INSERT IGNORE INTO `{maria_tbl}` ({col_names}) VALUES ({placeholders})"

    inserted = 0
    batch_size = 500
    for i in range(0, len(rows), batch_size):
        batch = rows[i:i + batch_size]
        try:
            cursor_m.executemany(sql, batch)
            maria_conn.commit()
            inserted += cursor_m.rowcount
        except Exception as e:
            maria_conn.rollback()
            logger.error(f"  {sqlite_tbl}: 배치 INSERT 실패 ({e})")
            # 개별 행 시도
            for row in batch:
                try:
                    cursor_m.execute(sql, row)
                    maria_conn.commit()
                    inserted += 1
                except Exception as row_e:
                    maria_conn.rollback()
                    logger.debug(f"  행 SKIP: {row_e}")

    logger.info(f"  {sqlite_tbl} → {maria_tbl}: {total}건 중 {inserted}건 이관")
    return inserted


def run_migration(sqlite_path, maria_host, maria_port, maria_db, maria_user, maria_pass):
    logger.info("=" * 60)
    logger.info("SQLite → MariaDB 데이터 이관 시작")
    logger.info(f"  SQLite: {sqlite_path}")
    logger.info(f"  MariaDB: {maria_host}:{maria_port}/{maria_db}")
    logger.info("=" * 60)

    # 연결
    sqlite_conn = sqlite3.connect(sqlite_path)
    sqlite_conn.row_factory = sqlite3.Row
    maria_conn  = get_mariadb_conn(maria_host, maria_port, maria_db, maria_user, maria_pass)

    total_inserted = 0
    for sqlite_tbl, maria_tbl in MIGRATE_TABLES:
        n = migrate_table(sqlite_conn, maria_conn, sqlite_tbl, maria_tbl)
        total_inserted += n

    sqlite_conn.close()
    maria_conn.close()

    logger.info("=" * 60)
    logger.info(f"이관 완료: 총 {total_inserted}건 MariaDB 적재")
    logger.info("=" * 60)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='SQLite → MariaDB 이관')
    parser.add_argument('--sqlite',   required=True,  help='SQLite DB 경로 (wms.db)')
    parser.add_argument('--host',     required=True,  help='MariaDB 호스트')
    parser.add_argument('--port',     default=3306,   help='MariaDB 포트')
    parser.add_argument('--db',       required=True,  help='MariaDB 데이터베이스명')
    parser.add_argument('--user',     required=True,  help='MariaDB 계정')
    parser.add_argument('--password', required=True,  help='MariaDB 패스워드')
    args = parser.parse_args()

    run_migration(
        args.sqlite,
        args.host, args.port, args.db,
        args.user, args.password
    )
