import { SAMPLE_HOME, SAMPLE_RECOMMEND } from "./sampleData";
// 개인 PC 주소를 코드에 넣지 않기 위해 환경변수 사용
// 로컬: frontend/.env.local 에 VITE_API_BASE_URL=http://localhost:8080
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";
// 팀 예시 데이터 기준일(2026-10-21) 확인용. 비워두면 서버가 오늘 날짜 사용
const HOME_DATE = import.meta.env.VITE_HOME_DATE;
// DB에 색상 컬럼이 없어서 화면에서 순서대로 지정
const COLORS = ["#d6b44c", "#6f99a1", "#a68fb8"];

// 공통 fetch 함수: 실패하면 에러를 던진다
async function request(path, options) {
    const res = await fetch(`${BASE_URL}${path}`, {
        headers: { "Content-Type": "application/json" },
        ...options,
    });
    if (!res.ok) throw new Error(`${res.status} ${path}`);
    return res.status === 204 ? null : res.json();
}

// 오늘 날짜 "yyyy-MM-dd" (toISOString은 UTC라 한국 날짜가 하루 밀릴 수 있어서 직접 만듦)
function todayString() {
    // const d = new Date();
    // const mm = String(d.getMonth() + 1).padStart(2, "0");
    // const dd = String(d.getDate()).padStart(2, "0");
    // return `${d.getFullYear()}-${mm}-${dd}`;
    return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Seoul" }).format(new Date());

}

/** 홈 화면 데이터: user + 알림 + 오늘 복용 목록을 합쳐서 예전 /api/home 모양으로 */
export async function getHome(userId) {
    try {
        const date = HOME_DATE ?? todayString();
        const [user, unread, supplements] = await Promise.all([
            request(`/api/v1/users/${userId}`),
            request(`/api/v1/notifications/unread-count`, {
                headers: { "X-User-Id": String(userId) },
            }),
            request(`/api/v1/intakes/today?date=${date}`, {
                headers: { "X-User-Id": String(userId) },
            }),
        ]);

        const data = {
            userName: user.data.nickname,                       // ApiResponse로 감싸져 있어서 .data
            today: date,
            hasUnreadNotifications: unread.data.unreadCount > 0, // ApiResponse로 감싸져 있어서 .data
            supplements: (supplements ?? []).map((s, i) => ({
                ...s,
                color: s.color ?? COLORS[i % COLORS.length],
            })),
        };
        return { data, isSample: false };
    } catch (e) {
        console.warn("[home] 홈 데이터 호출 실패 → 예시 데이터 사용", e);
        return { data: SAMPLE_HOME, isSample: true };
    }
}

/** POST /api/v1/intakes : 복용 체크 저장 (화면에 표시 중인 날짜로 저장) */
export async function updateIntake(userId, scheduleId, taken, date) {
    return request(`/api/v1/intakes`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
            "X-User-Id": String(userId),
        },
        body: JSON.stringify({ scheduleId, taken, date }),
    });
}

/** 맞춤 추천 요청 — 요청/응답 형식은 영준(AI)과 협의 필요 */
export async function getRecommendation(answers) {
    try {
        return await request("/api/home/recommend", {
            method: "POST",
            body: JSON.stringify(answers),
        });
    } catch (e) {
        console.warn("[home] 추천 API 호출 실패 → 예시 추천 사용", e);
        return SAMPLE_RECOMMEND;
    }
}