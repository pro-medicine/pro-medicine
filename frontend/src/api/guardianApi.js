import { api } from "./client";

// 지킴이 신청 (내가 지킴이, targetId는 보호 대상)
export function requestGuardian(targetId, inactivityDays) {
  return api.post("/api/v1/guardians", { targetId, inactivityDays });
}

// 내가 지키는 사람들 (보낸 신청 + 수락된 사람)
export function getWatching() {
  return api.get("/api/v1/guardians/watching");
}

// 나를 지키려는 사람들 (받은 신청 + 수락된 지킴이)
export function getMyGuardians() {
  return api.get("/api/v1/guardians/mine");
}

// 보호 대상이 수락
export function acceptGuardian(id) {
  return api.patch(`/api/v1/guardians/${id}/accept`, null);
}

// 미접속 일수 변경 (지킴이만)
export function changeGuardianDays(id, inactivityDays) {
  return api.patch(`/api/v1/guardians/${id}/days`, { inactivityDays });
}

// 거절 / 신청 취소 / 해지
export function removeGuardian(id) {
  return api.delete(`/api/v1/guardians/${id}`);
}
