import { api } from "./client";

export function fetchUser() {
  return api.get(`/api/v1/users/me`);
}

export function updateNickname(nickname) {
  return api.patch(`/api/v1/users/me/nickname`, { nickname });
}

export function fetchHealthProfile() {
  return api.get(`/api/v1/users/health-profile`);
}

export function saveHealthProfile(form) {
  return api.put(`/api/v1/users/health-profile`, form);
}

// 연속 복용일(+최고 기록), 복용 중인 영양제 수, 이번 주·이번 달 달성률, 요일별·주차별 그래프 데이터
export function fetchIntakeStats() {
  return api.get("/api/v1/intakes/stats");
}

export function fetchConditions() {
  return api.get(`/api/v1/users/conditions`);
}

// 빈 배열이면 "없음"
export function saveConditions(conditions) {
  return api.put(`/api/v1/users/conditions`, { conditions });
}

// 나의 영양관리: 성분별 하루 섭취량과 권장량 대비 섭취율
export function fetchNutrition() {
  return api.get(`/api/v1/users/nutrition`);
}

// 내가 먹는 영양제: 복용 중인 제품 정보 + 복용 일정 목록
export function fetchMySupplements() {
  return api.get(`/api/v1/users/products`);
}

// 영양제 등록: { catalogProductId, schedules: [{ mealSlot, mealRelation, remindAt }] }
export function addMySupplement(body) {
  return api.post(`/api/v1/users/products`, body);
}

// 영양제 삭제 (복용 중단 처리)
export function removeMySupplement(userProductId) {
  return api.delete(`/api/v1/users/products/${userProductId}`);
}

// 복용 일정 수정: 일정 전체를 통째로 교체
export function updateSupplementSchedules(userProductId, schedules) {
  return api.put(`/api/v1/users/products/${userProductId}/schedules`, {
    schedules,
  });
}

// 등록 폼 제품 검색: 제품명이나 제조사에 검색어가 들어간 제품 최대 20개
export function searchCatalogProducts(keyword) {
  return api.get("/api/v1/catalog-products", { params: { keyword } });
}

export function updateShareIntake(shared) {
  return api.patch(`/api/v1/users/me/share-intake`, { shared });
}
