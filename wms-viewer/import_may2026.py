#!/usr/bin/env python3
"""
import_may2026.py — 2026년 5월 SHPDH/SHPDI 데이터 로드
- /home/user/uploaded_files/SHPDH_202605.xlsx → SHPDH 테이블
- /home/user/uploaded_files/SHPDI_202605.xlsx → SHPDI 테이블
- DB에 있는 컬럼만 INSERT (xlsx에 여분 컬럼 무시)
- INSERT OR REPLACE → 중복 시 교체
"""
import sqlite3, pandas as pd, datetime, time, math

DB_PATH    = '/home/user/webapp/wms-viewer/wms.db'
SHPDH_FILE = '/home/user/uploaded_files/SHPDH_202605.xlsx'
SHPDI_FILE = '/home/user/uploaded_files/SHPDI_202605.xlsx'

CHUNK = 2000  # 배치 크기

def sv(v):
    """None / NaN → 공백 문자열"""
    if v is None: return ' '
    if isinstance(v, float) and math.isnan(v): return ' '
    s = str(v).strip()
    return s if s else ' '

def nv(v):
    if v is None: return 0.0
    if isinstance(v, float) and math.isnan(v): return 0.0
    try: return float(v)
    except: return 0.0

def get_db_cols(conn, table):
    return [r[1] for r in conn.execute(f'PRAGMA table_info({table})').fetchall()]

def load_table(conn, table, xlsx_path, chunk=CHUNK):
    print(f'\n[{table}] 로드 중: {xlsx_path.split("/")[-1]}')
    t0 = time.time()

    db_cols = get_db_cols(conn, table)
    print(f'  DB 컬럼: {len(db_cols)}개')

    # pandas로 전체 로드 (dtype=str 로 읽어서 타입 혼란 방지)
    print('  xlsx 읽는 중...')
    df = pd.read_excel(xlsx_path, dtype=str)
    print(f'  xlsx 행수: {len(df):,} / 컬럼수: {len(df.columns)}')

    # xlsx 컬럼명 정규화 (strip)
    df.columns = [str(c).strip() for c in df.columns]

    # DB에 있는 컬럼만 선택 (xlsx에 없는 DB 컬럼은 DEFAULT 사용)
    use_cols = [c for c in db_cols if c in df.columns]
    miss_cols = [c for c in db_cols if c not in df.columns]
    print(f'  사용 컬럼: {len(use_cols)}개 | xlsx에 없어 DB DEFAULT 사용: {len(miss_cols)}개')
    if miss_cols:
        print(f'    누락: {miss_cols[:10]}{"..." if len(miss_cols)>10 else ""}')

    df_use = df[use_cols].copy()

    # NaN → 빈 문자열
    df_use = df_use.fillna(' ')

    # 빈 행 제거 (첫 컬럼이 공백이면 스킵)
    pk_col = use_cols[0]
    df_use = df_use[df_use[pk_col].str.strip() != '']
    print(f'  유효 행수: {len(df_use):,}')

    # INSERT OR REPLACE
    ph  = ','.join(['?'] * len(use_cols))
    col_str = ','.join(use_cols)
    sql = f'INSERT OR REPLACE INTO {table} ({col_str}) VALUES ({ph})'

    inserted = 0
    rows = df_use.values.tolist()
    for i in range(0, len(rows), chunk):
        batch = rows[i:i+chunk]
        # 각 값을 sv()로 정리
        clean = [tuple(sv(v) for v in row) for row in batch]
        conn.executemany(sql, clean)
        conn.commit()
        inserted += len(clean)
        pct = inserted / len(rows) * 100
        elapsed = time.time() - t0
        print(f'  {inserted:,}/{len(rows):,} ({pct:.0f}%) — {elapsed:.1f}s', end='\r')

    print()
    final_cnt = conn.execute(f'SELECT COUNT(*) FROM {table}').fetchone()[0]
    print(f'  ✅ {table}: {inserted:,}건 INSERT | DB 총 {final_cnt:,}건 ({time.time()-t0:.1f}s)')
    return inserted

def main():
    conn = sqlite3.connect(DB_PATH)
    conn.execute('PRAGMA journal_mode=WAL')
    conn.execute('PRAGMA synchronous=NORMAL')

    print('='*60)
    print('2026년 5월 데이터 로드 시작')
    print('='*60)

    # 기존 데이터 현황
    h_before = conn.execute('SELECT COUNT(*) FROM SHPDH').fetchone()[0]
    i_before = conn.execute('SELECT COUNT(*) FROM SHPDI').fetchone()[0]
    print(f'\n기존 현황: SHPDH={h_before:,}건 / SHPDI={i_before:,}건')

    t_total = time.time()

    h_cnt = load_table(conn, 'SHPDH', SHPDH_FILE)
    i_cnt = load_table(conn, 'SHPDI', SHPDI_FILE)

    print()
    print('='*60)
    print('📊 최종 결과:')
    h_after = conn.execute('SELECT COUNT(*) FROM SHPDH').fetchone()[0]
    i_after = conn.execute('SELECT COUNT(*) FROM SHPDI').fetchone()[0]
    print(f'  SHPDH: {h_before:,} → {h_after:,} (+{h_after-h_before:,})')
    print(f'  SHPDI: {i_before:,} → {i_after:,} (+{i_after-i_before:,})')

    # 날짜 분포 확인
    print()
    print('📅 SHPDH 날짜 분포 (상위 10):')
    rows = conn.execute("""
        SELECT SUBSTR(RQSHPD,1,6) ym, COUNT(*) cnt
        FROM SHPDH GROUP BY 1 ORDER BY 1 DESC LIMIT 10
    """).fetchall()
    for r in rows:
        print(f'  {r[0]}: {r[1]:,}건')

    conn.close()
    print(f'\n✅ 완료! 총 소요시간: {time.time()-t_total:.1f}초')

if __name__ == '__main__':
    main()
