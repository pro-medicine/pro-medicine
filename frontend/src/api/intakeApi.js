import { api } from "./client";

// 로그인 사용자는 client.js가 붙이는 Bearer 토큰으로 서버가 구분한다 (X-User-Id 헤더 불필요)

export function getMyCalendar(start, end) {
  return api.get("/api/v1/intakes/calendar", {
    params: { start, end },
  });
}

export function getMyDayIntakes(date) {
  return api.get("/api/v1/intakes/today", {
    params: { date },
  });
}

// 마이페이지 달력 진행 링: [{ date, expected, taken }] (오늘까지만)
export function getMyCalendarProgress(start, end) {
  return api.get("/api/v1/intakes/calendar/progress", {
    params: { start, end },
  });
}
