#!/usr/bin/env bash
# =============================================================
# 약쏙 무중단 배포 스크립트 (EC2에서 실행)
#  1) 새 백엔드를 띄우되, nginx는 계속 기존 백엔드로만 요청을 보냄
#  2) 새 백엔드가 완전히 켜지면 nginx 대상을 새 백엔드로 바꾸고 reload (처리 중인 요청은 그대로 완료)
#  3) 잠시 기다린 뒤 기존 백엔드를 정상 종료
#  4) 프론트 정적 파일 교체 (nginx 재시작 없음)
# GitHub Actions가 ~/pro-medicine-artifacts 에 jar·dist를 올린 뒤 실행합니다.
# =============================================================
set -euo pipefail

APP_DIR="$HOME/pro-medicine"
ART_DIR="$HOME/pro-medicine-artifacts"
WAIT_SECONDS=300
DRAIN_SECONDS=15
UPSTREAM_FILE="frontend/upstream/backend.conf"

# 백엔드 컨테이너가 "Started Application"을 찍을 때까지 대기 (성공 0 / 실패 1)
wait_for_started() {
  local id="$1"
  for ((i = 0; i < WAIT_SECONDS; i += 5)); do
    if docker logs "$id" 2>&1 | grep -q "Started Application"; then
      return 0
    fi
    # restarting(자동 재시작 중)은 기다리고, 완전히 꺼진 경우만 실패 처리
    case "$(docker inspect -f '{{.State.Status}}' "$id" 2>/dev/null || echo missing)" in
      exited | dead | missing) return 1 ;;
    esac
    sleep 5
  done
  return 1
}

# 컨테이너 ID → 컨테이너 이름 (예: pro-medicine-backend-2)
container_name() {
  docker inspect -f '{{.Name}}' "$1" | sed 's#^/##'
}

# nginx가 요청을 보낼 백엔드를 지정하고 무중단 reload
point_nginx_to() {
  local name="$1"
  printf 'set $backend http://%s:8080;\n' "$name" > "$UPSTREAM_FILE"
  for ((i = 0; i < 10; i++)); do
    if docker compose exec -T frontend nginx -t >/dev/null 2>&1 &&
       docker compose exec -T frontend nginx -s reload; then
      echo "   nginx → $name"
      return 0
    fi
    sleep 2
  done
  echo "❌ nginx reload에 실패했습니다."
  return 1
}

cd "$APP_DIR"

echo "▶ 0. 폴더 준비 (nginx 연결 전에 배포 사용자 권한으로)"
for dir in frontend/dist frontend/upstream; do
  mkdir -p "$dir"
  if [ ! -w "$dir" ]; then
    echo "   $dir 가 root 소유라 권한을 복구합니다"
    sudo chown -R "$(id -u):$(id -g)" "$dir"
  fi
done

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
  echo "▶ 4. 기존 백엔드가 없어 바로 실행합니다 (최초 배포)"
  docker compose up -d backend
  NEW_ID="$(docker compose ps -q backend | head -n 1)"

  echo "▶ 5. 백엔드 기동 대기 (최대 ${WAIT_SECONDS}초)"
  if ! wait_for_started "$NEW_ID"; then
    echo "❌ 백엔드가 정상 기동하지 않았습니다. 배포를 중단합니다."
    docker logs --tail 50 "$NEW_ID" || true
    exit 1
  fi
  echo "   백엔드 기동 완료"
  point_nginx_to "$(container_name "$NEW_ID")"
else
  OLD_NAME="$(container_name "$OLD_ID")"

  echo "▶ 4. nginx를 기존 백엔드로 고정한 뒤 새 백엔드 추가 실행"
  # 새 컨테이너가 켜지는 동안 요청을 받지 않도록, nginx 대상을 기존 백엔드로 명시
  point_nginx_to "$OLD_NAME"
  docker compose up -d --no-deps --no-recreate --scale backend=2 backend
  NEW_ID="$(docker compose ps -q backend | grep -v "$OLD_ID" | head -n 1)"

  echo "▶ 5. 새 백엔드 기동 대기 (최대 ${WAIT_SECONDS}초)"
  if ! wait_for_started "$NEW_ID"; then
    echo "❌ 새 백엔드가 정상 기동하지 않았습니다. 기존 백엔드를 그대로 유지합니다."
    docker logs --tail 50 "$NEW_ID" || true
    docker rm -f "$NEW_ID"
    exit 1
  fi
  echo "   새 백엔드 기동 완료"

  echo "▶ 6. nginx 대상을 새 백엔드로 전환 (처리 중인 요청은 기존 백엔드에서 마무리)"
  point_nginx_to "$(container_name "$NEW_ID")"

  echo "▶ 7. ${DRAIN_SECONDS}초 대기 후 기존 백엔드 정상 종료"
  sleep "$DRAIN_SECONDS"
  docker stop -t 30 "$OLD_ID"
  docker rm "$OLD_ID"
fi

echo "▶ 8. 프론트 정적 파일 교체"
# 파일을 하나씩 복사하고, 하나라도 실패하면 즉시 중단 (index.html은 모두 성공한 뒤 마지막에 교체)
while IFS= read -r -d '' file; do
  install -D -m 644 "$ART_DIR/frontend/dist/$file" "$APP_DIR/frontend/dist/$file"
done < <(cd "$ART_DIR/frontend/dist" && find . -type f ! -name index.html -print0)
install -m 644 "$ART_DIR/frontend/dist/index.html" "$APP_DIR/frontend/dist/index.html"

echo "▶ 9. 안 쓰는 이미지 정리"
docker image prune -f

echo "✅ 배포 완료"
docker compose ps