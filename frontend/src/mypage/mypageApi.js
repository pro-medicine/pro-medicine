// 로그인 기능이 생기기 전까지 사용하는 테스트 유저
export const MY_USER_ID = 1;

// 실패하면 서버가 보낸 message로 에러를 던짐
async function request(url, method = "GET", body) {
  const response = await fetch(url, {
    method,
    headers:
      body === undefined ? undefined : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const result = await response.json();
  // 성공 여부는 success로만 판단 (삭제처럼 데이터 없는 성공 응답은 data 칸이 빠져서 옴)
  if (!result.success) {
    throw new Error(result.message);
  }
  // 데이터 없는 응답은 null로 통일
  return result.data ?? null;
}

export function fetchUser(userId) {
  return request(`/api/v1/users/${userId}`);
}

export function updateNickname(userId, nickname) {
  return request(`/api/v1/users/${userId}/nickname`, "PATCH", { nickname });
}

export function fetchHealthProfile(userId) {
  return request(`/api/v1/users/${userId}/health-profile`);
}

export function saveHealthProfile(userId, form) {
  return request(`/api/v1/users/${userId}/health-profile`, "PUT", form);
}

// 연속 복용일, 복용 중인 영양제 수, 이번 달 달성률
export function fetchIntakeStats(userId) {
  return request(`/api/v1/users/${userId}/intake-stats`);
}

export function fetchConditions(userId) {
  return request(`/api/v1/users/${userId}/conditions`);
}

// 빈 배열이면 "없음"
export function saveConditions(userId, conditions) {
  return request(`/api/v1/users/${userId}/conditions`, "PUT", { conditions });
}

// 나의 영양관리: 성분별 하루 섭취량과 권장량 대비 섭취율
export function fetchNutrition(userId) {
  return request(`/api/v1/users/${userId}/nutrition`);
}

// 내가 먹는 영양제: 복용 중인 제품 정보 + 복용 일정 목록
export function fetchMySupplements(userId) {
  return request(`/api/v1/users/${userId}/products`);
}

// 영양제 등록: { catalogProductId, schedules: [{ mealSlot, mealRelation, remindAt }] }
export function addMySupplement(userId, body) {
  return request(`/api/v1/users/${userId}/products`, "POST", body);
}

// 영양제 삭제 (복용 중단 처리)
export function removeMySupplement(userId, userProductId) {
  return request(`/api/v1/users/${userId}/products/${userProductId}`, "DELETE");
}

// 복용 일정 수정: 일정 전체를 통째로 교체
export function updateSupplementSchedules(userId, userProductId, schedules) {
  return request(
    `/api/v1/users/${userId}/products/${userProductId}/schedules`,
    "PUT",
    { schedules },
  );
}

// 등록 폼 제품 검색: 제품명에 검색어가 들어간 제품 최대 20개
export function searchCatalogProducts(keyword) {
  return request(
    `/api/v1/catalog-products?keyword=${encodeURIComponent(keyword)}`,
  );
}
