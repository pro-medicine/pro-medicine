import { SAMPLE_HOME, SAMPLE_RECOMMEND } from "./sampleData";
// 개인 PC 주소를 코드에 넣지 않기 위해 환경변수 사용
// 로컬: frontend/.env.local 에 VITE_API_BASE_URL=http://localhost:8080
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

// 공통 fetch 함수: 실패하면 에러를 던진다
async function request(path, options) {
    const res = await fetch(`${BASE_URL}${path}`, {
        headers: { "Content-Type": "application/json" },
        ...options,
    });
    if (!res.ok) throw new Error(`${res.status} ${path}`);
    return res.status === 204 ? null : res.json();
}

/** H-1 GET /home : 홈 화면 데이터 */
export async function getHome(userId) {
    try {
        return { data: await request(`/home?userId=${userId}`), isSample: false };
    } catch (e) {
        console.warn("[home] /home 호출 실패 → 예시 데이터 사용", e);
        return { data: SAMPLE_HOME, isSample: true };
    }
}

/** 복용 체크 저장 (경로는 백엔드 구현 시 확정) */
export async function updateIntake(supplementId, taken) {
    return request(`/home/intake/${supplementId}`, {
        method: "PATCH",
        body: JSON.stringify({ taken }),
    });
}

/** 맞춤 추천 요청 — 요청/응답 형식은 영준(AI)과 협의 필요 */
export async function getRecommendation(answers) {
    try {
        return await request("/home/recommend", {
            method: "POST",
            body: JSON.stringify(answers),
        });
    } catch (e) {
        console.warn("[home] 추천 API 호출 실패 → 예시 추천 사용", e);
        return SAMPLE_RECOMMEND;
    }
}