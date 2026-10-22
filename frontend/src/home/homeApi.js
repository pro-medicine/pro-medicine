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

/** GET /home : 홈 화면 데이터 */
export async function getHome(userId) {
    try {
        const dateQuery = HOME_DATE ? `&date=${HOME_DATE}` : "";
        const data = await request(`/api/home?userId=${userId}${dateQuery}`);
        data.supplements = (data.supplements ?? []).map((s, i) => ({
            ...s,
            color: s.color ?? COLORS[i % COLORS.length],
        }));
        return { data, isSample: false };
    } catch (e) {
        console.warn("[home] /home 호출 실패 → 예시 데이터 사용", e);
        return { data: SAMPLE_HOME, isSample: true };
    }
}

/** PATCH /home/intake/{scheduleId} : 복용 체크 저장 */
export async function updateIntake(userId, scheduleId, taken) {
    return request(`/api/home/intake/${scheduleId}?userId=${userId}`, {
        method: "PATCH",
        body: JSON.stringify({ taken, date: HOME_DATE }),
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