# module-shipment SQL 스크립트

## 실행 순서

```bash
# 1. 스키마(테이블) 생성
mysql -u tmsuser -p tms < 01_schema.sql

# 2. 초기 데이터 (해당 없음 — SAP 연계 테이블 별도 import)
mysql -u tmsuser -p tms < 02_seed_data.sql
```

## 테이블 구조

| 테이블명 | 설명 | 비고 |
|---|---|---|
| `shipment_filter_preset` | 출고 조회 즐겨찾기 | module-shipment 전용 |
| `shipment_history` | 출고 처리 이력 로그 | module-shipment 전용 |

## SAP 연계 테이블 (별도 관리)

module-shipment는 아래 테이블을 **조회 전용(읽기 전용)**으로 사용합니다.
이 테이블들은 SAP 연계 import 스크립트로 별도 생성/관리됩니다.

| 테이블명 | 설명 | import 스크립트 |
|---|---|---|
| `SHPDH` | 출고 헤더 | `import_shpdh.py` |
| `SHPDI` | 출고 아이템 | `import_shpdi.py` |
| `SKUMA` | 품목마스터 | SAP 연계 |
| `MEASI` | 환산단위 | SAP 연계 |
| `BZPTN` | 파트너마스터 (납품처) | SAP 연계 |
| `CMCDV` | 공통코드 | SAP 연계 |

## API 엔드포인트

| Method | URL | 설명 |
|---|---|---|
| `POST` | `/shipment-api/schedule` | 출고진행현황 목록 조회 (페이징) |
| `GET` | `/shipment-api/schedule/filter-opts` | 검색 필터 옵션 조회 |

## PLT개수(PLT_CNT) 계산 방식

SKUMA.GRSWGT(속중량) 기반, 파렛트 적재 기준 **1,200 kg**:

| 품목 구분 | 계산식 | 비고 |
|---|---|---|
| 원지 (`H` prefix) | `CEIL(QTSHPO(kg) ÷ 1,200)` | QTSHPO가 이미 kg 단위 |
| 판지 (`F`/`S` prefix) | `CEIL(QTSHPO(속) × GRSWGT(kg/속) ÷ 1,200)` | SKUMA.GRSWGT 필요 |
| GRSWGT 미등록 | `-` (표시 불가) | SKUMA 마스터 입력 필요 |
