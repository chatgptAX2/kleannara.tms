# module-wms-master

WMS 공통 마스터 테이블 DDL + 기초 데이터

## 테이블 목록

| 테이블 | 건수 | 설명 |
|--------|------|------|
| CMCDM  | 85건 | 공통코드 마스터 |
| CMCDV  | 1,990건 | 공통코드 값 |
| WAHMA  | 9건 | 물류센터(창고) 마스터 |

## 파일 구조

```
01_schema.sql    — CREATE TABLE (MariaDB/MySQL InnoDB)
02_seed_data.sql — INSERT ... ON DUPLICATE KEY UPDATE
```

## 실행 순서

```sql
SOURCE 01_schema.sql;
SOURCE 02_seed_data.sql;
```

## 생성일
2026-07-04 / wms-viewer/wms.db 현황 기반
