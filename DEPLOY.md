# Kleannara TMS 배포 가이드 (Spring Boot 단일 구성)

> Rocky Linux 9.6 | **Nginx + Spring Boot 단일 구성** | MariaDB 10.2.14.247:3306/intergration

---

## 최종 아키텍처

```
외부 접속 (port 80)
        │
     [Nginx :80]
        │  모든 요청 프록시
        ▼
[Spring Boot :18081]  ← kleannara-tms.jar
        │
        ▼
[MariaDB 10.2.14.247:3306/intergration]
```

> **Flask 완전 제거** — Python/Flask 의존성 없음, SQLite 사용 안 함

---

## API 경로 구조

| 접두사 | 기능 | 모듈 |
|--------|------|------|
| `/api/tables`, `/api/schema/*`, `/api/data/*`, `/api/sql` | WMS 뷰어 | module-wms |
| `/api/codes/*`, `/api/wahma/*`, `/api/shpdh/*` | 공통코드/물류센터/출고문서 | module-wms |
| `/api/dispatch/strategy`, `/api/carclass*`, `/api/ds-vehicle` | 배차전략/차종 | module-wms |
| `/api/dispatch-objective/*` | 목적식 관리 | module-wms |
| `/api/dispatch-const-set/*` | 제약조건 세트 | module-wms |
| `/api/dispatch-constraint/*` | 제약조건 프로파일 | module-wms |
| `/api/vehicle/*` | 차량 관리 | module-vehicle |
| `/api/delivery/*`, `/api/route_cost/*` | 납품처/운송비 | module-delivery |
| `/api/shipment/*` | 출고진행현황 | module-shipment |
| `/api/ps-dispatch/*` | PS 배차 | module-dispatch |
| `/api/ps-sap/*` | SAP 연동 | module-wms |
| `/api/doc/*` | 서류 관리 | module-document |
| `/auth/*` | 인증 (JWT) | core |
| `/*-api/*` | 레거시 경로 (하위 호환) | 각 모듈 |

---

## 서버 디렉토리 구조

```
/data/tms/
├── app/
│   └── kleannara-tms.jar     ← Spring Boot 실행 파일
├── config/
│   └── application-prod.yml  ← 운영 환경 설정 (별도 관리)
├── logs/
│   ├── stdout.log
│   └── stderr.log
└── uploads/                  ← 서류 파일 업로드 저장소
```

---

## STEP 1 — Java 17 설치

```bash
java -version

# 미설치 시
sudo dnf install -y java-17-openjdk java-17-openjdk-devel
```

---

## STEP 2 — MariaDB 스키마 적용

```bash
# MariaDB 접속
mysql -h 10.2.14.247 -u {DB_USER} -p{DB_PASS} intergration

# 스키마 파일 순서대로 실행
source /data/tms/sql/module-dispatch/01_schema.sql;
source /data/tms/sql/module-vehicle/01_schema.sql;
source /data/tms/sql/module-delivery/01_schema.sql;
source /data/tms/sql/module-dispatch-config/01_schema.sql;
source /data/tms/sql/module-shipment/01_schema.sql;
source /data/tms/sql/module-wms/01_schema.sql;        -- 신규
source /data/tms/sql/module-document/01_schema.sql;    -- 신규
```

### SQLite → MariaDB 데이터 이관 (최초 1회)

```bash
# wms.db가 있는 서버에서 실행
cd /home/user/webapp
python3 wms-viewer/migrate_to_mariadb.py \
  --sqlite wms-viewer/wms.db \
  --host 10.2.14.247 --port 3306 \
  --db intergration --user {DB_USER} --password {DB_PASS}
```

---

## STEP 3 — 빌드

```bash
cd /home/user/webapp/tms-spring

# Gradle Wrapper로 빌드
./gradlew clean bootJar -x test

# 빌드 결과 확인
ls -la app/build/libs/
# → app-1.0.0.jar (약 50~80MB)
```

---

## STEP 4 — JAR 서버 배포

```bash
# 서버에 JAR 복사
scp tms-spring/app/build/libs/app-1.0.0.jar {SERVER}:/data/tms/app/kleannara-tms.jar

# uploads 디렉토리 생성
mkdir -p /data/tms/uploads
chmod 755 /data/tms/uploads
```

---

## STEP 5 — systemd 서비스 설정

```bash
sudo vi /etc/systemd/system/kleannara-tms.service
```

```ini
[Unit]
Description=Kleannara TMS - Spring Boot
After=network.target

[Service]
Type=simple
User=tmsuser
Group=tmsuser
WorkingDirectory=/data/tms

Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="DB_USERNAME=tmsuser"
Environment="DB_PASSWORD=실제패스워드입력"
Environment="JWT_SECRET=운영용-JWT-시크릿-256비트이상"

ExecStart=/usr/bin/java \
  -Xmx1g -Xms512m \
  -jar /data/tms/app/kleannara-tms.jar \
  --spring.profiles.active=prod \
  --doc.upload.base-path=/data/tms/uploads

StandardOutput=append:/data/tms/logs/stdout.log
StandardError=append:/data/tms/logs/stderr.log

Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
# 서비스 등록 및 시작
sudo systemctl daemon-reload
sudo systemctl enable kleannara-tms
sudo systemctl start kleannara-tms

# 상태 확인
sudo systemctl status kleannara-tms
tail -f /data/tms/logs/stdout.log
```

---

## STEP 6 — Nginx 설정

```bash
# Nginx 설치 확인
nginx -v

# 미설치 시
sudo dnf install -y nginx

# 설정 파일 복사 (webapp/nginx.conf → 서버)
sudo cp /data/tms/nginx.conf /etc/nginx/nginx.conf

# 문법 검사
sudo nginx -t

# Nginx 시작/재시작
sudo systemctl enable nginx
sudo systemctl start nginx
# 또는 재시작
sudo systemctl reload nginx
```

---

## STEP 7 — 동작 확인

```bash
# Spring Boot 헬스체크
curl http://localhost:18081/actuator/health

# Nginx를 통한 접근
curl http://localhost/actuator/health

# WMS 테이블 목록 (인증 토큰 필요)
TOKEN=$(curl -s -X POST http://localhost/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}' | jq -r '.data.token')

curl -H "Authorization: Bearer $TOKEN" http://localhost/api/tables
```

---

## 환경변수 정리

| 변수명 | 설명 | 기본값 |
|--------|------|--------|
| `SPRING_PROFILES_ACTIVE` | 활성 프로파일 | `prod` |
| `DB_USERNAME` | DB 계정 | `tmsuser` |
| `DB_PASSWORD` | DB 비밀번호 | (필수) |
| `JWT_SECRET` | JWT 시크릿 (256bit+) | (변경 필수) |
| `SAP_RFC_URL` | SAP RFC 연동 URL | `http://localhost:8000/sap/rfc` |
| `SAP_WMS_URL` | WMS 인터페이스 URL | `http://localhost:9000/wms/ifc` |

---

## 트러블슈팅

### Spring Boot 시작 안 될 때
```bash
# 로그 확인
tail -100 /data/tms/logs/stdout.log

# MariaDB 연결 확인
mysql -h 10.2.14.247 -u {USER} -p{PASS} -e "SELECT 1" intergration

# 포트 확인
ss -tlnp | grep 18081
```

### 파일 업로드 실패
```bash
# 업로드 디렉토리 권한 확인
ls -la /data/tms/uploads/
chmod -R 755 /data/tms/uploads/
chown -R tmsuser:tmsuser /data/tms/uploads/
```

### Nginx 연결 실패
```bash
# Nginx 로그
tail -f /var/log/nginx/error.log

# SELinux 허용 (Rocky Linux)
sudo setsebool -P httpd_can_network_connect 1
```

---

## Flask 완전 제거 확인

```bash
# wms-viewer 디렉토리 삭제 (데이터 이관 완료 후)
# ⚠️ wms.db 데이터 이관 확인 후 실행!
rm -rf /data/tms/wms-viewer/

# Python/pip 의존성 제거 (선택)
pip3 uninstall flask openpyxl requests -y

# systemd에서 Flask 서비스 제거 (이전에 등록했다면)
sudo systemctl stop wms-viewer 2>/dev/null || true
sudo systemctl disable wms-viewer 2>/dev/null || true
sudo rm -f /etc/systemd/system/wms-viewer.service
sudo systemctl daemon-reload
```

---

## SQLite → MariaDB 데이터 이관 스크립트 위치

```
webapp/
└── sql/
    ├── module-wms/
    │   └── 01_schema.sql        # WMS 관련 테이블 DDL
    ├── module-document/
    │   └── 01_schema.sql        # 서류관리 테이블 DDL
    ├── module-dispatch/
    │   └── 01_schema.sql        # 배차 테이블 DDL
    └── ...
```

> **데이터 이관 순서**: 스키마 생성 → SQLite 덤프 → MariaDB INSERT
> 상세 이관 스크립트: `wms-viewer/migrate_to_mariadb.py` (별도 작성 필요)
