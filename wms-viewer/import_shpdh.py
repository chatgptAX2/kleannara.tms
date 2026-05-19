#!/usr/bin/env python3
import sqlite3, openpyxl, time, datetime

def clean(v):
    if v is None: return None
    if isinstance(v, str): return v.strip() if v.strip() else None
    if isinstance(v, datetime.datetime): return v.strftime('%Y%m%d%H%M%S')
    if isinstance(v, datetime.date):     return v.strftime('%Y%m%d')
    if isinstance(v, datetime.time):     return v.strftime('%H%M%S')
    return v

def import_shpdh():
    xlsx_path  = '/home/user/uploaded_files/SHPDH_202604.xlsx'
    db_path    = '/home/user/webapp/wms-viewer/wms.db'
    table_name = 'SHPDH'
    pk_cols    = ['SHPOKY']
    batch      = 2000
    t0 = time.time()

    print(f"[{table_name}] 임포트 시작")
    wb = openpyxl.load_workbook(xlsx_path, read_only=True, data_only=True)
    ws = wb.active
    headers   = [ws.cell(1, c).value for c in range(1, ws.max_column + 1)]
    total_rows = ws.max_row - 1
    print(f"  엑셀: {len(headers)}컬럼 / {total_rows:,}행")

    con = sqlite3.connect(db_path)
    con.execute("PRAGMA journal_mode=WAL")
    con.execute("PRAGMA synchronous=NORMAL")
    cur = con.cursor()
    cur.execute(f"PRAGMA table_info({table_name})")
    db_cols  = [r[1] for r in cur.fetchall()]
    use_cols = [c for c in headers if c in db_cols]
    use_idx  = [headers.index(c) for c in use_cols]

    cur.execute(f"SELECT COUNT(*) FROM {table_name}")
    before = cur.fetchone()[0]
    print(f"  기존: {before:,}건")

    pk_idx_excel = [headers.index(p) for p in pk_cols]
    cur.execute(f"SELECT {','.join(pk_cols)} FROM {table_name}")
    existing_pks = set(tuple(str(r[i]) for i in range(len(pk_cols))) for r in cur.fetchall())
    print(f"  기존 PK: {len(existing_pks):,}개")

    sql = f"INSERT OR IGNORE INTO {table_name} ({','.join(use_cols)}) VALUES ({','.join(['?']*len(use_cols))})"

    inserted = skipped = 0
    rows_buf = []

    for row_num, row in enumerate(ws.iter_rows(min_row=2, values_only=True), 1):
        pk_val = tuple(str(row[i]) if row[i] is not None else '' for i in pk_idx_excel)
        if pk_val in existing_pks:
            skipped += 1
            continue
        rows_buf.append([clean(row[i]) for i in use_idx])
        existing_pks.add(pk_val)

        if len(rows_buf) >= batch:
            cur.executemany(sql, rows_buf)
            con.commit()
            inserted += len(rows_buf)
            rows_buf = []
            print(f"  {row_num:,}/{total_rows:,} ({row_num/total_rows*100:.1f}%) | 삽입:{inserted:,} 스킵:{skipped:,} | {time.time()-t0:.1f}s")

    if rows_buf:
        cur.executemany(sql, rows_buf)
        con.commit()
        inserted += len(rows_buf)

    wb.close()
    cur.execute(f"SELECT COUNT(*) FROM {table_name}")
    after = cur.fetchone()[0]
    con.close()

    print(f"\n✅ 완료: {before:,} → {after:,}건 (삽입:{inserted:,} 스킵:{skipped:,}) | {time.time()-t0:.1f}s")

if __name__ == '__main__':
    import_shpdh()
