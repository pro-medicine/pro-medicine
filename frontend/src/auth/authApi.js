
import * as mockAuth from "./mockAuthApi";

import {
    setAccessToken,
    getAccessToken,
    clearAccessToken,
} from "./authToken";

const USE_MOCK_AUTH =
    import.meta.env.DEV &&
    import.meta.env.VITE_USE_MOCK_AUTH === "true";

const BASE_URL =
    import.meta.env.VITE_AUTH_API_BASE_URL ?? "";

async function request(path, { method = "GET", body } = {}) {
    const headers = {
        Accept: "application/json",
    };

    if (body !== undefined) {
        headers["Content-Type"] = "application/json";
    }

    const token = getAccessToken();

    if (token) {
        headers.Authorization = `Bearer ${token}`;
    }

    let response;

    try {
        response = await fetch(`${BASE_URL}${path}`, {
            method,
            headers,
            credentials: "omit",
            body: body === undefined
                ? undefined
                : JSON.stringify(body),
        });
    } catch {
        throw new Error(
            "서버에 연결할 수 없습니다. 실행 상태를 확인해주세요."
        );
    }

    const payload = await response.json().catch(() => null);

    if (!response.ok || !payload?.success) {
        const error = new Error(
            payload?.message ||
            `요청에 실패했습니다. (${response.status})`
        );

        error.status = response.status;
        throw error;
    }

    return payload.data;
}

// 회원가입
export async function signup(data) {
    if (USE_MOCK_AUTH) {
        return mockAuth.signup(data);
    }

    const result = await request("/api/v1/auth/signup", {
        method: "POST",
        body: data,
    });

    setAccessToken(result.accessToken);

    return result.user;
}

// 로그인
export async function login(data) {
    if (USE_MOCK_AUTH) {
        return mockAuth.login(data);
    }

    const result = await request("/api/v1/auth/login", {
        method: "POST",
        body: data,
    });

    setAccessToken(result.accessToken);

    return result.user;
}

// 현재 로그인 사용자
export async function getCurrentUser() {
    if (USE_MOCK_AUTH) {
        return mockAuth.getCurrentUser();
    }

    if (!getAccessToken()) {
        return null;
    }

    try {
        return await request("/api/v1/auth/me");
    } catch (error) {
        if (error.status === 401) {
            clearAccessToken();
            return null;
        }

        throw error;
    }
}

// 로그아웃
export async function logout() {
    if (USE_MOCK_AUTH) {
        return mockAuth.logout();
    }

    try {
        if (getAccessToken()) {
            await request("/api/v1/auth/logout", {
                method: "POST",
            });
        }
    } finally {
        clearAccessToken();
    }
}
