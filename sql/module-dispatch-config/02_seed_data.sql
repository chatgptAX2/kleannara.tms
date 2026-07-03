-- 기본 목적식 3종 삽입
INSERT IGNORE INTO ds_dispatch_objective (OBJ_CODE, OBJ_NM, OBJ_ICON, OBJ_ALGO, OBJ_DESC, SORT_SEQ, ACTIVE_YN, CREDAT, LMODAT)
VALUES
  ('MIN_VEHICLES', '차량 최소화',  '🚛', 'FFD BinPacking', '가능한 적은 차량으로 최대 적재 (FFD 알고리즘)', 10, 'Y', DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  ('MAX_FILL',     '적재율 최대화', '📊', 'BFD BinPacking', '각 차량을 가장 꽉 채우는 방식 (BFD 알고리즘)', 20, 'N', DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d')),
  ('MIN_COST',     '운송비 최소화', '💰', 'ROUTE_COST',    'ROUTE_COST 기반 최저비용 차종 선택',           30, 'N', DATE_FORMAT(NOW(),'%Y%m%d'), DATE_FORMAT(NOW(),'%Y%m%d'));
