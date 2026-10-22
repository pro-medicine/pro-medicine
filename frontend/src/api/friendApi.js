const API_BASE_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";

// 친구 목록 조회
export async function getFriends(userId) {
  const response = await fetch(
    `${API_BASE_URL}/api/v1/friends?userId=${userId}`,
  );

  if (!response.ok) {
    throw new Error(`친구 목록 조회 실패 (${response.status})`);
  }

  const result = await response.json();

  if (!result.success) {
    throw new Error(result.message || "친구 목록 조회 실패");
  }

  return result.data;
}

// 친구 상세 조회
export async function getFriendDetail(userId, friendUserId) {
  const response = await fetch(
    `${API_BASE_URL}/api/v1/friends/${friendUserId}?userId=${userId}`,
  );

  if (!response.ok) {
    throw new Error(`친구 상세 조회 실패 (${response.status})`);
  }

  const result = await response.json();

  if (!result.success) {
    throw new Error(result.message || "친구 상세 조회 실패");
  }

  return result.data;
}
