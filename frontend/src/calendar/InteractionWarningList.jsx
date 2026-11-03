// 상충 결과(InteractionCheckResponse)를 그리는 공용 컴포넌트.
// 등록 폼 미리보기(PrescriptionFields)와 달력 모달(InteractionNotice)이 같이 쓴다.
const SEVERITY = {
  AVOID: { label: "피하세요", cls: "avoid" },
  CAUTION: { label: "주의", cls: "caution" },
  TIMING: { label: "간격 두기", cls: "timing" },
  INFO: { label: "참고", cls: "info" },
};

export default function InteractionWarningList({ data, dateKey }) {
  if (!data) return null;

  if (!data.checked) {
    return (
      <p className="interaction-empty">
        목록에서 고른 약이 아니라 영양제와 비교하지 못했어요.
      </p>
    );
  }

  const generalWarnings = data.warnings ?? [];
  const datedWarnings = (data.datedWarnings ?? [])
    .filter((item) => !dateKey || item.date === dateKey)
    .map((item) => item.warning);

  const warnings = [...generalWarnings, ...datedWarnings];
  return (
    <div className="interaction-notice">
      {warnings.length === 0 ? (
        <p className="interaction-empty">
          지금 먹는 영양제와 겹치는 주의 사항은 찾지 못했어요.
        </p>
      ) : (
        <ul>
          {warnings.map((w, i) => {
            const s = SEVERITY[w.severity] ?? SEVERITY.INFO;
            return (
              <li key={i} className={`interaction-item ${s.cls}`}>
                <span className="interaction-badge">{s.label}</span>
                <span className="day-copy">
                  <strong>{(w.supplementProducts ?? []).join(", ")}</strong>
                  <small>{w.message}</small>
                </span>
              </li>
            );
          })}
        </ul>
      )}
      {data.unverifiedSupplementCount > 0 && (
        <p className="interaction-empty">
          성분을 알 수 없는 영양제 {data.unverifiedSupplementCount}개는 비교하지
          못했어요.
        </p>
      )}
    </div>
  );
}
