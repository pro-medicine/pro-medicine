import { api } from "./client";

// 지킴이 신청 (내가 지킴이, targetId는 보호 대상)
export function requestGuardian(userId, targetId, inactivityDays) {
  return api.post(
    "/api/v1/guardians",
    { targetId, inactivityDays },
    { params: { userId } },
  );
}

// 내가 지키는 사람들 (보낸 신청 + 수락된 사람)
export function getWatching(userId) {
  return api.get("/api/v1/guardians/watching", { params: { userId } });
}

// 나를 지키려는 사람들 (받은 신청 + 수락된 지킴이)
export function getMyGuardians(userId) {
  return api.get("/api/v1/guardians/mine", { params: { userId } });
}

// 보호 대상이 수락
export function acceptGuardian(userId, id) {
  return api.patch(`/api/v1/guardians/${id}/accept`, null, {
    params: { userId },
  });
}

// 미접속 일수 변경 (지킴이만)
export function changeGuardianDays(userId, id, inactivityDays) {
  return api.patch(
    `/api/v1/guardians/${id}/days`,
    { inactivityDays },
    { params: { userId } },
  );
}

// 거절 / 신청 취소 / 해지
export function removeGuardian(userId, id) {
  return api.delete(`/api/v1/guardians/${id}`, { params: { userId } });
}
