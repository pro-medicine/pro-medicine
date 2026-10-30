import { api } from "./client";

export function getMyCalendar(start, end) {
  return api.get("/api/v1/intakes/calendar", {
    params: { start, end },
    headers: { "X-User-Id": 1 }, // TODO: 로그인 연동 후 제거
  });
}

export function getMyDayIntakes(date) {
  return api.get("/api/v1/intakes/today", {
    params: { date },
    headers: { "X-User-Id": 1 }, // 달력과 같은 방식 (TODO: 로그인 연동 후 제거)
  });
}

// 마이페이지 달력 진행 링: [{ date, expected, taken }] (오늘까지만)
export function getMyCalendarProgress(start, end) {
  return api.get("/api/v1/intakes/calendar/progress", {
    params: { start, end },
    headers: { "X-User-Id": 1 }, // TODO: 로그인 연동 후 제거
  });
}
