import { api } from "./client";

// 친구 목록 조회
export function getFriends(userId) {
  return api.get("/api/v1/friends", { params: { userId } });
}

// 친구 상세 조회
export function getFriendDetail(userId, friendUserId) {
  return api.get(`/api/v1/friends/${friendUserId}`, { params: { userId } });
}

// 친구 삭제
export function deleteFriend(userId, friendUserId) {
  return api.delete(`/api/v1/friends/${friendUserId}`, { params: { userId } });
}
