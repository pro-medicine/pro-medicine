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

const unwrap = (res) => res?.data?.data ?? res?.data ?? res;

export function fetchInteractions(prescriptionId) {
  return api
    .get(`/api/v1/prescriptions/${prescriptionId}/interactions`)
    .then(unwrap);
}

export function searchMedicines(q) {
  return api.get(`/api/v1/medicines/search`, { params: { q } }).then(unwrap);
}

export function previewInteractions(
  medicineId,
  startAt,
  endAt,
  excludePrescriptionId,
) {
  return api
    .get(`/api/v1/medicines/${medicineId}/interactions`, {
      params: { startAt, endAt, excludePrescriptionId },
    })
    .then(unwrap);
}
