# SQL 디렉토리

kleannara TMS 데이터베이스 DDL 및 기초 데이터 (MariaDB/MySQL 호환)

## 모듈 구성

| 디렉토리 | 테이블 | 설명 |
|----------|--------|------|
| `module-wms-master/` | CMCDM, CMCDV, WAHMA | WMS 공통 마스터 |
| `module-vehicle/` | DS_VEHICLE, DS_INCH12, DS_INCH3, VHCMA | 차량 관리 |
| `module-dispatch-config/` | DS_DISPATCH_OBJECTIVE, DS_DISPATCH_PROFILE, DS_DISPATCH_CONST, DS_DISPATCH_CONST_ITEM, DS_DISPATCH_CONST_SET, DS_DISPATCH_CONST_SET_ITEM, DS_DISPATCH_CONST_SETTING | PS 제약조건 관리 |
| `module-dispatch/` | PS_DISPATCH_H, PS_DISPATCH_D, PS_DISPATCH_SPLIT, PS_SAP_STK, ROUTE_COST | PS 배차 |
| `module-delivery/` | (납품 관련) | 납품처 관리 |

## 전체 실행 순서 (FK 의존성 고려)

```sql
-- 1. WMS 마스터 (독립)
SOURCE module-wms-master/01_schema.sql;
SOURCE module-wms-master/02_seed_data.sql;

-- 2. 차량
SOURCE module-vehicle/01_schema.sql;
SOURCE module-vehicle/02_seed_data.sql;

-- 3. PS 제약조건 관리
SOURCE module-dispatch-config/01_schema.sql;
SOURCE module-dispatch-config/02_seed_data.sql;

-- 4. PS 배차 (ROUTE_COST 포함)
SOURCE module-dispatch/01_schema.sql;
SOURCE module-dispatch/02_seed_data.sql;

-- 5. 납품
SOURCE module-delivery/01_schema.sql;
SOURCE module-delivery/02_seed_data.sql;
```

## 데이터 현황 (2026-07-04 기준)

| 테이블 | 건수 |
|--------|------|
| CMCDM | 85건 |
| CMCDV | 1,990건 |
| WAHMA | 9건 |
| DS_VEHICLE | 16건 |
| DS_INCH12 | 32건 |
| DS_INCH3 | 32건 |
| VHCMA | 30건 |
| DS_DISPATCH_OBJECTIVE | 3건 |
| DS_DISPATCH_PROFILE | 3건 |
| DS_DISPATCH_CONST | 68건 |
| DS_DISPATCH_CONST_ITEM | 58건 |
| DS_DISPATCH_CONST_SET | 1건 |
| DS_DISPATCH_CONST_SET_ITEM | 67건 |
| ROUTE_COST | 1,284건 |
