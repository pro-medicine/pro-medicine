import * as mockAuth from "./mockAuthApi";
import { setAccessToken, getAccessToken, clearAccessToken } from "./authToken";

const USE_MOCK_AUTH = import.meta.env.DEV && import.meta.env.VITE_USE_MOCK_AUTH === "true";
const BASE_URL = import.meta.env.VITE_AUTH_API_BASE_URL ?? "";
export const SESSION_EXPIRED_EVENT = "yakssok:session-expired";

let refreshPromise = null;
let logoutPromise = null;
let authGeneration = 0;

const GUEST_DEMOGRAPHICS_KEY = "yakssok:guest-survey-demographics";

export function saveGuestSurveyDemographics(responseId, { birthYear, gender }) {
    if (!Number.isSafeInteger(responseId) || responseId <= 0) return;
    const currentYear = new Date().getFullYear();
    const year = Number.isInteger(birthYear) && birthYear >= 1900 && birthYear <= currentYear
        ? birthYear : null;
    const selectedGender = ["남성", "여성", "선택 안 함"].includes(gender) ? gender : null;
    try {
        sessionStorage.removeItem(GUEST_DEMOGRAPHICS_KEY);
        if (year != null || selectedGender != null) {
            sessionStorage.setItem(GUEST_DEMOGRAPHICS_KEY, JSON.stringify({
                responseId, birthYear: year, gender: selectedGender,
            }));
        }
    } catch {
    }
}

export function getPendingGuestDemographics() {
    const pending = getPendingGuestSurvey();
    if (!pending) return null;
    try {
        const saved = JSON.parse(sessionStorage.getItem(GUEST_DEMOGRAPHICS_KEY) || "null");
        if (!saved || saved.responseId !== pending.guestResponseId) return null;
        const year = Number(saved.birthYear);
        const currentYear = new Date().getFullYear();
        const birthYear = Number.isInteger(year) && year >= 1900 && year <= currentYear
            ? year : null;
        const gender = ["남성", "여성", "선택 안 함"].includes(saved.gender)
            ? saved.gender : null;
        return birthYear != null || gender != null
            ? { responseId: pending.guestResponseId, birthYear, gender } : null;
    } catch {
        return null;
    }
}

export function getPendingGuestSurvey() {
    if (USE_MOCK_AUTH) return null;
    const responseId = Number(sessionStorage.getItem("guestSurveyResponseId"));
    const guestToken = sessionStorage.getItem("guestToken");
    if (!Number.isSafeInteger(responseId) || responseId <= 0 || !/^[0-9a-f]{64}$/.test(guestToken ?? "")) return null;
    return { guestResponseId: responseId, guestToken };
}

function clearPendingGuestSurvey() {
    sessionStorage.removeItem("guestSurveyResponseId");
    sessionStorage.removeItem("guestToken");
    sessionStorage.removeItem(GUEST_DEMOGRAPHICS_KEY);
}

async function request(path, { method = "GET", body, credentials = "omit", useAuth = true } = {}) {
    const headers = { Accept: "application/json" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    const token = getAccessToken();
    if (useAuth && token) headers.Authorization = `Bearer ${token}`;

    let response;
    try {
        response = await fetch(`${BASE_URL}${path}`, {
            method,
            headers,
            credentials,
            body: body === undefined ? undefined : JSON.stringify(body),
        });
    } catch {
        throw new Error("서버에 연결할 수 없습니다. 실행 상태를 확인해주세요.");
    }
    const payload = await response.json().catch(() => null);
    if (!response.ok || !payload?.success) {
        const error = new Error(payload?.message || `요청에 실패했습니다. (${response.status})`);
        error.status = response.status;
        error.code = payload?.code;
        throw error;
    }
    return payload.data;
}

/**
 * Single-flight within a tab, optionally serialized across tabs via Web Locks.
 * A new opaque refresh token is returned only as an HttpOnly Set-Cookie.
 */
export async function refreshAccessToken() {
    if (USE_MOCK_AUTH || logoutPromise) return null;
    if (!refreshPromise) {
        const generation = authGeneration;
        const perform = async () => {
            try {
                const result = await request("/api/v1/auth/refresh", {
                    method: "POST", credentials: "include", useAuth: false,
                });
                if (generation !== authGeneration) return null;
                setAccessToken(result.accessToken);
                return result.accessToken;
            } catch (error) {
                if (generation !== authGeneration) return null;
                if (error.status === 401 || error.status === 403) {
                    const hadAccessToken = Boolean(getAccessToken());
                    clearAccessToken();
                    if (hadAccessToken) window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
                    return null;
                }
                throw error;
            }
        };
        refreshPromise = (async () => {
            if (typeof navigator !== "undefined" && navigator.locks?.request) {
                return navigator.locks.request("yakssok-refresh", perform);
            }
            return perform();
        })().finally(() => { refreshPromise = null; });
    }
    return refreshPromise;
}

export async function signup(data, guestV1 = null) {
    const generation = ++authGeneration;
    if (USE_MOCK_AUTH) {
        const user = await mockAuth.signup(data, () => generation === authGeneration);
        if (user) clearPendingGuestSurvey();
        return user;
    }
    const result = await request("/api/v1/auth/signup", {
        method: "POST", body: guestV1 ? { ...data, ...guestV1 } : data,
        credentials: "include", useAuth: false,
    });
    if (generation !== authGeneration) return null;
    setAccessToken(result.accessToken);
    clearPendingGuestSurvey();
    return result.user;
}

export async function login(data) {
    const generation = ++authGeneration;
    if (USE_MOCK_AUTH) {
        const user = await mockAuth.login(data, () => generation === authGeneration);
        if (user) clearPendingGuestSurvey();
        return user;
    }
    const result = await request("/api/v1/auth/login", {
        method: "POST", body: data, credentials: "include", useAuth: false,
    });
    if (generation !== authGeneration) return null;
    setAccessToken(result.accessToken);
    clearPendingGuestSurvey();
    return result.user;
}

export async function getCurrentUser() {
    if (USE_MOCK_AUTH) return mockAuth.getCurrentUser();
    if (!getAccessToken() && !(await refreshAccessToken())) return null;
    try {
        return await request("/api/v1/auth/me");
    } catch (error) {
        if (error.status !== 401) throw error;
        if (!(await refreshAccessToken())) return null;
        try {
            return await request("/api/v1/auth/me");
        } catch (lastError) {
            if (lastError.status === 401) {
                clearAccessToken();
                return null;
            }
            throw lastError;
        }
    }
}

export async function withdrawAccount(password) {
    if (USE_MOCK_AUTH) throw new Error("실제 계정에서만 회원 탈퇴할 수 있습니다.");
    const options = {
        method: "DELETE", body: { password }, credentials: "include",
    };
    const attemptedToken = getAccessToken();
    try {
        await request("/api/v1/users/me", options);
    } catch (error) {
        if (error.status !== 401 || error.code === "U004" || !attemptedToken) throw error;
        const currentToken = getAccessToken();
        const token = currentToken && currentToken !== attemptedToken
            ? currentToken : await refreshAccessToken();
        if (!token) throw error;
        await request("/api/v1/users/me", options);
    }
    authGeneration += 1;
    clearAccessToken();
    clearPendingGuestSurvey();
}

export function logout() {
    if (USE_MOCK_AUTH) return mockAuth.logout();
    if (logoutPromise) return logoutPromise;

    // Prevent an earlier refresh response from restoring the cookie after logout.
    logoutPromise = (async () => {
        if (refreshPromise) {
            try {
                await refreshPromise;
            } catch {
                // Even if refresh cannot connect, still attempt server logout.
            }
        }

        await request("/api/v1/auth/logout", {
            method: "POST", credentials: "include", useAuth: false,
        });

        // Only clear local auth state after the server confirms logout.
        authGeneration += 1;
        clearAccessToken();
    })().finally(() => {
        logoutPromise = null;
    });
    return logoutPromise;
}

/** Fetch wrapper for APIs that don't use the shared Axios client (notably Home). */
export async function fetchWithAuth(path, options = {}) {
    const execute = () => {
        const token = getAccessToken();
        return fetch(path, {
            ...options,
            headers: {
                ...(options.headers || {}),
                ...(token ? { Authorization: `Bearer ${token}` } : {}),
            },
        });
    };
    let response = await execute();
    if (response.status !== 401 || !getAccessToken()) return response;
    const renewed = await refreshAccessToken();
    if (renewed) response = await execute();
    return response;
}
