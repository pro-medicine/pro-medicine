import { api } from "./client";

// V4 AI 대화창 (회원 전용). 회원이면 X-User-Id 헤더 (recommendationApi.js와 같은 방식)
function userHeader(userId) {
  return userId ? { headers: { "X-User-Id": userId } } : {};
}

// 대화 시작 → { chatSessionId, pregnancyQuestion } (19~50세 여성만 pregnancyQuestion)
export function startChatSession(userId) {
  return api.post("/api/v1/chat/sessions", null, userHeader(userId));
}

// 입력 보내기 → { type: CLARIFY | RED_FLAG | OFF_TOPIC | RESULT | PENDING, ... }
// pregnancy는 세션의 첫 메시지에서만 (임신·수유 질문 답 code)
export function sendChatMessage(userId, chatSessionId, text, pregnancy) {
  return api.post(
    `/api/v1/chat/sessions/${chatSessionId}/messages`,
    pregnancy ? { text, pregnancy } : { text },
    userHeader(userId),
  );
}

// 결과 다시 보기 → { chatSessionId, status, result }
export function getChatSession(userId, chatSessionId) {
  return api.get(`/api/v1/chat/sessions/${chatSessionId}`, userHeader(userId));
}
