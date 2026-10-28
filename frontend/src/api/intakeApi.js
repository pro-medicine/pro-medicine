import { api } from "./client";

export function getMyCalendar(start, end) {
  return api.get("/api/v1/intakes/calendar", {
    params: { start, end },
    headers: { "X-User-Id": 1 }, // TODO: 로그인 연동 후 제거
  });
}
