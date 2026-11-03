import { api } from "./client";
import { getAccessToken } from "../auth/authToken";

// 비회원 토큰 헤더 (sessionStorage에 저장된 guestToken이 있으면 첨부)
// 로그인 상태(access token 있음)면 guestToken을 보내지 않는다
function guestTokenHeader() {
  if (getAccessToken()) return {};
  const token = sessionStorage.getItem("guestToken");
  return token ? { "X-Guest-Token": token } : {};
}

// 진행 중인 설문 조회 (문항 + 회원이면 prefill, needsConfirm)
export function getActiveSurvey() {
  return api.get("/api/v1/surveys/active");
}

// 민감정보 수집 동의 (회원만)
export function agreeSensitiveConsent() {
  return api.post("/api/v1/users/me/sensitive-consent");
}

// 복용 약 검색
export function searchDrugs(keyword) {
  return api.get("/api/v1/drugs", { params: { keyword } });
}

// 성분 목록 (코드 → 이름·단위)
export function getIngredients() {
  return api.get("/api/v1/ingredients");
}

// 설문 제출 → { responseId }
export async function submitSurvey(surveyId, answers) {
  const result = await api.post(
    `/api/v1/surveys/${surveyId}/responses`,
    { answers },
  );
  if (!getAccessToken() && result.guestToken && result.responseId != null) {
    sessionStorage.setItem("guestSurveyResponseId", String(result.responseId));
    sessionStorage.setItem("guestToken", result.guestToken);
  }
  return result;
}

// 추천 생성 (sync 모드면 DONE, queue 모드면 PENDING으로 온다)
export function createRecommendation(responseId) {
  return api.post("/api/v1/recommendations", { responseId }, { headers: guestTokenHeader() });
}

// 추천 조회 (queue 모드 폴링용)
export function getRecommendation(recommendationId) {
  return api.get(`/api/v1/recommendations/${recommendationId}`, { headers: guestTokenHeader() });
}

// ---------- V2 회원 맞춤 질문 (회원 전용) ----------

// 회원 추천 방식 → { mode: "V1" | "V2", missing }
export function getRecommendationMode() {
  return api.get("/api/v1/users/me/recommendation-mode");
}

// 맞춤 질문 회차 시작 → { sessionId, status, question, progress, canStop }
export function startPersonalizedSession() {
  return api.post("/api/v1/personalized-sessions");
}

// 질문에 답하기 → 다음 질문 또는 status READY
export function answerPersonalizedQuestion(sessionId, turnId, selected) {
  return api.post(
    `/api/v1/personalized-sessions/${sessionId}/answers`,
    { turnId, selected },
  );
}

// 여기까지 답하고 결과 보기 → status READY (조건 미충족이면 PQ003)
export function stopPersonalizedSession(sessionId) {
  return api.post(
    `/api/v1/personalized-sessions/${sessionId}/stop`,
  );
}

// ✕ 닫기 → status ABANDONED (추천 없음)
export function closePersonalizedSession(sessionId) {
  return api.post(
    `/api/v1/personalized-sessions/${sessionId}/close`,
  );
}

// 정밀 추천 (V1 추천과 같은 결과 형식)
export function createPersonalizedRecommendation(sessionId) {
  return api.post(
    `/api/v1/personalized-sessions/${sessionId}/recommendation`,
  );
}

// ---------- V3 약사 리뷰 기반 제품 추천 ----------

// 성분별 [제품 추천 보기] 버튼 노출 여부 → { MAGNESIUM: true, VITAMIN_D: false }
export function getProductAvailability(recommendationId) {
  return api.get(`/api/v1/recommendations/${recommendationId}/products/availability`, {
    headers: guestTokenHeader(),
  });
}

// 성분 카드의 추천 제품 (최대 3개) → { ingredient, products, excludedCount, notice, message }
export function getRecommendedProducts(recommendationId, ingredient) {
  return api.get(`/api/v1/recommendations/${recommendationId}/products`, {
    params: { ingredient },
    headers: guestTokenHeader(),
  });
}

// ---------- V7 마이페이지 최근 추천 ----------

// 가장 최근 추천 1건 (회원 전용) → { exists, source, recommendedAt, recommendationId, chatSessionId, notice, ingredients }
export function getRecentRecommendation() {
  return api.get("/api/v1/users/me/recommendations/recent");
}
