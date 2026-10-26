// 맞춤 질문 한 개 (담당: 영준) — 선택지 버튼, 진행 표시, 여기까지 답하고 결과 보기

/**
 * props (PersonalizedModal에서 전달)
 * - question: { turnId, fixed, text, answerType, maxSelect, options: [{ index, label, exclusive }] }
 * - progress: { asked, base, max }
 * - selected: 고른 선택지 index 목록 (고른 순서)
 * - sending: 답 보내는 중 여부
 * - onChange(selected): 선택 변경
 * - onSubmit: 답 보내기
 * - canStop: "여기까지 답하고 결과 보기" 가능 여부 (서버 판단: 임신 질문 답 + LLM 질문 1개 이상 답)
 * - onStop: 여기까지 답하고 결과 보기
 */
export default function QuestionStep({
  question,
  progress,
  selected,
  sending,
  canStop,
  onChange,
  onSubmit,
  onStop,
}) {
  const multi = question.answerType === "MULTI";
  const full = selected.length >= question.maxSelect;

  // "없어요"·"잘 모르겠어요"는 혼자만 고를 수 있다 (서버도 검증)
  const toggle = (option) => {
    if (selected.includes(option.index)) {
      onChange(selected.filter((i) => i !== option.index));
    } else if (!multi || option.exclusive) {
      onChange([option.index]);
    } else {
      const others = selected.filter(
        (i) => !question.options[i]?.exclusive,
      );
      onChange([...others, option.index].slice(0, question.maxSelect));
    }
  };

  // MULTI에서 최대 개수를 고르면 나머지는 비활성 (이미 고른 것과 혼자만 고르는 선택지는 누를 수 있음)
  const isDisabled = (option) =>
    sending ||
    (multi && full && !selected.includes(option.index) && !option.exclusive);

  return (
    <div className="rec-pq">
      <span className="rec-step-count">
        {question.fixed
          ? "기본 확인 질문"
          : `질문 ${progress.asked} / 최대 ${progress.max}`}
      </span>
      <p className="rec-question">{question.text}</p>

      <div className="rec-options">
        {multi && (
          <small className="rec-hint">
            최대 {question.maxSelect}개까지 고를 수 있어요
          </small>
        )}
        {question.options.map((option) => (
          <button
            type="button"
            key={option.index}
            className={`rec-option${selected.includes(option.index) ? " selected" : ""}`}
            disabled={isDisabled(option)}
            onClick={() => toggle(option)}
          >
            {option.label}
          </button>
        ))}
      </div>

      <div className="rec-nav">
        <button
          type="button"
          className="rec-link"
          disabled={sending || !canStop}
          title={canStop ? undefined : "질문에 1개 이상 답하면 결과를 볼 수 있어요"}
          onClick={onStop}
        >
          여기까지 답하고 결과 보기
        </button>
        <button
          type="button"
          className="rec-button primary"
          disabled={sending || selected.length === 0}
          onClick={onSubmit}
        >
          다음
        </button>
      </div>
    </div>
  );
}
