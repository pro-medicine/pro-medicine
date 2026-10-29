import { api } from "./client";

const headers = { "X-User-Id": 1 }; // TODO: 로그인 연동 후 제거 (intakeApi와 같은 방식)

export function getCalendarEvents(start, end) {
  return api.get("/api/v1/calendar-events", {
    params: { start, end },
    headers,
  });
}

export function createCalendarEvent(payload) {
  return api.post("/api/v1/calendar-events", payload, { headers });
}

export function updateCalendarEvent(eventId, payload) {
  return api.put(`/api/v1/calendar-events/${eventId}`, payload, { headers });
}

export function deleteCalendarEvent(eventId) {
  return api.delete(`/api/v1/calendar-events/${eventId}`, { headers });
}
