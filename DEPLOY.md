# Kleannara TMS 배포 가이드 (Spring Boot 단일 구성)

> Rocky Linux 9.x | **Nginx + Spring Boot 단일 구성** | MariaDB 10.2.14.247:3306/intergration

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

## 서버 디렉토리 구조

```
/data/tms/
├── source/                       ← git clone 위치 (소스코드)
│   ├── tms-spring/               ← Spring Boot 멀티모듈 프로젝트
│   │   ├── gradlew
│   │   ├── app/build/libs/app.jar  ← 빌드 결과물
│   │   └── ...
│   ├── sql/                      ← DDL 스크립트
│   ├── nginx.conf                ← Nginx 설정
│   └── wms-viewer/               ← (이관 완료 후 삭제 예정)
│       ├── wms.db
│       └── migrate_to_mariadb.py
├── app/
│   └── kleannara-tms.jar         ← 실행 JAR (빌드 후 복사)
├── logs/
│   ├── stdout.log
│   └── stderr.log
└── uploads/                      ← 서류 파일 업로드 저장소
```

> **역할 분리 원칙**
> - `source/` : git 관리 영역. `git pull` / 재빌드 시 이 디렉토리만 건드림
> - `app/`    : 실행 JAR. 서비스 재시작 시 이 JAR만 교체
> - `logs/`, `uploads/` : 런타임 데이터. 배포와 무관하게 유지됨

---

## API 경로 구조

| 접두사 | 기능 | 모듈 |
|--------|------|------|
| `/api/tables`, `/api/schema/*`, `/api/data/*`, `/api/sql` | WMS 뷰어 | module-wms |
| `/api/codes/*`, `/api/wahma/*`, `/api/shpdh/*` | 공통코드/물류센터/출고문서 | module-wms |
| `/api/dispatch/strategy`, `/api/carclass*`, `/api/ds-vehicle` | 배차전략/차종 | module-wms |
| `/api/dispatch-objective/*` | 목적식 관리 | module-wms |
| `/api/dispatch-const-set/*` | 제약조건 세트 | module-wms |
| `/api/dispatch-constraint/*` | 제약조건 프로파일 + 자동배차 | module-wms |
| `/api/vehicle/*` | 차량 관리 | module-vehicle |
| `/api/delivery/*`, `/api/route_cost/*` | 납품처/운송비 | module-delivery |
| `/api/shipment/*` | 출고진행현황 | module-shipment |
| `/api/ps-dispatch/*` | PS 배차 | module-dispatch |
| `/api/ps-sap/*` | SAP 연동 | module-wms |
| `/api/doc/*` | 서류 관리 | module-document |
| `/auth/*` | 인증 (JWT) | core |
| `/*-api/*` | 레거시 경로 (하위 호환) | 각 모듈 |

---

## STEP 1 — Java 17 설치

```bash
# 설치 여부 확인
java -version

# 미설치 시 (Rocky Linux)
sudo dnf install -y java-17-openjdk java-17-openjdk-devel

# 설치 확인
java -version
# → openjdk version "17.x.x" 이어야 함
```

---

## STEP 2 — 디렉토리 구조 생성

```bash
# 운영 디렉토리 일괄 생성
sudo mkdir -p /data/tms/source
sudo mkdir -p /data/tms/app
sudo mkdir -p /data/tms/logs
sudo mkdir -p /data/tms/uploads

# 실행 전용 유저 생성 (root 실행 방지)
sudo useradd -r -s /sbin/nologin tmsuser

# 소유권 설정
sudo chown -R tmsuser:tmsuser /data/tms
```

---

## STEP 3 — 소스코드 받기

```bash
# source/ 에 git clone
sudo -u tmsuser git clone \
  https://github.com/chatgptAX2/kleannara.tms.git \
  /data/tms/source

# 확인
ls /data/tms/source/
# → tms-spring/  sql/  nginx.conf  wms-viewer/  DEPLOY.md  ...
```

---

## STEP 4 — MariaDB 스키마 적용

```bash
# 신규 테이블 DDL 적용 (IF NOT EXISTS → 안전하게 재실행 가능)
mysql -h 10.2.14.247 -u tmsuser -p intergration \
  -e "source /data/tms/source/sql/module-wms/01_schema.sql"

mysql -h 10.2.14.247 -u tmsuser -p intergration \
  -e "source /data/tms/source/sql/module-document/01_schema.sql"
```

### SQLite → MariaDB 데이터 이관 (최초 1회)

```bash
# pymysql 설치
pip3 install pymysql

# 이관 실행
python3 /data/tms/source/wms-viewer/migrate_to_mariadb.py \
  --sqlite /data/tms/source/wms-viewer/wms.db \
  --host 10.2.14.247 --port 3306 \
  --db intergration \
  --user tmsuser \
  --password 실제패스워드
```

---

## STEP 5 — 빌드

```bash
cd /data/tms/source/tms-spring

# Gradle Wrapper 실행 권한 부여 (최초 1회)
chmod +x gradlew

# 빌드 (테스트 제외)
./gradlew clean bootJar -x test

# 빌드 결과 확인
ls -lh app/build/libs/
# → app.jar (50~80MB 예상)

# 오류 시 로그 확인
./gradlew clean bootJar -x test 2>&1 | tee /tmp/build.log
grep "error:" /tmp/build.log | head -30
```

---

## STEP 6 — JAR 배포 위치로 복사

```bash
# source/빌드결과 → app/ 실행 위치로 복사
sudo cp /data/tms/source/tms-spring/app/build/libs/app.jar \
        /data/tms/app/kleannara-tms.jar

sudo chown tmsuser:tmsuser /data/tms/app/kleannara-tms.jar

# 확인
ls -lh /data/tms/app/kleannara-tms.jar
```

---

## STEP 7 — systemd 서비스 등록

```bash
sudo vi /etc/systemd/system/kleannara-tms.service
```

아래 내용 붙여넣기 (`DB_PASSWORD`, `JWT_SECRET` 실제 값으로 수정):

```ini
[Unit]
Description=Kleannara TMS Spring Boot
After=network.target

[Service]
Type=simple
User=tmsuser
Group=tmsuser
WorkingDirectory=/data/tms

# ── 운영 환경 변수 (실제 값으로 반드시 수정) ──────────────────
Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="DB_USERNAME=tmsuser"
Environment="DB_PASSWORD=실제DB패스워드여기입력"
Environment="JWT_SECRET=운영용256비트이상시크릿키여기입력"
Environment="SAP_RFC_MOCK=true"
# SAP 실제 연동 시 아래 주석 해제 후 URL 입력
# Environment="SAP_RFC_MOCK=false"
# Environment="SAP_RFC_URL=http://SAP서버IP:8000/sap/rfc"
# Environment="SAP_WMS_URL=http://WMS서버IP:9000/wms/ifc"

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

# 상태 확인 (10~20초 대기 후)
sudo systemctl status kleannara-tms

# 기동 로그 실시간 확인
tail -f /data/tms/logs/stdout.log
# → "Started KleannaraTmsApplication in X.XXX seconds" 확인
```

---

## STEP 8 — Nginx 설정

```bash
# Nginx 설치 확인
nginx -v
sudo dnf install -y nginx   # 없으면 설치

# source/의 nginx.conf를 시스템에 적용
sudo cp /data/tms/source/nginx.conf /etc/nginx/nginx.conf

# 문법 검사
sudo nginx -t
# → nginx: configuration file test is successful

# Nginx 시작
sudo systemctl enable nginx
sudo systemctl start nginx
```

---

## STEP 9 — 동작 확인

```bash
# 1. Spring Boot 직접 헬스체크
curl http://localhost:18081/actuator/health
# → {"status":"UP"}

# 2. Nginx 통과 헬스체크
curl http://localhost/actuator/health
# → {"status":"UP"}

# 3. 로그인 테스트
curl -X POST http://localhost/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}'
# → {"code":200,"data":{"token":"eyJ..."}}

# 4. API 호출
TOKEN="위에서 받은 토큰값"
curl -H "Authorization: Bearer $TOKEN" http://localhost/api/tables
```

---

## STEP 10 — Flask 기존 서비스 제거

```bash
# Flask systemd 서비스 중단 및 제거
sudo systemctl stop    wms-viewer 2>/dev/null || true
sudo systemctl disable wms-viewer 2>/dev/null || true
sudo rm -f /etc/systemd/system/wms-viewer.service
sudo systemctl daemon-reload

# 데이터 이관 완료 확인 후 wms.db 삭제 (선택)
# rm /data/tms/source/wms-viewer/wms.db
```

---

## 업데이트 배포 절차 (이후 반복)

```bash
# 1. 소스코드 최신화 (source/ 에서만)
cd /data/tms/source && git pull origin main

# 2. 재빌드
cd /data/tms/source/tms-spring && ./gradlew clean bootJar -x test

# 3. JAR 교체
sudo cp /data/tms/source/tms-spring/app/build/libs/app.jar \
        /data/tms/app/kleannara-tms.jar

# 4. 서비스 재시작
sudo systemctl restart kleannara-tms

# 5. 기동 확인 (15초 대기)
sleep 15 && curl http://localhost:18081/actuator/health
```

---

## 환경변수 정리

| 변수명 | 설명 | 기본값 |
|--------|------|--------|
| `SPRING_PROFILES_ACTIVE` | 활성 프로파일 | `prod` |
| `DB_USERNAME` | DB 계정 | `tmsuser` |
| `DB_PASSWORD` | DB 비밀번호 | **(필수 설정)** |
| `JWT_SECRET` | JWT 시크릿 (256bit 이상) | **(필수 변경)** |
| `SAP_RFC_MOCK` | SAP Mock 모드 | `true` |
| `SAP_RFC_URL` | SAP RFC 연동 URL | `http://localhost:8000/sap/rfc` |
| `SAP_WMS_URL` | WMS 인터페이스 URL | `http://localhost:9000/wms/ifc` |

---

## 트러블슈팅

### Spring Boot 시작 안 될 때
```bash
tail -100 /data/tms/logs/stdout.log
mysql -h 10.2.14.247 -u tmsuser -p -e "SELECT 1" intergration
ss -tlnp | grep 18081
```

### Nginx 502 Bad Gateway
```bash
# Spring Boot 기동 여부 먼저 확인
curl http://localhost:18081/actuator/health

# Nginx 에러 로그
tail -f /var/log/nginx/error.log

# SELinux 차단 시 (Rocky Linux)
sudo setsebool -P httpd_can_network_connect 1
```

### 파일 업로드 실패
```bash
ls -la /data/tms/uploads/
sudo chown -R tmsuser:tmsuser /data/tms/uploads/
sudo chmod -R 755 /data/tms/uploads/
```

### 빌드 오류
```bash
cd /data/tms/source/tms-spring
./gradlew clean bootJar -x test 2>&1 | tee /tmp/build.log
grep "error:" /tmp/build.log | head -30
```
