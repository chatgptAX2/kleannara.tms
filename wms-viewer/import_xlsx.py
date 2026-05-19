#!/usr/bin/env python3
"""
대용량 xlsx → SQLite 임포트 스크립트
용도: SHPDH_202604.xlsx, SHPDI_202604.xlsx 데이터 추가 삽입 (기존 데이터 유지 + 중복 방지)
"""
import sqlite3, openpyxl, sys, time

def import_table(xlsx_path, table_name, db_path, pk_cols, batch=2000):
    print(f"\n{'='*60}")
    print(f"[{table_name}] 임포트 시작: {xlsx_path}")
    t0 = time.time()

    # 1) xlsx 헤더 읽기
    wb = openpyxl.load_workbook(xlsx_path, read_only=True, data_only=True)
    ws = wb.active
    headers = [ws.cell(1, c).value for c in range(1, ws.max_column + 1)]
    total_rows = ws.max_row - 1  # 헤더 제외
    print(f"  엑셀 컬럼: {len(headers)}개 / 데이터행: {total_rows:,}행")

    # 2) DB 컬럼 확인
    con = sqlite3.connect(db_path)
    con.execute("PRAGMA journal_mode=WAL")
    con.execute("PRAGMA synchronous=NORMAL")
    cur = con.cursor()
    cur.execute(f"PRAGMA table_info({table_name})")
    db_cols = [r[1] for r in cur.fetchall()]
    print(f"  DB 컬럼: {len(db_cols)}개")

    # 엑셀에 있고 DB에도 있는 컬럼만 사용
    use_cols = [c for c in headers if c in db_cols]
    use_idx  = [headers.index(c) for c in use_cols]
    print(f"  매핑 컬럼: {len(use_cols)}개")

    # 기존 건수
    cur.execute(f"SELECT COUNT(*) FROM {table_name}")
    before = cur.fetchone()[0]
    print(f"  기존 건수: {before:,}건")

    # 기존 PK 세트 (중복 방지)
    pk_idx_excel = [headers.index(p) for p in pk_cols if p in headers]
    print(f"  PK 컬럼: {pk_cols}")
    cur.execute(f"SELECT {','.join(pk_cols)} FROM {table_name}")
    existing_pks = set(tuple(str(r[i]) for i in range(len(pk_cols))) for r in cur.fetchall())
    print(f"  기존 PK 세트: {len(existing_pks):,}개")

    # 3) INSERT OR IGNORE 방식으로 삽입
    placeholders = ','.join(['?'] * len(use_cols))
    sql = f"INSERT OR IGNORE INTO {table_name} ({','.join(use_cols)}) VALUES ({placeholders})"

    import datetime
    def clean(v):
        if v is None: return None
        if isinstance(v, str): return v.strip() if v.strip() else None
        if isinstance(v, datetime.datetime): return v.strftime('%Y%m%d%H%M%S')
        if isinstance(v, datetime.date):     return v.strftime('%Y%m%d')
        if isinstance(v, datetime.time):     return v.strftime('%H%M%S')
        return v

    inserted = 0
    skipped  = 0
    rows_buf = []

    for row_num, row in enumerate(ws.iter_rows(min_row=2, values_only=True), start=1):
        # PK 중복 체크
        pk_val = tuple(str(row[i]) if row[i] is not None else '' for i in pk_idx_excel)
        if pk_val in existing_pks:
            skipped += 1
            continue

        values = [clean(row[i]) for i in use_idx]
        rows_buf.append(values)
        existing_pks.add(pk_val)

        if len(rows_buf) >= batch:
            cur.executemany(sql, rows_buf)
            con.commit()
            inserted += len(rows_buf)
            rows_buf = []
            elapsed = time.time() - t0
            pct = row_num / total_rows * 100
            print(f"  진행: {row_num:,}/{total_rows:,} ({pct:.1f}%) | 삽입: {inserted:,} | 스킵: {skipped:,} | {elapsed:.1f}s")

    if rows_buf:
        cur.executemany(sql, rows_buf)
        con.commit()
        inserted += len(rows_buf)

    wb.close()

    cur.execute(f"SELECT COUNT(*) FROM {table_name}")
    after = cur.fetchone()[0]
    con.close()

    elapsed = time.time() - t0
    print(f"\n  ✅ 완료!")
    print(f"  기존: {before:,}건 → 최종: {after:,}건")
    print(f"  삽입: {inserted:,}건 | 스킵(중복): {skipped:,}건")
    print(f"  소요시간: {elapsed:.1f}초")
    return inserted

if __name__ == '__main__':
    DB = '/home/user/webapp/wms-viewer/wms.db'
    BASE = '/home/user/uploaded_files'

    results = {}

    # SHPDI: PK = SHPOKY + SHPOIT
    results['SHPDI'] = import_table(
        f'{BASE}/SHPDI_202604.xlsx', 'SHPDI', DB,
        pk_cols=['SHPOKY', 'SHPOIT']
    )

    # SHPDH: PK = SHPOKY
    results['SHPDH'] = import_table(
        f'{BASE}/SHPDH_202604.xlsx', 'SHPDH', DB,
        pk_cols=['SHPOKY']
    )

    print(f"\n{'='*60}")
    print("전체 완료 요약:")
    for t, n in results.items():
        print(f"  {t}: {n:,}건 삽입")
