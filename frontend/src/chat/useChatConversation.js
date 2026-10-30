// V4 AI 대화창 상태 (담당: 영준) — 말풍선 목록, 대화 세션, 트랙 2 폴링
// 챗봇 말은 전부 아래 고정 문구 또는 서버 응답 필드. LLM이 쓴 문장은 되묻기 질문뿐 (서버가 검증)
// 대화 기록은 로그인한 동안 유지 (sessionStorage + 화면 상태, 설계 P14). localStorage는 쓰지 않는다 (건강 정보)
// 사용자(userId)가 바뀌면 다른 사용자의 기록은 지운다 (로그인 기능이 생기면 로그아웃 때도 clearChatStorage 호출)
// 세션은 첫 메시지를 보낼 때 만든다 (인사말은 API와 상관없이 바로). 오류 말풍선은 항상 1개, 저장하지 않는다
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";
import { getIngredients, getRecommendation } from "../api/recommendationApi";
import { sendChatMessage, startChatSession } from "../api/chatApi";

const STORAGE_PREFIX = "yakssok.chat.v1.";
const POLL_INTERVAL_MS = 1000; // 기존 추천 조회 폴링과 같은 간격 (RecommendationModal)
const MAX_POLLS = 90; // 1초 × 90번 ≈ 90초

export const TEXT = {
  greeting: "안녕하세요! 요즘 불편한 점이 있으신가요? 편하게 말씀해 주세요.",
  more: "다른 고민도 있으신가요?",
  login: "로그인 후 이용할 수 있어요",
  searching: "말씀하신 내용으로 성분을 찾고 있어요",
  connection: "지금 연결이 원활하지 않아요. 잠시 후 다시 시도해 주세요",
  retrying: "다시 시도 중…",
  pendingFailed: "추천을 만들지 못했어요. 잠시 후 다시 시도해 주세요.",
  pendingTimeout: "추천을 만드는 데 시간이 너무 오래 걸려요. 잠시 후 다시 시도해 주세요.",
  retry: "다시 시도",
};
export const EXAMPLES = ["피곤하고 잠을 잘 못 자요", "뼈 건강이 걱정돼요", "소화가 잘 안 돼요"];

const EMPTY = { messages: [], sessionId: null, turns: 0, pregnancy: null, queued: null, phase: "init", pending: null };

// 화면에 보일 오류 문구: 네트워크 오류·5xx는 고정 문구, 4xx는 서버 ErrorCode 메시지 (axios 원문은 보이지 않는다)
export function friendlyError(e) {
  const status = e?.status ?? null;
  if (status != null && status >= 400 && status < 500 && e.serverMessage) return e.serverMessage;
  return TEXT.connection;
}

function storageKey(userId) {
  return `${STORAGE_PREFIX}${userId}`;
}

function load(userId) {
  if (!userId) return EMPTY;
  try {
    const raw = sessionStorage.getItem(storageKey(userId));
    return raw ? { ...EMPTY, ...JSON.parse(raw) } : EMPTY;
  } catch {
    return EMPTY;
  }
}

// 다른 사용자의 대화 기록 삭제 (except: 남길 userId)
export function clearChatStorage(except) {
  try {
    Object.keys(sessionStorage)
      .filter((k) => k.startsWith(STORAGE_PREFIX) && k !== (except ? storageKey(except) : null))
      .forEach((k) => sessionStorage.removeItem(k));
  } catch {
    // 저장소를 못 쓰면 화면 상태만 쓴다
  }
}

let seq = 0;
const nextId = () => `${Date.now()}-${(seq += 1)}`;

const bot = (kind, data = {}) => ({ id: nextId(), from: "bot", kind, ...data });
const user = (text) => ({ id: nextId(), from: "user", kind: "text", text });
const greeting = () => bot("chips", { text: TEXT.greeting, chips: EXAMPLES.map((e) => ({ label: e })), chipType: "example" });
const withoutError = (messages) => messages.filter((m) => m.kind !== "error");

// 트랙 2 결과(기존 추천 응답) → 대화창 결과 말풍선 형식
function fromRecommendation(rec, message) {
  return {
    track: "RULE_BASED",
    message,
    cards: [...(rec.items ?? [])].sort((a, b) => a.sortOrder - b.sortOrder),
    excludedItems: rec.excludedItems ?? [],
    recommendationId: rec.recommendationId,
  };
}

export function useChatConversation(userId) {
  const [state, setState] = useState(() => load(userId));
  const [sending, setSending] = useState(false);
  const [ingredients, setIngredients] = useState({}); // 성분 코드 → { name, unit }
  const [prevUserId, setPrevUserId] = useState(userId);
  const timerRef = useRef(null);
  const aliveRef = useRef(true);
  const stateRef = useRef(state); // 비동기 처리 중에 최신 상태를 읽기 위함
  useLayoutEffect(() => {
    stateRef.current = state;
  });

  // 사용자가 바뀌면 그 사용자의 기록으로 바꾼다 (렌더 중 상태 갱신: effect 안 setState 대신)
  if (prevUserId !== userId) {
    setPrevUserId(userId);
    setState(load(userId));
  }

  useEffect(() => {
    clearChatStorage(userId);
  }, [userId]);

  // 오류 말풍선은 저장하지 않는다 (새로고침 후 다시 보이면 안 됨)
  useEffect(() => {
    if (!userId || state.phase === "init") return;
    try {
      sessionStorage.setItem(storageKey(userId), JSON.stringify({ ...state, messages: withoutError(state.messages) }));
    } catch {
      // 저장소를 못 쓰면 화면 상태만 쓴다
    }
  }, [userId, state]);

  useEffect(() => {
    aliveRef.current = true;
    return () => {
      aliveRef.current = false;
      clearTimeout(timerRef.current);
    };
  }, []);

  const update = useCallback((fn) => setState((s) => ({ ...s, ...fn(s) })), []);
  const push = useCallback((...msgs) => update((s) => ({ messages: [...s.messages, ...msgs] })), [update]);

  // 오류 말풍선: 있으면 그 자리를 바꾸고 없으면 맨 아래에 1개 (retryText = 다시 시도할 때 보낼 문장)
  const showError = useCallback(
    (e, retryText) => {
      const text = friendlyError(e);
      update((s) => {
        const existing = s.messages.find((m) => m.kind === "error");
        const msg = { ...(existing ?? bot("error")), text, retrying: false, retryText };
        return existing
          ? { messages: s.messages.map((m) => (m.kind === "error" ? msg : m)) }
          : { messages: [...s.messages, msg] };
      });
    },
    [update],
  );

  // 패널을 열면 인사말을 바로 보여준다 (세션은 첫 메시지 때 만든다)
  const open = useCallback(() => {
    if (Object.keys(ingredients).length === 0) {
      getIngredients()
        .then((list) => aliveRef.current && setIngredients(Object.fromEntries((list ?? []).map((i) => [i.code, i]))))
        .catch(() => {});
    }
    if (!userId) {
      setState({ ...EMPTY, phase: "blocked", messages: [bot("text", { text: TEXT.login })] });
      return;
    }
    if (stateRef.current.messages.length === 0) {
      update(() => ({ phase: "ready", messages: [greeting()] }));
    }
  }, [userId, update, ingredients]);

  const poll = useCallback(
    (recommendationId, message) => {
      const tick = (count) => {
        timerRef.current = setTimeout(async () => {
          let rec;
          try {
            rec = await getRecommendation(userId, recommendationId);
          } catch {
            rec = null;
          }
          if (!aliveRef.current) return;
          if (rec?.status === "DONE") {
            update((s) => ({
              pending: null,
              sessionId: null,
              messages: [
                ...s.messages.filter((m) => m.kind !== "pending"),
                bot("result", { result: fromRecommendation(rec, message) }),
                bot("text", { text: TEXT.more }),
              ],
            }));
          } else if (rec?.status === "FAILED" || count + 1 >= MAX_POLLS) {
            update((s) => ({
              pending: null,
              sessionId: null,
              messages: [
                ...s.messages.filter((m) => m.kind !== "pending"),
                bot("text", { text: rec?.status === "FAILED" ? TEXT.pendingFailed : TEXT.pendingTimeout }),
              ],
            }));
          } else {
            tick(count + 1);
          }
        }, POLL_INTERVAL_MS);
      };
      tick(0);
    },
    [userId, update],
  );

  // 새로고침 후에도 트랙 2를 기다리는 중이면 이어서 조회
  useEffect(() => {
    const p = stateRef.current.pending;
    if (userId && p) poll(p.recommendationId, p.message);
    return () => clearTimeout(timerRef.current);
  }, [userId, poll]);

  const handle = useCallback(
    (res) => {
      switch (res.type) {
        case "CLARIFY":
          push(bot("chips", { text: res.question, chips: res.options.map((o) => ({ label: o })), chipType: "answer" }));
          break;
        case "RESULT":
          update((s) => ({
            sessionId: null,
            messages: [...s.messages, bot("text", { text: res.message }), bot("result", { result: res }), bot("text", { text: TEXT.more })],
          }));
          break;
        case "PENDING":
          update((s) => ({
            pending: { recommendationId: res.recommendationId, message: res.message },
            messages: [
              ...s.messages,
              ...(res.medicationNotice ? [bot("alert", { text: res.medicationNotice, tone: "warn" })] : []),
              bot("pending", { text: TEXT.searching }),
            ],
          }));
          poll(res.recommendationId, res.message);
          break;
        case "RED_FLAG":
          update((s) => ({ sessionId: null, messages: [...s.messages, bot("alert", { text: res.notice, tone: "danger" })] }));
          break;
        default: // OFF_TOPIC
          push(bot("text", { text: res.notice || TEXT.connection }));
      }
    },
    [update, push, poll],
  );

  // 서버로 보내기: 세션이 없으면 만들고(한 고민 = 한 세션), 19~50세 여성이면 임신·수유 질문을 먼저 묻는다.
  // 성공하면 오류 말풍선을 지운다. 실패하면 오류 말풍선 1개 (다시 시도 = 같은 문장으로 이 함수를 다시)
  const deliver = useCallback(
    async (text, answeredPregnancy) => {
      setSending(true);
      try {
        let { sessionId: sid, turns } = stateRef.current;
        if (!sid) {
          const started = await startChatSession(userId);
          if (!aliveRef.current) return;
          sid = started.chatSessionId;
          turns = 0;
          const q = started.pregnancyQuestion;
          if (q && stateRef.current.pregnancy == null && answeredPregnancy == null) {
            update((s) => ({
              sessionId: sid,
              turns: 0,
              phase: "pregnancy",
              queued: text,
              messages: [...withoutError(s.messages), bot("chips", { text: q.question, chips: q.options, chipType: "pregnancy" })],
            }));
            return;
          }
          update(() => ({ sessionId: sid, turns: 0 }));
        }
        const pregnancy = answeredPregnancy ?? stateRef.current.pregnancy;
        const res = await sendChatMessage(userId, sid, text, turns === 0 ? pregnancy : null);
        if (!aliveRef.current) return;
        update((s) => ({ turns: turns + 1, queued: null, messages: withoutError(s.messages) }));
        handle(res);
      } catch (e) {
        if (!aliveRef.current) return;
        // 이미 끝난 세션(CH003)이면 다음 입력은 새 세션으로
        if (e?.status === 409) update(() => ({ sessionId: null }));
        showError(e, text);
      } finally {
        if (aliveRef.current) setSending(false);
      }
    },
    [userId, update, handle, showError],
  );

  const send = useCallback(
    (raw) => {
      const text = raw.trim();
      const s = stateRef.current;
      if (!text || sending || !userId || s.pending || s.phase === "blocked" || s.phase === "pregnancy") return;
      update((st) => ({ messages: [...withoutError(st.messages), user(text)] }));
      deliver(text);
    },
    [sending, userId, update, deliver],
  );

  // 오류 말풍선의 [다시 시도]: 새 말풍선을 쌓지 않고 그 말풍선을 "다시 시도 중…" → 결과로 바꾼다
  const retry = useCallback(
    (msg) => {
      if (sending) return;
      update((s) => ({ messages: s.messages.map((m) => (m.id === msg.id ? { ...m, text: TEXT.retrying, retrying: true } : m)) }));
      deliver(msg.retryText);
    },
    [sending, update, deliver],
  );

  // 말풍선의 선택지(칩) 누름
  const choose = useCallback(
    (msg, chip) => {
      if (msg.chipType === "pregnancy") {
        const queued = stateRef.current.queued;
        update((st) => ({ pregnancy: chip.code, phase: "ready", messages: [...st.messages, user(chip.label)] }));
        // 임신·수유 답을 받으면 처음 입력한 문장을 이어서 보낸다
        if (queued) deliver(queued, chip.code);
      } else {
        send(chip.label);
      }
    },
    [update, deliver, send],
  );

  const s = state;
  return {
    messages: s.messages,
    sending,
    pending: Boolean(s.pending),
    inputDisabled: !userId || s.phase === "blocked" || s.phase === "pregnancy" || Boolean(s.pending),
    ingredients,
    open,
    send,
    choose,
    retry,
  };
}
