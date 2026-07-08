-- ============================================================
-- module-shipment: 초기 데이터 (seed)
-- 실행 전제: 01_schema.sql 실행 완료
-- ============================================================

-- module-shipment는 SAP 연계 데이터를 조회 전용으로 사용하므로
-- 별도 마스터 초기 데이터가 없습니다.

-- 필요 시 아래 테이블에 import 스크립트를 별도 실행하세요:
--   · SHPDH   : import_shpdh.py  (Flask 기반 import 스크립트)
--   · SHPDI   : import_shpdi.py  (Flask 기반 import 스크립트)
--   · SKUMA   : SAP 품목마스터 연계
--   · MEASI   : SAP 환산단위 연계
--   · BZPTN   : SAP 파트너마스터 연계
--   · CMCDV   : 공통코드 연계

SELECT 'module-shipment seed data: no initial data required.' AS msg;
