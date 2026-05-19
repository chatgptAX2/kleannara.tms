#!/usr/bin/env python3
"""SHPDI_202604.xlsx → DB SHPDI 임포트 (NOT NULL 컬럼 None → DEFAULT 처리)"""
import sqlite3, openpyxl, time, datetime

def import_shpdi():
    xlsx_path  = '/home/user/uploaded_files/SHPDI_202604.xlsx'
    db_path    = '/home/user/webapp/wms-viewer/wms.db'
    table_name = 'SHPDI'
    batch      = 2000
    t0 = time.time()

    print(f"[{table_name}] 임포트 시작")

    # DB 컬럼 정보 (타입, NOT NULL, DEFAULT)
    con = sqlite3.connect(db_path)
    con.execute("PRAGMA journal_mode=WAL")
    con.execute("PRAGMA synchronous=NORMAL")
    cur = con.cursor()
    cur.execute(f"PRAGMA table_info({table_name})")
    cols_info = cur.fetchall()
    # col_name → (type, notnull, default)
    col_meta  = {r[1]: (r[2], r[3], r[4]) for r in cols_info}
    db_cols   = [r[1] for r in cols_info]

    cur.execute(f"SELECT COUNT(*) FROM {table_name}")
    before = cur.fetchone()[0]
    print(f"  기존: {before:,}건")

    # 기존 PK 세트
    cur.execute(f"SELECT SHPOKY, SHPOIT FROM {table_name}")
    existing_pks = set((str(r[0]).strip(), str(r[1]).strip()) for r in cur.fetchall())
    print(f"  기존 PK: {len(existing_pks):,}개")

    # xlsx 읽기
    wb = openpyxl.load_workbook(xlsx_path, read_only=True, data_only=True)
    ws = wb.active
    headers    = [ws.cell(1, c).value for c in range(1, ws.max_column + 1)]
    total_rows = ws.max_row - 1
    print(f"  엑셀: {len(headers)}컬럼 / {total_rows:,}행")

    use_cols = [c for c in headers if c in col_meta]
    use_idx  = [headers.index(c) for c in use_cols]
    print(f"  매핑 컬럼: {len(use_cols)}개")

    # DEFAULT 값 캐시 (NOT NULL 컬럼용)
    def get_default(col):
        _, notnull, dflt = col_meta[col]
        if notnull and dflt is not None:
            # DEFAULT ' ' → ' ', DEFAULT 0 → 0
            d = dflt.strip("'")
            # 숫자형 컬럼
            ctype = col_meta[col][0].upper()
            if 'REAL' in ctype or 'INT' in ctype or 'NUM' in ctype:
                try: return float(d)
                except: return 0
            return d
        return None

    def clean(v, col):
        if v is None:
            return get_default(col)
        if isinstance(v, str):
            s = v.strip()
            return s if s else (get_default(col) if col_meta[col][1] else None)
        if isinstance(v, datetime.datetime): return v.strftime('%Y%m%d%H%M%S')
        if isinstance(v, datetime.date):     return v.strftime('%Y%m%d')
        if isinstance(v, datetime.time):     return v.strftime('%H%M%S')
        return v

    placeholders = ','.join(['?'] * len(use_cols))
    sql = f"INSERT OR IGNORE INTO {table_name} ({','.join(use_cols)}) VALUES ({placeholders})"

    inserted = skipped = error = 0
    rows_buf = []

    for row_num, row in enumerate(ws.iter_rows(min_row=2, values_only=True), 1):
        pk_key = (str(row[0]).strip() if row[0] is not None else '',
                  str(row[1]).strip() if row[1] is not None else '')
        if pk_key in existing_pks:
            skipped += 1
            continue

        values = [clean(row[i], use_cols[k]) for k, i in enumerate(use_idx)]
        rows_buf.append(values)
        existing_pks.add(pk_key)

        if len(rows_buf) >= batch:
            try:
                cur.executemany(sql, rows_buf)
                con.commit()
                inserted += len(rows_buf)
            except Exception as e:
                # 배치 실패 시 개별 처리
                con.rollback()
                for single in rows_buf:
                    try:
                        cur.execute(sql, single)
                        inserted += 1
                    except Exception as e2:
                        error += 1
                con.commit()
            rows_buf = []
            elapsed = time.time() - t0
            pct = row_num / total_rows * 100
            print(f"  {row_num:,}/{total_rows:,} ({pct:.1f}%) | 삽입:{inserted:,} 스킵:{skipped:,} 오류:{error} | {elapsed:.1f}s")

    if rows_buf:
        try:
            cur.executemany(sql, rows_buf)
            con.commit()
            inserted += len(rows_buf)
        except Exception:
            con.rollback()
            for single in rows_buf:
                try:
                    cur.execute(sql, single)
                    inserted += 1
                except:
                    error += 1
            con.commit()

    wb.close()
    cur.execute(f"SELECT COUNT(*) FROM {table_name}")
    after = cur.fetchone()[0]
    con.close()

    elapsed = time.time() - t0
    print(f"\n✅ 완료: {before:,} → {after:,}건")
    print(f"   삽입:{inserted:,}  스킵(중복):{skipped:,}  오류:{error}")
    print(f"   소요시간: {elapsed:.1f}초")

if __name__ == '__main__':
    import_shpdi()
