import { api } from "./client";

// 회원이면 X-User-Id 헤더를 붙이고, 비회원(userId 없음)이면 헤더를 빼서 보낸다
function userHeader(userId) {
  return userId ? { headers: { "X-User-Id": userId } } : {};
}

// 진행 중인 설문 조회 (문항 + 회원이면 prefill, needsConfirm)
export function getActiveSurvey(userId) {
  return api.get("/api/v1/surveys/active", userHeader(userId));
}

// 민감정보 수집 동의 (회원만)
export function agreeSensitiveConsent(userId) {
  return api.post("/api/v1/users/me/sensitive-consent", null, userHeader(userId));
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
export function submitSurvey(userId, surveyId, answers) {
  return api.post(
    `/api/v1/surveys/${surveyId}/responses`,
    { answers },
    userHeader(userId),
  );
}

// 추천 생성 (sync 모드면 DONE, queue 모드면 PENDING으로 온다)
export function createRecommendation(userId, responseId) {
  return api.post("/api/v1/recommendations", { responseId }, userHeader(userId));
}

// 추천 조회 (queue 모드 폴링용)
export function getRecommendation(userId, recommendationId) {
  return api.get(
    `/api/v1/recommendations/${recommendationId}`,
    userHeader(userId),
  );
}

// ---------- V2 회원 맞춤 질문 (회원 전용) ----------

// 회원 추천 방식 → { mode: "V1" | "V2", missing }
export function getRecommendationMode(userId) {
  return api.get("/api/v1/users/me/recommendation-mode", userHeader(userId));
}

// 맞춤 질문 회차 시작 → { sessionId, status, question, progress, canStop }
export function startPersonalizedSession(userId) {
  return api.post("/api/v1/personalized-sessions", null, userHeader(userId));
}

// 질문에 답하기 → 다음 질문 또는 status READY
export function answerPersonalizedQuestion(userId, sessionId, turnId, selected) {
  return api.post(
    `/api/v1/personalized-sessions/${sessionId}/answers`,
    { turnId, selected },
    userHeader(userId),
  );
}

// 여기까지 답하고 결과 보기 → status READY (조건 미충족이면 PQ003)
export function stopPersonalizedSession(userId, sessionId) {
  return api.post(
    `/api/v1/personalized-sessions/${sessionId}/stop`,
    null,
    userHeader(userId),
  );
}

// ✕ 닫기 → status ABANDONED (추천 없음)
export function closePersonalizedSession(userId, sessionId) {
  return api.post(
    `/api/v1/personalized-sessions/${sessionId}/close`,
    null,
    userHeader(userId),
  );
}

// 정밀 추천 (V1 추천과 같은 결과 형식)
export function createPersonalizedRecommendation(userId, sessionId) {
  return api.post(
    `/api/v1/personalized-sessions/${sessionId}/recommendation`,
    null,
    userHeader(userId),
  );
}

// ---------- V3 약사 리뷰 기반 제품 추천 ----------

// 성분별 [제품 추천 보기] 버튼 노출 여부 → { MAGNESIUM: true, VITAMIN_D: false }
export function getProductAvailability(recommendationId) {
  return api.get(`/api/v1/recommendations/${recommendationId}/products/availability`);
}

// 성분 카드의 추천 제품 (최대 3개) → { ingredient, products, excludedCount, notice, message }
export function getRecommendedProducts(recommendationId, ingredient) {
  return api.get(`/api/v1/recommendations/${recommendationId}/products`, {
    params: { ingredient },
  });
}
