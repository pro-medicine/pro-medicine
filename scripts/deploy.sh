#!/usr/bin/env bash
# =============================================================
# 약쏙 무중단 배포 스크립트 (EC2에서 실행)
#  1) 새 백엔드 컨테이너를 기존 것 옆에 하나 더 띄움
#  2) 새 백엔드가 완전히 켜지면 기존 백엔드를 내림
#  3) 프론트 정적 파일 교체 (nginx는 재시작하지 않음)
# GitHub Actions가 ~/pro-medicine-artifacts 에 jar·dist를 올린 뒤 실행합니다.
# =============================================================
set -euo pipefail

APP_DIR="$HOME/pro-medicine"
ART_DIR="$HOME/pro-medicine-artifacts"
WAIT_SECONDS=300

cd "$APP_DIR"


echo "▶ 0. frontend/dist 폴더 준비 (nginx 연결 전에 배포 사용자 권한으로)"
mkdir -p frontend/dist
if [ ! -w frontend/dist ]; then
  echo "   root 소유 폴더라 권한을 복구합니다"
  sudo chown -R "$(id -u):$(id -g)" frontend/dist
fi

echo "▶ 1. 백엔드 jar 배치"
rm -rf backend/build/libs
mkdir -p backend/build/libs
cp "$ART_DIR/backend/app.jar" backend/build/libs/app.jar

echo "▶ 2. MySQL·nginx 준비 (변경 없으면 그대로 유지)"
docker compose up -d mysql
docker compose up -d --no-deps --build frontend

echo "▶ 3. 새 백엔드 이미지 빌드"
docker compose build backend

OLD_ID="$(docker compose ps -q backend | head -n 1)"

if [ -z "$OLD_ID" ]; then
  echo "   기존 백엔드가 없어 바로 실행합니다 (최초 배포)"
  docker compose up -d backend
else
  echo "▶ 4. 새 백엔드 컨테이너 추가 실행 (기존 것은 계속 서비스 중)"
  docker compose up -d --no-deps --no-recreate --scale backend=2 backend
  NEW_ID="$(docker compose ps -q backend | grep -v "$OLD_ID" | head -n 1)"

  echo "▶ 5. 새 백엔드 기동 대기 (최대 ${WAIT_SECONDS}초)"
  STARTED=false
  for ((i = 0; i < WAIT_SECONDS; i += 5)); do
    if docker logs "$NEW_ID" 2>&1 | grep -q "Started Application"; then
      STARTED=true
      break
    fi
    if [ "$(docker inspect -f '{{.State.Running}}' "$NEW_ID")" != "true" ]; then
      break
    fi
    sleep 5
  done

  if [ "$STARTED" != "true" ]; then
    echo "❌ 새 백엔드가 정상 기동하지 않았습니다. 기존 백엔드를 그대로 유지합니다."
    docker logs --tail 50 "$NEW_ID" || true
    docker rm -f "$NEW_ID"
    exit 1
  fi
  echo "   새 백엔드 기동 완료"

  echo "▶ 6. 기존 백엔드 트래픽 차단 후 종료"
  NETWORK="$(docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{end}}' "$OLD_ID")"
  docker network disconnect "$NETWORK" "$OLD_ID"
  sleep 5
  docker stop -t 30 "$OLD_ID"
  docker rm "$OLD_ID"
fi

echo "▶ 7. 프론트 정적 파일 교체"
mkdir -p frontend/dist
# 새 파일을 먼저 넣고 index.html은 마지막에 바꿔서, 교체 중에도 화면이 깨지지 않게 함
(cd "$ART_DIR/frontend/dist" && find . -type f ! -name index.html -exec install -D -m 644 {} "$APP_DIR/frontend/dist/{}" \;)
install -m 644 "$ART_DIR/frontend/dist/index.html" "$APP_DIR/frontend/dist/index.html"

echo "▶ 8. 안 쓰는 이미지 정리"
docker image prune -f

echo "✅ 배포 완료"
docker compose ps