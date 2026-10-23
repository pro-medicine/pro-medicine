import axios from "axios";

const API_BASE_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";

export const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { "Content-Type": "application/json" },
});

api.interceptors.response.use(
  (response) => {
    const result = response.data;
    if (result && result.success === false) {
      throw new Error(result.message || "요청에 실패했습니다.");
    }
    return result?.data ?? result;
  },
  (error) => {
    const message =
      error.response?.data?.message ||
      error.message ||
      "요청 처리 중 오류가 발생했습니다.";
    return Promise.reject(new Error(message));
  },
);
