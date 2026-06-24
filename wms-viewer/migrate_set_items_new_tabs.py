"""
마이그레이션: 신규 11개 제약조건 탭을 DS_DISPATCH_CONST_SET_ITEM에 등록
대상 세트: SET_ID=3 (제약조건 1), SET_ID=4 (제약조건 2)

실행: python3 migrate_set_items_new_tabs.py
"""
import sqlite3
import os

DB_PATH = os.path.join(os.path.dirname(__file__), 'wms.db')

NEW_TYPES = [
    'ROLL_UNIT', 'ROLL_STACK', 'ROLL_INCH_MIX', 'ROLL_3D_VERIFY',
    'BOARD_CBM_WEIGHT', 'BOARD_BULK_SPLIT', 'BOARD_FLEX_SPLIT', 'BOARD_3D_VERIFY',
    'MIX_Z_AXIS', 'MIX_Y_LIFO', 'MIX_DUAL_VERIFY',
]

def migrate():
    con = sqlite3.connect(DB_PATH)
    con.row_factory = sqlite3.Row
    cur = con.cursor()

    placeholders = ','.join('?' for _ in NEW_TYPES)
    cur.execute(f'''
        SELECT CONST_ID, CONST_TYPE, CONST_KEY, CONST_VALUE
        FROM DS_DISPATCH_CONST
        WHERE CONST_TYPE IN ({placeholders})
        ORDER BY CONST_ID
    ''', NEW_TYPES)
    new_rows = cur.fetchall()
    print(f'신규 탭 CONST 행 수: {len(new_rows)}건')

    inserted = 0
    for set_id in [3, 4]:
        for row in new_rows:
            cur.execute(
                'SELECT COUNT(*) FROM DS_DISPATCH_CONST_SET_ITEM WHERE SET_ID=? AND CONST_ID=?',
                (set_id, row['CONST_ID'])
            )
            if cur.fetchone()[0] > 0:
                continue
            cur.execute('''
                INSERT INTO DS_DISPATCH_CONST_SET_ITEM (SET_ID, CONST_ID, ACTIVE_YN, PARAM_VALUE)
                VALUES (?, ?, 'Y', ?)
            ''', (set_id, row['CONST_ID'], row['CONST_VALUE']))
            inserted += 1

    con.commit()
    print(f'✅ 삽입 완료: {inserted}건')

    # 검증
    for set_id in [3, 4]:
        cur.execute(f'''
            SELECT c.CONST_TYPE, COUNT(*) as cnt
            FROM DS_DISPATCH_CONST_SET_ITEM i
            JOIN DS_DISPATCH_CONST c ON c.CONST_ID = i.CONST_ID
            WHERE i.SET_ID=? AND c.CONST_TYPE IN ({placeholders})
            GROUP BY c.CONST_TYPE ORDER BY c.CONST_TYPE
        ''', [set_id] + NEW_TYPES)
        print(f'\nSET_ID={set_id}:')
        for r in cur.fetchall():
            print(f'  ✅ {r["CONST_TYPE"]:<22}: {r["cnt"]}건')
    con.close()

if __name__ == '__main__':
    migrate()
