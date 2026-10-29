import * as mockAuth from "./mockAuthApi";
import { setAccessToken, getAccessToken, clearAccessToken } from "./authToken";

const USE_MOCK_AUTH = import.meta.env.DEV && import.meta.env.VITE_USE_MOCK_AUTH === "true";
const BASE_URL = import.meta.env.VITE_AUTH_API_BASE_URL ?? "";
export const SESSION_EXPIRED_EVENT = "yakssok:session-expired";

let refreshPromise = null;
let authGeneration = 0;

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
        throw error;
    }
    return payload.data;
}

/**
 * Single-flight within a tab, optionally serialized across tabs via Web Locks.
 * A new opaque refresh token is returned only as an HttpOnly Set-Cookie.
 */
export async function refreshAccessToken() {
    if (USE_MOCK_AUTH) return null;
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

export async function signup(data) {
    if (USE_MOCK_AUTH) return mockAuth.signup(data);
    const result = await request("/api/v1/auth/signup", {
        method: "POST", body: data, credentials: "include", useAuth: false,
    });
    authGeneration += 1;
    setAccessToken(result.accessToken);
    return result.user;
}

export async function login(data) {
    if (USE_MOCK_AUTH) return mockAuth.login(data);
    const result = await request("/api/v1/auth/login", {
        method: "POST", body: data, credentials: "include", useAuth: false,
    });
    authGeneration += 1;
    setAccessToken(result.accessToken);
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

export async function logout() {
    if (USE_MOCK_AUTH) return mockAuth.logout();
    authGeneration += 1; // Don't let an in-flight refresh restore a logged-out user.
    clearAccessToken();
    try {
        await request("/api/v1/auth/logout", {
            method: "POST", credentials: "include", useAuth: false,
        });
    } finally {
        clearAccessToken();
    }
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
