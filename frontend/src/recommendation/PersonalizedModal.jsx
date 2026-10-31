// 회원 맞춤 질문 모달 (담당: 영준) — 모드 확인 → 맞춤 질문 → (여기까지 답하고 결과 보기) → 정밀 추천 → 결과 흐름.
// ✕ 닫기는 추천 없이 회차를 끝낸다
import {
  answerPersonalizedQuestion,
  closePersonalizedSession,
  createPersonalizedRecommendation,
  getIngredients,
  getRecommendationMode,
  startPersonalizedSession,
  stopPersonalizedSession,
} from "../api/recommendationApi";
import QuestionStep from "./QuestionStep";
import ResultStep from "./ResultStep";
import "./recommendation.css";
import { useCallback, useEffect, useRef, useState } from "react";

const CHECK_LOADING_TEXT = "건강정보를 확인하고 있어요";
const AI_LOADING_TEXT = "AI가 맞춤 추천을 만들고 있어요";
// 질문 생성 대기 문구 (정확한 시간은 모르므로 순서대로 바꾸고 마지막 문구에서 멈춘다)
const WAITING_TEXTS = [
  "답변을 살펴보고 있어요",
  "다음 질문을 고르고 있어요",
  "거의 다 만들었어요",
];
// 첫 질문 대기 문구 (서버가 질문을 미리 만드는 설정이면 10초 넘게 걸릴 수 있다)
const START_WAITING_TEXTS = [
  "건강정보를 살펴보고 있어요",
  "나에게 맞는 질문을 고르고 있어요",
  "질문을 준비하고 있어요",
];
const WAITING_INTERVAL_MS = 1700;
const START_WAITING_INTERVAL_MS = 3000;

/**
 * props (index.js의 RecommendationEntry에서 전달)
 * - userId: 로그인 사용자 id (회원만 이 모달을 쓴다)
 * - onFallbackToV1: 건강정보가 부족할 때 기본 설문(V1 회원 모드)으로 바꾸기
 * - onClose: 모달 닫기
 */
export default function PersonalizedModal({ userId, onFallbackToV1, onClose }) {
  const [step, setStep] = useState("loading"); // "loading" | "waiting" | "question" | "result" | "error"
  const [loadingText, setLoadingText] = useState(CHECK_LOADING_TEXT); // 로딩 화면 문구
  const [waitingIndex, setWaitingIndex] = useState(0); // 질문 생성 대기 문구 위치
  const [firstWait, setFirstWait] = useState(true); // 첫 질문 대기인지 (문구가 다르다)
  const [session, setSession] = useState(null); // 회차 응답 { sessionId, status, question, progress, canStop }
  const [selected, setSelected] = useState([]); // 지금 질문에서 고른 선택지 index
  const [ingredients, setIngredients] = useState({}); // 성분 코드 → { name, unit }
  const [result, setResult] = useState(null); // 정밀 추천 결과
  const [error, setError] = useState(null); // { message, retrySessionId }
  const [sending, setSending] = useState(false); // 요청 중 여부 (중복 클릭 방지)
  const aliveRef = useRef(true); // 닫힌 뒤에는 화면을 바꾸지 않기 위한 표시
  const openSessionRef = useRef(null); // 아직 결과가 나오지 않은 회차 id (닫을 때 서버에 알림)

  // ✕·ESC·바깥 클릭: 결과 전이면 회차를 닫고(추천 없음) 모달을 닫는다. 서버 응답은 기다리지 않는다
  const handleClose = useCallback(() => {
    const sessionId = openSessionRef.current;
    openSessionRef.current = null;
    if (sessionId) {
      closePersonalizedSession(userId, sessionId).catch(() => {});
    }
    onClose();
  }, [userId, onClose]);

  // ESC로 닫기
  useEffect(() => {
    const onKeyDown = (e) => e.key === "Escape" && handleClose();
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [handleClose]);

  useEffect(() => {
    aliveRef.current = true;
    return () => {
      aliveRef.current = false;
    };
  }, []);

  // 질문을 만드는 동안 대기 문구를 바꾼다
  useEffect(() => {
    if (step !== "waiting") return undefined;
    const texts = firstWait ? START_WAITING_TEXTS : WAITING_TEXTS;
    const timer = setInterval(() => {
      setWaitingIndex((i) => Math.min(i + 1, texts.length - 1));
    }, firstWait ? START_WAITING_INTERVAL_MS : WAITING_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [step, firstWait]);

  // 질문 생성 대기 화면 (문구는 처음부터)
  const wait = () => {
    setWaitingIndex(0);
    setFirstWait(false);
    setStep("waiting");
  };

  // retrySessionId: 정밀 추천만 실패했으면 그 회차로 "다시 시도"
  const fail = useCallback((message, retrySessionId) => {
    if (!aliveRef.current) return;
    setError({ message, retrySessionId });
    setStep("error");
  }, []);

  // 4) 정밀 추천 (실패해도 회차는 READY라 "다시 시도"로 다시 부를 수 있다)
  const recommend = useCallback(
    async (sessionId) => {
      setStep("loading");
      setLoadingText(AI_LOADING_TEXT);
      try {
        const data = await createPersonalizedRecommendation(userId, sessionId);
        if (!aliveRef.current) return;
        openSessionRef.current = null;
        setResult(data);
        setStep("result");
      } catch (e) {
        fail(e.message, sessionId);
      }
    },
    [userId, fail],
  );

  // 회차 응답 반영: 다음 질문이 있으면 질문 화면, 끝났으면(READY) 정밀 추천
  const applySession = useCallback(
    (data) => {
      if (!aliveRef.current) return;
      openSessionRef.current = data.sessionId;
      if (data.status === "READY") {
        recommend(data.sessionId);
        return;
      }
      setSession(data);
      setSelected([]);
      setStep("question");
    },
    [recommend],
  );

  // 1) 회차 시작. 실패하면 모드를 다시 확인해서 건강정보가 부족해졌으면 기본 설문으로
  const start = useCallback(async () => {
    setWaitingIndex(0);
    setFirstWait(true);
    setStep("waiting");
    setResult(null);
    setError(null);
    try {
      applySession(await startPersonalizedSession(userId));
    } catch (e) {
      try {
        const mode = await getRecommendationMode(userId);
        if (mode.mode !== "V2") {
          onFallbackToV1();
          return;
        }
      } catch {
        // 모드 확인도 실패하면 원래 오류를 보여준다
      }
      fail(e.message, null);
    }
  }, [userId, applySession, onFallbackToV1, fail]);

  // 화면이 처음 열릴 때: 성분 목록 + 모드 확인 → V2면 시작, 아니면 기본 설문
  useEffect(() => {
    getIngredients()
      .then((list) => {
        if (aliveRef.current) {
          setIngredients(Object.fromEntries(list.map((i) => [i.code, i])));
        }
      })
      .catch(() => {});
    getRecommendationMode(userId)
      .then((mode) => {
        if (!aliveRef.current) return;
        if (mode.mode === "V2") start();
        else onFallbackToV1();
      })
      .catch((e) => fail(e.message, null));
  }, [userId, start, onFallbackToV1, fail]);

  // 2) 답 보내기 → 다음 질문을 만드는 동안 대기 화면
  const handleAnswer = async () => {
    if (sending || !session?.question || selected.length === 0) return;
    setSending(true);
    wait();
    try {
      applySession(
        await answerPersonalizedQuestion(
          userId,
          session.sessionId,
          session.question.turnId,
          selected,
        ),
      );
    } catch (e) {
      fail(e.message, null);
    } finally {
      setSending(false);
    }
  };

  // 3) 여기까지 답하고 결과 보기 → 지금까지 답으로 정밀 추천 (서버가 허용할 때만: canStop)
  const handleStop = async () => {
    if (sending || !session?.canStop) return;
    setSending(true);
    try {
      applySession(await stopPersonalizedSession(userId, session.sessionId));
    } catch (e) {
      fail(e.message, null);
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="rec-backdrop" role="presentation" onMouseDown={handleClose}>
      <div
        className="rec-modal"
        role="dialog"
        aria-modal="true"
        aria-label="맞춤 질문 추천"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="rec-header">
          <div>
            <span className="rec-eyebrow">맞춤 영양 추천</span>
            <h2>맞춤 질문</h2>
          </div>
          <button
            type="button"
            className="rec-close"
            aria-label="닫기"
            onClick={handleClose}
          >
            ×
          </button>
        </div>

        {step === "loading" && (
          <div className="rec-loading">
            <span className="rec-spinner" aria-hidden="true" />
            <strong>{loadingText}</strong>
            {loadingText === AI_LOADING_TEXT && (
              <p>수 초에서 수십 초까지 걸릴 수 있어요. 창을 닫지 말고 기다려 주세요.</p>
            )}
          </div>
        )}

        {step === "waiting" && (
          <div className="rec-loading">
            <span className="rec-spinner" aria-hidden="true" />
            <strong key={`${firstWait}-${waitingIndex}`} className="rec-waiting-text">
              {(firstWait ? START_WAITING_TEXTS : WAITING_TEXTS)[waitingIndex]}
            </strong>
            <p>
              {firstWait
                ? "저장된 건강정보로 맞춤 질문을 준비하고 있어요. 10초 정도 걸릴 수 있어요."
                : "저장된 건강정보와 답변을 바탕으로 질문을 만들고 있어요."}
            </p>
          </div>
        )}

        {step === "question" && session?.question && (
          <QuestionStep
            question={session.question}
            progress={session.progress}
            selected={selected}
            sending={sending}
            onChange={setSelected}
            canStop={session.canStop}
            onSubmit={handleAnswer}
            onStop={handleStop}
          />
        )}

        {step === "result" && result && (
          <ResultStep
            result={result}
            ingredients={ingredients}
            basisText="건강 프로필과 맞춤 질문 답을 기준으로 확인했어요."
            restartLabel="맞춤 질문 다시 하기"
            onRestart={start}
            onClose={onClose}
          />
        )}

        {step === "error" && error && (
          <div className="rec-error">
            <strong>문제가 생겼어요</strong>
            <p>{error.message}</p>
            <div className="rec-actions">
              {error.retrySessionId && (
                <button
                  type="button"
                  className="rec-button primary"
                  onClick={() => recommend(error.retrySessionId)}
                >
                  다시 시도
                </button>
              )}
              <button type="button" className="rec-button" onClick={start}>
                처음부터
              </button>
              <button type="button" className="rec-button" onClick={handleClose}>
                닫기
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
