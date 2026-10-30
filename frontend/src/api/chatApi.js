import axios from "axios";

// V4 AI 대화창 (회원 전용). 공통 api(client.js)는 오류 상태 코드를 버리므로, 대화창은 같은 설정의 별도 인스턴스로
// 상태 코드·서버 메시지를 남긴다 (화면에 axios 원문 메시지를 보이지 않기 위함). 공통 client.js는 그대로
const chatClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL || "",
  headers: { "Content-Type": "application/json" },
});

/** 대화창 API 오류. status = HTTP 상태 (네트워크 오류면 null), serverMessage = 서버 ErrorCode 메시지 */
export class ChatApiError extends Error {
  constructor(status, serverMessage) {
    super(serverMessage || "chat api error");
    this.status = status;
    this.serverMessage = serverMessage;
  }
}

chatClient.interceptors.response.use(
  (response) => {
    const result = response.data;
    if (result && result.success === false) {
      throw new ChatApiError(response.status, result.message);
    }
    return result?.data ?? result;
  },
  (error) => {
    const data = error.response?.data;
    const serverMessage = data && typeof data === "object" ? data.message : null;
    return Promise.reject(new ChatApiError(error.response?.status ?? null, serverMessage));
  },
);

// 회원이면 X-User-Id 헤더 (recommendationApi.js와 같은 방식)
function userHeader(userId) {
  return userId ? { headers: { "X-User-Id": userId } } : {};
}

// 대화 시작 → { chatSessionId, pregnancyQuestion } (19~50세 여성만 pregnancyQuestion)
export function startChatSession(userId) {
  return chatClient.post("/api/v1/chat/sessions", null, userHeader(userId));
}

// 입력 보내기 → { type: CLARIFY | RED_FLAG | OFF_TOPIC | RESULT | PENDING, ... }
// pregnancy는 세션의 첫 메시지에서만 (임신·수유 질문 답 code)
export function sendChatMessage(userId, chatSessionId, text, pregnancy) {
  return chatClient.post(
    `/api/v1/chat/sessions/${chatSessionId}/messages`,
    pregnancy ? { text, pregnancy } : { text },
    userHeader(userId),
  );
}

// 결과 다시 보기 → { chatSessionId, status, result }
export function getChatSession(userId, chatSessionId) {
  return chatClient.get(`/api/v1/chat/sessions/${chatSessionId}`, userHeader(userId));
}
