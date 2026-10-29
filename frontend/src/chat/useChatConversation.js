// V4 AI 대화창 상태 (담당: 영준) — 말풍선 목록, 대화 세션, 트랙 2 폴링
// 챗봇 말은 전부 아래 고정 문구 또는 서버 응답 필드. LLM이 쓴 문장은 되묻기 질문뿐 (서버가 검증)
// 대화 기록은 로그인한 동안 유지 (sessionStorage + 화면 상태, 설계 P14). localStorage는 쓰지 않는다 (건강 정보)
// 사용자(userId)가 바뀌면 다른 사용자의 기록은 지운다 (로그인 기능이 생기면 로그아웃 때도 clearChatStorage 호출)
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
  error: "잠시 후 다시 시도해 주세요.",
  pendingFailed: "추천을 만들지 못했어요. 잠시 후 다시 시도해 주세요.",
  pendingTimeout: "추천을 만드는 데 시간이 너무 오래 걸려요. 잠시 후 다시 시도해 주세요.",
  retry: "다시 시도",
};
export const EXAMPLES = ["피곤하고 잠을 잘 못 자요", "뼈 건강이 걱정돼요", "소화가 잘 안 돼요"];

const EMPTY = { messages: [], sessionId: null, turns: 0, pregnancy: null, phase: "init", pending: null };

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

  useEffect(() => {
    if (!userId || state.phase === "init") return;
    try {
      sessionStorage.setItem(storageKey(userId), JSON.stringify(state));
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

  // 세션 시작. greet = 인사 말풍선을 붙일지 (첫 대화)
  const begin = useCallback(
    async (greet) => {
      try {
        const res = await startChatSession(userId);
        if (!aliveRef.current) return null;
        const q = res.pregnancyQuestion;
        if (q && stateRef.current.pregnancy == null) {
          update((s) => ({
            sessionId: res.chatSessionId,
            turns: 0,
            phase: "pregnancy",
            messages: [...s.messages, bot("chips", { text: q.question, chips: q.options, chipType: "pregnancy" })],
          }));
        } else {
          update((s) => ({
            sessionId: res.chatSessionId,
            turns: 0,
            phase: "ready",
            messages: greet
              ? [...s.messages, bot("chips", { text: TEXT.greeting, chips: EXAMPLES.map((e) => ({ label: e })), chipType: "example" })]
              : s.messages,
          }));
        }
        return res.chatSessionId;
      } catch (e) {
        if (!aliveRef.current) return null;
        update((s) => ({
          phase: "retry",
          messages: [...s.messages, bot("chips", { text: e.message || TEXT.error, chips: [{ label: TEXT.retry }], chipType: "retry" })],
        }));
        return null;
      }
    },
    [userId, update],
  );

  // 패널을 처음 열 때
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
      begin(true);
    }
  }, [userId, begin, ingredients]);

  const poll = useCallback(
    (recommendationId, message, count = 0) => {
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
          poll(recommendationId, message, count + 1);
        }
      }, POLL_INTERVAL_MS);
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
          update((s) => ({
            messages: [...s.messages, bot("chips", { text: res.question, chips: res.options.map((o) => ({ label: o })), chipType: "answer" })],
          }));
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
          push(bot("text", { text: res.notice || TEXT.error }));
      }
    },
    [update, push, poll],
  );

  const send = useCallback(
    async (raw) => {
      const text = raw.trim();
      const s = stateRef.current;
      if (!text || sending || !userId || s.pending || s.phase === "blocked" || s.phase === "pregnancy") return;
      push(user(text));
      setSending(true);
      try {
        let sid = s.sessionId;
        let turns = s.turns;
        if (!sid) {
          // 한 고민 = 한 세션: 결과가 나온 뒤 다시 입력하면 새 세션 (임신·수유 답은 처음 답한 값을 다시 쓴다)
          sid = await begin(false);
          turns = 0;
          if (!sid) return;
        }
        const res = await sendChatMessage(userId, sid, text, turns === 0 ? stateRef.current.pregnancy : null);
        if (!aliveRef.current) return;
        update(() => ({ turns: turns + 1 }));
        handle(res);
      } catch (e) {
        if (!aliveRef.current) return;
        push(bot("text", { text: e.message || TEXT.error }));
      } finally {
        if (aliveRef.current) setSending(false);
      }
    },
    [sending, userId, push, begin, update, handle],
  );

  // 말풍선의 선택지(칩) 누름
  const choose = useCallback(
    (msg, chip) => {
      if (msg.chipType === "pregnancy") {
        update((st) => ({
          pregnancy: chip.code,
          phase: "ready",
          messages: [
            ...st.messages,
            user(chip.label),
            bot("chips", { text: TEXT.greeting, chips: EXAMPLES.map((e) => ({ label: e })), chipType: "example" }),
          ],
        }));
      } else if (msg.chipType === "retry") {
        update(() => ({ phase: "init" }));
        begin(stateRef.current.messages.every((m) => m.chipType !== "example"));
      } else {
        send(chip.label);
      }
    },
    [update, begin, send],
  );

  const s = state;
  return {
    messages: s.messages,
    sending,
    pending: Boolean(s.pending),
    inputDisabled: !userId || s.phase === "blocked" || s.phase === "pregnancy" || s.phase === "retry" || Boolean(s.pending),
    ingredients,
    open,
    send,
    choose,
  };
}
