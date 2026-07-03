# module-dispatch-config SQL

## 실행 순서
1. `01_schema.sql` — ds_dispatch_objective, ds_dispatch_profile, ds_dispatch_constraint,
                    ds_dispatch_const_set, ds_dispatch_const_set_item,
                    ds_dispatch_const_cartype, ds_dispatch_const_region 테이블 생성
2. `02_seed_data.sql` — 기본 목적식 3종 삽입 (MIN_VEHICLES / MAX_FILL / MIN_COST)
