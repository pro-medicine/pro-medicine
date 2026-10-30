
import { SAMPLE_HOME, SAMPLE_RECOMMEND } from "./sampleData";
import { fetchWithAuth } from "../auth/authApi";

// 팀 공통 api/client.js와 동일한 환경변수 사용
// 비워두면 Vite /api 프록시 사용 (권장)
// 로컬 직접 호출 시: VITE_API_URL=http://localhost:8080
const BASE_URL = import.meta.env.VITE_API_URL || "";

// 테스트용 날짜. 없으면 한국 시간 기준 오늘 날짜 사용
const HOME_DATE = import.meta.env.VITE_HOME_DATE;

// DB에 색상 컬럼이 없어 화면에서 순서대로 지정
const COLORS = ["#d6b44c", "#6f99a1", "#a68fb8"];

// 공통 요청 함수
// JWT Access Token 자동 첨부 및 만료 시 Refresh Token 갱신
async function request(path, options = {}) {
    const res = await fetchWithAuth(`${BASE_URL}${path}`, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            ...(options.headers ?? {}),
        },
    });

    if (!res.ok) {
        throw new Error(`${res.status} ${path}`);
    }

    return res.status === 204 ? null : res.json();
}

// 한국 시간 기준 오늘 날짜 yyyy-MM-dd
function todayString() {
    return new Intl.DateTimeFormat("en-CA", {
        timeZone: "Asia/Seoul",
    }).format(new Date());
}

/**
 * 홈 화면 데이터 조회
 * 사용자 정보 + 안 읽은 알림 + 오늘 복용 목록
 */
export async function getHome(userId) {
    try {
        const date = HOME_DATE ?? todayString();

        const [user, unread, supplements] = await Promise.all([
            request(`/api/v1/users/${userId}`),

            request("/api/v1/notifications/unread-count", {
                headers: {
                    "X-User-Id": String(userId),
                },
            }),

            request(`/api/v1/intakes/today?date=${date}`, {
                headers: {
                    "X-User-Id": String(userId),
                },
            }),
        ]);

        const data = {
            userName: user.data.nickname,
            today: date,
            hasUnreadNotifications: unread.data.unreadCount > 0,
            supplements: (supplements ?? []).map((s, i) => ({
                ...s,
                color: s.color ?? COLORS[i % COLORS.length],
            })),
        };

        return {
            data,
            isSample: false,
        };
    } catch (e) {
        console.warn(
            "[home] 홈 데이터 호출 실패 → 예시 데이터 사용",
            e
        );

        return {
            data: SAMPLE_HOME,
            isSample: true,
        };
    }
}

/**
 * 복용 체크 저장
 * POST /api/v1/intakes
 */
export async function updateIntake(userId, scheduleId, taken, date) {
    return request("/api/v1/intakes", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            "X-User-Id": String(userId),
        },
        body: JSON.stringify({
            scheduleId,
            taken,
            date,
        }),
    });
}

/**
 * 맞춤 영양제 추천
 * 영준(AI) 담당 API와 추후 연동
 */
export async function getRecommendation(answers) {
    try {
        return await request("/api/home/recommend", {
            method: "POST",
            body: JSON.stringify(answers),
        });
    } catch (e) {
        console.warn(
            "[home] 추천 API 호출 실패 → 예시 추천 사용",
            e
        );

        return SAMPLE_RECOMMEND;
    }
}

/**
 * 홈 복용 알림 카드 조회 - develop 신규 기능
 *
 * GET /api/v1/notifications/intake-summary
 *
 * 다음 복용 알림 + 이번 주 제시간 복용률
 * JWT의 사용자 ID로 조회하므로 X-User-Id 헤더 불필요
 */
export async function getIntakeSummary(userId) {
    const res = await request(
        "/api/v1/notifications/intake-summary"
    );

    return res.data;
}
