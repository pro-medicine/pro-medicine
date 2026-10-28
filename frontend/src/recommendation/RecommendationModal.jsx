// 추천 설문 모달 (담당: 영준) — 설문 → (민감정보 동의) → 제출 → 추천 생성 → 결과 흐름
import {
  agreeSensitiveConsent,
  createRecommendation,
  getActiveSurvey,
  getIngredients,
  getRecommendation,
  getRecommendationMode,
  submitSurvey,
} from "../api/recommendationApi";
import ConsentStep from "./ConsentStep";
import SurveyStep from "./SurveyStep";
import ResultStep from "./ResultStep";
import "./recommendation.css";
import { useCallback, useEffect, useRef, useState } from "react";

const POLL_INTERVAL_MS = 1000; // queue 모드 조회 간격
const MAX_POLLS = 90; // 1초 × 90번 ≈ 90초가 지나도 끝나지 않으면 에러 화면
const SURVEY_LOADING_TEXT = "설문을 불러오고 있어요";
const AI_LOADING_TEXT = "AI가 추천을 만들고 있어요";

// 설문과 성분 목록을 함께 받는다. 성분 이름을 못 받아도 코드로 표시하고 진행
async function requestSurvey(userId) {
  const [data, ingredientList] = await Promise.all([
    getActiveSurvey(userId),
    getIngredients().catch(() => []),
  ]);
  return { data, ingredientList };
}

// prefill을 답변 초기값으로 바꾼다 (TEXT 문항은 JSON 문자열 → 목록)
function toInitialAnswers(questions) {
  const answers = {};
  questions.forEach((q) => {
    const p = q.prefill;
    if (!p) return;
    if (p.optionIds?.length) {
      answers[q.questionId] = { optionIds: p.optionIds };
    } else if (p.valueNumber != null) {
      answers[q.questionId] = { valueNumber: p.valueNumber };
    } else if (p.valueText) {
      answers[q.questionId] = { list: parseList(p.valueText) };
    }
  });
  return answers;
}

function parseList(valueText) {
  try {
    const parsed = JSON.parse(valueText);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

// TEXT 문항 목록 → 서버 JSON 형식 (화면용 필드는 빼고, 덜 입력한 행은 버린다)
function toSubmitList(code, list) {
  if (code === "Q-MEDICATIONS") {
    return list.map((i) =>
      i.drugId ? { drugId: i.drugId } : { name: i.name },
    );
  }
  if (code === "Q-SUPPLEMENTS") {
    return list
      .filter(
        (i) =>
          i.ingredient &&
          i.amount !== "" &&
          i.amount != null &&
          Number(i.amount) >= 0,
      )
      .map((i) => ({
        ingredient: i.ingredient,
        amount: Number(i.amount),
        unit: i.unit,
      }));
  }
  return list;
}

// 화면 답변 → 제출 형식. 답하지 않은 문항은 뺀다
function toSubmitAnswers(questions, answers) {
  const result = [];
  questions.forEach((q) => {
    const a = answers[q.questionId];
    if (!a) return;
    if (a.optionIds?.length) {
      result.push({ questionId: q.questionId, optionIds: a.optionIds });
    } else if (a.valueNumber != null && a.valueNumber !== "") {
      result.push({
        questionId: q.questionId,
        valueNumber: Number(a.valueNumber),
      });
    } else if (a.list) {
      const list = toSubmitList(q.code, a.list);
      // 저장돼 있던 목록을 모두 지운 경우에는 []를 보내야 서버에서도 지워진다
      if (list.length || q.prefill?.valueText) {
        result.push({
          questionId: q.questionId,
          valueText: JSON.stringify(list),
        });
      }
    }
  });
  return result;
}

/**
 * props (index.js의 RecommendationEntry에서 전달)
 * - userId: 로그인 사용자 id (없으면 비회원으로 설문)
 * - onClose: 모달 닫기
 * - onPersonalized: 회원 결과 화면에서 맞춤 질문(V2)으로 넘어가기 (없으면 안내를 띄우지 않음)
 */
export default function RecommendationModal({ userId, onClose, onPersonalized }) {
  const [step, setStep] = useState("loading"); // "consent" | "survey" | "loading" | "result" | "error"
  const [loadingText, setLoadingText] = useState(SURVEY_LOADING_TEXT); // 로딩 화면 문구
  const [survey, setSurvey] = useState(null); // GET /surveys/active 응답
  const [answers, setAnswers] = useState({}); // questionId → { optionIds } | { valueNumber } | { list }
  const [ingredients, setIngredients] = useState({}); // 성분 코드 → { name, unit }
  const [result, setResult] = useState(null); // 추천 결과
  const [error, setError] = useState(null); // { message, code }
  const [sending, setSending] = useState(false); // 동의·제출 중 여부 (중복 클릭 방지)
  const [canPersonalize, setCanPersonalize] = useState(false); // 회원 결과 뒤 맞춤 질문 안내 여부
  const timerRef = useRef(null); // 폴링 타이머
  const aliveRef = useRef(true); // 닫힌 뒤에는 화면을 바꾸지 않기 위한 표시

  // ESC로 닫기
  useEffect(() => {
    const onKeyDown = (e) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  // 닫힐 때 폴링 타이머 정리
  useEffect(() => {
    aliveRef.current = true;
    return () => {
      aliveRef.current = false;
      clearTimeout(timerRef.current);
    };
  }, []);

  const fail = (message, code) => {
    if (!aliveRef.current) return;
    clearTimeout(timerRef.current);
    setError({ message, code });
    setStep("error");
  };

  // 1) 받아온 설문을 화면에 반영 (afterConsent: 동의 직후라 동의 단계를 다시 띄우지 않음)
  const applySurvey = useCallback(({ data, ingredientList }, afterConsent) => {
    if (!aliveRef.current) return;
    setSurvey(data);
    setAnswers(toInitialAnswers(data.questions));
    setIngredients(Object.fromEntries(ingredientList.map((i) => [i.code, i])));
    // 회원인데 민감 문항이 없으면 아직 동의하지 않은 것
    const needsConsent =
      !afterConsent &&
      data.respondentType === "MEMBER" &&
      !data.questions.some((q) => q.sensitive);
    setStep(needsConsent ? "consent" : "survey");
  }, []);

  const applyLoadError = useCallback((e) => {
    if (!aliveRef.current) return;
    setError({ message: e.message });
    setStep("error");
  }, []);

  // 2) 화면이 처음 열릴 때 설문 + 성분 목록 불러오기
  useEffect(() => {
    requestSurvey(userId).then(applySurvey).catch(applyLoadError);
  }, [userId, applySurvey, applyLoadError]);

  // 동의 직후·다시 설문할 때 설문을 다시 불러온다
  const loadSurvey = async ({ afterConsent = false } = {}) => {
    clearTimeout(timerRef.current);
    setStep("loading");
    setLoadingText(SURVEY_LOADING_TEXT);
    setResult(null);
    setCanPersonalize(false);
    setError(null);
    try {
      applySurvey(await requestSurvey(userId), afterConsent);
    } catch (e) {
      applyLoadError(e);
    }
  };

  // 2) 민감정보 동의 → 민감 문항이 포함된 설문을 다시 받는다
  const handleAgree = async () => {
    if (sending) return;
    setSending(true);
    try {
      await agreeSensitiveConsent(userId);
      await loadSurvey({ afterConsent: true });
    } catch (e) {
      fail(e.message);
    } finally {
      setSending(false);
    }
  };

  // 3) 추천 상태에 따라 결과 / 에러 / 다시 조회 (pollCount: 지금까지 조회한 횟수)
  const handleRecommendation = (data, pollCount) => {
    if (!aliveRef.current) return;
    if (data.status === "DONE") {
      setResult(data);
      setStep("result");
    } else if (data.status === "FAILED") {
      fail("추천을 만들지 못했어요. 잠시 후 다시 시도해 주세요.", data.errorCode);
    } else if (pollCount >= MAX_POLLS) {
      fail("추천을 만드는 데 시간이 너무 오래 걸려요. 잠시 후 다시 시도해 주세요.");
    } else {
      timerRef.current = setTimeout(async () => {
        try {
          const next = await getRecommendation(userId, data.recommendationId);
          handleRecommendation(next, pollCount + 1);
        } catch (e) {
          fail(e.message);
        }
      }, POLL_INTERVAL_MS);
    }
  };

  // 4) 제출 → 추천 생성 (에러 화면의 "다시 시도"도 여기서부터)
  // skippedQuestionId: 마지막 문항을 건너뛰고 바로 제출할 때, 아직 state에 남아 있는 그 답을 뺀다
  const handleSubmit = async (skippedQuestionId) => {
    if (sending || !survey) return;
    setSending(true);
    setStep("loading");
    setLoadingText(AI_LOADING_TEXT);
    const submitAnswers = { ...answers };
    if (skippedQuestionId != null) delete submitAnswers[skippedQuestionId];
    try {
      const { responseId } = await submitSurvey(
        userId,
        survey.surveyId,
        toSubmitAnswers(survey.questions, submitAnswers),
      );
      const data = await createRecommendation(userId, responseId);
      handleRecommendation(data, 0);
    } catch (e) {
      fail(e.message);
    } finally {
      setSending(false);
    }
  };

  // 5) 회원 결과가 나오면 건강정보가 채워져 맞춤 질문(V2)이 가능한지 확인한다
  useEffect(() => {
    if (step !== "result" || !userId || !onPersonalized) return;
    getRecommendationMode(userId)
      .then((mode) => aliveRef.current && setCanPersonalize(mode.mode === "V2"))
      .catch(() => {});
  }, [step, userId, onPersonalized]);

  const changeAnswer = (questionId, value) => {
    setAnswers((current) => {
      const next = { ...current };
      if (value == null) delete next[questionId];
      else next[questionId] = value;
      return next;
    });
  };

  return (
    <div className="rec-backdrop" role="presentation" onMouseDown={onClose}>
      <div
        className="rec-modal"
        role="dialog"
        aria-modal="true"
        aria-label="맞춤 영양 추천"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="rec-header">
          <div>
            <span className="rec-eyebrow">AI RECOMMENDATION</span>
            <h2>맞춤 영양 추천</h2>
          </div>
          <button
            type="button"
            className="rec-close"
            aria-label="닫기"
            onClick={onClose}
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

        {step === "consent" && (
          <ConsentStep
            sending={sending}
            onAgree={handleAgree}
            onSkip={() => setStep("survey")}
          />
        )}

        {step === "survey" && survey && (
          <SurveyStep
            questions={survey.questions}
            needsConfirm={survey.needsConfirm}
            answers={answers}
            ingredients={ingredients}
            sending={sending}
            onChange={changeAnswer}
            onSubmit={handleSubmit}
          />
        )}

        {step === "result" && result && (
          <ResultStep
            result={result}
            ingredients={ingredients}
            onRestart={() => loadSurvey()}
            onClose={onClose}
          >
            {canPersonalize && (
              <div className="rec-pq-guide">
                <strong>맞춤 질문으로 더 정확하게 추천받아 보세요</strong>
                <p>저장된 건강정보를 바탕으로 몇 가지 질문을 더 드릴게요.</p>
                <button
                  type="button"
                  className="rec-button primary"
                  onClick={onPersonalized}
                >
                  맞춤 질문 시작
                </button>
              </div>
            )}
          </ResultStep>
        )}

        {step === "error" && error && (
          <div className="rec-error">
            <strong>문제가 생겼어요</strong>
            <p>{error.message}</p>
            {error.code && <small>오류 코드: {error.code}</small>}
            <div className="rec-actions">
              {survey && (
                <button
                  type="button"
                  className="rec-button primary"
                  disabled={sending}
                  onClick={() => handleSubmit()}
                >
                  다시 시도
                </button>
              )}
              <button
                type="button"
                className="rec-button"
                onClick={() => loadSurvey()}
              >
                처음부터
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
