import { api } from "./client";

export function createPrescription(payload) {
  return api.post("/api/v1/prescriptions", payload);
}

export function deletePrescription(prescriptionId) {
  return api.delete(`/api/v1/prescriptions/${prescriptionId}`);
}
