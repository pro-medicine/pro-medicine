import { useState } from "react";
import {
  agreeSensitiveConsent,
  getActiveSurvey,
  getIngredients,
  getRecommendationMode,
  submitSurvey,
} from "../api/recommendationApi";
import ConsentStep from "./ConsentStep";
import SurveyStep from "./SurveyStep";
import "./recommendation.css";

const FOLLOWUP_CODES = new Set(["Q-GENDER", "Q-CONDITIONS", "Q-MEDICATIONS", "Q-SUPPLEMENTS"]);

export default function SignupHealthFollowup({ onClose, onReady, onSkip }) {
  const [step, setStep] = useState("consent");
  const [survey, setSurvey] = useState(null);
  const [answers, setAnswers] = useState({});
  const [ingredients, setIngredients] = useState({});
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");

  const agree = async () => {
    if (sending) return;
    setSending(true);
    setError("");
    try {
      await agreeSensitiveConsent();
      const [data, ingredientList] = await Promise.all([
        getActiveSurvey(),
        getIngredients().catch(() => []),
      ]);
      const questions = data.questions.filter((question) => FOLLOWUP_CODES.has(question.code));
      if (!questions.some((q) => q.code === "Q-CONDITIONS") ||
          !questions.some((q) => q.code === "Q-MEDICATIONS")) {
        throw new Error("추가 건강정보 문항을 불러오지 못했어요.");
      }
      setSurvey({ ...data, questions });
      setIngredients(Object.fromEntries(ingredientList.map((item) => [item.code, item])));
      setStep("survey");
    } catch (cause) {
      setError(cause.message || "건강정보를 불러오지 못했어요.");
    } finally {
      setSending(false);
    }
  };

  const submit = async (skippedQuestionId) => {
    if (sending || !survey) return;
    const submitted = survey.questions.flatMap((question, index) => {
      if (question.questionId === skippedQuestionId) return [];
      let answer = answers[question.questionId];
      if (!answer && index === survey.questions.length - 1 && question.answerType === "TEXT") {
        answer = { list: [] };
      }
      if (!answer) return [];
      if (answer.optionIds?.length) {
        return [{ questionId: question.questionId, optionIds: answer.optionIds }];
      }
      if (answer.list != null) {
        const list = question.code === "Q-MEDICATIONS"
          ? answer.list.filter((item) => item.drugId || item.name?.trim())
              .map((item) => item.drugId ? { drugId: item.drugId } : { name: item.name.trim() })
          : answer.list.filter((item) => item.ingredient && item.amount !== "" &&
              item.amount != null && Number(item.amount) >= 0)
              .map((item) => ({ ingredient: item.ingredient, amount: Number(item.amount), unit: item.unit }));
        return [{ questionId: question.questionId, valueText: JSON.stringify(list) }];
      }
      return [];
    });
    if (submitted.length === 0) {
      setError("추가 건강정보를 하나 이상 입력해 주세요.");
      return;
    }
    setSending(true);
    setError("");
    try {
      await submitSurvey(survey.surveyId, submitted);
      const mode = await getRecommendationMode();
      if (mode.mode === "V2") {
        onReady();
      } else {
        setStep("incomplete");
      }
    } catch (cause) {
      setError(cause.message || "건강정보를 저장하지 못했어요.");
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="rec-backdrop" role="presentation">
      <div className="rec-modal" role="dialog" aria-modal="true" aria-label="회원가입 후 건강정보 등록">
        <div className="rec-header">
          <div>
            <span className="rec-eyebrow">회원가입 완료</span>
            <h2>맞춤 추천을 위한 추가 정보</h2>
          </div>
          <button type="button" className="rec-close" onClick={onClose} aria-label="나중에 하기">×</button>
        </div>
        {step === "consent" && (
          <ConsentStep sending={sending} onAgree={agree} onSkip={onSkip} />
        )}
        {step === "survey" && survey && (
          <SurveyStep
            questions={survey.questions}
            needsConfirm={false}
            answers={answers}
            ingredients={ingredients}
            sending={sending}
            onChange={(questionId, value) => setAnswers((previous) => ({ ...previous, [questionId]: value }))}
            onSubmit={submit}
          />
        )}
        {step === "incomplete" && (
          <div className="rec-error">
            <strong>맞춤 질문에 필요한 정보가 아직 부족해요</strong>
            <p>기저질환 및 복용약 정보를 입력하거나 '없음'으로 답하면 V2로 이어집니다.</p>
            <div className="rec-actions">
              <button type="button" className="rec-button primary" onClick={() => setStep("survey")}>다시 입력하기</button>
              <button type="button" className="rec-button" onClick={onClose}>나중에 하기</button>
            </div>
          </div>
        )}
        {error && <p className="rec-error" role="alert">{error}</p>}
      </div>
    </div>
  );
}
