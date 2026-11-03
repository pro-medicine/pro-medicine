import { api } from "./client";

export function requestFollow(nickname) {
  return api.post("/api/v1/follows", { nickname });
}

export function getFollowRequests(direction = "received") {
  return api.get("/api/v1/follows/requests", {
    params: { direction, status: "PENDING" },
  });
}

export function respondFollow(followId, status) {
  return api.patch(`/api/v1/follows/${followId}`, { status });
}

export function cancelFollow(followId) {
  return api.delete(`/api/v1/follows/${followId}`);
}

export function getFollowers() {
  return api.get(`/api/v1/follows/followers`);
}
