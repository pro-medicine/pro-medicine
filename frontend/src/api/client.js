import axios from "axios";
import { getAccessToken } from "../auth/authToken";
import { refreshAccessToken } from "../auth/authApi";

const API_BASE_URL = import.meta.env.VITE_API_URL || "";

export const api = axios.create({
    baseURL: API_BASE_URL,
    headers: { "Content-Type": "application/json" },
});

api.interceptors.request.use((config) => {
    const token = getAccessToken();
    if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
});

api.interceptors.response.use(
    (response) => {
        const result = response.data;
        if (result && result.success === false) throw new Error(result.message || "요청에 실패했습니다.");
        return result?.data ?? result;
    },
    async (error) => {
        const original = error.config;
        if (error.response?.status === 401 && original && !original._authRetried && getAccessToken()) {
            original._authRetried = true;
            const attemptedToken = original.headers?.Authorization;
            const currentToken = getAccessToken();
            try {
                const token = attemptedToken && attemptedToken !== `Bearer ${currentToken}`
                    ? currentToken : await refreshAccessToken();
                if (token) {
                    original.headers.Authorization = `Bearer ${token}`;
                    return api(original);
                }
            } catch (refreshError) {
                // Preserve the original API error when refresh cannot connect.
                console.warn("Access Token 갱신 실패", refreshError);
            }
        }
        const message = error.response?.data?.message || error.message || "요청 처리 중 오류가 발생했습니다.";
        return Promise.reject(new Error(message));
    },
);
