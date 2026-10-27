// 설문 단계 (담당: 영준) — 한 문항씩 진행, 서버 호출은 제출 때만
import MedicationInput from "./MedicationInput";
import SupplementInput from "./SupplementInput";
import { useState } from "react";

const THIS_YEAR = new Date().getFullYear();

// 숫자 문항 검사. 출생 연도만 범위가 정해져 있다
function numberError(question, value) {
  if (value == null || value === "") return "";
  const n = Number(value);
  if (Number.isNaN(n) || n < 0) return "0 이상의 숫자를 입력해 주세요.";
  const isBirth = question.code === "Q-BIRTH";
  if (isBirth && (n < 1900 || n > THIS_YEAR || !Number.isInteger(n))) {
    return `1900부터 ${THIS_YEAR} 사이의 연도를 입력해 주세요.`;
  }
  return "";
}

// "다음"을 누를 수 있는지 (TEXT 문항은 빈 목록도 답으로 본다)
function canGoNext(question, answer) {
  if (question.answerType === "TEXT") return true;
  if (!answer) return false;
  if (question.answerType === "NUMBER") {
    const value = answer.valueNumber;
    return value !== "" && value != null && !numberError(question, value);
  }
  return answer.optionIds?.length > 0;
}

/**
 * props (RecommendationModal에서 전달)
 * - questions: 설문 문항 목록
 * - needsConfirm: 저장된 정보가 6개월 이상 지났는지
 * - answers: questionId → 답변
 * - ingredients: 성분 코드 → { name, unit }
 * - sending: 제출 중 여부
 * - onChange(questionId, value): 답변 변경 (value가 null이면 답 지우기)
 * - onSubmit(skippedQuestionId?): 마지막 문항에서 제출
 *   (건너뛴 마지막 문항 id를 넘기면 제출에서 뺀다)
 */
export default function SurveyStep({
  questions,
  needsConfirm,
  answers,
  ingredients,
  sending,
  onChange,
  onSubmit,
}) {
  const [index, setIndex] = useState(0); // 지금 보고 있는 문항 위치

  const question = questions[index];
  const answer = answers[question.questionId];
  const isLast = index === questions.length - 1;
  const error =
    question.answerType === "NUMBER"
      ? numberError(question, answer?.valueNumber)
      : "";

  const goNext = () => {
    if (isLast) onSubmit();
    else setIndex((i) => i + 1);
  };

  // 건너뛰면 답을 지운다. 마지막 문항이면 지운 답을 빼고 바로 제출
  const skip = () => {
    onChange(question.questionId, null);
    if (isLast) onSubmit(question.questionId);
    else setIndex((i) => i + 1);
  };

  // 선택형 답 바꾸기. 질환의 "없음(NONE)"은 다른 선택과 함께 고를 수 없다
  const toggleOption = (option) => {
    const selected = answer?.optionIds ?? [];
    if (question.answerType === "SINGLE") {
      onChange(question.questionId, { optionIds: [option.optionId] });
      return;
    }
    const adding = !selected.includes(option.optionId);
    let next = adding
      ? [...selected, option.optionId]
      : selected.filter((id) => id !== option.optionId);
    if (question.code === "Q-CONDITIONS" && adding) {
      const noneIds = question.options
        .filter((o) => o.valueCode === "NONE")
        .map((o) => o.optionId);
      next = noneIds.includes(option.optionId)
        ? [option.optionId]
        : next.filter((id) => !noneIds.includes(id));
    }
    onChange(question.questionId, next.length ? { optionIds: next } : null);
  };

  const changeNumber = (e) => {
    const value = e.target.value;
    onChange(question.questionId, value === "" ? null : { valueNumber: value });
  };

  const renderInput = () => {
    if (question.answerType === "SINGLE" || question.answerType === "MULTI") {
      const selected = answer?.optionIds ?? [];
      return (
        <div className="rec-options">
          {question.answerType === "MULTI" && (
            <small className="rec-hint">여러 개 고를 수 있어요</small>
          )}
          {question.options.map((option) => {
            const isSelected = selected.includes(option.optionId);
            return (
              <button
                type="button"
                key={option.optionId}
                className={`rec-option${isSelected ? " selected" : ""}`}
                aria-pressed={isSelected}
                onClick={() => toggleOption(option)}
              >
                {option.label}
              </button>
            );
          })}
        </div>
      );
    }

    if (question.answerType === "NUMBER") {
      const placeholder =
        question.code === "Q-BIRTH"
          ? `예: ${THIS_YEAR - 30}`
          : "숫자를 입력해 주세요";
      return (
        <div className="rec-number">
          <input
            type="number"
            inputMode="numeric"
            aria-label={question.text}
            placeholder={placeholder}
            value={answer?.valueNumber ?? ""}
            onChange={changeNumber}
          />
          {error && <p className="rec-field-error">{error}</p>}
        </div>
      );
    }

    const list = answer?.list ?? [];
    const setList = (next) => onChange(question.questionId, { list: next });
    if (question.code === "Q-MEDICATIONS") {
      return <MedicationInput value={list} onChange={setList} />;
    }
    if (question.code === "Q-SUPPLEMENTS") {
      return (
        <SupplementInput
          value={list}
          ingredients={ingredients}
          onChange={setList}
        />
      );
    }
    return (
      <p className="rec-hint">
        이 문항은 아직 화면에서 입력할 수 없어요. 건너뛰어 주세요.
      </p>
    );
  };

  return (
    <div className="rec-survey">
      {needsConfirm && index === 0 && (
        <p className="rec-confirm">
          저장된 정보가 6개월 이상 지났어요. 확인해 주세요.
        </p>
      )}

      <div className="rec-progress" aria-hidden="true">
        <span style={{ width: `${((index + 1) / questions.length) * 100}%` }} />
      </div>
      <span className="rec-step-count">
        {index + 1} / {questions.length}
      </span>

      <h3 className="rec-question">{question.text}</h3>
      {question.prefill && (
        <small className="rec-hint">
          저장된 정보로 미리 채웠어요. 바꿀 수 있어요.
        </small>
      )}

      {renderInput()}

      <div className="rec-nav">
        <button
          type="button"
          className="rec-button"
          disabled={index === 0 || sending}
          onClick={() => setIndex((i) => i - 1)}
        >
          이전
        </button>
        <button
          type="button"
          className="rec-link"
          disabled={sending}
          onClick={skip}
        >
          건너뛰기
        </button>
        <button
          type="button"
          className="rec-button primary"
          disabled={!canGoNext(question, answer) || sending}
          onClick={goNext}
        >
          {isLast ? (sending ? "제출 중..." : "제출하고 추천받기") : "다음"}
        </button>
      </div>
    </div>
  );
}
