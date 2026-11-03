import { api } from "./client";

export function requestFollow(nickname) {
  return api.post("/api/v1/follows", { nickname });
}

export function getFollowRequests(userId, direction = "received") {
  return api.get("/api/v1/follows/requests", {
    params: { userId, direction, status: "PENDING" },
  });
}

export function respondFollow(userId, followId, status) {
  return api.patch(
    `/api/v1/follows/${followId}`,
    { status },
    { params: { userId } },
  );
}

export function cancelFollow(userId, followId) {
  return api.delete(`/api/v1/follows/${followId}`, { params: { userId } });
}

export function getFollowers() {
  return api.get(`/api/v1/follows/followers`);
}
