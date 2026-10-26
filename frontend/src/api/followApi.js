import { api } from "./client";

export function requestFollow(userId, nickname) {
  return api.post("/api/v1/follows", { nickname }, { params: { userId } });
}

export function getFollowRequests(userId) {
  return api.get("/api/v1/follows/requests", {
    params: { userId, direction: "received", status: "PENDING" },
  });
}

export function respondFollow(userId, followId, status) {
  return api.patch(
    `/api/v1/follows/${followId}`,
    { status },
    { params: { userId } },
  );
}
