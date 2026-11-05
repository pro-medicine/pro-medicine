import { api } from "./client";

// 친구 목록 조회
export function getFriends() {
  return api.get("/api/v1/friends");
}

// 친구 상세 조회
export function getFriendDetail(friendUserId) {
  return api.get(`/api/v1/friends/${friendUserId}`);
}

// 친구 삭제
export function deleteFriend(friendUserId) {
  return api.delete(`/api/v1/friends/${friendUserId}`);
}

export function getFriendCalendar(friendUserId, start, end) {
  return api.get(`/api/v1/friends/${friendUserId}/calendar`, {
    params: { start, end },
  });
}

export function getFriendDayIntakes(friendUserId, date) {
  return api.get(`/api/v1/friends/${friendUserId}/intakes`, {
    params: { date },
  });
}
