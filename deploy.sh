#!/bin/bash

echo "========================================="
echo " TMS 배포 시작: $(date '+%Y-%m-%d %H:%M:%S')"
echo "========================================="

# 소스 경로로 이동
cd /data/tms/source || { echo "❌ 소스 경로 이동 실패"; exit 1; }

# 최신 소스 pull
echo "▶ git pull..."
git pull origin main || { echo "❌ git pull 실패"; exit 1; }

# 빌드
echo "▶ 빌드 시작..."
cd tms-spring || { echo "❌ tms-spring 경로 이동 실패"; exit 1; }
./gradlew clean bootJar -x test -PsapJcoJar=/data/tms/app/libs/sapjco3.jar || { echo "❌ 빌드 실패"; exit 1; }

# jar 복사
echo "▶ jar 파일 복사..."
cp app/build/libs/app.jar /data/tms/app/app.jar || { echo "❌ jar 복사 실패"; exit 1; }

# 서비스 재시작
echo "▶ 서비스 재시작..."
sudo systemctl restart tms || { echo "❌ 서비스 재시작 실패"; exit 1; }

echo "========================================="
echo " ✅ 배포 완료: $(date '+%Y-%m-%d %H:%M:%S')"
echo "========================================="
