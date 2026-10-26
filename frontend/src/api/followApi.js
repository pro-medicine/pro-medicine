const API_BASE_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";

// 닉네임으로 친구 신청
export async function requestFollow(userId, nickname) {
  const response = await fetch(
    `${API_BASE_URL}/api/v1/follows?userId=${userId}`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ nickname }),
    },
  );

  const result = await response.json();

  if (!response.ok || !result.success) {
    throw new Error(result.message || "친구 신청에 실패했습니다.");
  }

  return result.data;
}

// 받은 친구 신청 목록
export async function getFollowRequests(userId) {
  const response = await fetch(
    `${API_BASE_URL}/api/v1/follows/requests?userId=${userId}&direction=received&status=PENDING`,
  );

  if (!response.ok) {
    throw new Error("친구 신청 목록 조회에 실패했습니다.");
  }

  const result = await response.json();

  if (!result.success) {
    throw new Error(result.message || "친구 신청 목록 조회 실패");
  }

  return result.data;
}

// 친구 신청 수락 또는 거절
export async function respondFollow(userId, followId, status) {
  const response = await fetch(
    `${API_BASE_URL}/api/v1/follows/${followId}?userId=${userId}`,
    {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ status }),
    },
  );

  const result = await response.json();

  if (!response.ok || !result.success) {
    throw new Error(result.message || "친구 요청 처리에 실패했습니다.");
  }

  return result.data;
}
