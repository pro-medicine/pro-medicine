import { api } from "./client";

// 로그인 기능이 생기기 전까지 사용하는 테스트 유저
export const MY_USER_ID = 1;

export function fetchUser(userId) {
  return api.get(`/api/v1/users/${userId}`);
}

export function updateNickname(userId, nickname) {
  return api.patch(`/api/v1/users/${userId}/nickname`, { nickname });
}

export function fetchHealthProfile(userId) {
  return api.get(`/api/v1/users/${userId}/health-profile`);
}

export function saveHealthProfile(userId, form) {
  return api.put(`/api/v1/users/${userId}/health-profile`, form);
}

// 연속 복용일(+최고 기록), 복용 중인 영양제 수, 이번 주·이번 달 달성률, 요일별·주차별 그래프 데이터
export function fetchIntakeStats(userId) {
  return api.get("/api/v1/intakes/stats", { params: { userId } });
}

export function fetchConditions(userId) {
  return api.get(`/api/v1/users/${userId}/conditions`);
}

// 빈 배열이면 "없음"
export function saveConditions(userId, conditions) {
  return api.put(`/api/v1/users/${userId}/conditions`, { conditions });
}

// 나의 영양관리: 성분별 하루 섭취량과 권장량 대비 섭취율
export function fetchNutrition(userId) {
  return api.get(`/api/v1/users/${userId}/nutrition`);
}

// 내가 먹는 영양제: 복용 중인 제품 정보 + 복용 일정 목록
export function fetchMySupplements(userId) {
  return api.get(`/api/v1/users/${userId}/products`);
}

// 영양제 등록: { catalogProductId, schedules: [{ mealSlot, mealRelation, remindAt }] }
export function addMySupplement(userId, body) {
  return api.post(`/api/v1/users/${userId}/products`, body);
}

// 영양제 삭제 (복용 중단 처리)
export function removeMySupplement(userId, userProductId) {
  return api.delete(`/api/v1/users/${userId}/products/${userProductId}`);
}

// 복용 일정 수정: 일정 전체를 통째로 교체
export function updateSupplementSchedules(userId, userProductId, schedules) {
  return api.put(
    `/api/v1/users/${userId}/products/${userProductId}/schedules`,
    { schedules },
  );
}

// 등록 폼 제품 검색: 제품명이나 제조사에 검색어가 들어간 제품 최대 20개
export function searchCatalogProducts(keyword) {
  return api.get("/api/v1/catalog-products", { params: { keyword } });
}
