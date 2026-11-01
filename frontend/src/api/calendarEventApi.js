import { api } from "./client";

export function getCalendarEvents(start, end) {
  return api.get("/api/v1/calendar-events", {
    params: { start, end },
  });
}

export function createCalendarEvent(payload) {
  return api.post("/api/v1/calendar-events", payload);
}

export function updateCalendarEvent(eventId, payload) {
  return api.put(`/api/v1/calendar-events/${eventId}`, payload);
}

export function updateCalendarEventStatus(eventId, status) {
  return api.patch(`/api/v1/calendar-events/${eventId}/status`, { status });
}

export function deleteCalendarEvent(eventId) {
  return api.delete(`/api/v1/calendar-events/${eventId}`);
}

export function createPrescription(payload) {
  return api.post("/api/v1/prescriptions", payload);
}
