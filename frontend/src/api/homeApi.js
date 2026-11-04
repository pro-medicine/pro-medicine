import { SAMPLE_HOME, SAMPLE_RECOMMEND } from "../home/sampleData";
import { api } from "./client";

// 테스트용 날짜. 없으면 한국 시간 기준 오늘 날짜 사용
const HOME_DATE = import.meta.env.VITE_HOME_DATE;

// DB에 색상 컬럼이 없어 화면에서 순서대로 지정
const COLORS = ["#d6b44c", "#6f99a1", "#a68fb8"];

// 한국 시간 기준 오늘 날짜 yyyy-MM-dd
function todayString() {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
  }).format(new Date());
}

// 홈이 지금 보여줘야 하는 날짜 (getHome과 같은 기준: VITE_HOME_DATE가 있으면 그 날짜)
export function currentHomeDate() {
  return HOME_DATE ?? todayString();
}

/**
 * 홈 화면 데이터 조회
 * 사용자 정보 + 안 읽은 알림 + 오늘 복용 목록
 */
export async function getHome() {
  try {
    const date = HOME_DATE ?? todayString();

    const [userRes, unreadRes, supplementsRes] = await Promise.all([
      api.get("/api/v1/users/me"),

      api.get("/api/v1/notifications/unread-count"),

      api.get("/api/v1/intakes/today", {
        params: { date },
      }),
    ]);

    // API 응답 구조 통일
    const user = unwrapResponse(userRes);
    const unread = unwrapResponse(unreadRes);
    const supplements = unwrapResponse(supplementsRes);

    console.log("[home] user:", user);
    console.log("[home] unread:", unread);
    console.log("[home] supplements:", supplements);

    if (!Array.isArray(supplements)) {
      throw new Error("오늘 복용 목록 응답이 배열이 아닙니다.");
    }

    const data = {
      userName: user?.nickname ?? "사용자",
      today: date,

      hasUnreadNotifications: (unread?.unreadCount ?? 0) > 0,

      supplements: supplements.map((s, i) => ({
        ...s,
        color: s.color ?? COLORS[i % COLORS.length],
      })),
    };

    return {
      data,
      isSample: false,
    };
  } catch (e) {
    console.error("[home] 홈 데이터 호출 실패:", e);

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
export async function updateIntake(scheduleId, taken, date) {
  const res = await api.post(`/api/v1/intakes`, { scheduleId, taken, date });
  return res.data;
}

/**
 * 맞춤 영양제 추천
 * 영준(AI) 담당 API와 추후 연동
 */
export async function getRecommendation(answers) {
  try {
    const res = await api.post(`/api/home/recommend`, answers);
    return res.data;
  } catch (e) {
    console.warn("[home] 추천 API 호출 실패 → 예시 추천 사용", e);

    return SAMPLE_RECOMMEND;
  }
}

/**
 * 홈 복용 알림 카드 조회 - develop 신규 기능
 * GET /api/v1/notifications/intake-summary
 * 다음 복용 알림 + 이번 주 제시간 복용률
 * JWT의 사용자 ID로 조회하므로 X-User-Id 헤더 불필요
 */
export async function getIntakeSummary() {
  const res = await api.get(`/api/v1/notifications/intake-summary`);
  return res.data?.data;
}

/** GET /api/v1/intakes/stats : 연속 복용 일수 (홈 "N일 연속 복용 중" 배지) */
export async function getStreakDays() {
  const res = await api.get(`/api/v1/intakes/stats`);
  return res.data?.data?.streakDays ?? 0;
}

/** 오늘(KST) 날짜 "yyyy-MM-dd" — 홈이 오늘을 보여주는지 확인할 때 사용 */
export function kstToday() {
  return todayString();
}

// Axios 응답 또는 인터셉터에서 반환한 응답을 동일한 형태로 변환
function unwrapResponse(res) {
  const body = res?.data ?? res;
  return body?.data ?? body;
}
