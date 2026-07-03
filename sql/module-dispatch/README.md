# module-dispatch SQL

## 실행 순서
1. `01_schema.sql` — ps_dispatch_h, ps_dispatch_d 테이블 생성
2. `02_seed_data.sql` — 초기 데이터 (현재 없음)

## 주의사항
- ps_dispatch_i Entity는 ps_dispatch_d 테이블을 사용 (테이블명 변경 시 Entity @Table(name=) 수정 필요)
- STATUS 값: DRAFT → CONFIRMED → CANCELLED
