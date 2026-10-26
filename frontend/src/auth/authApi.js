import * as mockAuth from "./mockAuthApi";

// DB 없는 로컬 개발에서만 활성화. 배포 빌드에서는 사용 불가.
const USE_MOCK_AUTH = import.meta.env.DEV && import.meta.env.VITE_USE_MOCK_AUTH === "true";

const BASE_URL = import.meta.env.VITE_AUTH_API_BASE_URL ?? "";
let csrfToken = null;

async function request(path, { method = "GET", body } = {}) {
    const headers = { Accept: "application/json" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (method !== "GET") {
        headers["X-CSRF-Token"] = await ensureCsrf();
    }

    let response;
    try {
        response = await fetch(`${BASE_URL}${path}`, {
            method,
            headers,
            credentials: "include",
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

async function ensureCsrf() {
    if (csrfToken) return csrfToken;
    let response;
    try {
        response = await fetch(`${BASE_URL}/api/v1/auth/csrf`, {
            credentials: "include",
            headers: { Accept: "application/json" },
            cache: "no-store",
        });
    } catch {
        throw new Error("서버에 연결할 수 없습니다. 실행 상태를 확인해주세요.");
    }
    const payload = await response.json().catch(() => null);
    if (!response.ok || !payload?.data?.csrfToken) {
        throw new Error("보안 토큰을 가져오지 못했습니다.");
    }
    csrfToken = payload.data.csrfToken;
    return csrfToken;
}

export async function signup(data) {
    if (USE_MOCK_AUTH) return mockAuth.signup(data);
    return request("/api/v1/auth/signup", { method: "POST", body: data });
}

export async function login(data) {
    if (USE_MOCK_AUTH) return mockAuth.login(data);
    return request("/api/v1/auth/login", { method: "POST", body: data });
}

export async function getCurrentUser() {
    if (USE_MOCK_AUTH) return mockAuth.getCurrentUser();
    try {
        return await request("/api/v1/auth/me");
    } catch (error) {
        if (error.status === 401) return null;
        throw error;
    }
}

export async function logout() {
    if (USE_MOCK_AUTH) return mockAuth.logout();
    await request("/api/v1/auth/logout", { method: "POST" });
    csrfToken = null;
}
