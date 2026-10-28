// 추천 결과 (담당: 영준) — 추천 성분 카드, 제외 성분, 안내 문구

const EMPTY_TEXT =
  "입력하신 건강 정보로는 지금 바로 권할 성분이 없어요. 복용 중인 약이나 질환이 있다면 의사·약사와 상담해 주세요.";

// 숫자 표시 (100.00 → 100, 2.5 → 2.5)
function formatNumber(value) {
  const n = Number(value);
  if (Number.isNaN(n)) return value;
  return n.toLocaleString("ko-KR", { maximumFractionDigits: 2 });
}

/**
 * props (RecommendationModal에서 전달)
 * - result: 추천 결과 (summary, items, excludedItems, fallback, notice)
 * - ingredients: 성분 코드 → { name, unit }
 * - onRestart: 다시 설문하기
 * - onClose: 닫기
 * - basisText: 결과 기준 안내 문구 (V2 맞춤 질문은 다른 문구)
 * - restartLabel: 다시 하기 버튼 문구
 * - children: 버튼 위에 덧붙일 안내 (V1 회원 결과의 맞춤 질문 안내 등)
 */
export default function ResultStep({
  result,
  ingredients,
  onRestart,
  onClose,
  basisText = "설문과 건강 프로필에 입력한 약을 기준으로 확인했어요.",
  restartLabel = "다시 설문하기",
  children,
}) {
  const nameOf = (code) => ingredients[code]?.name ?? code;
  const items = [...(result.items ?? [])].sort(
    (a, b) => a.sortOrder - b.sortOrder,
  );
  const excluded = result.excludedItems ?? [];

  return (
    <div className="rec-result">
      {result.fallback && (
        <p className="rec-notice">
          {result.notice ||
            "AI 맞춤 추천을 잠시 사용할 수 없어 기본 안내를 보여드려요"}
        </p>
      )}
      {/* 최종 추천이 0개면 AI 요약 대신 고정 안내 (제외 성분 목록은 아래에 그대로) */}
      {items.length > 0 && result.summary && (
        <p className="rec-summary">{result.summary}</p>
      )}

      {items.length === 0 ? (
        <div className="rec-empty">{EMPTY_TEXT}</div>
      ) : (
        <ul className="rec-cards">
          {items.map((item) => {
            const unit = item.unit || ingredients[item.ingredient]?.unit || "";
            return (
              <li className="rec-card" key={item.ingredient}>
                <div className="rec-card-head">
                  <strong>{nameOf(item.ingredient)}</strong>
                  <span className="rec-dose">
                    {item.amount != null
                      ? `하루 ${formatNumber(item.amount)}${unit} 추가`
                      : item.doseNote || "용량 정보 없음"}
                  </span>
                </div>
                {item.amount != null && item.doseNote && (
                  <small className="rec-hint">{item.doseNote}</small>
                )}
                <small className="rec-intake">
                  {item.intakeStatus === "KNOWN"
                    ? `현재 섭취량 ${formatNumber(item.currentIntake ?? 0)}${unit} 반영`
                    : "현재 섭취량 미반영"}
                </small>
                {item.reason && <p className="rec-reason">{item.reason}</p>}
                {item.warnings?.length > 0 && (
                  <ul className="rec-warnings">
                    {item.warnings.map((w) => (
                      <li key={w}>{w}</li>
                    ))}
                  </ul>
                )}
                <button type="button" className="rec-product" disabled>
                  제품 추천 보기 (준비 중)
                </button>
              </li>
            );
          })}
        </ul>
      )}

      {excluded.length > 0 && (
        <details className="rec-excluded" open={items.length === 0}>
          <summary>안전을 위해 제외한 성분 ({excluded.length})</summary>
          <ul>
            {excluded.map((item) => (
              <li key={item.ingredient}>
                <strong>{nameOf(item.ingredient)}</strong>
                {(item.warnings ?? []).map((w) => (
                  <p key={w}>{w}</p>
                ))}
              </li>
            ))}
          </ul>
        </details>
      )}

      <div className="rec-footnote">
        <p>{basisText}</p>
        <p>이 결과는 의학적 진단이 아니에요. 복용 전 의사·약사와 상담하세요.</p>
      </div>

      {children}

      <div className="rec-actions">
        <button type="button" className="rec-button" onClick={onRestart}>
          {restartLabel}
        </button>
        <button
          type="button"
          className="rec-button primary"
          onClick={onClose}
        >
          닫기
        </button>
      </div>
    </div>
  );
}
